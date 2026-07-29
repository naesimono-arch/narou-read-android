package com.novelreader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp as lerpFloat
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

    // ページ = 全スキン＋末尾に「今後追加」カード（並び順は Skin.entries＝和モダン→夜行、今後追加は常に末尾）。
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

/**
 * 本棚ミニチュア（スキンごとの縮図）。**そのスキン自身のトークン**で描く＝各装いの「素の姿」を並べる。
 *
 * トークンの引き方（プラン仕様4）: そのスキンの既定変種 supportedThemes.first() で material()/shelf() を引き、
 * 地色=background・見出し文字=onBackground・罫=shelf.hairline・ヒーロー面=surfaceContainer・accent 線=signatureAccent。
 */
@Composable
private fun SkinMiniature(skin: Skin, modifier: Modifier) {
    val tokens = skin.tokens
    val theme = tokens.supportedThemes.first()
    val material = tokens.material(theme)
    val hairline = tokens.shelf(theme).hairline
    val accent = tokens.signatureAccent

    // 書影チップの色: モックは装飾グラデ（D=#3A4F63→#28323E 等）だが新規16進の直書きは禁止（ADR 0014）。
    // なぜ lerp 導出か: primary→onBackground の中間へ寄せると「本文色に近い落ち着いた面」になり、
    // スキンの地に馴染む縮図の書影として成立する（装飾グラデをトークン外の生色で作らないための近似）。
    val coverColor = lerpColor(material.primary, material.onBackground, 0.35f)
    val heroLine = material.onBackground.copy(alpha = 0.20f)
    val listLine = material.onBackground.copy(alpha = 0.16f)

    Column(
        modifier = modifier
            .background(material.background)
            // モック .mshelf padding:16px 14px。14px は余白スケール {4,8,12,16,…} 外のため 16dp へ丸める
            //（等距離 12↔16 は大きい側＝ADR 0014 §C の丸め規則）。ミニチュアは抽象縮図ゆえ寸法差は非本質。
            .padding(Spacing.S16),
    ) {
        // 見出し「本棚」（明朝・字間広め＝モック .m-h1 letter-spacing .14em → 14sp×0.14≒1.96sp）。
        Text(
            text = "本棚",
            fontFamily = MinchoFamily,
            fontSize = 14.sp,
            letterSpacing = 1.96.sp,
            color = material.onBackground,
        )
        Spacer(Modifier.height(Spacing.S12)) // .m-h1 margin-bottom:12px

        // ヒーロー行（書影チップ＋テキスト線2本＋accent の進捗線）。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(material.surfaceContainer)
                .border(1.dp, hairline, RoundedCornerShape(8.dp))
                .padding(Spacing.S12), // モック .m-hero padding:10px → 12dp へ丸め（スケール化）
            horizontalArrangement = Arrangement.spacedBy(Spacing.S12), // gap:10px → 12dp
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 34.dp, height = 50.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(coverColor),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.S8), // 線間 6px → 8dp
            ) {
                MiniLine(widthFraction = 0.82f, height = 5.dp, color = heroLine)
                MiniLine(widthFraction = 0.58f, height = 5.dp, color = heroLine)
                MiniLine(widthFraction = 0.40f, height = 5.dp, color = accent) // .acc＝signatureAccent の進捗線
            }
        }
        Spacer(Modifier.height(Spacing.S16)) // .m-hero margin-bottom:14px → 16dp へ丸め

        // 罫線区切りのリスト行×3（行書影＋テキスト線2本）。各行は上ヘアラインで区切る（モック .m-li border-top）。
        val rowWidths = listOf(0.88f to 0.50f, 0.74f to 0.44f, 0.66f to 0.40f)
        rowWidths.forEach { (w1, w2) ->
            HorizontalDivider(color = hairline)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.S8), // .m-li padding:8px 0
                horizontalArrangement = Arrangement.spacedBy(Spacing.S12), // gap:10px → 12dp
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 22.dp, height = 32.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(coverColor),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.S4), // 線間 5px → 4dp
                ) {
                    MiniLine(widthFraction = w1, height = 4.dp, color = listLine)
                    MiniLine(widthFraction = w2, height = 4.dp, color = listLine)
                }
            }
        }
    }
}

/** ミニチュア内の抽象テキスト線（実文字でなく Box の線で縮図表現＝モックの .m-lines i / .m-ls i）。 */
@Composable
private fun MiniLine(widthFraction: Float, height: androidx.compose.ui.unit.Dp, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(2.dp))
            .background(color),
    )
}

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
