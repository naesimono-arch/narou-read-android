# orchestration batch 2026-08-05 — 実機系・ユーザー必須系以外の残タスク一括消化

**対象ブランチ: `verify/device-2026-07-29`**（この worktree で作業。Kotlin 2.x 便のみ ADR 0029 の指定により別ブランチへ隔離）

ユーザー指示: 「難易度が高い実装は fable に委譲。実機系とユーザー必須系以外は全てやろう」（2026-08-05）

## 波構成（Gradle は同時1つ＝memory `feedback-serialize-heavy-jobs`）

- **波1**（並走・Gradle 保持者は便1のみ）
  - 便1 [fable]: 復旧・削除・再取込領域の3件束ね（同一真因系）
    ①案X 本体（SAF ツリー→sha256 照合→復元→権限永続化）
    ②なろうPDF 由来本の「守れない約束」解消（cache PDF 直接再変換を優先経路に・自動DL禁止の裁定内で）
    ③削除時の cache/pdf_import 残留（②と整合させて設計）
  - 便2 [fable]: Web 取込 ncode null → 同一作品2枚並び（調査→実装の2段・Gradle 禁止＝監督ゲート）
  - 便3 [opus]: settings-D.html 3点逆同期 ＋ 読書「本文調整トグル説明」のモック候補作成（HTML のみ）
  - 監督直: L3 不採用 ADR（0030）執筆・lint スイープ受領
- **波2**（便1完了後・Gradle 枠移譲）
  - 便4 [opus]: 小粒3件束ね＝MigrationTest coverage-hole／M 面トグル prefs 不反映（調査→修正）／
    BookshelfScrollBenchmark 面指定。**確定事項: シーダー（LibrarySeedReceiver）が K のキー `k_grid_view` も書く方向**
    （benchmark ビルドは ADR 0027 ゲートで K クランプ＝ベンチ側でスキンを変える案は機能ゲート上不成立）
  - 監督: 便1/2 の機械検分＋ゲート再実行＋コミット確認
- **波3**（全 Gradle 終了後）
  - 便5 [fable・worktree 隔離]: Kotlin 2.x ＋ compose compiler ＋ Roborazzi ＋ tracing-ktx の1便バンプ（ADR 0029）

## 見送り（理由つき）

- 実機系: TabSwipeBenchmark 較正／K 4枚モック逆同期（実機スクショ起点）／⋮メニュー M 再現／
  jank 残③の実装（実機計測なしで検証不能・高さ設計の作り直しを伴う）
- 裁定系: 章見出し話数ラベルの意匠（§3-2）／M/P/J 章扉の二重表示（同）／discovery-detail-D 翻訳ズレ5件
  （前回「意図的に入れなかった」＝あらすじ押し出しトレードオフの裁定待ち）／横向き固定値棚卸し（§3-1 待ち）／
  U1 続き取得導線（UI 新設＝モック裁定ループが必須の入口から）
- 凍結: 汎用DL対応面拡大・richness モック（正本昇格時）
