package com.novelreader.ui.skins.m

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.novelreader.ui.theme.BaseSeizu
import com.novelreader.ui.theme.SkyCloudSeizu
import com.novelreader.ui.theme.SkyGradEndSeizu
import com.novelreader.ui.theme.SkyGradMidSeizu
import com.novelreader.ui.theme.SkyHorizonSeizu

// ============================================================
// スキンM「星図」の canvas 共通部品（本棚 BookshelfSkyM ／ 目次 TocSkyM が共有・ADR 0022 §1 の構造分岐先）。
// 二重定義を残さないための集約（C2 で目次を追加した際に Lcg と夜天背景を本ファイルへ抽出）。
// ============================================================

/**
 * モックと同じ線形合同法（seed 決定論）。Kotlin Random と混ぜないのは星配置の再現性を1系統に保つため。
 * bookshelf-M.html / toc-M.html の `rnd=()=>{seed=(seed*1103515245+12345)&0x7fffffff;return seed/0x7fffffff;}` を写す。
 */
internal class Lcg(seed: Int) {
    private var s = seed or 1
    fun next(): Float {
        s = (s * 1103515245 + 12345) and 0x7fffffff
        return s / 2147483647f
    }
}

/**
 * 夜天の3層グラデ背景（群青リニア＋右上の青雲 radial＋下辺の地平光 radial）。値の正本＝bookshelf-M.html .phone。
 *
 * なぜ本棚・目次で同一背景か: toc-M.html の .phone は雲 α .24／位置 74% 8%・地平光なしと僅かに異なるが、
 * 監督裁定（C2 仕様書 §2「夜天グラデ＝BookshelfSkyM と同じ Brush 3層」）で両星図画面の夜天を統一する
 * （同じ空の相を本棚⇄目次で連続させる意図・ADR 0022 §1）。toc 側の微差はこの統一で意図的に捨てる。
 */
internal fun DrawScope.drawNightSky() {
    drawRect(
        Brush.verticalGradient(
            0f to BaseSeizu, 0.44f to SkyGradMidSeizu, 1f to SkyGradEndSeizu,
        )
    )
    drawRect(
        Brush.radialGradient(
            colors = listOf(SkyCloudSeizu.copy(alpha = 0.28f), Color.Transparent),
            center = Offset(size.width * 0.72f, size.height * 0.20f),
            radius = size.width * 0.78f,
        )
    )
    drawRect(
        Brush.radialGradient(
            colors = listOf(SkyHorizonSeizu.copy(alpha = 0.55f), Color.Transparent),
            center = Offset(size.width * 0.30f, size.height * 1.08f),
            radius = size.width * 1.2f,
        )
    )
}
