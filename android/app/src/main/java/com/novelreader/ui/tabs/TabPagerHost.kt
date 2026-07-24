package com.novelreader.ui.tabs

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.novelreader.ui.theme.MotionDurationKTabSwitch
import kotlinx.coroutines.launch

/**
 * タブ層の恒常枠（ADR 0022 スロット契約・2026-07-24）。
 *
 * 「確定している形」＝〈恒常ボトムナビ＋タブ3面の水平 Pager〉を固定 API とし、タブの中身は
 * [pages] スロット（index = KTab.ordinal）として差し替え可能にする。**枠にはスキン分岐を持ち込まない**——
 * スキンごとの意匠は各スロット内（BookshelfScreen 等の exhaustive when）だけが担う。今後モックや
 * デザインを増やすときは、この枠の上でスロットの中身だけを滑らせる。
 *
 * なぜ Pager か（タブの横スワイプ化・2026-07-24 ユーザー裁定）: ジェスチャー語彙の4問審査
 * （毎セッション級頻度／ボトムナビの空間配列からの導出／可視代替＝ナビ自体／OS 予約語との共存＝
 *  Pager は端の戻るジェスチャと共存する標準挙動）を全問クリア。タップ切替とスワイプは同じ
 * スライド運動言語に統一される（旧 crossfade は Pager 追従と矛盾するため廃止）。
 *
 * Back の契約: タブ層でのシステム Back は「階層 up＝本棚（page 0）へ」。page 0 では枠は消費せず
 * Activity 既定（アプリ退出）へ委ねる。深い画面（読書・目次等）は NavHost 側の Back が先に受けるため
 * この BackHandler には到達しない（tabs ルートが前面のときだけ有効）。
 */
@Composable
internal fun TabPagerHost(
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    pages: List<@Composable () -> Unit>,
) {
    val scope = rememberCoroutineScope()
    // page 0 以外での Back＝本棚へ戻す（タブは同格だが「家」は本棚＝旧 popUpTo("bookshelf") 流儀の継承）。
    BackHandler(enabled = pagerState.currentPage != 0) {
        scope.launch { pagerState.animateScrollToPage(0, animationSpec = tween(MotionDurationKTabSwitch)) }
    }
    HorizontalPager(state = pagerState, modifier = modifier) { page ->
        pages[page]()
    }
}
