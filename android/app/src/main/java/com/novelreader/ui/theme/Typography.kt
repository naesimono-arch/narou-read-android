package com.novelreader.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ============================================================
// 視覚言語 D の字面トークン: 明朝（本文・題字）とゴシック（ラベル）の使い分け。
// モック ui-n-phase0/*-D.html は title=明朝 / label=ゴシック を署名とする。
// なぜ FontFamily.Serif を明朝として使うか: Android の Serif は CJK で Noto Serif CJK（＝明朝系）へ
// 解決され、フォント同梱なしで明朝が得られるため。読書本体（NativeReadingScreen）も同じ Serif を
// 既に本文明朝として使用しており、当端末で明朝レンダリングを確認済み。
// 直書き FontFamily.Serif の散在を防ぐため、明朝はこの単一トークン経由で参照する（④ Typography 土台）。
val MinchoFamily = FontFamily.Serif

// ============================================================
// 日本語最適化タイポグラフィ
// Material3 のデフォルトは英語前提で letterSpacing が広すぎるため、
// 日本語テキストに適した値（letterSpacing=0.sp）に上書きする。
// ============================================================
val NovelReaderTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
    ),
    displayMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp,
    ),
    displaySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp,
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,   // 英語デフォルト 0.15sp → 日本語は 0 が自然
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    ),
)

// ============================================================
// 役割別フォントサイズ・スロット（字面 SSOT＝ADR 0014 §A）
// なぜ M3 Typography と別口か: 各画面の `fontSize = N.sp` 直書き約145箇所は全てモック HTML の
// font-size px の写経値（2026-07-12 全数調査で px⇄sp 1:1 対応を確認）で、M3 の既定スケールと
// 一致しない（10.5/16.5 等）。M3 スロットへ寄せると見た目が変わるため、モック写経値をそのまま
// 「役割名の付いた単一値」としてここへ集約する＝値の変更ではなく命名・集約（見た目完全不変）。
// 値を変えるときは必ずここ1箇所＋対応モックの同時変更で行う（tools/check_design_tokens.py が
// 「各スロット値がモックの font-size px 集合に実在すること」を機械検査する）。
// 適用除外: 読書本文の fontSize（ユーザー設定値）・RubyText の既定/プレビュー・
// 文字サイズスライダーの見本グリフ「あ」（最小/最大の実寸見本そのもの）・M3 Typography 内部。
// ============================================================
val FontHomeTitle = 26.sp       // 本棚の大題字「本棚」
val FontScreenTitle = 24.sp     // 画面大見出し（見つける/探す/ジャンル）
val FontResultTitle = 21.sp     // 結果画面題字
val FontRankNumeral = 20.sp     // ランキングの順位数字
val FontSheetTitle = 18.sp      // シート/目次の題字（明朝）
val FontTopBarTitle = 17.sp     // トップバー題字（詳細/Web読書/取込）
val FontCardTitle = 16.5.sp     // 本棚カード題字（明朝）
val FontSectionTitle = 16.sp    // セクション/シート見出し（明朝）
val FontActionLabel = 15.sp     // アクション行ラベル・検索入力欄
val FontListItemTitle = 14.5.sp // 一覧項目題字（ランキング等）
val FontBody = 14.sp            // あらすじ本文・入力欄・メニュー項目・表紙題字
val FontPresetTitle = 13.5.sp   // 気分プリセット題字
val FontSubTitle = 13.sp        // 一覧サブ題字・メタ値・続きCTA・ヒント文
val FontButtonLabel = 12.5.sp   // 主ボタン文字・タブ文字
val FontCaption = 12.sp         // 注記・補足文・副ボタン/リンク・エラー文
val FontChipLarge = 11.5.sp     // 大ジャンル等のチップ文字（大）
val FontLabel = 11.sp           // 著者名・チップ・件数/進捗数字
val FontMicroLabel = 10.5.sp    // 極小ラベル（項目名・セクション極小見出し・メタキャプション）
val FontPresetCaption = 10.sp   // 気分プリセット説明
val FontSealBadge = 9.5.sp      // 『了』バッジ
val FontMissingBadge = 9.sp     // 「本文なし」欠落バッジ（案B・bookshelf-reimport-badge-D .miss の 9px）
val FontNavLabel = 9.5.sp       // 読書下端バーのアイコン下ラベル（C①案A・reading-gear-alt-D 案A② の 9.5px）
