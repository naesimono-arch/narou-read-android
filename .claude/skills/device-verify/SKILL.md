---
name: device-verify
description: 実機検証の入口。adb接続(WSL)・APK投入・androidTest実行・実機DB確認・OPPO/ColorOS固有の壁の回避作法を網羅する。
when_to_use: 「実機で検証したい」／「adbで操作したい」／「androidTestを実機で実行」／「実機のDBを確認したい」／「APKをインストールしたい」 などの依頼で使う。
---

# 実機検証の作法（OPPO PGEM10 / ColorOS / WSL）

実機 = PGEM10（Android 16 / ColorOS）。**事実の正本は `task_diary.md`（#N は固定ID）と
memory `workflow-autonomous-device-verification`**。このスキルは操作手順の入口に徹する。

## 0. 実機を触る前に — `adb-bridge` を一発

WSL2 は USB を直接認識しない。**必ず最初に `adb-bridge`** を実行する（PATH 済・冪等:
未接続なら Windows `adb.exe` 経由で tcpip 化→wlan0 IP へ connect、接続済みなら確認のみ）。
以後は素の `adb …`（`~/.local/bin/adb` ラッパー＝Windows の承認済み鍵を vendor key 提示・
鍵ローテーション自動追従）で操作する。

- **禁止**: PATH に `platform-tools` を前置きしない（素の `adb` が生 adb に化けて実機を見失う）。
- IP は DHCP で変動するためハードコードしない。
- TCP 全滅時（端末スリープ/WiFi落ち/IP変動）は `adb.exe`（Windows interop）へフォールバック。
  WSL ビルドの APK は ext4 にあり `adb.exe` から読めない → `/mnt/c` へ cp → `wslpath -w` で渡す。
  詳細は memory `workflow-autonomous-device-verification`。

### WiFi(tcpip) の能力範囲 — 「ケーブル非接続」≠「操作不能」（2026-07-07 実測）

- **WiFi(tcpip 5555) は USB adb と能力等価**。adb の認可はトランスポートでなく RSA 鍵ベース
  （`ro.adb.secure=1`・承認済みのこの PC のみ接続可）のため、WiFi だけで install・アプリ起動
  （monkey/am）・logcat・dumpsys・input 注入・screencap・`run-as` の DB 読取・force-stop まで
  **全て可能**（2026-07-07 に install→monkey 起動→クラッシュ調査→DB pull の全工程を WiFi のみで実測。
  そもそも WSL adb は常に WiFi 経由＝過去の実機検証も全て同経路）。
- **できないこと（adb の外）**: fastboot/bootloader/recovery 系は USB 専用。root 化も不可
  （`ro.debuggable=0` の user ビルド＝これは USB でも同じ）。
- **tcpip モードは端末 reboot まで開きっぱなし**。再起動で消えたら USB 接続時に `adb-bridge` が再発行。
- **含意（無断操作の実績あり）**: 接続中でも ColorOS に通知等の可視インジケータは出ない（実測ゼロ）
  ＝ユーザーが端末を使用中でも、並行セッションから気づかれず install・起動できてしまう。
  2026-07-07 に並行セッションの検証起動が「ゲーム中にアプリが勝手に展開→クラッシュ」として体感された。
  **実機へ install・起動・input する前に、ユーザーが端末を使用中の可能性を考慮すること**
  （深夜・長時間の自律検証では PushNotification で予告するのが安全）。

- **上書きインストール（`install -r` / `installDebug`）で蔵書DBを保持**する。署名は Windows の
  `debug.keystore` を `~/.android` へコピー済みで一致（memory `wsl-debug-keystore-share-for-install`）。
  **uninstall は最終手段**。
- **禁忌: `connectedAndroidTest` の直叩き**。AGP 既定で run 後にアプリ本体＋テスト APK を
  自動 uninstall し、**蔵書DB等の実データが消える**（task_diary #36。実際に消えた実績あり）。
  やむを得ず使う場合は `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true` を必ず付ける。

## 2. androidTest の実行（uninstall 回避手順）

```bash
# ビルド＆投入（Bash ツールでは gw が使えない → /build スキルの「Bashツール素起動」節を参照）
gw --init-script /home/qingj/ext-build/novel-reader-init.gradle installDebug installDebugAndroidTest
# 実行は connectedAndroidTest ではなく am instrument 直叩き（アンインストールが起きない）
adb shell am instrument -w -e class <テストFQCN> com.novelreader.test/androidx.test.runner.AndroidJUnitRunner
```

- 実 PDF 資産（`src/androidTest/assets/spike/`）は gitignore＝bring-your-own。
  `sample_pdfs/` と `ab-review/golden_regression/` から配置する。

## 3. 実機 DB の確認

```bash
adb shell run-as com.novelreader ls databases/
# novel_reader_db + -wal + -shm の3ファイルを必ず全部 pull（WAL に未チェックポイント分が残るため）
adb shell run-as com.novelreader cat databases/novel_reader_db > /tmp/…/db      # 3ファイル分繰り返す
sqlite3 /tmp/…/db "SELECT …"
```

## 4. OPPO/ColorOS 固有の壁（詳細は task_diary の該当 #）

| 症状 | 対処・参照 |
|------|-----------|
| CPU 集中プロセスが数分で強制 kill（logcat: `abnormal fg_cpu`・`o-kill(502)`。**OOM ではない**） | #37。素の androidTest は無防備。超長編の検証は前景サービス経路で行うか PDF を切詰める |
| 素の androidTest が超長編抽出中に no progress でハング（logcat: `OplusHansManager … F stay=` / CPU時間が凍結）。**kill ではなく freeze** | #38。Hans フリーザが background 扱いの instrumentation を凍結（#37 の kill とは別・操作/充電無関係）。回避=`adb shell monkey -p com.novelreader -c android.intent.category.LAUNCHER 1` で MainActivity を前面化し perceptible 化（%CPU 0→250% へ復帰し完走） |
| FGS + WakeLock でもバックグラウンドで停止 | #4 追補＝**端末設定「バックグラウンドアクティビティを許可」ON でも Hans が背面数秒で凍結**（2026-07-14 実測。正本=`docs/knowledge/coloros-hans-freezes-fgs-despite-bg-allow.md`）。背面完走は保証不能＝#38 の monkey 前面化で回避 |
| バッテリー最適化除外の画面遷移が誤動作 | #5（`ACTION_APPLICATION_DETAILS_SETTINGS` を使う） |
| 通知が表示されない | #2（ContentIntent 必須） |

## 5. シェル・パスの罠

- **Git Bash（Windows 側）**は `/sdcard` 等の device パスを MSYS が変換して push/pull/dump を壊す
  （#25）。PowerShell ツールか `MSYS2_ARG_CONV_EXCL` 前置きで回避。WSL の Bash ツール＋
  Linux adb ラッパーなら非該当。

## 6. テキスト入力は ADB Keyboard（フリック座標タップ禁止）

端末に **ADB Keyboard（`com.android.adbkeyboard/.AdbIME`）が導入済みで、これが既定 IME**
（2026-07-16 確認）。日本語を含むテキスト入力は、画面のフリックキーボードを座標タップで
打とうとせず（過去に多数のエージェントがこれで苦戦）、broadcast で直接注入する:

```bash
# 入力欄にフォーカスがある状態で（AdbIME がアクティブな時のみ効く）
adb shell am broadcast -a ADB_INPUT_TEXT --es msg 'ASCII text'
# 日本語などマルチバイトはシェルのエスケープ事故を避けて B64 経由が確実
adb shell am broadcast -a ADB_INPUT_B64 --es msg "$(printf '%s' 'かな漢字テキスト' | base64 -w0)"
adb shell am broadcast -a ADB_CLEAR_TEXT   # 入力欄クリア
```

- **`adb shell input text` は使わない**: ASCII 限定な上、IME 状態を狂わせる副作用を実測
  （2026-07-16: SAF picker の「追加」ボタンが無反応化。復旧は `ime set` で切替）。
- 検証の都合で IME を切り替えたら、終了時に必ず既定へ戻す:
  `adb shell ime set com.android.adbkeyboard/.AdbIME`
  （現在値の確認は `adb shell settings get secure default_input_method`）。

## 7. 検証ワークフロー（人間の関門）

Claude が adb を自律駆動する（install / logcat / input / screencap / DB 確認・不具合はその場で
自律デバッグ）。**報告は必ず実際のコマンド出力に基づくこと（捏造禁止）**。
CP（コミット）1つ分の検証を終えるごとに一旦停止し、ユーザーへ目視ダブルチェックを依頼してから
次へ進む（memory `workflow-autonomous-device-verification` / `workflow-notify-each-step-visual-check`）。
