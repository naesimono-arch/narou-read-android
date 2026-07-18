package com.novelreader.ui.skins.m

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged

// ============================================================
// スキンM「星図」の常駐 backdrop＝全 M 画面が共有する「動かない不変の空」1枚（ユーザー裁定 2026-07-19「空の一枚化」）。
//
// なぜ NavHost の背後に1枚常駐させるか（裁定の3点）:
//   ①全画面の天の川の形を本棚R1s（DeepSkyM の buildDeepSkyField/drawDeepSky/drawFarStars）へ統一する
//     ＝画面ごとに走向/seed が違う従前を是正（本棚・目次・発見が同じ空を見る）。
//   ②空は「動かない不変のもの」＝画面遷移で背景ごとスライドして壁紙が切り替わる違和感を排す。backdrop は
//     NavHost の外＝遷移の対象にならず、コンテンツ（各画面）だけがフェードで差し替わる（遷移は fade-through）。
//   ③スクロールの極微視差は残すが、画面遷移でそのオフセットがリセットされない＝視差量を backdrop 自身が
//     rememberSaveable で保持し、アクティブ画面からは「スクロール差分」だけを受ける（絶対値でなく差分ゆえ
//     画面が変わっても連続する）。
//
// 性能規律（DeepSkyM 基準・不変）: フィールドは remember 1回・固定 seed の決定的生成／drawBehind の遅延読み。
//   backdrop は NavHost の兄弟＝画面遷移で再コンポーズ/再生成されない（field は backdrop がコンポジションに在る限り生存）。
//   視差 translate は graphicsLayer の描画フェーズ読みで、offset の変化は再コンポーズを起こさない（レイヤ transform のみ）。
// ============================================================

/**
 * 視差オフセット（と読書本文中の非表示要求）を保持するコントローラ。backdrop が draw で `offsetPx` を読み、
 * アクティブ画面が nestedScroll から `onScrollDelta` を呼んで積む。単一インスタンスを CompositionLocal で配る。
 *
 * 【無限スクロール空・2026-07-19 裁定①】「宇宙は無限＝スクロールで天の川が止まる上限があってはならない」。
 *   旧実装は offset を [0,40dp] にクランプ＝40dp で天の川が固まった。撤廃し、空を縦トーラス化してシームレスに無限タイル。
 *   offset は「タイル高で mod 正規化」した周期座標として持つ＝いくらスクロールしても止まらず、Float も無限大に育たない。
 *
 * @param initialTileHeightPx トーラス周期＝縦タイル高（px。初期は画面高の推定値。backdrop が実測して setTileHeight で補正）。
 * @param factor スクロール差分→視差の係数（正本 R1 FACTOR 0.08＝知覚可能な最小の極微視差・不変）。
 * @param reduceMotion アニメーター無効（開発者設定/省電力）。true なら視差を積まない（現状の視差無効と同義）。
 */
@Stable
class SkyParallaxController(
    initialOffsetPx: Float,
    initialTileHeightPx: Float,
    private val factor: Float,
    val reduceMotion: Boolean,
) {
    // トーラス周期（px）。backdrop の onSizeChanged が実測タイル高で更新する（draw の 2 タイル記録の周期と一致させる）。
    private var tileHeightPx: Float = initialTileHeightPx.coerceAtLeast(1f)

    // 視差オフセット（px・[0,tileHeightPx) の周期座標）。backdrop の graphicsLayer が draw フェーズで読む。
    var offsetPx by mutableFloatStateOf(initialOffsetPx.mod(initialTileHeightPx.coerceAtLeast(1f)))
        private set

    // 読書本文（不透明な紙面）表示中は backdrop を描かない（占有され切るため無駄描画を避ける）。transient＝保存しない。
    // 【透過の天の川・2026-07-19 裁定】読書Mは backdrop を透かして見せる（reading-M-rich-R4 中）ため読書中も hidden=false。
    var hidden by mutableStateOf(false)

    // z2 演出（流星）だけを止める二段目のフラグ（空＝z0/z1 は見せる）。読書Mは「透過の天の川」で空を見せるが、
    // 読書Mモーションゼロ（ADR 0022 §3）との整合で流星は抑止する＝hidden とは別軸（空を消さず z2 だけ止める）。transient。
    var meteorSuppressed by mutableStateOf(false)

    /**
     * アクティブ画面のスクロール差分（nestedScroll onPostScroll の consumed.y）を視差へ積む。
     * consumed.y<0（下スクロール＝内容が上へ）で offset を増やし、天の川を上へ滑らせる（現状の向きを踏襲）。
     * クランプせず tileHeightPx で mod 正規化＝上下どちらへ無限にスクロールしても止まらず巻き戻り、値は [0,tile) に留まる
     * （Float 精度劣化の防止も兼ねる）。画面遷移では一切呼ばれない＝offset は保持され連続する（裁定③）。
     */
    fun onScrollDelta(consumedY: Float) {
        if (reduceMotion) return
        offsetPx = (offsetPx - consumedY * factor).mod(tileHeightPx)
    }

    /** backdrop の実測タイル高でトーラス周期を更新し、既存 offset も新周期へ再正規化する（描画周期と一致を保つ）。 */
    fun setTileHeight(px: Float) {
        val h = px.coerceAtLeast(1f)
        if (h == tileHeightPx) return
        tileHeightPx = h
        offsetPx = offsetPx.mod(h)
    }

    companion object {
        /** offset（Float 1本）だけを rememberSaveable で永続（hidden は transient）。tile/factor/reduce は再構築時に再注入。 */
        fun Saver(initialTileHeightPx: Float, factor: Float, reduceMotion: Boolean): Saver<SkyParallaxController, Float> =
            Saver(
                save = { it.offsetPx },
                restore = { SkyParallaxController(it, initialTileHeightPx, factor, reduceMotion) },
            )
    }
}

/**
 * 縦トーラスの可視窓カバレッジ検算（純関数＝JVMテストで回帰担保）。
 *
 * z1 は記録タイル群を translationY=-offset で滑らせ、外 Box の不動クリップで画面 [0, tile] を切り出す。
 * 各タイル k は記録域 [k*tile, (k+1)*tile]＝画面上は [k*tile - offset, (k+1)*tile - offset]。それらの和が画面
 * [0, tile] を 0 から連続で覆い切れるかを返す。旧バグ（clip と translationY 同居）は tile k=1 を切り落とし＝
 * 実効タイルが {0} のみに退化して off>0 で覆えなくなる。本関数へ tileIndices=listOf(0) を渡すとその退化を再現できる。
 *
 * @param tileIndices 記録している（クリップ後も生存する）タイルの添字集合。現行の是正実装では {0,1}。
 */
internal fun torusWindowCovered(offsetPx: Float, tileHeightPx: Float, tileIndices: List<Int>): Boolean {
    if (tileHeightPx <= 0f) return false
    val eps = 1e-3f
    var covered = 0f // 画面 [0, covered] まで連続で覆えている右端
    // 画面上の区間を左端でソートし、隙間なく連結できる範囲を伸ばす（隣接タイルは端点共有で連続）。
    val intervals = tileIndices.sorted().map { k ->
        (k * tileHeightPx - offsetPx) to ((k + 1) * tileHeightPx - offsetPx)
    }
    for ((start, end) in intervals) {
        if (start <= covered + eps && end > covered) covered = end
    }
    return covered >= tileHeightPx - eps
}

/**
 * 読書ナビ位置に応じた backdrop の（空の表示・z2 演出抑止）の解決（純関数＝JVM テストで状態機を固定）。
 *
 * 【透過の天の川・2026-07-19 裁定】読書Mは常駐 backdrop（R1s 実物の天の川）を透かして見せる＝空は常に表示（hidden=false）。
 * ただし本文（index.html 以外）では読書Mモーションゼロ（ADR 0022 §3）との整合で z2 流星を止める（meteorSuppressed=true）。
 * 目次（index.html）は透過の構造画面＝空も流星も見せる（他 M 構造画面と同じ・meteorSuppressed=false）。
 * 非M（backdrop 無し）は両フラグとも false（呼び出し側は M のときだけ controller が non-null）。
 */
internal data class SkyBackdropReadingState(val hidden: Boolean, val meteorSuppressed: Boolean)

internal fun skyBackdropReadingState(isSeizu: Boolean, isIndex: Boolean): SkyBackdropReadingState =
    when {
        !isSeizu -> SkyBackdropReadingState(hidden = false, meteorSuppressed = false)
        else -> SkyBackdropReadingState(hidden = false, meteorSuppressed = !isIndex)
    }

/** 「現在画面→backdrop」へスクロール差分を流す窓口。M 装着時のみ non-null（他スキンは backdrop 無し＝null）。 */
val LocalSkyParallax = staticCompositionLocalOf<SkyParallaxController?> { null }

/** 視差係数（正本 R1 FACTOR 0.08＝体感速度は不変）。トーラス化で上限（旧 40dp クランプ）は撤廃＝周期はタイル高（実測）。 */
const val SkyParallaxFactor: Float = 0.08f

/**
 * 常駐の空 backdrop 本体。skin==SEIZU_M のとき MainActivity が NavHost の背後へ1枚だけ置く。
 * 夜天3層（drawNightSky）→z0 深空（drawDeepSky＝星雲・アクセント星・蔵書非依存）→z1 天の川粒帯（drawFarStars・
 * 極微視差の縦トーラス）→z2 流星（MeteorCanvas）。読了星は含めない（本棚コンテンツ側で drawFinishedStars）。
 *
 * @param controller 視差/非表示のコントローラ（MainActivity が rememberSaveable で1個生成）。
 */
@Composable
internal fun SkyBackdropM(controller: SkyParallaxController, modifier: Modifier = Modifier) {
    // フィールドは backdrop がコンポジションに在る限り生存（NavHost の兄弟＝遷移で dispose されない）。
    // hidden の早期 return より前に remember するのは、読書本文への出入りで field を作り直さない（churn 回避）ため。
    val field = remember { buildDeepSkyField() }
    // 流星スケジューラも hidden の早期 return より前で構成＝読書往復（hidden 切替）で破棄→再起動しない（B の真因対処・
    // DeepSkyM の z2 節参照）。実時間抽選ゆえナビ・スクロールと無相関。
    val meteor = rememberMeteorHost(controller.reduceMotion)
    if (controller.hidden) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind { drawNightSky() }, // 夜天グラデは全面の地色＝トーラス対象外（継ぎ目の無い単一グラデ）
    ) {
        // z0 深空（固定・スクロール非追従）＝星雲＋アクセント星。drawBehind へ一度確定描画（スクロール state 非読）。
        // トーラスに含めない＝これは「動かない不変の深空の地」（裁定②）。無限に流れるのは天の川粒帯 z1（裁定①の対象）。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind { drawDeepSky(field) },
        )
        // z1 遠景視差＝天の川の粒帯＝縦トーラスで無限スクロール（裁定①）。graphicsLayer{translationY} で極微視差
        // （offset は draw/layer フェーズ読み・毎フレーム再コンポーズなし。2 タイルは1回だけ記録し以後は再合成のみ）。
        //
        // 【リグレッション修正・2026-07-19 差し戻し】旧実装は同一 graphicsLayer に clip=true と translationY を同居させた。
        //   RenderNode の clipToBounds はレイヤ「ローカル座標 [0,h]」で効き、その後にレイヤ全体（クリップ枠ごと）を
        //   translationY で平行移動する＝2枚目タイル [h,2h] は平行移動の前に切り落とされ、可視域は常に1タイルぶんしか
        //   残らない。translationY=-offset で1タイルが上へ滑ると下端に offset ぶんの空白が育ち、offset が周期 h に達して
        //   mod で 0 へ巻き戻る瞬間に帯が画面全体へ「バッと」復帰する（＝差し戻しの2症状の機序。2タイル記録は無効化されていた）。
        // 是正: クリップ（画面座標・不動）とトーラス平行移動（レイヤ変換）を別レイヤへ分離する——
        //   外 Box = clipToBounds のみ（変換を持たない＝クリップは画面座標 [0,h] で効く）。
        //   内 Box = graphicsLayer{ translationY=-offset; clip=false }＝2タイルを切り落とさず記録し変換だけ動かす。
        //   これで可視窓 [0,h] は tile0[-off,h-off]∪tile1[h-off,2h-off] に常に包含（off∈[0,h)）＝継ぎ目も空白も出ない
        //   （純関数 torusWindowCovered で検算＝DeepSkyFieldTorusTest）。性能規律不変（記録1回・再合成のみ・draw 再実行なし）。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds(), // 画面座標の不動クリップ（内レイヤの translationY に連動しない＝タイルを切り落とさない）
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { controller.setTileHeight(it.height.toFloat()) } // トーラス周期＝実測タイル高（draw と一致）
                    .graphicsLayer {
                        translationY = -controller.offsetPx
                        clip = false // クリップは外 Box（不動）が担う＝平行移動でタイル k=1 を切り落とさない
                    }
                    .drawBehind {
                        val tileH = size.height
                        drawFarStars(field)                                  // タイル k=0（[0,h]）
                        translate(top = tileH) { drawFarStars(field) }       // タイル k=1（[h,2h]）＝上下に隙間なく無限タイル
                    },
            )
        }
        // z2 演出＝まれな流れ星（時間イベント・非決定）。全 M 画面で同じ空ゆえ全画面で流れる（意匠上自然）。
        // ただし読書M本文は「空は見せるが z2 は止める」（meteorSuppressed＝読書Mモーションゼロ ADR 0022 §3）。
        // スケジューラ（rememberMeteorHost）は早期 return 前で生かしたまま描画だけ止める＝読書往復で破棄→再起動しない。
        if (!controller.meteorSuppressed) {
            MeteorCanvas(meteor, modifier = Modifier.fillMaxSize())
        }
    }
}
