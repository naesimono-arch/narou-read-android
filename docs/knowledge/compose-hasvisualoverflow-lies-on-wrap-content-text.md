# `hasVisualOverflow` は wrap-content の Text で嘘をつく（省略の判定に使わない）

**結論**: 版面テストで「字が省略されたか」を判定するとき、`TextLayoutResult.hasVisualOverflow` を使ってはいけない。
**自分の実測幅ちょうどで配置される Text**（`Modifier.weight(1f, fill = false)` を持つ子、および weight を持たない子）は、
**全字が見えていても `hasVisualOverflow == true` を返す**。判定には可視文字数
`getLineEnd(lineIndex = 0, visibleEnd = true)` を使い、`可視文字数 == 全字数` かどうかで見る。

## なぜ踏むのか（踏まない条件のほうが特殊）

`weight(1f)`（＝`fill = true`・CSS の `flex:1 1 0` 相当）を付けた Text は器いっぱいに引き伸ばされるため、
**幅に余りが生まれてフラグが立たない**。つまり「たまたま踏まない」側が既定になっている実装が多く、
`fill = false` や weight 無し（＝`flex:0 1 auto` / `flex:0 0 auto` 相当）へ寄せた瞬間に初めて表面化する。

⚠️ **真因は未確定**（丸め由来と推定）。ここで確定しているのは「wrap-content 幅の Text では全字可視でも true が返る」
という**外形の事実**だけで、それは 2 回の独立した実測で再現している。

## 実測（同じ轍を 2 回踏んでいる）

| 日付 | 場所 | 状況 |
|---|---|---|
| 2026-08-17 | `ui/skins/k/SettingsRowWidthLayoutTest.kt`（KDoc :44-48） | 「字が全部見えている 1.0 でも同フラグが true」。`getLineEnd(0, visibleEnd = true)` だけを使う方針をその場で決めたが、**KDoc に埋めたまま knowledge 化しなかった** |
| 2026-09-03 | `ui/discovery/SelectedKeywordsBarHeadLayoutTest.kt` | 検索の選択キーワード帯を案 A' へ寄せる便で再発。旧実装は `weight(1f)` だったので踏まず、A' で `fill = false` へ戻した瞬間に踏んだ。**等倍でも「省略されている」と誤検出**し、実装が正しいのにテストだけが赤くなった |

2 回目は「実装の幅配分が壊れた」ように見えるため、**真因の切り分けを誤ると正しい実装を壊しにいく**。
そこが最大の危険で、この知見を独立ファイルにした理由でもある。

## 使う側の作法

- 省略の有無 → `getLineEnd(0, visibleEnd = true)` と全字数の比較。
- 「縮んでよい側」と「縮んではいけない側」がある版面では、**両方に断言を置く**
  （縮む側は `可視 < 全字` かつ `>= 1`＝縮退経路を必ず踏ませる／縮まない側は `可視 == 全字`）。
  片側だけだと、両方が縮まない実装に退行しても緑のまま抜ける。
- `maxLines = 1` を指定した Text の `lineCount` は構造上 1 を超えられない＝
  **`lineCount == 1` の断言に検出力は無い**（安全網として残すのはよいが、「1 行が保たれる」ことの
  実質的な番人にはならない。同じ帯に居ることを矩形で見るほうが効く）。

## 関連

- 版面テストの再現条件の張り方（fontScale・qualifiers・`@GraphicsMode(NATIVE)` が要る理由）は
  `ui/discovery/SelectedKeywordsBarHeadLayoutTest.kt` と `ui/skins/k/SettingsRowWidthLayoutTest.kt` が実例。
- LEGACY モードでは文字幅が文字数に比例して溢れ自体が起きないため、**溢れを見るテストは NATIVE 必須**。
