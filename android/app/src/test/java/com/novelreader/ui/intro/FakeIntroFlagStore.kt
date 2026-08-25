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

    fun preShow(vararg groups: IntroGroup) {
        shown += groups
    }
}
