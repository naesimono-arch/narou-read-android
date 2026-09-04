---
name: new-screen
description: 画面・シート・ダイアログを新設するときの定型（種別を判定し、その種別で必要な作業だけを出す）。「新しい画面を作りたい」「画面を追加する」「ダイアログ／シートを出したい」「NavHost にルートを足す」「タブを増やす」「このスキンにも同じ画面を用意する」等の依頼で、コードを書き始める前に使う。
---

# 新画面の定型（種別を決める → その種別の分だけやる）

新設のたびに決めることは毎回同じ。**§1 で種別を判定 → §2 の共通ゲート → §3 の該当節だけ実行 → §4 で締める**。
所在の地図は `/architecture`、意匠の正本は `/visual-language` が入口（このスキルは重複させず参照する）。

## §1 まず種別を決める

| 種別 | 生やす場所 | この種別に固有の必須 |
|---|---|---|
| **深い画面**（本命） | `MainActivity` の NavHost へ `composable("...")` 追加＝タブ層の上へ push | 階層 up の着地決め・`launchSingleTop` |
| **シート/ダイアログ** | 呼び出し元の route 層が所有（画面ファイルを増やさない） | 1回書けば全スキンに効く |
| **タブ層の面** | `ui/tabs/TabPagerHost.kt` のスロット（ルートは `"tabs"` 単一） | Back 契約（ADR 0022 追記）とタブ遷移テスト |
| **既存画面のスキン面** | `ui/skins/{j,k,m,p}/` に1画面=1ファイル | **モック正本が先に在るときだけ**（下記） |
| **ルート常駐層**（backdrop / overlay） | `MainActivity` の root Box へ NavHost の**兄弟**として直に置く（ルートを持たない） | z＝Box の子順・長寿命 remember の churn 回避・inset を自前で持つ |

判定の勘所3つ:

- **全スキンに出したい部品は、まずシート/ダイアログで済まないかを疑う**。route 層所有なら1回で全スキンに効き、スキン面4枚の複製と「シート色・クロームは加算的で無音欠落しうる」既知リスク（ADR 0021）を丸ごと回避できる。
- **「どの画面の上にも／下にも出したい」だけならルート常駐層を疑う**。画面を4枚複製する代わりに root へ1枚置いて
  CompositionLocal で話しかける形になる。ただしこの種別は**兄弟サブツリー側の編集（透過・a11y）まで込みで1セット**＝下記。
- **モックの無い画面は構造を発明しない**（ADR 0022）。新画面のスキン対応は原則 K/D 共通実装で足り、M/P/J 専用面を起こすのは `docs/design-candidates/skins/` に該当モックが在るときだけ。

## §2 着手前ゲート（全種別共通）

1. `docs/design-candidates/` に該当の正本モックが在るか確認。無ければ `/visual-language`（小粒な追加要素は新規モックを起こさず正本へ直差分）。
2. 意匠はトークン経由（`ui/theme/` の Color/Typography/Spacing/Motion）＝**直書き禁止**。
3. Room を触るなら先に `/db-migration`。

## §3 実装チェックリスト

### 深い画面（VM を持つ全画面）

- [ ] **route(VM 結線)/Content(stateless) の2層**に割る（ADR 0009。Content が Robolectric のテスト対象）
- [ ] 引数が増えるなら `@Immutable` の**束 data class** にまとめ、**既定値を付けない**——「既存呼び出し互換のための既定値」は新しい呼び出し元の配線漏れを無音で成立させる欠陥クラス（`ui/skins/ShelfFace.kt` の判断）。全指定必須にして配線忘れをコンパイルエラーへ格上げする
- [ ] nav 引数は String、ドメイン型（`Ncode` 等の value class）は境界でほどく
- [ ] `navigate(...) { launchSingleTop = true }`（二度押しの二重 push 防止）
- [ ] **←もシステム Back も階層を1段上がる**（ADR 0026）。履歴 pop ではなく up の着地先を決めて `BackHandler` を配線し、契約テスト（発見系 `DiscoveryUpNavigationTest`／読書系 `ReadingEscapeNavigationTest`／タブ `KTabNavigationTest`）へ足す
- [ ] 遷移アニメを画面側に持たない（NavHost 共通契約が slide push・M星図のみフェード＝ADR 0019）
- [ ] 初回描画が重いなら enter アニメ中だけスケルトンへ差し替える（`deferHeavyContent` の系列）

### シート/ダイアログ

- [ ] 状態と表示は呼び出し元 route が所有（スキン面へ配らない）
- [ ] シート枠と中身を分け、中身を `internal fun XxxContent` に割る（テスト可能にする）
- [ ] **中身の Column に `verticalScroll` を付ける**（例外なし）。無いと fontScale や可変長テキストで
      操作要素が画面外へ押し出され**到達手段が消える**のに、非スクロール面の溢れは画素に痕跡が出ず
      golden でも検出できない＝機械走査で永久に捕まらない（`docs/knowledge/sheet-without-verticalscroll-hides-actions.md`。
      3面すべてが同じ型で踏んでいる）。`heightIn(min = …)` はこの型の対処にならない
- [ ] **初期の展開位置（`rememberModalBottomSheetState(skipPartiallyExpanded = …)`）を明示的に決める**。
      既定 false は中身が高いと `PartiallyExpanded` で起動する＝**主役の操作要素が折り目の下に落ちる**
      （表示設定シートは fontScale 1.0 でも文字サイズのつまみが 7px しか覗いていなかった＝2026-08-20 に
      全高起動へ裁定）。**何を初手で見せるかは意匠の裁定事項**であって実装の裁量ではない＝正本モックの
      シート高に従う。⚠️ 全高を嫌って**高さ上限を `Modifier` で掛けてはいけない**——`draggableAnchors` が
      読む constraints ごと縮み、シートが上端に張り付く（上限は中身側の Column へ＝`SearchConditionSheet.kt` の実装）。
      **この型は golden も到達性テストも構造的に見ていない**（どちらもシート枠を除外して Content を直接組む）＝
      検出手段は実機/エミュで開いた瞬間の1枚だけ（`docs/knowledge/sheet-partial-expansion-hides-the-main-control.md`）

### ルート常駐層（backdrop / overlay）

NavHost のどの route にも属さず、`MainActivity` の root Box の子として**画面より長く生き続ける**層
（実例2つ＝`SkyBackdropM`＝Box の**最初の子**で全ての下／`IntroOverlayHost`＝Box の**最後の子**で
恒常ナビも含めた全ての上。z の両端だが所有と寿命の作法は同じ）。
**この種別を選ぶ動機は「全画面に出したい」ではなく「画面遷移で破棄させたくない」**＝
遷移のたびに作り直されると壊れる状態（流星スケジューラ・視差オフセット・教示列の進行）を抱えるとき。
それが無いならシート/ダイアログで足りる（そちらの方が安い）。

- [ ] **ルートを足さない**＝`composable("...")` は書かない。置くのは root Box の直下の子で、NavHost の兄弟
- [ ] **z は Box の子順がすべて**。下に敷く（backdrop）なら**上に重なる層が透過でないと見えない**——
      route 層の `Surface` を透明にする結線が要る（`MainActivity` の `Surface(color = if (isSeizu) Color.Transparent else …)`
      が実物。この「兄弟サブツリー側の編集」を忘れると**層は正しく描かれているのに一切見えない**）。
      上に重ねる（overlay）なら Box の最後＝`KBottomNav` を含む Column の**外**に置く
- [ ] **出す/出さないは層が自分の state で決める**（nav では決まらない）＝`LocalXxx.current ?: return`／`if (controller.hidden) return`。
      ⚠️ **早期 return は長寿命 `remember` より後ろに置く**——前に置くと表示条件が切り替わるたびに作り直され、
      スケジューラや生成済みフィールドが churn する（読書画面の往復で流れ星が毎回リセットされる類。
      両実例ともこの順序の理由をコメントで固定してある）
- [ ] **画面との受け渡しは CompositionLocal**（route が無い＝nav 引数の通り道が存在しない）。
      下り＝入口が層を叩く（`introController.requestAuto(group)`）／上り＝画面が層へ値を流す
      （`LocalSkyParallax` の `nestedScroll` でスクロール差分を渡す）。**画面側にこの層のコピーを持たせない**
- [ ] **inset は自分で持つ**。route 層の Column に掛けた `windowInsetsPadding`（切り欠き回避など）は
      兄弟であるこの層には**届かない**＝同じ inset を層の側で書く（`IntroOverlay.kt` の `displayCutout` がその実物）
- [ ] overlay 側は**背後への素通しを塞ぐ**（スクリムに `pointerInput`）。`BackHandler` は NavHost より後に
      composition へ入る＝黙って最優先で受け取るので、**NavHost 側の Back 契約と噛み合うか**を確かめる
- [ ] overlay 側の a11y は**兄弟の側**を触る＝出している間だけ配下を `hideFromAccessibility()`
      （`MainActivity` の `semantics { if (introController.flow != null) … }` が実物）。ダイアログとして読ませるなら
      層側に `isTraversalGroup` / `paneTitle`
- [ ] **state holder は root で1個 `remember`**（画面をまたぐ）。構成変更をまたぐなら `rememberSaveable` + `Saver`。
      ⚠️ その場合コンストラクタ束縛の値は作り直されない＝reduce-motion のような live な設定は
      `SideEffect` で毎コンポジション書き戻す（無いと旧値のまま走り続ける）
- [ ] **テストはナビ契約からは張れない**（route が無い＝遷移テストが触れない）。代わりに
      ①状態機を純関数へ抜いて JVM で全数固定（`skyBackdropReadingState`／`IntroFlow`）
      ②描画を stateless な `XxxContent` に割って Robolectric（ADR 0009）——の2枚重ねにする

### タブ層の面 / スキン面

- [ ] タブ層＝スロット追加＋Back 契約（ADR 0022 追記）／スキン面＝`when(skin)` ルーターへ分岐先を追加（exhaustive when が漏れを止める）

## §4 締めのゲート

- [ ] Content の Robolectric テストを追加（新規画面でテスト無しは不可）
- [ ] `bash tools/gwlock.sh testDebugUnitTest`（⚠️ 旧記述の `./gradlew` 直叩きは ext4 worktree で Permission denied＝`/build` が「必ず gwlock.sh 経由」を正本にしている。Windows セッションでは通らない＝`/build` の Windows 節）
- [ ] 意匠に触れたら `python3 tools/check_design_tokens.py` ＋ `recordRoborazziDebug` で golden 再記録（既定ゲート非同乗＝忘れると腐ったまま潜伏する）
- [ ] STATUS/handover の更新は原因となった論理変更と同じコミットへ同梱／新しい設計判断が出たら ADR 起票
- [ ] 制御フロー・構成を変えたなら同じターンで `/stale-check`
- [ ] 実機確認は着手前にユーザーへ一度聞く（`/device-verify`）

## §5 コピー元の実例

- 深い画面＋VM → `ui/discovery/NovelDetailScreen.kt`（`NovelDetailScreen`／`NovelDetailContent`）＋ `viewmodel/NovelDetailViewModel.kt`・テスト `ui/discovery/NovelDetailContentTest.kt`
- 引数束の型 → `ui/skins/ShelfFace.kt`（`ShelfData`／`ShelfChrome`）
- シート → `ui/ReadingSettingsSheet.kt`（枠＋`ReadingSettingsSheetContent`）
- ルート登録・遷移契約 → `MainActivity.kt` の NavHost ブロック
- ルート常駐層（overlay）→ `ui/intro/IntroOverlay.kt`（`IntroOverlayHost`＝ホスト＋`IntroOverlayContent`＝描画層）・状態は `ui/intro/IntroController.kt`・置き場所は `MainActivity.kt` の root Box **末尾**の `IntroOverlayHost()` 呼び出し
- ルート常駐層（backdrop）→ `ui/skins/m/SkyBackdropM.kt`（`SkyBackdropM`）・置き場所は `MainActivity.kt` の root Box **先頭**（`if (skyParallax != null) SkyBackdropM(...)` の行）・画面からの上り口は同ファイルの `LocalSkyParallax`
