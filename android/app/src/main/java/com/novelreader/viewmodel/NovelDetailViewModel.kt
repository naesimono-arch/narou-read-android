package com.novelreader.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.NovelReaderApplication
import com.novelreader.data.WebNovelEntity
import com.novelreader.discovery.model.WorkDetail
import com.novelreader.narou.NarouApiException
import com.novelreader.narou.model.Ncode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface NovelDetailUiState {
    object Loading : NovelDetailUiState
    /**
     * @param fetchedAtMillis この詳細を API から取得し終えた時刻（epoch ms）。
     *   なぜ保持するか（M6/公理5 SSOT）: 詳細は一覧値の写しではなく取り直した最新値であり、
     *   画面に「いつ時点の情報か」を出して一覧との別取得由来の食い違いを判別可能にするため。
     */
    data class Content(val novel: WorkDetail, val fetchedAtMillis: Long) : NovelDetailUiState
    /** ncode に該当する作品が API に存在しない（削除・検索除外設定など）。 */
    object NotFound : NovelDetailUiState
    data class Error(val message: String) : NovelDetailUiState
}

/**
 * 取込済みの蔵書と、その本の**手元の栞**（作品詳細の固定バーが読む唯一の状態）。
 *
 * ⚠️ **顔（主CTA の文言）と着地先を1つの値から出すための型**（2026-09-04 裁定）。
 * この画面には栞が2つある——**手元の栞（ここ）** と **なろうの栞**（`readingProgress`）——ので、
 * どちらがどちらを決めるかを型で分ける: **主＝手元／副＝なろう**。
 * 取込済みの「既読／未読」の顔は手元の栞だけで決まり、なろう側の位置は一切見ない
 * （旧実装はなろうの位置で顔を決めていたため、〈アプリで8割読了・なろう未訪〉が「未読」の顔のまま
 * 続きへ着地していた）。
 *
 * ⚠️ **2026-09-07 に判定軸を明示して再確認した（結論は据え置き）**。軸＝「バーの顔と、主CTA を押した
 * ときの着地が食い違わないこと」＝表示が事実として正しいかは第二義。**この軸では手元の栞しか採れない**:
 * 主CTA の着地は手元の本の中（章 or 目次）にしか無く、なろうの位置はその着地を一切決めないため、
 * なろうを根拠にすると根拠と着地が別物のまま残る。同じ軸で落とした2案を再開させないためここへ畳む——
 * **併記案**（「アプリ8割／なろう第120話」）＝顔が2つになって「押したらどちらへ行くのか」が言えなくなり
 * 軸そのものを外す。加えて主CTA のラベル丈が伸び、案Bの「4状態すべてバー総高137dp」を割る。
 * **現状維持案**（なろう側のまま注記だけ足す）＝食い違いを残す選択なので軸に反する。
 * どちらも「事実として正しい」ことを理由に採れそうに見えるが、それは第二義の軸。
 *
 * @param bookId 蔵書の id（読書ルートの引数）。
 * @param lastReadFile 進捗行の `lastReadFilename`。行が無ければ null＝一度も章を開いていない。
 */
data class ImportedBook(val bookId: String, val lastReadFile: String?) {
    /** 主CTA の着地先ファイル。栞が無ければ目次（本棚から本を開く既存経路と同じ既定）。 */
    val startFile: String get() = lastReadFile ?: INDEX

    /** 「続きから」の顔にするか。⚠️ **着地が章かどうか**そのものを条件にしているので、
     *  顔と着地がずれることが**構造的に起きない**（進捗行に目次が保存されていても未読の顔になる）。 */
    val hasBookmark: Boolean get() = startFile != INDEX

    private companion object {
        /** 目次のファイル名（ReadingBackStack.INDEX と同値。読書ルートの既定着地）。 */
        const val INDEX = "index.html"
    }
}

/**
 * 作品詳細（discovery/detail/{ncode}）。ナビ引数の ncode を load() で受けて全項目を取得する。
 * DiscoveryViewModel から分離している理由: 詳細はルート引数だけで自己完結し、
 * 発見系の共有状態（ホーム/結果一覧）と寿命が異なるため。
 */
class NovelDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NovelReaderApplication
    private val repository = app.novelApiRepository

    // (b) Web由来・未取込カード: 「本棚に置く/外す」の永続先（蔵書 Room の web_novels）。
    private val bookRepository = app.repository

    private val _uiState = MutableStateFlow<NovelDetailUiState>(NovelDetailUiState.Loading)
    val uiState: StateFlow<NovelDetailUiState> = _uiState.asStateFlow()

    private var loadedNcode: Ncode? = null

    // load() された ncode の Flow 版。onShelf/importedBookId の購読切り替え（flatMapLatest）の起点にする。
    private val ncodeFlow = MutableStateFlow<Ncode?>(null)

    /** 現在の作品が Web由来カードとして本棚に置かれているか（固定バーのトグル表示用）。
     *  比較は保存時正規化（trim+uppercase）と同じ形＝表記ゆれで「置いたのにトグルが戻る」事故を防ぐ。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val onShelf: StateFlow<Boolean> = ncodeFlow
        .flatMapLatest { nc ->
            if (nc == null) flowOf(false)
            else bookRepository.webNovels.map { list ->
                val normalized = nc.storageKey
                list.any { it.ncode == normalized }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** 機能②: この作品の**なろう側**の読書位置（最後に開いた話数。未記録＝0）。
     *  >0 のとき記録した話へ直接着地する導線を出す。
     *  比較は保存時正規化（trim+uppercase）と同じ形で行う（表記ゆれで記録が引けない事故を防ぐ）。
     *
     *  ⚠️ **これは「なろうの栞」で、手元の本の栞（[ImportedBook.hasBookmark]）とは別物**（2026-09-04 裁定）。
     *  取込済みでは**副アクションだけ**がこれを読む＝主＝手元／副＝なろう。未取込のときは手元に本が
     *  無いのでこれが唯一の位置＝従来どおり固定バーの顔も決める。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val readingProgress: StateFlow<Int> = ncodeFlow
        .flatMapLatest { nc ->
            if (nc == null) flowOf(0)
            else bookRepository.webReadingProgress.map { list ->
                val normalized = nc.storageKey
                list.firstOrNull { it.ncode == normalized }?.lastReadEpisode ?: 0
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 現在の作品が既に蔵書（PDF 取込済み・ncode 紐付け）なら、その蔵書と**手元の栞**。未取込なら null。
     *  取込済みなら「取り込む」「本棚に置く」の2アクションは冗長のため固定バーから隠す（モック注記）。
     *
     *  ⚠️ **Boolean でなく [ImportedBook] を持つ**（2026-09-04 裁定・案A ＋ 同日の栞一本化）:
     *  取込済みの主CTA は蔵書を読書画面で開くので、判定だけでなく**着地先**（bookId と開くファイル）が要る。
     *  作品詳細が ncode しか持たず bookId を持たないことが「取込済みなのに手元の本へ行けない」の真因だった。
     *  ⚠️ さらに **`allProgress` を合流させて栞まで同じ1値に入れる**のが要点＝主CTA の文言（顔）と着地先が
     *  同じ源を見る。別々に引くと「未読の顔で続きから開く」食い違いが必ず戻る（それが今回の真因）。
     *  ⚠️ 進捗は Room の Flow なので、読書画面から戻ってきた時点で自動的に顔が更新される
     *  （一度きりの suspend 取得では「読んだのに未読の顔のまま」が残る）。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val importedBook: StateFlow<ImportedBook?> = ncodeFlow
        .flatMapLatest { nc ->
            if (nc == null) flowOf(null)
            else combine(bookRepository.allBooks, bookRepository.allProgress) { books, progress ->
                // 表記ゆれ無視の同一作品判定は Ncode.sameWorkAs（storageKey 突合＝2026-07-27 に全流儀と統一）に集約。
                val book = books.firstOrNull { it.ncode?.let { n -> Ncode(n).sameWorkAs(nc) } == true }
                book?.let { b ->
                    ImportedBook(b.id, progress.firstOrNull { it.bookId == b.id }?.lastReadFilename)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 「本棚に置く/外す」トグル。Content 未取得（Loading/Error）では何もしない
     *  （置くのに必要な title/writer/話数が無く、ボタン自体も Content でしか出ない）。 */
    fun toggleShelf() {
        val state = _uiState.value as? NovelDetailUiState.Content ?: return
        val nc = loadedNcode ?: return
        viewModelScope.launch {
            if (onShelf.value) {
                bookRepository.removeWebNovel(nc)
            } else {
                bookRepository.putWebNovel(
                    WebNovelEntity(
                        // 保存正規化は NcodeLinkSheet の紐付けと同じ Ncode.storageKey（trim+大文字）＝二重カード防止。
                        ncode = nc.storageKey,
                        // WorkSummary.title/author は非 null（マッパが欠落を弾く）ため従来の ?: フォールバックは不要。
                        title = state.novel.summary.title,
                        writer = state.novel.summary.author,
                        generalAllNo = state.novel.summary.chapterCount ?: 0,
                        addedAt = System.currentTimeMillis(),
                    )
                )
            }
        }
    }

    // なぜ Job を保持してキャンセルするか: 別 ncode で load() を連打すると、遅い旧リクエストの応答が
    // 後着して新しい結果を上書きしうる（応答の完了順は要求順を保証しない）。先行ロードをキャンセルして
    // 「最後の要求だけが状態を書く」を DiscoveryViewModel（homeLoadJob/resultLoadJob）と同じ方式で構造的に保証する。
    private var loadJob: Job? = null

    /** 同一 ncode の再呼び出し（再コンポーズ等）ではロードし直さない。 */
    fun load(ncode: Ncode) {
        if (loadedNcode == ncode && _uiState.value !is NovelDetailUiState.Error) return
        loadedNcode = ncode
        ncodeFlow.value = ncode
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = NovelDetailUiState.Loading
            _uiState.value = try {
                val novel = repository.novelDetail(ncode)
                if (novel != null) {
                    NovelDetailUiState.Content(novel, System.currentTimeMillis())
                } else {
                    NovelDetailUiState.NotFound
                }
            } catch (e: NarouApiException) {
                NovelDetailUiState.Error(e.userMessage)
            }
        }
    }

    fun retry() {
        val ncode = loadedNcode ?: return
        loadedNcode = null
        load(ncode)
    }
}
