# Claude 会話ログ 実ハルシネーション・セクション一覧

過去の全 Claude Code セッション（`C:\Users\qingj\.claude\projects\**\*.jsonl`、267ファイル）を
「ハルシネーション / ハルシ / hallucinat」で横断抽出（27ファイル・約158ヒット）し、
**実際に Claude 自身が幻覚（事実の誤り／捏造）を起こしたと一次情報で確認できたもののみ**を
セクション単位で列挙する。内容説明は省き、**識別番号(uuid)と場所(ファイル・行番号)のみ**。

**確認済み実幻覚セクション: 8件（5事象）**
（A〜D＝Windows 側の一括横断抽出。E＝Linux 移行後に WSL 側で発生・ユーザー申告で追加した1事象。）

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

### 除外の要点（参考・件数のみ）
- 概念議論（project-AI-os のコンテキスト衛生設計、nuru の論文検索結果 等）
- 他モデルの幻覚（zeta の qwen ベンチ判定、Spotwrite の「意図的な高温ハルシネーション」設定）
- 自己検証で「ハルシネーションではない」と確定した件（77af4d6a / b7e226cd の ADR プランモード逸脱）
- 疑いのみ・未確定（e5389f2b の `narou_api_manual.md` 文脈逸脱疑い）
- HANDOFF.md / MEMORY.md へのエコー・引き継ぎ転記（同一事象の重複ヒット多数）
