package com.novelreader.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * 供給元別スロット（PDF=Service／WEB=BookshelfViewModel）の状態独立の契約（2026-07-29 裁定③）。
 * 旧・単一 StateFlow 共有では「片方の完了 null 書きが他方のバナーごと消す」相互上書きが起きていた。
 * ここでは (a) 他スロットへの書き込みが自スロットを潰さない (b) 優先側（PDF）が畳まれたら
 * 従属側（WEB）が表示に浮上する (c) source の防御的刻印、を固定する。
 */
@RunWith(JUnit4::class)
class ProcessingStateHubTest {

    private fun pdfState(title: String) = ProcessingState(isProcessing = true, title = title)
    private fun webState(title: String) =
        ProcessingState(isProcessing = true, title = title, source = ProcessingSource.WEB)

    @Test
    fun `並行時 - WEB の書き込みは PDF の表示を潰さない（表示は PDF 優先）`() {
        val hub = ProcessingStateHub()
        hub.update(ProcessingSource.PDF, pdfState("PDF本"))
        hub.update(ProcessingSource.WEB, webState("Web小説"))

        // 表示は PDF 優先のまま（WEB の後着書き込みで上書きされない）。
        assertEquals("PDF本", hub.displayState.value?.title)
        assertEquals(ProcessingSource.PDF, hub.displayState.value?.source)
        // WEB スロット自体は生きている（潰されていない）。
        assertEquals("Web小説", hub.stateOf(ProcessingSource.WEB)?.title)
    }

    @Test
    fun `並行時 - PDF 完了の null 書きで WEB が表示に浮上する（WEB のバナーが消えない）`() {
        val hub = ProcessingStateHub()
        hub.update(ProcessingSource.PDF, pdfState("PDF本"))
        hub.update(ProcessingSource.WEB, webState("Web小説"))

        hub.update(ProcessingSource.PDF, null) // PDF 側の完了処理（旧実装ではここで Web バナーも消えた）

        assertEquals("Web小説", hub.displayState.value?.title)
        assertEquals(ProcessingSource.WEB, hub.displayState.value?.source)
    }

    @Test
    fun `並行時 - WEB 完了の null 書きは PDF の表示に影響しない`() {
        val hub = ProcessingStateHub()
        hub.update(ProcessingSource.PDF, pdfState("PDF本"))
        hub.update(ProcessingSource.WEB, webState("Web小説"))

        hub.update(ProcessingSource.WEB, null)

        assertEquals("PDF本", hub.displayState.value?.title)
        assertNull(hub.stateOf(ProcessingSource.WEB))
    }

    @Test
    fun `両スロット null で表示も null（バナー非表示）`() {
        val hub = ProcessingStateHub()
        hub.update(ProcessingSource.PDF, pdfState("PDF本"))
        hub.update(ProcessingSource.PDF, null)
        assertNull(hub.displayState.value)
    }

    @Test
    fun `source の防御的刻印 - 書き手が source を設定し忘れてもスロット側で強制される`() {
        val hub = ProcessingStateHub()
        // source 既定 PDF のまま WEB スロットへ書く（既存 Service 流儀の構築ミスを模す）。
        hub.update(ProcessingSource.WEB, ProcessingState(isProcessing = true, title = "Web小説"))

        // 停止ディスパッチ・ステッパー出し分けが取り違えないよう WEB へ刻印される。
        assertEquals(ProcessingSource.WEB, hub.stateOf(ProcessingSource.WEB)?.source)
        assertEquals(ProcessingSource.WEB, hub.displayState.value?.source)
    }
}
