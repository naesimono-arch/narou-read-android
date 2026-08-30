# 縦書き組版の正しさ担保は全部「実装系譜内」— 公開規範との突合が未実施

★★★ 2026-08-30。自前組版層 `typeset/`（＋`VerticalParagraph`/Canvas 直描き）の正しさは現状
〈自分のコーパスでの実測〉〈実装出力を固定した回帰テスト〉〈人間の目視〉の3点だけで担保されており、
**外部規範との突合が構造的に存在しない**。リポジトリ全体で JIS X 4051 / JLReq への参照は0件
（唯一の規格語が `typeset/CharClass.kt` の「JIS 縦組み慣行」という一言＝条文参照ではない）。
「一貫した間違いは内部矛盾ゼロなら見えない」構造（`independent-reimpl-anchoring-method.md`）の縦書き版。

## どこが弱いか

- **文字クラス（正立/回転/位置替え）`CharClass.kt`**: 出典が実機130字計測＋自コーパスの出現回数
  ＝**頻出字は強いがロングテールの希少字でクラス誤り（横倒し/正立の取り違え）が潜みうる**。最有力の検出面。
- **禁則 `LineBreaker.kt`**: 行頭・行末禁則の文字集合が実装者判断の直書き。規範の分割禁止規則と未照合。
- テスト期待値も実装出力の固定（人工メトリクス盤面・自コーパス回帰）＝規範由来ではない。
- **縦書き golden は二重に弱い**: record 型の自己参照（`golden-record-bakes-in-regressions.md`）に加え、
  `VerticalChapterContentScreenshotTest.kt` 自身が「Robolectric の縦書き字形は実機と一致しない可能性」を自認
  ＝**縦書き描画は golden の緑を信用できない領域**。実機検分（/device-verify）でしか閉じない。

## 対処: 独立再実装は不要、規範が既製で存在する

抽出コアで「web 原文」に相当する外部オラクルが、組版では最初から機械可読で公開されている:

1. `CharClass.kt` の文字集合 ⇄ **Unicode UTR#50 の `VerticalOrientation.txt`**
   （UCD 同梱: `https://www.unicode.org/Public/UCD/latest/ucd/VerticalOrientation.txt`。
   Vertical_Orientation プロパティ U/R/Tu/Tr）。
2. `LineBreaker.kt` の禁則集合 ⇄ **JLReq（W3C「日本語組版処理の要件」`https://www.w3.org/TR/jlreq/`）**
   の行頭禁則・行末禁則文字クラス表（JIS X 4051 は有償だが JLReq で実用上足りる）。

照合スクリプト1本級のコスト。⚠️ **規範との差分＝即バグではない**——縦書き文庫的慣行 vs 規格の方針差がありうるし、
意図的簡略は明文化済みのものがある（ぶら下げ未実装＝`LineBreaker.kt` に後続フェーズと明記）。
産物は「差分の棚卸し→人間裁定」であり、機械が自動で直してよい類ではない。
