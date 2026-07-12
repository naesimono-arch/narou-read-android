# 0012. なろう作品の"閲覧"をアプリ内 WebView（加工なし・URL 観測のみ）で行い、読書位置を記録して続きから再開する

- ステータス: Accepted
- 日付: 2026-07-11
- 関連実装: `ui/discovery/WebReaderScreen.kt`＋`viewmodel/WebReaderViewModel.kt`（読書用 WebView・URL 観測記録）／`data/WebReadingProgressEntity.kt`＋`WebReadingProgressDao.kt`（Room v15・`web_reading_progress`）／`narou/ContinuationLogic.kt` `parseNarouEpisodeNumber`（話ページ URL→話数）／`ui/discovery/NovelDetailScreen.kt`（続きから読む導線）／`ui/WebBookCard.kt`＋`ui/BookshelfScreen.kt`（本棚 Web カードの続き導線）／`MainActivity.kt`（`web-reader/{ncode}/{startEpisode}` ルート）
- 関連知見: `task_diary.md` #45（なろうヘルプ183「よくある違反行為」の原文・根拠条項）
- 関連ADR: [0010](0010-narou-unmodified-handoff-custom-tabs.md)（**本 ADR が"閲覧"用途について一部を更新する**＝読書送客の既定を Custom Tabs から「加工なし・URL 観測のみの WebView」へ改める。ただし 0010 の禁止本体＝"加工"はより厳格に厳守）／[0011](0011-narou-pdf-import-webview-limited-reintroduction.md)（"取り込み"用途の WebView 限定再導入。0011 は scrollIntoView 注入を許すが、本 ADR の読書 WebView は**JS 注入を一切しない**点が異なる）／[0005](0005-ui-n-visual-language-D.md)（意匠は権利を自前で持つ PDF 読書面に集中＝本 ADR の WebReader も意匠を発明しない＝仮意匠）

## Context（背景）

「Web由来カード／検索で見つけた作品の続きを、どこまで読んだか記録し、次回はその話から再開したい」（機能②・2026-07-09/07-11 ユーザー要望）。実現には**アプリが「今どの話を読んでいるか」を知る**必要がある。

ADR 0010 は読書を **Chrome Custom Tabs による加工なし送客**に定めた。しかし Custom Tabs は別プロセスのブラウザにページを丸投げする構造上、**アプリ側から閲覧中の URL・スクロール位置を一切観測できない**。したがって Custom Tabs のままでは読書位置を機械的に記録する術がなく、機能②は「ユーザーが手で第N話を申告する」等の摩擦の大きい代替に頼らざるを得なかった。

一方、なろう運営ヘルプ183「よくある違反行為」の原文（task_diary #45）は次を**明示的に区別**している:

- **問題としない**（原文）: 「WebView 及び類似の技術を用いて、作品の閲覧ページを**加工することなくそのまま**当該アプリ内で表示する行為」。
- **違反**（原文）: 「閲覧ページにて**広告を除去する等の加工**を行って表示する行為」／「本文を**機械的に取得**して表示・ダウンロードする行為」。

つまり **加工なしの WebView 表示それ自体は規約が明示的に許容している**。ADR 0010 が WebView 読書を退けたのは「禁止だから」ではなく、**WebView は `evaluateJavascript` 等で後から加工でき"無加工"を仕組みで保証しにくい**という運用上の慎重さ（0010 Why-not）が理由だった。

ここで、読書位置の記録に必要なのは **URL 文字列の観測だけ**である。WebView が `onPageFinished` で渡す URL（`https://ncode.syosetu.com/<ncode>/<N>/`）を読めば「第N話を開いた」が分かる。**URL を読む行為はページの加工でも本文の機械的取得でもない**（本文テキストは取得せず、ブラウザが辿った所在だけを見る）。

## Decision（決定）

**なろう作品の"閲覧"を、加工を一切しない・URL 観測のみの読書用 WebView（WebReader）で行い、話ページ到達を読書位置として記録して「続きから読む」を提供する。** 0010 の「閲覧＝加工なし送客」の"送客先"を、機能②の対象範囲に限り Custom Tabs から WebReader へ改める。

1. 読書用 WebView `WebReaderScreen` を新設し、`narouWorkUrl(ncode)`（目次＝初回）または `narouEpisodeUrl(ncode, N)`（続きから）をロードする。
2. `onPageFinished` の URL を `parseNarouEpisodeNumber` に通し、当該作品の話ページ(`.../<ncode>/N/`)なら第N話を読書位置として **last-wins で上書き記録**する（`web_reading_progress`・Room v15）。目次・感想・別作品・外部リンクは記録しない。
3. 記録があれば作品詳細・本棚 Web カードに「続きから読む 第N話」を出し、記録した話へ WebReader で直接着地させる。
4. 記録は本棚配置(`web_novels`)と**直交した別テーブル**に持つ＝検索経由で開いただけの未配置作品でも記録でき、機能②が「検索画面推移／Web由来カード」の両方で成立する。

### 規約の厳守事項（0010 の"加工"禁止を 0011 よりさらに厳格に守る）

- **読書 WebView には JS を一切注入しない**。取り込み用（0011・`PdfImportScreen`）はビューポート移動のため `scrollIntoView` を注入するが、**読書 WebReader は `evaluateJavascript` を一度も呼ばない**（スクロールも含め注入ゼロ）。なろうの閲覧ページを提供そのまま（広告含む）表示するだけ。
- **CSS 注入・DOM 改変・広告除去・本文テキストの抽出は一切しない**。アプリが触るのは `onPageFinished` の URL 文字列のみ。
- 広告はなろうのページがそのまま描画＝**必ず表示される**（収益基盤を侵さない）。
- これらは `WebReaderScreen.kt` の冒頭 KDoc とコード内コメントに固定し、将来の変更で崩さないよう明記する。

## Consequences（帰結）

- ADR 0010 の Decision 1「閲覧は Custom Tabs を既定」は、なろう作品の読書について本 ADR が更新する（0010 側に相互参照注記を追記）。0010 が守った"加工なし"の実質は、注入ゼロ・URL 観測のみで**むしろ 0011 より厳格に**維持される。
- `INTERNET` 権限は既存。WebReader は読書時のみ生成し破棄する（`DisposableEffect` で destroy）。
- **適用範囲**: 作品詳細（検索経由）＋本棚 Web由来カード。**PDF 蔵書の継続カード（`NativeReadingScreen` の「なろうで続きを読む」）は本 ADR のスコープ外＝当面 Custom Tabs 送客のまま据え置く**（当該画面は副作用が濃く実機検証を伴う改修が要る＝handover のリファクタ項。将来 WebReader へ寄せるかは別途判断）。
- 意匠は仮（モック未収載）。「続きから読む」ボタン・Web カードの続き導線は既存トークンの範囲で最小実装し、モック追従は宿題（handover）。

## Why-not（採らなかった選択肢）

- **Custom Tabs 据え置き＋読書位置は自己申告 UI（「ここまで読んだ」を手動選択）**: 規約は最も安全（0010 のまま）だが、①毎回ユーザーが話数を手で申告する摩擦、②申告忘れで記録が形骸化、が大きい。加工なし WebView は規約が明示許容しており、URL 観測で摩擦ゼロ・自動記録にできる利点が上回る。
- **アプリ内に「第1話〜第N話」の番号一覧を出す（Custom Tabs のまま各話へ送客し、タップを記録）**: なろう公式 API は**話タイトルを返さない**（取れるのは総話数 `general_all_no` のみ＝task_diary なろうAPI節）ため、一覧は番号だけの無味な羅列になり、955話級では自分で番号を探す不便が残る。WebReader では**なろうの目次ページそのものが話一覧の役割**を果たし、実際に読んだ話を URL から拾えるため、番号一覧を自作する必要が消える。
- **本文をネイティブ取得して自前描画（究極の没入）**: 「本文の機械的取得」に真正面から抵触（0010 Why-not と同じ＝案A の放棄で検討外）。

## 追補（2026-07-11）: 戻り遷移は記録しない（巻き戻り対処）

**実測（2026-07-11 実機スモーク・2作品で再現）**: `onPageFinished` は `goBack()` による履歴遡行でも再発火するため、ep1→ep2→ep3 と読み進めた後に戻る操作（本画面の Back/← は履歴がある限り goBack）で退出すると、Decision 2 の last-wins 記録が**セッション先頭話（ep1）へ巻き戻る**。仕様文言「最後に開いた話」には忠実だが「続きから」の UX 期待（読み止めた位置）を裏切る。

**決定**: 戻り遷移で到達したページは記録しない。判定は `copyBackForwardList()` の**履歴スタック位置**（`currentIndex < size-1`＝forward 履歴が残っている＝戻りで到達）による構造判定。新しいリンクを踏めば forward 履歴は切り詰められ末尾に戻るため、目次等から**意図的に開き直した話は従来どおり記録**される（last-wins の意図は前進閲覧に対して維持）。

**Why-not**:
- **「戻り操作中」フラグで次の onPageFinished を抑制**: 戻る連打時に goBack 発行数と onPageFinished 発火数が一致せず（描画スキップ/合流）、抑制の取り逃しと残留（次の正当な前進記録を殺す）のレースがある。構造判定は発火数に依存しない。
- **単調更新（max(既存, 新値)）**: 実装最小だが「意図的に前の話へ戻してそこから再開したい」（読み返し）を潰す副作用があり、last-wins の設計意図（最後に開いた話そのもの）と衝突する。
- **現状維持**: 実機で2作品とも巻き戻った＝通常の退出動線（戻る連打）で高頻度に発生するため放置不可。

## 追補（2026-07-12）: 再開の粒度は「話の冒頭」まで／構成変更は saveState で持ち回る（UX/Design 全層監査）

**背景**: 監査の persist Minor（話内スクロール非追随）と persist Major（構成変更で WebView 破棄→話が巻き戻る）。いずれも「JS 注入禁止」という本 ADR の中核制約から来る**構造的な受容境界**と、その中で埋められる部分の切り分け。

**決定 A（再開粒度＝話の冒頭・正直な表記）**: 読書 WebView は JS を一切注入しない（scrollIntoView すらしない）ため、話「内」のどこまで読んだか（スクロール位置）は**観測も復元も構造的に不可能**。したがって「続きから」の実体は常に**記録した話の冒頭への着地**である。これを「続きから読む」とだけ表示すると、前回読み止めた行から続く期待を抱かせ嘘になる（公理8 嘘をつかない）。よって UI 文言を **「第N話のはじめから読む」** とし、着地点（話の冒頭）を明示して期待を正す。
- 実装: `NovelDetailScreen` 詳細画面の再開 CTA を「第N話のはじめから読む」に変更。
- スコープ: 話内スクロールの復元は**やらない**（本文の機械的取得/加工に踏み込まずに実現する術が無いため。案A＝本文ネイティブ取得は 0010 Why-not で放棄済み）。これは劣化ではなく本 ADR の設計上の帰結＝**受容された境界**。

**決定 B（構成変更＝WebView.saveState/restoreState で履歴ごと持ち回る）**: 回転・ダーク切替・fontScale 変更等の Activity 再生成では WebView が破棄される。素朴に startUrl（入場時の話）を再ロードすると読み進めた分・前後ページ履歴が入場時話へ巻き戻る（副経路の巻き戻り）。これを **`WebView.saveState`/`restoreState` を `rememberSaveable`(Bundle) で持ち回る**ことで、再生成後も同じ話・同じ履歴から続けられるようにする。
- **規約整合**: `saveState`/`restoreState` は**ネイティブ WebView の状態シリアライズ API** であり、`evaluateJavascript` でも DOM 改変でもない。よって本 ADR の「加工なし・注入ゼロ」を**一切侵さない**（View 自身の状態を Bundle に写して戻すだけ）。
- 実装上の要点: 状態保存フェーズ（`onSaveInstanceState` 経由）は `onDispose` より前に走るため、`onDispose` で Bundle を書いても初回の構成変更に間に合わない。`rememberSaveable` の custom `Saver.save` を「保存フェーズ時点で生存中の WebView へ `saveState` する」形にして最新状態を確実に捕える（`WebReaderScreen.kt`）。
- 限界: `restoreState` は履歴スタック（＝今どの話か）を復元するが、話内スクロールは決定 A の理由で戻らない（＝話の冒頭へ）。プロセス death 復帰では Bundle サイズ次第で `TransactionTooLarge` の懸念があるが、通常の閲覧履歴規模では収まる。

**併記（監査 d-chrome Minor・画面消灯抑止）**: 読書コンポジション内で `View.keepScreenOn = true`（`DisposableEffect`・`onDispose` で解除）を入れ、長章の無操作読書中に OS 消灯で没入が切れる欠陥（Design/09 F）を最低限是正する。ネイティブ読書面との chrome 媒体差（上部バー常設 vs 中央タップ没入）は本 ADR で大半正当とした差のため、寄せるのは消灯抑止のみに留める。
