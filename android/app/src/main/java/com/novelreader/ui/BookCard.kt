package com.novelreader.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelreader.data.BookEntity
import com.novelreader.data.ProgressEntity
import com.novelreader.discovery.model.WorkSummary
import com.novelreader.narou.ContinuationInfo
import com.novelreader.narou.computeContinuation
import com.novelreader.ui.components.ShioriCover
import com.novelreader.ui.components.shioriAccentFor
import com.novelreader.ui.components.shioriHue
import com.novelreader.ui.theme.FontCardTitle
import com.novelreader.ui.theme.FontLabel
import com.novelreader.ui.theme.FontMicroLabel
import com.novelreader.ui.theme.FontMissingBadge
import com.novelreader.ui.theme.FontSealBadge
import com.novelreader.ui.theme.LocalShelfColors
import com.novelreader.ui.theme.LocalShioriColors
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.MotionDurationSeal
import com.novelreader.ui.theme.MotionEasingSeal
import com.novelreader.ui.theme.MotionSpringCard
import com.novelreader.ui.theme.ShioriSealScrimDark
import com.novelreader.ui.theme.ShioriSealVermilion
import com.novelreader.ui.theme.ShioriSealVermilionDark
import com.novelreader.domain.ReadingStatus
import com.novelreader.domain.chapterNumberOf
import com.novelreader.domain.progressFractionFor
import com.novelreader.domain.readingStatusFor
import com.novelreader.domain.relativeReadLabel
import com.novelreader.ui.theme.Spacing

// 朱印バッジの左下オフセット（拡張7段スケール外の構造値）。正本 grid-D .seal は left/bottom を 9px の
// 絶対配置で保持し、finished-seal-stamp-D も「19dp/左下9」で正本と同一と明記＝バッジ幾何の不変条件。
// 一括離散化だと 9→8 に潰れこの明文パリティを破るため、S8 へ丸めず 9dp を較正値として保持する。
private val SealCornerOffset = 9.dp

// ============================================================
// 進捗行（モック .pr）: 「N話 + 細い藍バー + N%」を藍で、未読は青磁で表示。
// グリッド=バー伸縮(flexBar=true) / リスト=バー80dp固定(false)。
// 色は token 経由（primary=藍 #1C3D5A / secondary=青磁 #9CB3A8 / track=outlineVariant）＝直書き回避。
// ============================================================
@Composable
private fun BookProgressRow(
    totalChaps: Int,
    progressFraction: Float?,
    flexBar: Boolean,
    modifier: Modifier = Modifier,
) {
    // 進捗の有無で描画ルート（Row/Text）が分岐するため、呼び出し側の modifier は
    // 実際に描画される側のルートへ適用する（どちらが出ても配置指定が効くように）。
    if (progressFraction != null) {
        val percent = (progressFraction * 100).toInt()
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${totalChaps}話",
                fontSize = FontLabel,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(Spacing.S8))
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = (if (flexBar) Modifier.weight(1f) else Modifier.width(80.dp))
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = LocalShelfColors.current.hairline,   // 本棚系 --track
            )
            Spacer(Modifier.width(Spacing.S8))
            Text(
                text = "$percent%",
                fontSize = FontLabel,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    } else {
        // 未読は濃青磁 UnreadSeiji。意味を運ぶ文字は 4.5:1 が最低線（ADR 0014-D 裁定＝旧・完全準拠トレードオフを上書き）。
        Text(
            text = "未読",
            modifier = modifier,
            fontSize = FontLabel,
            letterSpacing = 0.8.sp,
            color = LocalShelfColors.current.unreadLabel,
        )
    }
}

// ============================================================
// 最後に読んだ相対時刻「◯日前」（continuity Minor・モック未表現＝最終ユーザー確認バッチ対象）。
// よみかけ（progressFraction != null＝進捗あり）のカードにだけ、補助色で静かに1行添える。
// なぜカード内で now を都度取るか: 表示専用の粗い日粒度ラベルで、再コンポーズ時の微小なズレは無害。
// ============================================================
@Composable
private fun RelativeReadLabel(
    progressFraction: Float?,
    lastReadAt: Long,
    modifier: Modifier = Modifier,
) {
    if (progressFraction == null) return
    val label = relativeReadLabel(lastReadAt, System.currentTimeMillis()) ?: return
    Text(
        text = label,
        modifier = modifier,
        fontSize = FontMicroLabel,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ============================================================
// 続きありバッジ（モック fusion .new-chapters）: 青磁ドット＋藍文字「続き N話」。
// PDF↔Web継続読書の「新着の気配」を本棚でも静かに知らせる。
// ============================================================
// internal 昇格＝K の圧縮S目録カード（KListBookCard）でもこの続きバッジを共有するため
// （CardMenuButton / SelectionCheck を internal 化した先例と同じ・再実装を避ける）。
@Composable
internal fun NewChaptersBadge(newCount: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(MaterialTheme.colorScheme.secondary, CircleShape),
        )
        Spacer(Modifier.width(Spacing.S4))
        Text(
            text = "続き ${newCount}話",
            fontSize = FontMicroLabel,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

// ============================================================
// 欠落バッジ「本文なし」（案B・正本 bookshelf-reimport-badge-D .miss）: 書影左下のヘアラインチップ。
// ink-soft＝一画面一強調（藍は復旧ダイアログの実行ボタンに1点集中）を守り藍を使わない。
// 左下＝栞棒（上辺起点）と縦題字（右辺）のどちらとも重ならない静かな隅（朱印『了』と同隅だが、
// 欠落カードは了印を出さない＝呼び出し側で排他。実体を失った本に読了の徴を重ねない）。
// internal 共有＝K のカード（BookshelfK）も同じバッジを使う（NewChaptersBadge の internal 昇格と同流儀）。
// ============================================================
@Composable
internal fun MissingContentBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            // モック .miss: background=--base（棚地と同じ紙色）＝colorScheme.background。surface でないのは
            // K/D とも棚面の地が background トークンで塗られているため（バッジを地色で書影から浮かせる意匠）。
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, LocalShelfColors.current.hairline, RoundedCornerShape(2.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            text = "本文なし",
            fontSize = FontMissingBadge,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.7.sp, // モック .08em ≒ 9px×0.08
            color = LocalShelfColors.current.infoText,
        )
    }
}

/**
 * 手元PDFの章数（totalChaps）となろう詳細（novelDetail）を突き合わせ、新着話数を返す。null=バッジ非表示。
 *
 * なぜ Repository を直接叩かず引数の novelDetail を使うか（アーキ監査残課題1）:
 * 以前はカードごとに produceState で novelApiRepository.novelDetail を直撃しており、カード枚数ぶん
 * Repository を直撃＝テスト不能・本棚を開くたび並列発火だった。照会は BookshelfViewModel へ一括で吊り上げ、
 * カードは配布された詳細を受け取って突き合わせるだけの純粋関数に落とした（通信・キャッシュ・失敗握り潰しは VM 側）。
 * これは純 Kotlin の純粋計算なので Compose 非依存で単体テストできる。
 */
internal fun newEpisodeCountFor(novelDetail: WorkSummary?, totalChaps: Int): Int? {
    if (novelDetail == null || totalChaps <= 0) return null
    return (computeContinuation(totalChaps, novelDetail) as? ContinuationInfo.NewEpisodes)?.newCount
}

// ============================================================
// グリッド用書籍カード
// ============================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GridBookCard(
    book: BookEntity,
    progress: ProgressEntity?,
    // 続きありバッジ用の作品要約（VM が一括照会し配布。null=未紐付け/未取得/失敗）。
    novelDetail: WorkSummary?,
    // 章数（chap_N.html の枚数）。VM の chapterCountMap から渡す＝カード毎の重複IOを廃し、
    // 状態フィルタ（readingStatusFor）と同じ値を共有する。
    totalChaps: Int,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    // 選択モード（残8・複数選択削除）: true の間はタップ=選択トグル・長押しも選択トグル。
    // selected=この本が選択済み。onEnterSelection=通常時の長押しで選択モードへ入る（この本を選択して開始）。
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onEnterSelection: () -> Unit = {},
    // 本棚がこの本を「初めて読了として描く」瞬間だけ true＝朱印を一度だけ押印する（案A・ADR0014 §motion 追補）。
    // 既読了の再描画・スクロール・起動時の既読了は false＝静的表示（初回一回きり）。判定と記録は呼び出し側が持つ。
    playSealStamp: Boolean = false,
    // 押印アニメ完了時に呼ぶ。呼び出し側が「押印済み」を記録して二度と再生しないためのラッチ。
    onSealStamped: () -> Unit = {},
    // 本文欠落（案B・2026-07-29）: 非 null なら欠落本＝書影左下に「本文なし」バッジ＋状態行をこの文言で
    // 置き換える（文言は domain.reimportStatusLabel が正本）。既定 null は既存呼び出し・テストの互換。
    missingLabel: String? = null,
) {
    val chapNum = chapterNumberOf(progress?.lastReadFilename)

    // 最終章の章内スクロールを加味して「開いた瞬間100%」の嘘を消す（F-N・詳細は progressFractionFor）。
    val progressFraction = progressFractionFor(
        chapNum, totalChaps, progress?.scrollIndex ?: 0, progress?.scrollOffset ?: 0,
    )

    // 読了なら書影右下に朱印「了」を出す（正本 bookshelf-shiori-grid-D.html の .seal）。
    // 判定はカード表示・状態フィルタと同一の単一真実源 readingStatusFor を使い、進捗行(BookProgressRow)や
    // 「読了」フィルタと徴（しるし）がズレないようにする。
    val isFinished = readingStatusFor(progress, totalChaps) == ReadingStatus.FINISHED

    // キャプション行右端の可視⋮メニューの開閉（カード毎に独立）。
    var menuOpen by remember { mutableStateOf(false) }

    // タップ時にスケールダウンするアニメーション（Apple Books 的な触感）
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = MotionSpringCard,
        label = "gridCardScale",
    )

    // モック .bk: カード地・影・角丸チップを廃したフラット構図。書影＋メタを地に直接置く。
    // 通常＝クリックで開く / 長押しで選択モードに入る（残8・案B裁定）。選択モード中はタップで選択トグル。
    Column(
        modifier = modifier
            // TalkBack で1冊=1トラバーサル単位に束ねる（critic Major 2026-07-12）。題字/著者/進捗/続きバッジが
            // 個別ノードに割れて何度もスワイプさせる問題を解消する。
            .semantics(mergeDescendants = true) {}
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = { if (selectionMode) onToggleSelect() else onOpen() },
                onLongClick = { if (selectionMode) onToggleSelect() else onEnterSelection() },
            ),
    ) {
        // 書影＝栞（紙地＋色の棒＋先端＋表紙内の縦組み明朝題字）。角丸3px（モック .cv）。
        // 題字は表紙内で1度だけ（正本 bookshelf-shiori-final-D.html）＝本欄の横題字を廃し二重表示を解消。
        // モック .cv の box-shadow＝表紙が棚から浮く影。ダークは背景が暗く影が沈むため強め
        // （モック: ライト 0 6px 16px rgba(...,.12) ／ ダーク 0 8px 20px rgba(0,0,0,.5)）。
        val coverIsDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        Box(modifier = Modifier.fillMaxWidth()) {
            ShioriCover(
                title = book.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    // shadow は clip より前＝影を要素の外周に落としてから角丸で本体をクリップする。
                    .shadow(
                        elevation = if (coverIsDark) 8.dp else 6.dp,
                        shape = RoundedCornerShape(3.dp),
                    )
                    .clip(RoundedCornerShape(3.dp)),
                // 取込時に抽選・永続化した先端種/棒長（旧蔵書は null＝title 由来へフォールバックで見た目不変）。
                persistedTipIndex = book.shioriTipIndex,
                persistedLenFrac = book.shioriLenFrac,
            )
            // 選択中は書影へ藍の細縁取り＋淡い藍かぶせ（正本 .bk.sel .cv）。書影画像の上に重ねる。
            if (selected) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)),
                )
            }
            // 朱印「了」（読了バッジ）。正本 grid-D .seal: 19dp角・角丸2dp・左下9dp・枠1dp・明朝 SemiBold 9.5sp。
            // なぜ左下か（2026-07-12）: 右下だと表紙内の縦組み題字（右起点で読む）と重なるため左下へ移した
            //（正本 .seal も right→left に同期）。朱色は accent（title 由来色相）と無関係の固定「読了の徴」＝専用トークン。
            // 背景は紙地へ半透過で溶かす（ライトは surface 50%／ダークは正本の固定暗色トークン 50%）。coverIsDark 再利用。
            // 欠落バッジ（案B）: 左下＝了印と同じ静かな隅。欠落カードは了印を出さない（下の isFinished 条件で排他）
            // ＝実体を失った本に読了の徴を重ねず、モックどおり欠落表現だけを載せる。
            if (missingLabel != null) {
                MissingContentBadge(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = Spacing.S8, bottom = Spacing.S8),
                )
            }
            if (isFinished && missingLabel == null) {
                val sealColor = if (coverIsDark) ShioriSealVermilionDark else ShioriSealVermilion
                val sealBg = if (coverIsDark) ShioriSealScrimDark.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                // 押印アニメの進捗（0=振り上げ/透明・1=着地/定着）。押印する本は 0 始まりで一度だけ 1 へ、
                // 既読了表示は 1 で静止。初期値を playSealStamp で決め 0 始まりにして初回1フレームのちらつきを防ぐ。
                val sealStamp = remember { Animatable(if (playSealStamp) 0f else 1f) }
                LaunchedEffect(playSealStamp) {
                    if (playSealStamp) {
                        sealStamp.snapTo(0f)
                        sealStamp.animateTo(1f, animationSpec = tween(MotionDurationSeal, easing = MotionEasingSeal))
                        onSealStamped() // 完了後にラッチ（以後 playSealStamp=false で静的表示・二度と再生しない）
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = SealCornerOffset, bottom = SealCornerOffset)
                        // 押印演出（案A）: scale 1.2→1.0 の単調ダウン（1.0 未満へ揺り戻さない＝禁止則③の bounce/overshoot に
                        // 触れない）＋回転 -7°→0° settle＋透過。value を graphicsLayer 内で読み描画フェーズへ閉じる（再コンポーズ無し）。
                        .graphicsLayer {
                            val p = sealStamp.value
                            val s = 1f + 0.2f * (1f - p)
                            scaleX = s
                            scaleY = s
                            rotationZ = -7f * (1f - p)
                            alpha = (p / 0.55f).coerceIn(0f, 1f)
                        }
                        .size(19.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(sealBg)
                        .border(1.dp, sealColor, RoundedCornerShape(2.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "了",
                        fontFamily = MinchoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = FontSealBadge,
                        color = sealColor,
                        // グリフを四角の中央へ（不格好の是正）: 既定の includeFontPadding（字面上下の余白）で
                        // 「了」が上寄りに見えるため padding を切り、行高を字面へ trim して中央整列させる
                        // （モック .seal の display:grid;place-items:center 相当のセンタリングを Compose で再現）。
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.Both,
                            ),
                        ),
                    )
                }
            }
            // 選択モード中は書影右上に選択マーク（正本 .chk）。全書影に丸が出る＝非選択は白リング。
            if (selectionMode) {
                SelectionCheck(
                    selected = selected,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Spacing.S8),
                )
            }
        }

        // 表紙下は著者＋状態（左）＋可視⋮（右端・K形の是正＝モック .caprow .dots）。題字は表紙内で描くため本欄には出さない。
        // 著者はゴシック（既定）・補助色。栞表紙が作品の識別子なので下段は静かに添えるだけ。
        Spacer(Modifier.height(Spacing.S8))
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                if (book.author.isNotBlank()) {
                    Text(
                        text = book.author,
                        fontSize = FontLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.S8))
                }
                if (missingLabel != null) {
                    // 欠落本の状態行（案B）: 進捗行・相対時刻・続きバッジを欠落文言1行に置き換える
                    // （本文が無い本に進捗の数字を出すと嘘になる。復旧すれば通常表示へ自然に戻る）。
                    Text(text = missingLabel, fontSize = FontLabel, color = LocalShelfColors.current.infoText)
                } else {
                    BookProgressRow(
                        totalChaps = totalChaps,
                        progressFraction = progressFraction,
                        flexBar = true,
                    )
                    // よみかけ（進捗あり）に限り「いつぶりか」を静かに添える（continuity Minor・モック未表現）。
                    RelativeReadLabel(
                        progressFraction = progressFraction,
                        lastReadAt = progress?.lastReadAt ?: 0L,
                        modifier = Modifier.padding(top = Spacing.S4),
                    )
                    // 続きあり（モックは進捗行の下・上4px）
                    newEpisodeCountFor(novelDetail, totalChaps)?.let { newCount ->
                        NewChaptersBadge(newCount = newCount, modifier = Modifier.padding(top = Spacing.S4))
                    }
                }
            }
            // 可視⋮（K実装 KCardMenuButton と同型）。選択モード中は書影上の選択マークへ場を譲り隠す。
            // タップ＝カードメニュー。D の蔵書カードは単一削除の専用配線を持たず、複数選択の入口「選択」だけを露出して
            // 長押しに隠れていた選択導線を発見可能にする（K の KGridBookCard ⋮ と同じ回答＝新機能・VM 変更は作らない）。
            if (!selectionMode) {
                Box {
                    CardMenuButton(onClick = { menuOpen = true })
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("選択") },
                            onClick = { menuOpen = false; onEnterSelection() },
                        )
                    }
                }
            }
        }
    }
}

/**
 * キャプション行右端の可視⋮（32dpタップ面・モック .caprow .dots）。書影上でなく通常面に載るためスクリム不要＝
 * トークン色で描く（書影右上案は栞書影の縦題字と衝突するため移設＝K の実機検分 2026-07-23 と同じ是正）。
 * internal 昇格＝Web由来カード（WebBookCard.kt）でも同じ可視⋮を共有するため（系1）。
 */
@Composable
internal fun CardMenuButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.MoreVert,
            contentDescription = "メニュー",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

// ============================================================
// 文字目録の行（骨格3）: 表紙を排し、明朝の題字を主役に縦へ連ねる。
// 作品の識別は左端 4dp の色帯（本の小口メタファ・作品識別色）だけで行う
// ＝生成書影を捨てて存在しない装画を捏造しない（モック bookshelf-mokuroku-D.html）。
// ※関数名は呼び出し側（BookshelfContent のリストモード）との互換のため ListBookCard を維持する。
// ============================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ListBookCard(
    book: BookEntity,
    progress: ProgressEntity?,
    // 続きありバッジ用の作品要約（VM が一括照会し配布。null=未紐付け/未取得/失敗）。
    novelDetail: WorkSummary?,
    // 章数（chap_N.html の枚数）。VM の chapterCountMap から渡す＝カード毎の重複IOを廃し、
    // 状態フィルタ（readingStatusFor）と同じ値を共有する。
    totalChaps: Int,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onEnterSelection: () -> Unit = {},
    // 本文欠落（案B）: 非 null なら進捗行をこの文言で置き換える（GridBookCard と同契約。
    // 目録は書影を持たないためバッジは出さず状態行だけが欠落を運ぶ）。既定 null は既存呼出し互換。
    missingLabel: String? = null,
) {
    val chapNum = chapterNumberOf(progress?.lastReadFilename)

    // 最終章の章内スクロールを加味して「開いた瞬間100%」の嘘を消す（F-N・詳細は progressFractionFor）。
    val progressFraction = progressFractionFor(
        chapNum, totalChaps, progress?.scrollIndex ?: 0, progress?.scrollOffset ?: 0,
    )

    // 作品識別色（左端の色帯）。表紙を持たない目録では、この色帯だけが作品の視覚的な手がかり。
    // 書架の栞（棒・先端）と同じ shiori accent に一本化＝seed は book.id でなく title。
    // なぜ title か: 書架グリッドの栞は title→色相で描くため、目録も title 由来にしないと同じ本が
    // 書架と目録で違う色になる（整合ルール「1冊=1色相」の核心・正本 consistency-D）。
    val accentLightness = LocalShioriColors.current.accentLightness
    val barColor = remember(book.title, accentLightness) { shioriAccentFor(shioriHue(book.title), accentLightness) }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1.0f,
        animationSpec = MotionSpringCard,
        label = "listCardScale",
    )

    // モック 文字目録 .li: カード地・影・書影を廃し、上下余白＋下ヘアラインで区切る静かな行。
    // 外側 Column が行本体(Row)と区切り線(HorizontalDivider)を束ねる。
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                // 1冊=1トラバーサル単位に束ねる（critic Major）。行末の⋮ IconButton は自身が併合ノード境界の
                // ため別フォーカスとして残る＝「本1ノード＋⋮1ノード」の2単位になり、題字/著者/進捗の分割読みを解消。
                .semantics(mergeDescendants = true) {}
                .graphicsLayer { scaleX = scale; scaleY = scale }
                // 選択中は行全体に淡い藍かぶせ（正本 .bk.sel 相当・目録は色帯があるため控えめに）。
                .background(
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    else Color.Transparent
                )
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = { if (selectionMode) onToggleSelect() else onOpen() },
                    // 通常は長押しで選択モードへ・選択モード中はタップ/長押しで選択トグル（残8・案B裁定）。
                    onLongClick = { if (selectionMode) onToggleSelect() else onEnterSelection() },
                )
                // 色帯を行の高さいっぱいに伸ばすため、行の高さを内容の最小内在高さに合わせる。
                .height(IntrinsicSize.Min)
                .padding(top = Spacing.S16, bottom = Spacing.S16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左端の色帯（本の小口メタファ・作品識別色）。行の高さに合わせて stretch。
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor),
            )
            Spacer(Modifier.width(Spacing.S16))
            Column(modifier = Modifier.weight(1f)) {
                // 明朝の題字（目録の主役）。2行まで。
                Text(
                    text = book.title,
                    fontFamily = MinchoFamily,
                    fontSize = FontCardTitle,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // 著者＋続きあり（青磁）。モックの目録は著者脇に「続き N話」を寄せる。
                val newCount = newEpisodeCountFor(novelDetail, totalChaps)
                if (book.author.isNotBlank() || newCount != null) {
                    Spacer(Modifier.height(Spacing.S8))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (book.author.isNotBlank()) {
                            Text(
                                text = book.author,
                                fontSize = FontLabel,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                // 続きバッジの領域を確保するため、著者が長くてもバッジを押し出さない。
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                        newCount?.let {
                            NewChaptersBadge(
                                newCount = it,
                                modifier = Modifier.padding(start = if (book.author.isNotBlank()) Spacing.S12 else 0.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.S12))
                if (missingLabel != null) {
                    // 欠落本の状態行（案B）: 進捗行・相対時刻を欠落文言1行に置き換える（GridBookCard と同理由）。
                    Text(text = missingLabel, fontSize = FontLabel, color = LocalShelfColors.current.infoText)
                } else {
                    BookProgressRow(
                        totalChaps = totalChaps,
                        progressFraction = progressFraction,
                        flexBar = false,
                    )
                    // よみかけに限り「いつぶりか」を静かに添える（continuity Minor・モック未表現）。
                    RelativeReadLabel(
                        progressFraction = progressFraction,
                        lastReadAt = progress?.lastReadAt ?: 0L,
                        modifier = Modifier.padding(top = Spacing.S4),
                    )
                }
            }
            // 選択モード中は行末に選択マーク（正本 .chk）。選択で藍塗り＋白チェック。
            if (selectionMode) {
                Spacer(Modifier.width(Spacing.S8))
                SelectionCheck(selected = selected)
            }
        }
        // 行下のヘアライン区切り（モック .li の border-bottom 1px、本棚系 --hl）
        HorizontalDivider(
            thickness = 1.dp,
            color = LocalShelfColors.current.hairline,
        )
    }
}

// ============================================================
// 選択マーク（残8・複数選択削除。正本 bookshelf-multiselect-D .chk）
// 選択モード中は全書影/全行に出す。非選択=白リング＋暗いスクリム（任意の書影色の上で視認）／
// 選択=藍塗り＋白チェック。選択塗り＝primary(藍トークン)。リング/スクリムは画像可読性のための
// 固定色（朱印バッジと同じ発想＝テーマ色に紐づかない用途）。
// ============================================================
// internal 昇格＝Web由来カード（WebBookCard.kt・系3 の複数選択参加）でも同じ選択マークを共有するため。
@Composable
internal fun SelectionCheck(selected: Boolean, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (selected) primary else Color.Black.copy(alpha = 0.26f))
            .border(
                1.5.dp,
                if (selected) primary else Color.White.copy(alpha = 0.9f),
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
