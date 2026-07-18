package com.novelreader.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/**
 * 縦書き本文（[VerticalChapterContent] の LazyRow）の終端で未消費の横デルタを捕捉し、章送りの引っ張り
 * プレビュー（[ChapterScreenContent] の dragOffsetPx/settleSwipe）へ接続する NestedScrollConnection（P4）。
 *
 * なぜ nestedScroll か: 縦書き本文は LazyRow(reverseLayout) で横スクロールするため、親の
 * draggable(Horizontal) が touch slop 段階でジェスチャを奪えず不発になる（横書きは LazyColumn＝縦
 * スクロールなので横ドラッグが素通りし draggable が生きる）。よって「LazyRow が消費し切れず余った横
 * デルタ」を nestedScroll の post/pre で拾い、既存の引っ張り機構へ横流しする。
 *
 * 座標系（P0 実測）: reverseLayout=true でも available.x は画面座標のまま＝「item #0＝右端・読み進めは
 * 指を右へ」。ゆえに章末までスクロールし切ってさらに右へ引くと LazyRow が消費できず available.x>0 が
 * 漏れ、章頭（#0 が右端）で左へ引くと available.x<0 が漏れる。この符号を dragOffset へ積むと、縦書きは
 * 「offset>0＝次章／offset<0＝前章」＝横書き（offset<0＝次章）の鏡像になる（方向対応表 P4）。
 *
 * コンポーザブル状態からはラムダ注入で分離し、JVM 単体テスト可能にする（[ChapterPullConnectionTest]）。
 *
 * @param enabled 縦書きモードのときだけ true。false（横書き）では一切消費しない＝既存 draggable 経路を侵さない。
 * @param dragOffset 現在の引っ張りオフセット（px）。呼び出し側の dragOffsetPx を読む。
 * @param onDragOffset オフセットを更新する（bounds への clamp 後の値をそのまま渡す）。
 * @param bounds オフセットの許容範囲（縦書き用 clamp。前章が引ける側=負、次章が引ける側=正。端章は 0）。
 * @param onPullStart 引っ張り開始時に呼ぶ（確定/戻しアニメ Job の cancel。完了済み Job の cancel は無害）。
 * @param onSettle 指を離した瞬間の確定/戻し（既存 settleSwipe を velocityX 付きで呼ぶ）。
 */
internal class ChapterPullConnection(
    private val enabled: () -> Boolean,
    private val dragOffset: () -> Float,
    private val onDragOffset: (Float) -> Unit,
    private val bounds: () -> ClosedFloatingPointRange<Float>,
    private val onPullStart: () -> Unit,
    private val onSettle: (velocityX: Float) -> Unit,
) : NestedScrollConnection {

    // NestedScrollSource.Drag は 1.7 で deprecated＝UserInput が指ドラッグの後継（fling/relocate は別値）。
    // ドラッグ由来のデルタだけを引っ張りに使い、プログラム的スクロール等は素通しする。
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (!enabled() || source != NestedScrollSource.UserInput) return Offset.Zero
        // ドラッグ開始＝進行中の確定/戻しアニメを止め、指への追従を返す（毎フレーム呼ばれるが cancel は冪等）。
        onPullStart()
        val offset = dragOffset()
        // 引き戻しの巻き取り: 既に引いている(offset≠0)状態で逆符号のデルタが来たら、0 へ近づく分だけ
        // ここ（子より前）で消費する。これが無いと引っ張り中の戻し操作で、下敷きの LazyRow が先に
        // スクロールしてしまい本文がずれる。0 は跨がず、超過分は未消費で LazyRow のスクロールへ流す。
        val windingBack = (offset > 0f && available.x < 0f) || (offset < 0f && available.x > 0f)
        if (!windingBack) return Offset.Zero
        val newOffset = if (offset > 0f) {
            (offset + available.x).coerceAtLeast(0f)
        } else {
            (offset + available.x).coerceAtMost(0f)
        }
        onDragOffset(newOffset)
        return Offset(newOffset - offset, 0f)
    }

    // 子（LazyRow）が消費し切れず余った横デルタ＝章端でのはみ出し。これを引っ張りへ積む。
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (!enabled() || source != NestedScrollSource.UserInput || available.x == 0f) return Offset.Zero
        val offset = dragOffset()
        // bounds で clamp＝端章（進めない側）は 0..0 に潰れ、その向きへは引けない（横書き draggable と同契約）。
        val newOffset = (offset + available.x).coerceIn(bounds())
        if (newOffset == offset) return Offset.Zero
        onDragOffset(newOffset)
        return Offset(newOffset - offset, 0f)
    }

    // 指を離した瞬間: 引っ張り中(offset≠0)なら settle を起動し、全速度を消費してリスト側の fling を止める
    //（残すと確定/戻しアニメと LazyRow の慣性スクロールが同時に走って喧嘩する）。引いていなければ素通し。
    override suspend fun onPreFling(available: Velocity): Velocity {
        if (!enabled() || dragOffset() == 0f) return Velocity.Zero
        onSettle(available.x)
        return available
    }
}
