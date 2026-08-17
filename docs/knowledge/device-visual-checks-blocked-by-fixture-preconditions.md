# 実機目視が「判定不能」で止まるのは実装の疑いではなく、発火条件が実蔵書で踏めないから

**重要度 ★★／2026-08-16〜17 に各項目の「出せない理由」を条件レベルで確定／台帳側の入口＝`awaiting-human.md` §1**

1行要約: 実機の蔵書は **7冊すべて健全・すべて取込済み・web_novels=0・prefs は消費済み**なので、
**欠落／未取込／初回フラグ／Web 本を前提にする画面は発火経路そのものが存在しない**。
必要なのは調査ではなく**前提（捨て本・フラグ戻し）を作る作業**——この区別を取り違えると、
「未検証」を「実装が怪しい」と読み替えて無駄な調査に入る。

**実蔵書 7冊で破壊フローは踏まない**（削除の確認はダイアログの文言を見てキャンセルまで）＝
下記はいずれも**捨て本の再作成**か**prefs の巻き戻し**が前置き。

## 発火条件と、何を作れば踏めるか

| 見たいもの | 発火条件（出所） | 何を作れば踏めるか |
|---|---|---|
| **復旧ダイアログ**の3ボタン段組み | **本文欠落本のカードタップ**のみ（`ui/BookshelfScreen.kt` の `reimportPlans` 起点＝L170 収集・L327 分岐） | 捨て本を1冊用意し、その取込元を失わせて本文欠落状態にする |
| **作品詳細あらすじ**の既読4アクション版（2026-08-17 に成立させて実測済み） | `ui/discovery/NovelDetailScreen.kt:308` の `lastReadEpisode > 0` **かつ未取込** | 未取込作品を「なろうで読む」で開き、WebView 読書位置を1話ぶん記録させる。⚠️ **短編では作れない**＝`ContinuationLogic.kt:146` の正規表現が `ncode.syosetu.com/<ncode>/<N>/` 形しか受理せず、話ページを持たない短編は `web_reading_progress` が書かれない |
| **電池最適化ダイアログ**（fontScale 2.0） | 〈PDF 変換の開始＝`isProcessing` の false→true〉×〈未確認フラグ〉。実機の `app_prefs.xml` は既に `battery_dialog_dismissed=true` | prefs のフラグを false へ戻して PDF を1冊取り込む（SAF 操作は人の手） |
| **FAB と空棚CTA の被り** | `KEmptyState` は**蔵書0冊のときだけ** | 実蔵書では踏めない（蔵書0の環境が要る） |
| **Web 取込表示／二重押しガード／M・P・J の配線3点** | Web 本が要る（実蔵書 web_novels=0） | カクヨムか暁の URL を **adb の `am start` で投げる**（下節＝人手は不要）。**なろう URL では永久に踏めない**＝`SiteAdapterRegistry` の `blockedHosts` に `syosetu.com` があり「公式サイトでお読みください」へ送られる。2026-08-17 に作った捨て本は端末へ残置（現在値＝`STATUS.md`） |
| **話数ラベルの4桁** | 4桁話の本 | 実蔵書は最大 860話（式に桁上限は無いが実機では踏めない） |
| **走査中断**（M/P/J の配線3点の1件） | `reimportPlans` が非空＝**本文欠落本が1冊以上**。入口は `ui/BookshelfScreen.kt:636` `scanForBook` と同 770/774 `runSweepReimport` の2つだけで、**設定側に入口は無い** | 走査専用の捨て本を1冊作り `chap_*.html` を退避して欠落させる（⚠️ 実蔵書7冊は不触＝許可が要る＝`awaiting-human.md` §4）。**Web 由来の捨て本はプラン④ AutoWeb 扱いで走査対象外＝代用不可**。永続 URI 権限は `tree/primary:Download` の1件のみ・`pdf_library_tree_uri` も記憶済み＝**フォルダ選択なしで即走査が走る** |

### Web 取込の投入経路 — **adb で通る**（2026-08-17 に旧記述を実測で訂正）

`adb shell am start -a android.intent.action.VIEW -d "<対応サイトの URL>" -n com.novelreader/.MainActivity` は
**`AppStartConfirmDialogActivity`（ColorOS の関連起動ゲート）を一切出さずに配送され**、取込がその場で始まる
（出力は `Warning: Activity not started, its current task has been brought to the front` だけ）。
⚠️ **旧記述「ゲートに阻まれるので人が Chrome から共有で渡すのが唯一の経路」は誤り**——
突破を試す前に書かれた推測がそのまま台帳3か所へ伝播していた。**Web 取込の検証は Claude が adb で自走できる**。
なろう URL だけは経路以前に `blockedHosts` で永久に踏めない（公式サイトへ送客される）。

## 別クラス＝前提ではなく標本化の限界で判定不能なもの

`screencap`／`uiautomator dump` は**数フレーム級の見えを捉えられない**（アニメ10倍でも取れず＝2026-08-16 実測）。
ColorOS は `screenrecord` が使えないので 16ms 窓の標本化手段が無い。
⇒ **遷移スケルトンの骨・ページャの残像・Predictive Back のプレビュー・クローム出現の1〜2フレーム**は
条件を整えても機械では決まらない＝**肉眼でしか判定できない**（無理に自動化へ回さない）。
機械ハントを掛けるときの前提＝`adb-brute-force-hunt-needs-expected-values.md`。

## 第三のクラス＝「面を取り違える」と実装が無いように見える（2026-08-17）

M/P/J は〈没入面／一覧面〉の2面を持ち、**没入面は選択モードを構造的に持たない**——
`ShelfSelection`/`ShelfWebActions` をシグネチャに取らない＝**コンパイル時制約で閲覧専用**
（`BookshelfCartridgeP.kt:206`・`BookshelfSkyM.kt:182`・`BookshelfPortalJ.kt:297`）。
選択モードと Web 行の配線は**一覧面**にある（キー `web:<ncode>`＝`BookshelfListCartridgeP.kt:300-312`・
`BookshelfLogM.kt:396-408`・`BookshelfGridJ.kt:315-328`）。
実機 `app_prefs.xml` は `p_rack_view`/`m_sky_view`/`j_deck_view` とも true＝**既定が没入面**。

⚠️ このため「一覧に ⋮ ノードが0個・長押しは本が開くだけ」と観測され、台帳へ
**「P は選択モードの入口自体が特定できていない」と誤って記録された**（実際の入口はヘッダの面切替ボタン）。
⇒ **`uiautomator dump` で目的の要素が0個のときは、実装の不在を疑う前に
「いま何の面に居るか」を prefs とコードで確かめる**。端末の見えだけを根拠に
「未実装」と台帳へ書かない——その1行が次の検証ラウンドを丸ごと空振りさせる。

## 一般化

台帳（`awaiting-human.md`）には「**何が揃えば見られるか**」の1行だけを残し、
**発火条件の出所（コード行・prefs キー・分岐条件）はここへ集約する**。
台帳は常設注入で毎ターン読まれるので、条件の根拠まで置くと費用が積み上がる。
