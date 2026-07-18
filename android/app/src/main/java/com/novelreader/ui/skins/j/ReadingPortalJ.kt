package com.novelreader.ui.skins.j

import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.novelreader.ui.skins.m.kanjiNumber
import com.novelreader.ui.theme.AmbDarkGoldPortal
import com.novelreader.ui.theme.AmbDarkMossPortal
import com.novelreader.ui.theme.AmbLightGoldPortal
import com.novelreader.ui.theme.AmbLightMossPortal
import com.novelreader.ui.theme.AmbSepiaGoldPortal
import com.novelreader.ui.theme.AmbSepiaMossPortal
import com.novelreader.ui.theme.GlyphDarkPortal
import com.novelreader.ui.theme.GlyphLightPortal
import com.novelreader.ui.theme.GlyphSepiaPortal
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.MotionDurationDismiss
import com.novelreader.ui.theme.MotionDurationReveal
import com.novelreader.ui.theme.ReadingColors
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.Spacing
import com.novelreader.ui.theme.skins.SkinJ

// ============================================================
// スキンJ「ポータル」の読書クローム部品＋設定シート部品（正本 reading-J.html / settings-J.html・
// ADR 0022 §1「共通骨格＋スキン別部品」）。M の ReadingChromeM.kt・P の ReadingCartridgeP.kt がこの構成の見本。
//
// 本文エンジン（組版・ページング・章送り・状態機械・タップトグル・スワイプ覗き）は共有のまま。
// J で替わるのはクローム部品のみ:
//   ①章扉（章頭を「扉をくぐる瞬間」に＝大気ambient＋大象徴文字glyph＋敷居sillの光／reading-J .chap-h）
//   ②シーン区切り（--rule の一条の光へ滲むグラデ線／reading-J hr）
//   ③章末の印（— 第N話 了 — ＋敷居sill2／reading-J .chapend＝遊び心J2の相方）
//   ④遊び心J2『敷居光』（章末到達で右端に次章の扉の敷居が灯る／reading-J .nextdoor）
//   ⑤設定シート＝扉の前の身支度（テーマ3択を「扉の向こうの光」の小プレビューに／settings-J）
// 本文組版の規格層（明朝・ルビ・和文 letterSpacing 0）は不可侵＝一切触らない（版面はスキンで上書き不可）。
//
// J の話数表記は漢数字（reading-J .num「第 百二十七 話」＝M と同表記）。純関数 kanjiNumber を skins/m から
// 流用する（意匠依存でなく数値ユーティリティの共有＝重複実装を避ける）。
//
// バーは D 標準クローム（← ＋章題／前章・目次・表示設定・次章）のまま＝J はバー自体を署名にしない
// （M の結線進捗・P の緑LCDセーブバーのような常設クローム部品を J は持たない。モック実態）。色は
// D 標準バーが colors（＝SkinJ.reading）に追従するため、J の読書テーマ面へ自動で染まる。
//
// 色は Color.kt の Portal val のみ（直書き hex 禁止・theme/ は本タスク編集禁止）。モックにあって Portal val
// に無い値（settings-J のシート面/扉プレビューの大気/チップ境界の微差など）は近似せず、当該箇所へ TODO を
// 付し暫定は既存の役割相当トークンで描く（発明・近似禁止＝報告列挙）。
//
// モーション（ADR 0022 §3＋本タスク裁定）: J2 の敷居光の出没（章末到達で 0→1・離脱で 1→0）は「次への誘い」
//   ＝現在地フィードバック類型で Motion.kt の reveal/dismiss スロットに載せる。滲みの呼吸（breathe）は
//   モック @keyframes breathe(2.6s) の写経で、M の「現在地の脈動」先例（rememberInfiniteTransition・
//   reduce-motion で静止）に倣いインライン周期で実装する（Motion.kt は装飾ループを持たない設計＝脈動類は各所直書き）。
//   reduce-motion（アニメーター無効）では呼吸を止め固定輝度（モックの prefers-reduced-motion 分岐＝opacity .8）。
// ============================================================

/** reading-J .t-* の大気(amb1/amb2)＋象徴文字色(glyph)を読書テーマへ対応づける（章扉 ambient 用）。 */
private data class PortalAmbient(val gold: Color, val moss: Color, val glyph: Color)

private fun portalAmbientFor(theme: ReadingTheme): PortalAmbient = when (theme) {
    ReadingTheme.DARK -> PortalAmbient(AmbDarkGoldPortal, AmbDarkMossPortal, GlyphDarkPortal)
    ReadingTheme.LIGHT -> PortalAmbient(AmbLightGoldPortal, AmbLightMossPortal, GlyphLightPortal)
    ReadingTheme.SEPIA -> PortalAmbient(AmbSepiaGoldPortal, AmbSepiaMossPortal, GlyphSepiaPortal)
}

/**
 * 章扉（reading-J .chap-h）＝章頭を「扉をくぐる瞬間」として演出する劇場。
 * 大気(ambient)グラデ＋大象徴文字(glyph＝章題頭文字を巨大・極淡で＝本棚Jの扉と同じ「象徴で埋める」思想)＋
 * 話数(漢数字・金)＋章題(明朝)＋敷居sill(中央から左右へ滲む一条の光＋金の芯)。
 * 章題のサイズはユーザーの文字サイズ設定へ追従（D/M/P と同じ規格層追従＝本文＋2sp）。
 *
 * @param readingTheme 大気/glyph の変種選択に使う（colors は isLight しか持たず light/sepia を分離できないため
 *   theme を直接受ける＝近似せず正しい amb トークンを引く）。
 */
@Composable
fun ChapterHeaderJ(
    title: String,
    chapterNumber: Int?,
    colors: ReadingColors,
    readingTheme: ReadingTheme,
    fontSize: Int,
    bodyMarginDp: Int,
    bodyMaxWidth: Dp,
) {
    val amb = portalAmbientFor(readingTheme)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // ambient/glyph は版面より広く全幅で滲ませる（モック .chap-h::before left:-12%..right:112%）。
            // clipToBounds で巨大 glyph を章扉の高さへ切り落とす（モック overflow:hidden＝glyph は高さを支配しない）。
            .clipToBounds()
            .drawBehind {
                // 森の大気（.chap-h::before）＝金の radial（上・中央寄り）＋森緑の radial（さらに上から沈む）。
                // CSS の楕円 radial(％)→Compose の円 radial への幾何翻訳は質感の後詰め層（ADR 0005-B）。
                // 色は amb トークン厳密（近似なし）・置きは「上中央に金／頭上から森緑」の意図写経。
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(amb.gold, Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.02f),
                        radius = size.width * 0.72f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(amb.moss, Color.Transparent),
                        center = Offset(size.width * 0.5f, -size.height * 0.14f),
                        radius = size.width * 1.0f,
                    ),
                )
            },
    ) {
        // 大象徴文字（.glyph＝章題頭文字を巨大・極淡で）。matchParentSize で高さを支配させず（Column が高さを決める）、
        // はみ出しは親の clipToBounds が切る＝モックの絶対配置＋overflow:hidden と同挙動。
        Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.TopCenter) {
            Text(
                text = title.take(1),
                fontFamily = MinchoFamily,        // .glyph font-family: --mincho
                fontSize = 180.sp,                // .glyph 210px 相当の大象徴（clip 前提の巨大値）
                color = amb.glyph,                // .glyph = --glyph（極淡・意味非搬送＝装飾）
                maxLines = 1,
                softWrap = false,
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = bodyMaxWidth)
                .padding(horizontal = bodyMarginDp.dp)
                // .chap-h padding 92px..8px＝章頭の劇場の余白。上は画面リズム最大単位で「くぐる間」を確保。
                .padding(top = Spacing.S40, bottom = Spacing.S8),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (chapterNumber != null) {
                Text(
                    text = "第 ${kanjiNumber(chapterNumber)} 話",
                    fontSize = 11.sp,             // .num 11px（ゴシック・字間 .34em・金）
                    letterSpacing = 0.34.em,
                    color = colors.accent,        // --accent（金＝構造/話数）
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.S12)) // .t margin-top 14px → S12
            }
            Text(
                text = title,
                fontFamily = MinchoFamily,        // 本文組版＝明朝（規格層・不変）
                fontSize = (fontSize + 2).sp,     // 規格層追従（D/M/P と同じ「本文＋2」）
                fontWeight = FontWeight.SemiBold, // .t font-weight 600
                lineHeight = 1.6.em,              // .t line-height 1.6
                color = colors.text,              // --ink
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { heading() },
            )
            Spacer(Modifier.height(Spacing.S24))  // .sill margin-top 24px → S24
            SillJ(colors = colors)                // 敷居＝くぐった扉の光の帯
        }
    }
}

/**
 * 敷居（reading-J .chap-h .sill）＝中央から左右へ滲む一条の光（--rule グラデ）＋中央上の金の芯（--accent＋グロー）。
 * くぐり終えた扉の光を象徴する静的意匠（脈動なし＝章頭は静けさ・J2 の敷居光とは別物）。
 */
@Composable
private fun SillJ(colors: ReadingColors, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.width(132.dp).height(10.dp)) { // .sill 132×2＋::after の芯グロー分の縦余地
        val d = 1.dp.toPx()
        val lineY = size.height - 1.5f * d
        // 一条の光（transparent→--rule→transparent の横グラデ・opacity .9）
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Transparent,
                0.5f to colors.rule.copy(alpha = 0.9f),
                1f to Color.Transparent,
            ),
            topLeft = Offset(0f, lineY - d),
            size = androidx.compose.ui.geometry.Size(size.width, 2 * d),
        )
        // 金の芯（::after 6px 円・--accent＋box-shadow グロー・opacity .6）を中央やや上に。
        val cx = size.width / 2f
        val cy = lineY - 2 * d
        drawCircle(
            brush = Brush.radialGradient(
                0f to colors.accent.copy(alpha = 0.6f), 1f to Color.Transparent,
                center = Offset(cx, cy), radius = 6 * d,
            ),
            radius = 6 * d, center = Offset(cx, cy),
        )
        drawCircle(colors.accent.copy(alpha = 0.6f), radius = 3 * d, center = Offset(cx, cy))
    }
}

/**
 * シーン区切り（reading-J hr）＝中央 40% 幅の一条の光（transparent→--rule→transparent・opacity .5）。
 * D の中央短実線に対する J の翻訳＝滲む光の区切り。素材色は colors.rule（読書テーマ追従）。
 */
@Composable
fun SceneDividerJ(colors: ReadingColors, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.S16), // hr margin 16px → S16
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxWidth(0.4f).height(1.dp)) { // hr width 40% / height 1px
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.5f to colors.rule.copy(alpha = 0.5f), // opacity .5
                    1f to Color.Transparent,
                ),
            )
        }
    }
}

/**
 * 章末の印（reading-J .chapend＝遊び心J2の相方）＝「— 第N話 了 —」＋敷居sill2。
 * 「ここまで読み切った＝この扉をくぐり終えた」合図で、右端の次の扉（J2 敷居光）と対になる静的意匠。
 * chapterNumber が不明なら話数を持たない印（「— 了 —」）へ縮退＝版面を壊さず情報を偽らない。
 */
@Composable
fun ChapterEndMarkJ(chapterNumber: Int?, colors: ReadingColors, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Spacing.S32), // .chapend margin-top 34px → S32
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (chapterNumber != null) "— 第${kanjiNumber(chapterNumber)}話 了 —" else "— 了 —",
            fontSize = 10.5.sp,            // .mark 10.5px（ゴシック・字間 .34em・--soft）
            letterSpacing = 0.34.em,
            color = colors.textSecondary, // --soft（装飾＝了の印）
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.S12)) // .sill2 margin-top 14px → S12
        // 敷居sill2（96×2 --rule グラデ・opacity .7）
        Canvas(modifier = Modifier.width(96.dp).height(2.dp)) {
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.5f to colors.rule.copy(alpha = 0.7f),
                    1f to Color.Transparent,
                ),
            )
        }
    }
}

/**
 * 遊び心J2『敷居光』（reading-J .nextdoor）＝章末まで読み切ると右端に次章の扉の敷居の光が立ち上がり次章を誘う。
 * 右端 64dp の縦帯: 森緑の滲み(edge・--rule)＋金の芯(spine・--accent)＋「次の章へ」の縦書き(--accent)。
 *
 * @param atChapterEnd reader が章末に到達したか（NativeReadingScreen が !canScrollForward の derivedStateOf で供給）。
 *   true で敷居光が 0→1(reveal)・false で 1→0(dismiss)。呼び出し側で「次章が在るとき」だけ本部品を出す
 *   （最終章には次の扉が無い＝誘い先が無いため）。
 *
 * 出没は Motion.kt の reveal/dismiss スロット（次への誘い＝現在地フィードバック）。滲みの呼吸は M の
 * 「現在地の脈動」先例に倣いインライン周期(2.6s)・reduce-motion で静止（モック reduce 分岐＝固定 .8）。
 */
@Composable
fun NextDoorEdgeGlowJ(atChapterEnd: Boolean, colors: ReadingColors, modifier: Modifier = Modifier) {
    // 出没: 章末到達で敷居光の全体 alpha を 0→1(reveal 250ms)・離脱で 1→0(dismiss 150ms)。
    val showAlpha by animateFloatAsState(
        targetValue = if (atChapterEnd) 1f else 0f,
        animationSpec = tween(if (atChapterEnd) MotionDurationReveal else MotionDurationDismiss),
        label = "nextdoorShow",
    )
    // reduce-motion 検出（アニメーター無効設定/省電力のスケール0）。M/BookshelfSkyM と同じ判定を流用（ADR 0022 §3）。
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    // 滲みの呼吸（.edge breathe 2.6s・opacity .55↔1）。animateFloat 値を composition で読むと毎フレーム再コンポーズ
    // されるため、State のまま持ち Canvas draw ラムダ内でだけ読む provider にする（M の deferred-read 作法に統一）。
    // reduce 時は無限アニメを作らず null＝provider が固定 .8 を返す（モック reduce 分岐）。
    val breatheAnim: State<Float>? = if (reduceMotion) {
        null
    } else {
        rememberInfiniteTransition(label = "nextdoorBreathe").animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse), // @keyframes breathe 2.6s
            label = "nextdoorBreathePhase",
        )
    }
    val breathe: () -> Float = { breatheAnim?.value ?: 0.8f }
    // showAlpha≈0 のときは描画コストをかけない（章途中は右端が暗いまま＝モックの非 .show 状態）。
    if (showAlpha <= 0.01f) return
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(64.dp)                       // .nextdoor width 64px
            .graphicsLayer { alpha = showAlpha },
    ) {
        Canvas(modifier = Modifier.fillMaxHeight().width(64.dp)) {
            val d = 1.dp.toPx()
            val breatheNow = breathe() // 呼吸値は draw 段でだけ読む（deferred read）。
            // 滲み(.edge)＝270deg グラデ（右端 --rule .5 → .12@45% → 透明）。呼吸で全体濃度を上下させる。
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.55f to colors.rule.copy(alpha = 0.12f * breatheNow),
                    1f to colors.rule.copy(alpha = 0.5f * breatheNow),
                ),
            )
            // 芯(.spine)＝右端 6px 内側の 2px 縦バー（--accent→--rule）。上下 14% を空ける。
            val spineX = size.width - 6 * d
            val top = size.height * 0.14f
            val bottom = size.height * 0.86f
            drawLine(
                brush = Brush.verticalGradient(
                    0f to colors.accent, 1f to colors.rule.copy(alpha = 0.4f),
                    startY = top, endY = bottom,
                ),
                start = Offset(spineX, top),
                end = Offset(spineX, bottom),
                strokeWidth = 2 * d,
                cap = StrokeCap.Round,
            )
        }
        // 「次の章へ」の縦書き（.call writing-mode vertical-rl＝各文字を縦積み・--accent）。
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = Spacing.S12),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            "次の章へ".forEach { ch ->
                Text(
                    text = ch.toString(),
                    fontSize = 11.sp,             // .call 11px（ゴシック・字間縦・金）
                    color = colors.accent,        // --accent
                )
            }
        }
    }
}

// ============================================================
// 表示設定シート（settings-J）の J 部品＝「扉の前の身支度」。
// シートのロジック（テーマ択・スライダー・永続化）と面/つまみは共有（D 既定＝シート面 colors.background・
// 明朝見出し・金つまみ＝J の colors.accent が金のため既定のままで J の署名になる）。J 固有はテーマ3択のみ:
// 各チップを「扉の向こうにどの光が差すか」の小プレビュー（そのテーマ面＋明朝「あ」）にし、選択＝金の縁で灯す。
//
// settings-J の以下はモックにあって Portal val に無い（theme/ 編集禁止＝追加せず TODO・報告）:
//   ・--sheet #141C15（身支度の場のシート面。暫定=colors.background で読書テーマ面に追従＝モック caption「追従」に忠実）
//   ・--sheet-line rgba(233,240,228,.09)（チップ境界。暫定=colors.divider＝役割相当の theme ヘアライン）
//   ・扉プレビューの大気 .prev.*::before（rgba(214,196,120,.16)等＝reading amb の .16/.16/.2 で alpha 相違＝別値。
//     近似せず省略＝プレビューは「テーマ面＋明朝あ＋金縁」で扉の光を伝える。大気の重ねは token 補充後の後詰め）。
// ============================================================

/**
 * テーマ3択＝「扉の向こうの光」を選ぶ小プレビュー（settings-J .chips）。M/P と違い FilterChip を使わず、
 * 各チップにそのテーマ面（地色＋明朝「あ」）を灯し、選択＝金の縁（--gold）で示す。
 * 「システムに従う」（D 機能＝OS 明暗への自動追従。モック省略機能を J 意匠へ移植）を3扉の下に金の選択で併置する。
 *
 * @param sheetColors シート面＝現在の読書テーマ colors（ラベル文字色・非選択境界に使う）。
 */
@Composable
fun PortalThemeDoorChips(
    currentTheme: ReadingTheme,
    followingSystem: Boolean,
    onThemeChange: (ReadingTheme) -> Unit,
    onFollowSystem: () -> Unit,
    sheetColors: ReadingColors,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S12)) { // .chips gap 12px
            // 並びはモック順（ライト・セピア・ダーク）。
            listOf(ReadingTheme.LIGHT, ReadingTheme.SEPIA, ReadingTheme.DARK).forEach { theme ->
                // 扉プレビューの地色/文字色は SkinJ.reading(theme)＝その扉の向こうの読書面（正本値）。
                val prev = SkinJ.reading(theme)
                val selected = !followingSystem && currentTheme == theme
                val label = when (theme) {
                    ReadingTheme.LIGHT -> "ライト"
                    ReadingTheme.SEPIA -> "セピア"
                    ReadingTheme.DARK -> "ダーク"
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp)) // .chip border-radius 14px
                        // 選択＝金の縁（.chip.on border-color --gold）。非選択＝theme ヘアライン（--sheet-line 相当・TODO 上述）。
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) sheetColors.accent else sheetColors.divider,
                            shape = RoundedCornerShape(14.dp),
                        )
                        .clickable { onThemeChange(theme) }
                        .padding(Spacing.S8),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 扉プレビュー（.prev＝そのテーマ面＋明朝「あ」）。大気の重ねは token 補充後の後詰め（上述 TODO）。
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)               // .prev height 52px
                            .clip(RoundedCornerShape(10.dp)) // .prev border-radius 10px
                            .background(prev.background)  // その扉の向こうの地色
                            .border(1.dp, prev.divider, RoundedCornerShape(10.dp)), // 明地の識別（light-on-light の輪郭）
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "あ",
                            fontFamily = MinchoFamily,    // .prev font-family --mincho
                            fontSize = 22.sp,             // .prev 22px
                            color = prev.text,            // その扉の向こうの本文色
                        )
                    }
                    Spacer(Modifier.height(Spacing.S8))   // .chip gap 9px → S8
                    Text(
                        text = label,
                        fontSize = 12.5.sp,               // .cl 12.5px（ゴシック）
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) sheetColors.accent else sheetColors.text, // .chip.on .cl=--gold
                    )
                }
            }
        }
        // 「システムに従う」（D 機能の J 意匠移植）。3扉の下に金の選択で併置＝OS 明暗への自動追従へ戻す入口。
        // なぜ扉にしないか: 追従は特定テーマ面を選ぶ行為でなく「宣言を消す」ため、扉プレビューが無い（扉の光が定まらない）。
        Spacer(Modifier.height(Spacing.S12))
        Text(
            text = "システムに従う",
            fontSize = 12.5.sp,
            fontWeight = if (followingSystem) FontWeight.Bold else FontWeight.Normal,
            color = if (followingSystem) sheetColors.accent else sheetColors.textSecondary,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable { onFollowSystem() }
                .padding(horizontal = Spacing.S12, vertical = Spacing.S8),
        )
    }
}
