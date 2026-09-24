# WSL 上の git worktree は Windows 側の git から「prunable」に見える＝prune で登録が消える

**重要度 ★★★／2026-08-17 実証／関連＝`windows-jvm-cannot-run-robolectric-native-graphics.md`**

1行要約: ブランチ用の worktree を WSL の ext4（`/home/qingj/wt/...`）に置くと、**Windows 側の git からは
そのパスが解決できず `prunable` と表示される**。この状態で `git worktree prune` を打つと**登録ごと消える**
——作業中のブランチの worktree が git から見えなくなる。実体は生きているのに、である。

## 実測（同じリポジトリ・同じ時刻）

```
# WSL から（正常）
/mnt/c/.../novel-reader_andloid  5bf47ba [main]
/home/qingj/wt/sweep-…           41bbb6b [sweep/handover-sweep-2026-08-17]

# Windows から
C:/.../novel-reader_andloid      5bf47ba [main]
/home/qingj/wt/sweep-…           41bbb6b [sweep/handover-sweep-2026-08-17] prunable   ← ★
```

## なぜ起きるか

`prunable` の判定は「`.git/worktrees/<name>/gitdir` が指すパスが存在するか」だけ。
`/home/qingj/...` は WSL の ext4 上のパスで **Windows のファイルシステムには存在しない**ため、
git は「消えた worktree」と判定する。**登録が壊れているのではなく、見ている OS が違うだけ**。

## 規律

- **Windows 側から `git worktree prune` を打たない**。`git worktree list` に `prunable` が出ても
  「古い登録が残っている」と早合点しないこと。掃除が要るなら **WSL 側から実行する**
  ——そちらでは両方のパスが解決するので誤判定しない。
- 逆向きも同型＝Windows 側にしか無い worktree は WSL から `prunable` に見え得る。
  **worktree の生存判定は、その worktree を作った側の OS で行う。**

## 併せて: このプロジェクトの worktree は WSL 側へ一本化した（2026-08-17）

一時期、Windows 側 `.claude/worktrees/` に同内容のコピーが並存していた。**両者は自動同期されず**、
しかも Windows 側は `.git` が消えた metadata を指していて git が使えない状態だったため、
「片方を編集して片方をコミットする」事故が起きかけた（実際、手動同期スクリプトで凌いだ）。

ゲートが Windows で回らない（golden の verify も record も不可＝上記の関連知見）ことと併せて、
**git 操作もビルドも WSL 側の worktree で完結させる**のが現在の運用。
Windows 側から触るときは、編集も含めて `\\wsl$\<distro>\home\...` 経由で WSL 側の実体を直接見ること
——Windows 側にコピーを作らない。
