# Robolectric 上の pdfbox-android は実機とグリフ解決が完全一致する（JVM でゴールデン回帰が成立）

★★★／2026-07-16／JVM 単体テスト（Robolectric）で実PDF抽出のゴールデン回帰が成立＝実機不要で常時ゲート化できる
**実測環境**: pdfbox-android 2.0.27.0・Robolectric 4.11.1（@Config sdk=34）・Temurin 17

## 事実

- `PDFBoxResourceLoader.init(ApplicationProvider.getApplicationContext())` は **Robolectric の Application Context でも AAR 同梱の CMap/glyphlist 資産を正常にロード**し、CID→Unicode グリフ解決が実機（OPPO PGEM10 実測）と一致する。
- 一致の強さ: golden 3本のうち N1453LW / N2959KI は **body_sha256 が実機基準と完全一致**（38万字規模）。N6169DZ の章題不一致は JVM 2件 vs 実機 11件で、**JVM の方が少ない**（FF0D→2212・矢印等9件が `normalizeGlyphUnicode` 済みの golden に吸収されるため）。
- 速度: 8.9MB / 8,668ページの N6169DZ でも抽出＋突合 **約6秒**（実機 androidTest は約2分＋端末必須）。

## なぜ知見か（従来認識を覆す）

- 本リポジトリは従来「GlyphStripper/loadPages は実 PDF I/O ゆえ JVM 単体では走らせず実機 androidTest へ回す」を設計判断として明記していた（旧 PdfExtractorTest KDoc）。しかし**未実証の慎重論だった**ことが実測で確定——資産ロードの実体は Context 経由の assets アクセスで、Robolectric が忠実にエミュレートする。
- upstream apache-pdfbox（submission-B の JVM 成功実績）とは別物である点に注意: pdfbox-android は `PDFBoxResourceLoader` 初期化が必須で、そこが従来の不確定要素だった。

## How to apply

- 抽出ロジックの回帰は `JvmGoldenRegressionTest`（`android/app/src/test/`）が正本ゲート＝`testDebugUnitTest` に同乗し常時実行。実機 `PdfExtractorDeviceSpikeTest` は同一合格ラインの二重化（assets 手動配置時のみ・OEM 固有挙動の最終確認用）。
- 新しい golden を足すときは PDF を `sample_pdfs/`・基準 JSON を `ab-review/golden_regression/` へ（どちらも git 追跡済み）。JSON は `buildSnapshot` と同指標で生成する。

関連: `golden-regression-baselines.md`（基準値の正本）・`pdfbox-pdfminer-glyph-mapping-differences.md`（グリフ差の機序）
