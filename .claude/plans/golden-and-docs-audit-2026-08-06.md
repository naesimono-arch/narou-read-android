# golden 104枚 ＋ docs 96ファイルの監査（2026-08-06）

> **対象ブランチ: `review/code-health-2026-08-06`**（worktree・ext4）。**発見のみで、コードは1行も変えていない**（ユーザー裁定＝今回は見つけ出すにとどめる）。
> 出自: 同日のコード健全性監査（`code-health-audit-2026-08-06.md`）で**2つの構造的限界**が実証されたこと——
> ①**golden は退行しか止めない**＝初回記録時に既に壊れていた絵は「正」として永久に固定され CI は緑のまま
> （実例＝`KBottomNav_bookshelf_*_2.0.png` がボトムナビのラベル切れを保持）。
> ②**ドキュメントは名指しの実在だけが機械照合され、記述の内容が実装と食い違うかは誰も見ていない**
> （実例＝`PdfProcessingService.kt:226-230` の誤ったコメントが dead code の潜伏を許した）。
> この2つを「1件の事故」でなく「面としてどれだけ広がっているか」へ広げたのが本監査。
>
> 手段＝マルチエージェント監査21体（golden 5軸・docs 5軸・軸ごとに反証専任1体・統合1体）。95件の指摘のうち**32件を反証で却下**し63件が生存。
> **生データ（63件の JSON）は集約後に破棄した＝本書が正本**（証拠・機序・却下理由は全て本書へ取り込み済み）。
>
> ⚠️ **最重要の作業順序**: 壊れた golden を直すとき、**先に `recordRoborazziDebug` を打ってはいけない**
> （今の破綻がそのまま新しい正解として焼き付く）。実装を直してから再記録する。

---

**要約（5行）**

1. **golden は 104枚中20枚（19%）が壊れた絵を「正」として固定**している。うち15枚は fontScale 2.0 で、2.0 の golden は40枚しかないため**2.0 に限れば約4割が破綻**。孤児は0件（104↔104 の全単射）だが、孤児を検出する経路が Roborazzi の型として存在しない＝潜在。
2. 20枚の破綻は**根因4つ**（目次の現在地バー／設定シートのスクロール欠落／縦中横の寸法と向きの不一致／固定幅ラベル）に集約され、実装4系統を直せば17枚の再記録で片が付く。
3. **docs の陳腐化は統合後37件**（本節26件＋golden 節へ置いた記述系5件＋実装完了後に再確認6件）。腐りやすさは **台帳80% > patterns 56% > skills 33% > ADR 13% ≒ knowledge 9%**（本数あたりの汚染率）。
4. 分岐点は「**現在形で書いているか**」。ADR/knowledge は過去形の判断と機序なので時間に強く、台帳/patterns は現在値と手順を書くので実装が動くたび嘘になる。skills は密度3位だが「読んでから行動に変わるまでの距離」が最短で、実害の期待値では最上位。
5. 分類は **A（今すぐ直す）26件 / B（台帳へ登録）10件 / C（記録に留める）6件 / 却下3件 / 実装完了後に再確認8件**。

---

# 第1部　golden の判断

## 1-0. 投資判断サマリ

### 「golden という仕組みが今どれだけ信用できるか」

> **golden は「撮った面の、等倍（1.0）の、退行だけ」を見る仕組みであって、「レイアウト破綻を止める仕組み」ではない。** fontScale 2.0 の golden は40枚中15枚が壊れた絵を正として固定しており、検査として成立していない。

### 壊れた絵の内訳（20枚 / 104枚）

| 根因 | 枚数 | 該当 golden |
|---|---|---|
| 目次の現在地バーが章一覧を押し出す（4スキン同型） | 7 | `TocK_current_{light,dark,sepia}_2.0` / `TocK_ep4digits_light_2.0` / `NativeTableOfContentsScreen_{light,dark,sepia}_2.0` |
| 設定シートにスクロールが無く2項目が画面外 | 3 | `ReadingSettingsSheetContent_{light,dark,sepia}_2.0` |
| 縦中横の寸法（0.5em）と向き（正立）が不一致で字面が接触 | 4 | `VerticalParagraph_tatechuyoko_{light,dark}` / `VerticalChapterContent_normal_{light,dark}` |
| 固定幅・maxLines 無しのラベルが割れる／切れる | 6 | `KBottomNav_bookshelf_{light,dark,sepia}_2.0`（既知・出発点） / `DiscoveryHomeK_ranking_light_2.0` / `TocK_ep4digits_light_1.0` |
| 幅を分け合わない Row ＋ スクロール不在 | 1 | `BookshelfK_empty_light_2.0` |

注: G1 走査は `ContinuationCard_*_2.0` も破綻候補に挙げたが反証段階で落ちているため、この20枚には算入していない。

### 網羅の穴

- **画面ルート級で0枚**: 読書画面ルートとクローム（`NativeReadingScreen` / `ReadingChrome`）、装いの間（`WardrobeScreen`）。既存テストは semantics と算術のみで、幾何を見るアサーションは `src/test` 全体で4本しかない（`GridStatusLineWrap` / `ConstellationReadout` / `DiscoveryHomeKMood` / `RubyLayoutHelper`）。
- **さがす配下5ルート＋シート2種で0枚**（→ 要再確認節 F-1）。
- **条件の穴**: `VerticalChapterContent` の 2.0・sepia（虚偽の前提で省略）、D/C 目次の作品名サブ（`workTitle` 未指定）。
- **M/P/J/C スキンは全面0枚**。ADR 0027 で出荷対象外なので優先度は最下位でよいが、目次 HereBar の同型4スキンだけは例外（G-1）。

### 再記録すべき枚数と順序

**実装を直してから17枚**（G-1:7 / G-2:3 / G-3:4 / G-4:1 / G-5:1 / G-6:1）。KBottomNav の3枚を含めて20枚。**順序が本質**で、先に `recordRoborazziDebug` を打つと今の破綻がそのまま新しい正解になる（`docs/knowledge/golden-record-bakes-in-regressions.md`）。

### 追加すべき撮影条件（優先順）

1. 読書画面ルート＋クローム（没入 on/off × 3テーマ × 2スケール）
2. さがす配下（作品詳細・PDF取込・検索・結果・ジャンル・NcodeLinkSheet・SearchConditionSheet）※実装完了後
3. `VerticalChapterContent` の 2.0 × ep4digits と sepia
4. D/C 目次の `workTitle` 有り
5. 装いの間（3枚可視カルーセル）

### 「初回記録の誤りを固定する」構造的限界への対処（推奨順）

- **案1（機械・最安・推奨）— 2.0 破綻の走査を書く。** 今回の20枚は3パターンに収まり、いずれも純 Python の PNG デコードで検出できる（今回の監査で3人が独立に自作している＝実装コストは実証済み）。
  - (a) 1.0 に在った全幅罫線の本数が 2.0 で減る（目次の章消失＝7枚を一撃で検出）
  - (b) キャンバス最終行にインクが残る＝下端クリップ（K空棚・表示設定）
  - (c) 1.0 で1帯だったインクが 2.0 で同一 x 範囲の2帯へ割れる（順位・話数ラベル・ボトムナビラベル＝6枚）
  - この3本で20枚中14枚が機械検出できる。record 時と CI の両方で回せる。
- **案3（網羅の機械強制）— `MainActivity` の `composable(...)` ルート一覧 × THEMES × FONT_SCALES と golden 接頭辞を突合**し、未撮影ルートは理由付きの除外リストに載せないと赤。`DiscoveryHomeInvariantCoverageTest` の `acknowledgedOutOfScope` が既存パターンとして流用できる。同じスクリプトで孤児（G-7）も同時に閉じる。
- **案2（人間の儀式）— record 差分に「変化枚数・各枚の非背景画素の増減・新規追加枚」を出させ、人間の目視 OK を通す。** 規律自体は `golden-record-bakes-in-regressions.md` に既にあるが、台帳からその文書へ辿れない（G-16）。コストが高く案1で大半が取れるので優先度は3位。

---

## 1-1. 個別 finding

#### G-1【A】目次の現在地バーが fontScale 2.0 で章一覧を押し出す。4スキン同型で golden 7枚が固定済み
- **対象**: `ui/skins/k/TocK.kt:275` / `ui/NativeTableOfContentsScreen.kt:387` / `ui/skins/m/TocSkyM.kt:334` / `ui/skins/j/TocPortalJ.kt:295`。golden 7枚（上表）
- **食い違い**: 進捗 `Text(progress, fontSize = FontCaption, …)` に weight / maxLines / softWrap のいずれも無い。2.0 で「全4」「話・」「読了」「率」「25%」と縦5行に膨張し、K では同 Column の `LazyColumn(…weight(1f))` に残り高0が渡る。罫線走査で 1.0 は5本 → 2.0 は1本（＝章行0本）。D は章行が残るが第四章が初期表示外へ。
- **実害**: 1240話の本で目次のタップ対象が1件も存在しない。しかもこの golden を撮った `TocKEpisodeDigitsScreenshotTest.kt:34` は「現在地バーの『全N話・読了率X%』が桁数の多い N で崩れる」を**赤くなる条件として明記**しており、テストが自ら宣言した破綻を含む絵をそのテストが正解にしている。
- **直し方**: 進捗 Text に `weight(1f)` + `maxLines=1`（または Row を折返し可へ）。4実装を同便で直し7枚再記録。M/J は golden 0枚なので、同時に撮影条件を足さないと再発が見えない。

#### G-2【A】表示設定シートの「行間」「本文余白」は fontScale 2.0 で到達不能。golden 3枚が固定済み
- **対象**: `ui/ReadingSettingsSheet.kt:436-576`（ファイル全体に `verticalScroll` / `rememberScrollState` が0件）/ `ReadingSettingsSheetContent_{light,dark,sepia}_2.0.png`
- **食い違い**: 1.0 は3節とも 1280px 内（見出しの墨 y654 / y834 / y1014）。2.0 は文字サイズ節までで、以降は**見出しも値も端ラベルもつまみも持たない裸のトラック1本**（x124-595）と空白のみ。行間・本文余白の見出し文字は1画素も描かれていない。
- **実害**: 文字を大きくして使う層＝行間と余白を最も調整したい層が、2項目を一切変更できない。実機は `ModalBottomSheet` 経由でシート高が golden の全画面より低いため実害はより大きい。同ファイル `:400-402` は「このシートの Column はスクロールを持たず、末尾に足すと画面外に切れて到達不能になり得る」と危険を明文で認識しながら、縦書きトグルだけを上へ逃がしている。
- **直し方**: シート本体に `verticalScroll`。再記録3枚。

#### G-3【A】縦書きの単独半角数字が半角セルに正立で描かれ前後の字と接触する。golden 4枚が固定済みで、寸法コメントが真因を隠している
- **対象**: `typeset/render/PaintFontMetrics.kt:37-41` / `typeset/VerticalTypesetter.kt:161-163` / `typeset/render/GlyphRenderer.kt:51-52,98` / golden 4枚
- **食い違い**: 寸法側は「単一の半角 ASCII は 90 度回転して置くため、横幅（measureText）がそのまま縦の占有になる」と書くが、長さ1ランは `CharClass.UPRIGHT` へ上書きされ回転しない。全角の字面が 0.5em セルからはみ出し、連結成分解析で「月」「3」「日」が閾値150でも単一成分＝字面が literally 接触している。
- **実害**: 「3日」「1人」「A社」はなろう本文に頻出＝縦書きで毎回接触。純層テストは `FakeMonospaceMetrics` が等幅フェイクのため検出不能で、その KDoc 自身が「その差の吸収は P2 の実 Paint 実装＋golden で担保する」と golden に委ねている。**担保役に指名された唯一のゲートが、壊れた絵を正として修正を阻む側に回っている。**
- **直し方**: 正立で描くなら縦送りを実インク基準へ、回転させるなら分類を戻す。あわせて `PaintFontMetrics.kt:37` を「長さ1ランは UPRIGHT へ上書きされるためこの経路では成立しない」と but-for 条件込みで書き直す（回転が実際に効く経路＝4字以上ランとルビ親文字ではコメントは正しいので、全否定は誤り）。再記録4枚。

#### G-4【A】4桁目次の golden が「第1028話」の話数ラベル折返しを正として固定している
- **対象**: `TocK_ep4digits_light_1.0.png` / `ui/skins/k/TocK.kt:301-311 rememberEpLabelWidth` / `TocKEpisodeDigitsScreenshotTest.kt:31-32`
- **食い違い**: 他5行は x39-136 の1行なのに、第1028話だけ「第1028」(x39-113) と「話」(x39-60) の2行に割れる。整列幅は `measurer.measure("第${total}話")`＝total=1240 の1本だけを採寸して決めている。
- **実害**: 2026-07-29 実機の「第132話が 44dp に収まらず割れる」退行を二度と通さないために新設された golden が、初回記録の時点で同じ折返しを含む絵を正解として焼き付けた。テストが赤条件の筆頭に挙げた症状そのもの＝**この穴は塞がっていない**。
- **直し方**: 採寸を「実際に描く最長ラベル」か桁数×最大字幅の上界へ。真因が採寸文字列の選び方か Robolectric の丸めかは静的読解では確定していない（グリフ単位では 6/7/8 の墨が同幅）ので、両方を潰す上界方式が安全。再記録1枚。

#### G-5【A】ランキングの順位「10」が fontScale 2.0 で「1」と「0」に割れる
- **対象**: `ui/discovery/DiscoveryCommon.kt:226-237`（`Modifier.width(34.dp)` のみ・maxLines も softWrap も無し）/ `Typography.kt:140 FontRankNumeral = 20.sp` / `DiscoveryHomeK_ranking_light_2.0.png`
- **食い違い**: 1セルが縦2段に割れ、区切り線は上段の上にしか無い（＝2件のランクが並んでいるのではない）。同ファイル `:256-258` には「作者名が長いと右のジャンルタグが狭いカラムに押し出され1文字ずつ縦積みになる実機バグ」の対処コメントが既にあるのに、同じ行の順位列には及んでいない。
- **実害**: 順位はランキングの情報本体で、10位が「1」に読める＝事実誤認を招く表示。なろうランキングは常に10件出るので 2.0 利用者は毎回踏む。
- **直し方**: 順位 Text に `maxLines=1` + `softWrap=false`（幅は fontScale 追従）。再記録1枚。

#### G-6【B】K の空棚 CTA「PDFを追加」が fontScale 2.0 で1文字ずつ縦積みになり下端で切れる
- **対象**: `ui/skins/k/BookshelfK.kt:1334-1338`（weight も折返しも無い Row）/ `:1315-1319`（verticalScroll 無し）/ `:309-314`（`weight(1f)` の有界領域を渡す）/ `BookshelfK_empty_light_2.0.png`
- **食い違い**: 1.0 では2ボタンが横並びで両方1行。2.0 では第1ボタンが実寸を取り切り、輪郭ボタンは幅63.5dp まで圧縮されて「P」「D」「F」と縦積み、さらに y1279（キャンバス最終行）まで続いて切断、y≈1140 以降は FAB に覆われる。
- **実害**: 蔵書ゼロ＝新規ユーザーが最初に見る画面で導線の片方が判読不能。ただし同画面の FAB「＋ PDFを追加」は 2.0 でも完全に読めるため機能ブロッカーではない。D の空棚（CTA1個）は破綻していない＝K固有の Row 構成が原因。
- **直し方**: Row を FlowRow 化するか各ボタンへ weight、`KEmptyState` に verticalScroll。再記録1枚。

#### G-7【B】verify は孤児 golden を型として検出できない（現在0件＝潜在）
- **対象**: `.github/workflows/ci.yml:97` / Roborazzi 1.70.0 の sealed `CaptureResult`（Added / Changed / Recorded / Unchanged の4種のみ）
- **食い違い**: 照合は「撮った1枚 → 同名 golden」の片方向のみで、PNG 側から走査する経路が実装にも型にも無い。`cleanupOldScreenshots` は未設定（`roborazzi { }` ブロックも gradle.properties のキーも不在）で、しかも動作は無言の delete＝落とさない。
- **実害**: `Screenshot*Test` を1クラス削除すると PNG は git に残り verify は緑＝台帳 B 表 `removed-hook-leaves-dead-consumer`（撤去済みフックの残骸が13日間 dead）と完全に同型。caseId 改名は `recordRoborazziDebug` を打った瞬間に旧名が無言で孤児化する。
- **直し方**: 期待名生成（`@Parameters` × `goldenName`）と `ls` の集合差分を出すスクリプトを CI の verify 直後へ。今回の統合で作った 104↔104 の全単射表がそのまま仕様。案3（網羅強制）と同じスクリプトに同居できる。

#### G-8【B】出荷面のうち画面ルート級で golden 0枚が2群ある（読書クローム・装いの間）
- **対象**: `ui/NativeReadingScreen.kt`（1636行・ルートは `MainActivity` の `reading/{bookId}/{startFile}`）/ `ui/WardrobeScreen.kt`
- **食い違い**: golden 104枚の接頭辞は15種で、`NativeReadingScreen*` / `ReadingChrome*` / `WardrobeScreen*` は0枚。存在するのは semantics アサーション（TopPill / A11y / BarAlpha / BottomBarMirror / WindowContract）と fling 算術のみ。
- **実害**: KBottomNav 型の欠陥（ノードは合成ツリーに在り semantics も通るが画素として読めない）を原理的に1件も検出できない。文書上の免除も無い（ADR 0009 増補1 は screenshot の目的に「フォントスケール拡大時のレイアウト破綻」を明記している）。
- **注**: 「読書画面が0枚」は言い過ぎで、本文組版・章見出し・設定シート・目次・エラー面は撮られている。0枚なのは**画面ルートとクローム**。台帳へ載せるときは題を狭めること。

#### G-9【B】`VerticalChapterContent` は虚偽の前提（fontScale 非依存）で 2.0・sepia を落としている
- **対象**: `VerticalChapterContentScreenshotTest.kt:25-26` / `ui/VerticalChapterContent.kt:119-120, 218-219, 76, 301, 323`
- **食い違い**: KDoc は「本文は Canvas 直描き（px 直指定）で fontScale に依存せず、SEPIA・2.0 は描画分岐に新経路を通さない」と言うが、実装のコメント自身が `// sp→px（fontScale 込み＝WCAG 1.4.4）` と書き、話数ラベルは Canvas でなく実 Compose Text（`maxLines=1, softWrap=false` を Column へ縦積み）。fontScale は `captureThemed` が `LocalDensity` へ与える＝`sp.toPx()` に直接効く経路。
- **実害**: 同じ話数ラベルの横書き版は「最長ラベル×最大フォント＝最も折り返しやすい worst case」として 2.0 を必ず1枚撮る（`ChapterHeaderEpisodeDigitsScreenshotTest.kt:45`）のに、縦書き版だけ 1.0 しか無い。sepia 0枚でラベル色 `colors.accent` も無検査。
- **直し方**: 2.0 × ep4digits と sepia を追加し、KDoc の虚偽前提を削除。

#### G-10【B】ep4digits の縦書き golden は header と字数が同じで、桁数の負荷を一切かけていない
- **対象**: `VerticalChapterContentScreenshotTest.kt:68, 119` / `kanjiNumber`（`ui/skins/m/ReadingChromeM.kt:391-407`）
- **食い違い**: `kanjiNumber(1024)`＝「千二十四」は `kanjiNumber(127)`＝「百二十七」と同じ4字＝同じ6マス。header との画素差は2マス分（差分505px・bbox x619-638）のみで、ラベル列のマス位置は完全一致。真の最長は 9999→「九千九百九十九」の7字＝11マス。算用数字4桁を1マスへ束ねる経路（`displayLabel()`→`numLabelUnits`）を通す golden は0枚。
- **実害**: コミット d9f5f5d が「4桁話数の整列テスト」と記録し、テスト本文も「桁数の穴を塞ぐ流儀」と宣言しているため担保済みに読めるが、桁数由来のレイアウト負荷はゼロ。golden 2枚が事実上の重複として維持コストだけ増やしている。

#### G-11【B】D/C 目次の golden 6枚が作品名サブを一度も撮っていない
- **対象**: `NativeTableOfContentsScreenScreenshotTest.kt:46-54`（`workTitle` を渡さない）/ `ui/NativeTableOfContentsScreen.kt:109`（既定 `null`）/ 実運用は `NativeReadingScreen.kt:571` が常に `workTitle = bookTitle` を渡す
- **食い違い**: 常時表示される副題行が 3テーマ×2スケールの全 golden で未撮影。正本 `skins/toc-D.html:6` は「【K形を組込】←戻る＋明示タイトル「目次」＋作品名サブ」と要件化している。K 側は `workTitle` を渡すので写っている。
- **実害**: D/C 目次ヘッダで作品名サブが消える・二重表示になる等の退行が起きても6枚全部が緑のまま通る。

#### G-12【A】横画面K正本モックが「:root は縦正本と同値（新値なし）」と偽宣言している
- **対象**: `docs/design-candidates/skins/bookshelf-K-landscape.html:9, 20, 24, 29, 30, 85-87` vs `bookshelf-K.html:40, 41, 46, 47, 111, 113` / `ui/skins/k/BookshelfK.kt:1215 KCardMenuTapSize = 32.dp`
- **食い違い**: 実際は `--ink-soft`（#6A6E78 vs #7C808B）・`--field`・`--paper` が相違し `--info` が欠落＝4トークン相違。⋮タップ面も 28px（実装は 32dp・グリフ 16px vs 18px）。縦正本は 2026-08-06 に golden 起点で逆同期され旧 K 専用値 #6A6E78 は「実装不採用」と明記されたが、横画面はその便から漏れた。
- **実害**: 「同値」を信じて横画面だけ見た担当が、実装が採らなかった色と 28dp タップ面（Material の最小タップ標的未満）を正として持ち込む。縦横は同一の `KGridBookCard` を共有し向きで列数だけ分岐する設計なので、⋮寸法が縦横で違うことは本来あり得ない。`tools/check_design_tokens.py` の照合対象は `reading-*.html` のみ＝本棚モックは機械照合の外。
- **直し方**: landscape の :root を縦正本へ揃えるか、相違を「既知乖離」として明記する。`CURRENT.md` も同じ誤りを重ねているので同便で。

#### G-13【A】Compose コード中のモックセレクタ引用2件が正本に解決しない
- **対象**: `ui/skins/k/BookshelfK.kt:731`（`.cvt` — design-candidates 全 html に0件。正しくは `.vt`＋`.t`）/ `ui/skins/k/SettingsScreenK.kt:359, 370`（`.glabel` / `.card` — `settings-{D,M,P,J,K}.html` 5枚に0件。正しくは `.gh` / `.group`）
- **食い違い**: 記述内容（「12px 字間広め」「ヘアライン枠の白面」「書影内題字とキャプション題字の二重表示」）はいずれも正本と一致しており、**セレクタ名だけが実在しない**。
- **実害**: 「二重表示は意図的」「見出し・カード枠の正本」を確かめに行った担当が正本へ到達できず、記述ごと眉唾と判断して片方を削る（グリッドで書名が読めなくなる）／モックを直さず Compose 側だけ自己判断で変える（CLAUDE.md が禁じる意匠の自己判断）。`SettingsScreenK.kt:59` の KDoc が「モック正本＝skins/settings-{D,M,P,J,K}.html」と宣言している以上、引用は5枚のいずれかに解決しなければならない。
- **直し方**: 3語の置換のみ。

#### G-14【A】Compose BOM 天井の根拠コメントが Kotlin 1.9.22 / Roborazzi 1.30.1 のままで、その天井はもう存在しない
- **対象**: `android/app/build.gradle:330, 339-340, 344` / `android/settings.gradle:21, 32, 34, 37, 49` / 付随して `ci.yml:50`（「AGP 8.6.1 / Gradle 8.9 に合わせた」→ 実体は AGP 8.10.1・gradle-8.11.1）
- **食い違い**: 「BOM をこれ以上上げるには Kotlin 2.x 化が先＝ただし Kotlin 1.9.22 は Roborazzi 1.30.1 との連鎖で固定されている」と書くが、その1便は 2026-08-05 に完了済み（Kotlin 2.2.21・Roborazzi 1.70.0・`kotlinCompilerExtensionVersion` 方式自体が廃止）。ADR 0029 は「BOM は動かしていない」と正しく記録しており、**据え置きは事実だが据え置きの理由が消えている**。
- **実害**: 次に Compose を上げる人が (a) このコメントを信じて据え置きを恒久化するか、(b) 前提が古いと気づいて根拠ごと捨て、16KB ページ整列の再確認（`:342-344` の警告）まで一緒に落とす。Compose/Kotlin の版差は描画結果を動かす軸そのもの＝golden 直撃。
- **直し方**: 天井の記述を「ADR 0029 で解消済み。据え置きは意図的で、上げる際は 16KB ページ整列を再確認」へ書き換え。`ci.yml:50` も同便で。

#### G-15【A】「D 側の本棚には golden が1枚も無い」を根拠にした設計判断コメントが 2026-07-31 に失効している
- **対象**: `ui/GridStatusLineWrapTest.kt:44-46` vs `BookshelfD_*.png` 11枚 / `BookshelfDScreenshotTest.kt`（148行）
- **食い違い**: 「なぜ golden でなくレイアウト結果で縛るか: D 側の本棚には golden が1枚も無く（screenshots/ は BookshelfK_* のみ）、この不変条件だけのために D 用の撮影ハーネスを新設するのは重い」は日付注記の無い現在形の設計根拠だが、ハーネスは既に新設済み。`BookshelfDScreenshotTest.kt:26-29` が「2026-07-31 に判明した golden の空白: BookshelfK_* しか無く D/C の本棚は絵での回帰が一切効いていなかった」と**前提の解消を自ら記録している**。
- **実害**: 次に D の版面不変条件を足す人が、既にあるハーネスに1ケース足せば済むところを点検査で済ませるか二重にハーネスを起こす。K だけ絵で守られ D は点でしか守られない非対称こそが、K で是正した状態行の欠陥を D に残した当の原因。

#### G-16【A】golden 枚数「48枚」が2箇所で現在形のまま（実数104枚）。同じ行の参照先も別系統の文書を指している
- **対象**: `docs/known-bugs-registry.md:120`（検知手段列・関連 knowledge 列）/ `.github/workflows/ci.yml:85`（「実測 1072 件」も `golden-record-bakes-in-regressions.md:15` の 1151 件と不一致）
- **食い違い**: 48 が正しかったのは 0ab7f70（2026-07-30）まで。**同じ日の次便 9dde8f8 で85枚へ跳ね**、7f7c440=100、d9f5f5d=104 と増え続けたが一度も追従していない。参照先の `golden-regression-baselines.md` は PDF 抽出の基準値表で、Roborazzi・スクリーンショット・PNG の語が1件も無い。正しい対応文書は同ディレクトリの `golden-record-bakes-in-regressions.md`。
- **実害**: 検知手段列は「このバグ型はもう機械が見ている」の唯一の宣言で、投資判断がここを起点に決まる。書いた当日に壊れた数字が9日間放置された事実は、**この列全体が保守されていないこと**を示す。参照先の誤りは、監査の限界①を説明した唯一の文書へ台帳から辿れないことを意味する（ただし KBottomNav が焼かれた 2026-07-30 時点でその文書はまだ存在せず、因果ではなく初期からの誤参照）。
- **直し方**: 数字を消す（`.claude/skills/build/SKILL.md:73` が既に「枚数は増えるので書かない＝実数は `ls` で数える」と規約化）＋リンク張り替え。→ D-第1投資に同梱。

---

## 1-2. 2026-08-06 走査による追加検出（本文の既存記述は書き換えていない・追記のみ）

案1で新設した走査3本（`tools/check_golden_rule_loss.py`(a) / `check_golden_bottom_clip.py`(b) /
`check_golden_label_split.py`(c)・共通土台 `tools/golden_png.py`）を現行 golden 104枚へ適用したところ、
上表の20枚に**含まれない7枚**が赤になった。全数を実装まで辿って判定した結果は**7枚とも走査側の偽陽性**で、
`ContinuationCard_*_2.0` 3枚（監査が反証で却下済み）も同じく偽陽性だった。
**上表の根因4つ（現在地バー／シートのスクロール欠落／縦中横／固定幅ラベル）のどれにも当たらない**——
実装の欠陥ではなく、走査の署名が「画素からは見えない前提」を跨いでいたことによる。

| 候補（2.0） | 判定 | 機序（実装の構造） |
|---|---|---|
| `SettingsScreenK_default_{light,dark,sepia}` | **C 偽陽性** | 本体 Column が `verticalScroll`（`ui/skins/k/SettingsScreenK.kt:93-99`）＝ビューポートは画面全高で、増えた分は畳の外へ流れるだけで全項目に到達できる。行の中身も健全（題字 `:414` は `maxLines=1`＋省略記号、`:408-412` が `weight(1f)`）。走査(a) の 7本→2本 は可視行数の減少、(b) の下端インク32px は折り返し地点に居る「通知」見出しの縁 |
| `DiscoveryHomeK_home_{light,dark,sepia}` | **C 偽陽性** | `MoodCardK`（`ui/skins/k/DiscoveryHomeK.kt:446-480`）は題字・副題とも `maxLines` を持たず、カード自身も固定高を持たない＝2.0 では**カードが伸びて2行に折り返す**だけ。親も `Row`＋`weight(1f)` で幅を分け合う（`:428-443`）。走査(c) が数えた6件は全て折返し行の字グリッド（全角字が x を共有して真下に来る）で、切れも欠落も無い |
| `BookshelfK_list_mixed_light` | **C 偽陽性** | 一覧は `LazyColumn`（`ui/skins/k/BookshelfK.kt` の list 分岐・HEAD 時点 `:378-382`、`contentPadding` に `Insets.ScrollBottomForFab`）＝行が高くなり可視行が減っただけ。行内は省略記号で健全 |
| `ContinuationCard_{light,dark,sepia}` | **C 偽陽性**（監査の反証を追認） | 主ボタンは `fillMaxWidth()`＋`heightIn(min = 48.dp)`（`ui/ContinuationCard.kt:109-141`）で、文言が伸びればボタンが伸びる。走査(c) が拾った (255,747)-(265,774) は**太字「8」の左右に割れた 11×12px の断片2つ**＝字内の空きであってラベルの割れではない |

### 走査側に加えた是正（本物を取り逃す方向へは緩めていない）

**(a)(b): スクロール面の除外表を新設**（`tools/golden_png.py:302-334`）。
画素だけでは「押し出されて到達不能」と「畳の外へ流れただけ（スクロールすれば届く）」を区別できない——
Roborazzi が撮るのは最初のビューポート1枚だけだから。器の有無は実装にしか無い情報なので、
**1件ずつ人が実装を読んで判断した case だけ**を [参考] へ落とす（自動判定にしない理由＝目次K は
`LazyColumn` を持ちながら現在地バーに高さを奪われて viewport が 0 になる＝器の存在は十分条件でない）。
各エントリは根拠ファイルと在るべき字句を宣言し、**字句が消えたら除外は自動失効して赤へ戻る**。
結果: (a) 12件→赤7（真の破綻のみ）＋参考5、(b) 7件→赤4＋参考3。

**(c): 署名そのものを作り直した**（`tools/check_golden_label_split.py:57-135`）。初版の「縦の間隔が字送り相当
（字高の 0.55 倍以下）＝割れ」は、実測すると**両群を分けないどころか逆転していた**:
正常な CJK 折返しが 0.48 倍、真の縦積み（空棚CTA の P/D＝1.03倍、順位の 1/0＝0.94倍）が上回る。
1行1文字では字送りがそのまま行送りになるため。**初版の赤17件は1件も本物の縦積みではなく**
（字内の横空き・CJK 折返し）、捕まえるはずだった G-5・G-6 は逆に取り逃していた。
そこで軸を入れ替えた: ①塊の粒度を fontScale 比で正規化（絶対 px 固定だと 2.0 だけ細かく砕けて
「増えた」が構造的に起きる）②**行内孤立**を必須条件に（同じ y 帯の最近傍インクが字高の 0.4 倍以上離れる）
③縦間隔の上限を 1.3 倍へ。実測の谷は明確で、折返しの隣字は字高の 0.02〜0.25 倍、真の縦積みは 0.65〜1.3 倍。
結果: 40組で**赤2件（`BookshelfK_empty_light_2.0` の P/D、`DiscoveryHomeK_ranking_light_2.0` の 1/0）・
偽陽性0**、しかも指す座標が**初めて本物の破綻箇所**を指している（初版は同じ画像の別の場所を指していた）。

### 再記録前に残るリスク（監督向け）

- **G-2（表示設定シートのスクロール欠落）はどの走査も機械では捕まえていない。** (c) の赤は artifact だった。
  「非スクロール面で内容が可視域を越える」は画素に痕跡が出ない（切れずに空白が残る）ため、走査ではなく
  実装側の不変条件（シート本体に `verticalScroll` があること）で縛るしかない。
- **G-1 の目次現在地バーは (c) では出ない**（複数字が1行に残る折返しなので③で落ちる）。(a)(b) が赤にしている。
- **G-4（4桁目次の等倍での割れ）は 1.0 と 2.0 の差で見る限りどの閾値でも出ない**（等倍側に既に在るため）。
- (c) が今赤にしている2枚は、いずれも並走便が実装を直し済み（`BookshelfK` 空棚の FlowRow 化・
  `DiscoveryCommon` の順位セル幅 sp 追従）＝**再記録すれば緑になる。裏返せば、再記録前にこの2枚が
  赤のままなのが正しい状態**で、赤が消えていたら直しでなく走査の劣化を疑うこと。
- `SettingsScreenK` は「システムに従う」状態を撮っていない。trailing の現在値 Text（`:137-148`）は
  `weight` も `maxLines` も持たないため、文言が最長になるこの状態の 2.0 だけ題字が押される可能性がある
  （現行 golden は `followingSystem=false` 固定＝この状態は無検査）。撮影条件の追加候補。

---

# 第2部　docs の判断

## 2-0. 投資判断サマリ

### 陳腐化の総数と分布

**統合後37件**（本節26件 ＋ golden 節へ置いた記述系5件〔G-12〜G-16〕＋ 実装完了後に再確認6件）。

| 種別 | 本数 | 腐った本数 | 件数 | 汚染率 |
|---|---|---|---|---|
| **台帳**（STATUS / handover / registry / backlog / awaiting-human） | 5 | 4 | 8 | **80%** |
| **patterns** | 9 | 5 | 7 | **56%** |
| **skills** | 12 | 4 | 6 | **33%** |
| **ADR**（本体30＋索引） | 31 | 4 | 5 | 13% |
| モック正本 `skins/*.html` | 29 | 3 | 3 | 10% |
| reference | 10 | 1 | 1 | 10% |
| **knowledge** | 46 | 4 | 4 | **9%** |
| marketing / store | 3 | 1 | 2 | 33%（store 2本は健全） |

### 腐りやすさの順位と根拠

1. **台帳（80%・8件）** — 現在値だけを書く器なので、実装が動くたびに必ず嘘になる。決定的な根拠は `registry:120` が**書かれた当日の次便で崩れ、9日間放置された**こと。更新の同梱義務（CLAUDE.md「台帳の更新は原因となった論理変更と同じコミットに同梱」）が事実上効いていない。
2. **patterns（56%・7件）** — 「次はこう作れ」を現在形で規定する手順書で、腐り方が最も危険。`processing-state.md` は registry が「再発したらここを読め」と案内する先でありながら、**再発を招く旧処方を規範として提示している**。`narou-api-discovery.md` は解消済みの危険を現在形の警告マークで残している。
3. **skills（33%・6件）** — 密度は3位だが、**必須ゲートとして毎回読まれる＝誤りが即座に行動へ変換される**（禁止中の agy へ委譲する／存在しない memory を recall する／ktlint を回さず push する）。読んでから行動に変わるまでの距離が最短で、実害の期待値では1〜2位と並ぶ。
4. **ADR（13%・5件）** — 本体の Decision が腐ったのは 0005 のみ。ADR は「その時点の判断とその理由」を過去形で書くので時間に強い。腐るのは (a) 索引 README の現在形の進捗注記（2件） (b) 後の ADR が前の決定を解除したのに元 ADR へ注記が返らない**片方向更新**（0010 は注記2ブロックを打つ作法を守れているのに 0005 だけ外れている）。
5. **knowledge（9%・4件）** — 1知見1ファイルで小さく分かれているため腐りが局所化し他へ波及しない。**ただし密度と severity は独立**で、最も危険な1件（D-1・.so 0本）はここに在る。

### 次の投資先（順位付き）

- **第1投資: 台帳・CI コメント・skill から「現在値の数値」を消すか機械生成にする。** 今回の数値ズレ5件（48枚×2・1072件・297組・5ゲート）は全て `ls` / `wc` / checker 実行で1秒で出る。`build/SKILL.md:73` が既に「枚数は増えるので書かない」と規約化しているので、**規約の適用範囲を全 md へ広げ、`/stale-check` に「md 中の『N枚 / N件 / 計Nゲート』を実測と突合する」検査を足す**。5件が一撃で消え、以後の再発も止まる。
- **第2投資: patterns に「正本コード: <path>」ヘッダを必須化し、その path の公開シンボル集合が変わったら赤にする。** `ProcessingStateHub`・`ComponentPadding` はどちらもシンボル追加で検出できた。patterns は9本しかないので初期コストが小さく、汚染率2位を直撃する。
- **第3投資: 双方向注記の義務化。** 「後の ADR / コミットが前の記述を解除したら、解除された側にも注記を打つ」を `/stale-check` の検査項目へ。D-7・D-12・D-15・G-15 は全て片方向更新。
- **しないこと: knowledge の全数照合。** 46本中4本（9%）で費用対効果が低い。ただし**「外部事実を実測と称して書いた knowledge」だけは別枠**で、D-1 のように測定コマンド自体が偽（`unzip` 未導入でパイプが空を返す）というクラスは memory `bash-pipe-masks-exit-code-false-green` が既に警告している型＝実測を載せる knowledge には**使ったコマンドと環境を必ず併記させる**。

---

## 2-1. 個別 finding

#### D-1【A】「APK に .so は1本も入らない＝16KBページ非該当」は誤りで、実際は8本入っている
- **対象**: `docs/knowledge/apk-has-no-native-libs-16kb-page-not-applicable.md:9-12, 22-24, 29`
- **食い違い**: 実ビルド済み APK（release / debug 両方）を `python3 -m zipfile -l` で数えると `.so` は8本（`libandroidx.graphics.path.so` 4ABI ＋ `libdatastore_shared_counter.so` 4ABI）。供給元は `androidx.graphics:graphics-path:1.0.1`（ui-graphics 経由）と `androidx.datastore:datastore-core-android:1.1.1`。`build.gradle:208, 226` が「4ABI×2＝8本」「release APK は .so 8本すべて 0x4000」と正しく書いており、知見と同日付で正面から矛盾している。**偽測定の機序も特定済み**: この環境に `unzip` が無く、`unzip -l <apk> | grep '\.so$'` は無出力→grep が0件を返す。
- **実害**: 依存バンプ担当がこの知見を信じると、`build.gradle:206-227` の16KBページ整列確認手順（zipalign -P 16 と ELF p_align 検分）を「非該当だから不要」として恒久的に飛ばす。現在の8本はたまたま 0x4000 で合格しているだけで、上流が非対応版に差し替わった瞬間、Android 15+ の16KBページ端末で起動不能な APK を無検査で出荷する。加えて `build.gradle` の正しい記述を「旧記述＝誤り」として削除する二次被害もある。
- **直し方**: 知見を撤回または全面改稿し、実測コマンドを `python3 -m zipfile -l`（`unzip` 非導入環境でも可）へ。同ディレクトリの `agp-srcdir-taskprovider-drops-builtby.md:18` が既にその作法を書いている。

#### D-2【A】`ShelfItems` の「reachedEnd は ProgressEntity に無い・FINISHED は未成立」が同一ファイル30行下と正面矛盾する
- **対象**: `domain/ShelfItems.kt:266-273` vs 同 `:295-307`
- **食い違い**: 上のコメントは「そのフラグは ProgressEntity に無く…到達フラグが入るまで readingStatusFor の FINISHED は成立しない」と現在形で断定するが、`ProgressEntity.kt:26 val reachedEnd: Boolean = false`（v18 追加）・`AppDatabase.kt:316` の MIGRATION_17_18・`ShelfItems.kt:307 if (progress?.reachedEnd == true) return ReadingStatus.FINISHED`・書込経路 `ProgressDao.kt:46` まで全て稼働している。**両コメントが同じ「ssot Major 2026-07-12」を典拠に名乗りながら真逆**。
- **実害**: 読了機能のバグ調査で「FINISHED はまだ成立していない＝別レーンの宿題」と誤読し、稼働中の退行を「未実装だから当然」と却下する。逆に信じて再実装しようとすると Room の列追加から作り直して v18 と衝突する。読了フィルタ・「了」印のテスト範囲も「まだ動かない機能」として省かれる。

#### D-3【A】CI ゲートは6本なのに `/build` と STATUS が「5つ」「計5ゲート」と数えている（ktlint 欠落）
- **対象**: `.claude/skills/build/SKILL.md:66, 70-77` / `STATUS.md:67-70`
- **食い違い**: `ci.yml:73-75` に `- name: ktlint (未使用 import 検知)` / `./gradlew :app:ktlintCheck` が実在し、`ci.yml:67-69` に「2026-08-05 にブロッキングへ復帰（continue-on-error を外した）」と明記。プラグインも `build.gradle:19` に実在。`grep -rn ktlint --include=*.md .` は**0件**＝md 側のどこにも書かれていない。
- **実害**: 「push 前に赤を前倒しで拾いたい」でこの節どおり5本回した Claude は ktlintCheck だけ回さず、未使用 import 1つで CI が赤になる。STATUS は現在値の正本なので、必須ゲート skill の誤りと二重に補強し合って誰も気づかない。
- **直し方**: 2箇所に ktlint を追加（`ci.yml:70-72` だけが持つ運用知＝`--continue` 無しだと最初のソースセットで止まる、も一緒に拾えるようにする）。

#### D-4【A】禁止中の agy への委譲を skill 2本と参照台帳が今も規定している
- **対象**: `.claude/skills/hallucination/SKILL.md:45-46` / `docs/reference/hallucination-ground-truth.md:34, 42` / `.claude/skills/shiori-tips/SKILL.md:32(b)`
- **食い違い**: 「E型（対話文脈の捏造）を疑う場合の agy read-only 意味監査は台帳『追記手順』3 の定型を使う」「(b) env 無関係の agy へ委譲」に対し、CLAUDE.md:29 は「2026-07-24〜 agy は当面使用禁止（明示解除まで。委譲は Claude サブエージェントのみ）」「2026-07-26 に antigravity プラグイン自体を無効化」、`~/.claude/settings.json` の enabledPlugins も false。**代替手段（Claude サブでの意味監査）はどこにも書かれていない。**
- **実害**: 「キューを消化して」の実行入口である skill の指示に従うと禁止委譲へ直行する。プラグイン無効で失敗すれば時間を失い、`~/.local/bin/agy` は実在するので Bash 直叩きなら**ユーザー裁定に反した委譲が実際に成功してしまう**。どちらでも E型の確定作業は止まる。

#### D-5【A】存在しない env `CLAUDE_CODE_SUBAGENT_MODEL` を前提にした記述が5箇所に残っている
- **対象**: `.claude/skills/orchestration/SKILL.md:88` / `.claude/skills/shiori-tips/SKILL.md:32` / `.claude/agents/general-purpose.md:10` / `.claude/agents/device-verify.md:21` / `handover.md:247-248`
- **食い違い**: `~/.claude/settings.json` の env は6件（AUTOCOMPACT / MAX_OUTPUT_TOKENS / BACKGROUND_TASKS / JAVA_HOME / ANDROID_HOME / API_TIMEOUT_MS）のみで該当変数は無く、プロセス env にも0件。handover:247-248 は同じ段で「現状は env で opus 固定」と書いた直後に「2026-08-06 に opus固定指示は解除済み」と自己矛盾している。
- **実害**: 監督が「Agent ツールでは model 指定が効かない」と信じ、安く済む探索まで headless 迂回（`claude -p --model sonnet`）で組み立て続ける＝迂回の手間と文脈受け渡しコストを払い続ける。逆に `general-purpose.md` は「env が勝つから frontmatter に書かない」のまま放置され、**実際に何のモデルで走っているかを誰も宣言していない**状態になっている。

#### D-6【A】`/build` が撤去済み機構と存在しない memory を根拠に「テストは前景で」と縛っている
- **対象**: `.claude/skills/build/SKILL.md:51-52`（同文が `shiori-tips/SKILL.md:48` にも）
- **食い違い**: ①memory `background-gradle-test-skips-sentinel-hook` は不在（memory ディレクトリ46ファイルに該当名なし＝recall しても読めない）②センチネル生成者 `mark_kotlin_tests_passed.py` は 2026-07-12 に撤去（ADR 0017）③消費側も 2026-07-25 に退役（`detect_fabricated_execution_core.py:1207-1211` が明記）④現行 settings.json に該当コミットゲートは無く、`handover.md:251` も「ローカルの自動コミットゲートは現存しない」。
- **実害**: 存在しない副作用を避けるために重い Gradle を必ず前景で回す運用が固定される（長時間ブロック・ストール要因）。加えて存在しない memory を recall して空振りする。CLAUDE.md:47「フック撤去は参照する側まで含めて1セット」の取りこぼしそのもので、機械チェックの removed-hook 検査は**フック名ではなく memory 名で書かれているため検出できていない**。

#### D-7【A】ADR 0005 §C「スキン着せ替えは実装しない／main は現状 D のみ」が現在形のまま残り、撤回注記が無い
- **対象**: `docs/decisions/0005-ui-n-visual-language-D.md:30, 35`
- **食い違い**: 2026-07-17 に解除され実装済み（`Skin.kt:36-41` に6スキン・`ui/WardrobeScreen.kt`・`ui/skins/{j,k,m,p}/`・既定は `Skin.MEIKAI_K`）。ADR 0021 の関連行は「前史 ADR 0005 §C（スキン将来送り＝本 ADR で解除）」と明記しているのに **0005 側に相互参照が1件も無い**（0010 は更新時に冒頭へ注記ブロックを2つ足しており、本プロジェクトの作法から 0005 だけが外れている）。訂正先として指示された `handover.md` の「A2」節も存在しない（grep 0件）。
- **実害**: 0005 は 0007/0009/0011/0012/0020/0023 が「意匠の上位規範」として名指しする最上位 ADR＝意匠判断の入口として最初に読まれる。`ui/skins/{j,k,m,p}/` を規約外の逸脱と誤認する／装いの間の選択 UI を未設計として再設計する／K が既定であることを見落として D 前提でレビューする。

#### D-8【A】ADR README 索引の2エントリが、完了済みの作業を未着手・現在進行として書いている
- **対象**: `docs/decisions/README.md:37`（0027「実装未着手」）/ 同 `:39`（「依存バンプの天井は Kotlin 1.9.22」を完了語なしの現在形で）
- **食い違い**: 0027 は 2026-07-31 実装済み（`Features.kt:30`・build.gradle の debug/release 分岐・適用点3つ・両値テスト）。0029 は 2026-08-05 に1便で実行完了（settings.gradle が Kotlin 2.2.21 / KSP 2.2.21-2.0.5 / Roborazzi 1.70.0 / AGP 8.10.1）。ADR 本体はどちらも状態行を更新済みで、**索引だけが取り残されている**。
- **実害**: CLAUDE.md が「方式比較の前にまず README 索引を確認」と定めているため、索引だけ見た者が「release でスキンが全部見える状態が公開ブロッカーとして残っている」「Compose 1.9+ / lifecycle 2.9+ / work 2.11 は今も機械的に死ぬ」と誤認し、同じフラグを二重に作る／通る依存バンプを別便へ据え置く。ADR 0029 が「据え置きが5件溜まった」と記録した失敗を、天井が外れた後に再演する。

#### D-9【A】`Motion.kt` のタブ切替コメントが、廃止済みの crossfade を「なぜ slide でないか」として説明している
- **対象**: `ui/theme/Motion.kt:64-66` vs `docs/decisions/0022:130` / `ui/tabs/TabPagerHost.kt:70` / `MainActivity.kt:462, 466`
- **食い違い**: 「なぜ slide でなく短い crossfade か: …方向を語る slide は誤った空間語彙になる」に対し、ADR 0022 追記（2026-07-24）が「旧 crossfade（MotionDurationKTabSwitch）は廃止: スワイプ追従とタップ切替の運動言語を一致させるため、タップも同トークン長のページスライドへ」と決定済み。実装も `HorizontalPager` の `animateScrollToPage(…, tween(MotionDurationKTabSwitch))` で、`src/main` に `Crossfade(` は0件。`MainActivity.kt:462` のコメントと**正面衝突**している。
- **実害**: トークン定義（値の唯一の正本と宣言している場所）が廃止済みの理由を現在形で保持しているため、タブのモーションを触る者が ADR 0022 の裁定を知らずに crossfade へ「戻す」。Pager の指追従スワイプとタップ切替の運動言語が再び割れる。

#### D-10【A】handover の「章見出しの話数ラベルは描画側が誰も読んでいない」は false（描画側5ファイルが使用中）
- **対象**: `handover.md:229-232`
- **食い違い**: 「描画側はまだ誰もこの型を読んでいない＝見た目は1pxも変わっていない（golden 緑がその証拠）」に対し、`splitChapterTitle` は `ui/ChapterContent.kt:287` / `ui/VerticalChapterContent.kt:224` / `ui/skins/m/ReadingChromeM.kt:161` / `ui/skins/j/ReadingPortalJ.kt:134` / `ui/skins/p/ReadingCartridgeP.kt:242` から呼ばれている。実装は d9f5f5d（2026-08-06・golden 2再記録+4新規）で完了。参照先の `awaiting-human.md §3-2` に当該項目は無く §1-5 へ移っている＝相互参照も切れている。
- **実害**: handover は「悩んだらまず見る」台帳。済んだ意匠裁定を再度ユーザーへ回す／「描画側は無改変・golden 緑が証拠」を前提に章見出し周りの回帰を判断して誤る。CLAUDE.md「完了したら打ち消し線で残さず消す」の未消化でもある。

#### D-11【A】STATUS の「公開準備は作業ブランチ（worktree）で進行中」— そのブランチも worktree も存在しない
- **対象**: `STATUS.md:12`（`git show main:STATUS.md` でも同文＝正本 main が同じ状態）
- **食い違い**: `git worktree list` は canonical と本レビュー worktree の2つのみ、`git branch -a` も main と review ブランチのみ。公開準備の成果物は main に入っている（`git show main:android/app/build.gradle` に `hasReleaseKeystore` / `signingConfigs { release … }`・compileSdk/targetSdk 36）。当該行は 34bd24e（2026-07-29）以来未更新。
- **実害**: 現況の正本を読んだ Claude が「公開準備の変更は別レーンにあるので main には無い」と誤認し、release 署名まわりを触るときに存在しないブランチを探す／main の実装を二重に作る。

#### D-12【A】`narou-api-discovery` の「キャッシュは Main dispatcher 前提＝Worker 化で壊れる」警告は Mutex 化で解消済み
- **対象**: `docs/patterns/narou-api-discovery.md:13`
- **食い違い**: `NovelApiRepository.kt:77-84` が「かつては『全呼び出しが Main dispatcher で直列化される』暗黙前提で素の mutableMap のまま成立していたが、U1 新着チェック（WorkManager）がこの前提を破るため、読み書き・追い出しを排他し任意の dispatcher から安全に呼べるようにする」と書き、`cacheMutex.withLock` で `putCache` / `getCacheValid` を守っている。ぶら下がる参照「handover の技術的負債に注記」も handover の該当節に項目なし＝参照先も切れている。
- **実害**: 「なろうAPIのキャッシュはバックグラウンドから触ると壊れる既知の負債がある」と信じ、(a) Worker からの呼び出しを避けて別経路を新設 (b) 既に在る Mutex の上にさらに同期機構を重ねる (c) 未解消の負債として handover へ再掲、のいずれかで無駄な作業を生む。

#### D-13【A】`spacing-token-translation` が第3の退避先 `ComponentPadding` を知らない
- **対象**: `docs/patterns/spacing-token-translation.md:4, 18-22, 36` vs `ui/theme/Spacing.kt:57-73`
- **食い違い**: 手順書は正本コードを「7段スケール `Spacing` object ＋ 同ファイル内の `Insets` object」と2つだけ数え、退避先も「`Insets.*` か命名 `private val`」の2択。実体は3つ目の `object ComponentPadding`（「部品内部の造形寸法＝ADR 0014 §C の除外軸・2026-07-30 裁定」）を持つ。
- **実害**: 次に余白リテラルを機械翻訳する担当が、部品内側の造形寸法（バッジの padding 等）を `Insets.*`（＝他要素からの回避距離の意味）へ入れるか private val へ散らす。2026-07-30 に分けた3軸が再び混ざり、命名から「なぜこの値か」が読めなくなる。

#### D-14【A】`multi-branch-integration` が参照する「CLAUDE.md の task_diary 自動更新ルール」は存在しない
- **対象**: `docs/patterns/multi-branch-integration.md:26-28`
- **食い違い**: 現行 CLAUDE.md に該当の見出し・ルールは無い（grep 0件）。むしろ「`task_diary.md` は凍結アーカイブ＝既存 #N 参照は有効・新規追記はしない」へ変わっており、**連番資源としての #N 採番自体が発生しない**＝挙げられている衝突クラスが構造的に消滅している。
- **実害**: 次回の多ブランチ統合で存在しない章を CLAUDE.md 中に探す。さらに「task_diary #N の二重採番」を今も起こりうる衝突として点検リストに入れるため、実在する衝突源（Room version・ADR 番号・スキーマJSON）に割く注意が薄まる。参照実在の機械チェックはファイル名しか見ないため、この種の章名切れは誰も検出しない。

#### D-15【A】registry の `mock-code-drift` 検知手段「手動実行」は誤りで、CI が毎 push 走らせている
- **対象**: `docs/known-bugs-registry.md:103` vs `ci.yml:44-45` / 同表 `:90`, `:135`
- **食い違い**: `- name: Design token check` / `python3 tools/check_design_tokens.py` が CI の**最初のステップ**として実在（コメント「純 Python・数秒＝最速で drift を検知して fail-fast」）。CI 新設 b1349e4（2026-07-27）は registry 新設 3e8d688（2026-07-30）より前なので、**この行は生まれた時点で既に誤り**。同じ表の :90「CI の Design token check で毎push」・:135「起動点は CI の Design token check ステップのみ」と自己矛盾している。
- **実害**: 「一目で投資判断」という台帳唯一の効用が壊れる。既に済んでいる CI 結線に二重投資するか、逆にドリフトを CI が止めないと誤認してモック同期をコミット外に回す。

#### D-16【A】`02-narou-api-digest` の order 値 `daily_point` は実在せず、`weekly` は別指標
- **対象**: `docs/reference/02-narou-api-digest.md:34` vs `docs/reference/narou_api_manual.md:50, 55, 62, 188`
- **食い違い**: 正本は `dailypoint`（アンダースコアなし）。`daily_point` は order 値ではなく of の出力項目名。`weekly` は「週間ユニークユーザの多い順」で、週間ポイント順は `weeklypoint`。実装は正しい（`DiscoveryQuery.kt` の DAILY("dailypoint") / WEEKLY("weeklypoint")）。
- **実害**: `known-bugs-registry.md:105` が `external-api-contract-drift` の参照正本として、architecture skill:90 が API 仕様の要点としてこのファイルを名指ししている＝生きた参照。新しいランキング軸を足す実装者がこの形式を真似ると、UI は「日間ランキング」と名乗りながら別の並びを出しテストも通る（無効 order のフォールバック挙動は一次ソース未確認＝断定はしない）。
- **直し方**: 2語の修正。

#### D-17【B】用語辞書の「概念Aは『見つける』に固定」が出荷スキンKの「さがす」で破れており、Up の読み上げも実在しない画面名を名乗る
- **対象**: `docs/patterns/discovery-terminology.md:14-16, 21, 23` / `ui/skins/k/DiscoveryHomeK.kt:229, 237` / `ui/skins/k/KBottomNav.kt:43` / `ui/discovery/DiscoveryResultScreen.kt:214-217`
- **食い違い**: 辞書は「概念Aを指す表層語は『見つける』に固定・『発見』を UI 表層に出さない」「概念Bは『探す』に固定」。ところが K は h1「さがす」・タブ「さがす」（これは実装の逸脱ではなく `discovery-K.html:6`「②画面タイトルを明示（タブと同語彙「さがす」）」というモック正本の指示）。他4スキンは「見つける」、概念Bは「探す」＝**同一語「さがす／探す」が概念AとBの両方に当たっている**（この辞書が防ごうとした一語一義違反そのもの）。派生して `DiscoveryResultScreen` の Up は `contentDescription = "見つける画面に戻る"` だが、K では着地画面が自身を「さがす」と読み上げる。
- **実害**: 辞書を正として直す担当が K の「さがす」を辞書違反と見なしてモック正本から逸脱させる／辞書を放置すれば K 利用者はタブ「さがす」→画面「さがす」で発見ホームに着き検索画面でも「探す」と言われ、2画面の役割差が言葉から消える。TalkBack 利用者は Up の名乗りと着地画面名が一致せず現在地を追えない（release は明快K 固定＝全ユーザーが踏む）。
- **直し方**: どちらへ寄せるかは意匠裁定＝`awaiting-human.md` へ登録し、決着後に辞書・`DiscoveryResultScreen.kt:217` の順で反映。

#### D-18【B】`positioning-brief` が出荷実態と2点でずれている（着せ替えは release 非表示／Web小説取込が欠落）
- **対象**: `docs/marketing/positioning-brief.md:18-22, 24-31, 46, 48`
- **食い違い**: ①ウリ3つの3番目「着せ替え（装いの間）」は release で丸ごと非表示（`build.gradle:153 SKIN_SWITCHING_ENABLED=false`・`Features.kt`「初回公開は明快K 単独＝D/C/M/P/J と装いの間ごと隠す」）。同ファイル48行が自ら「開発中スキンを"今ある"扱いにしない（訴求は出荷済みのものに限定）」と禁じている。②主な機能からカクヨム・暁の本文取込が欠落し「本文は取得しない。読むのは利用者のPDF」と断言しているが、`KakuyomuAdapter` / `SiteProfiles`(暁) / `WebBookImporter` / `MainActivity` の VIEW フィルタまで配線済み（なろう本文を取らない部分は正しい）。
- **実害**: このブリーフを入力にストア説明・スクショ選定を進めると、出荷版に無い「着せ替え」を主要訴求に据え、出荷済みの主要機能を掲載文から落とす。しかも「読むのは利用者のPDF」を安全弁として引用したまま公開すると、Data safety 申告（カクヨム・暁への通信を明記）とストア説明が食い違う。同じ公開準備セットの `store/` 2本は正しく書けており、**marketing 側だけが取り残されている**。
- **【要再確認】** ②の根拠ファイル（WebBookImporter / SiteAdapterRegistry / ScrapeHttpClient / MainActivity）は編集中。→ F-4

#### D-19【B】ADR 0021 決定7 の装いの間カルーセル並び順が実装と違い、その誤りがコードのコメントにも転写されている
- **対象**: `docs/decisions/0021-ui-skin-framework.md:33-34` / `ui/WardrobeScreen.kt:85` / `ui/theme/Skin.kt:26`
- **食い違い**: 「並び順は『和モダン → 夜行 → 今後追加スロット』」に対し、`WardrobeScreen.kt:86 val skins = Skin.entries` の宣言順は MEIKAI_K → WAMODERN_D → YAKO_C → SEIZU_M → CARTRIDGE_P → PORTAL_J＝先頭は明快K、6枚＋追加スロットで7ページ。`Skin.kt:26` の enum KDoc も「並び順は…『和モダン → 夜行』（ADR 0021 決定7）」と書きながら8行下で「明快K＝新デフォルト。先頭に置く理由: enum順＝装いの間カルーセル順」と同一ファイル内で矛盾している。
- **実害**: 装いの間の実機目視で「左端が和モダンでない＝並び順が壊れている」と誤判定し、Skin enum の宣言順（exhaustive when・テストの全数要求と結びついている）を意匠都合で並べ替えにいく。

#### D-20【B】ADR 0015 の「exclude＝`file: novels/` を明示」は lint で書けないと判明した後も更新されていない
- **対象**: `docs/decisions/0015-layered-auto-backup.md:18` vs `res/xml/backup_rules.xml` / `data_extraction_rules.xml`
- **食い違い**: 両 XML に `<exclude>` 要素は無く、コメントが「明示の `<exclude path="novels/">` は『included path 内にない exclude』として lint エラーになるため書けない＝除外の意図はこのコメントが正本」と理由を明記している。ADR が「明示する」と決めた記述は、書けないと判明したまま ADR 側が未更新。
- **実害**: バックアップ設定を ADR と突き合わせた者が「決定どおりの exclude が欠落＝誰かが消した」と判断して足し、`ci.yml:113-115` の `:app:lintDebug`（abortOnError=true）で CI が赤になる。編集地点の XML コメントが理由を持つため往復は短い。

#### D-21【C】immersive 知見の対処2（バー色を常時 TRANSPARENT）は targetSdk 36 では無効化済み API
- **対象**: `docs/knowledge/immersive-toggle-cutout-letterbox-flicker.md:22` / `ui/theme/Theme.kt:155-156`
- **食い違い**: 手元の android.jar を javap で実査すると `setStatusBarColor` / `setNavigationBarColor` は android-34 では非 deprecated、35・36 で両方 deprecated。一次ソース（Android 15 behavior changes）でも targetSdk 35+ の「deprecated and disabled」に列挙。本プロジェクトは 2026-07-29 に targetSdk 36 へ移行済みで、`Theme.kt:155-156` は今も SideEffect で毎回実行している＝ジェスチャナビ機では no-op。
- **実害**: 結論（真因はカットアウト letterbox の伸縮）と対処1（`layoutInDisplayCutoutMode = ALWAYS`）は今も有効で、対処2が no-op でも表示は壊れない（targetSdk 35+ ではバー透明が platform 既定）。誤らせるのは「再発時に効いていない行を疑って時間を溶かす／新画面へ死にコードを写経する」だけ。→ 記録に留め、Theme.kt を触る便で併せて掃除。

#### D-22【C】`robolectric-compose-clock-capture-pitfalls` が Roborazzi 1.30.1 のバイトコード実査を根拠にしている（構成は 1.70.0）
- **対象**: `docs/knowledge/robolectric-compose-clock-capture-pitfalls.md:3-4, 11-12`（同文が `ShioriCoverHighLoadTest.kt:48` にも）
- **食い違い**: 本知見は 04f5b57（2026-08-06）で追加されたが、Roborazzi は前日 a7338e1（2026-08-05）で 1.30.1 → 1.70.0 に上がっている＝**執筆時点で既に構成に無い版**を根拠にしている。Gradle キャッシュには 1.30.1 と 1.70.0 の両方が残置されており、誤った artifact を読む物理的可能性は現に存在する。
- **実害**: 結論そのものは 1.70.0 でも成立する（233クラスを走査して `forceRedraw` 参照0件を独立検証済み）ので現時点の実害はゼロ。ただし次に Roborazzi を上げたとき「1.30.1 実査済み」が**検証済みの外観だけを残し、実際には誰も現行版を見ていない**状態を作る＝監査の出自②と同型。修正は版表記1つ。

#### D-23【C】`/build` の「`gw` が必須な理由＝CRLF」は誤り（gradlew は LF）
- **対象**: `.claude/skills/build/SKILL.md:26` vs `ci.yml:55-56`
- **食い違い**: `file android/gradlew` は canonical・worktree とも `POSIX shell script, Unicode text, UTF-8 text executable`＝CRLF ではない。`ci.yml` が一次情報として「改行は LF（file コマンドで確認済み）なので CRLF 変換の前処理は不要＝chmod のみ」と明記しており、リポジトリ内で説明が真っ向から食い違っている。実際に直接実行を阻むのは ext4 worktree 側の実行ビット欠落（git 100644）で、canonical は drvfs が 777 を返すのでむしろ実行できる。
- **実害**: Permission denied に遭遇した Claude が原因を改行コードだと誤診し、`dos2unix` 相当や再チェックアウトへ走る。行動の結論（gw を使う・AAPT2 EPERM で init-script が要る）は正しいままなので害は限定的。真の分岐（canonical=実行可 / worktree=不可）がどこにも書かれていない点は記録に値する。

#### D-24【C】さがすK正本の見出し右ラベル「② 気分転換」が実装・golden に無く、既知乖離としても未記録
- **対象**: `docs/design-candidates/skins/discovery-K.html:129, 70-71` vs `ui/skins/k/DiscoveryHomeK.kt:304, 358-364` / `DiscoveryHomeK_home_light_1.0.png`
- **食い違い**: モックは見出し行の右端に現在の気分組を名指しする淡色ラベルを置くが、実装は `SectionHeadingK("きょうの気分")` のテキスト1つのみ。組名を出すのは日替わり注記だけで、その文言は**初期組固定**（`todayPattern` は日付から一度決まる引数）でスワイプしても更新されない。同モック :14-18 の「golden との既知乖離」列挙にこの欠落は含まれていない。
- **実害**: 横スワイプで別の組へ移った後、いま並んでいる4枚がどの組かはドット位置からしか読めない。モックが右端ラベルで担保していた「現在地の言語化」が実装で失われたまま、既知乖離としても記録されていないので次の逆同期でも拾われない。
- **直し方**: 実装するか、モックの既知乖離リストへ追記するか（後者なら1行）。

#### D-25【C】K リスト正本モックに FAB「＋PDFを追加」が無いが、golden にも実装にも常時ある
- **対象**: `docs/design-candidates/skins/bookshelf-list-K.html`（"fab" が0件。縦グリッド正本は7件・横画面正本は5件）vs `BookshelfK_list_mixed_light_1.0.png` / `ui/skins/k/BookshelfK.kt:445-455, 331, 381`
- **食い違い**: 実装の拡張FABはグリッド／リストの分岐外にあり、両モードの contentPadding がともに `Insets.ScrollBottomForFab` を予約＝表示切替に関係なく常時出る。当該モックは「ヘッダ／フィルタチップ／ボトムナビは bookshelf-K.html と同一マークアップ」と共有クロームを列挙しながら FAB だけ落としている。同ファイルの逆同期ブロックは「未読の藍ドットと新着バッジがモック不在」と**まさに「モックに無い実装要素」を記録する運用**を自ら示しており、FAB の不記載はその台帳自身の基準に照らした抜け。
- **実害**: リスト表示の版面をこのモックだけ見て組み直す担当が「リストモードには PDF 追加の主導線が無い」と解釈し、切替時に FAB を隠す実装を入れる＝リスト常用者から取り込みの入口が消える。

#### D-26【C】`discovery-terminology.md` 末尾にツール呼び出しの生タグ `</content></invoke>` が混入している
- **対象**: `docs/patterns/discovery-terminology.md:45-46`（od -c で終端確認。patterns 他8本は正常終端）
- **食い違い**: 本文は44行で完結しており、以降の2行は書き込み時にツールの閉じタグがそのまま流れ込んだ残骸。Markdown としては未定義タグ。
- **実害**: レンダラによっては空要素として消えるため目視では気づきにくい一方、grep 集計・md→HTML 変換・LLM への投入ではノイズになる。実害は小さいが**生成物がそのままコミットされた痕跡**なので、他ファイルにも同種混入が無いか一度確認する価値がある。
- **直し方**: 2行削除（D-17 の辞書修正と同便で）。

---

# 第3部　【要再確認】実装ワークフローが同時に修正中の指摘

以下は編集中ファイルを根拠に含むため、**実装完了後に監督が再確認**すること（行番号は変動している前提で取り直す）。編集は一切していない。

| # | 指摘 | 編集中の根拠ファイル | 再確認の要点 |
|---|---|---|---|
| **F-1** | さがす配下の実ルート5面（詳細/取込/検索/結果/ジャンル）と `NcodeLinkSheet` / `SearchConditionSheet` が golden 0枚。`DiscoveryHomeKScreenshotTest.kt:58` は面**内**の穴しか申告しておらず、遷移先が丸ごと0枚である事実はどこにも記録がない。PdfImportScreen は長文＋進捗＝拡大時に最も崩れやすい構造なのに絵の回帰が0 | MainActivity.kt / NovelDetailScreen.kt / PdfImportScreen.kt / DiscoverySearchScreen.kt / NcodeLinkSheet.kt | ルート一覧と面構成を取り直し、撮影条件の設計へ（G-8 と同じ台帳項目に束ねる） |
| **F-2** | `docs/patterns/processing-state.md` が単一 StateFlow 前提のままで、2026-07-29 に置換された `ProcessingStateHub`（供給元スロット分離）が1語も出てこない。しかも `registry` の `processing-state-hub-overwrite` 行が「再発したらここを読め」と案内する先がこの文書＝**再発を招く旧処方を規範として提示している** | PdfProcessingService.kt / BookshelfViewModel.kt | Hub の現構造（`slots` 配列＋PDF優先の合成）で全面改稿。3つ目の書き手（バックアップ復元・EPUB取込等）が Hub を迂回しない書き方に |
| **F-3** | `string-hashcode-low-bit-bias-palette-skew.md` が「fmix32 で解決」と読めるが、`awaiting-human.md:250` は「fmix32 後も 5/6 扉が緑系。真因の一半はパレット自体が緑系2/4」と**未裁定**で記録。知見が「テストで固定」と称する回帰は「4パレットが全部1回以上出る」だけで色相の散り具合を測っていない。横展開節が「目視で気になったら同じ fmix32 を適用」と、J で無効と判明済みの因果を M/P へ勧誘している | BookshelfSkyM.kt / BookshelfLogM.kt / BookshelfCartridgeP.kt / BookshelfListCartridgeP.kt / BookshelfPortalJ.kt | 知見に「fmix32 は分散を直すがパレットの色相レンジは直さない・目視の偏りは残存（awaiting-human 参照）」を追記。二重台帳の解消 |
| **F-4** | `positioning-brief.md` の「主な機能」からカクヨム・暁の本文取込が欠落し「本文は取得しない。読むのは利用者のPDF」と断言（D-18 の後半） | WebBookImporter.kt / SiteAdapterRegistry.kt / ScrapeHttpClient.kt / MainActivity.kt | 取込の対応サイトが確定してから文言化。`store/data-safety-draft.md` の記述に合わせる |
| **F-5** | `discovery-terminology.md:37-42` の「配線依頼」2件（`BookshelfScreen.kt` の 🔍 contentDescription「小説を探す」／空棚CTA「新しい物語を見つける」）が指す UI 要素は 2026-07-29 に撤去済みで着手不能。「小説を探す」はリポジトリ全体で0件 | BookshelfScreen.kt | 撤去が確定していることを再確認して2項目を削除。決着済みの設計問い（🔍の着地）が未決に見える状態を解消 |
| **F-6** | `backlog-frozen.md` の凍結項目 file:line 3件が全て別の場所を指す。うち①`filterBooksByQuery` は編集中ファイルと無関係に確定（台帳 `ShelfItems.kt:37` は RecencyKey の KDoc 中／実体は `domain/ShelfItems.kt:417`・パスの `domain/` も欠落）。②`BookshelfScreen.kt:442` はバッテリー設定ダイアログ ③`DiscoverySearchScreen.kt:203-207` は選択中キーワード追従バー | BookshelfScreen.kt / DiscoverySearchScreen.kt | ②③は行ズレか実質ズレかを実装完了後に判定。①は今すぐ直せる |
| **F-7** | `PdfBookImporter.kt:349-351` の「BookRepository ④/⑤ のコメント参照」先に ④/⑤ は存在しない（2026-07-27 の責務分割で番号は同ファイルの `addBook` 内 :190/:247 へ移った）。4行上の `addBook ⑤` は正しく自ファイル参照している | PdfBookImporter.kt | 分割後の参照先へ張り替え（「本ファイル ④/⑤」で足りる） |
| **F-8** | KBottomNav の壊れた3枚（`KBottomNav_bookshelf_*_2.0`＝本監査の出発点・`KBottomNav.kt:61` の `.height(64.dp)`）の**再記録が実装便に含まれるか** | KBottomNav.kt | 実装完了後に3枚が再記録されたかを確認。されていなければ壊れた絵が残る |

---

# 却下（生き残ったが実害が薄いと判断したもの）

- **R-1 `known-bugs-registry.md:90` の「297組」（実測 330組）** — 数値ズレは事実（`check_design_tokens.py` の実出力は `PASS=306 NG=0 BASELINE=24 …(pairs=330)`）だが、10%のズレで反転する具体的な投資判断が示せず、同じ行の質的記述（`.copy(alpha=)` 合成・グラデーション面・Color.kt 直参照は未検査／CI で毎push）は現状と一致している。**ただし D-第1投資「数値の一括掃除」の対象には含める**（数字を消して checker 実行へ誘導する）。
- **R-2 `PdfBookExtractor.kt:11` の `BookRepository.ProgressListener`** — この型はリポジトリ全体に0件で phantom 参照なのは確定（`git log -S` でも存在した履歴が無い）。しかし名指し先のファイル自体は正しく、開けば同じ4引数の対応物（`BookRepository.kt:99` の無名ラムダ）がすぐ見つかる＝誤りが自己訂正される。「シグネチャ更新が漏れる」という帰結は飛躍。
- **R-3 `VerticalChapterContentScreenshotTest.kt:110`「長い題（縦書きで複数列に折り返し）」** — 題は21字×38px=798px で列高1200px に収まり折り返していない、というのは画素実測どおり。ただし単独で宿題化すると G-9（撮影条件の設計）と二重管理になるため、**G-9 に吸収**して却下する。