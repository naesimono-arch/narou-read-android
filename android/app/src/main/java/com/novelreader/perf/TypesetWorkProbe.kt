package com.novelreader.perf

import java.util.concurrent.atomic.AtomicLong

/**
 * 本文組版の「仕事量」を**回数**で数える計測プローブ（既定 OFF・出荷ビルドでは不活性）。
 *
 * ## なぜ ms でなく回数か
 * 開発端末（Find X6 Pro / SD 8 Gen 2 / RAM 16GB）もエミュレータも**速い側へ振れる**ため、
 * `gfxinfo` の ms も Macrobenchmark の `frameDurationCpuMs` も「遅い端末での体感」を一切代弁しない。
 * 一方、縦書き組版の構造的な問題は「フォント/行間スライダーのドラッグの**毎値**で可視段落が
 * 再組版される」＝**回数**の問題で、回数・組版文字数・advance 計測回数は
 * **同じ操作なら端末性能によらず同じ値になる**（純粋に composition の起こり方だけで決まる）。
 * ゆえに回数を主指標に置き、ms は補助にする。
 *
 * ## なぜ src/main に置くか（src/debug でない理由）
 * 計測点（[com.novelreader.typeset.DefaultVerticalTypesetter] / [com.novelreader.typeset.render.PaintFontMetrics] /
 * `RubyText` / `NativeReadingScreen`）はすべて main のホットパス上にあり、debug ソースセットから
 * 差し込める縫い目が無い。代わりに出荷物への影響を次の2点で断つ:
 * 1. 既定 OFF。無効時にホットパスで起きるのは `enabled` の volatile 読み1回＋即 return だけ。
 * 2. **有効化する手段（`com.novelreader.perf.TypesetProbeReceiver`）は src/debug 限定**＝
 *    release/benchmark APK には ON にする経路自体が存在しない。
 *
 * ## スレッド
 * 現状の計測点はすべて UI スレッド（composition / text layout コールバック）だが、
 * 改善 A（組版を composition から外し `Dispatchers.Default` へ出す）を入れると別スレッドから
 * 呼ばれる＝**A の前後で同じプローブを使えること**が要件になるため [AtomicLong] で数える。
 */
object TypesetWorkProbe {

    /** 計測の ON/OFF。既定 OFF＝出荷ビルドでは誰も true にできない（有効化経路が debug 限定）。 */
    @Volatile
    var enabled: Boolean = false

    // --- 縦書き（自前組版）---

    /** [com.novelreader.typeset.VerticalTypesetter.typeset] の呼び出し回数＝**1回＝1段落の全再組版**。 */
    private val verticalTypesetCalls = AtomicLong()

    /** 組版で配置し直したグリフ数の総和＝段落長で正規化した仕事量。 */
    private val verticalTypesetGlyphs = AtomicLong()

    /** 組版が生成した列数の総和（版面の広さの指標）。 */
    private val verticalTypesetColumns = AtomicLong()

    /**
     * うち **composition 段（UI スレッド同期）で走った**回数と、そこで置いたグリフ数。
     *
     * なぜ全体と別に数えるか: 改善 A（[com.novelreader.typeset.ChapterTypesetStore]）は組版を消すのではなく
     * **走る場所を移す**。移した先（`Dispatchers.Default`）の回数は端末の速さで変わりうる——値の到着に
     * 追い越されたぶんだけ取り消されるため——のに対し、**composition に残る回数は構造だけで決まる**＝
     * 端末非依存という本プローブの前提（このファイル冒頭の「なぜ ms でなく回数か」）を満たすのはこちら。
     * よって A の成否はこの2つで判定する: 改善前はフォント全振り1ドラッグで 55回・1,845グリフが
     * すべて composition 段だった。
     */
    private val verticalTypesetCallsInComposition = AtomicLong()

    private val verticalTypesetGlyphsInComposition = AtomicLong()

    /**
     * [com.novelreader.typeset.FontMetricsProvider.verticalAdvance] の実装（Paint 計測）呼び出し回数。
     * 組版コストの最小粒度＝**1字1回**の Paint 実測。横書きには対応物が Kotlin 側に存在しない
     * （Compose のテキストエンジンがネイティブで済ませる）＝縦書き固有コストはここに現れる。
     */
    private val verticalAdvanceCalls = AtomicLong()

    // --- 横書き（Compose テキストエンジン）---

    /** `BasicText` の `onTextLayout` 発火回数＝**1回＝1段落の再レイアウト**（縦書きの typeset と対になる単位）。 */
    private val horizontalTextLayouts = AtomicLong()

    /** 再レイアウトした文字数の総和。 */
    private val horizontalLayoutChars = AtomicLong()

    // --- 設定スライダー（再組版の駆動源＝分母）---

    /** フォントサイズ変更コールバックの発火回数（ドラッグ中の move イベントごとに来る）。 */
    private val fontSizeCallbacks = AtomicLong()

    /** うち**値が実際に変わった**回数＝再組版を起こした回数（丸めで同値になった分を除いた分母）。 */
    private val fontSizeEffective = AtomicLong()

    /** 行間変更コールバックの発火回数。 */
    private val lineHeightCallbacks = AtomicLong()

    /** うち値が実際に変わった回数。 */
    private val lineHeightEffective = AtomicLong()

    @Volatile private var lastFontSize: Int = Int.MIN_VALUE

    @Volatile private var lastLineHeight: Float = Float.NaN

    /** 縦書き1段落の組版完了。[glyphs] は配置済みグリフ数、[columns] は生成列数。 */
    fun onVerticalTypeset(glyphs: Int, columns: Int) {
        if (!enabled) return
        verticalTypesetCalls.incrementAndGet()
        verticalTypesetGlyphs.addAndGet(glyphs.toLong())
        verticalTypesetColumns.addAndGet(columns.toLong())
    }

    /**
     * 縦書き1段落の組版が **composition 段で同期に**走った。[onVerticalTypeset] と重ねて数える
     * （こちらは部分集合＝`v_typeset` のうち UI スレッドを止めたぶん）。
     */
    fun onVerticalTypesetInComposition(glyphs: Int) {
        if (!enabled) return
        verticalTypesetCallsInComposition.incrementAndGet()
        verticalTypesetGlyphsInComposition.addAndGet(glyphs.toLong())
    }

    /** 1ユニット分の縦送り実測（Paint）。段落あたり数百〜千数百回来る最小粒度。 */
    fun onVerticalAdvance() {
        if (!enabled) return
        verticalAdvanceCalls.incrementAndGet()
    }

    /** 横書き1段落のテキストレイアウト完了。[chars] はレイアウトした文字数。 */
    fun onHorizontalTextLayout(chars: Int) {
        if (!enabled) return
        horizontalTextLayouts.incrementAndGet()
        horizontalLayoutChars.addAndGet(chars.toLong())
    }

    /** フォントサイズスライダーの毎値コールバック。 */
    fun onFontSizeChange(value: Int) {
        if (!enabled) return
        fontSizeCallbacks.incrementAndGet()
        if (value != lastFontSize) {
            lastFontSize = value
            fontSizeEffective.incrementAndGet()
        }
    }

    /** 行間スライダーの毎値コールバック。 */
    fun onLineHeightChange(value: Float) {
        if (!enabled) return
        lineHeightCallbacks.incrementAndGet()
        if (value != lastLineHeight) {
            lastLineHeight = value
            lineHeightEffective.incrementAndGet()
        }
    }

    /** 全カウンタを 0 に戻す（シナリオ1本ごとに呼ぶ）。 */
    fun reset() {
        verticalTypesetCalls.set(0)
        verticalTypesetGlyphs.set(0)
        verticalTypesetColumns.set(0)
        verticalTypesetCallsInComposition.set(0)
        verticalTypesetGlyphsInComposition.set(0)
        verticalAdvanceCalls.set(0)
        horizontalTextLayouts.set(0)
        horizontalLayoutChars.set(0)
        fontSizeCallbacks.set(0)
        fontSizeEffective.set(0)
        lineHeightCallbacks.set(0)
        lineHeightEffective.set(0)
        lastFontSize = Int.MIN_VALUE
        lastLineHeight = Float.NaN
    }

    /**
     * 1行の key=value 列。`am broadcast` の result data と logcat の両方に載せる形式で、
     * awk/grep でそのまま集計できるようにしてある（値は空白区切り・値に空白を含めない）。
     */
    fun snapshot(): String = buildString {
        append("enabled=").append(enabled)
        append(" v_typeset=").append(verticalTypesetCalls.get())
        append(" v_glyphs=").append(verticalTypesetGlyphs.get())
        append(" v_cols=").append(verticalTypesetColumns.get())
        append(" v_typeset_comp=").append(verticalTypesetCallsInComposition.get())
        append(" v_glyphs_comp=").append(verticalTypesetGlyphsInComposition.get())
        append(" v_advance=").append(verticalAdvanceCalls.get())
        append(" h_layout=").append(horizontalTextLayouts.get())
        append(" h_chars=").append(horizontalLayoutChars.get())
        append(" font_cb=").append(fontSizeCallbacks.get())
        append(" font_eff=").append(fontSizeEffective.get())
        append(" lh_cb=").append(lineHeightCallbacks.get())
        append(" lh_eff=").append(lineHeightEffective.get())
    }
}
