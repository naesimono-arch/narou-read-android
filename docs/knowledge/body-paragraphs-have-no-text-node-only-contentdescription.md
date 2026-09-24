# 本文段落は書字方向を問わず a11y の `text` を持たない（`By.text` / `onNodeWithText` が原理的に空振りする）

**確定日**: 2026-08-21（実機 macrobenchmark の全5走行 fail から遡って構造で確定）
**一行要約**: ルビの読み置換のため本文段落は `clearAndSetSemantics { contentDescription = spoken }` で
`text` を捨てている＝**縦書きだけの話ではない**。本文へ着地したことを `text` 系セレクタで判定してはいけない。

## 機序

読み上げは「当て字を著者読みへ置換する」必要がある（『魔剣(つるぎ)』を『まけん』と読ませない）。
`semantics { text = … }` は Text プロパティの merge が**追記**で二重読みになるため、
子の `text` を `clearAndSetSemantics` で捨てて `contentDescription` 1本に置き換える設計を採っている。

⇒ **段落ノードに `text` は存在せず `contentDescription` にだけ全文が出る。**

- 横書き `ChapterContent` → `RubyText`（`clearAndSetSemantics`）
- 縦書き `VerticalChapterContent` → 段落ごとに `clearAndSetSemantics`

**両方とも同じ**。書字方向は関係ない。

## 効くセレクタ・効かないセレクタ

| 対象 | `By.text*` / `onNodeWithText` | `By.desc*` / `onNodeWithContentDescription` |
|---|---|---|
| 本文段落（全文・任意の一節） | **効かない**（縦横とも） | 効く |
| 章見出しの題（`splitChapterTitle` の題側） | **効く**——横書き `ChapterContent` の `ChapterHeader` が素の Compose `Text` で描くため | 題は `text` 側に出る |

⚠️ 章見出しが効くのは**横書きの既定ヘッダに限る**。ヘッダはスキン別
（`ChapterHeaderJ` / `ChapterHeaderM` / `ChapterHeaderP`）＝意匠を変えれば `text` の有無ごと変わる。
「本文に着地したか」の徴として使うなら、その依存をコメントに書き残すこと。

## 実害（この知見が無くて踏んだ形）

`TabSwipeBenchmark` の着地判定 `By.textStartsWith("第1章")` が、端末に残った prefs が縦書きだったために
**全5走行の iter000 で空振り**した（2026-08-19）。当時の fail 文言は「progress リセット未反映」を疑う
書き方で、実機 dump では progress は正しく張れており、真因から遠い場所を数日探すことになった。

**再発の型**: これを「縦書きだと空振りする」とだけ覚えると、横書きで本文の**一節**を `By.text` で
探した瞬間に同じ空振りを踏む（見出しは通るので「効いている」と誤認しやすい）。
機序は書字方向ではなく**ルビ読み置換のための semantics 設計**にある。

## 付随: 計測面は毎回シードで固定する

書字方向は `app_prefs` の単一 Boolean（全書籍共通）で、**端末に残った値がそのまま効く**。
描画経路が別物（縦＝自前組版＋Canvas ／ 横＝Compose Text）＝固定しないと**同じベンチが別の面を測る**。
`clearAndSeedLibrary` は `gridMode` と同じ規約で `verticalMode` も送る（既定 false＝予算を較正した横書き面）。

関連: `compose-offscreen-nodes-pruned-from-a11y-tree.md`（退避で a11y ツリーごと消える別クラス）。
