# M3 の AlertDialog はアクションが1行に収まらないと段を**逆順**に積む（確定が上・否定が下）

**重要度**: ★★
**確定日**: 2026-08-21（再取込ダイアログの実機観測 → material3 **1.3.2**＝compose-bom 2025.07.00 の bytecode 実査で裏取り）

1行要約: `AlertDialog` のアクション行（`AlertDialogFlowRow`）は、入り切らないと折り返すだけでなく
**折り返した段を逆順に置く**——`confirmButton` 側が上段、`dismissButton` 側が下段になる。
仕様どおりの動作（確定を上に見せる意図）だが、**書いた側の想定と並びが食い違う**。

## 症状の見え方

- 実機で「**確定ボタンだけが上段に独立し、残りのボタンが下段で揃う**」という一見不可解な並びになる。
- レイアウトを書いた側は「`dismissButton` に入れた2つ → その右に確定」の1行を想定しているので、
  ボタンの実装・順序・意匠のどれを見ても原因が出てこない。
- **幅が足りているうちは正しく見える**＝等倍・短文でだけ検証していると出ない。日本語の長い文言・
  `fontScale` 拡大・狭いダイアログで初めて現れる。golden を撮るなら **2.0 も撮る**。

## 機序（bytecode 実査）

1. `AlertDialogContent` はアクションを **`dismissButton` → `confirmButton` の順**で composition に載せる
   （`AlertDialogKt$AlertDialogImpl$1$1$1` で 2つの slot ラムダがこの順に `invoke` される）。
   よって measure 順も dismiss が先・confirm が後。
2. `AlertDialogFlowRow` は子を順に詰め、内寸に入らなくなると段を閉じて次の段へ移る。このとき
   **閉じた段を段リストの先頭へ挿入する**（`List.add(0, …)`＝逆アセンブルで `iconst_0` → `List.add(I,Object)` を確認）。
   一方、**段の cross 位置（y）は末尾へ昇順に追加**される。
3. 配置は「段リスト[i] と 位置リスト[i]」を同じ添字で組む。段リストだけが反転しているので、
   **最後に閉じた段（＝confirm を含む段）が y の先頭＝最上段**に来る。

つまり反転は事故ではなく、**M3 が「確定を上に置く」ために意図的にそうしている**。
なお内寸の目安は小さい——`DialogMaxWidth` は **560dp**（同 bytecode で確認）で、ここから左右パディングを引いた幅しかない。
段間は `ButtonsCrossAxisSpacing` = **12dp**、ボタン間は `ButtonsMainAxisSpacing` = **8dp**（同上）。

## 引き金は「3つ目のボタン」ではない（一般化）

引き金は**アクションの個数ではなく、2スロットの合計要求幅が内寸を超えたかどうか**。次はすべて同じ反転を生む:

- **スロットに `Row` や `Column` を詰めて複数アクションを入れる**——合成物は flow row から
  **1個の巨大な measurable** に見え、その中では折り返せない（今回は幅 544px の1要素として測られていた＝修正コミットの実測）。
  「2ボタンだから2個ぶんの幅で折り返してくれる」は成立しない。
- 文言が長い／翻訳で伸びた／`fontScale` が上がった（要求幅は比例して伸びる）。
- ダイアログが狭い（小画面・横向き・分割画面）。

**M3 の AlertDialog には 3つ以上のアクションを表現する口が無い**（スロットは confirm と dismiss の2つだけ）。
2つに押し込んで flow row の折り返しに任せた時点で、並びは幅次第の運任せになる。

## 対処

**折り返し任せを断つ**＝アクションを1つの `Column`（右揃え・段間 12dp）にまとめて **`confirmButton` へ渡し、
`dismissButton` は渡さない**。段の数・順序が版面の意図どおりに固定され、幅にもフォントスケールにも依存しなくなる。
実物＝`android/app/src/main/java/com/novelreader/ui/BookshelfScreen.kt` の `ReimportScanDialog`
（版面の不変条件は `android/app/src/test/java/com/novelreader/ui/ReimportScanDialogStackTest.kt` が固定）。

関連: `sheet-without-verticalscroll-hides-actions.md`（同じダイアログの版面でも、**縦**に溢れる方は
画素に痕跡が出ず走査で捕まらない。こちらの反転は画素に出るので golden で捕まえられる）。
