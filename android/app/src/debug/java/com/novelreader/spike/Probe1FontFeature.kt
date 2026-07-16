package com.novelreader.spike

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * プローブ1【最重要】: android.graphics.Paint.fontFeatureSettings="vert" が実際に字形を差し替えるかの機械判定。
 *
 * なぜ必要か: 縦書きを「横書きグリフの90度回転」でなく「縦書き字形（vert グリフ）への差し替え」で組めるかは、
 * 端末の実フォント（特に OPPO/ColorOS は既定フォント実体が Google と割れる懸念）が vert feature を
 * 持つか次第。持たなければ句読点・括弧・長音・小書き仮名を自前でトランスフォームする必要があり、実装方式が変わる。
 * 推測でなく実機で確定させるため、各対象文字を (a)未設定 (b)"vert" の2条件で同サイズ Bitmap にオフスクリーン描画し、
 * ピクセル一致で「字形が変化したか」を判定。measureText/getTextBounds の数値も両条件で記録する。
 * Typeface は既定と Serif（明朝相当）の2種で全対象を回す（本文は明朝＝Serif 系のため両方の実態が要る）。
 */

// 対象文字。縦書きで字形/位置が変わりやすい記号類を中心に、対照として変化しないはずの漢字・仮名も混ぜる
// （＝vert 判定が「常に緑」を返す偽陽性でないことを目視で確認できるように）。
private data class CharGroup(val label: String, val chars: String)

private val TARGET_GROUPS = listOf(
    CharGroup("句読点", "、。，．"),
    CharGroup("括弧類", "「」『』（）〔〕［］｛｝〈〉《》【】"),
    CharGroup("中点・長音・波", "・ー～〜…‥"),
    CharGroup("記号", "：；？！＝−"),
    CharGroup("小書き仮名", "ぁぃぅぇぉっゃゅょゎァィゥェォッャュョヮ"),
    CharGroup("全角英数字", "ＡＢＣ０１２"),
    CharGroup("対照（漢字・仮名）", "亜雨あアｱ"),
    // P0-2b（新規6作品の実データ）で発見された未計測字。〝〞=縦書き用引用符（N8809BK 593回・閉じの実体は
    // U+301F でなく U+301E）・―=U+2015（――連続の構成字）・Ⅳ=ローマ数字・ギリシャは作者固有の単幅特殊字。
    CharGroup("追加計測（P0-2b）", "〝〞―ⅣαβγΔ"),
)

private data class VertResult(
    val typefaceName: String,
    val char: String,
    val codepoint: Int,
    val changed: Boolean,
    val advancePlain: Float,
    val advanceVert: Float,
    val boundsPlain: Rect,
    val boundsVert: Rect,
)

// オフスクリーン描画のキャンバス寸法とテキストサイズ（px）。96px 角に 64px 字形なら全角グリフが収まる。
private const val CANVAS_PX = 96
private const val TEXT_PX = 64f

// 1文字を白背景・中央に黒で描いて RGB ピクセル配列を返す。同一条件下での差分検出用の指紋。
private fun renderGlyphPixels(paint: Paint, ch: String): IntArray {
    val bmp = Bitmap.createBitmap(CANVAS_PX, CANVAS_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(AndroidColor.WHITE)
    val fm = paint.fontMetrics
    val cx = CANVAS_PX / 2f
    // ベースライン中央寄せ（ascent/descent 中点で縦センタリング）
    val baseline = CANVAS_PX / 2f - (fm.ascent + fm.descent) / 2f
    val savedAlign = paint.textAlign
    paint.textAlign = Paint.Align.CENTER
    canvas.drawText(ch, cx, baseline, paint)
    paint.textAlign = savedAlign
    val pixels = IntArray(CANVAS_PX * CANVAS_PX)
    bmp.getPixels(pixels, 0, CANVAS_PX, 0, 0, CANVAS_PX, CANVAS_PX)
    bmp.recycle()
    return pixels
}

private fun measureOne(typefaceName: String, typeface: Typeface, ch: String): VertResult {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.BLACK
        textSize = TEXT_PX
        this.typeface = typeface
    }

    // (a) 未設定
    paint.fontFeatureSettings = null
    val plainPixels = renderGlyphPixels(paint, ch)
    val advancePlain = paint.measureText(ch)
    val boundsPlain = Rect().also { paint.getTextBounds(ch, 0, ch.length, it) }

    // (b) "vert" 設定
    paint.fontFeatureSettings = "vert"
    val vertPixels = renderGlyphPixels(paint, ch)
    val advanceVert = paint.measureText(ch)
    val boundsVert = Rect().also { paint.getTextBounds(ch, 0, ch.length, it) }

    return VertResult(
        typefaceName = typefaceName,
        char = ch,
        codepoint = ch.codePointAt(0),
        changed = !plainPixels.contentEquals(vertPixels),
        advancePlain = advancePlain,
        advanceVert = advanceVert,
        boundsPlain = boundsPlain,
        boundsVert = boundsVert,
    )
}

private fun Rect.toJson(): JSONObject = JSONObject()
    .put("left", left).put("top", top).put("right", right).put("bottom", bottom)

// 全対象 × 2 Typeface を計測し、結果一覧と JSON Lines 出力先パスを返す。
// JSON は 1行=1文字(1 Typeface)。OPPO/ColorOS で実体が割れる懸念があるため typeface 名も各行に持たせる。
private fun runProbe1(context: Context): Pair<List<VertResult>, String> {
    val typefaces = listOf(
        "default" to Typeface.DEFAULT,
        "serif" to Typeface.SERIF,
    )
    val results = ArrayList<VertResult>()
    for ((tfName, tf) in typefaces) {
        for (group in TARGET_GROUPS) {
            var i = 0
            while (i < group.chars.length) {
                val cp = group.chars.codePointAt(i)
                val ch = String(Character.toChars(cp))
                results.add(measureOne(tfName, tf, ch))
                i += Character.charCount(cp)
            }
        }
    }

    val outFile = File(context.getExternalFilesDir(null), "vert_probe_results.jsonl")
    outFile.bufferedWriter().use { w ->
        for (r in results) {
            val line = JSONObject()
                .put("typeface", r.typefaceName)
                .put("char", r.char)
                .put("codepoint", r.codepoint)
                .put("changed", r.changed)
                .put("advance_plain", r.advancePlain.toDouble())
                .put("advance_vert", r.advanceVert.toDouble())
                .put("bounds_plain", r.boundsPlain.toJson())
                .put("bounds_vert", r.boundsVert.toJson())
            w.write(line.toString())
            w.newLine()
        }
    }
    return results to outFile.absolutePath
}

@Composable
fun Probe1FontFeatureScreen() {
    val context = LocalContext.current
    var results by remember { mutableStateOf<List<VertResult>>(emptyList()) }
    var status by remember { mutableStateOf("計測中...") }

    LaunchedEffect(Unit) {
        // 計測は同期的な Bitmap 描画の連打なので Default ディスパッチャで回して UI スレッドを塞がない。
        val (res, path) = withContext(Dispatchers.Default) { runProbe1(context) }
        results = res
        val changedCount = res.count { it.changed }
        status = "完了 ${res.size}件中 変化${changedCount}件\nJSON: $path"
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = status, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.padding(4.dp))
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(results) { r ->
                // 変化=緑/変化なし=赤。advance も併記して数値変化を目視できるように。
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (r.changed) Color(0xFF1B5E20) else Color(0xFF7F1D1D))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "[${r.typefaceName}] '${r.char}' U+%04X".format(r.codepoint) +
                            "  changed=${r.changed}  adv ${r.advancePlain}->${r.advanceVert}",
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}
