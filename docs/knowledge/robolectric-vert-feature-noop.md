# Robolectric のフォント描画は fontFeatureSettings="vert" が no-op

★★ 2026-07-17 — JVM(Roborazzi) golden では句読点の縦字形・位置替えが出ない＝golden は構造の回帰固定用・vert の効きは実機でのみ検証可能

**症状**: VerticalParagraph の Roborazzi golden（P2）で、実機なら vert で右上へ寄る句読点「、。」が
セル中央のまま描かれる。回転（Canvas rotate）・縦中横（textScaleX）・ルビ配置は正しく出る。

**真因**: Robolectric のネイティブグラフィックスは HarfBuzz の OpenType feature 適用が実機と等価でなく、
`Paint.fontFeatureSettings="vert"` による字形差し替えが効かない（バンドルフォントも実機フォントと別物）。

**対処**: golden は「列送り・回転・縦中横・ルビ按分」など**自前で座標変換する層の回帰固定**として使い、
vert に委ねた字形（句読点・括弧の縦字形）の正しさは実機目視/スクショで確認する（P0-1 の実測が正本）。
golden が実機の見た目と一致しない前提を、golden 追加時のレビュー観点に含めること。

一次データ: `android/app/src/test/screenshots/VerticalParagraph_punct_*.png`（、。が中央のまま）／
実機側の正＝`docs/knowledge/vert-feature-pgem10-coverage.md`
