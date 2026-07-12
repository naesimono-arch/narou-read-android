# handover — やること台帳（main）

> **次に何をやろうか悩んだら、まずここを見る。**
> 作る予定のもの・あとで拾う思いつき・その場から漏れた取りこぼしを書き溜める場所。
> 思いついたら「思いつき・取りこぼし」へ追記して育てる。
>
> **ここは「やること」だけを置く。** 完了したら打ち消し線で残さず**消す**（完了の正本は **git log**・現況は `STATUS.md`・
> 実コミット等の一次情報は `.claude/plans/`・腐りにくい知見は `docs/knowledge/`〔1知見=1ファイル。`task_diary.md` は凍結アーカイブ〕）。
> 打ち消し線を溜めると「やったことリスト」に化けて台帳の役目を失う（運用: memory `docs-status-vs-handover-split`）。

## 思いつき・取りこぼし（随時追記）

> レビュー中・実装中に出た宿題や着想で、まだ下の各節に整理していないものをここへ。育ったら該当節へ移す。

- **[機能②・要判断] U1 新着チェックとの整合**: 続きあり判定は現状 PDF 蔵書の generalAllNo 突合のまま。Web カードの既読話数（web_reading_progress）を U1 基準へ組み込むかは別タスク（機能②実装時からの持ち越し）。
- **[取込] 「PDFを取り込む」ボタンの不安定さ**（2026-07-10 ユーザー報告・**再現条件未特定**）: FAB/空状態ボタンからの picker 起動が不安定に感じられることがある。まず再現条件の聞き取り（どの画面のボタンか・無反応か遅延か・バッテリー最適化ダイアログ絡みか）→ logcat での再現観察から。推定候補: 通知権限→picker の2段ゲート・バッテリー最適化ダイアログの分岐（`launchPdfPicker` 周り）だが未確定のため決め打ち修正はしない。

---

## ★UX/Design 全層監査 — 残タスク（2026-07-12）

> **これは何か**: `/mnt/c/Users/qingj/Desktop/project/UX`（UX24層＋Design10層＋公理候補）に対し novel-reader 全体を多エージェント監査（45体・敵対的検証済み）した指摘の、**残っている作業だけ**の action list（消化したら行を消す）。
> 消化済み分の一次情報＝`.claude/plans/ux-design-full-audit-2026-07-12.md`（§A 統合報告／§B 全指摘詳細・良い点含む）＋`.claude/plans/ux-audit-batch-execution-20260712.md`（実行記録）・実装＝git log（`ui/polish`）。
> **対象ブランチ**: `ui/polish`（この worktree＝ext4）。ゲート＝`cd android && testDebugUnitTest`（init-script 不要）＋`python3 tools/check_design_tokens.py`。**意匠絡みは Compose で自己判断せず ADR0005/0014＋モック正本に先に接地**。実機絡みは PushNotification→目視OK→コミット。

### 残0: main への統合
- 残1 の裁定・翻訳が一段落したら `ui/polish` を main へ --no-ff 統合（コミットは worktree 内セッションから＝guard の機序は memory `ff-merge-sentinel-not-consumed`）。

### 残1: 重いデザイン系（/design・DesignSync は主セッション限定＝委譲不可）
- **[F] 余白の離散スケール再設計**（確認バッチF＝モック作り直し裁定・**§C原則維持**）: **悉皆調査完了（2026-07-12）**＝全モック22＋Compose32ファイルの任意dp/px余白を洗い出し（生データ `.claude/plans/F-spacing-audit-raw-2026-07-12.json`）。**核心発見＝確定スケール{4,8,16,24,40}が実データに粗すぎる**——最頻オフスケール値が中間帯に集中（**12＝Compose首位76件**が8/16の完全タイ・20が16/24タイ・32が24/40タイ）。厳格に丸めると中間密度が潰れ原則2「余白は要素」に反する。比較モック `docs/design-candidates/spacing-scale-compare-D.html`（2026-07-13 に**正本 reading-D／settings-D の実DOMを丸ごと再現**し余白トークンだけ**現状⇔拡張7段でトグル**する忠実版へ組み直し＝同じ実ページの離散化 A/B。旧版は縮約抽出で正本タイポと乖離との指摘を受けての是正。棄却された厳格5段列は外した）。**★スケール裁定＝拡張7段 {4,8,12,16,24,32,40} で確定（2026-07-13）**（12・32のみ追加＝§C(ADR0014)改訂を要す）。§C(ADR0014)改訂＋Why記録は完了（2026-07-13・丸め則round-half-upも同裁定に記録）。②Spacing.kt＋Insetsは完了（2026-07-13・Compose実在の60/96/+64/+80を意味別命名。モック側92/210はlint allowlist・44/70/90はハーネス）。残＝③`check_design_tokens.py` にSpacing lint（集合membership＋トークン参照・除外規則は悉皆調査と厳密一致）追加 ④正本モック離散化 ⑤Compose再翻訳の残り＝`BookshelfScreen`→`ChapterContent`（`DiscoverySearchScreen` は完了 2026-07-13・実機目視は3画面まとめて最後に1回）。**規模大＝独立の集中便（fresh/plan）向き**。
- **[C②] 本棚先頭の発見帯の去就**（確認バッチC②＝撤去でなく /design ですり合わせ）: 試作モック **`docs/design-candidates/discovery/bookshelf-band-reposition-D.html`**（現行の**栞書影正本**エンジンを流用・4案＝現状/A一行融合/B退避/C末尾タイル）作成済み。**ユーザー裁定＝案B「スクロール退避」で確定（2026-07-12）**＝先頭到達時のみ帯フル表示・下スクロールで帯を畳み・状態フィルタ（よみかけ/未読/読了）は sticky で残す。残＝Compose翻訳（`BookshelfScreen.kt:582-643` の `FindGuideBand`＋フィルタ行を collapsing header＋sticky filter へ）。ADR0005/0014 接地。
- **[C①] 読書ギアの代替導線**（確認バッチC①＝撤去せず代替試作を見たい・**探索であり改善確約でない**）: 試作モック **`docs/design-candidates/reading-gear-alt-D.html`**（案A＝下端集約／案B＝下端2段）作成済み。**ユーザー裁定＝案A「下端集約」で確定（2026-07-12）**＝右上ギア撤去→上端は←＋章題のみ→中央タップでクローム表示→下端バーを4分割[前章｜目次｜**表示設定**｜次章]→表示設定タップで下端シート（中身は settings-D 正本のまま不変）。残＝Compose翻訳（`NativeReadingScreen.kt:920-928` のギア撤去＋下端バーに表示設定エントリ追加＋中央タップ起動）＋**reading-D.html 正本の更新**（ギア撤去・下端バー反映＝alt mock を正本へ）。旧「モックにギア実在で矛盾・実装停止中」は本裁定で解消。

### 残2: 要検証 実機送り（6件・`/device-verify`・コード修正で閉じない）
- 回転レース: 回転直前の最終スクロールデルタ(≤400ms)が保存に間に合わずレース→章を読みつつ即回転反復で DB 突合。
- 回転オフセット: 形態遷移（回転/折り畳み）で段落内 px オフセットが指す行がずれる→長段落で回転し何行ずれるか。
- ノッチ横向き: 横向き/サイドノッチで行頭・行末がカットアウトに欠ける→`displayCutout` 合成を横向き目視。
- TalkBack到達: 没入バー退避時に戻る/目次/前後章へ TalkBack スワイプ走査で到達可能か。
- ルビ掛け: 長ルビ・隣接ルビ間アキ制御なし→長ルビ実データで実機目視。
- 大フォント行数: 大フォント×広余白で1行字数が極端減（18sp≒18字/24sp≒11字）→各設定の体感リズム確認。

### 残3: 人間テスト送り（7件・UX/17 Krug 式）
> `.claude/plans/usability-test-protocol-2026-07-12.md`（T1〜T7）にタスク化済み。AI/実機で確定不能（2週間後・無説明での操作言語混乱・スコープ誤解・中央タップトグルの再発見可能性・通知テレポート着地の本特定 など）。実施は人間ユーザビリティテスト便で。

### 残4: 監査派生 backlog（新規タスク）
- **蔵書内フィルタ/series 束ね UI**（確認バッチC④＝保留）: ロジック `filterBooksByQuery` は実装済み・UI はモック未表現のため保留（`BookshelfScreen.kt:442`／`ShelfItems.kt:37`）。series 束ねはスキーマ変更要（設計案のみ）。
- **目次の部/編 折り畳み**: 抽出パイプラインに階層データ無し＝**抽出側の新機能**。実PDF→HTML の階層有無は要検証で「フラット確定」＝畳みは前提データ欠如で現状不成立。
- **Macrobenchmark 新設**: measure 要検証（大PDF/10倍蔵書/長時間送りの予算漸進劣化を P90/P99 で assert）＝独立タスク。INTERNET 無しで出荷後テレメトリ不能の代替。
- **lint 新 warnings（任意改善・非ブロック）**: ModifierParameter×3（新設 composable）・UsableSpace×2（getAllocatableBytes 併用提案）。

### 残5: 実機目視の残確認（記録上「ユーザー確認待ち」のまま閉じていないもの・確認済みなら行を消す）
- 検索/発見系フィードバック5件バッチ（2026-07-12・`ui/uiux-tasks` レーン＝ジャンル絞り込み・履歴チップ×押し出し・詳細CTA主従逆転・条件チップ縦ずれ）: 実機目視の最終確認が記録上「ユーザー確認待ち」のまま。
- デザイン正本の層構造整備①〜⑤（同レーン＝ヘアライン是正・UnreadSeiji・Motion トークン化ほか）: 実機目視未実施の記録のまま。
- 了スタンプ押下アニメ（案A・実装済み＝ADR0014 §motion 追補）の実機目視: 読了へ遷移した本を本棚が初めて読了として描くときの押印の質感・タイミング（scale 1.2→1.0／回転／透過の体感）。コードは閉じたが質感は実機後詰め層（ADR0005-B）＝目視確認は未。

## UI/UX 宿題

- **[モック追従・構造] 発見系モックの情報/装飾テキスト再分類**（2026-07-12 `a9a6a5c` 実装時に留置）: `InfoText` トークン（実装済み＝発見系の情報メタ6箇所を AA(4.5:1) へ引き上げ・Light #5C606D／Sepia #6C6148／Dark #8A929B）の discovery/*.html モックへの追従は、`--ink-soft` を共有する **10〜16箇所/ファイルの情報・装飾テキストの個別再分類**＋`--info-ink` 変数の新設＋`tools/check_design_tokens.py` へのマッピング追加が必要＝構造的大改修と判定し留置。**現状の一致検査は InfoText を未トラッキングで PASS＝この層ズレ（コードが AA へ引き上げた面をモックが `--ink-soft` のまま持つ）は未検知**になる点に注意。

## なろうAPI 発見・検索機能（第2の柱）

> Phase 0〜4 完了（現況＝`STATUS.md` §0・実装＝git log）。目標ロードマップ・作る機能一覧の一次情報＝plan `~/.claude/plans/api-agy-woolly-swan.md`。監査残課題（構造系）は下の「リファクタ / 技術的負債」。

## リファクタ / 技術的負債（deferred）

- **検索画面 S3＝カテゴリ列の LazyColumn 化（保留・要否判断）**: 重さの正体は「カテゴリ展開状態での操作毎の全画面再コンポーズ」で、S1（選択判定 Set 化・Regex 定数化）/S2（strong skipping＋@Immutable）は解消済み・実機体感は軽快（2026-07-11 実測）。残る理論コスト＝非 Lazy Column 上の22カテゴリ/115チップ（`DiscoverySearchScreen.kt:203-207`）の画面外存在コストと「全展開のまま再訪」の初回構成。**体感問題が再報告されるまで保留が妥当**。
- **R8/リソース収縮が無効**（`android/app/build.gradle` `minifyEnabled false`・`shrinkResources` 無し）: 有効化が**単独最大の軽量化レバー**（APK 24MiB の dead code/未使用リソース分）。Moshi/Room は codegen/KSP で keep は軽微見込みだが PDFBox/Retrofit/OkHttp の keep 確認＋実機回帰（`/device-verify`・収縮起因クラッシュはリリースでしか出ない）必須。
- `web_reading_progress` に prune/削除経路が皆無（upsert のみの単調増加。`removeWebNovel`/`deleteBook` とも触れず、new_episode_marks の日次 `pruneExcept` と非対称）。個人スケールでは無害だが設計の穴として記録。
- MigrationTest が「16.json 形状（web_reading_progress 無し）→17」経路を構造的に検証できない（chain テストは 14→15 でテーブルが生まれる系譜のみ通過）。既知の実機 v16→v17 未検証と同根の coverage-hole として記録。

## workflow / tooling

- **[モック陳腐化防止 hook の検討（2026-07-13・本セッションの F 比較モック drift が動機）]**: 派生モック（比較/プレビュー系）が正本モック（reading-D 等）やトークンの値を**複製**するため、正本更新で silent に陳腐化する（今回＝F 比較モックが正本の本文タイポ 16.5/2.4→旧 15.5/2.3 に drift・栞カバーも正本 canvas エンジン未流用の CSS 近似だった＝ユーザー2度指摘で発覚）。案＝①派生モックは正本を複製せず**参照**する規約化（`@dsCard` に「正本ソース＋最終同期日」メタを必須化し drift 検知）②`check_design_tokens.py` に**タイポ／スペーシングの mock⇄正本突合**を追加（現状は色の mock⇄token 同期のみ＝この drift 種別は未検知）③正本モック編集時に派生モックへ「要再同期」を立てる PostToolUse hook。フック新規は先に `task_diary.md` #26/#28・`docs/decisions/0004`/`0008`（サイレント失敗クラス）を確認。

- **[bestpractice 突合の回収候補（2026-07-12 調査）]**: ①スキル frontmatter の `triggers:` は標準外フィールドの可能性（自動発動は `description` 単体が正）＝ハーネスに解釈されているか検証 ②`block_destructive_migration.py` の Bash 経路が素朴な部分文字列一致（`FOO=1 cmd`・`$()` ですり抜け）＝settings permissions の `if` フィールド化を検討（主経路の Edit/Write 捕捉は健在で実害小） ③`tools/check_design_tokens.py` のコミットゲート配線 or 手動運用継続の裁定 ④サブエージェントの部品別モデル配分（fan-out/読み=haiku・照合=sonnet・監査=opus。現状は env `CLAUDE_CODE_SUBAGENT_MODEL` で opus 固定＝見直しは settings 変更を伴う）。

- **antigravity-delegate サブエージェントの同期実行が保証されない**（2026-07-07・委譲5件中3件で再発): agy をバックグラウンド起動したまま「待機中」で終了し完了通知が来ない。プロンプト明記・SendMessage 再開でも再発。運用回避（CLAUDE.md 委譲判断節に反映済み）＝完了判定を報告でなく**成果物の存在**（`git status`/grep/`ps`）で行う。**根治候補**＝プラグイン側で agy 起動を同期実行へ強制するか wrapper にポーリング内蔵。優先度中（運用回避が効き非ブロッキング）。
- **worktree(ext4) 作業の冒頭で `gw :app:lintDebug` を回す運用**: Lint コミットゲート（`check_lint_on_commit.py`）は drvfs でスキップされる設計＝canonical 作業が続く限り事実上無効。ext4 worktree なら in-tree で回るので冒頭で1回スイープする。基準＝0 errors/26 warnings（2026-07-12 時点）。

## 実行捏造検知器（ADR 0006）残タスク

> エンジン＝`.claude/hooks/detect_fabricated_execution_core.py`。完了分は **ADR 0006（増補含む）と git log が正本**。以下は開きのみ。

- **Tier B 汎用主張の免罪の限界**（事象D）: 「セッション内に成功実行が1回でもあれば免罪」で後半の汎用捏造を取りこぼす。具体値主張は具体照合に絞ったが汎用主張の掘り下げは将来課題。**進捗（2026-07-11・増補6）**: Tier E カテゴリ別突合が**現ターン分**の同根系列を吸収した。**過去ターンの汎用主張の掘り下げは引き続き将来課題**（Tier E は現ターン境界での判定）。
- **[2026-07-11・増補6 で切り出し] L44/L55 型「先行実行フレーミング」（完了主張形を持たない幻の先行実行参照）の検知**: 事象N の L44「出力が返っていないので結果を確認します」（存在しない先行 tool_use への参照）・L55「再作成します」（未実行の再作成宣言）は**完了・検証の断言ではなく段取り宣言**＝照合キーが無く Tier E の完了語トリガに当たらない。検知には「主張以前に対応する tool_use が無い先行実行参照」という別系統の突合が要る。要較正・真陽性は台帳N の L44/L55。
- **[2026-07-11・増補6 保留設計] 案3＝委譲主張の E2 突合（opt-in）**: 「〜を委譲した／agy に生成させた」等の委譲完了主張を、**委譲先 transcript（`subagents/agent-<id>.jsonl`）の tool_use とカテゴリ突合**して裏取りする案。**真陽性サンプルが皆無のため保留**（設計要点のみ保全）。真陽性が観測されたら着手。
- **[2026-07-11・増補6 副産物] 370800c1 の台帳レター事象化（人間確認待ち）**: D4 較正の slug 全走査で発見した未登録真陽性＝assistant text 内に `user[Request interrupted for tool use]`＋幻のユーザー発話を自己生成（幻発話テキストの実人間入力不在は transcript 直読で確認済み）。D4（CLI）と A3 変種拡張（live）の両方で active。人間確認が取れたら台帳の追記手順（事後検証モード）でレター事象化。
- **[2026-07-11・増補6 残課題] D5 対象語突合の字面依存FPクラス**: D5 は帰属対象の名詞（違和感/懸念/指摘…）の**字面**を実入力に探すため、ユーザーが真に指摘したが当該名詞を書かなかった場合「あなたの指摘は的を射て」型が潜在FP化しうる（現コーパス実測 0件・非ブロック Tier D で被害限定）。同義語・意味突合の導入は将来課題。
- **[2026-07-11・増補6 で切り出し] Tier E の Stop 昇格の再判断**: 新既定 ABCDE での CLI 運用実績（真陽性の積み上がり・FP 率）が揃ったら再判断。昇格には conf 設計の引き上げ（現 0.55-0.7 → Stop 閾値 0.8）または Stop 側の per-rule 閾値の新設計が必要。
- **意味照合系検知器**（着想段階・スコープ外構想）＝生成コード不具合・外部リサーチ捏造（正解データ事象B/C）。

## D. 長期・品質（backlog）

- **左右スワイプで章遷移**: 旧 `experiment`/`lab-old` は WebView 実装で流用不可。`HorizontalPager`/`pointerInput` で新規。チューニング知見＝軸ロック(`de60869`)/EMA+isDragging(`a07dd3e`)/距離OR速度複合(`4a0719b`)。元コミット `23b5f33`（main 未取り込み）。
- **[抽出] 単話（1話完結）作品の縦書きPDF変換で、本文が「作品情報（プロローグ）」側に乗り章題名も出ない**（2026-07-09 PDF取り込み導線の実機通し検証中にユーザー観測・対象 n2959ki）: 単話作品は章見出し／目次構造が無いため、章分割が本文を作品情報ページの続き扱いで流し込むと推定（**未確定**・要調査）。**やること**: ①n2959ki の抽出結果現物（`novels/<id>/index.html`・`chap_N.html` 構成）で事象を再現確認 ②単話 PDF の構造に対する分割ルール（`ParserRules`/`ChapterProcessor`）の扱いを設計 ③**ゴールデン基準との整合に注意**＝N2959KI はゴールデン本（`ab-review/golden_regression`）であり、基準自体がこの挙動を「正」として固定している可能性がある——修正はゴールデン更新とセットで判断すること。
- **超長編抽出エッジ残差の③アポストロフィ座標順**（N6169DZ・章題ドリフト残2件）: `兎'ｓ`↔`'鳥…` の座標順ずれで**1:1コードポイント置換不可**＝実質 won't-fix。基準＝`ab-review/golden_regression`、詳細＝task_diary #35。

## A2. UIスキン着せ替え（将来送り・保留）

> フェーズ0で D「和モダン・余白」をデフォルト視覚言語に採用済み（設計判断＝`docs/decisions/0005-ui-n-visual-language-D.md`／モック地図は `.claude/plans/archive/UI-n_DESIGN_PLAN-archived-2026-07-02.md` §6.1）。

- **方針確定（2026-06-27・ユーザー指示）＝UIスキン着せ替え（A〜J 選択）はまだ実装しない。main は現状 D のみ。** A〜J は資産として claude.ai/design（プロジェクト `Novel Reader UI`・projectId `bb5a35c8-70ac-4efa-bb03-1579d3f11d93` の `ui-n-phase0/`・`DesignSync: get_file` で再取得可）に保持。栞の他4型（A箔/C小口/D蔵書印/E綴じ紐）もスキン資産として保持（ADR 0005 C 方針）。
- **着手時はここから**: 「UI着せ替え」設定画面のモック化（選択肢=A〜J・既定=D・切替粒度の決定）／A〜J スキンの Compose 実装（スキン×読書テーマの関係・トークン体系）。bookshelf-D へのセピア変種追加もスキン着せ替え実装時に再検討（現状は `SepiaColorScheme` が本棚セピアの正本）。
