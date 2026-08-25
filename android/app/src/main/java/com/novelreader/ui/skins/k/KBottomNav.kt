package com.novelreader.ui.skins.k

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelreader.ui.theme.Spacing

/**
 * 明快K: 恒常ボトムナビ（モック正本＝skins/bookshelf-K.html ほか3枚の共通 tabbar）。
 *
 * なぜ恒常ナビか: 第三者テスト「一目でどの画面か分からない」の真因を〈現在地・目的地一覧が常時見えない
 * 構造〉と裁定したため（plan default-ui-clarity-K・UX/15「同格3〜5目的地＋現在地常時表示」）。
 * 競合4機（カクヨム/なろう公式/ナローリーダー2種）すべてがこの型＝ユーザーの既習慣に乗る。
 * 読書・目次など深い画面には出さない（没入優先＝モック正本どおり）。
 */
enum class KTab(val label: String, val icon: ImageVector) {
    BOOKSHELF("本棚", Icons.Outlined.MenuBook),
    DISCOVER("さがす", Icons.Outlined.Search),
    SETTINGS("設定", Icons.Outlined.Settings),
}

/**
 * 横向き Rail のタブ選択関数（非 null＝Rail 化が起動している）。
 *
 * なぜ CompositionLocal か（引数でなく）: Rail は「タブ3面を束ねる上位シェル（MainActivity）」の持ち物だが、
 * **置き場所が本文の左**なので、下端に固定された [KBottomNav] のスロットからは出せず各画面が自分で立てるしかない。
 * その結果〈帯を消す側（[KBottomNav]）〉と〈Rail を立てる側（BookshelfK / DiscoveryHomeK）〉が別ファイルに割れる。
 * この2つが**別々の条件で判断すると「帯も Rail も無い画面」が構造的に作れてしまう**ため、起動条件を
 * 1本の信号に束ねる。既定 null＝未結線では**縦横とも従来どおり**（帯が出て Rail は立たない）＝
 * 結線前に取り込まれても退行が起きない形にしてある。
 *
 * 結線は上位シェル側の1点だけ:
 * `CompositionLocalProvider(LocalKTabSelect provides onSelectTab) { …TabPagerHost と KBottomNav… }`
 * （タブ Pager を所有する層＝MainActivity。lambda の同一性で読み手が再コンポーズしないよう
 *  `remember` 済みの関数を渡すこと。）
 */
val LocalKTabSelect = compositionLocalOf<((KTab) -> Unit)?> { null }

/**
 * 横向きで**自前の Rail を立てる面**か（＝この面ではボトムナビの帯を出さない）。
 *
 * **タブ3面すべてが true**（2026-08-25 裁定）。当初は意匠裁定の範囲（ADR 0034 の T1 は本棚とさがすだけ）に
 * 合わせて設定を除いていたが、それだと**ナビの形が面ごとに違う**状態になり、横向きで さがす⇄設定 を
 * スワイプした中点で帯が出入りして Pager のビューポート高が 64dp ぶん跳ねる。
 * ⇒ **意匠（各面の版面をどう畳むか）と構造（ナビをどの軸に置くか）は別物**で、後者は3面で揃える。
 *
 * ここを増減するときは、対象の面が [KNavigationRail] を立てる／やめることと必ず同時に行う
 *（片方だけ動かすと、その面が「帯も Rail も無い画面」になる）。
 */
internal fun KTab.ownsLandscapeRail(): Boolean = true

/** Rail の幅（ADR 0034 意匠裁定＝R1「幅 80dp・ラベルあり」。R2 の 56dp は初見の自己説明性を優先して不採用）。 */
val KNavigationRailWidth = 80.dp

@Composable
fun KBottomNav(
    current: KTab,
    onSelect: (KTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 横向きは帯でなく Rail（ADR 0034）＝縦を食う 64dp+nav インセットを、余っている幅へ逃がす。
    // 判定を [LocalKTabSelect] と AND で取る理由は同 local の KDoc（帯も Rail も無い画面を作らないため）。
    if (LocalKTabSelect.current != null &&
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE &&
        current.ownsLandscapeRail()
    ) {
        return
    }
    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth()) {
        Column {
            // モックの上罫ヘアライン＝面の切れ目を1pxで示す（影を使わない静かな区切り・D系の流儀）。
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    // モックの帯高64px（.tabbar height:64px）は fontScale 1.0 の寸法＝下限（min）として保つ。
                    // 固定 height にしないのは、2.0 ではラベル(11sp→22sp相当)がピル32dp+上余白の残り≒20dpに
                    // 収まらず尻切れになる破綻が golden（KBottomNav_bookshelf_*_2.0）に焼かれていたため
                    // （監査 2026-08-06 根因④）。min なら拡大時だけ内容高へ追従して帯が伸びる。
                    // 1.0 で帯が 64dp のままである根拠は下の Text の lineHeight 明示（内容自然高 60dp < 64dp）。
                    .heightIn(min = 64.dp)
                    .selectableGroup(),
            ) {
                KTab.entries.forEach { tab ->
                    val selected = tab == current
                    val tint = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .selectable(
                                selected = selected,
                                role = Role.Tab,
                                onClick = { onSelect(tab) },
                            )
                            .padding(top = Spacing.S8),
                    ) {
                        KNavItemContent(tab, selected, tint)
                    }
                }
            }
        }
    }
}

/**
 * タブ1本の中身（選択ピル＋アイコン＋ラベル）。**帯と Rail で同一の実装を共有する**
 * ＝モック `.tab` と `.rt` は「並ぶ向きと寸法」だけが違い、中身（ピル 56x32・アイコン 24dp・
 * ラベル 11sp/16sp）は同一だから。片方だけ意匠が動く事故を構造で潰す。
 */
@Composable
private fun KNavItemContent(tab: KTab, selected: Boolean, tint: Color) {
    // 選択中はアイコン背後に藍10%の横長ピル（モック .pill 56x32/r16）＝現在地の面表示。
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 56.dp, height = 32.dp)
            .background(
                color = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0f)
                },
                shape = MaterialTheme.shapes.large,
            ),
    ) {
        Icon(tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
    }
    // ラベル常時表示（選択中のみ太字）＝アイコン語彙に依存しない自己説明（自明性A0）。
                        // なぜ lineHeight を明示するか（帯高の真因）: 未指定だと MaterialTheme が既定として流す
                        // bodyLarge の行送り 28sp（16sp 本文用の値）を 11sp のラベルが相続し、1タブの内容自然高が
                        // 8(上余白)+32(ピル)+4(ラベル上余白)+28(行ボックス)=72dp とモックの帯 64dp を 8dp 超える。
                        // 旧 height(64.dp) 固定はこの超過をクリップして隠していたため、heightIn(min) へ変えた
                        // 途端に 1.0 でも帯が 72dp へ伸びた（golden 720x130→720x146 の真因＝heightIn 自体ではない）。
                        // 16sp はモック .lb（font-size:11px・line-height 指定なし＝normal 相当）の行送りであり
                        // M3 labelSmall（11sp/16sp）とも一致する。これで自然高は 8+32+4+16=60dp となり、
                        // 1.0 は heightIn(min=64) が効いてモックどおり帯 64dp（golden 720x130）、2.0 は
                        // 8+32+4+32=76dp 相当（実測 帯76.5dp・ヘアライン込み golden 720x155）へ伸びて収まる。
    Text(
        tab.label,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = tint,
        modifier = Modifier.padding(top = Spacing.S4),
    )
}

/**
 * 明快K: 横向きの恒常ナビ＝左端縦置きの NavigationRail（構造の裁定＝ADR 0034・意匠の裁定＝同 ADR 末尾の追記）。
 *
 * なぜ横向きだけ形を変えるか: 横向きでは**幅が余って高さが枯れている**。ボトムナビの 64dp ＋ nav インセットは
 * 枯れている軸だけを食っており、実測で本棚の書影カードが1枚も収まらなかった。固定分を余っている軸（幅）へ
 * 逃がすのが構造的に正しい——という一点が ADR 0034 の決定。
 *
 * 採った形（いずれも 2026-08-20 のユーザー裁定・ここで作り直さない）:
 *  - 幅 80dp・**ラベルあり**（R1）。56dp のアイコンのみ（R2）で縦を更に稼ぐ道は採らない＝初見の自己説明性が上。
 *  - **FAB は Rail 上端**（[fab]）。拡張ラベル「PDFを追加」を失い円形になる代償は受け入れ済み
 *    （右下据え置きは最終列の書影に恒久的に重なるため却下）。
 *  - **上半（[header]）は画面ごとに変わってよい**＝下半のタブ3本は全画面不変のまま、上半は「今いる面の題」に使う。
 *    Rail が不動のナビでなくなる弱点は、題字の逃がし先を優先する裁定として引き受けた。
 *
 * インセット: Rail は各画面の `statusBarsPadding()` より**外側**（本文の左隣）に立つので、状態バー・
 * ナビバーの回避は自分で持つ。本文側は下端の帯が消えるぶん nav インセットを自分で持つ（各画面側）。
 */
@Composable
fun KNavigationRail(
    current: KTab,
    onSelect: (KTab) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable (ColumnScope.() -> Unit)? = null,
    fab: @Composable (() -> Unit)? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxHeight().width(KNavigationRailWidth),
    ) {
        Row {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(vertical = Spacing.S8), // モック .rail padding:8px 0
            ) {
                header?.invoke(this)
                fab?.invoke()
                // タブ3本は残り全域の中央（モック .items{flex:1;justify-content:center}）＝
                // 上半（題字・FAB）の有無で3本の位置が動かない＝面を移っても指の行き先が同じ。
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f).fillMaxWidth().selectableGroup(),
                ) {
                    KTab.entries.forEach { tab ->
                        val selected = tab == current
                        val tint = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                // モック .rt height:56px。**min** で持つ理由は帯（[KBottomNav]）の heightIn と同じ＝
                                // fontScale を上げるとラベルの行ボックスが伸びるので、固定高だと尻切れをクリップで隠す。
                                .heightIn(min = 56.dp)
                                .selectable(
                                    selected = selected,
                                    role = Role.Tab,
                                    onClick = { onSelect(tab) },
                                ),
                        ) {
                            KNavItemContent(tab, selected, tint)
                        }
                    }
                }
            }
            // モックの右罫ヘアライン（.rail border-right:1px）＝帯の上罫と同じ「影を使わない静かな区切り」。
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/**
 * Rail 上半の題（[KNavigationRail] の `header` へ渡す既定の形）。モック `.railhead`＝
 * 12px bold の面名（[title]）＋ 10px の従属メタ（[meta]・本棚の「12冊」）。
 *
 * ここへ移すのは T1 横一列化の要件そのもの＝**画面名を消さずに縦の固定分から外す**ため。
 */
@Composable
fun KRailHeader(title: String, meta: String? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.S8), // .railhead padding:2px 0 8px
    ) {
        Text(
            title,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        if (meta != null) {
            Text(
                meta,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
