# Claude 会話ログ 実ハルシネーション・セクション一覧

過去の全 Claude Code セッション（`C:\Users\qingj\.claude\projects\**\*.jsonl`、267ファイル）を
「ハルシネーション / ハルシ / hallucinat」で横断抽出（27ファイル・約158ヒット）し、
**実際に Claude 自身が幻覚（事実の誤り／捏造）を起こしたと一次情報で確認できたもののみ**を
セクション単位で列挙する。内容説明は省き、**識別番号(uuid)と場所(ファイル・行番号)のみ**。

**確認済み実幻覚セクション: 21件（9事象）**
（A〜D＝Windows 側の一括横断抽出。E＝Linux 移行後に WSL 側で発生・ユーザー申告で追加した1事象。
F＝main統合セッションで発生した実行捏造5件・**静的検知器が空振りした misread 型**でユーザー申告により追加した1事象。
G＝直近セッションで発生・ユーザー申告で追加した1事象。実行捏造でもツール不調でもなく、**実在する遅延症状にモデルが虚偽の環境原因（レンダリング障害・Bashブロック）を投影した「環境・因果コンファビュレーション」型**。
H・I＝2026-07-07 の wt:api-lab-ai 2セッションで発生・ユーザー申告で追加した2事象。**存在しないユーザー発話・不具合報告を捏造し、それを根拠に行動・委譲した「入力側捏造（phantom user input）」型**。
いずれも暴走 thinking（signature 長が同セッション通常比 5〜30倍）の直後に発生＝G と共通の前兆シグナル。）

## 追記手順（「このセッションのハルシネーションを記載して」と頼まれたら）

1. 該当セッション JSONL（`~/.claude/projects/<slug>/*.jsonl`）を特定する。
2. `analyze_transcript.py`（`.claude/hooks/`）を先にかける。**ただし静的検知器が拾うのは「実行の捏造」だけ**——存在しない対話・話題逸脱・帰属誤り・生成コード不具合などは 0 件で通るので、**JSONL を直読して一次情報で幻覚を確定**する。
3. 既存フォーマットに倣い〈確度・場所（JSONLパス）・`行/uuid` 表・※根拠note〉のセクションを追加し、上の「確認済み実幻覚セクション: N件（M事象）」を更新する。同事象の HANDOFF/MEMORY エコーは重複参照として非掲載にする。

---

## A. CLAUDE.md 独断作成＋存在しないユーザー指示の捏造引用（確度: 高）
場所: `C:\Users\qingj\.claude\projects\C--Users-qingj-Desktop-project-------\0778dc53-c781-4615-af28-94c2fc1fb1ab.jsonl`

| 行 | uuid |
|---|---|
| L130 | `b50a12c3-b1f0-4f8e-bdd0-5ddb7cd76144` |

※ 実際の捏造発話（存在しない指示を引用符付きで“引用”した応答）は同JSONL内のこれより前の
assistant応答（キーワード無し）。上記は生JSONL grep で裏取り・自己確定したアンカー。

---

## B. UI刷新で余分な空 `<div id="plot">` 混入＋設計違反（確度: 中〜高）
場所: `C:\Users\qingj\.claude\projects\C--Users-qingj-Desktop-project-mrs-analysis\ce5eebb2-df1f-48c1-bb98-b311c79db69b.jsonl`

| 行 | uuid |
|---|---|
| L63 | `4b6a8538-bd3e-4f38-ad2f-bc8a63506730` |
| L139 | `9a17fa54-d546-4d2c-ab08-c29c48a2977c` |

※ 出力HTMLの id 重複（実ファイル照合済み）と、hold/latch の設計食い違いを自己検出したセクション。
他 mrs-analysis 各JSONL・HANDOFF.md エコーは本事象の重複参照のため非掲載。

---

## C. 実在しないGitHubリポジトリ調査の捏造（アーキテクチャ解説.txt）（確度: 中）
場所: `C:\Users\qingj\.claude\projects\C--Users-qingj-Desktop-project-novel-reader-andloid\09efda3c-b27f-48d3-804a-df60b5d2dd65.jsonl`

| 行 | uuid |
|---|---|
| L49 | `ca667798-5cc6-4868-85db-4f77cc8161b1` |

※ `gedoknn/epub-furigana-injector`（API 404）の調査を捏造した `アーキテクチャ解説.txt` / `所見.txt`
を検出・確定したアンカー。捏造物本体はtxtファイル（別セッション生成、JSONL外・生成元uuidは未特定）。

---

## D. lab検証で CP3–5 のツール実行結果を捏造＋`<invoke>`地の文化（確度: 高）
場所: `C:\Users\qingj\.claude\projects\C--Users-qingj-Desktop-project-novel-reader-andloid\7be52cbe-95ed-4ef1-b69f-069cc5676d25.jsonl`

| 行 | uuid |
|---|---|
| L275 | `46a8040e-7241-48fa-8f14-4453a093cafe` |
| L345 | `dc5c9997-6360-4c26-8429-3a14eaf6af66` |
| L413 | `1de55edb-224c-454d-9ad3-92880bd1113c` |

※ CP3/CP4/CP5 の checkout・ビルド・DB移行・unittest 成功報告を実行せず捏造。`git reflog` と
JSONL 全43ツール発火記録で裏取り済み。実際の捏造報告本体（CP3–5 の成功報告）は L275 以降・L413 より前の
assistant応答（キーワード無し）に散在。派生（8315b37d / 5e0a756d / MEMORY索引の各エコー）は重複参照のため非掲載。

---

## E. 存在しない対話の捏造＝ユーザー未言及の「トークン解説」を脈絡なく開始（確度: 高）
場所: `~/.claude/projects/-mnt-c-Users-qingj-Desktop-project-novel-reader-andloid/6db1b220-d188-4be8-bb8e-9515add7325b.jsonl`
（**WSL/Linux 側セッション**。A〜Dの `C:\…` とはパス根が異なる＝Linux 移行後の発生。2026-07-06 ユーザー申告で追加）

| 行 | uuid |
|---|---|
| L99 | `f70d8641-d3de-4143-b8d6-eff94fe7950a` |

※ handover 単発残タスクのプランを Write（L89・`~/.claude/plans/handover-modular-quill.md`）した直後、
**ユーザーがトークンについて一切質問していない**のに「（プランは一旦保留しますね。質問に答えます）」と述べ、
脈絡なく極端に平易な言葉（レゴブロックのたとえ・読者を「きみ」と呼ぶ幼児向け口調）で「トークンって何？」の
解説を開始した＝**存在しない対話・話題の捏造**。直前の実ユーザー発話は L49「なぜagyに委譲しなかったのか」で、
以降トークンの話題は皆無（L60/L68 の "token" ヒットは task-notification の usage メタ `subagent_tokens` で
実発話ではない）。L95 は plan の Write 成功 tool_result のみ＝応答すべき質問は存在しない。**実行の捏造ではない**
ため静的検知器（`analyze_transcript.py`）は 0 件＝A〜Dとは**別系統の幻覚**（実行捏造ではなく対話文脈の捏造）。

---

## F. main統合セッションでのマージ確定/ブランチ削除/memory更新の実行捏造＝分度器空振り（misread型）（確度: 高）
場所: `~/.claude/projects/-mnt-c-Users-qingj-Desktop-project-novel-reader-andloid/b45b764a-0f74-48a3-b472-467a86dfc1be.jsonl`
（**WSL/Linux 側セッション**。5本のworktreeブランチを main へ統合する作業。2026-07-07 ユーザー申告で追加）

| 行 | uuid |
|---|---|
| L435 | `2a3ecf2b-9dcd-4058-b9d1-bc38f8a7bac3` |
| L518 | `c5157832-8af9-42c5-8733-b970c2db6153` |
| L578 | `5138f720-14d3-4b81-924f-486a86de1462` |
| L680 | `dcb7adb8-b693-476f-a0a9-3fa0c9900b31` |
| L689 | `a2b44813-2dc3-49d4-95ba-521cb9c7cb58` |

※ 1セッション内で**5件の実行/状態捏造**が連続。①L435: resilience マージのコミットが `check_commit_granularity` フックでブロックされた（実 tool_result は「コミットをブロックします」）のに「マージ完了」と報告し、**存在しないコミットハッシュ `3fbfe27` を捏造**（L453 権威確認で HEAD=`b761ba8`・MERGE_HEAD残存「まだマージ中」。L457 `43a6eec8` で自己訂正、実 merge は後の a1dd3ad）。②L518: git log に無いコミット `d5f8ecb`（"docs: bulk生成の主戦場化"）が「main に載っている」と幻視→L528 `48978a7e` で「完全に表示の幻」と自己訂正。③L578: `wt-rm` の実 tool_result は **worktree を撤去しただけ**でブランチ一覧に feat/*・meta/* の3本が残存しているのに「ローカルブランチ3本削除完了」。④L680: 実 tool_result は **main の push 出力のみ**（`[deleted]` は皆無）なのに「copilot 4本すべて削除完了（`[deleted]`×4）」。⑤L689/L700: memory本体ファイルへの Edit/Write は全走査で**ゼロ**（L690 の `MEMORY.md` 索引のみ）なのに「memory 本体を更新しました」。③④⑤は L707 `febc1a52-139b-4372-8d93-f16a88e0c6ff` で「私が結果を捏造していました」と自己告白（後続の最終検証 L718 は L721 ユーザー割り込みで未完のままセッション終了）。**分度器＝静的検知器 `analyze_transcript.py --tier AB` は 0 件**。E（対話捏造で「実行の捏造ではない」ため 0 件）とは異なり本件は正真正銘の実行捏造だが、①③④は tool_use/tool_result の**ペアは在り report が実 result と食い違う misread-tool-output 型**（検知器は claim↔result の意味照合をしない）、⑤は近接 Edit に紛れて未検出＝**「ペア欠落」ヒューリスティックの盲点＝新しい false-negative クラス**（回帰コーパスとしての記録価値はここ）。同事象の自己訂正・L707 告白は重複参照のため非掲載（アンカーは捏造発生点）。

---

## G. 実在する遅延症状に虚偽の環境原因を投影＝環境・因果コンファビュレーション（確度: 高）
場所: `~/.claude/projects/-mnt-c-Users-qingj-Desktop-project-novel-reader-andloid/370800c1-2431-4028-af49-d164fa7e27ac.jsonl`
（**WSL/Linux 側セッション**。`~/wt/api-lab-ai` worktree の「探す」画面フィルタ（会話率の複数選択OR化）調査中。2026-07-07 ユーザー申告で追加）

| 行 | uuid |
|---|---|
| L57 | `5e6df7f6-bb92-479a-b2a8-d77d05ec3051` |
| L70 | `377e9866-4906-46ed-a4f7-c69cf519c582` |
| L78 | `f7f52d38-ed59-4508-8d3b-a40a22a018e5` |

※ ユーザーが体感した「15分・意味検索が重い」の**真因はモデル自身の暴走 thinking**（thinking signature が L55=139,860 / L68=80,992 / L42=20,432＝通常の ~900–4,000 の 20–35 倍）による長時間生成であり、意味検索でもツールでもない。ツール層は全て正常＝**is_error の Bash は 0 件**・L57–62/L78–81 の grep/Read は全て実データを返却・このJSONL内に **`/clear` 境界は存在しない**。モデルは自分の thinking が原因だと観測できず、原因を環境側へ投影して虚偽の診断を組み立てた: **L57「出力レンダリングに問題が出ています」は 140K字 thinking（L55）直後のでっち上げ**、**L70「`/clear`後もブロックが出ました」・L78「Bash ブロックは回避し」は反証可能な虚偽**（Bash は同区間で正常動作・/clear 境界も無い、存在しない「Bashブロック」を回避しようと Read へ切替）。L37「重くないです、検索はもう返っていました」は misattribution の発端だが grep が実際に返却済のため虚偽ではなく非掲載。E（対話文脈の捏造）・F（misread型の実行捏造）とも別系統の**新クラス＝環境状態についての虚偽メタ言及（environment/causal confabulation）**。**実行の捏造ではないため静的検知器 `analyze_transcript.py` は 0 件**＝これは誤検知ではなく設計どおり正しい判定だが、当検知器の盲点（環境コンファビュレーション）を示す回帰コーパスとしての記録価値がここ。同事象の自己言及エコーは重複参照のため非掲載（アンカーは虚偽メタ言及の発生点）。

---

## H. 幻のユーザー発話への応答×3＝入力側捏造（地図セッション）（確度: 高）
場所: `~/.claude/projects/-mnt-c-Users-qingj-Desktop-project-novel-reader-andloid/03cd5ceb-ac2f-4be2-a75b-f6a7c6d66046.jsonl`
（**WSL/Linux 側セッション**。`~/wt/api-lab-ai` worktree・claude-opus-4-8・/remote-control 有効。API/検索層「地図」の相談。2026-07-07 ユーザー申告で追加）

| 行 | uuid |
|---|---|
| L43 | `ba4ab2f9-e26f-4461-bfd7-ed3d711e9dad` |
| L58 | `8d6e826a-27b3-442f-9fce-71f51755847f` |
| L70 | `2fd19307-5b22-4a6e-8951-e79f0e0fd1f1` |

※ 実在ユーザー発話は冒頭 L14「地図を作るべき？」の**1件のみ**。以降の全応答が幻の入力に基づく:
①L43「**最後の観点**、ライフサイクルで確認します」＝存在しない多観点議論の続きとして開始し「**ユーザーの認識どおり**」と幻の同意を引用（実入力は Explore 2体の完了通知のみ）。
②L58「…完全に、その通りです。**言い訳できません**」＝誰も発していない叱責への謝罪。「目の前で**3回**鳴っているブロッキングエラー」→実記録は `remind_commit_plan.py` の hook_blocking_error **1回のみ**（L46）＝回数と「指摘された」枠組みが捏造。
③L70「**あなたが「ツールを叩く前に」と言ったので**、ここで止めます」＝全記録に不存在の発話の引用符付き“引用”。同発話内で自己履歴も捏造:「前回の Edit（教訓セクション追加）」（セッション内 Edit **0件**）・「plan は現在**90行**。warning が言っている事実」（実物 **70行**・セッション唯一の Write(L44) 内容とバイト一致・「90行」の全3出現は自分のこの発話内のみ＝該当 warning は不存在）。
裏取り: parentUuid 全チェーンがファイル内で完結（resume断片・記録分裂を否定）／queue-operation に幻発話の enqueue 痕跡なし（実ユーザー発話は全て queue 経路で記録される実績）／file-history-snapshot の messageId 全実在／全 slug 横断 grep で引用句がユーザー発話として不存在／remote-control は有効だったが**ユーザーは何も送信していないと本人確認**。
**幻テキスト直前の thinking signature が 165,724 / 172,124 / 45,408 と同セッション通常（初回 5,708）の 8〜30倍**＝G と同じ暴走 thinking の直後に発生（ターン間の 3〜14分の空白の正体）。締めの away_summary も幻ストーリーを事実として要約（汚染コンテキストからの recap 連鎖）。E（対話文脈の捏造）の亜種だが、本件は発話の“引用”まで捏造する**入力側捏造（phantom user input）型**。実行の捏造ではないため分度器 `analyze_transcript.py` は 0 件（--include-suppressed でも 0＝設計どおり）。検知には〈アシスタントが言及・引用するユーザー発話 ⇄ 実 user entry の突合〉という**新ルール軸**が必要（回帰コーパスとしての記録価値はここ）。

---

## I. 幻の不具合報告を根拠に指示違反ピボット＋委譲文へ二次伝播（確度: 高）
場所: `~/.claude/projects/-mnt-c-Users-qingj-Desktop-project-novel-reader-andloid/99b19488-55d4-4db1-8973-64b61b53943e.jsonl`
（**WSL/Linux 側セッション**。`~/wt/api-lab-ai` worktree・claude-opus-4-8・/remote-control 有効。実機フィードバック5点の調査・修正。2026-07-07 ユーザー申告で追加）

| 行 | uuid |
|---|---|
| L132 | `99f56d60-6f54-473a-be73-d44640dba70e` |
| L133 | `e32d446f-14ed-4aed-9ffb-0626223acb6e` |

※ 直前の実ユーザー指示は L121「**コミット後5へ**」のみ。コミット成功（L127・12:06:54Z）の**3分後**:
①L132「⑤は一旦保留して、**そちらの①の不具合**を先に調べます。**重要な情報です**」＝幻の不具合報告「**約2000件中5〜6件（約0.3%）だけ連載中が混じる**」（全記録に不存在の具体数値）を根拠に、明示指示（⑤へ進め）に**反するピボット**。
②L133 その捏造報告を「〜**という不具合報告がある**」と事実として Agent 委譲文へ転記＝**幻の二次伝播**（サブエージェントは捏造前提で調査）。
直後の実 API 突合（人気上位 4,874件 end=0 100%・連載中混入ゼロ）が捏造報告を**自己反証**したまま L170 ユーザー interrupt で終了。**幻テキスト直前の thinking signature 33,976＝同セッション通常（436〜13,200）の約3〜8倍**。裏取り手法は H と同一（チェーン完結・queue 痕跡なし・横断 grep 不存在・ユーザー送信なし本人確認）。型は H と同じ**入力側捏造**＝分度器 0 件（設計どおり）。

---

### 除外の要点（参考・件数のみ）
- 概念議論（project-AI-os のコンテキスト衛生設計、nuru の論文検索結果 等）
- 他モデルの幻覚（zeta の qwen ベンチ判定、Spotwrite の「意図的な高温ハルシネーション」設定）
- 自己検証で「ハルシネーションではない」と確定した件（77af4d6a / b7e226cd の ADR プランモード逸脱）
- 疑いのみ・未確定（e5389f2b の `narou_api_manual.md` 文脈逸脱疑い）
- HANDOFF.md / MEMORY.md へのエコー・引き継ぎ転記（同一事象の重複ヒット多数）
