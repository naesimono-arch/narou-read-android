package com.novelreader.narou

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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

private val Context.searchHistoryDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "narou_search_history")

class DataStoreSearchHistoryStore(context: Context) : SearchHistoryStore {

    private val dataStore = context.applicationContext.searchHistoryDataStore

    companion object {
        // なぜ改行区切りの素朴なシリアライズか: 検索語は単一行入力（改行が混入し得ない）で、
        // JSONシリアライザを持ち込むほどの構造ではないため。
        private val KEY_PINNED = stringPreferencesKey("pinned")
        private val KEY_RECENT = stringPreferencesKey("recent")

        private fun decode(raw: String?): List<String> =
            raw?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()

        private fun encode(words: List<String>): String = words.joinToString("\n")

        private fun fromPreferences(prefs: Preferences): SearchHistory = SearchHistory(
            pinned = decode(prefs[KEY_PINNED]),
            recent = decode(prefs[KEY_RECENT]),
        )
    }

    override val history: Flow<SearchHistory> = dataStore.data.map(::fromPreferences)

    private suspend fun update(transform: SearchHistory.() -> SearchHistory) {
        dataStore.edit { prefs ->
            val next = fromPreferences(prefs).transform()
            prefs[KEY_PINNED] = encode(next.pinned)
            prefs[KEY_RECENT] = encode(next.recent)
        }
    }

    override suspend fun addRecent(word: String) = update { withRecentAdded(word) }
    override suspend fun pin(word: String) = update { withPinned(word) }
    override suspend fun unpin(word: String) = update { withUnpinned(word) }
    override suspend fun removeRecent(word: String) = update { withRecentRemoved(word) }
}
