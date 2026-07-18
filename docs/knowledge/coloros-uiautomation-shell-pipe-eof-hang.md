# ColorOS で UiAutomation シェルコマンドの完了待ちが永久ブロックする（SIGQUIT 除細動で回避）

2026-07-17・OPPO PGEM10（Android 16 / SDK 36・ColorOS）・Macrobenchmark 初回実機計測で確定。

## 症状

- Macrobenchmark（`androidx.benchmark:benchmark-macro-junit4` 1.3.4 / 1.4.1 とも）の実行が、
  シェルコマンド境界（`perfetto --background-wait` 起動・`trace_processor_shell -D` 起動など）で
  **無限に停止**する。テスト側プロセスは state=S・CPU 時間凍結・logcat 無進展。
- kill でも死なない残骸が残ることがある（SELinux が shell→perfetto ドメインへの kill を拒否
  ＝`Permission denied`。端末再起動でのみ完全掃除できる）。

## 機序（実測ベースの推定）

instrumentation は UiAutomation 経由でシェルスクリプトを実行し、その**完了（パイプ EOF /プロセス終了通知）
を待つ**。ColorOS ではこの通知が届かない——デーモン化する子（perfetto の background fork 等）が
パイプを握る等で EOF が来ず、読み取りスレッドが永久待ちになる。
**シグナルを1発送ると即座に解除されて処理が瞬時に進む**（ART の SIGQUIT ダンプは全スレッドへ
suspend シグナルを配るため、ブロック中の read が EINTR で叩き起こされる）ことを2回再現で確認
＝「処理は完了しているのに待ち側だけが眠り続ける」構図。

シェル機構そのものは健全（adb shell から perfetto の ftrace 単体・stdin 設定・FUSE 読みは全て正常完走）。
ColorOS の Hans 凍結（task_diary #38）とは別物（cgroup.freeze=0 を確認済み）。

## 回避＝除細動ループ（これで完走・508秒→計測成功）

instrument を背景起動した上で、テストプロセスへ 2 秒おきに SIGQUIT を送り続ける:

```bash
# PID は instrumentation プロセス（com.novelreader.macrobenchmark）のもの
adb shell 'while ps -p <PID> >/dev/null 2>&1; do
  run-as com.novelreader.macrobenchmark kill -3 <PID> 2>/dev/null; sleep 2; done'
```

- **`run-as` 経由必須**（shell uid からの直接 kill は app プロセスに送れない）。
  ただし `run-as … kill -0` は生存確認としては**偽陰性**を返す——生存判定は `ps -p` で行う。
- SIGQUIT はスレッドダンプを吐くだけで非破壊。**計測値は perfetto トレース内イベントから抽出される
  ため揺さぶりで汚れない**（実測: 5反復 243.7〜274.5ms と分布は正常・タイト）。
- 各シェルコマンド境界で最大2秒の死に時間が入るぶん、総実行時間は延びる（コールド起動5反復で約13分）。

## 適用範囲・前提

- Macrobenchmark の全シナリオ（起動・スクロール jank・章送り）で必要になる想定。
  ベンチ実行をスクリプト化するならこのループを標準装備すること
  （2026-07-17 `tools/run_macrobenchmark.sh` として標準装備済み＝残骸チェック・`install -r -g`・除細動ループ同梱）。
- 走行前に **perfetto / trace_processor_shell の残骸ゼロを確認**（`ps -A | grep -E "perfetto|trace_processor"`）。
  残骸の trace_processor が port 9001 を握ると新走行が版不一致応答を待ち続ける二次ハングになる。
  kill できない残骸は端末再起動で掃除。
- 関連して踏んだ前提条件2つ（同日解消・build.gradle の why コメント参照）:
  ①ColorOS は shell の `pm grant` を遮断（`GRANT_RUNTIME_PERMISSIONS` 不在）→ **`adb install -r -g`** で
  インストール時付与に切り替えて回避 ②profileinstaller は 1.4.1 必須（1.3.1 は SDK 36 非対応）。
