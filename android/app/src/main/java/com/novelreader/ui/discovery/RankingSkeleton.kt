package com.novelreader.ui.discovery

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.novelreader.narou.model.DiscoveryQuery
import com.novelreader.ui.SkeletonBone
import com.novelreader.ui.theme.LocalShelfColors
import com.novelreader.ui.theme.Spacing

// ============================================================
// ランキング一覧の構造スケルトン（案A 構造プレースホルダ・正本モック
// docs/design-candidates/transition-skeleton-D.html の案A／実装先例 BookshelfSkeleton・TransitionSkeletons）。
//
// なぜ「読み込んでいます」1行でなく骨を描くか（＝真因への対処）:
//   一覧を status 1行へ置き換えると、その領域の高さが行数ぶんから1行ぶんへ崩壊する。すると親 LazyColumn の
//   総コンテンツ高が縮み、LazyListState は保持していた可視アンカー（item index + offset）にもう留まれず
//   先頭側へクランプされる＝「勝手にトップまでスクロールされる」既知バグ
//  （docs/knowledge/lazylist-loading-full-replace-scroll-reset.md）。
//   直近 Content を控えて出し続ける stale-while-revalidate は「控えのある再訪」しか救えず、
//   控えの無い初訪（例: K のランキング期間ページャで一度も開いていない期間）では原理的に効かない。
//   そこで行数ぶんの骨を描いて「高さそのもの」を保つ＝位置を保存して復元する対症療法ではなく、
//   高さ崩壊という機序を起こさない構造にする。
//
// 骨の語彙は既存実装の写経（意匠を発明しない）:
//   棒＝[SkeletonBone]（11dp・角丸2dp）／色＝本棚骨と同じ ShelfColors.hairline（定義コメントどおり
//   「スケルトン線」用途のトークン。スキンとテーマへ自動追従するのでスキン別の骨を新造しない＝
//   TransitionSkeletons と同じ 2026-07-29 裁定）／シマー等のアニメは付けない（最小の同型要素）。
//
// 適用しない状態: Empty（真に0件）・Error（真に失敗）。骨で覆うと「無い・失敗した」を隠すため、
//   これらは従来どおり畳んで status を出す（発見ホーム各実装の既存裁定を継承）。
// ============================================================

/** 骨領域の読み上げ文言。旧 status 行が担っていた支援技術への通知を、文字を描かない案A でも落とさない。 */
internal const val RankingSkeletonDescription = "読み込んでいます"

/**
 * 骨の行数。ホームランキングが実際に受け取る件数（[DiscoveryQuery] の limit 既定値）に合わせる。
 * なぜ定数を直書きせず既定値を引くか: ここが実データの件数より少ないと、差し替え時に結局高さが縮んで
 * アンカーを失う（本バグの再来）。取得件数が変わったとき静かにズレないよう単一情報源から引く。
 */
internal val RankingSkeletonRowCount: Int = DiscoveryQuery().limit

/**
 * 骨の題字幅の揺らぎ。TocTransitionSkeleton（案A の目次骨）の12値をそのまま写経する
 * ＝一覧の骨という同じ役どころに新しい比率を発明しない。行数がこれを超える場合は先頭へ巡回させる。
 */
private val RankingSkeletonTitleWidths =
    listOf(0.88f, 0.64f, 0.76f, 0.92f, 0.58f, 0.81f, 0.70f, 0.86f, 0.62f, 0.78f, 0.90f, 0.66f)

/**
 * ランキング一覧の構造スケルトン（[rowCount] 行ぶん）。
 *
 * 行の外形は [NovelListRow] の1行（順位カラム 34dp・縦 padding S16・題字/作者/メタの3行・下ヘアライン）に
 * 揃える＝実内容へ差し替わったとき区切り線と左端が跳ばない。順位数字・現在地などの内容依存要素は骨に出さない
 *（内容非依存の汎形に留める＝案A モックの note と同判断）。
 */
@Composable
internal fun RankingListSkeleton(
    modifier: Modifier = Modifier,
    rowCount: Int = RankingSkeletonRowCount,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // 骨は装飾＝TalkBack に空の箱を rowCount 個読ませない。領域としてひとこと名乗るだけにする
            //（K の気分ゴースト格子が clearAndSetSemantics で幻のカードを読ませないのと同じ扱い）。
            .clearAndSetSemantics { contentDescription = RankingSkeletonDescription },
    ) {
        repeat(rowCount) { index ->
            RankingRowSkeleton(
                titleWidthFraction = RankingSkeletonTitleWidths[index % RankingSkeletonTitleWidths.size],
            )
            // 実行と同じ区切り線（同色・同位置）＝差し替えでヘアラインが動かない。
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** 骨1行（[NovelListRow] の外形写し）。 */
@Composable
private fun RankingRowSkeleton(titleWidthFraction: Float) {
    // 文字行の骨は本棚骨と同じ hairline 1値（BookshelfSkeleton のリスト版が題字・進捗を全て lineColor で
    // 引くのと同じ。ランキング行には書影のような塊要素が無いので塊色は使わない）。
    val bone = LocalShelfColors.current.hairline
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.S16)) {
        // 順位数字（実行は幅 34dp・上 S4）の場所取り＝骨と実行で題字の左端を揃える。
        Box(modifier = Modifier.width(34.dp).padding(top = Spacing.S4)) {
            SkeletonBone(color = bone, modifier = Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            // 題字1行ぶん＝実行の lineHeight 21sp と同値の枠に棒を縦中央置き（sp→dp は等倍近似。
            // 骨は内容非依存の汎形ゆえ fontScale には追従しない＝TocTransitionSkeleton と同じ裁定）。
            BoneLine(color = bone, height = 21.dp, widthFraction = titleWidthFraction)
            // 作者・ジャンル行（実行は上 S4 ＋ FontLabel 11sp）／メタ行（上 S8 ＋ FontMicroLabel 10.5sp）。
            // 枠高 15dp＝字送りの近似（fontSize×1.4）。幅は実データでも揺れの小さい行ゆえ固定比。
            BoneLine(color = bone, height = 15.dp, widthFraction = 0.45f, topSpace = Spacing.S4)
            BoneLine(color = bone, height = 15.dp, widthFraction = 0.60f, topSpace = Spacing.S8)
        }
    }
}

/** 1行ぶんの枠（上余白＋行高）に棒を縦中央で置く。 */
@Composable
private fun BoneLine(
    color: Color,
    height: Dp,
    widthFraction: Float,
    topSpace: Dp = 0.dp,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topSpace)
            .height(height),
        contentAlignment = Alignment.CenterStart,
    ) {
        SkeletonBone(color = color, modifier = Modifier.fillMaxWidth(widthFraction))
    }
}
