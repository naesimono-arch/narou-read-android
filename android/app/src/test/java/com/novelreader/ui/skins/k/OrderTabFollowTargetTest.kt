package com.novelreader.ui.skins.k

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 期間タブの選択追従（[orderTabFollowTarget]）の算術契約。
 *
 * 固定する契約:
 *  1) 選択タブが可視域に収まっているときは追従しない（null）＝期間を送るたびに位置を作り直して行が揺れない
 *  2) 右へ溢れているときは右端＋溝1つが可視域に入るところまで送る
 *  3) 左へ隠れているときは左端−溝1つまで戻す
 *  4) 送り先は 0〜maxScroll にクランプ＝行の端では余分な溝ぶんまで送らない
 *  5) タブ1本が可視域より広いときは頭（左端）を優先する＝右端合わせで頭を切らない
 *
 * 寸法は**手で組んだ架空の1組**（実測値ではない。この関数は px を受けて px を返す純関数なので、実機や
 * Robolectric の文字送りに依存させない方が契約を固定できる）。タブ6本＝
 * [com.novelreader.narou.model.NarouOrder] の entries 順に見立て、四半期だけ3文字ぶん広い形にしてある。
 * 左端と右端は left(i)=Σ幅+溝*i で決まり、この組では順に
 * 0-104 / 136-240 / 272-376 / 408-564 / 596-700 / 732-836。中身 836 に対し可視域 624＝212 溢れる。
 */
class OrderTabFollowTargetTest {

    private val widths = listOf(104, 104, 104, 156, 104, 104)
    private val gap = 32
    private val viewport = 624
    private val maxScroll = 212 // 中身 836px −可視域 624px

    private fun target(selectedIndex: Int, scroll: Int) = orderTabFollowTarget(
        tabWidths = widths,
        selectedIndex = selectedIndex,
        gapPx = gap,
        scroll = scroll,
        viewportWidth = viewport,
        maxScroll = maxScroll,
    )

    @Test
    fun `可視域に収まっている選択タブでは追従しない`() {
        assertNull("週間（136-240）は先頭表示のまま完全に可視", target(selectedIndex = 1, scroll = 0))
        assertNull("四半期（408-564）も先頭表示で可視", target(selectedIndex = 3, scroll = 0))
        assertNull("月間（272-376）は右端まで送った状態でも可視", target(selectedIndex = 2, scroll = maxScroll))
    }

    @Test
    fun `右へ溢れた選択タブは右端が可視域へ入るまで送る`() {
        // 累計（596-700）は先頭表示では右へ 76px はみ出す＝右端＋溝が入るところまで送る。
        assertEquals(700 + gap - viewport, target(selectedIndex = 4, scroll = 0))
    }

    @Test
    fun `左へ隠れた選択タブは左端が見えるところまで戻す`() {
        // 右端まで送った状態（212）から週間（136-240）を選ぶ＝左端−溝まで戻す。
        assertEquals(136 - gap, target(selectedIndex = 1, scroll = maxScroll))
        // 日間（左 0）は溝を引くと負になるが 0 でクランプ＝行頭より手前へは送らない。
        assertEquals(0, target(selectedIndex = 0, scroll = maxScroll))
    }

    @Test
    fun `行の端まで送るときは余分な溝ぶんを送らない`() {
        // 新着（732-836）は右端＋溝＝244 を要求するが、行の端（212）でクランプされる。
        assertEquals(maxScroll, target(selectedIndex = 5, scroll = 0))
    }

    @Test
    fun `タブ1本が可視域より広いときは頭が見える位置で止める`() {
        // 可視域 80＝四半期（408-564・幅 156）1本より狭い状況。右端合わせだと 516 まで送って頭が切れるので、
        // 左端（408）で丸める＝期間名の1文字目から読める側を採る。現行ラベルでは到達しないが、
        // 超拡大＋極狭幅の組み合わせで踏み得る分岐なので挙動を固定しておく。
        assertEquals(
            408,
            orderTabFollowTarget(
                tabWidths = widths,
                selectedIndex = 3,
                gapPx = gap,
                scroll = 0,
                viewportWidth = 80,
                maxScroll = 836 - 80,
            ),
        )
    }

    @Test
    fun `行が可視域に収まる幅なら常に追従しない`() {
        // 溢れが無い状態（既定 412dp・fontScale 1.0 側＝6本とも1行に収まる）では、どの期間でも動かさない。
        widths.indices.forEach { index ->
            assertNull(
                "溢れていないのに追従先が出た（index=$index）",
                orderTabFollowTarget(
                    tabWidths = widths,
                    selectedIndex = index,
                    gapPx = gap,
                    scroll = 0,
                    viewportWidth = 1000,
                    maxScroll = 0,
                ),
            )
        }
    }
}
