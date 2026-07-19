package com.novelreader.ui.skins.m

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.novelreader.ui.theme.FaintStarSeizu
import com.novelreader.ui.theme.StarCoreSeizu
import com.novelreader.ui.theme.StarNeutralSeizu
import com.novelreader.ui.theme.StarTempAmberSeizu
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// ============================================================
// スキンM「星図」の【高負荷スカイ】試作レイヤ（ADR 0023・debug トグル起点）。v6＝実機裁定「タイルの繰り返しが気になる／
// その場でリアルタイム生成できないか」「帯が端でぶつっと切れる。天の川は途切れない線のはず」への作り直し応答。
// v5（AGSL グロー全廃＋粒密度変調で天の川を彫る＋早回しドリフト）の設計を【チャンク式無限プロシージャル生成】へ発展させる。
// v6.2＝実機裁定「天の川が薄すぎる・全然足りない／蛇行が弱い1.3倍急に／knot が星の塊と気づけない」への較正強化（機構は不変・生成分布と
//   較正値のみ）。①帯粒 6000→12000＋輝度床0.07→0.12・径0.20→0.24基底＝帯の総発光 ~3.6倍（数値自己検証）。②帯軸の合成曲率（Σ振幅×周波数）
//   を ×1.3（周波数のみ×1.3・振幅据え置きで x 域維持）。③knot 湧き0.16→0.30・密度0.55→0.85・中心の輝度/暖色強化＝「星の塊」を知覚可能に。
//
// v7＝「動く天体・時間スケールの階層」を追加（既存の流星＝一瞬と合わせ〈一瞬/数分/数日/常時〉の4階層）。
//   ・一瞬=流星（既存 MeteorHost・SkyBackdropM が highLoad 分岐の前で remember＝高負荷でもスケジューラは生きている＝流星は落ちて
//     いない。実機で「見えない」の真因は 平均≒60s間隔×掃過≒1s×尾α≤0.42 で、v6.2 で帯が3.6倍明るくなり相対コントラストが更に低下した
//     こと）。→ 高負荷では専用描画 drawHlMeteor で存在感を増す（芯/尾を加算で明るく・共有の MeteorCanvas/MeteorTuning は不変＝通常モード非影響）。
//   ・数分=人工衛星（SatelliteHost・時間イベント＝seed 不要・非決定）: 瞬かない光点が完全な直線で画面空間を横断（30〜60s）・
//     ドリフト/視差から独立（画面座標で動く）・尾なし・中程度の輝度。数分に1度（指数分布・平均≒3分）。
//   ・彗星（実時刻決定的＝新種の決定性。v7.4 で日単位→30分横断へ）: System.currentTimeMillis を固定スロットで割った横断回次 k から
//     位置/軌道を O(1) hash 導出＝「同じ時刻に開けば同じ位置」。30分で1横断（見ていて分かる進み）＋不在期1〜3時間。淡い2本尾＋頭部光芒。
//   ・常時=BH 重力レンズ（チャンク hash 決定的・帯内に数十チャンクに1度）: mid 焼き込み時に孔近傍の粒座標を放射方向へ写像（点質量レンズの
//     θ=½(β+√(β²+4Re²))）＝Re 内に粒が入らず（黒い孔）・Re 近傍へ集積（アインシュタインリング）。発光体は描かず＝黒と歪みのみ・毎フレーム負荷ゼロ。
//
// v7.2＝天体の一目性/迫力を上げる磨き込み（実機裁定。機構・決定性・OFF 同一・毎フレーム負荷の性質は不変＝較正と描画造形のみ）:
//   ・衛星「分かりにくい」→ 過剰装飾を避けつつ①径微増（1.35→1.8＝実機 ~3.7px→5px 級）②横断中1度だけの滑らかな増減光フレア（イリジウム
//     フレアの実現象＝数秒のガウス山なり・尖った明滅でなく・ピークで一等星級＋淡い光芒）③太陽光反射色のわずかな暖色。点滅/尾/派手装飾は不可。
//   ・彗星「尾に見えない（白点の後ろに丸が2,3個）」→ 尾を粒の点列から本物へ作り直し。頭部から後方へ扇状に開く連続グラデ（Path＋linearGradient）
//     を2本（まっすぐ淡青のイオンテイル＋緩くカーブする白のダストテイル）＋多数の微粒を減衰散布＝「流れている」見え。長さも伸ばす。
//   ・BH「初見でそれと認識しづらい」→ 方向裁定が割れているため2案を実装し debug BH ボタン連打で交互に湧かせ実機比較（案A=現径 Re=0.033＋
//     明確な細いアインシュタインリングを明示描画／案B=半径 Re=0.017 のリング無し漆黒小孔）。※「孔内部の真の黒」は追加描画側の設計判断を要す
//     （下記 drawComet/BH 節および報告参照）＝加算合成レイヤ単独では地色より暗くできないため、迫力は明示リングとの明暗コントラストで作る。
//
// v7.3＝実機総評「宇宙の大きさに対し各天体を過大描画」への較正（機構・決定性・OFF 同一は不変・値と描画パラメータのみ）。衛星・流星は
//   合格＝不変。①彗星: 尾長を半分（0.32→0.16）・扇の開き角を1.5倍（後端半幅 0.05→0.0375）＝空に対し控えめに「見つかる」尾へ。②BH 2案を
//   再定義: 案A=Re を小さく(0.033→0.020)＋リングをさらに薄く鋭く(幅/輝度を絞る)＋フォトンリングも小さめ＝帯幅より明確に小さい「気づく人が
//   気づく」存在／案B=v7.2 案A と同径(Re=0.033)のリング無し漆黒孔。debug BH ボタンの A/B 交互湧きは維持。
//
// v7.4＝実機裁定（彗星=概ねOK・BH=まだ大きい。衛星「完璧」・流星=合格＝不変）への較正:
//   ①彗星の移動を「30分で1横断」の実時刻ベースへ再設計（日単位は進んでなさすぎ→見ていて数十秒で位置変化に気づく）。O(1) 決定性は維持
//     （横断回次 k の hash＝同じ時刻に開けば同じ位置）・不在期1〜3時間。②イオン尾の揺らぎ（監督裁定=採用）＝太陽風でゆらめく実機序を
//     timeS 駆動の±5%正弦波で極微に（ダストは慣性大＝揺らさない）。③BH 両案とも 5/12 サイズへ（案A Re 0.020→0.00833・案B 0.033→0.01375）。
//
// v7.5＝奥行きの深化3件（ユーザー提案。機構・決定性・OFF 同一・天体一式・帯較正・DRIFT・チャンク機構は不変＝焼き込み描画と
//   レイヤ構成のみ）。美学＝「空は巨大・天体は小さい／効果の存在に気づかせない・見続けるほど深い」:
//   ①空気遠近法（色と鮮明さの深度差・毎フレーム負荷ゼロ＝焼き込み時の描き分け）: 星空写真の機序そのもの＝遠いものほど大気で
//     青ずみ・ぼやける。far 粒を「わずかに青寄り（AerialFarTint へ ~0.12 lerp）＋ソフトエッジ（中心→透明の放射で輪郭をにじませる）」に、
//     near 粒を「シャープ（現状の鋭いコア維持）＋やや暖色（AerialNearTint へ ~0.10 lerp）」に。mid は中間＝色は無彩基調のまま・
//     エッジも締めたまま（＝帯の解像＝「粒でできた天の川の芯」を守るため mid はソフト化しない。中間性は主に色温度で担う）。
//     温度勾配は「並べれば分かるが単体では気づかない」量。
//   ②前面の暗黒シルエット雲（遮蔽による立体）: near よりさらに手前（視差比 RATIO_CLOUD=2.2×・新レイヤ）に、発光しない真っ黒な
//     ちぎれ雲を時折（CLOUD_PROB・チャンク hash 決定的）に流す。手前の雲が背後の星を隠して通り過ぎる「遮蔽」
//     ＝立体感を最も強く生む視覚手がかり（実際の暗黒星雲と同じ機序）。加算合成の星層では「暗く塗る」が効かないため、雲は独自レイヤで
//     SrcOver 黒（中心 α高め→端はローブのソフト減衰）で全星層の上から遮蔽する（発光しない＝白靄=v3失敗には構造的に転ばない）。
//     形は複数の柔い黒ローブ（重なりで中心が濃く＝ほぼ真っ黒）＋離れたちぎれ房で fbm 的 torn を近似。サイズは画面高の 1/6〜1/4級で
//     過大にしない。毎フレーム負荷は「可視チャンクに雲があるときだけ雲1枚ぶんの SrcOver 描画・不在チャンクは無コスト（空リスト）」。
//   ③天の川の2層化（手前/奥の微速度差＝立体・シアー無限蓄積の回避込み）: mid を mid-back/mid-front に分け（粒を折半 6000+6000＝
//     総密度・帯較正は不変／帯ジオメトリは同一ワールド関数 bandAxisWorld 等を共有）、深度差を次のハイブリッドで表現する——
//       (i) ドリフト速度差は sin 往復変調（手前層の相対位相を ±MID_SWAY_AMP_PX・MID_SWAY_PERIOD_S 周期でゆっくり往復）。
//       (ii) スクロール視差比の僅差（front RATIO_MID_FRONT=1.04×/back RATIO_MID_BACK=0.96×）。
//     【なぜ独立線形ドリフト差は禁止で往復変調なのか＝シアー無限蓄積の機序】ドリフトは常時前進する「無界の連続運動」ゆえ、同一の
//       1本の天の川を成す2層に独立した線形ドリフト速度差 Δv を与えると、相対ずれ = Δv×t が時間とともに際限なく増え、帯が2本にほどける
//       （far/near のような別フィールド同士なら視差で無限に離れてよいが、mid 2層は同じ天の川なので離れてはならない）。よってドリフト
//       成分は両層で厳密同一（RATIO_MID）に固定し、深度差は (i) 有界な往復（sin＝net-zero で蓄積ゼロだが d/dt≠0 ゆえ見かけの速度差は
//       常在）と (ii) 有界なスクロール比差（スクロールは実用上有界＝シアーも有界）のみで作る。BH は両層とも同じレンズ写像を掛ける
//       （孔が両層で一致）が、明示リング deco は back のみ焼く（加算二重＝2倍輝度を避ける）。
//   ④不変: v7 天体一式・帯較正・DRIFT 0.0062・チャンク機構・決定性・OFF 時厳密同一・reduce-motion（往復変調・雲ドリフトは drift/
//     breathe 時計が止まれば自動で停止＝既存の停止規律に相乗り＝専用分岐不要）。
//
// 【決定性の3規律（v7 で整理＝混ぜない）】静的な地（星の帯・knot・BH）＝チャンク hash（ワールド位置で決定）。時間イベント（流星・衛星）＝
//   実エントロピー seed・ナビ/スクロール無相関（＝seed を持ち込まない）。彗星＝実時刻が seed の新種（v7.4: 横断回次 k を hash＝同じ時刻＝同じ位置）。
//
// 思想（モード分離・不変）: 通常モード（SkyBackdropM の else 経路・DeepSkyM の規律）は一切触らない。高負荷側は SkyBackdropM の
//   SkyParallaxController から「wrap しない累積スクロール（scrollWorldPx）」だけを購読し、独自のワールド座標系で無限に空を生成する。
//   controller の mod wrap（offsetPx/setTileHeight）には一切依存しない＝通常モードの挙動に副作用を残さない（v5 の setTileHeight(h×4)
//   は撤去。高負荷は controller の wrap を使わないため不要になり、OFF 時の周期を汚さない）。
//
// v6 のチャンク機構（番号は v6 確定事項に対応）:
//   ① チャンク式無限生成: 空をチャンク（＝1画面高ぶんの区画）に分割し、チャンク index（Long）を seed に混ぜた決定的 hash で
//      粒を生成（同じ場所へ戻れば同じ星・どこまで行っても繰り返さない）。焼いた bitmap は深度ごとにリング（各3枚＝可視2＋先読み/
//      後読み1）で再利用し、境界跨ぎ時に不足チャンクだけ焼き直す（毎フレームは blit のみ＝現状同等）。焼きは背景スレッド
//      （Dispatchers.Default）で先んじて行い snapshot 状態へ載せる＝フレーム落ちを避ける。ドリフトは十分遅い（後述）ため同期焼き
//      でも間に合うが、速いスクロールでの一瞬の欠けを避けるため隣接チャンクを先読みする（req1 の先読み基本を採用）。
//   ② 帯ジオメトリをワールド座標の連続関数へ: 帯の中心線 bandAxisWorld・幅・ダークレーン・分子雲むらの全てを「ワールド Y
//      （無限連続のスクロール空間・単位=画面）」の関数で評価＝チャンク境界で天の川が切れない一本の蛇行した線になる。R1s の対角
//      走向は「1画面あたり大きく傾く低周波 sin」で維持しつつ、複数 sin の和で無限に winding させる（端で途切れない天の川）。
//      【v6.1 帯崩壊の是正】v6 初版は帯本体 mid を「一様撒き＋確率棄却」で作り、帯内外の密度比が ~1.7:1 に潰れて「天の川でなく
//      全体に散った星」になった（実機強い差し戻し）。真因＝v5 の帯（drawFarStars）は「軸まわりへガウス配置」で密度比 ~600:1 の
//      濃い一本の帯だったのに、その配置アルゴリズムを一様撒きへ替えてしまったこと。→ mid を v5 のガウス帯生成へ回帰（genBandGrains・
//      彫り/輝度/径は v5 値のまま）＝密度比 ~600:1・軸±半幅に ~95% 集中（数値自己検証）。far は帯を支える淡い層（base 0.18 へ下げ散りを抑制）。
//   ③ 画面固定の大構造を残さない: v5 の固定バルジ（y≈300 固定）は廃止。バルジ級の密集域は「帯に沿って稀に（数チャンクに1度・
//      チャンク hash で決定的に）現れる星団状の knot」として配置＝一期一会の天体（流れに乗って通り過ぎる）。
//   ④ float 精度対策: 累積オフセットは Double で保持（driftPx＝毎フレーム Long ナノ秒差分から加算＝小さな値の足し込みで精度を保つ）。
//      チャンク選択は floor(cameraPx/h).toLong()＝整数 index（誤差なし）。帯/むらの連続関数は sin 引数を Double で評価（Math.sin の
//      引数リダクションで大 index でも精度維持）・値ノイズの格子は整数（Long）ハッシュ＝大座標でも破綻しない。数時間〜年でも崩れない。
//   ⑤ 通常モード共有部: controller は offsetPx/tileHeightPx を通常モード z1 が使う＝一切変えない。高負荷用に wrap なしの
//      scrollWorldPx を1本足すだけ（onScrollDelta で積む・通常経路は不変）。v5 の setTileHeight 副作用は撤去済み（本ファイルは
//      setTileHeight を呼ばない）。
//   ⑥ 維持: ドリフト速度 0.0093・深度3面の視差/ドリフト速度比（各面が独立のワールド座標＝視差＋ドリフトを ratio 倍）・静謐要素
//      （息づき±0.06〜0.12/3〜8s・にじみハロー×2.3 脈動なし・恒星彩度0.6・芯白0.42・スパイク廃止）・帯の彫り（Great Rift 0.93・
//      darkNeb・fila・沿軸密度勾配＝v5 drawFarStars の値）・ディザ・0.42・決定性・reduce-motion（ドリフト/息づき停止・スクロール視差は維持）・
//      OFF 時は通常モードと厳密同一。
//   ⑦ 毎フレームコストは blit のみ（チャンク焼き直しは境界跨ぎ時だけ・背景スレッド）。
// ============================================================

// 3深度の視差係数（遠=ゆっくり・近=速い）。中面 0.08 は現行 z1 と同値＝天の川本体の体感速度を保つ。
private const val FACTOR_FAR = 0.04f
private const val FACTOR_MID = 0.08f
private const val FACTOR_NEAR = 0.14f
private const val RATIO_FAR = FACTOR_FAR / FACTOR_MID   // 0.5
private const val RATIO_MID = 1f
private const val RATIO_NEAR = FACTOR_NEAR / FACTOR_MID // 1.75

private const val TWO_PI = 6.2831853f

// ⑥ 早回し日周ドリフト（画面高/秒・mid 基準）。深度比 ratio を掛ける（近い面ほど速い）。裁定履歴: 0.014→0.0093→0.0062（2026-07-19「三分の二に」・mid 約161s/画面）。
private const val DRIFT_SCREENS_PER_SEC = 0.0062f

// ===== ② 帯本体 mid のガウス帯生成の較正（v5 drawFarStars の帯＝実機 OK の姿へ回帰）=====
// 帯粒数（1チャンク=1画面あたり）。v6.2 実機裁定「薄すぎる・全然足りない」→ 6000 を倍増（小刻みにしない）。ガウス配置ゆえ
// ~95% が軸±半幅に集中＝帯内密度は倍化しても帯内外比は維持（むしろ帯本体だけ増える＝帯として更に読める）。輝度床/径も一段上げ。
private const val MID_BAND_COUNT = 12000
private const val MID_RIFT_W = 13f / NW     // 暗黒帯の太さ（v5 dr=(off-riftCenter)/13 の 13px を /390 正規化）
private const val MID_PIP_BRIGHT = 0.80f    // これ以上明るい帯粒に白芯 pip＋にじみハロー（v5 の輝星＝解像を締める）

// ===== ② 遠景 far の粒密度変調（帯を支える淡い層＝v5 far と同流儀の一様撒き＋帯寄せ。帯本体は mid が担う）=====
// far は「全天の淡いダスト＋帯への寄せ」。帯崩壊の主因だった base を下げ帯寄与を上げる（散った星の支配を抑え、帯を支える側へ）。
private const val FAR_BASE_PROB = 0.18f    // 帯外の全天ダスト確率（v5 は 0.30＝散りの主因。下げてコントラストを上げる）
private const val FAR_BAND_PROB = 0.70f    // 帯内の追加確率（far も帯へ寄せて mid の帯を支える）
private const val FAR_RIFT_DEPLETE = 0.88f // 暗黒帯（ダークレーン）の粒欠乏率（帯内で最大88%間引く＝はっきり読める暗黒レーン）

// ③ 稀な星団状 knot（画面固定バルジの代替＝流れに乗る一期一会の密集域）。
// v6.2 実機裁定「knot が物足りない・星の塊と気づけない」→ 湧き確率と密度ブーストを引き上げ「あそこに星の塊がある」と知覚できる存在へ。
private const val KNOT_PROB = 0.30f        // チャンクごとに knot が湧く確率（≈3チャンクに1度＝0.16 から倍増）
private const val KNOT_DENSITY = 0.85f     // knot が置く確率へ足す密度ブースト（0.55→0.85＝軸上で keep 飽和＝はっきり密集）
private const val KNOT_SY = 0.12f          // knot の縦ガウス径（画面単位＝1チャンク未満＝隣接3チャンク評価で境界も連続）
private const val KNOT_SX = 0.09f          // knot の横ガウス径（正規化 x）

// 分子雲むらの空間周波数（値ノイズのスケール＝塊の大きさ）。
private const val MOTTLE_K = 2.6f

// seed 合成の salt（深度/用途ごとに独立ストリーム＝隣接チャンクで相関しないよう hash で well-distributed 化）。
private const val FAR_SALT = 0x00FA0001L
private const val MID_SALT = 0x00312002L
private const val NEAR_SALT = 0x000EA503L
private const val KNOT_SALT = 0x5EED1111L
private const val CLUSTER_SALT = 0x00C10004L
private const val BH_SALT = 0x0B1AC01EL      // v7 BH（重力レンズ）のチャンク抽選ストリーム

// 近景 near（大粒・少数・明・息づき）。チャンクごとに決定的生成（bitmap でなくライブ描画＝息づきの α を毎フレーム動かせる）。
private const val NEAR_PER_CHUNK = 45
private const val CLUSTER_PROB = 0.30f     // チャンクごとに星団（プレアデス様）が湧く確率
private const val CLUSTER_STARS = 9
private const val FIRSTMAG_THRESH = 0.93f  // これ以上明るい near 星に縮小にじみハローを付す（v5 の上位N点→無限空では固定しきい値）
// 息づき⑥＝「またたき」でなく「息づき」（振幅を大幅減衰し短周期を廃す）。
private const val TWINKLE_MAG_MIN = 0.28f  // これ以上明るい near 星が息づく
private const val TWINKLE_AMP_MIN = 0.06f  // 息づき振幅（±0.06〜0.12＝存在に気づかせない微揺らぎ）
private const val TWINKLE_AMP_MAX = 0.12f
private const val TWINKLE_PERIOD_MIN = 3.0f // 周期 3〜8s のみ（短周期＝速い「またたき」を全廃）
private const val TWINKLE_PERIOD_MAX = 8.0f

// チャンクのリング窓（可視は chunk0/chunk0+1 の2枚＝blitDepth が描く）。焼き/保持は [chunk0-BEHIND, chunk0+AHEAD]。
// ドリフトは常に前進（camPx 増加＝chunk0 は単調増）ゆえ前方 AHEAD を厚めに先読み（+2 まで＝跨ぎ前に次チャンクが焼き上がる）・
// 後方 BEHIND は 0（後退はスクロールのみ＝稀。必要時に非同期で焼き直す＝瞬間の欠けは背景ゆえ許容）。MARGIN=0＝保持窓=焼き窓
// （ばたつきは焼きが非同期・安価ゆえ許容し、メモリを抑える＝各深度3枚保持＝far+mid で6枚。ADR 0023 の高負荷許容の範囲）。
private const val CHUNK_AHEAD = 2L
private const val CHUNK_BEHIND = 0L
private const val CHUNK_EVICT_MARGIN = 0L

// ディザ（面のバンディング対策）: 64px 角のノイズタイルを全面へ敷き詰め。振幅は ±~2/255 級（過剰にしない）。
private const val DITHER_TILE = 64f
private const val DITHER_AMP = 0.02f

/** 恒星色の彩度係数＝全粒の「色の乗り」を一律で抑える（実在の夜空では肉眼で星の色はほぼ無彩色〜わずかに青白/橙）。 */
private const val STAR_SATURATION = 0.6f
// M型(赤)アンカーだけを試作ファイル内に足す（DeepSkyM の色温度4アンカーは橙止まり＝赤Mを欠く。Color.kt トークンは触らない）。
private val StarTempRedSeizuHl = Color(0xFFE0906E)

// ============================================================
// ===== v7 動く天体（時間スケールの階層）の較正 =====
// --- 数分=人工衛星（時間イベント・seed 不要・画面空間の直線横断）---
private const val SAT_INTERVAL_MIN_MS = 120_000L  // 最短間隔（指数分布の下駄）
private const val SAT_INTERVAL_MEAN_MS = 120_000L // 指数分布の平均（流星≒60s より疎＝時間スケールの対比を立てる）
private const val SAT_INTERVAL_MAX_MS = 360_000L  // 裾切り上限（間延び防止）
private const val SAT_CROSS_MIN_MS = 30_000       // 横断時間（この間に画面を端から端へ）
private const val SAT_CROSS_MAX_MS = 60_000
private const val SAT_ALPHA = 0.5f                // 平常時の輝度＝中程度の星と同等（主張しない）・瞬かない＝一定
private const val SAT_CORE_ALPHA = 0.62f          // 芯の白（点を締める）
// v7.2「分かりにくい」→ 過剰装飾を避けつつ目立たせる3点。①径微増（実機 ~3.7px→5px 級。1.35×(5/3.7)≒1.8）。
private const val SAT_RADIUS_NW = 1.8f            // 名目 NW(=390) スケールの半径（画面幅で拡縮＝流星の芯と同流儀）
// ②イリジウムフレア＝横断中の1点で数秒かけ滑らかに増光→戻る（尖った明滅でなく progress のガウス山なり）。ピークで一等星級。
private const val SAT_FLARE_PEAK = 0.95f          // フレア頂点の絶対輝度（一等星級＝加算白で明確に抜ける）
private const val SAT_FLARE_SIGMA = 0.05f         // 山の幅（progress 単位。横断30〜60s に対し FWHM≒0.118×dur≒3.5〜7s＝数秒）
private const val SAT_FLARE_RADIUS_GAIN = 0.7f    // 頂点での径の肥大率（+70%＝増光時に少し大きく見える bloom）
private const val SAT_FLARE_PEAK_MIN = 0.35f      // フレア頂点 progress の下限（画面内で起きるよう中盤へ）
private const val SAT_FLARE_PEAK_SPAN = 0.30f     // 頂点 progress の散らし幅（[0.35,0.65]＝毎回違う位置で1度）
// ③太陽光反射色のわずかな暖色（衛星本体＝金属/太陽電池の反射＝5800K 級のやや暖かい白。低彩度で「わずかに」）。
private val SatelliteColor = Color(0xFFFFF1DD)

// --- 彗星（実時刻決定的）---
// v7.4 総評「日単位移動は進んでなさすぎて分かりにくい」→ 実時刻ベースで「30分で1横断」する天体へ再設計（見ていると数十秒で位置変化に気づく）。
//   決定性は維持: 位置/軌道は横断回次 k（＝実時刻を固定スロットで割った index）の hash から O(1) で導出＝同じ時刻に開けば同じ位置。
//   タイムライン: スロット k（固定長 SLOT）内で crossing(30分) を jitter_k だけ後方へずらして配置＝スロット間の不在ギャップが自然に散る。
//   ギャップ = SLOT + jitter_{k+1} − jitter_k − CROSS。SLOT=2.5h・CROSS=0.5h・jitter∈[0,1h] ⇒ ギャップ∈[1h,3h]（不在期1〜3時間級）。
private const val COMET_SALT = 0x0C0FFEE1L
private const val COMET_SLOT_MS = 9_000_000L      // 横断スロット長（2.5h）。ギャップ中心 = SLOT−CROSS = 2h
private const val COMET_CROSS_MS = 1_800_000L     // 1横断の所要（30分＝この間に画面を横切る）
private const val COMET_JITTER_MS = 3_600_000L    // 横断開始オフセットの散らし幅（0〜1h）＝不在ギャップを [1h,3h] に散らす
private const val COMET_ANCHOR_MS = 0L            // スロットの位相アンカー（Unix epoch 基準＝任意の固定値でよい。決定性のためだけの定数）
private const val COMET_SPAN = 0.9f               // 30分で横断する画面比（入口→出口。0.55→0.9＝見ていて分かる進み）
// 尾のイオン揺らぎ（監督裁定=採用。太陽風でイオンテイルがゆらめく実機序を極微に。ダストは慣性大＝揺らさない）。
private const val COMET_ION_SWAY_PERIOD_S = 15f   // 揺らぎ周期（10〜20s級のゆっくりした正弦波）
private const val COMET_ION_SWAY_CURVE = 0.10f    // イオン尾の曲率変調振幅（尾長比。±5%は実機で知覚不能＝2026-07-19裁定→±10%へ）
private const val COMET_ION_SWAY_WIDTH = 0.10f    // イオン尾の開き（半幅）の変調率（±10%。同上の裁定で増幅）
// v7.2 尾の作り直し（「白点の後ろに丸が2,3個」→ 扇状に開く連続グラデ＋微粒散布の本物の尾）。長さは大幅に伸ばす（画面高の 1/3 級）。
// v7.3 総評「宇宙に対し過大」＝天体は小さく「見つかる」サイズへ。尾長を半分に、扇の開き角は1.5倍に。
private const val COMET_TAIL_LEN = 0.08f          // 尾長（画面高比。0.32→0.16→0.08＝2026-07-19裁定「半分に」）
// 扇の開き角∝ 後端半幅/尾長。尾長を半減しつつ開き角を v7.2 の1.5倍にする＝半幅 = 1.5×(0.05/0.32)×0.16 = 0.0375。
private const val COMET_DUST_HALF = 0.019f        // ダストテイルの後端半幅（0.0375→0.019＝2026-07-19裁定「幅半分」）
private const val COMET_ION_HALF = 0.0055f        // イオンテイルの半幅（0.011→0.0055＝同裁定で幅半分）
private const val COMET_DUST_CURVE = 0.13f        // ダストテイルの湾曲量（尾長比・後方ほど f² で横へ＝軌道遅れの緩いカーブ）
private const val COMET_TAIL_GRAINS = 120         // 尾に散らす微粒数（連続グラデの上に「流れ」の粒状感を足す）
private val CometHeadColor = Color(0xFFCFE8E4)    // わずかに冷たい白緑（彗星コマの色味・低彩度）
private val CometDustColor = Color(0xFFF6EEDC)    // ダストテイル＝日光散乱の白〜淡黄（塵の反射色）
private val CometIonColor = Color(0xFF9FC6FF)     // イオンテイル＝太陽風にイオン化したガスの淡青

// --- 常時=BH 重力レンズ（帯内・チャンク hash 決定的・mid 焼き込み時の座標写像のみ）---
private const val BH_PROB = 0.03f                 // チャンクごとに BH が湧く確率（≈33チャンクに1度＝数十チャンクに1度）
// v7.4 総評「まだ大きい」→ 両案とも 5/12 サイズへ縮小（案A 0.020×5/12・案B 0.033×5/12）＝「小さく・見つかる」存在を徹底。
private const val BH_RADIUS_N = 0.00833f          // 案A のアインシュタイン半径 Re（0.020×5/12）＝直径 ~0.0167＝~1/60 画面
private const val BH_RADIUS_N_B = 0.01375f        // 案B の Re（0.033×5/12）のリング無し漆黒小孔
private const val BH_REACH = 4f                   // レンズ影響半径 = BH_REACH×Re（外は写像ほぼ恒等＝計算を近傍に限定）
private const val BH_RING_BOOST = 1.35f           // 案A: Re 近傍へ落ちた粒の輝度増（細い光の輪郭を締める。案B は boost 無し＝素の穴）

// --- 一瞬=流星（既存 MeteorHost を高負荷用に明るく描く。共有の MeteorCanvas/MeteorTuning は不変）---
// v7.5前 実機裁定「流星の速さも半分に」＝高負荷 ON 時のみ掃過を半分（同じ軌跡を2倍の時間で流す）。造形/軌跡は不変・所要時間だけ2倍。
//   実現: SkyBackdropM が共有スケジューラ rememberMeteorHost へ highLoad 時だけこの倍率を渡す（通常モードは既定 1f＝厳密不変）。
//   debug 手動流星(manualMeteor)も同じ倍率で animateTo＝見比べの速度を一致させる。単一の正本にするため internal で公開。
internal const val HL_METEOR_DURATION_SCALE = 2f  // 高負荷流星の所要時間倍率（2倍＝速度半分）
private const val HL_METEOR_TAIL_ALPHA = 0.72f    // 尾αの実効上限（通常0.42＝可読規律。高負荷は非本文ゆえ存在感を優先）
private const val HL_METEOR_CORE_ALPHA = 0.9f     // 芯の白（加算＝明るい帯の上でも抜ける）
private const val METEOR_NW = 390f                // 流星の名目座標系（MeteorStreak は 390×844 で定義）
private const val METEOR_NH = 844f
// ============================================================

// ============================================================
// ===== v7.5 奥行きの深化（空気遠近法・前面暗黒雲・天の川2層化）の較正 =====
// ①空気遠近法（焼き込み時の描き分け＝毎フレーム負荷ゼロ）。far=青寄り＋ソフト／near=暖色＋シャープ／mid=中間（無改変）。
private val AerialFarTint = Color(0xFF8FB0E8)     // far を寄せる青（遠景の大気散乱＝わずかに青ずむ）
private const val AERIAL_FAR_TINT = 0.12f         // far 粒色を青へ寄せる量（「わずかに」＝単体では気づかない温度勾配）
private const val AERIAL_FAR_SOFT_MUL = 1.7f      // far 粒のソフトエッジ描画半径倍率（輪郭を放射でにじませる幅＝ハードな点にしない）
private val AerialNearTint = Color(0xFFFFE6C4)    // near を寄せる暖色（近景＝わずかに暖かい太陽光色）
private const val AERIAL_NEAR_TINT = 0.10f        // near 粒色を暖色へ寄せる量

// ②前面の暗黒シルエット雲（発光しない黒・SrcOver 遮蔽・near より手前）。チャンク hash 決定的・極稀。サイズは画面高の 1/6〜1/4級。
private const val CLOUD_SALT = 0x0DA12C10L        // 雲のチャンク抽選ストリーム（他 salt と独立＝相関しない）
private const val CLOUD_PROB = 0.20f              // チャンクごとに雲が湧く確率（0.07→0.20＝2026-07-19裁定「5チャンクに1度」へ増）
private const val RATIO_CLOUD = 2.2f              // 視差比（mid 基準・near=1.75 よりさらに手前＝最速で流れる＝遮蔽の運動視差を強める）
private const val CLOUD_RAD_MIN = 0.06f           // 雲の外形半径（画面高比）＝直径 ~0.12（画面の 1/6 級の下端）
private const val CLOUD_RAD_MAX = 0.11f           // 直径 ~0.22（1/4 級の上端）＝これ以上大きくしない
private const val CLOUD_MARGIN = 0.02f            // チャンク端からの余白（中心を rad+margin 内に置き雲が境界で切れない＝隣接収集不要）
private const val CLOUD_CORE_ALPHA = 0.9f         // 中心ローブの遮蔽 α（高め＝背後の星を隠す。重なりで中心はさらに濃く・端はソフト減衰）
private const val CLOUD_BODY_LOBES = 12           // 本体を成す柔い黒ローブ数（重なりで中心が積み上がりほぼ真っ黒に）
private const val CLOUD_WISP_LOBES = 5            // 本体から離れたちぎれ房の数（torn の輪郭）

// ③天の川2層化（mid-back/mid-front＝粒折半・帯ジオメトリ共有）。ドリフト成分は両層同一・往復＋スクロール比差で深度（KDoc v7.5③ 参照）。
private const val MID_BACK_SALT = MID_SALT        // back は元の salt を継ぐ（従来の帯の姿に最も近い奥層）
private const val MID_FRONT_SALT = 0x0031F00FL    // front は別 salt＝独立した粒集合（同じ帯ジオメトリに従う別粒＝ずれると本物の視差になる）
private const val MID_LAYER_COUNT = MID_BAND_COUNT / 2 // 折半（6000+6000＝総密度は不変＝帯輝度を保つ）
private const val RATIO_MID_BACK = 0.96f          // back のスクロール視差比（mid=1.0 基準の僅差）
private const val RATIO_MID_FRONT = 1.04f         // front のスクロール視差比（差 0.08＝スクロール有界ゆえシアーも有界）
private const val MID_SWAY_AMP_PX = 40f           // 手前層の相対位相の往復振幅（±数十px＝見かけの速度差を常在させるが蓄積ゼロ）
private const val MID_SWAY_PERIOD_S = 240f        // 往復周期（4分＝3〜5分の範囲）
// ============================================================

/**
 * 高負荷スカイの1粒（正規化座標＝チャンク内 fx∈[0,1]・fy∈[0,1]／fr=半径/名目幅 NW）。決定的生成。
 * twinkle/phase/periodS/ampl は近景の息づき（時間駆動・α のみ微揺らぎ）用。firstMag は輝星の演出（近景=縮小にじみハロー／遠中景=白芯 pip）対象か。
 */
private class HlStar(
    val fx: Float,
    val fy: Float,
    val fr: Float,
    val alpha: Float,
    val color: Color,
    val halo: Boolean = false,
    val twinkle: Boolean = false,
    val phase: Float = 0f,
    val periodS: Float = 4f,
    val ampl: Float = 0f,
    val firstMag: Boolean = false,
)

// 深度別の粒仕様（far=淡・微小・無彩色寄り／mid=明・やや大・フル彩色＝天の川本体）。
private class GrainSpec(
    val salt: Long,
    val count: Int,
    val tries: Int,
    val aMin: Float,
    val aMax: Float,
    val rMinN: Float,   // 半径（幅に対する割合）
    val rMaxN: Float,
    val cStrBase: Float, // 色の乗り（strength）基準
    val cStrKnot: Float, // knot での色の乗り増分（暖色密集）
    val sink: Boolean,   // true=FaintStar へ寄せて奥へ沈める（far）
    val pipThresh: Float, // この α 以上の粒に白芯 pip（mid の解像＝粒でできた天の川の芯締め。far は 1f で無効）
)
private val FAR_SPEC = GrainSpec(
    salt = FAR_SALT, count = 2200, tries = 2200 * 7,
    aMin = 0.015f, aMax = 0.06f, rMinN = 0.10f / NW, rMaxN = 0.28f / NW,
    cStrBase = 0.35f, cStrKnot = 0.30f, sink = true, pipThresh = 1f,
)
// mid（帯本体）は GrainSpec を使わない＝genBandGrains が v5 の輝度/径/色式を直接持つ（ガウス帯＝一様撒き spec と別アルゴリズム）。

// ===== 色（色相分布は維持・彩度を STAR_SATURATION で締める）=====
/** 色温度 t（0=青白O/B → 1=橙K → 1.25=赤M）を返す（t≤1 は DeepSkyM の token 補間、超過分だけ赤へ延伸）。 */
private fun hlTempColor(t: Float): Color =
    if (t <= 1f) starTempColor(t)
    else lerp(StarTempAmberSeizu, StarTempRedSeizuHl, ((t - 1f) / 0.25f).coerceIn(0f, 1f))

/** 恒星色: 熱 t の色を無彩色地から strength で引き上げ（暗い星ほど無彩色）。strength は STAR_SATURATION で締める（低彩度）。 */
private fun hlStarColor(t: Float, strength: Float): Color =
    lerp(StarNeutralSeizu, hlTempColor(t), (strength * STAR_SATURATION).coerceIn(0f, 1f))

/** 色温度を O/B・A/G・K・M へ決定的に割り当てる（u1=系統抽選・u2=系統内の散らばり）。 */
private fun hlTempPick(u1: Float, u2: Float): Float = when {
    u1 < 0.14f -> 0.02f + u2 * 0.16f   // O/B 青白
    u1 < 0.52f -> 0.36f + u2 * 0.40f   // A/G 白〜淡金
    u1 < 0.82f -> 0.78f + u2 * 0.22f   // K 橙
    else -> 1.0f + u2 * 0.25f          // M 赤
}

/** 息づき周期＝3〜8s の一様分布のみ（速い「またたき」を全廃）。 */
private fun hlTwinklePeriod(u: Float): Float = TWINKLE_PERIOD_MIN + u * (TWINKLE_PERIOD_MAX - TWINKLE_PERIOD_MIN)

// ===== ④ 精度対策つき整数ハッシュ（大 index/大格子でも破綻しない）=====
// splitmix64 系の Long 混合。チャンク seed（決定性の核）・値ノイズ格子の両方で使う。sin ベース hash01 は大引数で精度が落ちるため
// 無限空の格子には使わない（近景の位相など小引数のみ hash01 を流用）。
private fun hashUnit(x: Long, y: Long): Float {
    // 乗数は splitmix64 系の奇数定数（奇数＝全単射的に bit を撹拌。正確な値は非 load-bearing＝分布が良ければよい）。
    var h = x * -0x61c8864680b583ebL
    h = h xor (y * -0x3d4d51c2d82b14b1L)
    h = (h xor (h ushr 32)) * -0x2917014799a2b06dL
    h = h xor (h ushr 32)
    return ((h ushr 40) and 0xFFFFFFL).toFloat() / 16777216f // 上位24bit → [0,1)
}
/** チャンク＋salt から Lcg 用の正の Int seed を導く（well-distributed＝隣接チャンクが相関しない）。 */
private fun hashSeedInt(c: Long, salt: Long): Int {
    var h = (c * -0x61c8864680b583ebL) xor (salt * -0x3d4d51c2d82b14b1L)
    h = (h xor (h ushr 33)) * -0x2917014799a2b06dL
    h = h xor (h ushr 33)
    return (h and 0x7FFFFFFFL).toInt()
}

// ===== ② ワールド連続の帯ジオメトリ（引数 wy=ワールドY・単位=画面。sin を Double で評価＝大 index でも精度維持）=====
// 【v6.1 帯崩壊の是正】v6 は帯を「一様撒き＋確率棄却（base0.30）」で作った＝帯内外の密度比 ~1.7:1 で「帯」に見えず全体に散った星に
//   なった（実機裁定「天の川ではない」）。v5 の帯（drawFarStars）は「軸まわりへガウス配置」で密度比 ~600:1 の濃い一本の帯だった。
//   → 帯本体 mid を v5 のガウス帯生成へ回帰（genBandGrains）。以下の走向/幅/暗黒帯/むら/縞は v5 の bAxis/bHalf/riftCenter/bDens/
//   darkNeb/fila を「ワールド連続（wy=画面単位・y_nominal≈wy*984 と読み替え）」へ写したもの＝チャンク境界で切れない蛇行帯。
// 対角走向: v6 の緩い蛇行（1画面で ~8%移動）では帯が縦棒に見えた＝振幅/周波数を上げ、1画面で ~0.25〜0.35 横断する対角へ（数値検証:
//   6画面で軸 x∈[0.16,0.86] を winding＝常に画面を斜めに横切る）。R1s の「上=右→下=左」の対角性を無限連続で維持。
// v6.2 実機裁定「蛇行が弱い・1.3倍急に」→ 合成曲率（Σ 振幅×周波数＝0.30×2.3+0.12×0.85=0.7920）を ×1.3。振幅は据え置き（x 域を
//   [0.08,0.92] に保ち帯を画面外へ逃がさない＝途切れ回避）・周波数のみ ×1.3 で曲がりを急に。検算: 0.30×2.99+0.12×1.105=1.0296=0.7920×1.3。
private fun bandAxisWorld(wy: Double): Float =
    (0.5 + 0.30 * sin(wy * 2.99 + 0.6) + 0.12 * sin(wy * 1.105 + 2.1)).toFloat()
// 帯半幅（v5 bHalf=52+18sin を /NW=390 正規化＝0.133+0.046sin。y/280→wy*984/280=wy*3.51）。
private fun bandHalfWorld(wy: Double): Float =
    (0.133 + 0.046 * sin(wy * 3.51 + 0.6)).toFloat()
/** ダークレーン（Great Rift）の帯内オフセット（軸からの距離・正規化）。v5 riftCenter=12+11sin(y/95)+4sin(y/47) を /390・y=wy*984。 */
private fun riftOffWorld(wy: Double): Float =
    (0.031 + 0.028 * sin(wy * 10.36) + 0.010 * sin(wy * 20.94 + 1.3)).toFloat()
/** 沿軸の密度勾配（濃淡ムラ）。v5 bDens を wy 連続化（y=wy*984 で周波数を換算）。0..1。 */
private fun bandDensWorld(wy: Double): Float =
    (0.5 + 0.5 * (sin(wy * 20.66) * 0.5 + sin(wy * 46.2 + 1.1) * 0.3 + sin(wy * 8.86 + 2.3) * 0.2)).toFloat()
// sin 引数は全て Double で評価する（大 wy を Float へ落としてから sin すると精度が崩れる＝Double のまま sin し結果だけ Float 化）。
/** 暗黒星雲場（Great Rift への濃淡の重ね）。v5 darkNeb を nx=x/390・wy=y/984 連続化。負域は0クランプ＝高いほど粒を疎化。 */
private fun darkNebWorld(nx: Float, wy: Double): Float {
    val v = sin(nx * 13.65 - wy * 27.55 + 0.4) * 0.6 + sin(nx * 6.63 + wy * 51.17 + 2.2) * 0.4
    return if (v < 0.0) 0f else v.toFloat()
}
/** フィラメント場（縁のほつれ・沿軸の筋＝一様散布に見せない）。v5 fila を nx/wy 連続化。0..1。 */
private fun filaWorld(nx: Float, wy: Double): Float {
    val v = sin(nx * 42.9 + wy * 42.3) * 0.5 +
        sin(nx * 105.3 - wy * 98.4 + 1.7) * 0.32 +
        sin(nx * 23.8 + wy * 167.3 + 3.1) * 0.18
    return (v * 0.5 + 0.5).toFloat()
}

// 分子雲むら＝整数格子の値ノイズ（ワールド連続・大座標で破綻しない）。2オクターブ。面を描かない＝粒の疎密を作る密度場。
private fun valueNoiseWorld(px: Float, pyD: Double): Float {
    val x0 = floor(px); val ix = x0.toLong()
    val y0 = floor(pyD); val iy = y0.toLong()
    val fx = px - x0; val fy = (pyD - y0).toFloat()
    val ux = fx * fx * (3f - 2f * fx); val uy = fy * fy * (3f - 2f * fy)
    val a = hashUnit(ix, iy); val b = hashUnit(ix + 1, iy)
    val c = hashUnit(ix, iy + 1); val d = hashUnit(ix + 1, iy + 1)
    val ab = a + (b - a) * ux; val cd = c + (d - c) * ux
    return ab + (cd - ab) * uy
}
private fun mottleWorld(nx: Float, wy: Double): Float {
    var s = 0f; var amp = 0.6f; var k = MOTTLE_K; var oct = 0
    while (oct < 2) {
        // オクターブごとに座標をオフセット（別格子＝相関を切る）。
        s += amp * valueNoiseWorld(nx * k + oct * 31.7f, wy * k + oct * 17.3)
        k *= 2f; amp *= 0.5f; oct++
    }
    return s.coerceIn(0f, 1f)
}

// ③ 稀な星団状 knot（画面固定でなくワールドに置く＝流れに乗る一期一会の密集）。チャンク hash で決定的に湧く。
private class Knot(val wy: Double, val x: Float, val strength: Float)
private fun knotInChunk(c: Long): Knot? {
    if (hashUnit(c, KNOT_SALT) >= KNOT_PROB) return null
    val ly = hashUnit(c, KNOT_SALT + 7)                 // チャンク内 y
    val wy = c.toDouble() + ly
    val ax = bandAxisWorld(wy)                           // 帯の上に置く
    val xoff = (hashUnit(c, KNOT_SALT + 13) - 0.5f) * 0.10f
    val strength = 0.7f + hashUnit(c, KNOT_SALT + 19) * 0.6f
    return Knot(wy, (ax + xoff).coerceIn(0.05f, 0.95f), strength)
}
/** チャンク c に影響する knot（自身と隣接＝ガウス径が境界を跨ぐぶんを拾う）。 */
private fun collectKnots(c: Long): List<Knot> {
    val out = ArrayList<Knot>(3)
    knotInChunk(c - 1)?.let { out += it }
    knotInChunk(c)?.let { out += it }
    knotInChunk(c + 1)?.let { out += it }
    return out
}
private fun knotInfluence(knots: List<Knot>, nx: Float, wy: Double): Float {
    var s = 0f
    for (k in knots) {
        val dy = (wy - k.wy).toFloat(); val dx = nx - k.x
        s += k.strength * exp(-((dy * dy) / (2f * KNOT_SY * KNOT_SY) + (dx * dx) / (2f * KNOT_SX * KNOT_SX)))
    }
    return s
}

// ===== v7 常時=BH 重力レンズ（帯内・チャンク hash 決定的・mid 焼き込み時の座標写像のみ＝毎フレーム負荷ゼロ）=====
// nx=孔中心の正規化 x／fy=チャンク内 y（[0,1]＝wy-c）。中心 y は [0.2,0.8] に収め、影響半径(≈4Re≈0.13)が隣接チャンクへ漏れない
//   ようにする（＝chunk 内で完結＝knot のような隣接収集が不要）。帯の濃い領域に置く（bandAxisWorld 上＋微小オフセット）。
// variant: 0=案A（Re=BH_RADIUS_N＋明示リング）／1=案B（Re=BH_RADIUS_N_B・リング無しの漆黒小孔）。reN=この BH の Re（写像/リング径の実体）。
private class Bh(val nx: Float, val fy: Float, val reN: Float, val variant: Int)
private fun reForVariant(variant: Int): Float = if (variant == 1) BH_RADIUS_N_B else BH_RADIUS_N
private fun bhInChunk(c: Long): Bh? {
    // v7.1 検分: 強制チャンクは確率無視で必ず BH（帯軸上＝粒があり輪郭が出る）。番兵(MIN_VALUE)＝通常時は常に false＝製品挙動不変。
    // v7.2: 強制時は forcedBhVariant（BH ボタン連打の偶奇で A/B 交互）に従って径/意匠を切替＝連打で見比べ。
    if (c == HlSkyDebug.forcedBhChunk) {
        val wy = c.toDouble() + HlSkyDebug.forcedBhFy
        val v = HlSkyDebug.forcedBhVariant
        return Bh(bandAxisWorld(wy), HlSkyDebug.forcedBhFy, reForVariant(v), v)
    }
    if (hashUnit(c, BH_SALT) >= BH_PROB) return null
    val fy = 0.2f + hashUnit(c, BH_SALT + 7) * 0.6f      // 中心 y ∈ [0.2,0.8]（影響が chunk 内に収まる）
    val wy = c.toDouble() + fy
    val nx = (bandAxisWorld(wy) + (hashUnit(c, BH_SALT + 13) - 0.5f) * 0.12f).coerceIn(0.12f, 0.88f)
    // 野良（非強制）BH は案A で焼く＝比較の基準は debug ボタンが担い、平常の空は「現径＋明示リング」の1意匠に固定（意図しない小孔の乱立を避ける）。
    return Bh(nx, fy, BH_RADIUS_N, 0)
}
/**
 * 点質量レンズの放射写像＝像半径 θ=½(β+√(β²+4Re²))（β=源の中心距離・h 単位）。θ≥Re ゆえ Re 内は必ず空（黒い孔）・β→0 で θ→Re
 * ＝内側の粒が Re へ集積（アインシュタインリング）。写像は源座標の全性質（輝度/帯所属）確定後、描画位置(fx,fy)にだけ掛ける
 * （＝重力が「見かけの位置」だけ曲げる物理に対応）。アスペクトで x を h 基準へ正規化＝孔はピクセル上で真円。
 * 返り値: 像座標(fx,fy)と ring 近傍フラグ（縁の集光を締めるための輝度増対象）。中心の粒(β≈0)は方向不定ゆえ null＝落とす。
 */
private class Lensed(val fx: Float, val fy: Float, val ring: Boolean)
private fun bhLens(bh: Bh, nx: Float, fy: Float, aspect: Float): Lensed? {
    val re = bh.reN                                                  // v7.2: 案ごとの Re（A=0.033／B=0.017）で写像する
    val ux = (nx - bh.nx) * aspect
    val uy = fy - bh.fy
    val beta = sqrt(ux * ux + uy * uy)
    if (beta >= BH_REACH * re) return Lensed(nx, fy, false)          // 影響圏外＝恒等（写像コスト回避）
    if (beta < 1e-4f) return null                                    // 中心の粒は方向不定＝落とす（NaN 回避）
    val theta = 0.5f * (beta + sqrt(beta * beta + 4f * re * re))
    val scale = theta / beta                                         // 半径方向の伸長（アスペクトは相殺＝生の Δ にそのまま掛かる）
    val ring = bh.variant == 0 && theta < 1.3f * re                  // 案A のみ Re 近傍を集光（案B は素の穴＝boost 無し）
    return Lensed(bh.nx + (nx - bh.nx) * scale, bh.fy + (fy - bh.fy) * scale, ring)
}

// v7.2 焼き時に mid bitmap へ描く BH 装飾（案A の明示アインシュタインリング）。cx,cy=チャンク内正規化中心／reN=Re（画面高比）。
// レンズ写像で孔はピクセル真円（半径 reN×fh）ゆえリングも同径の円で縁に重なる。加算合成の高負荷レイヤは z0（夜天地色）へ Plus で
// 重ねる＝レイヤ単独では地色より暗くできない（＝孔内部の「真の黒」は加算のみでは不可＝設計判断は報告に明記）。よって案A の迫力/一目性は
// 「明るく細いリング × 地色暗の孔」の明暗コントラストで作る（リングは加算で光る）。案B はリングを描かず小さな暗い孔のみ。
private class BhDeco(val cx: Float, val cy: Float, val reN: Float, val variant: Int)
private fun bhDecoInChunk(c: Long): BhDeco? {
    val bh = bhInChunk(c) ?: return null
    return BhDeco(bh.nx, bh.fy, bh.reN, bh.variant)
}

// ===== v7.5 ②前面の暗黒シルエット雲（発光しない黒・SrcOver 遮蔽・チャンク hash 決定的・極稀）=====
// 加算合成の星層の上に「黒で描いて背後を隠す」＝遮蔽。手前の雲が星を隠して通り過ぎる立体手がかり（実際の暗黒星雲と同じ機序）。
// 形は複数の柔い黒ローブ（重なりで中心が濃く＝ほぼ真っ黒）＋離れたちぎれ房で fbm 的 torn を近似。単位: fx=チャンク内正規化 x（幅）・
//   fy=チャンク内正規化 y（[0,1]＝wy-c）・rad=画面高比の半径。ローブは genCloud 時に aspect でピクセル真円へ整形して置く。
private class CloudLobe(val fx: Float, val fy: Float, val rad: Float, val alpha: Float)
private fun genCloud(c: Long, aspect: Float): List<CloudLobe> {
    // v7.5.1 検分: forcedCloudChunk はこのチャンクを確率無視で必ず雲にする（release では番兵ゆえ常に false＝本物の CLOUD_PROB は非破壊）。
    val forced = c == HlSkyDebug.forcedCloudChunk
    if (!forced && hashUnit(c, CLOUD_SALT) >= CLOUD_PROB) return emptyList() // 極稀＝ほとんどのチャンクは雲なし（空リスト＝描画も走らない＝無コスト）
    val rng = Lcg(hashSeedInt(c, CLOUD_SALT))
    val rad = CLOUD_RAD_MIN + rng.next() * (CLOUD_RAD_MAX - CLOUD_RAD_MIN)   // 雲の外形半径（画面高比）
    // 中心はチャンク内に収める（rad+margin の余白＝雲が境界で切れない＝隣接チャンク収集が不要）。強制時は可視中央（cx=0.5・cy=forcedCloudFy）。
    //   非強制の rng 消費順（rad→cy→cx）は不変＝本物の雲の決定性を保つ（強制だけが cy/cx を rng でなく中央へ差し替える）。
    val cy: Float; val cx: Float
    if (forced) {
        cx = 0.5f
        cy = HlSkyDebug.forcedCloudFy.coerceIn(rad + CLOUD_MARGIN, 1f - (rad + CLOUD_MARGIN))
    } else {
        cy = (rad + CLOUD_MARGIN) + rng.next() * (1f - 2f * (rad + CLOUD_MARGIN))
        cx = 0.15f + rng.next() * 0.70f
    }
    val out = ArrayList<CloudLobe>(CLOUD_BODY_LOBES + CLOUD_WISP_LOBES)
    // ローブの水平オフセットは height 基準で生成し /aspect で幅比へ換算＝ピクセルで円形（縦横比で潰れない）。aspect=w/h。
    fun place(offHx: Float, offHy: Float, lr: Float, a: Float) { out += CloudLobe(cx + offHx / aspect, cy + offHy, lr, a) }
    // 本体＝中心近傍に重なる柔いローブ（重なりで中心の遮蔽が積み上がり真っ黒に近づく・端は各ローブのソフト減衰で薄れる）。
    repeat(CLOUD_BODY_LOBES) {
        place((rng.next() - 0.5f) * 1.2f * rad, (rng.next() - 0.5f) * 1.2f * rad, rad * (0.4f + rng.next() * 0.6f), CLOUD_CORE_ALPHA * (0.6f + rng.next() * 0.4f))
    }
    // ちぎれ房＝本体から離れた小さめのローブ（torn の輪郭＝雲がちぎれて散る見え）。
    repeat(CLOUD_WISP_LOBES) {
        val ang = rng.next() * TWO_PI
        val dist = rad * (1.0f + rng.next() * 0.9f)
        place(cos(ang) * dist, sin(ang) * dist, rad * (0.18f + rng.next() * 0.24f), CLOUD_CORE_ALPHA * (0.35f + rng.next() * 0.3f))
    }
    return out
}

// ===== 遠景 far の粒生成（一様撒き＋確率棄却＝淡い全天ダスト＋帯寄せ。チャンク index を seed に決定的生成）=====
private fun genGrains(c: Long, spec: GrainSpec): List<HlStar> {
    val rng = Lcg(hashSeedInt(c, spec.salt))
    val knots = collectKnots(c)
    val out = ArrayList<HlStar>(spec.count)
    var placed = 0; var tries = 0
    while (placed < spec.count && tries < spec.tries) {
        tries++
        val nx = rng.next()
        val ly = rng.next()
        val wy = c.toDouble() + ly
        val axis = bandAxisWorld(wy)
        val half = bandHalfWorld(wy)
        val off = nx - axis
        val dband = abs(off) / half
        val bandMem = exp(-(dband * dband) / (2f * 0.75f * 0.75f)) // 帯メンバーシップ（軸で1・半幅で減衰）
        val mottle = mottleWorld(nx, wy)
        val dr = (off - riftOffWorld(wy)) / (half * 0.28f)         // 暗黒帯の相対距離（帯幅の ~28% が暗黒帯の太さ）
        val rift = exp(-dr * dr) * bandMem                         // 暗黒帯は帯内のみ
        val knot = knotInfluence(knots, nx, wy)
        // 置く確率＝base（全天）＋帯×むら＋knot、を暗黒帯で欠乏（v5 の彫りをそのまま流用）。
        var pKeep = FAR_BASE_PROB + FAR_BAND_PROB * bandMem * (0.35f + 0.65f * mottle) + KNOT_DENSITY * knot.coerceAtMost(2f)
        pKeep *= (1f - FAR_RIFT_DEPLETE * rift)
        if (rng.next() > pKeep) continue
        val r = spec.rMinN + rng.next() * (spec.rMaxN - spec.rMinN)
        // v6.2: knot 中心の far 粒も明るく（0.6→0.8＝密集域が far 層でも発光を増す＝「星の塊」の知覚を後押し）。
        val lift = ((0.55f + 0.45f * bandMem * (0.5f + 0.5f * mottle) + 0.8f * knot.coerceAtMost(1.5f)) * (1f - 0.6f * rift))
            .coerceIn(0.25f, 1.8f)
        val a = ((spec.aMin + rng.next() * (spec.aMax - spec.aMin)) * lift).coerceAtMost(spec.aMax * 1.7f)
        // 色: knot（密集域）は暖色端（老いた星の集積）へ・その他は色温度分布。far は FaintStar へ寄せて沈める。
        val warmK = knot.coerceAtMost(1f)
        val t = if (warmK > 0.4f) 0.85f + rng.next() * 0.35f else hlTempPick(rng.next(), rng.next())
        val col0 = hlStarColor(t, spec.cStrBase + spec.cStrKnot * warmK)
        var col = if (spec.sink) lerp(FaintStarSeizu, col0, 0.5f) else col0
        // v7.5 空気遠近法: far（sink=true）は大気散乱で「わずかに青寄り」＝遠景の温度勾配（単体では気づかない量）。
        if (spec.sink) col = lerp(col, AerialFarTint, AERIAL_FAR_TINT)
        out += HlStar(nx, ly, r, a, col, firstMag = a >= spec.pipThresh)
        placed++
    }
    return out
}

// ===== 中景 mid＝天の川本体の粒生成（v5 drawFarStars の帯を「ワールド連続のガウス帯」へ回帰）=====
// 【v6.1 帯崩壊の是正の核】v5 と同じく「軸まわりへガウス配置」＝粒は帯の中に集中する（一様撒き＋棄却では帯にならない）。
//   数値自己検証: 軸±半幅に ~95% が入り、帯内/帯外の粒密度比 ~600:1（一様撒き版は ~1.7:1＝散った星）。彫り（Great Rift 0.93・
//   darkNeb・fila・沿軸密度勾配）と輝度/径式（a=0.07+0.40*bright・rad=0.20+mag*0.62）は v5 の値をそのまま。knot（③）で密集/暖色。
// v7: このチャンクに BH（重力レンズ）が湧いていれば、確定した粒の描画位置(fx,fy)へ bhLens を掛ける（aspect=w/h で孔をピクセル真円に）。
// v7.5: salt/count を引数化＝天の川2層（back=MID_BACK_SALT・front=MID_FRONT_SALT、各 MID_LAYER_COUNT で折半）。帯ジオメトリ関数は
//   両層で共有＝同じ1本の帯に沿う別粒集合。BH レンズ写像は両層で読み・掛ける（孔が両層一致）＝明示リング deco だけ back のみ焼く。
private fun genBandGrains(c: Long, aspect: Float, salt: Long, count: Int): List<HlStar> {
    val rng = Lcg(hashSeedInt(c, salt))
    val knots = collectKnots(c)
    val bh = bhInChunk(c)                                // v7 このチャンクの BH（無ければ null＝写像なし）
    val out = ArrayList<HlStar>(count)
    val tryLimit = count * 8                             // 撒き試行上限（疎化棄却ぶんの余裕＝粒数に比例＝×8 を維持）
    // 帯粒の垂直分布＝軸まわりのガウス（Box–Muller）。σ=半幅*0.5＝v5 と同じ「芯へ密・裾でほつれ」。
    fun gauss(): Float {
        val u = rng.next().let { if (it <= 0f) 1e-6f else it }
        return sqrt(-2f * ln(u)) * cos(TWO_PI * rng.next())
    }
    var placed = 0; var tries = 0
    while (placed < count && tries < tryLimit) {
        tries++
        val ly = rng.next()
        val wy = c.toDouble() + ly
        val dens = bandDensWorld(wy)
        // knot（密集域）は帯を局所的に濃くする＝沿軸密度の keep 確率を持ち上げる（軸上で評価＝帯粒は軸近傍ゆえ近似十分）。
        val knotAxis = knotInfluence(knots, bandAxisWorld(wy), wy)
        if (rng.next() > (0.30f + 0.64f * dens + KNOT_DENSITY * knotAxis).coerceAtMost(1f)) continue // 沿軸密度勾配＋knot
        val half = bandHalfWorld(wy)
        val off = gauss() * half * 0.5f                      // ★軸まわりガウス配置＝帯の集中（v5 の要）
        val nx = bandAxisWorld(wy) + off
        if (nx < -0.02f || nx > 1.02f) continue
        val d = (abs(off) / half).coerceAtMost(1f)           // 0核..1縁
        val dr = (off - riftOffWorld(wy)) / MID_RIFT_W
        val rift = exp(-dr * dr)
        if (rng.next() < rift * 0.93f) continue              // Great Rift を深く彫る（最大93%間引く）
        if (rng.next() < darkNebWorld(nx, wy) * 0.5f) continue // 暗黒星雲の重ね
        val fv = filaWorld(nx, wy)
        if (rng.next() < d * (1f - fv) * 0.7f) continue      // 縁のほつれ（縁かつ谷ほど間引く）
        val mag = rng.next().pow(2.8f)                       // べき分布＝輝星を絞る（v5 BAND_MAG_EXP）
        val bright = ((1f - d * 0.82f) * (0.26f + 0.74f * mag) * (0.5f + 0.5f * dens) * (0.78f + 0.22f * fv)).coerceIn(0f, 1f)
        // v6.2「薄すぎる」是正: 径の分布を一段上げ（0.20→0.24 基底・0.62→0.72 幅＝粒あたり被覆 ~1.3倍。粒径ゆえ面のベタ塗りにはならない）。
        val rad = (0.24f + mag * 0.72f) / NW
        val knotV = knotInfluence(knots, nx, wy).coerceAtMost(1.5f)
        // v6.2「薄すぎる」是正: 輝度床を一段上げ（0.07→0.12＝暗星多数を底上げ）。暗黒レーンは粒の棄却（上の rift/darkNeb）で彫るため
        //   床を上げても空にならず＝明るい帯 vs 空の rift の相対比はむしろ強まる（0.42 キャップは面輝度式由来ゆえ据え置き）。knot 中心は明るく（0.08→0.16）。
        var a = (0.12f + 0.40f * bright + 0.16f * knotV).coerceAtMost(0.42f)
        // 色: v5 は冷寄り基調＋核で暖。無限空では固定核を廃し knot（密集域）の暖色寄せで代替。v6.2 で knot の暖色を強化（0.45→0.60＝塊が赤M寄り）。
        val spread = (rng.next() - 0.5f) * 0.85f
        val t = (0.20f + spread + 0.60f * knotV).coerceIn(0f, 1.25f)
        val strength = ((bright - 0.30f) / 0.42f).coerceIn(0f, 1f) // 輝度上位ほど色が乗る／暗星は無彩色
        val halo = bright > MID_PIP_BRIGHT && rng.next() < 0.45f    // 輝星の一部に分離ハロー（v5）
        // v7 BH: 見かけの描画位置だけレンズ写像（源の輝度/色は不変＝物理どおり）。Re 近傍へ来た粒は少し明るく＝細い光の輪郭。
        var fx = nx; var fyDraw = ly
        if (bh != null) {
            val lp = bhLens(bh, nx, ly, aspect) ?: continue // 中心の粒は落とす（黒い孔を空ける）
            fx = lp.fx; fyDraw = lp.fy
            if (lp.ring) a = (a * BH_RING_BOOST).coerceAtMost(0.42f)
        }
        out += HlStar(fx, fyDraw, rad, a, hlStarColor(t, strength), halo = halo, firstMag = halo)
        placed++
    }
    return out
}

// ===== 近景生成（チャンクごと・少数・ライブ描画用の星リスト）=====
private fun genNear(c: Long): List<HlStar> {
    val rng = Lcg(hashSeedInt(c, NEAR_SALT))
    val out = ArrayList<HlStar>(NEAR_PER_CHUNK + CLUSTER_STARS)
    fun addNear(nx: Float, ly: Float, mag: Float, tempT: Float) {
        // v7.5 空気遠近法: near は「わずかに暖色」＝近景の温度勾配（far 青／mid 中間との対比。単体では気づかない量）。
        val col = lerp(hlStarColor(tempT, (0.4f + 0.6f * mag).coerceIn(0f, 1f)), AerialNearTint, AERIAL_NEAR_TINT) // 明るい星ほど色が乗る
        val amp = TWINKLE_AMP_MIN + rng.next() * (TWINKLE_AMP_MAX - TWINKLE_AMP_MIN)
        val per = hlTwinklePeriod(rng.next())
        val r = (0.6f + mag * 1.6f) / NW                                  // 大粒
        val a = 0.18f + mag * 0.34f                                       // 明（離散点＝0.42 非対象）
        out += HlStar(
            nx, ly, r, a, col,
            halo = mag > 0.62f,
            twinkle = mag > TWINKLE_MAG_MIN,
            phase = hash01(nx, ly),                                       // 位相を位置ハッシュで散らす（小引数ゆえ sin hash で可）
            periodS = per, ampl = amp,
            firstMag = mag >= FIRSTMAG_THRESH,
        )
    }
    repeat(NEAR_PER_CHUNK) {
        val mag = rng.next().pow(1.6f)                                    // べき分布＝少数だけ明るい
        addNear(rng.next(), rng.next(), mag, hlTempPick(rng.next(), rng.next()))
    }
    // 星団（プレアデス様）＝チャンクごと稀に。中心のまわりへガウス散布・青白寄り・中輝度。
    if (hashUnit(c, CLUSTER_SALT) < CLUSTER_PROB) {
        val cx = 0.15f + rng.next() * 0.7f
        val cy = 0.12f + rng.next() * 0.7f
        fun gauss(): Float {
            val u = rng.next().let { if (it <= 0f) 1e-6f else it }
            return sqrt(-2f * ln(u)) * cos(TWO_PI * rng.next())
        }
        repeat(CLUSTER_STARS) {
            val nx = (cx + gauss() * 0.022f).coerceIn(0f, 1f)
            val ly = (cy + gauss() * 0.022f).coerceIn(0f, 1f)
            val mag = 0.4f + rng.next() * 0.35f
            addNear(nx, ly, mag, 0.05f + rng.next() * 0.14f)             // 青白（O/B 寄り＝散開星団の若い星）
        }
    }
    return out
}

// ===== チャンク焼き（粒 → bitmap。生成 genGrains と焼き bakeGrainChunk は両方 Default スレッドで実行可）=====
private fun bakeGrainChunk(grains: List<HlStar>, w: Int, h: Int, density: Density, ld: LayoutDirection, bh: BhDeco? = null, softEdge: Boolean = false): ImageBitmap {
    val image = ImageBitmap(w, h)
    CanvasDrawScope().draw(density, ld, Canvas(image), Size(w.toFloat(), h.toFloat())) {
        val fw = size.width; val fh = size.height
        for (s in grains) {
            val c = Offset(s.fx * fw, s.fy * fh)
            if (s.halo) {
                // 輝星の分離ハロー＝中心を凹ませた淡い環（コアと離れて読める＝にじみでなく深み。v5 drawFarStars の halo）。
                val rr = 3.0f / NW * fw
                drawCircle(
                    Brush.radialGradient(
                        0f to s.color.copy(alpha = 0f),
                        0.45f to s.color.copy(alpha = 0.16f),
                        1f to Color.Transparent, center = c, radius = rr,
                    ), radius = rr, center = c,
                )
            }
            if (softEdge) {
                // v7.5 空気遠近法: far 粒はソフトエッジ（中心→透明の放射で輪郭をにじませる＝遠景が大気でぼやける見え）。半径を広げ淡い縁。
                val rr = s.fr * fw * AERIAL_FAR_SOFT_MUL
                drawCircle(
                    Brush.radialGradient(
                        0f to s.color.copy(alpha = s.alpha),
                        0.5f to s.color.copy(alpha = s.alpha * 0.6f),
                        1f to Color.Transparent, center = c, radius = rr,
                    ),
                    radius = rr, center = c,
                )
            } else {
                drawCircle(s.color.copy(alpha = s.alpha), radius = s.fr * fw, center = c) // 鋭いコア（にじませない＝解像の主役）
            }
            // 輝星は白芯 pip で締める（解像＝粒でできた天の川の芯。離散点ゆえ 0.42 でキャップ＝キラキラ抑止）。
            if (s.firstMag) drawCircle(StarCoreSeizu.copy(alpha = (s.alpha * 0.7f).coerceAtMost(0.42f)), radius = s.fr * fw * 0.42f, center = c)
        }
        // v7.2 案A: 孔の縁（半径 reN×fh のピクセル真円）へ薄く鋭いアインシュタインリングを明示描画（加算で光る）。案B は描かない。
        if (bh != null && bh.variant == 0) {
            val center = Offset(bh.cx * fw, bh.cy * fh)
            val rPx = bh.reN * fh
            // v7.3「目立ちすぎ」→ フォトンリングを小さめ・淡く（半径 1.4→1.25・輝度 0.16→0.09・帯を細く）。
            val glowR = rPx * 1.25f
            drawCircle(
                Brush.radialGradient(
                    0.72f to Color.Transparent, 0.88f to Color(0xFFCFE0FF).copy(alpha = 0.09f), 1f to Color.Transparent,
                    center = center, radius = glowR,
                ),
                radius = glowR, center = center,
            )
            // v7.4「両案5/12へ縮小」→ リング径が小さくなったため幅を画面相対でなく rPx 相対へ（小孔でも太らず細さを保つ）。
            //   幅 = rPx×0.14（下限0.8px＝最小画面でも消えない）。輝度 0.6 は据え置き（薄く鋭い）。
            drawCircle(
                Color(0xFFFFF4E0).copy(alpha = 0.6f), radius = rPx, center = center,
                style = Stroke(width = (rPx * 0.14f).coerceAtLeast(0.8f)),
            )
        }
    }
    return image
}

/** ディザ用ノイズタイル（±~2/255 の明暗ドット・固定 seed）。全面へ敷き詰めて面のバンディングを崩す。サイズ非依存＝1回生成。 */
private fun buildDitherTile(density: Density, ld: LayoutDirection): ImageBitmap {
    val n = DITHER_TILE.toInt()
    val image = ImageBitmap(n, n)
    val rnd = Lcg(555117)
    CanvasDrawScope().draw(density, ld, Canvas(image), Size(DITHER_TILE, DITHER_TILE)) {
        for (yy in 0 until n) {
            for (xx in 0 until n) {
                val col = if (rnd.next() < 0.5f) Color.White else Color.Black
                val a = rnd.next() * DITHER_AMP
                drawRect(col.copy(alpha = a), topLeft = Offset(xx.toFloat(), yy.toFloat()), size = Size(1f, 1f))
            }
        }
    }
    return image
}

/** 静止の基層（夜天3層グラデ＋ディザ）を1枚焼く。画面固定の地色＝スクロールしない（無限に流れるのは星のチャンク）。 */
private fun bakeDeep(size: IntSize, density: Density, ld: LayoutDirection): ImageBitmap {
    val w = size.width; val h = size.height
    val image = ImageBitmap(w, h)
    val noise = buildDitherTile(density, ld)
    CanvasDrawScope().draw(density, ld, Canvas(image), Size(w.toFloat(), h.toFloat())) {
        drawNightSky() // 夜天3層（画面固定の大気グラデ＝通常モード共有・不改変）
        // v5 の深空星雲 drawDeepSky（画面固定の局所 radial）は無限空では「星が流れるのに雲だけ固定」の違和感になるため外す
        //   ＝色は粒の色温度と knot（暖/冷の密集）で作る。ディザは面のバンディング対策＝全面タイリング。
        val fw = size.width; val fh = size.height
        var y = 0f
        while (y < fh) {
            var x = 0f
            while (x < fw) { drawImage(noise, topLeft = Offset(x, y)); x += DITHER_TILE }
            y += DITHER_TILE
        }
    }
    return image
}

// ===== チャンク・キャッシュ＋ドリフト時計の保持（コンポジション横断で生存＝remember 1回）=====
private class HlSky {
    val far = mutableStateMapOf<Long, ImageBitmap>()      // 焼いた遠景チャンク（idx→bitmap）
    val midBack = mutableStateMapOf<Long, ImageBitmap>()  // v7.5 天の川2層化: 奥層（元 mid＝BH リング deco はこちらに焼く）
    val midFront = mutableStateMapOf<Long, ImageBitmap>() // v7.5 手前層（別 salt の粒・往復＋スクロール比差で back とわずかにずれる＝立体）
    val near = mutableStateMapOf<Long, List<HlStar>>()    // 近景はライブ描画＝星リストを保持（bitmap 不要）
    val cloud = mutableStateMapOf<Long, List<CloudLobe>>() // v7.5 前面の暗黒雲（空リスト=雲なしチャンク・SrcOver 黒で遮蔽）
    val driftPx = mutableStateOf(0.0)                     // ④ 累積ドリフト px（Double＝大時刻でも精度維持）
    val breatheS = mutableFloatStateOf(0f)               // 息づき時計（秒・cosmetic。v7.5 往復変調の位相も兼ねる）
    /** サイズ変更時に焼き直しが要る＝チャンク群を破棄（drift/breathe は据え置き＝カメラ位置は継続）。 */
    fun clearChunks() { far.clear(); midBack.clear(); midFront.clear(); near.clear(); cloud.clear() }
}

/** カメラのワールド px（＝この深度が今見ている位置）。scroll（wrap なし累積）＋drift を ratio 倍（視差＋ドリフトの立体比）。 */
private fun cameraPx(ratio: Float, scrollWorldPx: Float, driftPx: Double): Double =
    ratio * (scrollWorldPx.toDouble() + driftPx)

/** 焼いた深度チャンクを可視ぶん blit（毎フレーム blit のみ＝生成しない）。chunk0 と chunk0+1 の2枚で画面 [0,h] を被覆。 */
private fun DrawScope.blitDepth(cache: SnapshotStateMap<Long, ImageBitmap>, ratio: Float, scrollWorldPx: Float, driftPx: Double) {
    val h = size.height
    if (h <= 0f) return
    val camPx = cameraPx(ratio, scrollWorldPx, driftPx)
    val chunk0 = floor(camPx / h).toLong()
    var idx = chunk0
    while (idx <= chunk0 + 1) {
        cache[idx]?.let { bmp ->
            val y = (idx.toDouble() * h - camPx).toFloat() // Double 演算後 toFloat＝大 index でも位置精度維持
            drawImage(bmp, topLeft = Offset(0f, y), blendMode = BlendMode.Plus)
        }
        idx++
    }
}

/** v7.5 手前層の相対位相＝有界の sin 往復（net-zero・蓄積ゼロだが d/dt≠0 で見かけの速度差が常在）。timeS=breathe 時計（reduceMotion で凍る＝往復停止）。 */
private fun midSwayPx(timeS: Float): Double = (MID_SWAY_AMP_PX * sin(TWO_PI * timeS / MID_SWAY_PERIOD_S)).toDouble()

/**
 * v7.5 天の川2層の blit（加算＝順序不問）。drift 成分は両層とも RATIO_MID 固定＝無界の drift でも2層が離れず帯がほどけない。
 * 深度差は scrollRatio の僅差（back 0.96／front 1.04）と front のみの swayPx（有界往復）で作る＝いずれも有界ゆえシアー有界。
 */
private fun DrawScope.blitMid(cache: SnapshotStateMap<Long, ImageBitmap>, scrollRatio: Float, scrollWorldPx: Float, driftPx: Double, swayPx: Double) {
    val h = size.height
    if (h <= 0f) return
    val camPx = scrollRatio * scrollWorldPx.toDouble() + RATIO_MID * driftPx + swayPx
    val chunk0 = floor(camPx / h).toLong()
    var idx = chunk0
    while (idx <= chunk0 + 1) {
        cache[idx]?.let { bmp ->
            val y = (idx.toDouble() * h - camPx).toFloat()
            drawImage(bmp, topLeft = Offset(0f, y), blendMode = BlendMode.Plus)
        }
        idx++
    }
}

/** 近景チャンクをライブ描画（息づきの α を毎フレーム動かす＝bitmap 化しない。星数が少なく安価）。 */
private fun DrawScope.drawNearChunks(cache: SnapshotStateMap<Long, List<HlStar>>, scrollWorldPx: Float, driftPx: Double, timeS: Float) {
    val h = size.height
    if (h <= 0f) return
    val camPx = cameraPx(RATIO_NEAR, scrollWorldPx, driftPx)
    val chunk0 = floor(camPx / h).toLong()
    var idx = chunk0
    while (idx <= chunk0 + 1) {
        cache[idx]?.let { stars ->
            val yOff = (idx.toDouble() * h - camPx).toFloat()
            drawNearStars(stars, size.width, h, yOff, timeS)
        }
        idx++
    }
}

/**
 * v7.5 前面の暗黒雲をライブ描画（発光しない黒・SrcOver＝加算の星層を「暗く」遮蔽する。雲チャンクは極稀ゆえ大半は空リスト＝無描画）。
 * 各ローブは中心 α→端 透明の放射でソフト減衰＝重なりで中心が積み上がりほぼ真っ黒に・輪郭は柔らかい。RATIO_CLOUD で最速に流れる。
 */
private fun DrawScope.drawCloudChunks(cache: SnapshotStateMap<Long, List<CloudLobe>>, scrollWorldPx: Float, driftPx: Double) {
    val w = size.width; val h = size.height
    if (h <= 0f) return
    val camPx = cameraPx(RATIO_CLOUD, scrollWorldPx, driftPx)
    val chunk0 = floor(camPx / h).toLong()
    var idx = chunk0
    while (idx <= chunk0 + 1) {
        cache[idx]?.takeIf { it.isNotEmpty() }?.let { lobes ->
            val yOff = (idx.toDouble() * h - camPx).toFloat()
            for (l in lobes) {
                val center = Offset(l.fx * w, l.fy * h + yOff)
                val rr = l.rad * h
                // 発光しない黒＝SrcOver（既定）で背後の星を遮蔽（加算では暗くできない＝ここだけ非加算）。中心 α→端 透明の放射でソフト減衰。
                drawCircle(
                    Brush.radialGradient(
                        0f to Color.Black.copy(alpha = l.alpha),
                        0.6f to Color.Black.copy(alpha = l.alpha * 0.55f),
                        1f to Color.Transparent, center = center, radius = rr,
                    ),
                    radius = rr, center = center,
                )
            }
        }
        idx++
    }
}

/** 焼き窓 [chunk0-BEHIND, chunk0+AHEAD] の不足チャンクを背景スレッドで焼き、保持窓外を破棄する（境界跨ぎ時のみ動く）。 */
private fun ensureDepth(
    scope: CoroutineScope,
    cache: SnapshotStateMap<Long, ImageBitmap>,
    baking: MutableSet<Long>,
    gen: (Long) -> List<HlStar>, // 深度別の粒生成（far=一様撒き genGrains／mid=ガウス帯 genBandGrains）
    chunk0: Long,
    size: IntSize,
    density: Density,
    ld: LayoutDirection,
    bhDeco: (Long) -> BhDeco? = { null }, // v7.2: mid のみ BH 装飾を焼き込む（far は null＝BH は帯本体にのみ）
    softEdge: Boolean = false,            // v7.5: far のみ true＝ソフトエッジ焼き（空気遠近法。mid は false＝解像を締めたまま）
) {
    var idx = chunk0 - CHUNK_BEHIND
    while (idx <= chunk0 + CHUNK_AHEAD) {
        val i = idx
        if (!cache.containsKey(i) && baking.add(i)) {
            // 生成＋焼きは Default（重い部分を UI スレッドから外す＝先読み）。完了時に Main で cache へ載せる。
            scope.launch(Dispatchers.Default) {
                val grains = gen(i)
                val bmp = bakeGrainChunk(grains, size.width, size.height, density, ld, bhDeco(i), softEdge)
                withContext(Dispatchers.Main) { cache[i] = bmp; baking.remove(i) }
            }
        }
        idx++
    }
    val lo = chunk0 - CHUNK_BEHIND - CHUNK_EVICT_MARGIN
    val hi = chunk0 + CHUNK_AHEAD + CHUNK_EVICT_MARGIN
    cache.keys.filter { it < lo || it > hi }.forEach { cache.remove(it) } // 保持窓外を破棄（メモリ＝リング相当）
}

/** 近景リングの補充＋破棄（生成が軽いため Main 同期で足りる＝bitmap 焼きなし）。 */
private fun ensureNear(cache: SnapshotStateMap<Long, List<HlStar>>, chunk0: Long) {
    var idx = chunk0 - CHUNK_BEHIND
    while (idx <= chunk0 + CHUNK_AHEAD) {
        if (!cache.containsKey(idx)) cache[idx] = genNear(idx)
        idx++
    }
    val lo = chunk0 - CHUNK_BEHIND - CHUNK_EVICT_MARGIN
    val hi = chunk0 + CHUNK_AHEAD + CHUNK_EVICT_MARGIN
    cache.keys.filter { it < lo || it > hi }.forEach { cache.remove(it) }
}

/** v7.5 雲リングの補充＋破棄（genCloud は極軽＝Main 同期で足りる。大半は空リスト＝雲なし）。aspect はローブをピクセル真円へ整形するため。 */
private fun ensureCloud(cache: SnapshotStateMap<Long, List<CloudLobe>>, chunk0: Long, aspect: Float) {
    var idx = chunk0 - CHUNK_BEHIND
    while (idx <= chunk0 + CHUNK_AHEAD) {
        if (!cache.containsKey(idx)) cache[idx] = genCloud(idx, aspect)
        idx++
    }
    val lo = chunk0 - CHUNK_BEHIND - CHUNK_EVICT_MARGIN
    val hi = chunk0 + CHUNK_AHEAD + CHUNK_EVICT_MARGIN
    cache.keys.filter { it < lo || it > hi }.forEach { cache.remove(it) }
}

/** 各深度の現在の先頭チャンク index（境界跨ぎ検出＝この値が変わったときだけ焼き直す）。v7.5: mid は2層＝back/front を別々に検出。 */
private data class ChunkWindow(val far0: Long, val midBack0: Long, val midFront0: Long, val near0: Long, val cloud0: Long)

// ===== v7 数分=人工衛星（時間イベント・seed 不要・画面空間の直線横断。流星と同じ「実時間抽選」の流儀）=====
// event=1回の横断の幾何（角度・弦オフセット・輝度）／progress=横断の進行 0→1（tween で 30〜60s かけて線形）。draw フェーズ遅延読み。
@Stable
private class SatelliteHost {
    val progress = Animatable(0f)
    var event by mutableStateOf<SatelliteEvent?>(null)
}
// angle=横断方向（rad・ピクセル空間で真っ直ぐ）／chord=中心からの垂直オフセット比（弦をずらし毎回違う道筋）／bright=平常時の芯の明るさ。
// v7.2 flarePeak=横断中にイリジウムフレアが頂点を迎える progress（[0.35,0.65]＝画面内で1度だけ増減光）。
private class SatelliteEvent(val angle: Float, val chord: Float, val bright: Float, val flarePeak: Float)

/** 次の衛星までの待機（指数分布 inter-arrival＝流星と同じ設計・平均≒3分・上限で裾切り）。u∈(0,1]。 */
private fun nextSatelliteDelayMs(u: Float): Long {
    val uu = u.coerceIn(1e-6f, 1f)
    val expMs = (-ln(uu)) * SAT_INTERVAL_MEAN_MS
    return (SAT_INTERVAL_MIN_MS + expMs.toLong()).coerceAtMost(SAT_INTERVAL_MAX_MS)
}

/**
 * 衛星スケジューラ（実時間抽選・ナビ無相関・高負荷専用）。流星の rememberMeteorHost と同じ堅牢化＝キーは reduceMotion のみ
 * （画面出入りで再起動しない）・実エントロピー seed（決定性を持ち込まない）・初回も抽選 delay。reduce-motion では衛星無効
 * （＝純粋な移動体ゆえ静止させると星と区別できず混乱を招く＝出さない）。
 */
@Composable
private fun rememberSatelliteHost(reduceMotion: Boolean): SatelliteHost {
    val host = remember { SatelliteHost() }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        val rnd = Random(System.nanoTime())
        fun r(): Float = rnd.nextFloat()
        while (isActive) {
            delay(nextSatelliteDelayMs(r()))
            host.event = SatelliteEvent(
                angle = r() * TWO_PI,
                chord = (r() - 0.5f) * 0.8f,               // 弦を画面短辺の ±0.4 までずらす（毎回違う道筋）
                bright = SAT_ALPHA * (0.85f + r() * 0.3f), // 平常時＝中程度の星と同等・僅かな個体差
                flarePeak = SAT_FLARE_PEAK_MIN + r() * SAT_FLARE_PEAK_SPAN, // フレア頂点位置＝毎回違う（[0.35,0.65]）
            )
            val durMs = SAT_CROSS_MIN_MS + (r() * (SAT_CROSS_MAX_MS - SAT_CROSS_MIN_MS)).toInt()
            host.progress.snapTo(0f)
            host.progress.animateTo(1f, tween(durMs, easing = LinearEasing)) // 30〜60s の等速横断
            host.event = null
        }
    }
    return host
}

// ===== v7.4 彗星（実時刻決定的＝実時刻が seed。「30分で1横断」＝見ていて分かる進み・不在期1〜3時間）=====
// xf/yf=画面比の頭部位置／dirx,diry=進行方向（尾はこの逆へ引く）。横断中のみ non-null（不在期は null＝空に彗星なし）。
private class Comet(val xf: Float, val yf: Float, val dirx: Float, val diry: Float)
/**
 * 実時刻から彗星を O(1) で決定（毎フレーム呼ばれるため走査不可＝閉形式）。k=floorDiv(t,SLOT) が横断回次、rel=floorMod(t,SLOT) が
 * スロット内経過。crossing は rel∈[jitter_k, jitter_k+CROSS] にのみ存在（それ以外は不在=null）。同じ nowMs は必ず同じ結果＝
 * 「同じ時刻に開けば同じ位置」。位置は prog=(rel−jitter)/CROSS の連続関数＝30分で滑らかに横断（ms 精度＝数十秒で位置変化に気づく）。
 * 軌道（中心 cx,cy・方向 ang）と jitter は横断回次 k の hash＝回ごとに違い、同じ横断中は不変。
 */
private fun currentComet(nowMs: Long): Comet? {
    val t = nowMs - COMET_ANCHOR_MS
    val k = Math.floorDiv(t, COMET_SLOT_MS)                          // 横断回次（スロット index）
    val rel = Math.floorMod(t, COMET_SLOT_MS)                        // スロット内の経過 ms
    val jitter = (hashUnit(k, COMET_SALT + 19) * COMET_JITTER_MS).toLong() // この回の横断開始オフセット（不在ギャップを散らす）
    if (rel < jitter || rel >= jitter + COMET_CROSS_MS) return null  // 不在期（横断ウィンドウ外）
    val prog = (rel - jitter).toFloat() / COMET_CROSS_MS            // 0..1（30分の横断進行）
    val cx = 0.30f + hashUnit(k, COMET_SALT) * 0.40f                // 横断の中心 x ∈ [0.3,0.7]
    val cy = 0.24f + hashUnit(k, COMET_SALT + 7) * 0.46f            // 中心 y ∈ [0.24,0.70]
    val ang = hashUnit(k, COMET_SALT + 13) * TWO_PI
    val dx = cos(ang); val dy = sin(ang)
    // prog=0.5 で中心・両端で画面外へ出入り（入口→出口。COMET_SPAN=0.9＝画面の大部分を30分で横切る）。
    return Comet(cx + dx * COMET_SPAN * (prog - 0.5f), cy + dy * COMET_SPAN * (prog - 0.5f), dx, dy)
}

// ============================================================
// ===== v7.1 検分用の即時トリガー（debug 専用・BuildConfig.DEBUG ガードの UI からのみ叩かれる）=====
// 「確率/周期を待たずに実機で見たい」への外付けスイッチ。本来のスケジューラ/確率生成は非破壊＝これは「割り込み注入」:
//   ・流星/衛星: 専用の manual Host を別に持ち手動発火（本物の Host のイベント/進行には一切触れない＝通常の確率イベントは正常継続）。
//   ・彗星: 表示だけ強制するフラグ（currentComet の周期ロジックは不変＝release では常に実時刻経路）。
//   ・BH: 可視中央チャンクを BH 付きで焼き直す（bhInChunk が forcedBhChunk を見て確率無視で必ず湧かせる）。
// release では UI が無く pulse は増えず cometForced=false・forcedBhChunk=番兵のまま＝製品挙動（確率・周期の本来設計）は完全に不変。
object HlSkyDebug {
    var meteorPulse by mutableStateOf(0)               // 押下ごとに ++（HighLoadSkyM が変化を観測し1発注入）
    var satellitePulse by mutableStateOf(0)
    var cometForced by mutableStateOf(false)           // true で強制表示（画面中央やや上）・false で通常の実時刻周期
    var bhPulse by mutableStateOf(0)
    @Volatile var forcedBhChunk: Long = Long.MIN_VALUE // bg 焼きスレッドの bhInChunk が読む＝Compose state でなく volatile。番兵=無効
    @Volatile var forcedBhFy: Float = 0.5f
    @Volatile var forcedBhVariant: Int = 0             // v7.2: BH ボタン連打の偶奇で 0=案A/1=案B を交互（bg 焼きスレッドが読む＝volatile）
    var cloudPulse by mutableStateOf(0)                // v7.5.1 押下ごとに ++（可視中央へ暗黒雲を1つ強制湧き＝流れ去るまで表示）
    @Volatile var forcedCloudChunk: Long = Long.MIN_VALUE // genCloud が確率無視で必ず雲を湧かせる world チャンク。番兵=無効（release では常に番兵）
    @Volatile var forcedCloudFy: Float = 0.5f          // 強制雲のチャンク内 y（＝可視中央。押下後どこを見ればよいか一目）
    fun fireMeteor() { meteorPulse++ }
    fun fireSatellite() { satellitePulse++ }
    fun toggleComet() { cometForced = !cometForced }
    fun fireBh() { bhPulse++ }
    fun fireCloud() { cloudPulse++ }
}
/** 検分用の強制彗星（画面中央やや上・固定方向＝押した後どこを見ればよいか一目で分かる）。 */
private fun forcedComet(): Comet = Comet(0.5f, 0.4f, 0.8f, -0.6f)

/**
 * 高負荷スカイ本体（v6・チャンク式無限プロシージャル）。SkyBackdropM が highLoad=true のとき現行レイヤの代わりに丸ごと敷く。
 * field は v6 では未使用（帯・星ともワールド座標で手続き生成する＝drawFarStars/drawDeepSky には依存しない）。署名は呼び出し側
 * （SkyBackdropM）互換のため据え置く。meteor は往復堅牢化のため上位で remember 済みのものを受け取る。
 */
@Composable
internal fun HighLoadSkyM(
    controller: SkyParallaxController,
    @Suppress("UNUSED_PARAMETER") field: DeepSkyField,
    meteor: MeteorHost,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val hl = remember { HlSky() }
    // v7 数分=衛星のスケジューラ（高負荷専用・時間イベント）。流星と同じく早期 return より前で構成＝画面出入りで再起動しない。
    val satellite = rememberSatelliteHost(controller.reduceMotion)
    // v7.1 検分用の手動 Host（debug トリガー専用・本物の Host とは別インスタンス＝確率スケジューラ非破壊）。
    val manualMeteor = remember { MeteorHost() }
    val manualSat = remember { SatelliteHost() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    // 基層（夜天＋ディザ）はサイズ確定時に1回焼く（静止＝画面固定・毎フレームは blit のみ）。
    val deep = remember(canvasSize) {
        if (canvasSize.width <= 0 || canvasSize.height <= 0) null
        else bakeDeep(canvasSize, density, layoutDirection)
    }

    // ⑥ ドリフト/息づきの時計（!reduceMotion のみ回す）。Double で累積＝大時刻でも精度維持。読みは draw フェーズに限る＝再コンポーズなし。
    if (!controller.reduceMotion) {
        LaunchedEffect(canvasSize) {
            if (canvasSize.height <= 0) return@LaunchedEffect
            var last = 0L; var first = true
            while (isActive) {
                withFrameNanos { now ->
                    if (!first) {
                        val dt = ((now - last).coerceAtLeast(0L)) / 1_000_000_000.0 // 秒（Long 差分→小さな Double＝精度良）
                        hl.driftPx.value += DRIFT_SCREENS_PER_SEC * canvasSize.height * dt
                        hl.breatheS.floatValue += dt.toFloat()
                    }
                    last = now; first = false
                }
            }
        }
    }

    // ① 焼きループ: 境界跨ぎ（ChunkWindow が変化）を snapshotFlow で検知し、不足チャンクだけ焼き直す（背景スレッド・先読み）。
    //   snapshotFlow は driftPx/scrollWorldPx を毎フレーム読むが、ChunkWindow（整数 index）が変わったときだけ emit＝焼きは境界時のみ。
    //   canvasSize をキーに relaunch＝サイズ変更でキャッシュを破棄して焼き直す（bitmap 寸法を実サイズへ一致）。
    LaunchedEffect(canvasSize) {
        if (canvasSize.height <= 0) return@LaunchedEffect
        hl.clearChunks()
        val scope = this
        val farBaking = HashSet<Long>()
        val midBackBaking = HashSet<Long>()
        val midFrontBaking = HashSet<Long>()
        snapshotFlow {
            val h = canvasSize.height
            val scroll = controller.scrollWorldPx.toDouble()
            val drift = hl.driftPx.value
            val base = scroll + drift
            // v7.5 天の川2層: drift 成分は両層 RATIO_MID 共通・scroll 比差 + front の sway で window が僅かに割れる＝各層独立に検出
            //   （sway を含めないと front の blit chunk0 が焼き窓の下側へ外れ得る＝欠け。含めて焼き窓と blit を一致させる）。
            val sway = midSwayPx(hl.breatheS.floatValue)
            ChunkWindow(
                floor(RATIO_FAR * base / h).toLong(),
                floor((RATIO_MID_BACK * scroll + RATIO_MID * drift) / h).toLong(),
                floor((RATIO_MID_FRONT * scroll + RATIO_MID * drift + sway) / h).toLong(),
                floor(RATIO_NEAR * base / h).toLong(),
                floor(RATIO_CLOUD * base / h).toLong(),
            )
        }.collect { win ->
            // aspect=w/h＝BH 孔をピクセル真円に写像するため mid 生成へ渡す（canvasSize は本 LaunchedEffect のキー＝焼き直しで最新）。
            val aspect = canvasSize.width.toFloat() / canvasSize.height.toFloat()
            ensureDepth(scope, hl.far, farBaking, { c -> genGrains(c, FAR_SPEC) }, win.far0, canvasSize, density, layoutDirection, softEdge = true) // far=ソフト
            // v7.5 天の川2層: back のみ BH リング deco を焼く（front も同じレンズ写像で孔は空くが deco 二重＝2倍輝度を避ける）。
            ensureDepth(scope, hl.midBack, midBackBaking, { c -> genBandGrains(c, aspect, MID_BACK_SALT, MID_LAYER_COUNT) }, win.midBack0, canvasSize, density, layoutDirection, { c -> bhDecoInChunk(c) })
            ensureDepth(scope, hl.midFront, midFrontBaking, { c -> genBandGrains(c, aspect, MID_FRONT_SALT, MID_LAYER_COUNT) }, win.midFront0, canvasSize, density, layoutDirection)
            ensureNear(hl.near, win.near0)
            ensureCloud(hl.cloud, win.cloud0, aspect)
        }
    }

    // v7.1 検分トリガー: pulse の変化を観測して手動 Host へ1発注入（本物のスケジューラは非破壊）。reduceMotion でも発火する
    //   ＝明示トリガーゆえ（Animatable アニメは frame clock 駆動でドリフト時計と独立）。release では pulse が増えず無動作。
    LaunchedEffect(Unit) {
        val rnd = Random(System.nanoTime())
        snapshotFlow { HlSkyDebug.meteorPulse }.collect { p ->
            if (p <= 0) return@collect
            val ev = buildMeteorEvent(pickMeteorPattern(rnd.nextFloat())) { rnd.nextFloat() }
            manualMeteor.event = ev
            manualMeteor.progress.snapTo(0f)
            // 高負荷 debug 流星も本物と同じ半分速度＝所要2倍で流す（見比べの速度を一致させる）。
            manualMeteor.progress.animateTo(1f, tween((ev.durationMs * HL_METEOR_DURATION_SCALE).toInt(), easing = LinearEasing))
            manualMeteor.event = null
        }
    }
    LaunchedEffect(Unit) {
        val rnd = Random(System.nanoTime())
        snapshotFlow { HlSkyDebug.satellitePulse }.collect { p ->
            if (p <= 0) return@collect
            fun r(): Float = rnd.nextFloat()
            manualSat.event = SatelliteEvent(r() * TWO_PI, (r() - 0.5f) * 0.8f, SAT_ALPHA * (0.85f + r() * 0.3f), SAT_FLARE_PEAK_MIN + r() * SAT_FLARE_PEAK_SPAN)
            val durMs = SAT_CROSS_MIN_MS + (r() * (SAT_CROSS_MAX_MS - SAT_CROSS_MIN_MS)).toInt()
            manualSat.progress.snapTo(0f)
            manualSat.progress.animateTo(1f, tween(durMs, easing = LinearEasing))
            manualSat.event = null
        }
    }
    // BH 強制: 可視中央の mid チャンクを BH 付きで焼き直して差し替え（先に焼いてから map 更新＝可視の欠けを作らない）。
    //   forcedBhChunk を立てておくと以降その world チャンクは常に BH 付きで焼かれる＝スクロールで流れ去るまで一貫（本物の確率生成は不変）。
    LaunchedEffect(Unit) {
        snapshotFlow { HlSkyDebug.bhPulse }.collect { p ->
            if (p <= 0 || canvasSize.height <= 0 || canvasSize.width <= 0) return@collect
            // v7.2: 連打の偶奇で案A/案Bを交互（1回目=A・2回目=B・3回目=A…）＝ユーザーが見た目で見比べられる。volatile を先に立ててから焼く。
            HlSkyDebug.forcedBhVariant = (p - 1) % 2
            val hd = canvasSize.height.toDouble()
            val camPx = cameraPx(RATIO_MID, controller.scrollWorldPx, hl.driftPx.value)
            val centerChunk = floor((camPx + hd / 2.0) / hd).toLong()
            HlSkyDebug.forcedBhChunk = centerChunk
            HlSkyDebug.forcedBhFy = (((camPx + hd / 2.0) % hd) / hd).toFloat().coerceIn(0.15f, 0.85f)
            val aspect = canvasSize.width.toFloat() / canvasSize.height.toFloat()
            withContext(Dispatchers.Default) {
                // v7.5 2層とも即時に BH 付きで焼き直す（両層が同じレンズ写像で孔を空ける／リング deco は back のみ＝二重輝度回避）。
                val backGrains = genBandGrains(centerChunk, aspect, MID_BACK_SALT, MID_LAYER_COUNT)
                val backBmp = bakeGrainChunk(backGrains, canvasSize.width, canvasSize.height, density, layoutDirection, bhDecoInChunk(centerChunk))
                val frontGrains = genBandGrains(centerChunk, aspect, MID_FRONT_SALT, MID_LAYER_COUNT)
                val frontBmp = bakeGrainChunk(frontGrains, canvasSize.width, canvasSize.height, density, layoutDirection)
                withContext(Dispatchers.Main) { hl.midBack[centerChunk] = backBmp; hl.midFront[centerChunk] = frontBmp }
            }
        }
    }
    // v7.5.1 雲強制: 可視中央のチャンクへ暗黒雲を1つ湧かせる。雲はライブ描画ゆえ再焼き不要＝lobe リストを再生成して差し替えるだけ。
    //   forcedCloudChunk を立てるとその world チャンクは確率無視で必ず雲＝視差2.2×で流れ去り自然に消える（本物の CLOUD_PROB 生成は不変）。
    LaunchedEffect(Unit) {
        snapshotFlow { HlSkyDebug.cloudPulse }.collect { p ->
            if (p <= 0 || canvasSize.height <= 0 || canvasSize.width <= 0) return@collect
            val hd = canvasSize.height.toDouble()
            val camPx = cameraPx(RATIO_CLOUD, controller.scrollWorldPx, hl.driftPx.value)
            val centerChunk = floor((camPx + hd / 2.0) / hd).toLong()
            // volatile を先に立ててから再生成（genCloud が forcedCloudChunk/Fy を読む）。fy=可視中央（BH の forcedBhFy と同流儀）。
            HlSkyDebug.forcedCloudFy = (((camPx + hd / 2.0) % hd) / hd).toFloat().coerceIn(0.15f, 0.85f)
            HlSkyDebug.forcedCloudChunk = centerChunk
            val aspect = canvasSize.width.toFloat() / canvasSize.height.toFloat()
            hl.cloud[centerChunk] = genCloud(centerChunk, aspect) // 即時に可視化（ライブ描画＝bitmap 焼き不要ゆえ Main で軽い）
        }
    }

    Box(modifier = modifier.fillMaxSize().onSizeChanged { canvasSize = it }) {
        // z0 静止基層（夜天＋ディザ）＝視差なし・SrcOver で1枚 blit。
        deep?.let { d -> Box(Modifier.fillMaxSize().drawBehind { drawImage(d) }) }
        // 遠景 far（視差0.04＋ドリフト0.5×・加算）＋中景 mid 2層（天の川本体・加算）。毎フレーム blit のみ。
        Box(Modifier.fillMaxSize().clipToBounds().drawBehind {
            val scroll = controller.scrollWorldPx; val drift = hl.driftPx.value // draw フェーズ読み＝再コンポーズなし
            val sway = midSwayPx(hl.breatheS.floatValue) // v7.5 手前層の往復位相（reduceMotion で breathe 停止＝往復も自動停止）
            blitDepth(hl.far, RATIO_FAR, scroll, drift)
            // v7.5 天の川2層: back→front の順（加算ゆえ順不同だが可読性で奥→手前）。drift 成分は blitMid 内で両層 RATIO_MID 同一＝帯がほどけない。
            blitMid(hl.midBack, RATIO_MID_BACK, scroll, drift, 0.0)
            blitMid(hl.midFront, RATIO_MID_FRONT, scroll, drift, sway)
        })
        // 近景 near（視差0.14＋ドリフト1.75×・加算・ライブ＝息づき＋一等星の縮小にじみ）。
        Box(Modifier.fillMaxSize().clipToBounds().drawBehind {
            drawNearChunks(hl.near, controller.scrollWorldPx, hl.driftPx.value, hl.breatheS.floatValue)
        })
        // v7.5 ②前面の暗黒雲（near より手前・視差 2.2×・SrcOver 黒で背後の星を遮蔽＝立体手がかり）。星層と同じ drift/scroll で流れ・reduceMotion で凍る。
        //   z2 動く天体より背面に置く＝流星/衛星は雲に隠されない（大気内の最前景ゆえ物理的にも妥当）。読書中も星層の一部として流れる（演出でなく構造）。
        Box(Modifier.fillMaxSize().clipToBounds().drawBehind {
            drawCloudChunks(hl.cloud, controller.scrollWorldPx, hl.driftPx.value)
        })
        // z2 動く天体（時間スケールの階層）＝彗星（数日・奥）→衛星（数分）→流星（一瞬・最前・最も明るい）の順に加算描画。
        //   読書M本文では z2 演出を抑止（meteorSuppressed＝読書Mモーションゼロ ADR 0022 §3）。BH（常時）は mid へ焼き込み済み＝
        //   ここには無く、読書中も帯の一部として見える（静的＝演出でなく構造）。
        if (!controller.meteorSuppressed) {
            Box(Modifier.fillMaxSize().clipToBounds().drawBehind {
                // v7.4: breatheS を draw 相で読む＝この層を毎フレーム再描画させる（時計の役目は二つ: 彗星の30分横断を滑らかに更新／
                //   イオン尾の揺らぎ位相）。reduceMotion では breatheS が凍る＝彗星の移動・揺らぎも停止（静謐原則どおり）。
                val timeS = hl.breatheS.floatValue
                // v7.1 検分: cometForced のとき強制彗星（画面中央やや上）／衛星・流星は本物＋手動 Host を重ねて描く。
                val comet = if (HlSkyDebug.cometForced) forcedComet() else currentComet(System.currentTimeMillis())
                drawComet(comet, size.width, size.height, timeS)
                drawSatellite(satellite, size.width, size.height)
                drawSatellite(manualSat, size.width, size.height)
                drawHlMeteor(meteor, size.width, size.height)
                drawHlMeteor(manualMeteor, size.width, size.height)
            })
        }
    }
}

/**
 * 近景星を1チャンクぶん描く（加算・離散点は 0.42 非対象）。静謐版⑥:
 *   ・息づき: α = base + 振幅*sin(2π(t/周期 + 位相))・振幅±0.06〜0.12＝存在に気づかせない微揺らぎ。α のみ揺らす（径は不動）。
 *   ・一等星: firstMag は径×2.3 の縮小にじみハロー（彩度を落とし脈動なし）。スパイクは全廃。
 */
private fun DrawScope.drawNearStars(stars: List<HlStar>, w: Float, h: Float, yOff: Float, timeS: Float) {
    for (s in stars) {
        val c = Offset(s.fx * w, s.fy * h + yOff)
        val wave = if (s.twinkle) sin(TWO_PI * (timeS / s.periodS + s.phase)) else 0f
        val a = (s.alpha + s.ampl * wave).coerceIn(0.02f, 1f)
        val rad = s.fr * w
        if (s.firstMag) {
            // 一等星のにじみ＝径×2.3・彩度を落とし（無彩色地へ 0.35 寄せ）・脈動なし。眩惑・レンズフレア化を避ける。
            val haloCol = lerp(s.color, StarNeutralSeizu, 0.35f)
            val hr = rad * 2.3f
            drawCircle(
                Brush.radialGradient(
                    0f to haloCol.copy(alpha = (s.alpha * 0.35f).coerceAtMost(0.30f)),
                    1f to Color.Transparent, center = c, radius = hr,
                ),
                radius = hr, center = c, blendMode = BlendMode.Plus,
            )
        } else if (s.halo) {
            drawCircle(s.color.copy(alpha = s.alpha * 0.14f), radius = rad * 2.2f, center = c, blendMode = BlendMode.Plus)
        }
        drawCircle(s.color.copy(alpha = a), radius = rad, center = c, blendMode = BlendMode.Plus)
        // 芯を白で締める（離散点＝解像。0.42 でキャップ＝キラキラ抑止）。
        drawCircle(StarCoreSeizu.copy(alpha = (a * 0.55f).coerceAtMost(0.42f)), radius = rad * 0.4f, center = c, blendMode = BlendMode.Plus)
    }
}

// ===== v7 一瞬=流星（既存 MeteorHost を高負荷用に明るく描く。造形は MeteorCanvas と同一・輝度と加算だけ強める）=====
// 共有の MeteorCanvas/MeteorTuning は一切変えない（通常モード非影響）＝ここは高負荷レイヤ内の別描画。真因対処: 平均≒60s・掃過≒1s・
//   尾α≤0.42 のうえ v6.2 で帯が3.6倍明るくなり相対コントラストが落ちた＝実機で見えづらかった。→ 尾/芯を加算＋実効α上限を上げて存在感回復。
private fun DrawScope.drawHlMeteor(host: MeteorHost, w: Float, h: Float) {
    val ev = host.event ?: return
    val t = host.progress.value
    val sx = w / METEOR_NW; val sy = h / METEOR_NH
    for (s in ev.streaks) {
        val lt = if (s.phase >= 1f) 1f else ((t - s.phase) / (1f - s.phase)) // イベント内の各筋のローカル進行
        if (lt <= 0f || lt >= 1f) continue
        val frames = 62f * lt
        val hx = (s.nx + s.vx * frames) * sx
        val hy = (s.ny + s.vy * frames) * sy
        val life = 1f - lt
        val vlen = hypot(s.vx, s.vy).coerceAtLeast(1e-4f)
        val tailX = hx - s.vx / vlen * s.len * sx
        val tailY = hy - s.vy / vlen * s.len * sy
        drawLine(
            Brush.linearGradient(
                listOf(s.color.copy(alpha = (s.tailAlpha * 1.6f).coerceAtMost(HL_METEOR_TAIL_ALPHA) * life), Color.Transparent),
                start = Offset(hx, hy), end = Offset(tailX, tailY),
            ),
            start = Offset(hx, hy), end = Offset(tailX, tailY),
            strokeWidth = 1.5f * sx, cap = StrokeCap.Round, blendMode = BlendMode.Plus,
        )
        drawCircle(StarCoreSeizu.copy(alpha = HL_METEOR_CORE_ALPHA * life), radius = 1.4f * sx, center = Offset(hx, hy), blendMode = BlendMode.Plus)
    }
}

// ===== v7 数分=衛星（瞬かない光点が画面空間を完全な直線で横断・尾なし・ドリフト/視差から独立）=====
// v7.2「分かりにくい」→ 過剰装飾を避けつつ目立たせる: 径微増（SAT_RADIUS_NW）＋暖色（SatelliteColor）＋横断中1度だけの
//   イリジウムフレア（progress のガウス山なり＝数秒で滑らかに増減光・ピークで一等星級＋淡い光芒）。点滅/尾は無し（＝実現象の範囲）。
private fun DrawScope.drawSatellite(host: SatelliteHost, w: Float, h: Float) {
    val ev = host.event ?: return
    val t = host.progress.value
    val d = Offset(cos(ev.angle), sin(ev.angle))       // 横断方向（ピクセル空間で真っ直ぐ）
    val perp = Offset(-d.y, d.x)
    val shortSide = if (w < h) w else h
    val center = Offset(w * 0.5f, h * 0.5f) + perp * (ev.chord * shortSide) // 弦をずらす
    val half = 0.62f * hypot(w, h)                     // 端から端まで確実に横断する半路程
    val head = center + d * (half * (2f * t - 1f))     // t: 0=一方の外→1=対辺の外（等速）
    val m = 8f
    if (head.x < -m || head.x > w + m || head.y < -m || head.y > h + m) return // 画面外は描かない（入退場は端で消える）
    val sx = w / METEOR_NW
    // フレア＝flarePeak を中心とするガウス（尖った明滅でなく滑らかな山なり）。頂点で一等星級まで持ち上げ、外では平常輝度へ戻る。
    val z = (t - ev.flarePeak) / SAT_FLARE_SIGMA
    val flare = exp(-(z * z))                           // 0..1（頂点=1）
    val bright = (ev.bright + (SAT_FLARE_PEAK - ev.bright) * flare).coerceAtMost(1f)
    val rad = SAT_RADIUS_NW * sx * (1f + SAT_FLARE_RADIUS_GAIN * flare) // 増光時わずかに肥大（bloom の見え）
    // フレア頂点付近だけ淡い光芒を滲ませる（増減光に同期＝常時の装飾でない。尾/スパイクではない bloom）。
    if (flare > 0.02f) {
        val gr = rad * 3.2f
        drawCircle(
            Brush.radialGradient(
                0f to SatelliteColor.copy(alpha = 0.5f * flare), 0.5f to SatelliteColor.copy(alpha = 0.2f * flare),
                1f to Color.Transparent, center = head, radius = gr,
            ),
            radius = gr, center = head, blendMode = BlendMode.Plus,
        )
    }
    drawCircle(SatelliteColor.copy(alpha = bright), radius = rad, center = head, blendMode = BlendMode.Plus) // 暖色・尾なし
    drawCircle(StarCoreSeizu.copy(alpha = SAT_CORE_ALPHA), radius = rad * 0.5f, center = head, blendMode = BlendMode.Plus)
}

// ===== v7 彗星（淡い尾＋頭部光芒。位置は実時刻決定的＝currentComet。ドリフト/視差には乗せない〔後述の設計判断〕）=====
// 設計判断: ドリフト（自転 107s/画面）に乗せると横断とドリフトが二重掃引で混乱する。＆driftPx はセッションでリセット・scrollWorldPx は
//   保存＝実時刻絶対のワールド配置は復元が脆い。→ 彗星は画面座標に実時刻決定的で置き、実時間の移動は「30分横断」に一本化（v7.4）。
// v7.2 尾の作り直し（「白点の後ろに丸が2,3個」→ 本物の尾）: ①Path＋linearGradient で頭部から後方へ扇状に開く連続グラデを塗る
//   ＝途切れない一枚の尾（点列でない）。②実物同様の2本構成＝まっすぐで細い淡青のイオンテイル＋緩くカーブする白いダストテイル。
//   ③その上に多数の微粒を減衰散布して「流れ」の粒状感を足す（決定的 PRNG＝dir seed＝毎フレーム不動でちらつかない）。頭部はコマ＋小核。
// v7.4 イオン尾の揺らぎ: 太陽風でイオンテイルがゆらめく実機序を極微に＝timeS 駆動のゆっくり正弦波で曲率/開きを±5%変調。ダストは慣性大＝揺らさない。
private fun DrawScope.drawComet(comet: Comet?, w: Float, h: Float, timeS: Float) {
    comet ?: return
    val m = 0.4f                                        // 尾が長くなったぶんマージンを広げ、頭が端外でも尾が見えるうちは描く
    if (comet.xf < -m || comet.xf > 1f + m || comet.yf < -m || comet.yf > 1f + m) return
    val head = Offset(comet.xf * w, comet.yf * h)
    val nw = w / METEOR_NW
    val tailLen = COMET_TAIL_LEN * h
    val tailDir = Offset(-comet.dirx, -comet.diry)     // 尾は進行方向の逆へ（太陽風に流される向きの近似）
    val perp = Offset(-tailDir.y, tailDir.x)
    val dustHalf = COMET_DUST_HALF * h
    val ionHalf = COMET_ION_HALF * h
    val curve = COMET_DUST_CURVE * tailLen             // ダストの湾曲量（後方ほど f² で横へ＝軌道遅れ）

    // 尾のポリゴン（中心線を f²で湾曲＋各点で ±halfWidth(f) に開く）。widen=頭側の絞り具合（0で細く始まり後方で扇）。
    fun tailPath(half: Float, curveAmt: Float, widen: Float): Path {
        val steps = 18
        val left = ArrayList<Offset>(steps + 1)
        val right = ArrayList<Offset>(steps + 1)
        for (i in 0..steps) {
            val f = i.toFloat() / steps
            val axis = head + tailDir * (tailLen * f) + perp * (curveAmt * f * f)
            val hw = half * (widen * f + (1f - widen) * f * f) // 頭で細く後方で広い扇
            left += axis + perp * hw
            right += axis - perp * hw
        }
        return Path().apply {
            moveTo(left[0].x, left[0].y)
            for (i in 1..steps) lineTo(left[i].x, left[i].y)
            for (i in steps downTo 0) lineTo(right[i].x, right[i].y)
            close()
        }
    }
    // ①ダストテイル＝緩くカーブする白の連続グラデ（頭で明・後方で透明へ減衰）。
    val dustEnd = head + tailDir * tailLen + perp * curve
    drawPath(
        tailPath(dustHalf, curve, 0.32f),
        brush = Brush.linearGradient(
            0f to CometDustColor.copy(alpha = 0.28f), 0.4f to CometDustColor.copy(alpha = 0.12f),
            1f to Color.Transparent, start = head, end = dustEnd,
        ),
        blendMode = BlendMode.Plus,
    )
    // ②イオンテイル＝まっすぐ細い淡青。v7.4 太陽風の揺らぎ＝timeS 駆動のゆっくり正弦波で曲率と開きを±5%変調（気づく人が気づく極微）。
    //   位相は dir 由来で個体差（彗星ごとに揺らぎが揃わない）。ダストは慣性大＝揺らさず（実物どおり）。
    val sway = sin(TWO_PI * timeS / COMET_ION_SWAY_PERIOD_S + comet.diry * 3.3f)
    val ionCurveAmt = COMET_ION_SWAY_CURVE * tailLen * sway   // 揺らめく曲率（後端で±5%横へ）
    val ionHalfMod = ionHalf * (1f + COMET_ION_SWAY_WIDTH * sway) // 開きの±5%変調
    val ionEnd = head + tailDir * (tailLen * 1.05f) + perp * ionCurveAmt
    drawPath(
        tailPath(ionHalfMod, ionCurveAmt, 0.6f),
        brush = Brush.linearGradient(
            0f to CometIonColor.copy(alpha = 0.24f), 0.5f to CometIonColor.copy(alpha = 0.11f),
            1f to Color.Transparent, start = head, end = ionEnd,
        ),
        blendMode = BlendMode.Plus,
    )
    // ③微粒散布＝連続グラデの上に粒状の「流れ」を足す。dir 由来の決定的 seed＝毎フレーム同一（ちらつかない）。
    val rng = Lcg(hashSeedInt(((comet.dirx * 9973f).toInt().toLong() * 1000003L) + (comet.diry * 9973f).toInt().toLong(), COMET_SALT))
    for (i in 0 until COMET_TAIL_GRAINS) {
        val f = rng.next().let { it * it }                 // u²＝頭寄り(f→0)に密・後方(f→1)に疎（尾の減衰らしさ）
        val hw = dustHalf * (0.32f * f + 0.68f * f * f)
        val lateral = (rng.next() - 0.5f) * 2f * hw        // 扇内に散る（±hw）
        val p = head + tailDir * (tailLen * f) + perp * (curve * f * f + lateral)
        val a = 0.14f * (1f - f) * (1f - f)                // 後方ほど淡く減衰
        val col = if (rng.next() < 0.28f) CometIonColor else CometDustColor
        drawCircle(col.copy(alpha = a), radius = (1.5f - 0.9f * f) * nw, center = p, blendMode = BlendMode.Plus)
    }
    // 頭部＝コマ（radial bloom の光芒）に包まれた小さな白核。
    val hr = 3.6f * nw
    drawCircle(
        Brush.radialGradient(
            0f to CometHeadColor.copy(alpha = 0.55f), 0.5f to CometHeadColor.copy(alpha = 0.24f),
            1f to Color.Transparent, center = head, radius = hr,
        ),
        radius = hr, center = head, blendMode = BlendMode.Plus,
    )
    drawCircle(StarCoreSeizu.copy(alpha = 0.6f), radius = 1.3f * nw, center = head, blendMode = BlendMode.Plus)
}
