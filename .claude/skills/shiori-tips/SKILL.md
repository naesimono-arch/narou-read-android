---
name: shiori-tips
description: 栞書影の先端ワンポイント意匠（SHIORI_TIPS）を安全に増補する手順。生成→機械検証→横断dedup→正本HTML/Kotlin/書影モック/ゴールデンの同期まで。「栞の意匠を増やしたい」「SHIORI_TIPSに足したい」「栞の先端を量産」等の依頼で使う。
---

# 栞 先端ワンポイント意匠の増補

栞書影（本棚グリッドの表紙）は「紙地＋天から下りる色の細棒＋その先端の小意匠」。先端は title から
決定論的に `tipIndex = floor(rng(title+"|B") * tipCount)` で1つ選ばれる。この意匠を増補する手順。

## 正本は4箇所・すべて「同一系列（同じ順序・同じ総数）」に保つ

| 役割 | 場所 |
|---|---|
| 意匠の正本（tipカタログ・nm/rd付き） | `docs/design-candidates/shiori-tips-D.html` の `const TIPS=[…]` |
| 実機の描画 | `android/.../ui/components/ShioriTips.kt` の `SHIORI_TIPS: List<ShioriTip>` |
| 書影モック（表紙デモ描画） | `docs/design-candidates/bookshelf-shiori-grid-D.html` の `const TIPS=[…]`（bare arrow） |
| 決定論のゴールデン | `android/.../ui/components/ShioriGeneratorTest.kt`（`tipCount` と tipIndex 期待値） |

**配列の並び（位置）＝ tipIndex**。4箇所すべてで**同じ順序**に追記する（順序が違うと「同じ本が別の絵」になる）。

## "append するだけ" では済まない3結合点（＝手順化の理由）

1. **意匠は二重に書く**: HTML(JS canvas)と Kotlin(Compose DrawScope)の両方に同じ絵が要る。1:1機械変換でないので目視照合が要る。
2. **tipCount が変わると tipIndex が全 title で変わる**: `ShioriGeneratorTest` の `tipCount` と tipIndex 期待値(3件)を再生成しないとテストが赤。ただし **hue/xFrac/lenFrac は総数非依存**（棒の乱数系列で先に引かれる）＝変わるのは tipIndex だけ＝極小diff。
3. **硬制約**: 色は `a`(accent)/`paper`(pp) のみ（色リテラル禁止＝タイトル駆動の色相系を壊す）・canvasプリミティブのみ（Compose移植可）・原点(x,y)から下方向 `x∈[x-8,x+8], y∈[y,y+24]` の包絡・和トーン簡素（意匠は ADR 0005/0014 の管轄）。

## パイプライン

### A. 生成（サブエージェント／安モデル狙い）
テーマ別に spec を渡し JS entries（`{f,nm,rd,draw}` の配列）を生成させる。spec に必須で書く: 上の硬制約・原点と包絡・`docs/design-candidates/shiori-tips-D.html` を手本として読ませる・**既存全 nm のリストを渡して重複回避**・出力は `const TIPS_<PFX>=[…]` と `const FAM_<PFX>=[f,'名','説明']`。
- **サブのモデル指定**: Agent 呼出時の `model:"sonnet"` か agent 定義 frontmatter で指定する（headless `claude -p --model sonnet` も可）。旧記述の env `CLAUDE_CODE_SUBAGENT_MODEL` による opus 固定（呼出指定が黙って負ける）は 2026-08-06 に解除済み＝該当 env は現存しない。旧迂回先だった agy は 2026-07-24 から使用禁止（委譲は Claude サブのみ＝CLAUDE.md）。走行後の実モデルは `grep -oE '"model":"[^"]*"' tasks/<id>.output` で確認。

### B. 機械検証（自己申告 GREEN は信じない）
各 entries ファイルを `node .claude/skills/shiori-tips/tools/verify_tips.js <entries.js> <constName>` にかける。判定: ①色は a/pp のみ ②canvasプリミティブのみ ③例外なし ④包絡（正しいアフィン変換スタックで実座標判定）⑤ファイル内 nm/rd 重複なし。**HARD FAILS 0 が通過条件**。
- 包絡は `save/restore` スタックの実装必須（怠ると translate がループで累積し偽陽性＝y99 等）。tools/verify_tips.js は実装済み。

### C. 横断 dedup
全 nm/rd の衝突を検出（テーマ跨ぎの同名）。`node .claude/skills/shiori-tips/tools/cross_dedup.js`（正本 shiori-tips-D.html の既存名＋新 entries を突合）。同名は改名 or drop。同音異字（例 富士/藤＝ふじ）は別意匠なら可。

### D. 提示（人の審級）
全数カタログHTMLを組み `chrome <file>` で見せる。ext4 の .html は UNC 読み込みが脆いので `/mnt/c` へ複製して開く。opt-out 方式（省くものだけ番号指定・残り全採用）。

### E. 統合
1. **Kotlin 移植**: 既存の `SHIORI_TIPS` ラムダを Rosetta stone に、JS draw → DrawScope へ翻訳（翻訳表は下）。SHIORI_TIPS 末尾へ**正順で追記**。bulk（数十以上）はサブへ委譲可＝ただし全数 diff 照合＋最難関（arcTo/save-rotate/部分弧）をスポット目視。
2. **正本HTML・書影モック同期**: `shiori-tips-D.html` と `bookshelf-shiori-grid-D.html` の TIPS を Kotlin と同順の総数へ（両者とも `const TIPS=[…]` を自前で持つ＝Kotlin の SHIORI_TIPS から機械生成すると取りこぼさない）。`bookshelf-shiori-consistency-D.html`/`bookshelf-shiori-palette-D.html` も TIPS を持つが色/整合が主眼で任意（174系列へは 2026-07-16 同期済み）。
3. **ゴールデン再生成**: `node .claude/skills/shiori-tips/tools/shiori_golden.js <新tipCount>` → `ShioriGeneratorTest` の `tipCount` と3件の tipIndex 期待値を差し替え（hue/xFrac/lenFrac は不変）。
4. **テスト**: 自分で実行する（旧記述「背景だとコミットゲートのセンチネルが出ない→前景で」は失効＝センチネル機構ごと撤去済み・経緯は `/build` スキル）。ext4 worktree は `--init-script` 不要。Bashツールは env 明示（`/build` スキル参照）。確証には `--rerun-tasks` で強制再実行。

### JS canvas → Kotlin DrawScope 翻訳表（既存31ラムダで全パターン確認可）
- 全数値に `f`。`Math.PI/cos/sin`→`PI/cos/sin`。
- 単線 `moveTo;lineTo;stroke`→`drawLine(a, Offset(..), Offset(..), Wf, StrokeCap.Round)`。
- 塗り円 `arc(..,0,7);fill`→`drawCircle(a, rf, Offset(..))`。線円は `style=strokeR(Wf)`。
- 部分弧 `arc(cx,cy,r,s,e)`(stroke)→`drawArc(a, deg(s), deg(e-s), false, Offset(cx-r,cy-r), Size(2rf,2rf), style=strokeR(Wf))`。
- 矩形 `strokeRect(x,y,w,h)`→`drawRect(a, Offset(x,y), Size(wf,hf), style=strokeR(Wf))`。
- パス `beginPath;moveTo/lineTo/quadraticCurveTo(cx,cy,ex,ey);closePath`→`Path().apply{ moveTo;lineTo;quadraticTo(cx,cy,ex,ey);close() }`＋`drawPath(p,a[,style=strokeR])`。パス内弧は `arcTo(Rect(..),deg(s),deg(e-s),true)`。
- `save;translate(tx,ty);rotate(r);<local>;restore`→`rotate(deg(r), pivot=Offset(tx,ty)){ <local(lx,ly) を Offset(tx+lx,ty+ly) に置換> }`（蔵書印 #26 が手本）。
- 紙抜き `fillStyle=pp`→ 同図形を `paper` 色で（半月 #19・勾玉 #22）。

## tools/
- `verify_tips.js <entries.js> [constName=TIPS_BATCH1]` — 実行時検証（色/メソッド/例外/包絡/重複）。
- `shiori_golden.js [tipCount=31]` — 正本JS(hashStr/mulberry32)移植でゴールデン(hue/xFrac/lenFrac/tipIndex)を算出。
- `cross_dedup.js` — 正本＋新 entries の nm/rd 横断衝突検出。
