package com.novelreader.ui.theme.skins

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.novelreader.ui.theme.*

/**
 * スキン「夜行C」（視覚言語 C・正本モック docs/design-candidates/skins/reading-C.html・bookshelf-C.html）のトークン束。
 *
 * 深炭×温白の没入（ADR 0021 初弾スキン）。パレットは深炭(bg)・温白(text)・月光スレート(accent)・灯火ember(進捗)で閉じる。
 *
 * なぜ固定1変種か: C は「夜読の没入」を主題にした暗面専用スキンで、ライト/セピア相当を持たない（ADR 0021 決定2＝
 * 各スキン1変種で開始）。そのため [supportedThemes] は DARK のみを宣言し、各 getter は theme 引数に関わらず同じ
 * 夜行値を返す（防御的＝万一 supportedThemes 外の theme が渡っても別スキンの色が漏れる穴を作らない。通常は
 * NovelReaderTheme が supportedThemes.first() へクランプするため DARK のみ到達する）。
 *
 * 2モックの値の食い違いは画面家系で分ける（D と同流儀・ADR 0014）: 読書系(ReadingColors)は reading-C 値、
 * 本棚系(ShelfColors)は bookshelf-C 値。Color.kt 側の Yako トークンを参照し、読書の導出インライン値のみ
 * SkinD 同様この場に集約する（生 Color(0xFF…) 直書きは SkinD の現行スタイルを踏襲）。
 */
object SkinC : SkinTokens {

    // C は固定1変種＝DARK 相当のみ（ADR 0021 決定2）。将来変種を足すならここへ追加する。
    override val supportedThemes: List<ReadingTheme> = listOf(ReadingTheme.DARK)

    // C の署名色は灯火 ember（wardrobe-D.html msC の acc 線 #C79A6A と同値＝EmberYako を流用）。
    override val signatureAccent: Color = EmberYako

    // 初弾は全スキンとも共有の本文タイポ（原則5「静謐は機能」＝字面はスキン間で不変）。
    override val typography: Typography = NovelReaderTypography

    // ============================================================
    // Material3 カラースキーム（darkColorScheme ベース＝夜行は暗面専用）
    // 各スロットのモック由来はコメントに明示。error 系は D DARK と共有し意味色のブレを避ける。
    // ============================================================
    private val NightColorScheme = darkColorScheme(
        primary              = SlateYako,             // bookshelf-C --slate（月光スレート）
        onPrimary            = OnSlateYako,           // bookshelf-C .resume 暗色文字（slate 上 6.26:1）
        primaryContainer     = SurfaceContainerYako,  // 処理中バナー面。C は着色コンテナを持たず surface-2 で閉じる
        onPrimaryContainer   = OnBackgroundYako,      // 温白（--text）
        secondary            = MutedYako,             // 未読の意味色スロット＝bookshelf-C --text-dim（D の青磁位置）
        onSecondary          = OnSlateYako,           // 暗色文字（muted 上）
        secondaryContainer   = SurfaceContainerYako,  // 同上＝surface-2 で閉じる
        onSecondaryContainer = OnBackgroundYako,      // 温白
        tertiary             = EmberYako,             // bookshelf-C --ember（灯火＝進捗系のアクセント）
        onTertiary           = OnSlateYako,           // 暗色文字（ember 上）
        tertiaryContainer    = SurfaceContainerYako,  // surface-2 で閉じる
        onTertiaryContainer  = OnBackgroundYako,      // 温白
        // error 系は D DARK と共有（意味色＝赤の階調はスキンで揺らさない・ADR 0021 裁定4）。
        error                = ErrorDark,
        onError              = OnErrorDark,
        errorContainer       = ErrorContainerDark,
        onErrorContainer     = OnErrorContainerDark,
        background           = BackgroundYako,        // bookshelf-C/reading-C --bg（深炭）
        onBackground         = OnBackgroundYako,      // --text（温白）
        surface              = BackgroundYako,        // 素地と同面（裁定4＝surface も #16181D）
        onSurface            = OnBackgroundYako,      // 温白
        surfaceVariant       = SurfaceYako,           // bookshelf-C --surface（カード面）
        onSurfaceVariant     = MutedYako,             // bookshelf-C --text-dim（補助テキスト・surface 上 4.65:1）
        surfaceContainer     = SurfaceContainerYako,  // bookshelf-C --surface-2
        outline              = OutlineYako,           // bookshelf-C --line
        outlineVariant       = OutlineYako,           // 同 --line（裁定4＝outline/outlineVariant 同値）
        inverseSurface       = InverseSurfaceYako,    // 明面反転（D DARK 流儀）
        inverseOnSurface     = InverseOnSurfaceYako,
        inversePrimary       = InversePrimaryYako,
    )

    override fun material(theme: ReadingTheme): ColorScheme = NightColorScheme // 固定1変種＝theme 非依存（防御的）

    override fun reading(theme: ReadingTheme): ReadingColors = when (theme) {
        // C は固定1変種＝どの theme 入力でも同じ夜行値を返す（防御的。通常は NovelReaderTheme が
        // supportedThemes=[DARK] へクランプするため DARK のみ到達するが、直接呼び・将来の呼び忘れでも
        // 別スキン色が漏れないよう全 theme を1本の夜行値へ束ねる）。値の正本＝reading-C.html。
        ReadingTheme.LIGHT, ReadingTheme.SEPIA, ReadingTheme.DARK -> ReadingColors(
            background       = Color(0xFF16181D),  // reading-C --bg（深炭）
            text             = Color(0xFFD8D1C5),  // reading-C --text（温白）
            textSecondary    = Color(0xFF7E7D77),  // reading-C --text-dim（装飾補助・bg 4.30:1＝意味非搬送で可）
            infoText         = InfoTextYako,       // 意味テキスト用の明化シェード（bg 4.80:1・Color.kt 参照）
            placeholder      = Color(0xFF545553),  // = textSecondary#7E7D77 @0.6 over 素地#16181D（焼き込み・非意味）
            navBackground    = Color(0xFF1E2128),  // bookshelf-C --surface（float ピル rgba(30,33,40,.82)≒同面）
            // 夜行はクロームを引く思想。上下バー面は素地と別面(--surface)、境界は --line ヘアラインで示す。
            topBarBackground = Color(0xFF1E2128),  // bookshelf-C --surface（バーは bg と別面＝D DARK の流儀）
            topBarTitle      = Color(0xFFD8D1C5),  // 温白（--text）
            topBarIcon       = Color(0xFF8B8A84),  // bookshelf-C iconbtn=--text-dim（バー面 4.65:1＝UI 3:1 充足）
            ruby             = Color(0xFF8C887E),  // reading-C --ruby（意味搬送小文字・bg 5.02:1＝AA）
            hr               = Color(0xFF2A2E37),  // reading-C hr background=--line（シーン区切り）
            divider          = Color(0xFF2A2E37),  // reading-C --line（目次区切り）
            blockBackground  = Color(0xFF1A1C21),  // = rgba(255,255,255,.018) over 素地#16181D（reading-C .block 焼き込み）
            blockBorder      = Color(0xFF2A2E37),  // reading-C .block border=--line
            accent           = Color(0xFF8E99B0),  // reading-C --slate（月光スレート＝現在章ハイライト・チップ選択）
            rule             = Color(0xFF5E6575),  // = --slate#8E99B0 opacity.6 over 素地（reading-C .chap-h .rule・装飾線 3.05:1）
            isLight          = false,              // 夜行は暗面＝ステータス/ナビのアイコンは明色
        )
    }

    // 本棚系の家系トークン（bookshelf-C 値）。固定1変種＝theme 非依存。
    //   hairline = --line #2C303A（読書系 divider #2A2E37 とは家系で分岐＝D 同流儀）
    //   unreadLabel/infoText = --text-dim #8B8A84（bg 5.13:1 で素のまま AA 充足＝MutedYako 共有）
    private val NightShelf = ShelfColors(OutlineYako, MutedYako, MutedYako)
    override fun shelf(theme: ReadingTheme): ShelfColors = NightShelf

    // 栞書影の紙／墨／識別色明度。固定1変種＝theme 非依存。
    //   紙 = bookshelf-C --surface #1E2128（暗面書架で表紙を素地から一段持ち上げる＝D DARK が専用暗紙を使った流儀）
    //   墨 = --text #D8D1C5／識別色明度 = 0.62f（暗面は正本 consistency-D の暗面規則と同値）
    private val NightShiori = ShioriColors(SurfaceYako, OnBackgroundYako, 0.62f)
    override fun shiori(theme: ReadingTheme): ShioriColors = NightShiori
}
