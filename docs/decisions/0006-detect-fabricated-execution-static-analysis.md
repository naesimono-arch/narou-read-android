# 0006. 実行捏造ハルシネーションのトランスクリプト静的解析検知（スコープと Why-not）

- ステータス: Accepted
- 日付: 2026-07-05
- 関連実装: `.claude/hooks/detect_fabricated_execution_core.py`（エンジン）／`.claude/hooks/analyze_transcript.py`（CLI 事後アナライザ）／`.claude/hooks/test_detect_fabricated_execution.py`（単体テスト）
- 関連コミット: `ffe5b35`（エンジン＋テスト）／`1beff15`（CLI）／`5059a7f`（精度改善＋Tier A3）
- 検証データ: `docs/reference/hallucination-ground-truth.md`（既知の実ハルシネーション正解データ・4事象）

## Context（背景）

Claude が地の文（assistant の `text` ブロック）で「テストを実行して通った」「コマンドを走らせた」等の**実行報告**をするが、それを裏付ける本物の `tool_use`/`tool_result` ペアがトランスクリプト（JSONL）に存在しない — ＝**実行の捏造・未検証の完了主張**というハルシネーションが実際に発生していた（`docs/reference/hallucination-ground-truth.md` 事象 D・c2e7a254 等）。これを機械的に検知したい。

構造的に検知可能なのは、Claude Code のアーキ境界による：**`tool_result` ブロックはハーネスが著者／地の文はモデルが著者**であり、捏造は必ず text ブロック内の作文に留まる（対応する `tool_use`/`tool_result` が JSONL に生成されない）。よって検知は意味理解ではなく `tool_use.id ↔ tool_result.tool_use_id` の 1:1 照合に還元できる。

## Decision（決定）

トランスクリプト JSONL を静的解析する検知器を、**純ロジックのエンジン＋薄いアダプタ2つ（CLI／将来の Stop フック）**で実装する。中核方針と主要判断は以下。

1. **assistant の text は一切証拠にしない**。text は「主張の抽出元」にのみ用い、真偽の証拠は `tool_result`／`toolUseResult`／ユーザ人間入力／サブエージェント JSONL のみ。捏造は定義上 text 内にしか無いという構造を前提にする。

2. **精度最優先（低再現率を許容）**。曖昧なものはフラグせず、確証が持てない場合は `suppressed`（降格）として Stop ブロック対象から外す。実データ検証で偽陽性 34→1（真陽性）まで絞り込み、この方針の妥当性を確認した。

3. **検知の3層**（`docs/reference/hallucination-ground-truth.md` 検証で確定・調整）:
   - **Tier A1**: 端末風フェンス出力なのに同一発話に `tool_use` が無く証拠にも由来しない。
   - **Tier A2**: git 文脈で存在しない commit SHA を断言（ファイルパス・行番号は偽陽性源なので**対象外**）。
   - **Tier A3**: ハーネス専用ブロック（`user<background-task-status>` / `<task-id>` / `<exit-code>` / `<invoke name=` 等）の**地の文化**。正解データ検証で判明した最重要ケース（Claude が偽の会話継続・偽のツール呼び出しを自作）に対応して**後から追加**した。
   - **Tier B**: テスト成功の断言に対応する成功実行がセッション内に無い。**具体値主張（「28件 OK」等）はその具体値が実出力に在る時だけ裏取り**し、汎用の過去実行では免罪しない（事象 D の偽陰性対策）。

4. **Tier C（意味照合・LLM ジャッジ・ML）はスコープ外（Why-not）**。正解データの事象 A（存在しないユーザ指示の捏造引用）・B（生成 HTML の id 重複）・C（実在しない GitHub リポ調査の捏造）はいずれも「実行捏造」ではなく帰属誤り・コード不具合・外部リサーチ捏造という別クラスで、静的照合では扱えない。これらを取るには帰属根拠照合・コード正当性・リサーチ根拠照合という別系統の検知器が要る。本検知器は**構造的に高精度で取れる一クラスに集中**し、他クラスには手を出さない。

5. **Stop フックは advisory 注入ができない（Why の帰結）**。PostToolUse と違い Stop フックは `additionalContext` を持たない（task_diary #28）。よってライブゲートは「**ブロックして自己修正させる**」か「**素通し**」の二択に割り切る。ヒューリスティック起因の偽陽性で作業を止める危険があるため、**ブロックは Tier B の三証人一致（成功 tool_result なし ∧ センチネル不在/古い ∧ 非降格）で `confidence≥0.8` のみ**。降格・低信頼シグナルは CLI 側のレビューに委ねる。

6. **段階導入**: CLI 事後アナライザを第一デリバラブルとし（非ブロック・配線ゼロ）、実データで精度を検証してから Stop フックを配線する。

## Consequences（帰結・既知の盲点）

- **既知の盲点（＝降格 or 取りこぼし）**:
  - **オフロード truncation**: 大容量出力は `<session>/tool-results/<id>.txt` に退避され本文が 2KB プレビューに置換される。全文を解決できない時は該当 finding を `suppressed=truncation` で降格する。
  - **サブエージェント委譲**: 委譲先の実行実体は `<session>/subagents/agent-<id>.jsonl` にあり、読めない時は関連 Tier B を降格し blind_spot に記録。
  - **Tier B の「早期実行による免罪」**: 具体値主張は具体照合に限定して緩和したが、汎用主張は「セッション内に成功実行が1回でもあれば免罪」のままで、汎用の後半捏造は取りこぼしうる。
  - **クロスセッション参照**: 別セッションで実行した結果を要約参照する主張は、当該トランスクリプト単体では裏取りできず偽陽性化しうる（Stop フックは現行セッションのみ対象なので実害小）。
  - **PowerShell 経由・ブランチ跨ぎ**: Stop フック配線は `matcher` の制約と、settings.json がブランチ追従しない一方フック実体は追従する非対称（ADR 0004）を継承する。実体は transcript 不在・例外で常に `exit 0`（非妨害）。
- **配置**: エンジン・アダプタ・テストは repo `.claude/hooks/`（`test_hooks.py` 方式の回帰をチーム共有・CI 化するため）。CLI は機械固有パスを引数化して repo-safe。大ファイルは既定 8MB 超をスキップ＋明示警告（WSL/drvfs での CPU・メモリ爆食いを防ぐ実害対策）。
- **正解データでの再現率**: 4事象中スコープ内は D のみで、D はセッションを検知（告白文経由）。A/B/C は設計どおり非検知。詳細は `docs/reference/hallucination-ground-truth.md`。
