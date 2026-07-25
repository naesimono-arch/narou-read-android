package com.novelreader.ui.theme.skins

import com.novelreader.ui.theme.SkinTokens

/**
 * スキンK「明快」＝新デフォルト（2026-07-23・plan `default-ui-clarity-K-2026-07-23.md`）。
 *
 * なぜ D への委譲か: K の本質は「構造の明快化」（ラベル付きボトムナビ・明示タイトル・設定画面の新設）で
 * あり、パレットは D（和紙地・藍）をそのまま用いる裁定（確定事項7）。色トークン束を複製すると
 * D の色調整が K に追従しなくなるため、トークン層は D へ全委譲し、K 固有の構造色（ナビ選択ピル等）は
 * 各 K 画面が LocalSkinTokens 経由の値から導出する。
 * モック正本: docs/design-candidates/skins/{bookshelf,discovery,settings,toc}-K.html
 */
object SkinK : SkinTokens by SkinD
