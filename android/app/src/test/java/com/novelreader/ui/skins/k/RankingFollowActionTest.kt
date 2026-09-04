package com.novelreader.ui.skins.k

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 期間ページャの追従判断（[rankingFollowAction]）の契約。
 *
 * 固定する契約:
 *  1) ページが既に order を指しているなら何もしない（送りの有無は問わない）
 *  2) 送りが走っていない食い違いは即座に送る
 *  3) **送りが走っている食い違いは「捨てる」でなく「据わりを待つ」**（[RankingFollowAction.AwaitSettle]）
 *
 * なぜ画面の試験でなくここで守るか: 3) の穴は実機でしか現れない。Compose のテスト入力注入
 *（`performClick`/`performTouchInput`）は注入前に waitForIdle を通すため、**送りが据わる前にタップを
 * 届かせることが JVM では原理的にできない**（2026-09-05 実測: `performClick` も order 直書きも、
 * フリングが据わってからしか届かず競合窓を作れない）。実機側は `adb` のログ計測で
 * `ORDER=DAILY currentPage=5 scrolling=true → SKIP` を直接観測して真因を確定させたが、**その観測は
 * 回帰試験にできない**（一時ログを本番へ残さない・実機は CI に居ない）。∴ 判断だけを機械で固定する。
 *
 * ⚠️ 3) を [RankingFollowAction.AlreadyThere] や「戻り値なし」に緩めると、実機で観測した症状
 *（期間タブのタップが無反応。しかも setHomeOrder が同値 no-op のため**同じタブの再タップでも復帰不能**）
 * がそのまま戻る。ここは緩めてよい分岐ではない。
 */
class RankingFollowActionTest {

    private fun action(currentPage: Int, orderPage: Int, scrolling: Boolean) =
        rankingFollowAction(currentPage = currentPage, orderPage = orderPage, scrollInProgress = scrolling)

    @Test
    fun `ページが既にorderを指しているなら送りの有無に関わらず何もしない`() {
        assertEquals(RankingFollowAction.AlreadyThere, action(currentPage = 2, orderPage = 2, scrolling = false))
        // 送り中でも「着いている」が勝つ＝据わり直後の書き戻しで無駄なアニメを起こさない
        //（実機ログの `SKIP pageAlreadyEq=true` に相当する無害な経路）。
        assertEquals(RankingFollowAction.AlreadyThere, action(currentPage = 2, orderPage = 2, scrolling = true))
    }

    @Test
    fun `静止しているのに食い違っていれば即座に送る`() {
        assertEquals(RankingFollowAction.Follow, action(currentPage = 0, orderPage = 5, scrolling = false))
        assertEquals(RankingFollowAction.Follow, action(currentPage = 5, orderPage = 0, scrolling = false))
    }

    @Test
    fun `送りが走っている最中に来た食い違いは捨てず据わりを待つ`() {
        // ★これが 2026-09-05 の無反応の真因に対応する分岐（旧実装はここで何もせず、再試行の口も無かった）。
        assertEquals(RankingFollowAction.AwaitSettle, action(currentPage = 5, orderPage = 0, scrolling = true))
        assertEquals(RankingFollowAction.AwaitSettle, action(currentPage = 1, orderPage = 2, scrolling = true))
    }

    @Test
    fun `全期間の組み合わせで捨てる分岐が一つも無い`() {
        // 6期間×6期間×送りの有無を総当たりし、「食い違っているのに何もしない」結果が出ないことを見る
        //（分岐を1つ足したときに取りこぼしが混ざるのを、個別ケースの列挙でなく全数で防ぐ）。
        for (page in 0..5) {
            for (order in 0..5) {
                for (scrolling in listOf(false, true)) {
                    val result = action(page, order, scrolling)
                    if (page == order) {
                        assertEquals(RankingFollowAction.AlreadyThere, result)
                    } else {
                        assertEquals(
                            "page=$page order=$order scrolling=$scrolling で食い違いが放置された",
                            if (scrolling) RankingFollowAction.AwaitSettle else RankingFollowAction.Follow,
                            result,
                        )
                    }
                }
            }
        }
    }
}
