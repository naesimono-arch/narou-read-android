package com.novelreader.ui.tabs

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
    // 画面遷移（NavHost push/pop）アニメ中は true。本棚グリッドの deferHeavyContent と同じ離散 State を
    // 呼び元（MainActivity の tabs ルート）が渡す＝遷移の端点でしか変化せず毎フレーム recompose を増やさない。
    deferNeighborPages: Boolean = false,
    pages: List<@Composable () -> Unit>,
) {
    val scope = rememberCoroutineScope()
    // page 0 以外での Back＝本棚へ戻す（タブは同格だが「家」は本棚＝旧 popUpTo("bookshelf") 流儀の継承）。
    BackHandler(enabled = pagerState.currentPage != 0) {
        scope.launch { pagerState.animateScrollToPage(0, animationSpec = tween(MotionDurationKTabSwitch)) }
    }
    // 遷移ジャム対策（2026-07-26 framestats 実測）: pop enter アニメ中に隣ページ（さがす面）の初回コンポーズが
    // 同居すると、pop 冒頭2フレームが約400ms（隣ページ常駐なし比で約2倍＝445/402↔212/153ms）へ悪化する。
    // アニメ中は常駐を 0 にし、settle 後に 1 へ戻して隣ページの初回コンポーズをアニメ外の単独フレームへ移送する
    //（P2 本棚スケルトンと同じ「重い仕事をアニメ窓の外へ移送」設計。隣ページは画面外＝視覚影響なし）。
    var residentNeighborPages by remember { mutableIntStateOf(if (deferNeighborPages) 0 else 1) }
    LaunchedEffect(deferNeighborPages) {
        if (deferNeighborPages) {
            residentNeighborPages = 0
        } else {
            // 本棚のスケルトン→実グリッド差戻し（defer 解除と同フレーム）と隣ページ初回コンポーズが同一フレームに
            // 同居すると post-anim フレームが二重に重くなるため、2フレームずらして別フレームへ分離する。
            withFrameNanos {}
            withFrameNanos {}
            residentNeighborPages = 1
        }
    }
    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        // 既定0だとタブ settle 毎に隣ページが破棄され、スワイプ開始のたび UI スレッド anim 段で
        // 再コンポーズが走るのがスワイプ jank の主因（2026-07-25 framestats 実測）→ 前後1ページ常駐化。
        // 遷移アニメ中のみ 0 へ落とす（上の residentNeighborPages コメント参照）。
        beyondViewportPageCount = residentNeighborPages,
    ) { page ->
        pages[page]()
    }
}
