package com.novelreader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp as lerpFloat
import com.novelreader.ui.skins.k.KTab
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.Skin
import com.novelreader.ui.theme.Spacing
import com.novelreader.ui.theme.tokens
import kotlin.math.absoluteValue

/**
 * UIスキン選択画面「装いの間（着せ替え）」。正本モック docs/design-candidates/skins/wardrobe-D.html の Compose 翻訳。
 *
 * 構図: ヘッダ（戻る＋題字＋サブ文）→ 本棚ミニチュアのコーバーフロー（中央=前面・両脇が覗く）→ ページドット →
 * 装着 CTA。中央に来た装いを CTA でアプリ全体へ適用する（入口は設定タブ「きせかえ」＝2026-07-29 に
 * 本棚の入口を撤去して移管・ADR 0021 追記。当初の「入口は本棚のみ」設計は K形正本追従で更新済み）。
 *
 * なぜ画面クローム（ヘッダ・地・ドット・CTA）を MaterialTheme.colorScheme そのままで描くか（プラン仕様1）:
 * 装着切替でアプリ全体の Theme が再構成され、この画面のクロームも即座に切り替わるのが正しい挙動（夜行を
 * 装着した瞬間クロームが深炭面へ即時反映＝モックの .phone.night）。ゆえにこの画面内でテーマを組み直さず、
 * 親 [NovelReaderTheme] の再構成に委ねる。各スキンの「素の姿」はミニチュアだけがそのスキン自身のトークンで描く。
 */
@Composable
fun WardrobeScreen(
    currentSkin: Skin,
    onSkinChange: (Skin) -> Unit,
    onBack: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    // ページ = 全スキン＋末尾に「今後追加」カード（並び順は Skin.entries＝明快K→和モダンD→夜行C→星図M→
    // カートリッジP→ポータルJ。「今後追加」は常に末尾）。
    val skins = Skin.entries
    val addPageIndex = skins.size
    val pageCount = skins.size + 1

    // 入場時は装着中スキンを中央に置く（自分の今の装いから見せ始める）。
    val pagerState = rememberPagerState(
        initialPage = skins.indexOf(currentSkin).coerceAtLeast(0),
        pageCount = { pageCount },
    )

    // 1スワイプ=中央から1枚だけ動かすための fling 差し替え（実機: 高速フリングで着せ替え先を行き過ぎる不具合の是正）。
    // なぜ既定 flingBehavior では不足か: 既定の PagerSnapDistance.atMost(1) は着地ページを firstVisiblePage 基準で
    // ±1へ丸める。だが本画面は左右に隣カードを覗かせる contentPadding 構図のため firstVisiblePage は視覚的中央カード
    //（currentPage）の1つ手前＝左の覗きカードになり、丸めの基準が視覚的中央から1枚ずれる。結果、少なくとも一方向の
    // 高速フリングで中央から2枚先へ着地しうる＝目的のスキンを行き過ぎ、着せ替え先の選択精度が落ちる。
    // 対策は速度を殺すハックではなく丸めの基準点の是正: 視覚的中央 currentPage を基準に着地を ±1 へ厳密制限する。
    val singleStepSnap = remember(pagerState) {
        object : PagerSnapDistance {
            override fun calculateTargetPage(
                startPage: Int,
                suggestedTargetPage: Int,
                velocity: Float,
                pageSize: Int,
                pageSpacing: Int,
            ): Int = clampWardrobeFlingTarget(pagerState.currentPage, suggestedTargetPage)
        }
    }
    // decay/snap の質感は既定のまま（PagerDefaults 経由）＝丸め基準だけを差し替える最小介入。
    val stepFlingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        pagerSnapDistance = singleStepSnap,
    )

    // 戻るボタンとシステムバックの着地を一致させる（サブ画面ゆえハード戻るも onBack へ流す）。
    // PredictiveBackHandler にしない理由: onBack＝NavHost pop で、戻り演出は遷移側（slide push 逆再生・
    // ADR 0019）が担う。navigation-compose 2.7.5 に進捗連動 pop は無く、置換しても駆動できる面が無い。
    BackHandler { onBack() }

    Scaffold(containerColor = scheme.background) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            // ── ヘッダ（モック .whead / .wtitle / .wsub）─────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.S8, top = Spacing.S8, end = Spacing.S24, bottom = Spacing.S4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.S16),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "戻る",
                        tint = scheme.onSurface,
                    )
                }
                Text(
                    text = "着せ替え",
                    fontFamily = MinchoFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 22.sp,
                    // letterSpacing 0.1em 相当（em=フォントサイズ＝22sp×0.1）。エディトリアルな字間広めの題字。
                    letterSpacing = 2.2.sp,
                    color = scheme.onSurface,
                )
            }
            Text(
                text = "装いはアプリ全体に適用されます",
                fontSize = 11.sp,
                letterSpacing = 0.44.sp, // モック .wsub letter-spacing .04em（11sp×0.04）
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.S24),
            )
            Spacer(Modifier.height(Spacing.S24)) // .wsub margin-bottom:24px

            // ── コーバーフロー（モック .stage2）─────────────────────────────────────
            // なぜ HorizontalPager × contentPadding × pageSpacing で覗きを作るか（プラン仕様3の実装方式）:
            // カード幅 180dp・中心間距離 150dp が正本。HorizontalPager の既定ページ幅は「ビューポート幅−
            // 左右 contentPadding」。左右パディングを (画面幅−180dp)/2 に取ると各ページがちょうど 180dp 幅で
            // 中央寄せになり、両隣が画面端で覗く。pageSpacing を −30dp にすると隣ページが中央側へ 30dp 潜り、
            // 中心間 180−30=150dp を満たす（余白ではなく合成オフセット＝モック .slot の ±150px 相当）。
            // 中央/両脇の scale・alpha はページオフセットから graphicsLayer で連続補間する（下記）。
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                val sidePadding = ((maxWidth - CardWidth) / 2).coerceAtLeast(0.dp)
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = sidePadding),
                    pageSpacing = (-30).dp,
                    // 中央カード基準の1枚 snap（上の singleStepSnap の「なぜ」を参照）。
                    flingBehavior = stepFlingBehavior,
                    // 隣ページの scale アニメが常に描かれるよう前後1ページを先行コンポーズする。
                    beyondViewportPageCount = 1,
                    verticalAlignment = Alignment.CenterVertically,
                ) { page ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .graphicsLayer {
                                // 中央=1.0、両隣=scale .85/alpha .6 をページオフセットから連続補間（モック .slot.center/left/right）。
                                // graphicsLayer 内で pager 状態を読む＝コンポジションでなく描画フェーズの遅延読み取りで
                                // スワイプ追従を再コンポーズなしに行う（chrisbanes state-deferred-reads）。
                                val offset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                                    .absoluteValue.coerceIn(0f, 1f)
                                val s = lerpFloat(1f, 0.85f, offset)
                                scaleX = s
                                scaleY = s
                                alpha = lerpFloat(1f, 0.6f, offset)
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val skin = skins.getOrNull(page)
                        // カード（本棚ミニチュア）。影は box-shadow 0 14 34 相当（add カードは box-shadow:none）。
                        Box(
                            modifier = Modifier
                                .width(CardWidth)
                                .height(MiniatureHeight)
                                .then(
                                    if (skin != null) {
                                        Modifier.shadow(elevation = 14.dp, shape = RoundedCornerShape(14.dp))
                                    } else {
                                        Modifier
                                    },
                                )
                                .clip(RoundedCornerShape(14.dp)),
                        ) {
                            if (skin != null) {
                                SkinMiniature(skin = skin, modifier = Modifier.fillMaxSize())
                            } else {
                                AddMiniature(soft = scheme.onSurfaceVariant, modifier = Modifier.fillMaxSize())
                            }
                        }
                        Spacer(Modifier.height(Spacing.S16)) // .cname margin-top:16px
                        // カード下の名前（スキン名）。add カードは名前欄空だが高さは確保して構図を揃える。
                        Text(
                            text = skin?.displayName ?: " ",
                            fontFamily = MinchoFamily,
                            fontSize = 16.sp,
                            color = scheme.onBackground,
                            textAlign = TextAlign.Center,
                        )
                        if (skin != null) {
                            Spacer(Modifier.height(Spacing.S4)) // .cone margin-top:4px
                            Text(
                                text = skin.tagline,
                                fontSize = 11.sp, // モック .cone 10.5px → sp スケール上は 11sp で近似（副文）
                                color = scheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            // ── ページドット（モック .dots）＋ CTA（モック .ctawrap）───────────────────
            val current = pagerState.currentPage
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.S24),
                horizontalArrangement = Arrangement.spacedBy(Spacing.S8, Alignment.CenterHorizontally),
            ) {
                repeat(pageCount) { i ->
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(if (i == current) scheme.primary else scheme.outlineVariant),
                    )
                }
            }

            // CTA はページ追従（中央のカードに対して切り替わる）。今後追加ページでは CTA を出さないが、
            // 高さは確保して切替時に画面が跳ねないようにする（プラン仕様6）。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CtaAreaHeight)
                    .padding(bottom = Spacing.S8),
                contentAlignment = Alignment.Center,
            ) {
                val skin = skins.getOrNull(current)
                when {
                    skin == null -> Unit // 今後追加＝CTA 非表示（スペースは上の height で保持）
                    skin == currentSkin -> {
                        // 装着中＝塗りなし・ヘアライン枠・チェック（今の装いであることを静かに示す）。
                        // タップは「これでよい」という確定＝そのまま本棚へ戻す（applyWardrobeSelection の裁定参照）。
                        OutlinedButton(
                            onClick = { applyWardrobeSelection(skin, currentSkin, onSkinChange, onBack) },
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outline),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.onBackground),
                            contentPadding = PaddingValues(horizontal = Spacing.S32, vertical = Spacing.S16),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = scheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(Spacing.S8))
                            Text("装着中", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    else -> {
                        // 未装着＝プライマリ塗り。タップで装着を永続化し、そのまま本棚へ戻る（装着状態で表示される）。
                        // 順序保証は applyWardrobeSelection が担う（onSkinChange 完了後に onBack）。
                        Button(
                            onClick = { applyWardrobeSelection(skin, currentSkin, onSkinChange, onBack) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = scheme.primary,
                                contentColor = scheme.onPrimary,
                            ),
                            contentPadding = PaddingValues(horizontal = Spacing.S32, vertical = Spacing.S16),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(Spacing.S8))
                            Text("これを装着", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 装いの間カルーセルのフリング着地ページを「中央カード ±1」へ制限する純関数（テスト可能な核）。
 *
 * なぜ currentPage 基準か: Compose 既定の PagerSnapDistance.atMost(1) は firstVisiblePage 基準で丸めるが、
 * 覗きカードのある contentPadding 構図では firstVisiblePage が視覚的中央から1枚ずれ、高速フリングで
 * 中央から2枚先へ着地しうる（詳細は WardrobeScreen の flingBehavior コメント）。ここで視覚的中央
 * currentPage を基準に ±1 clamp へ据え直すことで、1スワイプ=中央から1枚だけの移動を保証する。
 */
internal fun clampWardrobeFlingTarget(currentPage: Int, suggestedTargetPage: Int): Int =
    suggestedTargetPage.coerceIn(currentPage - 1, currentPage + 1)

/**
 * 装いの間の装着 CTA タップを「装着の永続化 → 本棚へ戻る」の確定順で実行する純関数（テスト可能な核）。
 *
 * なぜ順序を関数へ固定するか（要件＝押した直後に装着状態で本棚へ戻す）:
 * onSkinChange は app_skin の永続化を**同期的に**完了させる（MainActivity 側で状態巻き上げ appSkin=skin ＋
 * prefs.putString を同一呼び出し内で行う。SharedPreferences.apply() のディスク書き込みは非同期だがメモリ上の
 * 値はその場で更新され、以後のプロセス内読み取りに反映される）。ゆえに onSkinChange の呼び出しが戻った時点で
 * 装着はアプリ状態として確定しており、続けて onBack を呼べば本棚は更新済み appSkin で再コンポーズされる＝
 * 「装着が効いてから戻る」を追加の非同期待ちなしに保証できる。分岐と呼び出し順をこの関数へ集約し単体テストで固定する。
 *
 * 既装着スキンの「装着中」タップの裁定＝即戻る（onSkinChange は呼ばず onBack のみ）:
 * この画面の唯一の役割は「装いを選んで本棚へ帰る」。既に装着中なら選び直す必要はなく、タップは「これでよい」の
 * 確定の意思表示と解せる。大きな CTA を無反応にすると押しても何も起きず迷子になるため、戻す方が自然で全 CTA の
 * 挙動（タップ＝本棚へ戻る）も一貫する。同一スキンへの無駄な再永続化・再コンポーズも避けられる。
 */
internal fun applyWardrobeSelection(
    tappedSkin: Skin,
    currentSkin: Skin,
    onSkinChange: (Skin) -> Unit,
    onBack: () -> Unit,
) {
    if (tappedSkin != currentSkin) {
        onSkinChange(tappedSkin) // 先に装着を永続化してから
    }
    onBack() // 本棚へ戻る（装着済みの姿で表示される）
}

// カード寸法・角丸はモック値そのまま（ADR 0014 §C の余白スケール対象外＝寸法/角丸は px 追従で可）。
private val CardWidth = 180.dp
private val MiniatureHeight = 300.dp
// CTA 領域の固定高（今後追加ページで CTA を隠しても画面が跳ねないための予約高＝ボタン実寸を包む）。
private val CtaAreaHeight = 60.dp

// ============================================================
// 本棚ミニチュア（案E「全スキン実画面縮図」）
// 正本 docs/design-candidates/skins/wardrobe-D.html の Compose 翻訳。
// ============================================================

/**
 * 本棚ミニチュア（スキンごとの縮図）。**そのスキン自身のトークン**で描く＝各装いの「素の姿」を並べる。
 *
 * 案E＝**6枚を各スキンの署名構造で描き分ける**（2026-08-26 裁定・比較ドラフト
 * `docs/design-candidates/skins/wardrobe-miniature-compare.html` の A〜E から採択）。
 * 旧実装（案A）は全スキンが同じ抽象縮図でトークンだけ差し替える形だったが、明快K は
 * `SkinK : SkinTokens by SkinD` でトークンを D へ全委譲しているため **K と D の縮図がピクセル等価**になり、
 * カード下の名前を読むまで区別がつかなかった。スキンは「全く別のアプリ」級（2026-07-17 最上位原則）であり、
 * 装いの間は課金要素＝ここで手を抜かない、というユーザー方針で案E を採った。
 *
 * **なぜ「6つを丸ごとコピペ」にも「共通1つにパラメータ追加」にもしない構造か（恒久負債の管理点）**:
 * 縮図の中身は〈全スキン共通の事実〉と〈そのスキン固有の署名〉が混在している。前者——恒常ボトムナビ3タブ
 *（MainActivity が全スキンへ KBottomNav を搭載）・見出し行・状態チップ・書影グリッド・FAB——は
 * [MiniNav]／[MiniHead]／[MiniChips]／[MiniGrid]／[MiniFab] の共有パーツに1つずつ畳み、後者だけを
 * `MiniShelf*` 6関数が持つ。共通側を1箇所で直せる（＝コピペ6枚の負債を負わない）まま、
 * 「どこが署名か」がファイル上で6関数として名指されている（＝共通1つにフラグを足す解と違い、
 * 署名構造が when 分岐として可視化される）。下の `when` は網羅式なので、
 * **スキンを1つ足すとコンパイルエラーで縮図の設計を必ず要求される**——これが案E の恒久負債
 *（本棚の意匠を変えるたび縮図も追う）を「忘れて腐る」形にしないための構造的な歯止め。
 *
 * トークンの引き方: そのスキンの既定変種 `supportedThemes.first()` で解決した [MiniPalette] を各パーツへ渡す
 *（1カード1回だけ解決＝再コンポーズごとの再計算を避ける）。
 *
 * **なぜ縮図の中だけ fontScale を 1 に固定するか**: このカードは 180×300dp 固定の「**別画面の絵**」であって
 * 読ませる文字ではない。OS の文字拡大に追従させると、実画面なら版面が組み替わるところが縮図では
 * 絵が溢れて壊れるだけになる（案E は案A より情報量が多く、2.0 では書影グリッド最終行とナビのラベルが落ちる）。
 * 可読・読み上げの責務はカード下の装い名とタグライン（通常どおり sp 追従）が負うので、絵の側は固定でよい。
 */
@Composable
private fun SkinMiniature(skin: Skin, modifier: Modifier) {
    val palette = remember(skin) { miniPalette(skin) }
    val density = LocalDensity.current
    val fixedScale = remember(density) { Density(density = density.density, fontScale = 1f) }
    CompositionLocalProvider(LocalDensity provides fixedScale) {
        Box(modifier.background(palette.scheme.background)) {
            when (skin) {
                Skin.MEIKAI_K -> MiniShelfK(palette)
                Skin.WAMODERN_D -> MiniShelfD(palette)
                Skin.YAKO_C -> MiniShelfC(palette)
                Skin.SEIZU_M -> MiniShelfM(palette)
                Skin.CARTRIDGE_P -> MiniShelfP(palette)
                Skin.PORTAL_J -> MiniShelfJ(palette)
            }
        }
    }
}

/**
 * 1スキンぶんの縮図パレット。**署名色を2本持つ**のが要点:
 * - [chrome] = `material.primary`。FAB・塗りチップ・ナビ選択＝**実装がその色を引いている箇所**の写し
 *   （[com.novelreader.ui.skins.k.KBottomNav] は選択 tint に primary、ピルに primary@10% を使う）。
 * - [accent] = `SkinTokens.signatureAccent`。進捗線・星・扉の敷居＝**本棚本体の意匠**の署名。
 * ⚠️ 2本を分ける理由はカートリッジP: signatureAccent が液晶グリーンで、これは LCD 帯が専有する。
 *   FAB/ナビ選択にも同じ緑を使うと LCD の署名性が消えるため、そちらは CTA 色（primary＝退色レッド）が正しい。
 *   他の5スキンでは primary と signatureAccent が同値なので、この分岐は P だけに効く。
 */
@Immutable
private data class MiniPalette(
    val scheme: ColorScheme,
    val chrome: Color,
    val accent: Color,
    val hairline: Color,  // ShelfColors.hairline＝本棚本体の罫（D/K は outlineVariant と別値）
    val cover: Color,
    val heroLine: Color,
    val listLine: Color,
    val navTint: Color,
)

private fun miniPalette(skin: Skin): MiniPalette {
    val tokens = skin.tokens
    val theme = tokens.supportedThemes.first()
    val scheme = tokens.material(theme)
    return MiniPalette(
        scheme = scheme,
        chrome = scheme.primary,
        accent = tokens.signatureAccent,
        hairline = tokens.shelf(theme).hairline,
        // 書影チップの色: モックは装飾グラデだが新規16進の直書きは禁止（ADR 0014）。
        // なぜ lerp 導出か: primary→onBackground の中間へ寄せると「本文色に近い落ち着いた面」になり、
        // スキンの地に馴染む縮図の書影として成立する（装飾グラデをトークン外の生色で作らないための近似）。
        cover = lerpColor(scheme.primary, scheme.onBackground, 0.35f),
        heroLine = scheme.onBackground.copy(alpha = 0.20f),
        listLine = scheme.onBackground.copy(alpha = 0.16f),
        navTint = scheme.primary.copy(alpha = 0.10f), // KBottomNav の選択ピル実装値
    )
}

// ── 6つの署名構造 ──────────────────────────────────────────────────────────
// 各関数が持つのは「その装いにしか無いもの」だけ。共通のクロームは共有パーツを呼ぶ。

/** 明快K: 2列×3段の書影グリッド（6冊）＋**塗り**チップ＋FAB。情報量を出して「これが標準」を示す。 */
@Composable
private fun BoxScope.MiniShelfK(p: MiniPalette) {
    Column(Modifier.fillMaxSize()) {
        MiniHead(p, mincho = false)
        MiniChips(p, filledSelection = true)
        MiniGrid(p, rows = 3, coverHeight = 50.dp, titleLines = 2, rowGap = Spacing.S8)
        Spacer(Modifier.weight(1f))
        MiniNav(p)
    }
    MiniFab(p)
}

/** 和モダンD: 同じ2列でも4冊・書影を大きく・行間を広く＝**余白**が署名。見出しは明朝・チップは**枠**。 */
@Composable
private fun BoxScope.MiniShelfD(p: MiniPalette) {
    Column(Modifier.fillMaxSize()) {
        MiniHead(p, mincho = true)
        MiniChips(p, filledSelection = false)
        MiniGrid(p, rows = 2, coverHeight = 60.dp, titleLines = 1, rowGap = Spacing.S12)
        Spacer(Modifier.weight(1f))
        MiniNav(p)
    }
    MiniFab(p)
}

/**
 * 夜行C: 見出しもチップも FAB も持たない（**クロームを極小に**）。代わりに「続きから」を大きく1枚。
 * 姿の正本は `docs/design-candidates/skins/bookshelf-C.html`（実装との差は wardrobe-D.html の未反映メモ）。
 */
@Composable
private fun BoxScope.MiniShelfC(p: MiniPalette) {
    Column(Modifier.fillMaxSize()) {
        // 「続きから」ヒーロー（モック .e-hero）。margin-top 14px → S16（等距離 12↔16 は大きい側・ADR 0014 §C）。
        Column(Modifier.fillMaxWidth().padding(start = Spacing.S12, end = Spacing.S12, top = Spacing.S16)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(p.scheme.surfaceContainer)
                    .border(1.dp, p.hairline, RoundedCornerShape(6.dp))
                    .padding(Spacing.S8),
            ) {
                MiniText("続きから", 7.5f, p.accent, weight = FontWeight.Bold, letterSpacing = 1.05.sp)
                Spacer(Modifier.height(Spacing.S8))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.S8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(width = 30.dp, height = 44.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(p.cover),
                    )
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Spacing.S4),
                    ) {
                        MiniLine(0.88f, 4.dp, p.heroLine)
                        MiniLine(0.60f, 4.dp, p.heroLine)
                        MiniLine(0.38f, 3.dp, p.accent) // 灯火の進捗線＝C の署名色
                    }
                }
            }
        }
        // 目録2行だけ（モック .e-list）。行数を絞ることで「ヒーローが主役」の構図を保つ。
        Column(Modifier.fillMaxWidth().padding(start = Spacing.S12, end = Spacing.S12, top = Spacing.S8)) {
            listOf(0.88f to 0.50f, 0.74f to 0.44f).forEach { (w1, w2) ->
                HorizontalDivider(color = p.hairline)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = Spacing.S4),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.S8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(width = 16.dp, height = 23.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(p.cover),
                    )
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Spacing.S4),
                    ) {
                        MiniLine(w1, 3.dp, p.listLine)
                        MiniLine(w2, 3.dp, p.listLine)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        MiniNav(p)
    }
}

/**
 * 星図M: 3つの星座（星＋結線＋銘）が夜天に散る。**グリッドも罫線も持たない**のが署名。
 * 星は Canvas で一筆に描く（要素を星の数だけ積むとレイアウトノードが無駄に増えるため）。
 */
@Composable
private fun BoxScope.MiniShelfM(p: MiniPalette) {
    Column(Modifier.fillMaxSize()) {
        MiniHead(p, mincho = true)
        Box(Modifier.fillMaxWidth().weight(1f).padding(top = Spacing.S8)) {
            Canvas(Modifier.fillMaxSize()) {
                MiniConstellations.forEach { c ->
                    val points = c.stars.map { Offset((c.ox + it.x).dp.toPx(), (c.oy + it.y).dp.toPx()) }
                    // 結線＝隣り合う星を順に結ぶ（モックは rotate 指定だが、端点は星の中心と一致する）。
                    for (i in 0 until points.lastIndex) {
                        drawLine(
                            color = p.accent.copy(alpha = 0.5f),
                            start = points[i],
                            end = points[i + 1],
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                    c.stars.forEachIndexed { i, star ->
                        drawCircle(p.accent, radius = (star.diameter / 2f).dp.toPx(), center = points[i])
                    }
                }
            }
            MiniConstellations.forEach { c ->
                MiniText(
                    text = c.label,
                    sizeSp = 6.5f,
                    color = p.scheme.onBackground,
                    letterSpacing = 0.39.sp, // .06em × 6.5sp
                    modifier = Modifier.offset(x = (c.ox + c.labelX).dp, y = (c.oy + c.labelY).dp),
                )
            }
        }
        MiniNav(p)
    }
}

/** カートリッジP: 緑LCD の再生表示＋単列に挿さるカセット3本。**棚でなく「機械の面」**が署名。 */
@Composable
private fun BoxScope.MiniShelfP(p: MiniPalette) {
    Column(Modifier.fillMaxSize()) {
        MiniHead(p, mincho = false)
        // LCD 帯（モック .e-lcd）。地=tertiary（--lcd）・字=onTertiary（--lcd-ink）＝SkinP が持つ対のトークン。
        Column(Modifier.fillMaxWidth().padding(start = Spacing.S12, end = Spacing.S12, top = Spacing.S8)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(3.dp))
                    .background(p.scheme.tertiary)
                    .border(1.dp, p.hairline, RoundedCornerShape(3.dp))
                    .padding(Spacing.S8),
            ) {
                MiniText(
                    "NOW PLAYING", 6.5f, p.scheme.onTertiary,
                    weight = FontWeight.Bold, letterSpacing = 1.04.sp, // .16em × 6.5sp
                )
                Spacer(Modifier.height(Spacing.S4))
                MiniText("辺境の魔導 12/48", 8f, p.scheme.onTertiary, weight = FontWeight.Bold)
            }
        }
        // カセット3本（モック .e-cas）。筐体=surfaceVariant（--panel）・リブ溝=本棚ヘアライン。
        Column(
            Modifier.fillMaxWidth().padding(start = Spacing.S12, end = Spacing.S12, top = Spacing.S8),
            verticalArrangement = Arrangement.spacedBy(Spacing.S8),
        ) {
            repeat(3) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(p.scheme.surfaceVariant)
                        .border(1.dp, p.hairline, RoundedCornerShape(3.dp))
                        .padding(horizontal = Spacing.S8),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.S8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(width = 20.dp, height = 18.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(p.cover),
                    )
                    Row(
                        Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.S4),
                    ) {
                        repeat(5) {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(p.hairline),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        MiniNav(p)
    }
}

/** ポータルJ: 作品が**扉**として立つ。中央の大扉と両脇の覗き＋金の敷居（下辺2dp）が署名。 */
@Composable
private fun BoxScope.MiniShelfJ(p: MiniPalette) {
    Column(Modifier.fillMaxSize()) {
        MiniHead(p, mincho = true)
        Row(
            Modifier.fillMaxWidth().weight(1f).padding(start = Spacing.S8, end = Spacing.S8, top = Spacing.S8),
            horizontalArrangement = Arrangement.spacedBy(Spacing.S8, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiniDoor(p, width = 34.dp, height = 82.dp, glyph = "閉", glyphSp = 13f, dim = true)
            MiniDoor(p, width = 74.dp, height = 106.dp, glyph = "物", glyphSp = 22f, dim = false)
            MiniDoor(p, width = 34.dp, height = 82.dp, glyph = "扉", glyphSp = 13f, dim = true)
        }
        MiniNav(p)
    }
}

/** 扉1枚（上だけ角丸・下辺が金の敷居・面取りした奥へ象徴の1文字）。 */
@Composable
private fun MiniDoor(p: MiniPalette, width: Dp, height: Dp, glyph: String, glyphSp: Float, dim: Boolean) {
    val shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
    Box(
        Modifier
            .size(width = width, height = height)
            .alpha(if (dim) 0.55f else 1f) // 両脇は覗き＝奥へ引く
            .clip(shape)
            .background(p.scheme.surfaceContainer)
            .border(1.dp, p.hairline, shape),
        contentAlignment = Alignment.Center,
    ) {
        MiniText(glyph, glyphSp, p.accent.copy(alpha = 0.9f), mincho = true)
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(2.dp)
                .background(p.accent), // 金の敷居
        )
    }
}

// ── 共有パーツ（全スキン共通の事実。ここを直せば6枚すべてに効く） ────────────────

/**
 * 画面上部の見出し（本棚 ＋ 冊数 ＋ 表示切替）。夜行C だけ持たない＝極小クロームが C の署名。
 * @param mincho 題字を明朝で組むか（**K=ゴシック太字／D・C系=明朝**＝K と D の別れ目その1）。
 */
@Composable
private fun MiniHead(p: MiniPalette, mincho: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.S12, end = Spacing.S12, top = Spacing.S12),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.S4),
            verticalAlignment = Alignment.Bottom,
        ) {
            MiniText(
                text = "本棚",
                sizeSp = 12f,
                color = p.scheme.onBackground,
                mincho = mincho,
                weight = if (mincho) FontWeight.Medium else FontWeight.Bold,
                letterSpacing = if (mincho) 1.2.sp else 0.36.sp, // .1em / .03em × 12sp
            )
            MiniText("12冊", 8.5f, p.scheme.onSurfaceVariant, weight = FontWeight.SemiBold)
        }
        // 表示切替（グリッド⇄リスト）のアイコン枠。縮図では字の読めない小片なので四角で示す。
        Box(
            Modifier
                .size(12.dp)
                .border(1.dp, p.scheme.onSurfaceVariant.copy(alpha = 0.7f), RoundedCornerShape(2.dp)),
        )
    }
}

/**
 * 状態チップ行（すべて／よみかけ／未読）。
 * @param filledSelection 選択チップを塗るか（**K=塗り／D=枠**＝K と D の別れ目その2）。
 */
@Composable
private fun MiniChips(p: MiniPalette, filledSelection: Boolean) {
    val shape = RoundedCornerShape(percent = 50)
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.S12, end = Spacing.S12, top = Spacing.S8),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S4),
    ) {
        listOf("すべて", "よみかけ", "未読").forEachIndexed { index, label ->
            val selected = index == 0
            Box(
                Modifier
                    .height(12.dp)
                    .clip(shape)
                    .background(if (selected && filledSelection) p.chrome else Color.Transparent)
                    .border(1.dp, if (selected) p.chrome else p.hairline, shape)
                    .padding(horizontal = Spacing.S4),
                contentAlignment = Alignment.Center,
            ) {
                MiniText(
                    text = label,
                    sizeSp = 7f,
                    color = when {
                        selected && filledSelection -> p.scheme.onPrimary
                        selected -> p.chrome
                        else -> p.scheme.onSurfaceVariant
                    },
                    weight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/**
 * 書影グリッド（2列固定）。
 * @param rows 段数（**K=3段6冊の密／D=2段4冊のゆとり**＝K と D の別れ目その3）。
 * @param coverHeight 書影の高さ（D は大きく取る）。
 * @param titleLines 書影下の題字線の本数。
 */
@Composable
private fun MiniGrid(p: MiniPalette, rows: Int, coverHeight: Dp, titleLines: Int, rowGap: Dp) {
    Column(
        Modifier.fillMaxWidth().padding(start = Spacing.S12, end = Spacing.S12, top = Spacing.S8),
        verticalArrangement = Arrangement.spacedBy(rowGap),
    ) {
        repeat(rows) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.S8),
            ) {
                repeat(2) {
                    Column(Modifier.weight(1f)) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(coverHeight)
                                .clip(RoundedCornerShape(3.dp))
                                .background(p.cover),
                        ) {
                            // 栞の背表紙（K/D の書影＝ShioriCover の識別色棒の縮図）。
                            Box(
                                Modifier
                                    .padding(start = Spacing.S4, top = Spacing.S4)
                                    .size(width = 2.dp, height = 14.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(p.accent),
                            )
                        }
                        repeat(titleLines) { line ->
                            Spacer(Modifier.height(Spacing.S4))
                            MiniLine(if (line == 0) 0.86f else 0.52f, 3.dp, p.listLine)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 恒常ボトムナビ3タブ。**全6枚に載せる**——これは K 固有ではなくアプリ共通の事実で
 *（[com.novelreader.ui.skins.k.KBottomNav] は NavHost の外・スキン分岐の外で全スキンに描かれる）、
 * 一部のスキンにだけ描くと縮図が嘘になる。語彙（アイコン・ラベル・並び）は実ナビの [KTab] から直接引くので、
 * タブが増減・改名しても縮図が自動で追従する。
 */
@Composable
private fun MiniNav(p: MiniPalette) {
    HorizontalDivider(color = p.scheme.outlineVariant) // 実ナビの上罫も outlineVariant
    Row(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(p.scheme.surface), // 実ナビの帯面は surface（全スキンで background と同値）
        verticalAlignment = Alignment.CenterVertically, // 22dp の中身を 34dp の帯の中央へ
    ) {
        KTab.entries.forEach { tab ->
            val selected = tab == KTab.BOOKSHELF // 縮図は常に本棚を現在地として見せる
            val tint = if (selected) p.chrome else p.scheme.onSurfaceVariant
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.size(width = 26.dp, height = 14.dp), contentAlignment = Alignment.Center) {
                    if (selected) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .clip(RoundedCornerShape(7.dp))
                                .background(p.navTint), // 実ナビの選択ピル（primary@10%）
                        )
                    }
                    Icon(tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
                }
                MiniText(
                    text = tab.label,
                    sizeSp = 6.5f,
                    color = tint,
                    weight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/**
 * 取り込み FAB（＋PDF）。**K と D だけ**が持つ＝取り込み動線を前面に出す2スキンの署名
 *（M は地平の「新しい星を迎える」・P はスロットの空き枠・J は⋮メニューへ畳んでいる）。
 * 下余白はモック bottom:44px → 余白スケールの S40（ナビ帯 34dp のすぐ上に浮く）。
 */
@Composable
private fun BoxScope.MiniFab(p: MiniPalette) {
    Box(
        Modifier
            .align(Alignment.BottomEnd)
            .padding(end = Spacing.S8, bottom = Spacing.S40)
            .height(17.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(p.chrome)
            .padding(horizontal = Spacing.S8),
        contentAlignment = Alignment.Center,
    ) {
        MiniText("＋PDF", 7f, p.scheme.onPrimary, weight = FontWeight.Bold)
    }
}

/** ミニチュア内の抽象テキスト線（実文字でなく Box の線で縮図表現＝モックの i 要素）。 */
@Composable
private fun MiniLine(widthFraction: Float, height: Dp, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(2.dp))
            .background(color),
    )
}

/**
 * ミニチュア内の実文字。**lineHeight を必ず明示する**のが要点: 未指定だと MaterialTheme が既定として流す
 * bodyLarge の行送り（16sp 本文用の 28sp）を 7sp の微小文字まで相続し、行ボックスだけが本文並みに膨らんで
 * 300dp のカードから内容が溢れる（KBottomNav の帯高で実際に踏んだのと同じ罠）。
 */
@Composable
private fun MiniText(
    text: String,
    sizeSp: Float,
    color: Color,
    modifier: Modifier = Modifier,
    mincho: Boolean = false,
    weight: FontWeight = FontWeight.Normal,
    letterSpacing: TextUnit = TextUnit.Unspecified,
) {
    Text(
        text = text,
        modifier = modifier,
        fontFamily = if (mincho) MinchoFamily else null,
        fontSize = sizeSp.sp,
        lineHeight = (sizeSp * 1.2f).sp,
        fontWeight = weight,
        letterSpacing = letterSpacing,
        color = color,
        maxLines = 1,
    )
}

/** 星図M の星座1つ（銘＝作品名の位置は星列と重ならないよう正本モックの実値をそのまま持つ）。 */
@Immutable
private data class MiniConstellation(
    val ox: Float,
    val oy: Float,
    val stars: List<MiniStar>,
    val label: String,
    val labelX: Float,
    val labelY: Float,
)

/** 星1つ（[diameter] は dp。モック .cst b の width/height と同値）。 */
@Immutable
private data class MiniStar(val x: Float, val y: Float, val diameter: Float)

// 正本モック wardrobe-D.html .e-sky の実値（星座の起点・星の位置と大きさ・銘の位置）。
private val MiniConstellations = listOf(
    MiniConstellation(
        ox = 16f, oy = 14f,
        stars = listOf(MiniStar(0f, 0f, 4f), MiniStar(30f, 15f, 3f), MiniStar(50f, -1f, 5f)),
        label = "星の継承者", labelX = -2f, labelY = 12f,
    ),
    MiniConstellation(
        ox = 96f, oy = 76f,
        stars = listOf(MiniStar(0f, 0f, 3f), MiniStar(21f, 21f, 4.5f), MiniStar(44f, 13f, 3f)),
        label = "辺境の魔導", labelX = -6f, labelY = 32f,
    ),
    MiniConstellation(
        ox = 24f, oy = 120f,
        stars = listOf(MiniStar(0f, 0f, 4f), MiniStar(39f, -10f, 3f), MiniStar(52f, 7f, 4f)),
        label = "夜天の書庫", labelX = -2f, labelY = 16f,
    ),
)

/**
 * 「今後追加」カード（モック .mshelf.add）: 塗りなし・1dp 破線枠・中央に「＋」と「今後追加」。
 * 破線は drawBehind で PathEffect.dashPathEffect を用いた角丸ストロークとして描く（枠色=soft）。
 */
@Composable
private fun AddMiniature(soft: Color, modifier: Modifier) {
    Box(
        modifier = modifier.drawBehind {
            val strokeWidthPx = 1.dp.toPx()
            val dash = 6.dp.toPx()
            val inset = strokeWidthPx / 2 // ストロークが枠でクリップされないよう内側へ半幅寄せる
            drawRoundRect(
                color = soft,
                topLeft = Offset(inset, inset),
                size = Size(size.width - strokeWidthPx, size.height - strokeWidthPx),
                cornerRadius = CornerRadius(14.dp.toPx()),
                style = Stroke(
                    width = strokeWidthPx,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash), 0f),
                ),
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "＋", fontSize = 34.sp, color = soft)
            Spacer(Modifier.height(Spacing.S8)) // .plus small margin-top:8px
            Text(text = "今後追加", fontSize = 10.sp, letterSpacing = 0.8.sp, color = soft)
        }
    }
}
