package com.novelreader.narou

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.novelreader.PrefKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

// ============================================================
// 検索履歴＋ピン留め（D1）。蔵書 Room とは別系統のローカル永続（DataStore Preferences）。
// 並び・上限などの操作ロジックは SearchHistory の純関数に分離し、
// DataStore 実装は edit{} 内でそれを適用するだけにする（純JVMテストで担保するため）。
// ============================================================

/**
 * @param pinned ピン留めした検索語（ピン順・上限 [SearchHistoryLimits.MAX_PINNED]）
 * @param recent 最近の検索語（新しい順・上限 [SearchHistoryLimits.MAX_RECENT]）
 */
data class SearchHistory(
    val pinned: List<String> = emptyList(),
    val recent: List<String> = emptyList(),
)

object SearchHistoryLimits {
    const val MAX_RECENT = 20
    const val MAX_PINNED = 10
}

/**
 * 検索実行時の履歴追加。既出は先頭へ繰り上げ、ピン済みの語は recent に重複させない。
 * 上限超過は古い側から切り捨てる。空白語は無視。
 */
internal fun SearchHistory.withRecentAdded(word: String): SearchHistory {
    val w = word.trim()
    if (w.isEmpty() || w in pinned) return this
    val newRecent = (listOf(w) + (recent - w)).take(SearchHistoryLimits.MAX_RECENT)
    return copy(recent = newRecent)
}

/** ピン留め。recent から昇格し、ピン列の末尾へ足す（既存ピンの並びを崩さない）。上限到達時は何もしない。 */
internal fun SearchHistory.withPinned(word: String): SearchHistory {
    val w = word.trim()
    if (w.isEmpty() || w in pinned) return this
    if (pinned.size >= SearchHistoryLimits.MAX_PINNED) return this
    return copy(pinned = pinned + w, recent = recent - w)
}

/** ピン解除。消えると驚かせるため recent の先頭へ戻す。 */
internal fun SearchHistory.withUnpinned(word: String): SearchHistory {
    if (word !in pinned) return this
    return copy(pinned = pinned - word).withRecentAdded(word)
}

internal fun SearchHistory.withRecentRemoved(word: String): SearchHistory =
    copy(recent = recent - word)

/** 発見系画面が使う検索履歴ストア（テストで Fake に差し替えるため interface）。 */
interface SearchHistoryStore {
    val history: Flow<SearchHistory>
    suspend fun addRecent(word: String)
    suspend fun pin(word: String)
    suspend fun unpin(word: String)
    suspend fun removeRecent(word: String)
}

// なぜ corruptionHandler を指定するか: DataStore ファイルが破損すると読み書き時に
// CorruptionException が投げられる。これは IOException のサブクラスなので下の .catch でも捕捉はできるが、
// .catch は「その回だけ空を emit」するだけで破損ファイル自体は残り、以後の読み書きが毎回失敗し続ける。
// corruptionHandler は破損ファイルを空 Preferences で作り直して恒久復旧させるため、破損はこちらで塞ぐ。
// 検索履歴は非クリティカルなので黙って作り直して落とさない方針。
private val Context.searchHistoryDataStore: DataStore<Preferences>
        by preferencesDataStore(
            name = PrefKeys.FILE_NAROU_SEARCH_HISTORY,
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        )

class DataStoreSearchHistoryStore(context: Context) : SearchHistoryStore {

    private val dataStore = context.applicationContext.searchHistoryDataStore

    companion object {
        // なぜ改行区切りの素朴なシリアライズか: 検索語は単一行入力（改行が混入し得ない）で、
        // JSONシリアライザを持ち込むほどの構造ではないため。
        // キー文字列の正本は PrefKeys（型付き Preferences.Key への包みだけをここで行う）。
        private val KEY_PINNED = stringPreferencesKey(PrefKeys.SEARCH_HISTORY_PINNED)
        private val KEY_RECENT = stringPreferencesKey(PrefKeys.SEARCH_HISTORY_RECENT)

        private fun decode(raw: String?): List<String> =
            raw?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()

        private fun encode(words: List<String>): String = words.joinToString("\n")

        private fun fromPreferences(prefs: Preferences): SearchHistory = SearchHistory(
            pinned = decode(prefs[KEY_PINNED]),
            recent = decode(prefs[KEY_RECENT]),
        )
    }

    // なぜ .catch で IOException を握るか: DataStore のディスク読み出しは IOException を投げ得るが、
    // この Flow は DiscoveryViewModel の stateIn（viewModelScope・例外ハンドラ無し）で共有されるため、
    // 素通りさせるとアプリがクラッシュする。検索履歴は非クリティカルなので空履歴へフォールバックする
    // （DataStore 公式推奨パターン）。IOException 以外（プログラミングエラー等）は握り潰さず再送出する。
    override val history: Flow<SearchHistory> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::fromPreferences)

    private suspend fun update(transform: SearchHistory.() -> SearchHistory) {
        // なぜ IOException を握って無視するか: 検索履歴の書き込み失敗（ディスク I/O エラー等）で
        // アプリを落とす価値はない（非クリティカル機能）。ログのみ残し、次回書き込みで自然に回復させる。
        // IOException 以外は不具合の兆候なので握り潰さず再送出する。
        try {
            dataStore.edit { prefs ->
                val next = fromPreferences(prefs).transform()
                prefs[KEY_PINNED] = encode(next.pinned)
                prefs[KEY_RECENT] = encode(next.recent)
            }
        } catch (e: IOException) {
            Log.w("SearchHistoryStore", "検索履歴の保存に失敗（無視）", e)
        }
    }

    override suspend fun addRecent(word: String) = update { withRecentAdded(word) }
    override suspend fun pin(word: String) = update { withPinned(word) }
    override suspend fun unpin(word: String) = update { withUnpinned(word) }
    override suspend fun removeRecent(word: String) = update { withRecentRemoved(word) }
}
