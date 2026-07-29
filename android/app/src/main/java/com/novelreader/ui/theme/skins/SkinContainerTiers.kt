package com.novelreader.ui.theme.skins

import androidx.compose.material3.ColorScheme

/**
 * Material3 の surfaceContainer 4 段（Lowest / Low / High / Highest）をスキン自身の面へ束ね直す。
 *
 * ## 真因（2026-07-29 実機検証）
 * `lightColorScheme()` / `darkColorScheme()` は**未指定スロットを M3 baseline へフォールバック**させる。
 * baseline の neutral パレットは紫寄り（surfaceContainerHigh = light `#ECE6F0` / dark `#2B2930`）なので、
 * 全スキンが `surfaceContainer` だけを指定して残り 4 段を放置している現状では、その 4 段を引く M3 標準
 * コンポーネントが**スキンの外の紫面**で描画される。バイトコードで追った経路と実害:
 * - `AlertDialogDefaults.containerColor` → `DialogTokens.ContainerColor` = `SurfaceContainerHigh`
 *   ＝全スキンの確認ダイアログが紫面。K の削除確認（ユーザーが最も強く不可逆を読む面）を含む。
 * - `SwitchTokens` の uncheckedTrackColor = `SurfaceContainerHighest` ＝設定画面のトグルが紫面。
 * - 既定 `Card`（`FilledCardTokens` = Highest）/ `ModalBottomSheet`（`SheetBottomTokens` = Low）/
 *   elevated chip 系（Low）も同クラス。今は呼び出し側が containerColor を明示しているため表面化して
 *   いないだけで、色指定を忘れた新規呼び出しは即座に紫へ落ちる。
 *
 * ## High（＝ダイアログ面）に `surface` を採る根拠
 * 正本モックがダイアログ面を明示している: `docs/design-candidates/bookshelf-multiselect-D.html`
 * （削除確認）と `bookshelf-reimport-sweep-D.html`（再取込・トークンは skins/bookshelf-K.html の写経）の
 * どちらも `.dlg{background:var(--base)}`＝**ダイアログ面は素地そのもの**で、背景との分離は
 * `.scrim`（rgba(20,23,28,.34)）と `box-shadow` が担う。各スキンの `surface` はいずれもその素地
 * （D `--base` / C `--bg` / M 夜天の地 / P `--plastic` / J `--page`）に割り当て済みなので、
 * `surfaceContainerHigh = surface` がモック実色そのままの翻訳になる。
 * この app で High を引くのは AlertDialog だけ（`SearchBar` / `DatePicker` は未使用＝実測）。
 *
 * TODO(意匠): ダイアログ意匠のモックは **D/K の LIGHT しか存在しない**。SEPIA/DARK と C/M/P/J は
 * 「ダイアログ面＝素地・分離はスクリムと影」という上記の規則を移しただけで、実色の裏取りは無い
 * （暗面でも規則は成立する＝スクリムが背後だけを沈めるため相対的に素地が浮く、という機序の一致で採用）。
 * 各スキンのダイアログ意匠が起こされたら、その実色から採り直すこと。
 *
 * ## Lowest / Low / Highest に `surfaceContainer` を採る根拠
 * これらの面（シート・カード・スイッチのトラック）はモックにダイアログのような明示が無い。一方で
 * M / J / C / P は各スキンファイルに「surface-2 相当の別面を持たず同値」と既に明記しており、D も
 * 浮遊オーバーレイの実績値が `surfaceContainer` である（`DropdownMenu` は `MenuTokens.ContainerColor`
 * = `SurfaceContainer` で現行出荷済み）。よって新しい中間色を発明せず、既存のコンテナ面 1 枚へ畳む。
 *
 * ## 併せて確認した罠（将来の追加実装向け）
 * `Surface` は `containerColor == colorScheme.surface` のときだけ tonal elevation を適用する
 * （`ColorScheme.applyTonalElevation`）＝High を `surface` と同値にした以上、High を引く部品を
 * **非ゼロの tonalElevation で**追加すると `surfaceTint`（既定 = primary）が乗って面がアクセント色に
 * 染まる。AlertDialog は `AlertDialogDefaults.TonalElevation` が 0.dp なので現状は無害（1.3.2 の
 * バイトコードで確認済み。`DialogTokens.ContainerElevation` は Level3 だが AlertDialog は採用していない）。
 */
internal fun ColorScheme.withSkinContainerTiers(): ColorScheme = copy(
    // ダイアログ面＝素地（正本モックの `.dlg{background:var(--base)}`）。
    surfaceContainerHigh = surface,
    // 残り 3 段はモック不在＝各スキンが宣言する唯一のコンテナ面へ畳む（M3 baseline の紫へ落とさない）。
    surfaceContainerLowest = surfaceContainer,
    surfaceContainerLow = surfaceContainer,
    surfaceContainerHighest = surfaceContainer,
)
