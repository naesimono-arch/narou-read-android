package com.novelreader.ui.diagnostics

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.novelreader.NovelReaderApplication
import com.novelreader.diagnostics.DiagnosticsStore
import com.novelreader.ui.skins.k.KSettingsCard
import com.novelreader.ui.skins.k.KSettingsChevronHole
import com.novelreader.ui.skins.k.KSettingsGroupLabel
import com.novelreader.ui.skins.k.KSettingsRow
import com.novelreader.ui.skins.k.KSettingsValue
import com.novelreader.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 端末内診断（`filesDir/diagnostics/`）の書き出し画面。正本モック
 * `docs/design-candidates/skins/diagnostics-export-K.html` の**案B（専用画面）**の Compose 翻訳。
 *
 * なぜこの面が要るか（モック冒頭の申し送りより）: 収集は release でも無条件に走る
 * （[NovelReaderApplication] が CrashReporter と SessionWatch を起動する）のに、取り出す手段は
 * `adb shell run-as`＝**debug 専用**しかなかった。日常利用してもらっている検証機（release）からは
 * 誰も回収できない＝「集めても取り出せない」状態を解消するのがこの画面の存在理由。
 * よって入口も本画面も **debug 限定にしない**（BuildConfig.DEBUG で潰すと目的そのものが消える）。
 *
 * 外部送信ゼロ（ADR 0011）を崩さないための語彙の規則（モック申し送り）: 「送信」「レポートを送る」の
 * 類は置かない。主操作は〈ファイルへ書き出す〉1つだけで、保存先の選択は Android の保存ダイアログ（SAF）に
 * 委ねる＝端末外へ出すか否かは常に人間の側にある。
 *
 * 層の分け方は ADR 0009: ここ（route 層）が Application 依存・ファイル I/O・SAF を持ち、
 * 描画は stateless な [DiagnosticsExportContent] が持つ（Robolectric のテスト対象はそちら）。
 *
 * ⚠️ 階層 up（← とシステム Back の着地）は**この画面が決めない**＝呼び出し元の nav 配線が [onUp] を
 * 与える（ADR 0026 の「BackHandler は MainActivity のルート配線に置く」）。
 */
@Composable
fun DiagnosticsExportScreen(onUp: () -> Unit) {
    val context = LocalContext.current
    val store = (context.applicationContext as NovelReaderApplication).diagnostics.store
    val scope = rememberCoroutineScope()

    // 書き出しの結末（成功・失敗）。rememberSaveable は回転をまたいで結末を残すため
    //（書き出した直後に回転すると「押したのに何も起きなかった」に見えるのを防ぐ）。
    var resultMessage by rememberSaveable { mutableStateOf<String?>(null) }

    // 件数と容量の読み出しはファイル I/O（jank.txt は最大約 256KB＝[DiagnosticsStore.MAX_JANK_BYTES]）。
    // 合成スレッドで触らない＝この画面はカクつきを計測する面であり、自分が原因になっては本末転倒。
    // ⚠️ 容量を dumpJank() の全文から測っているのは、[DiagnosticsStore] に「大きさだけ」を返す API が
    // 無いため（本便では diagnostics/ を触らない裁定）。上限 256KB の1回読みで済むので実害は無いが、
    // 保管庫側に size API が生えたらそちらへ寄せること。
    val state by produceState<DiagnosticsExportState?>(initialValue = null, store) {
        value = withContext(Dispatchers.IO) {
            DiagnosticsExportState(
                eventCount = store.count(),
                jankBytes = store.dumpJank().toByteArray().size.toLong(),
            )
        }
    }

    // 保存先は SAF（Storage Access Framework）が決める＝アプリは書き込み権限を1つも要求しない。
    // 既定のファイル名だけこちらが提案し、場所も最終的な名前も人間が決める。
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(EXPORT_MIME_TYPE),
    ) { uri ->
        // null＝保存ダイアログを閉じた（＝書き出しをやめた）。結末表示も出さずに元の面へ戻す。
        if (uri != null) {
            scope.launch {
                val written = runCatching {
                    withContext(Dispatchers.IO) {
                        val text = diagnosticsExportText(store.dumpAll(), store.dumpJank())
                        // openOutputStream の null は「SAF が返した URI を開けなかった」＝
                        // 保存先が既に消えている等。握り潰さず失敗として扱う。
                        val stream = context.contentResolver.openOutputStream(uri)
                            ?: error("保存先の URI を開けなかった: $uri")
                        stream.use { it.write(text.toByteArray()) }
                    }
                }
                written.onFailure { e ->
                    // runCatching はキャンセルも捕まえる＝ここで再送出しないと、画面を離れた際の
                    // キャンセルが「書き出し失敗」に化けて誤った結末を出す。
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    resultMessage = MESSAGE_EXPORT_FAILED
                }
                written.onSuccess { resultMessage = MESSAGE_EXPORT_DONE }
            }
        }
    }

    val loaded = state
    if (loaded == null) {
        // 読み出し中（数ミリ秒・push の enter アニメ中に終わる）。モックは2状態しか持たないので
        // 3つ目の版面（スケルトン等）を発明せず、画面の地だけを置く。
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    } else {
        DiagnosticsExportContent(
            state = loaded,
            resultMessage = resultMessage,
            onExport = {
                resultMessage = null
                exportLauncher.launch(EXPORT_FILE_NAME)
            },
            onUp = onUp,
        )
    }
}

/**
 * 画面が映す「たまっている記録」の現在値（モックの2行に1対1で対応）。
 *
 * 既定値を付けないのは /new-screen の規律＝「既存呼び出し互換のための既定値」は新しい呼び出し元の
 * 配線漏れを無音で成立させるため（値が来ていない面は「まだ無し」と区別できない）。
 */
@Immutable
data class DiagnosticsExportState(
    /** 不具合の記録の件数（[DiagnosticsStore.count]）。 */
    val eventCount: Int,
    /** 動作のなめらかさ要約の大きさ（バイト）。 */
    val jankBytes: Long,
) {
    /** どちらの記録も無い＝モックの「まだ記録が無いとき」の版面（主操作は非活性）。 */
    val isEmpty: Boolean get() = eventCount == 0 && jankBytes == 0L
}

/**
 * 書き出し面の描画層（stateless・ADR 0009）。意匠はすべて `diagnostics-export-K.html` 案B の翻訳で、
 * 色・寸法は settings-K.html の既存トークン再利用＝新しい値は1つも足していない（モック申し送り）。
 */
@Composable
internal fun DiagnosticsExportContent(
    state: DiagnosticsExportState,
    resultMessage: String?,
    onExport: () -> Unit,
    onUp: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Scaffold(containerColor = scheme.background) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            // ── ヘッダ（モック .head.deep＝← ＋ 題。.scroll の外＝スクロールしない固定）──────────
            // gap を spacedBy で足さないのは、IconButton が 48dp の当たり判定に 24dp のアイコンを
            // 中央置きする＝右側に既に 12dp の余白を持っており、モックの gap:12px がそれで満たされるため
            //（足すと 24px になってモックから開く）。start も同じ理由で 20px − 12dp ＝ Spacing.S8。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.S8, end = Spacing.S16, bottom = Spacing.S12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onUp) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "戻る",
                        tint = scheme.onSurface,
                    )
                }
                // モック .head.deep h1＝gothic 22px bold。SettingsScreenK の「設定」と同じ翻訳
                //（headlineSmall＋Bold）を使う＝同じ CSS 宣言を2通りに訳さない。
                Text(
                    TITLE,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                )
            }

            // ── 本文（モック .scroll。横 20px → 設定画面と同じ Spacing.S16 の翻訳）─────────────
            // weight(1f)＝モック .scroll の `flex:1`（ヘッダを固定して残りを本文がスクロールで受ける）。
            // fillMaxSize にしないのは、兄弟のヘッダと高さを奪い合う形になるため。
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.S16),
            ) {
                // リード文（モック .lead）＝この面が何かを3秒で言い切る1文。
                // 「送信」の語彙を置かず「今は端末の中だけにある」という現在の事実だけを述べる
                //（否定形で不安を煽らない＝モック申し送りの言い回しの規則）。
                Text(
                    buildAnnotatedString {
                        append("これらの記録は")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("この端末の中だけ") }
                        append("に保存されています。ここで書き出すまで、外へは出ていきません。")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface,
                    modifier = Modifier.padding(top = Spacing.S8),
                )

                KSettingsGroupLabel("たまっている記録")
                KSettingsCard {
                    KSettingsRow(
                        icon = Icons.Outlined.WarningAmber,
                        title = "不具合の記録",
                        // 上限をリテラルで書かず保管庫の定数から組む＝間引き上限を変えたときに
                        // 文言だけが古い数字のまま残る（誰も気づかない嘘）ことを構造的に防ぐ。
                        description = "落ちた・急に終わったときの記録（${DiagnosticsStore.KEEP_EVENTS}件まで）",
                        trailing = {
                            DiagnosticsCountValue(
                                filled = state.eventCount > 0,
                                text = if (state.eventCount > 0) "${state.eventCount}件" else EMPTY_VALUE,
                            )
                            KSettingsChevronHole()
                        },
                        onClick = null,
                    )
                    KSettingsRow(
                        icon = Icons.Outlined.MonitorHeart,
                        title = "動作のなめらかさ",
                        description = "画面ごとのカクつきの要約",
                        trailing = {
                            DiagnosticsCountValue(
                                filled = state.jankBytes > 0L,
                                text = if (state.jankBytes > 0L) "${state.jankBytes / BYTES_PER_KB}KB" else EMPTY_VALUE,
                            )
                            KSettingsChevronHole()
                        },
                        onClick = null,
                    )
                }

                // 主操作（モック .pbtn）。一画面一強調＝藍（primary）はここだけに使う。
                // 角丸はグループ面と同じ 12dp＝MaterialTheme.shapes.medium（Card の既定と同一トークン。
                // Button の既定は全周丸めでモックの radius:12px と合わない）。
                // 上下の詰めが S12 なのは行高からの逆算: bodyLarge の lineHeight 28dp ＋ 12dp×2 ＝ 52dp で
                // モックの 15px+15px+行 ≒ 49px に最も近い（S16 だと 60dp まで伸びてモックから開く）。
                Button(
                    onClick = onExport,
                    enabled = !state.isEmpty,
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(horizontal = Spacing.S16, vertical = Spacing.S12),
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.S24),
                ) {
                    Text(
                        EXPORT_BUTTON_LABEL,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        // モック .pbtn letter-spacing:.06em（bodyLarge 16sp × 0.06）。
                        letterSpacing = 0.96.sp,
                    )
                }

                // 補足（モック .pnote）。空のときは「なぜ空なのか」を言う＝記録は異常時にしか作られないので、
                // 何も無い状態を「壊れている」と誤読させないため（モックの空状態注記）。
                Text(
                    if (state.isEmpty) NOTE_EMPTY else NOTE_FILLED,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.S8),
                )

                // 書き出しの結末。⚠️ この1行だけは正本モックに無い（モックは押した後の面を持たない）。
                // 無言だと SAF で保存先を選んだ後に成否が一切わからず、失敗が黙って消える＝
                // 握り潰しになるため置いた。新しい意匠語彙は足さず .pnote と同じ字送りのまま、
                // 色だけ onSurface にしてある（結末は「意味を運ぶ文字」＝WCAG 4.5:1 を満たす側へ。
                // 判断の根拠はモック自身が .num へ書いている ADR 0014-D の適用）。
                // モック正本への逆同期は監督の裁定待ち（報告に明記）。
                if (resultMessage != null) {
                    Text(
                        resultMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurface,
                        modifier = Modifier.padding(top = Spacing.S4),
                    )
                }

                KSettingsGroupLabel("書き出される内容")
                // 明細（モック .note）＝読むための面で操作は無いので枠を持たず地のまま置く。
                Text(
                    CONTENT_DETAIL,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                // モックが .strong（--ink）で立てている1文。ここだけ地の文より強いのは、
                // 「何が入らないか」がこの面で最も読まれるべき情報だから（モック申し送りの言い回しの規則）。
                Text(
                    CONTENT_EXCLUSION,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurface,
                    modifier = Modifier.padding(bottom = Spacing.S24),
                )
            }
        }
    }
}

/**
 * 「たまっている記録」の右端に出す値。
 *
 * 件数・容量を [KSettingsValue] の既定（bodyMedium / onSurfaceVariant）でなく墨（onSurface）の
 * 太字で置くのは、モックが明記する理由による＝これは「システムに従う」のような単なる現在値ではなく
 * **意味を運ぶ値**で、読み取れないとこの面の存在意義が消える（ADR 0014-D「意味を運ぶ文字は WCAG 4.5:1」）。
 * 「まだ無し」は逆に現在値の側なので既定のまま沈める。
 * 素の Text で書かずこの部品（[KSettingsValue]）を通すのは、値が行名の幅を食い潰す穴を避けるため
 * （同関数の KDoc＝`unweighted-trailing-steals-row-width`）。
 */
@Composable
private fun DiagnosticsCountValue(filled: Boolean, text: String) {
    if (filled) {
        KSettingsValue(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    } else {
        KSettingsValue(text)
    }
}

/**
 * 2種の記録を1つのテキストへまとめる（モック「1つのテキストファイルにまとめます」）。
 *
 * 収集・整形そのものは書き直さず [DiagnosticsStore.dumpAll]／[DiagnosticsStore.dumpJank]（どちらも
 * KDoc に「回収用」と書かれた既存の書き出し実装）の出力をそのまま連ねる＝**この関数は器だけ**。
 * 空のときに `(なし)` を挟むのは、回収した側が「その種別が空だった」のか「連結に失敗した」のかを
 * 区別できるようにするため（無言の空行だと区別がつかない）。
 */
internal fun diagnosticsExportText(events: String, jank: String): String = buildString {
    appendLine(SECTION_EVENTS)
    appendLine(events.ifBlank { SECTION_NONE })
    appendLine()
    appendLine(SECTION_JANK)
    appendLine(jank.ifBlank { SECTION_NONE })
}

private const val TITLE = "診断の記録"
private const val EXPORT_BUTTON_LABEL = "ファイルへ書き出す"
private const val EMPTY_VALUE = "まだ無し"
private const val NOTE_FILLED = "保存する場所はあなたが選びます。1つのテキストファイルにまとめます。"
private const val NOTE_EMPTY =
    "記録は、アプリが落ちたときと画面がカクついたときにだけ作られます。何も無いのは正常です。"
private const val CONTENT_DETAIL =
    "発生した日時／アプリの版／端末の機種と Android の版／そのとき開いていた画面の名前／" +
        "メモリの空き／エラーの詳しい内容。"
private const val CONTENT_EXCLUSION = "作品の題名・本文・アカウントの情報は含まれません。"

/** 結末の文言（テストが文字列で掴む＝表示と検証で二重定義しない）。 */
internal const val MESSAGE_EXPORT_DONE = "書き出しました。"
internal const val MESSAGE_EXPORT_FAILED = "書き出せませんでした。保存先を変えてもう一度お試しください。"

private const val EXPORT_MIME_TYPE = "text/plain"
private const val EXPORT_FILE_NAME = "novel-reader-diagnostics.txt"
private const val BYTES_PER_KB = 1024L
private const val SECTION_EVENTS = "=== 不具合の記録 ==="
private const val SECTION_JANK = "=== 動作のなめらかさ ==="
private const val SECTION_NONE = "(なし)"
