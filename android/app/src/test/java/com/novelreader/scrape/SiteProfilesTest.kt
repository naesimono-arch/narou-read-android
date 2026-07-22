package com.novelreader.scrape

import com.novelreader.scrape.generic.SiteProfiles
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 設定表（[SiteProfiles]）の手動同期点を機械封鎖するメタテスト。純 JVM（XML パース＋リソース存在確認）で動く。
 *
 * 表を 1 行足すたびに人手で合わせる 3 点——(1) Manifest の ACTION_VIEW ホスト列挙・(2) siteKey の一意性・
 * (3) fixture golden ディレクトリ——のズレを、実装が育つ前にここで赤にする（設計正本のテスト計画 3）。
 */
class SiteProfilesTest {

    /**
     * ① 全 profile の hosts が AndroidManifest の ACTION_VIEW `<data android:host>` に含まれる。
     * VIEW リンクタップの受け口が Manifest 側にないと、対応サイトなのに端末がアプリを候補に出さない（＝取り込めない）。
     * 表とは別ファイル（Manifest）の手動列挙が唯一の同期点なので、ここで機械照合する。
     */
    @Test
    fun everyProfileHostIsDeclaredInManifestViewFilter() {
        val viewHosts = manifestActionViewHosts()
        for (profile in SiteProfiles.ALL) {
            for (host in profile.hosts) {
                assertTrue(
                    "profile ${profile.siteKey} の host '$host' が Manifest の ACTION_VIEW data host に無い（Manifest 未同期）: $viewHosts",
                    host in viewHosts,
                )
            }
        }
    }

    /** ② siteKey は全アダプタ横断（kakuyomu 含む）で一意。永続化キー衝突＝別サイトのデータ取り違えを防ぐ。 */
    @Test
    fun siteKeysAreUniqueAcrossAllAdapters() {
        val keys = SiteAdapterRegistry().registeredAdapters.map { it.siteKey }
        assertTrue("siteKey が重複している: $keys", keys.size == keys.toSet().size)
    }

    /** ③ profile ごとに fixture golden ディレクトリ `scrape_fixtures/<siteKey>/` が実在する（破損監視の前提）。 */
    @Test
    fun everyProfileHasFixtureDirectory() {
        for (profile in SiteProfiles.ALL) {
            val res = javaClass.classLoader!!.getResource("scrape_fixtures/${profile.siteKey}")
            assertTrue("fixture ディレクトリ scrape_fixtures/${profile.siteKey}/ が無い", res != null)
        }
    }

    // ---- ヘルパ ----

    /** AndroidManifest.xml を読み、ACTION_VIEW を含む intent-filter の `<data android:host>` 集合を返す。 */
    private fun manifestActionViewHosts(): Set<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifestFile())
        val hosts = mutableSetOf<String>()
        val filters = doc.getElementsByTagName("intent-filter")
        for (i in 0 until filters.length) {
            val filter = filters.item(i) as Element
            // この filter が VIEW を宣言しているか（ACTION_SEND 等の他 filter の host を拾わない）。
            val actions = filter.getElementsByTagName("action")
            var isView = false
            for (j in 0 until actions.length) {
                if ((actions.item(j) as Element).getAttribute("android:name") == "android.intent.action.VIEW") {
                    isView = true
                }
            }
            if (!isView) continue
            val datas = filter.getElementsByTagName("data")
            for (j in 0 until datas.length) {
                val host = (datas.item(j) as Element).getAttribute("android:host")
                if (host.isNotBlank()) hosts.add(host)
            }
        }
        return hosts
    }

    /** モジュール相対で AndroidManifest.xml を探す（テストの作業ディレクトリはモジュール直下だが、揺れに備え数点試す）。 */
    private fun manifestFile(): File {
        val candidates = listOf(
            "src/main/AndroidManifest.xml",
            "app/src/main/AndroidManifest.xml",
        )
        return candidates.map { File(it) }.firstOrNull { it.exists() }
            ?: error("AndroidManifest.xml が見つからない（作業ディレクトリ=${File(".").absolutePath}）")
    }
}
