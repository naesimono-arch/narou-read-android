# ColorOS は broadcast を2様に「沈黙不達」にする（result=0 の正常完了に化ける）

2026-07-17・OPPO PGEM10（ColorOS / Android 16）で Macrobenchmark 用シーダー
（`app/src/benchmark` の `LibrarySeedReceiver` へ ordered broadcast）を組んだ際の実測。

## 症状

送信は例外なく完了し `Broadcast completed: result=0`（resultData 無し）が返るが、
**Receiver は一度も実行されていない**。AMS ログ上は Enqueue まで成功しており、配達段で静かに落ちる。
エラーも警告も（通常のフィルタでは）出ないため、resultCode 検証を入れていないと空シードのまま
計測が進む偽 PASS になる。

## 2つの遮断機序（いずれも実測で確認）

1. **背景アプリ発 × 対象プロセス dead**: テストアプリ（背景 uid）からの明示コンポーネント宛
   broadcast は、dead な対象プロセスの起動を伴う配達が遮断される（ColorOS の自動起動制限）。
   `FLAG_INCLUDE_STOPPED_PACKAGES` を付けても不達（stopped state の問題ではない。
   `dumpsys package` は stopped=false のままだった）。
2. **対象プロセス生存 × HANS 凍結**: プロセスが生きていても、背景滞在で
   OplusHansManager（凍結管理）が凍結した後は **shell 発の broadcast すら**配達スキップされる
   （logcat: `OplusHansManager: … enter SM` の後、Enqueue→4ms 後に result=0 完了）。

## 成立する唯一の条件と処方

**「dead（＝非凍結）状態への shell broadcast」だけが確実**（AMS がプロセス起動込みで配達する）。
よって受信側アプリへ確実に broadcast を届けたいときは:

```
am force-stop <package>          # dead＝非凍結を決定論化
am broadcast --include-stopped-packages -n <package>/<ReceiverClass> -a <action> …
```

- テスト内からは `UiDevice.executeShellCommand` で同列を実行（app-to-app の
  `sendOrderedBroadcast` は上記①で不成立）。shell 実行のハングリスクは
  [[coloros-uiautomation-shell-pipe-eof-hang]] の SIGQUIT 除細動ループが前提。
- **受信の検証を必ず resultCode で行う**（ordered broadcast の setResultCode に件数等を載せ、
  期待値不一致で fail）。「送信成功」は配達の証拠にならないのが本知見の核心。

実装現物＝`macrobenchmark/…/BookshelfScrollBenchmark.kt` の `seedLibrary`。
経緯の一次情報＝`.claude/plans/macrobenchmark-kickoff-2026-07-17.md` ②節。
