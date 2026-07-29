package com.novelreader.ui.theme.skins

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.novelreader.ui.theme.*

/**
 * スキン「カートリッジP」（視覚言語 P・正本モック docs/design-candidates/skins/{bookshelf,reading,toc,settings,discovery}-P.html）のトークン束。
 *
 * 退色プラスチック筐体（--plastic #dbd6c8）×緑の LCD（--lcd #a4af80）。パレットは退色プラ・墨インク・液晶緑・
 * 退色レッド（CTA）・退色ブルー（進捗）で閉じる。読書面はバックライト風のスクリーン（LIGHT --screen #e8e7d8）。
 *
 * 読書テーマ3変種（LIGHT/SEPIA/DARK・既定=LIGHT）＝「バックライトの相」。追補ドラフト reading-P-themes-draft.html を
 * 人間承認して3テーマ化した（2026-07-17・ADR 0022 §2 追記。当初は変種モック不在ゆえ [LIGHT] 開始だった）。
 * なぜ material/shelf/shiori が theme 非依存の固定筐体面か: 変わるのは嵌め込みスクリーンの読書面（--screen/--rd-*）
 * だけで、プラスチック筐体・緑LCDセーブバー・ピクセルHUD/コンソールは P の署名＝テーマ不変（J の「読書のみ変種」と同型）。
 * ゆえに material/shelf/shiori は theme 引数を無視して固定筐体値を返し、reading のみ3分岐 when で変種を出す（既定=先頭 LIGHT）。
 * SEPIA/DARK の地色は settings-P スウォッチ実値を正本値に昇格（値の正本＝reading-P-themes-draft.html＝reading-P.html の .t-*）。
 *
 * --line はモック間で家系分岐する（ADR 0022 §4）: 本棚/読書/目次=#bdb9a9・シート=#c4c0b1・発見=#c9c5b6 を Color.kt で
 * 別 val に分離し、outline/outlineVariant へ各々振る。ラベル/ジャンル識別色・燐光は構造画面専用パレット（ADR 0022 §5）。
 */
object SkinP : SkinTokens {

    // P は3テーマを持つが、作用するのは読書系トークンのみ（ADR 0022 §2 追記）。先頭 LIGHT が既定変種。
    override val supportedThemes: List<ReadingTheme> =
        listOf(ReadingTheme.LIGHT, ReadingTheme.SEPIA, ReadingTheme.DARK)

    // P の署名色は液晶グリーン（--lcd #a4af80＝LcdCartridge を流用）。
    override val signatureAccent: Color = LcdCartridge

    // 初弾は全スキンとも共有の本文タイポ（原則5「静謐は機能」＝字面はスキン間で不変）。
    override val typography: Typography = NovelReaderTypography

    // ============================================================
    // Material3 カラースキーム（lightColorScheme ベース＝退色プラは明面）。
    // error 系は D LIGHT と共有し意味色（赤）のブレを避ける（ADR 0021 裁定4）。
    // ============================================================
    private val CartridgeColorScheme = lightColorScheme(
        primary              = RedCartridge,        // 退色レッド（主 CTA・--red）
        onPrimary            = Color(0xFFFFFFFF),   // 白（red #b5564e 上 4.77:1＝plastic-hi は 3.79:1 で不足のため白）
        primaryContainer     = PlasticHiCartridge,  // 処理中バナー面
        onPrimaryContainer   = InkCartridge,        // 墨インク
        secondary            = BlueCartridge,       // 退色ブルー（進捗/装飾＝--blue）
        onSecondary          = Color(0xFFFFFFFF),   // 白（blue は装飾色＝D 同様 onSecondary を厳密 AA 保持しない。白 4.35:1）
        secondaryContainer   = PlasticHiCartridge,
        onSecondaryContainer = InkCartridge,
        tertiary             = LcdCartridge,        // 液晶グリーン（--lcd＝署名・装飾/面用途）
        onTertiary           = LcdInkCartridge,     // LCD 上の暗文字 #2b3616（lcd 上 5.49:1）
        tertiaryContainer    = PlasticHiCartridge,
        onTertiaryContainer  = InkCartridge,
        // error 系は D LIGHT と共有（意味色＝赤の階調はスキンで揺らさない・ADR 0021 裁定4）。
        error                = ErrorLight,
        onError              = OnErrorLight,
        errorContainer       = ErrorContainerLight,
        onErrorContainer     = OnErrorContainerLight,
        background           = PlasticCartridge,    // 筐体面 #dbd6c8
        onBackground         = InkCartridge,        // --ink 墨
        surface              = PlasticCartridge,    // 素地と同面
        onSurface            = InkCartridge,        // --ink 墨
        surfaceVariant       = PanelCartridge,      // --panel #cfcabb（一段沈めた面）
        onSurfaceVariant     = InkSoftCartridge,    // --ink-soft（装飾補助・deco スロット）
        surfaceContainer     = PlasticHiCartridge,  // --plastic-hi ハイライト面
        outline              = LineCartridge,        // 本棚/読書/目次-P --line #bdb9a9
        outlineVariant       = LineDiscCartridge,    // 発見-P --line #c9c5b6（家系分離＝ADR 0022 §4）
        inverseSurface       = InkCartridge,         // 明面反転（墨面）
        inverseOnSurface     = PlasticHiCartridge,   // 反転面上の淡色
        inversePrimary       = InversePrimaryCartridge, // red を暗面用に明化（#2c2b26 上 4.55:1）
        // surfaceContainer の未指定4段を P の面へ束ね直す（High=ダイアログ面＝筐体面 --plastic／
        // 残りは --plastic-hi ハイライト面）。放置すると M3 baseline の紫が確認ダイアログ等に出る
        // （機序と根拠＝SkinContainerTiers.kt。P にダイアログ意匠のモックは無い）。
    ).withSkinContainerTiers()

    override fun material(theme: ReadingTheme): ColorScheme = CartridgeColorScheme // 固定筐体面＝theme 非依存（chrome はテーマ不変・ADR 0022 §2）

    override fun reading(theme: ReadingTheme): ReadingColors = when (theme) {
        // 変種が作用するのは読書面（--screen/--rd-*）のみ（ADR 0022 §2 追記）。値の正本＝reading-P.html の .t-light/.t-sepia/.t-dark
        // （＝承認済み reading-P-themes-draft.html）。chrome（nav/topBar/divider/accent/rule）は筐体・緑LCD＝全テーマ不変。
        // LIGHT＝温白バックライト（現行正本と同値）。
        ReadingTheme.LIGHT -> ReadingColors(
            background       = Color(0xFFE8E7D8),  // .t-light --screen（バックライト風温白）
            text             = Color(0xFF26251D),  // .t-light --rd-ink（本文墨）
            textSecondary    = Color(0xFF5F5C50),  // .t-light --rd-soft（補助・screen 5.38:1）
            infoText         = Color(0xFF5F5C50),  // = --rd-soft（screen 5.38:1 で意味テキスト AA も満たすため textSecondary と同値）
            placeholder      = Color(0xFF969486),  // = --rd-soft#5F5C50 @0.6 over --screen#E8E7D8（焼き込み・非意味）
            navBackground    = Color(0xFFDBD6C8),  // reading-P .console/.hud=plastic 面（テーマ不変・グラデを代表単色 --plastic で表現）
            topBarBackground = Color(0xFFDBD6C8),  // 同 plastic 面（テーマ不変）
            topBarTitle      = Color(0xFF2C2B26),  // = --ink（バー面上の墨・テーマ不変）
            topBarIcon       = Color(0xFF2C2B26),  // reading-P .hud .ib=--ink（テーマ不変）
            ruby             = Color(0xFF5F5C4F),  // .t-light --rd-ruby（意味搬送小文字・screen 5.38:1 AA）
            // hr: reading-P hr は --rd-soft の破線グラデ opacity:.5＝代表単色は焼き込みで表現
            //   95*.5+232*.5=163.5→#A4／92*.5+231*.5=161.5→#A2／80*.5+216*.5=148→#94
            hr               = Color(0xFFA4A294),  // = --rd-soft#5F5C50 @0.5 over --screen#E8E7D8（モック hr 忠実値）
            divider          = Color(0xFFBDB9A9),  // reading-P --line（目次区切り・テーマ不変）
            // blockBackground: reading-P .t-light --rd-block-bg=rgba(43,54,22,.04) を --screen へ焼き込み
            //   43*.04+232*.96=224.4→#E0／54*.04+231*.96=223.9→#E0／22*.04+216*.96=208.2→#D0
            blockBackground  = Color(0xFFE0E0D0),  // .t-light --rd-block-bg 焼き込み（モック忠実値）
            blockBorder      = Color(0xFFDEDCCB),  // .t-light --screen-lo（.block border）
            accent           = Color(0xFFA4AF80),  // reading-P --lcd（章ルール i・save チップ地＝装飾/面用途・テーマ不変）
            rule             = Color(0xFFA4AF80),  // reading-P .chap-h .rule i=--lcd（章見出しルール＝accent と同値・テーマ不変）
            isLight          = true,               // 温白スクリーン＝明面
        )
        // SEPIA＝琥珀バックライト（settings-P スウォッチ #e4d2a4 昇格）。読書面のみ暖色へ・chrome は不変。
        ReadingTheme.SEPIA -> ReadingColors(
            background       = Color(0xFFE4D2A4),  // .t-sepia --screen
            text             = Color(0xFF2E2513),  // .t-sepia --rd-ink（screen 10.11:1）
            textSecondary    = Color(0xFF5C5236),  // .t-sepia --rd-soft（補助・screen 5.18:1）
            infoText         = Color(0xFF5C5236),  // = --rd-soft（screen 5.18:1 で意味テキスト AA も満たすため textSecondary と同値）
            placeholder      = Color(0xFF928562),  // = --rd-soft#5C5236 @0.6 over --screen#E4D2A4（焼き込み・非意味）
            navBackground    = Color(0xFFDBD6C8),  // plastic 面（テーマ不変）
            topBarBackground = Color(0xFFDBD6C8),  // plastic 面（テーマ不変）
            topBarTitle      = Color(0xFF2C2B26),  // --ink（テーマ不変）
            topBarIcon       = Color(0xFF2C2B26),  // --ink（テーマ不変）
            ruby             = Color(0xFF5E5334),  // .t-sepia --rd-ruby（screen 5.09:1 AA）
            // hr: --rd-soft#5C5236 @0.5 over --screen#E4D2A4
            //   92*.5+228*.5=160→#A0／82*.5+210*.5=146→#92／54*.5+164*.5=109→#6D
            hr               = Color(0xFFA0926D),  // .t-sepia hr 焼き込み（モック忠実値）
            divider          = Color(0xFFBDB9A9),  // --line（テーマ不変）
            // blockBackground: .t-sepia --rd-block-bg=rgba(64,50,16,.05) を --screen#E4D2A4 へ焼き込み
            //   64*.05+228*.95=219.8→#DC／50*.05+210*.95=202→#CA／16*.05+164*.95=156.6→#9D
            blockBackground  = Color(0xFFDCCA9D),  // .t-sepia --rd-block-bg 焼き込み（モック忠実値）
            blockBorder      = Color(0xFFD8C690),  // .t-sepia --screen-lo
            accent           = Color(0xFFA4AF80),  // --lcd（テーマ不変）
            rule             = Color(0xFFA4AF80),  // --lcd（テーマ不変）
            isLight          = true,               // 琥珀スクリーン＝明面
        )
        // DARK＝消灯の相（settings-P スウォッチ #2a2d24 昇格）。読書面のみ暗転・chrome は不変。
        ReadingTheme.DARK -> ReadingColors(
            background       = Color(0xFF2A2D24),  // .t-dark --screen
            text             = Color(0xFFDBD9C6),  // .t-dark --rd-ink（screen 9.84:1）
            textSecondary    = Color(0xFF999681),  // .t-dark --rd-soft（補助・screen 4.69:1）
            infoText         = Color(0xFF999681),  // = --rd-soft（screen 4.69:1 で意味テキスト AA も満たすため textSecondary と同値）
            placeholder      = Color(0xFF6D6C5C),  // = --rd-soft#999681 @0.6 over --screen#2A2D24（焼き込み・非意味）
            navBackground    = Color(0xFFDBD6C8),  // plastic 面（テーマ不変＝暗面でも筐体は退色プラのまま）
            topBarBackground = Color(0xFFDBD6C8),  // plastic 面（テーマ不変）
            topBarTitle      = Color(0xFF2C2B26),  // --ink（テーマ不変）
            topBarIcon       = Color(0xFF2C2B26),  // --ink（テーマ不変）
            ruby             = Color(0xFF98957F),  // .t-dark --rd-ruby（screen 4.63:1 AA）
            // hr: --rd-soft#999681 @0.5 over --screen#2A2D24
            //   153*.5+42*.5=97.5→#62／150*.5+45*.5=97.5→#62／129*.5+36*.5=82.5→#53
            hr               = Color(0xFF626253),  // .t-dark hr 焼き込み（モック忠実値）
            divider          = Color(0xFFBDB9A9),  // --line（テーマ不変）
            // blockBackground: .t-dark --rd-block-bg=rgba(233,231,216,.045) を --screen#2A2D24 へ焼き込み
            //   233*.045+42*.955=50.6→#33／231*.045+45*.955=53.4→#35／216*.045+36*.955=44.1→#2C
            blockBackground  = Color(0xFF33352C),  // .t-dark --rd-block-bg 焼き込み（モック忠実値）
            blockBorder      = Color(0xFF24271F),  // .t-dark --screen-lo
            accent           = Color(0xFFA4AF80),  // --lcd（テーマ不変）
            rule             = Color(0xFFA4AF80),  // --lcd（テーマ不変）
            isLight          = false,              // 消灯スクリーン＝暗面（ステータスバーアイコン明色）
        )
    }

    // 本棚系の家系トークン（bookshelf-P 値）。theme 非依存（筐体面はテーマ不変・ADR 0022 §2）。
    //   hairline = --line #bdb9a9
    //   unreadLabel/infoText = --ink-mid #5a574c（plastic #dbd6c8 上 4.98:1・--ink-soft は 3.13:1 で AA 不足のため昇格）
    private val CartridgeShelf = ShelfColors(LineCartridge, InkMidCartridge, InkMidCartridge)
    override fun shelf(theme: ReadingTheme): ShelfColors = CartridgeShelf

    // 栞書影の紙／墨／識別色明度。theme 非依存（筐体面はテーマ不変）。
    //   紙 = --plastic-hi #e9e5da（ハイライト面で表紙を素地から持ち上げる）／墨 = --ink #2c2b26
    //   識別色明度 = 0.52f（SkinD の LIGHT 値と同値＝明面書架の明度規則。SkinD.shiori(LIGHT) 参照）
    private val CartridgeShiori = ShioriColors(PlasticHiCartridge, InkCartridge, 0.52f)
    override fun shiori(theme: ReadingTheme): ShioriColors = CartridgeShiori
}
