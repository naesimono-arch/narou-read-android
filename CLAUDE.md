# CLAUDE.md

## 概要

日本語Web小説（なろう系）のPDFを、ふりがな対応のHTMLに変換する **Androidアプリ**。
Jetpack Compose + Kotlin ネイティブ PDF 抽出（PDFBox-Android）。
ビルド設定: PDFBox-Android 2.0.27.0 / compileSdk 34 / minSdk 26 / targetSdk 34
（旧 Chaquopy(Python 3.12)+pdfminer は 2026-07-05 Phase 5 で完全撤去＝APK 67→24MiB）

## 開発ルール

- **思考の構造化**: コードを書く前に `<thinking>` タグで「要求の分解」「アプローチと理由」「副作用」を言語化すること。
- **コードダンプ禁止**: チャットへのコード出力は10行以内。全体確認は `code <ファイルパス>` でエディターを開く。
- **Atomic Commit**: 1論理的変更＝1コミット。形式は `fix/feat/refactor: 要約（日本語可）`。`git commit` 前に変更内容を提示して人間の承認を得ること。`Co-Authored-By` トレーラーは付けないこと。
- **UIコメントは日本語**
- **自己検証必須**: Kotlin の `src/main` または `src/test` を変更した場合は必ず `cd android && ./gradlew testDebugUnitTest` を実行してからコミット計画を提示すること（`androidTest` は端末必須のため対象外）。PDF抽出ロジック（`java/com/novelreader/pdf/` の `PdfExtractor`/`TextProcessor`/`ChapterProcessor`/`HtmlExporter` 等）もこの Kotlin テストで担保される（旧 Python 版の単体テスト test_logic.py は Phase 5 で撤去済み）。
- **「なぜ」コメントの義務付け**: 自明でないロジック・バグ修正・防御的コードには必ず「なぜそうしているか」をコメントで残すこと。whatはコードを読めば分かる。根本原因が未確定の場合は「〇〇が原因と推定されるが未確定のため防御的に対処」と明記。**【絶対禁止】** what コメントのみ・why なしのバグ修正・防御的コード追加。
- **task_diary自動更新**: `fix:` コミット後は同じターン内で `task_diary.md` への追記が必要か確認すること。コードコメントだけでは伝わらない根本原因・OEM固有動作・将来はまりやすいパターンがある場合のみ追記。既存エントリと重複なら不要。追記時は内容で置き場を判断すること（**外部プラットフォームの事実・落とし穴**→ `task_diary.md` / **本アプリ実装パターン**＝コードが正本なので「なぜ」に絞り `docs/patterns/` / **設計判断・Why-not**→ `docs/decisions/` のADR）。task_diary のエントリ番号（#N・`§N`参照）は固定IDのためリナンバーしない（移設済みの旧ID対応は `task_diary.md` 末尾の移設マッピング表が正本）。
- **一時ファイルは「抽出→集約→削除」まで1セット（作りっぱなし禁止）**: スクラッチ・TODO・事象レポート・分析ダンプ等の一時ファイルは、役目を終えたら〈①残すべき事項を既存の管理ファイルへ分割・集約 → ②然る後に削除〉を同じ作業単位で完了すること。作りっぱなしにしない。集約先は管理ドキュメント体系に従う（現況→`STATUS.md`／やること→`handover.md`／腐りにくい知見→`task_diary.md`／一次情報の細部→`.claude/plans/`／ブランチ不変の作業知見→auto-memory）。**git 管理下では削除は不可逆ではない**（履歴から復元できる）。恐れるべきは「未抽出の知識ごと消すこと」であって削除そのものではない＝①を済ませれば②は安全になる。**削除・移設で他ファイルからの参照がリンク切れになるなら、張り替えるか参照ごと消すこと**（存在しないファイルを指す台帳は読者を誤誘導する＝放置そのものが害）。
- **スキル陳腐化チェック**: 構成・描画方式・モジュール間の制御フローを変えるリファクタ（例: WebView→Composeネイティブ移行、多重起動ガード方式の変更）をコミットした後は、同じターン内で `.claude/skills/`（特に `architecture`・`db-migration`）の記述が陳腐化していないか確認すること。スキルは久々に触る際の最初の参照先のため、実態とズレると誤誘導の温床になる。
- **UIの見た目は /design のHTMLモックが正本**: 配色・タイポ・余白/構図・レイアウト等の「見た目」は、claude.ai の `/design`（HTMLデザインシステム）で作った**HTMLモックを正本**とし、Compose はその**翻訳**として実装している。**なぜ**: 見た目の思想設計を白紙から立て直す UI-n の分業ワークフロー（見た目＝HTML正本／Claude Code は翻訳者）を採ったため。したがってUIの見た目を触るときは **Compose 側で意匠を自己判断せず、まず設計判断 `docs/decisions/0005-ui-n-visual-language-D.md` とモック現物を確認**すること。**モック現物はリポジトリ内には無い**＝claude.ai/design プロジェクト `Novel Reader UI`（projectId `bb5a35c8-70ac-4efa-bb03-1579d3f11d93`）の `ui-n-phase0/*-D.html` に在り、`DesignSync: get_file` で取得する（詳細は `handover.md` の該当行）。色は `theme/Color.kt`／明朝は `theme/Typography.kt` の `MinchoFamily` 経由（直書き禁止）というトークン構造も維持する。**スコープ外**＝操作感・組版の質感・アニメ・没入クロームの挙動は HTML→Compose で最も劣化する層のため実機フィードバックで後詰め（ADR 0005 §B）。
- **ツール使い分け（Windows環境）**: `Get-ChildItem` / `ForEach-Object` / `Select-Object` / `$_` / `$env:` 等のPowerShell構文は必ず **PowerShell ツール**で実行すること。**Bash ツールに PowerShell 構文を渡すことは禁止**（`/usr/bin/bash` は PS 構文を解釈できずエラーになる）。`git` / `python` / `grep` 等の POSIX コマンドはどちらでも可。
- **コード探索は semble を第一手段に（総当たりReadの禁止）**: 「〇〇する処理はどこか」「このシンボル／識別子の定義・関連箇所はどこか」を探すときは、ファイルを片端から Read・grep する前に必ず `semble`（MCPツール、またはCLI `semble search "<説明>" .`）を使うこと。意味検索で該当スニペットだけを返すため、総当たり Read よりトークン効率が桁違いに良い（公式実測でコード検索コスト約93%減）。**例外（semble を使わず従来手段でよい場面）**: ①ファイルパスが既知なら直接 Read する、②アーキ全体把握は `/architecture` スキルが先、③DBスキーマ変更は `/db-migration` スキルが先。semble の役割は「場所の特定」であり、正確な編集前の文脈確認や構造理解は従来どおり Read で行うこと。
- **ブランチ境界の遵守 / auto-memoryの役割分担**: Claude Code の auto-memory（`MEMORY.md`・`~/.claude` 配下）と session history は**ディレクトリパス紐付けで全ブランチ共有**＝ブランチを考慮しない。そのため:
  - **auto-memory にはブランチ不変情報のみを書く**（ユーザー嗜好・環境・ワークフロー・汎用知見）。スキーマ版・不具合の解決状況・CP進捗などブランチ固有の状態は書かず、**git 管理の `STATUS.md` / `handover.md`（ブランチ追従する）を正本**とする。
  - **`STATUS.md` は存在するブランチで現況の正本**。**`main` が現況台帳 `STATUS.md`＋やること台帳 `handover.md` の正本**（2026-07-02 に lab を統合し main へ一本化）。一時的な作業ブランチを切る場合はそのブランチの `STATUS.md` ないし `.claude/plans/` の active plan が現況で、マージで main へ集約する。現在ブランチは statusline 表示と SessionStart 注入を参照すること。
  - **整理済みの doc アーキ（`STATUS.md` ＋規約準拠 `handover.md` ＋ `docs/decisions`・`docs/patterns`）は main が正本**。handover/docs の整理分割（完了済み項目→`STATUS.md` 移動・冒頭の思いつき欄・`docs/decisions/` への ADR 化）は **main で行う**（かつて lab を正本とし main 先行整理との乖離が宿題だったが、2026-07-02 の統合で main へ一本化して解消）。
  - **コード変更・コミット前に必ず `git branch --show-current` を確認**し、active な plan ファイル冒頭に対象ブランチを記録する（コンテキスト圧縮で現在ブランチが脱落しても参照可能なデータとして残すため）。`main` への直接コミットは **Bash ツール経由の一般的な `git commit`（改行区切り・`git -C/-c …` 等のグローバルオプション付き）に加え、コミットを生成する merge/rebase/cherry-pick も `guard_commit_branch.py` が検知してブロック**する（2026-07-06 拡張）＝**ソフトな防御網であり完全な防止ではない**（PowerShell ツール経由・難読化形・`git pull` は対象外。既存コミット系フックが `matcher:"Bash"` のため PowerShell 実ゲート化は波及の大きい別タスク）。最終防壁は作業ブランチ運用と「未 push の main コミットは可逆」である事実。
  - ブランチ固有の内容を `@import` で `~/.claude` や親パスから引かないこと（worktree 越境でパスが誤解決する既知問題を避けるため、参照はリポジトリ内の相対パスに留める）。
- **委譲判断 / plan運用（agy＝実行者・Claude＝監督。コスト方針は「agy側は無料扱い・Claude側の節約を最大化」）**: サブエージェント／委譲の使い分けの原則は「**読み＝agy・判断＝Claude**」。
  - **①探索は agy に回す**＝コード read→依存関係・注意点の digest／多ファイル読→小 digest。実測で健全性を確認済み（同一タスク4モデル採点で flash **9.0/10**・幻覚ゼロ・行番号引用ほぼ正確＝「安心して任せられる」水準。かつサブエージェントの read 実体は全コストの**約9%**＝最大の未回収レバー）。普段使い tier は **flash**（詳細と pro/Sonnet 4.6 の使い所は memory `agy-model-selection-guideline`）。
  - **②統合・設計判断・trade-off 評価は Claude に残す**。**なぜ**: digest はコードと違いゲートで機械検証できず、かつ plan の質＝実行コストの源泉だから。委譲時の条件は〈**file:line 引用必須→Claude が引用行を spot-check**／網羅が要るなら観点を分けて複数回投げる（上位N制約で細部が落ちるため）／**verification は agy が無料でも Claude が必ず払う**＝自己申告 GREEN を信じずゲートを自分で回す〉。**この検証条件（引用spot-check・GREEN不信）は agy(Gemini) 等の外部モデル委譲に向けたもの**。Claude 自身のサブエージェント（Explore / general-purpose 等＝同系モデル）への委譲は正確性が期待できるため全数 spot-check は必須でない（ただし編集・削除・コミット等の不可逆操作の根拠に使う引用は現物確認してから使う）。
  - **③plan 作成時**は各フェーズを〈機械的バッチ〉〈判断ループ〉に二分し、**バッチ側の agy 委譲可否を plan 冒頭に明記**する。
  - **④plan 末尾に「実行セッション起動ブロック」を必須化**（対象ブランチ・★次はここから・最小読みセット＝実行に要るファイル+行範囲・検証ゲートのコマンド）。「最小読みセットが書けない」＝plan の圧縮が未完のシグナル。
  - **⑤実行見込み ~10ターン以上の plan は fresh セッションで実行**。**なぜ**: cache 非対称（読み直しは一回課金・太った context の持ち越しは毎ターン課金＋5分TTL 失効で全量再 write）で分岐点≈実行10ターン。
  - **⑥edit-streak ゲート**（`AGY_FORCE_DELEGATE_EDITS=1`・`.bashrc` 設定済み・次回 claude 起動から有効）が誤発火したら、**Bash の sed/heredoc へ迂回せずユーザーへ申告**する（env の逃げ道はセッション起動時固定でセッション中に設定できないため）。
  - **⑦plan モード中の探索委譲**: 既定は Explore（harness の Phase-1 が「Explore のみ」を明示）。だが〈ユーザー明示〉または〈明らかに break-even 超の read-only 探索（多ファイル read→小 digest）〉は **agy へ委譲してよい**。**絶対条件**: Claude Code は plan モードをプラグイン subagent の権限層へ**伝播させない**（`permissionMode`/`hooks` frontmatter もサイレント破棄＝2026-07-06 の実機 probe で確認・issue #4750 のギャップ）ため **plan モードは agy の書き込みを守らない** → **plan 中の agy 委譲は `--yolo` 厳禁・read-only digest のみ**（read-only は「`--yolo` を渡さない＝agy が書き込み不能」で構造的に担保）。割に合わない小さな読みは Explore にフォールバック。機序の詳細は `task_diary.md`「Claude Code フック」節。
  - **根拠**: 53セッション/$987 の transcript 解剖で、委譲の価値は「タイピングの肩代わり」（コード出力実体は総額0.8%）ではなく「太った context に居座るターン数の削減」（cache churn 回避）と確定。実測の全容は memory `agy-objective-minimize-claude-agy-free`／`workflow-plan-fresh-session-execution`。agy の作業空間・`--dir` 運用は `AGENTS.md`＋memory `agy-workspace-agents-md-two-layers`。

## ドメイン知識

**【必須】以下の場面では自力調査・コード探索より先に必ず該当スキルを実行すること。スキル実行を省略して試行錯誤することは禁止。**

- **ビルド・環境セットアップ・Gradleに関する作業が発生したら → 必ず `/build` スキルを最初に実行すること**
- **アーキテクチャ・構成・モジュール間の関係を把握する必要があれば → 必ず `/architecture` スキルを最初に実行すること**
- **Room DBのスキーマ・Entityを変更するときは → 必ず `/db-migration` スキルを最初に実行すること**
- **実機で検証する作業（adb操作・APK投入・androidTest実行・実機DB確認）が発生したら → 必ず `/device-verify` スキルを最初に実行すること**（connectedAndroidTest 直叩きによる蔵書DB消失などの禁忌を含む）
- PDF解析の定数・ルール → `android/app/src/main/java/com/novelreader/pdf/ParserRules.kt` を直接参照
- OPPO/ColorOS 固有動作 → `/device-verify` スキル（§4 の症状→対処表）経由で `task_diary.md` を参照
- Claude Code のフック（`.claude/hooks/`）を新規作成・改修するときは → 先に `task_diary.md` の「Claude Code フック」節（#26 stdin cp932 文字化け・#28 PostToolUse stdout 不達）と `docs/decisions/0004`（matcher範囲・ブランチ跨ぎ破綻）を必ず確認すること（いずれも**サイレント失敗クラス**＝踏むと長期間気づけないため、既存フックの雛形コピーだけで書き始めない）
- **agy(Antigravity) 委譲の実行者向けブリーフィング → `AGENTS.md`（agy が自動注入で読む）＋ `.agents/`（hooks＝禁忌コマンドの機械的ガード）**。監督側の委譲運用（--dir 必須・モデル選定）は auto-memory の `agy-*` 系を参照
- 実行捏造ハルシネーション検知器（トランスクリプト静的解析）→ エンジン `.claude/hooks/detect_fabricated_execution_core.py`／CLI `analyze_transcript.py`。既知の実ハルシネーション正解データ（検証・回帰用）→ `docs/reference/hallucination-ground-truth.md`
  - **ユーザーから「（このセッションの）ハルシネーションを記載して」と頼まれたら → `docs/reference/hallucination-ground-truth.md` に追記する**（正本はここ。他所には書かない）。手順: ①該当セッション JSONL（`~/.claude/projects/<slug>/*.jsonl`）を特定 → ②`analyze_transcript.py` を先にかける（**ただし静的検知器が拾うのは「実行の捏造」だけ**。存在しない対話・話題逸脱・帰属誤り・生成コード不具合などは 0 件で通るので、**JSONL を直読して一次情報で幻覚を確定**する）→ ③既存フォーマットに倣い〈確度・場所（JSONLパス）・`行/uuid` 表・※根拠note〉のセクションを追加し、冒頭の「確認済み実幻覚セクション: N件（M事象）」を更新。同事象の HANDOFF/MEMORY エコーは重複参照として非掲載にする。
- **管理ドキュメントの体系**（役割で分離。混ぜないこと）:
  - **今どうなっているか（状態・完了・既知不具合）→ `STATUS.md`**（現況台帳＝正本）
  - **次に何をやるか（backlog・思いつき・取りこぼし）→ `handover.md`**（やること台帳。**作業に悩んだらまず見る**／拾った宿題はここへ追記）
  - 腐りにくい知見（外部事実→ `task_diary.md` ／ 実装パターン→ `docs/patterns/` ／ 設計判断・Why-not→ `docs/decisions/`(ADR)） ／ 外部API等の参照資料 → `docs/reference/` ／ 過去プランの一次情報アーカイブ → `.claude/plans/`
  - 運用ルール詳細は memory `docs-status-vs-handover-split`。整合点検は `/stale-check` スキル。
- **ホットスポット分析**（頻繁変更ファイルの特定）:
  ```bash
  # ファイル別変更回数ランキング（上位20件）
  git log --name-only --format="" | sort | uniq -c | sort -rn | head -20
  # 特定ファイルの変更回数
  git log --oneline -- <file_path> | wc -l
  ```
  AIへの提示例: 「上記コマンドの結果を渡して、なぜ頻繁に変更されるのか・設計上の問題がないかを分析させる」
