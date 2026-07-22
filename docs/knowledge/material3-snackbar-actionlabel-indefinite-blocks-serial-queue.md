# Material3 showSnackbar は actionLabel 付きだと Indefinite 既定＝直列キューを塞ぐ

**事象（2026-07-20 実機観察→2026-07-23 真因確定）**: Web取込で「取り込み中です…」が取込完了後も
残留し、完了メッセージが表示されない（遅れて埋もれる）。

**機序**:
1. Material3 の `SnackbarHostState.showSnackbar(message, actionLabel)` は、`actionLabel != null` のとき
   `duration` の既定が `SnackbarDuration.Indefinite` になる（明示指定しない限りユーザーが閉じるまで消えない）。
2. `showSnackbar` は表示中ずっと suspend する。単一の `collect { showSnackbar(...) }` でイベントを直列消費する
   設計（本アプリの `errorEvents`＝`Channel(BUFFERED)`）では、Indefinite 1件が collect を塞ぎ、
   後続イベント（完了通知など）が Channel バッファで待機し続ける。
3. 結果、「進行中」を Indefinite スナックバーで出す設計は〈残留＋後続埋没〉の複合を構造的に生む。
   複数重複時の「閉じた直後に即再表示」（2026-07-16 実機切り分け）も同根。

**対処パターン（本アプリの採用形）**:
- **進行中表示をスナックバーに載せない**。継続状態は非ブロッキングの専用機構（本アプリでは
  `ProcessingState`→ProcessingBanner・全スキン `isProcessing` 駆動）へ寄せ、開始 set→`finally` で確実 clear。
- スナックバーは**完結した出来事の通知のみ**にし、自動消滅してよいものは actionLabel に頼らず
  Short を明示（本アプリでは `AppErrorEvent.transient` フラグで分岐）。
- 「閉じる」アクションが要るのはユーザーの判断・回復操作を伴うイベント（Blocked 公式送り等）だけ。

**根拠**: `BookshelfViewModel.importWebNovel`／`BookshelfScreen` の errorEvents collect（修正コミットの diff が正本）。
PDF 取込は当初から ProcessingBanner 方式でこの罠を回避しており症状が出ていなかった（対比が真因の傍証）。
