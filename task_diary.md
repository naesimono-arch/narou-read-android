# 開発知見メモ

> **重要度凡例**: ★★★ Critical（バグ/クラッシュ/動作不可に直結） ★★ Important（特定条件下で問題発生）
>
> **本ファイルの役割**: 外部プラットフォームの事実・落とし穴（Android/OEM/Chaquopy/pdfminer/Gradle/Room 等、**コードと無関係に真**で「はまったら引く」もの）の引き場。ほぼ腐らない知見に絞る。
> - 本アプリ固有の**実装パターン**（コードが正本）→ `docs/patterns/`
> - **設計判断・Why-not**（なぜその代替を採らなかったか）→ `docs/decisions/`（ADR）
> - 旧 Part II / Part III はそれぞれ上記へ移設済み。固定ID（`§N`）の対応は末尾の「移設マッピング」を参照。
>
> ※ 各エントリ番号（#1〜）は他文書・表内参照（`§N`）の固定IDのため、リナンバーしないこと。

---

## Part I — 外部プラットフォームの事実・落とし穴（はまったら引く / コードと無関係に真）

### Android — 通知 / ForegroundService / バックグラウンド

#### 1. 通知アイコンはアプリ固有リソース必須  ★★★

`android.R.drawable.*` などシステムドローアブルは Android 5以降の通知アイコンに使用不可。
`startForeground()` が例外を投げてサービスごとクラッシュする（通知も出ない）。

**対策**: `res/drawable/` に白単色シルエットの vector drawable を作成して使う。

---

#### 2. OEMによってはContentIntentがないと通知をブロックする  ★★★

OPPO/ColorOS など一部OEMは `setContentIntent()` がない通知を表示しないことがある。

**対策**: 全通知に `setContentIntent(openAppIntent())` を付与する。

---

#### 3. API 34以降はstartForegroundに型指定が必要  ★★★

```kotlin
ServiceCompat.startForeground(
    this, NOTIFICATION_ID, notification,
    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
)
```

---

#### 4. OPPO/ColorOSのバックグラウンド制限はForegroundService + WakeLockでも不十分  ★★

Android標準の `startForeground()` + `PARTIAL_WAKE_LOCK` だけでは ColorOS がプロセスを
数秒で強制停止する。根本的な解決はデバイス側の設定変更が必要。

**設定パス**: 設定 → バッテリー → アプリごとの消費管理 → 対象アプリ → バックグラウンドアクティビティを許可

---

#### 5. ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS はOPPOで誤動作する  ★★★

`Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` がファイルピッカー等に
誤ルーティングされる。

**対策**: `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` でアプリ詳細設定を開き、
ユーザーに手動で設定してもらう。

---

#### OPPO/ColorOS 固有まとめ

| 症状 | 参照 |
|------|------|
| 通知が表示されない | §2（ContentIntent必須） |
| バッテリー最適化除外の画面遷移が壊れる | §5（ACTION_APPLICATION_DETAILS_SETTINGS を使う） |
| FGS + WakeLockでもプロセスが停止する | §4（根本解決はユーザー設定のみ） |

---

### URI / パーミッション

#### 6. content:// URIをServiceに渡す際はFLAG_GRANT_READ_URI_PERMISSIONが必要  ★★★

ActivityでPickしたURIをそのままServiceのIntentに渡すと SecurityException が発生する。

```kotlin
intent.data = uri
intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
```

---

### Jetpack Compose / State管理

#### 7. Composableの状態変数は参照する前に宣言する  ★★

ラムダ内で `showBatteryOptDialog = true` のように参照する変数は、
そのラムダより**前**に `remember { mutableStateOf(...) }` で宣言しないと
`Unresolved reference` コンパイルエラーになる。

---

#### 8. SharedPreferencesをリアルタイムに反映するには mutableStateOf を使う  ★★

```kotlin
// NG: 初期値が固定されて更新が反映されない
val flag = remember { prefs.getBoolean("key", false) }

// OK: Stateとして保持し、prefs更新時に同時にStateも更新する
var flag by remember { mutableStateOf(prefs.getBoolean("key", false)) }
// 保存時:
prefs.edit().putBoolean("key", true).apply()
flag = true  // ← これがないと再起動するまで反映されない
```

---

#### 9. BackHandler の多段階戻り設計  ★★

複数画面（例: 章 → 目次 → 本棚）を単一 Composable で管理する場合、
`isIndex` のような状態フラグで排他制御して BackHandler を2つ重ねる。

```kotlin
BackHandler(enabled = !isIndex) { currentFile = "index.html" }  // 章 → 目次
BackHandler(enabled = isIndex)  { onNavigateToBookshelf() }     // 目次 → 本棚
```

両方 `enabled = true` にならないよう排他制御が必須。どちらも enabled の場合、後に宣言した方が優先される（予期しない遷移の原因になる）。

**現行実装（Phase 3）との関係**:
現在の `NativeReadingScreen` は BackHandler を使っていない。戻り操作の実装は以下の通り:
- 本棚への戻り → TopAppBar の戻るボタンが `navController.popBackStack("bookshelf", false)` を呼ぶ
- 章/目次の切り替え → `currentFile` state を変更するだけ（BackHandler 不使用）

多段階 BackHandler は将来の章履歴スタック導入時の候補としてコード内にコメントアウトで残存。

---

#### 10. Compose LazyColumn で lineHeight を複数Composable間に一貫適用する  ★★

`lineHeight = 2.5.em` などの行高は**単一 BasicText 内の折り返し行間**には適用されるが、
LazyColumn 上の**別Composable間の間隔**には自動適用されない。

bottom trailing leading（lineHeight の下余白分）がCompose バージョンによってsingle-line
BasicTextのcomposable高さに含まれるかどうかが未定義のため、行間が0〜27spと予測不能になる。

**対処パターン:**
```kotlin
val bodyStyle = TextStyle(
    fontSize = 18.sp,
    lineHeight = 2.5.em,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Proportional,
        trim = LineHeightStyle.Trim.LastLineBottom, // 高さを確定させる
    ),
)
// 各テキスト段落に明示的な下余白を追加
RubyText(
    style = bodyStyle,
    modifier = Modifier.padding(bottom = 14.dp), // 間隔を明示制御
)
```

`Trim.LastLineBottom` で composable 高さ = 上leading + fontSize に確定させ、
`padding(bottom)` で意図した間隔を明示的に付与する。

---

### Chaquopy / Python統合

#### 11. Chaquopyで使えるのは純Pythonパッケージのみ  ★★★

C拡張を含むパッケージ（PyMuPDF/fitz等）はChaquopyのpipがWindowsホスト上でクロスビルドを試みるが、
MSVCがなければビルド失敗。`pdfminer.six`（純Python）のような代替を選ぶこと。

---

#### 12. Python → Kotlin コールバックは fun interface（SAM）を使う  ★★

Chaquopy 15.0.1 では `fun interface` を Python から直接 `callback(percent, phase)` として呼び出せる。
IOスレッドから呼ばれるが `MutableStateFlow.value =` への代入はスレッドセーフ（`withContext(Main)` 不要）。

---

#### 13. Chaquopyのcallattr()はキャンセル不能 → NonCancellable必須

`callAttr()` は JNI の同期ブロッキング呼び出しのためコルーチンキャンセル不能。
Python処理 + DB登録を `withContext(NonCancellable)` でラップし、キャンセル不能であることを明示。
`ensureActive()` は NonCancellable ブロックの**外**で呼ぶこと（内側では機能しない）。

---

#### 14. Chaquopyの例外はPyExceptionにラップされる → クラス名で判定  ★★

Python で `raise EncryptedPdfError("...")` すると、Kotlin側では `PyException` としてラップされ、
`e.message` は `"builtins.EncryptedPdfError: ..."` のようにクラス名が先頭に含まれる。
`e is PyException && e.message.contains("クラス名")` で安全に判定できる。
マーカー文字列方式（`ERROR_ENCRYPTED:` 等）はスタックトレース全体を含む文字列になるため誤検出リスクあり。

---

#### 15. pdfminer.six の Y軸は下原点（PyMuPDF と逆）

- PyMuPDF: 上原点（`top` = ページ上端からの距離）
- pdfminer: 下原点（`y0`, `y1` = ページ下端からの距離）

変換: `top = page_height - y1` で既存ロジックをそのまま再利用できる。

---

#### 16. callAttr() 引数ミスマッチは無音失敗する  ★★★

`callAttr()` は引数の型チェックをしない。Python関数定義の引数の数と完全に一致させること。
ミスマッチ時は Python 側で `TypeError` が発生するが、Chaquopy は PyException を logcat に自動出力しない。
`runCatching + classifyError()` パターンが `Log.e()` なしで例外を吸収すると原因が永遠に見えない。

**対策**:
- `runCatching` を使う箇所には必ず `onFailure` 内に `Log.e()` を入れる
- logcat に E レベルのアプリエラーが出ない場合、「例外がどこかで catch されてログ出力されていない」を疑う

---

### ビルド設定（AGP / Gradle / Compose BOM）

#### 17. AGP と Gradle のバージョン互換性マトリクス

| AGP | 対応 Gradle |
|-----|-------------|
| 8.1.x | 8.0〜8.1 |
| 8.6.x | 8.7+ |

Gradleダウングレードより**AGPアップグレード**の方がAndroid Studioのキャッシュ問題を回避できて確実。
現在の構成: AGP 8.6.1 + Gradle 8.9 + Kotlin 1.9.22 + Compose Compiler 1.5.10 + Compose BOM 2024.04.01。

---

#### 18. gradlew はリポジトリに必ずコミットすること  ★★★

`gradlew` / `gradlew.bat` / `gradle-wrapper.jar` が未コミットだとCLIビルド不可（Android Studioは動くが紛らわしい）。
生成コマンド: `gradle wrapper`（Gradleのローカルインストール or `~/.gradle/wrapper/dists/` のキャッシュが必要）。

---

### 19. プロジェクトパスに非ASCII文字があるとビルドが拒否される（Windows）  ★★★

配置パスに日本語等の非ASCII文字（例: `Desktop\開発\...`）が含まれると、AGPが
**コンパイル開始前**に `Your project path contains non-ASCII characters` で `BUILD FAILED`。
Kotlinエラーではないため原因が分かりにくい。

**対策（推奨）**: プロジェクトをASCIIのみのパス（例: `Desktop\project\...`）へ配置する。
**回避策**: `gradle.properties` に `android.overridePathCheck=true`。ただし公式に
「Windowsでビルド失敗の可能性大」と警告される道なので、配置変更が本筋。

---

### 実機検証 / adb（Windows）

#### 25. Git Bash は adb の `/sdcard` 絶対パスを変換して push/pull/dump を壊す  ★★★

**根本原因**: この環境では `adb` がネイティブ実行ファイル（`scoop/shims/adb.exe`）かつ
`MSYS_NO_PATHCONV` が未設定（＝MSYSのパス自動変換がデフォルトでON）。そのため `adb` に
`/sdcard/x.xml` を渡すと、MSYS が引数のPOSIXパスを Git ルート基準で変換し
`C:\Program Files\Git\sdcard\x.xml` に化け、`failed to stat remote object 'C:/Program Files/Git/sdcard/...'`
で失敗する（`cygpath -w /sdcard/x.xml` で同じ化け先を再現確認）。**コマンド自体は正しくパスだけ化ける**ため気づきにくい。
該当: `adb push <file> /sdcard/...` / `adb pull /sdcard/x.xml` / `adb shell uiautomator dump /sdcard/x.xml`。

**回避策（すべて実測確認済）**:
- `~/.bashrc` に **project配下限定の adb ラッパー**を定義済。`$PWD` が `$HOME/Desktop/project/*` のとき
  `MSYS2_ARG_CONV_EXCL="/sdcard;/data;/system;/storage;/mnt;/data/local/tmp" command adb "$@"` を実行する。
  **`MSYS_NO_PATHCONV=1`（全変換OFF）ではなく `MSYS2_ARG_CONV_EXCL` で device パスのみ変換除外**する点が肝で、
  `/sdcard` は素通し・host側 `/c/Users/...` は通常通り `C:\Users\...` へ変換されるため**両方が前置き不要で通る**
  （旧 `MSYS_NO_PATHCONV=1` 版は host側保存先が壊れる制約があったが解消。3ケース実測: remote素通し✅／local宛先✅／project外は従来どおり化ける）。
- ⚠️ **Claude Code の Bash ツールは非対話シェル（`-c`／フラグに `i` 無し）で起動し `~/.bashrc` を source しない**
  ため、上記ラッパーは効かず生 `adb.exe` に直行する（`type adb`＝生shim、`BASH_ENV`空）。
  **エージェントから `/sdcard` を扱うときは PowerShell ツールで実行**するか、コマンド前置で同じ
  `MSYS2_ARG_CONV_EXCL=...` を付ける。恒久対応するなら settings.json の env で `BASH_ENV` をラッパー定義ファイルへ向ける手もある。
**補足**: デバイス側パスを引数に取らない adb は Git Bash でそのまま通る
— `adb exec-out screencap -p > out.png`（リダイレクト先はWindows側）、`adb shell input tap/swipe`、
`adb shell pidof`、`adb logcat` 等。

---

### Claude Code フック / Python stdin（Windows）

#### 26. Windows の Python は `sys.stdin` 既定が cp932 → UTF-8 の日本語 stdin が文字化け  ★★

**根本原因**: Windows の CPython は `sys.stdin.encoding` がロケール既定（日本語環境では **cp932**）。
Claude Code はフックへ入力を **UTF-8 の JSON で stdin 渡し**するため、`json.load(sys.stdin)` だと
UTF-8 の日本語が cp932 として誤デコードされ文字化けする（実測: `このバグを修正して` → `こ�\udc81�バグを修正して`）。
JSON の構造文字（`{ } " :`）と ASCII はそのまま読めるので **json パース自体は通る**＝気づきにくい。

**影響範囲**: 判定が **ASCII トークン**（コマンド種別・`fix:`/`feat:` 接頭辞・拡張子・識別子）に依る
フックは ASCII が化けないので正しく動く。**判定対象が stdin 内の日本語そのもの**のフックだけが
サイレントに空振りする（UserPromptSubmit の根本原因リマインダで実際に踏み、長期間一度も発火していなかった）。

**対策**: stdin を生バイトで受けて UTF-8 明示デコードする。
`raw = sys.stdin.buffer.read().decode("utf-8", errors="replace"); data = json.loads(raw)`
（`except` に `ValueError` も加える）。出力側 `sys.stdout = io.TextIOWrapper(..., encoding="utf-8")` だけでは
**入力側は直らない**点に注意。新フックを既存フックの雛形からコピーするとこの入力側を見落として再発しやすい。

**現状**: `~/.claude/hooks/remind_root_cause.py` は対応済。既存フックは判定が ASCII のため実害なく未変更。

---

### Room / DB

#### 27. Room Migration 前に PRAGMA table_info でカラムを確認する  ★★★

> ※ 旧 `#19`。非ASCIIパス項（上記 §19）と番号が重複していたため `#27` へ採番（ライブ `§19` 参照は無し）。

Migration SQL を書く前に端末 DB の実際のカラム名を `PRAGMA table_info(テーブル名)` で確認すること。
フレッシュインストール端末は version 据え置きのまま新スキーマで DB が作られるため、
「旧カラム名が必ず存在する」という前提が崩れやすい。

**よくある失敗パターン**:
1. `lastRead` → `lastReadFilename` リネーム時に version UP を忘れる
2. フレッシュインストール端末が `lastReadFilename` で DB を生成（`lastRead` は存在しない）
3. 後から追加した Migration が `lastRead` を参照 → `SQLiteException: no such column` でクラッシュ
4. コードを revert → デバイス DB とバージョン不一致でさらにクラッシュ

→ コードを revert する前に「端末 DB が何バージョンか」を必ず確認すること。

---

## 移設マッピング（旧 Part II / Part III の固定ID対応）

> 旧エントリ番号（`§N`）は固定ID。本ファイルから `docs/` へ移設したものは下表で追跡する（移設先での再採番はしない）。

| 旧ID | 内容 | 移設先 |
|---|---|---|
| §20 | Atomic Commit は実装順序から設計する | `docs/decisions/0003-atomic-commit-from-impl-order.md` |
| §21 | ProcessingState への一本化 | `docs/patterns/processing-state.md` |
| §22 | Hilt / UseCase 層 不採用（Why-not） | `docs/decisions/0001-no-hilt.md` ・ `docs/decisions/0002-no-usecase-layer.md` |
| §23 | Service 内キュー + シングルループ処理 | `docs/patterns/service-queue-loop.md` |
| §24 | TopAppBar オーバーレイ化 + NestedScrollConnection 非消費 | `docs/patterns/topappbar-overlay.md` |
