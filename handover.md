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

- （現在なし）

---

## 実機検証待ち（次回の実機接続でまとめて消化）

> 静的には完了済みで、実機での挙動確認だけが残るものを集約。実施は `/device-verify` 作法
> （`adb-bridge` 一発・**connectedAndroidTest 直叩きは蔵書DB消失の禁忌**＝task_diary #36）。

- **統合ツリー残③**: resilience（applicationScope）× FGS スコープ再生成 fix（`810f2dd`）の共存動作＝未検証（見送り中）。実PDF処理ジョブ＋強制 kill が要り invasive／`feat/processing-resilience` で既に実機3/3 緑・今回マージ分は Room 層のみゆえ優先度低。**変換の停止/再開の再目視**も同便（実PDF要・既存 spike-* 本 or 短編で本棚を増やさぬよう）。
- **`handover/task-sweep` 分の目視**: v10→v11 migration の実機通過（起動即クラッシュ監視＝task_diary #39 の機序）＋ `MigrationTest` 実機再実行（`am instrument`）／F-J「さらに読み込む」フッタ・上限表示／M12 没入ヒントが再インストール後の初回だけ／F-F 読書画面シート開閉の process death 復元／系統1分割後の本棚・発見系の見た目不変／空棚 CTA 遮蔽 fix（`c006f51`＝空棚で透明 Lazy コンテナが「PDFを追加する」タップを奪う実バグの解消）の確認。
- **UX監査 E-要検証10項目**（静的修正は完了・実機のみ）: F-A 経路依存 Back（2経路比較）・F-B 読書 Back overshoot・F-C/F-E process death 耐性（開発者オプション「アクティビティを保持しない」ON）・F-G 二重取込・M1 二重 push・F-P/M タッチ標的実寸（Layout Inspector）・コントラスト比 WCAG 4.5:1（`theme/Color.kt` 各テーマ）・フォントスケール200%崩れ・6h TTL キャッシュ Stale（`NovelApiRepository.kt`）・cold start 空フラッシュ計測。
- **進捗の統合%表示の最終目視**（`8277c91` 以降＝「本文を処理しています… X%」の単一フェーズ化が実機で「2周目」錯覚を起こさないか）。

## 意匠オーナー目視確認（モック正本との緊張・2点）

- 本棚カードの⋮削除アフォーダンスを既定表示化した（UX監査 M5＝削除手段の可視化）。モック正本 ADR 0005 はフラット構図＝⋮無しで、その緊張の中で最小対応を選択＝オーナー目視確認を要する。
- 継続カードが48dp化（F-P タッチ標的）で約30-40dp 高くなった。モック忠実性とのトレードオフ＝オーナー目視確認を要する。

## UI/UX 宿題

- **条件を調整シートの高速フリック「枠オーバーシュート」**（2026-07-08・**未修正・不具合残置**）: 高速フリックで手を離すとシート枠が Expanded 上限を超えオーバーシュート→復帰し上端に裏画面が覗く。真因（ModalBottomSheet 内蔵接続が境界フリング残速度を settle へ渡す）・棄却済み候補A〜D・本命の解・BOM 1.3.1 での再現から始める旨は **task_diary #51** が正本。要 UI-n 意匠確認。
- **discovery-search-D モックを現状機能とすり合わせ →「キーワードから選ぶ」を採用案Bの意匠へ**（2026-07-07 発生）: 「キーワードから選ぶ」（`NarouCuratedKeywords` 計22カテゴリ）の折りたたみを Compose 実装済み（`9ba5049`）だが**モックに存在せず mock 無しで足された**＝意匠が定まらない実機フィードバックの根本原因。条件調整シートもモックと現状がズレ。**やること**: ①一次正本 `docs/design-candidates/discovery/discovery-search-D.html`（リポジトリ側が正本・claude.ai `ui-n-phase0/` は収蔵コピー）を現状機能へすり合わせ→ DesignSync で収蔵側へ同期 ②キーワード見出しを採用済みの**案B**（ヘアライン「開ける行」＋濃色見出し＋畳んでも代表語を淡色プレビュー）へ意匠更新 → Compose へ翻訳（現状 Compose は機能のみで案Bの2段プレビュー未反映）。**なぜ分離**: モックが現状を反映しておらず意匠正本を先に整えないと翻訳の拠り所が無いため。※god file 分割（`SearchConditionSheet.kt` 抽出）は `handover/task-sweep` で完了済み＝この便で触るのはモック追従＋案B翻訳のみ。
- **本文処理の進捗にページ数もリアルタイム併記**（2026-07-06 実機でユーザー要望・優先度低）: 現状は通し%表示（%表記はユーザー評価「完璧」）。ページ n/m も併記できれば体験向上。**設計注意**: load(全ページ)と process(本文=全−4)は分母が違い、単純2カウンタ併記は「2周目に入った」錯覚が再発する→単一の連続ページ尺にするか %主・ページ副に。実装点は `PdfBookExtractor` の step-1 phase 文言（＋必要なら `ProcessingBanner`）。

## なろうAPI 発見・検索機能（第2の柱・Phase 4/5 残り）

> なろう公式APIの発見機能を「第2の柱」に育てる計画（案A＝本文非取得・メタのみ）。Phase 0〜3＋Phase 4 スライス1 は完了・main 統合済み（現況は `STATUS.md` §1）。
> 目標ロードマップ・作る機能一覧の一次情報は plan `~/.claude/plans/api-agy-woolly-swan.md`。監査残課題（構造系）は下の「リファクタ / 技術的負債」へ移設済み。

- **Phase 4 残り（融合本棚②＋育成 U1/U2）** ★次アクション:
  - **(b) Web由来・未取込カード**（新データ概念＝Web作品を本棚に置く・**DB変更を伴う**）。⚠️ DB 変更時は次版=**v12**（v11 は task-sweep の `contentSha256` が消費済み＝`STATUS.md` §0）だが、**着手前に必ず全 worktree の宣言 version を先取り確認**（`grep -h "version = " ~/wt/*/android/app/src/main/java/com/novelreader/data/AppDatabase.kt`＝task_diary #39・`/db-migration` スキル）。実装時は**加工なし送客（Chrome Custom Tabs）**を適用（独自UIを被せる WebView 内包はなろう規約NG＝ADR 0010・task_diary #45）。
  - **U1 新着話チェック＋通知**（Worker 化が濃厚）。⚠️ 本質的にバックグラウンド実行＝下の技術的負債「NovelApiRepository キャッシュの Main dispatcher 前提」と正面衝突するため、「事前の別作業」ではなく **U1 設計の一部として最初に**潰すこと（Mutex 化 or ConcurrentHashMap＋TTL で小さく済む）。
  - **U2 整理**。
- **Phase 5 doc昇格**: 本タスク（STATUS-api-lab 解体・2026-07-08）で大半消化（ADR 0010 化・architecture スキルへ発見/検索層の所在追記・なろうAPI実装 why を `docs/patterns/narou-api-discovery.md` へ集約）。残＝`docs/reference/03-api-feature-analysis.md`↔`04-competitor-app-features.md` の相互リンク程度（優先度低）。

## リファクタ / 技術的負債（deferred）

- **[発見系・アーキ] 続きありバッジの produceState が Repository 直撃**（監査残課題1の残り）: `BookCard` の続きありバッジがカード毎に `novelDetail` を発火（テスト不能）→ `BookshelfViewModel` で一括照会し Map 配布へ。※`NcodeLinkSheet` の検索ステートマシンの VM 吊り上げ（回転で全損の解消）は task-sweep `2cd372e` で完了済み。
- **[発見系・構造] `SearchConditionSheet` の残リファクタ**（監査残課題2の残り）: カスタム範囲入力の約90行×2コピペの部品化・段階チップ値とラベルの平行定義統合（`LENGTH_STEPS`/`TIME_STEPS` とチップ文言が別ファイル）。※主要部＝`SearchConditionSheet.kt` 抽出（1348→606行）＋カスタム2重フラグの `SearchDraft` 一本化は task-sweep `7d135ba` で解消済み。**実施タイミング＝上「discovery-search-D すり合わせ→案B翻訳」で同ファイルを触るとき**。
- **[発見系・型] 結果画面の条件チップが表示文字列マッチ＋位置規約で種別判定**（`DiscoveryResultScreen`・監査残課題3）: `conditionChipLabels` を `List<ConditionChip(label, kind)>` へ格上げ。
- **[発見系・機能ずれ] notword が UI 未配線のデッドパス**（監査残課題4・モデル/API/チップ文言だけ存在＝除外語入力が未実装）: `@Query("notword")` は在るので UI 配線のみ。※ページング（旧「30件打ち切り」）は F-J `c66c913` で解消済み。
- **[発見系・将来の罠] `NovelApiRepository` のインメモリキャッシュが「全呼び出しが Main dispatcher」の暗黙不変条件で成立**（監査残課題5・素の mutableMap）: U1 新着チェック（Worker化）で踏む→ ConcurrentHashMap 化 or Main 限定の why 明記が先（上「Phase 4 U1」に紐付け）。あわせて cacheKey は trim・送信は非 trim の非対称（`NcodeLinkSheet` 経由の word が素通し）。
- **[発見系・小粒]**（監査残課題6の残り）: `NcodeLinkSheet`「全0話」・`DiscoveryCommon`「連載中 1話」の欠損値捏造表示／`NovelDetailScreen` の日付手書きパース（`catch(Exception)` why なし・切り出してテスト可能に）／UA "NovelReader-Android/1.0" の BuildConfig 非連動（buildConfig 機能自体が無効のため見送り）／MockWebServer で実 URL エンコードを固定するテスト1本（全テストが mockk 差し替えで Retrofit 実エンコード未検証）。※`SearchDraft` の SavedStateHandle 対応（プロセス死でドラフト全損）は ui/polish の kotlin-parcelize 導入で解消済み。

- **`NativeReadingScreen`（892行）の route/Content 分割**: 系統1リファクタで唯一の残り。没入クローム・Custom Tabs 再入ガード・navHistory 等の副作用が濃く、純移動でも実機目視なしに畳むリスクが高いため見送り＝**実機検証を伴う機会に実施**。
- **`saveScrollPosition(bookId, filename)` 等の String 連続の型付け**: 系統4 Ncode 型付け（`@JvmInline value class`）の続きの適用候補として保全。
- **`BookRepository` インターフェース化**（テスト可能化）: 具象直参照＋static シングルトン（PDFBoxResourceLoader の Application 初期化/Room）で JVM 単体テスト不可。interface 抽出＋`FakeBookRepository`。影響 `BookRepository.kt`/`NovelReaderApplication.kt`。
- **worktree(ext4) 作業の冒頭で `gw :app:lintDebug` を回す運用**: Lint コミットゲート（`check_lint_on_commit.py`）は drvfs でスキップされる設計＝canonical 作業が続く限り事実上無効。ext4 worktree なら in-tree で回るので冒頭で1回スイープする（直近スイープ＝2026-07-08・0 errors/21 warnings＝新規起因の実質指摘は解消済み・残はノイズ）。

## workflow / tooling

- **antigravity-delegate サブエージェントの同期実行が保証されない**（2026-07-07・委譲5件中3件で再発）: agy をバックグラウンド起動したまま「待機中」で終了し完了通知が来ない。プロンプト明記・SendMessage 再開でも再発。運用回避（CLAUDE.md 委譲判断節に反映済み）＝完了判定を報告でなく**成果物の存在**（`git status`/grep/`ps`）で行う。**根治候補**＝プラグイン側で agy 起動を同期実行へ強制するか wrapper にポーリング内蔵。優先度中（運用回避が効き非ブロッキング）。

## 実行捏造検知器（ADR 0006）残タスク

> エンジン＝`.claude/hooks/detect_fabricated_execution_core.py`。完了分（Stop ライブ化・Tier C misread 型・Tier D 入力側捏造・陽性コントロール）は STATUS/ADR が正本。以下は開きのみ。

- **Tier B 汎用主張の免罪の限界**（事象D）: 「セッション内に成功実行が1回でもあれば免罪」で後半の汎用捏造を取りこぼす。具体値主張は具体照合に絞ったが汎用主張の掘り下げは将来課題。
- **サブエージェント/オフロード全文の裏取り強化**（現状は読めなければ降格）。
- **Tier D の K型盲点**（2026-07-08 台帳K）: 暴走 thinking 前兆を伴わない入力側捏造（応答継続中に幻のユーザーターンをロールマーカー込みで生成）は `no_thinking_anomaly` で降格し active 化できない。対策候補: ①assistant text 内の `[Request interrupted by user]`／ロールマーカー出現を拾う高精度新ルール ②降格ゲート再設計（thinking 空記録セッションでは軸2が原理的に発火不能な点も考慮）。
- **Tier B のメタ議論免罪欠落**（2026-07-08 実測）: 捏造を検証・報告する発話が捏造文言（「回帰テスト：全通過」等）を引用すると `unverified_test_claim` が偽陽性発火。Tier D の `meta_discussion` 降格に相当する免罪が Tier B に無い。引用・表組み文脈の認識が課題。
- **Stop ゲート検査窓の穴**（2026-07-08 台帳L）: 捏造報告の直後に AskUserQuestion 等の tool_use が続くとターンが継続し、Stop 発火時点の検査窓（scope=last_turn）に捏造発話が入らず素通り（同じ発話を事後 CLI は Tier B で検知＝ルールでなく窓の問題）。対策候補: last_turn をターン内全 text ブロックへ拡張、または PostToolUse 側の逐次検査。
- **意味照合系検知器**（着想段階・スコープ外構想）＝生成コード不具合・外部リサーチ捏造（正解データ事象B/C）。

## D. 長期・品質（backlog）

- **左右スワイプで章遷移**: 旧 `experiment`/`lab-old` は WebView 実装で流用不可。`HorizontalPager`/`pointerInput` で新規。チューニング知見＝軸ロック(`de60869`)/EMA+isDragging(`a07dd3e`)/距離OR速度複合(`4a0719b`)。元コミット `23b5f33`（main 未取り込み）。
- **Phase3 外部連携の残**: ①内部ブラウザから PDF 直接取込＆動線追加（②「小説家になろう」公式API連携・ランキング表示は api-lab 系で実装済み。詳細 `docs/reference/narou_api_manual.md`／`docs/reference/03-api-feature-analysis.md`）。
- **超長編抽出エッジ残差の③アポストロフィ座標順**（N6169DZ・章題ドリフト残2件）: `兎'ｓ`↔`'鳥…` の座標順ずれで**1:1コードポイント置換不可**＝実質 won't-fix。基準＝`ab-review/golden_regression`、詳細＝task_diary #35。①②のダッシュ/矢印9件は 2026-07-06 に `normalizeGlyphUnicode` で解消済み。

## A2. UIスキン着せ替え（将来送り・保留）

> フェーズ0で D「和モダン・余白」をデフォルト視覚言語に採用済み（設計判断＝`docs/decisions/0005-ui-n-visual-language-D.md`／モック地図は `.claude/plans/UI-n_DESIGN_PLAN-archived-2026-07-02.md` §6.1）。

- **方針確定（2026-06-27・ユーザー指示）＝UIスキン着せ替え（A〜J 選択）はまだ実装しない。main は現状 D のみ。** A〜J は資産として claude.ai/design（プロジェクト `Novel Reader UI`・projectId `bb5a35c8-70ac-4efa-bb03-1579d3f11d93` の `ui-n-phase0/`・`DesignSync: get_file` で再取得可）に保持。
- **着手時はここから**: 「UI着せ替え」設定画面のモック化（選択肢=A〜J・既定=D・切替粒度の決定）／A〜J スキンの Compose 実装（スキン×読書テーマの関係・トークン体系）。bookshelf-D へのセピア変種追加もスキン着せ替え実装時に再検討（現状は `SepiaColorScheme` が本棚セピアの正本）。

## 掃除

- **実機の本棚にテスト用シード本が残存**（2026-07-03 `PdfPipelineDeviceTest` が投入）: `spike-N1453LW`/`spike-N2959KI`（+空 `spike-N6169DZ` dir）。掃除の可否をユーザーに確認中で未実施。掃除するなら `filesDir/novels/spike-*` と books テーブルの該当行のみ削除（手動追加のルビ本・他蔵書には触れない）。Phase 3 の実書取込で上書きされる想定でもある。
