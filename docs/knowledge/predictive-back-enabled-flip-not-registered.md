# Predictive Back では `BackHandler(enabled = …)` の false→true 反転が登録されないことがある

**重要度**: ★★★
**確定日**: 2026-08-14（実機 PGEM10 / ColorOS / Android 16 で2件の実害・実装と bytecode で機序確認・修正済み）
**一行要約**: Predictive Back 下で Back を受けられるかは「OnBackInvokedDispatcher への**登録の有無**」で決まるので、`enabled` を後から反転させる形のハンドラは Back が OS へ抜けて**アプリが終了する**ことがある。

## 症状

システム Back を押すとアプリ内で消費されるはずの Back が消費されず、**アプリが終了する**。
実例2件（いずれも `release` 到達）:

- **タブ枠**: `BackHandler(enabled = pagerState.currentPage != 0)`。起動時 page 0＝`enabled=false` で生まれる
  → 設定タブへ移っても Back が効かず終了。
- **本棚の選択モード**: `BackHandler(enabled = selectionMode && isFrontTab)`。長押しで false→true へ反転
  → 選択モード中の Back で選択が解除されず終了（全スキン共通）。

## 真因

Predictive Back（`AndroidManifest` の `enableOnBackInvokedCallback` opt-in・targetSdk 36 の Android 16 実機では
OS が常時 ON 扱い）では、**「アプリが Back を受けるか」は OnBackInvokedDispatcher への*登録の有無*で決まる**
——OS が毎回コールバックの `enabled` フラグを読みに行くのではない。登録は `hasEnabledCallbacks`
（有効なコールバックが1つでも在るか）の変化に追随して行われる。

実機で正しく効いていた Back は**例外なく**「必要になった時点で `enabled=true` のコールバックが**新規に追加される**」形だった
＝追加のたびに登録が走るので、反転に依存しない。上の2件だけがこの形から外れていた。

## 対処

**反転に依存しない構造へ変える**＝条件を `enabled` 引数でなく `if` に出し、コールバック自体を出し入れする。

```kotlin
if (<条件>) { BackHandler { … } }   // 常設して enabled を反転させない
```

条件が偽のときはコールバックが存在しない＝旧 `enabled=false` と同値（除去時に `hasEnabledCallbacks` が再計算される）ので、
システム既定の Back プレビューは従来どおり効く。**撤去ではなく登録の作り方だけを変える。**

## ⚠️ 形だけで判定してはいけない（ここが知見の核）

**反転形でも実害が出ないものがある。** 決め手は形ではなく、
**反転の瞬間に Dispatcher の `hasEnabledCallbacks` が false→true になるか**。
他に有効なコールバックが既に居れば集約値は true のままで登録は既に済んでおり、
そのコールバックの反転が OS へ反映されなくても問題にならない。

実際、今回洗い出した反転形3つのうち実害があったのは2つで、残る NavController 内蔵のコールバックは
「反転はするが、依存する経路が必ず born-enabled のハンドラを追加する側でもある」ため**実害なしと判定した**。

→ **穴の形をしたものを全部直すのではなく、集約が動くかまで見て実害のあるものだけ直す**のが正しい手順。

## 未確定として残るもの

**どの層で反映が落ちているかは確定していない。** androidx activity 1.8.2 の bytecode には
反転→再登録の経路が**存在する**（`OnBackPressedCallback.setEnabled` → `updateEnabledCallbacks` →
`updateBackInvokedCallbackState`）ことは確認済みで、経路が無いわけではない。
しかし**端末を跨いだ再現・計測なしにはどこで落ちているか特定できない**。
よって本件は「反転が効かない原因を突き止めて直した」ではなく、**反転に依存しない構造へ変えた**という対処である。

## テスト側の落とし穴（再発防止）

既存の Back 契約テスト3本は**全て `initialPage` 指定で「最初から page 0 以外」の状態を作っており、
反転経路を1本も踏んでいなかった**。実アプリで唯一起きる経路＝「起動＝page 0 →タブ移動」がテストに無い
＝**緑のまま実機だけ落ちる**。

→ **Back の契約テストは「反転させて渡る」経路を明示的に踏むこと。** 到達済みの状態を `initialPage` 等で
直接作ると、登録が走る経路しか通らずこのクラスのバグを構造的に見逃す。

関連: ADR 0026 追記（2026-08-14）＝タブ間 Back を階層 up として扱う裁定と、否定された疑い2つ。
