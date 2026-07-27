// 読書クローム（上下バー）の部品＝バー全表示/全退避スナップ settleTopBar と下端バーの1ボタン BottomBarButton。
// なぜ分離したか: NativeReadingScreen.kt の純移動分割（2026-07-27）＝画面骨格だけを残し、部品として
// 自己完結した宣言を役割単位のファイルへ移すため。中身は無改変の純移動（可視性昇格のみ）。
package com.novelreader.ui

import androidx.compose.animation.core.animate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novelreader.ui.theme.FontNavLabel
import com.novelreader.ui.theme.MotionSpringBarSettle
import com.novelreader.ui.theme.ReadingColors
import com.novelreader.ui.theme.Spacing

/**
 * バーを全表示または全非表示へスナップさせる（中央タップのトグル専用）。
 * なぜ自前実装か: enterAlways の内蔵 snap はスクロール消費戦略と一体化しているが、
 * 本実装はスクロール接続そのものを持たない（タップ駆動のみ）ため、animate で直接動かす。
 *
 * @param target 退避先の heightOffset（0f=全表示／heightOffsetLimit=全退避）。
 */
// internal（旧 private）: 純移動のファイル分割で呼び出し元 ChapterScreenContent（NativeReadingScreen.kt）と
// 別ファイルになったため、跨ファイル参照できる最小可視性へ昇格する。
@OptIn(ExperimentalMaterial3Api::class)
internal suspend fun settleTopBar(
    state: TopAppBarState,
    target: Float,
) {
    animate(
        initialValue = state.heightOffset,
        targetValue = target,
        // なぜ StiffnessMediumLow か: オーバーレイ化によりバーの動きが本文レイアウトに
        // 伝わらなくなったため、デフォルトのバウンシー挙動を復元して軽快な触感にする。
        animationSpec = MotionSpringBarSettle,
    ) { value, _ ->
        state.heightOffset = value
    }
}

/**
 * 下端バーの1ボタン（アイコン＋ラベル縦積み）。C①案A の4分割バー（reading-gear-alt-D 案A②の翻訳）。
 * ラベルはゴシック（道具の声＝ADR 0014「明朝は題字と本文」＝既定 sans をそのまま使う）・[FontNavLabel]。
 * @param accent 藍で強調するか（表示設定＝画面唯一の強調・原則4「一画面一強調」）。
 * @param enabled false で disabled トークンへ淡色化しタップ無効化（前後章の目次未ロード中）。
 */
// internal（旧 private）: 純移動のファイル分割で呼び出し元 ChapterScreenContent（NativeReadingScreen.kt）と
// 別ファイルになったため、跨ファイル参照できる最小可視性へ昇格する。
@Composable
internal fun RowScope.BottomBarButton(
    icon: ImageVector,
    label: String,
    colors: ReadingColors,
    accent: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    // 淡色化の優先順位: 無効（機能不能）＞ accent（強調）＞ 通常。無効時は強調より不能表示を優先する。
    // 無効色は placeholder（「無効ボタン文字」用の専用シェード＝alpha 沈めでなく役割別トークン・Design/10§9）。
    val tint = when {
        !enabled -> colors.placeholder
        accent -> colors.accent
        else -> colors.topBarIcon
    }
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            // ラベル Text が可視の読み上げ名を担うため、アイコンは装飾扱い（null）にして二重読み上げを防ぐ。
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        // モックのアイコン↔ラベル間 3px を離散スケールへ最近傍丸め（round-half-up＝4dp）。
        Spacer(Modifier.height(Spacing.S4))
        Text(
            text = label,
            color = tint,
            fontSize = FontNavLabel,
            fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
