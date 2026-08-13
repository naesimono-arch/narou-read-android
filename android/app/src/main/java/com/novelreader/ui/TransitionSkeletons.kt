package com.novelreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.novelreader.ui.theme.LocalShelfColors
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
 * 本棚の遷移骨（案A の本棚版・2026-08-07）。タブ枠 push の enter 窓で、スキン面（M/P/J/K）の代わりに描く。
 *
 * なぜ既存 [BookshelfSkeleton]（P2・BookshelfScreen 内）を使い回さないか: あれは D/C 共通描画の内側にあり、
 * 版面を決める `isGridView` はスキンルーターより下（面が prefs で所有する状態）でしか手に入らない。
 * 骨をルーターの上流へ置く（＝スキン別の骨は新造しない・2026-07-29 裁定）ためには、面の状態に触れない汎形が要る。
 *
 * 版面は既定スキン K のグリッド（ADR 0027 で初回公開に出荷される＝実ユーザーが通る唯一の面）へ合わせる:
 * 左右 S24・列間 S32・行間 S16・書影 3:4・角丸3dp＝BookshelfK の LazyVerticalGrid と同値。
 * 骨の語彙（棒11dp・角丸2dp・塊/線の2値塗り）は P2 BookshelfSkeleton の写経、色は本棚トークン
 * （surfaceVariant＝塊／ShelfColors.hairline＝線）＝スキンとテーマに自動追従する。
 *
 * ヘッダ（題字・チップ行）まで骨で場所取りする理由: 骨は面の実ヘッダを持たないため、省くと着地の瞬間に
 * ヘッダの高さぶん本体が下へ跳ねる。目次の骨が実トップバーを描いて跳ねを消しているのと同じ要求を、
 * スキン共通の骨では「実ヘッダと同じ外形の骨」で満たす（M/P/J のヘッダ高は K と厳密には一致しないが、
 * 骨は内容非依存の汎形＝目次の骨が D の行外形で全スキンを賄っているのと同じ扱い）。
 * インセットを自分で持つ理由: 面（K の statusBarsPadding など）を丸ごと差し替えるため、骨が代わりに避ける。
 */
@Composable
internal fun ShelfTransitionSkeleton(modifier: Modifier = Modifier) {
    // 2値塗り＝P2 BookshelfSkeleton と同じトークン（塊＝書影・線＝題字/チップ）。
    val blockColor = MaterialTheme.colorScheme.surfaceVariant
    val lineColor = LocalShelfColors.current.hairline
    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        // ── ヘッダ骨（「本棚」＋「N冊」の場所取り）──
        // 行高48dp＝実ヘッダの右端 IconButton のタップ面（行高を決めているのはこれ）。padding も KHeader と同値。
        // 棒幅は実字の見当（題字 24sp×2字＝48dp／冊数 16sp×3字＝48dp）＝内容非依存（蔵書数に依らない）。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.S24, end = Spacing.S8, top = Spacing.S8, bottom = Spacing.S12)
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonBone(color = lineColor, modifier = Modifier.width(48.dp))
            Spacer(modifier = Modifier.width(Spacing.S8))
            SkeletonBone(color = lineColor, modifier = Modifier.width(48.dp))
        }
        // ── 状態フィルタチップ行の骨 ──
        // 行高30dp＝実チップ（文字 11.5sp≒14dp ＋上下 S8）。骨の語彙は棒だけに留め、ピルの輪郭は描かない
        //（骨が「内容」に見えないようにする＝目次骨で現在章ハイライトを出さないのと同じ判断）。
        // 棒幅＝固定4語（すべて/よみかけ/未読/読了）の字数×字送り 11.5sp≒12dp ＋ 左右 S16×2。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.S24, end = Spacing.S24, bottom = Spacing.S12)
                .height(30.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.S8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(3, 4, 2, 2).forEach { chars ->
                SkeletonBone(color = lineColor, modifier = Modifier.width(12.dp * chars + Spacing.S16 * 2))
            }
        }
        // ── 書影グリッドの骨（2列×3行＝360dp 級の窓を埋める最小行数）──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.S24, end = Spacing.S24, top = Spacing.S4),
            verticalArrangement = Arrangement.spacedBy(Spacing.S16),
        ) {
            repeat(3) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S32)) {
                    repeat(2) {
                        Column(modifier = Modifier.weight(1f)) {
                            // 書影（3:4・角丸3dp）＝K の ShioriCover と同寸。影は落とさない（骨は平置き）。
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(3f / 4f)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(blockColor),
                            )
                            // キャプション（題名1行＋状態行）の場所取り。間隔は実カード（S8→題名行→S4→状態行）
                            // の骨版＝棒2本を S8/S12 で置く（棒高11dp が実文字より低いぶんを下側の間隔で吸う）。
                            Spacer(modifier = Modifier.height(Spacing.S8))
                            SkeletonBone(color = lineColor, modifier = Modifier.fillMaxWidth(0.72f))
                            Spacer(modifier = Modifier.height(Spacing.S12))
                            SkeletonBone(color = lineColor, modifier = Modifier.fillMaxWidth(0.45f))
                        }
                    }
                }
            }
        }
    }
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
