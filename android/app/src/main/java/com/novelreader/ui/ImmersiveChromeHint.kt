// 没入クローム復帰ヒント（層②）の「見られた」判定。ChapterScreen.kt の route 層から切り出した（2026-08-21）。
// 切り出しの理由は ChapterScrollPersistence と同じ＝route ごと組むと Application/VM 依存で Robolectric に
// 載らないため、消費条件という「一度きりの資源を焼く」不変条件を単体で固定できる形にする。
package com.novelreader.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformLatest

/**
 * ヒントを「見られた」とみなすのに必要な**連続**可視時間（ms）。ピルのフェード表示尺そのもの。
 */
internal const val IMMERSIVE_HINT_VISIBLE_MS = 2600L

/**
 * [canBeSeen] が **連続で** [requiredMs] のあいだ true であり続けたときにだけ返る。
 * 途中で false へ落ちた回はタイマーごと破棄され 0 からやり直す（＝返らない）。
 *
 * なぜ「連続 [requiredMs] 可視」を〈見られた〉の代理指標に置くか:
 * アプリから視線そのものは観測できない。しかし**否定側**なら確実に言える——画面に載る前に消えた・
 * 載っている途中で消えた・そもそも前面ですらなかった時間は、**絶対に見られていない**。よって
 * 「出し切った（規定尺のあいだ、見られうる状態が一度も途切れずに続いた）」を消費条件に据えると、
 * 「見られていないのに通算1回を焼く」経路が構造的に消える。誤判定は必ず〈見たのにもう一度出る〉側へ倒れ、
 * 害の小さい方に寄る（逆＝〈見ていないのに二度と出ない〉は取り返しがつかない）。
 *
 * @param onVisibleChange 可視ゲートの開閉をそのまま通知する（＝ピルの表示状態はこの1本が唯一の駆動元）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun awaitImmersiveHintSeen(
    canBeSeen: Flow<Boolean>,
    requiredMs: Long = IMMERSIVE_HINT_VISIBLE_MS,
    onVisibleChange: (Boolean) -> Unit,
) {
    canBeSeen
        .distinctUntilChanged()
        .transformLatest { visible ->
            onVisibleChange(visible)
            // transformLatest は上流が動くと この block ごとキャンセルされる＝途中で可視が途切れた回は
            // emit へ到達しない。これが「やり直し」の実体で、経過時間を自前で積む必要が無い。
            if (visible) {
                delay(requiredMs)
                emit(Unit)
            }
        }
        .first()
}

/**
 * 没入クローム復帰ヒントの表示と、通算1回きりフラグの消費タイミングを所有する副作用コンポーネント。
 *
 * 【2026-08-21 修正の要点】旧実装は「クロームが退避した瞬間」に永続フラグを立てていた。だが
 * `collapsedFraction` を動かすのは中央タップのトグルだけでなく、**入場時の自動初期退避**
 * （ChapterScreen の didInitialCollapse）もある——つまりユーザー操作ですらない自動退避で、しかも
 * push 遷移窓（本文がまだ骨）や初期退避の実測待ちに重なる数フレームで、アプリ通算1回の教示機会が
 * 焼き切れていた。加えて 2600ms の途中で章送り・目次へ離脱・バックグラウンド化が起きると、
 * フラグだけが立ってピルは誰にも見られずに消えた。ここでは消費を「出し切った」時点まで遅らせる。
 *
 * @param barsVisualReady 初期退避の実測が完了したか。false の間はバー群が alpha=0 の未確定状態で、
 *   その上にピルだけ載せても画面として成立していない。
 * @param deferHeavyContent push 遷移窓（本文が骨）。窓が開いている間は画面が着地していない。
 * @param consumed 既に通算1回を消費済みか。true なら一切出さない。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImmersiveChromeHintEffect(
    topAppBarState: TopAppBarState,
    barsVisualReady: Boolean,
    deferHeavyContent: Boolean,
    consumed: Boolean,
    onVisibleChange: (Boolean) -> Unit,
    onConsumed: () -> Unit,
    visibleMs: Long = IMMERSIVE_HINT_VISIBLE_MS,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    // コールバックだけは State 越しに読む（毎コンポジション新しいラムダが来てもコルーチンを再起動させない）。
    val latestOnVisibleChange by rememberUpdatedState(onVisibleChange)
    val latestOnConsumed by rememberUpdatedState(onConsumed)

    // barsVisualReady / deferHeavyContent をキーに含める＝これらが動いたら効果ごと作り直す。
    // なぜ State 越しでなくキーで持つか: どちらも「見られうる画面か」の前提条件で、崩れた時点でその回は
    // 〈出し切っていない〉扱いにしてタイマーを 0 へ戻すのが正しい。再起動がそのままやり直しになるので、
    // 経過の持ち越しを別途潰す必要がない。collapsedFraction だけはフレームレート state でキーにできず、
    // snapshotFlow で composition の外から観測する。
    LaunchedEffect(topAppBarState, lifecycleOwner, visibleMs, barsVisualReady, deferHeavyContent, consumed) {
        // 前提が崩れている間は一切出さない（消費済み／初期退避の実測待ち／push 遷移窓で本文がまだ骨）。
        if (consumed || !barsVisualReady || deferHeavyContent) return@LaunchedEffect
        try {
            awaitImmersiveHintSeen(
                canBeSeen = combine(
                    // クロームが退避したまま＝復帰操作を知らないと詰む状態が現に続いている、の唯一の信号。
                    snapshotFlow { topAppBarState.collapsedFraction > 0.9f },
                    // 前面（RESUMED）でない時間は算入しない。旧実装ではホームへ抜けている間に 2600ms が
                    // 溶け、戻ったときにはピルが無くフラグだけが立っていた。
                    // なぜ repeatOnLifecycle でなく currentStateFlow か: repeatOnLifecycle は内部で
                    // Dispatchers.Main.immediate へ切り替えるためコルーチンが composition の
                    // フレームクロックから外れる（＝タイマーの時計が別系統になる）。ゲートの一部として
                    // 同じ flow に畳めば、時計は1つのまま前面判定だけを足せる。
                    lifecycleOwner.lifecycle.currentStateFlow,
                ) { chromeHidden, lifecycleState ->
                    chromeHidden && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
                },
                requiredMs = visibleMs,
                onVisibleChange = { latestOnVisibleChange(it) },
            )
            // 出し切った＝ここで初めてピルを引っ込め、同時に通算1回を焼く。
            latestOnVisibleChange(false)
            latestOnConsumed()
        } finally {
            // 前提が崩れた／画面を離れた、のいずれでもピルは必ず引っ込める
            // （出しっぱなしのまま効果だけ死ぬと、タップでしか消せない帯が残る）。
            latestOnVisibleChange(false)
        }
    }
}
