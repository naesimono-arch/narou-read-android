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

#### 43. getLineTop は「行ボックス上端」であり字面上端ではない（lineHeight 余剰の分配）  ★★★

Compose の `lineHeight`（em）の余剰スペースは**行上端と字面（グリフ）の間に分配される**。
そのため `TextLayoutResult.getLineTop()` を基準にオーバーレイ描画（ルビ等）をすると、
lineHeight に比例した量だけ字面から浮く（バグ#1 ルビ位置ずれの根本原因。
em 指定だとフォントサイズにも比例するため「文字サイズ非依存で常にずれる」ように見える）。

さらに **Compose 1.6 系（BOM 2024.04）には行単位のベースライン API が無い**
（`getLineBaseline` は 1.7+。公開は `firstBaseline` / `lastBaseline` / `getLineTop` / `getLineBottom` のみ）。
単一段落・単一スタイルなら「行0 = firstBaseline ／ 行i = getLineTop(i) + (lastBaseline − 最終行top)」で
正確に導出できる（行上端→ベースライン距離は全行一様・トリムの影響は先頭行上端のみ）。
実装 = `RubyLayoutHelper.lineBaseline()`。BOM 1.7+ へ上げたら `getLineBaseline` に置換してよい。

---

#### 29. M3 Slider の steps 目盛りドットは tickColor 透明化で消せる  ★

material3 1.2.x の `Slider` は `steps > 0` で目盛りドットを描くが、
`SliderDefaults.colors(activeTickColor = Transparent, inactiveTickColor = Transparent)` だけで
**スナップ挙動を保ったまま**ドットを消せる。カスタム `track` lambda は不要
（旧 handover に「カスタム track が要る」と書いていたのは誤りだった）。

---

### Chaquopy / Python統合

> ※Chaquopy は 2026-07-05 Phase 5 で完全撤去済み（現行は Kotlin/PDFBox 単独経路）。本節は再導入・類似機構検討時に引く歴史的知見として保全（エントリ番号 #N は固定IDのため維持）。

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

### 49. KDoc 内の `[...]` はリンク構文としてパースされ、範囲表記がコンパイルエラーになる（コメントがビルドを壊す）  ★★

> ※ 旧 `#43`。Compose 側 #43（getLineTop・#28 からの再採番先）と番号が重複していたため `#49` へ再採番（移設マッピング表参照）。

Kotlin(K2) は KDoc（`/** */`）内の `[...]` を要素リンクとして構文解析する。範囲のつもりで `[i..j]`（iドットドットj をブラケットで囲む）と書くと `e: ... Closing bracket expected` の**コンパイルエラー**で kspDebugKotlin/compileKotlin が落ちる（2026-07-07 `SearchDraft.kt` で実測）。行コメント `//` 内は自由。KDoc で範囲・添字を書くときはブラケットを外す。エラー行番号が KDoc の行を指していても「コメントは無害」という思い込みがあると原因特定が遅れる。

**同族の第2形態＝Markdown 太字×スラッシュ始まりの語（2026-07-08 `MigrationTest.kt` で実測）**: KDoc 内で `/device-verify` のようなスラッシュ始まりの語（スラッシュコマンド名・絶対パス）を太字で囲むと、開き太字のアスタリスク2連の直後に語頭のスラッシュが隣接して**コメント終端記号の並びになり、ブロックコメントがその場で閉じる**。以降の KDoc 残骸がコードとして解釈され `Expecting a top level declaration` が**数十個・無関係な位置に**噴き、第1形態より一層原因に見えない（こちらは KDoc パーサでなく字句解析レベルなので `//` 行コメント化でも太字をやめても直る）。対処＝スラッシュ始まりの語はバッククォートで包むか太字にしない。

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

### Claude Code フック（stdin 文字化け・出力の届き方）

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

#### 28. PostToolUse の stdout はモデルに届かない → `additionalContext` で注入する  ★★★

**根本原因**: フックの **plain stdout が「Claude のコンテキスト」に入るのは限られたイベントだけ**。
公式仕様で stdout がコンテキスト追加されるのは `UserPromptSubmit` / `UserPromptExpansion` /
`SessionStart` のみ。**PostToolUse / PreToolUse 等の stdout はデバッグログ止まり**でモデルには届かない
（exit 2 の stderr は届くが、それは「ブロック/エラー」を意味するので想起目的には不適）。

**踏んだ実例**: `remind_task_diary.py`（PostToolUse）は `print()` で想起文を出していたが、
モデルには一度も届いていなかった（＝想起として無意味）。

**対策**: モデルに読ませたいテキストは JSON で `hookSpecificOutput.additionalContext` に載せる。
PostToolUse はこれをサポートし、ツール結果の隣に system-reminder として注入される。
```python
print(json.dumps({"hookSpecificOutput": {
    "hookEventName": "PostToolUse", "additionalContext": msg}}, ensure_ascii=False))
```
**雛形コピー時の罠**: 既存フックの `print(...)`＋exit 0 をそのまま流用すると、ユーザー向け表示の
つもりでもモデルにもユーザーにも実質届かない（debug ログのみ）。「誰に届けたいか」でイベントと
出力方式を選ぶこと（#26 の stdin 文字化けと並ぶ、フック自作時の二大ハマりどころ）。

**追補（2026-07-07 実測）**: `hookSpecificOutput.additionalContext` は **PreToolUse でも有効**
（check_commit_granularity.py を JSON 出力化し、`git commit --dry-run` の発火で system-reminder
注入をライブ実測）。PreToolUse で「ブロックせずに情報だけモデルへ渡す」唯一の手段
（exit 2 の stderr はブロックとセット・plain stdout は不達のため）。`hookEventName` は
`"PreToolUse"` を指定する。

#### 42. セッション・トランスクリプト JSONL の構造と「実行捏造」ハルシネーションの形  ★★★

**用途**: フック/ツールがトランスクリプトを静的解析するときの不変点（詳細な設計判断は ADR 0006、実装は `.claude/hooks/detect_fabricated_execution_core.py`）。

**JSONL の不変点（`~/.claude/projects/<slug>/<session-id>.jsonl`）**:
- 1行1レコード、`type` で判別。実体照合に使うのは `assistant`/`user` の2種のみ（`mode`/`ai-title`/`attachment`/`queue-operation` 等はメタ・無視）。
- **照合一次キー**: `assistant.content[].tool_use.id`（`toolu_…`）== `user.content[].tool_result.tool_use_id`（1:1）。ツール結果 user 行はトップレベルに `toolUseResult`（dict か str）も持つ。
- **同一発話が `message.id`/`requestId` を共有して複数行に分割**される（thinking行→text行→tool_use行）。ブロック集約は `message.id` で束ねる。
- **Bash に数値 exit code フィールドは無い**。失敗時のみ `tool_result.content` 先頭が `"Exit code N"`＋`is_error:true`。成功は接頭辞なし stdout。`toolUseResult` は成功=dict{stdout,stderr,…}／失敗・拒否=str。
- **大容量出力はオフロード**され、`<session-id>/tool-results/<toolu_id>.txt` に全文、transcript 側は 2KB プレビュー＋`persistedOutputSize` に置換。
- **サブエージェント委譲の実体は別ファイル** `<session-id>/subagents/agent-<agentId>.jsonl`（全行 `isSidechain:true`）。メイン transcript に委譲先のツール実行は出ない。`agentId` は起動 `Agent` tool の `toolUseResult.agentId`。
- **「人間入力」の識別（2026-07-07 v3・Tier D 実装で確定）**: ①user 行 `content=str` でも中身が `<task-notification>`/`<system-reminder>`/`Caveat:` 等ならハーネス著者＝人間入力ではない ②`queued_command` attachment は `origin.kind=="human"` が人間発の判別キー（task-notification 由来の queued_command には origin が無い） ③**AskUserQuestion の回答はユーザー発話だが `tool_result`（`Your questions have been answered: …`）として届く**＝tool_use の name で判別しないと人間入力の索引から漏れる ④interrupt 直後のユーザー入力は user 行 `content=list` 内の text ブロックに入る。
- **thinking ブロックは本文が空**（`thinking:""`）で **`signature` 文字列長だけが情報量の代理指標**として残る（thinking トークン数とは単位が違う点に注意）。暴走 thinking（通常比5〜30倍）は幻覚事象 G/H/I の共通前兆だった。
- **system prompt 領域は JSONL に記録されない**（2026-07-09 v3.2 開発時に実測確定）: gitStatus スナップショット（Recent commits の SHA 一覧）・CLAUDE.md・memory 注入等の system prompt 内容は**モデルのコンテキストには実在するが transcript のどのレコードにも現れない**。モデルがそこから引用した具体値（直近コミット SHA 等）は、transcript を証拠集合とする静的解析からは**構造的に裏取り不能**＝「捏造」と区別できない（検知器 v3.2 はリポジトリ実在 SHA 照合の外部注入で回避）。transcript 全文 grep で見つからない値が即捏造とは言えない、という解析全般の限界として効く。

**実際に起きた「実行捏造」ハルシネーションの形（`docs/reference/hallucination-ground-truth.md`）**:
- **ハーネスブロックの地の文化**: Claude が会話の続きを自分で捏造し、**偽の `user<background-task-status>…<exit-code>1</exit-code>`／`system<total_tokens>`／`<invoke name=…>`** を assistant の text に生成した（c2e7a254・事象D）。これらのタグはハーネス/ツール層のみが著者で、正当な散文には現れない＝生（バッククォート引用でない）出現は捏造の強シグナル。
- **未実行の成功報告**: テスト/ビルドの成功を実行せず断言（事象D の CP3–5「unittest 28件 OK」等）。
- 検知は「text は証拠にせず tool_use/tool_result ペアに突き合わせる」＝ハーネス著者と assistant 著者の**構造的分離**が根拠。

#### 48. settings.json の hooks 配線変更はセッション再起動まで反映されない（削除した hook が呼ばれ続ける）  ★★

**根本原因**: Claude Code は hooks の登録（settings.json の `hooks` ブロック）を**セッション起動時に読み込んで固定**する。
セッション中に settings.json から hook を外しても旧配線のまま呼び続け、逆に新規追加した hook はそのセッションでは発火しない。
**実測（2026-07-06）**: mark_python_tests_passed.py（撤去済み）を配線ごと削除した直後から、毎 Bash 実行で
「can't open file … mark_python_tests_passed.py」の PostToolUse エラーが再起動まで出続けた（実コマンドへの影響は無し）。
**対処**: 配線変更を伴う hook 改修をしたら、エラー連発や無発火を「壊した」と誤診しない。反映はセッション再起動で。
なお **hook 本体（.py の中身）の編集は再読み込み不要で即反映**される（毎回 `python <file>` を起動し直すため）＝
「配線＝起動時固定／本体＝毎回読込」の非対称を覚えておく。
関連: `docs/decisions/0004` の「セッション内ブランチ跨ぎで hook が壊れる」も同じ起動時固定が根因（あちらはブランチ切替で実ファイル側が消える形、こちらは同一ブランチ内の配線編集が反映されない形）。

#### 40. plan モードはプラグイン subagent へ伝播しない → plan 中でも agy は実行され、`--yolo` 付きなら書き込む  ★★★

**事実**: Claude Code は (a) プラグイン subagent の `permissionMode`/`hooks`/`mcpServers` frontmatter を security 上**サイレント破棄**し、(b) main セッションの **plan モード（read-only）を subagent の権限層へ伝播させない**。結果、**plan モード中でも `antigravity-delegate` を spawn でき、`--yolo` なしの `agy-delegate` はそのまま実行される**。web の issue #4750（plan 中の subagent 挙動は未定義）がこの環境で現実化したもの。
**なぜ危険か（サイレント失敗クラス）**: plan モードの read-only 保証は **agy プロセスに及ばない**（agy は別プロセスの Gemini CLI で、監視するのは agy 自身の `.agents/scripts/guard_forbidden.py` deny フックのみ・fail-open）。read-only は「`--yolo`（= `agy --dangerously-skip-permissions`）を渡さない」ことだけで構造的に担保されており、**plan 中にうっかり `--yolo` を付ければ agy は plan モードを無視してファイルを書く**＝「plan だから安全」の前提が無言で破れる。
**実測（2026-07-06 probe）**: plan モードのまま `antigravity-delegate` に read-only digest タスク（`--yolo` 無し・`--dir` でリポジトリ指定）を投げ → agy が pdf/ パッケージを実読し正確な digest を返却（cited `file:line` を spot-check、幻覚なし）→ `git status --porcelain` clean で**書き込みゼロを独立確認**。ブロックしたのは subagent 自身の no-chaining ガード（`; echo` を足した時）だけで、plan モードや permission deny は一切発火しなかった。
**対処**: plan モード中の agy 委譲は **read-only（`--yolo` 厳禁）限定**。運用ルールは `CLAUDE.md`「委譲判断 / plan運用」節（plan 中の agy 委譲は read-only／`--yolo` 厳禁）。書き込みを伴う探索/生成は plan の外で行う。**機械ガード実装済み（2026-07-06）**: plugin hook `validate-delegate-bash.sh` に「`permission_mode=="plan"` かつ引用除去後 command に `--yolo`/`--dangerously-skip-permissions` を含むなら deny」を追加＝お願い→機械保証へ昇格（**ただしサブエージェント経路のみ**＝下記スコープ）。単体8/8＋plan 再突入の `--yolo` 委譲 block で end-to-end 検証済み（**PreToolUse payload の `permission_mode` は plan モードで subagent の Bash payload にも `"plan"` が届く**ことを同時確認。docs 記載どおり）。
**⚠ スコープ＆--yolo 不使用の再検証（2026-07-06）**: 機械 deny は hook が `agent_type` で自己スコープする（`validate-delegate-bash.sh` 38–47行）ため **`antigravity-delegate` サブエージェント経路のみ**に効く。**メインセッションから直接叩く `agy-delegate`**（`bash -ic` ラッパ・直叩き・`/antigravity:delegate` slash＝**antigravity プラグインの delegate command 定義**の step 2 で main が直接 `agy-delegate [--yolo]` を実行する設計・書き込みタスクには step 1 で `--yolo` を明示）は agent_type が無く **scope guard で素通り（対象外）**＝plan 中の `--yolo` を止めない。よって「機械保証へ昇格」は subagent 経路に限る backstop で、**primary の担保は依然「Claude が --yolo を渡さない規律」**。再検証は **`--yolo` を一切使わず**実施＝source hook に一時 probe を差し、subagent 経由で `source-hook-live=YES`／非plan時 `permission_mode='auto'`・plan時 `permission_mode='plan'` を実測し、静的8/8（`plan`+`--yolo`→deny）と合成して「plan＋subagent＋--yolo は deny」を立証（probe は検証後 revert）。別セッションが `bash -ic 'agy-delegate …'`（＝main 経路）で read-only 委譲を投げ**ゲート素通り**したのが、この scope 限界の実証。
**実装ノート（ハマり所）**: ①**hook はサンドボックス実行**＝hook 外で作った sentinel ファイルを `[ -f ]` で見えない → gate は stdin(JSON) payload と `tool_input.command` だけで判定すること。②**この directory-marketplace 版では live な hook は cache でなく git-test source**（`CLAUDE_PLUGIN_ROOT`→source。source に入れたゲートが発火した実測で確定）。編集・commit 対象は git-test source（[[antigravity-plugin-cache-vs-source-cost-gate]] を要更新）。
関連: #28（PostToolUse stdout 不達）／`docs/decisions/0004`（hook の matcher 範囲・起動時固定）／agy 安全機構は `.agents/scripts/guard_forbidden.py`・`AGENTS.md`。

#### 41. `!`（bash mode）で実行するコマンドはツールフックを一切通らない → 「AIは作れない・人間は作れる」を機構化できる  ★★

**事実**: ユーザーが入力欄で `! <cmd>`（bang-command / bash mode）を実行しても、`PreToolUse`/`PostToolUse`（matcher "Bash"）は**一切発火しない**。フックを通るのは **AI が Bash ツールで実行したコマンドだけ**で、その stdin JSON は `tool_name:"Bash"` と `tool_use_id` を持つ。公式ドキュメントに bash mode の記載は無く、挙動として実測でのみ確定できる。
**実測（2026-07-06）**: 既存 PreToolUse(Bash) フック本体に stdin JSON を丸ごと記録するプローブを仕込み（本体編集は即時反映＝#48）、①AI の `echo` → 記録あり（`tool_use_id` 付き）②ユーザーの `! echo …` → **記録ゼロ**。3 発火すべて AI ツール由来で、bang 由来のエントリは現れなかった。
**活用（レバー）**: この非対称性で「**AI はツール経由でファイルを作れない／人間は `!` で作れる**」を機構的に成立させられる。初適用が `guard_sentinel_creation.py`＝main コミット許可センチネル `.allow_protected_commit` を人間発行のみに限定（Write/Edit/Bash 経由の生成を PreToolUse でブロック）。「AI 由来か人間由来か」をフックで判別する必要すら無い（`!` はそもそも来ない）ため実装は単純な文字列/パス一致で済む。
**落とし穴（盲点）**: 逆に言うと「フックで全 Bash を捕捉している」つもりでも **`!` 経由は監査・ガードの盲点として素通り**する。コミットガード等が `!` で回避されうる点は設計時に意識する（＝ソフト境界。ADR 0004 の限界と同根）。加えて **新規フック配線はセッション中無反映**（#48）なので、この種の実測は「既存配線フックの本体編集（即時反映）」で行うのが速い。
**運用注意（センチネル手順は絶対パスで案内する・2026-07-07 実地で判明）**: `!` シェルの cwd は**リポジトリルートである保証がない**（実際にサブディレクトリから叩かれ、相対 `.claude/.allow_protected_commit` が `No such file or directory` で失敗した）。フックが AI に返す発行手順が相対パスだと、AI がそれを中継 → user が cwd 次第で失敗する。対処: `guard_commit_branch.py`／`guard_sentinel_creation.py` の案内メッセージを **`__file__` から算出した絶対パス**で出すよう修正済み（cwd 非依存で一発成功）。判定ロジックは従来どおり（前者=絶対パス存在チェック／後者=basename 一致）で、変えたのは**案内文の表記だけ**。
関連: #48（配線＝起動時固定／本体＝毎回読込の非対称）／`docs/decisions/0004`（この事実を利用した guard_sentinel_creation の設計判断＝Decision B-5）。

#### 44. fail-open 設計のフックは壊れていても「全通し」で無症状 → 新設・改修時に陽性コントロール1回が必須  ★★★

**実例（2026-07-07 顕在化）**: `check_lint_on_commit.py` が `PROJECT_DIR` を dirname 2回で算出しており（`<root>/.claude/hooks/…` からルートまでは3回が正）、存在しない `<root>/.claude/android` を cwd に subprocess 起動 → OSError → fail-open スキップ。**導入以来どの OS でも一度も Lint が走っていなかった**（WSL の gradlew CRLF 問題を調べる過程で e2e を取って発覚＝それ以前の階層で死んでいた）。コミットは全て素通りするため、外形上は「ゲートが健全に沈黙している」ように見える。

**なぜ起きるか**: ブロック系フックは「鳴らない＝健全」と「鳴らない＝壊れている」が外形上区別できない。fail-open（ゲート故障でコミットを妨げない）設計自体は正しい判断でも、その代償として故障が無症状化する。

**教訓**: フックを新設・改修したら、**わざと違反状態を作って一度発火させる陽性コントロールを取る**（本件は baseline 偽装で exit 2 ＋ stderr 理由を実測）。可能なら `test_*.py` に固定する（stale-check 項目12 が回帰実行する）。`test_stop_guard_fabrication.py` の陽性コントロール5ケースが先行の同型対策。関連: #48（配線のセッション固定）・#28（出力の届き方）＝いずれも同じサイレント失敗クラス。

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

#### 39. 並列ブランチが同じ Room version を別スキーマで宣言すると identity hash クラッシュ（migration は走らない）  ★★★

Room は起動時、端末 DB の `user_version` が宣言 version と**同値**だと migration を一切走らせず、
`room_master_table` の identity_hash 照合だけを行う。並列 worktree の2ブランチがそれぞれ
「version 8」を**別のスキーマ変更**で宣言すると、片方のビルドで v8 化した端末にもう片方を
インストールした瞬間 `Room cannot verify the data integrity`（identity hash 不一致）でクラッシュする。
**migration 不足系と違いエラーメッセージが「version を上げ忘れた」と誤誘導してくる**のが罠
（実際は version の上げ方ではなく「同じ番号の取り合い」が原因）。

**実測（2026-07-07）**: `feat/processing-resilience`（v8＝`pending_jobs` テーブル・実機検証済み）と
`api-lab-ai`（v8＝`books.ncode` 列）が PGEM10 上で衝突。端末 DB は resilience 側の v8 になっており、
api-lab-ai ビルドが起動即クラッシュ（user_version=8 なので MIGRATION_7_8 は呼ばれもしない）。

**対処（後発側が退避する）**: 後発ブランチは version 9（`MIGRATION_8_9`）へ退避し、先行側の 7→8 を
**同一内容で複製**して 7→8→9 のパスを繋ぐ（Room は自分の管理外テーブルを検証しないので、
エンティティ未定義の `pending_jobs` が存在しても無害）。マージ時は「version 9＋両 migration＋
両エンティティの合併」で解決する。

**予防**: スキーマを触る前に**全 worktree の宣言 version を確認**する
（`grep -h "version = " ~/wt/*/android/app/src/main/java/com/novelreader/data/AppDatabase.kt`）。
`/db-migration` スキルの手順にも組み込み済み。

**追補（2026-07-08・マージ時の変種）**: 退避した後発側（v9）が実機検証済みのままマージへ進むと、
本文の処方「version 9＋両 migration＋両エンティティの合併」だけでは不足する。合併でエンティティが
増えた時点で v9 の identity hash が変わるため、**後発側 v9 で migrate 済みの実機に合併ビルドを入れると
同 version 同士の hash 照合でクラッシュする**（＝本エントリと同機序が一段上で再発。migration は走らない）。
対処＝合併時に**さらに +1（v10）し no-op migration で hash を再スタンプ**する（実テーブルは両系の
migration で作成済みのため DDL 不要。`MIGRATION_9_10` が実例。旧 9.json は実機が保持する hash の記録として
上書きせず残す）。並列ブランチの双方が実機検証を挟む開発では、この「合併でもう一段 +1」を既定とすること。

---

### PDFBox-Android 移植（Chaquopy→Kotlin ネイティブ化）

#### 30. pdfbox-android は Maven 座標がハイフン・Java パッケージがアンダースコア（逆転の罠）  ★★★

Tom Roush の PDFBox-Android（Chaquopy/pdfminer からの移植先）は、**依存座標とパッケージ名で区切り文字が逆転する**:
- Maven 座標（build.gradle）: `com.tom-roush:pdfbox-android:2.0.27.0` ← **ハイフン**。`com.tom_roush`（アンダースコア）は Maven Central に存在せず `Could not find` で 404 になる。
- Java/Kotlin パッケージ名（import）: `com.tom_roush.pdfbox.*` ← **アンダースコア**（Java 識別子にハイフン不可のため）。

移植元プロト submission-B は JVM 版 `org.apache.pdfbox:pdfbox:2.0.31` を使うので、Android 版へは `org.apache.pdfbox.*` → `com.tom_roush.pdfbox.*` の import 差替で移る。**apache-pdfbox 2.0.x と tom-roush 2.0.x は API 1:1**（TextPosition の unicode/xDirAdj/yDirAdj/heightDir/font/fontSizeInPt、PDFTextStripper、上原点座標系が同名同義）なので import 以外はコード無改変で移植できる。バージョンは 2.0.x 系で固定する（Android 版が upstream 2.0.x ベースのため）。

#### 31. pdfbox-android は PDDocument.load 前に PDFBoxResourceLoader.init(context) が要る（2026-07-03 実機スパイクで検証済＝穴3 KILL）  ★★★

ToUnicode CMap を持たない CID フォントのグリフ解決に、AAR 同梱の Adobe glyphlist/CMap 資産を使う。そのため **`com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(applicationContext)` を全ての `PDDocument.load` の前に1回**呼ぶ必要がある（`MainActivity.onCreate` の `Python.start` 置換位置か `Application.onCreate`）。フォントは AAR 同梱で手動配布不要。androidTest では `@Before` で instrumentation の `targetContext` により init する。

**【2026-07-03 実機実測で解消＝穴3 KILL】** OPPO PGEM10(ColorOS) 実機で `PdfExtractorDeviceSpikeTest`（実PDF3件を golden_regression と同一指標で突合）を実行。**init は実機で効く**＝CID→Unicode 解決が根本的に機能する。決定的証拠: 短編 N1453LW は body_sha256 まで**完全一致**、中編 N2959KI（9786段落/131章/38万字）も **body_sha256 完全一致**。残差は init 失敗ではなく既知の CID→Unicode マッピング差で、正体は #35（波ダッシュ主因）＋超長編 N6169DZ の 0.01% オーダーのエッジ（文字+0.012%・ルビ+0.97%）。submission-B デスクトップ実測の「ルビ P/R 約81%」は char-level 指標での話で、段落/本文ベースでは実機でもほぼ一致した（[[kotlin-pdfbox-migration-prototype]]）。

#### 32. WSL Bash ツールで Gradle を回す作法（.bashrc 非ロード・sdk.dir 競合・sed 警告）  ★★

`.bashrc` が非対話で early-return するため Bash ツールでは `gw` 関数も `JAVA_HOME` も未定義（[[bash-tool-no-bashrc-gradle-env]]）。加えて `/mnt/c` 上の `local.properties` は Android Studio が `sdk.dir` を Windows パスで書き戻すため、Linux ビルドで競合する。作法:
- 毎回 `export JAVA_HOME=/home/qingj/opt/jdk-17` ほか（ANDROID_HOME/ANDROID_SDK_ROOT/PATH）を明示し、`java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain … --init-script /home/qingj/ext-build/novel-reader-init.gradle <task>` で起動（init script が build/ を ext4 へ逃がし AAPT2 EPERM を回避）。
- ビルド直前に `sed -i '/^sdk\.dir/d' local.properties` で Windows sdk.dir を除去し、export 済み `ANDROID_HOME`（Linux SDK）へフォールバックさせる（AGP の解決順位 sdk.dir > ANDROID_HOME のため行が在ると環境変数を上書きする）。
- **`sed -i` は `/mnt/c`(drvfs) で `preserving permissions … Operation not permitted` 警告を出すが置換自体は成功する**（無害・`2>/dev/null` で抑制可）。不可視文字の一括エスケープ化 `sed 's/\xc2\xa0/\\u00a0/g'`（生 NBSP → ` `）も drvfs 警告付きで成功する。

#### 33. Kotlin のブロックコメントはネストする＝KDoc 内の `/*`（ファイルパス glob 等）が入れ子を開く  ★★

Kotlin は Java と違い**ブロックコメントがネスト**する。そのため `/** … */` の KDoc 本文に
`golden_html/*.html` のような **`/*` を含む文字列**を書くと、`/*` が入れ子コメントを開き、
KDoc 末尾の `*/` はその内側だけを閉じ、**外側コメントが EOF まで未閉鎖**になる。コンパイラは
`Unclosed comment` を**ファイル末尾の行**で報告する（真の原因行から遠く、原因が読み取りにくい）。
HtmlExporter 移植のテスト KDoc でライブに踏んだ（`src/test/resources/golden_html/` の glob 表記 → `Unclosed comment` at EOF）。
回避: doc/コメントに glob やパスを書くときは `/*` を出さない（`{index,chap_1,chap_2}.html` 等の列挙、
または末尾スラッシュ止めにする）。移植で KDoc にファイル例を多用するため今後も再発しやすい。

#### 34. tom-roush の InvalidPasswordException コンストラクタは package-private（テスト生成不可）  ★★

apache-pdfbox の `InvalidPasswordException(String)` は public だが、**tom-roush 版(2.0.27.0)は package-private**。
そのため `com.tom_roush.pdfbox.pdmodel.encryption` 外（＝ユニットテスト）から `new`/サブクラス化できず、
`Cannot access '<init>': it is package-private` でコンパイルが落ちる。本番コードは PDFBox 内部が投げた実インスタンスを
`e is InvalidPasswordException` で拾えるので支障ないが、**暗号化分類のテストはこの型を作れない**。
対処: `classifyPdfError` を「型分岐＋"password" メッセージ fallback」の二段にし（Python も元々 `"password" in str(e)` 判定）、
テストは `IOException("…password…")` のメッセージ経路で暗号化分類を担保した。PDFBox 例外周りのテストで再発する落とし穴。

#### 35. PDFBox-android は WAVE DASH(U+301C) を FULLWIDTH TILDE(U+FF5E) に写す（pdfminer との CID→Unicode 差の主因）  ★★★

穴3 実機スパイク（#31）で判明した pdfminer↔PDFBox 残差の**主因**。同じ波ダッシュのグリフに対し、
**pdfminer は `〜`(U+301C WAVE DASH) を、PDFBox-android は `～`(U+FF5E FULLWIDTH TILDE) を返す**。
1:1 置換なので文字数は変わらず、title・本文の記号として現れる（例: 「シャングリラ・フロンティア〜…〜」）。
これは有名な「Windows 波ダッシュ問題」の CMap 版で、Adobe-Japan1 の CID→Unicode をどちらの正規形へ写すかの選択差。
pdfminer に揃えるには**抽出後に U+FF5E→U+301C を正規化**する手がある（なろう小説では波ダッシュが正でありFF5Eの正当な用例はほぼ無い＝低リスク）。ただし超長編 N6169DZ は波ダッシュ以外にも残差があり（文字+434=+0.012%・ルビ+110=+0.97%・段落+5・blank+1・章題グリフ写像差**11件**）、これは pdfminer が吸収していた抽出エッジ（座標順・グリフ写像）で波ダッシュ正規化だけでは body 完全一致にならない。正規化の要否は移植ロードマップの判断事項（handover 参照）。
**【2026-07-05 Phase 4 で章題11件の全容判明】** 当初この欄で「章題並び1件`兎'ｓ`↔`'鳥…`」と書いたのは**旧spikeが最初の差だけ表示した過少記録**で、実測は11件・全てグリフ写像差＝①ダッシュ変種 `－`(U+FF0D)→`−`(U+2212) が6件 ②矢印回転 `↑↓`(U+2191/2193)→`←→`(U+2190/2192) が3件（PDFBox が矢印を90°回転誤読・golden の←→が意味的に自然） ③アポストロフィ座標順 `兎'ｓ`↔`'鳥…` が2件。①②の9件は波ダッシュと同型の1:1コードポイント置換なので `normalizeGlyphUnicode` へ追加すれば golden に寄る（改善候補=handover D）。③は座標順のため1:1置換不可。文字化け・欠落ではなくグリフ写像の系統差である点が要点。
**【2026-07-06 実装（`fix/handover-singles`）】** ①②の9件（`FF0D→2212`・`2191→2190`・`2193→2192`）を `normalizeGlyphUnicode` へ個別 indexOf ガードのチェーンで追加（JVM 緑・非対象は同一参照＝`assertSame` 契約維持）。`FF0D→2212` は本文にも同グリフが出れば正規化され短中編 body_sha256 を破壊しうるため、本番判定は実機ゲート(`PdfExtractorDeviceSpikeTest`)で行い、**破壊時は `FF0D→2212` を取り下げ矢印2件のみ残す**（golden から離れる写像は入れない）。

#### 36. connectedAndroidTest はテスト後にアプリ本体+テストAPKを自動アンインストールする（実データ消失）  ★★

AGP の `connectedDebugAndroidTest` は既定で **run 後に対象アプリAPKとテストAPKの両方を uninstall** する。
そのため実機に入っていた `com.novelreader` の**蔵書DB等の実データが消える**（穴3スパイク実行で実際に消えた＝`pm list packages` から消滅・`run-as` も unknown package）。
[[wsl-debug-keystore-share-for-install]] が「uninstall は最終手段（蔵書保持）」と気にしているのと衝突する副作用。
回避: `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true` を付けて実行すると run 後もインストールが残る。
実機に保持したい実データがある状態で androidTest を回すときは必ず付けること。

#### 37. ColorOS(OPPO) は CPU 集中の androidTest プロセスを「abnormal fg_cpu」で強制killする（超長編抽出が落ちる）  ★★★

Task 9 実機フル疎通で、N6169DZ(8.9MB/350万字/951章)を `PdfBookExtractor.process` で抽出中、約2.5分後に
**ColorOS の OSense/Athena が com.novelreader を強制終了**した（logcat: `Athena OplusClearSystemService … reason: 502 o-kill(502)`、
`OGuardManager_AppPowerAnalyzer: a abnormal fg_cpu app … com.novelreader`、直前に `osense.compress … do shrink`）。
**OOM ではない**（GCInfo は 314→325MB でヒープ余裕あり）＝純粋に「長時間 CPU を食う前景プロセス」を OEM 電源ガーディアンが異常判定して殺した。
`am instrument` は `INSTRUMENTATION_RESULT: shortMsg=Process crashed.` を返す。短中編(N1453LW 3章/N2959KI 131章)は kill 前に完走・本棚シード済み、超長編だけが落ちた。
- **含意**: 素の androidTest には前景サービス保護が無いため、この OEM killer に対して無防備。**実アプリは `PdfProcessingService` の前景サービス＋WakeLock＋10分/件で保護**しており（handover/plan Phase2 memo）、Phase 3 配線後は生存しうるが、**ColorOS では前景サービスでも超長編が落ちないかは要実機再確認**（本アプリの最重要 OEM リスク）。
- **回避（検証用）**: ①電源最適化から除外を試す（`dumpsys deviceidle whitelist +com.novelreader` 等。ただし fg_cpu killer には効きにくい） ②検証目的なら PDF を冒頭数十ページに切詰めて CPU バーストを閾値未満にする ③実書での長編検証は Phase 3 の前景サービス経路で行う。
- 関連: [[workflow-autonomous-device-verification]]。connectedAndroidTest の自動uninstall(#36)とは別の落とし穴。

#### 38. ColorOS(OPPO) の Hans フリーザ(OplusHansManager)は素の androidTest プロセスを freeze する（kill ではない・#37 と別機構）  ★★★

Phase 4 精度回帰ゲート(`PdfExtractorDeviceSpikeTest`)の ≤15版クリーンラン取得中、N6169DZ(350万字)抽出が
「CPU時間 0:25 のまま凍結・約10分 no progress」でハングに見えた。真因は **ColorOS の Hans フリーザが
`com.novelreader` プロセスを freeze（cgroup freezer で丸ごと一時停止）** したこと（logcat:
`OplusHansManager: unfreeze uid ... com.novelreader ... reason: Signal`／`F exit(), F stay=1578`）。
- **#37(fg_cpu kill) とは別機構**: Osense の kill ガーディアンは instrumentation を
  `KillAction: don't check adj ... FGS/48/0/instrumentation` と**kill 対象外に保護**していた＝プロセスは死んでいない。
  **殺されたのではなく凍結された**。凍結中は CPU が進まないので「ハング/デッドロック」に誤認しやすい
  （SIGQUIT で一瞬解凍→数十秒 work→再凍結 を繰り返す＝CPU時間が飛び飛びに増える）。
- **端末操作・充電の有無は無関係**（無操作・充電中・`svc power stayon true`・
  `dumpsys deviceidle whitelist +com.novelreader` を全て満たしても凍結した）＝素の `am instrument` は
  プロセスを foreground/perceptible にしないため Hans に background 扱いされる。
  `device_config put activity_manager use_freezer false` は allowlist 権限不足で不可。
- **回避（実測で確立）**: テスト対象アプリの **MainActivity を前面化してプロセスを perceptible にする**＝
  `adb shell monkey -p com.novelreader -c android.intent.category.LAUNCHER 1`。前面化した瞬間 oom_adj が
  foreground(-10) になり **%CPU が 0→250% へ復帰**し N6169DZ が完走・`OK (1 test)`。instrumentation テストは
  同一プロセスで並行継続するので Activity 表示と共存できる。**超長編の素 androidTest を回すときは
  実行中に MainActivity を前面化しておくこと。**
- freeze/thaw は cgroup freezer のクリーンな中断・再開なので抽出結果は無破損（決定的）＝凍結を挟んでも
  PASS は有効（本ランの wall time 1784s は凍結分が大半で、実 CPU は約1分）。関連: #37・#4（FGS でも停止）・
  [[workflow-autonomous-device-verification]]。

---

### なろう小説API（検索パラメータ）

#### 46. type のハイフンOR指定はサイレントに無視され「全件」へフォールバックする（絞ったつもりが無絞り込み）  ★★★

なろう小説APIの複数値ORはパラメータごとに対応がバラバラで、**非対応パラメータに `-` 区切りを渡してもエラーにならず、そのパラメータ自体が無視される**（2026-07-07 実測: `type=t-r` の allcount 1,222,053 ＝ type 無指定と一致。t=594,243 / r=435,539 / er=192,254）。UI で「短編+連載中」を選んだつもりが全作品を検索する、という気づきにくい欠陥クラスになる。
- OR可: `ncode`・`userid`・`buntai`（マニュアル明記）と `genre`/`biggenre`（実装で使用中・動作確認済み）。
- `type` は不可。ただし公式複合値 `re`（連載中+完結済＝全連載）・`ter`（短編+完結済）が用意されている。**短編+連載中だけは単一クエリで表現不可**＝2クエリに分けてクライアントマージする（短編と連載中は排反なので allcount は単純加算で正確・同一 order のソート済みリスト同士のマージで上位N件も正確）。
- `lastup`/`lastupdate` はプリセット文字列（sevenday 等）のほか **UNIX秒の `開始-終了` レンジを受ける**ため、複数時期のORは連続レンジへ合成すれば表現できる。プリセットの暦はサーバ＝日本時間基準なので、クライアント計算は Asia/Tokyo 固定で行うこと。
実装の所在: `narou/model/DiscoveryQuery.kt` の `typeApiParam`/`lastupApiParam`・`NovelApiRepository.discover` のマージ経路（`8d09e7a`）。

#### 47. レスポンスのキー名が `of` 指定の有無で変わる項目がある（noveltype ↔ novel_type）＝片方だけマップすると「常に null」のサイレント無効  ★★★

なろう小説APIの作品種別は、**`of` でフィールドを絞ったときはキー `noveltype`、`of` 無指定（全項目）ではキー `novel_type`** で返る（マニュアル§5 の注記＋2026-07-07 実API実測で確定。他のフィールドはフルネームで一貫しており、この項目だけが例外）。
- 罠の型: JSON マッピングを片方のキーだけにすると、もう一方の経路では**例外にならず黙って null** になる。実際に `novelDetail`（of 無指定）経路で `novelType` が常に null → 詳細画面の短編が「完結 1話」誤表示・継続読書の短編ガードがサイレント無効、という実回帰が起きた（一覧＝of 指定経路は正常なので実機スモークでも気づけない）。
- 対処: Moshi は1フィールドに複数キーを張れないため、両キーを別フィールドで受けて算出プロパティで合流させる（`NarouNovel.kt` の `noveltypeCompact`/`novelTypeFull`→`novelType`）。**新しいフィールドを of 有無の両経路で使うときは、両形のレスポンスでキー名が同じか必ずマニュアル§5で確認すること。**

#### 52. リクエストの略号（of の t/n/gp）とレスポンスのキー名は別物／範囲指定は 0 を送らず「1 か省略」／転生+転移は istt へ振替  ★★

なろう小説APIで「マニュアルを読んだだけでは踏む」外部事実3点（`narou_api_manual.md` 本文と重複しない落とし穴に絞る。2026-07-08 集約＝旧 STATUS-api-lab §2）。

- **レスポンスの JSON キーはフルネーム**（`title`/`ncode`/`global_point`/`general_all_no`/`length`…）。`of` の略号（`t`/`n`/`gp` 等）は**リクエストの「どの項目を返すか」の選択用**であって、レスポンスのキー名ではない。→ Moshi の `@Json(name=...)` はフルネームで張る（略号で張ると全項目 null になる）。例外は #47（`noveltype`↔`novel_type` が `of` 有無でキー名自体が変わる唯一の項目）。
- **検索範囲 `title`/`ex`/`keyword`/`wname` に 0 を送らない**: マニュアル§4.1 は「1 で指定・全未指定なら全項目」としか定義せず、**0 送信は未定義**。選択した項目だけに 1 を送り、非選択は**キーごと省略**する（0 を「明示的にオフ」の意味で送ると挙動が保証されない）。
- **転生＋転移の同時指定は `istt=1` へ振替**: `istensei=1&istenni=1` を並べると **AND**（両方に該当する作品のみ）になり絞りすぎる。OR（どちらか）の意味を持つ `istt=1` を使う。※`type` の複合 OR がサイレント無視される #46 と同じ「AND/OR とフォールバックの罠」クラス。

#### 45. 閲覧ページを"加工して"アプリ内表示するのは運営が明文で禁止（広告除去・独自UI被せ＝強制退会/削除／"加工なしそのまま"表示のみ可）  ★★★

なろう運営の開発・運営者向けヘルプ（ヘルプ183「よくある違反行為」）が線引きを明文化している（2026-07-08 確認）。**アプリ内 WebView で独自UIを被せて没入させる**という発想は、なろうに対しては構造的に塞がれている。
- **問題としない**（原文）: 「WebView 及び類似の技術を用いて、作品の閲覧ページを**加工することなくそのまま**当該アプリ内で表示する行為」。
- **違反（確認され次第 強制退会・削除等）**（原文）: 「掲載作品の閲覧ページにて**広告を除去する等の加工**を行って表示する行為」／「掲載作品の本文を**機械的に取得**して表示・ダウンロードする行為」。
- **罠の要点**: 禁止の本体は"広告除去"ではなく**"加工"そのもの**（条文は「広告を除去する**等の**加工」）。フォント・配色・余白の CSS 注入も、ヘッダー/広告の DOM 除去も、**見た目を変える一切が"加工"に該当**し、広告を残しても違反。有料の広告除去オプションは存在せず運営意思は「広告は消させない」で一貫（＝明文が無くても運営意思・収益基盤に反する行為は不可という読みの裏付け）。
- **根拠条項**: 14条20項（運営・ネットワーク・システムへの支障）・23項（API以外の自動化手段によるアクセス／データ収集）＋包括の 24項（不適切と判断する行為）・22項（規約違反・権利侵害と運営が判断する行為）。ヘルプ183 はページ全体をこれらに紐づけて説明。
- **設計含意**: 「アプリ内完結」で許されるのは**加工なし表示のみ**＝体験は Custom Tabs と等価。**構造的に加工不能な Chrome Custom Tabs が最も安全**（素の WebView は後から `evaluateJavascript` で"加工"でき、運営に対し"無加工"を仕組みで保証しにくい）。本文をネイティブ描画する道も「本文の機械的取得」で違反＝**案A（本文非取得・メタのみ）が規約的に正しい**ことの裏付け。方針への反映＝設計判断は **ADR 0010**（加工なし送客＝Custom Tabs を既定）、現況は `STATUS.md` §1・残タスクは `handover.md`。

### WebView（アプリ内ブラウザ）

#### 56. `onPageFinished` は `goBack()` の履歴遡行でも再発火する（URL 観測で「進んだページ」だけ拾いたいなら履歴スタック位置で構造判定する）  ★★

URL 観測ベースの読書位置記録（機能②・ADR 0012）で、読み進めた後に戻る連打で退出すると記録がセッション先頭話へ巻き戻った（2026-07-11 実機 PGEM10 で2作品再現）。
- **事実**: `WebViewClient.onPageFinished` は前進ナビゲーションと `goBack()` を区別せず、**戻りで表示したページでも毎回発火**する。URL だけ見ていると「最後に発火したページ＝バックスタック最古側」が最終記録になる。
- **対処（構造判定）**: `view.copyBackForwardList()` で `currentIndex < size-1`（forward 履歴が残っている）なら**戻りで到達したページ**と判定できる。新しいリンクを踏めば forward 履歴は切り詰められ `currentIndex == size-1` に戻るため、意図的な開き直しは前進として扱われる。
- **罠（フラグ方式は不可）**: 「goBack 発行→次の onPageFinished を1回スキップ」のフラグ方式は、戻る連打時に goBack 発行数と onPageFinished 発火数が**一致しない**（描画スキップ/合流）ため、抑制の取り逃しと残留（次の正当な前進記録を殺す）が起きる。発火数に依存しない履歴位置判定を使うこと。
- 実装＝`WebReaderScreen.kt` の onPageFinished／設計判断＝ADR 0012 追補。

### テスト基盤（Robolectric / Compose UI Test）

#### 50. Robolectric では ModalBottomSheet 内の Composable に対する assertIsDisplayed / performClick が不安定に落ちる（内容を Content 分離してテストする）  ★★

Robolectric（4.11.1・sdk34・createComposeRule）で `ModalBottomSheet` を含む Composable を `setContent` すると、シート上部の要素は検証できるが、**下方の要素の `assertIsDisplayed` とチップの `performClick`→callback 検証が AssertionError で落ちる**（2026-07-08 実測＝ReadingSettingsSheetTest。テキスト書式・セマンティクスは正しいのに落ちる）。
- 機序: ModalBottomSheet は**別ウィンドウ描画＋部分展開**（既定 sheetState はまず部分高で開く）のため、シート下部が「存在するが画面外」となり可視判定に失敗する。別ウィンドウへの入力注入も JVM 環境では信頼できない。
- 対処（採用）: シート内容を `XxxSheetContent`（state+callback の葉）へ純抽出し、**テストは枠を剥がした Content を直接組む**（`ReadingSettingsSheet` → `ReadingSettingsSheetContent` が先例）。`assertExists` への緩和は「表示されていない退行」を見逃すため不採用。
- 一般則: Robolectric で新しく葉 Composable テストを書くときは、**ModalBottomSheet / Dialog / Popup を跨いだノードの可視判定・クリックを避け**、内容 Composable を検証単位に切ること（ADR 0009 の運用細則）。

#### 51. ModalBottomSheet 内蔵の NestedScrollConnection が境界フリング残速度を settle へ渡し枠がオーバーシュートする（material3 1.2.1）  ★★

「条件を調整」シート内を高速フリックして手を離すと、慣性で**シート枠全体が Expanded 上限を超えてオーバーシュート→復帰**し、上端に裏画面が一瞬覗く不具合の調査結果（2026-07-08・逆コンパイルで確定。**未修正・不具合残置**＝現状は素の `ModalBottomSheet`+`verticalScroll`。再挑戦の出発点として保全）。
- **真因**: `ModalBottomSheet` 内蔵の `ConsumeSwipeWithinBottomSheetBoundsNestedScrollConnection` が内容スクロールの境界フリング残速度を `onPostFling`→`anchoredDraggableState.settle` へ渡し、そのバネアニメが `minAnchor` を超えてオーバーシュートする（`ModalBottomSheet_androidKt`/`SheetDefaultsKt` を逆コンパイルして確認）。**内容ピクセルは漏れていない**＝`verticalScroll` は foundation 1.6.6 の `clipScrollableContainer` で上下端を厳密クリップ（Vertical=`Rect(-30dp,0,w+30dp,h)`／clip が `overscroll` の外側・`Surface(shape=ExpandedShape)` も角丸クリップ）。sheetState 側は正しい（中間落下・アンカー飛び越えは `1753211`＝`skipPartiallyExpanded=true`+`imePadding` で解消済み）＝**触らない**。
- **棄却済み候補A〜D（再試行の無駄防止）**: (A) 自前 `NestedScrollConnection` で `onPostFling` 上下両方向消費＋`onPostScroll` 下方向消費→**枠固定は成功**だが速いフリック端で内容の弾みが消え「スッと止まる」＝使用感低下で撤去。(B) (A)＋自前 overscroll へ `applyToFling` で残速度→ビルド可も実機で感触届かず撤去。(C) `scrimColor=background` で覗きを覆う→シートの tonalElevation と地色の差で二枚重ねに見え違和感で撤去。(D) シート下方拡張で隙間を覆う→ModalBottomSheet は内容縦スクロール時に Surface 高を fullHeight でキャップするため画面下端より下へ伸ばせず不可。（誤）`onPostFling` を下方向だけ消費→症状は上方向のため無効果。
- **本命の解**: 「上部の枠は固定・内容だけ自然に弾ませる」＝内容スクロールの overscroll/ディスパッチをシート内蔵接続から分離（`verticalScroll` を独自 `overscrollEffect`＋分離 dispatcher の `scrollable` に置換等）。内蔵接続が境界フリングを必ず奪う仕様上、素直には両立しない踏み込んだ実装。要 UI-n 意匠確認（静けさ意匠との整合）。
- ⚠️ 上記知見は **material3 1.2.1／foundation 1.6.6 時点**。2026-07-08 の main 統合で **BOM 2025.02.00（material3 1.3.1・foundation 1.7系＝`bcb5216`）** へ更新済み → 再挑戦時はまず 1.3.1 での現象再現と内蔵 NestedScrollConnection 実装の差分確認から始めること。
- **【2026-07-12 増補・1.3.1 判定＝上流解消】** material3 1.3.1 の公式 sources jar（Google Maven 直取得）を 1.2.1 と突合して確定: **1.3.0-alpha02 で settle の既定アニメが spring→tween(300ms, FastOutSlowInEasing) へ差し替わり**（`SheetDefaults.kt` L433-435 の private `BottomSheetAnimationSpec`）、tween は初速を「行き過ぎ」に使わないため**枠オーバーシュートは発生しない**（＝クランプ追加ではなくスペック変更による解消）。骨格は温存: 内蔵接続は健在（onPostFling→`onFling`→`settle` の残速度受け渡しに配線変更・上向きは onPreFling で全消費）、`animateTo`→`dragTo` の**非クランプ offset 書き込みも残存**（androidx コメントが「アニメの overshoot は意図的に許す」と明言）。スペックは private 固定で公開 API から差し替え不可＝通常利用で再発経路なし。ただし**独自に spring 系スペックを差し込む改造をすれば再発する**。注意: 1.3.1 は foundation ではなく **material3 内蔵コピー（`androidx.compose.material3.internal.AnchoredDraggableState`）**を使う＝将来の追従調査は material3 側を見ること。「本命の解」（内容スクロールの分離 dispatcher/overscroll 化）は**不要と判定**。なお「シートが全画面になった」というユーザー観測は、条件項目の増加で `skipPartiallyExpanded` シートが画面上限まで伸びていただけ（全画面化コミットは不存在）＝モック `.sheet` の max-height:85% への追従（`heightIn` 上限＝`da42089`）で部分シートの見た目を復元。

#### 57. ModalBottomSheet の modifier に高さ制約を渡すとシートが「画面上端」に張り付く（アンカー計算の constraints が縮む）  ★★★

条件シートへモックの max-height:85% を翻訳する際、`ModalBottomSheet(modifier = Modifier.heightIn(max = 画面85%))` と書いたら、実機でシートが**画面上端に張り付き下15%が空く**「天井シート」になった（2026-07-12 実機発現・material3 1.3.1 sources 直読で機序確定）。
- **機序**: ModalBottomSheet の `modifier` は内部 Surface のチェーン**先頭**に合成される（`modifier.align(TopCenter).widthIn(...).nestedScroll(...).draggableAnchors{...}`＝`ModalBottomSheet.kt` L224-236）。`draggableAnchors` はアンカーを `fullHeight = constraints.maxHeight` から計算するが、この constraints は**自分より外側（＝ユーザー modifier を含む）で縮められた後の値**。85% 制約下では fullHeight=画面の85%・シート実高も85%になり **`Expanded at max(0f, fullHeight - sheetSize.height)` = 0**。placement は `align(TopCenter)` 基準＋offset のため、offset=0 のシートはそのまま画面最上部へ置かれる。
- **正解**: 高さ上限は**シートの modifier でなく内容側（content の Column 等）に掛ける**。Surface は内容を wrap し、draggableAnchors の constraints は全画面のまま → Expanded アンカー = 画面高−シート高 ≈ 15% で正しく下端アンカーの部分シートになる。
- 教訓: material3 コンポーネントの `modifier` 引数は「ルート要素の装飾」であり、内部レイアウト計算（アンカー・オフセット）の座標系ごと歪める。**サイズ制約系（heightIn/fillMaxHeight 等）を modal 系の modifier に渡す前に、内部が constraints から何を計算しているか確認**すること。

#### 53. WorkManager 周期ジョブ（KEEP）は初回サイクル消化後 `cmd jobscheduler run -f` では doWork が発火しない（強制再現は workdb リセット一択）  ★★

2026-07-10 U1 新着話チェック（`PeriodicWorkRequest` 24h・KEEP・WorkManager 2.9.1 / Android 16 ColorOS）の実機E2Eで確定。
- **事実1**: `cmd jobscheduler run -f com.novelreader <id>` は `Running job [FORCED]` を返すが、**期限未到来の periodic WorkSpec は WM がディスパッチせず doWork が走らない**（logcat の WM 系タグ皆無・DB 副作用なしで確認）。JobScheduler 層の強制実行は WM 内部の周期判定（periodStartTime）を貫通しない。
- **事実2**: 確実な即時再実行は、force-stop → `run-as` で WM 自身の状態DB `no_backup/androidx.work.workdb{,-wal,-shm}` を削除 → アプリ再起動。`Application.onCreate` の `enqueueUniquePeriodicWork(KEEP)` がフレッシュ enroll になり、**周期 Work の初回は initialDelay 無しなら即時実行**される。アプリ本体DB（novel_reader_db）とは別ファイルで蔵書無傷・workdb は WM が自動再生成。
- **事実3（副作用の罠）**: `am instrument`（androidTest）もアプリプロセスを起動して `Application.onCreate` を走らせるため、周期 Work の登録＋初回即時実行が**テスト実行の副作用として**先回りで起こる（U1 E2E では MigrationTest 実行の数分後に無音初期化が完了済みで、後段の「marks は空のはず」前提が崩れた）。実機で「初回挙動」を検証する台本は instrument の実行順を考慮すること。

#### 54. Android ターゲットの Kotlin はバッククォートのテスト名でも `.` を不正文字として拒否する（純 JVM の緩和が効かない）  ★

2026-07-11 ShelfItemsTest で実測（AGP 8.6.1）。テスト名に `chap_N.html` / `index.html` を含めたら `compileDebugUnitTestKotlin` が `Name contains illegal characters: .` で失敗。
- **機序**: `testDebugUnitTest` は JVM 実行だが **Android バリアントとしてコンパイル**されるため、dex 互換の識別子制約（`.` `;` `[` `]` `/` 等の禁止）がバッククォート名にも課される。純 JVM モジュールなら通る書き方なので、他プロジェクトの流儀を持ち込むと Android 側だけ落ちる。
- **対処**: テスト名にファイル名・拡張子・小数（「1.5倍」等）を書きたいときは `.` を外した言い換えにする（例:「chap_N 形式の章ファイル名は…」）。

#### 55. `limitedParallelism(1)` への launch は DB 着地順を直列化しない（`withContext`/Room suspend DAO の再ディスパッチでスロットが手放される）  ★★

2026-07-11 pending_jobs 直列化の健全性監査（反証エージェント CONFIRMED）→ Mutex 化 `cccb4dc` で確定。
- **機序**: `Dispatchers.IO.limitedParallelism(1)` の単一スロットは「そのディスパッチャ上で同時に走るコルーチン数を1に絞る」だけ。コルーチンが `withContext(Dispatchers.IO)` で別ディスパッチャへ移った瞬間、また Room の suspend DAO が内部 executor へ再ディスパッチした瞬間にスロットは解放され、後続の仕事が追い越せる＝**「launch 順＝DB 着地順」は保証されない**。
- **帰結**: 「並列度1ディスパッチャへ launch すれば FIFO 直列化」という設計は、仕事が終端まで同ディスパッチャに留まる場合にしか成立しない。Room・`withContext` が挟まる実務コードではほぼ不成立（本件では「PDF投入直後に停止」で insert が deleteAll を追い越し、破棄済みジョブが復活する窓になっていた）。
- **対処**: suspend 呼び出しの完了までロックを保持する `Mutex.withLock` が正しい排他（ディスパッチャ非依存）。順序も要るなら main スレッド起点の launch 順＋Mutex の FIFO 公平性で担保する。

## 移設マッピング（旧 Part II / Part III の固定ID対応）

> 旧エントリ番号（`§N`）は固定ID。本ファイルから `docs/` へ移設したもの、および重複採番の解消で再採番したものは下表で追跡する（移設先での再採番はしない）。

| 旧ID | 内容 | 移設先 |
|---|---|---|
| #30（重複・後発側） | セッション・トランスクリプト JSONL の構造と「実行捏造」の形 | 同ファイル `#42` へ再採番（2026-07-05 に別ブランチで #30 が二重採番されマージで衝突→2026-07-07 解消。既存参照「#30-32」は全て先発の pdfbox 側を指すため先発側を維持） |
| #28（重複・Compose側） | getLineTop は「行ボックス上端」であり字面上端ではない | 同ファイル `#43` へ再採番（2026-07-02 の別ライン採番（単発修正バッチ↔lab知見移植）が衝突→2026-07-07 解消。フック側 #28 が先発（178f1fd）かつ CLAUDE.md 規約・ADR 0004/0006・hook コード注釈の計8箇所に定着済みのため維持し、STATUS 参照3件のみの Compose 側を移動・張り替え） |
| #42（重複・なろうAPI側） | type のハイフンOR指定はサイレント無視 | 同ファイル `#46` へ再採番（2026-07-07 に main メタ系（eaa4b23 02:56＝JSONL構造）と api-lab 系（8d09e7a 12:01）が同日別ラインで #42 を採番→2026-07-08 の main 統合で衝突。先発の main 側を維持し、参照2件（STATUS-api-lab・architecture スキル）を張り替え） |
| #44（重複・なろうAPI側） | レスポンスキー名が of 指定で変わる（noveltype↔novel_type） | 同ファイル `#47` へ再採番（同上＝main 側 #44（fail-open 陽性コントロール）が先発のため維持。#45 は api-lab-ai-3 のなろう規約エントリが使用済みのため欠番にしない） |
| #39（重複・フック配線側） | settings.json の hooks 配線変更はセッション再起動まで無反映 | 同ファイル `#48` へ再採番（2026-07-08 統合で main（a22cccb・先発）と api-lab 系（4aaf14e・Room version 衝突）の二重採番が衝突。フック側が先発だが参照は台帳内4件のみで、Room 側はコード注釈（AppDatabase.kt）・db-migration スキル・STATUS-api-lab・不変のコミットメッセージ（06d0fe7 等）に定着済みのため、**例外的に先発側を移動**（張り替えゼロ＝参照切れ最小化を優先） |
| #43（重複・なろうAPI側） | KDoc 内の `[...]` はリンク構文としてパースされ範囲表記がコンパイルエラー | 同ファイル `#49` へ再採番（2026-07-07 に Compose 側の #28→#43 **再採番**と api-lab 系の #43 **新規採番**が同日別レーンで発生し統合で衝突→2026-07-08 stale-check の diary_id 検査で検出・解消。getLineTop 側は STATUS 参照3件＋本表 #28 行に定着済みのため維持し、STATUS-api-lab 参照1件のみのなろう側を移動。教訓＝**再採番の移動先も新規採番と同じく全レーン確認の対象**） |
| §20 | Atomic Commit は実装順序から設計する | `docs/decisions/0003-atomic-commit-from-impl-order.md` |
| §21 | ProcessingState への一本化 | `docs/patterns/processing-state.md` |
| §22 | Hilt / UseCase 層 不採用（Why-not） | `docs/decisions/0001-no-hilt.md` ・ `docs/decisions/0002-no-usecase-layer.md` |
| §23 | Service 内キュー + シングルループ処理 | `docs/patterns/service-queue-loop.md` |
| §24 | TopAppBar オーバーレイ化 + NestedScrollConnection 非消費 | `docs/patterns/topappbar-overlay.md` |
