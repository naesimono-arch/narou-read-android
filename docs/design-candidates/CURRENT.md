# CURRENT — 画面ごとの「現行正本モック」索引

> **なぜ**: 正本が世代を重ねるたび「どれが最新か」が handover/ADR の散文注記にしか残らず、
> 参照の起点次第で旧世代を掴む事故が反復した（2026-07-16 複数選択削除モック・2026-07-17 スキン
> ギャラリーの D セル＝fusion-D を掴み栞・最終形を見落とし）。**新提案・比較・翻訳の下敷きは
> 必ずこの索引から引く**。モックの追加・退役・逆同期をしたら**同じコミットでこの表を更新**する
> （更新漏れの表は散文注記より害が大きい——mock-drift-inventory 2026-07-16 恒久ルールの拡張）。

| 画面 / 層 | 現行正本 | 注記 |
|---|---|---|
| 本棚（画面構造） | `discovery/bookshelf-fusion-D.html` | ⚠️ 発見帯の完全退避のみ未反映（handover 残1 確定待ち・意図的据え置き） |
| 本棚（書影＝栞・最終形） | `bookshelf-shiori-grid-D.html` | 先端174種・決定論選択。**見た目の最新はこちら** |
| 本棚（グリッド⇄リスト整合） | `bookshelf-shiori-consistency-D.html` | 1冊=1色相の共有規約 |
| 読書 | `reading-D.html` | モーションは ADR 0005 §B＝モック対象外 |
| 目次 | `toc-D.html` | |
| 設定 | `settings-D.html` | |
| 発見系 | `discovery/discovery-{home,genre,search,detail}-D.html` | ⚠️ InfoText AA 未反映（handover 留置） |
| 装いの間（スキン選択） | `skins/wardrobe-D.html` | 入口は本棚 topbar のみ（ADR 0021 決定7） |
| スキンC 夜行 | `skins/bookshelf-C.html`・`skins/reading-C.html` | 色トークン層のみ実装済み・構造/演出層は別タスク |
| スキンK 本棚（横画面グリッド） | `skins/bookshelf-K-landscape.html` | 5列＝案L5（2026-07-26 裁定）。縦正本 `skins/bookshelf-K.html`（2列改A）との差は列数のみ・破線/余白/キャプション同値 |
| スキンM 星図・P カートリッジ・J ポータル | `skins/{bookshelf,reading,discovery,toc,settings}-{M,P,J}.html` | **実装対象の正本**（2026-07-17 確定・Compose未実装）。P目次＝はっちゃけ版採用 |
| スキン候補（ステージング） | `skins/candidates/` | **正本ではない**。Q読書の庭＝差し戻し保留・L/N/O/R/S＝保留・hatchake/＝P試作の不採用分（目次のみ採用済み） |

退役・非正本（提案基盤に使わない）: `bookshelf-D.html`（fusion 前の旧骨格・退役注記済み）。
