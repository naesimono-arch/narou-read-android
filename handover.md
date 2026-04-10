# 引継ぎ：未対応タスク

> 最終更新: 2026-04-10
> コスト対リターン比 → UX優先 → 品質 → 長期 の軸で並べた版。
> 対応済み（③④⑤⑦）の詳細は `task_diary.md` の 2026-03-29 エントリを参照。

---

## Quick Win — 今すぐやる（工数小）

### pre-commit hook による Room スキーマ変更ガード（〜30分）

**背景**
2026-03-30 に `*Entity.kt` のカラムリネームで `version` UP と Migration を付け忘れ、アプリが即クラッシュした（詳細は `task_diary.md` 同日エントリ）。CLAUDE.md への記載だけでは再発防止に限界があるため、機械チェックを追加する。

**実装方針**

`.git/hooks/pre-commit` に追加：

```bash
#!/bin/sh
# Entity.kt が変更されていたら AppDatabase.kt も変更されているか確認する
entity_changed=$(git diff --cached --name-only | grep -E 'Entity\.kt$')
if [ -n "$entity_changed" ]; then
  db_changed=$(git diff --cached --name-only | grep 'AppDatabase.kt')
  if [ -z "$db_changed" ]; then
    echo "ERROR: *Entity.kt が変更されていますが AppDatabase.kt が変更されていません。"
    echo "       version を上げ、Migration を追加してからコミットしてください。"
    exit 1
  fi
fi
```

**検出範囲**

| チェック項目 | 検出可否 |
|---|---|
| Entity 変更時に AppDatabase.kt を変更し忘れる | ✅ |
| version の数値が実際に増えているか | △（追加チェックが必要） |
| Migration SQL の前提（カラム名等）が正しいか | ❌（推論エラーは機械検出不能） |

**難度**: ★☆☆☆☆　**工数**: 〜30分

---

### AtomicBoolean → Channel キューイング（〜半日）

**問題**
処理中に2冊目の PDF を選択すると `isProcessing.compareAndSet(false, true)` が `false` を返して**無言で破棄**される。ユーザーへのフィードバックがない。

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

> **注意**: WorkManager の導入は Chaquopy の 10 分実行制限に引っかかるリスクがあるため、
> Kotlin ネイティブ化が完了するまでは ForegroundService + Channel 方式が現実的。

**影響ファイル**: `PdfProcessingService.kt`（Channel 追加、通知更新ロジック）  
**難度**: ★★☆☆☆　**工数**: 〜半日

---

## 機能拡充 — UX・機能を育てる

### Phase 1: 触り心地の完成（UX磨き）

まずは「現在ある機能」の完成度を高め、手戻りを防ぐ。

1. **読書画面：題名の2段重複表示の解消**（最も簡単で見た目の効果が高い）
2. **読書画面：スライド時の題名格納アニメーション改善**（スクロール時の「不自然な吸着感」解消）
3. **本棚UI：スライドアニメーションのカクつき解消**（パフォーマンス起因かアニメーション設定の問題かを特定して修正）
4. **本棚UI：表示形式切り替えアニメーションの追加**（リスト/グリッド切り替え時のフェード実装）

---

### Phase 2: 文字サイズ変更

**読書アプリとして最重要の機能追加。** 設定値の永続化と UI への反映が必要。

---

### 読書画面：NativeReadingScreen の追加機能候補

Compose ネイティブ化（`366f9ea`）により、以下の機能が実装可能になった。

| 機能 | 実装アプローチ |
|------|--------------|
| ダークモード / セピアモード | `MaterialTheme` のカラースキーム切替 ＋ `SharedPreferences` 永続化 |
| 章内検索 | `AnnotatedString` の文字列検索 ＋ ハイライト `SpanStyle` 付与 |
| 読書進捗インジケータ | TopAppBar サブタイトルに「3 / 12章」表示 |
| **章内スクロール位置永続化** | 下記参照（Room Migration 必要） |

**章内スクロール位置永続化の技術詳細**

`LazyColumn` の復元には `firstVisibleItemIndex`（段落インデックス）と `firstVisibleItemScrollOffset`（ピクセルオフセット）の2値が必要。`ProgressEntity` にカラムを追加して DB を v5 → v6 に上げる。

```kotlin
// ProgressEntity.kt
@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey val bookId: String,
    val lastReadFilename: String,
    val scrollItemIndex: Int = 0,
    val scrollOffset: Int = 0,
)

// AppDatabase.kt  version = 6
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE progress ADD COLUMN scrollItemIndex INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE progress ADD COLUMN scrollOffset INTEGER NOT NULL DEFAULT 0")
    }
}
```

> **注意**: Migration 前に `PRAGMA table_info(progress)` で実際のカラム名を確認すること（`feedback_room_migration.md` 参照）。

**難度**: ★★☆☆☆　**優先度**: 低（章単位の進捗保存で実用上は十分）

---

### Phase 3: 外部連携（大型新機能）

内製機能が出揃った後に取り組む大型追加。

1. **内部ブラウザからの PDF 直接取り込み＆動線追加**（WebView 実装 ＋ ダウンロード→PDF処理サービスへの連携フロー）
2. **「小説家になろう」公式 API 連携・ランキング表示**（外部 API 通信 ＋ リスト UI の実装）

---

## 品質・保守

### テスト可能な設計へ（BookRepository インターフェース化）

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

**難度**: ★★★☆☆　**優先度**: 中（機能影響なし、品質改善）　**工数**: 1〜2日

---

## 長期・大規模

### Chaquopy → Kotlin ネイティブ化（＋完了後：並列処理）

**問題**
- APK サイズ +30〜50MB（Python ランタイム）
- 起動オーバーヘッド 800ms〜1.5s
- Python GIL により複数 PDF の並列処理が実質不可能
- Chaquopy の保守リスク

**修正方針**
`PDFBox-Android`（Apache 2.0）を使い、`processTextPosition()` で文字座標・フォント情報を取得して既存の Python ロジックを Kotlin で再実装する。

**最大の難所**
- 縦書き日本語の列復元（X 座標クラスタリング）
- ルビ対応付け（X 軸オフセット 14.84 での判定）
- pdfminer が暗黙に吸収していたエッジケース（不正 PDF・特殊フォント）

**工数感**: 「動く」は 3 日、「既存と同品質」は 2〜4 週間  
**難度**: ★★★★☆　**優先度**: 低（痛みが出た時に検討）

**完了後の次手**  
Kotlin ネイティブ化が完了すれば `Dispatchers.IO` の複数スレッドで真の並列処理が可能になり、Channel キューも `flatMapMerge` 等の並列キューに昇格できる。WorkManager 導入も現実的になる。
