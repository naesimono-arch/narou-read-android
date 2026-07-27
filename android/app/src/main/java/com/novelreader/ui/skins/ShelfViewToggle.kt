package com.novelreader.ui.skins

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.novelreader.PrefKeys

/**
 * スキン別ビュー切替（星図⇄一覧・ラック⇄一覧・デッキ⇄一覧・グリッド⇄リスト）の状態ホルダ。
 *
 * なぜスキン側で所有するか（2026-07-27 裁定・スキン固有状態の移設）: これらは各スキン固有の表示モードで、
 * 共通 route（玄関 BookshelfScreen）に置くと「M の状態が P 装着時も玄関に眠る」うえ、スキンを増やすたび
 * 玄関の状態と配線引数が2つずつ増える。P の p_hinge_detent が確立した「スキン自身が prefs を直接読む」
 * 実装形を踏襲し、状態と永続化を使うスキンの足元へ置く。
 *
 * 永続化: トグルの度に SharedPreferences へ即書き（旧 route 実装と同じ）。初期値も prefs から読むため、
 * コンポジション離脱→再入（スキン切替・Activity 再生成）でも値は失われない＝plain remember で足りる
 * （p_hinge_detent の rememberSaveable はドラッグ中の連続値を持つため必要・こちらは常に確定済みの2値）。
 */
@Stable
internal class ShelfViewToggle(initial: Boolean, private val persist: (Boolean) -> Unit) {
    var value by mutableStateOf(initial)
        private set

    /** もう一方の面へ切り替える（反転値を即永続）。 */
    fun toggle() {
        value = !value
        persist(value)
    }
}

/** [prefKey]（[PrefKeys] の Boolean キー）で永続されるビュー切替状態を生成する。 */
@Composable
internal fun rememberShelfViewToggle(prefKey: String, default: Boolean): ShelfViewToggle {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(PrefKeys.FILE_APP_PREFS, Context.MODE_PRIVATE)
    }
    // key に prefKey も含める: 同一コンポジション位置でキーが差し替わる誤用（通常起きない）でも
    // 古いキーの値を引きずらない防御（remember の恒等性を「どの設定か」に一致させる）。
    return remember(prefs, prefKey) {
        ShelfViewToggle(prefs.getBoolean(prefKey, default)) { v ->
            prefs.edit().putBoolean(prefKey, v).apply()
        }
    }
}
