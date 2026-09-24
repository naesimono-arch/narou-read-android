package com.novelreader.ui.intro

/**
 * 3 系統フラグと旧ピルフラグの記録だけを持つ差し替え（SharedPreferences を Robolectric へ持ち込まない）。
 * 「何回焼いたか」まで数えるのは、二重消費や取りこぼしが件数でしか出ない型のバグがあるため。
 */
internal class FakeIntroFlagStore(
    private val shown: MutableSet<IntroGroup> = mutableSetOf(),
) : IntroFlagStore {
    var immersiveHintShown: Boolean = false
        private set
    val marked: MutableList<IntroGroup> = mutableListOf()

    override fun isShown(group: IntroGroup): Boolean = group in shown

    override fun markShown(group: IntroGroup) {
        shown += group
        marked += group
    }

    override fun markImmersiveHintShown() {
        immersiveHintShown = true
    }

    /** 端末に残る `reading_vertical` の代わり。**null＝キー不在**（まだ誰も選んでいない）。 */
    var orientationVertical: Boolean? = null
        private set

    /** 何回書かれたか。「描いただけで書いていないか」は回数でしか出ないので数える。 */
    var orientationWrites: Int = 0
        private set

    override fun readOrientationVertical(): Boolean? = orientationVertical

    override fun writeOrientationVertical(vertical: Boolean) {
        orientationVertical = vertical
        orientationWrites++
    }

    /** 既に向きが保存されている端末を作る（選び直しの回を組み立てるため）。 */
    fun preSelectOrientation(vertical: Boolean) {
        orientationVertical = vertical
    }

    fun preShow(vararg groups: IntroGroup) {
        shown += groups
    }
}
