# Compose は画面外へ退避したノードを a11y ツリーから除外する（没入UIのTalkBack不達の根）

**確定日**: 2026-07-16（実機 OPPO PGEM10・uiautomator dump で clickable ノード0を実測）

## 事実

- `graphicsLayer { translationY = … }` 等で**画面外へ動かしただけ**の Composable は、描画上は「隠れている」だけでも、Compose が AccessibilityNodeInfo ツリーから**ノードごと除外**する（TalkBack のスワイプ走査・uiautomator dump の両方から消える）。
- 本アプリでは読書画面の没入モード（クローム非表示）で上下バーを translationY 退避しており、戻る/目次/前後章/表示設定の到達手段が TalkBack ユーザーから完全に消えていた（本文段落ノードは残る）。

## 含意（同じ穴を踏むケース）

- 「アニメのために visibility でなく offset/translation で隠す」パターン全般が対象。**視覚的に隠すこと ≠ a11y から隠すこと、が逆向きに成立してしまう**（視覚は残したいのに a11y から消える）。
- `alpha = 0f` は semantics が残る（逆に不可視ボタンが a11y に残留する別問題を生む）＝退避方式の選択が a11y 露出を暗黙に決める。

## 本アプリでの対処（採用解）

- 視覚・タップ挙動を変えず、没入中だけ画面ルートへ `semantics { customActions = … }`（実ボタンと同一コールバック）を付与し、TalkBack のローカルコンテキストメニュー経由の到達を回復（`NativeReadingScreen.kt`・Robolectric semantics テストで固定）。
- クローム表示中は customActions を付けない（実ボタンとの二重発話回避）。

## 検証の作法

- **uiautomator dump では customActions は見えない**（AccessibilityNodeInfo の標準属性のみ）＝是正後の確認は Robolectric semantics テスト（`SemanticsActions.CustomActions`）か実 TalkBack の音声走査で行う。静的 dump だけ見て「まだ不達」と誤診しないこと。
