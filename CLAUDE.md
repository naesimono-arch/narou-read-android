# CLAUDE.md

## 概要

日本語Web小説（なろう系）のPDFを、ふりがな対応のHTMLに変換する **Androidアプリ**。
Jetpack Compose + Kotlin ネイティブ PDF 抽出（PDFBox-Android）。
ビルド設定: PDFBox-Android 2.0.27.0 / compileSdk 34 / minSdk 26 / targetSdk 34
（旧 Chaquopy(Python 3.12)+pdfminer は 2026-07-05 Phase 5 で完全撤去＝APK 67→24MiB）

## 開発ルール

- **コードダンプ禁止**: チャットへのコード出力は10行以内。
- **Atomic Commit**: 1論理的変更＝1コミット。形式は `fix/feat/refactor: 要約（日本語可）`。`git commit` 前に変更内容を提示して人間の承認を得ること。`Co-Authored-By` トレーラーは付けないこと。
- **UIコメントは日本語**
- **自己検証必須**: Kotlin の `src/main` または `src/test` を変更した場合は必ず `cd android && ./gradlew testDebugUnitTest` を実行してからコミット計画を提示すること（`androidTest` は端末必須のため対象外）。PDF抽出ロジック（`java/com/novelreader/pdf/` の `PdfExtractor`/`TextProcessor`/`ChapterProcessor`/`HtmlExporter` 等）もこの Kotlin テストで担保される（旧 Python 版の単体テスト test_logic.py は Phase 5 で撤去済み）。
- **「なぜ」コメントの義務付け**: 自明でないロジック・バグ修正・防御的コードには必ず「なぜそうしているか」をコメントで残すこと。whatはコードを読めば分かる。根本原因が未確定の場合は「〇〇が原因と推定されるが未確定のため防御的に対処」と明記。**【絶対禁止】** what コメントのみ・why なしのバグ修正・防御的コード追加。
- **task_diary自動更新**: `fix:` コミット後は同じターン内で `task_diary.md` への追記が必要か確認すること。コードコメントだけでは伝わらない根本原因・OEM固有動作・将来はまりやすいパターンがある場合のみ追記。既存エントリと重複なら不要。追記時は内容で置き場を判断すること（**外部プラットフォームの事実・落とし穴**→ `task_diary.md` / **本アプリ実装パターン**＝コードが正本なので「なぜ」に絞り `docs/patterns/` / **設計判断・Why-not**→ `docs/decisions/` のADR）。task_diary のエントリ番号（#N・`§N`参照）は固定IDのためリナンバーしない（移設済みの旧ID対応は `task_diary.md` 末尾の移設マッピング表が正本）。**連番ID（task_diary #N・ADR 番号）の新規採番前に必ず全レーンの既存番号を確認**: `grep -h "^#### " task_diary.md ~/wt/*/task_diary.md`／ADR は `ls docs/decisions/ ~/wt/*/docs/decisions/`（Room version の worktree 先取り確認と同じ衝突クラス＝二重採番の再発防止。採番衝突はフック `check_sequence_id_collision.py` も警告するが並列レーンの grep が第一防衛）。
- **一時ファイルは「抽出→集約→削除」まで1セット（作りっぱなし禁止）**: スクラッチ・TODO・事象レポート・分析ダンプ等の一時ファイルは、役目を終えたら〈①残すべき事項を既存の管理ファイルへ分割・集約 → ②然る後に削除〉を同じ作業単位で完了すること。作りっぱなしにしない。集約先は管理ドキュメント体系に従う（現況→`STATUS.md`／やること→`handover.md`／腐りにくい知見→`task_diary.md`／一次情報の細部→`.claude/plans/`／ブランチ不変の作業知見→auto-memory）。**git 管理下では削除は不可逆ではない**（履歴から復元できる）。恐れるべきは「未抽出の知識ごと消すこと」であって削除そのものではない＝①を済ませれば②は安全になる。**削除・移設で他ファイルからの参照がリンク切れになるなら、張り替えるか参照ごと消すこと**（存在しないファイルを指す台帳は読者を誤誘導する＝放置そのものが害）。
- **スキル陳腐化チェック**: 構成・描画方式・モジュール間の制御フローを変えるリファクタ（例: WebView→Composeネイティブ移行、多重起動ガード方式の変更）をコミットした後は、同じターン内で `.claude/skills/`（特に `architecture`・`db-migration`）の記述が陳腐化していないか確認すること。スキルは久々に触る際の最初の参照先のため、実態とズレると誤誘導の温床になる。
- **UIの見た目は /design のHTMLモックが正本**: 配色・タイポ・余白/構図・レイアウト等の「見た目」は、claude.ai の `/design`（HTMLデザインシステム）で作った**HTMLモックを正本**とし、Compose はその**翻訳**として実装している。**なぜ**: 見た目の思想設計を白紙から立て直す UI-n の分業ワークフロー（見た目＝HTML正本／Claude Code は翻訳者）を採ったため。したがってUIの見た目を触るときは **Compose 側で意匠を自己判断せず、まず設計判断 `docs/decisions/0005-ui-n-visual-language-D.md` とモック現物を確認**すること。**モック正本は2系統**＝UI-n 系（本棚・読書・目次・設定）は claude.ai/design プロジェクト `Novel Reader UI`（projectId `bb5a35c8-70ac-4efa-bb03-1579d3f11d93`）の `ui-n-phase0/*-D.html`（リポジトリ内に現物なし・`DesignSync: get_file` で取得）／**発見・検索系6画面はリポジトリ内 `docs/design-candidates/discovery/*.html` が一次正本**（claude.ai `ui-n-phase0/` の同名は収蔵コピー＝正本ではない）。色は `theme/Color.kt`／明朝は `theme/Typography.kt` の `MinchoFamily` 経由（直書き禁止）というトークン構造も維持する。**スコープ外**＝操作感・組版の質感・アニメ・没入クロームの挙動は HTML→Compose で最も劣化する層のため実機フィードバックで後詰め（ADR 0005 §B）。
- **ツール使い分け（Windows環境）**: `Get-ChildItem` / `ForEach-Object` / `Select-Object` / `$_` / `$env:` 等のPowerShell構文は必ず **PowerShell ツール**で実行すること。**Bash ツールに PowerShell 構文を渡すことは禁止**（`/usr/bin/bash` は PS 構文を解釈できずエラーになる）。`git` / `python` / `grep` 等の POSIX コマンドはどちらでも可。
- **コード探索は semble を第一手段に（総当たりReadの禁止）**: 「〇〇する処理はどこか」「このシンボル／識別子の定義・関連箇所はどこか」を探すときは、ファイルを片端から Read・grep する前に必ず `semble`（MCPツール、またはCLI `semble search "<説明>" .`）を使うこと。意味検索で該当スニペットだけを返すため、総当たり Read よりトークン効率が桁違いに良い（公式実測でコード検索コスト約93%減）。**例外（semble を使わず従来手段でよい場面）**: ①ファイルパスが既知なら直接 Read する、②アーキ全体把握は `/architecture` スキルが先、③DBスキーマ変更は `/db-migration` スキルが先。semble の役割は「場所の特定」であり、正確な編集前の文脈確認や構造理解は従来どおり Read で行うこと。
- **ブランチ境界の遵守 / auto-memoryの役割分担**: Claude Code の auto-memory（`MEMORY.md`・`~/.claude` 配下）と session history は**ディレクトリパス紐付けで全ブランチ共有**＝ブランチを考慮しない。そのため:
  - **auto-memory にはブランチ不変情報のみを書く**（ユーザー嗜好・環境・ワークフロー・汎用知見）。スキーマ版・不具合の解決状況・CP進捗などブランチ固有の状態は書かず、**git 管理の `STATUS.md` / `handover.md`（ブランチ追従する）を正本**とする。
  - **`STATUS.md` は存在するブランチで現況の正本**。**`main` が現況台帳 `STATUS.md`＋やること台帳 `handover.md` の正本**（2026-07-02 に lab を統合し main へ一本化）。一時的な作業ブランチを切る場合はそのブランチの `STATUS.md` ないし `.claude/plans/` の active plan が現況で、マージで main へ集約する。現在ブランチは statusline 表示と SessionStart 注入を参照すること。
  - **整理済みの doc アーキ（`STATUS.md` ＋規約準拠 `handover.md` ＋ `docs/decisions`・`docs/patterns`）は main が正本**。handover/docs の整理分割（完了済み項目→`STATUS.md` 移動・冒頭の思いつき欄・`docs/decisions/` への ADR 化）は **main で行う**（かつて lab を正本とし main 先行整理との乖離が宿題だったが、2026-07-02 の統合で main へ一本化して解消）。
  - **コード変更・コミット前に必ず `git branch --show-current` を確認**し、active な plan ファイル冒頭に対象ブランチを記録する（コンテキスト圧縮で現在ブランチが脱落しても参照可能なデータとして残すため）。`main` への直接コミットは **Bash ツール経由の一般的な `git commit`（改行区切り・`git -C/-c …` 等のグローバルオプション付き）に加え、コミットを生成する merge/rebase/cherry-pick も `guard_commit_branch.py` が検知してブロック**する（2026-07-06 拡張）＝**ソフトな防御網であり完全な防止ではない**（PowerShell ツール経由・難読化形・`git pull` は対象外。既存コミット系フックが `matcher:"Bash"` のため PowerShell 実ゲート化は波及の大きい別タスク）。最終防壁は作業ブランチ運用と「未 push の main コミットは可逆」である事実。
  - ブランチ固有の内容を `@import` で `~/.claude` や親パスから引かないこと（worktree 越境でパスが誤解決する既知問題を避けるため、参照はリポジトリ内の相対パスに留める）。
- **委譲判断 / plan運用（agy＝実行者・Claude＝監督。agy側は無料扱い・Claude側の節約を最大化）**: 原則は「**生成＝agy（仕様固定後）・判断＝Claude**」。委譲はコスト削減であると同時に**監督予算を検証・真因掘りへ回す品質戦略**（2026-07-07 A/B実測で品質優位を確認＝`../claude-bestpractice/models/knowledge/06-field-ab-opus-vs-fable.md`）。
  - **委譲する**: 仕様・シグネチャ・正本パターンを固めた後の bulk 生成（目安＝**生成 ~300行以上**、または自分の context に載せたくない**非編集**の読み物。小口は監視サブエージェントの固定費で損＝直接やる）。UI/意匠の Compose 翻訳も3点セット〈モック現物・監督自作の Compose 正本1画面・厳密シグネチャ+色対応表〉で可（正本1画面を監督が先に書く工程は省けない）。**意匠・規約に触れるバッチは委譲仕様書にモック正本（ADR 0005）・関連 ADR の参照を必須記載**（A/B実測で唯一の統治逸脱がここから出た＝再発防止）。普段使い tier は flash。
  - **委譲しない**: 統合・設計判断・trade-off 評価・編集起点ファイルの読み（spot-check でどのみち再読＝二重読みで損）。
  - **検証（agy 等の外部モデル委譲時）**: 自己申告 GREEN を信じずゲートは自分で回す／**削除行込みの diff 全量レビュー**（追加行だけのレビューは削除退行を見逃す）／完了判定は報告でなく成果物の存在（`git status`・grep）で確認／外部API境界は仕様書との突合に加え〈UIに見える選択肢⇄実送信パラメータ〉の**全数突合**（未定義値の送信・送出経路の欠落という2つの実測欠陥形態を検出する）／委譲文に定型で「呼び出し側の境界不整合はコンパイルエラーのまま残せ（配線は監督）」を入れる。Claude 系サブエージェント（Explore 等）は全数 spot-check 不要（ただし不可逆操作の根拠に使う引用は現物確認）。
  - **plan運用**: 各フェーズを〈機械的バッチ／判断ループ〉に二分しバッチの委譲可否を plan 冒頭に明記／plan 末尾に「実行セッション起動ブロック」（対象ブランチ・★次はここから・最小読みセット・検証ゲート）を必須化／plan で比較して不採用にした代替案があれば ADR 化（`docs/decisions/`）をコミット計画に含める／実行見込み ~10ターン以上は fresh セッションで実行。**plan モード中の agy 委譲は `--yolo` 厳禁・read-only digest のみ**（plan モードはプラグイン subagent の権限層へ伝播しないため。機序は `task_diary.md` #40）。edit-streak ゲート誤発火は Bash 迂回せずユーザーへ申告。
  - 実測根拠・経済・モデル選定の詳細は auto-memory が正本: `agy-objective-minimize-claude-agy-free`・`agy-delegate-bulk-cost-savings`・`workflow-plan-fresh-session-execution`・`agy-model-selection-guideline`。作業空間は `AGENTS.md`＋memory `agy-workspace-agents-md-two-layers`。
- **Opus 運用チューニング（Fable 休止中の暫定。根拠＝2026-07-07 A/B実測 `../claude-bestpractice/models/knowledge/06-field-ab-opus-vs-fable.md`）**: Opus は「書かれた基準への追従」が最強で、「書かれていない基準の自己設定」（どこまで掘るか・検証をどこまで広げるか）が相対弱点（effort xhigh でも発現）。よってバッチ・複数項目タスクでは**完了定義を依頼側が外給**する:
  - **完了定義4行**: ①近似禁止＝API 等で表現不可なら**停止して相談**するか合成解（複数リクエスト等）を検討 ②症状でなく真因＝なぜその症状かの構造原因を特定してから項目を閉じる ③検証は影響面の**全画面・全組合せ**（スモーク範囲を明示） ④外部事実は一次ソース2点照合後に台帳へ記録。
  - **項目ごと関門**: 複数項目バッチは UI に限らず 1項目ごとに PushNotification→人間目視 OK→コミット（memory `workflow-notify-each-step-visual-check` の全バッチ種への拡張）。
  - **委譲の能動化**: Opus は放置すると自前実装に寄る（公式プロンプティングガイド・実測とも一致）→ 上の委譲基準に該当する生成を**自前でやると決めたときは、着手前に理由を明示提示**する（判断の可視化。自前実装自体は禁止しない）。

## ドメイン知識

**【必須】以下の場面では自力調査・コード探索より先に必ず該当スキルを実行すること。スキル実行を省略して試行錯誤することは禁止。**

- **ビルド・環境セットアップ・Gradleに関する作業が発生したら → 必ず `/build` スキルを最初に実行すること**
- **アーキテクチャ・構成・モジュール間の関係を把握する必要があれば → 必ず `/architecture` スキルを最初に実行すること**
- **Room DBのスキーマ・Entityを変更するときは → 必ず `/db-migration` スキルを最初に実行すること**
- **実機で検証する作業（adb操作・APK投入・androidTest実行・実機DB確認）が発生したら → 必ず `/device-verify` スキルを最初に実行すること**（connectedAndroidTest 直叩きによる蔵書DB消失などの禁忌を含む）
- PDF解析の定数・ルール → `android/app/src/main/java/com/novelreader/pdf/ParserRules.kt` を直接参照
- OPPO/ColorOS 固有動作 → `/device-verify` スキル（§4 の症状→対処表）経由で `task_diary.md` を参照
- Claude Code のフック（`.claude/hooks/`）を新規作成・改修するときは → 先に `task_diary.md` の「Claude Code フック」節（#26 stdin cp932 文字化け・#28 PostToolUse stdout 不達）と `docs/decisions/0004`（matcher範囲・ブランチ跨ぎ破綻）・`0008`（フックは並列実行・検知正規表現は hooks_common.py の単一定義。旧0007）を必ず確認すること（いずれも**サイレント失敗クラス**＝踏むと長期間気づけないため、既存フックの雛形コピーだけで書き始めない）
- **agy(Antigravity) 委譲の実行者向けブリーフィング → `AGENTS.md`（agy が自動注入で読む）＋ `.agents/`（hooks＝禁忌コマンドの機械的ガード）**。監督側の委譲運用（--dir 必須・モデル選定）は auto-memory の `agy-*` 系を参照
- 実行捏造ハルシネーション検知器（トランスクリプト静的解析）→ エンジン `.claude/hooks/detect_fabricated_execution_core.py`／CLI `analyze_transcript.py`。既知の実ハルシネーション正解データ（検証・回帰用）→ `docs/reference/hallucination-ground-truth.md`
  - **ハルシネーションの台帳登録は `/hallucination`**: ユーザーが打った瞬間に UserPromptSubmit フック `record_hallucination.py` が transcript を機械的にスナップショット保全＋台帳の未確定キューへ記載する（モデル推論を介さない＝幻覚直後の Claude を信用しない設計）。Claude の仕事は後段＝`/hallucination` スキルに従い分類・一次情報確定・正式セクション化。正本は `docs/reference/hallucination-ground-truth.md`（他所には書かない。フォーマットは同ファイル冒頭「追記手順」）。
- **方式選定・アーキ判断で代替案を比較するときは → まず `docs/decisions/`（README 索引）で既存の判断・Why-not を確認すること**。判断が下りたら（**不採用の判断・コミットを生まない判断も含め**）ADR 化を検討する——「採用しなかった理由」も1件の ADR（例: 0008）。
- **管理ドキュメントの体系**（役割で分離。混ぜないこと）:
  - **今どうなっているか（状態・完了・既知不具合）→ `STATUS.md`**（現況台帳＝正本）
  - **次に何をやるか（backlog・思いつき・取りこぼし）→ `handover.md`**（やること台帳。**作業に悩んだらまず見る**／拾った宿題はここへ追記）
  - 腐りにくい知見（外部事実→ `task_diary.md` ／ 実装パターン→ `docs/patterns/` ／ 設計判断・Why-not→ `docs/decisions/`(ADR)） ／ 外部API等の参照資料 → `docs/reference/` ／ 過去プランの一次情報アーカイブ → `.claude/plans/`
  - 運用ルール詳細は memory `docs-status-vs-handover-split`。整合点検は `/stale-check` スキル。