# Grep/Glob ツールは実行系に存在しない——真因はサーバー配信のツール構成（設定でもバージョンでも戻せない）

**★★★ / 2026-08-11 / 探索の既定ツールが消えている。ローカル要因ではないので直そうとせず `rg` へ切り替える。**

## 症状

Claude Code 2.1.227 で `Glob` を呼ぶとこう返る（`Grep` も同様）:

```
No such tool available: Glob. Glob is not available in this session
— find files with `find` via the Bash tool instead.
```

`ToolSearch` で検索しても検索系ツールは1つもヒットしない（deferred にも無い＝完全に非存在）。

## 真因の切り分け

- **対照実験で「ローカル要因」を全否定**: プロジェクト外（scratchpad）・hooks/プラグイン/MCP/CLAUDE.md
  すべて非適用・別モデル・新規セッションで headless 起動しても `NO_GLOB`。
  ⇒ このセッション固有でも、このプロジェクト固有でも、モデル固有でもない。
- **設定は無実**: user/project/local の settings・環境変数のいずれにも無効化は無い。むしろ
  `.claude/settings.local.json` は `Glob`/`Grep` を **allow** している＝使えていた頃の名残。
- **製品から消えたのでもない**: 2.1.227 バイナリに `"Glob"` 11箇所・`"Grep"` 12箇所が残存。
  定義はあるのに**セッションのツール構成を組む段階で外れている**。2.1.223/226/227 で挙動差なし
  ＝当日の自動更新（`autoUpdatesChannel: latest`）が原因でもない。
- **構成はサーバー配信**: `~/.claude.json` の `cachedGrowthBookFeatures` に 483 件のフラグ。
  うち `tengu_deferred_stub_tool = True`＝`ToolSearch` による遅延ツール機構が有効で、実際の
  ツール一覧は「常設＋deferred」の二層になっている。**変わったのは配信側**。
- **いつから**: 当リポジトリのセッションログ（`~/.claude/projects/*/`）を遡ると `Grep` の tool_use は
  **少なくとも 2026-08-01 以降ゼロ**。一時的な不調ではない。
- **未確定**: どのフラグが外しているかは特定できていない。フラグ表 `tengu_pewter_kestrel` に
  `BashSearchTool`（Bash ベース検索ツール）の枠があり、エラー文も Bash 代替を名指しするので
  「Bash で代替可能として除外」と推定されるが、当ビルドに `BashSearchTool` の実体は 0 ヒット＝断定不可。
- **`--tools` では戻せない**: 無効なツール名（`--tools NoSuchToolXYZ`）を渡しても検証されず黙殺される
  ＝built-in set に無いものは指定しても現れない。
- **LSP も代替にならない**: `kotlin-lsp` は `workspaceSymbol` で exit code 7 crash（同日実測）。

## 対処

探索は `rg`（14.1.1 導入済み）起点に統一し、出力とコマンド長の両方を絞る作法を規約化した
（CLAUDE.md「開発ルール」）。`.gitignore` 自動尊重で `-prune` の羅列が消えるのが効く——費用は
Bash 出力 20.1% より **tool_use 引数 31.1%** の方が大きいため（実測は同 CLAUDE.md）。
