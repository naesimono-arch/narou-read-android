// 教示カード列の「いつ出すか／いつ焼くか」を所有するセッション状態（VM は持たない＝正本 §8「VM 不要」）。
// SharedPreferences は [IntroFlagStore] の裏へ追い出してあるので、規則そのものは JVM で直接固定できる。
package com.novelreader.ui.intro

import android.content.SharedPreferences
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.novelreader.PrefKeys

/** 3 系統フラグ＋没入ピルの旧フラグの読み書き口（テストで差し替えるためのシーム）。 */
internal interface IntroFlagStore {
    fun isShown(group: IntroGroup): Boolean
    fun markShown(group: IntroGroup)

    /**
     * 没入ヒント（ピル）の通算1回を、教示カード側から焼く。
     * キー名は変えない＝[PrefKeys.IMMERSIVE_HINT_SHOWN] のまま（正本 §8「旧フラグの扱い」）。
     * 意味だけが増えている＝「ピルを出し切った」に加えて「組B がピルの役目を肩代わりした」でも立つ。
     */
    fun markImmersiveHintShown()

    /**
     * 本文の向きの保存値。**null＝キーがまだ無い**（＝誰もまだ選んでも設定してもいない）。
     *
     * なぜ Boolean でなく Boolean? か: [PrefKeys.READING_VERTICAL] の既定は false（横書き）のままにするので、
     * `getBoolean(key, false)` では〈横書きを選んだ人〉と〈まだ何も選んでいない人〉が同じ false に潰れる。
     * カードの初期選択は縦書きなので、この 2 つを取り違えると**横書きを選んだ人へ縦書きを選び直させる**。
     */
    fun readOrientationVertical(): Boolean?

    /** 本文の向きを確定する。書き先は既存の [PrefKeys.READING_VERTICAL] 1 本＝**新しいキーは足さない**。 */
    fun writeOrientationVertical(vertical: Boolean)
}

/** 組 → prefs キー。3 本とも Boolean・FILE_APP_PREFS・apply()（正本 §8）。 */
internal fun introPrefKey(group: IntroGroup): String = when (group) {
    IntroGroup.ABOUT -> PrefKeys.INTRO_ABOUT_SHOWN
    IntroGroup.READING -> PrefKeys.INTRO_READING_SHOWN
    IntroGroup.SEARCH -> PrefKeys.INTRO_SEARCH_SHOWN
}

internal class PrefsIntroFlagStore(private val prefs: SharedPreferences) : IntroFlagStore {
    override fun isShown(group: IntroGroup): Boolean = prefs.getBoolean(introPrefKey(group), false)

    override fun markShown(group: IntroGroup) {
        prefs.edit().putBoolean(introPrefKey(group), true).apply()
    }

    override fun markImmersiveHintShown() {
        prefs.edit().putBoolean(PrefKeys.IMMERSIVE_HINT_SHOWN, true).apply()
    }

    override fun readOrientationVertical(): Boolean? =
        if (prefs.contains(PrefKeys.READING_VERTICAL)) {
            prefs.getBoolean(PrefKeys.READING_VERTICAL, false)
        } else {
            null
        }

    override fun writeOrientationVertical(vertical: Boolean) {
        prefs.edit().putBoolean(PrefKeys.READING_VERTICAL, vertical).apply()
    }
}

/**
 * 向きの選択カードが最初に見せる側＝**縦書き**。
 *
 * ⚠️ これは [PrefKeys.READING_VERTICAL] の**既定値ではない**（あちらは false＝横書きのまま動かさない）。
 * 既定値そのものを true へ倒すと、**まだ一度も向きを触っていない既存ユーザーの本文が次回起動で縦書きに変わる**
 * （キー不在＝既定値が読まれるため）。このカードは新規の人にだけ問うので、その副作用を負わずに
 * 「看板（アプリ名も短い説明も縦書き）と初見の一致」だけを取れる。
 */
internal const val INTRO_ORIENTATION_DEFAULT_VERTICAL = true

/**
 * 教示カード列の進行と消費を所有するセッション寿命の state holder。
 *
 * 消費（フラグを焼く）規則は 3 本とも同じ 1 つ＝**その組の最後のカードまで到達したうえで、
 * 閉じた／次の組へ進んだ時点**（正本 §8）。途中で閉じた回は焼かないので、次の機会にまた出る。
 * 「閉じたとき」で足りるのは、カードがユーザーの操作を待つ＝閉じられた＝確実に見られた、が
 * 成立するため（ピルのように時間で「見たか」を推定する必要がない）。
 */
@Stable
internal class IntroController(private val store: IntroFlagStore) {

    /** null＝いま何も出していない。 */
    var flow: IntroFlow? by mutableStateOf(null)
        private set

    /**
     * 組B を表示したことで没入ピルを黙らせたか（**同一セッション内**の信号）。
     *
     * なぜ prefs だけでは足りないか: ChapterScreen の `chromeHintConsumed` は
     * `remember { mutableStateOf(prefs.getBoolean(…)) }` で**入場時に 1 度読むだけ**なので、
     * 同じセッションで prefs を書いても state は false のまま効果が走り続け、
     * **カードを閉じた直後にピルが出てくる**。この observable state を consumed 側へ合流させて塞ぐ。
     */
    var chromeHintSilenced: Boolean by mutableStateOf(false)
        private set

    /**
     * 向きの選択カードでいま**選ばれて見えている**側（true＝縦書き）。
     *
     * 保存値が在ればそれを、無ければ [INTRO_ORIENTATION_DEFAULT_VERTICAL]（縦書き）を初期選択にする。
     * ⚠️ **ここを読んだだけでは prefs へ何も書かない**——描いた瞬間に書くと、［あとで］で降りた人や
     * カードを見ただけの人の端末を勝手に書き換えることになる（このカードは設定画面ではない）。
     */
    var orientationVertical: Boolean by mutableStateOf(
        store.readOrientationVertical() ?: INTRO_ORIENTATION_DEFAULT_VERTICAL,
    )
        private set

    /**
     * チップを押した＝**その場で確定**する（画面の見えと保存値を 1 操作もずらさない）。
     * 押した人は明示的に選んだので、この後 ［あとで］ で降りても選択は残ってよい。
     */
    fun selectOrientation(vertical: Boolean) {
        orientationVertical = vertical
        store.writeOrientationVertical(vertical)
    }

    /**
     * 自動の割り込み（組A/B/C）。**条件を満たした組はその場で出す**＝「1 起動 1 組」の間引きは
     * 入れない（2026-08-21 裁定）。組A と組C が数秒で連続してもよい。
     *
     * ⚠️ すでに別のカードを出している間だけは開かない。これは間引きではなく
     * 「同時に 2 枚は描けない」という物理的制約で、**その組のフラグは未消費のまま**＝
     * 次にその画面へ着いたときに改めて出る（機会を焼かない）。
     */
    fun requestAuto(group: IntroGroup) {
        if (flow != null) return
        if (store.isShown(group)) return
        open(IntroFlow(startGroup = group, walkthrough = false))
    }

    /**
     * 設定＞操作の説明からの「通し」。**フラグを見ない**＝何度でも読める（正本 §8）。
     * ただし読み進めた組はその場で消費するので、ここで全部読んだ人へあとから自動で割り込むことはない。
     */
    fun openWalkthrough() {
        open(IntroFlow(startGroup = IntroGroup.ABOUT, walkthrough = true))
    }

    /** 主ボタン。終端なら閉じる、そうでなければ 1 枚進む。 */
    fun next() {
        val current = flow ?: return
        // 選択カードから**先へ進んだ**時点で、見えている選択をそのまま確定する。チップに触れずに
        // ［つづける］ を押した人も「見えていたもの」が選ばれる＝画面と保存値が食い違わない。
        // ⚠️ [dismiss] 側には置かない——［あとで］／スクリム外タップ／先頭 Back で降りた人の端末は
        // 書き換えない（見せただけで設定を変えるのは、このカードが持ってよい権能を超える）。
        if (current.card.choice != null) store.writeOrientationVertical(orientationVertical)
        if (current.isTerminal) {
            dismiss()
            return
        }
        // 「次の組へ進んだ時点」の消費はここ＝組の最後から先へ出た回だけ焼ける。
        consumeIfAtGroupEnd(current)
        show(current.advanced())
    }

    /** ［← もどる］／先頭以外でのシステム Back。 */
    fun back() {
        val current = flow ?: return
        if (current.isFirst) return
        show(current.back())
    }

    /** ［あとで］／終端ボタン／スクリム外タップ／先頭でのシステム Back（正本 §8 は 4 つとも同じ扱い）。 */
    fun dismiss() {
        val current = flow ?: return
        consumeIfAtGroupEnd(current)
        flow = null
    }

    private fun open(next: IntroFlow) {
        show(next)
    }

    private fun show(next: IntroFlow) {
        flow = next
        // 組B は**表示した瞬間**にピルを黙らせる（正本 §8）。T4 の轍（見た保証が無いのに焼く）を踏まないのは、
        // ここで焼くのがピルの機会だけで、教育の役目はカードが引き受けている＝失われる機会がゼロだから。
        // 組B 自身の消費（intro_reading_shown）は従来どおり「閉じたとき」＝目的の違う 2 つを同じ瞬間に縛らない。
        if (next.group == IntroGroup.READING && !chromeHintSilenced) {
            chromeHintSilenced = true
            store.markImmersiveHintShown()
        }
    }

    private fun consumeIfAtGroupEnd(current: IntroFlow) {
        if (current.index == IntroDeck.lastIndexOf(current.group)) {
            store.markShown(current.group)
        }
    }
}

/**
 * 画面側（入口）からコントローラへ届ける唯一の経路。null＝教示のホストが居ない構成
 * （個別画面だけを立てる Robolectric テスト・プレビュー）＝何も出さないし何も黙らせない。
 */
internal val LocalIntroController = staticCompositionLocalOf<IntroController?> { null }

/**
 * 没入ピルの「消費済み」判定。永続フラグと、同一セッションで組B が肩代わりした事実の**論理和**。
 *
 * この 1 本に合流させるのが今回いちばん壊しやすい箇所（正本 §8 の★）。触るのは
 * ChapterScreen の結線（consumed 引数へ渡す値）だけで、判定ロジック（awaitImmersiveHintSeen）には
 * 手を入れない——あちらは別便が直したばかりで、正しい。
 */
internal fun immersiveHintConsumed(persisted: Boolean, introSilenced: Boolean): Boolean =
    persisted || introSilenced
