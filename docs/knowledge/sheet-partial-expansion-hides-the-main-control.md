# ボトムシートの部分展開起動は「主役が折り目の下」で開く——絵は正しいのに開いた瞬間だけ間違う

**重要度 ★★☆／表示設定シート便の申し送り（2026-08-20 裁定）を 2026-08-26 に類型化**

1行要約: `rememberModalBottomSheetState()` の既定は `skipPartiallyExpanded = false`＝中身が高いシートは
**`PartiallyExpanded`（画面のおよそ半分）で開く**。中身のレイアウトも到達性も壊れていないのに、
**そのシートの主役が最初の1画面に入っていない**。しかも現行の機械ゲートは**構造的に枠を見ていない**ので永久に緑。

## なぜ既存知見への追記でなく別ファイルにしたか

近縁は `sheet-without-verticalscroll-hides-actions.md`（非スクロール面の溢れ）。**別類型と判断した**——
壊れている層・症状・処方・見つけ方の4つとも違い、しかも**片方の是正がもう片方を隠す**関係にあるため
（`verticalScroll` が在ると指で送れてしまい「動いている」と読める）。あちらの主張は
「溢れは画素に痕跡を残さない」で、こちらは**痕跡は出るが誰も撮っていない**＝同居させると芯が濁る。

| | 溢れて到達不能（既存知見） | 部分展開で主役が折り目の下（本稿） |
|---|---|---|
| 壊れている層 | シートの**中身**（スクロール容器が無い） | シートの**枠**（初期アンカー） |
| 症状 | 操作要素に**到達する手段が消える**（機能の欠落） | 到達はできる（指で上げれば出る）が**開いた瞬間に主役が見えない**（意図の欠落） |
| 発火条件 | fontScale を上げる・文言が伸びる等、**可変長が効いたときだけ** | 中身が画面の約半分を超えた瞬間から**常に**（fontScale 1.0 でも起きた） |
| 処方 | `verticalScroll` を付ける＝**機械的・意匠裁定不要** | 何を初手で見せるかを決める＝**意匠の裁定事項**（実装の裁量ではない） |

## いつ発火するか（次便が踏む条件）

- `ModalBottomSheet` に **`sheetState` を渡さない**、または `rememberModalBottomSheetState()` を引数なしで呼ぶ。
  どちらも `skipPartiallyExpanded = false`＝中間アンカーが生きる。
- 中身の高さが画面の約半分を超える。**超えたかどうかは意匠を足すたびに変わる**＝
  「作った日は全部見えていた」シートが、項目を1つ足した日から静かにこの型へ落ちる。
- 実測の具体例＝表示設定シートは fontScale 1.0 でも**文字サイズのつまみが 7px しか覗いていなかった**
  （主役はスライダー3本とライブプレビュー）。全高起動へ裁定した理由は `ReadingSettingsSheet.kt` の
  `ModalBottomSheet(` 直前のコメント（`rg -n skipPartiallyExpanded` で引ける）に残してある。

## なぜ機械走査で捕まらないか（＝何を見れば気づくか）

**壊れる場所（シート枠）が、テストの対象から明示的に除外されている**。これは手落ちではなく判断の結果で、
だからこそ放っておくと恒久に緑のまま残る。

- **golden**: `ReadingSettingsSheetScreenshotTest` は `ReadingSettingsSheetContent` を素の `Box` へ直接組む。
  KDoc に除外理由が書いてある——「`ModalBottomSheet` 枠は Robolectric で不安定（別ウィンドウ描画・部分展開）」。
  つまり**部分展開そのものが除外の理由**＝この不具合は golden の視野の外側に置かれている。
  `NcodeLinkSheetScreenshotTest`・`SearchConditionSheetScreenshotTest` も同じ形。
- **到達性テスト**: `NcodeLinkSheetReachabilityTest` も Content 直組みで、`performScrollTo` してから
  `assertIsDisplayed` を見る。実機でも指で上げれば出る以上、**この型では到達性は真のまま**＝赤くならない。
- ⇒ 残る検出手段は**実機／エミュでシートを開いた瞬間の1枚**だけ。シートに項目を足す変更を出すときは、
  スクロールせずに撮った1枚を目視の関門へ入れる（`/device-verify`・`/emulator-verify`）。

## 現況（`rg -n 'ModalBottomSheet\(' android/app/src/main/java` で引ける3か所）

- `ReadingSettingsSheet.kt`（表示設定）＝`skipPartiallyExpanded = true` 明示・裁定済み
- `discovery/SearchConditionSheet.kt`（検索条件）＝同上。**別の害も同時に潰している**——中間アンカーが在ると、
  カスタム入力で IME が出て `adjustResize` でウィンドウが縮んだ瞬間にアンカーが再計算され、
  **全開のシートが勝手に真ん中まで落ちる**（理由は同ファイルの `sheetState` 直前コメント）
- `NcodeLinkSheet.kt`（なろう紐付け）＝`sheetState` を渡していない＝**既定のまま＝未裁定**。
  主役（候補リストと「紐付け」）が初手で見えるかは実機で確かめて決める

## 処方（と、そこで踏みやすい罠）

1. **明示的に決める**。`skipPartiallyExpanded` を書かないという選択をしない——既定に任せた結果は
   「意匠が中間アンカーを選んだ」ではなく「誰も決めていない」であり、後から読んでも区別が付かない。
2. **何を初手で見せるかは正本モックが決める**（`docs/design-candidates/` のシート高に従う）。実装側で
   「半分くらい見えていれば十分」と判断しない。
3. ⚠️ **全高が嫌でも `ModalBottomSheet` の `modifier` に高さ上限を掛けてはいけない**——`draggableAnchors` が
   読む constraints ごと縮み、シートが上端に張り付く。上限は**中身側の Column** に `heightIn(max = …)` で掛ける
   （`SearchConditionSheet.kt` の中身 Column がその形。「85%上限は内容側に掛ける」のコメントが目印）。

関連: `sheet-without-verticalscroll-hides-actions.md`（同じシートで同時に踏みうる別類型）／
`/new-screen` §3「シート/ダイアログ」（着手時のチェックリスト側の正本）
