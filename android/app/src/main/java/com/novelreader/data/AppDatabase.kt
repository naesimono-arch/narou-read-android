package com.novelreader.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [BookEntity::class, ProgressEntity::class, PendingJobEntity::class],
    version = 11,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao
    abstract fun progressDao(): ProgressDao
    abstract fun pendingJobDao(): PendingJobDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** v3→v4: progress テーブルのカラム名を lastReadFilename に統一する。
         *
         *  v3 DB には2種類のスキーマが存在する:
         *  A) 旧ビルド端末: lastRead カラムが存在する → テーブル再作成で移行
         *  B) 新エンティティでフレッシュインストール済み端末: lastReadFilename が既に存在する → 変更不要
         *
         *  PRAGMA table_info で実在するカラムを確認して分岐する。
         *  minSdk 26 の SQLite 3.18.x は RENAME COLUMN 未対応のためテーブル再作成方式を採用。 */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val cursor = database.query("PRAGMA table_info(progress)")
                val nameIdx = cursor.getColumnIndex("name")
                var hasLastRead = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIdx) == "lastRead") { hasLastRead = true; break }
                }
                cursor.close()

                if (hasLastRead) {
                    // パターンA: lastRead → lastReadFilename へ移行
                    database.execSQL(
                        "CREATE TABLE `progress_new` " +
                        "(`bookId` TEXT NOT NULL, `lastReadFilename` TEXT NOT NULL, PRIMARY KEY(`bookId`))"
                    )
                    database.execSQL(
                        "INSERT INTO `progress_new` SELECT `bookId`, `lastRead` FROM `progress`"
                    )
                    database.execSQL("DROP TABLE `progress`")
                    database.execSQL("ALTER TABLE `progress_new` RENAME TO `progress`")
                }
                // パターンB: 既に lastReadFilename → テーブル変更不要
            }
        }

        /** v4→v5: books テーブルに author 列を追加する。
         *
         *  STEP 6 で BookEntity に author フィールドを追加したため、
         *  既存インストール端末の books テーブルに列が存在しない状態が発生する。
         *  ALTER TABLE で列を追加し、デフォルト値 '' で既存行を補完する。
         *  minSdk 26 の SQLite 3.18.x は ADD COLUMN をサポートしているため
         *  テーブル再作成は不要。 */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE books ADD COLUMN author TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v5→v6: progress テーブルに章内スクロール位置（scrollIndex / scrollOffset）を追加する。
         *
         *  読書再開時に章の途中から復元するための列。既存行は DEFAULT 0（章先頭）で補完する。
         *  新規追加のみで既存カラム名に依存しないため PRAGMA 分岐は不要（MIGRATION_4_5 と同方針）。
         *  minSdk 26 の SQLite 3.18.x は ADD COLUMN をサポートしている。 */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE progress ADD COLUMN scrollIndex INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE progress ADD COLUMN scrollOffset INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v6→v7: 本棚の「最近の活動順」ソート用に日時列を追加する。
         *  books.addedAt（追加日時）と progress.lastReadAt（最終読書日時）。
         *  既存行は DEFAULT 0 で補完するため、初回はタイトル順クラスタになり、
         *  以降の追加・読書で recency に浮上する。ADD COLUMN のみで既存カラム名に
         *  依存しないため PRAGMA 分岐は不要（MIGRATION_4_5/5_6 と同方針）。 */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE books ADD COLUMN addedAt INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE progress ADD COLUMN lastReadAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v7→v8: 処理キューの永続テーブル pending_jobs を新設する（強制終了からの再開材料）。
         *  既存テーブルへは一切触れない新規 CREATE のみのため PRAGMA 分岐は不要。
         *  DDL は Room が Entity から期待するスキーマ（NOT NULL・主キー）と厳密一致させること
         *  （不一致は起動時の schema validation でクラッシュする）。 */
        // なぜ internal か: androidTest の MigrationTest が本物の Migration を検証するため（複製だと本体変更にテストが追従しない）。
        internal val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pending_jobs` " +
                    "(`uri` TEXT NOT NULL, `displayName` TEXT NOT NULL, `enqueuedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`uri`))"
                )
            }
        }

        /** v8→v9: books テーブルに ncode 列（なろう作品の Nコード）を追加する。
         *  PDF↔Web継続読書（発見機能・目玉①）で「手元PDFの続きをなろうへ縫合」するための紐付けキー。
         *  nullable TEXT＝未紐付けが既定状態のため DEFAULT 句は不要（既存行は NULL 補完）。
         *  新規追加のみで既存カラム名に依存しないため PRAGMA 分岐は不要（MIGRATION_4_5 以降と同方針）。
         *  なぜ v8 でなく v9 か: version 8 は並列ブランチ feat/processing-resilience が先に消費し
         *  実機 DB も既にそのスキーマで v8 になっているため（identity hash 衝突の実測クラッシュあり）。 */
        // なぜ internal か: androidTest の MigrationTest が本物の Migration を検証するため（複製だと本体変更にテストが追従しない）。
        internal val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE books ADD COLUMN ncode TEXT")
            }
        }

        /** v9→v10: スキーマ無変更の identity hash 再スタンプ専用（DDL なし）。
         *  なぜ必要か: version 9 は api-lab-ai 系（PendingJobEntity 未登録＝schemas/9.json がその記録）が
         *  先に消費し、実機は既にその identity hash で v9 化済み。マージ合併で PendingJobEntity を登録した
         *  本スキーマを同じ version 9 のまま入れると、migration は走らず hash 照合だけが行われ
         *  起動即クラッシュする（task_diary #39 と同機序の「マージ時」変種＝同 #39 追補）。
         *  v10 へ上げるとこの no-op が走り、実テーブル（7→8 の pending_jobs／8→9 の ncode）は
         *  そのまま検証を通って新 hash が再記録される。 */
        // なぜ internal か: androidTest の MigrationTest が本物の Migration を検証するため（複製だと本体変更にテストが追従しない）。
        internal val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 意図的に何もしない（上記コメント参照）
            }
        }

        /** v10→v11: books テーブルに contentSha256 列（取込元 PDF の内容ハッシュ）を追加する。
         *  F-G 恒久策＝同一内容 PDF の別 URI 再取込を「重い変換の前」に弾くための内容指紋を保存する列。
         *  nullable TEXT＝旧取込分（本列を持たない v11 未満で登録された蔵書）は判定不能が既定のため
         *  DEFAULT 句なし（既存行は NULL 補完＝ハッシュ照合に一致せず、従来の title＋author 照合へ委ねる）。
         *  新規追加のみで既存カラム名に依存しないため PRAGMA 分岐は不要（MIGRATION_4_5 以降と同方針）。
         *  minSdk 26 の SQLite 3.18.x は ADD COLUMN をサポートしている。 */
        // なぜ internal か: androidTest の MigrationTest が本物の Migration を検証するため（複製だと本体変更にテストが追従しない）。
        internal val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE books ADD COLUMN contentSha256 TEXT")
            }
        }

        fun getDatabase(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "novel_reader_db")
                    .addMigrations(
                        MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                        MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
                    )
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
