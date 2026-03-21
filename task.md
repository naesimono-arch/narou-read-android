# Android開発 知見メモ

> **重要度凡例**: ★★★ Critical（バグ/クラッシュ/動作不可に直結） ★★ Important（特定条件下で問題発生）

---

<!-- ─── 通知 (Notification) ─── -->

## 1. 通知アイコンはアプリ固有リソース必須  ★★★

`android.R.drawable.*` などシステムドローアブルは Android 5以降の通知アイコンに使用不可。
`startForeground()` が例外を投げてサービスごとクラッシュする（通知も出ない）。

**対策**: `res/drawable/` に白単色シルエットの vector drawable を作成して使う。

---

## 2. OEMによってはContentIntentがないと通知をブロックする  ★★★

OPPO/ColorOS など一部OEMは `setContentIntent()` がない通知を表示しないことがある。

**対策**: 全通知に `setContentIntent(openAppIntent())` を付与する。

---

<!-- ─── ForegroundService / バックグラウンド実行 ─── -->

## 3. API 34以降はstartForegroundに型指定が必要  ★★★

```kotlin
ServiceCompat.startForeground(
    this, NOTIFICATION_ID, notification,
    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
)
```

---

## 4. OPPO/ColorOSのバックグラウンド制限はForegroundService + WakeLockでも不十分  ★★

Android標準の `startForeground()` + `PARTIAL_WAKE_LOCK` だけでは ColorOS がプロセスを
数秒で強制停止する。根本的な解決はデバイス側の設定変更が必要。

**設定パス**: 設定 → バッテリー → アプリごとの消費管理 → 対象アプリ → バックグラウンドアクティビティを許可

---

## 5. ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS はOPPOで誤動作する  ★★★

`Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` がファイルピッカー等に
誤ルーティングされる。

**対策**: `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` でアプリ詳細設定を開き、
ユーザーに手動で設定してもらう。

---

<!-- ─── パーミッション / URI ─── -->

## 6. content:// URIをServiceに渡す際はFLAG_GRANT_READ_URI_PERMISSIONが必要  ★★★

ActivityでPickしたURIをそのままServiceのIntentに渡すと SecurityException が発生する。

```kotlin
intent.data = uri
intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
```

---

<!-- ─── Jetpack Compose / State管理 ─── -->

## 7. Composableの状態変数は参照する前に宣言する  ★★★

ラムダ内で `showBatteryOptDialog = true` のように参照する変数は、
そのラムダより**前**に `remember { mutableStateOf(...) }` で宣言しないと
`Unresolved reference` コンパイルエラーになる。

---

## 8. SharedPreferencesをリアルタイムに反映するには mutableStateOf を使う  ★★

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

<!-- ─── OEM固有まとめ（OPPO/ColorOS）横断参照 ─── -->

## OPPO/ColorOS 固有の落とし穴まとめ

| 症状 | 参照 |
|------|------|
| 通知が表示されない | §2（ContentIntent必須） |
| バッテリー最適化除外の画面遷移が壊れる | §5（ACTION_APPLICATION_DETAILS_SETTINGS を使う） |
| FGS + WakeLockでもプロセスが停止する | §4（根本解決はユーザー設定のみ） |
