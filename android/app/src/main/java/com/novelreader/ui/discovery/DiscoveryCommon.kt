package com.novelreader.ui.discovery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelreader.discovery.model.SerialState
import com.novelreader.discovery.model.WorkSummary
import com.novelreader.domain.relativeReadLabel
import com.novelreader.narou.model.NarouGenres
import com.novelreader.narou.model.NarouOrder
import com.novelreader.ui.theme.FontLabel
import com.novelreader.ui.theme.FontListItemTitle
import com.novelreader.ui.theme.FontMicroLabel
import com.novelreader.ui.theme.FontRankNumeral
import com.novelreader.ui.theme.LocalShelfColors
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

// ============================================================
// 発見系画面の共通部品（モック docs/design-candidates/discovery/*.html の翻訳）。
// 一覧行 .rk ＝ 順位（明朝数字・上位3位のみ藍）＋タイトル明朝＋作者・ジャンル＋メタ1行。
// 「公式より丁寧」の実体: pt生数字に読了目安（time）を人の言葉で併記する。
// 色は MaterialTheme.colorScheme（Color.kt トークン）経由・直書き禁止（ADR 0005）。
// ============================================================

/**
 * 「連載中 127話」「完結 88話」「短編」。end の意味は仕様書どおり直感と逆（0=短編・完結済）。
 * 話数（generalAllNo）が欠損（null）のときは話数を出さず状態のみ表示する。
 * なぜ 0/1 で埋めないか: of で general_all_no を外すと欠損しうるが、そこを 1 等で補うと
 * 「連載中 1話」のような実データに無い話数を捏造表示してしまうため（欠損は正直に伏せる）。
 */
fun novelStatusLabel(work: WorkSummary): String {
    // serialState はマッパが novelType(2=短編)＋end(0=完結・1=連載中) を吸収済み（旧 novelType/end 分岐と等価）。
    if (work.serialState == SerialState.SHORT) return "短編"
    val status = if (work.serialState == SerialState.COMPLETED) "完結" else "連載中"
    return work.chapterCount?.let { "$status ${it}話" } ?: status
}

/** 読了時間（分）→「約12分」「約8時間」。readMinutes 欠損時は lengthChars から導出（読了時間＝文字数÷500 切り上げ）。 */
fun readTimeLabel(work: WorkSummary): String? {
    val minutes = work.readMinutes
        ?: work.lengthChars?.let { (it + 499) / 500 }
        ?: return null
    return if (minutes < 60) {
        "約${minutes}分"
    } else {
        // なぜ四捨五入か: 一覧の目安表示に分単位の精度は不要で、「約8時間」の丸い言葉が D の静けさに合うため。
        val hours = (minutes + 30) / 60
        "約${hours}時間"
    }
}

/**
 * 現在の order タブに対応するポイントラベル（「週間 12,345pt」）。値が無ければ null。
 *
 * 不変条件: **この行の数値は「その並び順を決めた指標」でなければならない**。順位数字のすぐ隣に置かれる
 * 数値を、読み手は「順位の根拠」として読むためで、根拠でない数値を並べると順位そのものが壊れて見える。
 *
 * だから新着（[NarouOrder.NEW]）は pt を出さない（2026-07-30 実機報告の真因是正）。
 * なろうAPI の order=new は novelupdated_at 降順＝**ポイントは並びに一切関与しない**
 * （根拠は [com.novelreader.narou.NovelApiRepository.mergeByOrder] の NEW 分岐と narou_api_manual.md §3）。
 * 旧実装は TOTAL と同じ枝で累計ptへ倒しており、実機の新着タブが「1位 22pt・2位 27,031pt・3位 0pt」と
 * 並んだ＝**並び自体は正しいのに壊れて見える**表示になっていた。
 *
 * 新着で代わりに出す指標は更新日時＝[updatedAtLabel]（2026-07-31 ユーザー裁定）。
 */
fun pointLabel(order: NarouOrder, work: WorkSummary): String? {
    val (prefix, value) = when (order) {
        NarouOrder.DAILY -> "日間" to work.points?.daily
        NarouOrder.WEEKLY -> "週間" to work.points?.weekly
        NarouOrder.MONTHLY -> "月間" to work.points?.monthly
        NarouOrder.QUARTER -> "四半期" to work.points?.quarter
        NarouOrder.TOTAL -> "累計" to work.points?.global
        // 新着は pt が並びに関与しない＝説明にならない数値は出さない（上の KDoc が理由）。
        // 枝を独立させておくことで、期間が増えたとき「どのptで並ぶのか」の判断を when が強制する。
        NarouOrder.NEW -> return null
    }
    if (value == null) return null
    return "$prefix ${String.format(Locale.JAPAN, "%,d", value)}pt"
}

/** なろうAPI の日時文字列が拠って立つ暦（[updatedAtLabel] の判定基準・理由は同関数の KDoc）。 */
private val NarouCalendarZone: ZoneId = ZoneId.of("Asia/Tokyo")

/** `novelupdated_at` の書式（narou_api_manual.md §4「YYYY-MM-DD HH:MM:SS」）。 */
private val UpdatedAtPattern: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.JAPAN)

/** 当日ぶんの時刻表記。作品詳細の取得時刻表示（SimpleDateFormat("HH:mm")）と同じ書式を流用＝新書式を作らない。 */
private val UpdatedAtTimePattern: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm", Locale.JAPAN)

/**
 * 新着順の一覧行に出す更新日時ラベル。当日なら「05:45 更新」・それ以前は「昨日 更新」「3日前 更新」。
 * [raw] は [com.novelreader.discovery.model.WorkSummary.updatedAt]（なろうは novelupdated_at の生文字列）。
 *
 * **なぜ2段の粒度か**: 新着順は更新の新しい順で、実測（2026-07-31 05:49 取得の order=new 上位20件）では
 * 4分前〜20分前に密集する。ここに日粒度だけを当てると全行が「今日」になり順位の根拠を一切説明しない。
 * 逆に古い作品まで時刻だけを出すと「何日前の 05:45 か」が消える。だから当日は時刻・それ以前は日付相対にする。
 *
 * **どの暦で「今日」を判定するか＝なろうの JST 固定**（端末タイムゾーンに追従しない）。理由:
 *  (1) この文字列はオフセットを持たない壁時計で、なろうがJSTで発行し、なろうのページもJSTで表示する。
 *      端末TZへ変換すると、ユーザーが「なろうで読む」で飛んだ先の更新時刻と表示が食い違い突合できなくなる。
 *  (2) 同アプリの [formatLastupLabel]（作品詳細「2026年7月5日 更新」）は既に生文字列の日付部をそのまま
 *      出しており＝JST壁時計基準。ここだけ端末TZ基準にすると同じ作品の更新日が画面間でずれる。
 * 端末が別TZだと「今日」は日本の暦日を指すが、根拠がひとつに揃う方を採る（上記(1)(2)）。
 *
 * **なぜ相対側へ暦日の開始時刻を渡すか**: [com.novelreader.domain.relativeReadLabel] の閾値は経過時間の
 * 24時間窓で、暦日の境界ではない。素の経過ミリ秒を渡すと「昨日 23:59 更新／今 00:01」が差 2分＝「今日」と
 * 判定され、直前で暦日により当日でないと決めた判断と矛盾する。両引数を暦日の 00:00 に揃えて渡すと
 * 差がちょうど N 日になり、同関数の閾値が暦日差の意味へ素直に写る（1日→昨日・2〜6→N日前・7→1週間前…）。
 * 語彙は同関数のまま＝発明しない。
 *
 * **[nowMillis] を引数で受ける理由**: 関数内で実時計を読むとこのラベルはテスト不能になる
 * （境界＝当日00:00・昨日23:59・日跨ぎ直後を固定できない）。時計への依存は呼び出し側の境界へ追い出す
 * （ui/skins/k/DiscoveryHomeK.kt の initialMoodPattern と同じ state hoisting の判断）。
 *
 * @return 欠損（null）・書式外・未来日時は **null＝何も出さない**。空文字や「不明」で埋めず、現在時刻でも
 *   代用しない（[com.novelreader.discovery.model.WorkSummary.updatedAt] が定めた約束）。順位の根拠として
 *   読まれる値なので、根拠が無いことを別の値で埋めると誤情報になるため。
 *   なぜ [DateTimeParseException] だけを捕えるか: なろうはこの書式を公式に保証しておらず想定外の形が来うる
 *   一方、ここは付帯的なメタ表示で画面を落とす価値が無い。捕捉は書式不一致に限定し他の例外は覆い隠さない
 *   （[formatLastupLabel] と同じ流儀）。
 */
internal fun updatedAtLabel(raw: String?, nowMillis: Long): String? {
    val updated = raw?.let {
        try {
            LocalDateTime.parse(it, UpdatedAtPattern)
        } catch (e: DateTimeParseException) {
            null
        }
    } ?: return null

    val today = Instant.ofEpochMilli(nowMillis).atZone(NarouCalendarZone).toLocalDate()
    val updatedDate = updated.toLocalDate()
    if (updatedDate == today) return "${updated.format(UpdatedAtTimePattern)} 更新"

    // 暦日の 00:00 同士で比較する（上の KDoc「なぜ暦日の開始時刻を渡すか」）。未来日時は
    // relativeReadLabel が null を返す＝何も出さない（端末時計のズレ等で起きうる防御）。
    val relative = relativeReadLabel(
        lastReadAt = updatedDate.atStartOfDay(NarouCalendarZone).toInstant().toEpochMilli(),
        now = today.atStartOfDay(NarouCalendarZone).toInstant().toEpochMilli(),
    ) ?: return null
    return "$relative 更新"
}

/**
 * 一覧行に出す「その並び順を決めた指標」を1つだけ返す。pt で並ぶ期間は [pointLabel]、新着は [updatedAtLabel]。
 *
 * なぜ排他か: 順位数字の隣の値は「順位の根拠」として読まれるため、根拠は常にひとつでなければ
 * 何が並びを決めたのか判別できなくなる。when を order で分岐させ、期間が増えたときに
 * 「その期間は何で並ぶのか」の判断をコンパイラが強制する形にしてある。
 */
internal fun orderMetricLabel(order: NarouOrder, work: WorkSummary, nowMillis: Long): String? =
    when (order) {
        NarouOrder.NEW -> updatedAtLabel(work.updatedAt, nowMillis)
        NarouOrder.DAILY, NarouOrder.WEEKLY, NarouOrder.MONTHLY,
        NarouOrder.QUARTER, NarouOrder.TOTAL -> pointLabel(order, work)
    }

/**
 * [orderMetricLabel] の Composable 版。「今」を行の初回コンポーズ時刻で凍結して渡す。
 *
 * なぜ用意するか: D 以外のスキン（J/M/P）は [NovelListRow] を使わず自前で一覧行を描くため、
 * 「実時計は remember で凍結して純関数へ渡す」という規律を各スキンへ写経させることになる。
 * 写経は必ずどこかで崩れる（1箇所でも素の System.currentTimeMillis() を書けば、その行だけ
 * 再コンポーズのたびに「今」が動き、同一フレーム内で行ごとに違う暦日判定をしうる）ので、
 * 規律ごと1箇所へ閉じる。時計を引数化した理由そのものは [updatedAtLabel] の KDoc を参照。
 */
@Composable
internal fun rememberOrderMetricLabel(order: NarouOrder, work: WorkSummary): String? {
    val nowMillis = remember { System.currentTimeMillis() }
    return orderMetricLabel(order, work, nowMillis)
}

/**
 * 一覧の1行（モック .rk）。
 * @param rank 1始まりの表示順位。上位3位のみ藍（primary）、以降は補助色。
 * @param order メタ行末の指標（pt／更新日時）の種別決定に使う（結果一覧では基本 order を渡す）。
 * @param nowMillis 更新日時ラベルの「今」。既定は行の初回コンポーズ時刻を [remember] で凍結する。
 *   なぜ引数か: 関数内で実時計を読むと [orderMetricLabel] の暦日境界がテストから固定できないため、
 *   時計依存を呼び出し側の境界へ追い出す（DiscoveryHomeK の initialMoodPattern と同じ state hoisting）。
 *   なぜ remember で凍結するか: 再コンポーズのたびに読み直すと一覧が同一フレーム内で別々の「今」を持ちうる。
 *   代償として日跨ぎ直後は行が再生成されるまで「05:45 更新」が残るが、粒度が日なので実害は無い。
 */
@Composable
fun NovelListRow(
    rank: Int,
    novel: WorkSummary,
    order: NarouOrder,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    nowMillis: Long = remember { System.currentTimeMillis() },
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.S16),
    ) {
        Text(
            text = rank.toString(),
            fontFamily = MinchoFamily,
            fontSize = FontRankNumeral,
            // rank>3 は順位＝情報を運ぶ文字のため infoText（AA 4.5:1 充足）。上位3位の primary は据え置き。
            // onSurfaceVariant（装飾用）は素地上 3.79:1 で AA 未達（ADR 0014-D 裁定で情報用途のみ分離）。
            color = if (rank <= 3) MaterialTheme.colorScheme.primary
            else LocalShelfColors.current.infoText,
            modifier = Modifier
                .width(34.dp)
                .padding(top = Spacing.S4),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = novel.title,
                fontFamily = MinchoFamily,
                fontSize = FontListItemTitle,
                lineHeight = 21.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = Spacing.S4),
                horizontalArrangement = Arrangement.spacedBy(Spacing.S8),
            ) {
                Text(
                    text = novel.author,
                    fontSize = FontLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // なぜ weight(1f)+ellipsis: 作者名が長い作品（例「藍銅 紅@『お姉様は…』」）だと
                    // 右のジャンルタグが狭いカラムに押し出され1文字ずつ縦積みになる実機バグが出るため、
                    // 作者名側を可変幅で詰めてタグ用の横幅を確保する。
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                NarouGenres.genreLabel(novel.genreCode)?.let { genre ->
                    Text(
                        text = genre,
                        fontSize = FontLabel,
                        letterSpacing = 0.5.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        // なぜ maxLines=1+softWrap=false: タグ自体が改行して縦積みになるのを防ぎ、
                        // 常に横一列で表示させる（タグは固定内容なので折返し不要）。
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
            Row(
                modifier = Modifier.padding(top = Spacing.S8),
                horizontalArrangement = Arrangement.spacedBy(Spacing.S12),
            ) {
                Text(
                    text = novelStatusLabel(novel),
                    fontSize = FontMicroLabel,
                    // 連載状態は情報を運ぶ文字＝infoText（AA 4.5:1）。装飾用 onSurfaceVariant と分離（ADR 0014-D）。
                    color = LocalShelfColors.current.infoText,
                )
                readTimeLabel(novel)?.let {
                    Text(
                        text = it,
                        fontSize = FontMicroLabel,
                        // 読了目安も情報テキスト＝infoText（AA 4.5:1・ADR 0014-D 裁定）。
                        color = LocalShelfColors.current.infoText,
                    )
                }
                // 並び順を決めた指標をひとつだけ（pt 系の期間は pt・新着は更新日時）。字面・色は
                // 従来の pt と同一トークンのまま＝同じ役どころに新しい意匠を発明しない。
                orderMetricLabel(order, novel, nowMillis)?.let {
                    Text(
                        text = it,
                        fontSize = FontMicroLabel,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/**
 * 発見系リスト領域の「本体でない」表示状態（読込中／空／エラー）。
 * なぜ専用 sealed 型か: DiscoveryStatusBox は DiscoveryUiState（ホーム・結果一覧）と
 * NovelDetailUiState（作品詳細）の2つの状態型から共有され、いずれも Content 相当（一覧本体・
 * 詳細本体）は各画面が自前で描く。box が受け持つのはこの3状態だけなので、両状態型の共通部分を
 * ここへ切り出す。旧 API の bool＋nullable 併用（isLoading/emptyMessage/errorMessage）だと
 * 「読込中かつエラー」のような不正な組合せが型上表現できてしまうため、排他を型で保証する。
 */
sealed interface DiscoveryStatus {
    object Loading : DiscoveryStatus
    data class Empty(val message: String) : DiscoveryStatus
    data class Error(val message: String, val onRetry: (() -> Unit)? = null) : DiscoveryStatus
}

/** Loading / Empty / Error の共通表示（発見系画面のリスト領域用）。 */
@Composable
fun DiscoveryStatusBox(
    status: DiscoveryStatus,
    modifier: Modifier = Modifier,
) {
    // なぜ fillMaxSize を内部固定しないか: 呼び出し側でサイズ要求が実際に分岐する
    // （結果一覧・詳細は領域いっぱい＝fillMaxSize、ホームは LazyColumn item 内で半分の高さ）。
    // サイズは配置を決める親の責務なので modifier で受け取り、ここでは素の Box に適用する。
    Box(modifier = modifier) {
        when (status) {
            is DiscoveryStatus.Loading ->
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            is DiscoveryStatus.Error -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(Spacing.S24),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = status.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = Spacing.S16),
                    )
                    status.onRetry?.let { onRetry ->
                        Button(onClick = onRetry) { Text("再試行") }
                    }
                }
            }
            is DiscoveryStatus.Empty -> Text(
                text = status.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

// ── Preview ──────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun DiscoveryStatusBoxPreview_Loading() {
    MaterialTheme {
        DiscoveryStatusBox(
            status = DiscoveryStatus.Loading,
            modifier = Modifier.fillMaxWidth().height(160.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DiscoveryStatusBoxPreview_Empty() {
    MaterialTheme {
        DiscoveryStatusBox(
            status = DiscoveryStatus.Empty("該当する作品がありません"),
            modifier = Modifier.fillMaxWidth().height(160.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DiscoveryStatusBoxPreview_Error() {
    MaterialTheme {
        DiscoveryStatusBox(
            status = DiscoveryStatus.Error("通信に失敗しました", onRetry = {}),
            modifier = Modifier.fillMaxWidth().height(160.dp),
        )
    }
}
