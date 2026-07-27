# 2026-07-27 リファクタ大バッチ（/orchestration）

> **完了（2026-07-27）**: Wave 1〜3 全消化・16コミット（正本＝git log）。各波ともゲート（testDebugUnitTest／lintDebug／トークン検査）を監督が直接再実行して GREEN 確認。残タスク・見送り裁定・裁定候補は handover.md「リファクタ/技術的負債」へ移記済み＝以下は経緯の一次情報として保全。

**対象ブランチ: `device/verify-round-0725`**（worktree `/home/qingj/wt/device-verify-round-0725`・ext4＝素の gradlew 可）

## 入力
ユーザー提示の棚卸しリスト（すぐやる／今週やる／設計判断要／急がない／測ってから決める）。全件処理の指示。

## 確定事項（2026-07-27 ユーザー裁定・AskUserQuestion 回答）
1. **ncode 型化＝実施**（Ncode に storageKey/urlSlug・21箇所張り替え・値は1bitも変えない）
2. **スキン層OO化＝①束ね＋②Immersive/Listing sealed の両方**（ファクトリは skins/ 側・ShelfSelection のみ @Stable）
3. **compileSdk＝35へ**（AGP 8.6.1 のまま。targetSdk 34 据え置き＝実行時挙動不変。依存バンプは別判断・36直行は Play トラックで）
4. **追加スコープ＝純移動の大型分割＋Repository/domain 分割を実施**。**Baseline Profile と計測・調査群は今回見送り**（ユーザー選択外）

## Wave 1（並走中・ファイル素は不交差・子は commit / Gradle 実行 禁止）
- **T**: `.github/workflows/ci.yml` 新設 ＋ `tools/check_design_tokens.py` SKIP可視化（全滅SKIP検知で exit 1）
- **K**: `android/gradle.properties` 4行 ＋ 本棚リスト contentType 全該当
- **U**: テスト3本（RubyConverter / HtmlEscape / BookIdentifiers＝ncode型化の安全網を兼ねる）
- **P**: PrefKeys 集約（キー値は1bitも変えない）＋ Predictive Back ＋ AdapterHealthMenuSection 削除（呼出ゼロ grep 確認後）

## Wave 2（依存グラフ＝ファイル所有の衝突回避で直列化）
- **C（即起動・衝突なし）**: compileSdk 35（app/build.gradle＋天井コメント是正）＋ ShioriCover→ShioriTips 純移動
- **N（P 完了後）**: ncode 型化（P の PrefKeys 張り替えと viewmodel 面で交わる恐れ→直列）。
  **U班の実地知見（仕様へ反映済みの前提）**: ncode 型の実体は `narou/model/Ncode.kt`（BookIdentifiers.kt は BookId/ChapterFilename のみ）。
  正規化は型でなく用途サイト分散＝narouWorkUrl（trim+lowercase）・NcodeLinkSheet（trim+uppercase）・isValidNcode（trim+大小無視regex）。
  現行挙動の固定テスト＝BookIdentifiersTest（新設）＋ContinuationLogicTest（既存）。「型は正規化しない」を固定してあるため、
  型へ正規化を集約するとこの固定は**意図的に**落ちる→N がテストも同時に改訂する（挙動変更でなく契約の移動と明記）。
- **V（P 完了後）**: NativeReadingScreen 純移動分割（P が BackHandler で同ファイルを触るため直列）
- **S（K・P 完了後）**: スキン固有状態移設＋スキンOO化①②＋EmptyBookshelf 移設（BookshelfScreen/skins/玄関＝K・P と交差）
- **R（N 完了後）**: DefaultBookRepository 6責務分割＋SearchDraft/ShelfItems→domain/（N が同 Repository を触るため直列）

## N班の残余（裁定候補・意図的に未修正＝挙動温存）
- **isImported の第4流儀**（`NovelDetailViewModel.kt:91`）: 唯一 ignoreCase 比較（他は uppercase キー突合）。非ASCIIで結果が割れ得る。
  挙動温存で `sameWorkAs` アクセサに隔離済み＝統一は挙動変更なので要裁定。
- **prune の無正規化突合**（`DefaultBookRepository.kt:641`）: DB 生値を keep 集合と無正規化で突合（保存契約頼み）。既存挙動のまま。
- **表示用 lowercase（trimなし）**（`BookshelfSkyM.kt:646`）: 表示のみ・urlSlug と食い違うが実害なし＝観察のみ。

## Wave 3（Gradle・実機は直列）
- 監督ゲート一括: `testDebugUnitTest`＋`lintDebug`＋`check_design_tokens.py`（/build 接地後・worktree は init-script 不要）
- Baseline Profile 生成（裁定＋adb 許可後・`/device-verify` 接地）

## 規律
- 子は git commit・Gradle 実行・/mnt/c 書込み禁止。ゲートは監督が一括。
- コミットは検収後に論理変更単位（都度OKの合意あり・main/push は従来どおり）。
- 同一ファイル所有: BookshelfScreen/skins リスト＝K、settings/prefs/manifest/Back系＝P、tools+.github＝T、src/test 新規＝U。
