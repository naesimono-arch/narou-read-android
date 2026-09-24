# AGENTS.md

> **正本は `CLAUDE.md`。このファイルはその複製**（agy／Codex CLI は `AGENTS.md` を読むため必要）。
> **固有名詞の一括置換をしない**——過去に `Claude`→`Codex` の全置換が `.claude/hooks/` を `.Codex/hooks/` に、
> `claude-bestpractice` を `Codex-bestpractice` に化けさせ、**実在しないパスを指す規約**になっていた（2026-08-11 是正）。
> 更新するときは `cp CLAUDE.md AGENTS.md` してこの見出しブロックだけ戻す。
>
> **本文中の `/build` などのスキルは `.claude/skills/<name>/SKILL.md` が実体**——スラッシュ起動を持たない
> エージェントは該当ファイルを直接読むこと。`.agents/skills` への複製は**置かない**（同じ置換事故で
> `~/.Codex/AGENTS.md` 等の実在しないパスに化けていた9ファイルを 2026-08-11 に撤去した。
> 二重管理はドリフト源＝skills の正本は `.claude/skills` 一箇所）。

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
- **報告は結論と根拠だけ**: 調査の実況・検討して捨てた選択肢の列挙・同じ結論の言い換え・末尾の要約を書かない。
  表は3行以上の比較にだけ使う。見出しは話題が変わるときだけ（**なぜ**＝監督の判断を速くするため。
  ⚠️ **コスト規律としてではない**——応答本文の実効寄与は**7〜10%**の小項目にすぎない。
  **最大はツール引数 37.7%**・次いでシェル出力 25.6%・**Read 20.4%**
  （計測は `tools/measure_read_residency.py`〔ほか `measure_read_kind.py`・`measure_toolinput_and_ledger.py`〕）。
  ⚠️ **旧記載「assistant 4.3%・Read 36.3%〔handover.md 8.9%〕・tool_use 31.1%・Bash 20.1%」は誤り**
  〔2026-09-02 に独立実装との突合で確定〕——**順位まで違っていた**（旧は Read 最大・実際はツール引数が最大）。
  真因は計測側の欠陥3件＝①換算係数が除数で使われ約4.8倍ずれる ②分母が `cache_read` でなく人間の入力と
  常設注入を数えていない ③1回の API 応答が複数イベント行に分割記録され**各行が同じ usage を複製**するため、
  イベント単位で数えると総量もターン数も約2倍に膨らむ（③は独立実装が発見）。
  ⚠️ **構成比は信用してよい**（独立実装と全区分 2.4pt 以内で一致・換算係数を変えても比は 0.5pt しか動かない）。
  ⚠️ **絶対値はまだ信用しない**＝総額に未説明 22% が残る（jsonl 本文に現れない注入が候補・特定未了）。
- **コミット**: 1論理変更＝1コミット・形式 `fix/feat/refactor: 要約（日本語可）`・`git commit` 前に変更内容を提示して人間の承認を得る・`Co-Authored-By` は付けない。**台帳（STATUS/handover）の更新は原因となった論理変更と同じコミットに同梱**（`docs:` 単独コミットはドキュメント自体が作業対象のときのみ）。main への直コミット・merge はフックがブロック＝作業ブランチで進め、コミットは worktree 内セッションから行う。
- **自己検証必須**: Kotlin の `src/main`/`src/test` を変更したら `cd android && ./gradlew testDebugUnitTest` を実行してからコミット計画を提示（PDF抽出ロジックも同テストで担保。androidTest は端末必須のため**実行**は対象外）。
  **本番の public シグネチャを変えたら `./gradlew :app:assembleDebugAndroidTest` でコンパイルだけ確認する**——
  既定ゲートは androidTest をコンパイルしないため、追従漏れが壊れたまま潜伏する（実際に2回発生）。
  golden・release R8 を含む全ゲートの結線は CI が持つ（`.github/workflows/ci.yml`）ので、ローカルは上記2つで足りる。
- **「なぜ」コメントの義務付け**: 自明でないロジック・バグ修正・防御的コードには理由を残す（what のみのコメント禁止。真因未確定なら「〇〇と推定されるが未確定のため防御的に対処」と明記）。
- **git が記録するものは書かない**: SHA・コミットレンジ・コミット数・差分行数・コミット表を台帳へ手書きしない。**完了の記録はコミットメッセージが正本**（だから件名は今後も具体的に書く）。必要ならその場で `git log` を引く。
- **一時ファイルは「抽出→集約→削除」まで1セット**（作りっぱなし禁止。git 管理下の削除は履歴から復元可能＝恐れるべきは未抽出の知識ごと消すこと）。削除・移設で他ファイルからの参照が切れるなら、張り替えるか参照ごと消す。
- **コード変更・コミット前に `git branch --show-current` を確認**し、active plan 冒頭に対象ブランチを記録する（コンテキスト圧縮でブランチ文脈が落ちる対策）。
- **委譲は二層**（⚠️ **agy は 2026-08-13 に再凍結＝プラグインも無効・第3層は当面 Claude サブが兼ねる**。裁定＝**ADR 0031 決定5**、成果物の所在と解凍条件＝`docs/backlog-frozen.md`）: **判断＝main／設計と品質保証＝Claude サブ／生成＝Claude サブ**（仕様固定後・目安 ~300行超）。委譲しないのは統合・設計判断・trade-off 評価・編集起点ファイルの読み。**設計サブと品質保証サブは分ける**（仕様の不備は書いた本人に構造的に見えない）。「**削除行込み diff 全量レビュー**」は2層目（品質保証サブ）の職務＝監督は最終ゲートだけ持つ。検証の核＝自己申告 GREEN を信じずゲートは自分で回す／完了判定は成果物の存在（`git status`・grep）で確認／外部API境界は〈UIの選択肢⇄実送信パラメータ〉全数突合。⚠️ 節約の期待値を誤らないこと＝コスト主体は生成でなく `cache_read` で、**生成だけ移しても約3%**（ADR 0031 判断材料6）＝**委譲の動機はコストでなく〈監督のコンテキスト保護〉と〈独立した第二の目〉に置く**。plan は〈機械バッチ／判断ループ〉に二分し末尾に実行起動ブロックを必須化・実行見込み ~10ターン超は fresh セッションで。
- **Opus を実行者にするときの処方**（「書かれた基準への追従」が最強・「書かれていない基準の自己設定」が弱点＝effort を上げても埋まらない。根拠＝A/B実測 `/mnt/c/Users/naesimono/Desktop/project/knowledge/claude-ops/models/knowledge/06-field-ab-opus-vs-fable.md`）: **完了定義を外給する**——①近似禁止（表現不可なら停止して相談するか合成解を検討）②症状でなく真因 ③検証は影響面の全画面・全組合せ（スモーク範囲を明示）④外部事実は一次ソース2点照合。複数項目バッチは1項目ごとに PushNotification→人間目視OK→コミット。委譲基準に該当する生成を自前でやるときは着手前に理由を明示。タスク種別ルーティング・ブリーフ設計＝`/mnt/c/Users/naesimono/Desktop/project/knowledge/claude-ops/supervision/opus-protocol.md`。
- Windows 側セッションでは PowerShell 構文（`$_`・`Get-ChildItem` 等）を Bash ツールに渡さない（PowerShell ツールで実行。POSIX コマンドはどちらでも可）。
- **コード探索は `rg` 起点**（総当たり Read の禁止）。⚠️ **Grep/Glob ツールは現行の実行系に存在しない**——旧記載「専用 Grep ツールを使う」は物理的に守れず、実測の乖離（Bash grep 706回 vs Grep 18回）を規律違反として誤読させていた（真因＝サーバー配信のツール構成。ローカル設定・バージョン・プロジェクト固有要因はいずれも対照実験で否定済み＝`docs/knowledge/tooling-no-grep-glob-tools.md`。**復旧待ちや設定変更で戻せる類ではない**）。作法＝**①`rg -l` でファイルを絞る ②必要箇所だけ Read の offset/limit で読む ③行を出すなら `-g '<glob>' -m 3` で対象と1ファイル上限を付ける**。`find`/`grep -r` より `rg` を優先（`.gitignore` を自動尊重するので `-prune` の羅列が消え、**コマンド文字列自体が縮む**＝費用はシェル出力 25.6% より**ツール引数 37.7% の方が大きい**〔最大区分〕）。semble はツールの一つ＝キーワードで絞れない意味検索・類似実装探しのときだけ。パス既知なら直接 Read／アーキ全体把握・スキーマ変更は上の必須ゲートが先／編集前の文脈確認は従来どおり Read。

## 管理ドキュメントの体系（役割で分離・混ぜない）

- **現況（現在値のみ・目安60行）→ `STATUS.md`**（ブランチ追従・main が正本）／**やること → `handover.md`**（悩んだらまず見る。完了したら打ち消し線で残さず**消す**）
- **人間の目視・裁定・外部手続き待ち → `awaiting-human.md`**（handover は「Claude が今すぐ動けるもの」だけ。二分の軸＝待ちの種類・迷ったら handover 側＝ADR 0028）
- **完了の履歴 → git log が正本**／**判断・Why-not → `docs/decisions/`**（方式比較の前にまず README 索引を確認。不採用判断・コミットを生まない判断も ADR 化を検討）／**凍結・見送り → `docs/backlog-frozen.md`**（捨てず解凍条件つき）
- **腐りにくい知見 → 新規は `docs/knowledge/` に1知見=1ファイル**（`task_diary.md` は凍結アーカイブ＝既存 #N 参照は有効・新規追記はしない）／実装パターンの「なぜ」→ `docs/patterns/`／外部APIなど参照資料 → `docs/reference/`／過去プランの一次情報 → `.claude/plans/`（役目を終えたら `archive/` へ）
- auto-memory は**ブランチ不変情報のみ**（ブランチ固有の状態・進捗は STATUS/handover が正本）。ブランチ固有内容を `@import` で親パスから引かない。整合点検＝`/stale-check`。

## ドメイン知識（ポインタ）

- PDF解析のルール → 文書ごと自動検出 `android/app/src/main/java/com/novelreader/pdf/DetectedRules.kt`（検出不能時のフォールバック定数＝同 `ParserRules.kt`）を直接参照
- OPPO/ColorOS 固有動作 → `/device-verify`（§4 の症状→対処表）経由で `task_diary.md`
- フック（`.claude/hooks/`）の新規作成・改修 → 先に `task_diary.md` #26/#28 と `docs/decisions/0004`・`0008` を確認（いずれもサイレント失敗クラス＝既存フックの雛形コピーだけで書き始めない）
- **フックの撤去は「参照する側」まで含めて1セット**: 撤去するフック名（拡張子抜き）でリポジトリ全体を grep し、他フックのロジック・コメント・docstring・`.gitignore`・skill の記述に残骸が無いことを確認する。撤去コミットが「撤去する側」しか触らないと、**生成物に依存した判定が恒久 dead 化してもテストは緑のまま通り続ける**（2026-07-12 のテスト強制3点撤去でセンチネル照合が13日間死んでいた実例）
- 実行捏造検知器 → エンジン `.claude/hooks/detect_fabricated_execution_core.py`／CLI `analyze_transcript.py`／正解データ `docs/reference/hallucination-ground-truth.md`
- `/hallucination` は打った瞬間にフックが機械保全して完結（そのターンの Claude は分類・調査を始めず直前の作業に戻る）。事後の分類・正式登録は明示依頼時のみ `/hallucination` スキルで。
