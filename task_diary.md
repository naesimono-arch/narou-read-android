# 開発知見メモ ＋ 作業日誌

> **前半（§1〜§17）**: 永続参照の重要知見
> **後半**: 作業日誌（日付順、新規エントリを追加していく）
>
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

### 7. Composableの状態変数は参照する前に宣言する  ★★★

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

## Chaquopy / Python統合

### 9. Chaquopyで使えるのは純Pythonパッケージのみ  ★★★

C拡張を含むパッケージ（PyMuPDF/fitz等）はChaquopyのpipがWindowsホスト上でクロスビルドを試みるが、
MSVCがなければビルド失敗。`pdfminer.six`（純Python）のような代替を選ぶこと。

---

### 10. Python → Kotlin コールバックは fun interface（SAM）を使う

Chaquopy 15.0.1 では `fun interface` を Python から直接 `callback(percent, phase)` として呼び出せる。
IOスレッドから呼ばれるが `MutableStateFlow.value =` への代入はスレッドセーフ（`withContext(Main)` 不要）。

---

### 11. Chaquopyのcallattr()はキャンセル不能 → NonCancellable必須

`callAttr()` は JNI の同期ブロッキング呼び出しのためコルーチンキャンセル不能。
Python処理 + DB登録を `withContext(NonCancellable)` でラップし、キャンセル不能であることを明示。
`ensureActive()` は NonCancellable ブロックの**外**で呼ぶこと（内側では機能しない）。

---

### 12. Chaquopyの例外はPyExceptionにラップされる → クラス名で判定  ★★

Python で `raise EncryptedPdfError("...")` すると、Kotlin側では `PyException` としてラップされ、
`e.message` は `"builtins.EncryptedPdfError: ..."` のようにクラス名が先頭に含まれる。
`e is PyException && e.message.contains("クラス名")` で安全に判定できる。
マーカー文字列方式（`ERROR_ENCRYPTED:` 等）はスタックトレース全体を含む文字列になるため誤検出リスクあり。

---

### 13. pdfminer.six の Y軸は下原点（PyMuPDF と逆）

- PyMuPDF: 上原点（`top` = ページ上端からの距離）
- pdfminer: 下原点（`y0`, `y1` = ページ下端からの距離）

変換: `top = page_height - y1` で既存ロジックをそのまま再利用できる。

---

## ビルド設定（AGP / Gradle / Compose BOM）

### 14. AGP と Gradle のバージョン互換性マトリクス

| AGP | 対応 Gradle |
|-----|-------------|
| 8.1.x | 8.0〜8.1 |
| 8.6.x | 8.7+ |

Gradleダウングレードより**AGPアップグレード**の方がAndroid Studioのキャッシュ問題を回避できて確実。
現在の構成: AGP 8.6.1 + Gradle 8.9 + Kotlin 1.9.22 + Compose Compiler 1.5.10 + Compose BOM 2024.04.01。

---

### 15. gradlew はリポジトリに必ずコミットすること  ★★★

`gradlew` / `gradlew.bat` / `gradle-wrapper.jar` が未コミットだとCLIビルド不可（Android Studioは動くが紛らわしい）。
生成コマンド: `gradle wrapper`（Gradleのローカルインストール or `~/.gradle/wrapper/dists/` のキャッシュが必要）。

---

## アーキテクチャパターン

### 16. Atomic Commitは実装順序から設計する

複数コミットに分けることを事前に決めていた場合は、**コミット単位に合わせた実装順序**で進める。
（例: まず③④のファイルのみ変更してコミット → 次に⑦のファイルを変更してコミット）
後から `git add -p` で分割しようとすると、異なる変更が同一ハンクになって分割不能になることがある。

---

### 17. ProcessingStateへの一本化パターン

`_isProcessing: Boolean` を `ProcessingState(isProcessing, percent, phase)` に置き換えると、
「処理中かどうか」「何%か」「どのフェーズか」を単一のStateFlowで管理でき、UI側の collectAsState も1箇所で済む。
try/finally で成功・失敗いずれの場合も `ProcessingState()` にリセットされるよう保証すること。

---

### 18. 意図的に採用しなかったアーキテクチャとその理由

#### Hilt（DIフレームワーク）
**不採用**。依存グラフが `Application → Repository → ViewModel` の一直線に近く、手動DIで10分以内に管理可能な規模。
Hiltの主目的は依存解決の自動化であり、テスタビリティはその副産物。プロトタイプ段階で単体テストより手動テストを優先しているため、テスト容易性のためにDIを整備する動機もない。

#### UseCase層（Clean Architecture的な中間層）
**不採用**。ビジネスロジックの大部分がPython（`app.py` 以下）にカプセル化されており、Kotlinは橋渡し役に徹している。
KotlinにUseCase層を設けても `repository.xxx()` を呼ぶだけの薄いラッパーになるため、間接層が増えるだけでメリットがない。
ViewModel → Repository 直結の素直なMVVMを採用。

---
---

# 作業日誌

<!-- 新規エントリは以下に追加。フォーマット: ## YYYY-MM-DD | タイトル -->

