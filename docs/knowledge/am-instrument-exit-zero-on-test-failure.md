# `am instrument` 直叩きはテスト失敗でも exit 0 を返す（成否は出力本文で判定する）

★★★・2026-07-17・Macrobenchmark 起動予算 assert の FAIL 経路実証で実測確定。

## 症状

`adb shell am instrument -w …` を直叩きすると、**テストが AssertionError で FAIL しても
adb クライアントの exit code は 0** になる。exit code だけで成否判定すると偽 GREEN。

## 真因

`am instrument -w` の exit code は「instrumentation の実行が完了したか」を表すだけで、
テスト結果（OK/FAILURES）を反映しない。テスト結果は stdout のテキスト
（`OK (N tests)` / `FAILURES!!!` / `INSTRUMENTATION_STATUS_CODE:` / `shortMsg=`）にのみ現れる。
さらに adb 自体・パイプ経由でも exit code は潰れうる（memory `bash-pipe-masks-exit-code-false-green` と同根クラス）。

## 対処

出力をファイルに落とし、**本文マーカーで最終判定**する（`tools/run_macrobenchmark.sh` §8 が実装例＝
`FAILURES!!!`／`Process crashed`／`shortMsg=`／`INSTRUMENTATION_STATUS_CODE: -1` → FAIL、
`OK ([0-9]+ test` → PASS、どれも無ければ UNKNOWN 扱いで非 0 終了）。

実測: 予算 100ms で意図的に FAIL させた走行が
`instrument 終了（adb exit=0）→ 本文判定: FAIL` となり、exit code 判定だと緑に化けていた。

## 適用範囲

`/device-verify` §2 の androidTest 直叩き（`connectedAndroidTest` の蔵書消失回避で標準手順）にも同じ罠が
当てはまる＝am instrument を直叩きする検証すべてで本文判定を使うこと。
