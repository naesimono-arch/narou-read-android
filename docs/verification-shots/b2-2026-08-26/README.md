# 便B2 — 実機で発火経路が無かった5件＋走査の協調中断（emulator-5560 / AVD `nr_c`）

> **⚠️ このディレクトリは便B2 の“抜粋”です（2026-09-02 に scratchpad から移設）。**
> 移したのは**まだ裁定が生きているもの**だけ＝`a-empty-skin-WAMODERN_D.png`／`a-empty-fs20-WAMODERN_D.png`
> （D の空棚で CTA と FAB が同じ操作を二重に出す症状＝`awaiting-human.md` §3-1 の裁定待ち）と、
> 対比用の `a-empty-skin-MEIKAI_K.png`（K は `BookshelfK.kt` の `!isEmptyShelf` で解消済み）。
> **下の表に載っている他のファイルはここには無い**（役目を終えたものは git へ入れない方針＝入れると履歴に残り続ける）。
> 実体は撮影セッションの scratchpad に残置してあるが**セッション固有＝いつ消えてもおかしくない**。
> 表は「ファイルの索引」ではなく**前提の作り方（コマンド列）の記録**として読むこと——こちらが本体の価値。
> 蔵書ファブリケータ `make-fixtures.py` / `setpref.py` は `../c1-2026-09-02/tools/` に置いてある（serial だけ書き換えて使う）。


撮影 2026-08-26。端末＝`emulator-5560`（AVD `nr_c`・API 36・1080x2400・density 420・fontScale は明記なければ 1.0）。
APK＝`/home/qingj/ext-build/novel-reader/app/outputs/apk/debug/app-debug.apk`（**debug**＝`SKIN_SWITCHING_ENABLED` は
`android/app/build.gradle:146` により **true**。**フラグの反転はしていない**＝解禁便は不要だった）。

**判定（良し悪し）は人間**。ここに置いてあるのは「前提を作って踏んで撮った」ところまで。

⚠️ **掲載素材に流用しない**: `c-*` と `d-dialog-02` に写る2冊は `sample_pdfs/` の**実在なろう作品**（題名・作者名つき）。
ストア用は必ず `docs/store/assets/screenshots/seed-demo-library.py` の書き下ろしデモ蔵書で撮り直すこと
（自衛線＝`docs/store/listing-draft.md` §0）。`ep4d` / `mis0N` / `web01` / `n900Nzz` は**この便の書き下ろし**なので流用可。

## 共通の前置き

```bash
A=~/Android/Sdk/platform-tools/adb            # 非対話 Bash は PATH に adb を持たない＝絶対パス
$A -s emulator-5560 root                      # /data/data 直操作に必須（他エージェントの unroot で外れることがある）
$A -s emulator-5560 install -r /home/qingj/ext-build/novel-reader/app/outputs/apk/debug/app-debug.apk
```

道具はこのディレクトリに同梱:
- `make-fixtures.py` — 蔵書ファブリケータ（章数・本文欠落・contentSha256 を指定して books 行ごと作る）
- `setpref.py`（`<scratchpad>/setpref.py`）— `app_prefs.xml` の書き換え（**force-stop → pull → 編集 → push**）。
  ⚠️ **端末側 sed / grep での XML 直編集はやめる**——属性の引用符が落ちて `<string name=app_skin>` のような
  不正 XML になり、prefs が丸ごと読めなくなる（この便で1回踏んだ）。

## ファイル対応表

| ファイル | 何の画面 | 前提をどう作ったか |
|---|---|---|
| `a-empty-01-intro-card1.png` | 初回起動の教示「はじめに」1枚目（空棚の上に被る） | `$A -s emulator-5560 shell pm clear com.novelreader` → `am start -n com.novelreader/.MainActivity` |
| `a-empty-02-shelf-K-grid.png` | **蔵書0の空棚（明快K）** | 上の続きで「あとで」をタップ。**FAB は出ない**（`BookshelfK.kt:273` `!isEmptyShelf`＝2026-08-20 裁定②で撤去済み） |
| `a-empty-skin-{MEIKAI_K,WAMODERN_D,SEIZU_M,CARTRIDGE_P,PORTAL_J}.png` | 蔵書0の空棚を5スキンで | `python3 setpref.py app_skin string <SKIN> intro_about_shown boolean true intro_reading_shown boolean true intro_search_shown boolean true notif_priming_shown boolean true` → `am start` |
| `a-empty-fs20-*.png` | 同上 **fontScale 2.0** | `$A -s emulator-5560 shell settings put system font_scale 2.0` を足すだけ |
| `c-picker-01.png` | SAF ピッカー（Downloads） | 空棚 CTA「PDFを追加」→ Show roots → Downloads |
| `c-battery-01-fs10.png` | **電池最適化ダイアログ（1.0）** | `pm clear` 直後（`battery_dialog_dismissed` 未設定＝false）＋ `/sdcard/Download` の PDF を1冊取り込む。`isProcessing` の false→true で出る（`BookshelfScreen.kt:321`）。**「二度と表示しない」は押していない** |
| `c-battery-02-fs20.png` | 同 **fontScale 2.0** | `settings put system font_scale 2.0` のうえで2冊目を取り込む（1回消しても次の取込で再び出る＝フラグ未消費） |
| `b-toc4digit-0{1,2,3}-{K,M,J}.png` | **4桁話ラベルの目次**（全1240話・第1027〜1037話） | `python3 make-fixtures.py --id ep4d --title '…' --chapters 1240 --chapfmt '第{n}話　旅のつづき'` → `sqlite3 <DB> "INSERT OR REPLACE INTO progress VALUES('ep4d','chap_1028.html',0,0,1787680000000,0);"`（**進捗を入れると目次が4桁の位置へ勝手にスクロールする**＝1240行を手で送らずに済む） → 本を開く→本文タップ→「目次」 |
| `d-shelf-missing-01.png` | 欠落バッジ＋一括バナー「本文データが見つからない本が 4冊あります」 | `make-fixtures.py --missing`（`index.html` を消す＝`BookEntity.hasContent` の判定点）を3冊＋既存の取込本1冊で `rm index.html` |
| `d-dialog-01-scan3btn.png` | **復旧ダイアログの3ボタン縦積み**（場所から探す／自分で選ぶ／やめる） | `make-fixtures.py --missing --sha <64桁hex>`＝`sourceUri` NULL・`contentSha256` あり → `PickPdfNoRecord(scanSha256≠null)` → `ReimportScanDialog` |
| `d-dialog-02-permlost.png` | 同ダイアログの②分岐（「取込元の PDF: N0833HI.pdf」の行つき） | SAF で取り込んだ本の `index.html` を消しただけ。⚠️ **AutoPdf にならず `PickPdfPermissionLost` に落ちた**（下の「気づき」参照） |
| `e-web-02-shelf-K.png` | 本棚に Web（なろう・未取込）行が並ぶ（明快K） | `sqlite3 <DB> "INSERT OR REPLACE INTO web_novels VALUES('n9001zz','…','…',412,1787600000000);"` を2行 |
| `e-web-03-listface-M.png` / `e-web-05-listface-P.png` / `e-web-07-listface-J.png` | **M/P/J の一覧面**に Web 行（`web:<ncode>`） | スキン切替後、ヘッダの `content-desc="一覧表示に切替"` を押す。⚠️ **没入面には選択モードが無い**＝一覧面へ移らないと配線は見えない |
| `e-web-04-selection-M.png` | M 一覧面で Web 行を長押し→選択モード（「1 天体を選択」「星を消す」） | `input swipe <x> <y> <x> <y> 900`（同座標＋900ms＝長押し） |
| `e-web-06-webmenu-P.png` | P 一覧面の Web 行 ⋮ メニュー（縦書きPDFを取り込む／本棚から外す） | `content-desc="未取込作品のメニュー"` をタップ |
| `e-web-01-importing.png` | Web 取込の実行中バナー「章 N/598 取得中」＋停止 | `am start -a android.intent.action.VIEW -d "https://kakuyomu.jp/works/…" -n com.novelreader/.MainActivity`（**宛先 `-n` を省くと Chrome へ流れる**） |
| `e-web-08-doubletap-guard.png` | **二重押しガード**（AutoWeb ダイアログの「再取得する」が押せない＋「いま別の取得が動いています」） | ①`sourceUrl` を持つ欠落本を1冊作る（`make-fixtures.py --missing` → `UPDATE books SET sourceUrl=…, sourceSite='kakuyomu'`）②別の Web 取込を走らせたまま ③その本のカードをタップ。dump 上は `clickable="false"`（`enabled` は View 側の値なので true のまま＝**enabled を見て「押せる」と読み違えない**） |
| `e3-sweep-01-breakdown.png` | 一括再取込の内訳ダイアログ | 一括バナー →「まとめて再取込」 |
| `e3-scan-01-banner-running.png` | **走査の進捗バナー（7 / 24）＋停止** | 下記「③ 走査の協調中断」の手順 |
| `e3-scan-03-cancelled-report.png` | 中断後の結果「途中で停止しました／24件 のうち 14件 を調べました。」 | 同上（走査中に「停止」） |

## ③ 走査の協調中断 — 実機で踏めなかったものが踏めた理由と手順

実機は `tree/primary:Download`（36GB）の SAF 列挙が終わらず、3回とも**列挙フェーズ**で停止が着弾していた
（結果は `147件 のうち 0件`）。エミュは**ツリーを小さく作れる**ので、列挙が数百 ms で終わり
「ハッシュ中の停止」を初めて踏める。

```bash
# 1) 小さい走査ツリー（列挙が即終わる大きさ・1件あたりのハッシュに時間が乗る大きさ）
$A -s emulator-5560 shell 'mkdir -p /sdcard/Download/scan-small; cd /sdcard/Download/scan-small; \
  for i in $(seq 1 24); do dd if=/dev/zero of=bulk_$i.pdf bs=1M count=80 2>/dev/null; done'
# 2) 走査対象＝contentSha256 を持つ欠落本。ツリーに無い指紋にしておくと途中で早期終了しない
python3 make-fixtures.py --id mis01 --title '行方不明の記録　その1' --chapters 8 --missing \
  --sha deadbeef00000000000000000000000000000000000000000000000000000001
# 3) アプリ側: 一括バナー →「まとめて再取込」→「PDFのある場所を選ぶ」→ SAF ツリーピッカーで
#    Download → scan-small → USE THIS FOLDER → ALLOW（以後は pdf_library_tree_uri に記憶され無操作）
```

**実測（emulator-5560・80MB×24件）**

| 量 | 値 |
|---|---|
| 列挙フェーズ | 最初の観測（確定タップの約2.8s後）で既に `total=24`＝**実質ゼロ**（実機はここで頭打ちだった） |
| ハッシュ速度 | 4件/2.99s＝**0.748 s/件**（24件の完走＝約18s） |
| 停止なしの完走 | 「一致するPDFが見つかりませんでした／**24件 のうち 24件** を調べました。」 |
| 中断ラン① | 停止タップ時の観測 `hashed=10`（dump スナップショット）→ 結果 **14件**。同ランのレートで tap 時刻へ外挿すると 13.9 ⇒ **超過 0〜1 件** |
| 中断ラン② | 観測 `hashed=6` → 結果 **9件**。外挿 9.4 ⇒ **超過 0〜1 件** |
| 中断後の表示 | 「**途中で停止しました**」＋「24件 のうち N件 を調べました。」＋未一致本の内訳 |

⇒ **設計どおり**（`PdfFolderScan.kt:152-158`＝ループ先頭で `isCancelled()` を見る＝「いま読んでいる1件の完了後」）。

⚠️ **計測の作法**: `uiautomator dump` は**完了まで 2.5〜3.0 秒かかり、スナップショットは dump 開始時点**。
つまり読めた `hashed` は最大3秒＝約4件ぶん古い。**observed と最終値の差をそのまま「中断の遅れ」と読むと 4件遅れに見える**。
同じランの実測レートで tap 時刻へ外挿してから比べること。
盲打ちで「開始から N 秒後に停止」する測り方は**採ってはいけない**——4台のエミュ同居でホストが混み、
ラン間でレートが揺れる（+8s→12件 と +14s→11件 という逆転を実測）。**必ず同一ラン内で完結させる**。

## この便で分かった「次便がハマらないための事実」

- **端末側 toybox `grep` は日本語パターンに当たらない**（`grep -c 停止しました dump.xml` が 0）。
  端末内で完結する検出ループは **ASCII だけ**で書く（例: `grep -q 'text="24'`）。ホストへ pull してから grep でも可。
- **`uiautomator dump` は1ステップ遅れて見えることがある**（スキン切替直後など）。
  **screencap は正しい**ので、**見えの判定は必ずスクリーンショット側**で行う。dump は座標取りの道具と割り切る。
- **`am force-stop` → prefs 書換 → `am start`** が skin 切替の最短経路（装いの間を UI で通る必要はない）。
- **教示「はじめに」は `pm clear` のたびに必ず出て空棚を覆う**。`intro_about_shown` / `intro_reading_shown` /
  `intro_search_shown` を true にしておくと以後は出ない。
- **SELinux**: `make-fixtures.py` は展開の**前**に `ls -Zd` / `stat -c %u:%g` で `files/novels` のラベルと uid を控え、
  展開後に `chown -R` + `chcon -R` する（skill §3 のとおり。踏むと「R8 が Room を壊した」と誤診する）。

### 気づき（判定は人間・この便では踏み込まない）

- **SAF（Downloads プロバイダ）で取り込んだ本が `AutoPdf` にならない**: 取込直後に `index.html` を消すと
  `PickPdfPermissionLost`（＝永続読取権限が生きていない扱い）へ落ちた。`sourceUri` は
  `content://com.android.providers.downloads.documents/document/raw%3A%2Fstorage%2Femulated%2F0%2FDownload%2F….pdf` で
  DB には残っている。**①「元のPDFから再取込」ダイアログはこの便では一度も出せていない**（＝未検証のまま）。
  エミュのプロバイダ固有か実バグかは**判定不能**。
- **一括バナーは一度消費すると戻らない**（`reimport_sweep_seen_ids`）。走査をやり直したいときは
  カード → 復旧ダイアログ →「場所から探す」の1冊経路を使う（こちらは指紋を消費しない）。
