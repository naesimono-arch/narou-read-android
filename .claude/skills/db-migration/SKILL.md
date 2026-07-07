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

## 作業前の必須確認

**スキーマ変更を始める前に必ず `AppDatabase.kt` を読んで現在の `version` を確認すること。**

```
android/app/src/main/java/com/novelreader/data/AppDatabase.kt
```

現在の version 番号はすぐ古くなるため、この手順書には固定値で書かない（AppDatabase.kt が唯一の正典）。末尾の「既存の Migration 履歴」表は過去の移行記録であり、最新 version の確認には使わないこと。

**あわせて並列 worktree の version 先取りも必ず確認する**（task_diary #39・実測クラッシュあり）:

```bash
grep -h "version = " ~/wt/*/android/app/src/main/java/com/novelreader/data/AppDatabase.kt | sort -u
```

別ブランチが同じ次期番号を別スキーマで既に消費している（＝実機がそのスキーマで migrate 済みの）場合、
同番号を名乗ると identity hash 不一致で起動即クラッシュする。その場合は**さらに +1 した番号へ退避し、
先行ブランチの Migration を同一内容で複製してパスを繋ぐ**こと（詳細は task_diary #39）。

## 手順

1. Entity クラスのフィールドを変更する
2. `AppDatabase` の `version` を +1 する（AppDatabase.kt を読んで現在値を確認すること）
3. `Migration` オブジェクトを書く（N = 現在version, N+1 = 新version）

```kotlin
val MIGRATION_N_N1 = object : Migration(N, N+1) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 変更前に必ず PRAGMA table_info で実際のカラム名を確認すること
        // 例: database.execSQL("ALTER TABLE books ADD COLUMN coverPath TEXT")
    }
}
```

4. `databaseBuilder` に追加する（既存のMigrationリストはAppDatabase.ktで確認）

```kotlin
Room.databaseBuilder(context, AppDatabase::class.java, "novel_reader_db")
    .addMigrations(..., MIGRATION_N_N1)
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

> 下表は**過去の移行記録**。現在の version と最新の Migration リストは必ず AppDatabase.kt を正典として確認すること（下表は追記漏れがありうる）。

| Migration | 内容 |
|-----------|------|
| 〜v3      | 歴史不明のため省略（v3 がゼロベース） |
| v3 → v4   | progress テーブルのカラムリネーム（MIGRATION_3_4）|
| v4 → v5   | books テーブルに author 列を追加（MIGRATION_4_5、ADD COLUMN DEFAULT ''）|
| v5 → v6   | progress テーブルに scrollIndex / scrollOffset 列を追加（MIGRATION_5_6、ADD COLUMN INTEGER DEFAULT 0、章内スクロール位置の永続化）|
| v6 → v7   | books に addedAt / progress に lastReadAt 列を追加（MIGRATION_6_7、ADD COLUMN INTEGER NOT NULL DEFAULT 0、蔵書追加日時・最終読書日時の記録）|
| v7 → v8   | pending_jobs テーブルを新規作成（MIGRATION_7_8、CREATE TABLE。version 8 は並列ブランチ feat/processing-resilience が先に消費。当ブランチでは未使用だが 7→8→9 の migration パス接続のため定義＝内容は resilience 側と一致必須・変更禁止。task_diary #39）|
| v8 → v9   | books に ncode 列を追加（MIGRATION_8_9、ADD COLUMN TEXT nullable。なろう作品の Nコード＝PDF↔Web継続読書〔発見機能の目玉①〕の紐付けキー。未紐付けが既定のため DEFAULT 句なし）|
