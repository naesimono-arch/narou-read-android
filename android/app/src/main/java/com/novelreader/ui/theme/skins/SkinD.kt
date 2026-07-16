package com.novelreader.ui.theme.skins

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.novelreader.ui.theme.*

/**
 * スキン「和モダンD」（視覚言語 D・docs/decisions/0005-ui-n-visual-language-D.md）のトークン束。
 *
 * ここは D の値の正本＝旧 Theme.kt に散在していた Material カラースキーム・読書 ReadingColors 3 分岐・
 * 本棚 ShelfColors 分岐・栞 ShioriColors を 1 スキン=1 ファイルへ集約した（P1 スキン骨格導入・プラン
 * 2026-07-17）。値・「なぜ」コメントは移設元から一言一句そのまま引き継ぎ、挙動は 1px も変えない。
 */
object SkinD : SkinTokens {

    override val supportedThemes: List<ReadingTheme> =
        listOf(ReadingTheme.LIGHT, ReadingTheme.SEPIA, ReadingTheme.DARK)

    // 初弾は全スキンとも共有の本文タイポ（原則5「静謐は機能」＝字面はスキン間で不変）。
    override val typography: Typography = NovelReaderTypography

    // ============================================================
    // Material3 カラースキーム
    // ============================================================
    private val LightColorScheme = lightColorScheme(
        primary              = PrimaryLight,
        onPrimary            = OnPrimaryLight,
        primaryContainer     = PrimaryContainerLight,
        onPrimaryContainer   = OnPrimaryContainerLight,
        secondary            = SecondaryLight,
        onSecondary          = OnSecondaryLight,
        secondaryContainer   = SecondaryContainerLight,
        onSecondaryContainer = OnSecondaryContainerLight,
        tertiary             = TertiaryLight,
        onTertiary           = OnTertiaryLight,
        tertiaryContainer    = TertiaryContainerLight,
        onTertiaryContainer  = OnTertiaryContainerLight,
        error                = ErrorLight,
        onError              = OnErrorLight,
        errorContainer       = ErrorContainerLight,
        onErrorContainer     = OnErrorContainerLight,
        background           = BackgroundLight,
        onBackground         = OnBackgroundLight,
        surface              = SurfaceLight,
        onSurface            = OnSurfaceLight,
        surfaceVariant       = SurfaceVariantLight,
        onSurfaceVariant     = OnSurfaceVariantLight,
        surfaceContainer     = SurfaceContainerLight,
        outline              = OutlineLight,
        outlineVariant       = OutlineVariantLight,
        inverseSurface       = InverseSurfaceLight,
        inverseOnSurface     = InverseOnSurfaceLight,
        inversePrimary       = InversePrimaryLight,
    )

    private val DarkColorScheme = darkColorScheme(
        primary              = PrimaryDark,
        onPrimary            = OnPrimaryDark,
        primaryContainer     = PrimaryContainerDark,
        onPrimaryContainer   = OnPrimaryContainerDark,
        secondary            = SecondaryDark,
        onSecondary          = OnSecondaryDark,
        secondaryContainer   = SecondaryContainerDark,
        onSecondaryContainer = OnSecondaryContainerDark,
        tertiary             = TertiaryDark,
        onTertiary           = OnTertiaryDark,
        tertiaryContainer    = TertiaryContainerDark,
        onTertiaryContainer  = OnTertiaryContainerDark,
        error                = ErrorDark,
        onError              = OnErrorDark,
        errorContainer       = ErrorContainerDark,
        onErrorContainer     = OnErrorContainerDark,
        background           = BackgroundDark,
        onBackground         = OnBackgroundDark,
        surface              = SurfaceDark,
        onSurface            = OnSurfaceDark,
        surfaceVariant       = SurfaceVariantDark,
        onSurfaceVariant     = OnSurfaceVariantDark,
        surfaceContainer     = SurfaceContainerDark,
        outline              = OutlineDark,
        outlineVariant       = OutlineVariantDark,
        inverseSurface       = InverseSurfaceDark,
        inverseOnSurface     = InverseOnSurfaceDark,
        inversePrimary       = InversePrimaryDark,
    )

    // セピアはライトの暖色変種＝素地・墨・面・ヘアライン・藍だけを琥珀紙トーンへ差し替え、
    // secondary（青磁＝未読の意味色）や error はライトと共有して意味色のブレを避ける。
    // なぜ用意するか: かつてセピア選択時はライト配色を流用しており、本棚・発見系で
    // 「ライトとセピアの差がない」実機フィードバック（2026-07-07）の主因だったため。
    private val SepiaColorScheme = LightColorScheme.copy(
        primary              = PrimarySepia,
        primaryContainer     = PrimaryContainerSepia,
        onPrimaryContainer   = OnPrimaryContainerSepia,
        tertiary             = PrimarySepia,
        tertiaryContainer    = PrimaryContainerSepia,
        onTertiaryContainer  = OnPrimaryContainerSepia,
        background           = BackgroundSepia,
        onBackground         = OnBackgroundSepia,
        surface              = BackgroundSepia,
        onSurface            = OnBackgroundSepia,
        surfaceVariant       = SurfaceVariantSepia,
        onSurfaceVariant     = OnSurfaceVariantSepia,
        surfaceContainer     = SurfaceContainerSepia,
        outline              = OutlineSepia,
        outlineVariant       = OutlineVariantSepia,
    )

    override fun material(theme: ReadingTheme): ColorScheme = when (theme) {
        ReadingTheme.LIGHT -> LightColorScheme
        ReadingTheme.SEPIA -> SepiaColorScheme
        ReadingTheme.DARK -> DarkColorScheme
    }

    override fun reading(theme: ReadingTheme): ReadingColors = when (theme) {
        // UI-n: 白紙設計で採用した視覚言語 D「和モダン・余白」へ全面差し替え。
        // なぜ旧クリーム＋朱墨を捨てるか: UI-n は既存配色を踏襲せず作り直す方針（docs/decisions/0005-ui-n-visual-language-D.md）。
        // 値は確定モック ui-n-phase0/reading-D.html から写経し、寒色白×藍ヘアラインで統一する。
        // LIGHT = 素地 #FBFAF8／墨 #1C1F26／アクセント藍 #1C3D5A。
        ReadingTheme.LIGHT -> ReadingColors(
            background       = Color(0xFFFBFAF8),
            text             = Color(0xFF1C1F26),
            textSecondary    = Color(0xFF7C808B),
            infoText         = InfoTextLight, // 素地 6.01:1（意味テキスト用の暗化シェード）
            placeholder      = Color(0xFFAFB1B7), // = textSecondary#7C808B @0.6 over 素地（焼き込み）
            navBackground    = Color(0xFFFBFAF8),
            // D はトップ/ボトムバーを本文素地と同色に揃え、藍のヘアラインだけで境界を示す。
            // アイコンは素地から十分離れた濃灰でコントラストを確保（タイトルは墨色）。
            topBarBackground = Color(0xFFFBFAF8),
            topBarTitle      = Color(0xFF1C1F26),
            topBarIcon       = Color(0xFF4A4F58),
            // ルビ＝著者指定の読み＝意味を運ぶ小テキストのため WCAG 4.5:1 が最低線（ADR 0014-D）。
            // 旧 #8B96A0 は素地 2.89:1／前書き後書きブロック地(#F1F0EC) 2.64:1 で未達だった。
            // 青灰の色相(H≈209°)・彩度(S≈0.10)を保ち明度のみ暗化した #616C77 で
            // 素地 5.14:1／ブロック地 4.70:1 と全面 AA 充足（本文 15.81:1 より薄く階層は保つ）。
            ruby             = Color(0xFF616C77),
            // hr は藍 #1C3D5A を素地に約50%で溶かした青灰（モックの opacity:.5 相当）。
            // 破線で主張させすぎないシーン区切りにする。
            hr               = Color(0xFF9FB0BC),
            divider          = Color(0xFFECEAE4), // D のヘアライン色
            blockBackground  = Color(0xFFF1F0EC),
            blockBorder      = Color(0xFFE4E2DB),
            accent           = Color(0xFF1C3D5A), // D の藍（現在章ハイライト・チップ選択色）
            rule             = Color(0xFF1C3D5A), // 章見出しルール（モック --rule）＝LIGHT は accent と同値
            isLight          = true,
        )
        // SEPIA は D の寒色を温かい紙トーンへ寄せた変種。藍アクセントは骨格として残しつつ
        // やや深い藍鼠 #2E4A60 にして暖色背景と調和させる。
        // なぜモック reading-D.html の .t-sepia 写経値（#F3ECDD 系）から逸脱するか:
        // 実機フィードバック（2026-07-07「ライトとセピアの色味に差がなく同じ色に見える」）を受け、
        // 彩度を約15%まで上げた琥珀の紙・焦茶の墨へ再調律してライト（寒色白・ほぼ無彩色）との
        // 知覚差を保証するため。モック側への逆反映は handover の宿題（実装がこの値の正本）。
        ReadingTheme.SEPIA -> ReadingColors(
            background       = Color(0xFFF2E7CE),
            text             = Color(0xFF3D3121),
            textSecondary    = Color(0xFF8C7D5D),
            infoText         = InfoTextSepia, // 素地 4.97:1（意味テキスト用の暗化シェード）
            placeholder      = Color(0xFFB5A78A), // = textSecondary#8C7D5D @0.6 over 素地（焼き込み）
            navBackground    = Color(0xFFECDFC0),
            // LIGHT と同方針: 上下バーを本文紙トーンに揃え、ヘアラインで境界を示す。
            topBarBackground = Color(0xFFECDFC0),
            topBarTitle      = Color(0xFF3D3121),
            topBarIcon       = Color(0xFF6A5B3C),
            // ルビは意味色＝WCAG 4.5:1 最低線（ADR 0014-D）。旧 #A3906A は素地 2.53:1／
            // ブロック地(#EBDEBE) 2.33:1 で未達。琥珀の色相(H≈40°)・彩度(S≈0.24)を保ち明度のみ
            // 暗化した #6D5F43 で素地 5.08:1／ブロック地 4.67:1 と全面 AA 充足。
            ruby             = Color(0xFF6D5F43),
            hr               = Color(0xFFB4A379),
            divider          = Color(0xFFE0D3B0),
            blockBackground  = Color(0xFFEBDEBE),
            blockBorder      = Color(0xFFDCCC9F),
            accent           = Color(0xFF2E4A60), // 暖色背景に合わせやや深めの藍鼠
            rule             = Color(0xFF2E4A60), // 章見出しルール（モック --rule）＝SEPIA は accent と同値
            isLight          = true,
        )
        // DARK は D の寒色を保った冷たい暗面（旧の温かい黒 #1C1916 から転換）。
        // アクセントは暗背景で沈まないよう明るい青 #6E96B8 にする（モック reading-D.html の .t-dark）。
        ReadingTheme.DARK -> ReadingColors(
            background       = Color(0xFF14171C),
            text             = Color(0xFFC7CDD3),
            textSecondary    = Color(0xFF7B838C),
            infoText         = InfoTextDark, // 暗面 5.70:1（意味テキスト用の役割別トークン）
            placeholder      = Color(0xFF52585F), // = textSecondary#7B838C @0.6 over 暗面（焼き込み）
            navBackground    = Color(0xFF181C22),
            topBarBackground = Color(0xFF181C22),
            topBarTitle      = Color(0xFFC7CDD3),
            topBarIcon       = Color(0xFF9AA2AB),
            // ルビは意味色＝WCAG 4.5:1 最低線（ADR 0014-D）。暗面ではルビだけ暗すぎると読めないため
            // 旧 #6E7984 は素地 4.05:1／ブロック地(#1B1F26) 3.72:1 で未達。青灰の色相(H≈210°)・
            // 彩度(S≈0.09)を保ち明度のみ明化した #7F8994 で素地 5.05:1／ブロック地 4.65:1 と全面 AA 充足。
            ruby             = Color(0xFF7F8994),
            hr               = Color(0xFF46566A),
            divider          = Color(0xFF2A2F38),
            blockBackground  = Color(0xFF1B1F26),
            blockBorder      = Color(0xFF2A2F38),
            accent           = Color(0xFF6E96B8), // 暗背景で沈まない明るい藍
            rule             = Color(0xFF5E7E9C), // 章見出しルール（モック --rule）＝DARK のみ accent #6E96B8 と乖離
            isLight          = false,
        )
    }

    // 本棚系の家系トークン（ヘアライン／未読ラベル）をテーマに応じて返す。
    // ヘアラインはセピア/ダークで OutlineVariant と同値だが、ライトは本棚系専用値（#E4E2DB）へ分岐。
    // 未読ラベルはライト/セピア=濃青磁 UnreadSeiji、ダークは暗面で合格済みの SecondaryDark を継続。
    override fun shelf(theme: ReadingTheme): ShelfColors = when (theme) {
        ReadingTheme.LIGHT -> ShelfColors(ShelfHairlineLight, UnreadSeiji, InfoTextLight)
        ReadingTheme.SEPIA -> ShelfColors(OutlineVariantSepia, UnreadSeiji, InfoTextSepia)
        ReadingTheme.DARK -> ShelfColors(OutlineVariantDark, SecondaryDark, InfoTextDark)
    }

    // 栞書影の紙／墨／識別色明度。
    // なぜ Skin から明示供給か: 旧実装は栞の紙/墨を surface/onSurface から流用し（ダークのみ専用
    // トークン ShioriCoverPaperDark/InkDark）、識別色明度は surface の luminance/BackgroundSepia 一致で
    // 推定していた＝D 前提の暗黙結合でスキン導入時に必ず壊れる。値の対応は移設元と完全同値:
    //   紙/墨: ライト/セピア=surface/onSurface と一致（＝SurfaceLight/OnSurfaceLight・
    //          BackgroundSepia/OnBackgroundSepia）、ダークのみ専用の ShioriCoverPaperDark/InkDark。
    //   明度 accentLightness: 正本 consistency-D の THEMES.accL（ライト52/セピア48/ダーク62）。
    override fun shiori(theme: ReadingTheme): ShioriColors = when (theme) {
        ReadingTheme.LIGHT -> ShioriColors(SurfaceLight, OnSurfaceLight, 0.52f)
        ReadingTheme.SEPIA -> ShioriColors(BackgroundSepia, OnBackgroundSepia, 0.48f)
        ReadingTheme.DARK -> ShioriColors(ShioriCoverPaperDark, ShioriCoverInkDark, 0.62f)
    }
}
