---
name: architecture
description: アプリ全体構成の入口。タスク→場所→罠の早見表と「コードから読み取れない設計判断・罠」だけを持つ（構造の詳細はコード/KDocが正本）。
triggers:
  - "アーキテクチャを教えて"
  - "全体構成を確認したい"
  - "どのファイルがどの役割か"
---

# アーキテクチャ早見表（WHERE-TO-LOOK）

日本語Web小説（なろう系）のPDFを、ふりがな対応HTMLに変換する Androidアプリ
（Jetpack Compose + 純 Kotlin PDF 抽出＝PDFBox-Android。旧 Chaquopy/Python 経路は 2026-07-05 Phase 5 で完全撤去。
精度オラクルの双子 `ab-review/submission-B` は残置）。

**構造の詳細（パイプラインのステップ構成・クラス関係）はコード/KDoc が正本**。
このスキルは「どこを見るか」と「コードから読み取れない罠・設計判断」だけを持つ。

## タスク → 場所 → 罠

| タスク | 場所 | 罠・注意 |
|---|---|---|
| PDF抽出ロジック | `android/app/src/main/java/com/novelreader/pdf/`（入口は facade `PdfBookExtractor.kt`＝4ステップ進捗・例外分類。ステップ構成は同ファイル KDoc） | `PDDocument.load` 前に `PDFBoxResourceLoader.init` 必須＝CID→Unicode 解決（`NovelReaderApplication.onCreate` で配線済み・task_diary #31）。グリフ正規化（波ダッシュ等）は `PdfExtractor` の `normalizeGlyphUnicode`（#35/#38） |
| 抽出の定数・ルール | `pdf/ParserRules.kt` | — |
| 精度の基準・回帰 | `ab-review/golden_regression/`＋実機ゲート `androidTest/…/pdf/PdfExtractorDeviceSpikeTest.kt`／HTMLバイト等価ゴールデン `src/test/resources/golden_html/` | 実機テストは `/device-verify` スキル必読（`connectedAndroidTest` 直叩きは蔵書DB消失＝task_diary #36） |
| UI（本棚/読書/目次） | `ui/BookshelfScreen.kt`（カード=`BookCard.kt`・バナー=`ProcessingBanner.kt`）／`ui/NativeReadingScreen.kt`（公開名 ReadingScreen。本文=`ChapterContent.kt`・設定=`ReadingSettingsSheet.kt`・エラー=`ReadingErrorScreen.kt`）。NavHost は2ルート（"bookshelf"・"reading/{bookId}/{startFile}"） | 読書画面は **WebView ではなく Compose ネイティブ**（HTML解析=`parser/ChapterHtmlParser.kt`・ルビ=`ui/compose/RubyText.kt`）。目次 `NativeTableOfContentsScreen` は NavHost ルートでなく ReadingScreen 内から表示 |
| 見た目（配色・タイポ・余白）の変更 | まず `docs/decisions/0005-ui-n-visual-language-D.md`＋claude.ai/design のモック現物（`ui-n-phase0/*-D.html`・取得は `DesignSync: get_file`・入口は handover.md） | **HTMLモックが正本・Compose は翻訳**＝Compose 側で意匠を自己判断しない。色=`theme/Color.kt`・明朝=`theme/Typography.kt` の `MinchoFamily` 経由（直書き禁止） |
| 変換サービス | `PdfProcessingService`（Foreground）→ `BookRepository.addBook` → `PdfBookExtractor.process` | 下記「コードから読み取りにくい設計判断」 |
| データアクセス | `repository/BookRepository.kt`（Room + 抽出呼び出し。`NovelReaderApplication` がシングルトン保持し Service/ViewModel 共用） | DB操作は IO Dispatcher |
| DBスキーマ | `AppDatabase.kt`＋各 Entity が正典（version・Migration 含む） | 変更は必ず `/db-migration` スキルを先に実行 |
| 生成物の保存先 | `context.filesDir/novels/{bookId}/`（`index.html`＋`chap_N.html`） | — |

## コードから読み取りにくい設計判断・罠

- **進捗/エラーのUI通知**: `NovelReaderApplication.processingState: StateFlow`（書き込みは `updateProcessingState()` のみ）＋ `errorEvents: Flow<String>`＝**Channel ベースの one-shot**。StateFlow だと画面回転で再表示・複数購読で重複するため Channel（受信時に消費・clearError 不要）。
- **多重起動制御**: `ReentrantLock` + `ArrayDeque<Uri>` のキュー方式（処理中の追加PDFは無音破棄せずキューへ）。「キュー追加+ループ起動判定」と「取り出し+終了判定」を1つの lock でアトミックに保護。
- **停止（ACTION_STOP）はページ境界で即中断**（2026-07-07 再配線）: キュー待ちは破棄し、処理中の1冊も子 Job（`currentBookJob`）を cancel → `BookRepository.addBook` の進捗コールバック内 `ensureActive()`（TextProcessor 自体は coroutines 非依存）が次のページ境界で中断する。ループ Job ごと cancel しないのは cancel〜finally 間に来た ACTION_START を取りこぼすレース回避（ループは生かし次周回の空キュー検知で `stopSelf`）。処理ループ中は `PARTIAL_WAKE_LOCK` 保持（OPPO のバックグラウンド強制停止対策）。
- **強制終了（OEM kill/OOM/onTimeout）からの再開**（2026-07-07 導入）: enqueue 時に `pending_jobs`（Room v8・`PendingJobDao`）へ記帳し成否確定で削除（明示停止は全消し＝再開しない）。次回起動時 `NovelReaderApplication.runStartupRecoveryOnce()`（MainActivity.onCreate トリガー・プロセス毎1回・Service 非稼働時のみ）が孤立HTML掃除（books に無い `novels/<id>/` を削除）→ 未完了ジョブを snackbar 通知＋権限が生きる分を FGS 再投入。プロセス跨ぎ読取のため `BookshelfViewModel.addBook` が `takePersistableUriPermission` 取得（解放は記帳削除時）・記帳の insert/全消しは `pendingJobDispatcher`（並列度1）で直列化＝「追加直後に停止」でも破棄済みジョブが復活しない。
- **読書進捗の上書き防止**: `index.html`（目次）閲覧時は進捗を上書きしない制御が `NativeReadingScreen.kt` にある。実装は `fileName != "index.html"` の**ブロックリスト方式**（`chap_` 接頭辞の許可リスト判定ではない）。
- OPPO/ColorOS 固有動作 → `/device-verify` スキル（症状→対処表）経由で `task_diary.md` を参照。
