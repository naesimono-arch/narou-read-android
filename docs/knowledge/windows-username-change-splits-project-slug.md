# Windows 側ユーザー名を変えると project slug が分裂し、auto-memory が空のまま静かに動く

**重要度 ★★★／2026-08-24 実証／関連＝`wsl-worktree-looks-prunable-from-windows.md`**

1行要約: Claude Code の `~/.claude/projects/<slug>/` は **cwd の文字列**から作られる。Windows 側の
ユーザー名を変えると slug が変わり、**auto-memory も較正データも旧 slug に取り残される**。
新 slug 側は空のディレクトリとして作られ、**エラーを一切出さずに「memory 0 件」で動き続ける**。

## 症状（2026-08-23 のユーザー名移行 qingj→naesimono で実際に起きたもの）

| | 旧 slug `-mnt-c-Users-qingj-…` | 新 slug `-mnt-c-Users-naesimono-…` |
|---|---|---|
| auto-memory | 47 件 / 220K | **0 件**（空ディレクトリだけ生成） |
| `delegation-stats.jsonl` | 1,707 行 | 無し |
| transcript | 433 本 | 1 本（移行後のみ） |

`CLAUDE.md` が必須参照として名指ししている memory（`workflow-parallel-worktrees` ほか3件）が
**まとめて dead になる**。読めないだけで例外は出ないので、セッションは正常に見える。

## 真因

slug は **cwd のパス文字列**を「`/`→`-`」で潰したもの。ユーザー名はその文字列の一部なので、
名前が変われば別プロジェクト扱いになる。

⚠️ **symlink では救えない**——`/mnt/c/Users/qingj → naesimono` を張れば旧パスでの
ファイルアクセスは通るようになるが、slug は**実際に起動した cwd の文字列**から作られる。
「パスが解決できること」と「slug が一致すること」は別問題である。ここを取り違えると、
symlink を張った時点で解決したと誤認する。

**そして実際に張られている**（2026-08-23・`dir C:\Users /AL` で `<JUNCTION> qingj [\??\C:\Users\naesimono]`）。
これは救済ではなく**延命**で、副作用の向きが厄介: 旧パス `C:\Users\qingj\…` が**実在するものとして解決し続ける**ため、
参照実在チェック（`/stale-check` の項目6）も `[ -f ]` ガードも**全部素通りする**。
つまり **slug 分裂だけが直り、旧パスを書いた記述は「壊れていないので誰も直さない」まま残る**。
2026-08-25 の点検では auto-memory 5本＋MEMORY.md に旧パス 9 箇所が生き残っていた（機械では出せず、
`grep -rn 'Users[\\/]qingj'` で初めて出た）。**ジャンクションを撤去するなら、先にこの grep を 0 にすること**
——撤去した瞬間に、それまで無害だった記述が一斉に実パス切れへ変わる。

## 対処

1. `ls ~/.claude/projects/ | grep <リポジトリ名>` で分裂を検出する（2つ以上出たら該当）。
2. 旧 slug の `memory/*.md`（`MEMORY.md` 込み）と状態ファイルを**新 slug へ移す**。
   transcript（`*.jsonl`）は過去ログなので移さなくてよい。
3. **slug を直書きしている側を追従させる**。実測で波及したのは以下:
   - フック 1本（`count_delegation_turns.py` の状態置き場）
   - 計測スクリプト 8本（`tools/measure_*.py` の走査先）
     ——過去の実測値は旧 slug 側にしか無いので、**新旧を両方走査して realpath で畳む**のが正しい
   - ドキュメント 14本・`~/.claude` と `~/.local/bin` の設定 6本（Windows パス直書き）

## 同時に壊れるもの（見落としやすい）

> **⚠️ 2026-08-25 実測＝下記3件はいずれも「移行直後の観測」で、現在は状態が変わっている**
> （機序の記録としては有効なので本文は残す）。
> ①`.android/adbkey` は**実在**（1732B・同日 12:50）②`debug.keystore` は Windows/WSL で
> **SHA-256 完全一致**（同 12:52。P620 が自動生成した版は `debug.keystore.p620-20260825.bak` へ退避済み）
> ＝コピー手順は実行してよい。**いつ誰が戻したかは追えていない**ため、ここでは観測（mtime とハッシュ）だけを記す。
> ⚠️ **照合してから動く原則は不変**——同じ事故は移行のたびに再発しうる。
> ③旧 slug 参照は**もう生きていない**: `docs/reference/hallucination-ground-truth.md` が指す jsonl 32件のうち
> 生存は9件で、**全てスナップショット（`~/.claude/hallucination-archive/`）側**。live 側は23件とも失効した。
> 原因は移行ではなく**セッションログの保持期間切れ**なので、旧 slug を書き換えても復活しない
> （証跡だから書き換えない、という扱い自体は不変）。

- **`C:\Users\<name>\.android\` が引き継がれない**＝承認済み adbkey が消える。
  `~/.local/bin/adb` は `[ -f "$WIN_KEY" ]` でガードしているので**ラッパーは壊れず**、
  代わりに**実機が未承認になる**（vendor key を提示せず起動するだけ）。実機を繋ぐまで気づけない。
- **`debug.keystore` は「消える」のではなく「作り直される」**＝壊れ方が adbkey と逆で、より危険。
  移行後に Android Studio 等が新しい鍵を生成するため（実測 mtime 2026-08-25 03:51）、
  WSL 側へ以前コピーした鍵と **md5 が一致しなくなる**。この状態で「Windows のを WSL へコピーする」という
  従来の対処（memory `wsl-debug-keystore-share-for-install`）を実行すると、端末に入っている
  WSL 鍵署名のアプリと**不一致に転じて `install -r` が壊れる**＝対処が破壊に反転する。
  照合してから動く: `md5sum /mnt/c/Users/<name>/.android/debug.keystore ~/.android/debug.keystore`
- 過去 transcript の場所を記録した資料（`docs/reference/hallucination-ground-truth.md` 等）は
  **旧 slug のまま参照が生きている**＝証跡なので書き換えてはいけない。
  同様に `/home/<linux-user>/…` は Linux 側のパスで、Windows 名の移行とは無関係。
  **「qingj を一括置換」は誤り**で、Windows パスだけを選んで直す。
