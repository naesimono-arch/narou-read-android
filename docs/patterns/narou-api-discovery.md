# なろうAPI 発見・検索の実装パターン（なぜこう作ったか）  ★★

> 旧台帳 `STATUS-api-lab`（削除済み）§2「実装知見」から集約（2026-07-08 の解体で移設）。
> ここは **コードが正本**（`com.novelreader.narou` 一式・`ui/discovery/`）。実装の詳細はコードを読めば分かるので、各項は「**なぜこのパターンか**」に絞る。
> なろうAPI の外部事実（サーバ挙動・パラメータの落とし穴）は `task_diary.md`「なろう小説API（検索パラメータ）」節が正本（本ファイルはアプリ側の設計選択に限る）。

## ネットワーク層・テスト容易性

- **API層は `com.novelreader.narou` に隔離**（蔵書系 Room とは別系統・Room に一切触れない）。発見機能は「第2の柱」だが蔵書の永続化と関心が独立しており、隔離パッケージにすることで蔵書 DB への副作用ゼロを構造的に保証する。
- **VM で `withContext(Dispatchers.IO)` は不要**: Retrofit の `suspend` 関数は main-safe（内部で IO ディスパッチャへ退避する）。ここで実 IO へ手動で切り替えると `TestDispatcher` の制御が及ばず、テストが `Loading` のまま進まず失敗する（この方針で解決）。
- **`assertThrows` の中に `runTest` を入れ子にしない**（`IllegalStateException` になる）。`runTest` スコープ内で直接 try/catch する。
- **Moshi は codegen（KSP）**: 既存の Room/KSP に相乗りでき `kotlin-reflect` を APK に持ち込まずに済む（APK 肥大回避）。`@JsonClass(generateAdapter = true)`。
- **キャッシュは 6h TTL のインメモリ**（`allcount` を分離・上限50）。`timeSource` を注入してテストで時間を制御する。⚠️ このキャッシュは「全呼び出しが Main dispatcher」の暗黙不変条件に依存（素の `mutableMap`）＝Worker 化（U1 新着チェック）で壊れる（handover の技術的負債に注記）。

## 検索・発見の設計選択

- **検索履歴は DataStore Preferences**（`narou_search_history`・蔵書 Room と別系統）。並び・上限の操作ロジックは `SearchHistory` 拡張の純関数へ分離し純 JVM テストで担保。VM 側は lazy＋`WhileSubscribed` で、検索画面を開くまでディスクに触れない。
- **モックのレンジスライダーは段階チップへ翻訳**する（文字数・読了時間など）: ダイナミックレンジが広すぎて線形スライダーは実用に耐えない（1万字〜数百万字を1本のスライダーで扱えない）。上位刻み（10万〜50万／50万〜100万／100万〜等）の段階チップ＋カスタム数値入力へ翻訳した。操作系の差分は ADR 0005 のスコープ外規定に沿う（見た目＝HTML正本、操作系は実機フィードバックで後詰め）。
- **ランキング一覧の `of` から `story`（あらすじ）を外す**（`OF_LIST`）: 一覧はあらすじ非表示の意匠のため転送しない（転送量マナー）。詳細は `novelDetail(ncode)` が `of` 無指定で全項目取得する。

## PDF↔Web 継続読書（Phase 3）

- **ncode 紐付けは人間確定必須**: title 一致だけの自動紐付けは同名別作品の誤誘導リスクがあるため採らない。候補提示→タップ確定（or 手動 ncode 入力・`isValidNcode` 検証付き）のみ。解除導線（継続カード末尾の極小テキスト）が唯一の救済パス。
- **PDF 正規化済みタイトル（波ダッシュ U+301C）でも、なろう検索はヒットする**: シャングリラ・フロンティア（タイトルに 〜 を含む）の書名自動検索が候補1件を正しく返した（API 側が波ダッシュ差を吸収する模様。**実測1件・保証ではない**→ヒットしない場合の逃げ道が手動 ncode 入力）。
- **継続情報の取得は最終章表示時のみ**（`novelDetail` は 6h TTL キャッシュ相乗り）。オフライン失敗時は静かに非表示＝読書の没入を通信エラーで壊さない（次回の最終章表示で自然に再試行）。

コード: `com.novelreader.narou`（`NovelApiRepository`/`NarouNetwork`/`model`）・`ui/discovery/`・`viewmodel/DiscoveryViewModel.kt`・`NovelDetailViewModel.kt`・継続判定純関数 `ContinuationLogic`。
