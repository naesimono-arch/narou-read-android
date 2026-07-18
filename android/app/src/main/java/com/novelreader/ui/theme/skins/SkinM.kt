package com.novelreader.ui.theme.skins

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.novelreader.ui.theme.*

/**
 * スキン「星図M」（視覚言語 M・正本モック docs/design-candidates/skins/{bookshelf,reading,toc,settings,discovery}-M.html）のトークン束。
 *
 * 群青の夜天（#0B1330）×金の結線星（--star #E9DDB4）。パレットは夜天の地・温白の文字・金の星・青灰の補助で閉じる。
 *
 * なぜ固定1変種か: settings-M がテーマ固定表示（C と同型）を明示＝M は「夜の相」専用（ADR 0022 §2）。そのため
 * [supportedThemes] は DARK のみを宣言し、各 getter は theme 引数に関わらず同じ星図値を返す（防御的＝万一
 * supportedThemes 外の theme が渡っても別スキンの色が漏れる穴を作らない。通常は NovelReaderTheme が
 * supportedThemes.first() へクランプするため DARK のみ到達する。SkinC と同型の1本化）。
 *
 * 5モックで同名変数が食い違う点は正規化して吸収する（ADR 0022 §4）: --line は settings のみ α.22 だが .20 へ正規化。
 * 学名ドット4色・パネル面などの構造画面専用パレットは Color.kt の系統別 val（Seizu*）を構造画面が直接参照する
 * （ADR 0022 §5＝SkinTokens にスロットを切らない）。読書 ReadingColors の直書き値は SkinC/SkinD 同様この場に集約する。
 */
object SkinM : SkinTokens {

    // M は固定1変種＝DARK 相当のみ（ADR 0022 §2・settings-M のテーマ固定表示準拠）。
    override val supportedThemes: List<ReadingTheme> = listOf(ReadingTheme.DARK)

    // M の署名色は金の結線星（bookshelf/reading-M --star #E9DDB4＝StarSeizu を流用）。
    override val signatureAccent: Color = StarSeizu

    // 初弾は全スキンとも共有の本文タイポ（原則5「静謐は機能」＝字面はスキン間で不変）。
    override val typography: Typography = NovelReaderTypography

    // ============================================================
    // Material3 カラースキーム（darkColorScheme ベース＝星図は夜の暗面専用）。
    // 各スロットのモック由来はコメントに明示。error 系は D DARK と共有し意味色（赤）のブレを避ける（ADR 0021 裁定4）。
    // ============================================================
    private val SeizuColorScheme = darkColorScheme(
        primary              = StarSeizu,        // 金の結線星（--star＝主アクセント/hero）
        onPrimary            = OnStarSeizu,      // bookshelf-M .const.hero .resume の実文字色 #141B33（star 上 12.53:1）
        primaryContainer     = PanelSeizu,       // 処理中バナー面。M は着色コンテナを持たずパネル合成面で閉じる
        onPrimaryContainer   = TextSeizu,        // 温白（--text）
        secondary            = DimSeizu,         // 補助/未読の意味色スロット＝--dim（bg 5.81:1）
        onSecondary          = OnStarSeizu,      // 暗色文字（dim 面上）
        secondaryContainer   = PanelSeizu,       // パネル合成面で閉じる
        onSecondaryContainer = TextSeizu,        // 温白
        tertiary             = BrightStarSeizu,  // 最輝星 #F5F1DE（進捗先端＝reading-M .prog .tip／banner .kindle）
        onTertiary           = OnStarSeizu,      // 暗色文字（最輝星上）
        tertiaryContainer    = PanelSeizu,       // パネル合成面で閉じる
        onTertiaryContainer  = TextSeizu,        // 温白
        // error 系は D DARK と共有（意味色＝赤の階調はスキンで揺らさない・ADR 0021 裁定4）。
        error                = ErrorDark,
        onError              = OnErrorDark,
        errorContainer       = ErrorContainerDark,
        onErrorContainer     = OnErrorContainerDark,
        background           = BaseSeizu,        // 夜天の地 #0B1330（.phone グラデ起点）
        onBackground         = TextSeizu,        // --text（温白 #DCE3F2）
        surface              = BaseSeizu,        // 素地と同面（M パネルは半透明で地に載る＝surface も #0B1330）
        onSurface            = TextSeizu,        // 温白
        surfaceVariant       = PanelSeizu,       // パネル合成面 #0C1432（rgba(14,22,52,.42〜.5) 焼き込み）
        onSurfaceVariant     = DimSeizu,         // --dim（補助テキスト・パネル面≒bg で 5.81:1）
        surfaceContainer     = PanelSeizu,       // 同パネル合成面（M は surface-2 相当の別面を持たず同値）
        outline              = LineSeizu,        // --line 合成 #273151（本棚系ヘアライン）
        outlineVariant       = LineSeizu,        // 同 --line（outline/outlineVariant 同値）
        inverseSurface       = TextSeizu,        // 明面反転＝onBackground（D DARK: inverseSurface=onSurface）
        inverseOnSurface     = PanelSeizu,       // 反転面上の暗色（パネル面系）
        inversePrimary       = InversePrimarySeizu, // star を明面用に暗化（#DCE3F2 上 4.56:1）
    )

    override fun material(theme: ReadingTheme): ColorScheme = SeizuColorScheme // 固定1変種＝theme 非依存（防御的）

    override fun reading(theme: ReadingTheme): ReadingColors = when (theme) {
        // M は固定1変種＝どの theme 入力でも同じ星図値を返す（防御的・SkinC と同型）。値の正本＝reading-M.html。
        ReadingTheme.LIGHT, ReadingTheme.SEPIA, ReadingTheme.DARK -> ReadingColors(
            background       = Color(0xFF0B1330),  // reading-M .phone グラデ起点（夜天の地）
            text             = Color(0xFFDCE3F2),  // reading-M --text（温白・bg 14.18:1）
            textSecondary    = Color(0xFF8791AD),  // reading-M --dim（補助・意味非搬送だが bg 5.81:1）
            infoText         = Color(0xFF8791AD),  // = --dim（bg 5.81:1 で意味テキスト AA も満たすため textSecondary と同値）
            placeholder      = Color(0xFF555F7B),  // = --dim#8791AD @0.6 over 素地#0B1330（焼き込み・非意味）
            navBackground    = Color(0xFF0D1530),  // reading-M .bottombar rgba(14,21,48,.82) 焼き込み
            topBarBackground = Color(0xFF0D1530),  // .topbar は透明で地に載るが、上下バー面を bottombar 合成に揃える
            topBarTitle      = Color(0xFF8791AD),  // reading-M .topbar .ct=--dim（章題・bar 上 5.73:1）
            topBarIcon       = Color(0xFFDCE3F2),  // reading-M .topbar .ib=--text（bar 上 13.98:1）
            ruby             = Color(0xFF9AA4C0),  // reading-M --ruby（意味搬送小文字・bg 7.34:1 AA）
            hr               = Color(0xFF273151),  // = --line 合成（.scene .ln＝シーン区切り）
            divider          = Color(0xFF273151),  // = --line 合成（目次区切り）
            blockBackground  = Color(0xFF121A38),  // = rgba(150,168,214,.05) over 素地#0B1330（reading-M .block 焼き込み）
            blockBorder      = Color(0xFF273151),  // reading-M .block border=--line 合成
            accent           = Color(0xFFE9DDB4),  // reading-M --star（金の星＝章番号/選択・現在地）
            rule             = Color(0xFFA6A08C),  // = --star#E9DDB4 @0.7 over 素地（reading-M .chap-h .rule .ln・装飾線）
            isLight          = false,              // 星図は暗面＝ステータス/ナビのアイコンは明色
        )
    }

    // 本棚系の家系トークン（bookshelf-M 値）。固定1変種＝theme 非依存。
    //   hairline = --line 合成 #273151
    //   unreadLabel/infoText = --dim #8791AD（本棚背景 #0B1330 上 5.81:1 で AA 充足＝素のまま意味色に使える）
    private val SeizuShelf = ShelfColors(LineSeizu, DimSeizu, DimSeizu)
    override fun shelf(theme: ReadingTheme): ShelfColors = SeizuShelf

    // 栞書影の紙／墨／識別色明度。固定1変種＝theme 非依存。
    //   紙 = パネル合成面 #0C1432（M 本棚は星座ラベル構造で栞書影を使わないが、束の契約上供給する防御値）
    //   墨 = --text #DCE3F2／識別色明度 = 0.62f（暗面規則＝SkinC/consistency-D の暗面値と同値）
    private val SeizuShiori = ShioriColors(PanelSeizu, TextSeizu, 0.62f)
    override fun shiori(theme: ReadingTheme): ShioriColors = SeizuShiori
}
