# CLAUDE.md

## 概要

日本語Web小説（なろう系）のPDFを、ふりがな対応HTMLに変換して読む **Androidアプリ**
（Jetpack Compose + PDFBox-Android の純 Kotlin 抽出）。ビルド値（SDK・依存バージョン）は `android/app/build.gradle` が正本。

## 必須ゲート（該当作業の前に必ず該当スキルを最初に実行。省略しての試行錯誤は禁止）

- ビルド・環境セットアップ・Gradle → `/build`
- アーキテクチャ・構成・モジュール関係の把握 → `/architecture`
- Room DB のスキーマ・Entity 変更 → `/db-migration`
- 実機検証（adb・APK投入・androidTest・実機DB。connectedAndroidTest 直叩きの蔵書DB消失など禁忌含む）→ `/device-verify`
- **UIの見た目（配色・タイポ・余白・レイアウト・アニメ）→ `/visual-language`**（HTMLモックが正本・Compose は翻訳。意匠の自己判断禁止）
- 構成・描画方式・制御フローを変えるリファクタ後の md/skill 点検 → `/stale-check`（同じターン内で）

## 開発ルール

- チャットへのコード出力は10行以内。UIコメントは日本語。
- **コミット**: 1論理変更＝1コミット・形式 `fix/feat/refactor: 要約（日本語可）`・`git commit` 前に変更内容を提示して人間の承認を得る・`Co-Authored-By` は付けない。**台帳（STATUS/handover）の更新は原因となった論理変更と同じコミットに同梱**（`docs:` 単独コミットはドキュメント自体が作業対象のときのみ）。main への直コミット・merge はフックがブロック＝作業ブランチで進め、コミットは worktree 内セッションから行う。
- **自己検証必須**: Kotlin の `src/main`/`src/test` を変更したら `cd android && ./gradlew testDebugUnitTest` を実行してからコミット計画を提示（PDF抽出ロジックも同テストで担保。androidTest は端末必須のため対象外）。
- **「なぜ」コメントの義務付け**: 自明でないロジック・バグ修正・防御的コードには理由を残す（what のみのコメント禁止。真因未確定なら「〇〇と推定されるが未確定のため防御的に対処」と明記）。
- **git が記録するものは書かない**: SHA・コミットレンジ・コミット数・差分行数・コミット表を台帳へ手書きしない。**完了の記録はコミットメッセージが正本**（だから件名は今後も具体的に書く）。必要ならその場で `git log` を引く。
- **一時ファイルは「抽出→集約→削除」まで1セット**（作りっぱなし禁止。git 管理下の削除は履歴から復元可能＝恐れるべきは未抽出の知識ごと消すこと）。削除・移設で他ファイルからの参照が切れるなら、張り替えるか参照ごと消す。
- **コード変更・コミット前に `git branch --show-current` を確認**し、active plan 冒頭に対象ブランチを記録する（コンテキスト圧縮でブランチ文脈が落ちる対策）。
- **委譲（agy＝実行者・Claude＝監督）**: **2026-07-24〜 agy は当面使用禁止**（明示解除まで。委譲は Claude サブエージェントのみ＝memory `feedback-avoid-agy-low-trust`）。**2026-07-26 に antigravity プラグイン自体を無効化**（`~/.claude/settings.json` の `enabledPlugins` を `false`）＝禁止中の agy へ強制委譲する `force-delegate-gate` が Bash/Edit をブロックしていたため。解除は同フラグを `true` へ戻すだけ（実体は残置＝可逆。**フック配線はセッション起動時固定のため反映は次セッションから**＝`docs/knowledge/claude-code-hook-wiring-session-fixed.md`）。以下は解除後の規範: 原則「**生成＝agy（仕様固定後・目安 ~300行超）・判断＝Claude**」。委譲しない＝統合・設計判断・trade-off 評価・編集起点ファイルの読み。検証の核＝自己申告 GREEN を信じずゲートは自分で回す／**削除行込み diff 全量レビュー**／完了判定は成果物の存在（`git status`・grep）で確認／外部API境界は〈UIの選択肢⇄実送信パラメータ〉全数突合。**plan モード中の agy は `--yolo` 厳禁・read-only digest のみ**（機序＝task_diary #40）。plan は〈機械バッチ／判断ループ〉に二分し末尾に実行起動ブロックを必須化・実行見込み ~10ターン超は fresh セッションで。経済・モデル選定・手順詳細は auto-memory `agy-*` 系が正本。
- **Opus を実行者にするときの処方**（「書かれた基準への追従」が最強・「書かれていない基準の自己設定」が弱点＝effort を上げても埋まらない。根拠＝A/B実測 `/mnt/c/Users/qingj/Desktop/project/claude-bestpractice/models/knowledge/06-field-ab-opus-vs-fable.md`）: **完了定義を外給する**——①近似禁止（表現不可なら停止して相談するか合成解を検討）②症状でなく真因 ③検証は影響面の全画面・全組合せ（スモーク範囲を明示）④外部事実は一次ソース2点照合。複数項目バッチは1項目ごとに PushNotification→人間目視OK→コミット。委譲基準に該当する生成を自前でやるときは着手前に理由を明示。タスク種別ルーティング・ブリーフ設計＝`/mnt/c/Users/qingj/Desktop/project/claude-bestpractice/supervision/opus-protocol.md`。
- Windows 側セッションでは PowerShell 構文（`$_`・`Get-ChildItem` 等）を Bash ツールに渡さない（PowerShell ツールで実行。POSIX コマンドはどちらでも可）。
- コード探索は Grep/Glob 起点（この規模なら十分絞れる。総当たり Read の禁止）。**「起点」＝Bash の `grep`/`sed -n` でなく専用 Grep/Read ツールを使う**（drvfs 上でも速く権限プロンプトも踏まない。過去30日実測で Bash grep 706回 vs Grep ツール18回と乖離していたため明文化）。semble はツールの一つ＝キーワードで絞れない意味検索・類似実装探しのときだけ。パス既知なら直接 Read／アーキ全体把握・スキーマ変更は上の必須ゲートが先／編集前の文脈確認は従来どおり Read。

## 管理ドキュメントの体系（役割で分離・混ぜない）

- **現況（現在値のみ・目安60行）→ `STATUS.md`**（ブランチ追従・main が正本）／**やること → `handover.md`**（悩んだらまず見る。完了したら打ち消し線で残さず**消す**）
- **完了の履歴 → git log が正本**／**判断・Why-not → `docs/decisions/`**（方式比較の前にまず README 索引を確認。不採用判断・コミットを生まない判断も ADR 化を検討）
- **腐りにくい知見 → 新規は `docs/knowledge/` に1知見=1ファイル**（`task_diary.md` は凍結アーカイブ＝既存 #N 参照は有効・新規追記はしない）／実装パターンの「なぜ」→ `docs/patterns/`／外部APIなど参照資料 → `docs/reference/`／過去プランの一次情報 → `.claude/plans/`（役目を終えたら `archive/` へ）
- auto-memory は**ブランチ不変情報のみ**（ブランチ固有の状態・進捗は STATUS/handover が正本）。ブランチ固有内容を `@import` で親パスから引かない。運用詳細＝memory `docs-status-vs-handover-split`・整合点検＝`/stale-check`。

## ドメイン知識（ポインタ）

- PDF解析のルール → 文書ごと自動検出 `android/app/src/main/java/com/novelreader/pdf/DetectedRules.kt`（検出不能時のフォールバック定数＝同 `ParserRules.kt`）を直接参照
- OPPO/ColorOS 固有動作 → `/device-verify`（§4 の症状→対処表）経由で `task_diary.md`
- フック（`.claude/hooks/`）の新規作成・改修 → 先に `task_diary.md` #26/#28 と `docs/decisions/0004`・`0008` を確認（いずれもサイレント失敗クラス＝既存フックの雛形コピーだけで書き始めない）
- **フックの撤去は「参照する側」まで含めて1セット**: 撤去するフック名（拡張子抜き）でリポジトリ全体を grep し、他フックのロジック・コメント・docstring・`.gitignore`・skill の記述に残骸が無いことを確認する。撤去コミットが「撤去する側」しか触らないと、**生成物に依存した判定が恒久 dead 化してもテストは緑のまま通り続ける**（2026-07-12 のテスト強制3点撤去でセンチネル照合が13日間死んでいた実例）
- 実行捏造検知器 → エンジン `.claude/hooks/detect_fabricated_execution_core.py`／CLI `analyze_transcript.py`／正解データ `docs/reference/hallucination-ground-truth.md`
- `/hallucination` は打った瞬間にフックが機械保全して完結（そのターンの Claude は分類・調査を始めず直前の作業に戻る）。事後の分類・正式登録は明示依頼時のみ `/hallucination` スキルで。
