package com.novelreader.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * 「各版を**フレッシュインストール**した端末の形状から1段上げると、次版が期待する全テーブルが揃うか」を
 * checked-in スキーマ JSON から機械生成して検証する（JVM＝端末不要で毎ゲート走る）。
 *
 * **なぜ androidTest の MigrationTest だけでは足りないか（本テストを足した理由・2026-08-05）**:
 * 既存の [com.novelreader.data.MigrationTest] は v7 の DB を作って 7→8→…→21 と**1本の鎖**で上げる。
 * この形だと「途中版で新設したテーブルが、その後の版の JSON に載っていなくても鎖の中では生き続ける」ため、
 * **その版をフレッシュインストールした端末の形状**（＝Room が当時のエンティティ集合だけから作る DB）を
 * 一度も通らない。並列レーン開発で採番が分岐した本プロジェクトでは、この差が実害になる:
 *
 *  - `9.json` は pending_jobs を含まない（api-lab-ai レーンが PendingJobEntity 未登録で採番）が
 *    `10.json` は含む → v9 フレッシュ端末は v10 で「期待テーブルが無い」＝起動即クラッシュ。
 *  - `16.json` は web_reading_progress を含まない（ui/vertical-pdf-import レーン）が `17.json` は含む
 *    → v16 フレッシュ端末は v17 で同じクラッシュ（2026-08-05 に本テストで検出し MIGRATION_9_10 /
 *    MIGRATION_16_17 へ IF NOT EXISTS 付き CREATE を追加して是正）。
 *
 * **判定を「次版の期待テーブルを包含するか」にする理由**（集合の完全一致にしない）:
 * Room が起動時に落ちるのは**期待するものが無い**ときだけで、余剰テーブルは production の検証を通す
 * （`validateDroppedTables` は MigrationTestHelper 側の追加検査）。実際 8→9・15→16 は鎖の系譜差で
 * 余剰テーブルが正常に残る区間＝完全一致にすると正しい実機挙動を誤検知する（既存 MigrationTest が
 * 同区間で validateDroppedTables=false にしているのと同じ理由）。
 *
 * **本テストの限界**: テーブル**名**の存在までしか見ない（列・型・主キー・インデックスの一致は見ない）。
 * 列レベルの厳密検証は Room 本体の schema validation が要る＝androidTest の MigrationTest が担当。
 * 役割分担: こちらが「端末なしで毎回走る粗い網」・あちらが「端末で走る精密な網」。
 *
 * **区間を 7→21 に絞る理由**: MIGRATION_4_5〜6_7 は private で参照できず、かつ v3〜v6 の実機は存在しない
 * ことが確認済み（floor v7 は確定判断＝MigrationTest の KDoc 参照）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationShapeCoverageTest {

    @Test
    fun `各版のフレッシュ形状から1段上げると次版が期待する全テーブルが揃う`() {
        // 失敗は全区間ぶん集めてから1回で報告する（最初の1件で止めると、同種の穴が他にもあるか分からない）。
        val missing = mutableListOf<String>()
        for ((from, migration) in MIGRATIONS) {
            val to = from + 1
            val helper = openBlankDatabase()
            try {
                val db = helper.writableDatabase
                createFreshShape(db, from)
                migration.migrate(db)
                val actual = tableNamesIn(db)
                for (table in declaredTablesIn(to) - actual) {
                    missing += "v$from→v$to で `$table` が作られない（v$from フレッシュ端末が起動即クラッシュする）"
                }
            } finally {
                helper.close()
            }
        }
        assertTrue(missing.joinToString("\n"), missing.isEmpty())
    }

    @Test
    fun `検証区間の全段が実際に走っている（区間の取りこぼし検知）`() {
        // 上のループが空回りしても assertTrue は通ってしまう＝段数そのものを固定して空振りを検知する。
        // 7→8 から 20→21 までの14段（版を足したら Migration 追加と同じコミットでここも +1 する）。
        assertEquals("検証区間の段数が想定と違う（MIGRATIONS への追記漏れ／削除の疑い）", 14, MIGRATIONS.size)
        assertEquals("区間の始点が v7 でない", 7, MIGRATIONS.first().first)
        assertEquals("区間の終点が現行版の1つ手前でない", 20, MIGRATIONS.last().first)
    }

    // ---- ここから下は「JSON の形状で DB を作る」道具立て ----

    /** 名前なし＝インメモリの空 DB を開く（版番号は使わないので callback は素通し）。 */
    private fun openBlankDatabase(): SupportSQLiteOpenHelper {
        val configuration = SupportSQLiteOpenHelper.Configuration
            .builder(RuntimeEnvironment.getApplication())
            .name(null) // null＝インメモリ（テスト間で状態が漏れない・後始末も不要）
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration)
    }

    /**
     * [version] を**フレッシュインストール**した端末と同じ形状を作る。
     * DDL は checked-in の N.json が持つ `createSql` をそのまま使う＝テスト側に SQL を写経しない
     * （写経は本体変更に追従せず二重真実源になる。MigrationTest が本物の Migration を参照するのと同じ流儀）。
     */
    private fun createFreshShape(db: SupportSQLiteDatabase, version: Int) {
        val entities = schemaJson(version).getJSONObject("database").getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            db.execSQL(entity.getString("createSql").replace(TABLE_NAME_PLACEHOLDER, table))
            val indices = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) {
                db.execSQL(indices.getJSONObject(j).getString("createSql").replace(TABLE_NAME_PLACEHOLDER, table))
            }
        }
    }

    /** N.json が宣言するテーブル名（＝その版の Room が存在を期待するもの）。 */
    private fun declaredTablesIn(version: Int): Set<String> {
        val entities = schemaJson(version).getJSONObject("database").getJSONArray("entities")
        return (0 until entities.length()).map { entities.getJSONObject(it).getString("tableName") }.toSet()
    }

    /** 実 DB に存在するテーブル名（SQLite/Room の内部テーブルは除く）。 */
    private fun tableNamesIn(db: SupportSQLiteDatabase): Set<String> =
        db.query(
            "SELECT name FROM sqlite_master WHERE type='table' " +
                "AND name NOT LIKE 'sqlite_%' AND name NOT IN ('android_metadata', 'room_master_table')"
        ).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }

    private fun schemaJson(version: Int): JSONObject = JSONObject(schemaFile(version).readText())

    /** モジュール相対でスキーマ JSON を探す（作業ディレクトリはモジュール直下だが揺れに備え数点試す＝SiteProfilesTest と同手）。 */
    private fun schemaFile(version: Int): File {
        val relative = "schemas/com.novelreader.data.AppDatabase/$version.json"
        return listOf(File(relative), File("app/$relative")).firstOrNull { it.exists() }
            ?: error("$relative が見つからない（作業ディレクトリ=${File(".").absolutePath}）")
    }

    private companion object {
        /** Room が createSql に埋めるテーブル名のプレースホルダ（JSON 中の文字列そのもの）。 */
        const val TABLE_NAME_PLACEHOLDER = "\${TABLE_NAME}"

        /** 検証対象の（起点版, その版から1段上げる本物の Migration）。本物参照＝複製を作らない。 */
        val MIGRATIONS: List<Pair<Int, Migration>> = listOf(
            7 to AppDatabase.MIGRATION_7_8,
            8 to AppDatabase.MIGRATION_8_9,
            9 to AppDatabase.MIGRATION_9_10,
            10 to AppDatabase.MIGRATION_10_11,
            11 to AppDatabase.MIGRATION_11_12,
            12 to AppDatabase.MIGRATION_12_13,
            13 to AppDatabase.MIGRATION_13_14,
            14 to AppDatabase.MIGRATION_14_15,
            15 to AppDatabase.MIGRATION_15_16,
            16 to AppDatabase.MIGRATION_16_17,
            17 to AppDatabase.MIGRATION_17_18,
            18 to AppDatabase.MIGRATION_18_19,
            19 to AppDatabase.MIGRATION_19_20,
            20 to AppDatabase.MIGRATION_20_21,
        )
    }
}
