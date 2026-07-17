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

// ============================================================
// スキンC「夜行」（正本モック docs/design-candidates/skins/reading-C.html・bookshelf-C.html）。
// 深炭×温白の没入。パレットは深炭(bg #16181D)・温白(text #D8D1C5)・月光スレート(#8E99B0)・灯火ember(#C79A6A)
// で閉じる（ADR 0021 改訂＝スキンごとに閉じたパレット規範・装飾新色はパレット改訂を要する）。
// 2モックの値の食い違いは画面家系で分ける（D と同流儀・ADR 0014）: 読書系は reading-C・本棚系は bookshelf-C 値。
// 固定1変種（DARK 相当のみ）＝ライト/セピア用の Yako トークンは存在しない。
// ここは Material カラースキームと家系トークンが参照する「Color.kt 側の正本値」。読書 ReadingColors の
// 直書き部（hr/rule/blockBg/placeholder 等の導出値）は SkinD 同様 SkinC.reading にインライン集約する。
// ============================================================

// --- 面・線・アクセント（bookshelf-C --bg/--surface/--surface-2/--line/--slate/--ember）---
val BackgroundYako          = Color(0xFF16181D)   // bookshelf-C/reading-C --bg（深炭素地）
val OnBackgroundYako        = Color(0xFFD8D1C5)   // bookshelf-C/reading-C --text（温白・低グレア）
val SurfaceYako             = Color(0xFF1E2128)   // bookshelf-C --surface（カード面・上下バー面）
val SurfaceContainerYako    = Color(0xFF252932)   // bookshelf-C --surface-2（ヒーロー面・FAB・コンテナ面）
val OutlineYako             = Color(0xFF2C303A)   // bookshelf-C --line（本棚系ヘアライン＝outline/outlineVariant 兼用）
val SlateYako               = Color(0xFF8E99B0)   // bookshelf-C/reading-C --slate（月光スレート＝accent/primary）
val OnSlateYako             = Color(0xFF15171C)   // bookshelf-C .resume の暗色文字（onPrimary・slate 上 6.26:1）
val EmberYako               = Color(0xFFC79A6A)   // bookshelf-C/reading-C --ember（灯火＝進捗/tertiary・bg 上 6.98:1）

// 本棚系の補助色（bookshelf-C --text-dim）。未読ラベル・メタ・onSurfaceVariant・topBarIcon・Material secondary が共有。
// なぜ専用の明化シェードを切らないか: #16181D 上 5.13:1／#1E2128 上 4.65:1 と素のまま WCAG 4.5:1（意味文字）・
// 3:1（UIアイコン）を満たす＝「未達なら色相彩度保持で明度のみ明化」の導出（ADR 裁定3）は条件が不成立のため不要
// （D DARK が SecondaryDark を使ったのと同格の役割トークンだが、C は muted 自身が AA を満たす）。
val MutedYako               = Color(0xFF8B8A84)   // bookshelf-C --text-dim（本棚 muted・未読・meta の意味色スロット）

// 読書「意味テキスト」用（エラー本文・空状態説明）。装飾補助 textSecondary(#7E7D77・bg 4.30:1) を alpha で沈めると
// AA を割るため、reading-C --text-dim #7E7D77 の色相・彩度を保ち明度のみ明化して bg #16181D 上 4.80:1 を満たす
// 役割別シェードにする（Material の InfoText と同型・ADR 0014-D／裁定3）。本棚系 infoText もこれを共有。
val InfoTextYako            = Color(0xFF868580)

// inverse 系は D DARK 流儀（inverseSurface=onSurface の明面／primary は明面用に暗化）で機械導出。
val InverseSurfaceYako      = Color(0xFFD8D1C5)   // 明面反転＝OnBackgroundYako（D DARK: inverseSurface=onSurface）
val InverseOnSurfaceYako    = Color(0xFF1E2128)   // 反転面上の暗色文字（surface 系・#D8D1C5 上で 10.6:1）
val InversePrimaryYako      = Color(0xFF4C566B)   // slate を明面用に暗化（#D8D1C5 上 4.86:1・色相保持の機械導出）

// ============================================================
// スキンM「星図」（正本モック docs/design-candidates/skins/{bookshelf,reading,toc,settings,discovery}-M.html）。
// 群青の夜天（#0B1330＝.phone グラデ起点）×金の結線星（--star #E9DDB4）。固定1変種（DARK・夜の相）。
// パネル面は素地へ半透明青を焼き込んだ極薄リフト。学名ドット4色は構造画面専用の識別パレット（ADR 0022 §5＝
// SkinTokens スロットを切らず Color.kt の系統別 val を構造画面が直接参照）。値はモック :root と指定ハードコード
// 由来。rgba は指定素地 #0B1330 へ合成した単色 hex にし焼き込み算式をコメントへ（丸め・目分量なし）。
// 読書 ReadingColors の値は SkinM.reading にインライン集約する（SkinC/SkinD の流儀＝checker はインライン Color を拾う）。
// ============================================================
val StarSeizu        = Color(0xFFE9DDB4)   // {…}-M --star（金の結線星＝accent/primary・bg 13.45:1）
val BrightStarSeizu  = Color(0xFFF5F1DE)   // reading-M .prog .tip／bookshelf-M .banner .kindle（最輝星＝進捗先端・tertiary）
val OnStarSeizu      = Color(0xFF141B33)   // bookshelf-M .const.hero .resume color/fill（hero ボタン実文字色＝onPrimary・star 上 12.53:1）
val DimSeizu         = Color(0xFF8791AD)   // {…}-M --dim（補助/未読/secondary・bg 5.81:1＝意味テキストの AA も充足）
val RubySeizu        = Color(0xFF9AA4C0)   // reading-M --ruby（ルビ＝意味搬送小文字・bg 7.34:1 AA）
val TextSeizu        = Color(0xFFDCE3F2)   // {…}-M --text（温白文字＝onBackground・bg 14.18:1）
val BaseSeizu        = Color(0xFF0B1330)   // {…}-M .phone linear-gradient 起点（夜天の地＝background/surface）
// --line rgba(150,168,214,.20) を #0B1330 へ焼き込み: 150*.2+11*.8=38.2→#27／168*.2+19*.8=48.8→#31／214*.2+48*.8=81.2→#51
val LineSeizu        = Color(0xFF273151)   // outline/divider/hr/blockBorder（settings-M の α.22 は .20 へ正規化＝ADR 0022 §4）
// パネル面 rgba(14,22,52,.42〜.5) を #0B1330 へ焼き込み（素地とほぼ同色ゆえ .42/.5 とも同一 hex へ丸まる）:
//   14*.5+11*.5=12.5→#0C／22*.5+19*.5=20.5→#14／52*.5+48*.5=50→#32（.42 側も同値）
val PanelSeizu       = Color(0xFF0C1432)   // bookshelf-M .banner rgba(14,22,52,.5)／.discover .42＝surfaceVariant/surfaceContainer
// bottombar rgba(14,21,48,.82) を #0B1330 へ焼き込み: 14*.82+11*.18=13.5→#0D／21*.82+19*.18=20.6→#15／48*.82+48*.18=48→#30
val BottomBarSeizu   = Color(0xFF0D1530)   // reading-M .bottombar（下部バー面＝nav/topBar 合成・bar 上 text 13.98:1）
// star #E9DDB4 を明面反転 #DCE3F2 用に暗化（色相保持の機械導出・#DCE3F2 上 4.56:1）
val InversePrimarySeizu = Color(0xFF766323)
// 学名ドット4色（bookshelf-M .const --id＝構造画面専用の識別パレット・ADR 0022 §5・SkinTokens 非搭載）
val SeizuIdGreen     = Color(0xFFB7C6A6)   // bookshelf-M hero --id
val SeizuIdPurple    = Color(0xFF9E93C6)   // bookshelf-M 2番目 --id
val SeizuIdSlate     = Color(0xFF7E8AA0)   // bookshelf-M unread --id
val SeizuIdRose      = Color(0xFFC6A0AE)   // bookshelf-M 4番目 --id
// ---- M 構造画面（星図 canvas・背景）専用の基色（ADR 0022 §5・bookshelf-M.html の <script>/背景ハードコード値）。
//      α はグラデ地の上へ層で載る描画層が .copy(alpha=) で付与する（単一素地が無く焼き込めない）。----
val MoonSlateSeizu     = Color(0xFF96A8D6)  // 経緯線/境界線/ゾーン塗りの月光スレート基色 rgba(150,168,214,α)
val FaintStarSeizu     = Color(0xFF96A6CE)  // 未点灯の結線・淡星の基色 rgba(150,166,206,α)（線系より2段沈む canvas 変種）
val DustSeizu          = Color(0xFFD2DEFA)  // 星屑 rgba(210,222,250,α)（discovery-M の 214,224,250 も知覚下微差ゆえ同帯へ正規化＝ADR 0022 §4）
val StarGlowInnerSeizu = Color(0xFFF0EBCD)  // 星光グロー中心 rgba(240,235,205,α)
val StarGlowOuterSeizu = Color(0xFFE1D2A0)  // 星光グロー外縁 rgba(225,210,160,α)
val StarCoreSeizu      = Color(0xFFF5F8FF)  // 星芯 rgba(245,248,255,α)
val SkyGradMidSeizu    = Color(0xFF0D1636)  // .phone 背景 linear-gradient 44% 停止（起点=BaseSeizu・終点=SkyGradEndSeizu）
val SkyGradEndSeizu    = Color(0xFF080E26)  // 同 100%
val SkyCloudSeizu      = Color(0xFF3A4E96)  // 右上の青雲 radial rgba(58,78,150,.28) 基色
val SkyHorizonSeizu    = Color(0xFF1E2A60)  // 下辺の地平光 radial rgba(30,42,96,.55) 基色
val ResumeGradStartSeizu = Color(0xFFEBDFB4) // hero .resume linear-gradient 始点
val ResumeGradEndSeizu   = Color(0xFFD8C68C) // 同 終点
// ---- 目次（星図）専用の直書き色（toc-M.html・ADR 0022 §5＝構造画面が Color.kt を直接参照）----
val TocInkSeizu     = Color(0xFFC9D0E1)   // toc-M .li .tx（章名の墨＝明朝・夜天 bg 11.8:1 で可読維持。モック cap「沈めるのは星グリフのみ」＝章名は状態で沈めない）
val TocCurStarSeizu = Color(0xFFF7F3E1)   // toc-M .li.cur .dot --dotc（現在章ドットの最輝星＝星グリフ・装飾・AA 対象外。BrightStarSeizu #F5F1DE とは非同値ゆえ新設）
// ---- 発見（星図）専用の直書き色（discovery-M.html・ADR 0022 §5＝構造画面が Color.kt を直接参照）----
val MilkyWaySeizu     = Color(0xFF788CD2)  // discovery-M 天の川の淡帯 rgba(120,140,210,α) 基色（linearGradient .06・αは描画層が付与）
val GenreChipInkSeizu = Color(0xFFC3CADB)  // discovery-M .gc（ジャンル入口チップの文字・夜天 bg 9.3:1）
val AuthorInkSeizu    = Color(0xFF818BA6)  // discovery-M .rk .a（一覧の作者名 byline。bookshelf-M .const .by #818BA6 と同値＝同役割の byline 色）

// ============================================================
// スキンP「カートリッジ」（正本モック docs/design-candidates/skins/{bookshelf,reading,toc,settings,discovery}-P.html）。
// 退色プラスチック筐体（--plastic #dbd6c8）×緑の LCD（--lcd #a4af80）。読書テーマ3変種（LIGHT/SEPIA/DARK）＝
// 「バックライトの相」（追補ドラフト reading-P-themes-draft.html を人間承認→3テーマ化・2026-07-17・ADR 0022 §2 追記）。
// 変わるのは読書面（--screen/--rd-*）だけ＝筐体/LCD/HUD/コンソールはテーマ不変（J の「読書のみ変種」と同型）。
// ゆえに material/shelf/shiori は theme 非依存の固定値（下の骨格色は LIGHT の筐体面）、reading のみ3分岐。
// --line は画面家系で3値に分岐（本棚/読書/目次=#bdb9a9・シート=#c4c0b1・発見=#c9c5b6＝ADR 0022 §4）。
// ラベル/ジャンル識別色は構造画面専用パレット（ADR 0022 §5）＝同値（w1=g3 等）は1 val に統合し両役割をコメント。
// 読書骨格色（screen/rd-*）は下に3テーマ分を登録＝値の正本。派生値（placeholder/hr/blockBg＝rgba 焼き込み）は
// SkinP.reading にインライン集約し算式コメントを併記（checker はインライン Color を拾う＝SkinC/SkinD 流儀）。
// ============================================================
val PlasticCartridge   = Color(0xFFDBD6C8)  // --plastic（筐体面＝background/surface）
val PlasticHiCartridge = Color(0xFFE9E5DA)  // --plastic-hi（ハイライト面＝surfaceContainer・栞紙）
val PlasticLoCartridge = Color(0xFFC3BFAE)  // --plastic-lo（陰影面）
val PanelCartridge     = Color(0xFFCFCABB)  // --panel（一段沈めた面＝surfaceVariant）
val InkCartridge       = Color(0xFF2C2B26)  // --ink（主文字＝onBackground/onSurface）
val InkSoftCartridge   = Color(0xFF79766B)  // --ink-soft（装飾補助メタ＝onSurfaceVariant・panel 上 2.78:1＝D 同格の deco スロット・意味メタは ShelfColors.infoText が担保）
val InkMidCartridge    = Color(0xFF5A574C)  // --ink-mid（意味メタの AA 値＝plastic #dbd6c8 上 4.98:1・--ink-soft が 3.13:1 で不足のため昇格）
val LineCartridge      = Color(0xFFBDB9A9)  // 本棚/読書/目次-P --line（outline・本棚 hairline・読書 divider）
val LineSheetCartridge = Color(0xFFC4C0B1)  // settings-P --line（シート系・家系分離）
val LineDiscCartridge  = Color(0xFFC9C5B6)  // discovery-P --line（発見系・家系分離＝outlineVariant）
val LcdCartridge       = Color(0xFFA4AF80)  // --lcd（液晶グリーン＝tertiary/署名・装飾/面用途に限る＝reading の章ルール i・save チップ地）
val LcdHiCartridge     = Color(0xFFB4BE92)  // --lcd-hi
val LcdInkCartridge    = Color(0xFF2B3616)  // --lcd-ink（LCD 上の暗文字＝onTertiary・lcd 上 5.49:1）
val LcdFrameCartridge  = Color(0xFF33352B)  // --lcd-frame
val RedCartridge       = Color(0xFFB5564E)  // --red（退色レッド＝主 CTA/primary）
val RedLoCartridge     = Color(0xFF8D4139)  // --red-lo
val BlueCartridge      = Color(0xFF5C7D96)  // --blue（退色ブルー＝secondary・装飾/進捗＝D 同様 secondary は意味色でなく onSecondary を AA 保持しない）
val BlueLoCartridge    = Color(0xFFC4C0B2)  // --blue-lo
val BlueInkCartridge   = Color(0xFF3F5A70)  // --blue-ink（章番号/SCORE 数値文字）
val ScreenCartridge    = Color(0xFFE8E7D8)  // reading-P --screen（バックライト風温白の読書面＝reading.background）
val ScreenLoCartridge  = Color(0xFFDEDCCB)  // reading-P --screen-lo（読書面陰＝reading.blockBackground）
val RdInkCartridge     = Color(0xFF26251D)  // reading-P --rd-ink（読書本文＝reading.text）
val RdSoftCartridge    = Color(0xFF5F5C50)  // reading-P --rd-soft（読書補助＝reading.textSecondary/infoText・screen 5.38:1 AA）
val RdRubyCartridge    = Color(0xFF5F5C4F)  // reading-P .t-light --rd-ruby（読書ルビ＝reading.ruby・screen 5.38:1 AA）
// ---- 読書テーマ SEPIA（.t-sepia＝琥珀バックライト・settings-P スウォッチ #e4d2a4 昇格）の骨格色（reading-P-themes-draft 承認値）----
val ScreenSepiaCartridge   = Color(0xFFE4D2A4)  // .t-sepia --screen（琥珀の読書面＝reading.background）
val ScreenLoSepiaCartridge = Color(0xFFD8C690)  // .t-sepia --screen-lo（読書面陰＝reading.blockBorder）
val RdInkSepiaCartridge    = Color(0xFF2E2513)  // .t-sepia --rd-ink（読書本文＝reading.text・screen 10.11:1）
val RdSoftSepiaCartridge   = Color(0xFF5C5236)  // .t-sepia --rd-soft（読書補助＝reading.textSecondary/infoText・screen 5.18:1 AA）
val RdRubySepiaCartridge   = Color(0xFF5E5334)  // .t-sepia --rd-ruby（読書ルビ＝reading.ruby・screen 5.09:1 AA）
// ---- 読書テーマ DARK（.t-dark＝消灯の相・settings-P スウォッチ #2a2d24 昇格）の骨格色（reading-P-themes-draft 承認値）----
val ScreenDarkCartridge    = Color(0xFF2A2D24)  // .t-dark --screen（消灯の読書面＝reading.background）
val ScreenLoDarkCartridge  = Color(0xFF24271F)  // .t-dark --screen-lo（読書面陰＝reading.blockBorder）
val RdInkDarkCartridge     = Color(0xFFDBD9C6)  // .t-dark --rd-ink（読書本文＝reading.text・screen 9.84:1）
val RdSoftDarkCartridge    = Color(0xFF999681)  // .t-dark --rd-soft（読書補助＝reading.textSecondary/infoText・screen 4.69:1 AA）
val RdRubyDarkCartridge    = Color(0xFF98957F)  // .t-dark --rd-ruby（読書ルビ＝reading.ruby・screen 4.63:1 AA）
// .t-dark のみ章番号を明化（暗面で --blue-ink #3f5a70 は沈むため。退色ブルーの色相を保つ）。--rd-num の DARK 値。
// P 読書は現状 ReadingColors に章番号スロットを持たず（共通読書エンジンが描画）未配線＝BlueInkCartridge 同様の登録値。
val BlueInkDarkCartridge   = Color(0xFF8FB3CD)  // reading-P .t-dark --rd-num（章番号/SCORE 数値の暗面明化）
// red #b5564e を暗い反転面 #2c2b26 用に明化（色相保持の機械導出・#2c2b26 上 4.55:1）
val InversePrimaryCartridge = Color(0xFFC77F79)
// 燐光/アーケード盤（discovery-P --board/--phos/--phos-dim＝発見構造画面専用・ADR 0022 §5）
val BoardCartridge     = Color(0xFF2C3618)  // --board（暗アーケード盤）
val PhosCartridge      = Color(0xFFB3BD82)  // --phos（燐光・明）
val PhosDimCartridge   = Color(0xFF97A56B)  // --phos-dim（燐光・暗）
// ラベル/ジャンル識別色（bookshelf-P --w1..w4／discovery-P --g1..g6＝構造画面専用・ADR 0022 §5・同値統合）
val CartridgeGold       = Color(0xFFC7A15C)  // --w1 ＝ --g3（ラベル金／ジャンル金）
val CartridgePurple     = Color(0xFF736C85)  // --w2（ラベル紫）
val CartridgeGreen      = Color(0xFF7F9A5E)  // --w3 ＝ --g2（ラベル緑／ジャンル緑）
val CartridgePlum       = Color(0xFFBF8F9C)  // --w4 ＝ --g1（ラベル梅／ジャンル梅）
val CartridgeGenreSlate = Color(0xFF8B84A0)  // --g4（ジャンル青灰）
val CartridgeGenreTaupe = Color(0xFF9A9384)  // --g5（ジャンル灰茶）
val CartridgeGenreClay  = Color(0xFFA88F7A)  // --g6（ジャンル土）
// 挿入中カセットの淡緑ボディ地（bookshelf-P .cart.inslot の linear-gradient(150deg,#e6ecd6,#d7e0c2)＝
// :root 変数外のインライン実値。焼き込みでなくモック直値をそのまま昇格・近似せず。構造画面専用＝ADR 0022 §5）。
val InslotHiCartridge   = Color(0xFFE6ECD6)  // .cart.inslot グラデ上（挿入中カセットの淡緑ハイライト）
val InslotLoCartridge   = Color(0xFFD7E0C2)  // .cart.inslot グラデ下

// ============================================================
// スキンJ「ポータル」（正本モック docs/design-candidates/skins/{bookshelf,reading,toc,settings,discovery}-J.html）。
// 物語への扉・金の敷居。署名3色（金=構造/強調・森緑=世界/続きあり/未取込・宵紫=他の扉 peek）は全画面不動点。
// material/本棚/目次/発見は theme 非依存の固定ダーク森面（モック実態・ADR 0022 §2）。3テーマ変種は読書のみ。
// 外殻（bookshelf/discovery --page #0C0E0B＝回廊）と内側（toc/reading --page #0F1712＝森の内側）は家系2トークン
// （ADR 0022 §4）。soft/line は rgba を各家系の素地へ焼き込んだ単色 hex（算式コメント）。ambient/glyph は
// 森の大気＝透過重ね描き用（構造画面専用・ADR 0022 §5・単一素地が無くグラデ上へ層で載るため焼き込まず alpha 保持）。
// 読書 3テーマ×各値は SkinJ.reading にインライン集約する（SkinC/SkinD の流儀＝checker はインライン Color を拾う）。
// ============================================================
val GoldPortal   = Color(0xFFE2C878)   // --gold（金＝構造/強調・primary/署名・全画面不動）
val GreenPortal  = Color(0xFF9FCFA9)   // --green（森緑＝続きあり/未取込・secondary/unread・外殻 #0C0E0B 上 11.05:1）
val PlumPortal   = Color(0xFFB79AD0)   // --plum（宵紫＝他の扉 peek・tertiary・全画面不動）
val PagePortal   = Color(0xFF0C0E0B)   // bookshelf/discovery-J --page（回廊の外殻＝固定ダークの地・material background）
val InnerPortal  = Color(0xFF0F1712)   // toc-J --page／reading-J .t-dark --bg（森の内側）
val InkPortal    = Color(0xFFF1F4EC)   // bookshelf/discovery-J --ink（外殻の主文字＝onBackground・#0C0E0B 上で高コントラスト）
val InkTocPortal = Color(0xFFE7ECE1)   // toc/settings-J --ink（内側面の主文字）
val PanelPortal  = Color(0xFF16211A)   // reading-J .t-dark --panel（森のパネル面＝material surfaceVariant/栞紙）
// --line rgba(233,240,228,.14) を外殻 #0C0E0B へ焼き込み: 233*.14+12*.86=42.9→#2B／240*.14+14*.86=45.6→#2E／228*.14+11*.86=41.4→#29
val LinePortal   = Color(0xFF2B2E29)   // bookshelf-J --line（外殻ヘアライン＝outline/本棚 hairline）
// --soft rgba(233,240,228,.62) を外殻 #0C0E0B へ焼き込み: 233*.62+12*.38=149→#95／240*.62+14*.38=154→#9A／228*.62+11*.38=145→#92
val SoftPortal   = Color(0xFF959A92)   // bookshelf-J --soft（外殻の補助＝onSurfaceVariant/本棚 infoText・#0C0E0B 上 6.75:1 AA）
// gold #E2C878 を明面反転 #F1F4EC 用に暗化（色相保持の機械導出・#F1F4EC 上 4.52:1）
val InversePrimaryPortal = Color(0xFF866C1D)
// 森の大気ambient／象徴文字glyph（reading-J .t-* の --amb1/--amb2/--glyph＝発見/読書の背景大気・ADR 0022 §5）。
// 透過重ね描き用ゆえ alpha を保持（0xAARRGGBB・α = round(a*255)）。焼き込むとグラデ上の層の透けが失われる。
val AmbDarkGoldPortal  = Color(0x38D6C478)  // .t-dark --amb1 rgba(214,196,120,.22)（α.22=0x38）
val AmbDarkMossPortal  = Color(0xB8274030)  // .t-dark --amb2 rgba(39,64,48,.72)（α.72=0xB8）
val GlyphDarkPortal    = Color(0x0FE9F0E4)  // .t-dark --glyph rgba(233,240,228,.06)（α.06=0x0F）
val AmbLightGoldPortal = Color(0x24C5A84A)  // .t-light --amb1 rgba(197,168,74,.14)（α.14=0x24）
val AmbLightMossPortal = Color(0x2E78966E)  // .t-light --amb2 rgba(120,150,110,.18)（α.18=0x2E）
val GlyphLightPortal   = Color(0x0F1C281C)  // .t-light --glyph rgba(28,40,28,.06)（α.06=0x0F）
val AmbSepiaGoldPortal = Color(0x2EB48C3C)  // .t-sepia --amb1 rgba(180,140,60,.18)（α.18=0x2E）
val AmbSepiaMossPortal = Color(0x33788250)  // .t-sepia --amb2 rgba(120,130,80,.2)（α.2=0x33）
val GlyphSepiaPortal   = Color(0x123C3014)  // .t-sepia --glyph rgba(60,48,20,.07)（α.07=0x12）
