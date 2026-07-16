# ParserRules ハードコード脱却 — 設計 plan（2026-07-16）

**対象ブランチ: `pdf/parser-rules-relative`**（worktree `/home/qingj/wt/pdf-parser-rules-relative`・ext4＝素の `gw testDebugUnitTest` 可）

## 目的（handover.md 該当項目より）

`ParserRules.kt` の判定定数が現行 PDF 出力形状への絶対値ハードコード＝**生成側の微小な数値変更で全滅する脆さ**を解消する。
「別形状 PDF への対応」ではなく、**同じ形状のまま起きる微小変更への耐性**が目的。
制約: **ゴールデン回帰（3本＝N1453LW/N2959KI/N6169DZ）で挙動完全保存**を検証必須。

## 現状の全依存マップ（調査①ダイジェスト・2026-07-16 Explore 全数調査）

| 定数 | 値 | 使用箇所 | 判定 |
|---|---|---|---|
| FONT_SIZE_BODY_TITLE | 14.0 | TextProcessor:170 / ParserRules:52 | ==±0.1 で本文/題名分類 |
| FONT_SIZE_RUBY | 7.0 | TextProcessor:174 | ==±0.1 でルビ分類 |
| FONT_SIZE_PAGE | 12.0 | TextProcessor:157 | ページ番号除去1段目 |
| PAGE_NUM_Y | 528.98 | TextProcessor:158-159 | top/bottom ==±5.0（絶対Y） |
| RUBY_OFFSET_X | 14.84 | TextProcessor:48-51 | 親列逆算 ==±0.1 |
| LINE_STEP_X | 22.68 | TextProcessor:222,227 | 1.5倍閾値＋空行数除算 |
| FONT_SIZE_AUTHOR | 12.0 | PdfExtractor:163 | 表紙著者 ==±0.1 |
| COVER_FOOTER_Y(_TOL) | 500.0±30 | PdfExtractor:164 | フッター帯除外（絶対Y） |
| TOLERANCE | 0.1 | isClose 既定 | — |

ParserRules 外のマジックナンバー: TextProcessor:158-159 の absTol=5.0／:222 の 1.5倍係数／PdfExtractor:149 の absTol=0.1。
題名 maxSize（PdfExtractor:149）は既に動的。ChapterProcessor は座標非依存。
CharBox にページ寸法なし → GlyphStripper.startPage で mediaBox から取得する配線が必要。

## 方針（設計判断＝Claude）

**文書ごとの自動検出（DetectedRules）＋比率アンカー＋検出不能時は現行絶対値へフォールバック**。

1. **本文サイズ**: 本文ページ（先頭3/末尾1除外後）のフォントサイズ最頻値（0.1 バケット）→ `bodySize`。
2. **題名**: Bold かつ ≈bodySize（checkIsTitle を bodySize 引数化）。
3. **ルビ**: bodySize×0.5 アンカー（許容帯は実データで較正。N2959KI の 11.0pt/20.0pt を拾わないこと）。
4. **ページ番号**: 「bodySize 外サイズ × Yバケット」の (size,y) シグネチャを本文ページ横断で頻度検出（大多数ページに再出する組＝ページ番号）→ 検出値±5.0 相当で除去。ページ高さ相対でなく再出頻度ベース（ヘッダ/フッタ移動にも自動追従）。
5. **LINE_STEP_X**: 列 x0 の隣接差分の文書内最頻値 → `lineStep`。
6. **RUBY_OFFSET_X**: ルビ x0 −（左隣の本文列 x0）の文書内最頻値 → `rubyOffset`（マッチングは現行の逆算±0.1 を検出値で継続＝文書内一貫性は高いので保存的）。
7. **表紙著者**: authorSize＝「タイトル(最大)サイズ未満の最大サイズ群」等の相対規則＋フッター帯はページ高さ相対化（mediaBox 高さ要取得）。※実PDFの表紙寸法・サイズ分布での較正必須。
8. **フォールバック**: 検出不能（ヒストグラム空・曖昧）時は現行定数値。理由コメント必須。

## 検証（ゲートは Claude 自身が回す）

- `cd android && gw testDebugUnitTest`（既存488件＋新規）
- ゴールデン回帰: 経路確定は調査②完了（下記ダイジェスト）。一次候補＝**JVM golden テスト新設（Robolectric）**・分岐点は「Robolectric 上で PDFBoxResourceLoader.init が AAR 同梱 CMap/glyphlist を読めるか」→ スパイクで実証中。不成立なら候補A＝実機 PdfExtractorDeviceSpikeTest（assets/spike/ へ PDF+JSON 配置・実機は事前ユーザー確認）
- 新規テスト: 「全数値を一律シフトした合成 PDF 形状（13.8pt/6.9pt/別Y/別ステップ）でも同一出力」＝本リファクタの目的そのものの担保
- 実機絡みは事前にユーザー確認（memory `feedback-ask-before-device-testing`）

## 調査②ダイジェスト（2026-07-16 Explore 完了）

- **golden_regression.py は復旧不能**: `android/app/src/main/python` の `pdf_extractor`/`chapter_processor` を import するがリポジトリ全体に存在せず（Python engine 撤去済み）。
- **材料は全部 worktree 内に揃っている**: PDF現物3本＝`sample_pdfs/{N1453LW,N2959KI,N6169DZ}.pdf`（追跡済み）／golden 基準＝`ab-review/golden_regression/*.pdf.json`（メトリクス+body_sha256 形式・追跡済み）／突合ロジック＋合格ライン＝`PdfExtractorDeviceSpikeTest.buildSnapshot()`（実機 androidTest・Phase4 で恒久ゲート昇格済み。短中編2本は body_sha256 完全一致・N6169DZ は許容帯）。
- JVM で実PDFを回した前例は本モジュールに無し（src/test は全て合成 CharBox）。pdfbox-android は implementation 依存＝unit test classpath に載る。robolectric 4.11.1 導入済み。本番は `PDFBoxResourceLoader.init(context)`（NovelReaderApplication.kt:159）が CID→Unicode 解決の要。
- submission-B（ab-review/・JVM スタンドアロン）は **upstream apache-pdfbox** で JVM 抽出成功の実績あり＝pdfbox-android の JVM 動作証明にはならない。

## コード形状ダイジェスト（2026-07-16 Explore 完了・実装仕様の素材）

- **GlyphStripper.kt は存在しない**＝`GlyphStripper` クラス・`PdfExtractor` object・`normalizeGlyphUnicode`・`BookMeta` はすべて `PdfExtractor.kt` 1ファイル内。
- フロー: `PdfBookExtractor.process`(:68) → `extractBookMeta`(:136・表紙=loadFirstPage 1ページ目固定・**本文と別ストリップパス**) ＋ `runFinalEngine`(:181) → `loadPages`(:100・全ページ一括 `List<List<CharBox>>`) → `TextProcessor.processPages`(:126・文書一括受領) → ChapterProcessor（座標非依存確認済み）。
- **2パス化は容易**: processPages は外側 for(:136)がページ逐次処理だが、分類判定は CharBox 単体依存でページ間状態なし。全ページ分が既にメモリ上＝ループ前(:130直後)に事前スキャンパスを差し込める。ページ跨ぎ可変状態は currentParagraph/allParagraphs のみ。列ステップ判定(:222,:227)は prevX がページ内リセット＝ページ内完結。
- **mediaBox は現状どこにも保持されない**。唯一の捕捉点＝`GlyphStripper.startPage(page: PDPage)`(:64)。CharBox(text/fontName/size/x0/top/bottom/rubyText) にページ寸法なし→型追加が必要。
- 定数消費点: FONT_SIZE_BODY_TITLE=ParserRules:52,TextProcessor:170／RUBY=TP:174／PAGE=TP:157／PAGE_NUM_Y=TP:158,159(absTol=5.0)／RUBY_OFFSET_X=TP:48／LINE_STEP_X=TP:222,227(1.5倍係数)／AUTHOR=PdfExtractor:163／COVER_FOOTER_Y(±30)=PE:164。isClose 消費=TP:27,51,157-159,170,174/PE:149,163,164。
- 注入境界の構造事実: TextProcessor/PdfExtractor とも object＝**関数引数注入が自然経路**（processPages に rules 引数追加・runFinalEngine 内で検出→注入が配線しやすい）。表紙メタは別パスなので表紙用検出は extractBookMeta 側で別途。既存テスト（TextProcessorTest/PdfExtractorTest）は ParserRules 定数を合成入力生成に直接参照＝定数の可視性維持かテスト追随が必要。titleFromChars は既に動的（表紙内 maxSize）。
- checkIsTitle の Bold 判定（FONT_MARKER_TITLE）はサイズ検出と独立＝DetectedRules に含めるか固定残置かの切り分け要。

## スコープ追加（2026-07-16 ユーザー指示）: 単話（N2959KI）章分割問題の確認

handover.md「単話作品の変換で本文が『作品情報（プロローグ）』側に乗り章題名も出ない（n2959ki・未確定）」を本タスクに含めて確認する。
推定機序＝単話は章見出し/目次構造が無く、章分割が本文を作品情報ページの続き扱いで流し込む。
**注意＝N2959KI はゴールデン本**: 基準自体がこの挙動を「正」として固定している可能性→**修正はゴールデン更新とセットで判断**（＝ハードコード脱却の「挙動完全保存」ゲートと相互作用する。単話修正を入れるなら、リファクタの挙動保存検証とゴールデン更新の順序を設計すること）。

### 機序調査の結論（2026-07-16 Explore 完了）

- **golden はバグを固定していない（現物ベースで NO）**: N2959KI.pdf.json は 131章・章題正常（「１　嵐の夕暮れ」…）・head=「【題名】１　嵐の夕暮れ」の健全パース。「単話」という docs の呼称（golden-regression-baselines.md:28・ADR 0011:62）と現物131章が**矛盾**。
- **症状のコード機序は確定**: checkIsTitle=「fontName に Bold を含む AND size≈14.0」の AND 条件（ParserRules.kt:49-52）。題名該当 0 件 → TextProcessor が【題名】段落を一切挿入せず（TextProcessor.kt:166-192）→ splitIntoChapters が全段落を既定タイトル「作品情報・プロローグ」（ChapterProcessor.kt:28）の単一章へ流し込む（:43-45・SplitIntoChaptersTest:16-22 で固定済み挙動）。表示上の見出しはこの既定値がそのまま HtmlExporter へ流れたもの（HtmlExporter.kt:84-91）。ユーザー報告の「作品情報（プロローグ）」は「作品情報・プロローグ」の近似表記。
- **決定的な新事実（Claude 照合）**: golden の sample_pdfs/N2959KI.pdf は **891,461B**・2026-07-09 に実機 WebView 導線で取得した PDF は **contentLength=891538**（ADR 0011:59 実測）＝**77B 差の別バイナリ**。pdfnovels.net は毎回動的生成。
- **有力仮説（検証中）**: 新しい生成では題名グリフが Bold×14pt の AND 条件から外れ（フォント名 or サイズの生成側変更）、題名0件→症状発現。＝単話構造の問題ではなく**ハードコード脆さの実弾発現**の可能性。検証＝pdfnovels.net から n2959ki を新規生成DL→pdfminer.six フォントダンプで「Bold AND 14.0±0.1」該当数を実測（general-purpose 委譲・実行中）。
- 修正時の影響範囲: checkIsTitle 頑健化は N6169DZ の際どい許容帯（章題不一致≤15件）に波及し得る→ golden 3本での回帰必須。真の題名0件ケースを回帰で守るには新規 golden 追加が必要。

## スパイク＋較正の実測結果（2026-07-16・設計の較正データ正本＝scratchpad/calibration/*.json）

- **JVM golden ゲート成立**: Robolectric 上で `PDFBoxResourceLoader.init` が AAR 同梱 CMap/glyphlist を正常ロード＝実機と同一グリフ解決。`JvmGoldenSpikeTest`（未追跡・src/test）で N1453LW/N2959KI= body_sha256 完全一致・N6169DZ=許容帯内（章題不一致2件のみ＝実機11件より少ない。FF0D→2212 等9件は normalizeGlyphUnicode で吸収済み・残はアポストロフィ座標順 won't-fix）。所要 約6秒/8.9MB＝実機2分の桁違い高速。**リファクタの回帰ゲートは testDebugUnitTest 同乗で確定**。
- **較正実測（3本共通）**: mediaBox 全ページ 841.9×595.3 不変（横向き2段組）／本文14.0最頻・支配的／ルビ7.0=厳密に本文の0.5倍（N6169DZのみ・他2本はルビ0）／列ステップ最頻22.7（第2峰45.4≈2倍=段落間）／ルビオフセット最頻14.8だが**二峰性（9.8が約10%・5575件）**／ページ番号=(size12.0, top≈532) 再出率 0.999（N6169DZ）〜0.93（N1453LW）。
- **設計への含意**: ①PAGE_NUM_Y=528.98 は実測532と3ptズレ＝±5.0の緩判定で偶然通っているだけ→絶対Yをやめ「本文外サイズの最高再出 top」検出が妥当 ②ルビオフセットは単一値検出だと9.8群を取りこぼす→現行も14.84固定で同じ取りこぼしをしている可能性（挙動保存の観点では「検出最頻値のみ」が現行等価。9.8群の救済は挙動変更＝別判断）③N2959KI表紙の20pt(タイトル)/11pt(惹句)は本文帯から桁で分離＝検出で混同しない ④表紙12pt帯: 著者top≈347.7・フッターtop≈503.3（500±30が正しく捕捉）。

## 確定設計（2026-07-16 Claude 裁定・実装委譲仕様）

**コミット分割**: ①ゲート昇格（JvmGoldenSpikeTest→JvmGoldenRegressionTest 改名・KDoc「git管理外」誤記修正・ParserCalibrationProbe 削除）②DetectedRules リファクタ本体。単話問題は現物待ちで別トラック。

**DetectedRules.kt 新設**（data class: bodySize/rubySize/pageNumSize/pageNumY/rubyOffsetX/lineStepX ＋ companion FALLBACK=現行 ParserRules 定数・detect(charListsByPage)）:
- bodySize=全文書サイズ最頻値(0.1バケット)／rubySize=bodySize×0.5（厳密半分は実測確認済み）／pageNum=(bodySize外サイズ×top1.0バケット)のページ再出率最大シグネチャ・再出率≥0.5で採用／lineStepX=ページ内隣接列x0差分の最頻バケット→バケット内中央値で精緻化／rubyOffsetX=ルビx0−直左本文列x0の最頻バケット→中央値精緻化（二峰性の9.8群は現行も取りこぼしており挙動保存のため主峰のみ＝救済は将来の挙動変更として別判断）。各項目とも検出不能時は FALLBACK 値＋理由コメント。
- 注入=関数引数（object 構造維持）: processPages に `rules: DetectedRules = FALLBACK` 追加（既存テスト無改変で通る）・checkIsTitle に bodySize 引数化・runFinalEngine 内で detect→注入。Bold マーカー（FONT_MARKER_TITLE）はサイズ検出と独立のため固定残置。
- 表紙（extractBookMeta 別パス）: mediaBox は doc.getPage(0) から直接取得（GlyphStripper/CharBox の型変更を回避）。フッター帯=ページ高さ相対（比率は 500.0/595.28 相当→golden 上で現行帯と等価）・authorSize=「タイトル(maxSize)未満の最大サイズ群」検出＋fallback 12.0。較正 JSON の表紙実測（scratchpad/calibration/*.json 項目6）で裏取りしてから確定。
- 検証: golden 3本 sha256/許容帯の完全保存（窓ズレ＝pageNumY 532 vs 528.98 の±5窓差で sha が動いたら原因分析・帳尻合わせ禁止）＋合成シフトテスト（13.8pt/6.9pt/別Y/別ステップで同一出力＝目的そのものの担保）＋detect 単体テスト。

## 単話トラック — 真因確定（2026-07-16・実測）

- **単話の実体は N5368ML**（n2959ki への帰属はユーザー自身の申告どおり取り違え）。実機 DL フォルダから回収済み＝`sample_pdfs/N5368ML.pdf`（29,895B・sha256 7e709aa7…・20ページ）。7/9 の N2959KI(891,538B) は端末にも残存せず。
- **実測（pdfminer フォントダンプ）**: 本文 MS-Mincho 14.0pt=8,429字・**Bold×14.0pt はわずか4字＝「注意事項」のみ**（定型ページ見出し）。ほか 20pt Bold=64字（表紙タイトル）・12pt=141字・Helvetica 11pt/12pt＝golden 群と同一レイアウト。
- **真因**: 単話作品には章見出しグリフが本文中に存在しない → checkIsTitle 不発 → 【題名】マーカー0 → splitIntoChapters が全本文を既定「作品情報・プロローグ」単一章へ（機序は Explore 調査でコード確定済み）。生成側変更でもハードコード脆さでもなく、**題名0件ケースの仕様未設計**。
- **修正の性質＝挙動変更**（保存リファクタと別コミット・別判断）: 題名0件時の章タイトル扱い（候補: 作品タイトル流用／「本編」等）＋ N5368ML を golden 第4本として追加（題名0件ケースの恒久回帰）。ユーザー裁定待ち。

## 単話（n2959ki）トラックの現況

- 仮説「生成側フォント変更で Bold×14pt 全滅」は**棄却**: 新規生成 fresh(891,589B) のフォント構成は golden と完全同一（MS-Mincho,Bold 14.0pt=3,727字一致）。生成はバイト不安定（golden/7月9日版/fresh の3バイナリ全部違う）だがフォントは安定。
- 7/9 症状の真因は未確定（DL破損等の個体要因の可能性）。**確定にはユーザー実機の 7/9 現物 PDF or 当該蔵書の抽出結果が必要**（ユーザーへ依頼済み・sample_pdfs/N2959KI-20260709.pdf 等の名前で受領予定）。
- DL 導線の外部変化（付随発見・ADR 0011 の陳腐化）: pdfnovels.net サイト廃止→フォームは ncode.syosetu.com へ移設（checkpdfapi と PDF 実体配信のみ pdfnovels.net 残存）。アプリの WebView 導線への影響は別途確認要。

## 進行

- [x] 調査① 定数使用マップ（Explore 委譲・完了）
- [x] 調査② 検証経路（Explore 委譲・完了＝上記ダイジェスト）
- [x] コード形状精密ダイジェスト（Explore 委譲・完了＝上記）
- [x] JVM golden スパイク＝**成立**（Claude 自身のゲート実行で 3 tests / failures 0 を XML で確認済み）＋較正プローブ完了
- [x] 単話（N2959KI）章分割の機序調査（Explore 委譲・完了＝上記結論。golden はバグ非固定・77B差の別バイナリが真犯人候補）
- [x] n2959ki 新規生成 PDF の取得とフォント構成比較→**仮説棄却**（fresh もフォント構成同一）→ 実体は N5368ML（題名グリフ0件）と確定
- [x] リファクタ実装・diff 全量レビュー・ゲート自走（503件緑）・4コミット完了（ゲート新設/リファクタ/単話docs/stale-check）
- [x] 単話修正＝題名マーカー0件時に作品タイトル流用（ユーザー裁定）＋ golden 第4本 N5368ML 追加（golden4本✓・503件緑）
- [ ] 設計確定 → 実装委譲 → diff 全量レビュー → ゲート実行（Claude 自身）→ 台帳更新 → コミット承認
