---
name: db-migration
description: Room DBのスキーマ変更手順。Entityを変更する際のMigrationオブジェクト作成・version管理・禁止事項を説明する。
triggers:
  - "Entityを変更したい"
  - "DBスキーマを変える"
  - "Migrationを書く"
  - "Room version"
---

# Room DBスキーマ変更手順

## 現在のバージョン

- **AppDatabase version = 4**（`android/app/src/main/java/com/novelreader/data/AppDatabase.kt`）
- 最新の適用済み Migration: `MIGRATION_3_4`（git commit 21ef32e で追加）
- 次回スキーマ変更時は **MIGRATION_4_5** を作成すること

## 手順

1. Entity クラスのフィールドを変更する
2. `AppDatabase` の `version` を +1 する（現在 4 → 次は 5）
3. `Migration` オブジェクトを書く

```kotlin
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 変更前に必ず PRAGMA table_info で実際のカラム名を確認すること
        // 例: database.execSQL("ALTER TABLE books ADD COLUMN coverPath TEXT")
    }
}
```

4. `databaseBuilder` に追加する

```kotlin
Room.databaseBuilder(context, AppDatabase::class.java, "novel_reader_db")
    .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
    .build()
```

5. スキーマ JSON が `android/app/schemas/` に自動出力される → **git に含めること**

## 禁止事項

- **`fallbackToDestructiveMigration()` は絶対に使用禁止**
  - 既存ユーザーの全データ（書籍一覧・読書進捗）が消える
  - Hook によりこの文字列を含む編集は自動ブロックされる

## Migration SQL を書く前の必須確認

実際のカラム名を仮定で書かず、必ず以下で確認してから書くこと（過去にクラッシュが発生した教訓）:

```sql
PRAGMA table_info(テーブル名);
```

Androidの `SupportSQLiteDatabase` でも `execSQL("PRAGMA table_info(books)")` で確認できる。

## 既存の Migration 履歴

| Migration | 内容 |
|-----------|------|
| 〜v3      | 歴史不明のため省略（v3 がゼロベース） |
| v3 → v4   | progress テーブルのカラムリネーム（MIGRATION_3_4）|
