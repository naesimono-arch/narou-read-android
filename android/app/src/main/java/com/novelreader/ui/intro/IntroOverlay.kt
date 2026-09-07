// 教示カード列の描画（正本モック docs/design-candidates/tutorial-onboarding-K.html の
// `.scrim` / `.ocard` / `.odots` / `.obtn` を翻訳したもの）。NavHost には足さない＝呼び出し元の上へ重ねる。
// 意匠の自己判断はしない: 寸法・語・並びは正本の写経で、色と余白だけトークン層へ載せ替える。
package com.novelreader.ui.intro

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.novelreader.ui.theme.FontButtonLabel
import com.novelreader.ui.theme.FontChipLarge
import com.novelreader.ui.theme.FontPresetTitle
import com.novelreader.ui.theme.FontTopBarTitle
import com.novelreader.ui.theme.LocalShelfColors
import com.novelreader.ui.theme.MotionDurationIntroCardFlip
import com.novelreader.ui.theme.MotionDurationIntroCardFlipFadeIn
import com.novelreader.ui.theme.MotionDurationIntroCardFlipFadeOut
import com.novelreader.ui.theme.MotionEasingIntroCardFlip
import com.novelreader.ui.theme.Spacing
import com.novelreader.ui.theme.rememberReduceMotion

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
        orientationVertical = controller.orientationVertical,
        onOrientationChange = controller::selectOrientation,
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
    /** 向きの選択カードでいま選ばれて見えている側（true＝縦書き）。選択を持たないカードでは使わない。 */
    orientationVertical: Boolean,
    /** チップ押下。**確定は呼ばれた側（IntroController）の責務**＝ここでは通知しかしない。 */
    onOrientationChange: (Boolean) -> Unit,
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
            Column {
                // 本文域＝**この回で最も高い 1 枚に固定した器**（[IntroCardDeck]）。点とボタンは器の外＝
                // カードの下端に留め置く（正本 §8「めくりの遷移と寸法」）。
                // weight(fill = false) にするのは、器へ「残りの高さ」を上限として渡しつつ、
                // 中身が短い回でカードを縦に引き伸ばさないため。
                IntroCardDeck(
                    flow = flow,
                    orientationVertical = orientationVertical,
                    onOrientationChange = onOrientationChange,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Column(
                    // 正本 `.ocard{padding:26px 22px 18px}` の左右と下（上は器の側が持つ）を
                    // 離散スケールへ最近傍で丸めた（ADR 0014 §C）。
                    modifier = Modifier.padding(
                        start = Spacing.S24, end = Spacing.S24, bottom = Spacing.S16,
                    ),
                ) {
                    // ⚠️ 点の有無に関わらず入れる間隔。本文域はスクロール器なので溢れた行は下端で
                    // 断ち切られ、ここが 0 だと**半分に切れた行が ［とじる］ に直接くっつく**
                    // （fontScale 2.0・点の無い組C/組D で実測）。IntroDots の上側を S24→S8 へ
                    // 減らして合計 S24 を保つので、点がある回の見えは動かない（正本 `.obody{margin-bottom:14px}`）。
                    Spacer(Modifier.height(Spacing.S16))
                    IntroDots(flow)
                    Spacer(Modifier.height(Spacing.S4)) // 正本 `.obtns{margin-top:2px}`
                    IntroButtons(flow, onNext = onNext, onBack = onBack, onDismiss = onDismiss)
                }
            }
        }
    }
}

/**
 * カード 1 枚を出す器。**2026-09-07 のユーザー実機所見 2 件をここで受ける**。
 *
 * ## ①「『次へ』をタップするたびにパッと切り替わる」＝遷移が無かった
 * 旧実装は [IntroFlow.card] を直に描くだけで、[IntroController] が flow を差し替えると
 * **再コンポーズで中身が入れ替わるだけ**だった（このファイルにアニメの指定が 1 つも無かった）。
 * ここで [AnimatedContent] を噛ませ、正本 §8 の横スライド（shared axis X）へ翻訳する。
 *
 * ## ②「カードの大きさもバラバラ」＝カードが中身の量で伸縮していた
 * 旧実装のカードは高さを持たず、[Surface] が中身の高さをそのまま纏っていた。
 * **枚ごとに違う固定値が置いてあったのではなく、内容量でそのまま伸縮していた**のが真因
 * （正本モックの実測でも組A は 1 枚目 257px → 3 枚目 380px ＝ 1 枚めくるだけで 1.5 倍に伸びる）。
 *
 * [SubcomposeLayout] で **[IntroFlow.runIndices] の全カードを測り、最も高い 1 枚に器を固定**する。
 * dp の定数に置き換えないこと——最大値は fontScale・端末幅・文言の改稿で動くので、定数にした瞬間に
 * 次に文言を 1 行足した人が黙って溢れさせる。
 *
 * ⚠️ 測るだけのカードは place しないので**描かれはしない**が、**semantics には残る**
 * （2026-09-07 実測＝TalkBack が同じ文を 2 度読み、Compose テストも 8 件が「ノードが 2 つある」で赤）。
 * 配置しないことを a11y の遮蔽と混同しないこと——遮蔽は [clearAndSetSemantics] で明示的に行う。
 */
@Composable
private fun IntroCardDeck(
    flow: IntroFlow,
    orientationVertical: Boolean,
    onOrientationChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val slidePx = with(LocalDensity.current) { Spacing.S32.roundToPx() }
    // 端末の「アニメーションを減らす」設定では遷移を丸ごと外す（既存 NativeReadingScreen と同じ扱い）。
    val reduceMotion = rememberReduceMotion()
    SubcomposeLayout(modifier) { constraints ->
        // ① 揃える高さ＝この回の全カードの本文域の最大（器に収まらなければ器いっぱい）。
        //    線画は高さ 112dp の固定枠なので**測るときは描かない**＝Canvas を回の枚数ぶん焼かずに済む。
        val probeConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        var uniformHeight = 0
        for (index in flow.runIndices) {
            subcompose("probe$index") {
                // ⚠️ **配置しないだけでは a11y から消えない**。測るためだけの複製は place しないので
                // 描かれはしないが、semantics ツリーには残って TalkBack が読み上げ、Compose テストも
                // 「同じ文字のノードが 2 つある」と拾う（2026-09-07 実測＝この対処なしで 8 件が赤）。
                // clearAndSetSemantics で複製の意味づけを丸ごと落とす（測るのに意味づけは要らない）。
                Box(Modifier.clearAndSetSemantics { }) {
                    IntroCardPage(IntroDeck.cards[index], orientationVertical, {}, drawFigure = false)
                }
            }.forEach { uniformHeight = maxOf(uniformHeight, it.measure(probeConstraints).height) }
        }
        uniformHeight = uniformHeight.coerceIn(constraints.minHeight, constraints.maxHeight)

        // ② 現在のカードだけを、その固定高で置く。幅は必ず有界（親が fillMaxWidth の Surface）。
        val width = constraints.maxWidth
        val placeables = subcompose("page") {
            AnimatedContent(
                targetState = flow.index,
                transitionSpec = {
                    if (reduceMotion) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        // 進む＝新しい面が右から入り、古い面は左へ抜ける。［← もどる］ はその鏡像。
                        val enterFrom = if (targetState > initialState) slidePx else -slidePx
                        val slide = tween<IntOffset>(
                            durationMillis = MotionDurationIntroCardFlip,
                            easing = MotionEasingIntroCardFlip,
                        )
                        val enter = slideInHorizontally(slide) { enterFrom } + fadeIn(
                            tween(
                                durationMillis = MotionDurationIntroCardFlipFadeIn,
                                // 退場が終わってから入場する（フェードスルー）＝32dp しか動かさないので、
                                // 同時に薄く重ねると 2 枚の文字がほぼ同じ位置で二重に見える。
                                delayMillis = MotionDurationIntroCardFlipFadeOut,
                                easing = MotionEasingIntroCardFlip,
                            ),
                        )
                        val exit = slideOutHorizontally(slide) { -enterFrom } + fadeOut(
                            tween(MotionDurationIntroCardFlipFadeOut, easing = MotionEasingIntroCardFlip),
                        )
                        // 器の高さは外側で固定済み＝サイズは動かさない（clip=false で余計な切り取りもしない）。
                        (enter togetherWith exit).using(SizeTransform(clip = false))
                    }
                },
                label = "introCardFlip",
            ) { index ->
                IntroCardPage(IntroDeck.cards[index], orientationVertical, onOrientationChange)
            }
        }.map { it.measure(Constraints.fixed(width, uniformHeight)) }
        layout(width, uniformHeight) { placeables.forEach { it.place(0, 0) } }
    }
}

/**
 * スライドする 1 ページ＝カード 1 枚の中身。
 *
 * @param drawFigure false＝寸法を測るためだけの複製（線画を描かない）。図版は高さ固定の枠なので
 *   描かなくても測る高さは 1px も変わらない。
 */
@Composable
private fun IntroCardPage(
    card: IntroCard,
    orientationVertical: Boolean,
    onOrientationChange: (Boolean) -> Unit,
    drawFigure: Boolean = true,
) {
    Column(
        modifier = Modifier
            // 例外なしの規則（`/new-screen`）: 非スクロール面が溢れると**操作要素が画面外へ押し出され
            // 到達手段が消える**のに、溢れは画素に痕跡を残さず golden でも捕まらない。ここは
            // 図版 112dp ＋ 項目 3 つで、小さい画面や fontScale を上げた端末では実際に溢れる。
            // ⚠️ スクロールする器は**本文域だけ**＝点と ［とじる］ は器の外（下端）にいるので、
            // 溢れても操作要素が画面外へ出ない（旧実装はカード全体が 1 つのスクロール器だった）。
            .verticalScroll(rememberScrollState())
            // 正本 `.ocard{padding:26px 22px 18px}` の左右と上（下は器の外側が持つ）。
            // 横の余白を**ページの内側**へ置くのは、スライドが余白ごと動いてカード端で切れるようにするため
            // （余白を器側に置くと、入ってくる文字が余白の中へ唐突に湧いて見える）。
            .padding(start = Spacing.S24, end = Spacing.S24, top = Spacing.S24),
    ) {
        IntroCardBody(card, orientationVertical, onOrientationChange, drawFigure)
    }
}

@Composable
private fun IntroCardBody(
    card: IntroCard,
    orientationVertical: Boolean,
    onOrientationChange: (Boolean) -> Unit,
    drawFigure: Boolean,
) {
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
        if (drawFigure) IntroFigureArt(card.figure)
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

    card.choice?.let { choice ->
        IntroChoiceRow(
            choice = choice,
            selectedIsTrue = orientationVertical,
            onSelect = onOrientationChange,
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

/**
 * 2 択チップの行（正本 `.ochips` / `.ochip`）。
 *
 * **a11y の要**: 見えでは「チップが 2 つ並んでいる」以上のことが伝わらず、TalkBack には
 * 〈どちらか一方しか選べない〉も〈いまどちらが選ばれているか〉も出ない。
 * [selectableGroup] ＋ 各チップの [Role.RadioButton] で**単一選択の群**として読み上げさせる
 * （チェックボックス的に「両方 on にできる」と誤解されるのを、意味づけの側で塞ぐ）。
 */
@Composable
private fun IntroChoiceRow(
    choice: IntroChoice,
    selectedIsTrue: Boolean,
    onSelect: (Boolean) -> Unit,
) {
    Spacer(Modifier.height(Spacing.S16)) // 正本 `.ochips{margin-top:14px}` を離散スケールへ丸めた
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S12), // 正本 `.ochips{gap:12px}`
    ) {
        // 並び順は正本どおり〈false 側 → true 側〉＝左が横書き・右が縦書き。
        IntroChoiceChip(
            label = choice.labelForFalse,
            selected = !selectedIsTrue,
            onClick = { onSelect(false) },
            modifier = Modifier.weight(1f), // 正本 `.ochip{flex:1}`
        )
        IntroChoiceChip(
            label = choice.labelForTrue,
            selected = selectedIsTrue,
            onClick = { onSelect(true) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun IntroChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        // 正本 `.ochip{border-radius:10px}`＝主ボタンと同値（同じ「押せるもの」の語彙に揃える）。
        shape = RoundedCornerShape(IntroPrimaryCorner),
        // 正本 `.ochip.on{background:var(--ai);color:#fff}` / off は枠線だけ＝素地を透かす。
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        // 正本 `.ochip{border:1px solid var(--line)}`＝再訪導線の罫と同じ線トークン。
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Text(
            text = label,
            modifier = Modifier
                .fillMaxWidth()
                // 正本 `.ochip{padding:12px 16px}`。
                .padding(horizontal = Spacing.S16, vertical = Spacing.S12),
            textAlign = TextAlign.Center,
            fontSize = FontPresetTitle, // 正本 `.ochip{font-size:13.5px}`
            // 正本 `.ochip.on{font-weight:600}`＝選択側だけ字面を重くする（色だけに頼らない差）。
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun IntroDots(flow: IntroFlow) {
    if (flow.dotCount <= 0) return
    Spacer(Modifier.height(Spacing.S8)) // 正本 `.odots{margin:6px 0 14px}` の上（上の S16 と合わせて 24dp）
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
