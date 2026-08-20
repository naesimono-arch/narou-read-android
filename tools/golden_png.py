#!/usr/bin/env python3
"""golden PNG 走査の共通土台（純標準ライブラリ・zlib だけで PNG をデコードする）。

なぜ自前デコーダか: 走査スクリプトは CI と record 直後の両方で回したいが、この環境には Pillow も
numpy も無く（`sudo` 不可＝新規導入も自由に効かない）、追加依存は「入っていない環境では黙って
skip される検査」を生む。zlib は標準添付なので、8bit 非インターレースの PNG（Roborazzi の出力形式・
実測 104枚すべて bitdepth=8 / colortype=6 / interlace=0）に限れば依存ゼロで読める。

なぜ「速度」を設計に織り込むか: 素直に画素ループを書くと 720x1280 x 104枚 = 9600万画素で分オーダー
になり、CI にも record 直後の人力運用にも載らない。そこで
  ・行の要約は `sum(bytes)`（C 実装）で取り、詳細計算は候補行だけに絞る
  ・インク判定は `bytes.translate`（C 実装の 1byte→1byte 写像）＋ int の bitwise OR で 1 行まとめて出す
  ・run（連続インク区間）の抽出は `bytes.find`（C 実装）で舐める
という形にしてある。Python レベルの画素ループは残していない。
"""
import struct
import sys
import zlib
from pathlib import Path

# ---- デコード ---------------------------------------------------------------


class UnsupportedPng(Exception):
    """このデコーダが扱わない PNG（16bit・インターレース・パレット）。黙って skip せず呼び出し側で落とす。"""


def decode_rgba(path):
    """PNG を (width, height, rows) で返す。rows[y] は RGBA 並びの bytearray（長さ width*4）。"""
    data = Path(path).read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise UnsupportedPng(f"PNG シグネチャが無い: {path}")
    pos = 8
    width = height = colortype = None
    idat = []
    while pos + 8 <= len(data):
        (length,) = struct.unpack(">I", data[pos:pos + 4])
        ctype = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        if ctype == b"IHDR":
            width, height, bitdepth, colortype, _comp, _filt, interlace = struct.unpack(">IIBBBBB", body)
            if bitdepth != 8 or interlace != 0 or colortype not in (0, 2, 4, 6):
                raise UnsupportedPng(
                    f"未対応の PNG 形式 (bitdepth={bitdepth} colortype={colortype} interlace={interlace}): {path}",
                )
        elif ctype == b"IDAT":
            idat.append(body)
        elif ctype == b"IEND":
            break
        pos += 12 + length
    raw = zlib.decompress(b"".join(idat))
    nch = {0: 1, 2: 3, 4: 2, 6: 4}[colortype]
    rows = _unfilter(raw, width, height, nch)
    if nch != 4:
        rows = [_to_rgba(r, width, nch) for r in rows]
    return width, height, rows


def _unfilter(raw, width, height, nch):
    """PNG のスキャンライン・フィルタを解く。Paeth/Average だけは画素ループが避けられない。"""
    stride = width * nch
    rows = []
    prev = bytearray(stride)
    off = 0
    for _y in range(height):
        ft = raw[off]
        off += 1
        cur = bytearray(raw[off:off + stride])
        off += stride
        if ft == 0:
            pass
        elif ft == 1:  # Sub
            for i in range(nch, stride):
                cur[i] = (cur[i] + cur[i - nch]) & 0xFF
        elif ft == 2:  # Up — 上行との加算のみ＝バイト列演算で済ませられる最頻ケース
            for i in range(stride):
                cur[i] = (cur[i] + prev[i]) & 0xFF
        elif ft == 3:  # Average
            for i in range(stride):
                left = cur[i - nch] if i >= nch else 0
                cur[i] = (cur[i] + ((left + prev[i]) >> 1)) & 0xFF
        elif ft == 4:  # Paeth
            for i in range(stride):
                a = cur[i - nch] if i >= nch else 0
                b = prev[i]
                c = prev[i - nch] if i >= nch else 0
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                cur[i] = (cur[i] + pr) & 0xFF
        else:
            raise UnsupportedPng(f"未知のフィルタ型 {ft}")
        prev = cur
        rows.append(cur)
    return rows


def _to_rgba(row, width, nch):
    out = bytearray(width * 4)
    for x in range(width):
        if nch == 1:
            g = row[x]
            out[4 * x:4 * x + 4] = bytes((g, g, g, 255))
        elif nch == 2:
            g = row[2 * x]
            out[4 * x:4 * x + 4] = bytes((g, g, g, row[2 * x + 1]))
        else:
            out[4 * x:4 * x + 3] = row[3 * x:3 * x + 3]
            out[4 * x + 3] = 255
    return out


# ---- 画素の要約（すべて C 実装のバイト列演算で組む） -------------------------

def channels(row):
    """1行の RGBA から (R, G, B) の bytes を切り出す（スライスのステップ＝C 実装で高速）。"""
    return bytes(row[0::4]), bytes(row[1::4]), bytes(row[2::4])


def background_color(width, height, rows, step=4):
    """最頻色を背景とみなす。step 間引き＝走査コストを 1/16 にしても最頻色は動かない（実測）。"""
    counts = {}
    for y in range(0, height, step):
        row = rows[y]
        for x in range(0, width * 4, 4 * step):
            key = bytes(row[x:x + 3])
            counts[key] = counts.get(key, 0) + 1
    return max(counts.items(), key=lambda kv: kv[1])[0]


def row_background(row, width):
    """1行の最頻色。走査(b) が「下端行の地」を基準色に取るために使う。

    なぜ画面全体の最頻色（[background_color]）ではだめか: **浮遊窓（ダイアログ）を撮った golden では
    最頻色が絵ごとに裏返る**。`onNode(isDialog()).captureRoboImage` は scrim ごと 720x1280 を撮るが、
    ダイアログは fontScale 2.0 で画面幅いっぱい（maxWidth 560dp > 端末 360dp）まで育つため、
    1.0 では最頻色＝scrim なのに 2.0 ではダイアログ面が最頻になり、**scrim の側がインク扱い**へ入れ替わる。
    走査(b) は 1.0→2.0 の下端インク量の差で判定するので、参照色が入れ替わると量が比較不能になる。
    実測（2026-08-07・golden 166枚）:
      ・reimport_scan の 2.0 は下端が全て scrim（ダイアログは y45..1233 に収まる）なのに
        「最終行インク 720px＝全幅が切れている」と読めた＝偽陽性。
      ・battery / import_prompt は 1.0 の時点で最頻色がダイアログ面のため 1.0/2.0 とも 720px で並び、
        「1.0 から接触＝守備範囲外」の参考行へ落ちていた＝**本当に下端で切れても差が出ない盲点**。
    どちらも「画面全体の最頻色 ≠ 下端行の地」であることが根。この検査が問うているのは
    「下端行に、下端行の地でない画素が居るか」なので、地は下端行そのものから、かつ**版面が健全な
    1.0 側から**取る（2.0 側から取ると、切れた内容が自分自身を地と名乗って破綻を取り逃す）。
    """
    counts = {}
    for x in range(0, width * 4, 4):
        key = bytes(row[x:x + 3])
        counts[key] = counts.get(key, 0) + 1
    return max(counts.items(), key=lambda kv: kv[1])[0]


def _diff_table(base, threshold):
    """|v - base| > threshold を 1、それ以外を 0 に写す 256 要素の変換表。"""
    return bytes(1 if abs(v - base) > threshold else 0 for v in range(256))


def ink_mask_row(row, width, bg, threshold):
    """1行のインクマスク（背景色から threshold より離れた画素を 1 とする 0/1 の bytes）。

    3チャネルの OR を int の bitwise OR で一括処理する。各バイトが 0/1 なので桁上がりが起きず、
    バイト単位の論理和とちょうど一致する（走査コストの支配項をここで潰す）。
    """
    r, g, b = channels(row)
    mr = r.translate(_diff_table(bg[0], threshold))
    mg = g.translate(_diff_table(bg[1], threshold))
    mb = b.translate(_diff_table(bg[2], threshold))
    merged = int.from_bytes(mr, "big") | int.from_bytes(mg, "big") | int.from_bytes(mb, "big")
    return merged.to_bytes(width, "big")


def ink_mask(width, height, rows, bg, threshold):
    """全行のインクマスク。変換表は 3 つだけ作って使い回す（行ごとに作ると表生成が支配項になる）。"""
    tr = _diff_table(bg[0], threshold)
    tg = _diff_table(bg[1], threshold)
    tb = _diff_table(bg[2], threshold)
    out = []
    for y in range(height):
        row = rows[y]
        r, g, b = channels(row)
        merged = (
            int.from_bytes(r.translate(tr), "big")
            | int.from_bytes(g.translate(tg), "big")
            | int.from_bytes(b.translate(tb), "big")
        )
        out.append(merged.to_bytes(width, "big"))
    return out


ONE = b"\x01"
ZERO = b"\x00"


def runs(mask_row):
    """マスク行から連続インク区間 [(x_start, x_end_inclusive), ...] を取る（find は C 実装）。"""
    out = []
    pos = 0
    while True:
        start = mask_row.find(ONE, pos)
        if start < 0:
            return out
        end = mask_row.find(ZERO, start)
        if end < 0:
            out.append((start, len(mask_row) - 1))
            return out
        out.append((start, end - 1))
        pos = end


def ink_count(mask_row):
    """行のインク画素数（count は C 実装）。"""
    return mask_row.count(ONE)


def components(width, height, mask, gap=3, min_pixels=24, v_gap=None):
    """インクの連結成分を [(x1, y1, x2, y2, 画素数), ...] で返す（x1,y1 と x2,y2 は含む）。

    run（行内の連続インク区間）を単位に union-find する。画素単位の BFS だと 92万画素 x 104枚で
    分オーダーになるが、run は 1枚あたり数千個しかないため実質ゼロコストで済む。

    [gap] は横方向の橋渡し幅、[v_gap] は縦方向（省略時は gap と同値）。**縦横を分けられることが
    走査(c) には本質的**: 「1文字ずつ縦積みになった列」を数えるには、字間（横 2〜3px）はつないで
    語をまとめつつ、縦に詰まった文字同士は分離したままにする必要がある。両者を同じ gap にすると
    縦積みが 1 つの塊へ融合して「段に割れた」ことが消える（K の空棚 CTA の P/D/F で実測）。
    """
    if v_gap is None:
        v_gap = gap
    parent = {}

    def find(a):
        while parent[a] != a:
            parent[a] = parent[parent[a]]
            a = parent[a]
        return a

    def union(a, b):
        ra, rb = find(a), find(b)
        if ra != rb:
            parent[rb] = ra

    boxes = {}
    recent = []  # 直近 gap+1 行ぶんの [(run_id, x1, x2)]（縦方向の橋渡し用）
    for y in range(height):
        row_runs = runs(mask[y])
        current = []
        prev_id = prev_x2 = None
        for (x1, x2) in row_runs:
            rid = len(parent)
            parent[rid] = rid
            boxes[rid] = [x1, y, x2, y, x2 - x1 + 1]
            # 同一行内: 隙間が gap 以内なら同じ塊（字間で切れた文字をつなぐ）。
            if prev_id is not None and x1 - prev_x2 - 1 <= gap:
                union(prev_id, rid)
            prev_id, prev_x2 = rid, x2
            current.append((rid, x1, x2))
        for (rid, x1, x2) in current:
            for rowlist in recent:
                for (pid, px1, px2) in rowlist:
                    # 縦方向: x 範囲が重なれば同じ塊（左右の余裕は取らない＝斜めに離れた別要素を
                    # つながないため。横のつながりは同一行内の gap 判定が既に担っている）。
                    if x1 <= px2 and px1 <= x2:
                        union(pid, rid)
        recent.append(current)
        if len(recent) > v_gap + 1:
            # 直近 v_gap+1 行だけ保持＝「縦に v_gap px 空いていてもつながる」を満たす最小の窓。
            recent.pop(0)
    merged = {}
    for rid, (x1, y1, x2, y2, npx) in boxes.items():
        root = find(rid)
        cur = merged.get(root)
        if cur is None:
            merged[root] = [x1, y1, x2, y2, npx]
        else:
            cur[0] = min(cur[0], x1)
            cur[1] = min(cur[1], y1)
            cur[2] = max(cur[2], x2)
            cur[3] = max(cur[3], y2)
            cur[4] += npx
    return [tuple(v) for v in merged.values() if v[4] >= min_pixels]


# ---- golden ファイル名の分解 -------------------------------------------------

def scale_pairs(png_dir):
    """`<接頭辞>_<theme>_<scale>.png` を (接頭辞+theme) で束ね、1.0/2.0 が揃った組だけ返す。

    fontScale 破綻は「等倍では成立していた版面が拡大で壊れる」ことなので、比較の基準として
    必ず同一 case の 1.0 が要る。scale を持たない golden（VerticalParagraph 等・テーマのみ）は
    この走査の対象外＝呼び出し側が skip 件数として明示すること（黙って0件にしない）。
    """
    pairs = {}
    for path in sorted(Path(png_dir).glob("*.png")):
        stem = path.stem
        parts = stem.rsplit("_", 1)
        if len(parts) != 2:
            continue
        head, scale = parts
        if scale not in ("1.0", "2.0"):
            continue
        pairs.setdefault(head, {})[scale] = path
    return {k: v for k, v in sorted(pairs.items()) if "1.0" in v and "2.0" in v}


def all_scaled_pngs(png_dir):
    """scale 付き golden の全件（ペアが揃わないものも含む）。"""
    return sorted(p for p in Path(png_dir).glob("*.png") if p.stem.endswith(("_1.0", "_2.0")))


# ---- スクロール面の除外（走査(a)(b) 共用） -----------------------------------
#
# なぜ除外表が要るか: 走査(a)(b) は「2.0 で一覧行が減った／下端で切れた」を破綻とみなすが、**画素だけでは
# 『押し出されて到達不能』と『スクロールすれば届く（畳の外へ流れただけ）』を区別できない**。Roborazzi は
# 最初のビューポート1枚しか撮らないので、スクロール可能面では 2.0 で可視行が減り下端に内容が接するのが
# 正常な姿になる（2026-08-06 実測＝設定K・本棚Kリスト・さがすKランキング）。
#
# なぜ「LazyColumn / verticalScroll が在れば自動除外」にしないか: **十分条件ではない**。目次K は
# `LazyColumn(Modifier.weight(1f))` を持ちながら、同じ Column の現在地バーが 2.0 で膨張して残り高 0 を
# 渡すため、スクロール器はあってもビューポートが 0＝1行も出せない（監査 G-1＝真の破綻）。器の有無は
# 機械で分かるが「その器に高さが渡っているか」は分からない。よって**1件ずつ人が実装を読んで判断した
# ものだけ**をここへ載せる（判断の根拠は reason に残す＝次に読む人が再判断できる）。
#
# 陳腐化への保険: 各エントリは根拠ファイルとその中に在るべき字句を宣言する。字句が消えた（スクロールを
# 外した・ファイルを消した/改名した）ら除外は無効になり、その case は赤へ戻る＝除外が黙って生き残らない。
#
# 字句は**タプルで複数宣言できる（全て在ることが条件）**。目次3スキンのように「器が在る」だけでは
# 足りず「その器に高さが渡っている」ことが別の修正に依っている場合、両方を宣言しないと片方が
# 失われても除外が生き残ってしまうため（目次K は器＝LazyColumn を持ちながら現在地バーに高さを
# 奪われて viewport 0 だった＝監査 G-1。その修正が外れたら除外も外れる必要がある）。
#
# 字句は**複数行にまたがってよい**（ファイル本文への部分一致。改行・字下げ込みで一致を要求する）。
# 同じ1行が同一ファイルに複数在って区別できないときの唯一の手段で、整形で崩れたら除外は失効する
# ＝赤へ戻る側に倒れるので安全側（下の検索画面が実例）。
#
# 検索画面の**本体**スクロールを指す字句。`.verticalScroll(rememberScrollState())` 単独だと、同じ
# ファイルの選択キーワード追従バー（`heightIn(max = 96.dp)` 付き・bottomBar 側）にも一致してしまい、
# **本体のスクロールを外しても除外が生き残る**。本体だけに在る連なりで固定する。
_SEARCH_BODY_SCROLL = ".padding(paddingValues)\n                .verticalScroll(rememberScrollState())"

SCROLLING_SURFACES = {
    "SettingsScreenK_default_light": ("android/app/src/main/java/com/novelreader/ui/skins/k/SettingsScreenK.kt", "verticalScroll"),
    "SettingsScreenK_default_dark": ("android/app/src/main/java/com/novelreader/ui/skins/k/SettingsScreenK.kt", "verticalScroll"),
    "SettingsScreenK_default_sepia": ("android/app/src/main/java/com/novelreader/ui/skins/k/SettingsScreenK.kt", "verticalScroll"),
    "SettingsScreenK_followsystem_light": ("android/app/src/main/java/com/novelreader/ui/skins/k/SettingsScreenK.kt", "verticalScroll"),
    "BookshelfK_list_mixed_light": ("android/app/src/main/java/com/novelreader/ui/skins/k/BookshelfK.kt", "LazyColumn"),
    "BookshelfD_list_mixed_light": ("android/app/src/main/java/com/novelreader/ui/BookshelfScreen.kt", "LazyColumn"),
    "DiscoveryHomeK_ranking_light": ("android/app/src/main/java/com/novelreader/ui/skins/k/DiscoveryHomeK.kt", "LazyColumn"),
    "ReadingSettingsSheetContent_light": ("android/app/src/main/java/com/novelreader/ui/ReadingSettingsSheet.kt", "verticalScroll"),
    "ReadingSettingsSheetContent_dark": ("android/app/src/main/java/com/novelreader/ui/ReadingSettingsSheet.kt", "verticalScroll"),
    "ReadingSettingsSheetContent_sepia": ("android/app/src/main/java/com/novelreader/ui/ReadingSettingsSheet.kt", "verticalScroll"),
    "NcodeLinkSheet_candidates_light": ("android/app/src/main/java/com/novelreader/ui/NcodeLinkSheet.kt", "verticalScroll(rememberScrollState())"),
    "NcodeLinkSheet_candidates_dark": ("android/app/src/main/java/com/novelreader/ui/NcodeLinkSheet.kt", "verticalScroll(rememberScrollState())"),
    "NcodeLinkSheet_candidates_sepia": ("android/app/src/main/java/com/novelreader/ui/NcodeLinkSheet.kt", "verticalScroll(rememberScrollState())"),
    "TocK_current_light": ("android/app/src/main/java/com/novelreader/ui/skins/k/TocK.kt", ("LazyColumn", "監査 2026-08-06 G-1")),
    "TocK_current_dark": ("android/app/src/main/java/com/novelreader/ui/skins/k/TocK.kt", ("LazyColumn", "監査 2026-08-06 G-1")),
    "TocK_current_sepia": ("android/app/src/main/java/com/novelreader/ui/skins/k/TocK.kt", ("LazyColumn", "監査 2026-08-06 G-1")),
    "TocK_ep4digits_light": ("android/app/src/main/java/com/novelreader/ui/skins/k/TocK.kt", ("LazyColumn", "監査 2026-08-06 G-1")),
    "TocSkyM_ep4digits_light": ("android/app/src/main/java/com/novelreader/ui/skins/m/TocSkyM.kt", ("LazyColumn", "監査 2026-08-06 G-1")),
    "TocPortalJ_ep4digits_light": ("android/app/src/main/java/com/novelreader/ui/skins/j/TocPortalJ.kt", ("LazyColumn", "監査 2026-08-06 G-1")),
    # 2026-08-07 の新設撮影条件（初めて 2.0 の絵が撮られた面）を1枚ずつ実装で判定した分。
    "DiscoverySearchScreen_drafted_light": ("android/app/src/main/java/com/novelreader/ui/discovery/DiscoverySearchScreen.kt", _SEARCH_BODY_SCROLL),
    "DiscoverySearchScreen_drafted_dark": ("android/app/src/main/java/com/novelreader/ui/discovery/DiscoverySearchScreen.kt", _SEARCH_BODY_SCROLL),
    "DiscoverySearchScreen_drafted_sepia": ("android/app/src/main/java/com/novelreader/ui/discovery/DiscoverySearchScreen.kt", _SEARCH_BODY_SCROLL),
    "NovelDetailScreen_content_light": ("android/app/src/main/java/com/novelreader/ui/discovery/NovelDetailScreen.kt", "verticalScroll(scrollState)"),
    "NovelDetailScreen_content_dark": ("android/app/src/main/java/com/novelreader/ui/discovery/NovelDetailScreen.kt", "verticalScroll(scrollState)"),
    "NovelDetailScreen_content_sepia": ("android/app/src/main/java/com/novelreader/ui/discovery/NovelDetailScreen.kt", "verticalScroll(scrollState)"),
    "BookshelfDialogK_notif_priming_light": ("android/app/src/main/java/com/novelreader/ui/theme/NovelReaderAlertDialog.kt", ("Modifier.verticalScroll(rememberScrollState())", "増補（2026-08-07）")),
    # 2026-08-21: 上と同一部品・同一機序（縦3段化でカードが伸び 2.0 で窓が画面全高に達した）。
    # 器の字句が消えたら失効する点も同じなので、宣言する根拠ファイル・字句は notif_priming と揃える。
    "BookshelfDialogK_reimport_scan_light": ("android/app/src/main/java/com/novelreader/ui/theme/NovelReaderAlertDialog.kt", ("Modifier.verticalScroll(rememberScrollState())", "増補（2026-08-07）")),
    # 2026-08-07: 条件帯が 2.0 で本文領域を使い切り一覧に残り高0だった破綻を是正した後の登録。
    # 帯を maxHeight/2 で閉じたので一覧に必ず半分の高さが渡る＝罫線が減るのは可視行が減っただけ。
    "DiscoveryResultScreen_list_light": ("android/app/src/main/java/com/novelreader/ui/discovery/DiscoveryResultScreen.kt", ("heightIn(max = maxHeight", "verticalScroll")),
    "DiscoveryResultScreen_list_dark": ("android/app/src/main/java/com/novelreader/ui/discovery/DiscoveryResultScreen.kt", ("heightIn(max = maxHeight", "verticalScroll")),
    "DiscoveryResultScreen_list_sepia": ("android/app/src/main/java/com/novelreader/ui/discovery/DiscoveryResultScreen.kt", ("heightIn(max = maxHeight", "verticalScroll")),
}

SCROLL_EXEMPT_REASON = {
    "SettingsScreenK_default_light": "本体 Column が画面全高の verticalScroll＝2.0 で伸びた分は下へ流れるだけで全項目に到達できる",
    "SettingsScreenK_default_dark": "同上（テーマ違いの同一構造）",
    "SettingsScreenK_default_sepia": "同上（テーマ違いの同一構造）",
    "SettingsScreenK_followsystem_light": "同上（テーマ行の値が最長になる状態違いの同一構造＝罫線帯の座標は default と 1.0/2.0 とも完全一致）。⚠️ この絵が初めて撮られた 2026-08-17 には行名「テーマ」が「…」へ潰れる実破綻を含んでおり、走査(a) の赤はそれを指していた。案B（値に widthIn(max) を与えて行名に幅を予約）で是正済みで、以後は SettingsRowWidthLayoutTest が守る＝この登録は是正後にのみ正しい",
    "BookshelfK_list_mixed_light": "一覧は LazyColumn＝行が高くなり可視行数が減っただけ。行自体は maxLines+省略記号で健全",
    "BookshelfD_list_mixed_light": "一覧は LazyColumn＝K の同型（行が高くなり可視行数が減っただけ）。⚠️ この登録が 2026-08-20 まで不要だったのは**走査が罫線を数え間違えていたから**で、D の絵が変わったからではない: D のチップは角丸2px の矩形枠なので、fontScale 2.0 でチップ行が全幅へ溢れると4個の上辺・下辺が y=158/224 に一直線に揃い、全幅罫線2本として数えられていた（一覧の区切り線は 1.0/2.0 とも 1本ずつしか変わっていない）。スクロール端フェード（2026-08-20 裁定①）が右端 56dp を地色へ溶かして cover を 0.80 未満へ落としたため、この見せかけの2本が消えて素の比較（1.0=2本 → 2.0=1本）が露出した＝赤の中身は最初から K と同じ正常な縮退",
    "DiscoveryHomeK_ranking_light": "一覧は LazyColumn＝同上。かつて併発していた順位の縦割れ（監査 G-5＝順位セルが width(34.dp) 固定で中身だけ fontScale で伸び、2.0 で「10」が「1」「0」に割れた）は 2026-08-07 に器を 34.sp.toDp() へ替えて是正済み（DiscoveryCommon.kt:227-232）＝走査(c) は現在この絵を赤にしない",
    "NcodeLinkSheet_candidates_light": "シート中身の Column が verticalScroll＝2.0 で押し出された手動 N コード欄・「紐付け」・「再試行」はスクロールで到達できる。⚠️ 2026-08-17 に器を得るまでは**本当に到達不能**で（監査 根因④の heightIn(min=120.dp) は「縮んで欠ける」側しか直していなかった）、走査(a) が赤にしていたのは実在の破綻だった＝この登録は是正後にのみ正しい",
    "NcodeLinkSheet_candidates_dark": "同上（テーマ違いの同一構造）",
    "NcodeLinkSheet_candidates_sepia": "同上（テーマ違いの同一構造）",
    "ReadingSettingsSheetContent_light": "シート内容の Column が verticalScroll＝2.0 で溢れた「行間」「本文余白」はスクロールで到達できる（監査 G-2 の修正で獲得。下端の切れ方はスクロール面の折返し地点そのもの）",
    "ReadingSettingsSheetContent_dark": "同上（テーマ違いの同一構造）",
    "ReadingSettingsSheetContent_sepia": "同上（テーマ違いの同一構造）",
    "TocK_current_light": "章一覧は LazyColumn で、現在地バーの進捗が1行に固定された（G-1 修正）ことで実高を得ている＝2.0 の可視行減・下端の行の切れはスクロール面の正常な姿（golden 2.0 で実際に4行出ている）",
    "TocK_current_dark": "同上（テーマ違いの同一構造）",
    "TocK_current_sepia": "同上（テーマ違いの同一構造）",
    "TocK_ep4digits_light": "同上（4桁話数の同一構造）。かつて併発していた「現在章行の題名が『ここから再開』チップに幅を奪われて1行1文字へ潰れる」破綻は 2026-08-07 にチップをアイコン化して是正済み（TocK.kt:381）＝走査(c) は現在この絵を赤にしない",
    "TocSkyM_ep4digits_light": "章一覧は LazyColumn＝同上（M も G-1 修正で現在地バーが1行）。かつて併発していた話数ラベル `.ep` の 52dp 固定による「第/1024/話」3行割れは 2026-08-07 に桁数追従幅へ替えて是正済み（skins/TocEpLabelWidth.kt＝52dp は固定幅ではなく下限になった）。⚠️ この割れは**どの走査も捕まえていなかった**（走査(c) は行内に隣字が居ると落とすため）＝再発しても画素側の網では止まらない。守りは test/.../TocResumeAffordanceLayoutTest.kt のレイアウト値断言",
    "TocPortalJ_ep4digits_light": "章一覧は LazyColumn＝同上（J も G-1 修正で現在地バーが1行）。`.ep` 52dp 固定の3行割れも M と同じ経緯で 2026-08-07 に是正済み（守りが走査でなく TocResumeAffordanceLayoutTest である点も同じ）",
    "DiscoverySearchScreen_drafted_light": "本体が Scaffold 本文まるごとの verticalScroll＝2.0 で下へ流れた条件セクションはスクロールで到達できる。bottomBar の選択キーワード追従バーは内側 FlowRow が heightIn(max=96.dp) で閉じているため本体のビューポートを食い潰さない（＝器に高さが渡っていることまで確認済み）。1.0 の罫線4本→2.0 の3本は可視セクションが減っただけ",
    "DiscoverySearchScreen_drafted_dark": "同上（テーマ違いの同一構造）",
    "DiscoverySearchScreen_drafted_sepia": "同上（テーマ違いの同一構造）",
    "NovelDetailScreen_content_light": "詳細本文は Scaffold 本文直下の Column が fillMaxSize+verticalScroll＝ヒーロー/メタ/あらすじ/状態グリッドは下へ流れるだけで到達できる（同じ Box の兄弟が居ない＝高さを奪う相手が構造上いない）。1.0 の区切り7本→2.0 の5本は可視セクションが減っただけ",
    "NovelDetailScreen_content_dark": "同上（テーマ違いの同一構造）",
    "NovelDetailScreen_content_sepia": "同上（テーマ違いの同一構造）",
    "BookshelfDialogK_notif_priming_light": "本文スロットは NovelReaderAlertDialog が verticalScroll で包む（2026-08-07 増補）。2.0 では窓が画面全高まで育ち、最終行のインクは**ダイアログ面そのものの下端**＝角丸の弧が上端 y0・下端 y1279 とも 1.0 の弧と同一形（run 47..672 から始まる）で完全に描かれており、切断ではない。題字と2ボタンは可視で、本文の溢れはスクロールで到達できる",
    "BookshelfDialogK_reimport_scan_light": "notif_priming と同一部品（NovelReaderAlertDialog）・同一機序の 2026-08-21 版＝3操作を縦3段へ揃えた（正本モック bookshelf-reimport-badge-D の分岐②）ぶんカードが伸び、2.0 で窓が画面全高まで育った。画素で確認: ダイアログ面は y0..y1279 に**完全な角丸矩形として収まっており**、下端の幅が 660→656→654→650→642→636→630→610 と閉じる弧を描く＝1.0 の閉じ際 y1070..y1077 と同一形で、キャンバスによる切断は起きていない。最終行の 626px が「インク」に数えられるのは、走査が両スケールを**1.0 の下端＝scrim 灰**を地として測るため、2.0 で scrim が消えてダイアログ面（#FBFAF8）だけが残ると差が出るから＝[bottom_row_ink] の docstring が予告している scrim/面の入れ替わりそのもので、絵の破綻ではない。実際に切れているのは本文2段落目（ファイル名ヒント）の3行目「義妹になっていました.pdf」だけで、これは verticalScroll の折返し地点＝スクロールで到達できる。題字・本文1段落目（「フォルダを教えて…しおりは残ります。」全4行）・3操作は全可視。⚠️ **見直しの合図**: この除外は「切れるのは本文だけ・3操作は全可視」という前提の上に立つ。前提の後半は ReimportScanDialogStackTest が fontScale 2.0 で assertIsDisplayed により機械で守る（走査は最終行のインクしか見ないので、操作が画面外へ押し出されても絵の signature は変わらない＝画素側の網では止まらない）。文言を伸ばす・段を増やす改修をしたら、その断言が落ちていないかを必ず見ること",
    "DiscoveryResultScreen_list_light": "条件帯（FlowRow）が 2.0 で8段に折り返し一覧の weight(1f) へ残り高0を渡していた破綻を 2026-08-07 に是正済み——帯を BoxWithConstraints で maxHeight/2 に閉じ、溢れた段は帯の内部スクロールで到達できる。是正後の実測でインクは下段が最大（27,296＝一覧が実際に描かれている）。罫線が 1.0 の2本→2.0 の0本になるのは、行が高くなって罫線の入る位置まで到達しないため＝可視行が減っただけ",
    "DiscoveryResultScreen_list_dark": "同上（テーマ違いの同一構造）",
    "DiscoveryResultScreen_list_sepia": "同上（テーマ違いの同一構造）",
}


def scroll_exemption(case, repo_root=None):
    """[case] がスクロール面として除外済みなら (状態, 説明) を返す。未登録なら None。

    状態は "ok"（除外を適用してよい）か "stale"（根拠字句が消えた＝除外を適用しない）。
    """
    entry = SCROLLING_SURFACES.get(case)
    if entry is None:
        return None
    rel, tokens = entry
    # 単一字句は文字列で書ける（既存エントリの形）。複数条件はタプル＝全て在ることを要求する。
    if isinstance(tokens, str):
        tokens = (tokens,)
    root = Path(repo_root) if repo_root else Path(__file__).resolve().parent.parent
    src = root / rel
    if not src.is_file():
        return ("stale", f"根拠ファイルが無い（{rel}）＝除外を取り消して赤にする")
    text = src.read_text(encoding="utf-8")
    # 表示用に改行・字下げを1個の空白へ潰す（複数行字句でも走査の出力を1件1行に保つ。照合は元の字句のまま）。
    def _flat(token):
        return " ".join(token.split())

    for token in tokens:
        if token not in text:
            return (
                "stale",
                f"根拠の字句 `{_flat(token)}` が {rel} から消えた＝スクロール（またはその器へ高さを渡す修正）を"
                "失った可能性。除外を取り消して赤にする",
            )
    shown = "` `".join(_flat(t) for t in tokens)
    return ("ok", f"{SCROLL_EXEMPT_REASON.get(case, '')}（根拠: {rel} の `{shown}`）")


def resolve_dir(argv, default_rel="android/app/src/test/screenshots"):
    """引数の PNG ディレクトリを解決する（省略時はリポジトリ既定の golden 置き場）。"""
    if len(argv) > 1:
        target = Path(argv[1])
    else:
        target = Path(__file__).resolve().parent.parent / default_rel
    if not target.is_dir():
        print(f"PNG ディレクトリが見つからない: {target}", file=sys.stderr)
        raise SystemExit(2)
    return target
