# scrape fixture（実HTMLスナップショット）の取得・更新手順

★★・2026-07-23・**fixture はバイト無加工の実HTMLが正本＝取得/更新の作法と「大文字タグ取り逃し」等の罠**

> 対象: `android/app/src/test/resources/scrape_fixtures/<site>/`。golden テスト（例: `KakuyomuGoldenTest`・`AkatsukiGoldenTest`）が
> サイト構造ドリフトを `testDebugUnitTest` で常時検知するための正本データ。plan `scraping-foundation-design-2026-07-20.md` P6 宿題の docs 化（2026-07-23）。

## 手順

1. **サンプル作品の条件**: 完結済み（構造が動かない）・章数 10〜100（TOC 件数固定に手頃）・本文に実ルビを含む話を最低1つ（ルビ変換の回帰に必須）。
2. **取得**: 素の GET で**バイト無加工**保存（整形・再エンコード禁止。gzip 透過解除のみ可）。リクエスト間隔はアダプタの `crawlDelayMs` 以上を手動でも守る。総フェッチは目安 10 以下。
3. **命名**: `toc_<作品ID>.html`／`episode_<話ID>.html`（ID は URL 中の実値）。
4. **確認**: charset（UTF-8 か）・BOM 有無・Cookie/UA 要件（素の GET で 200 か）を記録し、golden テストの固定値（章数・タイトル・ルビ変換例・本文床値20字）を実ファイルから起こす。
5. **更新時**: サイト改版で golden が割れたら、新スナップショットを再取得して**差分を目視**（構造変化の把握が目的＝無言差し替え禁止）。旧 fixture は git 履歴が保持するため削除してよい。

## 罠（実測）

- **タグ名の大文字**: 暁は `<RUBY><RB><RT>` と大文字で出す。case-sensitive な grep/セレクタは「ルビ0件」と誤判定する
  （2026-07-23 実測: 候補5作品を『ルビ無し』と取り逃した）。ルビ探索・パーサとも case-insensitive 必須。
- **`《》` の不在**: 投稿時の `｜base《reading》` はサイト側で `<ruby>` に変換済みのことがある＝生 HTML に `《》` が無くてもルビはある。
- **複数 `div.body-novel` 型**: 前書き/後書きが本文と同クラスの別ブロックで並ぶサイト（暁）では、マーカー（`<b>前書き</b>` 等）で
  帰属を判定しないと袖が本文へ混入する。
