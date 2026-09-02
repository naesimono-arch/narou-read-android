# 便C1 — エミュでしか作れない前提で撮った2件（2026-09-02）

**まず開くのは `index.html`**（`mockview docs/verification-shots/c1-2026-09-02/index.html`）。
コマ・静止画・録画Bを1枚に data URI で埋め込んだ自己完結ページ＝**単体コピーしても参照が切れない**。

端末＝AVD `nr_b`（`emulator-5554`・API 36・1080x2400・density 420・fontScale は明記なければ 1.0）。
APK＝`~/ext-build/novel-reader/app/outputs/apk/debug/app-debug.apk`（debug・2026-08-26 ビルド。
以後の棚関連コミット 1640f6e は並び順のみで FAB 条件・復旧分岐に触れない）。
**蔵書はすべてエミュ内の使い捨て**＝実機 PGEM10 の実蔵書7冊とは無関係（この便で実機には触れていない）。

⚠️ **判定（良し悪し）は人間**。ここにあるのは「前提を作って踏んで撮った」ところまで。
⚠️ `03*` `04` `05` に写る本は `sample_pdfs/N1453LW.pdf`＝**実在のなろう作品**（題名が写る）＝
**掲載素材に流用しない**（ストア用は `docs/store/assets/screenshots/seed-demo-library.py` の書き下ろし蔵書で撮り直す）。

| ファイル | 中身 | 前提の作り方 |
|---|---|---|
| `index.html` | 全部入りの検分ページ（コマ18枚＋静止画5枚＋録画B） | — |
| `recB.mp4` | **蔵書0のままフィルタ「読了」で `isEmptyShelf` だけ反転**＝FAB だけが出没する最も純粋な経路 | 空棚で `読了` → `すべて` を交互に押す |
| `recA.mp4` | 長押しの選択モード退避／キャンセルで復帰／最後の1冊を削除して空棚化 | `make-fixtures.py` の書き下ろしダミー1冊 |
| `frames/B_*.png` `A_*.png` `C_*.png` | 上の遷移前後のコマ（下端 1080x420 を切り出し） | `ffmpeg -fps_mode passthrough` |
| `02-shelf-missing-badge.png` | 本文なしバッジ＋一括バナーの棚 | 下記①〜④ |
| `03-recovery-autopdf.png` / `03b-…-fs20.png` | **復旧ダイアログ①AutoPdf 分岐**（1.0 / 2.0） | 下記①〜④ |
| `04-sweep-breakdown-autopdf.png` | 一括バナーの内訳。「元のPDFから自動で再変換（取込元の記録と権限あり）1冊」が初めて非0 | 同上 |
| `05-autopdf-restored.png` | 「再取込する」を実押しした後＝本文が戻った棚（`books.id` は `ae173517` のまま） | 同上 |
| `tools/make-fixtures.py` `tools/setpref.py` | 蔵書ファブリケータ／prefs 書換（便B2 由来・serial を `emulator-5554` に直したもの） | — |

**動きの測り方**（目視でなく数値）＝`ffmpeg -vf "crop=280:90:730:2000,signalstats,metadata=print:file=-"` で
FAB 矩形の平均輝度を全コマぶん出す。81＝FAB全開／147＝選択バー／230＝素の背景。詳細＝
`docs/knowledge/emulator-screenrecord-and-synthetic-input-limits.md` §5。

**①AutoPdf を出す前提**（本番コード不変。真因と根拠＝
`docs/knowledge/device-visual-checks-blocked-by-fixture-preconditions.md`）:
①小PDFを SAF で取込 → ②同じパスの PDF を壊して**同じ URI で取込を失敗させる**（失敗経路だけが永続権限を残す）
→ ③PDF の中身を戻す → ④その本の `index.html` を消してカードをタップ。

**未収録**: 録画C（空棚→取込開始で FAB が出る経路）の mp4 は容量のため入れていない（45s の大半が SAF ピッカー操作）。
判断材料の4コマは `frames/C_*.png` と `index.html` に入っている。
