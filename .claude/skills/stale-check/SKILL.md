---
name: stale-check
description: md・skill・hooks・settings の陳腐化を検出する（軽量=差分／フル=全網羅の2モード）。実態とのズレを確度別に報告し修正案を提示。「陳腐化チェック」「stale check」「ドキュメントやskillが古くないか確認したい」等の依頼で使う。
---

# stale-check — 管理ファイル陳腐化チェック

CLAUDE.md / STATUS.md / handover.md / task_diary.md / `docs/**`（decisions・patterns・knowledge・reference）/
`.claude/skills/` / `.claude/hooks/` / settings / `.mcp.json` が
実態（コード・ビルド設定・DBスキーマ・git）とズレていないかを点検する。

検出で終わらせず **確度高 / 要確認** に分類して報告し、**修正案（diff）を提示**する。
適用とコミットは常に人間承認（CLAUDE.md のフローに従う）。

## 2つのモード

| 呼び出し | モード | 用途 |
|----------|--------|------|
| `/stale-check` | 軽量（既定） | 前回チェック以降の差分だけ意味確認。日常的に回す。単一エージェント。 |
| `/stale-check full`（`all` / `--full` / 「全網羅」「フル」でも可） | フル | 並列エージェントで全ファイルを意味レベルまで全面照合。 |

どちらも最初に機械チェック `check_machine.py` を実行する（決定的・高速）。
モードの差は「意味チェックを差分に絞るか／全面でやるか」だけ。

## 軽量モード（既定）の手順

1. 機械チェックを実行する:
   ```
   python3 .claude/skills/stale-check/check_machine.py
   ```
   - 登録済みの機械チェックを**全件**実行し、「前回チェック以降に変わった管理ファイル」一覧の提示と、
     状態ファイル `.claude/.stale_check_state.json` の自動更新まで行う。
   - 状態記録が無い初回は「初回フォールバック」と表示される → その場合は全管理ファイルを意味確認の対象にする。
2. 出力の「前回チェック以降に変わった管理ファイル」に挙がったものだけ、下記**意味チェック観点**で精読する
   （差分が無ければ機械チェック結果のみで完了）。
3. 機械チェックの「確度高」と意味チェックで見つけたズレを統合し、**確度高 / 要確認** で報告する。
4. 各指摘に修正案（diff）を添える。承認を得てから適用・コミットする。

## フルモード（明示時）の手順

1. 機械チェックを全件実行:
   ```
   python3 .claude/skills/stale-check/check_machine.py --full
   ```
2. **Explore エージェントを3つ並列起動**して意味レベルまで全面照合する（差分に絞らない）:
   - (a) 管理md系: CLAUDE.md / STATUS.md / handover.md / task_diary.md / `docs/decisions/` / `docs/patterns/` の主張 ↔ 実コード・git
     （下記「横断検査4本」を全面適用。knowledge は検査3の該当記述のみ＝全数照合しない）
   - (b) skill系: `.claude/skills/**/SKILL.md` ↔ 実構成・DBスキーマ・コマンド
   - (c) hooks/settings系: `.claude/hooks/**` ↔ settings 登録・参照パス・git追跡
   - 各エージェントに「主張(file:line) / 実態 / 推奨アクション」を確度別で報告させる。
3. 機械チェックと3エージェントの結果を統合して報告＋修正提案する。

## check_machine.py が見る項目（機械的に確定）

**項目一覧の正本はスクリプト側の `CHECKS` 表**。照会はこれで行う:

```
python3 .claude/skills/stale-check/check_machine.py --list
```

**ここへ列挙を複製しない**——以前はこの skill に項目表を持ち、件数の一致だけを機械照合していたが、
件数しか見ないので内容のズレは素通しな上、表そのものが腐った（2026-07-12 に1項目足したとき
列挙が 13 項目のまま 13 日間放置された）。複製を無くせば腐る対象自体が消える。
機械チェックを足すときは `CHECKS` に〈関数, 1行説明〉を1行で追加する
（タプルなので説明の書き忘れは実行時に必ず落ちる＝黙って列挙が欠けない）。

## 消したファイルに言及するときの書き方（項目6 の前提・2026-07-30）

参照実在チェック（項目6）の対象は `docs/**` と `.claude/plans` 直下まで広がった。
**消えたファイルを歴史として書き残すのは正しい**ので、機械が「意図的な言及」だと分かる形で書く。
次の4つのどれかを満たせば対象外になる（満たさないと毎回ノイズとして出続ける＝検査が信用されなくなる）:

1. **同じ行**に `撤去`／`削除済み`／`現存しない`／`非収蔵` 等の断り
2. **直後の注記行**（`※…` や字下げの続き。空行・見出し・新しい箇条書きで切れるまでの3行以内）
3. **前置きの引用ブロック**（`> …`）＝最初の `##` より前なら文書全体、節の中なら次の見出しまで
4. **冒頭での名指し宣言**（「本文中の `foo.py` は当時のファイルで現存しない」＝その名前は文書全体で対象外）

`削除` 単独・`移設` 単独は効かない（過去形に限定してある。**移設を告げながら旧パスを指したままの記述こそ
検出したい対象**だから）。凍結アーカイブ（`.claude/plans/archive/`・`task_diary.md`）は最初から対象外。

## 意味チェック観点（Claude／エージェントが担当・機械化困難）

- architecture skill の記述 ↔ 実コード構成（Service名・Composable・**描画方式**。WebView→Compose のような構造変化の追従漏れ）
- skill 内部の論理矛盾（「版数は書かない」と言いつつ版数を明記、等）
- STATUS / task_diary / handover の個別エントリで状況が変わった・解決済みの記述
- `docs/decisions/` の ADR ↔ 実装（恒久決定が実装とズレていないか。例: hook 仕様変更が ADR に未反映）
- `docs/reference/frontend-design/SKILL.md` などがプロジェクト用途に対し有効か
- CLAUDE.md のルール ↔ 現行 hook / skill の実運用の整合
- `docs/known-bugs-registry.md`（既知バグレジストリ＝L4）の棚卸し: 前回以降の `fix:` コミットで
  **再発しうる機序**が新たに出ていないか／既存 ID の再発で状態（`[!]`/`[~]`/`[o]`）が実態とずれていないか
  （名指しの実在は機械チェック側が見る＝ここで項目を列挙しない）

### 横断検査4本（2026-08-06 監査で追加。軽量=差分ファイルに適用／フル=全対象に適用）

出自＝`.claude/plans/golden-and-docs-audit-2026-08-06.md` 第2部「次の投資先」。いずれも意味チェック
（機械化困難）だが、**判定材料は下記の実測コマンドで取る**（目視カウント・記憶で判定しない）。

1. **現在値数値の実測突合**: md 中の「N枚・N件・N本・N組・計Nゲート」等の**現在値を名乗る数値**を実測と突合する。
   候補の洗い出しと実測の例:
   ```bash
   grep -rnE '[0-9]+ ?(枚|件|本|組|ゲート)' --include='*.md' docs .claude/skills STATUS.md handover.md
   ls android/app/src/test/screenshots/*.png | wc -l        # golden 枚数
   grep -n '\- name:' .github/workflows/ci.yml               # CI ステップ一覧（ゲート数はセットアップ行を除いて数える）
   python3 tools/check_design_tokens.py                      # トークン照合の組数（出力の pairs=）
   ls docs/knowledge/*.md | wc -l                            # knowledge 本数
   ```
   **推奨処方は「正しい数値へ書き直す」ではなく「数値を消して実測コマンドを書く」**
   （`.claude/skills/build/SKILL.md` の golden 枚数の作法を全 md へ適用する。数値は書いた瞬間から腐るが、
   コマンドは実装が動いても正しいまま）。歴史記述の数値（「当時48枚だった」等の過去形）は対象外。
   実例: 「48枚」×2・「実測1072件」・「297組」・「計5ゲート」の5件が、いずれも書かれた直後に実態と乖離した。

2. **双方向注記**: 後の ADR・コミット・実装が**前の記述（決定・警告・前提）を解除**したとき、解除された側の
   文書にも注記が返っているか。差分に「解除・撤回・廃止・置換・復帰・済み」の類が入っていたら、
   その旧記述を grep で探しに行き、注記が無ければ指摘する。手本＝`docs/decisions/0010-narou-unmodified-handoff-custom-tabs.md`
   （更新のたび冒頭へ注記ブロックを追加）。実例＝片方向更新4件（監査 D-7/D-12/D-15/G-15）:
   ADR 0005 §C（0021 が解除を明記・0005 側に注記なし）／narou-api-discovery の Main dispatcher 警告（Mutex 化済み）／
   registry の「手動実行」（CI 結線済み）／GridStatusLineWrapTest の「D に golden 無し」（ハーネス新設済み）。

3. **実測を名乗る knowledge のコマンド併記**: 外部事実を「実測・実査・確認済み」と主張する knowledge は、
   **使ったコマンドと実行環境（ツールの有無・版）の併記が必須**。無ければ指摘する。
   実例: 「APK に .so は0本」知見（監査 D-1）は `unzip` 未導入環境で `unzip -l | grep` が空を返した偽測定だった
   （実際は8本。memory `bash-pipe-masks-exit-code-false-green` の型）。作法の手本＝
   `docs/knowledge/agp-srcdir-taskprovider-drops-builtby.md`。
   **knowledge の全数照合はしない**（監査時点で46本中4本＝9%と低汚染。1知見1ファイルで腐りが局所化している）＝
   この検査は「差分に入った knowledge」と「実測を名乗る記述」に限定する。

4. **patterns の正本ヘッダ**: `docs/patterns/*.md`（README 除く）は冒頭引用ブロックに
   `正本コード: <repoルート相対path>` 行が必須（規約と例外形＝`docs/patterns/README.md`）。
   (a) 行が無ければ指摘 (b) path が実在するか確認 (c) その path の**公開シンボル集合が文書の前提から
   大幅に変わっていないか**（object/class/fun の新設・改名・撤去。例: `ProcessingStateHub` 新設も
   `ComponentPadding` 追加も、正本1ファイルへの grep 一発で「文書が知らないシンボル」として検出できた）。
   `正本コード: —（…が正本）` の明示形は (b)(c) を免除する（手順正本パターン用）。

## 出力フォーマット

```
【類型】対象(file:line) → 主張 / 実態 / 推奨アクション
```
を「確度高（実態と明確に矛盾）」「要確認（疑わしいが断定不可）」に分類して提示する。

## 注意

- 実行環境はこのマシンでは Linux/WSL が正本。**フックは `python` で可・Bash ツールからは `python3` を使う**
  ——`~/.local/bin/python → python3` のシムが効くのは Claude Code 本体プロセス（＝そこから起動されるフック）だけで、
  **Bash ツールの PATH には `~/.local/bin` が無い**＝`python` は `command not found`（exit 127）で落ちる
  （2026-08-25 実測。この skill 自身が `python` を指示していて起動できなかった）。
  Windows では PowerShell / Bash どちらでも可。
- 状態ファイル `.claude/.stale_check_state.json` は `.gitignore` 済みのローカル状態。コミットしない。
- リナンバー禁止: task_diary.md のエントリ番号（#N）は固定IDなので、重複を見つけても自動リネームせず報告に留める。
