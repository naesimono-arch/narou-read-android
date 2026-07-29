package com.novelreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.novelreader.ui.theme.ReadingColors
import com.novelreader.ui.theme.Spacing

// ============================================================
// push 遷移中の構造骨（案A・2026-07-29 ユーザー裁定。正本モック＝
// docs/design-candidates/transition-skeleton-D.html の案A）。
// 本棚→目次/本文 push の 250ms slide 窓に実内容の初回 measure（Perfetto 2026-07-16 実測:
// 本棚グリッド 51ms・目次初回コンポーズ 93ms 級・本文テキスト 67ms 級）が同居して落ちるため、
// 遷移中は外形を実寸一致させた骨だけを描き、重い measure をアニメ完了後の静止フレームへ移送する
//（P2 BookshelfSkeleton と同じ差し替え機序）。
// 骨の語彙は P2 写経: 棒11dp・角丸2dp・塗り2値。色は ReadingColors の blockBackground/blockBorder
//（本棚骨の surfaceVariant/hairline に対応する読書パレットの既存トークン＝LIGHT で同値
// #F1F0EC/#E4E2DB。SkinTokens.reading 経由でテーマとスキンの両方へ自動追従）を使う。
// スキン別の骨は新造しない（2026-07-29 裁定＝この共通1式で全スキン成立）。
// シマー等のアニメは付けない（P2 と同じ「最小の同型要素」に留める＋遷移窓の描画コスト自体を最小化）。
// ============================================================

/** 骨1本（棒11dp・角丸2dp＝P2 SkeletonLine と同寸）。幅は modifier で与える。 */
@Composable
internal fun SkeletonBone(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(11.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color),
    )
}

/**
 * 目次の遷移骨（案A）。行の外形（左4dpルール域・padding S24/S16・行高24dp・divider 0.5dp）を
 * 実リスト（TocList）の1行ぶんと同一にし、実内容への差し替えで区切り線が1pxも跳ばないようにする。
 * 現在章ハイライト・既読✓・現在地バーは骨に出さない（内容依存の要素は実内容の初回描画で
 * 「答え合わせ」として初出させる＝骨は内容非依存を保つ。モック案A の note と同判断）。
 */
@Composable
internal fun TocTransitionSkeleton(colors: ReadingColors, modifier: Modifier = Modifier) {
    // 骨色は「線」側（blockBorder＝本棚骨 hairline の読書パレット対応値）。モック .bone 既定＝--skel-line の写経。
    val boneColor = colors.blockBorder
    // 幅の揺らぎ＝モック案A の12行を写経（章題らしい不規則さ。意匠の自己判断はしない）。
    val widthFractions =
        listOf(0.88f, 0.64f, 0.76f, 0.92f, 0.58f, 0.81f, 0.70f, 0.86f, 0.62f, 0.78f, 0.90f, 0.66f)
    Column(modifier = modifier) {
        widthFractions.forEach { fraction ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 実リストの左アクセントバー（S4）ぶんの透明域＝テキスト開始位置を実行と揃える。
                Spacer(modifier = Modifier.width(Spacing.S4))
                // 実行の Text は padding(S24/S16)＋lineHeight 24sp＝1行ぶんの行高。骨は同じ外形の中で
                // 棒を縦中央に置く（24.dp は 24.sp の等倍近似。骨は内容非依存の汎形＝fontScale 追従はしない）。
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = Spacing.S24, vertical = Spacing.S16)
                        .height(24.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    SkeletonBone(color = boneColor, modifier = Modifier.fillMaxWidth(fraction))
                }
            }
            // 実リストと同じヘアライン（divider 0.5dp）＝差し替え時に線が動かない要。
            HorizontalDivider(color = colors.divider, thickness = 0.5.dp)
        }
    }
}

/**
 * 本文の遷移骨（案A）。段落棒を実本文の行リズム（fontSize×lineHeightEm）へ載せ、章見出し
 *（話数・題・短いルール）の場所取りを添える。前書きブロックの有無・実段落構成は章ごとに可変のため
 * 骨に出さない（内容非依存の汎形＝モック案A の note と同判断）。
 * 縦書きモードでも横書き骨のまま使う（2026-07-29 裁定＝縦書き専用骨は作らない。250ms の場所取りに
 * 組方向の忠実さより「1種で全設定に成立する汎形」を優先し、差し替え点も縦横分岐の上流に置く）。
 */
@Composable
internal fun ReadingBodySkeleton(
    colors: ReadingColors,
    fontSize: Int,
    lineHeightEm: Float,
    bodyMarginDp: Int,
    modifier: Modifier = Modifier,
) {
    // 見出し骨＝「線」側／段落骨＝「塊」側（多数並ぶ段落棒は一段淡い方＝モック .bone/.bone.blk の写経）。
    val headBone = colors.blockBorder
    val paraBone = colors.blockBackground
    // 段落棒のピッチ＝実本文の行送り（sp→dp は等倍近似＝骨は汎形・厳密一致は不要）。棒間隔＝行送り−棒高で
    // 並べると棒列が本文の行リズムに載る（モックの間隔 29px＝40−11 と同式）。極端な設定値でも棒が
    // 密着しないよう下限 S8 で防御する（fontSize×lineHeightEm が 19dp を下回る設定は現状 UI では作れないが、
    // 設定レンジの将来変更に対する防御＝真因は設定値の外部依存）。
    val lineGap = ((fontSize * lineHeightEm).dp - 11.dp).coerceAtLeast(Spacing.S8)
    Column(
        modifier = modifier
            .fillMaxSize()
            // 左右余白は実本文と同じユーザー設定値＝差し替えで棒→文字の左端が揃う。
            .padding(horizontal = bodyMarginDp.dp),
    ) {
        // 章見出しの場所取り（話数ラベル・題・短いルール）。棒の寸法はモック案A 実寸（72/208/48×2px）、
        // 縦間隔は S スケールへ丸め（20/12/18/28px → S16/S12/S16/S24＝ADR 0014 スケール。HereBarD と同じ丸め裁定）。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.S16, bottom = Spacing.S24),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SkeletonBone(color = headBone, modifier = Modifier.width(72.dp))
            Spacer(modifier = Modifier.height(Spacing.S12))
            SkeletonBone(color = headBone, modifier = Modifier.width(208.dp))
            Spacer(modifier = Modifier.height(Spacing.S16))
            // 章見出し下の短いルール（48×2dp・角丸1dp）＝実見出しの藍ルールの場所取り（色は骨2値に留め、
            // アクセント色は使わない＝骨が「内容」に見えないようにする）。
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(headBone),
            )
        }
        // 段落棒＝モック案A の3段落構成（4行＋1行＋3行）を写経。first=幅比率／second=段落頭（1em≒fontSize dp
        // の字下げ＝組版の呼吸を骨でも保つ）。結び行 45%・全行が同一ピッチ＝段落間も行送り1つぶん（モック同値）。
        val lines = listOf(
            0.88f to true, 1f to false, 1f to false, 0.45f to false,
            0.64f to true,
            0.94f to true, 1f to false, 0.58f to false,
        )
        lines.forEach { (fraction, indented) ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = if (indented) fontSize.dp else 0.dp, bottom = lineGap),
            ) {
                SkeletonBone(color = paraBone, modifier = Modifier.fillMaxWidth(fraction))
            }
        }
    }
}
