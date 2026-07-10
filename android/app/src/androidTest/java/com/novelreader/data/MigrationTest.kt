package com.novelreader.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room Migration の Instrumented 回帰テスト。
 *
 * **このテストの目的**: identity hash 起因の「起動即クラッシュ」を〈実機で踏む〉から〈テストで捕まえる〉へ変える。
 * 本プロジェクトは version と schema-hash の不整合で起動即クラッシュを実測で2回踏んでいる（task_diary #39）。
 * MigrationTestHelper で「過去版スキーマから作った DB を実際の Migration で上げ、各段で Room の schema 検証を通すか」
 * ＋「最新版を新規作成したとき実エンティティの identity hash が checked-in スキーマと一致するか」を機械検証する。
 *
 * **実行は実機/エミュレータ必須（androidTest）**。ただし `gw connectedAndroidTest` の直叩きは禁忌
 * （全 androidTest 一括実行で実アプリの蔵書 DB を巻き込み消し得るため）。実行は必ず `/device-verify` スキル経由で、
 * （※この KDoc で /device-verify を Markdown 太字にしないこと: アスタリスク2連の直後にスラッシュが
 *   続くとコメント終端記号と解釈され、ブロックコメントがそこで閉じてコンパイルエラーになる＝2026-07-08 実測）
 * このクラスに絞った `am instrument`（`-e class com.novelreader.data.MigrationTest`）で回すこと。
 * 本テストは実 DB 名 "novel_reader_db" を一切触らず、専用の使い捨て DB 名のみ使う（下記 TEST_DB_* 定数）。
 *
 * **本番 Migration を直接参照している**: 検証対象は AppDatabase.kt の companion object にある本物の
 * MIGRATION_7_8 / 8_9 / 9_10 / 10_11 / 11_12 / 12_13（`internal` 宣言＝同一モジュール・同一パッケージの androidTest から可視）。
 * かつては private ゆえ参照できず同一 SQL を本ファイル下部へ写経していたが、写しは本体変更に追従せず
 * 二重真実源になるため、可視性を internal へ上げて本物参照へ一本化した（AppDatabase.kt 側にも
 * 「なぜ internal か」を明記済み）。
 *   → 限界: 本番の `addMigrations(...)` への登録漏れ自体は本チェーンテストでは捕まえられない（本物の
 *     オブジェクトを `AppDatabase.MIGRATION_*` で直接渡すため、登録配線そのものは経由しない）。
 *     その層は `freshInstallAtV13_passesIdentityHashCheck`（実エンティティの hash 照合）が別角度で補う。
 *
 * **対象範囲を 7→13 に絞る理由**: identity hash 衝突を実測した版（v8/v9/v10）と最新の v11（F-G 恒久策＝
 * books.contentSha256 追加）、v12（web_novels テーブル新設）、および v13（new_episode_marks テーブル新設）がこの区間に集中する。MIGRATION_3_4〜6_7 も AppDatabase に実装は在るが
 * （3_4 は PRAGMA 分岐を持つ）、区間外・写し増による保守負債を避けて対象外とした。3→7 への拡張が必要に
 * なれば同方式で追加できる。
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    // Class 版コンストラクタ: schemas/<AppDatabase の canonical 名>/<version>.json を androidTest アセットから読む
    // （build.gradle の sourceSets で $projectDir/schemas を androidTest.assets.srcDirs へ公開済み）。
    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    /**
     * 7→8→9→10→11→12→13 の全パスを1段ずつ適用し、各段で結果スキーマが checked-in JSON と一致するか検証する。
     *
     * **何を捕まえるか**: Migration の DDL が Room がエンティティから期待するスキーマ（8/9/10/11/12/13.json）と食い違う退行。
     * 食い違えば実機では起動時 schema validation で即クラッシュする——それを1段ごとに前倒しで検出する。
     */
    @Test
    fun migrate7to13_validatesSchemaAtEachStep() {
        // v7 スキーマの空 DB を 7.json から作る（＝v7 で作られた実機 DB 相当の初期状態）。
        helper.createDatabase(TEST_DB_CHAIN, 7).close()

        // 7→8: pending_jobs を新設。8.json は3テーブル全部を含み余剰テーブルは無いので validateDroppedTables=true で厳格検証。
        helper.runMigrationsAndValidate(TEST_DB_CHAIN, 8, true, AppDatabase.MIGRATION_7_8).close()

        // 8→9: books に ncode 追加。ここは validateDroppedTables=**false** にする。
        //   なぜ false か: 9.json は並列レーン api-lab-ai（PendingJobEntity 未登録）が採番した系譜で pending_jobs を含まない。
        //   だが実行時チェーンでは 7→8 で作った pending_jobs が生きたまま。true にすると「9.json に無い余剰テーブル
        //   (pending_jobs)」で検証が落ち、正しい実機挙動を誤検知してしまう。この不一致こそ task_diary #39 の
        //   「マージ系譜分岐」そのもので、false 指定はそれをテストが明示的に encode したもの。
        helper.runMigrationsAndValidate(TEST_DB_CHAIN, 9, false, AppDatabase.MIGRATION_8_9).close()

        // 9→10: no-op（identity hash 再スタンプ専用・DDL なし）。10.json は3テーブル全部を含むので true で厳格検証に戻す。
        helper.runMigrationsAndValidate(TEST_DB_CHAIN, 10, true, AppDatabase.MIGRATION_9_10).close()

        // 10→11: books に contentSha256 追加（F-G 恒久策）。11.json も3テーブル全部を含むので true で厳格検証。
        helper.runMigrationsAndValidate(TEST_DB_CHAIN, 11, true, AppDatabase.MIGRATION_10_11).close()

        // 11→12: web_novels テーブル新設。12.json も4テーブル全部を含むので true で厳格検証。
        helper.runMigrationsAndValidate(TEST_DB_CHAIN, 12, true, AppDatabase.MIGRATION_11_12).close()

        // 12→13: new_episode_marks テーブル新設。13.json も5テーブル全部を含むので true で厳格検証。
        helper.runMigrationsAndValidate(TEST_DB_CHAIN, 13, true, AppDatabase.MIGRATION_12_13).close()
    }

    /**
     * v7 で入れた代表データが 7→13 のチェーンを通っても失われないことを検証する。
     *
     * **何を捕まえるか**: どこかの段が破壊的（テーブル再作成でデータ取りこぼし・列消失）になっていないか。
     * 本区間は全て ADD COLUMN / CREATE TABLE / no-op で非破壊のはずだが、それを SELECT で実証して固定する。
     */
    @Test
    fun migrate7to13_preservesExistingRows() {
        helper.createDatabase(TEST_DB_DATA, 7).apply {
            // v7 に実在する列だけを使う（ncode は v9・contentSha256 は v11 で追加されるためここでは書けない
            // ＝版ごとの列構成に厳密整合）。
            execSQL(
                "INSERT INTO books (id, title, htmlDirPath, author, addedAt) " +
                    "VALUES ('book-1', 'テスト小説', '/data/novels/book-1', 'テスト著者', 1700000000000)"
            )
            execSQL(
                "INSERT INTO progress (bookId, lastReadFilename, scrollIndex, scrollOffset, lastReadAt) " +
                    "VALUES ('book-1', 'chap_3.html', 5, 120, 1700000001000)"
            )
            close()
        }

        // 全チェーンを一括適用。最終 v13 のみ検証すればよく、13.json は5テーブル全部を含むので validateDroppedTables=true。
        val db = helper.runMigrationsAndValidate(
            TEST_DB_DATA, 13, true,
            AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10,
            AppDatabase.MIGRATION_10_11, AppDatabase.MIGRATION_11_12, AppDatabase.MIGRATION_12_13,
        )

        db.query("SELECT title, author, addedAt, ncode, contentSha256 FROM books WHERE id = 'book-1'").use { c ->
            assertTrue("books 行が migration で消えた", c.moveToFirst())
            assertEquals("title が変質", "テスト小説", c.getString(c.getColumnIndexOrThrow("title")))
            assertEquals("author が変質", "テスト著者", c.getString(c.getColumnIndexOrThrow("author")))
            assertEquals("addedAt が変質", 1700000000000L, c.getLong(c.getColumnIndexOrThrow("addedAt")))
            // ncode は v9 追加の nullable 列＝v7 既存行は NULL で補完される（DEFAULT 句なし・未紐付けが既定）。
            assertTrue("既存行の ncode は NULL のはず", c.isNull(c.getColumnIndexOrThrow("ncode")))
            // contentSha256 は v11 追加の nullable 列＝v7 既存行は NULL で補完される（DEFAULT 句なし・
            // 旧取込分は内容指紋を持たず変換前遮断の対象外＝ハッシュ照合で一致しないことの土台）。
            assertTrue("既存行の contentSha256 は NULL のはず", c.isNull(c.getColumnIndexOrThrow("contentSha256")))
        }

        db.query("SELECT lastReadFilename, scrollIndex, scrollOffset FROM progress WHERE bookId = 'book-1'").use { c ->
            assertTrue("progress 行が migration で消えた", c.moveToFirst())
            assertEquals("lastReadFilename が変質", "chap_3.html", c.getString(0))
            assertEquals("scrollIndex が変質", 5, c.getInt(1))
            assertEquals("scrollOffset が変質", 120, c.getInt(2))
        }

        // 7→8 で新設した pending_jobs が存在し空であること（新設テーブルの疎通確認）。
        db.query("SELECT COUNT(*) FROM pending_jobs").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("pending_jobs は新設直後で空のはず", 0, c.getInt(0))
        }

        // 11→12 で新設した web_novels が存在し空であること（新設テーブルの疎通確認）。
        db.query("SELECT COUNT(*) FROM web_novels").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("web_novels は新設直後で空のはず", 0, c.getInt(0))
        }

        // 12→13 で新設した new_episode_marks が存在し空であること（新設テーブルの疎通確認）。
        db.query("SELECT COUNT(*) FROM new_episode_marks").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("new_episode_marks は新設直後で空のはず", 0, c.getInt(0))
        }

        db.close()
    }

    /**
     * 最新版 v13 の「フレッシュインストール」が identity hash 検証を通ることを検証する。
     *
     * **何を捕まえるか**: エンティティ（BookEntity/ProgressEntity/PendingJobEntity/WebNovelEntity/NewEpisodeMarkEntity や @Database version）を変えたのに
     * schemas/13.json の再生成 or version 上げを忘れた退行。この乖離は実機の**起動即クラッシュ**の正体で、
     * 本プロジェクトが2回踏んだ事象（task_diary #39）そのもの。
     *
     * **機序**: ①13.json（checked-in の最新スキーマ）から v13 DB を作り、その identityHash を room_master_table に刻む。
     * ②実アプリの Room で同じファイルを開くと、Room がコンパイル時にエンティティから算出した identity hash を
     * ①で刻まれた 13.json 由来 hash と照合する（RoomOpenHelper.checkIdentity）。両者が乖離していれば
     * IllegalStateException で落ちる＝テストが赤くなり、実機投入前に検出できる。
     * （version 13 == 現行のため migration は走らず、純粋な hash 照合だけが起こる）
     */
    @Test
    fun freshInstallAtV13_passesIdentityHashCheck() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB_FRESH) // 前回の残骸を掃除して決定的にする（createDatabase も削除するが二重の安全）

        // ① 13.json から v13 DB を作り、13.json の identityHash を刻む。
        helper.createDatabase(TEST_DB_FRESH, 13).close()

        // ② 実エンティティの Room で同じファイルを開き、コンパイル時 hash と刻まれた hash を照合させる。
        val db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB_FRESH).build()
        try {
            // writableDatabase を触ると Room が実際に open→identity 照合を発火する。ここで例外が飛ばず開ければ一致。
            assertTrue(
                "v13 スキーマで Room が開けない＝実エンティティと 13.json の identity hash 不一致",
                db.openHelper.writableDatabase.isOpen,
            )
        } finally {
            db.close()
            context.deleteDatabase(TEST_DB_FRESH) // 使い捨て DB を後始末（実アプリ DB とは別名なので蔵書に影響なし）
        }
    }

    companion object {
        // 実アプリの "novel_reader_db" とは別名の使い捨て DB（蔵書 DB を巻き込まないため・テスト間で名前も分離）。
        private const val TEST_DB_CHAIN = "migration-chain-test.db"
        private const val TEST_DB_DATA = "migration-data-test.db"
        private const val TEST_DB_FRESH = "migration-fresh-install-test.db"
    }
}
