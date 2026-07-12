package com.novelreader.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BookEntity::class,
        ProgressEntity::class,
        PendingJobEntity::class,
        WebNovelEntity::class,
        NewEpisodeMarkEntity::class,
        WebReadingProgressEntity::class,
    ],
    // なぜ 17 か: ui/vertical-pdf-import レーンが v16 を実機投入済み（レーン専有）。episode-nav 合流で
    // WebReadingProgressEntity が entities に加わり v16 の identity hash が変わるため、実機 v16 との
    // 同 version 衝突を no-op 再スタンプ（MIGRATION_16_17）で回避する（前例 v9→v10＝task_diary #39 追補）。
    // v18: progress に reachedEnd 列を追加（読了＝『了』印・読了フィルタの永続化／ssot Major 2026-07-12）。
    version = 18,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao
    abstract fun progressDao(): ProgressDao
    abstract fun pendingJobDao(): PendingJobDao
    abstract fun webNovelDao(): WebNovelDao
    abstract fun newEpisodeMarkDao(): NewEpisodeMarkDao
    abstract fun webReadingProgressDao(): WebReadingProgressDao

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

        /** v11→v12: なろうで発見したがまだ PDF 取込していない作品を本棚に置く「Web由来・未取込カード」の永続化テーブル web_novels を新設する。
         *  既存テーブルへは一切触れない新規 CREATE のみのため PRAGMA 分岐は不要。
         *  DDL は Room が Entity から期待するスキーマ（NOT NULL・主キー）と厳密一致させること
         *  （不一致は起動時の schema validation でクラッシュする）。 */
        // なぜ internal か: androidTest の MigrationTest が本物の Migration を検証するため（複製だと本体変更にテストが追従しない）。
        internal val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `web_novels` (" +
                    "`ncode` TEXT NOT NULL, `title` TEXT NOT NULL, `writer` TEXT NOT NULL, " +
                    "`generalAllNo` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`ncode`))"
                )
            }
        }

        /** v12→v13: 新着話の通知基準を管理する new_episode_marks テーブルを新設する。
         *  既存テーブルへは一切触れない新規 CREATE のみのため PRAGMA 分岐は不要。
         *  DDL は Room が Entity から期待するスキーマ（NOT NULL・主キー）と厳密一致させること
         *  （不一致は起動時の schema validation でクラッシュする）。 */
        // なぜ internal か: androidTest の MigrationTest が本物の Migration を検証するため（複製だと本体変更にテストが追従しない）。
        internal val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `new_episode_marks` (" +
                    "`ncode` TEXT NOT NULL, `lastNotifiedAllNo` INTEGER NOT NULL, `lastCheckedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`ncode`))"
                )
            }
        }

        /** v13→v14: ラベル機能（labels テーブルおよび book_labels 中間テーブル）を新設する。
         *  既存テーブルへは一切触れない新規 CREATE のみのため PRAGMA 分岐は不要。
         *  DDL は Room が Entity から期待するスキーマ（NOT NULL・主キー・インデックス）と厳密一致させること
         *  （不一致は起動時の schema validation でクラッシュする）。 */
        // なぜ internal か: androidTest の MigrationTest が本物の Migration を検証するため（複製だと本体変更にテストが追従しない）。
        internal val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // labels テーブルの作成（why: ラベルの永続化のため）
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `labels` (" +
                    "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))"
                )
                // labels テーブルの unique インデックスの作成（why: name の重複登録を防ぐため）
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_labels_name` ON `labels` (`name`)"
                )
                // book_labels 中間テーブルの作成（why: 本とラベルの多対多リレーションシップを紐付けるため）
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `book_labels` (" +
                    "`bookId` TEXT NOT NULL, `labelId` TEXT NOT NULL, " +
                    "PRIMARY KEY(`bookId`, `labelId`))"
                )
                // book_labels テーブルの labelId インデックスの作成（why: ラベル逆引きやクリーンアップを高速に行うため）
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_book_labels_labelId` ON `book_labels` (`labelId`)"
                )
            }
        }

        /** v14→v15: なろうWebView読書の読書位置テーブル web_reading_progress を新設する（機能②＝続きから再開）。
         *  既存テーブルへは一切触れない新規 CREATE のみのため PRAGMA 分岐は不要。
         *  DDL は Room が Entity から期待するスキーマ（NOT NULL・主キー）と厳密一致させること
         *  （不一致は起動時の schema validation でクラッシュする）。
         *  採番経緯: v15 は feat/episode-nav が本 DDL で消費し、並列レーン ui/vertical-pdf-import は同一 DDL の
         *  複製でパスを繋いで自レーンのラベル撤去を v16 へ退避した（task_diary #39 の衝突クラス回避）。
         *  2026-07-11 の合流で両レーンの複製は本定義1本に戻った。 */
        // なぜ internal か: androidTest の MigrationTest が本物の Migration を検証するため（複製だと本体変更にテストが追従しない）。
        internal val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `web_reading_progress` (" +
                    "`ncode` TEXT NOT NULL, `lastReadEpisode` INTEGER NOT NULL, `lastReadAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`ncode`))"
                )
            }
        }

        /** v15→v16: ラベルシステム廃止（labels / book_labels テーブルを付与データごと削除する）。
         *  なぜ撤去か: 本棚の分類は読書状態〔よみかけ/未読/読了〕の導出値へ置き換えたため、専用テーブルは
         *  不要になった。付与済みデータごと削除するのは「機能ごと撤去する」というユーザー判断による。
         *  削除順は junction（book_labels）→ 親（labels）: 参照する側を先に落とす流儀（FK なし設計だが順序を守る）。
         *  インデックス（index_labels_name / index_book_labels_labelId）は DROP TABLE に随伴して自動で消える。
         *  なぜ IF EXISTS か: v15 経由（feat/episode-nav 側スキーマ＝labels/book_labels が存在する系譜）と、
         *  将来の合流系譜（既に落ちている系譜）の両方を安全に通すため（無い前提で DROP すると後者で落ちる）。
         *  なぜ MIGRATION_13_14（labels 新設）を残すのか: v13 の実機が v16 まで上がる migration パスに 13→14 が
         *  必要なため。「14 で作って 16 で落とす」は一見無駄だが、途中版で止まった実機を段階的に前進させる正しい鎖。 */
        // なぜ internal か: androidTest の MigrationTest が本物の Migration を検証するため（複製だと本体変更にテストが追従しない）。
        internal val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE IF EXISTS `book_labels`")
                database.execSQL("DROP TABLE IF EXISTS `labels`")
            }
        }

        /** v16→v17: スキーマ無変更の identity hash 再スタンプ（no-op＝DDL なし）。
         *  なぜ: ui/vertical-pdf-import レーンが v16 を実機投入済みの状態で feat/episode-nav が合流し、
         *  WebReadingProgressEntity の登録でエンティティ集合＝v16 の identity hash が変わった。実機の
         *  branch 版 v16 と同 version を別 hash で名乗ると起動即クラッシュするため +1 で回避する
         *  （前例 v9→v10＝task_diary #39 追補。テーブル実体は 14→15 で作成済みのため DDL 不要）。 */
        // なぜ internal か: androidTest の MigrationTest が本物の Migration を検証するため（複製だと本体変更にテストが追従しない）。
        internal val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // no-op: スキーマ実体は不変（entities 登録変更に伴う version 前進のみ）
            }
        }

        /** v17→v18: progress テーブルに reachedEnd 列（最終章の末尾到達＝読了の実績）を追加する。
         *  ssot Major 2026-07-12: 最終章を1行スクロールした瞬間に読了扱いする「100%の嘘」を是正した結果、
         *  読了（『了』印・読了フィルタ）を導出する経路が消えた。読書画面が本当に末尾を可視化したときだけ
         *  立てる事実ベースの読了フラグをここで永続化し、読了状態を復活させる。
         *  既存行は DEFAULT 0（未読了）で補完する。NOT NULL 前提のため DEFAULT 句は必須
         *  （無いと既存行が NULL になり Room の非 null 検証と食い違って起動時クラッシュする）。
         *  新規追加のみで既存カラム名に依存しないため PRAGMA 分岐は不要（MIGRATION_4_5 以降と同方針）。
         *  minSdk 26 の SQLite 3.18.x は ADD COLUMN をサポートしている。 */
        // なぜ internal か: androidTest の MigrationTest が本物の Migration を検証するため（複製だと本体変更にテストが追従しない）。
        internal val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE progress ADD COLUMN reachedEnd INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "novel_reader_db")
                    .addMigrations(
                        MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                        MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
                        MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
                        MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17,
                        MIGRATION_17_18,
                    )
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
