# 実機調査 followup 2件（2026-07-16・機序特定/切り分けのみ・実装なし）

> 実機必須の残調査2件をサブエージェント委譲で消化した一次情報。P2（遷移jank）の Perfetto 結果は
> `reading-transition-jank-measurement-2026-07-16.md` 末尾の「Perfetto escalate」節が正本。
> 環境: OPPO PGEM10（192.168.1.210:5555）・v19 蔵書 DB 無傷・uninstall/connectedAndroidTest 不使用。

## 1. 残5＝読書クローム出没の視覚ノイズ2件 — 機序特定（修正は意匠裁定待ち）

**手法**: この端末は `screenrecord` が OEM 制約で動作不能（mp4/stdout とも Permission denied/ハング）
→ `screencap` バーストキャプチャ（実測上限 約4〜5fps）で代替し、実ピクセル値で判定。
中央タップ・スワイプ復帰の両経路 × ダーク/ライト両テーマで計108フレーム。

### ②ステータスバー明滅（黒⇄明灰）＝確定
- 実測: クローム表示時はステータスバー領域(y0-88)が灰（ダーク[124,125,124]／ライト[114,115,114]）、
  没入時は**純黒[0,0,0]**。ライトでは白紙本文[245,244,242]の直上に純黒155pxの帯が出没
  ＝ユーザー原文「目がちかちか」に一致。黒→灰の遷移は約450ms（3フレーム捕捉）。
- 機序: `NativeReadingScreen.kt:826-835` の LaunchedEffect が collapsedFraction<0.5 反転で
  `controller.show/hide(systemBars())` をトグル。Edge-to-Edge（`MainActivity.kt:87`）配下で、
  表示時は TopAppBar の topBarBackground（灰）が statusBarsPadding で塗るが、
  没入時はバーが graphicsLayer{translationY} で退避＋hide → 最上155pxが
  **アプリの紙色が届かない OS/ウィンドウ既定の純黒**で残る。
  ＝「紙色でクリアされない最上帯」が hide/show に同期して黒⇄灰フリップする。

### ①本文ちらつき＝独立機序は棄却（②と同根）
- 実測: 全108フレームで本文先頭行のY座標は260で不変＝インセット起因リフロー・再コンポーズ空白とも否定。
  コードも整合（`ChapterContent.kt:133-138` が `*IgnoringVisibility` インセットで意図的に防いでいる／
  上下バーは translationY オーバーレイで本文レイアウトへ不伝播）。
- 知覚実体は②と同根＝本文上端直上の高コントラスト帯反転＋バーのスライドが本文端を横切ること。
- 限界の明示: キャプチャ上限4〜5fpsのため200ms未満のサブフレーム白フラッシュは原理上撮り切れていない
  （ただし構造上、本文を空白化する経路が存在しない＝可能性は低いと判断）。

### 修正方向（未実装・意匠絡み＝ADR 0005/0014 接地＝/visual-language ゲート必須）
1. **没入時のステータスバー帯をアプリ背景（紙色/テーマ色）で塗る**（本命候補。API 35 で
   `statusBarColor` 非推奨のため最上端まで届くテーマ色描画で対処）。
2. systemBars hide/show をやめ自作バーのみ出没（時計/電池の可視契約が変わる＝バー契約の再裁定要）。
3. hide/show 遷移へのクロスフェード等の対症（黒帯の真因を残すため非推奨）。

## 2. スナックバー「閉じる」タップ無反応疑い — 白（真のバグではない）

**結論**: コード配線は健全・**正確な bounds 中心タップなら「閉じる」は確実に効く**（実機で確認）。
従来「無反応に見えた」正体は次の複合:
- **①adb タップの座標精度**（重複スナックバーは actionLabel あり duration 未指定＝Indefinite で居座るため、
  外すと「押しても消えない」に見える。`BookshelfScreen.kt:275`）
- **②再表示ループ（実機で確定再現）**: `Channel.BUFFERED`（`NovelReaderApplication.kt:70`）の直列消費により、
  複数重複を一括投入すると「閉じる」タップ直後に**同座標へ次の同型スナックバーが即出現**
  ＝「効いてない」誤認を生む。スワイプ dismiss（f8ffff1）も正常動作（対照確認済み）。

**検証手法（蔵書汚染ゼロ）**: 実機DB（wal/shm 込み pull・読み取りのみ）の `contentSha256` と
ローカル `sample_pdfs/` の sha256 照合で byte 同一の既存本（N5368ML・N1453LW）を特定→
同一PDFの再取込で変換前 Duplicate（`DefaultBookRepository.kt:207-215`）を発火＝新規本は作られない。
books 件数は全工程で 9 のまま不変。push した検証用PDFは削除済み。

**残る改善候補（要否はユーザー判断）**: 複数重複時の連続再表示 UX（例: 同型メッセージの集約
「N件は取り込み済みです」化、または重複通知の duration を有限化）。SwipeToDismissBox の
タップ先食い仮説はコード・実機の両面で棄却。
