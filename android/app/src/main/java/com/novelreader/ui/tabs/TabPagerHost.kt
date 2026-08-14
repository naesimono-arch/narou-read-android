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
 * タブ層の「家」＝本棚ページのスロット index。タブ間 Back（階層 up）の唯一の着地点。
 * KTab.BOOKSHELF.ordinal と一致することは KTabNavigationTest.pages_renderBySlotIndex が
 * `assertEquals(0, KTab.BOOKSHELF.ordinal)` で明示的に固定する（KTab を並べ替えたらここも直る側）。
 * 枠側に KTab（K スキンの列挙）を持ち込まないのは ADR 0022＝枠にスキン分岐を入れない規律のため。
 */
private const val HOME_TAB_PAGE = 0

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
 * この BackHandler には到達しない（tabs ルートが前面のときだけ有効）。規則の実装とその形の理由は
 * 下の BackHandler 直上のコメント（2026-08-14 実機バグ①②の真因対処）が正本。
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
    // ── タブ層の Back 規則の唯一の決定点（各画面・各スキンへは置かない）─────────────────────────
    // page 0 以外＝階層 up で本棚（page 0）へ／page 0＝枠は関与せずシステム既定（アプリ退出）。
    // 「家」が本棚なのは旧 popUpTo("bookshelf") 流儀の継承（タブは同格だが戻り先は1つに決める）。
    //
    // なぜ「enabled を反転させる常設の BackHandler」でなく「page 0 以外のときだけ BackHandler を置く」か
    //（2026-08-14 実機バグ①設定タブの Back で終了／②装いの間で装着後の Back で終了、への真因対処）:
    //   ・Predictive Back 下（AndroidManifest の enableOnBackInvokedCallback。targetSdk 36 では OS が常時
    //     ON 扱い）では「アプリが Back を受けるか」は、OnBackPressedDispatcher が OS の
    //     OnBackInvokedDispatcher へコールバックを**登録しているか**で決まる。登録は
    //     hasEnabledCallbacks（有効コールバックが1つでも在るか）の変化に追随して行われる。
    //   ・旧実装は起動時 page 0＝enabled=false で生まれた1個のコールバックを、以後は isEnabled の
    //     false→true 反転だけで使い回していた。実機（PGEM10 / ColorOS / Android 16）では設定タブへ
    //     移った後の Back がアプリへ届かず終了する＝**反転が OS 側の登録へ反映されていない**と推定される
    //     （androidx activity 1.8.2 側に反映経路が在ること自体は bytecode で確認済み。どこで落ちているかは
    //       端末を跨いで再現・計測しないと確定できないため未確定。ゆえに反転へ依存しない形に構造を変える）。
    //   ・実機で正しく効いている Back（読書・目次・装いの間・発見の結果一覧/詳細）は全て「必要になった
    //     時点で enabled=true のコールバックが新規に追加される」形＝追加のたびに登録が走る経路で、反転に
    //     依存しない。タブ層だけがこの形から外れていたのが①②に共通する構造上の真因。
    //   ・よって同じ形へ揃える。page 0 では**コールバックが存在しない**（旧 enabled=false と同値で登録も
    //     外れる＝除去時に hasEnabledCallbacks が再計算される）ので、page 0 のシステム「ホームへ戻る」
    //     プレビューは従来どおり効く（ここが本アプリで唯一の退出点。撤去でなく登録の作り方だけを変えた）。
    //   ・page 1⇄2 の移動では if の条件が真のまま＝同じコールバックが登録されたまま残る（再登録は起きない）。
    // PredictiveBackHandler にしない理由: これは純粋な状態遷移（Pager の水平スクロール）で、Back 進捗に
    // 連動させると Pager 自身の横スワイプと同軸の第二演出になり語彙が衝突する（ADR 0019 のスライド統一とも別系）。
    if (pagerState.currentPage != HOME_TAB_PAGE) {
        BackHandler {
            scope.launch {
                pagerState.animateScrollToPage(HOME_TAB_PAGE, animationSpec = tween(MotionDurationKTabSwitch))
            }
        }
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
