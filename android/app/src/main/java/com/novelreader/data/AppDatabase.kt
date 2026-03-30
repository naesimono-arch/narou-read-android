package com.novelreader.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [BookEntity::class, ProgressEntity::class], version = 4, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao
    abstract fun progressDao(): ProgressDao

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

        fun getDatabase(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "novel_reader_db")
                    .addMigrations(MIGRATION_3_4)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
