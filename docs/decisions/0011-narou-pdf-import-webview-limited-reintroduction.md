# 0011. なろう公式縦書きPDF取り込み導線に WebView を限定再導入する（案B・0010 を本導線に限り補完）

- ステータス: Accepted（一部**要スパイク**＝下記「未確定事項」）
- 日付: 2026-07-09
- 関連実装（予定）: `NovelDetailScreen.kt`（取り込みボタン）／取り込み用 WebView 1画面（`setDownloadListener`）／`BookshelfViewModel.addBook(uri)`（既存合流点）／`BookEntity.ncode`（既存列・作品紐付け）／`narouWorkUrl(ncode)`（既存・目次URL）
- 関連知見: `task_diary.md` #45（なろうヘルプ183「よくある違反行為」の原文・根拠条項）／なろうヘルプ99 `helppageid/99`（縦書きPDF機能）／`handover.md`「PDF取り込み導線」節（実機偵察の一次情報）
- 関連ADR: [0010](0010-narou-unmodified-handoff-custom-tabs.md)（WebView 廃止・加工なし送客を既定化。**本 ADR は 0010 を覆さず、"取り込み"という別用途に限り WebView 再導入の例外を定義する**）／[0005](0005-ui-n-visual-language-D.md)（没入意匠は権利を自前で持つ PDF 読書面に集中）

## Context（背景）

なろうで発見した作品を、なろう公式の**縦書きPDF機能**（目次ページ最下部「縦書きPDF」リンク→生成ページ〔広告＋出だし200字〕→「縦書きPDFダウンロード」ボタン。ヘルプ99）で DL し、その場で蔵書化する導線を作りたい。**抽出互換性は実証済み**＝ゴールデン本 `N2959KI/N1453LW/N6169DZ.pdf` がまさになろう公式縦書きPDF（`ParserRules.kt` の座標/フォント判定はこれ前提）で、取り込めれば既存の変換パイプラインにそのまま乗る。

しかし本アプリは ADR 0010 で WebView を意図的に廃止し「加工なし送客（Chrome Custom Tabs）」を既定にした経緯がある（`InAppBrowser.kt`・task_diary #45）。**0010 が塞いだのは「閲覧ページを WebView 内包し独自UIを被せて没入させる／本文をネイティブ描画する」用途**であり、禁止の本体は"加工"そのもの（ヘルプ183「広告を除去する**等の**加工」）だった。本導線は閲覧ではなく**公式が用意した DL 機能の操作**であり、用途が異なる。

2026-07-09 にユーザーと方式を合意し、実機偵察で以下を確定した（一次情報＝`handover.md`）:

- **DL方式＝静的直リンクではなく動的・多段・CSRFトークン制（確定）**: 目次最下部 `div.c-under-nav` 内の POST フォーム（`action=/novelpdf/creatingpdf/ncode/<ncode>/`・hidden の**ページ毎 CSRF トークン**）→ 生成完了ページ → DLリンク `/novelpdf/downloadend/.../pdftoken/<ワンタイム>/`（XHR で約4KB の中間ページ）→ 実体 `https://pdfnovels.net/<生成毎トークン>/<NCODE>.pdf`。**帰結**: 直URLを DownloadManager で叩く近道は技術的に不可（毎回フローを通す必要）。
- **ログイン不要（確定）**: 非ログイン状態でフォーム・トークン・生成・実体リンクまで成立。ゲートは認証でなく CSRF トークン＋生成フロー。→ WebView 側の Cookie/セッション管理は不要（案Bの実装が一段軽い）。
- **名前付きアンカー無し（確定・先の暫定を訂正）**: `#footer`/`#main` は対象要素なし・`location.hash` でも `scrollY` 不動。PDF フォームは id 無しの `c-under-nav`／`l-foot-contents` 内・最下部（≈5778/5977px）。→ Custom Tabs ではフラグメント自動スクロール不可＝目次全体を手動スクロールする摩擦が確定（951話等で特に重い）。

## Decision（決定）

**案B＝WebView を本導線に限定再導入する。**

1. `NovelDetailScreen.kt` に「縦書きPDFを取り込む」ボタンを置く。
2. タップで**取り込み専用 WebView**を開き、`narouWorkUrl(ncode)`（目次）をロード。
3. PDF フォーム位置まで**注入 JS で自動スクロール**（`scrollIntoView` 等＝ビューポート移動のみ）。ユーザーが自分で「縦書きPDFダウンロード」相当をタップして生成・DL を実行する。
4. `setDownloadListener` で DL を捕捉し、一時ファイル→`content://` 正規化を経て**既存の `BookshelfViewModel.addBook(uri)` に合流**（変換〜Room 登録を再利用）。
5. 取り込んだ本に **`BookEntity.ncode`（既存列）を紐付け**、継続読書と接続する。

### 0010 との線引き（規約の厳守事項）

- WebView 再導入は**本取り込み導線に限定**する。0010 の「閲覧は加工なし送客」は覆さない。
- **「加工」禁止を厳守**＝広告は絶対に残す。注入する JS は**スクロール（ビューポート移動）のみ**に限定し、**CSS 注入・DOM 改変・広告除去は一切しない**。
- 生成ページ（広告＋出だし200字）を**構造的に必ず経由する**点はむしろ規約上プラス（広告が必ず表示される）。
- **純 HTTP で POST を機械化して広告をスキップするのは「本文の機械的取得」＋収益回避で違反＝絶対にやらない**（直URL DownloadManager 近道はトークン制で技術的にも不可）。

## Consequences（帰結）

- ADR 0010 は"閲覧"に対しては引き続き有効。WebView という技術の再登場が 0010 と矛盾して見えないよう、0010 側にも本 ADR への相互参照注記を追記する（用途の違いで両立）。
- `INTERNET` 権限は既存。WebView は取り込み時のみ生成する使い捨て1画面とし、閲覧系（発見・継続読書）には持ち込まない（0010 の適用範囲を侵さないための境界）。
- 既存 `addBook` は `content://`＋`takePersistableUriPermission` 前提／WebView DL は別経路のため、**URI 変換（DL 実体→一時ファイル→`content://` 正規化）の配線が新たに要る**。

## Why-not（採らなかった選択肢）

- **案A（Chrome Custom Tabs ＋共有受信）**: WebView 不要で規約完全セーフだが、**名前付きアンカーが無い**ため生成フォームまで**目次全体を手動スクロールする摩擦**が確定（951話等で重い）。加えて DL 後にアプリへ戻す**共有の一手間**も残る。この**2摩擦**を両方消せる点が案B採用の決め手。0010 で "加工なし送客" の既定として採った案A自体は閲覧では引き続き正しく、ここで否定するのは"取り込み UX"としての適性のみ。
- **案C（WebView ＋手動スクロール）**: WebView は再導入するのに、名前付きアンカー無しで自動化できる摩擦（手動スクロール）を解消しない中途半端案。JS スクロール（ビューポート移動のみ＝加工に当たらない）で自動化できる以上、採らない理由がない。
- **純 HTTP で生成フローを機械化（広告ページを経由せず PDF を取得）**: 「本文の機械的取得」＋広告収益回避で規約違反。技術的にも CSRF/ワンタイムトークン制で近道不可。**検討対象外**。

## 未確定事項（要スパイク・結果で本節を追記）

> **make-or-break＝fresh session の一歩目・実装より先。** → **2026-07-09 スパイク実施・解決済み（下記）。**

- **`setDownloadListener` が最終 PDF（`pdfnovels.net/<token>/<NCODE>.pdf`）で発火するか**が唯一の未検証点だった。中間ページが `window.location=<pdf>`（フルナビゲーション）なら application/pdf を描画不能と判断して発火＝配線は素直。もし `fetch`＋`blob:`＋`<a download>` 実装なら発火せず、`shouldInterceptRequest` で `pdfnovels.net` の GET を横取りする**フォールバック**が要る——という分岐だった。
- **検証手順**: 使い捨て WebView 1画面（debug ソースセット `spike/PdfDownloadSpikeActivity.kt`・logcat タグ `PdfSpike`）で n2959ki を通し、実機 PGEM10（Android 16 / WebView Chromium 149）で観測。

### スパイク結果（2026-07-09 実測・発火確定）

- **`setDownloadListener` は最終 PDF URL で発火する（確定）**: `url=https://pdfnovels.net/<token>/N2959KI.pdf`・`contentDisposition=attachment; filename=N2959KI.pdf`・`contentLength=891538`。**採用経路＝`setDownloadListener`。`shouldInterceptRequest` フォールバックは不要。**
- **フローの実測形**（偵察時の「XHR 中間ページ」想定を訂正）: 生成ページ内の XHR は `pdfnovels.net/pdfout/checkpdfapi/ncode/<ncode>/` への POST（生成完了ポーリング）で、`downloadend` ページへは**フルナビゲーション**、そこから PDF 実体へも**フルナビゲーション**（`shouldOverrideUrlLoading` で観測）。`blob:`/`a[download]` 方式ではなかった＝素直に発火。
- **⚠️ 配線時の注意（実測で判明）**: サーバの `mimetype` は **`application/octet-stream`**（`application/pdf` ではない）。**MIME での PDF 判定は不可**＝`contentDisposition` の `filename=*.pdf` か URL 拡張子で判定すること。
- 同一トークン URL は直後の再タップで再発火した（少なくとも短時間はトークン再利用可）。生成は数秒で完了（短編 n2959ki・checkpdfapi POST から約3秒で downloadend 遷移）。
