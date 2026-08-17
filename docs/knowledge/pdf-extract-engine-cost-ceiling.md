# PDF 抽出の高速化には天井がある（engine の約 55% は PDFBox 内部）

★★ / 2026-08-17 / `Extract#engine` の 2/3 は `loadPages`、その 85〜91% は PDFBox 内部＝こちらから動かせない。可動域は engine の約 45% しかない。

## 症状（というより、着手前に知っておくこと）

「取込が遅い」を C++ 系の最適化論（ホット/コールド分離・アロケーション削減・データレイアウト）で
攻めようとすると、支配区間を外した箇所に労力を払うことになる。

## 実測（JVM プロファイラ `ExtractPhaseProfileTest`・N6169DZ 8.9MB/8,668ページ/338万グリフ）

engine の内訳（比率は中編 N2959KI 799ページでもほぼ同一＝**規模を9倍にしても動かない**）:

| フェーズ | engine 比 |
|---|---|
| `loadPages`（グリフ収集） | 66〜70% |
| `DetectedRules.detect` | 18〜22% |
| `TextProcessor.processPages` | 12〜13% |

`loadPages` をさらに分離すると、**PDFBox のパース+TextPosition 生成が 85〜91%**、
自前（`normalizeGlyphUnicode` + `CharBox` 生成）は 9〜15%。

`processPages` の中では `groupCharsByLine` が内部比 27〜30% あるが、**engine 全体では 3〜4%**。
`associateRuby` の二重ネストループは engine の 0.2% 未満。

## 対処（どこを触る価値があるか）

- **可動域は engine の約 45%**（detect 約20% + processPages 約13% + CharBox 生成約10%）。
  全部ゼロにしても engine は 2 倍未満。実機の取込 24 秒が理論下限で 13 秒。
- 費用対効果が最も良かったのは `detect` の**重複した仕事**の除去。`lineStepX` と `rubyOffsetX` が
  ページごとに同じ `filter`+`groupCharsByLine` を2周していたのを1周に畳んで、その列復元ブロックを
  42% 削減（＝detect 約27%減・engine 5〜6%減）。挙動は `body_sha256` で不変を確認済み。
- `groupCharsByLine` の線形走査（許容誤差つき近傍一致）と `associateRuby` の二重ネストは、
  **形は悪いが量が無い**。厳密に同じ列を選ぶ索引を設計するコストに見合わない。

## なぜそうなるか / 再計測の作法

支配区間がサードパーティのパーサなので、自前コードをいくら整えても天井が動かない。
元ネタの C++ 事例（88〜200倍）は対象が全部自前カーネル＝可動域 100% だった点が決定的に違う。

再計測するときは:

- **走行をまたいだ絶対値比較をしない**。同じコードのまま `loadPages` が 3.5s→5.8s、
  `processPages` が 0.7s→1.0s に振れた走行を実測している（機械側の負荷・drvfs・GC）。
  変更の効果は**同一走行内で旧形状と新形状を並べて比で読む**（`probeColsOldShape` /
  `probeColsMergedShape` がその対照群）。
- プローブは本体のコピーなので、本体を変えたら追従させる。被覆率が 100% に届かないのは正常
  （段落縫合を測っていない＋JIT 差）。**上回ったら**追従漏れか計測破損。
- 絶対値の正本は実機 Macrobenchmark（`PdfImportBenchmark` + `ImportBudget`）のまま。
  JVM の値は比率と相対比較にだけ使う。
