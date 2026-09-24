# ColorOS の OEM インストーラが adb からの APK 投入を止める（2026-08-19 PGEM10 実測）

マクロベンチを回そうとして**計測対象アプリを投入できず止まった**。`adb install` が失敗ではなく
**無応答**になるのが特徴で、原因を「WSL の adb が遅い」「APK が壊れている」と誤読しやすい。

## 症状と実測

- `pm list packages` に計測対象（`com.novelreader.benchmark`）が無い。APK のビルド自体は成功している。
- `adb install -r -g` が **5分超で無応答**。`adb push` + `adb shell pm install -r -g` に切り替えると
  **転送は 5.7s / 2.7MB/s で正常に終わるのに `pm install` が180秒タイムアウト**＝転送路の問題ではない。
- 前面を見ると `com.oplus.appdetail/.model.guide.ui.InstallGuideActivity`、再試行後は
  `.modelv2.parsing.PackageParsingV2Activity` が**確認待ちで居座っている**。
- `settings get global verifier_verify_adb_installs` は既に **0**＝**AOSP 側の検証は無効なのに止まる**
  ＝門は OPPO のインストーラで、画面上の承認を待っている。
- そのとき端末は `deviceLocked=1` / `trustManaged=1` / `mWakefulness=Dozing`＝**資格情報ロック中**で、
  adb からは解除できない（承認する手が無い）。

## なぜ自動化で抜けられないか

`verifier_verify_adb_installs=0` は AOSP の検証だけを切る設定で、**OEM インストーラの確認画面には効かない**。
承認は画面操作でしか通せず、ロック中は画面操作自体が塞がれている。**adb 側の工夫では抜けられない。**

## 人間が要る2手（これだけで以後は自律で回る）

1. 端末のロックを解除し、**画面点灯を維持する**（開発者向けオプション→「充電中は画面をスリープしない」＋充電接続）。
2. **adb インストールの確認ダイアログを承認する**。

⚠️ 加えて `tools/run_macrobenchmark.sh` は **keyguard 検出で入口 die する**（`isKeyguardShowing=true` を実測）
＝投入できてもロック解除は別途要る。

## 併せて確認できたこと

ベンチのシード（`clearAndSeedLibrary` の全消し）は `applicationIdSuffix ".benchmark"` の**別パッケージ側にしか届かない**
（`android/app/build.gradle` と `ImportBenchReceiver.handleClear` で確認）＝**実蔵書には触れない**。

## 解消（2026-08-19）— 人間の2手は実際に効いた

上の2手（ロック解除＋画面点灯維持／確認ダイアログの承認）を人が踏んだ後、
**`adb install -r -g` が OEM ダイアログ無しで通るようになった**（`app-benchmark.apk`・`macrobenchmark-benchmark.apk` とも）。
一度承認が通れば以後の再投入は詰まらない＝**恒久的な壁ではなく、初回投入時だけの門**。

## ⚠️ 併せて踏んだ罠 — 端末上のテスト APK が古いまま残る

壁を越えた直後に判明。端末に残っていたベンチ APK は **2026-08-06 のもの**で、
現ブランチの変更を一切含んでいなかった。**投入できなかった期間に「残置されているから使える」と考えると、
古い APK に対して計測してしまう**（数値は出るが意味が無い）。
⚠️ **計測の前に必ず `lastUpdateTime` を見て、測りたいコミットのビルドが載っているか確かめること。**

