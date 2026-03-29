# 引継ぎ: 未対応アーキテクチャ負債

> 作成日: 2026-03-29
> 今回のセッションで洗い出したが対応できなかった課題の引継ぎ。
> 対応済み（③④⑤⑦）の詳細は `task_diary.md` の 2026-03-29 エントリを参照。

---

## ⑥ 単体テスト不能（DI なし・インターフェースなし）

**問題**
`BookRepository` がインターフェースを持たず、`PdfProcessingService` と `BookshelfViewModel` が具象クラスを直接参照している。`Python.getInstance()`（Chaquopy）と `AppDatabase.getDatabase()`（Room）が static シングルトンのため JVM 上の単体テストが書けない。

**修正方針**
1. `BookRepository` をインターフェース化
   ```
   interface BookRepository { ... }
   class BookRepositoryImpl(context: Context) : BookRepository { ... }
   ```
2. `NovelReaderApplication.repository` の型を `BookRepository`（インターフェース）に変更
3. テスト用 Fake 実装を作成して `PdfProcessingService` / `BookshelfViewModel` のテストを記述

**影響ファイル**
- `BookRepository.kt`（インターフェース抽出）
- `NovelReaderApplication.kt`（型変更）
- 新規: `FakeBookRepository.kt`（テスト用）

**難度**: ★★★☆☆
**優先度**: 中（機能影響なし、品質改善）

---

## ⑧ AtomicBoolean の多重起動防止 → Channel キューイングへ

**問題**
現在、処理中に2冊目の PDF を選択すると `isProcessing.compareAndSet(false, true)` が `false` を返して無言で破棄される。ユーザーには何のフィードバックもない。

**修正方針**
`Channel<Uri>(capacity = UNLIMITED)` でキューを持ち、Service 起動中は追加するだけにする。

```
onStartCommand()
  └─ uri を Channel に送信
処理ループ
  └─ Channel から受け取り順番に処理
  └─ 通知を「変換中 (1/2)」のように更新
  └─ キューが空になったら stopSelf()
```

**注意**: WorkManager の導入は Chaquopy の 10 分実行制限に引っかかるリスクがあるため、Kotlin ネイティブ化（①）が完了するまでは ForegroundService + Channel 方式が現実的。

**影響ファイル**
- `PdfProcessingService.kt`（Channel 追加、通知更新ロジック）

**難度**: ★★☆☆☆
**優先度**: 中（UX 改善）

---

## ① Chaquopy/Python → Kotlin ネイティブ化（長期）

**問題**
- APK サイズ +30〜50MB（Python ランタイム）
- 起動オーバーヘッド 800ms〜1.5s
- Python GIL により複数 PDF の並列処理が不可能
- Chaquopy の保守リスク

**修正方針**
`PDFBox-Android`（Apache 2.0）を使い、`processTextPosition()` で文字座標・フォント情報を取得して既存の Python ロジックを Kotlin で再実装する。

**最大の難所**
- 縦書き日本語の列復元（X 座標クラスタリング）
- ルビ対応付け（X 軸オフセット 14.84 での判定）
- pdfminer が暗黙に吸収していたエッジケース（不正 PDF・特殊フォント）

**工数感**: 「動く」は 3 日、「既存と同品質」は 2〜4 週間

**前提条件**: ①が完了すると②（並列処理）と WorkManager 導入も可能になる

**難度**: ★★★★☆
**優先度**: 低（今すぐやる必要なし、痛みが出た時に検討）

---

## ② 複数 PDF の並列処理（①依存）

**問題**
Python GIL により `Dispatchers.IO` で複数コルーチンから Chaquopy を呼んでも実質シリアル処理になる。

**修正方針**
① の Kotlin ネイティブ化が完了すれば、`Dispatchers.IO` の複数スレッドで真の並列処理が可能になる。その後 ⑧ の Channel キューも並列キュー（`flatMapMerge` 等）に昇格できる。

**難度**: ①完了後は ★★☆☆☆
**優先度**: ①完了後に検討

---

## 推奨対応順序

```
近いうち（工数小）
  ⑧  AtomicBoolean → Channel キューイング（UX 改善・半日）

次フェーズ（工数中）
  ⑥  インターフェース化 + テスト追加（品質改善・1〜2日）

将来（大規模）
  ①  Kotlin ネイティブ化 → ② 並列処理 → WorkManager 導入
```
