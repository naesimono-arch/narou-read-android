# .claude/plans/archive — 役目を終えたプランの一次情報

ここに置くのは**当時の記述をそのまま凍結したもの**。読む側の前提を1点だけ明示しておく。

## ⚠ パス参照は現在のツリーと一致しない

アーカイブ内のファイルパス・クラス名・スキル名は**書かれた当時のもの**で、その後の移設・改名・削除を反映していない。
実在しない参照が多数あるが、それは腐敗ではなく**凍結の正しい帰結**——当時どう見えていたかが一次情報だからだ。
だから機械的な張り替えはしない（張り替えると「当時の記述」でなくなる）。

具体的には、少なくとも次のクラスが現在は解決しない:

- **Python 時代の資産** — `app.py` / `chapter_processor.py` / `html_exporter.py` / `test_logic.py` 等。
  2026-07-05 Phase 5 で Kotlin `pdf/` へ全面移植し Chaquopy ごと撤去済み（経緯は `/architecture`・ADR 0002）。
- **移設された Kotlin** — 例: `viewmodel/SearchDraft.kt` は現在 `domain/SearchDraft.kt`。
- **UI-n 期の候補モック** — `bookshelf-{A,B,E,F,G,H,I}.html` 等。採用されなかった候補は掃除済みで、
  現存する正本モックは `docs/design-candidates/` 配下。
- **昇格・改名された台帳** — `UI_FIXES_TODO.md` / `UI-n_DESIGN_PLAN.md` 等は ADR・`handover.md` へ吸収された
  （`UI-n_DESIGN_PLAN.md` の本体は同ディレクトリの `UI-n_DESIGN_PLAN-archived-2026-07-02.md` がその一次情報）。
- **リポジトリ外の作業ファイル** — `~/.claude/plans/*.md`・statusline のローカル版・調査時の一時ファイルなど。

## 現在値を知りたいときの行き先

現況は `STATUS.md`、やることは `handover.md`、完了の履歴は git log、判断は `docs/decisions/`
（体系の正本は `CLAUDE.md`「管理ドキュメントの体系」）。**アーカイブを現況の根拠に使わないこと。**
