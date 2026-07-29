package com.novelreader.ui.theme.skins

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.novelreader.ui.theme.*

/**
 * スキン「ポータルJ」（視覚言語 J・正本モック docs/design-candidates/skins/{bookshelf,reading,toc,settings,discovery}-J.html）のトークン束。
 *
 * 物語への扉・金の敷居。署名3色（金 --gold #E2C878＝構造/強調・森緑 --green #9FCFA9＝続きあり/未取込・
 * 宵紫 --plum #B79AD0＝他の扉 peek）は全画面の不動点。世界観は言葉でなく大気と象徴文字（ambient/glyph）で出す。
 *
 * なぜ material/本棚/目次/発見が theme 非依存の固定ダーク森面か: reading-J にだけ .t-dark/.t-light/.t-sepia の
 * 変種一式があり、本棚/目次/発見/設定はモックが固定ダーク森面（回廊 --page #0C0E0B）＝変種が作用するのは読書系
 * トークンのみ（ADR 0022 §2・モック実態）。ゆえに [supportedThemes] は3種を宣言しつつ material/shelf/shiori は
 * theme 引数を無視して固定ダーク値を返し、reading のみ3分岐 when で変種を出す（既定=先頭 DARK）。
 *
 * 外殻（bookshelf/discovery --page #0C0E0B）と内側（toc/reading --page #0F1712）は家系2トークン（ADR 0022 §4）。
 * ambient/glyph は構造画面専用の大気パレット（ADR 0022 §5＝Color.kt の Portal* 系 val を構造画面が直接参照）。
 */
object SkinJ : SkinTokens {

    // J は3テーマを持つが、作用するのは読書系トークンのみ（ADR 0022 §2）。先頭 DARK が既定変種。
    override val supportedThemes: List<ReadingTheme> =
        listOf(ReadingTheme.DARK, ReadingTheme.LIGHT, ReadingTheme.SEPIA)

    // J の署名色は金の敷居（--gold #E2C878＝GoldPortal を流用）。
    override val signatureAccent: Color = GoldPortal

    // 初弾は全スキンとも共有の本文タイポ（原則5「静謐は機能」＝字面はスキン間で不変）。
    override val typography: Typography = NovelReaderTypography

    // ============================================================
    // Material3 カラースキーム（darkColorScheme ベース＝theme 非依存の固定ダーク森面）。
    // なぜ固定か: 本棚/目次/発見/設定はモックが固定ダーク森面（回廊 --page #0C0E0B）で、reading の3テーマは
    // material へ波及しない（ADR 0022 §2）。error 系は D DARK と共有（ADR 0021 裁定4）。
    // ============================================================
    private val PortalColorScheme = darkColorScheme(
        primary              = GoldPortal,       // 金＝構造/強調（--gold）
        onPrimary            = PagePortal,       // 外殻の暗色 #0C0E0B（gold 上 11.79:1）
        primaryContainer     = PanelPortal,      // 森のパネル面 #16211A
        onPrimaryContainer   = InkPortal,        // 森面の主文字
        secondary            = GreenPortal,      // 森緑＝続きあり/未取込（--green）
        onSecondary          = PagePortal,       // 暗色（green 上 11.05:1）
        secondaryContainer   = PanelPortal,
        onSecondaryContainer = InkPortal,
        tertiary             = PlumPortal,       // 宵紫＝他の扉 peek（--plum）
        onTertiary           = PagePortal,       // 暗色（plum 上 7.89:1）
        tertiaryContainer    = PanelPortal,
        onTertiaryContainer  = InkPortal,
        // error 系は D DARK と共有（意味色＝赤の階調はスキンで揺らさない・ADR 0021 裁定4）。
        error                = ErrorDark,
        onError              = OnErrorDark,
        errorContainer       = ErrorContainerDark,
        onErrorContainer     = OnErrorContainerDark,
        background           = PagePortal,       // 回廊の外殻 #0C0E0B（発見系共有画面の地）
        onBackground         = InkPortal,        // --ink 温白 #F1F4EC
        surface              = PagePortal,       // 素地と同面
        onSurface            = InkPortal,
        surfaceVariant       = PanelPortal,      // 森のパネル面 #16211A
        onSurfaceVariant     = SoftPortal,       // --soft 合成（装飾補助・#0C0E0B 上 6.75:1）
        surfaceContainer     = PanelPortal,      // 同パネル面
        outline              = LinePortal,       // --line 合成 #2B2E29（外殻ヘアライン）
        outlineVariant       = LinePortal,       // 同 --line 合成
        inverseSurface       = InkPortal,        // 明面反転＝onBackground
        inverseOnSurface     = PanelPortal,      // 反転面上の暗色
        inversePrimary       = InversePrimaryPortal, // gold を明面用に暗化（#F1F4EC 上 4.52:1）
        // surfaceContainer の未指定4段を J の面へ束ね直す（High=ダイアログ面＝回廊の外殻 --page／
        // 残りは森のパネル面＝上の「同パネル面」の宣言どおり）。放置すると M3 baseline の紫が
        // 確認ダイアログ等に出る（機序と根拠＝SkinContainerTiers.kt。J にダイアログ意匠のモックは無い）。
    ).withSkinContainerTiers()

    override fun material(theme: ReadingTheme): ColorScheme = PortalColorScheme // 固定ダーク森面＝theme 非依存

    override fun reading(theme: ReadingTheme): ReadingColors = when (theme) {
        // 変種が作用するのは読書のみ（ADR 0022 §2）。値の正本＝reading-J.html の .t-dark/.t-light/.t-sepia。
        // 森の内側の暗面（既定）。ambient/glyph は Color.kt の *DarkPortal を構造/読書背景が参照。
        ReadingTheme.DARK -> ReadingColors(
            background       = Color(0xFF0F1712),  // .t-dark --bg（森の内側）
            text             = Color(0xFFE4E9DE),  // .t-dark --ink
            textSecondary    = Color(0xFF8E998C),  // .t-dark --soft（bg 6.15:1）
            infoText         = Color(0xFF8E998C),  // = --soft（bg 6.15:1 で意味テキスト AA も充足）
            placeholder      = Color(0xFF5B655B),  // = --soft#8E998C @0.6 over --bg#0F1712（焼き込み・非意味）
            navBackground    = Color(0xFF101913),  // .t-dark --bar
            topBarBackground = Color(0xFF101913),  // 同 --bar
            topBarTitle      = Color(0xFFE4E9DE),  // = --ink
            topBarIcon       = Color(0xFFE4E9DE),  // = --ink
            ruby             = Color(0xFFA2AC9C),  // .t-dark --ruby（意味搬送小文字・bg 7.74:1 AA）
            hr               = Color(0xFF57735E),  // = --rule#9FCFA9 @0.5 over --bg#0F1712（reading-J hr opacity:.5 焼き込み）
            divider          = Color(0xFF22301F),  // .t-dark --bar-line
            blockBackground  = Color(0xFF16211A),  // .t-dark --panel
            blockBorder      = Color(0xFF25332A),  // .t-dark --panel-bd
            accent           = Color(0xFFE2C878),  // .t-dark --accent（金）
            rule             = Color(0xFF9FCFA9),  // .t-dark --rule（森緑）
            isLight          = false,
        )
        // 明るい森の朝（LIGHT）。material/shelf は固定ダークのまま＝読書面だけ明転する。
        ReadingTheme.LIGHT -> ReadingColors(
            background       = Color(0xFFFAFBF8),  // .t-light --bg
            text             = Color(0xFF171E18),  // .t-light --ink
            textSecondary    = Color(0xFF6C746B),  // .t-light --soft（bg 4.65:1）
            infoText         = Color(0xFF6C746B),  // = --soft（bg 4.65:1 で意味テキスト AA も充足）
            placeholder      = Color(0xFFA5AAA3),  // = --soft#6C746B @0.6 over --bg#FAFBF8（焼き込み・非意味）
            navBackground    = Color(0xFFFAFBF8),  // .t-light --bar
            topBarBackground = Color(0xFFFAFBF8),
            topBarTitle      = Color(0xFF171E18),  // = --ink
            topBarIcon       = Color(0xFF171E18),
            ruby             = Color(0xFF5A6257),  // .t-light --ruby（bg 6.09:1 AA）
            hr               = Color(0xFFBAD2BF),  // = --rule#7BA986 @0.5 over --bg#FAFBF8（hr opacity:.5 焼き込み）
            divider          = Color(0xFFE7ECE4),  // .t-light --bar-line
            blockBackground  = Color(0xFFF1F4EE),  // .t-light --panel
            blockBorder      = Color(0xFFE2E7DE),  // .t-light --panel-bd
            accent           = Color(0xFF2E5B3E),  // .t-light --accent（深森緑）
            rule             = Color(0xFF7BA986),  // .t-light --rule
            isLight          = true,
        )
        // 琥珀の森（SEPIA）。同上＝読書面のみ暖色紙へ。
        ReadingTheme.SEPIA -> ReadingColors(
            background       = Color(0xFFF3E8CF),  // .t-sepia --bg
            text             = Color(0xFF332918),  // .t-sepia --ink
            textSecondary    = Color(0xFF8A7B5B),  // .t-sepia --soft（装飾・bg 3.41:1＝意味非搬送で可）
            // infoText は意味テキスト＝bg 4.5:1 最低線（ADR 0014）。--soft #8A7B5B は 3.41:1 で未達のため、
            // その色相(H≈40°)・彩度を保ち明度のみ暗化した #74684D で bg #F3E8CF 上 4.51:1 を満たす最小暗化（ADR 0014 InfoText 同型）。
            infoText         = Color(0xFF74684D),
            placeholder      = Color(0xFFB4A789),  // = --soft#8A7B5B @0.6 over --bg#F3E8CF（焼き込み・非意味）
            navBackground    = Color(0xFFEDE1C3),  // .t-sepia --bar
            topBarBackground = Color(0xFFEDE1C3),
            topBarTitle      = Color(0xFF332918),  // = --ink
            topBarIcon       = Color(0xFF332918),
            ruby             = Color(0xFF6C5D40),  // .t-sepia --ruby（bg 5.26:1 AA）
            hr               = Color(0xFFCEBA8E),  // = --rule#A88B4E @0.5 over --bg#F3E8CF（hr opacity:.5 焼き込み）
            divider          = Color(0xFFE0D3B0),  // .t-sepia --bar-line
            blockBackground  = Color(0xFFEBDEBE),  // .t-sepia --panel
            blockBorder      = Color(0xFFDBCB9E),  // .t-sepia --panel-bd
            accent           = Color(0xFF5E4A1C),  // .t-sepia --accent
            rule             = Color(0xFFA88B4E),  // .t-sepia --rule
            isLight          = true,
        )
    }

    // 本棚系の家系トークン（bookshelf-J 値・固定ダーク森面＝theme 非依存・ADR 0022 §2）。
    //   hairline = --line 合成 #2B2E29
    //   unreadLabel = --green #9FCFA9（「続きあり」=森緑がモックの意味色・外殻 #0C0E0B 上 11.05:1 AA）
    //   infoText = --soft 合成 #959A92（#0C0E0B 上 6.75:1 AA）
    private val PortalShelf = ShelfColors(LinePortal, GreenPortal, SoftPortal)
    override fun shelf(theme: ReadingTheme): ShelfColors = PortalShelf

    // 栞書影の紙／墨／識別色明度（固定ダーク森面＝theme 非依存）。
    //   J 本棚は横スワイプの没入扉構造で栞書影を使わないが、束の契約上供給する防御値（why=束 interface が全スキンへ要求）。
    //   紙 = 森のパネル面 #16211A（暗面で表紙を地から持ち上げる）／墨 = --ink 系 #E7ECE1／識別色明度 = 0.62f（暗面規則＝SkinC と同値）
    private val PortalShiori = ShioriColors(PanelPortal, InkTocPortal, 0.62f)
    override fun shiori(theme: ReadingTheme): ShioriColors = PortalShiori
}
