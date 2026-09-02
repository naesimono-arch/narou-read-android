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
| **復旧ダイアログ**の3ボタン段組み（2026-08-17 に発火済み） | **本文欠落本のカードタップ**のみ（`ui/BookshelfScreen.kt` の `reimportPlans` 起点＝L170 収集・L327 分岐） | 捨て本を1冊用意し、その取込元を失わせて本文欠落状態にする。⚠️ **段数は条件で変わる＝段数を前提にした裁定をしない**。2026-08-17 は「2段に割れる」と記録したが、2026-08-26 のエミュ検分（`nr_b`・fontScale 1.0）では**縦3段・右寄せ**だった＝画面幅・fontScale・ボタン文字列のどれかで割れ方が動く。実物のショットは撮れているので残るは意匠裁定（`awaiting-human.md` §1-1） |
| **作品詳細あらすじ**の既読4アクション版（2026-08-17 に成立させて実測済み） | `ui/discovery/NovelDetailScreen.kt:308` の `lastReadEpisode > 0` **かつ未取込** | 未取込作品を「なろうで読む」で開き、WebView 読書位置を1話ぶん記録させる。⚠️ **短編では作れない**＝`ContinuationLogic.kt:146` の正規表現が `ncode.syosetu.com/<ncode>/<N>/` 形しか受理せず、話ページを持たない短編は `web_reading_progress` が書かれない |
| **電池最適化ダイアログ**（fontScale 2.0） | 〈PDF 変換の開始＝`isProcessing` の false→true〉×〈未確認フラグ〉。実機の `app_prefs.xml` は既に `battery_dialog_dismissed=true` | prefs のフラグを false へ戻して PDF を1冊取り込む（SAF 操作は人の手） |
| **FAB と空棚CTA の被り** | `KEmptyState` は**蔵書0冊のときだけ** | 実蔵書では踏めない（蔵書0の環境が要る） |
| **Web 取込表示／二重押しガード／M・P・J の配線3点** | Web 本が要る（実蔵書 web_novels=0） | カクヨムか暁の URL を **adb の `am start` で投げる**（下節＝人手は不要）。**なろう URL では永久に踏めない**＝`SiteAdapterRegistry` の `blockedHosts` に `syosetu.com` があり「公式サイトでお読みください」へ送られる。2026-08-17 に作った捨て本は端末へ残置（現在値＝`STATUS.md`） |
| **話数ラベルの4桁** | 4桁話の本 | 実蔵書は最大 860話（式に桁上限は無いが実機では踏めない） |
| **走査中断**（M/P/J の配線3点の1件・**2026-08-17 に3面とも PASS**） | `reimportPlans` が非空＝**本文欠落本が1冊以上**。入口は `ui/BookshelfScreen.kt:636` `scanForBook` と同 770/774 `runSweepReimport` の2つだけで、**設定側に入口は無い** | 走査専用の捨て本を1冊作り `chap_*.html` を退避して欠落させる（2026-08-17 に実施＝捨て本 `cf4ee71b`・実蔵書7冊は不触・現在値は `STATUS.md`）。⚠️ **残る限界**＝3回とも停止が**列挙フェーズ**で着弾した（停止後の表示が `147件 のうち 0件 を調べました`＝36GB の Download ツリーの SAF 列挙が25秒経っても終わらないため）＝**ハッシュ1ファイルぶんの遅れで止まる協調中断の実挙動だけは未観測**。**Web 由来の捨て本はプラン④ AutoWeb 扱いで走査対象外＝代用不可**。永続 URI 権限は `tree/primary:Download` の1件のみ・`pdf_library_tree_uri` も記憶済み＝**フォルダ選択なしで即走査が走る** |

### Web 取込の投入経路 — **adb で通る**（2026-08-17 に旧記述を実測で訂正）

`adb shell am start -a android.intent.action.VIEW -d "<対応サイトの URL>" -n com.novelreader/.MainActivity` は
**`AppStartConfirmDialogActivity`（ColorOS の関連起動ゲート）を一切出さずに配送され**、取込がその場で始まる
（出力は `Warning: Activity not started, its current task has been brought to the front` だけ）。
⚠️ **旧記述「ゲートに阻まれるので人が Chrome から共有で渡すのが唯一の経路」は誤り**——
突破を試す前に書かれた推測がそのまま台帳3か所へ伝播していた。**Web 取込の検証は Claude が adb で自走できる**。
なろう URL だけは経路以前に `blockedHosts` で永久に踏めない（公式サイトへ送客される）。

⚠️ **宛先の指定（`-n` か `-p com.novelreader`）を省くと Chrome へ流れる**（2026-08-17 実測）。
本アプリは VIEW の既定ハンドラではないため、`-a VIEW -d <URL>` だけでは取込が始まらず**ブラウザにタブが開くだけ**になる。
台帳へ短く引くときも宛先を落とさないこと。

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

## エミュなら前提そのものを作れる（2026-08-26・emulator-5560 で実施）

**上表の「何を作れば踏めるか」は、エミュでは全部こちらの手で作れる**（実機は実蔵書が人質で作れなかっただけ）。
実際に作った手順・撮ったもの・実測値は **`<scratchpad>/shots/b2/README.md` と同ディレクトリの
`make-fixtures.py` / `setpref.py`** が一次情報（README に「ファイル名→画面→前提の作り方（コマンド列）」の全表がある）。
ここには**次便が同じ穴に落ちないための事実だけ**を残す。

**前提の作り方の骨**（詳細＝上記 README）:

| 作りたい前提 | 最短手段 |
|---|---|
| 蔵書0 | `adb -s <emu> shell pm clear com.novelreader`。⚠️ 教示「はじめに」が必ず空棚を覆う＝`intro_about_shown`/`intro_reading_shown`/`intro_search_shown` を true にしてから起動する |
| 4桁話の本 | `make-fixtures.py --chapters 1240`。**`progress` 行に `chap_1028.html` を入れておくと目次が4桁の位置へ自動で寄る**（1240行を手で送らない） |
| 本文欠落（復旧ダイアログ・走査対象） | `make-fixtures.py --missing`（`index.html` を消す＝`BookEntity.hasContent` の判定点）。3ボタン縦積みを出したければ `--sha <64桁hex>` も付ける（`sourceUri` NULL＋指紋あり＝`PickPdfNoRecord`） |
| 電池最適化ダイアログ | `pm clear` 後に PDF を1冊取り込むだけ（`isProcessing` の false→true）。**「二度と表示しない」を押さなければ取込のたびに再発火する**＝1.0 と 2.0 の撮り分けが1台でできる |
| Web 本（棚の `web:<ncode>` 行・M/P/J 配線） | `sqlite3 <DB> "INSERT OR REPLACE INTO web_novels VALUES('<ncode>','<題名>','<作者>',<総話数>,<addedAt>);"`＝**ネットワーク取得なしで棚に出る** |
| 二重押しガード | `sourceUrl` を持つ欠落本を1冊作り、**別の Web 取込を走らせたまま**そのカードをタップ |
| スキン切替 | `app_prefs.xml` の `app_skin` を書き換えて `am force-stop`→`am start`（装いの間を UI で通らない）。debug ビルドは `SKIN_SWITCHING_ENABLED=true`＝**フラグ反転は不要** |

**上表を上書きする事実（2026-08-26 実測）**

- **「FAB と空棚CTA の被り」は明快K では消えている**——`ui/skins/k/BookshelfK.kt:273` が `!isEmptyShelf` で
  FAB 自体を出さない（2026-08-20 裁定②）。`isEmptyShelf` を持つのは **K だけ**で、D/M/P/J は空棚でも FAB が出る
  （D は空棚 CTA「PDFを追加する」と FAB「＋ PDFを追加」の**同一操作の二重表示**が残る）。
- **走査の協調中断は初めて実挙動を観測できた**（実機の「列挙で頭打ち」は消えた）。80MB×24件の小ツリーで
  列挙は実質ゼロ・ハッシュ **0.748 s/件**。停止タップ時の件数へ同一ランのレートで外挿すると、
  最終 `hashedCount` の超過は **0〜1件**＝`domain/PdfFolderScan.kt:152-158` の設計どおり。
  表示は「途中で停止しました」＋「24件 のうち N件 を調べました。」。

**エミュ計測でだけ踏む罠（実機の知見と別物）**

- **`uiautomator dump` は完了に 2.5〜3.0 秒かかり、スナップショットは dump 開始時点**。
  読めた進捗値は最大3秒＝約4件ぶん古い。**observed と最終値の差をそのまま「中断の遅れ」と読むと 4件遅れに見える**。
  かといって「開始から N 秒後に盲打ちで停止」も駄目——4台同居でホストが混みラン間でレートが揺れる
  （+8s→12件 と +14s→11件 という逆転を実測）。**同一ラン内で観測→即タップ→外挿**が唯一信用できる形。
- **端末側 toybox `grep` は日本語パターンに当たらない**（`grep -c 停止しました dump.xml` が 0）。
  端末内で完結する検出ループは ASCII だけで書く（`grep -q 'text="24'` 等）。
- **`uiautomator dump` は1ステップ古い画面を返すことがある**（スキン切替直後に前スキンの木を返した）。
  `screencap` は正しかった＝**見えの判定は必ずスクショ側**、dump は座標取りの道具と割り切る。
- **prefs を端末側 `sed`/`grep` で直編集しない**——属性の引用符が落ちて `<string name=app_skin>` という不正 XML になり
  prefs が丸ごと読めなくなる。**force-stop → pull → ホストで編集 → push** が安全（`setpref.py`）。

## ①AutoPdf が出ない真因と、出すための前提（2026-09-02・emulator-5554 / AVD `nr_b` で確定）

**撮った実物と再現用の道具＝`docs/verification-shots/c1-2026-09-02/`**（git 追跡下。入口は同ディレクトリの
`README.md` と `index.html`＝`mockview` で開く）。

**エミュのプロバイダ固有ではない。SAF 取込では構造的に踏めない**——取込成功の直後に
`repository/PendingJobStore.kt:107-110`（`settlePendingJob`）が `releasePersistableUriPermission` を呼ぶため、
`books.sourceUri` は残るのに**永続 URI 権限だけが即座に返却される**。よって
`viewmodel/BookshelfViewModel.kt:375-378` の `hasPersistedRead` が必ず false になり、②`PickPdfPermissionLost` へ落ちる。
実測（`adb shell dumpsys activity permissions`）＝取込直後の当該 URI は **`persistable=0x3 persisted=0x0`**
（一時グラントは Activity が持つが `persistedUriPermissions` には現れない）。
⚠️ この解放は `releaseOrphanedPermissions` の keepUris ②（「本の生存中ずっと保持する」と書かれた意図）と
**食い違って見える**＝取込元PDF削除が権限失効で失敗しうる。是非の判断は人間（このファイルは前提の作り方が責務）。

**①を出す前提の作り方**（本番コード不変・エミュで4分）:

1. 小さい PDF を SAF ピッカーで取り込む（本ができ `sourceUri` が入る。権限はここで返却済み）
2. **同じパスの PDF を壊し、同じ URI でもう一度取込を走らせて失敗させる**——失敗経路だけは権限を意図的に残す
   （`repository/PdfBookImporter.kt:334` `deleteRowKeepingPermission`）。`dumpsys` が `persisted=0x3` に変わる
3. PDF の中身を元へ戻す（URI もグラントも不変＝①の実行が実際に成功する状態になる）
4. その本の `index.html` を消す → カードをタップ

⇒ ①「…を元のPDFから再取込しますか？／記録されている取込元 PDF からもう一度変換します」（2ボタン）が出る。
一括バナーの内訳ダイアログも「元のPDFから自動で再変換（取込元の記録と権限あり） 1冊」になる。
**作り物でない確認**＝「再取込する」を押すと `books.id` が変わらないまま本文が戻る（読書位置・栞を保つ①本来の動き）。
