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

// 青磁 #9CB3A8（モック --seiji）。装飾・面・署名（Web由来カードの縦ルール等）の補助色。
// 未読ラベルには使わない＝意味を運ぶ文字は WCAG 4.5:1 が最低線（ADR 0014-D で旧『モック完全準拠＞
// 可読性』の即席判断を上書き。未読は UnreadSeiji へ分離）。
val SecondaryLight           = Color(0xFF9CB3A8)   // 青磁（装飾・署名の補助色）
val OnSecondaryLight         = Color(0xFFFFFFFF)
val SecondaryContainerLight  = Color(0xFFD9E4DF)
val OnSecondaryContainerLight= Color(0xFF18241F)

// 未読ラベル用の濃青磁。青磁の色相・彩度を保ったまま、ライト素地 5.79:1／ライトカード 5.30:1／
// セピア素地 4.92:1／セピアカード 4.52:1 の全面で WCAG 4.5:1 を満たす最小暗化（ADR 0014-D 裁定）。
// ダークは SecondaryDark(#A9C2BB) が暗面 7:1 超のため専用値不要。
val UnreadSeiji              = Color(0xFF50685C)

// 情報を運ぶ補助テキスト（順位番号 rank>3・連載状態・読了目安・最終更新・結果サブタイトル・未選択タブ）用。
// OnSurfaceVariant（装飾的補助＝著者名・キャプション）は素地上 3.79:1 で通常文字 AA(4.5:1) 未達のため、
// 「意味を運ぶ文字は WCAG 4.5:1 ＞ 美学」（ADR 0014-D 審級）に従い、装飾用途は据え置き情報用途だけを
// 役割別トークンへ分離する（UnreadSeiji と同型の先例踏襲）。
//   Light #5C606D: 青灰色相を保った暗化。ライト素地 6.01:1／ライトカード 5.50:1 で AA 充足。
//   Sepia #6C6148: #8C7D5D の茶系色相・彩度（HSL 色相/彩度固定）を保った暗化。セピア素地 4.97:1／
//     セピアカード 4.57:1 で AA 充足（沈めすぎない最小暗化＋UnreadSeiji 同等の安全余裕）。
//   Dark  #8A929B: OnSurfaceVariantDark と同値（暗面 surface 5.70:1／card 5.25:1 で既に合格のため値は変えない）。
//     ただし役割別トークンとして独立させる＝値の単一性の役割分離先例（ヘアライン2トークン・64c52da）と同流儀。
val InfoTextLight            = Color(0xFF5C606D)
val InfoTextSepia            = Color(0xFF6C6148)
val InfoTextDark             = Color(0xFF8A929B)

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
val OnSurfaceVariantLight    = Color(0xFF7C808B)   // 装飾的補助（著者名・キャプション等）※未読は UnreadSeiji・進捗は primary へ分離済
val SurfaceContainerLight    = Color(0xFFEFEEE9)
val OutlineLight             = Color(0xFF9CA0A8)
val OutlineVariantLight      = Color(0xFFECEAE4)   // ヘアライン（モック --line。発見系・区切り線の正本値）

// 本棚系（目録/栞モック）の線・進捗トラック（--hl/--track #E4E2DB）。発見系の --line #ECEAE4 とは
// 画面家系で値が分かれる（役割でなく正本モックの家系で決まる＝ADR 0014 適用裁定）。
// セピア/ダークは OutlineVariantSepia/Dark と同値のため専用トークンは持たない。
val ShelfHairlineLight       = Color(0xFFE4E2DB)
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

// 書影の縦ルール（D 署名要素）のデフォルト藍。暗色スラブ上で沈まないようトークン藍 #1C3D5A より
// 明るい藍を使う（BookCover.kt から昇格＝直書き解消。読書 DARK accent と同値だが意味は独立）。
val BookCoverRuleIndigo      = Color(0xFF6E96B8)

// ============================================================
// 栞書影（本棚グリッド）専用の紙／墨（ダークのみ）。
// なぜ surface/onSurface でなく専用値か: 栞表紙は「紙に一点」の意匠のため、暗面 surface(#14171C)
// と同色だとダークの書架で表紙が地に沈む。表紙紙を暗面より一段持ち上げた #20232B にし、墨も
// onSurface(#C7CDD3) より明るい #ECE9E2 にして題字を読ませる（意匠正本 bookshelf-shiori-final-D.html の
// ダーク値と一致）。ライト／セピアは surface/onSurface がモック値と一致するためトークンを流用する。
val ShioriCoverPaperDark     = Color(0xFF20232B)
val ShioriCoverInkDark       = Color(0xFFECE9E2)

// 栞「了」朱印（読了バッジ）の朱色。意匠正本 bookshelf-shiori-grid-D.html の .seal。
// なぜ専用トークンか: 朱印は accent（title 由来の任意色相）とは無関係の固定「読了の徴」で、
// 和の朱肉色を意味色として持たせる（ライト #A1573F／ダーク #CC8B73＝正本値。枠・文字色に使う）。
val ShioriSealVermilion      = Color(0xFFA1573F)
val ShioriSealVermilionDark  = Color(0xFFCC8B73)
// 朱印のダーク背景敷き（正本 .seal の rgba(16,19,25,.5) の実色部。alpha は使用側で 0.5f）。
// ShioriCoverPaperDark(#20232B) より暗いのは正本どおり＝紙地でなく「面の奥」へ沈め枠と文字だけ浮かせる。
val ShioriSealScrimDark      = Color(0xFF101319)
