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

---

## ⑨ pre-commit hook による Room スキーマ変更の機械的ガード

**背景**

2026-03-30 のセッションで、`*Entity.kt` のカラムリネームに `version` UP と Migration を付け忘れたことが原因で、アプリが即クラッシュする事態が発生した（詳細は `task_diary.md` の同日エントリを参照）。

Claude Code はルールを「知っている」が問題解決モード中に手続き的ルールへの注意が薄れるため、CLAUDE.md への記載だけでは再発防止に限界がある。また、hook による強制は「汎用的な機械チェック」には有効だが、「Migration SQL の前提が正しいか」のような推論エラーは hook では検出できない。それでも **発端となったルール違反（version を上げ忘れる）** は機械的に検出可能であるため、pre-commit hook で最低限のガードを設ける。

**実装方針**

`.git/hooks/pre-commit` に以下のチェックを追加する：

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

**このガードで検出できること / できないこと**

| チェック項目 | 検出可否 |
|---|---|
| Entity 変更時に AppDatabase.kt を変更し忘れる | ✅ 検出できる |
| version の数値が実際に増えているか | △ 追加チェックが必要 |
| Migration SQL の前提（カラム名など）が正しいか | ❌ 検出不可（推論エラーは機械チェック不能） |

**優先度**: 低〜中（工数 30 分、再発防止として有効だが必須ではない）

---

## ⑩ 機能追加とUI改善のロードマップ (2026-04-10作成)

**背景**
やりたいこと（パフォーマンス改善、UIアニメーション、新機能、外部連携）がバラバラに存在し、どこから手をつけるべきか見失いやすくなっていたため、機能駆動（垂直スライス）でユーザー体験に直結する順序でロードマップを整理した。

**開発フェーズ**

### Phase 1: クイックウィン＆UX改善（不快感の除去）
まずは「現在ある機能」の触り心地を完璧にし、手戻りを防ぐ。
1. **読書画面：題名の2段重複表示の解消**（最も簡単で見た目の効果が高い修正）
2. **読書画面：スライド時の題名格納アニメーション改善**（スクロール時の「不自然な吸着感」の解消）
3. **本棚UI：スライドアニメーションのカクつき解消**（パフォーマンス起因かアニメーション設定の問題かを特定し修正）
4. **本棚UI：表示形式切り替えアニメーションの追加**（リスト/グリッド等切り替え時のフェード実装）

### Phase 2: リーダー基本機能の拡充
5. **文字サイズの変換機能の追加**（読書アプリとして最重要の追加機能。設定値の永続化とUI反映）

### Phase 3: コンテンツ取得の外部連携（大きな新機能）
6. **内部ブラウザからのPDF直接取り込み＆動線追加**（WebView実装と、ダウンロード→PDF処理サービスへの連携フロー構築）
7. **「小説家になろう」公式APIの連携・ランキング表示**（外部APIとの通信処理、リストUIの実装の一気貫通）

### Phase 4: 全体最適化
8. **データ処理の効率化**（機能が出揃った段階で、PDF解析やDB処理のボトルネックを計測し、非同期処理などを最適化）

**進め方の原則**
バラバラに手をつけるのではなく、この順番に沿って**「1つのトピック（処理〜UI〜動線）を完全に終わらせてから次へ行く」**ことを徹底する。

---

## ⑪ 読書画面 Compose 化（Phase 4 完了）で解放された追加機能

**背景**
WebView 版では実現不可能だった機能が、Compose ネイティブ化により実装可能になった。
フォントサイズ変更は ⑩ Phase 2 に記載済み。以下はその他の候補。

### 実装候補

| 機能 | 実装アプローチ |
|------|--------------|
| ダークモード / セピアモード | `MaterialTheme` のカラースキーム切替＋`SharedPreferences` 永続化 |
| 章内検索 | `AnnotatedString` の文字列検索＋ハイライト `SpanStyle` 付与 |
| 読書進捗インジケータ | TopAppBar サブタイトルに「3 / 12章」表示 |
| **章内スクロール位置永続化** | 下記参照（Room Migration 必要） |

### 章内スクロール位置永続化の技術詳細

`LazyColumn` の復元には `firstVisibleItemIndex`（段落インデックス）と
`firstVisibleItemScrollOffset`（段落内ピクセルオフセット）の2値が必要。
`ProgressEntity` にカラムを追加し、DB を v5 → v6 に上げる。

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
// addMigrations(..., MIGRATION_5_6) に追加
```

**注意**: Migration 前に `PRAGMA table_info(progress)` で実際のカラム名を確認すること
（`feedback_room_migration.md` 参照）。

**難度**: ★★☆☆☆  **優先度**: 低（章単位の進捗保存で実用上は十分）
