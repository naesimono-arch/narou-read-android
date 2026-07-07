package com.novelreader.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================
// UI-n: 視覚言語 D「和モダン・余白」へ全面差し替え。
// なぜ旧「紙と墨・朱墨色」を捨てるか: UI-n は既存配色を踏襲せず白紙で作り直す方針（docs/decisions/0005-ui-n-visual-language-D.md）。
// 値は確定モック ui-n-phase0/bookshelf-D.html から写経。
//   素地 #FBFAF8 ／ 墨 #1C1F26 ／ アクセント藍 #1C3D5A ／ 補助 青磁 #9CB3A8 ／ ヘアライン #ECEAE4。
// 本棚は MaterialTheme.colorScheme をトークン経由で参照するため、この値変更だけで D へ追従する。
// ============================================================

val PrimaryLight             = Color(0xFF1C3D5A)   // 藍（FAB・主アクセント）
val OnPrimaryLight           = Color(0xFFFFFFFF)
val PrimaryContainerLight    = Color(0xFFD6E0E9)   // 淡い藍（処理中バナー背景）
val OnPrimaryContainerLight  = Color(0xFF0E2335)

// 青磁 #9CB3A8（モック bookshelf-D.html の --seiji）。本棚の「未読」ラベル等の補助色に使う。
// トレードオフ: 素地 #FBFAF8 上で約2:1 と低コントラスト＝モックが意図する「静かに沈める」表現だが
// 可読性は弱い。完全準拠を優先しモック値を採用（要・後日コントラスト再検証）。secondary は他画面で
// 直接未使用のため、この変更の波及は本棚の未読色のみ。
val SecondaryLight           = Color(0xFF9CB3A8)   // 青磁（未読ラベル）
val OnSecondaryLight         = Color(0xFFFFFFFF)
val SecondaryContainerLight  = Color(0xFFD9E4DF)
val OnSecondaryContainerLight= Color(0xFF18241F)

val TertiaryLight            = Color(0xFF1C3D5A)   // 進捗バーも藍で統一（Dは藍の細線で進捗を示す）
val OnTertiaryLight          = Color(0xFFFFFFFF)
val TertiaryContainerLight   = Color(0xFFD6E0E9)
val OnTertiaryContainerLight = Color(0xFF0E2335)

val ErrorLight               = Color(0xFFBA1A1A)
val OnErrorLight             = Color(0xFFFFFFFF)
val ErrorContainerLight      = Color(0xFFFFDAD6)
val OnErrorContainerLight    = Color(0xFF410002)

val BackgroundLight          = Color(0xFFFBFAF8)   // D 素地（寒色白）
val OnBackgroundLight        = Color(0xFF1C1F26)   // D 墨
val SurfaceLight             = Color(0xFFFBFAF8)
val OnSurfaceLight           = Color(0xFF1C1F26)
val SurfaceVariantLight      = Color(0xFFF1F0EC)   // カード背景（素地よりわずかに沈める）
val OnSurfaceVariantLight    = Color(0xFF7C808B)   // 補助テキスト（著者・未読・進捗）
val SurfaceContainerLight    = Color(0xFFEFEEE9)
val OutlineLight             = Color(0xFF9CA0A8)
val OutlineVariantLight      = Color(0xFFE4E2DB)   // ヘアライン・進捗トラック
val InverseSurfaceLight      = Color(0xFF2A2E35)
val InverseOnSurfaceLight    = Color(0xFFF2F1EE)
val InversePrimaryLight      = Color(0xFF9DB6CC)

// ============================================================
// セピア（読書テーマ SEPIA 用の暖色ライト変種。ReadingColors.SEPIA と同じ琥珀紙に Material 面を揃える）
// なぜ追加するか: かつてセピア選択時の本棚・発見系はライト配色を流用しており（darkTheme=false 扱い）、
// 「ライトとセピアの色味に差がなく同じ色に見える」実機フィードバック（2026-07-07）の主因だった。
// 素地・墨・面・ヘアラインだけを琥珀紙へ寄せ、secondary（青磁＝未読の意味色）と error はライトと
// 共有して意味色のブレを避ける（残りのトークンは SepiaColorScheme が LightColorScheme.copy で継承）。
// ============================================================

val PrimarySepia             = Color(0xFF2E4A60)   // 藍鼠（読書 SEPIA の accent と同値・暖色背景と調和）
val PrimaryContainerSepia    = Color(0xFFD8DFE2)   // 淡い藍鼠（処理中バナー背景の暖色変種）
val OnPrimaryContainerSepia  = Color(0xFF14293A)

val BackgroundSepia          = Color(0xFFF2E7CE)   // 琥珀の紙（ReadingColors.SEPIA.background と同値）
val OnBackgroundSepia        = Color(0xFF3D3121)   // 焦茶の墨
val SurfaceVariantSepia      = Color(0xFFEBDEBE)   // カード面
val OnSurfaceVariantSepia    = Color(0xFF8C7D5D)   // 補助テキスト
val SurfaceContainerSepia    = Color(0xFFECDFC0)
val OutlineSepia             = Color(0xFF9A8C6C)
val OutlineVariantSepia      = Color(0xFFE0D3B0)   // ヘアライン・進捗トラック

// ============================================================
// ダークモード（D の寒色を保った冷たい暗面）
// background は青みのある暗色。primary は暗背景で沈まない明るい藍。
// ============================================================

val PrimaryDark              = Color(0xFF8FB3D4)   // 明るい藍
val OnPrimaryDark            = Color(0xFF0E2030)
val PrimaryContainerDark     = Color(0xFF24435F)
val OnPrimaryContainerDark   = Color(0xFFCFE0EF)

val SecondaryDark            = Color(0xFFA9C2BB)   // 青磁の明色
val OnSecondaryDark          = Color(0xFF1B2A26)
val SecondaryContainerDark   = Color(0xFF36433F)
val OnSecondaryContainerDark = Color(0xFFCFE5DE)

val TertiaryDark             = Color(0xFF8FB3D4)   // 進捗も明るい藍で統一
val OnTertiaryDark           = Color(0xFF0E2030)
val TertiaryContainerDark    = Color(0xFF24435F)
val OnTertiaryContainerDark  = Color(0xFFCFE0EF)

val ErrorDark                = Color(0xFFFFB4AB)
val OnErrorDark              = Color(0xFF690005)
val ErrorContainerDark       = Color(0xFF93000A)
val OnErrorContainerDark     = Color(0xFFFFDAD6)

val BackgroundDark           = Color(0xFF14171C)   // D 寒色暗面
val OnBackgroundDark         = Color(0xFFC7CDD3)
val SurfaceDark              = Color(0xFF14171C)
val OnSurfaceDark            = Color(0xFFC7CDD3)
val SurfaceVariantDark       = Color(0xFF1B1F26)
val OnSurfaceVariantDark     = Color(0xFF8A929B)
val SurfaceContainerDark     = Color(0xFF181C22)
val OutlineDark              = Color(0xFF6B727B)
val OutlineVariantDark       = Color(0xFF2A2F38)
val InverseSurfaceDark       = Color(0xFFC7CDD3)
val InverseOnSurfaceDark     = Color(0xFF2A2E35)
val InversePrimaryDark       = Color(0xFF1C3D5A)
