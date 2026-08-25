// 教示カード列の描画（正本モック docs/design-candidates/tutorial-onboarding-K.html の
// `.scrim` / `.ocard` / `.odots` / `.obtn` を翻訳したもの）。NavHost には足さない＝呼び出し元の上へ重ねる。
// 意匠の自己判断はしない: 寸法・語・並びは正本の写経で、色と余白だけトークン層へ載せ替える。
package com.novelreader.ui.intro

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.novelreader.ui.theme.FontButtonLabel
import com.novelreader.ui.theme.FontChipLarge
import com.novelreader.ui.theme.FontPresetTitle
import com.novelreader.ui.theme.FontTopBarTitle
import com.novelreader.ui.theme.LocalShelfColors
import com.novelreader.ui.theme.Spacing

/** 正本 `.scrim{background:rgba(28,31,38,.74)}` の α。色は scrim トークンから引く（直書き禁止）。 */
private const val INTRO_SCRIM_ALPHA = 0.74f

/** 正本 `.ocard{border-radius:16px}`。 */
private val IntroCardCorner = 16.dp

/** 正本 `.obtn.pri{border-radius:10px}`。 */
private val IntroPrimaryCorner = 10.dp

/** 正本 `.ocard .fig{height:112px}`。 */
private val IntroFigureBoxHeight = 112.dp

/** 正本 `.odots i{width:6px;height:6px}`。 */
private val IntroDotSize = 6.dp

/**
 * 教示カード列のホスト。[LocalIntroController] が居て、かつ出す回があるときだけ描く。
 * 置き場所は MainActivity のルート＝背景（本棚／本文／検索／設定）を選ばない 1 か所で足りる。
 */
@Composable
internal fun IntroOverlayHost(modifier: Modifier = Modifier) {
    val controller = LocalIntroController.current ?: return
    val flow = controller.flow ?: return
    IntroOverlayContent(
        flow = flow,
        onNext = controller::next,
        onBack = controller::back,
        onDismiss = controller::dismiss,
        modifier = modifier,
    )
}

/**
 * 描画層（state を持たない＝Robolectric で分岐を全数固定できる形／ADR 0009・`/new-screen` §4）。
 */
@Composable
internal fun IntroOverlayContent(
    flow: IntroFlow,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // システム Back ＝ ［← もどる］と同じ。先頭カードでの Back は閉じる（正本 §8）。
    // NavHost より後に composition へ入るため、この割込みが最優先で受け取る。
    BackHandler { if (flow.isFirst) onDismiss() else onBack() }

    val card = flow.card
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = INTRO_SCRIM_ALPHA))
            // スクリム外タップ＝閉じる（正本 §8 の消費規則は終端かどうかで決まるので、ここでは
            // 「閉じる」だけを伝えて判断は Controller に持たせる）。同時に背後への素通しも塞ぐ。
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            // カードはダイアログとして読み上げる（正本 §8 a11y）＝この配下を 1 つの走査群にまとめる。
            .semantics { isTraversalGroup = true }
            // 教示カードは NavHost の外（Box 直下）に重ねるため、MainActivity の Column に入れた
            // カットアウト回避が **効かない**＝ここで同じ inset を自分で持つ。background より後に
            // 置くのは、スクリムは切り欠きの下まで敷き詰めたままカードだけを安全な帯へ寄せるため。
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
            .padding(Spacing.S24),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(IntroCardCorner),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                // カード面のタップはスクリムへ落とさない（誤タップで閉じるのを防ぐ）。
                // clickable ではなく pointerInput なのは、カード自体をボタンとして読み上げさせないため。
                .pointerInput(Unit) { detectTapGestures { } }
                .semantics {
                    paneTitle = card.title
                    // 点は装飾なので読み上げない。代わりに「2 枚中 1 枚目」相当をカードの状態として持たせる
                    // （章内の枚数で数える＝画面の点と一致させる・正本 §8 a11y）。
                    if (flow.dotCount > 0) {
                        stateDescription = "${flow.dotCount}枚中${flow.dotIndex + 1}枚目"
                    }
                },
        ) {
            Column(
                modifier = Modifier
                    // 例外なしの規則（`/new-screen`）: 非スクロール面が溢れると**操作要素が画面外へ押し出され
                    // 到達手段が消える**のに、溢れは画素に痕跡を残さず golden でも捕まらない。ここは
                    // 図版 112dp ＋ 項目 3 つ ＋ 点 ＋ ボタンで、小さい画面や fontScale を上げた端末では
                    // 実際に［とじる］が落ちる（Robolectric の既定画面で再現した）。
                    .verticalScroll(rememberScrollState())
                    .padding(
                        // 正本 `.ocard{padding:26px 22px 18px}` を離散スケールへ最近傍で丸めた（ADR 0014 §C）。
                        start = Spacing.S24, end = Spacing.S24, top = Spacing.S24, bottom = Spacing.S16,
                    ),
            ) {
                IntroCardBody(flow)
                IntroDots(flow)
                Spacer(Modifier.height(Spacing.S4)) // 正本 `.obtns{margin-top:2px}`
                IntroButtons(flow, onNext = onNext, onBack = onBack, onDismiss = onDismiss)
            }
        }
    }
}

@Composable
private fun IntroCardBody(flow: IntroFlow) {
    val card = flow.card
    // 本文色。正本の `.ocard p{color:#4A4C47}` はモック文書側の生値で K トークンに対応が無いため、
    // 役割トークン `infoText`（意味を運ぶ補助テキスト・AA 充足）へ載せ替える。装飾専用の
    // onSurfaceVariant を使わないのは、ここが「読ませる文」だから（ADR 0014-D の審級）。
    val bodyColor = LocalShelfColors.current.infoText
    val inkEmphasis = SpanStyle(color = MaterialTheme.colorScheme.onSurface)
    // 正本 `.ocard dd em{color:var(--ai);font-weight:700}`＝項目中の強調だけ藍で立てる。
    val accentEmphasis = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntroFigureBoxHeight)
            // 線画は装飾＝TalkBack へ新しいノードを足さない。
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        IntroFigureArt(card.figure)
    }
    Spacer(Modifier.height(Spacing.S16)) // 正本 `.fig{margin-bottom:18px}`

    Text(
        text = card.title,
        fontSize = FontTopBarTitle, // 正本 `.ocard h3{font-size:17px}`
        color = MaterialTheme.colorScheme.onSurface,
        letterSpacing = 0.05.em,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(Spacing.S12)) // 正本 `h3{margin-bottom:12px}`

    card.paragraphs.forEachIndexed { i, paragraph ->
        if (i > 0) Spacer(Modifier.height(Spacing.S8)) // 正本 `p+p{margin-top:9px}`
        Text(
            text = introAnnotated(paragraph, inkEmphasis),
            fontSize = FontPresetTitle, // 正本 `.ocard p{font-size:13.5px}`
            lineHeight = 26.3.sp, // 13.5 × 1.95（正本 line-height）
            color = bodyColor,
        )
    }

    card.items.forEachIndexed { i, item ->
        if (i > 0) Spacer(Modifier.height(Spacing.S12)) // 正本 `dt{margin-top:12px}`
        Text(
            text = item.label,
            fontSize = FontChipLarge, // 正本 `.ocard dt{font-size:11.5px}`
            letterSpacing = 0.06.em,
            // 正本は ink-soft（装飾スロット）だが、項目名は「何がどこに割り当たっているか」を運ぶ文字＝
            // AA を満たす infoText を使う（ReadingSettingsSheet の極小ラベルと同じ裁定）。
            // dd との階層は色ではなく寸法（11.5 vs 13.5sp）と字間で作る。
            color = bodyColor,
        )
        Spacer(Modifier.height(Spacing.S4)) // 正本 `dd{margin-top:2px}`
        Text(
            text = introAnnotated(item.text, accentEmphasis),
            fontSize = FontPresetTitle,
            lineHeight = 25.7.sp, // 13.5 × 1.9（正本 line-height）
            color = bodyColor,
        )
    }

    card.footnote?.let { footnote ->
        Spacer(Modifier.height(Spacing.S16)) // 正本 `.later{margin-top:15px}`
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(Spacing.S12)) // 正本 `.later{padding-top:12px}`
        Text(
            text = footnote,
            fontSize = FontButtonLabel, // 正本 `.later{font-size:12.5px}`（スロットは値で選ぶ）
            lineHeight = 21.3.sp, // 12.5 × 1.7
            color = bodyColor,
        )
    }
}

@Composable
private fun IntroDots(flow: IntroFlow) {
    if (flow.dotCount <= 0) return
    Spacer(Modifier.height(Spacing.S24)) // 正本 `.odots{margin:20px 0 14px}` の上
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 点そのものは読み上げない（状態はカードの stateDescription が持つ・正本 §8 a11y）。
            .clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(Spacing.S8, Alignment.CenterHorizontally),
    ) {
        repeat(flow.dotCount) { i ->
            val on = i == flow.dotIndex
            Box(
                Modifier
                    .size(IntroDotSize)
                    .background(
                        color = if (on) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            // 正本 `#D6D4CD`（素地とほぼ同輝度の消し点）に相当する濃さを、
                            // outline トークンから α で導く（生値の直書きを避けるため）。
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                        },
                        shape = CircleShape,
                    ),
            )
        }
    }
    Spacer(Modifier.height(Spacing.S16)) // 正本 `.odots{margin-bottom:14px}`
}

@Composable
private fun IntroButtons(
    flow: IntroFlow,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    val subtle = LocalShelfColors.current.infoText
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (flow.secondary == IntroSecondary.NONE) {
            Arrangement.End // 正本 `.obtns.r`（1 枚で終わる回は主ボタンだけ）
        } else {
            Arrangement.SpaceBetween
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (flow.secondary) {
            IntroSecondary.NONE -> Unit
            IntroSecondary.LATER -> TextButton(onClick = onDismiss) {
                Text("あとで", fontSize = FontPresetTitle, letterSpacing = 0.04.em, color = subtle)
            }
            IntroSecondary.BACK -> TextButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = null, // 直後の「もどる」が同じことを言う＝二重読み上げを避ける
                    tint = subtle,
                )
                Spacer(Modifier.width(Spacing.S4)) // 正本 `.obtn.back{gap:5px}`
                Text("もどる", fontSize = FontPresetTitle, letterSpacing = 0.04.em, color = subtle)
            }
        }
        Button(
            onClick = onNext,
            shape = RoundedCornerShape(IntroPrimaryCorner),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            // 正本 `.obtn.pri{padding:11px 22px}` を離散スケールへ丸めた。
            contentPadding = PaddingValues(horizontal = Spacing.S24, vertical = Spacing.S12),
        ) {
            Text(
                text = flow.primaryLabel,
                fontSize = FontPresetTitle,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.04.em,
            )
        }
    }
}

/**
 * 正本の `<b>` / `<em>` を `**…**` で持ち回るための最小マークアップ展開。
 *
 * なぜ素の String に印を埋めるか: 強調は文の途中に何度も現れるので、カード定義側を
 * `listOf(Span("PDF", true), Span(" と Web…", false))` の形にすると**文言そのものが読めなくなり**、
 * 正本 §4 との突き合わせ（1 文字も変えていないか）が人力でできなくなる。
 * 閉じ忘れた `**` は強調を開始しないまま素通しする（文字が消えるより残る方が害が小さい）。
 */
internal fun introAnnotated(text: String, emphasis: SpanStyle): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    while (true) {
        val open = text.indexOf("**", cursor)
        if (open < 0) break
        val close = text.indexOf("**", open + 2)
        if (close < 0) break
        append(text.substring(cursor, open))
        pushStyle(emphasis)
        append(text.substring(open + 2, close))
        pop()
        cursor = close + 2
    }
    append(text.substring(cursor))
}
