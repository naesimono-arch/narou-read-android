package com.novelreader.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.PdfProcessingService
import com.novelreader.narou.model.Ncode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

/**
 * 縦書きPDF取り込み画面の状態。最小3値に絞る（ADR 0011）。
 * WebView 操作中は Idle、DL 実体を掴んで保存している間は Downloading、失敗時は Error（画面残留＝リトライ可能）。
 */
sealed interface PdfImportUiState {
    /** WebView でなろうの生成フローを操作中（DL 未捕捉）。 */
    data object Idle : PdfImportUiState
    /** DL 実体を OkHttp で取得・保存している最中。インジケータを出す。 */
    data object Downloading : PdfImportUiState
    /** DL 失敗。message を出して画面に残留し、ユーザーが DL ボタンを再タップして再試行できる。 */
    data class Error(val message: String) : PdfImportUiState
}

/**
 * 取り込み画面から UI へ配送する一度きりのイベント。
 * なぜ状態でなくイベントか: 「取り込み開始 Toast → 画面 pop」は副作用であり、状態として保持すると
 * 再コンポーズで二重発火しうる。Channel の受け切りイベントで一回だけ流す。
 */
sealed interface PdfImportEvent {
    /** Service へ取り込みを投入済み。UI は「開始しました」を出して画面を閉じる。 */
    data object ImportStarted : PdfImportEvent
}

/**
 * 縦書きPDF取り込み（ADR 0011・案B）の ViewModel。
 *
 * 役割: WebView の setDownloadListener が捕捉した最終 PDF URL（pdfnovels.net/...）を受け取り、
 * OkHttp で実体を cacheDir/pdf_import/ へ落とし、FileProvider で content:// 化して既存の取り込み経路
 * （PdfProcessingService → BookRepository.addBook）へ ncode 付きで合流させる。
 *
 * WebView 自体（JS・スクロール注入・戻る制御）は画面側 [com.novelreader.ui.discovery.PdfImportScreen] が持つ。
 * VM はネットワーク副作用（DL・Service 起動）だけを担い、Android View への依存を持たない。
 */
class PdfImportViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<PdfImportUiState>(PdfImportUiState.Idle)
    val uiState: StateFlow<PdfImportUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<PdfImportEvent>(Channel.BUFFERED)
    val events: Flow<PdfImportEvent> = eventChannel.receiveAsFlow()

    /**
     * WebView の setDownloadListener から呼ばれる。DL を捕捉して取り込みへ繋ぐ。
     *
     * @param url DL 対象 URL（最終 PDF 実体 or それ以外）
     * @param userAgent WebView が提示していた User-Agent
     * @param contentDisposition レスポンスの Content-Disposition（filename 判定に使う）
     * @param cookie CookieManager が持つ当該 URL の Cookie（null 可）
     * @param ncode 取り込む本に紐付けるなろう作品の Nコード
     */
    fun onDownloadRequested(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        cookie: String?,
        ncode: Ncode,
    ) {
        // なぜ MIME で判定しないか（ADR 0011 スパイク実測）: なろうの最終 PDF は
        // mimetype=application/octet-stream で返り、application/pdf ではない。よって MIME では PDF を判別できず、
        // Content-Disposition の filename=*.pdf か URL 拡張子 .pdf で判定する。PDF でない DL は無視する。
        if (!looksLikePdf(url, contentDisposition)) {
            Log.i(TAG, "PDF でない DL を無視: url=$url disposition=$contentDisposition")
            return
        }
        // 二重投入防止: 既に DL 中なら新たな捕捉を無視する（トークン URL は短時間再発火しうる＝スパイク実測）。
        if (_uiState.value is PdfImportUiState.Downloading) return

        val filename = deriveFilename(url, contentDisposition, ncode)
        val context = getApplication<Application>()
        // 保存先は file_paths.xml の cache-path pdf_import/ と一致させる（FileProvider の公開範囲）。
        val dir = File(context.cacheDir, "pdf_import")
        val outFile = File(dir, filename)

        _uiState.value = PdfImportUiState.Downloading
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    dir.mkdirs()
                    // 使い捨て1リクエスト用途のため OkHttpClient を都度生成する（NarouNetwork の client は private で
                    // 共有できず、また API 用の UA インターセプタは PDF DL には不要）。共有プールの利点が無いので新規で妥当。
                    val client = OkHttpClient()
                    val requestBuilder = Request.Builder().url(url)
                    // WebView の UA と Cookie を転送する。ログイン不要は実証済み（ADR 0011）だが、生成毎トークンの
                    // 検証が UA/Cookie に依存する可能性への防御。過剰でも実害は無いため付けておく。
                    userAgent?.let { requestBuilder.header("User-Agent", it) }
                    cookie?.takeIf { it.isNotBlank() }?.let { requestBuilder.header("Cookie", it) }
                    val call = client.newCall(requestBuilder.build())
                    // 協調キャンセル: viewModelScope キャンセル（DL 中に画面を離れた等）で OkHttp Call を中断し、
                    // ブロッキングな byte コピーを解いてスレッドを解放する。copyTo 自体は協調キャンセル非対応のため。
                    coroutineContext.job.invokeOnCompletion { call.cancel() }
                    call.execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IOException("DL に失敗しました（HTTP ${response.code}）")
                        }
                        val body = response.body ?: throw IOException("応答が空でした")
                        outFile.outputStream().use { out -> body.byteStream().copyTo(out) }
                    }
                    outFile
                }
            }
            result.fold(
                onSuccess = { file ->
                    handoffToImport(file, ncode)
                    _uiState.value = PdfImportUiState.Idle
                    eventChannel.send(PdfImportEvent.ImportStarted)
                },
                onFailure = { e ->
                    // 中断・失敗いずれでも一時領域に部分 DL のゴミを残さない（DL 中に離脱＝キャンセル時の掃除も兼ねる）。
                    runCatching { if (outFile.exists()) outFile.delete() }
                    // キャンセルはエラー表示せず静かに伝播させる（画面はもう pop 済み）。
                    if (e is CancellationException) throw e
                    Log.e(TAG, "PDF DL 失敗", e)
                    _uiState.value = PdfImportUiState.Error("PDFの取得に失敗しました。通信状況を確認して、もう一度お試しください")
                },
            )
        }
    }

    /** 保存済み PDF を FileProvider で content:// 化し、既存の取り込み経路（Service）へ ncode 付きで投入する。 */
    private fun handoffToImport(file: File, ncode: Ncode) {
        val context = getApplication<Application>()
        val uri: Uri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)

        // BookshelfViewModel.addBook と同じ作法: 永続権限の取得を試みる。FileProvider の content:// は
        // persistable permission を取れない（受領時に FLAG_GRANT_PERSISTABLE が無い）ため runCatching で
        // 無害に失敗する＝この本は強制終了→再開が効かないだけ（既存 addBook のコメントと同じ前提）。
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // 同一アプリ内なので Intent の FLAG_GRANT_READ_URI_PERMISSION だけで Service は openInputStream できるが、
        // 経路によっては Service プロセスへ grant が届かない可能性への防御として自パッケージへ明示 grant する（動く最小）。
        context.grantUriPermission(context.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

        val intent = Intent(context, PdfProcessingService::class.java).apply {
            action = PdfProcessingService.ACTION_START
            data = uri
            putExtra(PdfProcessingService.EXTRA_NCODE, ncode.value)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    companion object {
        private const val TAG = "PdfImportViewModel"
        // AndroidManifest の <provider android:authorities> と一致させること。
        private const val FILE_PROVIDER_AUTHORITY = "com.novelreader.fileprovider"

        /** Content-Disposition の filename=*.pdf か URL 拡張子 .pdf で PDF を判定する（MIME は使えない＝ADR 0011）。 */
        internal fun looksLikePdf(url: String, contentDisposition: String?): Boolean {
            val fromDisposition = contentDisposition
                ?.let { Regex("filename\\*?=(?:UTF-8''|\")?([^\";]+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }
                ?.trim()
                ?.endsWith(".pdf", ignoreCase = true) == true
            // URL はクエリ/フラグメントを除いたパス末尾で判定する。
            val path = url.substringBefore('?').substringBefore('#')
            val fromUrl = path.endsWith(".pdf", ignoreCase = true)
            return fromDisposition || fromUrl
        }

        /** 保存ファイル名を決める。Content-Disposition の filename → URL 末尾 → ncode の順でフォールバックし、
         *  パス区切りを除いた安全なベース名にする（ディレクトリトラバーサル防止）。必ず .pdf 拡張子を保証する。 */
        internal fun deriveFilename(url: String, contentDisposition: String?, ncode: Ncode): String {
            val raw = contentDisposition
                ?.let { Regex("filename\\*?=(?:UTF-8''|\")?([^\";]+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: url.substringBefore('?').substringBefore('#').substringAfterLast('/').takeIf { it.isNotBlank() }
                ?: ncode.value
            // パス区切り等を除去してベース名だけにする（外部由来文字列を保存パスに使う際の防御）。
            val base = raw.substringAfterLast('/').substringAfterLast('\\')
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .ifBlank { ncode.value }
            return if (base.endsWith(".pdf", ignoreCase = true)) base else "$base.pdf"
        }
    }
}
