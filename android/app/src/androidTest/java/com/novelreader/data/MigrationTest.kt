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
 * MIGRATION_7_8 / 8_9 / 9_10 / 10_11 / 11_12 / 12_13 / 13_14 / 14_15 / 15_16 / 16_17 / 17_18 / 18_19 / 19_20 / 20_21（`internal` 宣言＝同一モジュール・同一パッケージの androidTest から可視）。
 * かつては private ゆえ参照できず同一 SQL を本ファイル下部へ写経していたが、写しは本体変更に追従せず
 * 二重真実源になるため、可視性を internal へ上げて本物参照へ一本化した（AppDatabase.kt 側にも
 * 「なぜ internal か」を明記済み）。
 *   → 限界: 本番の `addMigrations(...)` への登録漏れ自体は本チェーンテストでは捕まえられない（本物の
 *     オブジェクトを `AppDatabase.MIGRATION_*` で直接渡すため、登録配線そのものは経由しない）。
 *     その層は `freshInstallAtV21_passesIdentityHashCheck`（実エンティティの hash 照合）が別角度で補う。
 *
 * **対象範囲を 7→21 に絞る理由**: identity hash 衝突を実測した版（v8/v9/v10）と最新の v11（F-G 恒久策＝
 * books.contentSha256 追加）、v12（web_novels テーブル新設）、v13（new_episode_marks テーブル新設）、v14（labels / book_labels 新設）、
 * v15（web_reading_progress 新設＝機能②。ADR 0012）、v16（ラベルシステム廃止＝labels / book_labels を DROP）、
 * v17（no-op 再スタンプ＝2026-07-11 合流で WebReadingProgressEntity 登録に伴う hash 前進）、
 * v18（progress.reachedEnd 追加＝読了フラグの永続化）、v19（books.shioriTipIndex/shioriLenFrac 追加＝栞書影の個体差の永続化）、
 * v20（books.sourceUri 追加＝並列 feat/delete-source-pdf 先着分を同一 SQL で複製しパス接続）、
 * v21（books.sourceUrl/sourceSite 追加＝Web取込元の作品URL・サイトアダプタキーの永続化＝当ブランチ固有）が
 * この区間に集中する。MIGRATION_3_4〜6_7 も AppDatabase に実装は在るが
 * （3_4 は PRAGMA 分岐を持つ）、区間外・写し増による保守負債を避けて対象外とした。3→7 への拡張が必要に
 * なれば同方式で追加できる。
 *
 * **floor v7 は確定判断**（2026-07-12・確認バッチ G）: オーナーが v1/v2 実機の残存無しを確認済み。
 * v3〜v6 で作られた実機が現実に存在しないため、未テストの 3→7 区間は実機で踏まれず、識別ハッシュ起因
 * クラッシュのリスクはゼロと扱う。したがって floor v7 は「未整備の穴」ではなく意図的な確定判断であり、
 * MIGRATION_3_4 のデータ入り回帰追加は不要と裁定した（AppDatabase 側に 3_4〜6_7 の実装自体は温存）。
 * 将来 v1/v2 相当の古い端末が発掘されたら、そのときに同方式で 3→7 を追加する。
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
     * 7→8→9→10→11→12→13→14→15→16→17→18→19→20→21 の全パスを1段ずつ適用し、各段で結果スキーマが checked-in JSON と一致するか検証する。
     *
     * **何を捕まえるか**: Migration の DDL が Room がエンティティから期待するスキーマ（8〜21 の各 .json）と食い違う退行。
     * 食い違えば実機では起動時 schema validation で即クラッシュする——それを1段ごとに前倒しで検出する。
     *
     * **20.json の前提**: 19→20 の検証段は schemas/20.json を必要とする。当ブランチの Room 生成物は現行版 v21 の
     * 21.json のみで、v20 の中間 json は自動生成されない（並列 feat/delete-source-pdf が採番した版のため）。実機での
     * 本テスト実行前に 20.json（feat/delete-source-pdf 生成物＝sourceUri のみ追加した v20 スキーマ）を配置する必要がある。
     */
    @Test
    fun migrate7to21_validatesSchemaAtEachStep() {
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

        // 13→14: labels / book_labels テーブル新設。14.json も7テーブル全部を含むので true で厳格検証。
        helper.runMigrationsAndValidate(TEST_DB_CHAIN, 14, true, AppDatabase.MIGRATION_13_14).close()

        // 14→15: web_reading_progress テーブル新設（機能②）。15.json（feat/episode-nav 採番＝2026-07-11 合流で
        // 収蔵）は labels/book_labels 含む8テーブル全部を含むので true で厳格検証。
        helper.runMigrationsAndValidate(TEST_DB_CHAIN, 15, true, AppDatabase.MIGRATION_14_15).close()

        // 15→16: labels / book_labels の DROP。validateDroppedTables=**false** にする。
        //   なぜ false か: 16.json は ui/vertical-pdf-import レーン（WebReadingProgressEntity 未登録）が採番した
        //   系譜で web_reading_progress を含まない。だが実行時チェーンでは 14→15 で作ったそれが生きたまま。
        //   true にすると「16.json に無い余剰テーブル」で検証が落ちる＝正しい実機挙動の誤検知
        //   （既存 8→9 ステップで pending_jobs を false で許容したのと同じ機序）。
        helper.runMigrationsAndValidate(TEST_DB_CHAIN, 16, false, AppDatabase.MIGRATION_15_16).close()

        // 16→17: no-op 再スタンプ（合流で WebReadingProgressEntity が登録され hash が前進）。17.json は
        // 6テーブル全部（web_reading_progress 含む・labels 系なし）を含むので true で厳格検証に戻す。
        helper.runMigrationsAndValidate(TEST_DB_CHAIN, 17, true, AppDatabase.MIGRATION_16_17).close()

        // 17→18: progress に reachedEnd 列を追加（読了フラグの永続化）。18.json も6テーブル全部を含むので true で厳格検証。
        helper.runMigrationsAndValidate(TEST_DB_CHAIN, 18, true, AppDatabase.MIGRATION_17_18).close()

        // 18→19: books に shioriTipIndex / shioriLenFrac 列を追加（栞書影の個体差の永続化）。19.json も6テーブル全部を含むので true で厳格検証。
        helper.runMigrationsAndValidate(TEST_DB_CHAIN, 19, true, AppDatabase.MIGRATION_18_19).close()

        // 19→20: books に sourceUri 列を追加（取込元PDFの永続化＝本削除時に取込元も消す土台）。20.json も6テーブル全部を含むので true で厳格検証。
        helper.runMigrationsAndValidate(TEST_DB_CHAIN, 20, true, AppDatabase.MIGRATION_19_20).close()

        // 20→21: books に sourceUrl / sourceSite 列を追加（Web取込元の記録）。21.json も6テーブル全部を含むので true で厳格検証。
        helper.runMigrationsAndValidate(TEST_DB_CHAIN, 21, true, AppDatabase.MIGRATION_20_21).close()
    }

    /**
     * v7 で入れた代表データが 7→21 のチェーンを通っても失われないことを検証する。
     *
     * **何を捕まえるか**: どこかの段が破壊的（テーブル再作成でデータ取りこぼし・列消失）になっていないか。
     * 本区間の books/progress への操作は全て ADD COLUMN / CREATE TABLE / no-op で非破壊のはずだが、それを
     * SELECT で実証して固定する（15→16 の DROP は labels/book_labels のみが対象で books/progress は無関係）。
     */
    @Test
    fun migrate7to21_preservesExistingRows() {
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

        // 全チェーンを一括適用。最終 v21 のみ検証すればよく、21.json は6テーブル全部
        // （web_reading_progress 含む・labels 系なし）を含むので validateDroppedTables=true で厳格検証。
        val db = helper.runMigrationsAndValidate(
            TEST_DB_DATA, 21, true,
            AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10,
            AppDatabase.MIGRATION_10_11, AppDatabase.MIGRATION_11_12, AppDatabase.MIGRATION_12_13,
            AppDatabase.MIGRATION_13_14, AppDatabase.MIGRATION_14_15, AppDatabase.MIGRATION_15_16,
            AppDatabase.MIGRATION_16_17, AppDatabase.MIGRATION_17_18, AppDatabase.MIGRATION_18_19,
            AppDatabase.MIGRATION_19_20, AppDatabase.MIGRATION_20_21,
        )

        db.query("SELECT title, author, addedAt, ncode, contentSha256, shioriTipIndex, shioriLenFrac, sourceUri, sourceUrl, sourceSite FROM books WHERE id = 'book-1'").use { c ->
            assertTrue("books 行が migration で消えた", c.moveToFirst())
            assertEquals("title が変質", "テスト小説", c.getString(c.getColumnIndexOrThrow("title")))
            assertEquals("author が変質", "テスト著者", c.getString(c.getColumnIndexOrThrow("author")))
            assertEquals("addedAt が変質", 1700000000000L, c.getLong(c.getColumnIndexOrThrow("addedAt")))
            // ncode は v9 追加の nullable 列＝v7 既存行は NULL で補完される（DEFAULT 句なし・未紐付けが既定）。
            assertTrue("既存行の ncode は NULL のはず", c.isNull(c.getColumnIndexOrThrow("ncode")))
            // contentSha256 は v11 追加の nullable 列＝v7 既存行は NULL で補完される（DEFAULT 句なし・
            // 旧取込分は内容指紋を持たず変換前遮断の対象外＝ハッシュ照合で一致しないことの土台）。
            assertTrue("既存行の contentSha256 は NULL のはず", c.isNull(c.getColumnIndexOrThrow("contentSha256")))
            // shioriTipIndex / shioriLenFrac は v19 追加の nullable 列＝v7 既存行は NULL で補完される
            // （DEFAULT 句なし・未抽選が既定＝描画側で title 由来の決定論値へフォールバックすることの土台）。
            assertTrue("既存行の shioriTipIndex は NULL のはず", c.isNull(c.getColumnIndexOrThrow("shioriTipIndex")))
            assertTrue("既存行の shioriLenFrac は NULL のはず", c.isNull(c.getColumnIndexOrThrow("shioriLenFrac")))
            // sourceUri は v20 追加の nullable 列＝v7 既存行は NULL で補完される（DEFAULT 句なし・削除可能な
            // 取込元を持たない本＝本削除時の取込元削除の対象外であることの土台）。
            assertTrue("既存行の sourceUri は NULL のはず", c.isNull(c.getColumnIndexOrThrow("sourceUri")))
            // sourceUrl / sourceSite は v21 追加の nullable 列＝v7 既存行は NULL で補完される
            // （DEFAULT 句なし・PDF由来の蔵書は Web 取込元 URL/サイトを持たないのが既定）。
            assertTrue("既存行の sourceUrl は NULL のはず", c.isNull(c.getColumnIndexOrThrow("sourceUrl")))
            assertTrue("既存行の sourceSite は NULL のはず", c.isNull(c.getColumnIndexOrThrow("sourceSite")))
        }

        db.query(
            "SELECT lastReadFilename, scrollIndex, scrollOffset, reachedEnd FROM progress WHERE bookId = 'book-1'"
        ).use { c ->
            assertTrue("progress 行が migration で消えた", c.moveToFirst())
            assertEquals("lastReadFilename が変質", "chap_3.html", c.getString(0))
            assertEquals("scrollIndex が変質", 5, c.getInt(1))
            assertEquals("scrollOffset が変質", 120, c.getInt(2))
            // reachedEnd は v18 追加の NOT NULL 列＝v7 既存行は DEFAULT 0（未読了）で補完される。
            assertEquals("既存行の reachedEnd は 0（未読了）のはず", 0, c.getInt(3))
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

        // 14→15 で新設した web_reading_progress が存在し空であること（新設テーブルの疎通確認。
        // 2026-07-11 合流で WebReadingProgressEntity 登録済み＝17.json に含まれる正規テーブル）。
        db.query("SELECT COUNT(*) FROM web_reading_progress").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("web_reading_progress は新設直後で空のはず", 0, c.getInt(0))
        }

        // 15→16 の DROP が効いて labels / book_labels が消えていること（COUNT ではなく存在自体を照合する:
        // DROP 済みのテーブルへ COUNT を投げると "no such table" で落ちるため、sqlite_master でテーブル数を数える）。
        db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('labels', 'book_labels')"
        ).use { c ->
            assertEquals("labels / book_labels は 15→16 の DROP で消えているはず", 0, c.count)
        }

        db.close()
    }

    /**
     * v9 を**フレッシュインストール**した端末の形状（9.json＝pending_jobs 無し）から v10 へ上げられるか。
     *
     * **なぜチェーンテストと別に要るか**: 上の 7→21 チェーンは 7→8 で pending_jobs を作ってから 9→10 に入る
     * ため、「9.json の形状そのもの」＝api-lab-ai レーン（PendingJobEntity 未登録）で v9 をフレッシュ
     * インストールした端末の DB を一度も通らない。その形状では pending_jobs が物理的に存在せず、
     * 旧 MIGRATION_9_10（no-op）だと 10.json の検証で「期待テーブルが無い」＝起動即クラッシュした。
     * 2026-08-05 に MIGRATION_9_10 へ IF NOT EXISTS 付き CREATE を追加して是正した回帰固定。
     */
    @Test
    fun migrate9FreshShapeTo10_validatesSchema() {
        helper.createDatabase(TEST_DB_V9_FRESH, 9).close()
        // 9.json 形状（2テーブル）＋補完された pending_jobs ＝10.json の全テーブルと過不足なく一致するので true で厳格検証。
        helper.runMigrationsAndValidate(TEST_DB_V9_FRESH, 10, true, AppDatabase.MIGRATION_9_10).close()
    }

    /**
     * v16 を**フレッシュインストール**した端末の形状（16.json＝web_reading_progress 無し）から v21 まで上げられるか。
     *
     * **なぜチェーンテストと別に要るか**: 上の 7→21 チェーンは 14→15 で web_reading_progress を作ってから
     * 16→17 に入るため、「16.json の形状そのもの」＝ui/vertical-pdf-import レーン（WebReadingProgressEntity
     * 未登録）で v16 をフレッシュインストールした端末の DB を**構造的に一度も通らない**（handover に
     * 「MigrationTest の coverage-hole」として残っていた穴）。その形状では当該テーブルが物理的に存在せず、
     * 旧 MIGRATION_16_17（no-op）だと 17.json の検証で「期待テーブルが無い」＝起動即クラッシュした。
     * 2026-08-05 に MIGRATION_16_17 へ IF NOT EXISTS 付き CREATE を追加して是正した回帰固定。
     * 17 で止めず 21 まで通すのは、この系譜の実機が現行版へ到達できることまで固定するため。
     */
    @Test
    fun migrate16FreshShapeTo21_validatesSchemaAtEachStep() {
        helper.createDatabase(TEST_DB_V16_FRESH, 16).close()
        // 16.json 形状（5テーブル）＋補完された web_reading_progress ＝17.json の全テーブルと一致するので true で厳格検証。
        helper.runMigrationsAndValidate(TEST_DB_V16_FRESH, 17, true, AppDatabase.MIGRATION_16_17).close()
        // 以降は列追加のみ＝テーブル集合は不変。各段の .json と厳密照合する（チェーン側と同じ粒度）。
        helper.runMigrationsAndValidate(TEST_DB_V16_FRESH, 18, true, AppDatabase.MIGRATION_17_18).close()
        helper.runMigrationsAndValidate(TEST_DB_V16_FRESH, 19, true, AppDatabase.MIGRATION_18_19).close()
        helper.runMigrationsAndValidate(TEST_DB_V16_FRESH, 20, true, AppDatabase.MIGRATION_19_20).close()
        helper.runMigrationsAndValidate(TEST_DB_V16_FRESH, 21, true, AppDatabase.MIGRATION_20_21).close()
    }

    /**
     * 最新版 v21 の「フレッシュインストール」が identity hash 検証を通ることを検証する。
     *
     * **何を捕まえるか**: エンティティ（BookEntity/ProgressEntity/PendingJobEntity/WebNovelEntity/NewEpisodeMarkEntity/WebReadingProgressEntity や @Database version）を変えたのに
     * schemas/21.json の再生成 or version 上げを忘れた退行（v16 で LabelEntity/BookLabelEntity は撤去済み・
     * v17 で WebReadingProgressEntity が登録済み・v18 で ProgressEntity.reachedEnd 追加・v19 で BookEntity.shioriTipIndex/shioriLenFrac 追加・
     * v20 で BookEntity.sourceUri 追加・v21 で BookEntity.sourceUrl/sourceSite 追加＝現行エンティティ集合）。この乖離は実機の**起動即クラッシュ**の正体で、
     * 本プロジェクトが2回踏んだ事象（task_diary #39）そのもの。
     *
     * **機序**: ①21.json（checked-in の最新スキーマ）から v21 DB を作り、その identityHash を room_master_table に刻む。
     * ②実アプリの Room で同じファイルを開くと、Room がコンパイル時にエンティティから算出した identity hash を
     * ①で刻まれた 21.json 由来 hash と照合する（RoomOpenHelper.checkIdentity）。両者が乖離していれば
     * IllegalStateException で落ちる＝テストが赤くなり、実機投入前に検出できる。
     * （version 21 == 現行のため migration は走らず、純粋な hash 照合だけが起こる）
     */
    @Test
    fun freshInstallAtV21_passesIdentityHashCheck() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB_FRESH) // 前回の残骸を掃除して決定的にする（createDatabase も削除するが二重の安全）

        // ① 21.json から v21 DB を作り、21.json の identityHash を刻む。
        helper.createDatabase(TEST_DB_FRESH, 21).close()

        // ② 実エンティティの Room で同じファイルを開き、コンパイル時 hash と刻まれた hash を照合させる。
        val db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB_FRESH).build()
        try {
            // writableDatabase を触ると Room が実際に open→identity 照合を発火する。ここで例外が飛ばず開ければ一致。
            assertTrue(
                "v21 スキーマで Room が開けない＝実エンティティと 21.json の identity hash 不一致",
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
        private const val TEST_DB_V9_FRESH = "migration-v9-fresh-shape-test.db"
        private const val TEST_DB_V16_FRESH = "migration-v16-fresh-shape-test.db"
    }
}
