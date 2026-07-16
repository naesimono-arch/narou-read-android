package com.novelreader.ui.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.novelreader.ui.theme.skins.SkinD

// ============================================================
// 読書テーマ: ライト/セピア/ダークの3種類
// Composeネイティブ描画（NativeReadingScreen / NativeTableOfContentsScreen）の
// 全色をここに集約する。直書き色の散在を防ぐための単一の正典。
// （旧 cssAttribute はWebView時代の遺物のため削除済み）
// ============================================================
enum class ReadingTheme { LIGHT, SEPIA, DARK }

data class ReadingColors(
    val background: Color,        // 本文・目次の背景
    val text: Color,              // 本文文字
    val textSecondary: Color,     // 装飾的補助テキスト（見出し添え・プレースホルダ等・意味を運ばない）
    // 意味を運ぶ補助テキスト（エラー本文・空状態の説明など）。textSecondary を alpha で沈めると AA を割る
    // ため、素地上 4.5:1 を満たす専用暗化シェード（Material の InfoText と同値・ADR 0014-D）を使う。
    val infoText: Color,
    // プレースホルダ・無効ボタン文字などの「例示/不活性」テキスト（意味を運ばない＝WCAG 概ね対象外）。
    // textSecondary.copy(alpha=0.6) の二重帳簿を避け、その合成結果を素地上で焼き込んだ役割別シェード
    // （Design/10§9「alpha でなく専用シェード」＝コード衛生）。
    val placeholder: Color,
    val navBackground: Color,     // 下部ナビバー（使用側で半透明化する）
    val topBarBackground: Color,  // 読書画面トップバー
    val topBarTitle: Color,       // トップバーのタイトル文字
    val topBarIcon: Color,        // トップバー・ナビバーのアイコン
    val ruby: Color,              // ルビ文字
    val hr: Color,                // 本文中の水平線（シーン区切り）
    val divider: Color,           // 目次の区切り線
    val blockBackground: Color,   // 前書き・後書きブロック背景
    val blockBorder: Color,       // 前書き・後書きブロック枠線
    val accent: Color,            // 強調色（目次の現在章ハイライトなど）
    // 章見出しの短いルール色＝モック reading-D.html の --rule に対応。
    // なぜ accent と別トークンにするか: LIGHT/SEPIA は accent と同値だが DARK だけ
    // --rule #5E7E9C ≠ accent #6E96B8 で乖離するため、accent 流用では DARK が再現できない。
    val rule: Color,              // 章見出しルール（モック --rule）
    val isLight: Boolean,         // true ならステータスバーアイコンを暗色にする
)

/**
 * D 固定の読書配色アクセサ。値の正本は [SkinD.reading] へ移設済み（P1 スキン骨格）。
 *
 * なぜ温存するか: @Preview・スキン非依存文脈（CompositionLocal を持たない静的呼び出し）専用の
 * D 固定アクセサとして残す。実画面はスキン追従が必要なため [rememberReadingColors]（LocalSkinTokens
 * 経由）を使うこと。ここから直接読むと現在スキンに関係なく常に D の値になる。
 */
val ReadingTheme.colors: ReadingColors
    get() = SkinD.reading(this)

/**
 * 現在スキンとテーマから読書配色を取得する @Composable アクセサ（実画面はこれを使う）。
 * なぜ LocalSkinTokens 経由か: スキン導入で読書配色はスキン別になったため、D 固定の getter でなく
 * 現在スキンのトークン束（[LocalSkinTokens]）から引く。
 * なぜ remember 化するか: reading(theme) は呼ぶたびに 17 フィールドの ReadingColors を新規生成する
 * ため、読書画面の再コンポジション（スクロール保存・設定変更等）のたびにアロケートが走る。
 * スキン束(tokens)とテーマ(theme)を key にして、変わらない限り同一インスタンスを再利用し、
 * アロケートと下流の等値比較コストを避ける。
 */
@Composable
fun rememberReadingColors(theme: ReadingTheme): ReadingColors {
    val tokens = LocalSkinTokens.current
    return remember(tokens, theme) { tokens.reading(theme) }
}

// ============================================================
// 本棚系の追加色（Material スロットに収まらない画面家系トークン）。
// なぜ CompositionLocal か: ヘアラインは「役割」でなく「正本モックの家系」で値が分かれる
// （発見系 --line #ECEAE4 ＝ outlineVariant／本棚系 --hl/--track #E4E2DB）ため、
// colorScheme とは別口でテーマ追従させる（ReadingColors と同じ流儀・ADR 0014）。
// unreadLabel: 未読は意味を運ぶ文字＝4.5:1 最低線（ADR 0014-D）。ダークは既存 SecondaryDark を継続。
// infoText: 発見系の情報メタ（順位番号・連載状態・読了目安・最終更新・結果サブタイトル・未選択タブ）用。
//   OnSurfaceVariant（装飾的補助）は 4.5:1 未達のため情報用途だけを役割別トークンへ分離（同 ADR 0014-D 裁定）。
// ============================================================
data class ShelfColors(
    val hairline: Color,     // 目録区切り線・進捗トラック・スケルトン線（--hl/--track）
    val unreadLabel: Color,  // 「未読」ラベル文字
    val infoText: Color,     // 情報を運ぶ補助テキスト（発見系メタ）
)

val LocalShelfColors = staticCompositionLocalOf {
    ShelfColors(hairline = ShelfHairlineLight, unreadLabel = UnreadSeiji, infoText = InfoTextLight)
}

/**
 * 選択テーマを現在スキンが実際に持つ変種へ丸める（単一所有のクランプ）。
 *
 * なぜ NovelReaderTheme が唯一の所有者か: スキンは変種を1つ以上持つが全 [ReadingTheme] を持つとは限らない
 * （C 夜行は DARK 相当のみ）。永続キー `"reading_theme"` は後方互換で LIGHT/SEPIA/DARK を保持し続けるため、
 * C 選択中に theme==LIGHT が渡りうる。ここで supportedThemes 外なら `supportedThemes.first()` へ丸め、
 * colorScheme・reading・shelf・shiori・ステータスバー明暗のすべてを同じクランプ済み theme で引くことで、
 * 「Material だけ夜行・読書だけ別変種」のような家系間のズレを構造的に防ぐ（各スキン側の防御と二重化）。
 */
fun clampThemeToSkin(theme: ReadingTheme, tokens: SkinTokens): ReadingTheme =
    if (theme in tokens.supportedThemes) theme else tokens.supportedThemes.first()

@Composable
fun NovelReaderTheme(
    skin: Skin = Skin.WAMODERN_D,
    theme: ReadingTheme = if (isSystemInDarkTheme()) ReadingTheme.DARK else ReadingTheme.LIGHT,
    content: @Composable () -> Unit,
) {
    // スキン=トークン束の単一入口。Material 配色・本棚/栞トークン・字面はすべて現在スキンから引く。
    val tokens = skin.tokens
    // 選択 theme をスキンの持つ変種へ丸めた「実効 theme」を1変数に束ね、以降の全 getter へ渡す（単一所有）。
    val effectiveTheme = clampThemeToSkin(theme, tokens)
    val colorScheme = tokens.material(effectiveTheme)
    // 変種の明暗（ステータスバー明暗の正）。D では theme != DARK と同値だが、スキン導入後は
    // 「theme 値」でなく「変種が明色か（reading(theme).isLight）」が正（1変種スキンで theme==LIGHT
    // でも暗色変種がありうるため）。
    val readingColors = tokens.reading(effectiveTheme)

    // 本棚系の家系トークン（ヘアライン／未読ラベル）を現在スキン×実効テーマから provide する。
    // ヘアラインはセピア/ダークで OutlineVariant と同値だが、ライトは本棚系専用値（#E4E2DB）へ分岐。
    // 未読ラベルはライト/セピア=濃青磁 UnreadSeiji、ダークは暗面で合格済みの SecondaryDark を継続（SkinD.shelf）。
    val shelfColors = remember(tokens, effectiveTheme) { tokens.shelf(effectiveTheme) }
    // 栞書影の紙/墨/識別色明度も現在スキン×実効テーマから provide（旧 luminance/BackgroundSepia 推定を根絶）。
    val shioriColors = remember(tokens, effectiveTheme) { tokens.shiori(effectiveTheme) }

    // ステータスバーアイコンの色をテーマに合わせる（ライト/セピア=暗いアイコン、ダーク=明るいアイコン）
    // setDecorFitsSystemWindows は MainActivity で呼んでいるためここでは行わない
    // なぜここが唯一の所有者か: theme は appTheme 単一正本のため全画面でこの1式が常に正しい。
    // 画面側で個別に設定・復元すると正本とズレた値を書き戻す余地が生まれる
    // （実例: 読書画面の旧 DisposableEffect がシステム準拠へ「復元」し誤明暗になるバグ＝2026-07-08 撤去）。
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = readingColors.isLight
            // ナビバーのピル/ボタンもテーマ明暗に追従させる（ステータスバーと同格の正本設定）。
            // なぜ theme 値でなく変種の isLight か: スキン導入で「暗色の変種」が theme==DARK 以外にもあり得る
            //（1変種スキンは常に DARK 相当を LIGHT/SEPIA スロットで持つ等）ため、明暗は変種の明度が正。D では同値。
            insetsController.isAppearanceLightNavigationBars = readingColors.isLight
            // 没入トグルの下端ちらつき対策（2026-07-16）: XML テーマ既定（Material）の不透明ナビバー色と、
            // API29+ が透明バーへ強制する contrast scrim は、どちらも「バーの出没」と同期して帯を明滅させる。
            // バーは常時透明＋scrim 強制を無効にし、hide/show で変わるのをアイコン/ピルのフェードだけにする
            //（背後は Edge-to-Edge のアプリ描画＝window 背景と同じテーマ紙色で不変）。上端も同理由で透明化。
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
            // 残5: 没入トグルでステータスバー帯が黒⇄灰に明滅する問題の根治。読書の没入時は systemBars を
            // hide するが、Edge-to-Edge（setDecorFitsSystemWindows(false)）配下では最上端（旧ステータスバー
            // 領域）へアプリ描画が届かず window(DecorView) 既定の純黒[0,0,0]が透け、hide/show に同期して
            // 黒⇄灰フリップしていた（実機実測155px＝device-verify-followup 2026-07-16）。window 背景を
            // テーマ背景色へ再定義して「純黒→紙色」の低コントラスト差に落とす（colorScheme.background は
            // ReadingColors.background と全テーマで同値＝本文と完全同色でシームレス）。
            // なぜ画面側でなくここか: window はテーマ単一正本の所有物（上の isAppearanceLightStatusBars と同格）。
            // 画面側で個別設定・復元すると正本とズレる（旧 DisposableEffect の誤明暗バグと同型リスク）。
            // 2026-07-16 追補: 上端 letterbox の伸縮（幾何）自体は MainActivity の
            // layoutInDisplayCutoutMode=ALWAYS で不変化済み＝本設定は過渡フレームで window 面が露出した
            // 場合の保険（純黒でなく紙色が出る）として維持する。
            window.setBackgroundDrawable(ColorDrawable(colorScheme.background.toArgb()))
        }
    }

    CompositionLocalProvider(
        LocalSkinTokens provides tokens,
        LocalShelfColors provides shelfColors,
        LocalShioriColors provides shioriColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = tokens.typography,
            content = content,
        )
    }
}
