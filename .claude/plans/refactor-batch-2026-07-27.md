# 2026-07-27 リファクタ大バッチ（/orchestration）

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

## Wave 3（Gradle・実機は直列）
- 監督ゲート一括: `testDebugUnitTest`＋`lintDebug`＋`check_design_tokens.py`（/build 接地後・worktree は init-script 不要）
- Baseline Profile 生成（裁定＋adb 許可後・`/device-verify` 接地）

## 規律
- 子は git commit・Gradle 実行・/mnt/c 書込み禁止。ゲートは監督が一括。
- コミットは検収後に論理変更単位（都度OKの合意あり・main/push は従来どおり）。
- 同一ファイル所有: BookshelfScreen/skins リスト＝K、settings/prefs/manifest/Back系＝P、tools+.github＝T、src/test 新規＝U。
