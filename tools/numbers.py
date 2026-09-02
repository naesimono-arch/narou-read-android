#!/usr/bin/env python3
"""「値は人が書かない」を md へ効かせる（宣言＝人／値＝機械／照合＝毎走）。

真の失敗様式は「値がずれた」ではなく **主張と出所を結ぶリンクが無いこと**——
2026-09-02 に CLAUDE.md の費用構成比が順位ごと誤っていた件は、値が古かったのではなく
「その値がどこから来たか」を機械が知らなかったために誰も気づけなかった、が正確な機序。
∴ 人が書くのは「この主張はここを指す」という宣言だけにする。

3 ファイル構成:
  1. tools/numbers_manifest.tsv  人が編集。key / 出所 / セレクタ / 桁。**値の欄は無い**。
  2. tools/numbers.json          `--extract` だけが書く生成物。人は編集しない。
                                 値・出所・出所の sha256（または母集団の指紋）・抽出時刻を持つ。
  3. md 側の引用タグ             数値の直後に `〔#key〕`。例: `**37.9%**〔#ctx.toolinput_pct〕`

  ⚠️ numbers.json は docs/reference/ ではなく tools/ に置く（本便の所有権が tools/ 配下に限られたため）。
     置き場を動かすなら manifest とセットで動かすこと。

使い方:
  python3 tools/numbers.py --extract          # 1→2 を作る（出所を実際に叩く）
  python3 tools/numbers.py --verify           # 3 と 2 を突合（オフライン・CI 安全）
  python3 tools/numbers.py --verify --live    # 加えて出所を再測し、乖離を報告する
  python3 tools/numbers.py --list
  python3 tools/numbers.py --selftest         # マスク規則とタグ解釈の自己テスト

判定の設計（なぜこうしたか）:
  * kind=measure_json の出所はセッション JSONL＝リポジトリ外で CI では再現できない。
    ∴ md ↔ numbers.json の一致を**本判定**（オフラインで必ず走る）にし、再測は --live の任意扱い。
    再測の乖離は 2.5pt までを info とする——独立実装との突合が全区分 2.4pt 以内で一致した実測
    （docs/knowledge/context-cost-breakdown-2026-08-10.md）が「同じ主張と見なせる幅」の唯一の根拠。
    これを超えたら順位が入れ替わりうるので high。**閾値は実装から決めず、この記録から引いている。**
  * kind=kotlin_* / doc_number の出所はリポジトリ内＝毎走再導出できる。∴ 乖離は即 FAIL。
  * 出所の sha256 を numbers.json に刻み、--verify で読み直す。刻んだ時点で固定されると
    「出所を作り直しても全一致を返す」形で静かに破れる（nuru の実例）。

守れない範囲（隠すと「検証済み」の誤った安心を生むので列挙する）:
  * タグの無い数値の掃討は info 止まり。md 中の全数値を必須タグ化はしない
    （旧記載の引用・単位付きの一般的な数字が偽陽性になり、鳴りすぎた番人は読まれなくなる）。
  * measure_json の値は「抽出時の母集団」に依存する。母集団が増えれば正しく動く＝
    numbers.json の値が古いことは検知できるが、抽出器の**仕様の誤り**は検知できない。
  * doc_number は出所 md の数値をそのまま信じる。その md 自身の正しさは射程外。
  * CLAUDE.md の「7〜10%」のような**幅の主張**はタグ対象外（manifest の note に理由を宣言）。
"""
import argparse
import datetime
import hashlib
import json
import os
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "tools/numbers_manifest.tsv"
SNAPSHOT = ROOT / "tools/numbers.json"

# 再測の乖離をどこから high にするか（出所＝上の docstring）。
LIVE_DRIFT_TOLERANCE_PT = 2.5

TAG_RE = re.compile(r"〔#([A-Za-z0-9_.@\-]+)〕")
NUM_RE = re.compile(r"[0-9][0-9,]*(?:\.[0-9]+)?")


# ── マスク（inert 領域）: fail-closed（曖昧なら鳴る側へ倒す）──────────────────
def mask_inert(text):
    """コードスパン／フェンスの中身を空白へ潰す。長さと列位置は 1 文字も動かさない。

    なぜマスクが要るか: 壊れた書式や旧記載の実例を md に**引用**した瞬間に検査器が赤くなり、
    引用を消さない限り永久に赤い＝番人が読まれなくなる。
    なぜ fail-closed か: 除外機構そのものが「静かに全部を黙らせる」形で腐るのが最悪の腐り方。
      F1 コードスパンの開き記号に同じ長さの閉じ記号が同じ行に無ければマスクしない
      F2 コードスパンは行をまたがない（前行の閉じ忘れが次行を黙らせない）
      F3 フェンス行が奇数（閉じ忘れ）ならフェンスを1つもマスクしない
    """
    lines = text.split("\n")
    fence_idx = [i for i, ln in enumerate(lines) if ln.lstrip().startswith("```")]
    out = list(lines)

    # F3: 閉じ忘れならフェンスを一切マスクしない
    if len(fence_idx) % 2 == 0:
        for a, b in zip(fence_idx[0::2], fence_idx[1::2]):
            for i in range(a + 1, b):
                out[i] = " " * len(out[i])
        fenced = {i for a, b in zip(fence_idx[0::2], fence_idx[1::2])
                  for i in range(a, b + 1)}
    else:
        fenced = set()

    # F1/F2: コードスパンは行内で閉じているものだけ
    for i, ln in enumerate(out):
        if i in fenced:
            continue
        out[i] = _mask_spans_in_line(ln)
    return "\n".join(out)


def _mask_spans_in_line(line):
    runs = [(m.start(), len(m.group(0))) for m in re.finditer(r"`+", line)]
    chars = list(line)
    used = set()
    for a in range(len(runs)):
        if a in used:
            continue
        pos_a, len_a = runs[a]
        for b in range(a + 1, len(runs)):
            if b in used:
                continue
            pos_b, len_b = runs[b]
            if len_b != len_a:
                continue
            for j in range(pos_a + len_a, pos_b):
                chars[j] = " "
            used.add(a)
            used.add(b)
            break
    return "".join(chars)


# ── マニフェスト ──────────────────────────────────────────────────────────
def load_manifest():
    rows = []
    for raw in MANIFEST.read_text(encoding="utf-8").split("\n"):
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue
        parts = raw.split("\t")
        if parts[0] == "key":
            continue
        if len(parts) < 6:
            raise SystemExit(f"manifest の列が足りない: {raw!r}")
        rows.append({
            "key": parts[0], "kind": parts[1], "source": parts[2],
            "selector": parts[3], "fmt": parts[4], "tag_in": parts[5],
            "note": parts[6] if len(parts) > 6 else "",
        })
    return rows


def render(value, fmt):
    if fmt == "1dp":
        return f"{float(value):.1f}"
    if fmt == "int":
        return str(int(round(float(value))))
    if fmt == "int_k":
        return str(int(round(float(value) / 1000.0)))
    raise SystemExit(f"未知の fmt: {fmt}")


def split_key(key):
    """`量@YYYY-MM-DD` を (量, 日付 or None) へ。

    なぜ「点」を並べる形にしたか: 同じ量の測定が 2 つ食い違ったとき、片方で上書きすると
    **消えた側を誰も検証できなくなる**。単一セッションの測定は代表性が不明で、どちらが正しいかを
    決める材料が無いことの方が普通なので、検査器は「どちらが真か」を判定せず
    **各点がそれぞれの出所どおりであること**だけを見る。
    """
    q, _, as_of = key.partition("@")
    return q, (as_of or None)


def sha256_of(rel):
    p = ROOT / rel
    if not p.exists():
        return None
    return hashlib.sha256(p.read_bytes()).hexdigest()


# ── 抽出器 ────────────────────────────────────────────────────────────────
_measure_cache = {}


def _run_measure(script):
    if script in _measure_cache:
        return _measure_cache[script]
    r = subprocess.run([sys.executable, str(ROOT / script), "--json"],
                       capture_output=True, text=True, cwd=str(ROOT), timeout=600)
    if r.returncode != 0:
        raise RuntimeError(f"{script} が異常終了: {r.stderr[-400:]}")
    _measure_cache[script] = json.loads(r.stdout)
    return _measure_cache[script]


def _dig(obj, dotted):
    """ドット経路。キー自体にドットを含む場合（`handover.md`）があるので最長一致で降りる。"""
    cur = obj
    rest = dotted
    while rest:
        if not isinstance(cur, dict):
            raise KeyError(dotted)
        cand = [k for k in cur if rest == k or rest.startswith(k + ".")]
        if not cand:
            raise KeyError(f"{dotted}（{rest} が見つからない）")
        k = max(cand, key=len)          # 最長一致＝`read.pct_of_total` を `read`+`pct_of_total` と誤読しない
        cur = cur[k]
        rest = rest[len(k) + 1:]
    return cur


def extract(row):
    """(値, 出所の説明, 出所指紋) を返す。失敗は例外（黙って None を返さない）。"""
    kind, src, sel = row["kind"], row["source"], row["selector"]
    if kind == "measure_json":
        data = _run_measure(src)
        val = _dig(data, sel)
        fp = {"turns": data.get("turns"), "sessions": data.get("sessions"),
              "total_eff": data.get("total_eff") or data.get("read_total_eff")
              or data.get("toolinput_total_eff")}
        return float(val), f"{src} --json :: {sel}", {"corpus": fp}

    text = (ROOT / src).read_text(encoding="utf-8")
    if kind == "doc_number":
        # ラベルの直後に現れる最初の数値。マスク後の本文で位置を決め、値は生テキストから読む。
        masked = mask_inert(text)
        hits = [m.start() for m in re.finditer(re.escape(sel), masked)]
        if len(hits) != 1:
            raise RuntimeError(f"ラベル {sel!r} が {len(hits)} 箇所（1 箇所であること）")
        m = NUM_RE.search(text, hits[0])
        if not m:
            raise RuntimeError(f"ラベル {sel!r} の直後に数値が無い")
        return float(m.group(0).replace(",", "")), f"{src} :: 「{sel}」直後", {"sha256": sha256_of(src)}

    if kind in ("kotlin_const", "kotlin_attr"):
        pat = (rf"const\s+val\s+{re.escape(sel)}\s*=\s*([0-9_]+(?:\.[0-9]+)?)"
               if kind == "kotlin_const"
               else rf"(?<![A-Za-z0-9_]){re.escape(sel)}\s*=\s*([0-9_]+(?:\.[0-9]+)?)")
        ms = re.findall(pat, text)
        if len(ms) != 1:
            raise RuntimeError(f"{src} で {sel} が {len(ms)} 箇所（1 箇所であること）")
        return float(ms[0].replace("_", "")), f"{src} :: {sel}", {"sha256": sha256_of(src)}

    raise SystemExit(f"未知の kind: {kind}")


# ── タグの収集 ────────────────────────────────────────────────────────────
def find_tags(md_rel):
    """live な md が主張しているタグを返す。[(key, 数値文字列, 行番号, 連結部)]

    「数値の直後に〔#key〕」が定義なので、タグ直前 60 字の**最後の数値**を主張値とみなす
    （`**37.9%**〔#k〕` のように単位や強調記号が挟まる）。連結部が長すぎるものは
    「主張から剥がれた」として呼び出し側が鳴らす。
    """
    text = (ROOT / md_rel).read_text(encoding="utf-8")
    masked = mask_inert(text)
    out = []
    for m in TAG_RE.finditer(masked):
        line_no = masked.count("\n", 0, m.start()) + 1
        # 窓は行をまたがない（F2 と同じ理由）。またぐと、数値を伴わないタグが前の行の数値を
        # 拾って「主張から剥がれている」を見逃す＝黙る側へ倒れる（selftest tag-orphan で実証）。
        line_start = text.rfind("\n", 0, m.start()) + 1
        window = text[max(line_start, m.start() - 60):m.start()]
        nums = list(NUM_RE.finditer(window))
        if not nums:
            out.append((m.group(1), None, line_no, ""))
            continue
        last = nums[-1]
        out.append((m.group(1), last.group(0).replace(",", ""), line_no,
                    window[last.end():]))
    return out


# ── コマンド ──────────────────────────────────────────────────────────────
def cmd_extract():
    rows = load_manifest()
    entries, errs = {}, []
    for r in rows:
        try:
            val, desc, fp = extract(r)
        except Exception as e:
            errs.append(f"  ✗ {r['key']}: {e}")
            continue
        quantity, as_of = split_key(r["key"])
        entries[r["key"]] = {
            "value": render(val, r["fmt"]), "raw": val, "kind": r["kind"],
            "quantity": quantity, "as_of": as_of,
            "source": r["source"], "selector": r["selector"], "origin": desc,
            "note": r["note"], **fp,
        }
    # 同じ量の複数点をまとめた索引。人が numbers.json を開いたとき「2 点あって食い違っている」
    # ことが一目で分かるようにする（片方を上書きしていない証跡でもある）。
    points = {}
    for k, v in entries.items():
        points.setdefault(v["quantity"], []).append(
            {"as_of": v["as_of"], "value": v["value"], "origin": v["origin"]})
    multi = {q: sorted(ps, key=lambda x: x["as_of"] or "")
             for q, ps in points.items() if len(ps) > 1}

    # 測定時の条件のうち**機械が観測できるぶん**を自動で刻む。
    # なぜ要るか: 台帳の占有率は「台帳自体の大きさ」に依存するので、測定中の台帳の字数が判らないと
    # 後から代表性を判断できない（2026-09-03 の再測は測定中に handover.md が縮んでいた）。
    # 観測できない条件（並列度・その便の性質）は manifest の note に人が宣言する。
    ledgers = {}
    for led in ("STATUS.md", "handover.md", "awaiting-human.md"):
        f = ROOT / led
        ledgers[led] = len(f.read_text(encoding="utf-8")) if f.exists() else None

    snap = {
        "_generated_by": "tools/numbers.py --extract",
        "_do_not_edit": "人はこのファイルを編集しない。値の主張は md 側の〔#key〕タグで行う。",
        "_generated_at": datetime.datetime.now().astimezone().isoformat(timespec="seconds"),
        "_manifest_sha256": sha256_of("tools/numbers_manifest.tsv"),
        "_n_keys": len(entries),
        "_context_at_extract": {
            "_why": "測定時の条件。台帳の占有率は台帳自体の大きさに依存するので、"
                    "後から代表性を判断するにはこの字数が要る。単一セッションの測定を恒久値として引かないこと。",
            "ledger_chars": ledgers,
        },
        "_points_by_quantity": multi,
        "numbers": entries,
    }
    SNAPSHOT.write_text(json.dumps(snap, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"=== --extract: {len(entries)} キーを {SNAPSHOT.relative_to(ROOT)} へ書いた ===")
    for k, v in entries.items():
        print(f"  {k:<28} {v['value']:>8}   ({v['origin']})")
    if errs:
        print(f"\n■ 抽出できなかった宣言 {len(errs)} 件")
        print("\n".join(errs))
        return 1
    return 0


def cmd_verify(live=False):
    rows = load_manifest()
    if not SNAPSHOT.exists():
        print("✗ tools/numbers.json が無い。先に --extract を走らせること。")
        return 1
    snap = json.loads(SNAPSHOT.read_text(encoding="utf-8"))
    nums = snap.get("numbers", {})
    high, info = [], []

    if snap.get("_manifest_sha256") != sha256_of("tools/numbers_manifest.tsv"):
        high.append("manifest が numbers.json の生成後に変わっている＝--extract をやり直すこと")

    by_key = {r["key"]: r for r in rows}
    for key in nums:
        if key not in by_key:
            high.append(f"[{key}] numbers.json に在るが manifest に宣言が無い（生成物が独り歩きしている）")

    # 1) 宣言 ↔ タグ ↔ スナップショット
    tags_by_file = {}
    for r in rows:
        key, tgt = r["key"], r["tag_in"]
        if key not in nums:
            high.append(f"[{key}] 宣言はあるが numbers.json に値が無い＝--extract が失敗している")
            continue
        want = nums[key]["value"]
        if tgt == "-":
            info.append(f"[{key}] タグ要求なし（宣言済みの守れない範囲）: {r['note'][:60]}")
            continue
        if tgt not in tags_by_file:
            tags_by_file[tgt] = find_tags(tgt)
        found = [t for t in tags_by_file[tgt] if t[0] == key]
        if len(found) != 1:
            high.append(f"[{key}] {tgt} の〔#{key}〕タグが {len(found)} 箇所（1 箇所であること）＝リンク不在")
            continue
        _, got, line_no, joint = found[0]
        if got is None:
            high.append(f"[{key}] {tgt}:{line_no} タグの直前に数値が無い＝主張から剥がれている")
        elif len(joint) > 8:
            high.append(f"[{key}] {tgt}:{line_no} タグと数値の間が {len(joint)} 字＝直後ではない: {joint!r}")
        elif got != want:
            high.append(f"[{key}] {tgt}:{line_no} md={got} ↔ 出所={want}（{nums[key]['origin']}）")

    # 2) 出所の sha を読み直す（刻んだ時点で固定されると静かに破れる）
    for key, ent in nums.items():
        if "sha256" not in ent:
            continue
        now = sha256_of(ent["source"])
        if now is None:
            high.append(f"[{key}] 出所 {ent['source']} が消えている")
        elif now != ent["sha256"]:
            r = by_key.get(key)
            try:
                val, _desc, _fp = extract(r)
            except Exception as e:
                high.append(f"[{key}] 出所が変わり、再導出にも失敗: {e}")
                continue
            cur = render(val, r["fmt"])
            if cur != ent["value"]:
                high.append(f"[{key}] 出所が変わり値も動いた: {ent['value']} → {cur}（--extract して md も直す）")
            else:
                info.append(f"[{key}] 出所ファイルは変わったが値は同じ（{ent['source']}）")

    # 3) --live: リポジトリ外の出所を再測する
    if live:
        for key, ent in nums.items():
            if ent["kind"] != "measure_json":
                continue
            r = by_key.get(key)
            try:
                val, _d, _f = extract(r)
            except Exception as e:
                high.append(f"[{key}] 再測に失敗: {e}")
                continue
            drift = abs(float(val) - float(ent["raw"]))
            msg = (f"[{key}] 再測 {render(val, r['fmt'])} ↔ スナップショット {ent['value']}"
                   f"（差 {drift:.1f}pt）")
            # `@日付` 付き＝その日の測定として凍結した点。後日ずれるのは当然で、追随したら
            # 歴史的な点でなくなる ∴ 乖離は info 止まり。日付なし＝「現在値」の主張なので high。
            if ent.get("as_of"):
                info.append(msg + f"＝{ent['as_of']} 時点の凍結点なので乖離は正常（追随させない）")
            elif drift > LIVE_DRIFT_TOLERANCE_PT:
                high.append(msg + "＝許容 2.5pt 超。--extract して md を更新すること")
            else:
                info.append(msg)

    # 4) タグの無い同値の再掲（info 止まり＝鳴りすぎない）
    for tgt, tags in tags_by_file.items():
        text = mask_inert((ROOT / tgt).read_text(encoding="utf-8"))
        tagged_lines = {t[2] for t in tags}
        for key in {t[0] for t in tags}:
            v = nums.get(key, {}).get("value")
            if not v or len(v) < 3:
                continue                        # 1〜2 桁は一般語と衝突するので掃討しない
            for m in re.finditer(re.escape(v) + r"\s*%", text):
                ln = text.count("\n", 0, m.start()) + 1
                if ln not in tagged_lines:
                    info.append(f"[{key}] {tgt}:{ln} 同じ値がタグ無しで再掲されている（写しの二重化）")

    print(f"=== numbers --verify（{len(nums)} キー / 高 {len(high)} 件 / 情報 {len(info)} 件）"
          f"{' --live' if live else ''} ===")
    print(f"■ 要対応（{len(high)}）")
    for x in high:
        print("  ✗ " + x)
    if not high:
        print("  （なし）")
    print(f"■ 情報（{len(info)}）")
    for x in info:
        print("  - " + x)
    if not info:
        print("  （なし）")
    return 1 if high else 0


def cmd_list():
    rows = load_manifest()
    print(f"=== 数値マニフェスト（{len(rows)} キー）===")
    for r in rows:
        print(f"  {r['key']:<28} {r['kind']:<13} {r['tag_in']:<20} {r['source']}::{r['selector']}")
    return 0


# ── selftest（期待値は fixture から手で導き、実装を走らせて出た値を貼らない）────
def cmd_selftest():
    fails = []

    def eq(name, got, want):
        if got != want:
            fails.append(f"{name}: got={got!r} want={want!r}")

    # F1: 閉じたコードスパンは中身が潰れ、長さは保存される
    s = "a `x1` b"
    eq("F1-masked", mask_inert(s), "a `  ` b")
    eq("F1-len", len(mask_inert(s)), len(s))
    # F1 の fail-closed: 閉じ記号が無ければマスクしない（黙らせない）
    eq("F1-open", mask_inert("a `x1 b"), "a `x1 b")
    # F1: バッククォートの長さが違えば対にしない
    eq("F1-lenmismatch", mask_inert("a `x1`` b"), "a `x1`` b")
    # F2: 行をまたがない（前行の閉じ忘れが次行を黙らせない）
    eq("F2", mask_inert("a `x1\nb `y2` c"), "a `x1\nb `  ` c")
    # F3: フェンス行が奇数ならフェンスを1つもマスクしない
    eq("F3-odd", mask_inert("```\n99\n"), "```\n99\n")
    eq("F3-even", mask_inert("```\n99\n```"), "```\n  \n```")

    # _dig の最長一致（`handover.md` のようにキー自体がドットを含む）
    d = {"read": {"pct_of_total": 1.5}, "by_basename_pct_of_total": {"handover.md": 2.5}}
    eq("dig-nested", _dig(d, "read.pct_of_total"), 1.5)
    eq("dig-dotted-key", _dig(d, "by_basename_pct_of_total.handover.md"), 2.5)

    # render
    eq("fmt-1dp", render(37.85580148547056, "1dp"), "37.9")
    eq("fmt-int", render(350.0, "int"), "350")
    eq("fmt-int_k", render(35000.0, "int_k"), "35")

    # タグ解釈: 強調記号や単位を挟んでも「直前の数値」を拾う／マスク領域のタグは主張でない
    tmp = ROOT / "tools/.numbers_selftest.md"
    tmp.write_text("x **12.3%**〔#a.b〕 y\n`〔#c.d〕` z\n〔#e.f〕 only\n", encoding="utf-8")
    try:
        got = find_tags("tools/.numbers_selftest.md")
        eq("tag-count", len(got), 2)                      # コードスパン内の 1 本は主張でない
        eq("tag-key", got[0][0], "a.b")
        eq("tag-value", got[0][1], "12.3")
        eq("tag-joint", got[0][2], 1)
        eq("tag-orphan", got[1][1], None)                 # 数値の無いタグは剥がれとして報告できる形
    finally:
        tmp.unlink(missing_ok=True)

    print(f"=== numbers --selftest: {'FAIL' if fails else 'PASS'}（{len(fails)} 件の不一致）===")
    for f in fails:
        print("  ✗ " + f)
    return 1 if fails else 0


def main():
    ap = argparse.ArgumentParser(add_help=True)
    ap.add_argument("--extract", action="store_true")
    ap.add_argument("--verify", action="store_true")
    ap.add_argument("--live", action="store_true")
    ap.add_argument("--list", action="store_true")
    ap.add_argument("--selftest", action="store_true")
    a = ap.parse_args()
    if a.extract:
        return cmd_extract()
    if a.verify:
        return cmd_verify(live=a.live)
    if a.list:
        return cmd_list()
    if a.selftest:
        return cmd_selftest()
    ap.print_help()
    return 0


if __name__ == "__main__":
    sys.exit(main())
