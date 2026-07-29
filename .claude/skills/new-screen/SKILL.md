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

判定の勘所2つ:

- **全スキンに出したい部品は、まずシート/ダイアログで済まないかを疑う**。route 層所有なら1回で全スキンに効き、スキン面4枚の複製と「シート色・クロームは加算的で無音欠落しうる」既知リスク（ADR 0021）を丸ごと回避できる。
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

### タブ層の面 / スキン面

- [ ] タブ層＝スロット追加＋Back 契約（ADR 0022 追記）／スキン面＝`when(skin)` ルーターへ分岐先を追加（exhaustive when が漏れを止める）

## §4 締めのゲート

- [ ] Content の Robolectric テストを追加（新規画面でテスト無しは不可）
- [ ] `cd android && ./gradlew testDebugUnitTest`
- [ ] 意匠に触れたら `python3 tools/check_design_tokens.py` ＋ `recordRoborazziDebug` で golden 再記録（既定ゲート非同乗＝忘れると腐ったまま潜伏する）
- [ ] STATUS/handover の更新は原因となった論理変更と同じコミットへ同梱／新しい設計判断が出たら ADR 起票
- [ ] 制御フロー・構成を変えたなら同じターンで `/stale-check`
- [ ] 実機確認は着手前にユーザーへ一度聞く（`/device-verify`）

## §5 コピー元の実例

- 深い画面＋VM → `ui/discovery/NovelDetailScreen.kt`（`NovelDetailScreen`／`NovelDetailContent`）＋ `viewmodel/NovelDetailViewModel.kt`・テスト `ui/discovery/NovelDetailContentTest.kt`
- 引数束の型 → `ui/skins/ShelfFace.kt`（`ShelfData`／`ShelfChrome`）
- シート → `ui/ReadingSettingsSheet.kt`（枠＋`ReadingSettingsSheetContent`）
- ルート登録・遷移契約 → `MainActivity.kt` の NavHost ブロック
