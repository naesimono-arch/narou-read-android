# handover — やること台帳（main）

> **次に何をやろうか悩んだら、まずここを見る。**
> 作る予定のもの・あとで拾う思いつき・その場から漏れた取りこぼしを書き溜める場所。
> 思いついたら「思いつき・取りこぼし」へ追記して育てる。
>
> **ここは「やること」だけを置く。** 完了したら打ち消し線で残さず**消す**（完了知識の正本は `STATUS.md`。
> 実コミット等の一次情報は `.claude/plans/` のアーカイブ・腐りにくい知見は `task_diary.md`／`docs/`）。
> 打ち消し線を溜めると「やったことリスト」に化けて台帳の役目を失う（運用: memory `docs-status-vs-handover-split`）。

## 思いつき・取りこぼし（随時追記）

> レビュー中・実装中に出た宿題や着想で、まだ下の各節に整理していないものをここへ。育ったら該当節へ移す。

- **[機能②・要判断] U1 新着チェックとの整合**: 続きあり判定は現状 PDF 蔵書の generalAllNo 突合のまま。Web カードの既読話数（web_reading_progress）を U1 基準へ組み込むかは別タスク（機能②実装時からの持ち越し）。
- **[取込] 「PDFを取り込む」ボタンの不安定さ**（2026-07-10 ユーザー報告・**再現条件未特定**）: FAB/空状態ボタンからの picker 起動が不安定に感じられることがある。まず再現条件の聞き取り（どの画面のボタンか・無反応か遅延か・バッテリー最適化ダイアログ絡みか）→ logcat での再現観察から。推定候補: 通知権限→picker の2段ゲート・バッテリー最適化ダイアログの分岐（`launchPdfPicker` 周り）だが未確定のため決め打ち修正はしない。

---

## 本棚 書架（グリッド）ビュー — 栞書影 ✅全意匠課題消化（2026-07-12）

> 実装完了の詳細は **STATUS §1「栞意匠」項＋「栞整合」項が正本**。意匠の正本＝`docs/design-candidates/bookshelf-shiori-grid-D.html`（生成規則・スケール補正の詳細は `ShioriCover.kt` コメント）／整合＝`bookshelf-shiori-consistency-D.html`／色域確定記録＝`bookshelf-shiori-palette-D.html` フッター。
> 2026-07-11 オーナー裁定「本棚モックはすべて確定」を受け、残っていた3課題を消化:
> ①リスト⇄グリッド色相共有＝**実装完了**（2026-07-12・consistency-D 翻訳） ②色域＝**現行（全周和リング）維持で確定**（palette-D フッターに記帳） ③他4型（A箔/C小口/D蔵書印/E綴じ紐）＝スキン資産として保持（ADR 0005 C 方針どおり・「A2. UIスキン着せ替え」参照）。
> 探索の記録（採用前の参考・正本ではない）: `bookshelf-geo-D.html`／`bookshelf-generative-directions-D.html`／`bookshelf-shoka-D.html`／`bookshelf-cover-D.html`。

## UI/UX 宿題

- **補助テキストのコントラストが通常文字 AA(4.5:1) 未達**（2026-07-08 実機＋`theme/Color.kt` 実測）: 著者名・進捗% の `OnSurfaceVariant #7C808B` は **Light 3.79 / Sepia 3.02 / card上 3.46**＝大字のみ合格・通常文字 AA 未達。青磁未読ラベルは Light **2.14**（コード L18-21 に「意図的・後日再検証」と既記載）。本文・藍アクセント・ダーク各テーマは全て合格。**意匠上「静かに沈める」意図は理解しつつ、進捗% など情報性のある要素だけ視認性を一段上げる余地**。要 UI-n 意匠判断。
- **デザイン正本の層構造整備（2026-07-08・UX/Design 知識ベースとの突合で確定）**: 出典＝`C:\Users\qingj\Desktop\project\UX\Design`（7篇・本プロジェクトを対象に書かれた意匠の決め方 KB）。中心診断「HTMLモック（姿）が思想と語彙の正本を兼任し、ドリフトが実測レベルで開始済み」はコード突合で事実確認済み。着手順の提案:
  - ① **原則の逆抽出を一度だけ正式化し ADR 化**: KB 01 §4 の5原則ドラフト（本文が主役／余白は要素／色は意味／一画面一強調／静謐は機能）＋ 04 §2 の禁止則表がほぼ完成品。裁定の上位審級を作る（⋮アフォーダンスや青磁コントラストを「原則N番との衝突」として書けるようにする）。
  - ② **値の単一性回復**: ヘアライン2値の裁定（モック全6ファイル `--line:#ECEAE4` vs `OutlineVariantLight=#E4E2DB`、`Theme.kt:61/63` で両値が無記録混在、`Color.kt:9` 冒頭コメントとも食い違い）／直書き3件の裁定（`BookCover.kt:118` 書影藍ルール `#6E96B8`＝D署名要素がトークン外→昇格・`ProcessingBanner.kt:41` `#D7C6BF`＝旧「紙と墨」暖色パレットの取り残し・`RubyText.kt:69` `#777777`＝死デフォルト［実描画は `colors.ruby` 経由でテーマ追従済み］）／モックCSS変数⇄`Color.kt` の一致検査スクリプト1本（tokens.json/Style Dictionary 導入は現段階では過剰＝KB 03 §2 の現実解）。
  - ③ **UI-n 主要4画面モックのリポジトリ収蔵**: bookshelf/reading/toc/settings-D が claude.ai/design のみ（バージョン管理外・diff不能）で、発見系6画面（リポジトリ正本）と二重基準。`docs/design-candidates/` へ収蔵し claude.ai 側を収蔵コピーへ降格、ADR 0005・CLAUDE.md の正本記述も追従。
  - ④ **Roborazzi スクリーンショットテスト**: ADR 0009 の Robolectric 基盤への増分導入。3テーマ×フォントスケールの golden 画像で「ライトとセピアが同色」級の退行を機械検知。実機検証待ちの「コントラスト比 WCAG」「フォントスケール200%」項と合流可。
  - ⑤ **原則による裁定2件**: 青磁「未読」約2:1（`Color.kt:19-21` の即席判断「モック完全準拠＞可読性」を「可読性最低線＞美学・沈め表現は補助限定」の原則で裁定し直す。実機でも沈みを確認済み）／motion 層の「枠」正本化（duration/easing のトークンスロット＋「motion はフィードバックのみ」の1行。値は実機調整のままでよい＝ADR 0005-B の後詰め層に正本ゼロという穴の最小手当て）。
- **条件を調整シートの高速フリック「枠オーバーシュート」**（2026-07-08・**未修正・不具合残置**）: 高速フリックで手を離すとシート枠が Expanded 上限を超えオーバーシュート→復帰し上端に裏画面が覗く。真因（ModalBottomSheet 内蔵接続が境界フリング残速度を settle へ渡す）・棄却済み候補A〜D・本命の解・BOM 1.3.1 での再現から始める旨は **task_diary #51** が正本。要 UI-n 意匠確認。
- **本文処理の進捗にページ数もリアルタイム併記**（2026-07-06 実機でユーザー要望・優先度低）: 現状は通し%表示（%表記はユーザー評価「完璧」）。ページ n/m も併記できれば体験向上。**設計注意**: load(全ページ)と process(本文=全−4)は分母が違い、単純2カウンタ併記は「2周目に入った」錯覚が再発する→単一の連続ページ尺にするか %主・ページ副に。実装点は `PdfBookExtractor` の step-1 phase 文言（＋必要なら `ProcessingBanner`）。

## なろうAPI 発見・検索機能（第2の柱・Phase 4/5 残り）

> なろう公式APIの発見機能を「第2の柱」に育てる計画（案A＝本文非取得・メタのみ）。Phase 0〜3＋Phase 4 スライス1 は完了・main 統合済み（現況は `STATUS.md` §1）。
> 目標ロードマップ・作る機能一覧の一次情報は plan `~/.claude/plans/api-agy-woolly-swan.md`。監査残課題（構造系）は下の「リファクタ / 技術的負債」へ移設済み。

- **Phase 4 完了（2026-07-10）**: 全項目消化＝STATUS §1 参照。
  - ~~(b) Web由来・未取込カード~~ → **完了**（2026-07-09 `a6569ee`+`15d9e1a`・実機目視OK＝STATUS §1）。
  - ~~U1 新着話チェック＋通知~~ → **完了**（2026-07-10 `2789512`+`0b2d2b7`・実機E2E全GREEN＝STATUS §1。強制発火の罠は task_diary #53）。
  - ~~U2 整理（ラベル分類）~~ → **完了**（2026-07-10 `30762aa`+`a7e403e`・Room v14・実機目視OK＝STATUS §1）。※その後 **2026-07-11 に機能ごと撤去し読書状態フィルタへ置換**（STATUS §1 先頭）＝「Web由来カードへのラベル付与」将来拡張は消滅。
- **Phase 5 doc昇格**: 本タスク（STATUS-api-lab 解体・2026-07-08）で大半消化（ADR 0010 化・architecture スキルへ発見/検索層の所在追記・なろうAPI実装 why を `docs/patterns/narou-api-discovery.md` へ集約）。残＝`docs/reference/03-api-feature-analysis.md`↔`04-competitor-app-features.md` の相互リンク程度（優先度低）。

## リファクタ / 技術的負債（deferred）

- ~~[発見系・構造] `SearchConditionSheet` の残リファクタ（監査残課題2の残り）~~ → **解消済み**（2026-07-11 `1b83684`＝カスタム範囲入力 約90行×2 を `CustomRangeInput` へ部品化・段階チップの値とラベルを `RangeStep` 対定義（`SearchDraft.kt`・既存 `LENGTH_STEPS`/`TIME_STEPS` は射影で温存＝呼び出し側無改修）へ統合。764→669行・見た目/挙動不変＝STATUS §1。※当初「discovery-search-D すり合わせ時に同ファイルを触るとき」予定だったが refactor/tech-debt レーンで先行消化。**モック追従＋案B翻訳のタスク（上記）自体は残存**）。
- ~~[発見系・将来の罠] `NovelApiRepository` のインメモリキャッシュの Main dispatcher 前提（監査残課題5）~~ → **解消済み**（2026-07-09 `13c97f2`＝Mutex 排他＋word trim 非対称の修正。U1 Worker 化の前提として先行実施）。

- ~~`saveScrollPosition(bookId, filename)` 等の String 連続の型付け~~ → **解消済み**（2026-07-11 `013b06a`＝`BookId`/`ChapterFilename` value class 新設（`model/BookIdentifiers.kt`・Ncode と同じ素通し方針）で saveProgress/saveScrollPosition/linkNcode/getLastRead/getProgress を Repository/VM/UI 貫通で型付け。Room Entity/DAO・Map キー・nav ルート文字列は String 維持＝境界 `.value` unwrap（線引きの why は BookIdentifiers.kt の KDoc）＝STATUS §1）。
### コード健全性監査の指摘（2026-07-11・`refactor/tech-debt` で6観点並列監査・挙動バグ3件は反証専門エージェントで CONFIRMED 済み）

> 監査体制: 観点別6エージェント（並行処理/エラー処理/Room/デッドコード/Compose/API・テスト）＋描画性能2＋検索画面深掘り1＋敵対的検証3。**クリーン確認済みの面**: デッドコード・撤去残骸ゼロ（TODO/FIXME 0件・未使用リソース/依存なし・ラベル/Chaquopy 残骸なし）・直近リファクタ4コミット退行なし・読書画面の描画設計は高水準（ルビ=1段落1BasicText+Canvasオーバーレイ・バー退避=graphicsLayerラムダ・スクロール観測=snapshotFlow・パース/AnnotatedString/TextStyle は remember 済み＝「ルビ数万ノード」「フォント変更で全文再パース」は不発生）。

**確定バグ（検証CONFIRMED・3件とも解消済み＝2026-07-11 一括消化バッチ・STATUS §1）**

- ~~**[クラッシュ経路] 検索履歴 DataStore の IOException 未処理**~~ → **解消済み**（`5815802`＝corruptionHandler＋読み Flow の IOException 空履歴フォールバック＋書き込み側許容。※監査記述と異なり CorruptionException は IOException のサブクラスだが、`.catch` では破損ファイルが残り毎回失敗し続けるため恒久復旧に corruptionHandler が別途必要——両対策で正）。
- ~~**[レース] pending_jobs 記帳の直列化が実は不成立**~~ → **解消済み**（`cccb4dc`＝DAO 呼び出し完了までロックを保持する `pendingJobMutex` で全 pending_jobs 書き込みを排他・`limitedParallelism(1)` 撤去。機序の一般知見は task_diary #55）。
- ~~**[FGS] onTimeout 後の旧ループ finally が新ループを道連れ**~~ → **解消済み**（`f70b937`＝ループ世代カウンタ。採番を launch 前の main スレッドで確定・閉じ込め、旧世代の finally は新世代の isLoopRunning/stopSelf に触れず退場。通常経路は世代が進まず挙動完全一致）。
- **[小粒・新規＝上記修正の副産物指摘] `processSingleUri` 側 finally の通知カウントに世代ガード無し**: onTimeout が `doneCount=0` リセット後、旧ループ在籍中の1冊が遅延キャンセルされると `doneCount` が進み、新バッチ初冊の通知が「2/1」等と一時表示されうる（表示のみ・実害小）。`f70b937` と同じ世代照合の横展開で対処可。

**検索画面の重さ（ユーザー実体感あり・診断確定）**

- 正体＝「**カテゴリ展開状態での操作毎の全画面再コンポーズ**」（既定の全畳みは軽い＝「重いときがある」と整合）。キーワード22カテゴリ/115チップが非 Lazy Column（`DiscoverySearchScreen.kt:203-207`）上にあり、`SearchDraft` が Set 内包で unstable＋strong skipping 無効のため**毎キーストローク最大115チップ全再コンポーズ**、さらに選択判定 `containsWordToken` がチップ毎に Regex 再コンパイル（`SearchDraft.kt:223-224`・メモ化なし）。展開状態は rememberSaveable 保存→全展開のまま再訪すると**初回オープンから重い**第二経路。アニメ・履歴 DataStore・キーワード定義構築はシロ。
- 修正順: ~~**S1** 選択判定を `remember(draft.word)` の Set 化＋Regex をトップレベル定数化~~ → **解消済み**（`a3662c2`・全ケース同値テスト付き）→ ~~**S2** `experimentalStrongSkipping=true`＋`@Immutable`~~ → **解消済み**（`b1c0bfc`・stability レポート機械検証に加え **2026-07-11 実機全画面スモーク GREEN**＝本棚フィルタ/検索チップ/結果並替/詳細キーワードトグル/発見タブ/テーマ一括切替/目次で stale UI ゼロ・検索体感は軽快）→ **S3** 外側を LazyColumn 化（中・画面外チップの存在コストと全展開再訪の初回構成。**S1/S2 実機体感=軽快（2026-07-11 実測・引っかかりなし）を踏まえ要否判断**＝残。体感問題が再報告されるまで保留が妥当）。

**描画/ビルドの軽量化（読書画面以外）**

- **R8/リソース収縮が無効**（`android/app/build.gradle:22-25` `minifyEnabled false`・`shrinkResources` 無し）: 有効化が**単独最大の軽量化レバー**（APK 24MiB の dead code/未使用リソース分）。Moshi/Room は codegen/KSP で keep は軽微見込みだが PDFBox/Retrofit/OkHttp の keep 確認＋実機回帰（`/device-verify`・収縮起因クラッシュはリリースでしか出ない）必須。
- ~~ルビ描画パスの Paint×2 再生成＋`calculateRubyPositions` 再計算（`RubyText.kt`）~~ → **解消済み**（`8f452a3`＝Paint/ascent の remember 化＋位置計算を TextLayoutResult×rubyRanges のインスタンス同一性でキャッシュ）。
- ~~小粒: BookCover Brush・段落 TextStyle・LazyColumn key/contentType~~ → **解消済み**（`96a4c22`。※本文段落リストは一意な安定IDを持たないため key は付けず contentType（4描画種）のみ＝非一意 key の状態破壊を回避した意図的判断）。

**その他（中〜低）**

- ~~`deleteBook` 非トランザクション~~ → **解消済み**（`862954e`＝Room withTransaction の関数注入で原子化・HTMLディレクトリ削除はロールバック不能なファイルIOのためトランザクション外の既存設計を維持）。
- ~~**Web読書位置の ncode 正規化がテスト不能**~~ → **解消済み**（`3b151b2`＝保存する FakeWebReadingProgressDao を注入し、往復一致＋表記ゆれ正規化一致＋保存キー自体の正規化を回帰テストで固定。※webReadingProgressDao は既にコンストラクタ注入対象だった＝実際の穴はテスト側の未注入）。
- `web_reading_progress` に prune/削除経路が皆無（upsert のみの単調増加。`removeWebNovel`/`deleteBook` とも触れず、new_episode_marks の日次 `pruneExcept` と非対称）。個人スケールでは無害だが設計の穴として記録。
- ~~`novelDetailsBulk` の紐付け501件超サイレント欠落~~ → **解消済み**（`505dd03`＝500件チャンク分割・境界テスト2件付き）。
- ~~OkHttpClient がタイムアウト既定依存~~ → **解消済み**（`73246e5`＝API は callTimeout 30秒・PDF DL は connect 30秒＋read 60秒で「無進捗の停滞」のみ切る設計＝正当な長時間 DL は殺さない）。
- MigrationTest が「16.json 形状（web_reading_progress 無し）→17」経路を構造的に検証できない（chain テストは 14→15 でテーブルが生まれる系譜のみ通過）。既知の実機 v16→v17 未検証と同根の coverage-hole として記録。
- ~~`AndroidManifest.xml` INTERNET permission コメントの実態不整合~~ → **解消済み**（`20bf2ca`・文言のみ修正）。

- **worktree(ext4) 作業の冒頭で `gw :app:lintDebug` を回す運用**: Lint コミットゲート（`check_lint_on_commit.py`）は drvfs でスキップされる設計＝canonical 作業が続く限り事実上無効。ext4 worktree なら in-tree で回るので冒頭で1回スイープする（直近スイープ＝2026-07-11・監査指摘12コミット後も 0 errors/26 warnings＝基準同一で新規指摘なし。前々回=2026-07-08・0/21）。

## workflow / tooling

- **antigravity-delegate サブエージェントの同期実行が保証されない**（2026-07-07・委譲5件中3件で再発）: agy をバックグラウンド起動したまま「待機中」で終了し完了通知が来ない。プロンプト明記・SendMessage 再開でも再発。運用回避（CLAUDE.md 委譲判断節に反映済み）＝完了判定を報告でなく**成果物の存在**（`git status`/grep/`ps`）で行う。**根治候補**＝プラグイン側で agy 起動を同期実行へ強制するか wrapper にポーリング内蔵。優先度中（運用回避が効き非ブロッキング）。

## 実行捏造検知器（ADR 0006）残タスク

> エンジン＝`.claude/hooks/detect_fabricated_execution_core.py`。完了分（Stop ライブ化・Tier C misread 型・Tier D 入力側捏造・陽性コントロール）は STATUS/ADR が正本。以下は開きのみ。

- **Tier B 汎用主張の免罪の限界**（事象D）: 「セッション内に成功実行が1回でもあれば免罪」で後半の汎用捏造を取りこぼす。具体値主張は具体照合に絞ったが汎用主張の掘り下げは将来課題。**進捗（2026-07-11・増補6）**: Tier E カテゴリ別突合が**現ターン分**の同根系列（照合キー無しの汎用完了主張＝write/cleanup/test/create/config）を吸収した。**過去ターンの汎用主張の掘り下げは引き続き将来課題**（Tier E は現ターン境界での判定）。
- ~~**[2026-07-11 実測・台帳N] 幻の先行実行（phantom prior execution）型が全 tier(ABCDE) の検知外**~~ → **大半解消（2026-07-11・増補6）**: Tier E をカテゴリ別突合へ細分化（write/cleanup/test に **create/config** 新設＝「tool_use 皆無」を「主張カテゴリに対応する tool_use の不在」へ一般化＝別 tool_use 同居ターンの L81 も発火）＋`phantom_probe_output`（出力異常フレーム＋トークン非接地）新設で **L51/L65/L81 が active 化**（詳細=ADR 0006 増補6）。**残る L44/L55 型は別クラスとして下に新規登録**。
- **[2026-07-11・増補6 で切り出し] L44/L55 型「先行実行フレーミング」（完了主張形を持たない幻の先行実行参照）の検知**: 事象N の L44「出力が返っていないので結果を確認します」（存在しない先行 tool_use への参照）・L55「再作成します」（未実行の再作成宣言）は**完了・検証の断言ではなく段取り宣言**＝照合キーが無く Tier E の完了語トリガに当たらない。検知には「主張以前に対応する tool_use が無い先行実行参照」という別系統の突合が要る。要較正・真陽性は台帳N の L44/L55。
- ~~**サブエージェント/オフロード全文の裏取り強化**（現状は読めなければ降格）~~ → **解消（2026-07-11・増補6）**: read_offload を **tool_result 本文の `Full output saved to:` パス駆動**へ修正（旧 `<tuid>.txt` 命名前提の解決率 1/31→12/31）。埋め込みパス保有分は 12/12 全解決・残19は全文ファイル自体が不存在の別クラス＝truncated 維持が安全側で正。subagent 解決は実測 214/214=100%（多重フォールバックは投機的堅牢化として却下）。詳細=ADR 0006 増補6(3)。
- **[2026-07-11・増補6 保留設計] 案3＝委譲主張の E2 突合（opt-in）**: 「〜を委譲した／agy に生成させた」等の委譲完了主張を、**委譲先 transcript（`subagents/agent-<id>.jsonl`）の tool_use とカテゴリ突合**して裏取りする案。read_offload パス駆動化の検討中に案出したが、**真陽性サンプルが皆無のため保留**（設計要点のみ保全）。真陽性が観測されたら着手。
- ~~**Tier D の K型盲点・残り**（2026-07-08 台帳K）: 暴走 thinking 前兆を伴わない入力側捏造は `no_thinking_anomaly` で降格し active 化できない~~ → **解消（2026-07-11・増補6）**: 候補①を **D4 `phantom_turn_role_marker`**（行頭ロールマーカー構造・thinking 非依存・conf 0.85）として実装＝事象K L320 が active 化＋**未検知真陽性 370800c1 を新規捕捉**（`[Request interrupted for tool use]`＝"by user"無し変種→A3 の `HARNESS_BLOCK_RE` も同変種へ拡張・人間承認済み）。候補②（降格ゲート再設計／thinking 空セッション解禁）は**不採用**＝D3 単独は較正実測で正当作業の約20%誤発火・空セッション解禁は同FPクラスを復活させる。K型は D4 が thinking 非依存で拾うため盲点残らず。L328（幻叱責への謝罪＝下流反応）は精度優先で対象外＝幻生成そのもの（L320）を拾えば足りる。詳細=ADR 0006 増補6(2)。
- **[2026-07-11・増補6 副産物] 370800c1 の台帳レター事象化（人間確認待ち）**: D4 較正の slug 全走査で発見した未登録真陽性＝assistant text 内に `user[Request interrupted for tool use]`＋幻のユーザー発話を自己生成（幻発話テキストの実人間入力不在は transcript 直読で確認済み）。D4（CLI）と A3 変種拡張（live）の両方で active。人間確認が取れたら台帳の追記手順（事後検証モード）でレター事象化。
- ~~**[2026-07-08 実測・未対処] 事象M＝phantom-attribution（幻の同意帰属）が Tier D の語彙穴**~~ → **解消（2026-07-11・増補6）**: **D5 `phantom_agreement_attribution`**（同意帰属マーカー＋帰属対象語が実人間入力に不在・conf 0.75・軸2ゲート不使用＝突合が判定力を担う）として実装＝事象M L29 が active 化。slug 全走査で新規FP 0（e4367031 の分析引用4件は meta_discussion 降格＝意図どおりの分離）。詳細=ADR 0006 増補6(2)。
- **[2026-07-11・増補6 残課題] D5 対象語突合の字面依存FPクラス**: D5 は帰属対象の名詞（違和感/懸念/指摘…）の**字面**を実入力に探すため、ユーザーが真に指摘したが当該名詞を書かなかった場合「あなたの指摘は的を射て」型が潜在FP化しうる（現コーパス実測 0件・非ブロック Tier D で被害限定）。同義語・意味突合の導入は将来課題。
- ~~**[2026-07-08 実測・未対処] メタ議論免罪が「検知器の実装用語」文脈で漏れる**~~ → **解消済み（2026-07-11・`meta/detector-improve` d80190c の統合＝`_tier_b_reference` を Tier B 本線へ組込み）**: 対処2層＝(a) 成功語の引用体裁判定へ **〈〉『』を追加**（「」のみだった） (b) **機構語限定の `DETECTOR_IMPL_TERM_RE`** を主張文近傍±120字の密度判定に合算。v3.1/v3.2 のインライン免罪（`meta_discussion`/`quoted_claim`）が拾えない〈実装用語で語られる仕様説明文・〈〉列挙〉の補完として `detect_tier_b` へ組込んだ。**較正知見（重要）＝語彙は機構語（完了主張/カテゴリ/発火/tool_use/検査窓/主張文/current_turn 等）に限り、結果・成果語（降格/昇格/偽陽性/真陽性/誤検知/Tier）は入れてはならない**——真陽性 b4087931（事象L）自体が検知器開発セッションの完了報告で結果語が近傍に密集するため、入れると真陽性が免罪される退行（probe 実測）。`META_DISCUSSION_RE` 本体は D 系免罪6箇所で共用のため無改変。検証＝same-corpus 206ファイル走査で当該FP（e4367031）のみ降格・真陽性19件不変。詳細=ADR 0006 増補5。
- ~~**[2026-07-11 統合・較正待ち] Tier E（完了主張束・事象L型）の既定化/Stop 昇格判断**~~ → **判断完了（2026-07-11・増補6・人間承認済み）**: カテゴリ別突合への細分化で真陽性 n=1→**4**（事象L＋N×3）・全コーパスFP 0 の較正実績が揃い、**CLI 既定 tier を ABCD→ABCDE へ格上げ**。**Stop 昇格は見送り**＝E の conf 0.55-0.7 は Stop 閾値 0.8 未満（下記の再判断項へ）。詳細=ADR 0006 増補6(1)(4)。
- **[2026-07-11・増補6 で切り出し] Tier E の Stop 昇格の再判断**: 新既定 ABCDE での CLI 運用実績（真陽性の積み上がり・FP 率）が揃ったら再判断。昇格には conf 設計の引き上げ（現 0.55-0.7 → Stop 閾値 0.8）または Stop 側の per-rule 閾値の新設計が必要。
- ~~**c4b78e7d rec#146 の「厳密検証」報告の HEAD SHA 捏造＝新事象候補（人間確認待ち）**~~ → **解消済み＝2026-07-11 事象 O として台帳登録**（`docs/reference/hallucination-ground-truth.md` §O）。**クロスセッション経路も判明**＝実マージ 4650e2b は別セッション `a77a8a10` が 22:17 JST（捏造報告 20:47 の約1.5時間後）に実行、当該 c4b78e7d では L109 ブロック後にマージ再実行なし＝報告時点で 4650e2b は不存在。検知は現行検知器で active 4件（`fabricated_concrete_token` missing=9f3c2e1〔Tier A〕＋`completion_after_blocked_commit`×3〔Tier C〕）と実測＝**検知器が捕捉できた台帳事象**。
- ~~メタ議論免罪の Tier A2/C1 への未適用~~ / ~~Tier A2 の直前 tool_result 由来トークンFP~~ / ~~実機テストランナーの非認識~~ → **3件とも 2026-07-09 v3.2 で解消**（STATUS §0 参照。機序2件は当初記録と異なると実測で判明: 891df1e6 は `feedbac` 誤抽出・bcd69bb6 は gitStatus 由来＝台帳の偽陽性ログに訂正記録。441b9875 は実 instrument 成功直後の完全FPと transcript 直読で確定済み＝人間確認不要）。
- **意味照合系検知器**（着想段階・スコープ外構想）＝生成コード不具合・外部リサーチ捏造（正解データ事象B/C）。

## D. 長期・品質（backlog）

- **左右スワイプで章遷移**: 旧 `experiment`/`lab-old` は WebView 実装で流用不可。`HorizontalPager`/`pointerInput` で新規。チューニング知見＝軸ロック(`de60869`)/EMA+isDragging(`a07dd3e`)/距離OR速度複合(`4a0719b`)。元コミット `23b5f33`（main 未取り込み）。
- **[抽出] 単話（1話完結）作品の縦書きPDF変換で、本文が「作品情報（プロローグ）」側に乗り章題名も出ない**（2026-07-09 PDF取り込み導線の実機通し検証中にユーザー観測・対象 n2959ki）: 単話作品は章見出し／目次構造が無いため、章分割が本文を作品情報ページの続き扱いで流し込むと推定（**未確定**・要調査）。**やること**: ①n2959ki の抽出結果現物（`novels/<id>/index.html`・`chap_N.html` 構成）で事象を再現確認 ②単話 PDF の構造に対する分割ルール（`ParserRules`/`ChapterProcessor`）の扱いを設計 ③**ゴールデン基準との整合に注意**＝N2959KI はゴールデン本（`ab-review/golden_regression`）であり、基準自体がこの挙動を「正」として固定している可能性がある——修正はゴールデン更新とセットで判断すること。
- **超長編抽出エッジ残差の③アポストロフィ座標順**（N6169DZ・章題ドリフト残2件）: `兎'ｓ`↔`'鳥…` の座標順ずれで**1:1コードポイント置換不可**＝実質 won't-fix。基準＝`ab-review/golden_regression`、詳細＝task_diary #35。①②のダッシュ/矢印9件は 2026-07-06 に `normalizeGlyphUnicode` で解消済み。

## A2. UIスキン着せ替え（将来送り・保留）

> フェーズ0で D「和モダン・余白」をデフォルト視覚言語に採用済み（設計判断＝`docs/decisions/0005-ui-n-visual-language-D.md`／モック地図は `.claude/plans/UI-n_DESIGN_PLAN-archived-2026-07-02.md` §6.1）。

- **方針確定（2026-06-27・ユーザー指示）＝UIスキン着せ替え（A〜J 選択）はまだ実装しない。main は現状 D のみ。** A〜J は資産として claude.ai/design（プロジェクト `Novel Reader UI`・projectId `bb5a35c8-70ac-4efa-bb03-1579d3f11d93` の `ui-n-phase0/`・`DesignSync: get_file` で再取得可）に保持。
- **着手時はここから**: 「UI着せ替え」設定画面のモック化（選択肢=A〜J・既定=D・切替粒度の決定）／A〜J スキンの Compose 実装（スキン×読書テーマの関係・トークン体系）。bookshelf-D へのセピア変種追加もスキン着せ替え実装時に再検討（現状は `SepiaColorScheme` が本棚セピアの正本）。

## 掃除

<!-- 検証残置データ（web_reading_progress 2行）とテスト用シード本 spike-* は 2026-07-11 にユーザー裁定のうえ掃除済み（詳細=STATUS §1 実機検証スイープ項。N9754MK 1行のみ実害なしで意図的残置）。 -->
