# Claude Code に「条件付き強制」の口は無い——permissions は宣言的・hooks の `if` は fail-open

2026-08-06 一次ソース照合（code.claude.com/docs/en/settings・同 /en/permissions）。
migration ガード強化（`.claude/hooks/block_destructive_migration.py`）の方式選定時に確定した事実。

- **permissions ルールは `Tool(specifier)` 形式の宣言的文字列のみ**。`Bash(command:...)` の内容照合は
  公式が「compound command で迂回可能」と明示して**意図的に無視する**仕様＝内容ベースの deny をここへ置いても効かない。
- **hooks 側には `if` フィールドが実在する（v2.1.85+）が、公式に「best-effort・パース不能時は fail-open。
  強制は permission システムで」と明記**——つまり両者とも「条件式で強制ブロック」の受け皿ではない。
  さらに hooks `if` は AND 複合条件（トークン×書込み×拡張子）を表現できない。
- **帰結**: コマンド内容に基づく強制ブロックは**フック本体の Python 判定に書くしかない**。
  素朴な部分文字列一致は `FOO=1 cmd`（env-prefix）・`$()` 組立で真正にすり抜ける（テストベクタで実証済み）＝
  正規化派生テキスト（env 代入展開・クォート除去）＋断片ペア照合が現実解。
  回帰は `.claude/hooks/test_block_destructive_migration.py`（stdin 直叩き 16 ベクタ）が守る。
- **理論限界**: `printf '%s' 断片` のさらなる分割・base64 難読化は静的検知不能（実行せず判定する以上の壁）。
  ここを守るのは検知強化ではなく運用（破壊的 migration は捨て本・`/db-migration` ゲート）。
