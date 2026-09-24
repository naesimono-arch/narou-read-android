# Baseline Profile の `targetAppId` は自動注入されない（2026-09-02・AGP/plugin 1.4.1 実測）

**症状**＝`:app:generateReleaseBaselineProfile` が失敗するが、Gradle の出力に**Java 例外もスタックも
アプリのログも出ず、`Process crashed.` の1行しか出ない**。原因の手掛かりがゼロなのが最悪の点。

## 真因

**`targetAppId`（macrobenchmark が計測対象を知るための instrumentation 引数）は、
`androidx.baselineprofile` プラグインが自動で入れてくれない**。自分で渡す必要がある。

さらに**出所を間違えやすい**——`variant.testedApplicationId` を使うと、返るのは
**`<applicationId>.baselineprofile`＝テスト APK 自身**になる。producer モジュールはプラグインによって
**self-instrumenting** にされるためで、`com.android.test` の直感（「tested＝計測対象」）と食い違う。

誤った ID を渡すと macrobenchmark は**自分のプロセスを force-stop** する。だから例外もログも残らず
`Process crashed.` だけになる。⚠️ この症状は**手動 `am instrument` に誤値を渡すと再現できる**
（切り分けはこれで確定させた）。

## 正しい形

producer の `build.gradle` で **`testedApks` を `BuiltArtifactsLoader` で読み**、そこから
`applicationId` を取って instrumentation 引数へ渡す。加えて generator 側に
**「受け取った ID が自分自身を指していたら、値を添えて即座に落とす」ガード**を置く
——ここで落とせば `Process crashed.` ではなく原因の書かれたメッセージが出る。

## 併せて踏む危険: `useConnectedDevices = true`

これは「**接続中の全端末**へ `nonMinifiedRelease` を撒く」意味になる。`ANDROID_SERIAL` を指定せずに
生成タスクを回すと、開発機に繋がっている実機にも撒かれる（別 `applicationId` なので実蔵書のような
データは無傷だが、撒かれること自体が事故）。⚠️ **`ANDROID_SERIAL` は AGP の
`DeviceProviderInstrumentTestTask` が `System.getenv` で読んで device provider へ渡す**
（実装を逆アセンブルで確認・daemon への env 伝播も probe で実証）ので、env で対象を固定できる。
ただし**人手の作法に頼る形は残さない**のが正しい＝設定段階のガードか Managed Device 化へ寄せる。
