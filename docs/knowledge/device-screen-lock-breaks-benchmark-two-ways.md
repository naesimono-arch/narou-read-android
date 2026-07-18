# 画面ロック（keyguard）はベンチ走行を2様に壊す — EPERM 途中死と前面不達

★★・2026-07-18・**ロック中の実機は「media 書込 EPERM で iteration 途中死」か「起動アプリが前面に来ず setup fail」のどちらかで必ず壊れる。認証ロックは adb から解除不能＝入口検査で止めるしかない。**

OPPO PGEM10（ColorOS / Android 16）で Macrobenchmark 予算 assert の連続4走行中、走行間に
画面が消灯→認証ロックして遭遇。同一セッションで両症状を実測。

## 症状（2様）

1. **走行中にロックが発生**: perfetto トレース設定の書き出し
   `Android/media/<test-pkg>/trace_config.pb` が `FileNotFoundException: open failed: EPERM` で死ぬ。
   **紛らわしい点**: 空き 61GB（容量無関係）・同一パスへ iter000/001 は正常に書けて3反復目だけ死ぬ・
   直前反復の `.perfetto-trace`（155MB/本）は正常に残る。ストレージ権限バグや容量枯渇に見えるが違う。
2. **ロック状態で走行を開始**: `startActivityAndWait` は例外を出さず完走風に通るが、アクティビティは
   keyguard の裏で前面に来られない。前面ガード（`By.pkg` 出現検証）が正しく fail する
   （ガード無しだと launcher/ロック画面を対象に計測が進む偽装死になる——ガードの価値の実証でもある）。

## 真因

`dumpsys window` で `isKeyguardShowing=true`・`mCurrentFocus=NotificationShade`・`isSleeping=true`。
ロック中は FUSE/MediaProvider 経由の `/storage/emulated/0/Android/media/` への**新規 open が EPERM に
なる**（既存 fd への書き込み継続は生きるため「途中まで書けて次の open で死ぬ」時系列になる）。
前面不達はロック画面がアクティビティ遷移を堰き止める仕様どおりの挙動。

## 対処

- **走行前に keyguard を検査して即 die**（`tools/run_macrobenchmark.sh` の「画面ロックチェック」＝
  `dumpsys window | grep isKeyguardShowing=true`）。ロック中に走らせると上記のどちらかで
  数分〜30分後に不可解な fail をするため、入口で止めて人間に解除を求めるのが最速。
- **認証ロック（PIN/パターン）は adb から解除できない**: `input keyevent KEYCODE_WAKEUP`・
  `wm dismiss-keyguard`・swipe up・`keyevent 82` を全て試して不可を実測。人間の解除が唯一の経路。
- 長い連続走行では消灯自体の抑止を推奨（設定→開発者向け→「充電中は画面を点けたまま」）。
  スクリプト側からは恒久設定を書き換えない（検証端末の状態を黙って変えない方針）。

## なぜそうなるか（教訓）

EPERM の顔をした失敗の原因候補に「画面ロック」を入れておく。とくに無人・長時間の実機走行は
「開始時は点灯していたのに走行中に消灯」で後半反復だけ死ぬため、症状が反復番号依存の
フレークに見える（今回も初見は MediaProvider の状態異常を疑い残骸掃除→再走で誤診しかけた）。
