package com.novelreader.ui.skins.j

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.novelreader.ui.TocState
import com.novelreader.ui.theme.AmbDarkGoldPortal
import com.novelreader.ui.theme.AmbDarkMossPortal
import com.novelreader.ui.theme.GlyphDarkPortal
import com.novelreader.ui.theme.GoldPortal
import com.novelreader.ui.theme.InkTocPortal
import com.novelreader.ui.theme.InnerPortal
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.Spacing
import com.novelreader.ui.tocInitialFirstVisibleIndex

// ============================================================
// スキンJ「ポータル」の目次＝この物語世界の「廊下を進む道程」（正本 toc-J.html・ADR 0022 §1 の構造分岐先）。
//
// 思想: 本棚Jの扉をくぐった先。左に一本の道(rail)を通し、章＝道沿いの節目(node)にする。
//   歩いた章（既読）は道が金に灯り節目が埋まる／現在章＝いま立つ扉＝金の灯り＋淡い金面で最も明るい／
//   まだ先の章（未読）＝道が細く沈み節目は空。進行を光量で語る（モック cap の明示）。
//
// D 機能の全数移植（M の TocSkyM・P の TocCartridgeP と同じ流儀）:
//   ・現在章ハイライト＝ .li.cur（淡い金面＋金の左ルール〔灯る道/節〕＋明朝太字＋金文字）。
//   ・既読/未読区別＝ .li.passed（道が金 goldd・節が埋まる・章題は --dim へ沈める＝読了の見当識）／
//     未読（既定）＝道が --line で細く沈む・節は空（--page 地＋--line 縁）。
//   ・章タップで本文へ＝行 clickable → onSelectChapter(fileName)。戻る＝トップバー左の戻る → onNavigateToBookshelf。
//   ・話数カウンタ＝J はモック実態として数値カウンタを持たない（topbar は「目次」のみ・cap「光量で進行を語る」）。
//     話数/進捗の機能は金の道（歩いた道＝既読数）＋現在節の灯りが担う＝数値ヘッダを足すと発明になる（報告参照）。
//     章題は空題時 "第N話" フォールバックで話数を保つ（J の本棚が 話 系＝BookshelfPortalJ に倣う）。
//   ・初期スクロール位置＝D/M/P と同じ tocInitialFirstVisibleIndex（現在章の1つ手前・未読は先頭）。
//   ・Loading/Empty/Error＝Empty はモック .empty に忠実。Loading/Error はモック未定義＝内側面基調の最小文言（M/P と同型）。
//
// 一画面一強調（ADR 0014 原則4・ADR 0022 §3 の抑制）: 強調は現在章行1点＝淡い金面＋灯る節＋金題。
//   passed の goldd 節/道は「読了状態」であって強調ではない（読了の静かな痕跡）。
//
// モーション: J モックに keyframes/transition/JS は無い（ADR 0022 §3）＝完全静止で実装する。
//   ゆえに Motion.kt スロットの適用箇所も reduce-motion 分岐も持たない（脈動・自動再生が存在しない＝
//   常時静止で reduce-motion 要件は自明に充足。現在地は「灯る扉」で示し点滅に頼らない＝モック cap の明示）。
//   機能モーション（遷移等）が要るのは実機後詰め層（ADR 0005 §B）。
//
// 色は Portal val のみ（直書き hex 禁止）。toc-J の内側家系の透過白（--soft/--dim/--line/glyph）は base #E9F0E4
//   ＝内側面の半透明白で、同 RGB を持つ Portal val は GlyphDarkPortal（reading-J --glyph #E9F0E4@.06）のみ。
//   近似（InkTocPortal #E7ECE1 で代替）は Δ が知覚下でも色相の別値化ゆえ避け、GlyphDarkPortal の RGB を借り
//   α だけ正本値へ差し替えて厳密一致させる（.copy(alpha=)＝焼き込み不能な透過色は P の LcdDotToc と同じ流儀）。
//   ambient（.phone::before）と goldd も同様に AmbDark*Portal / GoldPortal の RGB を借り α を正本へ差し替え＝全て厳密一致。
//   専用の named token を Color.kt へ足せばより明快だが本エージェントは theme/ を編集できない＝改善案で報告。
//
// タイポ: 章題・題字＝明朝(MinchoFamily・mock var(--mincho))。px 値は正本モックの font-size を 1:1 で sp へ写す。
// ============================================================

// ── 内側家系の透過色（グラデ地/暗面の上へ載る＝焼き込めず .copy(alpha=) で正本 α を付与）──
// base #E9F0E4（toc-J 内側の半透明白）を持つ Portal val は GlyphDarkPortal のみ＝RGB を借り α を正本値へ差し替え（厳密一致）。
private val SoftToc = GlyphDarkPortal.copy(alpha = 0.52f) // --soft rgba(233,240,228,.52)（sb/空状態/エラーの補助文字）
private val DimToc = GlyphDarkPortal.copy(alpha = 0.34f)  // --dim  rgba(233,240,228,.34)（passed=読了章題の沈め色）
private val LineToc = GlyphDarkPortal.copy(alpha = 0.09f) // --line rgba(233,240,228,.09)（未踏の道・節の縁・行の下線）
private val GlyphInkToc = GlyphDarkPortal.copy(alpha = 0.05f) // glyph rgba(233,240,228,.05)（象徴文字の極淡＝現状未使用・下記参照）

// 金の系列（GoldPortal #E2C878＝--gold と同 RGB。goldd/面/灯りは α を正本値へ差し替え＝厳密一致）。
private val GolddToc = GoldPortal.copy(alpha = 0.5f)      // --goldd rgba(226,200,120,.5)（歩いた道・埋まった節）
private val CurFaceFlat = GoldPortal.copy(alpha = 0.06f)  // .li.cur 平面 rgba(226,200,120,.06)
private val CurFaceGlow = GoldPortal.copy(alpha = 0.12f)  // .li.cur 左からの淡い金グロー rgba(226,200,120,.12)
private val CurNodeRing = GoldPortal.copy(alpha = 0.16f)  // .li.cur .node box-shadow spread 5px rgba(226,200,120,.16)

// ambient（.phone::before の森の大気＝上部220px の金＋苔。AmbDark*Portal の RGB を借り α を toc 値へ差し替え＝厳密一致）。
private val AmbGoldToc = AmbDarkGoldPortal.copy(alpha = 0.10f) // radial gold rgba(214,196,120,.10)
private val AmbMossToc = AmbDarkMossPortal.copy(alpha = 0.50f) // radial moss rgba(39,64,48,.5)

/** 章行の道程状態（現在章より前＝PASSED／現在章＝CUR／未読＝AHEAD）。値の正本＝toc-J.html .li.{passed,cur,（既定）}。 */
private enum class RowStep { PASSED, CUR, AHEAD }

@Composable
internal fun TocPortalJ(
    tocState: TocState,
    currentChapterFile: String?,
    onSelectChapter: (fileName: String) -> Unit,
    onNavigateToBookshelf: () -> Unit,
    onRetry: () -> Unit,
) {
    val entries = (tocState as? TocState.Content)?.entries ?: emptyList()
    // 現在章の index（D 実装 tocInitialFirstVisibleIndex と同じ突合＝fileName 一致）。未読/不一致は -1＝全章 AHEAD。
    val currentIndex = remember(entries, currentChapterFile) {
        entries.indexOfFirst { it.fileName == currentChapterFile }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 内側面の地（--page #0F1712）＋上部の森の大気（.phone::before の金/苔 radial）。
            .drawBehind { drawInnerAtmosphere() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // 象徴文字（.glyph 薬）はその物語の象徴＝目次画面は書籍 ID/題名を受け取らないため描けない（発明せず省く・報告参照）。
            TocPortalTopBar(onNavigateToBookshelf)
            when (tocState) {
                is TocState.Content -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    state = rememberLazyListState(
                        // 現在章付近を開いた瞬間から表示（D/M/P と同じ導出＝現在章の1つ手前・未読は先頭）。
                        initialFirstVisibleItemIndex = tocInitialFirstVisibleIndex(entries, currentChapterFile),
                    ),
                    contentPadding = PaddingValues(0.dp), // .scroll に余白なし（末尾のみ .pad 24px を後置）。
                ) {
                    itemsIndexed(entries, key = { _, e -> e.fileName }) { index, entry ->
                        val step = when {
                            index == currentIndex -> RowStep.CUR
                            currentIndex >= 0 && index < currentIndex -> RowStep.PASSED
                            else -> RowStep.AHEAD
                        }
                        TocChapterRow(
                            title = entry.title.ifEmpty { "第${index + 1}話" },
                            step = step,
                            onClick = { onSelectChapter(entry.fileName) },
                        )
                    }
                    item { Spacer(Modifier.height(Spacing.S24)) } // .pad height:24px → S24（末尾の余白）。
                }
                is TocState.Empty -> TocEmptyBody(Modifier.weight(1f))
                // Loading/Error は J 意匠のモック未定義＝最小限（内側面・題字は既に描かれている）。
                is TocState.Loading -> Spacer(Modifier.weight(1f))
                is TocState.Error -> TocErrorBody(tocState.message, onRetry, Modifier.weight(1f))
            }
        }
    }
}

// ============================================================
// topbar（モック .topbar: 戻る＋「目次」。数値カウンタなし＝J は光量で進行を語る）
// ============================================================
@Composable
private fun TocPortalTopBar(onNavigateToBookshelf: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)                       // .topbar height 56px
            .padding(horizontal = Spacing.S12),  // .topbar padding 0 12px
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onNavigateToBookshelf) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "本棚に戻る", tint = InkTocPortal)
        }
        Text(
            "目次",
            fontFamily = MinchoFamily,
            fontSize = 19.sp,                    // .topbar h1 19px
            letterSpacing = 0.14.em,             // letter-spacing:.14em
            fontWeight = FontWeight.Medium,      // font-weight:500
            color = InkTocPortal,                // --ink #E7ECE1
            modifier = Modifier.padding(start = Spacing.S8), // .topbar gap 8px
        )
    }
}

// ============================================================
// 章行（モック .li: 左48pxの道の列〔縦線＋node〕＋右の明朝章題〔下線〕。現在章は淡い金面＋灯る節＋金題）
// ============================================================
@Composable
private fun TocChapterRow(
    title: String,
    step: RowStep,
    onClick: () -> Unit,
) {
    // 道（rail::before）: 歩いた道（passed/cur）＝金 goldd／未踏（ahead）＝細く沈む --line。
    val railColor = if (step == RowStep.AHEAD) LineToc else GolddToc
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 現在章＝いま立つ扉。淡い金の平面＋左からの淡い金グロー（.li.cur background の2層）。
            .then(if (step == RowStep.CUR) Modifier.drawBehind { drawCurFace() } else Modifier)
            .clickable(onClick = onClick)
            // 現在章の a11y 焦点＝「いま立つ扉」。視覚は金面/灯りで示すが、読み上げ・分岐検証の識別子として付与
            //（意匠でなく a11y メタ＝モック非規定でも足してよい層。D 目次には無い＝ルーター分岐の検証点も兼ねる）。
            .then(if (step == RowStep.CUR) Modifier.semantics { contentDescription = "現在の章" } else Modifier)
            .height(IntrinsicSize.Min),
    ) {
        // 道の列（.rail width 48px）: 縦線と節を水平中央（線 x23-25・節 left18+6=24＝ともに 48/2 の中央）に重ねる。
        Box(
            modifier = Modifier.width(48.dp).fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.width(2.dp).fillMaxHeight().background(railColor)) // .rail::before 2px 縦線
            TocNode(step)                                                   // .node（線の上に重ねる）
        }
        // 章題（.tx）＋下線（border-bottom）。
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = MinchoFamily,
                fontSize = 15.sp,                // .tx 15px
                lineHeight = 23.sp,              // line-height:1.55 × 15 = 23.25 → 23sp
                // 現在章＝金 Bold／passed（読了）＝--dim へ沈める（読了の見当識・モック明示）／未読＝--ink。
                color = when (step) {
                    RowStep.CUR -> GoldPortal
                    RowStep.PASSED -> DimToc
                    RowStep.AHEAD -> InkTocPortal
                },
                fontWeight = if (step == RowStep.CUR) FontWeight.Bold else FontWeight.Normal,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                // .tx padding 18px 24px 18px 2px → S16 / S24 / S16 / S4（18は S16・2は S4 へ丸め＝ADR 0014 スケール）。
                modifier = Modifier.padding(start = Spacing.S4, top = Spacing.S16, end = Spacing.S24, bottom = Spacing.S16),
            )
            HorizontalDivider(color = LineToc, thickness = 1.dp) // .tx border-bottom 1px var(--line)
        }
    }
}

/** 章行の節（.node）＝12dp 円。passed=金充填／cur=灯る金＋5dp リング／ahead=空（--page 地＋--line 縁）。 */
@Composable
private fun TocNode(step: RowStep) {
    Box(
        modifier = Modifier.size(12.dp).drawBehind { // .node 12x12
            val r = size.minDimension / 2f
            when (step) {
                RowStep.PASSED -> drawCircle(GolddToc, radius = r) // bg+border goldd＝金の充填
                RowStep.CUR -> {
                    drawCircle(CurNodeRing, radius = r + 5.dp.toPx()) // box-shadow 0 0 0 5px（spread 5px・blur0＝硬縁リング）
                    drawCircle(GoldPortal, radius = r)                // bg+border gold＝最も明るい灯り
                }
                RowStep.AHEAD -> {
                    drawCircle(InnerPortal, radius = r)                                   // bg var(--page)
                    drawCircle(LineToc, radius = r - 1.dp.toPx(), style = Stroke(2.dp.toPx())) // border 2px var(--line)
                }
            }
        },
    )
}

/** 現在章行の面（.li.cur background）＝平らな金 α.06 ＋ 左8%からの淡い金グロー α.12（60% で透明へ）。 */
private fun DrawScope.drawCurFace() {
    drawRect(CurFaceFlat) // rgba(226,200,120,.06)（行全面の淡い金）
    // radial-gradient(120% 140% at 8% 50%, α.12, transparent 60%) の代替＝左寄りの金グロー（大気ゆえ falloff は近似可・ADR 0022 §5）。
    drawRect(
        Brush.radialGradient(
            0f to CurFaceGlow,
            1f to Color.Transparent,
            center = Offset(size.width * 0.08f, size.height * 0.5f),
            radius = size.width * 0.7f,
        ),
    )
}

/** 内側面の地（--page #0F1712）＋上部220px の森の大気（.phone::before の金/苔 radial）。 */
private fun DrawScope.drawInnerAtmosphere() {
    drawRect(InnerPortal) // --page #0F1712（森の内側）
    val w = size.width
    val h = size.height
    // 苔の沈み（radial 120% 80% at 50% -16%, moss α.5→透明 60%）を先に、金の敷居（90% 70% at 50% -6%, gold α.10→透明 58%）を上に。
    // 中心は画面上端よりわずか上（-6%/-16%）＝上部220px 帯の大気。falloff は近似可（ambient・ADR 0022 §5）。
    drawRect(
        Brush.radialGradient(
            0f to AmbMossToc,
            1f to Color.Transparent,
            center = Offset(w * 0.5f, -h * 0.03f),
            radius = w * 1.1f,
        ),
    )
    drawRect(
        Brush.radialGradient(
            0f to AmbGoldToc,
            1f to Color.Transparent,
            center = Offset(w * 0.5f, -h * 0.01f),
            radius = w * 0.9f,
        ),
    )
}

// ============================================================
// 空状態（モック .empty: 「章が見つかりません」・明朝・中央。破線枠等は toc-J に無い＝付けない）
// ============================================================
@Composable
private fun TocEmptyBody(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "章が見つかりません",
            fontFamily = MinchoFamily,          // .empty font-family:var(--mincho)
            fontSize = 15.sp,                    // .empty font-size:15px
            color = SoftToc,                     // .empty color:var(--soft)
        )
    }
}

// ============================================================
// エラー（モック未定義＝内側面基調の最小文言＋再試行。D の再試行導線を J 意匠で欠落なく残す）
// ============================================================
@Composable
private fun TocErrorBody(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.S24),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "目次の読み込みに失敗しました",
            fontFamily = MinchoFamily,
            fontSize = 15.sp,
            color = InkTocPortal,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.S8))
        Text(
            message,
            fontFamily = MinchoFamily,
            fontSize = 12.sp,
            // 失敗理由＝意味を運ぶ文字。--soft は #0F1712 上 AA(≈4.8:1) を満たす（--dim は不足のため使わない）。
            color = SoftToc,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.S16))
        Text(
            "再試行",
            fontFamily = MinchoFamily,
            fontSize = 15.sp,
            color = GoldPortal,                  // 金＝構造/導線の署名色
            modifier = Modifier.clickable(onClick = onRetry).padding(Spacing.S8),
        )
    }
}
