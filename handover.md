# handover — やること台帳（main）

> **次に何をやろうか悩んだら、まずここを見る。**
> **ここに置くのは「Claude が今すぐ着手できるやること」だけ**——実装残・調査残・計測・ドキュメント作業、
> それに作る予定のものとあとで拾う思いつき（末尾の「思いつき・取りこぼし」へ追記して育てる）。
> **人間の実機目視・意匠/方針の裁定・外部手続き（ブランド名・鍵・Play Console・第三者）は `awaiting-human.md`**——
> 分けた理由と責務、**どちらに置くか迷ったときの規則（迷ったらこちら＝handover 側）**は **ADR 0028** が正本。
>
> **ここは「やること」だけを置く。** 完了したら打ち消し線で残さず**消す**（完了の正本は **git log**・現況は `STATUS.md`）。
> それ以外の置き場（知見・ADR・一次情報など）の割り振りは **CLAUDE.md「管理ドキュメントの体系」が正本**——
> ここへ再掲すると片方だけ古くなる（2026-07-25 に STATUS 側で実際に起きた）。
> 打ち消し線を溜めると「やったことリスト」に化けて台帳の役目を失う（運用: memory `docs-status-vs-handover-split`）。

## ★最優先方針（2026-07-20 転換 — デフォルトUI明快化＋幅広いサイト対応）

> **背景**: 第三者にアプリを触らせた結果、「豪奢なスキンより先に直すべき本質課題」が2つ判明——①**デフォルトUI（和モダンD）が一目で分からない**、②**なろう限定では求心力が足りない**。
> **豪奢スキン（M/P/J・星図リッチ化＝ui/refine 系）は否定しない＝温存**。ただし優先度は下記 A/B の下に置く。既存のスキン/リッチ化 backlog は消さずそのまま保持。
> 大原則＝memory `feedback-default-ui-legible-first`（デフォルトUIは「一目でわかる」最優先・豪奢さは opt-in の付加価値）。

### 最優先A：デフォルトUI を「一目でわかる・迷わせない」設計へ

> **現在地**: 回答＝新デフォルトUIスキン「明快K」（構造の要約＝`STATUS.md` §0）。設計と裁定の一次情報＝
> `.claude/plans/` の `default-ui-clarity-K-2026-07-23.md`・`k-shape-propagation-2026-07-23.md`・`ui-density-swipe-round-2026-07-24.md`。
> K 形は D/M/P/J へ伝播済み（正本＝`skins/{bookshelf,discovery,toc,settings}-{D,M,P,J}.html`）。以下は**残り**のみ。

- **[モック逆同期・K 4枚]** K 実装後の実スクリーンショットを出発点に `skins/*-K.html` を実装準拠へ描き直す
  （⋮＝キャプション行・書影の輪郭線→影・ヘッダの冊数ベースライン・Surface 配色接地後の実色）。
  `skins/wardrobe-D.html` の和モダン tagline（「余白の装い」）逆同期も同便で。
- **[装いの間・K ミニチュア]** 現状はトークン D 委譲の自動描画で機能は成立。K らしさ（ナビ付きミニチュア）を出すかは磨き込み判断。
- **[遷移 jank・残り]**（実機スタック報告の計測ラウンドの残り。対処済み分の機序と why は `TabPagerHost` のコメントが正本）
  - **残③**: 「さがす」面の初回コンポーズ自体が重い（~700ms/2フレーム）＋ pop 後の tabs 再コンポーズ ~180ms
    ＝**perfetto で実名を特定してから軽量化**。距離2ページ復帰の単発 65〜73ms（頻度低）もここに包含。
    案2（`isScrollInProgress` 連動 defer）は補助策として温存。
  - **残④**: macrobenchmark（`.claude/plans/macrobenchmark-kickoff-2026-07-17.md`）へ**タブスワイプ＋遷移シナリオ**を足して回帰固定。
  - **副次**: 目次画面の初回コンポーズ 93/81ms（P2 対象外）を別タスク化するか。
  - 参考ベースライン（framestats 実測・再計測時の比較用）: `beyondViewportPageCount=1` 導入で cold P95 42→24・P99 450→73ms、
    pop は 445→177〜205ms。push 側の初回コンポーズは章/目次 70〜120ms（501話本のスポットでも最大112ms＝超長編特異ではない）。
- **[読書] 本文調整/トグルの説明を出す**（何のトグルか分かるように）。
- **[本棚] 長押し時の触覚フィードバック（haptic）**＝**後回しでOK**とユーザー明言。
- **[非Kスキンの気分]** 現状 CLASSIC 固定。ページャ化・日替わりの各スキン適用は別ラウンド（J の扉 glyph が P1 前提）。

### 最優先B：幅広いサイト対応＝汎用オフラインDL基盤（検索→DL→アプリ内で読む）

- なろう限定を脱し、**汎用の取得/抽出基盤**でハーメルン/アルファポリス等も見据える（サイトごとに抽出器を分離できる設計）。オフラインDL＝手元の**蔵書コレクション**の位置づけ。
- **利用規約で禁止のサイトは除外・別対応**（一律スクレイピングしない＝ユーザー裁定）。なろうは公式API＝安全で優先。**公式サイト直行の逃げ道**も併設。
- スクレイピングは**サイトのHTML変更で壊れやすい**前提で設計（脆さは織り込み・公式直行の逃げ道がその保険）。

### 着手順序（2026-07-20 裁定）

- **まず最優先Aの明快さ・バグを一掃**（数日で"他者が困らない"状態へ）。最優先Bの中核設計は並行で下ごしらえ。

### 汎用DL基盤 — 残っている材料

> 設計判断 D1〜D6・カクヨム実構造・フェーズ順＝`.claude/plans/scraping-foundation-design-2026-07-20.md`／
> 汎用アダプタの設計正本＝`.claude/plans/generic-adapter-design-2026-07-23.md`／規約線と全裁定の正本＝**ADR 0024（追記含む）**。
> **対応面の拡大はいったん打ち止め**（表駆動の新規候補は暁で尽き・ヒューリスティック G2 は不採用裁定）。
> 将来の解放条件＝ハーメルン裁定 or グレー勢の再裁定 or 新規 SSR サイトの発見（表1行＋fixture で即追加可）。
> **再開するときに最初に開く表＝`docs/reference/08-web-novel-site-survey.md`**（各サイトの生存・規約・robots・構造の実地照合結果）。

- **[温存メモ・着手時に使う]**（ユーザー指示で保持・ADR 0024 が「handover の注1/注2」として参照している）:
  **注1 Pixiv**＝R-18 はログイン必須＝アプリ内ブラウザ認証（Cookie/セッション保持）が前提・メンバーページ登録もログイン要／
  **注2 アルファポリス**＝連続DL制限あり＝Crawl-delay 厚め＋制限検知バックオフ・リトライ（土台は `ScrapeHttpClient` に実装済み）。
- **[参照資料] 競合のスクレイピング実装解析**: `/mnt/c/Users/qingj/Desktop/project/book-api-analysis/07-competitor-scraping-techniques.md`
  （唯一の実スクレイプ競合 B・約38サイト・jsoup・3抽出戦略・per-host レート制御/WebView Cookie 間借り等の「作法」）。
  **内容が濃いため直読みせず、新アダプタ設計時に委譲ダイジェストで参照**（ユーザー指示）。

## 未修正・調査中のバグ

- **[削除警告が「守れない約束」になりうる（2026-07-30 実機実測）]**: なろう縦書きPDF取込の本は
  `sourceUri`／`sourceUrl` が**両方 NULL**（実測＝保有しているのは `ncode` と `contentSha256` のみ）。よって本文が欠落すると
  再取込プランは③`PickPdfNoRecord`＝「PDF のある場所から探しますか？」へ落ちるが、**その PDF はアプリ自身の
  `cache/pdf_import/<ncode>.pdf` にしか存在せず SAF ピッカーから辿れない**（`/sdcard/Download` にも無いことを実測）。
  つまり今日入れた削除警告が約束する「カードから再取込すれば読書位置・しおり・追加日を保ったまま戻せます」が、
  **なろうPDF由来の本では実行不能**になりうる。`ncode` は持っているので本来は④`AutoWeb` 相当（なろうから PDF を再取得）へ
  載せられるはずで、そこが埋まれば約束も嘘でなくなる。
  ⚠️ **案X（フォルダ走査＋内容ハッシュ照合）を実装してもこの本は救われない**——ユーザーの手元に PDF ファイルが無いため。
  案X の対象は「自分で PDF を保存している本」に限られる、という前提を案X 着手時に明記すること。
- **[Web 取込経路では「自然昇格」が成立せず同一作品が本棚に2枚並びうる]**（2026-07-29 発見・未報告）:
  `WebBookImporter` は `books.ncode` に **null を明示的に書く**（意図的判断・同ファイル 121-122 行）ため、
  「本棚に置く」で `web_novels` に行を作った作品を **URL 共有→`addWebBook`** で取り込むと、ncode が無く昇格判定に掛からず web カードが残る。
  ヘッダ冊数と一覧枚数は一致するので**冊数ずれとしては現れない**。対処は「取込時に ncode を記録する」等のデータ意味論の変更で影響範囲が広い。
  着手時は ADR 0011 と `NcodeLinkSheet` の手動紐付けとの整合を先に見ること。
- **[蔵書復旧ダイアログが取込元を数値IDで表示する]**: 記録済み `sourceUri` は全冊 MediaStore Documents Provider 形式
  （`content://…/document/document%3A<数値ID>`）で**ファイル名を含まない**のに、`sourceFileNameHint()` は `primary:Download/foo.pdf` 形式のみ想定＝
  「取込元の PDF: 1000027648」と無意味な数値が出る（機序の詳細＝`docs/knowledge/auto-backup-does-not-restore-uri-permissions.md`）。
- **[本文読書中の章遷移で「描画が上部にジャンプする」]**（実機ユーザー報告・**報告者自身も再現できていない**＝再現手順の取得が先決＝`awaiting-human.md` §1-4）:
  - **調査済み＝同じ道を再探索しないこと**: 本文で章遷移時にスクロールが 0 へリセットされる経路は**実コードに存在しない**と判定。
    根拠 ①章→章は `AnimatedContent` が file でキーするため別サブコンポジション＝`LazyListState` は毎回新規（**新章が先頭から始まるのは設計どおり**）
    ②既読章へ戻る経路は `sessionScrollByFile`→`chapterRestore`→(0,0) の順で復元される
    ③本文経路の明示スクロールは「最上部へ」ピルと a11y アクションだけで、いずれもユーザー操作起点。
  - 既知知見 `docs/knowledge/lazylist-loading-full-replace-scroll-reset.md` とは**別系統**（本文の Loading は LazyColumn 自体が unmount される）。
  - **ユーザー確認済みの事実**: 没入読書中に章を送るとステータスバーが実際に出る（＝章送り時の没入破壊・2026-07-29 に構造是正）。
    **ただし「それによるジャンプとは限らない」と留保**＝バー復帰とジャンプの因果は未確定。修正後も残るなら別系統を追う。
  - 再現条件が取れたら Robolectric で赤を出してから直す。
- **[本棚/全スキン] ⋮メニュー上端にアプリバー裏の背後文字が覗く**（D 由来の持病・軽微・D/M 共通で再現）:
  本棚⋮の DropdownMenu がアプリバー直下に置かれ、バーとポップアップ上端の隙間に背後コンテンツの文字が覗く。
  直すなら DropdownMenu の offset/アンカー調整。再現は実機で⋮を開くだけ。

## 蔵書復旧導線（案X・本体は未実装）

> **裁定＝案X「場所を1回教えるだけ」**（2026-07-29）: SAF のフォルダ選択（`ACTION_OPEN_DOCUMENT_TREE`）を**1回**だけ求め、
> 以後アプリがツリー配下の PDF を列挙して**中身のハッシュで自動照合**し一致本を復元する。ツリー権限は永続化＝次回以降は完全自動走査。
> **なぜ従来案（記録済み sourceUri からの自動再取込）が成立しないか＝`docs/knowledge/auto-backup-does-not-restore-uri-permissions.md`**
> （実害＝ユーザーが読書位置・栞・追加日を永久に失った実例つき）。実機蔵書は全冊 `contentSha256` を保有＝照合は全冊効く。

- **[本体] 案X の実装**（フォルダ選択 → ツリー走査 → `contentSha256` 照合 → 一致本の本文復元 → 権限の永続化）。
- **[小] Service 経由の復元完了通知が「変換完了」文言のまま**（1行分岐で出し分ける候補）。

## Google Play 公開準備 — 技術トラック

> 一次情報＝`/mnt/c/Users/qingj/Desktop/project/アプリ公開戦略/`（`Google Play公開戦略.md`・`Google Play 初回公開 完全フロー….md`・`外部リサーチ実査結果_2026-07-19.md`）。
> 決定済み方針＝組織アカウント（個人事業主）／最初から API 36／スキン M/P/J は初回リリースに含めず課金実装後のアップデート目玉に温存。
> **ブランド名・鍵バックアップ・ストア素材・提出フォームはユーザー側＝`awaiting-human.md` §4**（applicationId とストア素材はブランド名待ちの前置き）。

- **[前提・維持する設計上の守り]**（2026-07-19 ユーザー裁定「なろう縦書きPDF取込→独自描画は現設計のまま公開」の条件）:
  ①一括・自動 DL を実装しない ②取込までの導線は公式ページを無加工・広告込みで表示し DL ボタン押下は毎回ユーザー ③外部送信なし・端末内完結。
  裁定の根拠＝取込は**ユーザーの明示的手動操作**であり対象は**なろう公式が提供する PDF の端末内整形再表示**＝第14条23項の「自動化された手段による
  アクセス・データ収集」に当たらない、との解釈。残留リスク（記録のみ）＝明示安全圏（WebView 無加工表示）の外側である点・第14条24項の包括条項。
  必要が生じたら企業・団体向け窓口（syosetu.com/businessinquire/）への事前照会という選択肢は残る。
- **[スコープ] 公開機能ゲートの実装（初回アップロード前・必須・裁定済み＝ADR 0027・実装未着手）**: 公開は**明快K のみ**
  （D/C/M/P/J に加え装いの間そのものを隠す。テーマ選択は Skin と別軸なので残す）。作業＝
  ①`build.gradle` に `buildConfigField`（debug=true / release=false・benchmark は `initWith release` で継承）
  ②適用点3つ〔設定「きせかえ」行を出さない／`composable("wardrobe")` を登録しない／`skinFromName()` を `MEIKAI_K` へクランプ〕
  ③テスト（判定はフラグを引数で受ける純粋関数に切り出して両値・設定行の非表示は Robolectric）。**prefs の保存値は消さない**（読み替えるだけ）。
  ⚠️ **入口を消すだけでは穴**＝`skinFromName` は不正値しか弾かず、既に D/M を選んである検証機や Auto Backup 復元は素通りする。
  副作用として**検証機に公開ビルドを入れると明快K で起動**する（debug に戻せば復帰）。
  **要実測＝R8 で D/C/M/P/J が落ちるか**（`valueOf` 駆動のため残る見込み＝隠してもサイズは減らない。`shrinkResources` が
  スキン専用リソースだけ落とすと解禁時に初めて気づくため同時に見る）。**解禁（課金投入）便はフラグ反転＋R8 実機回帰が1セット**。
- **[ID] applicationId の変更（初回アップロード前・必須／ブランド名確定後に着手）**: `com.novelreader` は公開後**永久変更不可**。
  作業＝固有 ID へ変更 →`${applicationId}` 参照（FileProvider 等）は自動追従するので**ハードコードの有無を grep で確認**→
  benchmark の `applicationIdSuffix` 追従も確認。⚠️ 実機では**別アプリ扱い**＝既存検証端末のデータ引き継ぎは無い。
- **[SDK 36 の残る注意点]**（移行自体は完了）: **16KB ページ要件は現状そもそも非該当**＝2026-07-30 の実測で
  **debug/release とも APK に `.so` が1本も入っていない**（`unzip -l <apk> | grep '\.so$'` が0件）。旧記述の「Compose・datastore 由来の
  `.so` が 4ABI×2 入る」は誤りで、Compose も datastore も純 Kotlin/Java・PDFBox-Android も純 Java 実装のため入る道理が無い。
  `zipalign -c -P 16` は通る（resources.arsc の整列は満たす）が、**要件の本体であるネイティブライブラリの `p_align` は検査対象がゼロ**。
  ⇒ 依存バンプのたびの再確認は不要。ただし**将来 `.so` を持つ依存（画像コーデック・暗号・DB エンジン等）を入れた瞬間に該当する**ので、
  そのときは ELF `p_align=0x4000` と `zipalign -P 16` の2点を見る（`zipalign` の `-P` は build-tools 35+ が要る）。
  また JVM テストは全ファイルが `@Config(sdk = [34])` 固定＝
  **targetSdk 35/36 固有の実行時挙動はテストでは一切捕まらない**（実機が唯一の検証手段）。
- **[Play要件] プライバシーポリシー**: 下書き＝`docs/store/privacy-policy-draft.md`（ホスティングは GitHub Pages で裁定済み）。
  Claude 側の残り＝**公開後にアプリ内からのリンクを設置する**（プレースホルダ確定と公開はブランド名待ち＝`awaiting-human.md`）。

## ★UX/Design 全層監査 — 残タスク（2026-07-12）

> **これは何か**: `/mnt/c/Users/qingj/Desktop/project/UX`（UX24層＋Design10層＋公理候補）に対する全体監査（45体・敵対的検証済み）の、
> **残っている作業だけ**の action list。消化済み分の一次情報＝`.claude/plans/ux-design-full-audit-2026-07-12.md`（§A 統合報告／§B 全指摘詳細）＋
> `.claude/plans/ux-audit-batch-execution-20260712.md`。ゲート＝`cd android && testDebugUnitTest`＋`python3 tools/check_design_tokens.py`。
> **意匠絡みは Compose で自己判断せず ADR0005/0014＋モック正本に先に接地**。

### 残1: 発見帯 collapse 退避アニメ 体感の追い込み（deferred polish）

> ⚠️ **本節は obsolete 見込み**——2026-07-29 の発見/装い導線の撤去で帯そのものが消えた。
> `awaiting-human.md` §1-2 の実機目視で撤去が確認できたら**節ごと削除**すること。

- 帯の collapse は「完全退避」で確定・実装・実機OK 済み（2026-07-14）。**残っていたのは退避アニメの体感**＝
  閾値トリガの AnimatedVisibility（8dp 超で 150ms 縮小）はスクロールと完全連動しないため退避開始に軽い不連続感がある。
  やるなら band 高さ∝スクロールオフセットを nestedScroll で連続縮小する collapsing header 本来型へ再設計。
  試作/裁定の記録＝`discovery/bookshelf-band-collapse-D.html`（却下1行restyle vs 完全退避）・`bookshelf-band-tailtile-D.html`・`bookshelf-band-reposition-D.html`。

### 残4: 監査派生 backlog

- **蔵書内フィルタ/series 束ね UI**: ロジック `filterBooksByQuery` は実装済み・UI はモック未表現のため保留（`BookshelfScreen.kt:442`／`ShelfItems.kt:37`）。series 束ねはスキーマ変更要（設計案のみ）。
- **目次の部/編 折り畳み**: 抽出パイプラインに階層データ無し＝**抽出側の新機能**。実PDF→HTML は「フラット確定」＝畳みは前提データ欠如で現状不成立。
- **lint 残 warnings（任意改善・非ブロック）**: UsableSpace×2（`DefaultBookRepository.kt` の抽出前空き容量チェック）＝
  `getAllocatableBytes` は消去可能キャッシュ込みの楽観値で事前チェックが甘くなり ENOSPC で変換終盤失敗を招くため、現状の保守的 `usableSpace` は意図的。
  触るなら API26 分岐・例外処理込みの設計判断が要る（純機械修正ではない）。

## モック逆同期・意匠の宿題

- **[AlertDialog 本文が AA 未達（2026-07-30 発見）]**: M3 は `textContentColor` を `onSurfaceVariant` へ配線するが、
  本プロジェクトは ADR 0014-D で onSurfaceVariant を「装飾専用・**意味を運ぶ文字は InfoText 系へ分離**」と裁定している。
  実測 D LIGHT で `#7C808B` on `#FBFAF8` ＝ **3.94:1**（M3 baseline の紫面だった頃の 3.23:1 からは改善したが未達のまま）。
  削除確認は「不可逆」を伝える面なので本文が読みにくいのは実害。対処案＝ダイアログ本文だけ InfoText 系へ寄せる／
  AlertDialog を包む薄いラッパを1本作る。
  ⚠️ **`tools/check_design_tokens.py` の a11y ペア表に `onSurfaceVariant⇄surfaceContainerHigh` が無く機械検査をすり抜ける**＝
  直すときにペア表への追加も同時にやらないと、直しても検査が守らない。
- **[向き応答していない固定値の棚卸し]**: `Insets.ScrollBottomForFab` / `ChromeHintBottom` はいずれも縦向き前提の 96dp 固定。
  横向きの構造裁定（`awaiting-human.md` §3-1）のついでに見直す。
> 棚卸しの一次情報＝`.claude/plans/mock-drift-inventory-2026-07-16.md`（正本モック全数の未反映リスト・優先順位）。

- **恒久ルール**（破ると実害が出た実績つき）:
  ①コード先行の視覚変更を入れたら**正本モックへの逆同期 or「未反映」注記をセット**で。
  ②モックのプレビューは必ず `mockview`（素の `chrome <file>` 禁止）。
  ③**本棚を下敷きにする候補モックは既定スキンの現行正本（`skins/bookshelf-K.html`）を下敷きに**——旧世代（`bookshelf-D` 直下系・`fusion-D`）は
  語彙参照のみ可・構造下敷き禁止（2026-07-29 の復旧導線候補が旧基盤で描かれ差し戻しになった実害。委譲時は下敷き正本のパスを仕様に明示する）。
  ④**モックのプレースホルダは実データの色域を模す**（栞紙＝地色同値を濃色板でごまかすと一体化バグを素通しする・実証済み）。
  ⑤**モック目視→実機目視の二段検分を必須**（モックは構図の裁定・実機は実データ衝突の検出＝役割が別）。
- **`settings-D.html` が実装に対し3点未反映**（2026-07-29 検出）: 「システムに従う」チップ・縦書き節・本文余白スライダー。
- **`reading-vertical-scroll-D.html` と縦書き実装の構造差**: モック正本は「非没入時は本文がバー下から開始（通常フロー＝重ならない）・
  没入時はバー消去」を規定する一方、実装は縦書き本文の上端クリアランスを**意図的に省略**している
  （`VerticalChapterContent.kt`・横書きの `ReadingBodyTopExtra=64dp` は「横画面で列高約4割潰れ＋クローム追従リフロー」を招くため不採用と理由コメント明記）。
  2026-07-29 の「縦書き時タイトル非表示」で重なりの実害は緩和したが**構造差は残る**＝実機目視の結果しだいでモック逆同期 or 実装是正のどちらかへ。
- **`fusion-D`**: 発見帯の完全退避構造のみ未反映⚠️（全面描き直しは上の★残1の方式確定後）。`bookshelf-D`（旧世代）は退役＝提案基盤に使わない。
- **発見系モックの情報/装飾テキスト再分類（留置中）**: `InfoText` トークン（実装済み＝発見系の情報メタ6箇所を AA(4.5:1) へ・Light #5C606D／Sepia #6C6148／Dark #8A929B）の
  `discovery/*.html` への追従は、`--ink-soft` を共有する**10〜16箇所/ファイルの個別再分類**＋`--info-ink` 変数の新設＋`tools/check_design_tokens.py` への
  マッピング追加が必要＝構造的大改修と判定して留置。**現状の一致検査は InfoText を未トラッキングで PASS＝この層ズレは未検知**である点に注意。
- **richness モック正本の形状統一反映**: `toc-M-rich-R1` / `discovery-M-rich-R1` は画面別seed時代の空のまま（Compose は一枚化で統一済み）。
  正本昇格時に空レイヤを R1s 形へ差し替える（一次情報＝`.claude/plans/richness-expansion-round-2026-07-19.md` 差し戻し節）。

## スキン磨き込み backlog（M/P/J）

> ⚠️ **ADR 0027 追記により初回公開までは新規着手しない**（既存実装は削除せず・解禁は課金アップデート便と同時）。
> 例外＝共通実装の波及・exhaustive when を通す最小翻訳・クラッシュ/データ破損。意匠の裁定待ち分は `awaiting-human.md` §3-2。
> 検分の一次情報＝`.claude/plans/ui-refine-richness-round-2026-07-18.md`／リッチ化の型＝`.claude/plans/richness-expansion-brief-2026-07-19.md`（R1の型10技法・着手時は全読）。

- **[高負荷モード横展開（ADR 0023）]**: ①星図M v8（月齢/惑星/流星群）・v9（変光星/ジャイロ視差）＝ロードマップと全裁定は
  `.claude/plans/richness-expansion-round-2026-07-19.md` ②知見の和モダンD展開→以降各画面 ③**製品トグルの置き場所・既定値・reduce-motion 優先関係の確定**
  （現状は debug トグルのみ＝ADR 0023 の宿題）。
- **[リッチ化の横展開]**（fresh セッションで実施＝2026-07-19 裁定）: 深空リッチ化は本棚Mのみ→M目次/発見・P質感・J発光層へ「R1級」展開。
- **[取込バナーのスキン残]** M `SkyProcessingBanner`／P `WritingBanner` の Web 0%凍結（`source` フィールド配布済み＝各1行の出し分け）。
  Web 一括の本間でバナーが一瞬畳まれる軽微ちらつきは割り切り済み（気になったら）。
- **[構造穴]** `NativeReadingScreen`/`ReadingSettingsSheet` のスキン差分は「加算的クローム/値選択」で exhaustive when 化の対象外＝
  **新スキン追加時にシート色・クローム欠落が無音で起きる**残存リスク（是正は SkinTokens 化など別機構。ルーター30分岐は when 化済み）。
- **[J のトークン整備]** 発見Jの不足トークン棚卸し（扉固有森リニア #1A2A1F 等・回廊森 rgba(31,52,38,.55)・光条α群＝`DiscoveryPortalJ.kt` 設計コメント参照。
  本棚Jは Amb*Portal パレット化済み＝同じ流儀で。本棚Jの「薬と草の base が近縁」も実機で弱ければ base ストップ追い込み）／
  `settings-J` 不足4値（--sheet #141C15・--sheet-line・扉プレビュー大気3値）／
  **内側半透明白の base val 新設**（目次Jの --soft/--dim/--line は GlyphDarkPortal の RGB 借用＝意味が読みにくい。`SoftTocPortal` 等 or base val 1本で意図明快化・値不変。本棚Jの .resume ≒InkPortal 近似の厳密化も同時に）。
- **[J のその他]** 目次に書籍文脈が届かない（目次画面が書籍ID/題名を受けないため象徴文字glyph を省略中＝出すなら骨格のシグネチャ拡張）／
  象徴文字glyph の semantics（極淡96spの装飾テキストが TalkBack 読み上げ対象になり得る→ノイズなら `clearAndSetSemantics {}`）／
  **時刻大気の発展**（①時刻3相の base/floor 色相トークン化 ②長時間常駐で時間帯を跨いだときの追従＝現状は起動時1回固定・produceState＋5〜10分ポーリングが拡張余地）。
- **[M/P のアニメ・データ]** M昇華アニメ＆P1押印アニメのトリガ配線（同型課題＝「読了の瞬間」イベントが本棚Composableに流入しない。
  栞の `playSealStamp/onSealStamped` ラッチと同型の配線を骨格から通せば一度きり再生可能）／
  P2現像の実カード昇格（ProcessingState から仮カセットカードをラック先頭に＝未生成の本を並べる placeholder 方針の設計判断が先）／
  カセットカードの semantics 整備（読了カセットに「読了・CLEAR」等の contentDescription）／
  **読書時間の計測データ新設**（P本棚LCDの TIME 表示は捏造回避で現在非表示・セッション累積の記録機構が要る）／
  **連続読書日数（streak）の記録新設**（P3『連続プレイの炎』のデータ源。prefs で日付集合を持つ最小実装から）／
  J扉の incipit（BookEntity に synopsis 相当が無く省略。抽出時に第1章冒頭を保存すれば表示可能）。
- **[P の見送り分]** 読書の浮遊puck（モックの没入時浮遊操作は共有 tap-to-reveal に畳んだ＝実機で不便なら独立部品化）／
  設定のLCD値チップ・液晶スウォッチ型テーマ選択（標準部品を優先して未採用＝P密度を上げたければ再検討）／
  **ヒンジのアクセシブル代替**（ドラッグ専用でキーボード/スイッチ操作の段送りが無い。tap-to-cycle か semantics カスタムアクション。
  段の取り分 HingeDetentsDp=56/180/260 の体感も実機微調整可）。
- **[横展開候補]** ①「続きに戻る」チップ（`NativeReadingScreen` 参照ジャンプ）も同構造の半透明ピル＝暗色スキン×明色要素で透けうる（稀な状態のため未対処）
  ②題末区切りダッシュのトリムは J目次のみ＝データ由来なので D/M/P 目次・章扉にも潜在（要すれば共通ユーティリティ化）
  ③**hashCode 直割当の偏り**は J扉パレットのみ fmix32 で是正済み（`docs/knowledge/string-hashcode-low-bit-bias-palette-skew.md`）＝
  同型の M `idColorFor`・P `labelColorFor` も目視で気になったら同適用
  ④M視差の信号源精密化（代表セル高150dp×index の近似＝境界で最大12px段差の理論値。実機で目につけば実測高の累積へ）。
- **[磨き込み候補・グレー所見]**（裁定不要・リッチ化ラウンドの入力）: M発見のカード枠/ジャンルchip境界が星空地で薄い・M本棚の未読/読了chipの淡さ・
  J/P の非選択チップ/タブ文字が4.5:1近傍・J発見「今夜の一気読み」カードのみ青紫（パレット外）・P情報密度/太ベゼル/没入SAVE帯の声量・
  P本棚LCD版の主CTA（上端の小さな赤▶）が弱い・M本棚一覧の天体ドット多色の意味整理。
- **[精査待ち]** ①P章扉のpixel話数を一段強調（モック追補要）④J一覧の栞先端色を扉ambientに連動（一覧是正の設計と合流して精査）。

## 読書・目次まわりの残り

- **[縦書き] 章見出しの話数ラベル分離とゴシック化**: データ/トークンが未整備なので**まず整備が要る**（ここまでは Claude 側）。
  意匠の最終形は design 裁定＝`awaiting-human.md` §3。全体像は ADR 0020・プラン `.claude/plans/vertical-reading-mode.md`。
  スパイク計測器は `android/app/src/debug/` に収載済み（P6 の OPPO 較正で再利用できる）。
- **[モーション P1]** 章→章（話送り）は**スワイプ経由はスライド化済み**（引っ張りプレビュー）・**ボタン（前章/次章）経由のみ瞬間のまま据え置き**＝
  要望が出たらスライド化（ADR 0019・競合解析＝`docs/reference/06-competitor-reading-motion.md`・全数値＝`.claude/plans/reading-transition-jank-measurement-2026-07-16.md`）。
- **[U1 新着チェック・Web 統合の残り]** ①**通知後の「続き取得」導線が未整備**（同一 URL 再共有は Duplicate ガードで弾かれる＝差分更新の導線は別途設計）
  ②本棚「続きあり」バッジへの Web 新着反映の配線（marks を読む側＝小）。

## なろうAPI 発見・検索機能（第2の柱）

> Phase 0〜4 完了（現況＝`STATUS.md` §0）。目標ロードマップ・作る機能一覧の一次情報＝plan `~/.claude/plans/api-agy-woolly-swan.md`。
> 構造系の監査残課題は下の「リファクタ / 技術的負債」。

## リファクタ / 技術的負債（deferred）

- **[要判断] 全依存の天井は compileSdk ではなく Kotlin 1.9.22 だった**（2026-07-30 の依存バンプで確定）:
  `JvmMetadataVersion` の受入上限が 1.9.0（次版 2.0.0）で、**`mv=2.1.0` の artifact は機械的に確実死**（Compose 1.11 系・work 2.11 系）、
  `mv=2.0.0` 群（Compose 1.9+・lifecycle 2.9+・tracing 1.3+）はベンダが KGP 2.0.0+ 必須と明言。
  そして Kotlin を上げられないのは **Roborazzi 1.30.1 が Kotlin 1.9.22 ビルドである連鎖**（`settings.gradle` のコメントが正本）。
  ⇒ 次に依存を動かすときは **Kotlin 2.x ＋ compose compiler plugin ＋ Roborazzi を1便**にするしかない。
  **この構造を ADR 化しておかないと、また「別便で」の据え置きが溜まる**（実際5件溜まって今回まとめて解消した）。
  なお `tracing-ktx` だけは今回も据え置き（1.3.0 が Kotlin 2.0 ビルド＝上限超え）。
- **[小] `activity-compose` は宣言 1.8.1 に対し解決 1.8.2**（material3 が推移要求・従前から）。宣言を実態へ揃えると読み違いが減る。
- **[2026-07-27 リファクタ大バッチの残り]**（裁定と依存グラフ＝`.claude/plans/refactor-batch-2026-07-27.md`）:
  ③ Baseline Profile 生成＝**見送り裁定**（profileinstaller/macrobenchmark/StartupBudget は揃っており generator 1本で起動20〜30%改善見込み・要実機）
  ④ 計測・調査群＝**見送り裁定**: ShioriCover の Path 毎フレーム確保（drawWithCache 候補・`BookshelfScrollBenchmark` が予算内なら実害なし＝先に測る）／
  OkHttp ディスクキャッシュ未設定／Room AutoMigration 不使用の方針 ADR 1行／Native 接頭辞・ビュー切替名の整理
- **検索画面 S3＝カテゴリ列の LazyColumn 化（保留・要否判断）**: 重さの正体は「カテゴリ展開状態での操作毎の全画面再コンポーズ」で、
  S1/S2 は解消済み・実機体感は軽快（2026-07-11 実測）。残る理論コスト＝非 Lazy Column 上の22カテゴリ/115チップ
  （`DiscoverySearchScreen.kt:203-207`）の画面外存在コストと「全展開のまま再訪」の初回構成。**体感問題が再報告されるまで保留が妥当**。
- **MigrationTest の coverage-hole**: 「16.json 形状（`web_reading_progress` 無し）→17」経路を構造的に検証できない（chain テストは 14→15 でテーブルが生まれる系譜のみ通過）。既知の実機 v16→v17 未検証と同根。

## workflow / tooling

- **[要裁定寄りだが結論方向は出ている] 再発防止 L3（knowledge の自動注入フック）の採否**: L1/L2/L4 は着地済み（L4＝`docs/known-bugs-registry.md`）。
  残る判断は「knowledge へ `triggers:` を持たせ Edit/Write 時に該当知見を自動注入するフック」を作るか。**判断材料＝L4 が出した無防備の内訳**
  ——現在 **21/63 件**（`なし` 15・`知見のみ` 6）。L3 が触れるのは `知見のみ` の6件だけで、残り15件は knowledge すら無く注入する材料が存在しない。
  **2026-07-30 の実証で論点が動いた**＝「知見のみ」は**注入せずとも機械検知へ変換できる**（tooling の2件で実演＝撤去フックの残骸検知・委譲のスコープ外削除通告。
  knowledge を「読ませる文書」でなく「検知を書くための設計書」として使う路線）。残る6件の見通し＝**変換できる**は
  `snackbar-indefinite-blocks-queue`（Indefinite×actionLabel の同時使用を走査）・`theme-invariant-surface-loses-contrast`（contrast check に「面がテーマ不変か」のフラグ）／
  **部分的**は `fixed-bar-clearance-hardcoded-guess`・`webview-position-mis-record`／**静的検査では無理**は `oem-background-kill`・`benchmark-device-run-fragility`
  （実機依存。ただし「防御コードが在るか」の構造検査なら可）。
  → **L3 は不採用の方向で、ADR には「効かないから」ではなく「上位互換（機械検知への変換）があるから」と書く**（不採用も記録する規約）。
- **[番人の未整備] `awaiting-human.md` がどの機械チェックにも掛かっていない**（ADR 0028 の宿題）: stale-check の
  `check_size_budgets`（打ち消し線）・`check_referenced_files`（名指しファイルの実在）・トークン予算チェックは
  対象を `CLAUDE.md`/`STATUS.md`/`handover.md`/`task_diary.md` で**列挙**しているため新設ファイルが素通りする。
  `.claude/skills/stale-check/check_machine.py` の対象リストへ `awaiting-human.md` を足す
  （ADR 0017 決定5＝規約は機械の番人とセット。宣言だけのルールは数週間で崩れる、が大掃除前の実測）。
- **[較正待ち] 委譲粒度の谷=30 の実測較正**: 委譲ターン計測フック（`count_delegation_turns.py`＝30/60/90…回で子へ中間通告＋完走時
  `~/.claude/projects/...-novel-reader-andloid/delegation-stats.jsonl` へ記録）の分布が貯まったら（目安20〜30件）谷の位置を確かめ、orchestration §0 の「~30」を実測で更新する。
- **[bestpractice 突合の回収候補]**: ①`block_destructive_migration.py` の Bash 経路が素朴な部分文字列一致（`FOO=1 cmd`・`$()` ですり抜け）＝
  settings permissions の `if` フィールド化を検討（主経路の Edit/Write 捕捉は健在で実害小）
  ②サブエージェントの部品別モデル配分（fan-out/読み=haiku・照合=sonnet・監査=opus。現状は env `CLAUDE_CODE_SUBAGENT_MODEL` で opus 固定＝見直しは settings 変更を伴う）。
- **[agy 解除時に再燃する宿題] antigravity-delegate サブエージェントの同期実行が保証されない**（委譲5件中3件で再発＝バックグラウンド起動のまま完了通知が来ない）。
  運用回避＝完了判定を報告でなく**成果物の存在**（`git status`/grep/`ps`）で行う。**根治候補**＝プラグイン側で agy 起動を同期実行へ強制するか wrapper にポーリング内蔵。
  ※2026-07-26 にプラグインごと無効化したため当面は発生しない。
- **[運用] worktree(ext4) 作業の冒頭で `gw :app:lintDebug` を回す**: ローカルの自動コミットゲートは現存しない（かつての hook は撤去済み＝導入以来 fail-open だった）。
  2026-07-30 以降は **CI の Android Lint が毎 push で errors=0 を担保する**ので、このスイープの役目は**push 前に赤を見つける**前倒し検知。
  基準＝0 errors/31 warnings（ModifierParameter×3・UsableSpace×2 は意図的）。

## 実行捏造検知器（ADR 0006）残タスク

> エンジン＝`.claude/hooks/detect_fabricated_execution_core.py`。完了分は **ADR 0006（増補含む）と git log が正本**。以下は開きのみ。

- **Tier B 汎用主張の免罪の限界**（事象D）: 「セッション内に成功実行が1回でもあれば免罪」で後半の汎用捏造を取りこぼす。
  Tier E カテゴリ別突合が**現ターン分**の同根系列を吸収したが、**過去ターンの汎用主張の掘り下げは将来課題**。
- **[保留設計] 案3＝委譲主張の E2 突合（opt-in）**: 「〜を委譲した／agy に生成させた」等の委譲完了主張を、
  委譲先 transcript（`subagents/agent-<id>.jsonl`）の tool_use とカテゴリ突合して裏取りする案。**真陽性サンプルが皆無のため保留**（設計要点のみ保全）。
- **D5 対象語突合の字面依存FPクラス**: D5 は帰属対象の名詞（違和感/懸念/指摘…）の**字面**を実入力に探すため、
  ユーザーが真に指摘したが当該名詞を書かなかった場合「あなたの指摘は的を射て」型が潜在FP化しうる（現コーパス実測 0件・非ブロック Tier D で被害限定）。同義語・意味突合は将来課題。
- **Tier E の Stop 昇格の再判断**: 新既定 ABCDE での CLI 運用実績（真陽性の積み上がり・FP 率）が揃ったら再判断。
  昇格には conf 設計の引き上げ（現 0.55-0.7 → Stop 閾値 0.8）または Stop 側の per-rule 閾値の新設計が必要。
- **意味照合系検知器**（着想段階・スコープ外構想）＝生成コード不具合・外部リサーチ捏造（正解データ事象B/C）。

## D. 長期・品質（backlog）

- **超長編抽出エッジ残差の③アポストロフィ座標順**（N6169DZ・章題ドリフト残2件）: `兎'ｓ`↔`'鳥…` の座標順ずれで**1:1コードポイント置換不可**＝実質 won't-fix。
  基準＝`ab-review/golden_regression`、詳細＝task_diary #35。

## A2. UIスキン機構（M/P/J 統合済み＝main／残＝C 夜行・将来送り）

> 機構の裁定＝ADR 0021・0022／フェーズ詳細＝`.claude/plans/ui-skin-framework-2026-07-17.md`・`.claude/plans/skin-compose-implementation-2026-07-17.md`／
> 生成規範＝`.claude/plans/skin-design-digest-2026-07-17.md`＋memory `feedback-skin-design-judgment-criteria`。
> **C 夜行の再着手はユーザーからのイメージ聴取が前提＝`awaiting-human.md` §4。**

- **[C 夜行の「らしさ本体」＝構造・演出層]**（別タスク・都度追加）: モックの体感は
  〈本棚=続きからヒーロー＋静かな1列リスト・栞書影の C 用ミュート・ember の効かせどころ（章番号エブロウ/上端ヘアライン進捗/続きからラベル）・
  極小クローム＋読書の浮きピル〉に宿っており**色トークンだけでは D ダークと区別がつかない**（実機で確認済み）。
  **着手はモック作成（発見/目次/設定の C 版含む欠落分）から**＝スキンごと構造切替の枠は外枠に含めず、実装は画面単位で都度。
- **[保留中の候補]** Q 読書の庭＝差し戻し保留（最終版は `skins/candidates/*-Q.html`）／候補 L/N/O/R/S＝本棚1枚のみ（同 candidates/）／
  P はっちゃけ試作の不採用4画面＝`skins/candidates/hatchake/`。旧A〜J原本は claude.ai/design `ui-n-phase0/`。
- **[ツール]** 候補比較＝`tools/build_skin_gallery.py`・画面別ギャラリー（等倍・スキン単体可）＝`tools/build_screen_gallery.py [ID]`。
  プレビューは必ず `mockview`。DesignSync は主セッション限定。任意＝claude.ai 側への収蔵バックアップ同期は未実施。
- **[将来送り（ADR 0021）]** 栞「型」軸（A箔/C小口/D蔵書印/E綴じ紐）／D 以外のテーマ変種／旧候補の移植（I は退役のまま）。
  A〜J 資産は claude.ai/design（プロジェクト `Novel Reader UI`・projectId `bb5a35c8-70ac-4efa-bb03-1579d3f11d93` の `ui-n-phase0/`・`DesignSync: get_file` で再取得可）に保持。
  `bookshelf-D` へのセピア変種追加も再検討枠（現状は `SepiaColorScheme` が本棚セピアの正本）。

## 思いつき・取りこぼし（随時追記）

- **[実機検証 2026-07-30 で拾った軽微な不備・まとめて片付ける枠]**:
  ①装いの間で「これを装着」がステータスバー領域に**二重描画**される（カルーセル横スワイプ直後の1フレーム・別セッションで2回再現。
  ただし screencap の合成アーティファクトの可能性も残り確度は中）②星図M の ⋮ メニューが最上部で切れて描画される
  （中身は debug 限定の1項目のみ・実害小）③M の再取込ダイアログ（3ボタン）が2段に割れ、確定「場所から探す」だけが上段に浮く
  （M3 AlertDialog のボタン溢れ時の既定挙動だが主アクションが分離して見える）④本を削除しても
  `cache/pdf_import/<ncode>.pdf` が残る（削除時の「取込元PDFも削除」は `sourceUri` を持つ本だけが対象＝なろうPDF由来は対象外）。
- **[文字の折り返し・所見]**（同日の実機観察）: グリッドの状態行「本文なし・タップで再取込」が2列グリッドで「…再/取込」と割れる
  （選択モードでは ⋮ が消えて1行に収まる）／M のカード readout が「まだ星は結ばれてい/ない」で割れる／
  設定の「きせかえ」副文が「（現在: 和モダ/ン）」と閉じ括弧だけ次行に落ちる／設定カードで「文字と組版」行だけリーディングアイコンが無い。
> レビュー中・実装中に出た宿題や着想で、まだ上の各節に整理していないものをここへ。育ったら該当節へ移す。

- **[スキン・候補] 2026-07-25 モデルA/B生成実験の生存2案**: 製図室（青写真）＝`skins/candidates/bookshelf-seizushitsu.html`・
  カプセル売場（ガチャ）＝`skins/candidates/bookshelf-capsule.html`（12案中この2つのみユーザー合格）。正式スキン化は別ラウンドで。
  起案手順の知見＝`docs/knowledge/skin-concept-first-mock-second.md`（コンセプト行で数打ち→当たりだけモック化）。
- **[スキン・着想] アニメ等のキャラクターをもとにしたスキンモック**（2026-07-17 ユーザー着想）: 次のスキン起案ラウンドの案。
  着手時の論点＝実在IPの意匠・名称は権利面の検討が要る（特定作品の直写でなく「キャラクター的な世界観の翻案」に留めるか、の裁定から）。
