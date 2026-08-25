package com.novelreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.novelreader.ui.theme.Insets
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
//
// ── 2026-08-26 裁定（ADR 0040・正本モック transition-skeleton-D.html を同日更新）──
// 本文骨の「粒度」と「版面」を詰めた。骨の型（案A）・語彙（棒11dp/角丸2dp/2値）・差し替え点は据え置き。
//  ①横書き＝案3「段落の呼吸まで」: 章題は2行折り返し前提で骨も2本／段落の行数を1〜4行でばらす／
//    段落末の余りを毎回ちがう長さに／短い会話行を混ぜる／面の下端まで埋める。
//  ②縦書き＝案V2「段落の切れ目まで」: 縦書き専用骨を新設し、2026-07-29 の「縦書き専用骨は作らない」を覆した
//    （それまでは縦書き設定でも横棒の骨が出て、着地の瞬間に紙面が90度変わっていた）。
//  ③クリアランス: 骨が実本文と同じ上下クリアランスを持つ（横書きのみ。縦書きは足さない＝各関数の why）。
//  ④骨の濃さ（コントラスト）は【保留＝現行値のまま】。他社13本の逆解析から一度は「ライト CR 1.27〜1.44 の
//    帯へ寄せる」と裁定したが、精査でその帯が〈骨↔背景／base↔highlight／lerp 両端〉と測る量の違う値の
//    混成＝比較不能と判明したため撤回した。統一した測り方だと他社実測（X）は 1.116/1.275/1.210 で、
//    現行値（LIGHT 1.093/1.243・DARK 1.087/1.336）と大差ない。**骨色は1ビットも触らない**。
//  ⑤shimmer は入れない（定数は他社から揃ったが ADR 0014 §C 禁止則「静謐」＝自動ループ禁止と衝突）。
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
 * 骨1本・縦組み（幅11dp・角丸2dp＝[SkeletonBone] を90度回したもの）。列高は modifier で与える。
 *
 * 横組みの棒と別関数にする理由: 骨の語彙（11dp・角丸2dp・2値塗り）は同じでも、固定される軸が
 * 高さ⇄幅で入れ替わるため。同じ関数に向きフラグを持たせると呼び出し側でどちらの軸が modifier 任せか
 * 読めなくなる（縦書き骨は列高を比率で与える＝ここが可変であることが要）。
 */
@Composable
internal fun SkeletonBoneVertical(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(11.dp)
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
 * 本文の遷移骨・横書き（案A の粒度＝案3「段落の呼吸まで」・2026-08-26 裁定＝ADR 0040）。
 * 段落棒を実本文の行リズム（fontSize×lineHeightEm）へ載せ、章見出し（話数・題2本・短いルール）の
 * 場所取りを添える。前書きブロック・ルビ・シーン区切りは章ごとに有無が変わるため骨に出さない
 *（内容非依存の汎形＝案4「紙面まるごと」を採らなかった核心。無い章では着地の瞬間に板や粒が消えて
 * 骨が嘘をつくため）。
 *
 * 縦書きは専用骨 [ReadingBodySkeletonVertical] へ分岐する（2026-08-26 に「縦書き専用骨は作らない」
 *（2026-07-29）を覆した＝ADR 0040。それまでは縦書き設定でも横棒が出て着地で紙面が90度変わっていた）。
 */
@OptIn(ExperimentalLayoutApi::class)
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
            // 上下バーのクリアランス（2026-08-26 追加）＝実本文 ChapterContent の contentPadding と同値。
            // なぜ骨が自分で持つ必要があるか: 上下バーはオーバーレイ描画（Scaffold の contentWindowInsets=0）で
            // 版面を1dpも削らないため、骨がこれを持たないと画面の最上端から描き始め、章見出しの骨が
            // ステータスバー／トップバーの裏へ入って見えない＝着地の瞬間に実見出しが約2行ぶん下から現れて跳ねる
            //（旧実装の実害。正本モックが上下バーを「レイアウトを食う兄弟」として描いていたため、モック検分でも
            // 素通りしていた＝2026-08-26 にモック側の模型ごと是正した）。
            // IgnoringVisibility を使う理由も実本文と同じ: 没入のバー出没で inset が 0⇄実測値に振れると版面が
            // リフローするため、「バーが在るときの寸法」で固定する。
            .padding(
                top = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding() +
                    Insets.ReadingBodyTopExtra,
                bottom = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding() +
                    Insets.ReadingBodyBottomExtra,
            )
            // 左右余白は実本文と同じユーザー設定値＝差し替えで棒→文字の左端が揃う。
            .padding(horizontal = bodyMarginDp.dp),
    ) {
        // 章見出しの場所取り（話数ラベル・題2本・短いルール）。棒の寸法はモック案3 実寸（72/288/126/48×2px）、
        // 縦間隔は S スケールへ丸め（20/12/8/18/28px → S16/S12/S8/S16/S24＝ADR 0014 スケール。HereBarD と同じ丸め裁定）。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.S16, bottom = Spacing.S24),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SkeletonBone(color = headBone, modifier = Modifier.width(72.dp))
            Spacer(modifier = Modifier.height(Spacing.S12))
            // 題は2行に折り返る前提で骨も2本（案3 ①）。実題の折り返し位置とは一致しない＝骨は内容非依存の汎形で、
            // 「章題がここに2行ぶん入る」という場所取りだけを担う。本文余白を最大（40dp）にすると 288dp は
            // 版面幅で頭打ちになるが、実題も同じ幅で折り返すので破綻はしない。
            SkeletonBone(color = headBone, modifier = Modifier.width(288.dp))
            Spacer(modifier = Modifier.height(Spacing.S8))
            SkeletonBone(color = headBone, modifier = Modifier.width(126.dp))
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
        // 段落棒＝モック案3 の7段落構成を写経。first=幅比率／second=段落頭（1em≒fontSize dp の字下げ）。
        // 案3 が案2（一様な棒列）から変えているのは4点だけで、いずれも「文章に見せる」ための操作:
        //  ①段落の行数を1〜4行でばらす ②段落末の余りを毎回ちがう長さに（38/64/44/78%）
        //  ③短い会話の1行段落（52/66%）を混ぜる ④面の下端まで埋める（続きがあるのは実本文と同じ）。
        // 全行が同一ピッチ＝段落間も行送り1つぶん（モック同値。段落間の空行は骨に入れない＝別途判断）。
        val lines = listOf(
            0.92f to true, 1f to false, 1f to false, 0.38f to false,
            0.52f to true,
            0.96f to true, 1f to false, 0.64f to false,
            0.88f to true, 0.44f to false,
            0.66f to true,
            0.94f to true, 1f to false, 1f to false, 0.78f to false,
            0.90f to true, 1f to false,
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

/**
 * 本文の遷移骨・縦書き（案V2「段落の切れ目まで」・2026-08-26 裁定＝ADR 0040）。
 *
 * 骨を90度立てて右→左に並べ、列送り・列高・左右余白・見出しの積み方を実本文
 * [com.novelreader.ui.VerticalChapterContent] と揃える。加えて段落の切れ目（字下げ・列末の余り・
 * 短い会話列）を骨でも見せる＝横書きの案3 と同じ操作を組方向へ読み替えたもの。
 *
 * なぜ 2026-07-29 の「縦書き専用骨は作らない」を覆したか: 汎形1種で通す取引の対価は「縦書き利用者だけが
 * 着地の瞬間に紙面の90度回転を見る」ことで、これは骨の目的（着地先の予告＝跳ねを消す）と正面から反する。
 * 分岐の実装コストも小さい（呼び出し側の if 1つ・差し替え点は縦横分岐の上流のまま）。
 * ⚠️ 他社13本の逆解析でも縦書きの骨は先例ゼロ（縦書き実装3本は骨を持たず、骨を持つ8本は全部横組み）＝
 * この形は借りずに決めた判断なので、根拠は ADR 0040 に残す。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReadingBodySkeletonVertical(
    colors: ReadingColors,
    fontSize: Int,
    lineHeightEm: Float,
    bodyMarginDp: Int,
    modifier: Modifier = Modifier,
) {
    val headBone = colors.blockBorder
    val paraBone = colors.blockBackground
    // 列送り＝実本文の columnAdvance（fontSize×lineHeightEm）。骨11dp を引いた残りが列と列の間隔
    //（横書きの行送りと同式＝組方向に依存しない原理）。下限 S8 の理由も横書きと同じ。
    val colGap = ((fontSize * lineHeightEm).dp - 11.dp).coerceAtLeast(Spacing.S8)
    // Rtl の Row: 先頭子が右端＝縦書きの読み順の起点（VerticalStyledBlock と同じ idiom。
    // Row は reverseLayout を持たないため、実本文の LazyRow(reverseLayout=true) に対応するのはこれ）。
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Row(
            modifier = modifier
                .fillMaxSize()
                // 上下＝システムバーのインセットのみ。⚠️ 横書きの ReadingBodyTopExtra/BottomExtra（64/80dp）は
                // 【足さない】。なぜか: 縦組みでは上下が交差軸＝全列の列高から恒久的に差し引かれ、横画面
                //（視野高 360dp）では列高が約4割まで潰れる（2026-07-17 実機実測・VerticalChapterContent の
                // contentPadding が同じ理由で足していない）。骨だけ足すと差し替えで列の上端・下端が動く。
                // 列はバーの下を素通りする＝横書きが本文行のバー下通過を許容するのと同じ取引。
                .padding(
                    top = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding(),
                    bottom = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues()
                        .calculateBottomPadding(),
                )
                // 左右＝読み進め方向の余白（実本文 contentPadding の start/end と同値 S24）。
                // 列の上下＝ユーザー設定の本文余白。実本文は各アイテムに掛けるが、骨は全列が同じ高さ基準なので
                // 版面へ一括で掛ける（見え方は同じで、列ごとに padding を積まないぶん遷移窓の描画が軽い）。
                .padding(horizontal = Spacing.S24, vertical = bodyMarginDp.dp),
        ) {
            // ── 章見出しの骨（右端＝先頭列）──
            // 外形は実 VerticalChapterHeader の Row（padding horizontal=S16・縦中央）と同値。内側は読み順
            //（右→左）に〈話数・題1列目・題2列目・藍ルール〉。間隔も実見出しの写経（ラベル→題 S8／題→ルール S16）。
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = Spacing.S16),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBoneVertical(color = headBone, modifier = Modifier.height(72.dp))
                Spacer(modifier = Modifier.width(Spacing.S8))
                SkeletonBoneVertical(color = headBone, modifier = Modifier.height(208.dp))
                Spacer(modifier = Modifier.width(Spacing.S8))
                // 題は2列に折り返る前提で骨も2本（横書き案3 の①を列へ読み替えたもの）。
                SkeletonBoneVertical(color = headBone, modifier = Modifier.height(150.dp))
                Spacer(modifier = Modifier.width(Spacing.S16))
                // 藍ルールの場所取り（2×48dp・角丸1dp）＝横書きの 48×2dp を90度回したもの。色は骨2値のまま。
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(48.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(headBone),
                )
            }
            // ── 段落の列（案V2）──
            // first=列高の比率（1f＝全高＝段落の途中で次の列へ続く）／second=段落頭（1em ぶん上端を下げる
            //＝縦書きの字下げ）。横書き案3 と同じ4操作の読み替え: 段落の列数をばらす／段落末の列は下端が余る
            //（38/64/47%）／短い会話の1列段落（34%）を混ぜる／面の左端まで埋める。
            val columns = listOf(
                1f to true, 1f to false, 1f to false, 0.38f to false,
                0.34f to true,
                1f to true, 1f to false, 0.64f to false,
                1f to true, 0.47f to false,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(colGap)) {
                columns.forEach { (heightFraction, indented) ->
                    SkeletonBoneVertical(
                        color = paraBone,
                        modifier = Modifier
                            .padding(top = if (indented) fontSize.dp else 0.dp)
                            .fillMaxHeight(heightFraction),
                    )
                }
            }
        }
    }
}
