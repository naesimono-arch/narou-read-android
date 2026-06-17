# 開発知見メモ

> **重要度凡例**: ★★★ Critical（バグ/クラッシュ/動作不可に直結） ★★ Important（特定条件下で問題発生）

---

## Android — 通知 / ForegroundService / バックグラウンド

### 1. 通知アイコンはアプリ固有リソース必須  ★★★

`android.R.drawable.*` などシステムドローアブルは Android 5以降の通知アイコンに使用不可。
`startForeground()` が例外を投げてサービスごとクラッシュする（通知も出ない）。

**対策**: `res/drawable/` に白単色シルエットの vector drawable を作成して使う。

---

### 2. OEMによってはContentIntentがないと通知をブロックする  ★★★

OPPO/ColorOS など一部OEMは `setContentIntent()` がない通知を表示しないことがある。

**対策**: 全通知に `setContentIntent(openAppIntent())` を付与する。

---

### 3. API 34以降はstartForegroundに型指定が必要  ★★★

```kotlin
ServiceCompat.startForeground(
    this, NOTIFICATION_ID, notification,
    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
)
```

---

### 4. OPPO/ColorOSのバックグラウンド制限はForegroundService + WakeLockでも不十分  ★★

Android標準の `startForeground()` + `PARTIAL_WAKE_LOCK` だけでは ColorOS がプロセスを
数秒で強制停止する。根本的な解決はデバイス側の設定変更が必要。

**設定パス**: 設定 → バッテリー → アプリごとの消費管理 → 対象アプリ → バックグラウンドアクティビティを許可

---

### 5. ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS はOPPOで誤動作する  ★★★

`Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` がファイルピッカー等に
誤ルーティングされる。

**対策**: `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` でアプリ詳細設定を開き、
ユーザーに手動で設定してもらう。

---

### OPPO/ColorOS 固有まとめ

| 症状 | 参照 |
|------|------|
| 通知が表示されない | §2（ContentIntent必須） |
| バッテリー最適化除外の画面遷移が壊れる | §5（ACTION_APPLICATION_DETAILS_SETTINGS を使う） |
| FGS + WakeLockでもプロセスが停止する | §4（根本解決はユーザー設定のみ） |

---

## URI / パーミッション

### 6. content:// URIをServiceに渡す際はFLAG_GRANT_READ_URI_PERMISSIONが必要  ★★★

ActivityでPickしたURIをそのままServiceのIntentに渡すと SecurityException が発生する。

```kotlin
intent.data = uri
intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
```

---

## Jetpack Compose / State管理

### 7. Composableの状態変数は参照する前に宣言する  ★★

ラムダ内で `showBatteryOptDialog = true` のように参照する変数は、
そのラムダより**前**に `remember { mutableStateOf(...) }` で宣言しないと
`Unresolved reference` コンパイルエラーになる。

---

### 8. SharedPreferencesをリアルタイムに反映するには mutableStateOf を使う  ★★

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

### 9. BackHandler の多段階戻り設計  ★★

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

### 10. Compose LazyColumn で lineHeight を複数Composable間に一貫適用する  ★★

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

## Chaquopy / Python統合

### 11. Chaquopyで使えるのは純Pythonパッケージのみ  ★★★

C拡張を含むパッケージ（PyMuPDF/fitz等）はChaquopyのpipがWindowsホスト上でクロスビルドを試みるが、
MSVCがなければビルド失敗。`pdfminer.six`（純Python）のような代替を選ぶこと。

---

### 12. Python → Kotlin コールバックは fun interface（SAM）を使う  ★★

Chaquopy 15.0.1 では `fun interface` を Python から直接 `callback(percent, phase)` として呼び出せる。
IOスレッドから呼ばれるが `MutableStateFlow.value =` への代入はスレッドセーフ（`withContext(Main)` 不要）。

---

### 13. Chaquopyのcallattr()はキャンセル不能 → NonCancellable必須

`callAttr()` は JNI の同期ブロッキング呼び出しのためコルーチンキャンセル不能。
Python処理 + DB登録を `withContext(NonCancellable)` でラップし、キャンセル不能であることを明示。
`ensureActive()` は NonCancellable ブロックの**外**で呼ぶこと（内側では機能しない）。

---

### 14. Chaquopyの例外はPyExceptionにラップされる → クラス名で判定  ★★

Python で `raise EncryptedPdfError("...")` すると、Kotlin側では `PyException` としてラップされ、
`e.message` は `"builtins.EncryptedPdfError: ..."` のようにクラス名が先頭に含まれる。
`e is PyException && e.message.contains("クラス名")` で安全に判定できる。
マーカー文字列方式（`ERROR_ENCRYPTED:` 等）はスタックトレース全体を含む文字列になるため誤検出リスクあり。

---

### 15. pdfminer.six の Y軸は下原点（PyMuPDF と逆）

- PyMuPDF: 上原点（`top` = ページ上端からの距離）
- pdfminer: 下原点（`y0`, `y1` = ページ下端からの距離）

変換: `top = page_height - y1` で既存ロジックをそのまま再利用できる。

---

### 16. callAttr() 引数ミスマッチは無音失敗する  ★★★

`callAttr()` は引数の型チェックをしない。Python関数定義の引数の数と完全に一致させること。
ミスマッチ時は Python 側で `TypeError` が発生するが、Chaquopy は PyException を logcat に自動出力しない。
`runCatching + classifyError()` パターンが `Log.e()` なしで例外を吸収すると原因が永遠に見えない。

**対策**:
- `runCatching` を使う箇所には必ず `onFailure` 内に `Log.e()` を入れる
- logcat に E レベルのアプリエラーが出ない場合、「例外がどこかで catch されてログ出力されていない」を疑う

---

## ビルド設定（AGP / Gradle / Compose BOM）

### 17. AGP と Gradle のバージョン互換性マトリクス

| AGP | 対応 Gradle |
|-----|-------------|
| 8.1.x | 8.0〜8.1 |
| 8.6.x | 8.7+ |

Gradleダウングレードより**AGPアップグレード**の方がAndroid Studioのキャッシュ問題を回避できて確実。
現在の構成: AGP 8.6.1 + Gradle 8.9 + Kotlin 1.9.22 + Compose Compiler 1.5.10 + Compose BOM 2024.04.01。

---

### 18. gradlew はリポジトリに必ずコミットすること  ★★★

`gradlew` / `gradlew.bat` / `gradle-wrapper.jar` が未コミットだとCLIビルド不可（Android Studioは動くが紛らわしい）。
生成コマンド: `gradle wrapper`（Gradleのローカルインストール or `~/.gradle/wrapper/dists/` のキャッシュが必要）。

---

## Room / DB

### 19. Room Migration 前に PRAGMA table_info でカラムを確認する  ★★★

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

## アーキテクチャパターン

### 20. Atomic Commitは実装順序から設計する

複数コミットに分けることを事前に決めていた場合は、**コミット単位に合わせた実装順序**で進める。
（例: まず③④のファイルのみ変更してコミット → 次に⑦のファイルを変更してコミット）
後から `git add -p` で分割しようとすると、異なる変更が同一ハンクになって分割不能になることがある。

---

### 21. ProcessingStateへの一本化パターン

`_isProcessing: Boolean` を `ProcessingState(isProcessing, percent, phase)` に置き換えると、
「処理中かどうか」「何%か」「どのフェーズか」を単一のStateFlowで管理でき、UI側の collectAsState も1箇所で済む。
try/finally で成功・失敗いずれの場合も `ProcessingState()` にリセットされるよう保証すること。

---

### 22. 意図的に採用しなかったアーキテクチャとその理由

#### Hilt（DIフレームワーク）
**不採用**。依存グラフが `Application → Repository → ViewModel` の一直線に近く、手動DIで10分以内に管理可能な規模。

#### UseCase層（Clean Architecture的な中間層）
**不採用**。ビジネスロジックの大部分がPython（`app.py` 以下）にカプセル化されており、KotlinはUseCase層を設けても `repository.xxx()` を呼ぶだけの薄いラッパーになる。
ViewModel → Repository 直結の素直なMVVMを採用。

---

### 23. Service内キュー+シングルループ処理パターン  ★★

複数の URI が短時間に `onStartCommand()` に来ても無言破棄せず直列処理するパターン。

```kotlin
private val lock = ReentrantLock()
private val uriQueue = ArrayDeque<Uri>()
private var isLoopRunning = false

override fun onStartCommand(intent: Intent?, ...): Int {
    val uri = intent.data ?: return START_NOT_STICKY
    val shouldStart = lock.withLock {
        uriQueue.add(uri)
        if (!isLoopRunning) { isLoopRunning = true; true } else false
    }
    if (shouldStart) startProcessingLoop()
    return START_NOT_STICKY
}
```

**設計のポイント**:
- `lock.withLock {}` で「追加+起動判定」と「取り出し+終了判定」をアトミック化することで競合ゼロ
- `isLoopRunning` フラグで多重起動を防止。ループ終了時に `isEmpty()` の確認と同一ロックで行う
- WakeLock はフィールドではなくローカル変数で管理（フィールド共有だと旧ループが誤解放するリスクがある）
- **WakeLock は「ループ全体で1回」ではなく「PDF 1件ごと」に acquire/release する**。`acquire(10*60*1000)` の10分上限はバッチ総処理時間とは無関係で、ループ単位で1度だけ取ると複数 PDF の合計が10分を超えた時点で自動解放され、OPPO 等にバックグラウンド kill されて残り PDF が孤立する（§4 の WakeLock 不十分問題とは別軸の「取得粒度」の話）
- ループが例外で破綻した場合の finally ブロックで `isLoopRunning = false` のフェイルセーフが必要

コード: `PdfProcessingService.kt`（65abfe4 で導入）

---

### 24. TopAppBar オーバーレイ化 + NestedScrollConnection 非消費パターン  ★★

`enterAlwaysScrollBehavior` をそのまま `Scaffold` に渡すと、スクロールを横取りして
LazyColumn の `contentPadding` が再計算され本文が揺れる問題がある。

**解決パターン**: `Scaffold` の外側の `Box` に TopAppBar をオーバーレイで重ね、
バーの動きは `graphicsLayer { translationY }` で制御する。

```kotlin
Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.nestedScroll(nonStealingConnection),
        // TopAppBar は Scaffold の topBar に渡さない
    ) { ... }

    TopAppBar(
        modifier = Modifier.graphicsLayer {
            translationY = topAppBarState.heightOffset
        },
        scrollBehavior = scrollBehavior, // heightOffsetLimit 計測のために維持
    )
}
```

**NestedScrollConnection の実装方針**:
- `onPreScroll`: 下スクロール時にバーを追従させるが `Offset.Zero` を返して消費しない
- `onPostScroll`: 上スクロール時は本文が実際に動いた分だけバーを復元
- `onPostFling`: 慣性終了後に `settleTopBar()` を呼んで全表示/全非表示へスナップ

標準の snap は消費戦略と一体化しているため自前実装が必要。
`scrollBehavior = null` にすると `heightOffsetLimit` が測定されず追従計算が壊れるため、
`scrollBehavior` は引き続きバーに渡し続けること。

コード: `NativeReadingScreen.kt`（8a27999, 2662bf6 で導入）
