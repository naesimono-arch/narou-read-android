---
name: db-migration
description: Room DBのスキーマ変更手順（Migration作成・version管理・禁止事項）。「Entityを変更したい」「DBスキーマを変える」「Migrationを書く」「Room version」等の依頼で使う。
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

**原則「実機に入れた版番号はそのレーンの専有」**（task_diary #39 追補の一般化）:
並列レーンの**双方**が実機検証を挟むと、番号を退避してもマージ合併時にエンティティ増で hash が変わり、
実機の同 version と**もう一段の再衝突**が起きる（対処＝合併でさらに +1 の no-op 再スタンプ。v9→v10 が実例）。
並列レーンでスキーマを触る間は**実機投入を片方のレーンに限定**できれば、この衝突クラス自体が発生しない。
着手前に「どちらのレーンが実機を握るか」を決めること。

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

> 下表は**過去の移行記録の索引**。現在の version と最新の Migration リストは必ず AppDatabase.kt を正典として確認すること（下表は追記漏れがありうる）。
> **各移行の why（採番の経緯・退避の理由・PRAGMA 分岐の要否・DDL の細部）は AppDatabase.kt の KDoc が正本**＝ここへ複製しない（二重管理は必ず片方が腐る）。

| Migration | 内容 |
|-----------|------|
| 〜v3      | 歴史不明のため省略（v3 がゼロベース） |
| v3 → v4   | progress のカラムリネーム（旧端末のみ PRAGMA 分岐でテーブル再作成） |
| v4 → v5   | books に author 追加 |
| v5 → v6   | progress に scrollIndex / scrollOffset 追加（章内スクロール位置） |
| v6 → v7   | books に addedAt・progress に lastReadAt 追加（recency ソートの材料） |
| v7 → v8   | pending_jobs 新設（処理キューの永続化＝強制終了からの再開） |
| v8 → v9   | books に ncode 追加（PDF↔Web 継続読書の紐付けキー）。並列レーンの v8 先取りで退避 |
| v9 → v10  | no-op 再スタンプ（マージ合併による identity hash 衝突の回避） |
| v10 → v11 | books に contentSha256 追加（別URI・同内容の再取込を変換前に遮断する内容指紋） |
| v11 → v12 | web_novels 新設（Web由来・未取込カードのメタ置き場） |
| v12 → v13 | new_episode_marks 新設（新着話チェックの前回通知済み基準値） |
| v13 → v14 | labels / book_labels 新設（v16 で撤去済み。migration パス保持のため定義は残置） |
| v14 → v15 | web_reading_progress 新設（なろうWebView読書の位置＝ADR 0012） |
| v15 → v16 | labels / book_labels を DROP（ラベル廃止＝読書状態の導出値へ置換） |
| v16 → v17 | no-op 再スタンプ（レーン合流時の hash 衝突回避・前例 v9→v10） |
| v17 → v18 | progress に reachedEnd 追加（読了「了」印の永続化） |
| v18 → v19 | books に shioriTipIndex / shioriLenFrac 追加（栞書影の個体差を取込時に焼き付け） |
| v19 → v20 | books に sourceUri 追加（取込元PDFの content://＝削除機能用） |
| v20 → v21 | books に sourceUrl / sourceSite 追加（Web取込元の作品URLとアダプタキー） |
