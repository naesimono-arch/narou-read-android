package com.novelreader.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 取込進行表示（ProcessingBanner）の供給元。
 * PDF＝PdfProcessingService（FGS・4段ステップ変換）／WEB＝BookshelfViewModel.importWebNovel（章単位取得）。
 * バナーの出し分け（ステッパーの有無）と停止操作のディスパッチ先（Service への ACTION_STOP か
 * viewModelScope ジョブの cancel か）の両方をこの区別で決める。
 */
enum class ProcessingSource { PDF, WEB }

/**
 * 供給元別の ProcessingState スロットと、バナーが購読する単一の表示状態を持つハブ。
 *
 * なぜスロット分離か（2026-07-29「全部直す」裁定③・分離を選んだ理由）:
 * 旧実装は単一 MutableStateFlow を PDF（Service）と Web（ViewModel）が直接共有しており、
 * 並走時に相互上書き（片方の完了 null 書きが他方のバナーごと消す／進捗が混線する）が起きていた。
 * 直列キュー化は「Web 取込を FGS キューへ相乗りさせる」大きな構造変更（Web は pending_jobs 非対象・
 * FGS 不使用の設計＝WebBookImporter 冒頭の why）になるため、書き込み側の独立を保ったまま
 * 表示だけを合成するスロット分離が既存構造に沿う。
 *
 * 表示の優先は PDF > WEB（固定）: PDF は長走行の FGS で通知・停止のページ境界意味論を持つ主役、
 * Web 取込は短時間で終わる従属側。並走時に隠れた側も自スロットは生き続け、優先側が畳まれた
 * 時点で表示に浮上する（＝潰されない）。
 */
class ProcessingStateHub {
    private val lock = Any()
    private val slots = arrayOfNulls<ProcessingState>(ProcessingSource.entries.size)

    private val _displayState = MutableStateFlow<ProcessingState?>(null)

    /** バナーが購読する表示状態（PDF スロット優先・両方 null なら null＝バナー非表示）。 */
    val displayState: StateFlow<ProcessingState?> = _displayState.asStateFlow()

    /** 指定スロットの更新。null で畳む。スロット代入と表示合成を同一ロックで行い、
     *  並走する Service（IO スレッド）と ViewModel（Main）の書き込みが交錯しても
     *  表示が必ず「最新のスロット内容の合成」になることを保証する。 */
    fun update(source: ProcessingSource, state: ProcessingState?) {
        synchronized(lock) {
            // source フィールドをスロット側で刻印する: 書き手（特に既存の Service 呼び出し）が
            // ProcessingState.source を設定し忘れても、表示状態の source は常にスロットと一致し、
            // 停止ディスパッチ・ステッパー出し分けが取り違えない（防御的刻印）。
            slots[source.ordinal] = state?.copy(source = source)
            _displayState.value =
                slots[ProcessingSource.PDF.ordinal] ?: slots[ProcessingSource.WEB.ordinal]
        }
    }

    /** 指定スロットの現在値（表示合成ではなく生スロット）。停止操作の isStopping 反映など
     *  「自分の供給元の状態だけを読み書きしたい」呼び出しが表示状態越しに他スロットを
     *  誤読しないための読み口。 */
    fun stateOf(source: ProcessingSource): ProcessingState? =
        synchronized(lock) { slots[source.ordinal] }
}
