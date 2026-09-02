# 実機（emulator-5556 / AVD `nr_b`）検分ショット — 2026-08-26

> **2026-09-02 に scratchpad から移設（全ファイル）**。台帳がここを指すのは
> `awaiting-human.md` §1 の実機ツアー（端フェード／FAB 出没／復旧ダイアログ縦3段／表示設定シート／
> あらすじ案B／作品詳細の書影＝**まだ実機では見ていない6件**）＝**エミュ側の見えがここに揃っている**ため。

APK＝監督ビルドの **debug**（`/home/qingj/ext-build/novel-reader/app/outputs/apk/debug/app-debug.apk`・02:18 生成、02:23:55 install）。
同時刻の release は `app-release-unsigned.apk`＝**未署名で install 不能**だったため debug を採用。
端末＝`sdk_gphone64_x86_64` / API 36 / 1080x2400 / density 420。

⚠️ **装いは K（明快）**。`app_skin` キー不在＝`skinFromName` の既定は `MEIKAI_K`（`ui/theme/Skin.kt:36`「明快K＝新デフォルト」）。
**`PrefKeys.kt:33` の KDoc「キー不在＝D（既定装い）」は腐っている**（別便で直す価値あり）。
＝下の全ショットは D ではなく **K の見え**。

## ファイル → 何の画面 → 対応するモック → 踏むために作った前提

| ファイル | 何の画面 | 対応するモック（正本） | 踏むために作った前提 |
|---|---|---|---|
| `01-edge-fade-K-right-fs20.png` | 本棚・状態チップ行の**右端フェード**（「読了」が右端で切れて抜けていく） | `docs/design-candidates/skins/bookshelf-K.html` の `.chipsrow` `--fade-w:56px`／`--fade-stops`／`.can-r`（裁定＝**ADR 0036**） | **fontScale 2.0**。1.0・1.5 では4チップが収まり**発火しない**（1.5 は「読了」右端が 951/1080 でぎりぎり収まる）＝ADR 0036 の「1.0 では版面不変」の実測確認 |
| `02-fab-absent-empty-shelf-K.png` | 空棚。CTA 2つ（作品をさがす／PDFを追加）**のみ・FABなし** | `docs/design-candidates/skins/bookshelf-K.html`（裁定＝**ADR 0037**「空棚では FAB を引っ込める」K 固有） | DB の books/progress/web_* 等を全 delete ＋ `files/novels/*` 削除＝**蔵書0冊**。`KEmptyState` は蔵書0のときだけ |
| `02-fab-present-seeded-K.png` | 蔵書9冊。**FAB（PDFを追加）が右下に復帰**（`[673,1977][1038,2124]`） | 同上（出没の対になる側） | `seed-demo-library.py --serial emulator-5556 --push`＝**書き下ろしデモ蔵書9冊**（実在なろう作品は写っていない） |
| `03-recovery-dialog.png` | 本文欠落本のカードタップ→**復旧ダイアログ**。ボタン3つが**縦3段・右寄せ**（場所から探す／自分で選ぶ／やめる）。上部に「本文データが見つからない本が 1冊あります」帯も同時に写る | `docs/design-candidates/bookshelf-reimport-badge-D.html`／`bookshelf-reimport-sweep-D.html` | 1冊の `chap_*.html` 221本を `/data/local/tmp` へ退避＝**本文欠落**を作り、そのカードをタップ（発火経路＝`ui/BookshelfScreen.kt` の `reimportPlans`） |
| `04-reading-settings-sheet-portrait.png` | 読書画面の**表示設定シート**（縦）。テーマ4チップが**2段に折れる**／本文の向き／文字サイズ・行間・本文余白の3スライダー | `docs/design-candidates/reading-settings-livepreview-D.html`（別案＝`reading-gear-alt-D.html`） | 本棚→書影タップ→**目次**→第1話→本文中央タップでクローム表示→「表示設定」 |
| `04-reading-settings-sheet-landscape.png` | 同シート（横）。テーマ4チップが**1段に収まる**／シートは中央寄せの柱 | 同上 | `user_rotation 1` ＋ `accelerometer_rotation 0`（**両方要る**＝auto-rotate が生きていると user_rotation が無視される） |
| `04-reading-settings-sheet-open.mp4`<br>`04-sheet-open-framesheet.png` | シートがせり上がる**動き**（動画＋18コマのコンタクトシート） | 同上 | `screenrecord` ＋ Windows 側 `ffmpeg.exe` でコマ抽出（下の「screenrecord」節） |
| `04b-T1-landscape-search-periodtabs.png` | **横向き T1**：さがす画面。期間タブ（日間〜新着）が**検索バーと同一行・その右**に並ぶ | `docs/design-candidates/discovery/discovery-search-D.html` | 横向き。採取済み数値「検索バー右端から 18.3dp で同一行」の**見え方**side |
| `04c-T1-landscape-snackbar.png` | **横向き T1**：SnackbarHost の寄り。スナックバーは **Rail 込みの画面中央**に出る（本文中央より左） | `docs/design-candidates/skins/bookshelf-K-landscape.html` | 本棚タブ**に居る状態で**（SnackbarHost は BookshelfScreen 側）`am start -a VIEW -d <なろうURL> -n com.novelreader/.MainActivity`＝blockedHosts 由来の案内スナックバー |
| `06-detail-cover-top.png` | 作品詳細の上部＝**書影**（生成書影・栞＋縦題字）＋題名／作者／ジャンル、素性4項目 | `docs/design-candidates/discovery/discovery-detail-cover-size-compare.html`／`discovery-detail-cover-random-fit-D.html`／正本 `discovery/discovery-detail-D.html` | さがす→ランキング（週間）1位をタップ。**ネットワーク必要**（エミュから疎通あり） |
| `05-detail-synopsis-scrolled.png`<br>`05-detail-synopsis-bottom.png` | 同画面の**あらすじ**と固定バー。バーは現状 **縦3段のまま**（縦書きPDFを取り込む／なろうで読む／本棚から外す） | 正本 `discovery/discovery-detail-D.html`／一時ドラフト `discovery/candidates/detail-fontscale-viewport-compare.html` | 同上＋縦スクロール |

## screenrecord — **使える**（ColorOS との最大の差）

`screenrecord` は AOSP エミュで**動く**。アプリ描画も正しく入る（722KB / 340KB の H.264 が取れた）。
`ffprobe.exe`／`ffmpeg.exe`（`/mnt/c/Users/naesimono/scoop/shims/`・WSL interop で呼べる）でコマ単位に割れる。
※ WSL 側に ffmpeg は無い＝**mp4 を `/mnt/c` へ置いてから Windows 側 exe を叩く**。新しめの版なので
`-vsync` は廃止＝`-fps_mode passthrough`、`tile` 出力は `-frames:v 1` が要る。

⚠️ **ただし 16ms 窓ではない**。`screenrecord` は**変化があったときだけ**コマを吐くので、
アニメ中の実測コマ間隔は **20〜63ms（多くは 28〜50ms）**、静止中は 1.4〜3.1 秒空く。
＝**60Hz の 1〜2 フレームだけの現象は取りこぼしうる**。「骨・残像・Predictive Back」を
これで詰めるなら、**取れなかった＝無い、とは言えない**ことに注意。

## 踏めなかったもの

- **① 左端フェード（`.can-l`）**: チップ行を右へスクロールさせられず**未取得**。行は実装上スクロール可
  （`ui/skins/k/BookshelfK.kt:713-725`＝`horizontalScrollEdgeFade` + `horizontalScroll(scrollState)`）だが、
  **adb の合成入力では横ドラッグを必ずタブの HorizontalPager が奪う**（`input swipe` 400ms／1500ms、
  `input motionevent` の小刻み16手、いずれも本棚→さがす へページ送りになった。見切れた「読了」チップの
  タップでも行は動かず＝bounds 不変）。**実指でも同じかは未確認**＝人の指で1回試す価値あり。
- **② FAB の出没「アニメ」**: 静止の前後2枚は取れたが動きは未取得。`読了` フィルタ（0件）では
  **空棚にならず FAB は消えない**（`KEmptyState` は**蔵書0冊のときだけ**）＝フィルタ経由では出没を起こせない。
