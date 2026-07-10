package com.novelreader.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.NovelReaderApplication
import com.novelreader.narou.model.Ncode
import kotlinx.coroutines.launch

/**
 * なろう作品をアプリ内 WebView で読むための ViewModel（機能②・ADR 0012）。
 *
 * 役割は「WebView 側が観測した話ページ到達を、蔵書 Room（web_reading_progress）へ記録する」1点のみ。
 * WebView インスタンスの保持・戻る制御・描画は画面側（WebReaderScreen）に置き、VM は Room 副作用だけを
 * 担う（NovelDetailViewModel/PdfImportViewModel と同じ VM=永続副作用・View=WebView の責務分離）。
 *
 * 規約（ADR 0010/0012 厳守）: 読書 WebView は本文の機械的取得・ページ加工を一切しない。VM が受け取るのは
 * 画面側が onPageFinished の**URL**から parseNarouEpisodeNumber で割り出した話数のみ（URL 観測＝加工でない）。
 */
class WebReaderViewModel(application: Application) : AndroidViewModel(application) {
    private val bookRepository = (application as NovelReaderApplication).repository

    /**
     * 話ページ(.../N/)への到達を読書位置として記録する（last-wins 上書き）。
     * 画面側で話数抽出済み（話ページ以外は呼ばれない）＝ここでは正当な話数前提で保存に徹する。
     */
    fun onEpisodeReached(ncode: Ncode, episode: Int) {
        viewModelScope.launch { bookRepository.recordWebReadingEpisode(ncode, episode) }
    }
}
