package com.novelreader.ui.discovery

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelreader.narou.model.NarouGenres
import com.novelreader.narou.model.Ncode
import com.novelreader.ui.components.ShioriCover
import com.novelreader.ui.components.hslToColor
import com.novelreader.ui.components.shioriHue
import com.novelreader.ui.theme.FontActionLabel
import com.novelreader.ui.theme.FontBody
import com.novelreader.ui.theme.FontButtonLabel
import com.novelreader.ui.theme.FontChipLarge
import com.novelreader.ui.theme.FontLabel
import com.novelreader.ui.theme.FontMicroLabel
import com.novelreader.ui.theme.FontSectionTitle
import com.novelreader.ui.theme.FontSubTitle
import com.novelreader.ui.theme.FontTopBarTitle
import com.novelreader.ui.theme.LocalShelfColors
import com.novelreader.ui.theme.LocalShioriColors
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.MotionDurationCrossfade
import com.novelreader.viewmodel.NovelDetailUiState
import com.novelreader.viewmodel.NovelDetailViewModel
import com.novelreader.ui.theme.Spacing
import java.util.Locale
import kotlin.math.roundToInt

// ============================================================
// 作品詳細の書影ブロック＝案2-c「淡地」（2026-08-21 ユーザー裁定・意匠正本 discovery/discovery-detail-D.html）。
//
// 旧構成は全幅の暗色スラブ（BookCover・彩度12〜21%／明度26〜34%）で、①ほぼ無彩色ゆえ版面が毎回
// そっくり同じに見え、②同じ作品が本棚（栞書影）と詳細で別の顔になっていた。案2-c は本棚と1ピクセル同じ
// 栞書影をカードで置き、その背後の帯の地だけを作品色から導くことで両方を解く。
//
// ランダム性をどう収めるか（本案の核心）: 帯の地は作品色の**色相だけ**を借り、彩度14%・明度は固定窓へ
// 圧縮する。窓を固定するので8色相のどれでも墨のコントラストが 13.88〜14.36:1 に揃い（振れ0.5以内）、
// **作品ごとに変わるのは色みだけで版面の明暗は動かない**。書影の生成規則（ShioriGenerator）には一切触らない。
// ============================================================

/** 書影カード 114×152dp＝3:4（本棚グリッドと同比）。**fontScale に追従しない**（下の [detailHeroMetrics]）。 */
private val DetailCoverWidth = 114.dp
private val DetailCoverHeight = 152.dp

/** 書影カードの上端＝帯の内側余白 S12。カードが dp 固定なので、この値も fontScale に追従しない。 */
private val DetailCoverTopInset = Spacing.S12

/**
 * 書影カードの下端（12 + 152 = 164dp）。ブロック高の `max` の相手であり、
 * 「書影が帯の境界を越える量 = 164 − 帯」の基準線でもある単一情報源。
 */
private const val DetailCoverBottomDp = 164f

/**
 * 書影ブロックの寸法一式。**fontScale（F）から一意に決まる**ので、必ずこの1関数から引く
 * （帯の高さ・情報列・題名の箱・App bar へ題字を出す閾値を別々に計算すると必ず食い違う）。
 *
 * ---- なぜ帯を fontScale へ追従させるのか（案A・2026-08-26 ユーザー裁定）----
 * 帯が dp 固定だと、中の情報列は sp で伸びるのに箱が伸びないため、**fontScale 2.0 で作者名とジャンルが
 * 箱から溢れて判読不能**になっていた（題名も7字で止まる）。箱を F 倍すれば字と箱の比が保たれる。
 *
 * ---- なぜ「あらすじが 0行になる」問題はこれで直らないのか ----
 * 実測で 1.0→2.0 の押し出し +268.5dp の内訳は ステータス格子 +161.5・固定バー +55.6・見出し塊 +27.8・
 * システムのステータスバー +23.6 で、**書影ブロックは 1dp も動いていない**＝帯は主犯ではない。
 * 本文を1行取り戻すには 110dp 要る。唯一届く案（あらすじを格子より前へ出す並び替え）は
 * 「作品の素性を先に見せる構成」を捨てるため **不採用**＝2.0 であらすじが 0行なのは受諾済みの結論で、
 * その段で「下へ続く」を言う役目はスクロール示唆の印（端フェード＋シェブロン）が負う。
 */
private data class DetailHeroMetrics(
    /** 帯 = **74·F + 46**（F=1 で 120＝2026-07-31 裁定値と一致）。 */
    val bandHeight: Dp,
    /** ブロック = max(帯, 書影カード下端 164) + カード下の逃げ 2。 */
    val blockHeight: Dp,
    /** 帯の上下の内側余白 = **12 − F**（上下対称）。 */
    val bandInset: Dp,
    /** 情報列 = **76·F + 22**。 */
    val infoColumnHeight: Dp,
    /** 題名の箱 = **46·F**（T2）。 */
    val titleBlockHeight: Dp,
    /** 作者の箱 = 16·F。 */
    val authorBlockHeight: Dp,
    /** ジャンルチップの字面の箱 = 14·F（総高は padding4×2＋枠1×2 を足して 14F+10）。 */
    val chipTextHeight: Dp,
)

/**
 * [DetailHeroMetrics] を fontScale から起こす。式は意匠正本 discovery-detail-D.html の規定そのもの。
 *
 * ---- 帯 = 74·F + 46 の 74 と 46 は何か ----
 * 74 ＝ **sp で伸びる箱の総和**（題名 44 ＋ 作者 16 ＋ チップ字面 14）。
 * 46 ＝ **伸びない部分**（要素間のアキ 6×2 ＋ チップの padding/枠 10 ＋ 帯の上下内側余白 12×2）。
 * ⚠️ 74 の内訳の「題名 44」は T2 で 46 になったが、**帯の式は 74·F + 46 のまま据え置く**のが裁定
 * （T2 の +2dp で帯を太らせない）。増えた 2dp は上下の内側余白から吸収するので、対称は保ったまま
 * 余白の値だけが 12 → **12 − F** になる。ここを「74 を 76 に直す」と裁定と別物になるので触らない。
 *
 * ---- なぜ題名の箱が 46·F なのか（T2）----
 * 旧 44dp は「22×2行ぶん」のつもりの値だったが、Compose 実測の自然行高は 22.75dp で 2行に 45.5dp 要る。
 * **1.5dp 足りず、maxLines=2 と書いてありながら2行目が一度も描かれていなかった**（長い題名が 13字+… で
 * 止まる既存バグの真因）。46 にすると 46 ÷ 45.5 = 1.011 > 1 ＝ **箱と行の「比」**が立つ。
 * 比は F を掛けても変わらないので、**1.0 / 1.3 / 1.5 / 2.0 のどの段でも2行が描かれる**
 * （逆に 44 のままでは 0.967 のままで、箱ごと拡大しても 2.0 でも1行のまま）。
 * ⚠️ 非線形 sp 拡大（Android 14）で実際の字面は F 倍より小さくなる側へずれる＝比はさらに余裕が出る方向。
 *
 * ---- なぜ題名の箱を広げてもあらすじが減らないのか ----
 * ブロック高 = max(帯, 164) + 2 で、F < 1.60 では**書影カード下端 164dp が支配的**だから
 * （帯が 164 を超えるのは F ≥ 1.595）。F=1.3 でも 1.5 でもブロックは 166dp のまま動かない。
 */
private fun detailHeroMetrics(fontScale: Float): DetailHeroMetrics {
    val band = 74f * fontScale + 46f
    val info = 76f * fontScale + 22f
    return DetailHeroMetrics(
        bandHeight = band.dp,
        blockHeight = (maxOf(band, DetailCoverBottomDp) + 2f).dp,
        // (帯 − 情報列) / 2 ＝ 12 − F。割り算で書くのは「上下対称」が定義そのものだから
        // （定数 12 − F を直書きすると、帯か情報列の式を触ったときに対称が静かに崩れる）。
        bandInset = ((band - info) / 2f).dp,
        infoColumnHeight = info.dp,
        titleBlockHeight = (46f * fontScale).dp,
        authorBlockHeight = (16f * fontScale).dp,
        chipTextHeight = (14f * fontScale).dp,
    )
}

/** 情報列の左端。カード右端 24+114=138dp との間が 16dp（S16）＝水平も帯の内側余白と同じ規定で刻む。 */
private val DetailInfoColumnStart = 154.dp

/**
 * 情報列の要素間アキ。⚠️ Spacing.S4/S8 の中間の 6dp を**あえて**使う——情報列 76F+22 の内訳を
 * 合わせるための計算値だから（S8 へ丸めると帯の式 74F+46 と噛み合わず、上下対称の根拠がその場で崩れる）。
 * ⚠️ **アキは fontScale に追従しない**（字ではないので伸ばす理由が無く、46 の「伸びない部分」の一部）。
 */
private val DetailInfoGap = 6.dp

/**
 * 題名・作者・チップの行送りを**明示**する。フォント既定（`normal` 相当）に委ねると環境で 1〜2dp ぶれ、
 * 「情報列 76F+22 で確定」＝上下対称の根拠そのものが消えるため（意匠正本の警告をそのまま写した拘束）。
 */
private val DetailTitleLineHeight = 22.sp
private val DetailAuthorLineHeight = 16.sp
private val DetailChipLineHeight = 14.sp

/**
 * 行送りを CSS の `line-height` に寄せるための様式。情報列の3要素すべてに掛ける。
 *
 * ⚠️ **Compose の `lineHeight` は下限であって上限ではない**（本実装で実測）。CSS の `line-height` は
 * フォント本来の行箱より小さくできるが、Compose はフォントの自然行高を下回れない——正本の指定どおり
 * 22/16/14sp を渡しても実測は 22.75 / 18.5 / 17dp になり、情報列の内訳が 8dp ぶん膨らんだ。
 * [LineHeightStyle.Trim.Both] + `includeFontPadding = false` で行箱の**外側の余白**は落とせるが、
 * それでも自然行高までしか縮まない。よって各要素の箱の高さは [Modifier.height] で明示して確定させる
 * （落とされるのは行間であって字面ではないので、字面が切れることはない）。
 * この様式は「箱の中で字面を中央に置く」役割＝ [LineHeightStyle.Alignment.Center] が本体。
 */
private val DetailInfoTextStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)

/**
 * ジャンルチップの枠線幅。
 * ⚠️ CSS の border は箱の外側に積まれるが、Compose の `Modifier.border` は要素の**内側**へ描いて寸法を
 * 増やさない。そのまま写すとチップが 14F+8 になり情報列の内訳（14F+10）が合わないので、枠ぶんを
 * padding へ足して総高 14F+((4+1)×2) を再現する（正本 `.genre-label` の 4px padding + 1px border と同値）。
 */
private val DetailChipBorderWidth = 1.dp

/**
 * 帯の地の固定窓。彩度は 14% 固定、明度は「栞紙より 5 ポイント沈めた値」。
 *
 * なぜ明度を定数 0.93 で直書きしないか: 正本モックはライト専用で、0.93 をそのまま焼くとセピア／ダークで
 * 白い帯になって破綻する。ライトの 93% の正体は**栞紙 #FBFAF8（L=97.8%）のちょうど 5 ポイント下**＝
 * 「書影の紙より一段沈んだ面の上に本が載っている」という関係そのものなので、値ではなく関係の側を写す。
 * これで各変種の栞紙（[com.novelreader.ui.theme.ShioriColors.paper]）から自動で導け、ライトは正本と同値 93% になる。
 */
private const val DetailBandSaturation = 0.14f
private const val DetailBandLightnessDrop = 0.05f

/**
 * スクロール示唆の印（印③・2026-08-26 ユーザー裁定）の寸法と濃度。
 *
 * ---- なぜ「端フェード」と「シェブロン」が2つで1組なのか（片方だけ実装しない）----
 * 端フェードは**下端にまだ本文があるときにしか効かない**。地色へ溶かす演出なので、fontScale 2.0 のように
 * 下端へ来るのが余白だと**地色に地色を重ねる**ことになって何も見えない。その段で「下へ続く」を言う役目は
 * シェブロンが負う——つまり **1.0 はフェード・2.0 はシェブロンが担当**していて、どちらを落としても
 * どちらかの fontScale で無言になる。逆に 1.0 では ⌄ がフェードの中に浮くので記号が地から浮きすぎない。
 *
 * ⌄ の色は青磁 `--seiji-ink`（[com.novelreader.ui.theme.ShelfColors.semanticMicroText]）の α.62＝
 * 「意味を運ぶ小さな記号」の既存スロット。ここで新色は作らない（ADR 0014 のトークン層）。
 */
private val DetailScrollHintFadeHeight = 36.dp
private val DetailScrollHintChevronSize = 22.dp
/** ⌄ と固定バーの間。フェード 36dp の中に ⌄ が収まる位置＝記号が地から浮きすぎない。 */
private val DetailScrollHintChevronGap = 10.dp
private const val DetailScrollHintFadeAlpha = 0.97f
private const val DetailScrollHintChevronAlpha = 0.62f

/**
 * なろうAPIの日付文字列（`general_lastup`＝"2024-01-05 12:34:56" 形式想定）を
 * 「2024年1月5日 更新」ラベルへ整形する。整形できなければ null（呼び出し側は表示を省く）。
 *
 * なぜ手書きパースか: 表示は年月日だけで足り、SimpleDateFormat 等でパース→再フォーマットする必要が無く、
 * また時刻・タイムゾーンの解釈も不要なため、先頭の日付部を "-" で割って和暦風表記へ組むだけで済む。
 *
 * なぜ NumberFormatException を握り潰すか: なろうAPIはこの日付文字列の形式を公式に保証しておらず、
 * 想定外の形（数値でない・欠損・区切り違い）が来うる。ここは付帯的なメタ表示であり、
 * 解釈できない値で画面を落とすより表示スキップに倒すのが妥当なため、数値変換失敗のみを捕えて null にする。
 * 握り潰す範囲は toInt() の失敗（NumberFormatException）に限定し、他の想定外例外は覆い隠さない。
 */
internal fun formatLastupLabel(raw: String?): String? {
    val datePart = raw?.split(" ")?.firstOrNull() ?: return null
    val ymd = datePart.split("-")
    if (ymd.size < 3) return null
    return try {
        val year = ymd[0].toInt()
        val month = ymd[1].toInt()
        val day = ymd[2].toInt()
        "${year}年${month}月${day}日 更新"
    } catch (e: NumberFormatException) {
        null
    }
}

/**
 * 作品詳細画面（モック discovery-detail-D.html）。
 * 書影ヒーロー表示、作者情報、作品ステータス、あらすじ、キーワード、評価項目などを
 * 和モダンの静謐なレイアウトで構築し、最下部に「なろうで読む」外部連携導線を常駐させる。
 */
/**
 * 作品詳細のルート層（state-holder / UI 分割の route）。
 * ViewModel の受け取り・詳細ロードの起動・状態の collect と、「なろうで読む」の外部ブラウザ起動という
 * プラットフォーム副作用（Custom Tabs＋二重起動ガード）を担い、純粋な描画は [NovelDetailContent] に委ねる
 * （BookshelfScreen と同じ分割方針。プラットフォーム副作用はルート層に置く＝描画層を VM/Context 非依存に保つ）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NovelDetailScreen(
    ncode: Ncode,
    viewModel: NovelDetailViewModel,
    onSearchKeywords: (List<String>) -> Unit,
    onImportPdf: () -> Unit,
    // 機能②: なろう作品をアプリ内 WebView で読む（読書位置の自動記録・ADR 0012）。外部ブラウザ送客(Custom Tabs)は
    // 廃し、目次(初回)と続きから(記録話へ直接)の2着地をルート層のナビへ委ねる（描画層は callback を叩くだけ）。
    onReadFromToc: () -> Unit,
    onResumeReading: (episode: Int) -> Unit,
    // 2026-09-04 裁定（案A）: 取込済みなら主CTA は手元の蔵書を開く。着地の作法（getLastRead ?: index.html）は
    // 本棚から開く既存経路と同一で、ルート層（MainActivity）が持つ＝この画面は bookId を渡すだけ。
    onOpenImportedBook: (bookId: String) -> Unit,
    // 作品詳細の ← は階層 up＝一段上の「直近の結果一覧」へ（発見ホーム直行入場だけは発見ホームへ）。
    // システム Back も同じ up で一本（MainActivity の BackHandler・分岐の機序は upFromDiscoveryDetail の KDoc）。
    // 旧「←＝発見ホーム固定 Up／Back＝履歴 pop」の二本立て（D 統一・2026-07-12）は 2026-07-29 ユーザー裁定
    // 「わかりやすく」で廃止＝読書側（章→目次→本棚）と同じ一規則（ADR 0026）。
    onUp: () -> Unit,
) {
    LaunchedEffect(ncode) {
        viewModel.load(ncode)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // (b) 固定バーのトグル表示状態（本棚に置く/外す・取込済みなら2アクション非表示）。
    val onShelf by viewModel.onShelf.collectAsStateWithLifecycle()
    // 取込済みなら蔵書の bookId（未取込は null）。判定（!= null）と着地先を同じ1つの状態から出す。
    val importedBookId by viewModel.importedBookId.collectAsStateWithLifecycle()
    // 機能②: この作品の WebView 読書位置（最後に開いた話。>0 なら「続きから読む」を出す）。
    val lastReadEpisode by viewModel.readingProgress.collectAsStateWithLifecycle()

    NovelDetailContent(
        uiState = uiState,
        onSearchKeywords = onSearchKeywords,
        onImportPdf = onImportPdf,
        onShelf = onShelf,
        isImported = importedBookId != null,
        onToggleShelf = { viewModel.toggleShelf() },
        onUp = onUp,
        onRetry = { viewModel.retry() },
        lastReadEpisode = lastReadEpisode,
        onReadOnNarou = onReadFromToc,
        onResumeReading = { onResumeReading(lastReadEpisode) },
        // 描画層へ id を渡さず、ルート層で捕まえた bookId を閉じ込める（onResumeReading が
        // lastReadEpisode を閉じ込めるのと同型＝描画層は VM/識別子を知らない葉のまま保つ）。
        onOpenImportedBook = { importedBookId?.let(onOpenImportedBook) },
    )
}

/**
 * 作品詳細の描画層（stateless / UI 分割の content）。NovelDetailScreen からの純移動。
 * VM や Context を持たず [uiState]＋コールバックだけで Loading/NotFound/Error/Content の分岐と
 * ヒーロー・ステータス・あらすじ・キーワード・評価・外部連携導線を描画する葉。スクロール追従の題字表示
 * といった画面ローカル UI 状態のみ内部に残す。外部ブラウザ起動は [onReadOnNarou]、再試行は [onRetry] へ委譲。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun NovelDetailContent(
    uiState: NovelDetailUiState,
    onSearchKeywords: (List<String>) -> Unit,
    onImportPdf: () -> Unit,
    onUp: () -> Unit,
    onRetry: () -> Unit,
    // 機能②: onReadOnNarou＝目次(最初から)をアプリ内 WebView で開く。onResumeReading＝記録した話へ直接（続きから）。
    // lastReadEpisode>0 のとき「続きから読む（第N話）」を主導線に切り替える。既定値は既存テスト・プレビュー互換のため。
    onReadOnNarou: () -> Unit,
    lastReadEpisode: Int = 0,
    onResumeReading: () -> Unit = {},
    // (b) Web由来カードの入口（固定バーのトグル）。既定値は既存テスト・プレビューの互換のため。
    onShelf: Boolean = false,
    isImported: Boolean = false,
    onToggleShelf: () -> Unit = {},
    // 案A: 取込済みの主CTA「アプリで読む」＝手元の蔵書を開く。既定値は既存テスト・プレビュー互換のため。
    onOpenImportedBook: () -> Unit = {},
) {
    // スクロール状態を最上位で保持する（M10/層②）。書影ヒーローと本文タイトルが画面外へ流れたら
    // App bar に作品名を常駐表示し、今どの作品を見ているかの手掛かりが消えないようにするため。
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    // 書影ブロックの寸法は fontScale から一意に決まる（案A＋T2）。閾値も情報列も**同じ1個**から引く。
    val heroMetrics = remember(density.fontScale) { detailHeroMetrics(density.fontScale) }
    // 情報列の題名が上端へ抜けたら App bar へ作品名を出す。案2-c では題名の位置が幾何で確定している
    // （帯の内側余白から題名の箱まで）ので、旧「ヒーロー高の6割」という当て推量をやめて
    // **題名の下辺そのもの**を閾値にする。構成要素を単一情報源として引くので寸法変更に自動追従する
    // （fontScale で題名の箱が伸びれば閾値も一緒に伸びる＝大きい文字設定で題字が早出しにならない）。
    val heroThresholdPx = remember(density, heroMetrics) {
        with(density) { (heroMetrics.bandInset + heroMetrics.titleBlockHeight).toPx() }
    }
    val showBarTitle by remember {
        derivedStateOf { scrollState.value > heroThresholdPx }
    }
    // なぜフェードか: 既存の同型演出（BookCard の animateFloatAsState）に倣い、出没を滑らかにして
    // スクロールに追従する題字のちらつきを抑える。
    // なぜ animationSpec を明示するか: 既定 spring だと Motion.kt を経由せず野良曲線になる（Design/08 禁止則②
    // ＝duration/easing はトークン経由）。復帰ヒント等と同型の「そっと現れて消える」演出のため crossfade
    // トークン（MotionDurationCrossfade）の tween で一元化する（監査 d-motion Minor）。
    val barTitleAlpha by animateFloatAsState(
        targetValue = if (showBarTitle) 1f else 0f,
        animationSpec = tween(MotionDurationCrossfade),
        label = "detailBarTitle"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // 書影が流れて本文タイトルが見えなくなったら作品名を出す（普段は透明で不可視）。
                    val barState = uiState
                    if (barState is NovelDetailUiState.Content) {
                        Text(
                            text = barState.novel.summary.title,
                            fontFamily = MinchoFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = FontTopBarTitle,
                            letterSpacing = 1.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.alpha(barTitleAlpha)
                        )
                    }
                },
                navigationIcon = {
                    // 階層 up（2026-07-29 一本化・ADR 0026）: 一段上＝直近の結果一覧へ（ホーム直行入場は発見ホームへ）。
                    // Back も同じ up（旧「固定 Up／履歴 pop」二本立て＝D 統一 2026-07-12 は廃止）。
                    IconButton(onClick = onUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "戻る"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (uiState is NovelDetailUiState.Content) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.S24, vertical = Spacing.S16)
                        ) {
                            // 機能②: 固定バーの読む/取り込み導線。案A「完全一貫」（2026-07-16 ユーザー裁定）＝
                            // アプリの主目的は「手元に本を置く」＝PDF取り込み。未取込である限り取込を藍の主CTA
                            // 最上段に固定し、既読になっても降格させない（旧 2026-07-12 の「既読は続きからを主」裁定を
                            // 上書き＝状態依存で主従が入れ替わる一貫性欠如を解消）。取込済みなら取込は冗長で消える。
                            // 意匠正本＝discovery-detail-D.html（既読パネルと同期）。
                            // なろう側の着地はいずれもアプリ内 WebView でページを **加工せず** 表示し話遷移から
                            // 読書位置を記録（ADR 0012）。
                            //
                            // ---- 案A（2026-09-04 裁定）: 取込済みの主CTA は手元の蔵書「アプリで読む」 ----
                            // ⚠️ 旧記載（2026-08-21 まで）＝「取込済みなら読む導線（なろう）を藍の主CTAへ昇格」。
                            // 真因は**作品詳細が ncode しか持たず bookId を持たなかった**こと＝取込済みなのに
                            // どのボタンもなろうの WebView へ出て、**この画面から手元の本へ行く道がゼロ**だった
                            // （経路B＝発見から入った人はバック3回で本棚へ戻るしかない）。
                            // ⚠️ **スロット数は増やさない**＝主従を入れ替えるだけなので 2026-08-21 の
                            // 「バー総高 137dp」を侵さない。むしろ取込済・未読が 81→137dp へ揃い、**4状態すべて 137dp**
                            // になる（総高 = 1+16+48n+8(n−1)+16）。「1つ足す」案は 193dp であらすじが推奨3行に接地する
                            // ため落とした。
                            // ⚠️ **なろう系は副へ降ろすが消さない**: 取り込んだ PDF は取込時点のスナップショットで
                            // 連載は続く。アプリ自身が NewEpisodeCheckWorker の「続きが N 話更新されています」通知を
                            // 出していて、**その行き先はなろうの WebView だけ**＝消すと自分の通知の着地を自分で塞ぐ。
                            //
                            // ---- 案B（2026-08-21 裁定・2026-08-26 に翻訳）: 副アクションは1行に横並べ ----
                            // 主CTA は全幅で最上段に固定したまま、**副アクションだけを畳んで1行に並べる**。
                            // ⚠️ **隠さずに畳む**のが要点＝オーバーフローメニューへの退避は「せっかくの導線を隠すのは
                            // 論外」として却下済み（ADR 0011「取込導線は無加工で毎回ユーザー操作」と同じ筋）。
                            // なぜ効くか: 3アクション版と4アクション版で**バーの総高が同じ 137dp** になる。
                            // 旧・縦積みは 229dp / 285dp と 56dp 差があり、**4アクション版だけあらすじが0行**へ落ちていた
                            // ＝「片方だけ直る案は使えない」というのが案B採用の決め手そのもの。
                            // ⚠️ この裁定は 2026-08-21 にモックへ入りながら Compose へ**翻訳されていなかった**
                            // （未翻訳の逆同期債務）。2026-08-26 の fontScale 裁定でここを閉じ、1.0 の版面が +27dp 戻る。
                            if (!isImported) {
                                // 未取込（既読/未読問わず）: 取込を藍の主CTA・最上段に固定（案A・2026-07-16）。
                                DetailPrimaryAction(
                                    icon = Icons.Filled.Download,
                                    label = "縦書きPDFを取り込む",
                                    onClick = onImportPdf,
                                )
                            } else {
                                // 取込済（既読/未読を問わず同一）: 主CTA は手元の蔵書を開く。
                                // ⚠️ 文言は「アプリで読む」1つで、未読/既読で振り分けない。理由は2つ:
                                //  ・着地の実装が getLastRead(bookId) ?: "index.html" の**1動作**（続きが在れば章・
                                //    無ければ目次）＝振り分ける先が実は無い。
                                //  ・「なろうで読む」と1軸2択の対比語彙になる（IntroDeck が既に〈アプリ／なろう〉で
                                //    語っており、新しい語を発明しない）。
                                DetailPrimaryAction(
                                    icon = Icons.AutoMirrored.Filled.MenuBook,
                                    label = "アプリで読む",
                                    onClick = onOpenImportedBook,
                                )
                            }

                            // 副アクション行。⚠️ **横に並ぶ数でラベルの丈を変える**（正本の3アクション版は
                            // 「なろうで読む」「本棚に置く」、4アクション版は「第13話から」「目次」「本棚」と縮む）。
                            // なぜ変えるか: 横並びでは1つあたりの幅が 1/n になるので、丈のあるラベルのまま3つ並べると
                            // fontScale 1.0 でも2行に折れ、案Bの要点である「バー総高が状態で変わらない」が壊れる。
                            //
                            // ---- アイコンの規則（2026-09-04 裁定）: 手元＝MenuBook ／ なろう＝外部リンク ----
                            // 案Aで主CTA が蔵書側（MenuBook）へ移るため、**なろう行きにも MenuBook を当てていると
                            // 「どちらが手元の本か」が絵で見分けられなくなる**。よって MenuBook は「アプリで読む」
                            // 専用にし、なろうの WebView へ出るボタンは全て OpenInNew（ContinuationCard で
                            // 既に使っている外部リンク記号＝新しい記号を発明しない）で揃える。
                            // ⚠️ これは案Aと同時の**逆同期の是正**でもある: 正本は「なろうで読む」を外部リンクで
                            // 描いているのに実装は MenuBook を当てていた（＝正本と実装が食い違っていた）。
                            when {
                                !isImported && lastReadEpisode > 0 -> DetailSubActionRow {
                                    // 4アクション版＝3つ横並び。ここだけラベルを縮める。
                                    DetailSubActionButton(
                                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                                        label = "第${lastReadEpisode}話から",
                                        onClick = onResumeReading,
                                    )
                                    DetailSubActionButton(
                                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                                        label = "目次",
                                        onClick = onReadOnNarou,
                                    )
                                    // (b) Web由来・未取込カードの入口。置いた後は「外す」へトグルし、押し直しで
                                    // 取り消せる（確認ダイアログ無し＝失うものが無く即座に戻せる操作のため）。
                                    DetailSubActionButton(
                                        icon = if (onShelf) Icons.Filled.BookmarkRemove else Icons.Filled.BookmarkAdd,
                                        label = if (onShelf) "外す" else "本棚",
                                        onClick = onToggleShelf,
                                    )
                                }
                                !isImported -> DetailSubActionRow {
                                    // 3アクション版＝2つ横並び。幅が半分あるので正本どおり丈のあるラベルで出す。
                                    DetailSubActionButton(
                                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                                        label = "なろうで読む",
                                        onClick = onReadOnNarou,
                                    )
                                    DetailSubActionButton(
                                        icon = if (onShelf) Icons.Filled.BookmarkRemove else Icons.Filled.BookmarkAdd,
                                        label = if (onShelf) "本棚から外す" else "本棚に置く",
                                        onClick = onToggleShelf,
                                    )
                                }
                                lastReadEpisode > 0 -> DetailSubActionRow {
                                    // 取込済・既読: 主CTA から降りたなろう系2つ（「本棚に置く」は取込済みでは
                                    // 冗長＝蔵書カードが正。モック .cap「取込済みなら取込とその周辺は冗長で消える」）。
                                    // ⚠️ **「なろうで」を前置する**: 主CTA が蔵書側へ移った瞬間、副の「第12話」が
                                    // **何の**話数か言えなくなる（手元の本の栞と、なろうの読書位置は別物）。
                                    // 実効幅 124dp（152dp − アイコン16 − アキ4 − 内側 padding8）に対し
                                    // 「なろうで第12話から」＝103.5dp、4桁話数でも 115dp で1行に収まる（正本の算式で検算）。
                                    DetailSubActionButton(
                                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                                        label = "なろうで第${lastReadEpisode}話から",
                                        onClick = onResumeReading,
                                    )
                                    DetailSubActionButton(
                                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                                        label = "なろうの目次",
                                        onClick = onReadOnNarou,
                                    )
                                }
                                else -> DetailSubActionRow {
                                    // 取込済・未読: 降りたなろう系は1つだけ（行の全幅を使えるので丈は縮めない）。
                                    // ⚠️ この行を出すことで取込済・未読も 2スロット＝137dp になり、**4状態が揃う**。
                                    DetailSubActionButton(
                                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                                        label = "なろうで読む",
                                        onClick = onReadOnNarou,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is NovelDetailUiState.Loading -> {
                    DiscoveryStatusBox(DiscoveryStatus.Loading, modifier = Modifier.fillMaxSize())
                }
                is NovelDetailUiState.NotFound -> {
                    DiscoveryStatusBox(
                        DiscoveryStatus.Empty("作品が見つかりませんでした（削除または検索除外の可能性）"),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                is NovelDetailUiState.Error -> {
                    DiscoveryStatusBox(
                        DiscoveryStatus.Error(state.message, onRetry = onRetry),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                is NovelDetailUiState.Content -> {
                    val novel = state.novel
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    ) {
                        // 書影ブロック（案2-c）。作者・ジャンルは帯の中の情報列へ吸収済み＝
                        // 旧 `.detail-meta-top` 行はここには無い（線引きを「帯の中＝書影と、その作品の名指し」で
                        // 一度に通す。チップだけ動かして題名が半端に残る事故を防ぐための一括移動）。
                        DetailCoverBlock(
                            title = novel.summary.title,
                            author = novel.summary.author,
                            genreLabel = NarouGenres.genreLabel(novel.summary.genreCode),
                            metrics = heroMetrics,
                        )

                        // ステータス表（2列グリッド）
                        val statusText = novelStatusLabel(novel.summary)
                        val length = novel.summary.lengthChars
                        val lengthText = if (length != null) {
                            if (length >= 10000) {
                                String.format(Locale.JAPAN, "（%.1f万字）", length / 10000.0)
                            } else {
                                String.format(Locale.JAPAN, "（%,d字）", length)
                            }
                        } else {
                            ""
                        }
                        val readTime = readTimeLabel(novel.summary) ?: "—"
                        val readTimeVal = if (length != null) "$readTime$lengthText" else readTime

                        // mock .status-grid: border-top/bottom 1px var(--line) の外郭ヘアライン＋
                        // :nth-child(odd) の border-right ＝中央縦罫を持つ2×2表。行の縦罫を全高に届かせるため
                        // Row を IntrinsicSize.Min で包み VerticalDivider を fillMaxHeight で立てる。
                        // セル余白も mock .status-item（padding:12px 0・odd padding-right:16px・even padding-left:16px）
                        // の写経＝旧実装の「中罫だけ・外郭なし」はモック逆同期 2026-07-31 で検出されたズレ。
                        StatusGridRow2x2(
                            topLeft = "状態" to statusText,
                            topRight = "読了目安" to readTimeVal,
                            bottomLeft = "会話率" to (novel.kaiwaritu?.let { "$it%" } ?: "—"),
                            bottomRight = "挿絵" to (novel.sasieCnt?.let { "${it}枚" } ?: "—"),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = Spacing.S16)
                                .padding(horizontal = Spacing.S24)
                        )

                        // あらすじセクション
                        if (!novel.story.isNullOrEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.S24)
                            ) {
                                Text(
                                    text = "あらすじ",
                                    fontSize = FontMicroLabel,
                                    letterSpacing = 3.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold,
                                    // mock .sec{margin:24px 0 12px}: 見出し下は 12px（旧 S8 はモック逆同期 2026-07-31 で検出されたズレ）
                                    modifier = Modifier.padding(top = Spacing.S24, bottom = Spacing.S12)
                                )
                                Text(
                                    text = novel.story,
                                    fontFamily = MinchoFamily,
                                    fontSize = FontBody,
                                    lineHeight = 26.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // キーワードセクション
                        val keywords = remember(novel.keyword) {
                            novel.keyword?.split(Regex("[\\s　]+"))?.filter { it.isNotEmpty() } ?: emptyList()
                        }
                        if (keywords.isNotEmpty()) {
                            // 複数選択（フィードバック2）: チップをトグル選択式にする。選択状態は画面ローカルで足り、
                            // 画面離脱でのリセットは自然挙動として許容する。構成変更（回転・ダーク切替）では
                            // 維持したいので rememberSaveable。SnapshotStateList に既製 saver が無いため listSaver で
                            // トークン一覧を保存/復元する（Set 意味論は contains 判定で担保）。
                            val selectedKeywords = rememberSaveable(
                                saver = listSaver(
                                    save = { it.toList() },
                                    restore = { it.toMutableStateList() }
                                )
                            ) { emptyList<String>().toMutableStateList() }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.S24)
                            ) {
                                Text(
                                    text = "キーワード",
                                    fontSize = FontMicroLabel,
                                    letterSpacing = 3.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold,
                                    // mock .sec{margin:24px 0 12px}: 見出し下は 12px（旧 S8 はモック逆同期 2026-07-31 で検出されたズレ）
                                    modifier = Modifier.padding(top = Spacing.S24, bottom = Spacing.S12)
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.S8),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.S8),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    keywords.forEach { keyword ->
                                        val selected = keyword in selectedKeywords
                                        // A11y（F-P/Android §C）: 枠線チップの見た目は現寸のまま、
                                        // タップ判定だけ最小48dpへ拡げる。外側の透明Boxを clickable+sizeIn にし、
                                        // 内側の枠線チップは元の寸法で中央寄せする（外側Box分離＝NcodeLinkSheet と同型）。
                                        Box(
                                            modifier = Modifier
                                                .clickable {
                                                    // トグル。List を Set 意味論で扱うため contains で分岐し重複追加を防ぐ。
                                                    if (selected) selectedKeywords.remove(keyword)
                                                    else selectedKeywords.add(keyword)
                                                }
                                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    // 選択中は primary 反転（塗り）で示す。未選択は従来の secondary 枠線のまま。
                                                    .then(
                                                        if (selected) {
                                                            Modifier.background(
                                                                MaterialTheme.colorScheme.primary,
                                                                RoundedCornerShape(2.dp)
                                                            )
                                                        } else {
                                                            Modifier
                                                        }
                                                    )
                                                    .border(
                                                        width = 1.dp,
                                                        color = if (selected) {
                                                            MaterialTheme.colorScheme.primary
                                                        } else {
                                                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                                                        },
                                                        shape = RoundedCornerShape(2.dp)
                                                    )
                                                    .padding(horizontal = Spacing.S12, vertical = Spacing.S4)
                                            ) {
                                                Text(
                                                    text = keyword,
                                                    fontSize = FontLabel,
                                                    // 未選択のキーワードは検索語を名指す＝意味を運ぶ文字で
                                                    // AA(4.5:1) が要る。青磁 secondary #9CB3A8 は素地 2.14:1 で
                                                    // 未達＝ADR 0014-D の濃青磁へ寄せる（枠線は装飾のため据置）。
                                                    color = if (selected) {
                                                        MaterialTheme.colorScheme.onPrimary
                                                    } else {
                                                        LocalShelfColors.current.semanticMicroText
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                                // 1件以上選択されたら、まとめて検索するアクションを出す（primary）。
                                if (selectedKeywords.isNotEmpty()) {
                                    // A11y（F-P/Android §C）: 上のキーワードチップと同様、見た目は文字行のまま
                                    // タップ判定を最小48dpへ確保する（外側Box分離＝同セクションのチップと同型）。
                                    Box(
                                        modifier = Modifier
                                            .padding(top = Spacing.S4)
                                            .fillMaxWidth()
                                            .clickable { onSearchKeywords(selectedKeywords.toList()) }
                                            .sizeIn(minHeight = 48.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = "選択した ${selectedKeywords.size} 件のキーワードで検索",
                                            fontSize = FontButtonLabel,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                }
                            }
                        }

                        // 評価セクション
                        val evalItems = remember(novel) {
                            listOf(
                                "総合評価" to novel.summary.points?.global?.let { String.format(Locale.JAPAN, "%,d pt", it) },
                                "ブックマーク" to novel.favNovelCnt?.let { String.format(Locale.JAPAN, "%,d 件", it) },
                                "評価者数" to novel.allHyokaCnt?.let { String.format(Locale.JAPAN, "%,d 人", it) },
                                "週間ポイント" to novel.summary.points?.weekly?.let { String.format(Locale.JAPAN, "%,d pt", it) }
                            ).filter { it.second != null }
                        }
                        if (evalItems.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.S24)
                            ) {
                                Text(
                                    text = "評価",
                                    fontSize = FontMicroLabel,
                                    letterSpacing = 3.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold,
                                    // mock .sec{margin:24px 0 12px}: 見出し下は 12px（旧 S8 はモック逆同期 2026-07-31 で検出されたズレ）
                                    modifier = Modifier.padding(top = Spacing.S24, bottom = Spacing.S12)
                                )
                                evalItems.forEachIndexed { index, pair ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = Spacing.S12),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = pair.first,
                                            fontSize = FontChipLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = pair.second!!,
                                            fontSize = FontSubTitle,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    if (index < evalItems.lastIndex) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                }
                            }
                        }

                        // 最終更新表示
                        val lastupText = remember(novel.generalLastup) {
                            formatLastupLabel(novel.generalLastup)
                        }
                        // 取得時刻の表示（M6/公理5 SSOT）。
                        // なぜ出すか: この画面は一覧値の写しではなく詳細APIで取り直した最新値を単一の真実として表示している。
                        // 「いつ時点の情報か」を明示することで、別取得の一覧値と食い違って見えても出所を判別できるようにする。
                        val fetchedAtText = remember(state.fetchedAtMillis) {
                            val time = java.text.SimpleDateFormat("HH:mm", Locale.JAPAN)
                                .format(java.util.Date(state.fetchedAtMillis))
                            "$time 時点の情報"
                        }
                        val metaText = if (lastupText != null) {
                            "$lastupText ・ $fetchedAtText"
                        } else {
                            fetchedAtText
                        }
                        Text(
                            text = metaText,
                            fontSize = FontMicroLabel,
                            // 最終更新+取得時刻は情報を運ぶ文字＝infoText（AA 4.5:1・ADR 0014-D 裁定で装飾用と分離）。
                            color = LocalShelfColors.current.infoText,
                            modifier = Modifier
                                .padding(horizontal = Spacing.S24)
                                // mock .last-updated{margin-top:16px}（旧 S24 はモック逆同期 2026-07-31 で検出されたズレ。
                                // 下 S24 はモック外＝スクロール末尾の余白でそのまま維持）
                                .padding(top = Spacing.S16, bottom = Spacing.S24)
                        )
                    }

                    // ---- スクロール示唆の印＝端フェード ＋ シェブロン（印③・2026-08-26 ユーザー裁定）----
                    // なぜ要るか: この画面は fontScale が上がるほど初期ビューポートから本文が消え、2.0 では
                    // あらすじが0行になる（受諾済み）。「まだ下に続く」ことだけは**版面を1dpも使わずに**言う。
                    // なぜ版面を食わないか: どちらも Box への align 配置＝兄弟であるスクロール列の測定に
                    // 一切入らない（印を足した代償に本文が減る、という取引をしていない）。さらに
                    // pointerInput を持たないのでヒットテストにも入らず、印の上をなぞってもスクロールは
                    // そのまま下の列へ届く（CSS の `pointer-events:none` に相当）。
                    // なぜ canScrollForward で出し分けるか: 出しっぱなしにすると**下端に着いても「まだ続く」と
                    // 嘘をつく**。derivedStateOf で包むのは、スクロール値そのものではなく真偽が変わった
                    // ときだけ再合成させるため（毎フレーム再合成すると印のためにスクロールが重くなる）。
                    val showScrollHint by remember {
                        derivedStateOf { scrollState.canScrollForward }
                    }
                    // 出没は App bar の題字フェードと同じ crossfade トークンで揃える（既定 spring だと
                    // Motion.kt を経由しない野良曲線になる＝Design/08 禁止則②）。
                    val scrollHintAlpha by animateFloatAsState(
                        targetValue = if (showScrollHint) 1f else 0f,
                        animationSpec = tween(MotionDurationCrossfade),
                        label = "detailScrollHint"
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(DetailScrollHintFadeHeight)
                            .alpha(scrollHintAlpha)
                            .background(
                                // 地色そのものへ溶かす（α0 → α.97）。テーマの background から引くので
                                // ライト／セピア／ダークのどれでも「その面の地色」になる。
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.background.copy(alpha = 0f),
                                        MaterialTheme.colorScheme.background
                                            .copy(alpha = DetailScrollHintFadeAlpha),
                                    )
                                )
                            )
                    )
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        // 読み上げには出さない: 「下へ続く」はスクロール可能性そのものが
                        // TalkBack へ既に伝わっている情報で、印は目で見る人のための冗長表現だから。
                        contentDescription = null,
                        tint = LocalShelfColors.current.semanticMicroText
                            .copy(alpha = DetailScrollHintChevronAlpha),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = DetailScrollHintChevronGap)
                            .size(DetailScrollHintChevronSize)
                            .alpha(scrollHintAlpha)
                    )
                }
            }
        }
    }
}

/**
 * ステータス2×2表（mock .status-grid の翻訳）。
 * 外郭＝border-top/bottom 1px var(--line)、1行目下＝:nth-child(1),(2) の border-bottom、
 * 中央縦罫＝:nth-child(odd) の border-right。縦罫を行の全高に届かせるため、各行を
 * Row(IntrinsicSize.Min) で包み VerticalDivider を fillMaxHeight() で立てる（Compose に
 * CSS grid の子境界線に対応する既製要素が無いための等価構成）。
 * セル＝mock .status-item（padding:12px 0・odd は padding-right:16px・even は padding-left:16px・
 * ラベル .lbl と値 .val の縦積み gap 4px）。
 */
// レイアウト回帰テストが寸法を名指しで掴むためのタグ（「情報列が帯の中に上下対称で据わる」は目視でなく
// 数で守る対象＝正本が数で規定している以上、守りも数でなければ静かに崩れる）。
// ⚠️ 2026-08-26 の案A＋T2 で期待値が動いた（帯 120→74F+46・情報列 96→76F+22・題名 44→46F・
// 内側余白 12→12−F）＝ NovelDetailCoverBlockLayoutTest の数値も同時に更新しないと赤くなる。
internal const val DetailCoverBlockTag = "detail_cover_block"
internal const val DetailCoverBandTag = "detail_cover_band"
internal const val DetailCoverCardTag = "detail_cover_card"
internal const val DetailInfoColumnTag = "detail_info_column"
internal const val DetailInfoTitleTag = "detail_info_title"
internal const val DetailInfoAuthorTag = "detail_info_author"
internal const val DetailInfoChipTag = "detail_info_chip"

/**
 * 作品詳細の書影ブロック＝案2-c「淡地」（意匠正本 discovery/discovery-detail-D.html）。
 *
 * 版面（fontScale 1.0。1dp = 正本の1px。他の段は [detailHeroMetrics] の式が決める）:
 * ```
 *   y0                                          帯（作品色の淡地・全幅・高さ 74·F+46）
 *   y11   ┌ 書影 114×152 ┐   ┌ 情報列 98 ────┐   ← 左24(S24) / 列左154 / 右余白24(S24)
 *   y12   │  ↑カードだけ  │   │               │   ← カードの上端は S12 固定（追従しない）
 *   y109  │  上端 12 固定 │   └───────────────┘   ← 帯の内側余白 11dp（上と同値＝上下対称）
 *   y120  │              │   ────────────────────  帯の境界（ヘアライン）
 *   y164  └──────────────┘                        ← カードだけが境界を 44dp 越える（意図）
 *   y166  ブロック下端
 * ```
 * ⚠️ 内側余白が 12 でなく **11** なのは T2（題名の箱 44→46）の +2dp を帯を太らせずに吸収したため
 * （[detailHeroMetrics] の KDoc）。上下対称は保たれている。
 *
 * @param title 書影のシードでもある（栞書影は**題名**から決定論生成＝本棚と1ピクセル同じ絵になる）。
 *   旧 BookCover は ncode をシードにしていたので、同じ作品が本棚と詳細で別の顔になっていた。
 * @param metrics fontScale から起こした寸法一式（案A＋T2）。呼び出し側と閾値計算で**同じ値**を使うため引数で渡す。
 */
@Composable
private fun DetailCoverBlock(
    title: String,
    author: String,
    genreLabel: String?,
    metrics: DetailHeroMetrics,
    modifier: Modifier = Modifier,
) {
    val shiori = LocalShioriColors.current
    // 帯の地。作品色の色相だけを借り、彩度14%・明度は栞紙より5ポイント下へ圧縮する（定数の why は上）。
    val bandColor = remember(title, shiori.paper) {
        val paper = shiori.paper
        // HSL の明度＝(max+min)/2。Compose の Color は HSL を持たないので RGB から起こす。
        val paperLightness =
            (maxOf(paper.red, paper.green, paper.blue) + minOf(paper.red, paper.green, paper.blue)) / 2f
        // 1%刻みへ丸めるのは正本が `hsl(h 14% 93%)` と整数%で書かれているため。
        // ライトは 97.8−5=92.8 → 93% ＝正本の実値とビット単位で一致する。
        val lightness = (((paperLightness - DetailBandLightnessDrop) * 100f).roundToInt() / 100f)
            .coerceIn(0f, 1f)
        hslToColor(shioriHue(title).toFloat(), DetailBandSaturation, lightness)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(metrics.blockHeight)
            .testTag(DetailCoverBlockTag),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(metrics.bandHeight)
                .background(bandColor)
                .testTag(DetailCoverBandTag),
        )
        // 帯の境界（正本 `border-bottom:1px rgba(28,31,38,.10)`）。box-sizing:border-box なので
        // 罫は帯の**内側**最下段に載る＝オフセットは（帯の高さ）−1（帯が fontScale で伸びれば一緒に下がる）。
        HorizontalDivider(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(y = metrics.bandHeight - 1.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
        )
        // 書影カード。本棚グリッドと同じ ShioriCover を題名で呼ぶだけ＝生成側へは何も要求しない。
        // shadow は clip より前＝影を外周へ落としてから角丸で本体をクリップする（本棚カードと同じ作法）。
        ShioriCover(
            title = title,
            modifier = Modifier
                .align(Alignment.TopStart)
                // ⚠️ カードの上端は **S12 固定**（metrics.bandInset ではない）＝カードは dp 固定で
                // fontScale に追従しないため。下端は常に 164dp で、帯が伸びるぶん「越え」だけが痩せる。
                .offset(x = Spacing.S24, y = DetailCoverTopInset)
                .size(width = DetailCoverWidth, height = DetailCoverHeight)
                .shadow(
                    elevation = shiori.coverShadowElevation,
                    shape = RoundedCornerShape(2.dp),
                )
                .clip(RoundedCornerShape(2.dp))
                .testTag(DetailCoverCardTag),
        )
        // 情報列。高さを確定させるのは飾りでなく上下対称の根拠そのもの
        // （内側余白 + 情報列 + 内側余白 = 帯 が恒等的に成り立つ＝(12−F) + (76F+22) + (12−F) = 74F+46）。
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(
                    start = DetailInfoColumnStart,
                    top = metrics.bandInset,
                    end = Spacing.S24,
                )
                .height(metrics.infoColumnHeight)
                .testTag(DetailInfoColumnTag),
            verticalArrangement = Arrangement.spacedBy(DetailInfoGap),
        ) {
            // 題名。箱を高さで固定するので **1行の題名でも箱は 46·F**＝情報列の高さが内容に依らない
            // （高さを与えないと短題名で列が縮み、上下対称が題名の長さ次第で崩れる）。
            Text(
                text = title,
                fontFamily = MinchoFamily,
                fontSize = FontSectionTitle,
                fontWeight = FontWeight.SemiBold,
                lineHeight = DetailTitleLineHeight,
                style = DetailInfoTextStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                // 箱を 46·F で固定する＝題名が1行でも2行でも列の埋まり方が変わらない（内容非依存の要）。
                // ⚠️ 46 は「2行 45.5dp に 0.5dp の余り」＝**2行目を本当に描かせるための値**（T2）。
                // 旧 44dp では 1.5dp 足りず、maxLines=2 と書いてあるのに2行目が落ちていた。
                // minLines=2 では代用にならない: Compose は「minLines の合成高」と「実際に2行組んだ高さ」を
                // 別々に計算するため、実測で 41dp / 45.5dp と食い違った。
                modifier = Modifier
                    .height(metrics.titleBlockHeight)
                    .testTag(DetailInfoTitleTag),
            )
            // 作者は1行 nowrap + ellipsis。折り返すと情報列が伸びて帯からはみ出す（正本が写し取り損ねて
            // いた拘束で、実装側が先に正しかった箇所＝2026-08-21 にモックを実装へ合わせた）。
            Text(
                text = author,
                fontSize = FontButtonLabel,
                fontWeight = FontWeight.Medium,
                lineHeight = DetailAuthorLineHeight,
                style = DetailInfoTextStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(metrics.authorBlockHeight)
                    .testTag(DetailInfoAuthorTag),
            )
            if (genreLabel != null) {
                Text(
                    text = genreLabel,
                    fontSize = FontChipLarge,
                    letterSpacing = 0.5.sp,
                    lineHeight = DetailChipLineHeight,
                    style = DetailInfoTextStyle,
                    // 分類を名指す＝意味を運ぶ文字なので AA(4.5:1)。枠線は装飾のため青磁のまま据置
                    // （淡地の上でも意味は文字が運ぶ＝ADR 0014-D の切り分け）。
                    color = LocalShelfColors.current.semanticMicroText,
                    // タグは固定語彙＝改行縦積みを禁じて常に横一列で出す。
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        // testTag は枠の**外側**へ置く（padding より後ろに置くと semantics の bounds が
                        // padding の内側＝字面の箱になり、回帰テストがチップ総高 24dp でなく 14dp を測る）。
                        .testTag(DetailInfoChipTag)
                        .border(
                            width = DetailChipBorderWidth,
                            color = MaterialTheme.colorScheme.secondary,
                            shape = RoundedCornerShape(2.dp),
                        )
                        .padding(
                            horizontal = Spacing.S8 + DetailChipBorderWidth,
                            vertical = Spacing.S4 + DetailChipBorderWidth,
                        )
                        // 字面の箱を 14·F に固定＝padding 5×2 と合わせてチップ総高 14F+10（F=1 で 24dp）。
                        // ⚠️ padding と枠は字ではないので追従させない＝これが帯の式の「+46」側の一部。
                        .height(metrics.chipTextHeight),
                )
            }
        }
    }
}

/**
 * 固定バーの主CTA（藍・全幅・最上段）。案A「完全一貫」で**未取込である限り取込がここに居座る**。
 * 可視高は M3 既定の 40dp、タップ標的は Surface が 48dp へ広げる＝正本の「スロット48／ボタン可視40」と同値。
 */
@Composable
private fun DetailPrimaryAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        ),
        shape = RoundedCornerShape(2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(Spacing.S8))
        Text(
            text = label,
            fontSize = FontActionLabel,
            letterSpacing = 1.5.sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 案B の副アクション行（横一列）。⚠️ **行を増やさないのが唯一の目的**なので、ここに縦積みの分岐を
 * 足さないこと（足した瞬間に「3アクション版と4アクション版でバー総高が同じ」という案Bの要点が壊れ、
 * 4アクション版だけあらすじが0行に落ちる旧構成へ戻る）。
 */
@Composable
private fun DetailSubActionRow(content: @Composable RowScope.() -> Unit) {
    // スロット間 8dp（正本 .slot + .slot{margin-top:8px}）。
    Spacer(modifier = Modifier.height(Spacing.S8))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S8),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * 副アクション1つ（正本 `.btn-ghost.mini`）。枠線のゴーストで、行の中を**等幅で分け合う**。
 *
 * なぜ主CTA より字を落とすか（[FontActionLabel] 15sp → [FontChipLarge] 11.5sp）: 横並びでは1つあたりの
 * 幅が 1/n になるので、主CTA と同じ字面のままだと fontScale 1.0 でも2行に折れてバー高が状態依存になる
 * （正本も `.btn-ghost.mini` として一段落とした字面を規定している）。
 * ⚠️ 行数は縛らない＝fontScale 2.0 では折り返して2行になり、バーはそのぶん伸びる（受諾済みの挙動。
 * `maxLines=1` で省略記号に倒すと、大きい文字設定の人にだけ導線名が読めなくなる）。
 */
@Composable
private fun RowScope.DetailSubActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(2.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        // 既定の左右 24dp は横一列では効きすぎる（正本 .btn-ghost に左右 padding は無く、
        // 字とアイコンが flex の中で中央に据わるだけ）＝字が入る幅を padding へ渡さない。
        // ⚠️ 4dp まで削るのは 3つ横並び（4アクション版）が効く条件だから: 360dp 幅で1つ 98.7dp、
        // 枠内の余白8＋アイコン16＋アキ4 を引くと字に使えるのは 70.7dp ＝ 11.5sp なら全角6字強。
        // 「第123話から」（3桁話数）が1行に収まるのはここまで削った場合だけで、S8 だと折り返す。
        // 4桁話数では折り返して行が伸びる＝そこは受諾（省略記号で話数を隠すより折れるほうがまし）。
        contentPadding = PaddingValues(horizontal = Spacing.S4, vertical = Spacing.S4)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        // 正本 `.btn-ghost.mini{gap:4px}`（主CTA の 8dp より詰める＝字の幅を稼ぐ）。
        Spacer(modifier = Modifier.width(Spacing.S4))
        Text(
            text = label,
            fontSize = FontChipLarge,
            // 正本 .btn-ghost.mini{letter-spacing:.06em} ＝ 11.5×0.06 ≒ 0.7sp。
            letterSpacing = 0.7.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StatusGridRow2x2(
    topLeft: Pair<String, String>,
    topRight: Pair<String, String>,
    bottomLeft: Pair<String, String>,
    bottomRight: Pair<String, String>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        StatusGridRow(left = topLeft, right = topRight)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        StatusGridRow(left = bottomLeft, right = bottomRight)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** [StatusGridRow2x2] の1行（左右セル＋中央縦罫）。 */
@Composable
private fun StatusGridRow(
    left: Pair<String, String>,
    right: Pair<String, String>,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // なぜ IntrinsicSize.Min: 縦罫（VerticalDivider）は fillMaxHeight で行の高さに追従させるが、
            // 行の高さ自体はセル内容から決めたい。Min 指定でセルの必要最小高＝行高になり、縦罫だけが伸びる。
            .height(IntrinsicSize.Min)
    ) {
        StatusGridCell(
            label = left.first,
            value = left.second,
            modifier = Modifier
                .weight(1f)
                .padding(top = Spacing.S12, bottom = Spacing.S12, end = Spacing.S16)
        )
        VerticalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.fillMaxHeight()
        )
        StatusGridCell(
            label = right.first,
            value = right.second,
            modifier = Modifier
                .weight(1f)
                .padding(top = Spacing.S12, bottom = Spacing.S12, start = Spacing.S16)
        )
    }
}

/** [StatusGridRow2x2] の1セル（.status-item: .lbl 上・.val 下・gap 4px。タイポは旧インライン実装から不変）。 */
@Composable
private fun StatusGridCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = FontMicroLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = FontSubTitle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.S4)
        )
    }
}
