package com.novelreader.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================
// ライトモード — 「紙と墨・朱墨色」パレット
// primary に日本の朱墨色を採用し、background は和紙の温かいオフホワイト。
// 完全な白・黒を避けることで目の疲れを軽減しつつ文学的雰囲気を出す。
// ============================================================

val PrimaryLight             = Color(0xFF7B3F2A)   // 朱墨色
val OnPrimaryLight           = Color(0xFFFFFFFF)
val PrimaryContainerLight    = Color(0xFFF5DDD5)   // 薄い桜色
val OnPrimaryContainerLight  = Color(0xFF4A1A08)

val SecondaryLight           = Color(0xFF5A6475)   // 青鼠
val OnSecondaryLight         = Color(0xFFFFFFFF)
val SecondaryContainerLight  = Color(0xFFDCE4F0)
val OnSecondaryContainerLight= Color(0xFF1A2535)

val TertiaryLight            = Color(0xFF4A7C6B)   // 緑青（進捗・完了状態）
val OnTertiaryLight          = Color(0xFFFFFFFF)
val TertiaryContainerLight   = Color(0xFFC8EDE2)
val OnTertiaryContainerLight = Color(0xFF002019)

val ErrorLight               = Color(0xFFBA1A1A)
val OnErrorLight             = Color(0xFFFFFFFF)
val ErrorContainerLight      = Color(0xFFFFDAD6)
val OnErrorContainerLight    = Color(0xFF410002)

val BackgroundLight          = Color(0xFFFAF7F4)   // 和紙色（完全白より温かい）
val OnBackgroundLight        = Color(0xFF1C1916)   // 墨色（純黒より柔らかい）
val SurfaceLight             = Color(0xFFFAF7F4)
val OnSurfaceLight           = Color(0xFF1C1916)
val SurfaceVariantLight      = Color(0xFFF0EAE4)   // カード背景
val OnSurfaceVariantLight    = Color(0xFF524540)
val SurfaceContainerLight    = Color(0xFFEEEAE6)   // 一段低い階層の背景
val OutlineLight             = Color(0xFF857470)
val OutlineVariantLight      = Color(0xFFD7C6BF)
val InverseSurfaceLight      = Color(0xFF342F2C)
val InverseOnSurfaceLight    = Color(0xFFF6EDE8)
val InversePrimaryLight      = Color(0xFFFFBBA3)

// ============================================================
// ダークモード
// background は墨色で読書にも適した暗さ。
// primary は朱色を明るくして視認性を確保。
// ============================================================

val PrimaryDark              = Color(0xFFFFBBA3)   // 明るい朱色
val OnPrimaryDark            = Color(0xFF4A1A08)
val PrimaryContainerDark     = Color(0xFF612B15)
val OnPrimaryContainerDark   = Color(0xFFFFDBCE)

val SecondaryDark            = Color(0xFFB8C8DE)
val OnSecondaryDark          = Color(0xFF283040)
val SecondaryContainerDark   = Color(0xFF3F4858)
val OnSecondaryContainerDark = Color(0xFFD4E4F8)

val TertiaryDark             = Color(0xFFA8D5C4)
val OnTertiaryDark           = Color(0xFF0F3A2B)
val TertiaryContainerDark    = Color(0xFF296350)
val OnTertiaryContainerDark  = Color(0xFFC4F0DE)

val ErrorDark                = Color(0xFFFFB4AB)
val OnErrorDark              = Color(0xFF690005)
val ErrorContainerDark       = Color(0xFF93000A)
val OnErrorContainerDark     = Color(0xFFFFDAD6)

val BackgroundDark           = Color(0xFF1C1916)   // 墨色背景
val OnBackgroundDark         = Color(0xFFEDE1DC)
val SurfaceDark              = Color(0xFF1C1916)
val OnSurfaceDark            = Color(0xFFEDE1DC)
val SurfaceVariantDark       = Color(0xFF2A2420)
val OnSurfaceVariantDark     = Color(0xFFD7C6BF)
val SurfaceContainerDark     = Color(0xFF252019)
val OutlineDark              = Color(0xFFA08580)
val OutlineVariantDark       = Color(0xFF524540)
val InverseSurfaceDark       = Color(0xFFEDE1DC)
val InverseOnSurfaceDark     = Color(0xFF342F2C)
val InversePrimaryDark       = Color(0xFF7B3F2A)
