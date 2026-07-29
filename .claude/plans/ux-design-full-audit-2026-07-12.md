# UX/Design 全層監査 — 一次情報アーカイブ（2026-07-12）

> **対象ブランチ**: ui/polish ／ **監査対象**: novel-reader コードベース全体（Kotlin 約17,400行）

> **file:line は 2026-07-12 時点で凍結**。以降の移設で現ツリーと一致しない箇所がある
> （例: `viewmodel/ShelfItems.kt` は `domain/` へ移設済み）。指摘の中身が一次情報であって、
> パスは当時の証拠位置＝機械的に張り替えない。現在地はコード側を引くこと。

> **正本の役割分担**: この文書は監査の**一次情報（全指摘の evidence・敵対的検証ノート）の細部アーカイブ**。
> やること（backlog）の正本は `handover.md`「特別監査項目」節。現況は `STATUS.md`。指摘の判定基準は `/mnt/c/Users/qingj/Desktop/project/UX`（UX24層＋Design10層）。

## 監査方法

- `00-監査プロンプト.md` §C（UX/07 プロンプトA＋Design/07 プロンプトB＋UX/06・Design/06 チェックリスト）を多エージェント（45体）で実行。
  ※このファイルは上記の判定基準リポジトリ側（`/mnt/c/Users/qingj/Desktop/project/UX/00-監査プロンプト.md`）にあり、本リポジトリには収蔵していない。
- 事実地図作成 → 18観点ファインダーが各層＋該当コードを読み指摘生成 → **各指摘を敵対的検証**（file:line 実在＋根拠確認・成立しないものは REFUTED/降格）→ チェックリスト取りこぼし検出 → §D統合。
- **検証済み(REFUTED除く) 130件・棄却 1件**。Critical 3件は監督（Claude）が現物コードで再確認済み。

**件数**: Critical 3 / Major 30 / Minor 36 / 要検証 16 / 人間テスト送り 6 / 良い点 42

---

## A. 統合報告（§D 形式・handover 転記元）

# 統一監査 統合ドラフト（novel-reader / Android）

## 1. 総評
- **Critical 3件は「読書資産の喪失」に集中**——①目次から章を確認しに開くと自動保存位置が即上書きされ読みかけの先端が不可逆消失（戻す導線なし）、②端末喪失で蔵書・読書位置・設定が全損（バックアップ経路が三経路とも皆無）、③アプリ未起動でも日次通知でユーザーを読書へ呼び戻す（オプトイン/オフ設定なし）。いずれも中核タスク「続きから読む」の信頼を直接毀損する。
- **Major 帯はふりがな可読性（コントラスト/フォントスケール/TalkBack の三重失敗＝支援機能そのものの WCAG 違反）・トークン規律の穴（sp/dp/alpha の二重帳簿）・没入クローム（システムバー残留・消灯抑止なし・入場時既定表示）**に集積。
- 永続性・べき等性・SSOT の中核実装（PDF 読書位置復元、三層冪等化、CONFLATED 単一チャネル）は模範的で、欠陥は主に「周縁経路（Web 読書・発見系）と仕上げ層」に偏在する。

---

## 2. 指摘一覧（重大度降順・重複統合済み）

### Critical
| 重大度 | 出所 | 公理・法則 | 場所 file:line | 症状 | 修正案 |
|---|---|---|---|---|---|
| Critical | continuity | 公理14候補D 位置の意味論／公理6 永続性(層①) | NativeReadingScreen.kt:149-156 / BookshelfViewModel.kt:407-409 / ProgressDao.kt:18-20 | 章12を60%読了後、目次から章3を確認のため開くと自動保存が章3先頭へ即上書きし読みかけ先端が恒久喪失。戻す導線なし | navigateForward の eager saveProgress を削り debounce/ON_STOP フラッシュへ委譲。charter D の「続きに戻る」チップ(jumpOrigin 退避)＋滞留昇格 |
| Critical | portable **(2件束ね)** | 公理18 資産の端末独立性(C 3経路)／D 層別の逆進性 | AndroidManifest.xml:23 / res/xml / 設定 prefs | 端末を割った日に新端末で本棚が空。数年分の蔵書・位置・設定が全損。実体25MB制約を口実にメタデータ層まで一律に端末と心中させている | allowBackup=false 撤回→data_extraction_rules.xml の `<cloud-backup>` で database+datastore を include・books/ を exclude。位置/設定(数十KB)を自動復元 |
| Critical | notify | 公理13 沈黙が既定値(B②非時間性・C オプトアウト搾取・G 読書中割り込み) | NovelReaderApplication.kt:142,148-181 / NewEpisodeCheckWorker.kt:77-108 | ncode 紐付け本があるとアプリ未起動でも24h毎チェックが音付き(IMPORTANCE_DEFAULT)で「続きがN話更新」通知。オプトイン/オフ設定なし。同更新は本棚バッジで無音表示済み | scheduleNewEpisodeCheck と Worker/チャネルを撤去し更新提示を本棚バッジへ一本化。残すなら既定OFF＋明示トグル＋priming＋IMPORTANCE_LOW |

### Major
| 重大度 | 出所 | 公理・法則 | 場所 file:line | 症状 | 修正案 |
|---|---|---|---|---|---|
| Major | ssot | 公理8 嘘をつかない | ShelfItems.kt:108-111 | 最終章を1行スクロールした瞬間に進捗100%・朱印『了』・READINGフィルタから消え読了へ移動。今読んでいる本が「よみかけ」で見つからない | else 1f をやめ最終章スクロール中は<1f・READING側に留める。『了』は末尾到達フラグにのみ結ぶ |
| Major | a11y+d-type **(両面束ね)** | 公理11(b)チャネル等価/WCAG1.4.3 | Theme.kt:63,89,108 / RubyText.kt:87-95 / ChapterContent.kt:262,276 | ふりがなが背景に薄すぎ(実測 L 2.89 / S 2.53 / D 4.05:1、下限4.5未達)。furigana を最も要する弱視・漢字困難層に見えず本業と矛盾 | UnreadSeiji/InfoText と同手法で ruby 3値を色相維持のまま暗化し全面4.5:1へ |
| Major | a11y | 公理11 C 動的スケール/WCAG1.4.4 | RubyText.kt:90,101-106 | OS フォント拡大で親漢字は拡大しルビだけ据え置き→相対極小化・縦位置ズレ。低視力が頼るリサイズが効かない | 手計算をやめ sp 変換へ委譲(`(fontSize*ratio).toPx()`)、baseAscent も fontScale 反映 |
| Major | a11y | 公理11 F semantics(ルビ=著者指定の読み) | RubyText.kt:133,209,39 | TalkBack が当て字を著者読みで読み上げず親漢字の既定読みのみ(『魔剣(つるぎ)』→『まけん』)。視覚と音声が非等価 | 段落 BasicText に読み置換 AnnotatedString/VerbatimTtsAnnotation。既存 segment.reading 流用 |
| Major | continuity | 公理14候補E 再開ハブ(1タップ)/公理1 経路独立 | WebBookCard.kt:69-124 / BookshelfScreen.kt:216 / MainActivity.kt:189 | 進捗ありWeb作品の表紙タップが続きでなく「なろう目次」着地、再開は11sp/<48dpの小リンクへ格下げ。同型PDFカードと身振りの意味が割れる | 進捗あればWebも主タップを再開へ統一しPDFと揃える。目次は⋮へ降格(＝<48dp問題も解消) |
| Major | continuity | 公理14候補D(参照ジャンプで後退させない)／公理6 | DefaultBookRepository.kt:94-102 / WebReaderViewModel.kt:24-29 | (PLAUSIBLE) Web で51話まで読後、目次から10話を確認・退出すると再開ポインタが10話へ後退し先端喪失。※last-opened は意図的設計との係争あり | recordWebReadingEpisode を furthest-wins 化(episode>既存のみ更新)。次善は「続きに戻る」チップ |
| Major | persist | 公理6 永続性(構成変更) | WebReaderScreen.kt:62,65,152 / AndroidManifest.xml:32-41 | システム回転/ダーク/fontScale の Activity 再生成で WebView 破棄→入場時話を再ロードし前進分・スクロール・履歴が巻き戻る(副経路) | 再生成時 startUrl を DB 最終話へ差替。理想は saveState/restoreState を rememberSaveable(Bundle) で持ち回り |
| Major | ia | 15-§B 既定ソート=続きから読む | ShelfItems.kt:49 / BookDao.kt:14 | PDF取込のたび未読新刊(addedAt=now)が読みかけ本の上へ来て支配的タスク対象が先頭から押し下がる | 既定ソートを lastReadAt 主キーへ(未読は addedAt フォールバックで下)。意匠絡みは ADR 経由 |
| Major | ia | 15-§G②④ 既知アイテム高速路/束ね | BookshelfScreen.kt:442 / MainActivity.kt:181 / ShelfItems.kt:37 / BookEntity.kt | 数百冊で名前を知る本への蔵書内最短路が無い(蔵書内タイトル/作者フィルタも五十音索引も無く🔍は外部検索へ飛ぶ)。シリーズも束ねられずフラットな壁 | 蔵書内 LIKE フィルタ＋series を第一級エンティティ化し GROUP BY 束ね |
| Major | add | 公理12 最初の価値への段差禁止 | BookshelfScreen.kt:171-181,246-285 | 初回FABタップでファイルピッカー前にバッテリー最適化案内モーダル(長文手順＋『二度と表示しない』＋設定へ離脱)が割り込む。1冊も選ぶ前に説明+質問を最初の一手に挿入 | 初回addからダイアログを外し、実際に長い変換が背景へ回った/OEM kill 検知の文脈まで遅延 |
| Major | add+errtext **(2箇所束ね)** | エラー分類10-C 自動リトライ/公理10§C | PdfImportViewModel.kt:105-155 / NovelApiRepository.kt:102-132 | 一過性失敗(timeout/DNS/瞬断/単発5xx/429)が自動再試行なしで即Error表示し手動再タップを強いる(PDF DL・なろうAPI両系) | 単一集約点で retryable(IO/timeout/5xx/429)のみ指数バックオフ+Full Jitter 1-2回。429はRetry-After尊重、4xxは非リトライ維持 |
| Major | idempo **(2件束ね)** | 公理4 可逆性/UX16-H 確認<Undo | BookshelfScreen.kt:689-704 / DefaultBookRepository.kt:385-394 | 蔵書削除が即・完全に不可逆(DB行＋変換済HTML実体を一度に物理削除、取込元URI非保持)。唯一の防護の確認ダイアログも題名がインライン平文で不可逆コスト(要再変換)を過小表示 | 確認撤去→即カードを外し snackbar『元に戻す』の遅延削除へ。ウィンドウ経過後に DB＋File 確定 |
| Major | privacy | 公理15③ 透明化(見て消せる)/削除の完全性(層E) | WebReadingProgressDao.kt:10-21 / DefaultBookRepository.kt:87,94,380-395 | WebViewで読んだ位置履歴(ncode+話数+時刻)が本削除・カード除去でも消せず端末に永久残留(データ消去/アンインストールでのみ消える) | DAO に deleteByNcode 追加し removeWebNovel/deleteBook から相乗り＋起動時 orphan 掃除 |
| Major | privacy+measure **(両面束ね)** | 公理15 B②(ログは保存層)/22層§B 禁止段 | PdfImportViewModel.kt:92 | DL対象URL(ncode含む)と Content-Disposition(書名含みうる)を DEBUG ガードなしで logcat 出力。minifyEnabled false でリリースにも残る。CI 静的検査対象パターン | 変数展開を落とし定数ログ化、または `if(BuildConfig.DEBUG)` で囲む。looksLikePdf 真偽のみログ |
| Major | measure | 24層§E 回復パスの意図的発火 | NovelReaderApplication.kt:87 runStartupRecoveryOnce | 起動リカバリの統合順序(空pendingでも先に権限解放→partition→emitError→再投入)が退行しても緑のまま(発火テスト0件) | partition と順序を純関数抽出し JVM テストで固定。startForegroundService 発火は androidTest 少数へ |
| Major | measure | 24層C表#8 破損PDF隔離 interlock | DefaultBookRepository.kt:198 / PdfBookExtractor.kt:130 | 破損PDF抽出失敗時の隔離(outputDir削除・未insert・pending削除)が退行しても repository 層テストが緑。addBook は engine 注入不可の public 版のみ配線 | addBook を internal process(engine,…)経由へ差替可能化し、例外投げる fake engine で隔離を assert |
| Major | d-token | ADR0014§A 字面SSOT/公理5 意匠版 | ui/BookCard.kt:113,227,345 ほか ui/ 161ヒット | fontSize 直書き(9.5/10.5/…/16.5.sp)が Typography スロットを迂回。テーマ切替/一括調整に追従せず「35個の青」のタイポ版。check_design_tokens は色のみで drift 無警告 | 直書きを typography.* スロット経由へ。不足スロットを彫り sp 突合を機械検査へ追加 |
| Major | d-token | ADR0014§C 余白は離散スケールのみ | DiscoveryResultScreen.kt:247 / ChapterContent.kt・BookshelfScreen.kt 全域 | 余白が離散(4/8/16/24/40)外の任意値(11/5/14/18/20/26dp 等)で乱立。theme/ に Spacing トークン不在で宣言と実装が乖離 | 任意dpを最近接値へ丸め、SpacingTokens を彫り 25%則昇格審査を機械検査へ |
| Major | d-token+d-type **(両面束ね)** | ADR0014§D 意味色4.5:1/公理11 | ReadingErrorScreen.kt:42,48 / SearchConditionSheet.kt:365 / DiscoverySearchScreen.kt:327 / TOC.kt:149 | 意味を運ぶエラー/制約/状態テキストを `copy(alpha=0.75/0.7)` で本文以下に沈め実効色がテーマ地色で AA を割る(エラー詳細 L 2.56/S 2.31/D 3.18)。InfoText 暗化トークン裁定を alpha で打ち消す退行 | alpha 合成を削り InfoText/専用暗化シェードを素値で使う。トラック/scrim/disabled は対象外 |
| Major | d-motion | 08 禁止則③ overshoot/bounce 禁止 | Motion.kt:20 / BookCard.kt:171,309 / WebBookCard.kt:62,155 | 最頻操作の本棚カード全4種をタップ毎に書影が縮んで“ポヨン”と跳ねる(dampingRatio=0.6f underdamped)。標準リップルを既に持つのにスケールバウンス重畳 | dampingRatio を DampingRatioNoBouncy(1f)へ(1箇所修正で4使用不変)。徹底ならスケール自体を削除 |
| Major | d-chrome | Design/09 D システムバー契約/A 既定値=無 | NativeReadingScreen.kt:846-897 / MainActivity.kt:75 | 自作上下バーは退避しても OS ステータス/ナビバーが黒衣で残り読書画面が一度も「無」に到達しない(WindowInsetsController.hide 不在) | isChromeVisible から hide/show(systemBars())を同フレーム駆動＋BEHAVIOR_SHOW_TRANSIENT_BARS。版面 inset を IgnoringVisibility へ |
| Major | d-chrome | Design/09 A 既定値=無/B 入場規律 | NativeReadingScreen.kt:507 | 章に入った瞬間の既定が「chrome 表示」で能動的に消すまでバーが本文上に居座る。ChapterHeader が章題を担うため入場時にバーは不要 | 入場時 heightOffsetLimit で全退避し既定を「無」に |
| Major | d-chrome | Design/09 F 画面消灯 | NativeReadingScreen.kt / WebReaderScreen.kt 全域 | 長章を無操作で読むと OS 消灯タイマーで暗転し没入が強制終了(KEEP_SCREEN_ON 不在)。読書アプリで charter が「事実上の欠陥」と明記 | 読書コンポジション内で DisposableEffect により FLAG_KEEP_SCREEN_ON、onDispose で clear |
| Major | reach | 21-C 到達性の秩序/21-B 隅 | NativeReadingScreen.kt:920-928 | 読書中に触る表示設定(フォント/行間/テーマ/余白)の唯一の入口が上端右上隅の歯車。中央タップ→右上へ持ち替え→歯車がセッションで複利蓄積。標準の悪例に逐語一致 | 上端 actions のギア撤去、起動を中央タップ→下端シートへ。上端は Up＋章題のみ |
| Major | reach | 21-E 適応の一次元化(GridCells.Adaptive) | BookshelfScreen.kt:569 | 本棚が常時2列固定で窓幅に適応せず、タブレット横/折り畳み開/大画面分割(≥600dp)でカードが肥大し余白の砂漠に | GridCells.Fixed(2)→Adaptive(minSize)。スマホは影響0、≥600dpで自然多列化 |
| Major | critic | UX/06⑥ 一覧アイテムは1フォーカス単位 | BookCard.kt:315 ほか grid/Web/discovery カード | TalkBack が本棚1冊を題名/著者/バッジ/進捗/⋮の複数ノードに分割読み上げ、1冊移動に何度もスワイプ(最頻画面) | カード行に `Modifier.semantics(mergeDescendants=true)` を付与し1カード=1トラバーサル単位へ |

### Minor
| 重大度 | 出所 | 公理・法則 | 場所 file:line | 症状 | 修正案 |
|---|---|---|---|---|---|
| Minor | persist | 公理6 永続性 | DiscoveryResultScreen.kt:385 / DiscoveryViewModel.kt:263 | 「さらに読み込む」で深くスクロール中にプロセスdeath→再入場で積み上げページが消え先頭へ(回転はVM生存で保つ) | novels+paging も SavedStateHandle へミラー、代替は復帰時に先頭へ明示リセット |
| Minor | persist | 公理6(話内スクロール非追随) | WebReaderViewModel.kt:27 / WebReaderScreen.kt:65 | Web読書再開が話冒頭まで(話内位置非保存)。JS注入禁止(ADR0010/0012)で構造的に受容境界 | 「第N話のはじめから」等の表記で嘘のない期待に。ADR 0012 に明文化 |
| Minor | continuity | 公理14候補E/F 再開ハブ③『いつぶりか』 | BookCard.kt:54-96 / BookDao.kt:11-15 | 本棚カードが「N話・%」は出すが「◯日前」を出さず、数週間ぶりと昨日の続きが区別できない | lastReadAt を READING カードに相対時刻で静かに添える |
| Minor | add+notify **(両面束ね)** | 公理12 11-E-2/公理13-F§120 priming | BookshelfScreen.kt:152-165 | 初回取込時に通知の用途説明(priming)を挟まずシステム権限ダイアログを直接表示 | launch 前に理由ダイアログ(OK/今はしない)を挟む。要求は取込開始文脈まで遅延も可 |
| Minor | add | 10-H 資源は起きる前に測る | DefaultBookRepository.kt:143-204 | 取込前に空き容量を計測せず、逼迫時は抽出途中で ENOSPC 失敗し時間と一時ファイルを浪費 | 抽出前に usableSpace/getAllocatableBytes で必要見込み下回れば開始せず具体値提示 |
| Minor | errtext | 08§C 内部事情の翻訳 | NativeReadingScreen.kt:442,275 → ReadingErrorScreen.kt:45 | 章/目次読取例外時に生例外(絶対パス『/data/user/0/…ENOENT』)がそのまま読書エラー画面へ | `e.message?:` を捨て固定文言、原因 e は Log.e へ退避 |
| Minor | errtext | 08§D 次の一手(持続性失敗に無効な行動) | PdfProcessingService.kt:376 → BookshelfScreen.kt:229 | パスワード付き/破損PDFの失敗にも『再試行』が出て、押しても同一URIが決定的に同じ失敗を再走 | Encrypted/Corrupted は retryUri=null で『閉じる』のみ。容量不足は再試行を残す |
| Minor | errtext | 08§B① 行動不能な内部理由は伏せる | PdfProcessingService.kt:225 | 「変換が時間制限により中断」の『時間制限により』は FGS 実行時間上限で読み手に無関係 | 「変換が中断されました。アプリを開き直すと再開します」へ短縮 |
| Minor | ia | 15-§C 一語一義 | DiscoveryResultScreen.kt:146 / DiscoveryHomeScreen.kt:122 / BookshelfScreen.kt:445 / DiscoverySearchScreen.kt:171 | 発見エリアが「見つける/探す/検索/発見」の4語で呼ばれ、『発見に戻る』『小説を探す』の着地が「見つける」画面 | 画面タイトルを正本に語を統一、用語辞書1枚化 |
| Minor | ia | 15-§G 共有地の悲劇 | BookshelfScreen.kt:582-643,763 | 本棚先頭に「見つける導線帯」＋フィルタ行が居座り支配的タスクの先頭本が1-2行下がる。帯は top-bar 🔍と自認重複 | 帯撤去し発見入口を🔍へ一本化。ただしモック正本(bookshelf-fusion-D)由来につき ADR0005 経由で判断 |
| Minor | ia | 15-§F スコープ変更は先出ししない | DiscoverySearchScreen.kt:270-321 | 検索実行前に「タイトル/キーワード/作者/あらすじ」範囲チップを先出し。範囲変更の需要は多すぎ/0件で初めて生まれる | 既定(タイトル)で即検索させ範囲チップは折り畳み or 結果画面『条件を変更』へ。意匠はモック由来の可能性 |
| Minor | ia | 15-§E 目次は既読を視覚区別 | NativeTableOfContentsScreen.kt:216-246 | 目次が現在章のみ強調し既読(index<currentIndex)を淡色化せず「あとどれだけ」の見当識が弱い | 現在章より前をグレー+ウェイトで既読表示(currentIndex から導出可・データ追加不要) |
| Minor | ia | 15-§G③ 各値に件数を添える | BookshelfScreen.kt:726-746 | 状態チップ(よみかけ/未読/読了)に件数がなく0件分類も押せて空表示に落ちる(空文言で緩和済み) | 0件チップを dim/非表示、または各チップに件数を添える |
| Minor | gesture | 公理17 C-1 語彙の実領域一致 | NativeReadingScreen.kt:960,781-790 | chrome復帰ヒント文言が「画面中央をタップ」だが実タップ領域は全面(fillMaxSize)。ヒントは通算初回のみ・再表示メニュー無し | 文言を「画面をタップでメニュー表示」へ(実領域=全面と一致) |
| Minor | settings | 19-B/H 宣言を未宣言へ戻せる | ReadingSettingsSheet.kt:121 / MainActivity.kt:127 / Theme.kt:24 | 一度テーマチップを押すと二度と「自動追従(未宣言)」へ戻せず、夜に暗くなる既定挙動が消える | チップ列に「システムに従う」を追加し選択時は `remove("reading_theme")` でキー削除 |
| Minor | notify | 公理13-D §86 foreground判定 | PdfProcessingService.kt:355-361,514-524 | 本棚を前面で見て取込待ち中に変換完了すると、本棚に本が現れる上に通知トレイにも「変換完了」が積む二重報告 | ProcessLifecycleOwner で foreground 時は完了通知をスキップし本棚の反応表示に委ねる |
| Minor | notify | 公理13-D §87 stale 通知の取り下げ | PdfProcessingService.kt:514 / NewEpisodeCheckWorker.kt:106 | 通知をタップせずアプリで当該本を読んでも通知が未読の顔で残る(setAutoCancel のみ) | deep link 着地/該当本を開いた時点で cancel(NOTIFICATION_ID / tag) |
| Minor | notify | 公理13-E §2 setOnlyAlertOnce | PdfProcessingService.kt:467-489 | 取込進捗通知が onProgress 毎に%不変でも高頻度で再 notify(IMPORTANCE_LOW で音は無く描画コストのみ) | setOnlyAlertOnce(true)＋progress 変化時のみ notify |
| Minor | portable | 公理18 D 再結合キー | BookEntity.kt:10,28 / DefaultBookRepository.kt:233 / ProgressEntity.kt:8 | メタデータ復元後に同PDFを入れ直しても昨日の位置が自動で戻らない(htmlDirPath=端末絶対パス・bookId=端末ローカルUUID)。※復元経路が出来て初めて顕在化する下流 | 復元時 htmlDirPath は bookId から再導出。contentSha256 を再結合キーへ昇格 |
| Minor | a11y | 公理11 F(d) liveRegion/WCAG4.1.3 | NativeReadingScreen.kt:793-839 | 章パースの失敗/成功、継続カード出現がフォーカス外の TalkBack へ告知されない | ReadingErrorScreen/継続カードに `liveRegion=Polite` |
| Minor | a11y | 公理11 F(d) heading() | ChapterContent.kt:174-183,248-253 | 章タイトル・前書き/後書きラベルが視覚は見出しなのに heading() 無しで見出しジャンプ不可 | 両 Text に `Modifier.semantics{ heading() }` |
| Minor | evolve | 公理23-F 全開始点の移行テスト | MigrationTest.kt:40-42 / AppDatabase.kt:58-66 | データ詰替を伴う唯一の移行 MIGRATION_3_4 に「データ入り」回帰テストが無く、退行を実機投入前に検出できない | v3実機が無いと確証できるなら 3_4 と schema をチェーンごと削除し floor を v7 へ。確証不能なら migrate3to7 データ保存テスト追加 |
| Minor | evolve | 公理23-E 設定は意味を保存/19-I schemaVersion | MainActivity.kt:88,128 / SearchHistoryStore.kt:80-82 | prefs/DataStore に schemaVersion なく、テーマは enum 定数名の生保存。将来の enum 改名で選択が黙って既定へ差し替わる(予防的) | settings_schema_version を1つ置き、enum 改名時は意味保存マッピング移行 |
| Minor | d-token | charter(a) theme/外 Color(0x…)禁止 | ShioriCover.kt:279 | ship-UI に生 ARGB リテラル1件(栞書影5%内枠)。テーマ改訂でこの罫だけ取り残されうる(isDark 分岐で実効追従は済) | cs.onSurface/ヘアライントークンを正本に名前付き alpha 定数で1回宣言し2分岐を畳む |
| Minor | d-token | KB03§4 primitive 直参照 | RubyText.kt:243,264,279 | @Preview 3箇所が `Color(0xFF8B96A0)` を写経しトークン改訂でプレビューだけ旧値に | ReadingTheme.LIGHT.colors.ruby 参照へ(値の単一化) |
| Minor | d-motion | 08-C enter/exit・禁止則② | BookshelfScreen.kt:519-522 | バナー入退場が同一時間・同曲線(既定spring)で exit が enter より短くなく値も Motion.kt 非経由 | Motion トークン化 spec で exit を enter より短く(reveal250/dismiss150) |
| Minor | d-motion | 08 禁止則② トークン経由 | NativeReadingScreen.kt:946 / NovelDetailScreen.kt:186 | 復帰ヒント fade・詳細バー題字 fade が animationSpec 未指定=既定spring で Motion.kt 非経由(フェード類型自体は妥当) | tween(250, linear) 相当の crossfade トークンを渡し一元化 |
| Minor | d-chrome | Design/09(a) 経路一貫性/Jakob | WebReaderScreen.kt:84-104 vs NativeReadingScreen.kt:781 | 同じ「読む」で PDF は中央タップ没入、なろうWeb は上部バー常設で没入もトグルも無く chrome 挙動が経路で別物(媒体差 ADR0012 で大半正当) | 読む面の chrome 規律を最低限近づける。native 側 A/D/F 是正が先 |
| Minor | d-type | Design/10§9 alpha でなく専用シェード | NcodeLinkSheet.kt:148,357,392 | placeholder(『作品名を入力』)・無効時ボタン文字を `copy(alpha=0.6)` で沈める(placeholder/inactive は WCAG 概ね対象外＝コード衛生) | alpha を削り階層段の専用シェードトークンへ |
| Minor | reach | 21-D 版面の自律性(字数×フォント) | ChapterContent.kt:142,73 | 本文最大幅が dp 定数(600)でフォント非追従(18sp≒33字/24sp≒25字/14sp≒43字)。行間 em も行長非連動 | 600.dp を『~40*fontSize』字数基準へ、行間 em も行長入力の関数へ(危険域60字超には未達で害限定的) |
| Minor | critic | UX/06㉑ 入力欄はラベルを持つ | DiscoverySearchScreen.kt:217 / NcodeLinkSheet.kt:121,337 | 検索欄が恒常ラベルを持たず入力後は placeholder も消え何の欄か手掛かりなし。TalkBack で欄名が読まれない | 恒常ラベル(画面内見出し or `contentDescription="検索語"`)を与え placeholder は例示専用へ |

---

## 3. 要検証リスト（実機/静的で確定できない項目＋検証手順）
| 出所 | 項目 | 検証手順 |
|---|---|---|
| nav | 作品詳細の←が到達経路で別着地(発見ホーム発↔結果一覧発)。←の意味が画面間で不統一 | 実機で「発見ホーム→詳細」「結果一覧→詳細」を辿り、視覚同一の←の着地差が混乱を生むか目視。統一裁定なら Result 同型の固定Upへ揃える |
| persist | 回転直前の最終スクロールデルタ(≤400ms)が getProgress 読出に間に合わず巻き戻るレース | 実機で章を読みつつ即回転を反復し最終位置が保存されるか。発現すれば ON_STOP フラッシュを suspend 完了待ちに |
| ssot | 章数が「chapファイル数」と「index目次<li>数」の二経路で導出(現状 HtmlExporter がロックステップ生成で一致) | `#chapファイル == #tocエントリ` の不変条件を testDebugUnitTest で固定し将来の静かな divergence を検知 |
| add | 取込画面の WebView 初期ロード中にローディング表示が無く白画面露出 | 実機・低速回線で入室直後の白画面露出時間を実測。長ければ onPageStarted/Finished でフラグ持ち既存スピナー流用 |
| gesture | 下端 BottomAppBar(章送り)がジェスチャナビ帯と近接し誤発火 | 3ボタン式/ジェスチャ式ナビ双方の実機で下端ジェスチャ帯に入らないか。移動量ゼロのタップゆえ物理衝突は本質的に低い |
| ia | 数百話のなろう作品で目次が部・編で畳めずフラット | 実PDF→HTML が部構造を出すか実データの階層有無を確認。あれば見出しで畳む/無ければ100話単位(TocEntry 拡張要) |
| privacy | ncode 紐付け本があると日次で ncode 群を syosetu へ送信、停止トグル無し・既定ON | プロダクト判断: 背景ポーリングを既定OFFのオプトインに。Data safety へ「紐付け作品IDを送信」記載・Manifest 用途コメントと一致 |
| a11y | 没入バー退避時 TalkBack が戻る/目次/前後章へ到達可能か(graphicsLayer は a11y ツリー保持の公算大) | 実機 TalkBack で退避後にスワイプ走査で各ボタンへ到達できるか。到達可なら中央タップ復帰の音声等価物(customActions)追加で足りる |
| reach | 形態遷移(回転/折り畳み)で段落内 px オフセットが指す行がずれる | 実機回転テストで長段落の途中を読み、復帰後に何行ずれるか。ずれれば scrollOffset を文字オフセット基準へ |
| evolve | v1/v2 スキーマの実機が実在すると移行未発見で起動時クラッシュ(fallback不在) | 過去に v1/v2 で実機投入した開発端末の残存を人間知識で確認。残存し得るなら MIGRATION_2_3 補完 or 正直なエラー経路 |
| measure | 大PDF/10倍蔵書/長時間送りのメモリ・フレーム予算が漸進劣化しても機械検出できず、INTERNET 無しで出荷後テレメトリも不能 | Macrobenchmark モジュール新設、大/中/小/病的PDF＋10倍蔵書で取込・起動・章ジャンプを TraceSectionMetric P90/P99 実測し §F 予算を assert 化 |
| d-token+d-motion **(両面束ね)** | Motion.kt:28 MotionDurationProgress=400ms が 350ms 上限超(④進行=免除余地大) | 意匠意図の裁定: 進行 smoothing は上限外とコメント/ADR に明記して免除正本化、上限内で足りるなら300へ |
| d-type | ルビの掛け(隣接漢字への被り)・隣接ルビ間アキ制御が無い | 長ルビ(1漢字4モーラ等)実データで実機目視。被れば隣接字種で掛け制限＋最小アキ挿入 |
| d-type | 大フォント×広余白で1行字数が極端に減る(既定18sp≒18字/24sp≒11字) | 実機で各設定の体感リズムを確認。余白スライダー上限低下 or 大フォント時の余白自動縮小 |
| d-chrome | 横向き/サイドノッチ端末で行頭・行末がカットアウトに欠ける(版面横 inset に displayCutout 無し) | ノッチ端末を横向きで目視。欠ければ版面横 padding に displayCutout/safeDrawing 合成 |
| critic | 章題ブロックの余白逆転(top14<bottom26)で近接則上、章境界の上方に寄って見える | 章オープナー意匠として意図的か確認。意図的なら実装コメント/ADR に記録、in-flow 見出し扱いなら top>bottom へ |

---

## 4. 人間テスト送り（理解・誤解系。17プロトコルでタスク化）
| 出所 | 検証タスク |
|---|---|
| nav | 変換完了通知等で読書画面へテレポート着地した直後、上部バーに書名が無く章題が『第1話』等の汎用文言のとき「どの本か」を特定できるか。特定に迷えば読書 chrome に書名を副行/目次見出しで常設 |
| ia | 本文入口が複数(目次/本棚カード/deep link/navHistory)ある。各入口→本文の位置/スワイプ/継続挙動が同一か実機で確認(読書継続性レーンと重複可) |
| gesture | 2週間後・無説明で「ネイティブ読書」と「Web読み」を交互に触らせ、別の操作言語(全面タップトグル↔常時バー)の habit-transfer が記憶を混乱させるか(Web は別モード=許容例外の可能性大) |
| settings | 「この本の読書画面」で開く表示設定でダークを選ぶと本棚も全書籍も暗くなる。ユーザーが「この本だけ変えたつもり」の誤解を持つか。持てば見出しを「全書籍の表示設定」等でスコープ予告 or 本ごと上書き追加 |
| gesture | 2週間後・無説明で「メニューを出して」と依頼し、通算初回のみのヒントで消えた chrome トグル(中央タップ)を再発見できるか(上スクロール復帰の可視代替は有り) |
| d-motion | 開発者オプション「アニメスケール0」で ①カード押下の残留バウンス無し ②バーの settle 瞬時スナップ(onPostFling coroutine が MotionDurationScale を運ぶか) ③バナー/ヒント即時化 を目視。追従しない箇所のみ isReducedMotion 分岐 |
| d-chrome | 少し上スクロールで上下バーが自動復帰し本文上端を覆う挙動(enterAlways 慣習)を惜しむ声が出るか。出れば自動表示を削り再表示を中央タップへ一本化 |

---

## 5. 良い点（守れている公理）
1. **公理6 永続性の模範** — NativeReadingScreen.kt:133-183,529-561: 章・章内スクロール・章⇄目次の遷移履歴・シート開閉を rememberSaveable(bookId キー)＋DB 正本＋ON_STOP 即時フラッシュで回転/ダーク/プロセスdeath/切替を跨いで保存し、復元は章一致時のみジャンプ注入。BookshelfScreen.kt:203-207 のカード1タップ→保存章＋スクロール復元と合わせ中核タスク「続きから読む」を堅牢に満たす。
2. **公理3 べき等性の三層防御** — PdfProcessingService.kt(ActiveUriTracker で同一URIキュー弾き)＋BookEntity contentSha256(内容指紋)＋title/author 照合で、連続追加・DL連打でも二重変換/二重登録にならず ActiveUriTrackerTest/BookRepositoryTest で機械検証済み。
3. **公理5 SSOT** — BookshelfViewModel.kt:205-226 の CONFLATED 単一 progressChannel が「最後のユーザー操作」を確実に最後へ着地(旧2チャネル競合の巻き戻りを排除)、ShelfItems.kt:101-137 の readingStatusFor が %/朱印/状態フィルタを単一計算から導出。※単一ソース側の 1f 過大主張(§2 ssot Major)は別途是正要。

---

## 6. 統合メモ
**束ねた重複・両面指摘の対応:**
- **Critical portability を2件→1件**: portable「allowBackup=false で全損(C 3経路)」と「層別未検討(D 逆進性)」は同一 allowBackup=false が根で、finder 自身が「1件の根に束ねるのが妥当」と明記。1 Critical に統合。
- **両面束ね(UX+Design 同一構造欠陥)**: ①ルビ低コントラスト = a11y(WCAG1.4.3)＋d-type(Design/05§2)→1 Major。②意味テキストの alpha 沈め AA割れ = d-token(ADR0014§D)＋d-type(Design/10§9)→1 Major。③PdfImportViewModel:92 ログ露出 = privacy(公理15 B②)＋measure(22層§B)→1 Major。④Motion 400ms上限 = d-motion(禁止則①)＋d-token(KB03§5)→1 要検証。いずれも重大度は高い方を採用。
- **同一根本原因の束ね**: ⑤一過性ネットワーク失敗の自動リトライ欠如 = add(PDF DL)＋errtext(なろうAPI)の2サブシステム→1 Major(高い方)。⑥蔵書削除の可逆性 = idempo Major(Undo無し)＋Minor(確認ダイアログ弱)→1 Major。⑦通知権限 priming 無し = add(11-E-2)＋notify(§120)同一箇所→1 Minor。⑧復帰ヒント/詳細バー題字の fade 非トークン化(d-motion)を1 Minor に集約。
- **ルビ3欠陥は分離維持**: コントラスト/フォントスケール非追従/TalkBack読み上げは根本原因が異なる(色 vs スケール計算 vs semantics 配線)ため独立3 Major。
- **件数**: Critical 3・Major 26・Minor 32(束ね後)。ふりがな(可読性の要)に Major が3本集中、トークン規律(sp/dp/alpha)に3本、没入クローム(d-chrome)に3本。
- **REFUTED(棄却)=1件**: notify「お知らせ系チャネルが無い」との付随主張は NEW_EPISODE_CHANNEL_ID『新着話のお知らせ』(IMPORTANCE_DEFAULT)が実在するため棄却(＝むしろ Critical notify が問題視する再訪リマインダ用チャネル本体)。当該 finding の核(F§119 タイミング・F§121 グレースフルデグレード)は良い点として維持。併せて ia「検索経由 vs 目次経由の本文分岐」は FTS 不在で違反不成立(REFUTED相当)だが、残る「多入口の本文パリティ」を人間テスト送りへ横移動済み。
- **層③(配色/角丸/アニメ趣味)は対象外**として除外済み。d-motion/d-chrome/d-type/d-token の指摘はいずれもトークン規律・可読性・没入契約という機能層の欠落であり趣味判断ではない。


---

## B. 全指摘の詳細 evidence（敵対的検証ノート付き）

> 各指摘の完全な根拠と検証者の裁定理由。handover の1行サマリを追う際の一次情報。良い点42件は末尾。


### Critical

#### [continuity] 公理14候補D 位置の意味論(参照ジャンプで自動保存を動かすな)・公理6 永続性(層①)  (CONFIRMED)
- **場所**: `NativeReadingScreen.kt:149-156 / :299 / BookshelfViewModel.kt:407-409 / ProgressDao.kt:18-20`
- **症状**: 章12を60%まで読んだ後、目次から章3を確認のつもりで開くと即座に自動保存位置が章3先頭へ上書きされ、読みかけの先端(章12/60%)が恒久的に失われる。戻す導線も無い。
- **根拠(検証済)**: NativeReadingScreen.kt:149-156 navigateForward=`{ target -> navHistory = pushNavHistory(navHistory, target); if (target != "index.html") { lastChapterFile = target; viewModel.saveProgress(bookId, ChapterFilename(target)) } }`。:299 `onSelectChapter = navigateForward`（目次の章選択も同経路）、:352 `onNavigateTo = navigateForward`。BookshelfViewModel.kt:408 `progressChannel.trySend(ProgressEntity(bookId.value, filename.value))`＝scrollIndex/offset は既定0。ProgressEntity は `@PrimaryKey val bookId`＝本ごと1行、ProgressDao.kt:18-19 `@Insert(onConflict = OnConflictStrategy.REPLACE)`＝丸ごと上書きで furthest/max 保護なし。BackHandler(:164-171)は明示的に saveProgress を呼ばず、復元は LaunchedEffect(bookId)(:176-183)で入場時一度きり。Up退出でも戻し保存無し。charter 13 §D 適用例『しおりの場所を確認しに行く|悪い:自動保存位置がしおり位置で上書きされ続きが行方不明』＝公理6違反(層①)に直撃。『元の位置に戻る』チップも存在せず。
- **修正案**: 参照ジャンプ(目次選択・前後章)を自動保存の即時対象から外す。charter D の解に沿い『続きに戻る』チップ(jumpOrigin を SavedStateHandle 退避)を出し、滞留(数ページ読了)で新『今ここ』へ昇格させる。最小手当て＝navigateForward の eager saveProgress を削り、debounce(:529-535)/ON_STOP(:550-561)フラッシュに保存を委ねる。
- **検証ノート**: 行番号ほぼ厳密一致(VM saveProgress のみ 402-406→407-409 の軽微ズレ)。charter が名指しする flagship 違反例で、単一行REPLACE＋furthest不在を静的に確認。実損は瞬時かつ不可逆＝Critical妥当。

#### [notify] 公理13 沈黙が既定値（B②時間性・C§67-69オプトアウト搾取・例外§157再訪リマインダ・G読書中割り込み）  (CONFIRMED)
- **場所**: `NovelReaderApplication.kt:142,148-163,176-181 / NewEpisodeCheckWorker.kt:77-108`
- **症状**: ncode紐付け済みの本があると、アプリ未起動でも24hごとの自動チェックが『(書名) 続きが N 話更新されています』を音付き(IMPORTANCE_DEFAULT)で通知しユーザーを読書へ呼び戻す。オプトインもオフ設定も無い。
- **根拠(検証済)**: NovelReaderApplication.kt:142 `scheduleNewEpisodeCheck()` は onCreate 内で無条件呼び出し（ユーザー操作の返答ではない）。:149-160 `PeriodicWorkRequestBuilder<NewEpisodeCheckWorker>(24, TimeUnit.HOURS)…enqueueUniquePeriodicWork(…ExistingPeriodicWorkPolicy.KEEP…)`＝毎起動で維持される cron。:176-179 `NotificationChannel(NEW_EPISODE_CHANNEL_ID, "新着話のお知らせ", NotificationManager.IMPORTANCE_DEFAULT)`＝音+ヘッドアップ。NewEpisodeCheckWorker.kt:100 `.setContentText("続きが \${alert.newCount} 話更新されています（全\${alert.totalAllNo}話）")`、:87-90 タップ着地は MainActivity→当該本の読書画面（継続カード＝なろう導線）。:80-83 showNotification は POST_NOTIFICATIONS の有無だけ確認し foreground/読書中判定は無い。オプトイン/オフ設定の不在も確認: 設定画面は ReadingSettingsSheet.kt のみで通知トグル無し、scheduleNewEpisodeCheck を preference でゲートする分岐も cancelUniqueWork も全ツリーに存在せず（grep 0）。同じ更新は BookshelfScreen.kt:101 newEpisodeNovelMap『続きあり』バッジで次回起動時に無音表示される＝軸②に該当。Worker 自身の :59-60 コメントも『新着通知は1日遅れても実害がない』と非時間性を認めている。
- **修正案**: scheduleNewEpisodeCheck()（NovelReaderApplication.kt:142,148-163）と new_episode_channel・Worker を撤去し、更新提示は本棚『続きあり』バッジ(newEpisodeNovelMap)に一本化する。残すなら既定OFF専用チャネル＋明示トグル＋priming 同意＋最低 IMPORTANCE_LOW 無音化で読書中に鳴らさない。
- **検証ノート**: コード実体は全て主張どおり実在・Critical 成立。唯一の緩和論点＝ncode 紐付けは弱い興味シグナルにはなるが、Worker 自身が非時間性を認めており軸②で in-app 提示が既存する以上、公理13の『沈黙が既定値』の核心（非時間性の更新は通知でなく次回起動時のアプリ内表示）に対する層①違反として Critical を維持。

#### [portable] 公理18候補 資産の端末独立性（C 可搬性の3経路）  (CONFIRMED)
- **場所**: `AndroidManifest.xml:23 / res/xml (file_paths.xml のみ) / grep 全空`
- **症状**: 端末を割った/水没/買い替えた日に新端末で本棚が空。数年分の蔵書・読書位置・表示設定が丸ごと消え戻らない。公理18『新端末で続きから読めるか？』への答えが全否定。
- **根拠(検証済)**: AndroidManifest.xml:23 `android:allowBackup="false"`（①自動バックアップ全体を opt-out）。res/xml 実体は file_paths.xml 1本のみで data_extraction_rules.xml 不在＝<device-transfer>/<cloud-backup> ルール未定義（③D2Dも allowBackup=false で参加不能）。②手動: `grep ACTION_CREATE_DOCUMENT|CreateDocument|BackupManager|bmgr app/src/main` は全て0件。設定は MainActivity.kt:83 `context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)` に置くのみでバックアップ対象外。=蔵書・位置・設定が端末外へ出る経路が皆無。
- **修正案**: allowBackup="false" を撤回し data_extraction_rules.xml の <cloud-backup> を database+datastore のみ include（books/ は exclude）＝メタデータ層(位置・設定)を全ユーザーへ自動復元。手動SAFエクスポート/D2D同梱は次善だが、最低1経路を通さない限り公理18違反は解消しない。
- **検証ノート**: 3経路の不在を4点（manifest:23 / res/xml 実ファイル一覧 / 4語 grep 0件 / prefs 保存箇所）すべてコード実体で確認。層①（端末独立で資産が生き残るか）違反かつ端末喪失時に実害（全損）＝Critical で確定。


### Major

#### [a11y] 公理11 チャネル等価性 (b) / WCAG 1.4.3 コントラスト  (CONFIRMED)
- **場所**: `Theme.kt:63,89,108 (ruby tokens) + RubyText.kt:87-95 (paint)`
- **症状**: ふりがな(ルビ)が背景に対し薄すぎ、furigana支援を最も必要とする弱視・老眼・漢字困難ユーザーに見えない。アプリの本業(読む困難の支援)と正面から矛盾。
- **根拠(検証済)**: Theme.kt:63 `ruby = Color(0xFF8B96A0)`／:89 `ruby = Color(0xFFA3906A)`／:108 `ruby = Color(0xFF6E7984)`。RubyText.kt:89-90 `color = rubyColor.toArgb(); textSize = style.fontSize.value * rubyFontSizeRatio * density`(既定 rubyFontSizeRatio=0.5f=本文の半分≒9sp の小テキスト)。独立計算した実測コントラスト: LIGHT 8B96A0/FBFAF8=2.89:1／SEPIA A3906A/F2E7CE=2.53:1／DARK 6E7984/14171C=4.05:1 — 3テーマとも小テキスト下限4.5:1 未達(finder の数値と完全一致)。対して本文は 15.81/10.30/11.21:1 で余裕合格＝ルビだけ未達。自己矛盾も実在: Color.kt:26-42 で UnreadSeiji(#50685C)/InfoText を『意味を運ぶ文字は WCAG 4.5:1 が最低線(ADR 0014-D)』として色相維持のまま暗化しているのに、ルビ3トークンは同じ審級から漏れている。
- **修正案**: UnreadSeiji/InfoText と同手法で ruby 3値(Theme.kt:63/89/108)を色相・彩度維持のまま暗化し、素地/カード/セピア/ダーク全面で4.5:1を満たす最小値へ差し替える(新トークン不要・既存3値の置換で足りる)。
- **検証ノート**: 色定数・paint 経路・コントラスト値をすべて現物確認し独立再計算で一致。ルビは著者指定の読み=意味を運ぶ小テキストのため4.5:1が正しい下限。既存の4.5:1審級(ADR 0014-D)からの適用漏れという自己矛盾も Color.kt:26-42 で裏取り済み。Major 維持(本文は読めるため Critical=全損には至らないが、支援機能そのものの WCAG 違反)。

#### [a11y] 公理11 C 動的テキストスケール / WCAG 1.4.4 Resize Text  (CONFIRMED)
- **場所**: `RubyText.kt:90 (ルビpx) ・:101-106 (baseAscent)`
- **症状**: OSのフォントサイズ拡大でふりがなだけ拡大せず、親漢字は拡大するためルビが相対的に極小化し縦位置もずれる。低視力ユーザーが最も頼るリサイズが furigana に効かない。
- **根拠(検証済)**: RubyText.kt:90 `textSize = style.fontSize.value * rubyFontSizeRatio * density`／:103 `textSize = style.fontSize.value * density`(baseAscent)。いずれも `LocalDensity.current.density`(:80)=表示密度のみで、fontScale を掛けていない。一方 親文字は :110-111 `BasicText(text = annotated, style = style)` で fontSize を sp のまま渡すため sp→px 変換時に fontScale が乗る。結果 fontScale=2.0 で親は倍化・ルビは据え置き=相対半分。さらに縦アンカーの baseAscent も fontScale 非適用のため、fontScale 適用済みの実 baselineY(TextLayoutResult 由来)と不整合になりルビが上下にずれる。sp の生数値×density の手計算経路。
- **修正案**: 手計算をやめ標準 sp 変換へ委譲: `with(LocalDensity.current){ (style.fontSize * rubyFontSizeRatio).toPx() }` 等で fontScale 込み変換し、baseAscent も同様に fontScale を反映する。
- **検証ノート**: density(表示密度)と fontScale が別フィールドであること、親が sp 経由で fontScale を適用することを踏まえ、ルビ側だけ非適用=バグを確認。Finding 0(低コントラスト)と複合し furigana が低視力ユーザーに二重で失敗する。Major 維持。

#### [a11y] 公理11 F semantics設計 (急所②『ルビは著者指定の読み』)  (CONFIRMED)
- **場所**: `RubyText.kt:133 (ルビ Canvas 描画) ・:209/:39 (独自アノテ)`
- **症状**: TalkBack が当て字を著者指定の読みで読み上げず、親漢字の既定読みしか音声チャネルに届かない(『魔剣(つるぎ)』→『まけん』誤読)。視覚(ルビ)と音声が等価でない。
- **根拠(検証済)**: ルビ読みは :133 `canvas.nativeCanvas.drawText(info.rubyText, info.centerX, y, rubyPaint)` の Canvas 重ね描きのみ。:209 `addStringAnnotation(RUBY_TAG, segment.reading, start, end)` は :39 `private const val RUBY_TAG = "ruby"` の独自文字列タグ=TtsAnnotation(VerbatimTtsAnnotation)ではないため TalkBack は無視。:208 コメントも『アノテーションも記録（将来の用途向け）』＝TTS 未配線を自認。親文字は :110-111 `BasicText(text = annotated)` が semantics に載るため『無音の本』は回避(良い基盤)だが、ChapterContent.kt:259-278 の RubyText 呼び出しにも読み替え semantics は無い。
- **修正案**: 段落 BasicText に `Modifier.semantics{ text = <ルビ置換後 AnnotatedString> }` を与えるか、各ルビ範囲へ VerbatimTtsAnnotation で読みを付す。既にビルド済みの segment.reading(:205-209)を流用でき新規データ不要。
- **検証ノート**: 独自タグが TtsAnnotation でない事実・base text が semantics に載る事実を現物確認。実害は当て字/難読漢字に集中し base の漢字は TTS が正しく読むケースが多いが、なろう系は技名等の当て字ルビが頻出のジャンルのため損失は無視できず Major 維持。WCAG に明示 SC は無く charter F急所②級のエンハンスだが等価性欠落は実在。

#### [add] 公理12 最初の価値への経路に価値と無関係な段差を置かない（11-B/A）  (CONFIRMED)
- **場所**: `app/src/main/java/com/novelreader/ui/BookshelfScreen.kt:171-181, 246-285`
- **症状**: 初回のFABタップ（空棚のCTA含む）で、ファイルピッカーの前にバッテリー最適化の案内モーダル（長文手順＋『二度と表示しない』チェック＋『設定を開く』）が割り込む。まだ1冊も選んでいない段階で説明ページ・設定質問を最初の一手に挿入している。
- **根拠(検証済)**: onFabClick(:173-174) `val needsWarning = !batteryDialogDismissed && !pm.isIgnoringBatteryOptimizations(context.packageName)`。新規インストールは batteryDialogDismissed=false・電池最適化除外=false（既定）→ needsWarning=true。:175-180 `if (needsWarning) { doNotShowAgain = false; showBatteryOptDialog = true } else { launchPdfPicker() }`＝真枝で launchPdfPicker を呼ばずダイアログを出す。ダイアログ(:264)title「バックグラウンド処理について」・(:267)長文手順・(:270-274)Checkbox『二度と表示しない』・confirmButton(:279)『設定を開く』→ dismiss(true)(:253-257) `startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)...)` でアプリ外へ離脱しピッカー未起動。合格ライン『決定は追加/ファイル選択の2つ・説明0・質問0』に反する。
- **修正案**: 初回の add タップ前からこのダイアログを外す。バッテリー最適化案内は価値到達後・文脈まで遅延（公理12 B(c)(d)）＝実際に長い変換がバックグラウンドへ回った/OEM kill 検知の文脈でのみ出す。少なくとも1冊目取り込み完了まで抑止。
- **検証ノート**: コード実体・分岐条件とも主張どおり確認。1点だけ補足＝ダイアログには dismissButton『このまま続ける』(:282)→ dismiss(false)(:258-260) `launchPdfPicker()` があり、ここを押せばピッカーへ到達する（finder の『行き止まり』は confirmButton『設定を開く』枝に限定した正確な記述）。よって全経路が dead-end ではないが、初回の一手に説明+質問の段差を挿入している核心の違反は成立＝Major 維持。層①（最初の価値への経路）への顕著な段差。

#### [add] エラーの分類と回復（10-C 自動リトライ）／charter(e) 一過性を即エラー=Major  (CONFIRMED)
- **場所**: `app/src/main/java/com/novelreader/viewmodel/PdfImportViewModel.kt:105-155`
- **症状**: なろう縦書きPDFのDLが通信の一過性失敗（timeout/DNS/一時的5xx/瞬断）で落ちると、自動再試行を一切挟まず即 Error 状態を表示し手動再タップを強いる。分類①ネットワーク一過性を回復戦略に載せていない。
- **根拠(検証済)**: onDownloadRequested は :125 `val call = client.newCall(requestBuilder.build())`・:129 `call.execute().use { response -> ... }` を1回だけ実行。:130-131 `if (!response.isSuccessful) throw IOException("DL に失敗しました（HTTP ${response.code}）")`、IOException も runCatching→onFailure(:145) へ落ち :151 `_uiState.value = PdfImportUiState.Error("PDFの取得に失敗しました。通信状況を確認して、もう一度お試しください")`。retry/backoff/repeat/delay/jitter は grep 実測ゼロ（同ファイル）。:149 `if (e is CancellationException) throw e` でキャンセルは素通し。
- **修正案**: DL に短い指数バックオフ＋Full Jitter のリトライ1〜2回（合計数秒＝知覚閾値内）を噛ませ、残余だけ Error へ落とす。retryIf は IO+timeout+5xx のみ（4xx は持続性でリトライ禁止）、CancellationException は素通し維持。
- **検証ノート**: 1回 execute・即 Error・リトライ皆無を実引用とgrepゼロで確認。charter(e) が『一過性を即エラー=Major』と明示するため Major 維持（層②の回復戦略欠落だがユーザーに手動肩代わりを強いる顕著な劣化）。ただし DL トリガはトークンURL（:95-96 コメント＝短時間再発火しうる）由来のため、リトライ時の URL 有効性は実装側で要考慮（finding の妥当性には影響せず）。

#### [continuity] 公理14候補E 再開ハブ(最頻タスクは1タップ・大きく近く)・公理1 経路独立性  (CONFIRMED ⚠️調整)
- **場所**: `WebBookCard.kt:69-73 / :115-124 / BookshelfScreen.kt:216-218,617-618 / MainActivity.kt:189-190`
- **症状**: 進捗ありのWeb作品カードの大きな表紙タップは続きでなく『なろう目次』へ着地し、再開は11spの小さな二次リンクへ格下げ。同型のPDFカードは表紙タップで保存位置へ直接再開するためジェスチャの意味が割れる。
- **根拠(検証済)**: WebBookCard.kt:69-73 combinedClickable `onClick = onOpen`(grid)、:163-168 同(list)。BookshelfScreen.kt:216 `onOpenWebNovel = { novel -> onReadWebNovel(novel.ncode, 0) }`、:617 `onOpen = { onOpenWebNovel(item.novel) }`、MainActivity.kt:189-190 `web-reader/$ncode/$startEpisode`(0=目次)＝lastReadEpisode>0でも表紙タップは目次固定。再開は別リンク :115-124 `Text("続きから 第${lastReadEpisode}話", fontSize = 11.sp, modifier = Modifier.clickable(onClick = onResume).padding(vertical = 4.dp))`＝タッチ域≒テキスト+8dp≒24dpで48dp未満。対比 BookshelfScreen.kt:203-207 PDFは `onOpenBook` で getLastRead→保存章+スクロールへ1タップ再開。charter 13 §E原則2『最重要の1つを大きく・近く』・公理1『入口が何であれ本文画面の挙動は完全一致』違反。
- **修正案**: 進捗があればWebカードも主タップ(onOpen)を再開へ振りPDFと統一。冗長化する小リンクを廃し(＝<48dp問題も解消)、目次アクセスは⋮メニュー/二次リンクへ降格。
- **検証ノート**: 全 loc 行番号厳密一致。ただし WebBookCard.kt:112-114 に『カード本体タップは常に目次…「最初は目次・二度目以降は続きから読むボタン」に沿う』と設計意図が明記＝優先順位逆転は意図的設計との係争。最も硬い実体は『再開のタッチ域<48dp(層②顕著)』と『同型カードでPDF/Webのジェスチャ意味が割れる(公理1)』。severity調整=意図設計の留保を付けMajor維持。

#### [continuity] 公理14候補D(自動保存は参照ジャンプで動かさない)・公理6 永続性  (PLAUSIBLE ⚠️調整)
- **場所**: `DefaultBookRepository.kt:94-102 / WebReaderViewModel.kt:24-29 / WebReaderScreen.kt:134-142 / WebReadingProgressEntity.kt`
- **症状**: Web作品で第51話まで読んだ後、前方リンクや目次経由で第10話を新規ロードして確認し退出すると、再開ポインタが第10話へ後退し先端(第51話)が失われる。
- **根拠(検証済)**: DefaultBookRepository.kt:94-102 `recordWebReadingEpisode`＝`webReadingProgressDao.upsert(WebReadingProgressEntity(ncode=..., lastReadEpisode = episode, ...))` を無条件 upsert＝max比較なし。WebReaderViewModel.kt:24 doc『話ページ…を読書位置として記録する（last-wins 上書き）』、:27-29 `onEpisodeReached`→launch record。WebReaderScreen.kt:134-142 は `reachedByBack = history.currentIndex < history.size - 1` が false(＝前方への新規ロード)のとき記録するため、目次から前の話を開くと reachedByBack=false でその小さい話数を記録し先端を上書き。charter 13 §D の furthest 上書き違反パターンに一致。
- **修正案**: recordWebReadingEpisode を furthest-wins化(episode>既存 lastReadEpisode のみ更新)。episode粒度なので max 比較で先端を守れる。次善は参照ジャンプ後の『続きに戻る』チップ。
- **検証ノート**: コードパスは静的に確定(全 loc 厳密一致)。ただし WebReadingProgressEntity に『なぜ「最後に開いた話」か（最大到達でなく）…直近に居た話へ戻す』と last-opened が意図的設計として明記＝charter 準拠か否かは製品判断の係争。かつ発生頻度は実機要検証。verdict=PLAUSIBLE(害の分類が文脈次第・実機検証待ち)、severity は同種[0]が層①のためMajor据置き。

#### [critic] UX/06 ⑥ アクセシビリティ『一覧アイテムは mergeDescendants で1フォーカス単位か』／公理11 F  (CONFIRMED)
- **場所**: `本棚カード BookCard.kt:315（目録行 Row）＋同 grid 版・WebBookCard・discovery result カード`
- **症状**: TalkBack が本棚の1冊を『題名／著者／続きN話バッジ／進捗／⋮』の複数ノードに分割して読み上げ、1冊移動に何度もスワイプが要る（本棚は最頻画面のため影響大）
- **根拠(検証済)**: 目録行は Row+combinedClickable(:319) 配下に題名 Text(:342)・著者 Text(:358)・NewChaptersBadge(:369)・BookProgressRow(:377)・⋮IconButton(:387) を並置。combinedClickable は onClick 意味論を付与するがノード併合はしない。ui/ 全域で mergeDescendants／clearAndSetSemantics の使用がゼロ（grep 実測）。既出のa11y指摘（ルビ・liveRegion・heading・ShioriCover Canvas）はいずれも一覧アイテムの併合を扱っておらず未評価
- **修正案**: カード行に Modifier.semantics(mergeDescendants = true) を付与し 1カード=1トラバーサル単位へ束ねる（Material Card/ListItem 相当の挙動を明示的に再現）

#### [d-chrome] Design/09 D システムバーとの契約（同時に出入り）／A 既定値=無  (CONFIRMED)
- **場所**: `NativeReadingScreen.kt:846-897・MainActivity.kt:75・Theme.kt:267`
- **症状**: アプリ自作の上下バーは退避しても OS のステータス／ナビゲーションバーが黒衣として残り続け、読書画面が一度も『無』に到達しない。
- **根拠(検証済)**: com/novelreader 全域 grep で WindowInsetsController.hide(systemBars())／BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE／systemBars() が一切ヒットしない（NONE）。MainActivity.kt:75 は `WindowCompat.setDecorFitsSystemWindows(window, false)`＝edge-to-edge のみ。Theme.kt:267 は `WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = theme != ReadingTheme.DARK`＝アイコン明暗（色）だけで可視性は触らない。バー退避は BottomAppBar(:850-853)/TopAppBar(:894-897) の `graphicsLayer { translationY = ... }` によるアプリ内オーバーレイ移動のみ。charter D 悪例『アプリバーは消えたのにステータスバーが黒帯で残る』に一致。
- **修正案**: isChromeVisible 相当（collapsedFraction/heightOffset 閾値）から WindowInsetsController.hide/show(systemBars()) を同フレーム駆動し behavior=BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE を設定。併せて ChapterContent.kt:103-106 の版面 inset を *IgnoringVisibility へ替えてバー出入りのリフローを防ぐ。
- **検証ノート**: コード実体はすべて確認どおり（grep NONE も再現）。ただし実害は『没入の質』であってデータ/制御の毀損ではないため層②の顕著な欠落。読書=没入が機能要件に近い（charter A）点で Major を維持。

#### [d-chrome] Design/09 A 既定値=無／B 入場時規律  (CONFIRMED)
- **場所**: `NativeReadingScreen.kt:507`
- **症状**: 章に入った瞬間の既定状態が『chrome 表示』で、ユーザーが能動的に消すまで上下バーが本文の上に居座る。既定値が『無』でなく『出しっぱなし』。
- **根拠(検証済)**: :507 `val topAppBarState = rememberTopAppBarState()`＝初期 heightOffset=0（全表示）。currentFile 非キーで生成され、遷移ごとに chrome 表示から始まる。charter A 良い例『入った瞬間から無』に反し悪い例（出しっぱなし）側。見当識は ChapterContent.kt:110-117 `item { ChapterHeader(...) }` が本文先頭で担い、復帰ヒントは :575-589 の消灯時発火なので入場即『無』でも学習機会は保たれる（finder 主張どおり実在）。
- **修正案**: 入場時は heightOffsetLimit（全退避）で構成し既定を『無』にする。ChapterHeader が章タイトルを担うため入場時にバーで見せる必要はない。
- **検証ノート**: コード実体は確認どおり。『bars visible on entry until first scroll』は一般的で防御可能なリーダー慣習でもあるため Major/Minor 境界だが、charter A（中心思想＝既定値は無）を直接的に契約違反しているため finder の Major を尊重。層②。

#### [d-chrome] Design/09 F 画面消灯（読書＝操作していない）  (CONFIRMED)
- **場所**: `NativeReadingScreen.kt（全域）・WebReaderScreen.kt（全域）`
- **症状**: 長い章を無操作で読むと OS の消灯タイマーで暗転し没入が強制終了する。WebReader 読書でも同様。
- **根拠(検証済)**: com/novelreader 全域 grep で FLAG_KEEP_SCREEN_ON／keepScreenOn／KEEP_SCREEN_ON が一切ヒットしない（NONE）。NativeReadingScreen.kt・WebReaderScreen.kt のいずれにも消灯抑止が無い。charter F は読書アプリでのこれを『事実上の欠陥』と明記し既定 ON を求める。
- **修正案**: 読書コンポジション内で DisposableEffect により window に FLAG_KEEP_SCREEN_ON を立て onDispose で clear（生存範囲は読書画面のみ・設定で解除可）。WebReaderScreen も同様に付与。
- **検証ノート**: grep NONE を再現・確認。charter が明示的に『欠陥』と呼ぶ読書快適性の頭出し欠落＝層②の顕著。データ/制御の毀損ではないため Critical ではなく Major。

#### [d-motion] 08 禁止則③（overshoot/bounce/spring振動禁止）＋G適用例『押下フィードバックにカスタムのスケールバウンスを足さない』  (CONFIRMED)
- **場所**: `Motion.kt:20（定義）／BookCard.kt:171,309・WebBookCard.kt:62,155（使用）`
- **症状**: 本棚の全カード（PDF書架/目録＋Web書架/目録の4種）をタップするたび書影が縮んで“ポヨン”と跳ねて戻る。最頻操作に振動演出が乗り静謐の逆方向。
- **根拠(検証済)**: Motion.kt:20 `val MotionSpringCard: SpringSpec<Float> = spring(dampingRatio = 0.6f, stiffness = 400f)`。dampingRatio<1.0＝underdamped。自コメント Motion.kt:18-19 も『dampingRatio=0.6f はわずかに跳ねる復帰』と明記。4呼び出し側（BookCard.kt:169-172 scale 0.96f, :307-310 0.98f／WebBookCard.kt:60-63 0.96f, :153-156 0.98f）が graphicsLayer{scaleX=scale;scaleY=scale} に適用。同じ combinedClickable が `indication = LocalIndication.current`（BookCard.kt:183,321／WebBookCard.kt:71,165）＝Material 標準リップルを既に持つ。参照08-G『押下フィードバック Material 標準リップルで足りる。カスタムのスケールバウンスを足さない（禁止則③）』と名指しで禁止。
- **修正案**: Motion.kt:20 の dampingRatio を Spring.DampingRatioNoBouncy(1f) にして振動を消す（跳ねない整定）＝1箇所修正で4呼び出し側不変。徹底するなら custom scale 自体を削除しリップルのみへ（G『リップルで足りる』）。
- **検証ノート**: 実体・4使用箇所・リップル重畳を確認。振動の視覚量は scale 2-4% に対し overshoot≈0.4%と小さいが、名指しの禁止則③違反が最頻ジェスチャ×4サイトに乗る点で層②顕著＝Major 維持。

#### [d-token] ADR 0014 §A(②値=Typography.kt が字面SSOT・一致検査で同期) / KB03 §6 タイプスケール先彫り / 公理5 意匠版  (CONFIRMED)
- **場所**: `ui/BookCard.kt:113,227,345 ほか ui/ 配下約150箇所`
- **症状**: 字面(サイズ)がテーマ切替/一括調整で追従せず、画面ごと微妙に違う文字サイズが増殖（『35個の青』のタイポ版）
- **根拠(検証済)**: grep実測 `fontSize = [0-9]` が ui/ で161ヒット。代表: BookCard.kt:113 `fontSize = 10.5.sp,` / :227 `fontSize = 9.5.sp,` / :345 `fontSize = 16.5.sp,`、DiscoveryResultScreen.kt:239 `fontSize = 10.5.sp,`、DiscoveryHomeScreen.kt:287 `fontSize = 13.5.sp,`・:392 `fontSize = 12.5.sp,`、DiscoveryCommon.kt:112 `fontSize = 14.5.sp,`・:152 `fontSize = 10.5.sp,`、NativeReadingScreen.kt:904 `fontSize = 16.sp,`・:963 `fontSize = 13.sp,`。野良値 9.5/10.5/11.5/12.5/13.5/14.5/16.5.sp が NovelReaderTypography(Typography.kt:23-114 は bodyMedium=14sp 等の全スケールを持つ)のスロットを経由せず生指定。ADR 0014(行19)は Typography.kt を『②値=機械可読な単一値・一致検査スクリプトで同期を検証』と宣言するが、check_design_tokens.py は色のみ検査(sp参照ゼロ)＝drift 無警告。
- **修正案**: 呼び出し側の fontSize 直書きを削り MaterialTheme.typography.* スロット経由へ統一(不足スロットは Typography.kt へ集約)。次善=不足スロットを彫り check_design_tokens.py に sp 突合を追加し機械検査化。
- **検証ノート**: 161ヒット全数は未走査だが代表8アンカー全て実在・行番号一致。Typography.kt に完全スケールが存在するのに生spで迂回している構造を確認。層②(トークン規律)顕著につき Major 維持。

#### [d-token] ADR 0014 §C 禁止則『余白は離散スケール(4/8/16/24/40dp)のみ・任意値禁止』(Accepted) / KB03 §6-3 隣接値25%則  (CONFIRMED)
- **場所**: `ui/discovery/DiscoveryResultScreen.kt:247 / ui/ChapterContent.kt・ui/BookshelfScreen.kt 全域`
- **症状**: 余白が離散スケール外の任意値(11/5/13/14/6/20dp 等)で乱立、構図の一貫性が値レベルで担保されない
- **根拠(検証済)**: DiscoveryResultScreen.kt:247 `.padding(horizontal = 11.dp, vertical = 5.dp)`。ChapterContent.kt は非スケール dp が多数(:171 top=14.dp bottom=26.dp / :184 height(15.dp) / :218 vertical=18.dp / :211,242 20.dp / :263,277 bottom=14.dp)。BookshelfScreen.kt も 5/6/7/10/11/13/14/15/18/20.dp をグリップ実測。ADR 0014(行39)『余白は離散スケール(4/8/16/24/40dp)のみ。任意値禁止』が Accepted だが theme/ に Spacing トークン不在(Color/Motion/Theme/Typography のみ)、check_design_tokens.py も dp 非検査。宣言と実装が乖離。
- **修正案**: 第一候補=任意 dp を最近接スケール値へ丸めて選択肢を消す。次善=SpacingTokens(4/8/16/24/40)を彫り 25%則の昇格審査を check_design_tokens.py に追加。面サイズ/48dpタップ枠/1・0.5dpヘアラインは対象外。
- **検証ノート**: アンカー DiscoveryResultScreen.kt:247 は行番号・値とも厳密一致。ただし指摘の ChapterContent 具体値『5,6,10,13,14dp』は不正確(実体は 4/14/15/18/20/26dp)＝その列挙はBookshelfScreen由来の混同と推定。現象(非スケールdp乱立)自体は豊富に成立。

#### [d-token] ADR 0014 §D『意味を運ぶ文字は WCAG 4.5:1＞美学』(InfoText 暗化トークン新設で裁定済) / KB04 §2 色地上の前景を alpha で作らない禁止則  (CONFIRMED)
- **場所**: `ui/discovery/SearchConditionSheet.kt:365 / ui/ReadingErrorScreen.kt:48 / ui/NativeTableOfContentsScreen.kt:149 / ui/ProcessingBanner.kt:129,193 / ui/discovery/DiscoverySearchScreen.kt:327`
- **症状**: 意味を運ぶ補助/エラー/状態テキストが alpha で本文以下に沈み、実効色がテーマ地色で予測不能に変わる。可読性が AA を割る懸念
- **根拠(検証済)**: Color.kt:32 コメント自身が『OnSurfaceVariant…素地上 3.79:1 で通常文字 AA(4.5:1) 未達』と明記し、Color.kt:40-42 に InfoTextLight/Sepia/Dark(意味テキスト用暗化トークン)を新設済。にもかかわらず SearchConditionSheet.kt:365 制約文『読了時間と併用できません…』が `onSurfaceVariant.copy(alpha = 0.7f)`、DiscoverySearchScreen.kt:327 検証エラー文が同 `.copy(alpha = 0.7f)`、ReadingErrorScreen.kt:48/TOC:149 が `textSecondary.copy(alpha = 0.75f)` を意味テキストに使用。ADR-D の暗化トークン裁定を alpha 合成で打ち消す退行。
- **修正案**: 意味テキストの .copy(alpha=…) を削り InfoText/専用暗化シェードを素の値で使う。トラック/スクリム/disabled は M3 標準用途で対象外。
- **検証ノート**: トークン規律違反(InfoText 迂回の alpha 合成)はコードで CONFIRMED。ただし『AA を割る』の定量は実機/計測待ち＝PLAUSIBLE段。ProcessingBanner:129,193 は onPrimaryContainer 基準(M3 標準ペア)でアンカーとして弱い。中核3件(制約文/エラー/TOC状態)は onSurfaceVariant/textSecondary=既にサブAA地の重ね沈めで妥当。Major 維持。

#### [d-type] WCAG 1.4.3 / Design/05 §2（本文最低4.5:1・沈めてよいのは補助/装飾のみ）  (CONFIRMED)
- **場所**: `ChapterContent.kt:262,276 / Theme.kt:63,89,108 / RubyText.kt:69,90`
- **症状**: ふりがな（ルビ）が地に溶け、難読漢字・非母語読者ほど読みのフォールバックが機能しない。
- **根拠(検証済)**: ChapterContent.kt:262 と :276 とも `rubyColor = colors.ruby,`。ruby 値は Theme.kt:63 `ruby = Color(0xFF8B96A0)`（LIGHT）・:89 `Color(0xFFA3906A)`（SEPIA）・:108 `Color(0xFF6E7984)`（DARK）。RubyText.kt:69 `rubyFontSizeRatio: Float = 0.5f` かつ :90 `textSize = style.fontSize.value * rubyFontSizeRatio * density`＝本文14〜24sp の半分(7〜12sp)の小文字。自前計算した対背景コントラスト＝LIGHT 2.89:1・SEPIA 2.53:1・DARK 4.05:1 で申告値と一致、3テーマ全て4.5:1未満。
- **修正案**: 色相/彩度を保ったまま濃度を上げAA(4.5:1)達成。UnreadSeiji/InfoText の『意味用途は役割別トークンで暗化』先例を踏襲。
- **検証ノート**: 行番号・色値・比率・コントラスト全数を実測照合し一致。ルビは基底漢字が読める補助だが furigana 依存読者には唯一の読み情報のため Major 妥当（基底テキストは 15.8:1 等で合格＝Critical までは至らず）。

#### [d-type] WCAG 1.4.3 / Design/10 §1（主役を沈めない）・§6/§9（色地上のalpha沈め禁止）  (CONFIRMED ⚠️調整)
- **場所**: `ReadingErrorScreen.kt:42,48`
- **症状**: エラー内容（何が失敗したか）が読めず復帰判断ができない。テキスト階層が反転。
- **根拠(検証済)**: ReadingErrorScreen.kt:42 `color = colors.textSecondary,`（対象は :39 の固定文言『読み込みに失敗しました』）。:48 `color = colors.textSecondary.copy(alpha = 0.75f),`（対象は :45 の実エラー内容 message）。実測: 見出し textSecondary＝LIGHT 3.79/SEPIA 3.28/DARK 4.68、詳細 alpha0.75 合成後＝LIGHT 2.56/SEPIA 2.31/DARK 3.18。詳細は全テーマ、見出しは LIGHT/SEPIA で4.5:1未満（申告値と一致）。
- **修正案**: 詳細行の copy(alpha=0.75f) 除去、見出しは colors.text へ引き上げ、意味用途 textSecondary は AA を満たす専用シェードへ分離。
- **検証ノート**: コントラスト全数一致。ただし2点の枠付け不正確を補正: (a)『主役の見出し』とされる :42 は固定汎用文言で、実際の『何が失敗したか』は :48 の detail。(b)『primary 強調が一つも無い』は不正確＝:53/:60 に塗りつぶし Button（再試行/本棚に戻る）があり主要アクションは強調・可読。核心（機能テキストが全テーマで4.5:1未達）は成立するため Major 維持。エラー画面は稀パスだが機能情報の可読破綻は実害。

#### [ia] 15-§B『既定ソートは支配的タスク＝続きから読む』／§G①自己スケール（正確には公理1/6ではなく§Bが基盤）  (CONFIRMED ⚠️調整)
- **場所**: `ShelfItems.kt:49 ／ BookDao.kt:14`
- **症状**: PDFを新規取込するたび未読の新刊(addedAt=now)が読みかけの本(lastReadAt=過去)の上へ来て、支配的タスク『続きから読む』の対象が本棚先頭から押し下がる。
- **根拠(検証済)**: ShelfItems.kt:49 `ShelfItem.Book(book, maxOf(book.addedAt, lastReadAt))`／BookDao.kt:14 `"ORDER BY MAX(b.addedAt, COALESCE(p.lastReadAt, 0)) DESC, b.title ASC"`。正本§B『追加日順を既定にすると、新規インポートのたびに読みかけの本が押し流される——支配的タスクを既定が裏切る』／実装含意『ORDER BY last_read_at DESC』。MAX(addedAt,lastReadAt)は addedAt=now の未読が過去読了の lastReadAt を上回るため症状が実際に成立（昨日Xを読んだ後今日Yを取込→YがXの上）。
- **修正案**: 既定ソートを lastReadAt 主キー（未読は addedAt フォールバックで読みかけ群の下）へ。ソート方式変更は §B『変更は永続・全画面一貫』を満たす形で、意匠に絡むなら ADR 経由で。
- **検証ノート**: コード実体・§Bの名指しとも一致しCONFIRMED。ただしファインダーの rule『公理6/公理1』は不正確＝ソートはグリッド/リストで一貫し(公理1未発火)、ユーザーのソート設定UI自体が無い(公理6未発火)。実際の基盤は§Bのみ。MAX()は純追加日順でなく能動読書中は上位を保つため埋没は『取込時＋取込前に読んだ本』に限局。よみかけフィルタ(READING)はあるが既定ビューは救わない。層②prominentでMajor維持。

#### [ia] 15-§G②束ね・④既知アイテム高速路『見つけられない本は持っていないのと同じ』  (CONFIRMED ⚠️調整)
- **場所**: `BookshelfScreen.kt:442-447／MainActivity.kt:180-182／ShelfItems.kt:37／BookEntity.kt`
- **症状**: 蔵書が数百冊になると名前を知っている『あの本』への蔵書内最短路が無い（蔵書内タイトル/作者フィルタも五十音索引も無く🔍は外部作品検索へ飛ぶ）。シリーズも束ねられずフラットな壁になる。
- **根拠(検証済)**: BookshelfScreen.kt:442 `IconButton(onClick = onOpenDiscovery)`＋:445 `contentDescription = "小説を探す"` → MainActivity.kt:181 `navController.navigate("discovery")`＝なろう発見ホーム(蔵書内検索ではない)。ShelfItems.kt:37 `fun mergeShelfItems(...)` はフラットList合成＝束ね無し。BookEntity.kt:7-28 に series 列なし(id/title/htmlDirPath/author/addedAt/ncode/contentSha256のみ)。蔵書内フィルタは StatusChipRow の状態軸のみでタイトル/作者LIKEも五十音索引も不在。
- **修正案**: 追加が次善：①蔵書内タイトル/作者フィルタ(Room LIKE)＝既知アイテム最短路、②seriesを第一級エンティティ化しGROUP BY束ね（インポート時に候補提示紐づけ）。
- **検証ノート**: 全実体を確認しCONFIRMED。ただし規模条件付き＝現行の小蔵書では潜在的欠如で、§G『最初から数百冊を仮定』の設計要件違反として層②prominent。能動的バグでなく増設backlogである点をMajorの文脈に付す。🔍→external discoveryはMainActivity:181で確定。

#### [idempo] 公理4 可逆性（+ UX/16 H 確認<Undo）  (CONFIRMED)
- **場所**: `BookshelfScreen.kt:695 / DefaultBookRepository.kt:392`
- **症状**: 蔵書削除が即・完全に不可逆。DB行と変換済みHTML実体（数分の変換成果）が一度に消え、Undo が全経路に存在しない。取込元PDFのURIも保持しないため復旧は元PDF再取得＋再変換のみ。取り違え削除を戻せない。
- **根拠(検証済)**: BookshelfScreen.kt:695-698 confirmButton の TextButton onClick={ onDeleteBook(book); bookToDeleteId=null } が直接確定（onDeleteBook は :210 で viewModel.deleteBook）。BookshelfViewModel.kt:328-330 `fun deleteBook(book) { viewModelScope.launch(Dispatchers.IO) { repository.deleteBook(book) } }` に遅延・撤回層なし。DefaultBookRepository.kt:385-394 `runInTransaction { bookDao.deleteById; progressDao.deleteByBookId } … if (!File(book.htmlDirPath).deleteRecursively())` で即物理削除。grep で undo/元に戻す/softDelete/pendingDelete いずれも不在、snackbar は取込失敗リトライ用のみ（BookshelfScreen.kt:230-236）。deleteUiMode(:149) は削除トリガ方式（⋮メニュー/長押し）の切替に過ぎず Undo ではない。BookEntity.kt:6-29 に source PDF URI 列なし（htmlDirPath/contentSha256/ncode のみ）＝復旧材料を持たない主張も裏付け。唯一のガードは確認ダイアログ BookshelfScreen.kt:689-704。
- **修正案**: 確認ダイアログ（:689-704）を撤去し、削除タップで即カードを棚から外し既存 snackbarHostState で『「題名」を削除しました／元に戻す』を数秒出す遅延削除へ置換。ウィンドウ経過後に初めて DB＋File を確定（公理4 の可逆化＝16-H の上位解）。確認を残すなら Finding 1 の対象主役化を併用。
- **検証ノート**: コード実体は主張どおり全て実在（行番号も一致）。真に不可逆（再変換要・source URI 非保持を BookEntity で確認）でありUndoゼロという点で層①疑いの Major は妥当。ただし削除は「トリガ→確認ダイアログ→承認」の多段・意図操作であり確認ダイアログという緩和が現に存在するため、defect というより hardening 提案（Major/Minor 境界）。16-H の『確認<Undo』と表紙なし ListBookCard の取り違えベクタ（Finding 1）を根拠に Major を維持。

#### [measure] 24層 §E 回復パスの意図的発火（新しい回復パスの PR には発火テストを同梱）／charter(b)  (CONFIRMED)
- **場所**: `android/app/src/main/java/com/novelreader/NovelReaderApplication.kt:87 runStartupRecoveryOnce`
- **症状**: 孤立HTML掃除→孤児権限解放（pending 空でも early-return より前）→lost/resumable partition→emitError→再投入 の起動リカバリ統合順序・分岐が退行しても緑のまま
- **根拠(検証済)**: L87 `fun runStartupRecoveryOnce()`。コメント L100-104 が『pending が空でも走らせる必要があるため、下の early return より前に置く』と明記し L103 `repository.releaseOrphanedPermissions(...)`→L104 `if (pending.isEmpty()) return@launch`。L112 `val (resumable, lost) = pending.partition{...}`、L114-119 emitError。test/androidTest 全体を grep して `runStartupRecoveryOnce` を発火するテストは 0 件（呼び出しは MainActivity.kt:70 の実コードのみ）。sub 関数 cleanOrphanHtmlDirs/orphanedPermissionUris は BookRepositoryTest:278-332 で個別固定だが、束ねる順序・partition・再投入は未検証。
- **修正案**: partition と『pending 空でも権限解放を先に走らせる』順序を純関数へ抽出（orphanedPermissionUris と同じ流儀）し、resumable/lost 振り分け・空pending時の権限解放呼び出しを JVM テストで固定。startForegroundService 発火自体は androidTest の少数シナリオへ。
- **検証ノート**: 位置・順序コメント・grep 0 件すべて主張どおり。層②（機械検証）の回復パス未発火は §E 明文違反で Major 妥当。

#### [measure] 24層 C表#8・E表 破損PDF注入→隔離 interlock（本棚に出ない・現行データ無傷）／charter(b)  (CONFIRMED)
- **場所**: `android/app/src/main/java/com/novelreader/repository/DefaultBookRepository.kt:198（addBook 失敗クリーンアップ）／pdf/PdfBookExtractor.kt:130 classify`
- **症状**: 破損PDFで抽出失敗時の隔離（書きかけ outputDir 削除・BookEntity 未 insert・pending_jobs 行削除）が退行しても repository 層テストが緑のまま
- **根拠(検証済)**: addBook は L194 で public `PdfBookExtractor.process(tempFile, bookId, outputDir){...}` を呼ぶ。この public 版は PdfBookExtractor.kt:73 `= process(PdfBoxEngine, ...)` で実エンジン固定＝engine 注入口なし。engine 差替可能な `internal fun process(engine,...)`（PdfBookExtractor.kt:76）は PdfBookExtractorTest の FakeEngine からのみ使用（L46/52）。失敗時クリーンアップは L198-204 `catch(e: Throwable){ ... outputDir.deleteRecursively(); throw e }`。BookRepositoryTest の CorruptedPdf 言及は L93-95 の classifyError 純判定のみで、addBook を破損注入で駆動し『outputDir 消滅＋未 insert＋pending 削除』を assert するテストは存在しない。破損分類は PdfBookExtractorTest（fake engine→IOException）で担保だが repository 層の実路は未検証。
- **修正案**: addBook の抽出呼び出しを internal process(engine,...) 経由へ差替可能にし、例外を投げる fake engine で駆動して outputDir 削除＋未 insert＋pending_jobs 行削除を assert する回復テストを同梱。
- **検証ノート**: public/internal 二重シグネチャ・addBook が public 版のみ配線・失敗 catch の deleteRecursively・repo 層テスト不在をすべて確認。E表『使われないコードは腐る』該当で Major 妥当。

#### [measure] 22層 §B 禁止段（ログに書名・URI が乗る＝内容の観測。CI 静的検査で落とす対象）／§F-4（ログはバグレポート経由で端末外へ出る前提）／charter(a)  (CONFIRMED)
- **場所**: `android/app/src/main/java/com/novelreader/viewmodel/PdfImportViewModel.kt:92`
- **症状**: 取り込もうとした作品の DL URL（ncode を含む）とファイル名（=書名になる Content-Disposition）が logcat に残り、『何を読もうとしたか』が観測データ化する
- **根拠(検証済)**: L92 `Log.i(TAG, "PDF でない DL を無視: url=$url disposition=$contentDisposition")`。url は onDownloadRequested 引数（KDoc L75『DL 対象 URL』＝ncode を含む）、contentDisposition は filename 判定用（L77）で書名になりうる。Log.i は既定でリリースにも残る（proguard で strip 対象は v/d のみ）。判定に必要なのは looksLikePdf の真偽（L91）のみで、内容識別子の出力は不要。
- **修正案**: 出力から url と disposition を除去し、looksLikePdf の判定結果（真偽）だけをログする。内容識別子を一切残さない。
- **検証ノート**: コード・引用は主張どおり実在。INTERNET 権限が無いため即時 exfil 経路は不在（＝実害＝Critical ではない）だが、§B が CI ブロック対象と明示する名前付き禁止パターンで、§F-4 のバグレポート経由の端末外流出は残る＝層①疑い（潜在的プライバシー軸違反）として Major 妥当。INTERNET 不在を重く見れば Minor も成立余地あり。

#### [persist] 公理6 永続性（構成変更で読書位置を失わない）  (CONFIRMED ⚠️調整)
- **場所**: `WebReaderScreen.kt:62,:65,:152 / AndroidManifest.xml:32-41`
- **症状**: Web読書中に構成変更（回転/システムのダークモード変更/システムfontScale変更）でActivity再生成が起こると、WebViewが破棄・再生成され、入場時の話（nav引数 startEpisode）を再ロードして話内スクロール・WebView前後履歴・セッション内の前進分（次へで進んだ話）が巻き戻る。
- **根拠(検証済)**: WebReaderScreen.kt:62 `val webViewHolder = remember { mutableStateOf<WebView?>(null) }`（rememberSaveable/saveState-restoreState なし）。:65 `val startUrl = remember(ncode, startEpisode) {` → :66 `if (startEpisode > 0) narouEpisodeUrl(ncode, startEpisode) else narouWorkUrl(ncode)`（入場時固定のnav引数から生成）。factory 内 :152 `loadUrl(startUrl)` が再生成の度に入場時の話を再ロード。:71-76 DisposableEffect onDispose が `webViewHolder.value?.destroy()` で確実に破棄＝再生成で必ず作り直し。AndroidManifest.xml:32-41 の MainActivity に android:configChanges 属性が無く（画面回転のscreenOrientation指定も無し）、回転/uiMode/fontScale は全てActivity再生成を起こす。DB(web_reading_progress)は onEpisodeReached で最新話を記録するが、再生成時に startUrl はDBを参照しない。
- **修正案**: 再生成時（=構成変更のたび）に startUrl を『DBの最終記録話』へ差し替える（getWebReadingEpisode 等を LaunchedEffect で読み直す）のが最小策。理想は WebView.saveState(Bundle)/restoreState を rememberSaveable(Bundle) で持ち回り、話・スクロール・履歴まで復元する。
- **検証ノート**: 機構は静的解析で確定（回転は orientation ロック無しで確実に再生成、システムuiMode/fontScareも再生成）。ただし指摘の『ダーク切替/フォントスケール変更』のうち、アプリ内トグル（MainActivity.kt:85 `var appTheme by remember` / 独自fontSize prefs）はComposeの再コンポーズのみでActivityを再生成しない＝これらでは巻き戻らない。実トリガはシステム回転・システムダーク・システムfontScale。二次読書経路（WebReader）に限定され、フル再入場ではDBから話単位で復帰するため Major に留める（Critical=主読書経路なら該当だが本件は副経路・話単位で回復可）。要検証→Majorへ確度を上げた。

#### [portable] 公理18候補 D 運ぶものの層別／E 経路①・D節の逆進性  (CONFIRMED)
- **場所**: `AndroidManifest.xml:13-20（理由コメント）+ :23`
- **症状**: 『HTML実体が25MB枠に入らない』を理由に、置換不能な読書位置・設定まで一律に端末と心中させている。使い込んだユーザーほど失う量が大きい逆進性。
- **根拠(検証済)**: AndroidManifest.xml:16-19 のコメントは『Auto Backup には 25MB 上限があり…DB だけ復元される → …実体が無く…NativeReadingScreen の resolvedFile==null』を根拠に allowBackup 全体を無効化（:23）。実体層とメタデータ層(数十KB〜数MB=25MB枠に常収まる)を層別 include/exclude する選択肢を検討せず全遮断へ振った過剰対処であることをコメント文面から確認。
- **修正案**: allowBackup="false" を撤回し、data_extraction_rules.xml で <cloud-backup>=database+datastore include・books/ exclude、<device-transfer> は books/ も include（Android11以下は fullBackupContent 併記）。実体を運べない事実は保ったまま位置・設定を全ユーザーで自動復元。
- **検証ノート**: コメント範囲・論理はコード実体どおり CONFIRMED。ただし本質は Finding[0] と同一の allowBackup=false を『判断の当否』側から論じたもので、生きた実害（メタデータ全損）は[0]と重複。独立した設計判断の欠陥（層別未検討）としては成立するため Major 維持だが、統合時は[0]と1件の根に束ねるのが妥当。

#### [privacy] 公理15 ③透明化（見て・消せる）／削除の完全性（層E）／公理8のデータ版  (CONFIRMED)
- **場所**: `WebReadingProgressDao.kt:10-21 ＋ DefaultBookRepository.kt:380-395/87-88/94`
- **症状**: WebViewで読んだなろう作品の読書位置履歴(ncode+話数+時刻)が、いかなるユーザー操作でも消せない。本削除・Webカード除去・検索経由の起動いずれでも web_reading_progress 行が端末に永久残留する(アプリのデータ消去/アンインストールでのみ消える)。
- **根拠(検証済)**: WebReadingProgressDao.kt:12-21 は @Query getAll / get(:16-17) / @Insert(REPLACE) upsert(:20-21) のみで delete メソッド皆無。app/src/main 全体の grep で `DELETE FROM web_reading_progress` および WebReadingProgress の delete/remove/clear = NONE(実測)。DefaultBookRepository.kt:385-388 の runInTransaction{ bookDao.deleteById; progressDao.deleteByBookId } は web_reading_progress に触れない。:87-88 removeWebNovel = webNovelDao.deleteByNcode(...) のみ。:94-102 recordWebReadingEpisode は WebReader 起動ごとに upsert するが除去経路が無い。
- **修正案**: 既存の除去アクションに削除を相乗り: WebReadingProgressDao に deleteByNcode を1本追加し、removeWebNovel と deleteBook(ncode 保持時)から同 ncode 行を消す。加えて起動時に『どの棚項目にも紐付かない孤児 web_reading_progress を回収』する掃除(cleanOrphan 系と同様)で完全化する。
- **検証ノート**: location 行番号は全て実体と一致。データは端末内 DB に閉じ送信されない(層②はクリーン)ため Critical ではなく、システムの Clear Data/アンインストールでは消せる=消去手段が皆無ではない。欠落しているのは『粒度のある in-app 削除＋カスケード』であり、公理15③(消せる)/層Eの削除完全性の確定した層①ギャップ。Major は妥当だが Major/Minor 境界寄り(外部露出なし)。

#### [privacy] 公理15 B②(ログもまた保存層／センシティブデータをログに出さない)／charter(c)  (CONFIRMED)
- **場所**: `PdfImportViewModel.kt:92`
- **症状**: 縦書きPDF取り込み中、PDFでないDLを無視する分岐で、DL対象URL(なろう ncode を含みうる)と Content-Disposition(filename=書名を含みうる)を BuildConfig.DEBUG ガード無しで logcat に出力する。読書関心の識別子がリリースビルドのログに残る。
- **根拠(検証済)**: PdfImportViewModel.kt:92 `Log.i(TAG, "PDF でない DL を無視: url=$url disposition=$contentDisposition")` — DEBUG ガード無し。app/build.gradle:33 `minifyEnabled false`(release) のため proguard/R8 の Log 除去(assumenosideeffects)は一切効かず Log.i がリリース APK に残る(proguard-rules に Log 除去ルール無し)。gradle の firebase/crashlytics/analytics/sentry grep = NONE(外部流出SDK無し)。
- **修正案**: 変数展開を落として定数ログ("PDF でない DL を無視")にする、または行全体を if (BuildConfig.DEBUG) で囲む。分岐条件は looksLikePdf が判定済みで url/disposition を残す必要は無い。
- **検証ノート**: コード事実は完全一致・CONFIRMED。ただし実害面は狭い: minSdk 26 で logcat はアプリ自身に閉じ READ_LOGS は通常アプリに付与不可、かつクラッシュ/解析SDK皆無で外部流出経路が無い。url が ncode を含むのは narou 閲覧フローでは成立、disposition の書名は『含みうる』条件付き。層①(保存層へのセンシティブ露出)なので Major 維持は妥当だが Major/Minor 境界の低位で、Minor と評しても不当ではない。

#### [reach] 21-C 到達性の秩序（毎セッション級＝中央タップ→下端シート／上端はタイトルと戻るだけ）+ 21-B（隅は12mm・48dpは隅で不足）  (CONFIRMED)
- **場所**: `NativeReadingScreen.kt:920-928（TopAppBar actions のギア IconButton :922）`
- **症状**: 本文フォント・行間・テーマ・余白（読書中に触る表示設定）の唯一の入口が上端バー右上隅の歯車。片手読書のたびに中央タップでchrome→右上隅へ持ち替えて歯車→下端シート、という到達コストがセッションで複利蓄積する。
- **根拠(検証済)**: NativeReadingScreen.kt:920-927 `actions = { … IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.Settings, contentDescription = "表示設定") } }`。grep で `showSettings = true` は :922 のみ＝唯一のトリガ（他は :709 宣言・:988/:1002 の開閉参照）。中央タップ :781-790 `detectTapGestures(onTap = { … settleTopBar(…) })` は chrome トグルのみで設定は開かない。開くシート ReadingSettingsSheet.kt:34 KDoc『表示設定ボトムシート（テーマ切替・文字サイズ）』/:80『テーマ3択・スライダー3本』＝テーマ+文字サイズ+行間+余白。下端バー :846-891 は prev/目次/next のみで表示設定を持たない。標準21 適用例表 line151『悪い＝上端 app bar にフォントのアイコン／良い＝中央タップ→下端の Aaシート。上端はタイトルと戻るだけ』に逐語一致。
- **修正案**: 上端 actions のギアを撤去し、表示設定シートの起動を中央タップ→下端シート（または下端バーへアイコン移設）に寄せる。上端は『本棚に戻る(Up)＋章タイトル』だけに保つ。
- **検証ノート**: コード実体・ルール対応とも逐語一致でCONFIRMED。21-C は §G で層②の強い原則（破ると『疲れる』）＝Major(層②顕著)が妥当。毎セッション級の駆動因子は主にテーマ切替（環境光で変わる）で、フォント/行間/余白は UX19『最良の設定は開かれない』の set-and-forget 側＝頻度の複利はテーマ依存。テーマが実質 set-once の運用なら Minor 寄りだが、標準の逐語的悪例に一致するため Major を維持。

#### [reach] 21-E 適応の一次元化（本棚＝feed の理想形は GridCells.Adaptive＝分岐ゼロの適応）  (CONFIRMED)
- **場所**: `BookshelfScreen.kt:569（LazyVerticalGrid columns = GridCells.Fixed(2)）`
- **症状**: 本棚グリッドが常に2列固定で窓幅に適応しない。タブレット横持ち・折りたたみ開・大画面分割（≥600/840dp）でも2列のまま＝各カードが肥大し feed が密度化せず余白の砂漠になる。標準が『この層の理想形』と名指しした Adaptive を採らず幅からの列導出を放棄。
- **根拠(検証済)**: BookshelfScreen.kt:569 `columns = GridCells.Fixed(2)`。grep 実測: app/src/main 全体で `GridCells` は :24 import と :569 使用のみ、`Adaptive` 使用ゼロ、`WindowSizeClass`/`currentWindowAdaptiveInfo`/`BoxWithConstraints` もゼロ（適応レイアウトの分岐が全アプリで皆無）。標準21-E line105『本棚＝feed。LazyVerticalGrid(columns = GridCells.Adaptive(minSize=…)) は列数を幅から自動導出——分岐ゼロの適応でありこの層の理想形』。
- **修正案**: GridCells.Fixed(2) を GridCells.Adaptive(minSize = カード最小幅) へ置換し列数を幅から自動導出。スマホ(<600dp)は影響ゼロ、≥600dp で自然に多列化。
- **検証ノート**: コード違反（Adaptive不使用・決め打ちFixed(2)）はCONFIRMED。実害は ≥600dp 窓に限局しスマホ多数派には無影響だが、影響端末上ではカード肥大が視覚的に顕著＝Major(層②顕著)を維持（Critical=層①ではない）。実機での実列数・カード肥大の見え方は要検証（finding も自認）。

#### [ssot] 公理8 嘘をつかない（終わっていないものを終わったと見せない）  (CONFIRMED)
- **場所**: `app/src/main/java/com/novelreader/viewmodel/ShelfItems.kt:108-111`
- **症状**: 最終章を1行でもスクロールすると章の途中でも進捗100%表示・書影に朱印『了』・『読中(よみかけ)』フィルタから消え『読了』フィルタへ移動する。全2話の本なら2話目を読み始めた瞬間に読了と主張。
- **根拠(検証済)**: ShelfItems.kt:108 `return if (chapNum >= totalChaps) {` → :110-111 `val atTop = scrollIndex == 0 && scrollOffset == 0` / `if (atTop) (totalChaps - 1).toFloat() / totalChaps else 1f`（最終章でスクロール有りなら無条件 1f）。この 1f が3経路を同時反転: ① BookCard.kt:63 `val percent = (progressFraction * 100).toInt()`→:80-84 `Text(text = "$percent%")`＝『100%』、② ShelfItems.kt:133-134 `fraction == null -> ReadingStatus.UNREAD` / `fraction >= 1f -> ReadingStatus.FINISHED`（readingStatusFor は :126 で progressFractionFor を経由）→ BookCard.kt:164 `val isFinished = readingStatusFor(progress, totalChaps) == ReadingStatus.FINISHED`→:209-231 朱印『了』、③ filterShelfByStatus(:155-157)経由で『読了』該当。設計コメント ShelfItems.kt:99『表示計算のみで嘘を消す』は at-top 側の 100% 嘘だけ消し、スクロール有り側の完読断定は残存。章内総量は DB 未保持（コメント :93-94 が明言）で真の完読は判定不能なのに 1f で断定している点が公理8違反。
- **修正案**: else 1f をやめ、章内総量を検証できない最終章スクロール中は READING 側・100%未満(<1f)に留める。朱印『了』と『読了』フィルタは検証可能な完読シグナル（例: 最終章の実末尾到達 reached-bottom フラグを1件記録）にのみ結ぶ。
- **検証ノート**: 全引用が実在・主張どおり。実害は労働: readingStatusFor が FINISHED を返すため filterShelfByStatus(:155-157)で READING フィルタから除外＝今まさに読んでいる本が『よみかけ』で見つからず findability を損なう（層②顕著＋機能的余波）。ただしデータ破損はなく読書位置は正しく保存されるため Critical(層①実害)には至らず Major が妥当。


### Minor

#### [a11y] 公理11 F (d) liveRegion なしの非同期状態変化 / WCAG 4.1.3 Status Messages  (CONFIRMED)
- **場所**: `NativeReadingScreen.kt:793-839 (Loading/Success/Error 切替) ・:809-819 (継続カード出現)`
- **症状**: 章パースの失敗/成功や、最終章末尾に継続カードが現れたことが、フォーカス外の TalkBack ユーザーに告知されない。
- **根拠(検証済)**: :793 `when (val result = parseResult)` の Loading(:794 CircularProgressIndicator)→Success(:822 ChapterContent)→Error(:833 ReadingErrorScreen)分岐、および :810-819 `ContinuationCard(...)` の出現部に `Modifier.semantics{ liveRegion = LiveRegionMode.Polite }` が一切無い。Error 画面へ入れ替わっても自動告知されず、ユーザーが再探索するまで気づけない。
- **修正案**: エラー表示ルート(ReadingErrorScreen 側の見出し/メッセージ)に Modifier.semantics{ liveRegion = LiveRegionMode.Polite } を付す。継続カードにも過剰告知を避け Polite で同様に。
- **検証ノート**: 液リージョン不在を現物確認。パースは通常一瞬で優先度低め・状態自体は再探索すれば読めるため 層② 軽微=Minor が妥当(finder 判定維持)。

#### [a11y] 公理11 F (d) heading() なしの見出し  (CONFIRMED)
- **場所**: `ChapterContent.kt:174-183 (章タイトル) ・:248-253 (前書き/後書きラベル)`
- **症状**: TalkBack の見出しジャンプで章タイトルや前書き/後書きへ飛べない。視覚的には見出し(中央寄せ明朝+藍ルール)なのに非視覚チャネルでは見出しと認識されない。
- **根拠(検証済)**: ChapterHeader の :174-183 `Text(text = title, fontFamily = MinchoFamily, fontSize = (fontSize + 2).sp, fontWeight = FontWeight.SemiBold, …)` に semantics{ heading() } 無し。StyledBlock ラベルの :248-253 `Text(text = block.label, style = bodyStyle.copy(fontWeight = FontWeight.Bold, color = colors.accent), …)` にも heading() 無し。
- **修正案**: 両 Text に Modifier.semantics{ heading() } を1行付す(追加最小・見た目不変)。
- **検証ノート**: heading() 不在を現物確認。読書画面は章1枚=見出し1つで実害限定のため Minor 妥当(finder 判定維持)。

#### [add] 公理12 権限（11-E-2 事前説明priming）  (CONFIRMED)
- **場所**: `app/src/main/java/com/novelreader/ui/BookshelfScreen.kt:118-123, 152-165`
- **症状**: Android 13+ で POST_NOTIFICATIONS 未許可時、最初の『PDFを追加』タップ（バッテリーダイアログ後）に自前 priming を挟まずシステム通知権限ダイアログを直接出す。理由を利益の言葉で先に説明していない。
- **根拠(検証済)**: launchPdfPicker(:157-160) `if (granted) { pdfPicker.launch(...) } else { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }`＝未許可なら直接システムダイアログ。前段に rationale/priming UI 無し。緩和策として結果無視で進む notificationPermissionLauncher(:119-123) `{ pdfPicker.launch(arrayOf("application/pdf")) }`＝graceful degradation は成立。11-E-2②に未対応。
- **修正案**: 通知は最初の1冊の取り込み・読書に不要なので add タップ時の要求をやめ、実際にバックグラウンド変換が始まる文脈まで遅延。要求するなら直前に priming 一枚（『変換の進捗を通知で受け取りますか？』＋『あとで』）を挟む。
- **検証ノート**: 直接 launch とpriming不在、結果無視の graceful degradation を実引用で確認。層②の軽微なポリッシュ（ProcessingBanner で in-app 進捗は見えるため通知は価値到達に非必須）＝Minor 維持。

#### [add] 端末資源は起きる前に測る（10-H／10-B ④資源は予測可能）  (CONFIRMED)
- **場所**: `app/src/main/java/com/novelreader/repository/DefaultBookRepository.kt:143-204`
- **症状**: 取り込み（PDFコピー→HTML抽出）開始前に空き容量を計測していない。逼迫時は抽出途中まで進んでから ENOSPC で失敗し、時間と一時ファイルを浪費してから InsufficientStorage に至る。予測可能な資源系失敗を予防でなく事後回復に寄せている。
- **根拠(検証済)**: addBook は :161-163 でtempへ copyTo→:172 sha256→:193-197 `PdfBookExtractor.process(tempFile, bookId, outputDir) { ... }` と進み、開始前の usableSpace/getAllocatableBytes/StorageManager/freeSpace 呼び出しは grep 実測ゼロ。失敗は classifyError(:129) `is InsufficientStorageError -> BookImportError.InsufficientStorage()`・(:136) `"No space left on device" -> BookImportError.InsufficientStorage()` で行動可能文言には至る。10-H『DL/抽出前に空きを測って始めない』の予防が欠落。
- **修正案**: 抽出前に File.usableSpace（または StorageManager.getAllocatableBytes）で必要見込みを下回るなら開始せず『空き容量が足りません（あと約xxMB）』を出す（資源系は具体値可＝08-B①）。
- **検証ノート**: 事前計測の完全な不在を grep ゼロで確認、事後分類は実引用で確認。finder の行番号は概ね妥当（addBook は :143 開始、抽出は :193-197／finder 記載の :158-197 とほぼ整合）。事後回復あり＝致命でない層②軽微＝Minor 維持。

#### [continuity] 公理14候補E/F 再開ハブ message③『いつぶりか』・文脈グラデーション  (CONFIRMED)
- **場所**: `BookCard.kt:54-96 / BookDao.kt:11-15`
- **症状**: 本棚カードは『N話・%バー』(どこまで)は出すが『◯日前』(いつぶりか)を出さず、数週間ぶりの本と昨日の続きが区別できない。
- **根拠(検証済)**: BookCard.kt:54-96 BookProgressRow は `${totalChaps}話`・LinearProgressIndicator・`$percent%` のみを描画し経過時間ラベルが無い。lastReadAt は BookDao.kt:11-15 `ORDER BY MAX(b.addedAt, COALESCE(p.lastReadAt, 0)) DESC` のソート順にのみ使用(表示に未使用)。charter 13 §E原則1『表紙＋進捗＋「◯日前」…①どれを・②どこまで・③いつぶりか』・§F『数日→…「◯日前」』が③を求めるが欠落。
- **修正案**: 保存済み lastReadAt を READING 状態カードへ相対時刻(『昨日/3日前』)で静かに添える。signal(最近順ソート)は既に満たされているため message③の補完のみ。
- **検証ノート**: 行番号一致。ただしデータ損失も導線破綻も無い『表示要素の欠落=拡張提案』＝層②軽微。charter が理想 message の一部として挙げるため Minor は妥当だが違反というより不足。

#### [critic] UX/06 ㉑『入力欄はプレースホルダーのみのラベルにしない（label 使用・placeholderは例示専用）』＋⑥ 入力欄の accessible name  (CONFIRMED)
- **場所**: `検索 DiscoverySearchScreen.kt:217 ／ 本文リンクシート NcodeLinkSheet.kt:121, 337`
- **症状**: 検索入力欄が恒常ラベルを持たず、文字入力後はプレースホルダー『作品名・作者・キーワード』も消えて何の欄か手掛かりが残らない。TalkBack では欄名が読まれない
- **根拠(検証済)**: BasicTextField(:217) の唯一の見出しは decorationBox 内の placeholder Text(:239-245)。Modifier.semantics { contentDescription } は無し。NcodeLinkSheet も同型 BasicTextField(:121,:337)。比較: ReadingSettingsSheet.kt:125 は正しく label= を使用。既出の検索系指摘（0件3原則・スコープ明示・かな正規化）は入力欄のラベル層を扱っていない
- **修正案**: 欄に恒常ラベル（画面内見出し or Modifier.semantics { contentDescription = "検索語" }）を与え、placeholder は例示専用へ降格

#### [d-chrome] Design/09 (a) 経路で変わる挙動／Jakobの法則（慣習の一貫性）  (PLAUSIBLE)
- **場所**: `WebReaderScreen.kt:84-104 vs NativeReadingScreen.kt:781-790`
- **症状**: 同じ『読む』でも PDF 取込作品は中央タップで没入できるのに、なろう Web へ渡ると上部バー常設で没入もタップトグルも効かず、chrome 挙動が経路で別物。
- **根拠(検証済)**: WebReaderScreen.kt:84-105 は標準 Scaffold＋固定 topBar（:86-102 `TopAppBar(title={Text("なろうで読む"...)}...)`）で退避・タップトグル・下部バー・消灯抑止のいずれも無し。一方 NativeReadingScreen は中央タップトグル(:781-790)＋バー退避を持つ。両画面は継続導線で横断する。
- **修正案**: WebReader の媒体差(ADR0012)は正当だが、読む面の chrome 規律を最低限近づける（例: 中央タップで上部バー退避に寄せる）。優先度は低く native 側 A/D/F の是正が先。
- **検証ノート**: コード差は確認どおり。finder 自身が『WebReader はなろう素通し＝常設バー＝常時戻れる（閉じ込め無し）で公理9違反ではない』と認めており、媒体差で大半は正当化される一貫性ニット。Minor 維持だが差の許容可否は人間テスト送り。

#### [d-motion] 08 禁止則①（上限350ms。それを超える duration トークンを彫らない）  (PLAUSIBLE)
- **場所**: `Motion.kt:28（ProcessingBanner.kt:177 が参照）`
- **症状**: PDF処理中バナーの進捗バーが現ステップ目標値へ伸びる整定に400msかかり350ms上限を超える。
- **根拠(検証済)**: Motion.kt:28 `const val MotionDurationProgress: Int = 400`。ProcessingBanner.kt:177 `animationSpec = tween(durationMillis = MotionDurationProgress)`（Animatable.animateTo の smoothing tween）。参照08-禁止則①『上限350ms』／08-D『単発 duration は実質350msが上限』。コメント Motion.kt:26-27 に350超の正当化なし。
- **修正案**: 第一候補=350以下（既存 transition 帯300）へ下げる。④進行ゆえ超過を許すなら Motion.kt に『④進行の smoothing のため上限外』と why を明記。
- **検証ノート**: 400>350 と非トークン正当化欠落は事実。ただし禁止則①の実質根拠は Doherty(操作起点の非ブロッキング遷移)で、これは非ブロッキングな④進行のバー整定＝semantic表で duration『—』枠。④進行 smoothing が350上限の適用対象かは解釈依存＝CONFIRMEDでなくPLAUSIBLE。層②軽微=Minor。

#### [d-motion] 08-C（enter/exit別指定・exitはenterより短い＝加速）／禁止則②（野良既定に委ねずトークン経由）  (CONFIRMED)
- **場所**: `BookshelfScreen.kt:519-522（PDF処理中バナーの AnimatedVisibility）`
- **症状**: バナーの入場と退場が同一時間・同一曲線（Compose 既定 spring）で退場が入場より速くなく、値が Motion.kt を経由しない。
- **根拠(検証済)**: BookshelfScreen.kt:521 `enter = slideInVertically(initialOffsetY = { -it }) + fadeIn()`／:522 `exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()`。enter/exit とも animationSpec 未指定＝Compose 既定 spring で対称。方向は上入り/上出しの逆再生で C の方向要件は充足（良）だが、exit短縮(dismiss150<reveal250)を満たさず値もトークン非経由（禁止則②）。
- **修正案**: enter/exit に Motion.kt トークン化 spec を与え exit を enter より短く（reveal 250 / dismiss 150 相当）。既定任せをやめ明示1本化。
- **検証ノート**: animationSpec ゼロ＝既定 spring 対称、exit短縮なし、トークン非経由を確認。逆方向は満たす点も finding が正しく良と評価。層②軽微=Minor。

#### [d-motion] 08 禁止則②（duration/easing の野良既定禁止＝トークン経由）  (CONFIRMED)
- **場所**: `NativeReadingScreen.kt:944-947（没入クローム復帰ヒントの AnimatedVisibility）`
- **症状**: 復帰ヒント（丸ピル）の出没フェードが Motion.kt を経由せず Compose 既定 spec のまま。
- **根拠(検証済)**: NativeReadingScreen.kt:946 `enter = fadeIn()`／:947 `exit = fadeOut()`（いずれも animationSpec 省略＝既定 spring）。純フェード(effects)で類型は妥当（禁止則④『迷ったらフェード』整合）だが値がトークンを通らない。semantic では alpha のみ変化は crossfade(250 linear)。
- **修正案**: fadeIn/fadeOut に crossfade トークン相当の tween(250, linear) を渡し Motion.kt 一元管理へ。フェード方式自体は妥当なので維持。
- **検証ノート**: 既定 spec のフェードを確認。類型(effects)は正しく、純粋にトークン非経由の drift 源＝層②軽微=Minor。

#### [d-motion] 08 禁止則②（トークン経由）／禁止則④（状態変化はフェード＝妥当）  (CONFIRMED)
- **場所**: `NovelDetailScreen.kt:186（barTitleAlpha の animateFloatAsState）`
- **症状**: 作品詳細スクロールのツールバー題字出没フェードが animationSpec 未指定＝Compose 既定 spring 依存。
- **根拠(検証済)**: NovelDetailScreen.kt:186-189 `val barTitleAlpha by animateFloatAsState(targetValue = if (showBarTitle) 1f else 0f, label = "detailBarTitle")`＝animationSpec 引数なしで既定 spring。alpha のみ変化(effects)で類型は正しい（禁止則④）が値が Motion.kt 非経由（本来 crossfade=250 linear 帯）。
- **修正案**: animationSpec に crossfade 相当の linear tween(250) を渡す。フェード方針は維持。
- **検証ノート**: animationSpec 欠落による既定 spring を確認。フェード類型は妥当、トークン非経由のみが問題＝層②軽微=Minor。

#### [d-token] charter(a) theme/外の Color(0x…)直書き禁止 / ADR 0014 §C『区切り線の色はヘアライントークンのみ』 / ADR 0005 色直書き禁止  (CONFIRMED ⚠️調整)
- **場所**: `ui/components/ShioriCover.kt:279`
- **症状**: テーマ非追従になりうる生色リテラルがシップUIに1件。テーマ/意匠改訂時この罫だけ取り残される
- **根拠(検証済)**: ShioriCover.kt:279 `val borderColor = if (isDark) Color(0x0DFFFFFF) else Color(0x0D1C1F26)` ＝ theme/ 外での生 ARGB リテラル(黒/白+生 alpha 0x0D=5%)。栞書影の内枠5%罫で、ヘアライントークンでなくリテラル合成。ship-UI の Color(0x 直書きはこの1件のみ(他は @Preview)。
- **修正案**: cs.onSurface/ヘアライントークンを正本に、5%は ShioriCover 内の名前付き alpha 定数で1回宣言し2分岐を畳む。次善=『栞書影内枠』専用ヘアライントークンを Color.kt へ昇格。
- **検証ノート**: リテラル実在は CONFIRMED。ただし isDark 分岐で明/暗を手動対応済み＝実効はテーマ追従し、単一の装飾5%内枠。実害小(層②軽微)につき指摘の Major→Minor へ降格。

#### [d-token] KB03 §4 primitive 直参照の漏れ / 実装チェックリスト『Color(0x のヒットがゼロ』  (CONFIRMED)
- **場所**: `ui/compose/RubyText.kt:243,264,279`
- **症状**: プレビュー色が ReadingColors.LIGHT.ruby と二重帳簿になり、トークン改訂時プレビューだけ旧値で残る
- **根拠(検証済)**: RubyText.kt:243/264/279 いずれも `rubyColor = Color(0xFF8B96A0), // プレビュー用＝ReadingColors.LIGHT.ruby と同値`。Theme.kt:63 `ruby = Color(0xFF8B96A0)` と同値＝写経による二重管理。3箇所とも @Preview 関数内(RubyTextPreview_Normal/Bold/LineWrap)でシップUI非搭載。
- **修正案**: プレビューで ReadingTheme.LIGHT.colors.ruby を参照し生リテラルを削除(値の単一化)。
- **検証ノート**: 3行の行番号・リテラル・同値関係すべて一致。@Preview 限定=開発時資産で影響限定につき Minor 妥当、維持。

#### [d-type] Design/10 §9（即席 copy(alpha=) は lint 対象・専用シェードを彫れ）・§6（色地+alpha で washed out）  (CONFIRMED ⚠️調整)
- **場所**: `NativeTableOfContentsScreen.kt:149 / NcodeLinkSheet.kt:148,357,392 / ReadingErrorScreen.kt:48`
- **症状**: セピア/ダーク地で補助テキストが色褪せ・非活性風になり可読性と階層が同時に崩れる。
- **根拠(検証済)**: NativeTableOfContentsScreen.kt:149 `color = colors.textSecondary.copy(alpha = 0.75f),`（TOC エラー本文）。NcodeLinkSheet.kt(colors:ReadingColors を :80 で確認):148 `color = colors.textSecondary.copy(alpha = 0.6f)`（placeholder『作品名を入力』）・:357 同（placeholder『N1234AB』）・:392 `color = if (isValid) colors.background else colors.textSecondary.copy(alpha = 0.6f),`（無効時ボタン文字）。ReadingErrorScreen.kt:48 alpha0.75。元 textSecondary が既に LIGHT 3.79/SEPIA 3.28 で、alpha0.6 合成後は LIGHT 2.06/SEPIA 1.93/DARK 2.50 まで沈む。
- **修正案**: alpha 削除し、テーマ×階層段の専用シェードトークンを彫って参照（InfoText/UnreadSeiji 先例）。
- **検証ノート**: 全サイト実在を確認。パターン指摘は妥当。ただし NcodeLinkSheet:148/357 は入力プレースホルダ、:392 は無効化ボタン文字＝WCAG 1.4.3 は inactive component / placeholder を概ね対象外とするため個別の必達度は低い。§9 の『alpha でなく専用シェード』というコード衛生観点で Minor 妥当（層②軽微）。TOC:149 と ErrorScreen:48 は機能テキストで [1] と重複。

#### [errtext] 公理10 §A/§C 言う前に立て直す（自動回復＝短いリトライを先に試す）  (PLAUSIBLE ⚠️調整)
- **場所**: `NovelApiRepository.kt:102-132 / NarouNetwork.kt:25-32 / 発火点 DiscoveryViewModel.kt:231, NovelDetailViewModel.kt:138-139, BookshelfViewModel.kt:392-393`
- **症状**: Wi-Fiの一瞬の途切れ・単発の503/429・DNSもたつき等の一過性失敗が、自動リトライを挟まず即エラー表示になり、人が手で『再試行』を押す（アプリのリトライ仕事の肩代わり）。体感信頼性が下がる。
- **根拠(検証済)**: コード事実は全て確認。wrapApiException(NovelApiRepository.kt:105-131)は分類後 初回失敗で即 `throw NarouApiException(...)`(116/122/127/130) し、リトライ機構なし。NarouNetwork.kt:25-32 は `.addInterceptor(UserAgentInterceptor())` と `.callTimeout(30, TimeUnit.SECONDS)` のみで retry インターセプタ無し（OkHttp 既定 retryOnConnectionFailure は 5xx/429/read-timeout を再試行しない＝技術的主張も正確）。捕捉点は DiscoveryViewModel.kt:231 `} catch (e: NarouApiException) {` →232 `DiscoveryUiState.Error(e.userMessage)`、NovelDetailViewModel.kt:138 `} catch (e: NarouApiException) {` →139 `NovelDetailUiState.Error(e.userMessage)`、BookshelfViewModel.kt:392 `} catch (e: NarouApiException) {` →393 `NcodeSearchUiState.Error(e.userMessage)`。いずれも自動回復(2)を経ず翻訳(08)へ直行。
- **修正案**: wrapApiException（単一集約点）で retryable クラス（IOException/timeout・5xx・429）に限り base=500ms cap=4s の指数バックオフ+Full Jitter で最大2回自動再試行し、諦めた残余のみ現行文言へ落とす。429は Retry-After 尊重。4xx(429除く)は現状どおり非リトライ維持。
- **検証ノート**: 根拠は完全確認（REFUTEDにあらず・修正提案の集約点も妥当）。ただし Major→Minor へ降格: 全失敗面に手動リトライ導線が既存（DiscoveryViewModel.retryNcodeSearch/loadMoreResults、NovelDetailViewModel.retry():144、tocRetryKey/retryKey・ReadingErrorScreen onRetry）でユーザーは詰まらず、自動リトライは公理10§Cの理想＝機能欠陥でなく強化提案。影響（flaky網での体感信頼性）は層②で緩和済み。

#### [errtext] 08§C 内部事情の翻訳（生Java例外文言/内部パスの露出禁止・消す元はUI/消す先はログ）  (CONFIRMED ⚠️調整)
- **場所**: `NativeReadingScreen.kt:442（章parse例外）/ :275（目次例外）→ ReadingErrorScreen.kt:45 で表示`
- **症状**: 章/目次の読取り例外時、生の例外メッセージ（FileNotFound系の絶対パス『/data/user/0/…/open failed: ENOENT』等）がそのまま読書エラー画面へ出る。内部パス/例外文言の露出で不信を招く。
- **根拠(検証済)**: NativeReadingScreen.kt:442 `ParseResult.Error(e.message ?: "不明なエラー", currentFile)`、:275 `TocState.Error(e.message ?: "目次の読み込みに失敗しました")` が e.message を直送。表示経路確認: :833 `is ParseResult.Error -> ReadingErrorScreen(` :834 `message = result.message,` → ReadingErrorScreen.kt:45 `text = message,`。ChapterHtmlParser は :22 `if (!file.exists()) return null`／:41 `if (!file.exists()) return emptyList()` で欠損は防ぐが、その後 :23/:42 `Jsoup.parse(file, "UTF-8")` は読取り中IOでthrowし message にパスを含み得る。既定文言は e.message==null のときだけ勝つ＝実運用では生message優先。他所（NarouApiException翻訳・normalizeImportErrorMessage）と不整合。
- **修正案**: 引く: :442/:275 の `e.message ?:` を捨て常に固定文言（『ファイルの読み込みに失敗しました』/『目次の読み込みに失敗しました』）を出し、原因 e は Log.e へ退避。
- **検証ノート**: コード事実（生message→UI・表示経路）はCONFIRMED、08§C逸脱は成立。ただし Major→Minor へ降格: exists()ガード(:22/:41)で常用トリガのENOENT-at-openは到達せず、残余は読取り中の稀なI/O・エンコード失敗（後者はパスを含まないことが多い）で実発生確率は低い＝層②の顕著性は軽微。攻撃者境界なし（ローカル・端末所有者表示、08§F）で Critical 相当ではない点は finder と一致。

#### [errtext] 08§D 次の一手（持続性失敗に無効な行動を出さない）/ 公理10§B 全部リトライの匂い  (CONFIRMED)
- **場所**: `PdfProcessingService.kt:376（emitError で常に retryUri 付与）→ BookshelfScreen.kt:229-238`
- **症状**: パスワード付き/破損PDFの取込失敗でも『再試行』が出る。押しても同一URI＝同一PDFが決定的に同じ失敗を再走するだけで直らず、無効な救済策を1回無駄打ちさせる。
- **根拠(検証済)**: PdfProcessingService.kt:369 `onFailure = { e ->` の中で :372 `val msg = normalizeImportErrorMessage(e)`（:454 `if (e is BookImportError) return e.userMessage`＝分類済みを保持）にもかかわらず :376 `app.emitError(msg, uri.toString())` で一律 retryUri を付与。BookshelfScreen.kt:229 `if (event.retryUri != null) {` →232 `actionLabel = "再試行",`。分類は実在: BookImportError.EncryptedPdf("パスワード付きPDFは現在サポートしていません")/CorruptedPdf("…破損しているか、読み取れません")/InsufficientStorage（BookshelfViewModel.kt:35-40, DefaultBookRepository.kt:128-136）。破損・暗号化は決定的持続性でリトライ無意味。
- **修正案**: 引く: Encrypted/Corrupted では retryUri=null を渡し『再試行』を出さない（『閉じる』のみ）。空き容量不足は間に解消され得るので再試行を残す。文言は現状可。
- **検証ノート**: 全根拠を確認。層②の軽微な無効アフォーダンスで Minor は妥当（維持）。

#### [errtext] 08§B① 行動不能な内部理由は伏せる / §H 半分に削る（Krug 第3法則）  (CONFIRMED)
- **場所**: `PdfProcessingService.kt:225（onTimeout の emitError 文言）`
- **症状**: 『変換が時間制限により中断されました。アプリを開き直すと再開します。』の『時間制限により』は dataSync FGS の実行時間上限というユーザー関与不能の内部事情で、読み手が一瞬戸惑い行動には無関係。
- **根拠(検証済)**: PdfProcessingService.kt:225 `it.emitError("変換が時間制限により中断されました。アプリを開き直すと再開します。")`。文脈確認: :200 `Log.w(TAG, "FGS タイムアウト(dataSync 実行時間上限)により処理を中断")` の onTimeout 経路＝『時間制限』は FGS 実行時間上限で行動可能性なし。actionable な後半のみで意味は保てる。
- **修正案**: 引く: 『変換が中断されました。アプリを開き直すと再開します。』へ短縮（内部理由『時間制限により』を削除）。
- **検証ノート**: 文言実在と内部理由（FGS上限）性は確認。08§B①適用は妥当だが主観的な文言微調整＝層②軽微で Minor 維持。

#### [evolve] 公理23-F 全開始点の移行テスト（移行テストの無いスキーマ変更は願望）  (CONFIRMED ⚠️調整)
- **場所**: `MigrationTest.kt:40-42 / AppDatabase.kt:58-66 (MIGRATION_3_4)`
- **症状**: データを詰め替える唯一の移行 MIGRATION_3_4 に『データ入り』回帰テストが無く、退行しても実機投入前に気づけない（読書位置消失の火種）。
- **根拠(検証済)**: MigrationTest.kt:40-42 KDoc『MIGRATION_3_4〜6_7 も AppDatabase に実装は在るが…区間外・対象外とした』を実確認。データ保存テスト migrate7to17_preservesExistingRows は line116 `helper.createDatabase(TEST_DB_DATA, 7)` ＝v7始点で、AppDatabase.kt:62-66 の `INSERT INTO progress_new SELECT bookId, lastRead` → `DROP TABLE progress` → `RENAME TO progress` はデータを持った状態で一度も実行されない。他の12本は全て ADD COLUMN/CREATE/DROP/no-op でテーブル再構築を伴うデータ移送は 3_4 のみ＝『唯一』も事実。schemas/3〜6.json 実在も ls で確認。
- **修正案**: 第一候補『引く』＝v3未満スキーマの実機が実在しないと確証できるなら MIGRATION_3_4 と schemas/3〜6.json をチェーンごと削除し migration floor を v7 へ上げる（保守面積の純減）。確証できないなら migrate3to7_preservesExistingRows を追加し lastRead 入り v3 DB→v17 で lastReadFilename に保存されることをアサート。
- **検証ノート**: ファインダー引用 MigrationTest.kt:62-64 は正確には migrate7to17_validatesSchemaAtEachStep（スキーマ検証のみ）の v7 始点で、データ保存テストの v7 始点は line116だが、両テストとも v7 始点で 3_4 未実行という結論は変わらず主張成立。severity Major→Minor に降格: (1) app 未公開(versionCode=1)、(2) 3_4 は凍結済み単純レガシー DDL で改変可能性低、(3) v3 実機の存在が未確認（指摘[2]自身が未確定と認める）＝層①データ損失は二重に条件付きで実害は投機的。凍結移行のテスト欠落は層②の保守健全性問題に留まる。

#### [evolve] 公理23-E 設定は意味を保存して運ぶ / 19-I schemaVersion 予約席  (CONFIRMED)
- **場所**: `MainActivity.kt:88,128 / SearchHistoryStore.kt:80-82`
- **症状**: 設定(app_prefs)・検索履歴(DataStore)に schemaVersion キーが無く、テーマは enum 定数名の生文字列保存。将来 enum 改名/削除で選択が黙ってシステム既定へ差し替わる（意味保存移行の足場が無い）。
- **根拠(検証済)**: MainActivity.kt:88 `prefs.edit().putString("reading_theme", theme.name).apply()` で enum 名を保存、line128 `return runCatching { ReadingTheme.valueOf(saved) }.getOrDefault(systemFallback)` で未知名を静かに既定へフォールバック（line123 コメントも『enum名変更時はシステム追従へフォールバック』と宣言破棄を自認）。SearchHistoryStore.kt:80-82 `preferencesDataStore(name = "narou_search_history", corruptionHandler = ...)` に version キー無し。grep -riE 'schemaVersion|SharedPreferencesMigration|SCHEMA_VERSION' src/main ＝NO HITS。
- **修正案**: app_prefs / DataStore に settings_schema_version キーを1つ置き、enum 改名時は旧名→新名の意味保存マッピングを移行として書く（成功確認まで旧値保持）。現行フォールバックは防御として妥当だが『宣言を黙って捨てる』点を移行で埋める。
- **検証ノート**: コード事実は全て実在・行番号も一致で CONFIRMED。ただし現時点で active な不具合ではなく、enum 改名という未来事象で初めて実害化する潜在的・予防的ギャップ。null 判定(line127 `?: return systemFallback`)による既定追従は正しく実装済み。層②の将来堅牢性として severity Minor は妥当（据え置き、降格不要）。

#### [gesture] 公理17 C-1 語彙の実領域一致（ヒント文言＝実タップ領域） / G 記憶可能性 / D 可視代替  (CONFIRMED ⚠️調整)
- **場所**: `NativeReadingScreen.kt:570-589,:741-743,:960,:781`
- **症状**: chrome復帰ヒント文言が『画面中央をタップ』だが実タップ領域は全面(fillMaxSize)＝語彙と実装がズレる。ヒントは通算初回のみ表示・再表示メニュー無し。
- **根拠(検証済)**: NativeReadingScreen.kt:571-573 `chromeHintConsumed = ... prefs.getBoolean("immersive_hint_shown", false)`、:579-587 で `chromeHintConsumed=true` 化＋ `putBoolean("immersive_hint_shown", true)` 永続化＝通算初回のみ点灯。:960 `text = "画面中央をタップでメニュー表示"`。しかしタップ判定は :781-790 の Box(fillMaxSize) 全面 detectTapGestures＝『中央』は実領域(全面)より狭い記述。復帰の可視代替は :741-743 `if (consumed.y > 0){ topAppBarState.heightOffset = (...).coerceAtMost(0f) }`＝上スクロールで topBar 復帰(bottomBar も :852 collapsedFraction連動で復帰)。ReadingSettingsSheet に『操作ガイド再表示』は存在せず(全読了で確認)。
- **修正案**: 文言を『画面をタップでメニュー表示』へ1語修正(実領域=全面と一致)＝Minor欠陥の即時解消。菜単消失リスクは上スクロールの可視代替が機能するため当面成立。2週間後・無説明『メニューを出して』再テストで記憶可能性を実測(人間テスト送り)。
- **検証ノート**: 全コード実体を確認しCONFIRMED。ただし『メニューが分からなくなりうる』という警告的フレーミングは、上スクロール可視代替(:741-743)が確認できたため部分的に緩和される。確定できる欠陥は文言/実領域の語彙ズレ(層②軽微=Minor)のみ。記憶可能性は人間テスト送りだがそれ自体はコード欠陥でない。severity を 人間テスト送り→Minor へ調整(確定的に修正可能な語彙欠陥が実在するため)。

#### [ia] 15-§C 一語一義（公理1の語彙版）・タップした語＝行き先の名前（Krug Ch6）  (CONFIRMED)
- **場所**: `DiscoveryResultScreen.kt:146／DiscoveryHomeScreen.kt:122／BookshelfScreen.kt:445／DiscoverySearchScreen.kt:171`
- **症状**: 発見エリアが『見つける／探す／検索/発見』の4語で呼ばれる。戻る『発見に戻る』の着地は『見つける』画面、本棚『小説を探す』の着地も『見つける』（探す＝検索入力を期待して押すと閲覧ホームに着く）。
- **根拠(検証済)**: DiscoveryResultScreen.kt:146 `contentDescription = "発見に戻る"`(onUp→発見ホーム)だが着地 DiscoveryHomeScreen.kt:122 `text = "見つける"`。BookshelfScreen.kt:445 `contentDescription = "小説を探す"` も MainActivity:181 で同じ『見つける』へ着地。真の検索画面は DiscoverySearchScreen.kt:171 `text = "探す"` で更に1階層下。§C『ナビのラベル→画面タイトルを全対応で突き合わせる』で不一致。
- **修正案**: 画面タイトルを正本に語を統一。『発見に戻る』→着地名(見つける)、本棚アイコン説明も着地(見つける)へ。用語辞書1枚化。
- **検証ノート**: 主張どおり不一致成立、CONFIRMED。行番号は微ズレ(発見に戻る=:146でファインダー147／探す=:171でファインダー174＝Textブロック内のズレ)だが実体は一致。層②軽微でMinor維持。

#### [ia] 15-§G 共有地の悲劇（本棚追加は続きから読むを薄めない・立証責任は足す側）  (CONFIRMED)
- **場所**: `BookshelfScreen.kt:582-585／638-643／442／763`
- **症状**: 本棚を開くと先頭に『見つける導線帯』＋フィルタ行が居座り、支配的タスクの先頭の本が1〜2行ぶん下がる。帯はtop-barの🔍と機能重複。
- **根拠(検証済)**: BookshelfScreen.kt:582-585 グリッドで `item(span=...) { FindGuideBand(onClick = onOpenDiscovery) }` を books(items :597)より前に描画、:638-643 リストも同様。条件 :582 `if (shelfItems.isNotEmpty() || selectedStatus != null)` で本があると帯＋チップ行の2ヘッダが常に本の上。top-bar :442 に discovery 🔍あり、帯自身のコメント :763 `TopAppBar の🔍と役割が重なる`。
- **修正案**: 引く＝FindGuideBand撤去し発見入口を常設🔍へ一本化。ただしモック正本との緊張あり（下記）。
- **検証ノート**: 事実(帯が先頭item・🔍と自認重複)は全てCONFIRMED。ただし§Gの『審査』観点で明確な公理違反ではなく、且つモック正本 bookshelf-fusion-D の .find-guide 由来でコメントが帯=発見未知者への明示導線／🔍=既知者ショートカットと弁明(CLAUDE.md: 見た目はモックが正本)。よって『撤去』修正はモック正本/ADR0005経由で判断すべき。1スクロールで回復＝カヤック免罪則でMinor。StatusChipRowは§G③規模装備で正当(帯のみが論点)。

#### [ia] 15-§F スコープ変更は先出ししない（選択肢は結果画面に置く・Krug Ch6）  (CONFIRMED)
- **場所**: `DiscoverySearchScreen.kt:270-321`
- **症状**: 検索実行前に『タイトル/キーワード/作者名/あらすじ』の範囲チップを提示し、入力前にスコープ選択を促す。範囲変更の需要は多すぎ/0件と分かって初めて生まれる。
- **根拠(検証済)**: DiscoverySearchScreen.kt:270-283 `Text("検索範囲"...)` セクションを入力欄直下に常時先出し、:297-321 で FilterChipItem 4つ(タイトル/キーワード/作者名/あらすじ)を FlowRow で提示。既定 SearchDraft.kt:103 `val inTitle: Boolean = true` で最低限は動く。§F『スコープ変更の選択肢は先出ししない…選択肢は結果画面に置く』に反し前倒し。
- **修正案**: 既定(タイトル)で即検索させ、範囲チップは既定折り畳み or 結果画面『条件を変更』へ寄せる。
- **検証ノート**: コード実体一致でCONFIRMED（inTitle既定trueはファインダー104に対し:103＝隣接プロパティのズレ）。ただし§Fが第一に禁ずるのはコーパススコープ(蔵書横断/この本の全文)の先出しで、本件はフィールド一致facet＝『0件という嘘』の危険度が低く軽減余地あり(ファインダーも自認)。:288-290 F-H制約注記あり意匠はモック由来の可能性。カヤック免罪則でMinor維持。

#### [ia] 15-§E 目次は現在位置＋既読の視覚区別（見当識『残りどれだけ』・目標勾配）  (CONFIRMED)
- **場所**: `NativeTableOfContentsScreen.kt:216-246`
- **症状**: 目次は現在章だけ強調し、現在章より前(既読)と未読の視覚区別が無く『あとどれだけ残っているか』の見当識が弱い。
- **根拠(検証済)**: NativeTableOfContentsScreen.kt:216 `val isCurrent = index == currentIndex` のみで分岐し、:222 背景 `if (isCurrent) colors.accent.copy(alpha=0.06f) else Color.Transparent`、:234 左バー `if (isCurrent) colors.accent else Color.Transparent`、:242 `fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal`、:245 色 `if (isCurrent) colors.accent else colors.text`。既読(index<currentIndex)の淡色化が無い。currentIndex(:195)から導出可能なのに未使用。§E『現在章の強調＋既読章の視覚的区別』の後半欠落。
- **修正案**: 追加＝現在章より前をグレー(＋ウェイト併用・05H色だけに頼らない)で既読表示。currentIndexで導出可、データ追加不要。
- **検証ノート**: 実体・§Eとも一致しCONFIRMED。線形読書前提で既読=index<currentIndexは妥当な導出(章別既読フラグは持たず現在位置から推定)。追加系・見当識の弱化に留まり層②軽微でMinor。

#### [ia] 15-§G③ 絞り込みは各値に件数を添え0件の袋小路を予防（公理8近傍）  (CONFIRMED)
- **場所**: `BookshelfScreen.kt:726-746`
- **症状**: 『よみかけ/未読/読了』チップに件数が無く、0件になる分類(例:読了0冊)も普通に押せて空表示に落ちる。
- **根拠(検証済)**: BookshelfScreen.kt:713-748 StatusChipRow、:726-746 で固定4チップ(すべて/よみかけ/未読/読了)を件数なしで表示。0件時は :752-759 `StatusFilterEmptyText`＝`Text("この分類の本はありません"...)` を出し明示・回復容易。§G③『各値に件数を添えて0件の袋小路を予防(0件値を平然と見せるのは公理8違反)』。
- **修正案**: 引く＝0件チップをdim/非表示、または次善で各チップに件数(chapterCountMap+readingStatusForでローカル算出)を添える。
- **検証ノート**: 件数なし・0件チップ押下可・空文言回復とも実体一致でCONFIRMED。§G③は『平然と見せる＝公理8違反』とやや強い表現だが、:752-759 の空状態文言が袋小路を緩和(即気づき/自力復帰/動揺なし)＝カヤック免罪則成立でMinor維持。

#### [idempo] UX/16 H ヒューマンエラー（確認は対象を視覚的主役に／不可逆コストの明示）  (CONFIRMED)
- **場所**: `BookshelfScreen.kt:690-704（削除確認ダイアログ）`
- **症状**: 唯一の防護である確認ダイアログが取り違えを止めきれない造り。消す対象（題名）が本文中のインライン平文で視覚的主役でなく、文言が『読書進捗も削除されます』止まりで、変換済み本文の削除＝要再変換という真の不可逆コストを過小表示。
- **根拠(検証済)**: BookshelfScreen.kt:692-693 `title = { Text("削除の確認") }` / `text = { Text("「${book.title}」を削除しますか？\n読書進捗も削除されます。") }`。題名は独立強調なしのインライン平文で、表紙が無いアプリ（ListBookCard は題字＋色帯のみ）ゆえ 16-H の『表紙とタイトルを大きく』での取り違え発見が効かない。文面に HTML削除・要再変換・取込元PDF非保持（BookEntity.kt で確認）の不可逆コストが無い。
- **修正案**: Finding 1 の Undo 化で本ダイアログごと撤去が第一候補。確認を残す場合の次善: 題名を独立行で大きく主役化し、文言を『変換した本文も削除され、元に戻せません』へ改め不可逆を明示。
- **検証ノート**: 引用テキストは :693 に完全一致で実在。題名がインライン平文である点も確認。文言が不可逆コスト（HTML/再変換）を欠く点も事実。層②の軽微な文言・情報設計品質で Minor 妥当。

#### [notify] 公理13-D アプリ内vs通知の出口の使い分け（§84二重報告・§86 foreground判定・適用例§152）  (CONFIRMED ⚠️調整)
- **場所**: `PdfProcessingService.kt:355-361,514-524（foreground判定は全ツリー不在）`
- **症状**: 本棚を前面で見て取込を待つ最中に変換完了すると、本棚に新しい本が現れる上にシステム通知トレイにも『変換完了』が積まれる二重報告になる。
- **根拠(検証済)**: PdfProcessingService.kt:361 `showCompletionNotification(outcome.book.id, outcome.book.title)` は fold(onSuccess) 内で前面/背面を問わず無条件呼び出し。:523 `notificationManager().notify(NOTIFICATION_ID, notification)`＝前面判定なしで notify。grep 実測: ProcessLifecycleOwner / isAppInForeground / ActivityLifecycleCallbacks はコード中に存在せず（唯一 NativeReadingScreen.kt:549 の LocalLifecycleOwner は無関係）。前面では uiState が Room Flow(allBooks) で完了本を反応表示するため in-app には既に伝わる＝§152 の悪い例。
- **修正案**: 前面時（ProcessLifecycleOwner で foreground 判定）は showCompletionNotification をスキップし本棚の反応表示＋バナー消失に委ね、背面時のみ通知する。
- **検証ノート**: コード事実（foreground 出口振り分けの完全不在・無条件 notify）は CONFIRMED。ただしチャネルは IMPORTANCE_LOW（無音）かつ完了は NOTIFICATION_ID 単一上書き（滞留は1件のみ）で、前面時の実害は無音のシャド一件に留まる＝層②軽微。Major→Minor に降格。

#### [notify] 公理13-D §87 stale 通知の取り下げ（用が済んだら cancel）  (CONFIRMED)
- **場所**: `PdfProcessingService.kt:514-524 / NewEpisodeCheckWorker.kt:106-107`
- **症状**: 『変換完了』『続きが N 話更新』通知をタップせずアプリを開いて当該の本を読んでも、確認済みなのに通知トレイに未読の顔で残り続ける。
- **根拠(検証済)**: 後始末は setAutoCancel(true) のみ＝タップ経路でしか消えない: PdfProcessingService.kt:519 `.setAutoCancel(true)`、NewEpisodeCheckWorker.kt:102 `.setAutoCancel(true)`。アプリ/本を開いた時点で消す cancel は全ツリー不在（grep で notificationManager().cancel / NotificationManagerCompat…cancel / manager.cancel = 0 件）。§87『本を開いた時点で cancel』が未実装。修正着地先の MainActivity.kt:146 `LaunchedEffect(deepLinkBookId)` は実在。完了は NOTIFICATION_ID 単一上書きで滞留窓が限定的＝Minor。
- **修正案**: 完了 deep link 着地時（MainActivity の LaunchedEffect(deepLinkBookId)）と該当 ncode の本を開いた時点で NotificationManager.cancel(NOTIFICATION_ID)/cancel(tag=new_episode_ncode) を呼び既読通知を取り下げる。
- **検証ノート**: 行番号・引用・grep 全て一致。Minor 妥当。

#### [notify] 公理13-E §2 setOnlyAlertOnce / §109 更新頻度の間引き  (CONFIRMED)
- **場所**: `PdfProcessingService.kt:467-489 / 更新呼び出し :349`
- **症状**: 取込進捗通知が onProgress のたびに再 notify されるが setOnlyAlertOnce が無く、%が変わらなくても高頻度で更新される。
- **根拠(検証済)**: buildProgressNotification（PdfProcessingService.kt:467-485）に setOnlyAlertOnce の呼び出しは無い（grep setOnlyAlertOnce = 0 件）。updateProgressNotification（:487-489）は `notificationManager().notify(NOTIFICATION_ID, buildProgressNotification(...))` を無条件実行し、これが onProgress コールバック内 :349 から毎回呼ばれる＝%変化での間引き無し。ただしチャネルは IMPORTANCE_LOW（NovelReaderApplication.kt:169）で音は出ないため E§2『音は最初の1回』は実質成立済み、残る実害はシステム側更新/描画コストのみ＝Minor。
- **修正案**: buildProgressNotification に .setOnlyAlertOnce(true) を付け、updateProgressNotification は progress 値が前回から変化した時だけ notify する（%スロットリング）。
- **検証ノート**: 事実一致。IMPORTANCE_LOW 前提で音の実害は無く、間引きは描画/システムコストの最適化に近い＝Minor 妥当。

#### [notify] 公理13-F §120 権限は理由とセットで（priming UI を挟む）  (CONFIRMED)
- **場所**: `BookshelfScreen.kt:152-165（notificationPermissionLauncher.launch:160）`
- **症状**: 初回取込時に、通知が何に使われるかの説明(priming)を挟まず、いきなり OS の権限ダイアログが出る。
- **根拠(検証済)**: launchPdfPicker（BookshelfScreen.kt:152-165）の未許可分岐 :159-160 は `else { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }` とシステムダイアログへ直行し、間に理由ダイアログ（OK/今はしない）が無い。要求タイミング自体は『最初の取込をユーザーが開始した時』で正しい（起動直後要求という NN/g 筆頭の誤りは回避）。
- **修正案**: notificationPermissionLauncher.launch の前に、取込進捗をバックグラウンドでも知らせるためと明示する軽い理由ダイアログ（OK/今はしない）を挟む。
- **検証ノート**: priming 不在は CONFIRMED。タイミングは適切なため層②軽微＝Minor 妥当（ベストプラクティス補強であり実害ではない）。

#### [persist] 公理6 永続性（スクロール位置＝ユーザーの資産）  (CONFIRMED ⚠️調整)
- **場所**: `DiscoveryResultScreen.kt:385 / DiscoveryViewModel.kt:147,:205,:263-264,:457-459`
- **症状**: 結果一覧で『さらに読み込む』を重ねて深くスクロール中にプロセスdeath→再入場すると、積み上げた追加ページが消え初回ページ相当（先頭）へ戻る。回転（同一プロセス・VM生存）では保たれる。
- **根拠(検証済)**: 追加結果は DiscoveryViewModel.kt:263-264 `val mergedNovels = current.novels + next.novels` / `_resultState.value = current.copy(novels = mergedNovels,` でメモリ上VM状態にのみ蓄積。SavedStateHandle への退避は :147 `savedStateHandle[KEY_RESULT_CONTEXT] = _resultContext.value` の ResultContext のみで novels/paging は非対象。process death 復帰の init は :457-459 `savedStateHandle.get<ResultContext>(KEY_RESULT_CONTEXT)?.let { restored -> _resultContext.value = restored; loadResult() }` で文脈だけ復元し loadResult→:205 `fetchResultFirstPage(ctx.query)` で初回ページのみ再取得。DiscoveryResultScreen.kt:385 `LazyColumn(` は state 引数を渡さず暗黙 rememberLazyListState（Saver 有りで保存済み index を持つ）だが、復元後リストが1ページへ縮み深い index がクランプされ位置喪失。
- **修正案**: novels+paging も SavedStateHandle へミラーし復帰時に積み上げ全体を再構成（Parcelable 化コスト有り）。代替は復帰時に明示的に先頭へ戻し劣化を限定。機能削除の『引く』は不可。
- **検証ノート**: 機構は静的解析で確定（CONFIRMED）。ただしトリガはプロセスdeath限定（回転はVM生存で保たれる＝:73-81のViewModelスコープ）で、対象は再取得可能な検索ブラウズ状態（ユーザー生成物・読書位置ではない）かつ回復可能。層②のスクロール/ページ深度喪失で比較的低頻度イベント＝Minorへ調整（要検証→Minor）。

#### [persist] 公理6 永続性（読書位置がページ番号のみ・話内スクロール非追随）  (CONFIRMED)
- **場所**: `WebReaderViewModel.kt:27-28 / WebReaderScreen.kt:65-66`
- **症状**: Web読書の再開は『第N話の先頭』までで、話の途中まで読んでも再入場すると話冒頭に着地する（話内スクロール非保存）。ネイティブPDF読書が scrollIndex/offset まで復元するのと非対称。
- **根拠(検証済)**: WebReaderViewModel.kt:27 `fun onEpisodeReached(ncode: Ncode, episode: Int) {` → :28 `viewModelScope.launch { bookRepository.recordWebReadingEpisode(ncode, episode) }` で episode(Int) のみ保存＝話内スクロール量を持たない。再入場 WebReaderScreen.kt:65-66 の startUrl も話URLのみ（narouEpisodeUrl(ncode, startEpisode)）で話頭着地。WebReaderScreen.kt:40-43 のクラスdoc『この読書 WebView は…JS を一切注入しない（scrollIntoView すら行わない）…evaluateJavascript を足すことは規約違反』（ADR 0010/0012）で話内位置の復元は構造的に不可＝設計上の受容境界。
- **修正案**: 規約上『引く/改変』不可。話単位再開は許容範囲だが『第N話のはじめから』等のUI表記で嘘のない期待にする（追加は次善）。ADR 0012 の受容境界として明文化。
- **検証ノート**: コード挙動（話単位保存・話頭着地）は事実として確認＝CONFIRMED。ただし JS注入禁止（ADR 0010/0012）により話内スクロール復元は構造的に達成不能で、話単位復帰が達成可能な最善。副経路かつ1話は数画面程度で回復容易＝Minor 維持が妥当（charterの『ページ番号のみ』アンチパターンだが本件は規約強制の受容境界）。

#### [portable] 公理18候補 D 再結合キー／実体とメタデータの疎結合  (CONFIRMED ⚠️調整)
- **場所**: `BookEntity.kt:10,28 / DefaultBookRepository.kt:115-116,172-173,233 / ProgressEntity.kt:8`
- **症状**: 仮にメタデータ層を復元しても、復元後に同じPDFを入れ直したとき昨日の位置が自動で戻らない。復元 book 行が壊れた絶対パスを指したまま『既存扱い』され位置が孤児化する恐れ。
- **根拠(検証済)**: BookEntity.kt:10 `val htmlDirPath: String` は DefaultBookRepository.kt:233 `BookEntity(bookId, meta.title, outputDir.absolutePath, …)` で端末ローカル絶対パスを保持。bookId は :152 `UUID.randomUUID().toString().take(8)` で端末ローカル生成＝端末跨ぎの再結合キーにならない。contentSha256(BookEntity.kt:28) は DefaultBookRepository.kt:172-173 `sha256Hex(it)`→`findExistingBookByHash`(:115-116) の重複排除専用で、portability 用途には未配線。ProgressEntity.kt:8 は bookId 紐付け。=再結合の材料は揃うが繋がっていない、は事実どおり。
- **修正案**: 復元導線を作る際、htmlDirPath は復元値を信用せず起動時に bookId から再導出。dedup-by-hash が『同ハッシュだが実体欠損』を検出したら重複スキップでなくHTML再生成＋progress再結合へ分岐。既存 contentSha256 を再結合キーへ昇格すれば実装コストは小さい。
- **検証ノート**: コード実体（絶対パス htmlDirPath・device-local bookId・dedup 専用 sha256）は全て CONFIRMED。ただし本 finding の実害は『バックアップ経路が存在してから』初めて顕在化する完全な下流（finding 自身も『実害は Finding1/2 の下流』と明記）。現状 restore 経路ゼロで生きた被害なし＋材料既在で修正安価なため、層②軽微＝Major→Minor へ降格。

#### [reach] 21-D 版面の自律性（最大幅は字数×実効フォントで定義／行間は行長に連動＝二重比例則）  (CONFIRMED)
- **場所**: `ChapterContent.kt:142（widthIn(max=600.dp)）＋ :73（lineHeight = lineHeightEm.em）`
- **症状**: 本文版面の最大幅が dp 定数(600)でフォントサイズに追従しない。既定18spで約33字/行、24spで約25字、14spで約43字と字数が最適域(40字前後)から前後にぶれる。行間(em)も行長に連動せず二重比例則が未実装。
- **根拠(検証済)**: ChapterContent.kt:141-144 `.widthIn(max = 600.dp).padding(horizontal = bodyMarginDp.dp)`。:73 `lineHeight = lineHeightEm.em`（:66 `remember(colors.text, fontSize, lineHeightEm)` 内でユーザー可変・行長入力なし）。標準21-D line88『版面の最大幅を dp 定数でなく字数で定義＝最適字数×実効フォントサイズ…行間はこの実効行長を入力に取る同じ関数から導く』。
- **修正案**: 600.dp の決め打ちを『~40 * fontSize』相当の字数基準 widthIn へ置換しフォント宣言の変更に版面幅を自動追従。行間 em も行長を入力に取る関数へ寄せる（優先度は行長字数域＞行間）。
- **検証ノート**: dp定数幅・固定em行間の実体はCONFIRMED。ただし600dp上限では算出字数域が概ね25〜43字（bodyMargin でさらに減）で危険域60字超に達しない＝大フォントほど字数減=短い=安全側。害限定的で層②軽微=Minor が妥当。

#### [settings] 19-B 宣言の有無と値の分離／19-H リセットの可逆性（宣言を未宣言へ戻せること）  (CONFIRMED)
- **場所**: `ReadingSettingsSheet.kt:121 / MainActivity.kt:86-88,125-129 / theme/Theme.kt:24`
- **症状**: OSの昼夜切替に自動追従したいユーザーが、一度でもテーマチップ（ライト/セピア/ダーク）を押すと二度と『自動追従(未宣言)』へ戻せない。夜に自動で暗くなる既定挙動が消える。
- **根拠(検証済)**: loadInitialTheme(MainActivity.kt:127) `val saved = prefs.getString("reading_theme", null) ?: return systemFallback`＝キー不在時のみOS追従で公理Bを正しく実装。だが選択肢は theme/Theme.kt:24 `enum class ReadingTheme { LIGHT, SEPIA, DARK }` の3固定で、ReadingSettingsSheet.kt:121 `ReadingTheme.values().forEach { theme ->` が回すチップに『システムに従う/自動』が無い。onThemeChange(MainActivity.kt:88) `prefs.edit().putString("reading_theme", theme.name).apply()` が必ず固定値を書き込み、キー削除で未宣言へ戻す経路はコード全体に不在（grep: `remove("reading_theme")`・『既定に戻す』・『システムに従う』いずれも0件）。よって公理B『未宣言は既定値の進化(OS追従)へ追従』への再合流がUIから断たれている。
- **修正案**: テーマチップ列に『システムに従う』を1枚追加し、選択時は値上書きでなく `prefs.edit().remove("reading_theme").apply()` でキー削除して loadInitialTheme のOS追従へ再合流させる（値書き込みだと公理B/Hの意味論を壊す）。
- **検証ノート**: 全コード実体を実地確認。loadInitialTheme(125-129)・values()(121)・onThemeChange(86-88)・enum(Theme.kt:24)いずれも主張どおり。reset/キー削除の不在もgrepで確認。層②の設計理想からの逸脱（既定は新規状態では正しく機能し、データ喪失やクラッシュは無い）ため Minor 維持。


### 要検証

#### [a11y] 公理11 B/F (急所③ 没入クロームの復帰) / ジェスチャ専用操作の等価物  (PLAUSIBLE ⚠️調整)
- **場所**: `NativeReadingScreen.kt:781-790 (中央タップ復帰) ・:944-966 (視覚のみヒント) ・:850-853/:894-897 (退避)`
- **症状**: バー自動退避後、TalkBack ユーザーが戻る/目次/表示設定/前後章へ到達する手段を失い、復帰の『中央タップ』を発見も実行もできない、という主張。
- **根拠(検証済)**: バー復帰は :781-790 `pointerInput(Unit){ detectTapGestures(onTap = { … settleTopBar … }) }` の素の pointerInput のみで semantics{customActions} 無し=タップゾーンの音声等価物が無いのは事実。復帰ヒント :959-964 の Text は liveRegion 無し、:584-586 `showChromeHint = true; delay(2600); showChromeHint = false` で2600ms自動消灯=TalkBack へ実質届かないのも事実。ただし退避は :850-853/:894-897 いずれも `graphicsLayer{ translationY = … }` による描画移動で、Compose の graphicsLayer は semantics ノードをツリーから除外しない(条件付き除去や alpha=0/invisible ではない)ため、TopBar/BottomBar の 戻る/目次/設定/前後章ボタンは画面外でも a11y ツリーに残り TalkBack のスワイプ走査で到達可能な公算が高い。system Back は :164 `BackHandler(enabled = navHistory.size > 1)`(size==1 時はシステム既定で本棚 pop)で脱出可=完全閉じ込めでないのも finder 記載どおり。
- **修正案**: 本文 Box に semantics{ customActions=listOf(CustomAccessibilityAction("メニューを表示"){ settleTopBar(...,0f); true }) } を足しタップゾーンの音声等価物を用意(発見性向上の妥当なエンハンス)。あわせて退避バーが a11y ツリーに残るか実機 TalkBack で確認。
- **検証ノート**: コード実体(pointerInput/liveRegion欠落/2600ms/graphicsLayer/BackHandler)はすべて現物一致。しかし Major の根拠である『控えが到達不能=閉じ込め』は graphicsLayer が a11y ツリーを保持する標準挙動により成立しない公算が高く、finder 自身も『除外可否は実機要検証』と留保。到達可能なら残る問題は中央タップ復帰の音声等価物欠如という発見性の nit に縮小するため、severity を Major→要検証(実機確認待ち)へ降格。

#### [add] 取り込み画面の4状態（charter d）／公理12 G 初回は白画面にしない（11-D/G）  (PLAUSIBLE)
- **場所**: `app/src/main/java/com/novelreader/ui/discovery/PdfImportScreen.kt:135-190`
- **症状**: 取り込み画面入室直後の WebView 初期ロード中、およびページ遷移中にローディング表示が無い。スピナーは Downloading 状態のみのため、初回はページ描画まで無地WebViewが見え『読込中かエラーか』の区別がつかない瞬間が生じうる。
- **根拠(検証済)**: CircularProgressIndicator は :185 `if (uiState is PdfImportUiState.Downloading)` のときのみ描画(:186-190)。WebViewClient(:144-163) は onPageFinished(:145-157) で scrollIntoView 注入のみ・shouldOverrideUrlLoading(:159-162) は `false` 返し＝onPageStarted/onPageFinished に連動した初期ロードインジケータは無い。空白露出時間は回線速度依存。
- **修正案**: onPageStarted/onPageFinished で読込中フラグを持ち、初期ロード時のみ既存 CircularProgressIndicator を中央に流用（新規意匠は足さない）。実機で白画面の実測時間を確認して要否判定。
- **検証ノート**: コード事実（初期ロード用インジケータ不在・スピナーは Downloading 限定）は実引用で確認済み＝CONFIRMED相当。ただし実害（白画面の露出時間）は回線/端末依存で未計測のため PLAUSIBLE・severity 要検証（finder の自己申告どおり実機測定待ち）。

#### [critic] Design/06 組版『見出しの余白が space-above > space-below（見出しは続く本文に密着）』  (CONFIRMED)
- **場所**: `読書本文 章題ブロック ChapterContent.kt:171`
- **症状**: 章題ブロックが上余白(14dp)<下余白(26dp)で、近接則上は続く本文でなく章境界の上方へ寄って見えうる（見出し-本文のグルーピングが弱まる）
- **根拠(検証済)**: 章題 Column は .padding(top=14.dp, bottom=26.dp)(:171) で上<下＝則の逆。対して前書き/後書きラベルは padding(bottom=4.dp)(:252) で密着則を満たしており、逆転は章題のみ。ただし中央寄せ＋48dp罫の章オープナー意匠で下方の余白が意図的（章頭の呼吸）の可能性あり。既出の ChapterContent 指摘（heading() 欠落）は semantics で、余白比は未評価＝別根本原因
- **修正案**: 章オープナーとして意図的なら乖離を実装コメント/ADR に記録（無記録乖離ゼロ則）。in-flow 見出しとして扱うなら上>下へ是正（例 top=26/bottom=14）

#### [d-chrome] Design/09 D 本文はカットアウトを避ける（版面は displayCutout 内）  (PLAUSIBLE)
- **場所**: `ChapterContent.kt:103-106・141-144`
- **症状**: 横向き/サイドノッチ端末で行頭・行末の文字がカットアウトに欠ける可能性。
- **根拠(検証済)**: contentPadding(:103-106) は top=statusBars／bottom=navigationBars のみで start/end 無し。版面横 inset は ParagraphItem(:141-144) の `.padding(horizontal = bodyMarginDp.dp)` のみ（bodyMarginDp 最小 10dp＝NativeReadingScreen.kt:230 `coerceIn(10, 40)`）。com/novelreader 全域 grep で displayCutout／safeDrawing NONE。AndroidManifest.xml:32-41 の MainActivity に screenOrientation も configChanges も無く回転可＝サイドノッチが版面に掛かりうる（確認済み）。
- **修正案**: 版面の横 padding に WindowInsets.displayCutout（または safeDrawing）を合成。背景は全画面へ広げ、テキスト領域だけ cutout 内に収める。
- **検証ノート**: 静的事実（cutout inset 不在・回転可）は確認どおり。実際に文字が欠けるかは端末ノッチ形状と向き依存のため静的には確定できず 要検証 が妥当。

#### [d-token] KB03 §5『350ms超のスロットは彫らない——上限をトークンの不在で強制』  (PLAUSIBLE)
- **場所**: `ui/theme/Motion.kt:28(ui/ProcessingBanner.kt:177 で使用)`
- **症状**: motion トークン棚に 350ms 超の値スロットが1つ存在＝上限規律の穴になりうる
- **根拠(検証済)**: Motion.kt:28 `const val MotionDurationProgress: Int = 400`。ProcessingBanner.kt:177 `animationSpec = tween(durationMillis = MotionDurationProgress)` で使用。値400は350超。ただしコメント(Motion.kt:26)通り determinate 進捗バーが現ステップ目標へ伸びる時間＝遷移/フィードバックでなく進行表示。KB §5 の progress 枠は『唯一のループ許可枠(進行表示)』で免除余地大。ADR 0014 本文に 350ms 条項は無し(350のヒットゼロ)＝上限則は KB03 §5 由来。
- **修正案**: 進行表示は 350ms 上限の対象外である旨を Motion.kt コメント/ADR に1文明記して免除を正本化。上限内で足りるなら 300ms 等へ寄せる。
- **検証ノート**: 値400の実在は事実。だが『違反』か『免除』かは意匠意図次第で確定せず＝要検証据え置き。指摘者自身も免除余地を認識。

#### [d-type] JLREQ §3 ルビ処理（前後漢字への掛け禁止・仮名のみ許容／隣接ルビの連続禁止）・Design/05 §1(b)  (PLAUSIBLE)
- **場所**: `RubyText.kt:91,133-138 / RubyLayoutHelper.kt:110,136`
- **症状**: 親文字より長い読み（1漢字4モーラ等）が隣接漢字へ被る、または隣接ルビ語同士が接触しうる。
- **根拠(検証済)**: RubyText.kt:91 `textAlign = android.graphics.Paint.Align.CENTER`、:133-138 `canvas.nativeCanvas.drawText(info.rubyText, info.centerX, y, rubyPaint)`＝中央基準の単純描画。centerX は RubyLayoutHelper.kt:110 `val centerX = (startBox.left + endBox.right) / 2f`（同一行）・:136 同（行またぎ）＝親文字範囲の中央のみ。はみ出し時の隣接字種判定による掛け制限も、隣接ルビ間の最小アキ確保コードも存在しない（calculateRubyPositions 全体を通読して該当ロジック無しを確認）。
- **修正案**: はみ出し時に隣接字種を見て掛け範囲を制限し、隣接ルビ間へ最小アキを入れる（掛けを抑える方向＝引く）。長ルビ実例で実機確認。
- **検証ノート**: コード構造（掛け制御・隣接アキ制御の不在）は静的に CONFIRMED。ただし実際に被るかは長ルビの実データと版面幅に依存し座標だけでは確定不能＝実機目視が要る。severity 要検証は妥当（人間テスト送り相当）。

#### [d-type] Design/05 §2 可読性研究値（和文行長 40字前後が設計域）  (PLAUSIBLE)
- **場所**: `ChapterContent.kt:142,144 / ReadingSettingsSheet.kt:176,242`
- **症状**: 大フォント×広余白で1行字数が極端に減り視線リズムが崩れる恐れ。
- **根拠(検証済)**: ChapterContent.kt:142 `.widthIn(max = 600.dp)`、:144 `.padding(horizontal = bodyMarginDp.dp)`。ReadingSettingsSheet.kt:176 `valueRange = 14f..24f`（フォント）、:242 `valueRange = 10f..40f`（余白）。360dp端末・余白40dp・24sp で本文幅≒280dp／全角≒24dp＝約11〜12字/行、既定(15dp/18sp)で約18字/行＝伝統域40字より短い。600dp 上限はタブレットで適域。
- **修正案**: 余白スライダー上限を下げる、またはフォント大時に余白を自動縮小する版面適応。体感は実機テストへ。
- **検証ノート**: コード参照は全て CONFIRMED。ただし『40字』は印刷/デスクトップ縦組み由来の設計域で、モバイル縦画面はどの設定でも 40字に到達不能（18spで40字は幅720dp必要）＝横組みモバイル小説の実用域は 15〜25字が通例。よって『硬い違反』でなく設定域チューニングの軟らかい懸念。severity 要検証・要実機体感で妥当。

#### [evolve] 公理23-F 全開始点からの移行 / 公理8 黙って落とさない  (PLAUSIBLE)
- **場所**: `AppDatabase.kt:275-280 (addMigrations 最古は MIGRATION_3_4)`
- **症状**: マイグレーション鎖の最下段が v3。v1/v2 の DB が実在すると fallbackToDestructiveMigration 不在ゆえ移行未発見で起動時 IllegalStateException（無言クラッシュ）。
- **根拠(検証済)**: AppDatabase.kt:276 `MIGRATION_3_4, MIGRATION_4_5, ...` ＝addMigrations の最古は 3_4 で 2_3/1_2 不在。grep fallbackToDestructive ＝NO HITS。schemas/ の ls で最古は 3.json（1/2.json 無し）。build.gradle:22-23 `versionCode 1` `versionName "1.0"`＝未公開も実確認。Room は移行未発見かつ destructive fallback 無しなら起動時 IllegalStateException を投げる（仕様上主張どおり）。
- **修正案**: 第一候補『引く』＝DB が真に v3 始点と確定できるなら対応不要（現状で正しい）。要確認: 過去に v1/v2 スキーマで実機投入した開発端末が残存しないこと。残存し得るなら MIGRATION_2_3 等を補うか、移行未発見時に『このデータは読めません』と正直に出す経路を用意。
- **検証ノート**: コード事実(最古3_4・fallback不在・schema最古3.json・未公開)は全て実確認。だが実害の成否は『v1/v2 実機が実在するか』にかかり静的解析では確定不能＝過去の開発端末履歴という人間知識が要る。exportSchema が 3.json 始点な点は v1/v2 が正式スキーマとして存在しなかった可能性を示唆するが、exportSchema 導入前の開発ビルド残存の余地は否定できない。攻撃前提が反証されていないため REFUTED でなく PLAUSIBLE / 要検証を維持。

#### [gesture] 公理17 E システムとの衝突回避（送りボタンを下端ギリギリに置かない）  (PLAUSIBLE)
- **場所**: `NativeReadingScreen.kt:846-891（align :848）`
- **症状**: 章送りの主操作(前の章/目次/次の章)を画面下端 BottomAppBar に配置。ジェスチャーナビ機のホーム/クイックスイッチ帯と近接し誤発火・押しにくさが生じうる。
- **根拠(検証済)**: NativeReadingScreen.kt:846-853 `BottomAppBar( modifier = Modifier.align(Alignment.BottomCenter).onSizeChanged{...}.graphicsLayer{ translationY = bottomBarHeightPx * topAppBarState.collapsedFraction } ...)`。windowInsets 引数は明示されず Material3 の BottomAppBarDefaults.windowInsets(systemBars) 既定に委ねる＝ナビバー分だけ持ち上がる想定。Scaffold は contentWindowInsets=WindowInsets(0,0,0,0) だがこの BottomAppBar は Scaffold スロット外のオーバーレイ(外側Box直下)のため自前既定インセットが効く。実機の当たり判定は静的確定不可。
- **修正案**: 引く＝BottomAppBar の windowInsets を 0 に上書きしない(既定インセット維持でホームバー上に配置)。3ボタン式/ジェスチャー式ナビ双方の実機で下端ジェスチャ帯に入らないか要検証。ボタンは移動量ゼロのタップのためスワイプ系ホームジェスチャとの物理衝突は本質的に低い(公理17 E)。
- **検証ノート**: 既定インセット適用の機序はコードから確認できたが、最終判定は実機依存＝タスク定義の『実機検証待ち』に相当しPLAUSIBLE。severity 要検証は妥当。物理衝突リスクはタップ(移動量ゼロ)ゆえ低く、優先度は低い。

#### [ia] 15-§E 長編はチャンクする（部・編・100話単位で畳む・Miller）  (PLAUSIBLE)
- **場所**: `NativeTableOfContentsScreen.kt:206-215`
- **症状**: 数百話のなろう作品で目次が部・編で畳めずフラットに全話並ぶ。離れた既知の話へジャンプする手段が無い。
- **根拠(検証済)**: NativeTableOfContentsScreen.kt:206 `LazyColumn(...)`、:215 `itemsIndexed(entries, key = { _, entry -> entry.fileName })` でフラット列挙、部/編グルーピングや折り畳み無し。TocEntry は ChapterContent.kt:25 `data class TocEntry(val title: String, val fileName: String)` で階層情報を持たず、チャンク化は抽出データ次第。
- **修正案**: 追加＝抽出に部・編があれば見出しで畳む/無ければ100話単位。まず実PDF→HTMLが部構造を出すか実データの階層有無を確認。
- **検証ノート**: フラット列挙・TocEntry無階層は静的にCONFIRMED。ただし§Eの需要成立(数百話)と修正可否(抽出が部構造を出すか)は実データ依存で静的確定不可＝要検証維持。TocEntry拡張が要るため『データ追加不要』とは言えない点も留意。

#### [measure] 24層 §G/§H/§F 予算表（O(1)規律・出荷後不可視＝リリース前実測義務／§G-4 収集ゼロが検証義務を重くする）  (CONFIRMED)
- **場所**: `android/settings.gradle:32（include ':app' 単一）＋ app/src/main 全ツリー`
- **症状**: 大PDF取込・10倍蔵書起動・長時間ページ送りのメモリ/フレーム予算が漸進劣化しても機械検証で検出できず、INTERNET 無しで出荷後テレメトリも持てない
- **根拠(検証済)**: settings.gradle 末尾は `include ':app'` の単一モジュール（benchmark ディレクトリは find で 0 件）。`Macrobenchmark|StartupTimingMetric|FrameTimingMetric|TraceSectionMetric|JankStats|reportFullyDrawn` を app/src/main 全体 grep してヒット 0＝性能計測基盤が実在しない。benchmark モジュール・大PDF/10倍蔵書フィクスチャ計測が未実装なのは事実として確定。
- **修正案**: Macrobenchmark モジュールを新設し、大/中/小/病的PDF＋10倍蔵書フィクスチャで取込・本棚起動・章ジャンプを TraceSectionMetric 区間として P90/P99 実測、§F 予算を assert 化してリリース前チェックへ1行追加。まず計測して O(ページ数)/O(冊数)化の有無を確定。
- **検証ノート**: インフラ不在（計測ゼロ）は grep/find で確定。ただし実際の性能退行の有無自体は未計測＝finding も『まず要検証』と認める通り。層②（機械検証/性能）の観測ギャップとして 要検証 が妥当。

#### [nav] 公理1 経路独立性（Android原則A: Up≠Back）  (PLAUSIBLE)
- **場所**: `NovelDetailScreen.kt:210-216 / MainActivity.kt:200(home直行),251(result経由),291`
- **症状**: 作品詳細の App bar『←』が到達経路で別着地（発見ホーム発→ホーム／結果一覧発→一覧）。視覚同一の←が、固定Up画面（Reading=本棚/Result=発見）と履歴Back画面（Detail等）で混在し『この矢印は何をするか』のメンタルモデルが揺れる。
- **根拠(検証済)**: NovelDetailScreen.kt:211 `IconButton(onClick = onBack)`＋:214 `contentDescription = "戻る"`（純・履歴Back／generic文言）。MainActivity.kt:291 `onBack = { navController.popBackStack() }`。到達2経路は :200 と :251 で同一の `navController.navigate("discovery/detail/${ncode.value}")`。対比＝DiscoveryResultScreen.kt:146 "発見に戻る"（固定Up）・NativeReadingScreen.kt:913-916 "本棚に戻る"（固定Up）は destination 名指し。
- **修正案**: 第一候補（統一）：detail の←を Result 同型の固定Up（popBackStack("discovery", false)）へ揃え、←＝固定Up・端末Back＝履歴の1メタファーに統一。統一しない裁定なら現状維持も可。実機で『矢印挙動の混在が混乱を生むか』を要確認。
- **検証ノート**: コード実体は全て引用どおり CONFIRMED。ただし detail の←は常に『来た所へ戻る』一貫規則の履歴Backで、detail 画面の内容・アクションは経路不変（変わるのは Back の着地先のみ＝正しい Back 挙動）。公理1の path-dependence 違反というより『←の意味が画面間で不統一』という一貫性設計論で、finder 自身も『確定的違反とは断定できない』と明記。Android原則3は アプリ内 Up==Back を許容。よって事実CONFIRMEDだが違反成立は未確定＝PLAUSIBLE/要検証を維持（層①違反とは認定せず）。

#### [persist] 公理6 永続性（回転で最終スクロールデルタが競合し得る）  (PLAUSIBLE)
- **場所**: `NativeReadingScreen.kt:550-557,:176-182 / BookshelfViewModel.kt:214,:217-224,:412`
- **症状**: 構成変更直前にスクロールした最後の一瞬（≤400ms分）が、再生成後の getProgress 読み出しに間に合わず巻き戻る可能性。実害は最大でスクロール数百ms分と小さい。
- **根拠(検証済)**: 離脱時フラッシュは NativeReadingScreen.kt:552-557 `if (event == Lifecycle.Event.ON_STOP) { latestOnSaveScroll(lazyListState.firstVisibleItemIndex, lazyListState.firstVisibleItemScrollOffset) }` → onSaveScroll(:346-348)→ BookshelfViewModel.kt:412 `progressChannel.trySend(...)`（非ブロッキング送信）。実書込は :214 `private val progressChannel = Channel<ProgressEntity>(Channel.CONFLATED)` を :217-224 の `viewModelScope.launch(Dispatchers.IO) { for (p in progressChannel) { repository.saveScrollPosition(...) } }` が非同期でドレイン。再生成側は NativeReadingScreen.kt:176-182 `LaunchedEffect(bookId) { val p = viewModel.getProgress(bookId); chapterRestore = ...}` でDBを読むが、両者に happens-before の同期が無い。危険窓は :533 `.debounce(400)` により最大400ms分に限定。
- **修正案**: ON_STOPフラッシュを suspend で完了待ちしてから破棄、または getProgress をチャネルドレイン後に読む順序保証を入れる。まず実機で回転時に最終スクロールが保存されるか確認して要否判断。
- **検証ノート**: 構造的レースは実在（trySend→IO書込 と getProgress読出に同期障壁なし）＝方向は正しい。ただしBookshelfViewModelは構成変更を跨いで生存（progressChannel・IOコルーチンも生存）し、Activity teardown+rebuild(数十〜数百ms) の間に単一行REPLACE(数ms)がほぼ確実に先着するため実発現は極めて低確率。影響も≤400msスクロール＝発現しても層②軽微(Minor)。実機で回転時の最終位置保存を確認するまで 要検証 を維持（人間テスト送り相当）。

#### [privacy] 公理15 A/B②(端末外送信=収集への格上げ)＋G(背景通信はオプトイン既定)＋F(Data safety 申告)  (PLAUSIBLE)
- **場所**: `NewEpisodeCheckWorker.kt:56 ＋ NovelReaderApplication.kt:158`
- **症状**: ncode 紐付け蔵書が1冊でもあると、毎日バックグラウンドでフォロー作品集合(ncode群)を syosetu へ送信する。この定期送信を止めるトグルが無く、既定ON・停止不可で読書関心を第三者ホストへ継続開示している。
- **根拠(検証済)**: NewEpisodeCheckWorker.kt:56 `app.novelApiRepository.novelDetailsBulk(linkedBooks.keys.map { Ncode(it) })` が book.ncode を持つ全蔵書の ncode を api.syosetu.com へ照会。NovelReaderApplication.kt:142 onCreate→148-162 scheduleNewEpisodeCheck が :158 で enqueueUniquePeriodicWork(24h, KEEP) を無条件登録。トグル探索: cancelUniqueWork/disable 系の呼び出し=該当なし、DataStore キーは SearchHistoryStore の pinned/recent のみで背景通信 ON/OFF の preference 皆無。AndroidManifest.xml:10 の INTERNET 用途コメントは api.syosetu.com メタ取得/WebView/PDF DL を挙げる。
- **修正案**: 背景の新着ポーリングを既定OFFのオプトインにする(設定/初回に明示同意)。少なくとも Data safety に『新着確認のため紐付け作品IDをなろうAPIへ送信』を記載し、Manifest の用途コメントと申告を一致させる。
- **検証ノート**: コード事実(日次送信・トグル不在・既定ON無条件登録)は全て CONFIRMED。しかし公理15違反として是正必須かは文脈次第=送信先が作品配信元 syosetu 自身で増分露出が小さく、本文非送信(charter(d)充足)。オプトアウト式が Privacy-by-Default(G-1)と緊張する点は正しいが、可否は人間/プロダクト判断。よって verdict は PLAUSIBLE・severity 要検証 を維持。

#### [reach] 21-F 形態の連続性（どんな形態遷移後も『読んでいた文』に戻る＝位置正本はレイアウト非依存の文字オフセット）／公理6  (PLAUSIBLE)
- **場所**: `NativeReadingScreen.kt:344-345・517-519（scrollIndex/Offset 復元）＋ ProgressEntity.kt:10-14`
- **症状**: 回転・折りたたみ開閉・分割画面化で版面が再計算されると、段落先頭からのpxオフセットが指す行がずれ、長い段落の途中を読んでいた場合に『さっき読んでいた文』から数行ずれて復帰しうる。
- **根拠(検証済)**: ProgressEntity.kt:13-14 `val scrollIndex: Int=0` `val scrollOffset: Int=0`、:10-11 コメント『LazyListState の firstVisibleItemIndex / ScrollOffset に対応』。NativeReadingScreen.kt:344-345 `initialScrollIndex = … restore.scrollIndex` `initialScrollOffset = … restore.scrollOffset`、:517-519 `remember(currentFile){ LazyListState(initialScrollIndex, initialScrollOffset) }`、:531 保存 `lazyListState.firstVisibleItemIndex to lazyListState.firstVisibleItemScrollOffset`。段落index(幅非依存)は保つが段落内オフセットが px 基準＝幅依存。標準21-F line120『適応レイアウトの土台は適応しない座標系＝レイアウト非依存の文字オフセット』に半準拠。
- **修正案**: scrollOffset の px 値を段落内『文字オフセット』基準へ置換（段落index は既に幅非依存なので段落内アンカーを文字数へ変えれば回転・折りたたみでも同一文へ復元）。
- **検証ノート**: px オフセット設計の実体はCONFIRMED。ただし段落index は保持され章・段落は失わない（Critical=構造化回避）ため害は単一段落内のずれに限局。なろう系は短い会話段落が多く先頭可視段落が短ければドリフト無視できる。回転で実際に何行ずれるかは実機回転テスト（06③拡張）で確認要＝要検証が妥当。

#### [ssot] 公理5 状態の単一の真実（同じ事実は一箇所から導く）  (PLAUSIBLE)
- **場所**: `app/src/main/java/com/novelreader/viewmodel/BookshelfViewModel.kt:147-153 と app/src/main/java/com/novelreader/ui/NativeReadingScreen.kt:270,487`
- **症状**: 『手元PDFの話数』という同一量が本棚バッジと読書画面継続カードで別々に算出され、食い違えば同一作品で本棚『続きN話』⇄読書画面『追いつき済み』の矛盾を見せうる。
- **根拠(検証済)**: 本棚は BookshelfViewModel.kt:149-151 `val chapPattern = Regex("chap_\\d+\\.html")` / `book.id to (File(book.htmlDirPath).listFiles { f -> f.name.matches(chapPattern) }?.size ?: 0)` の枚数を chapterCountMap 経由で BookCard.kt:131 `computeContinuation(totalChaps, novelDetail)` へ渡す。読書画面は NativeReadingScreen.kt:270 `ChapterHtmlParser.parseToc(File(htmlDirPath, "index.html"))` で得た tocEntries.size を :487 `computeContinuation(tocEntries.size, it)` へ渡す。＝同じ章数を『chapファイル数』と『index目次<li>数』の二経路で導出。
- **修正案**: 章数を1ヘルパーで一度だけ算出して両画面へ渡し導出を二重化しない。最低限 #chapファイル == #tocエントリ の不変条件を testDebugUnitTest で固定して将来の静かな divergence を検知する。
- **検証ノート**: 二重導出という code 事実は CONFIRMED。ただしユーザー可視の矛盾は現状 未発現: HtmlExporter.kt:82 `val filename = "chap_${i + 1}.html"` と :85 `indexHtml.append("<li><a href=\"$filename\">$safeTitle</a></li>")` が同一ループで chap ファイルと index の <li> をロックステップ生成するため両数は常に一致し、DB 登録は完全出力後。よって現時点の実害はゼロで、不変条件テスト・単一ソースが無い『SSOT の芽（潜在リスク）』に留まる。finder の 要検証 判定は妥当で過大主張なし。


### 人間テスト送り

#### [d-chrome] Design/09 B 呼び出し=中央タップのみ／公理9  (PLAUSIBLE ⚠️調整)
- **場所**: `NativeReadingScreen.kt:735-746`
- **症状**: 少し上へスクロールしただけで要求していない上下バーが自動的に戻り、再読しようとした本文上端を覆う。
- **根拠(検証済)**: onPostScroll(:735-746) の :741-745 `if (consumed.y > 0) { topAppBarState.heightOffset = (topAppBarState.heightOffset + consumed.y).coerceAtMost(0f) }`＝上スクロールで chrome を表示方向へ追従（実在確認）。ただしバー移動量は `consumed.y`（本文が実際に動いた分）に比例し、コメント :740『本文が実際に動いた分だけ』のとおり自走ではなくユーザーのジェスチャ駆動。下スクロール退避(onPreScroll :727-731)と対称の enterAlways 崩壊バー慣習。
- **修正案**: 厳格運用なら :741-745 の自動表示ブロックを削除し再表示を中央タップ(:781-790)へ一本化する（下スクロール退避は残す）。ただし採否は好みに依る。
- **検証ノート**: コード実体は確認どおりだが『違反』認定は争える：バー追従はタイマー等の自走でなくユーザースクロールに比例するため公理9（アプリが勝手に動く）には当たらず、退場(下)と対称の慣習挙動。finder 自身も『自動表示を惜しむ声の有無は人間テスト送り』と明記。severity を Minor→人間テスト送りへ降格。

#### [d-motion] 08-F（reduce-motion＝ANIMATOR_DURATION_SCALE=0 で全モーション即時化。Compose標準APIは自動追従・自前ループのみ手動分岐）  (PLAUSIBLE ⚠️調整)
- **場所**: `NativeReadingScreen.kt:1021（settleTopBar の animate()）／全ツリーに isReducedMotion 分岐0件`
- **症状**: 端末『アニメーションを削除』時に没入バーの settle・カード押下スケール・バナー/ヒントのフェードが即時スナップに落ちるか静的に確定できない。
- **根拠(検証済)**: grep 実測: isReducedMotion/ANIMATOR_DURATION_SCALE/MotionDurationScale 参照0件。settleTopBar は自前フレームループでなく NativeReadingScreen.kt:1021 `animate(initialValue = state.heightOffset, targetValue = target, animationSpec = MotionSpringBarSettle)`＝Compose 標準 suspend API。他も animateFloatAsState / Animatable.animateTo(tween) / AnimatedVisibility / Material 進捗＝すべて標準 API。参照08-F『Compose の animation API は 1.2.0 以降追従／標準 API 部分は自動追従に任せる』＝委譲経路は許容。
- **修正案**: 実機で開発者オプション『アニメスケール0』にし、①カード押下(残留バウンス無し) ②バーのタップ/フリック settle(瞬時スナップ／onPostFling NativeReadingScreen.kt:753 由来 coroutine が MotionDurationScale を運ぶか) ③バナー/ヒントを目視。全て即時なら分岐追加不要。追従しない箇所のみ isReducedMotionEnabled で即時スナップに分岐。
- **検証ノート**: 分岐0件・settleTopBar が animate() 使用・全て標準 API を確認。F は標準 API の自動追従を明示的に許容するため『分岐皆無＝即欠陥』ではない。残る不確定は(i)onPostFlingの coroutine 文脈が MotionDurationScale を運ぶか (ii)MotionSpringCard の bouncy spring が scale=0 で残留バウンス無く即終端するかの2点＝実機検証事項。findingの要検証から人間テスト送りへ横移動。

#### [gesture] 公理17 メタファーの単一性 / C-1 語彙表（本文画面は1身振り1意味）  (PLAUSIBLE ⚠️調整)
- **場所**: `WebReaderScreen.kt:84-103,:148 / NativeReadingScreen.kt:781-790,:846-891`
- **症状**: 同一の『なろうを読む』行為でネイティブ読書とWeb読みが別の操作言語を持つ。ネイティブは全面タップでchromeトグル＋下部バー章送り。Web読みは常時バー・没入無し・アプリ章送り無し・本文タップはWebView素通り。
- **根拠(検証済)**: WebReaderScreen.kt:86-102 は Scaffold topBar に TopAppBar(title="なろうで読む") を常時描画し没入/トグル無し。:148 `override fun shouldOverrideUrlLoading(...): Boolean = false // 横取りせず WebView 自身に読ませる`＝本文タップはWebViewへ委譲。対し NativeReadingScreen.kt:781-790 `.pointerInput(Unit){ detectTapGestures(onTap={ ... settleTopBar ... }) }`＝全面タップでchromeトグル、:859-889 BottomAppBar に「前の章/目次/次の章」。両者の語彙は実在し分裂は事実。
- **修正案**: 引く＝Web読みにchromeトグルを足さない。Web読みは常時『なろうで読む』バー＋Web体裁＋広告で持続モードが明示され、公理17 C-1 の許容例外(モード常時明示)に該当＝規約違反は成立しない。残る懸念はhabit-transferの記憶問題のみで、2週間後・無説明の再テストで実測。
- **検証ノート**: コード実体はファインダー記載どおり全て実在(CONFIRMEDレベル)。ただし『違反成立』は文脈依存＝Web読みは別モード(WebViewでなろう閲覧・広告あり)で持続的にモード明示されるため公理17の許容例外に落ちる。よって層②軽微の恒久欠陥ではなく人間テスト送り(習慣転移の記憶)に降格。ファインダー自身も『概ね正当』と結論しており整合。

#### [ia] 15-§F／公理1 経路独立性（検索経由と目次経由の本文は完全一致）  (PLAUSIBLE ⚠️調整)
- **場所**: `NativeTableOfContentsScreen.kt:220／本文への他入口`
- **症状**: 『この本の中の全文検索』が無いため検索経由vs目次経由の本文分岐は現状発生しない。ただし本文入口は複数(目次/本棚カード/deep link/navHistory)あり挙動一致は静的未確認。
- **根拠(検証済)**: tree全走査で FTS/全文/本文内検索UIは不在(grep `fts|full.?text|本文.*検索|全文|searchInBook` のヒットは DiscoveryQueryLabels.kt:87・BookshelfScreen.kt:793 の無関係コメントのみ)＝検索vs目次の本文食い違いは構造的に起き得ない。目次は NativeTableOfContentsScreen.kt:220 `.clickable { onSelectChapter(entry.fileName) }` で本文へ。
- **修正案**: 違反未成立のため引く不要。実機で複数入口→本文の位置/スワイプ/継続挙動が同一か確認(読書継続性レーンと重複可)。
- **検証ノート**: 『検索経由vs目次経由の分岐』という主張は FTS 不在ゆえ違反不成立(この部分はREFUTED相当)。残る多入口の本文パリティは実機依存で静的確定不可＝ファインダーの要検証を『人間テスト送り』へ調整(唯一のactionableが実機検証のため)。ファインダー自身が『違反未成立・引く不要』と正しく結論しており過剰主張なし。

#### [nav] 公理1(b) テレポート自己位置 ／ 公理8 表示は現実と一致  (PLAUSIBLE)
- **場所**: `NativeReadingScreen.kt:899-911,913-917 / 104,115,973-975`
- **症状**: 変換完了通知タップ等で読書画面へテレポート着地したとき上部バーは『章タイトル』のみで『どの本か（書名）』が画面単体で不明。章題が『第1話』『プロローグ』等の汎用文言だと着地直後にどの作品か特定できない。
- **根拠(検証済)**: NativeReadingScreen.kt:900-908 `is ParseResult.Success -> Text(text = r.content.title, ...)`＝章タイトルを TopAppBar title に表示。書名の bookTitle は :104 KDoc『蔵書タイトル（なろう紐付けシートの初期検索語に使う）』・:115 param として受領するのみで、:973-975 `LaunchedEffect(Unit) { onSearchNcode(bookTitle) }` / `NcodeLinkSheet(bookTitle = bookTitle, ...)`＝リンクシート初期検索語専用。chrome（TopAppBar）には書名の表示なし。出口 :913-916 "本棚に戻る"・目次 :871-878 は在るが『何の本』のみ欠落。
- **修正案**: 追加型（引く候補なし）：読書 chrome に書名を添える（章題の副行、または目次見出しでの書名常設）。経路非依存（本棚起動でも同様）＝path-dependence ではないがテレポート自己位置チェックに一項目欠ける。汎用章題で書名が消える度合いは知覚判断のため実機で要確認。
- **検証ノート**: コード実体は全て引用どおり CONFIRMED（title=章タイトル・bookTitle はシート専用・chrome非表示）。ただし実害は章題が書名を代替できないほど汎用な場合に限る知覚依存で、本棚起動でも同挙動＝経路独立性そのものの違反ではない自己位置完全性の指摘。人間テスト送りが妥当。

#### [settings] 19-G スコープの可視化（置き場所が効果範囲を語る＝フィードフォワード）／02-I システムイメージ  (PLAUSIBLE)
- **場所**: `ReadingSettingsSheet.kt:119-143 / 呼び出し ChapterScreenContent NativeReadingScreen.kt:988-1004 / テーマ正本 MainActivity.kt:85-88`
- **症状**: 特定の本の読書画面内で開く『表示設定』でダークを選ぶと、その本だけでなく本棚も他の全書籍も一斉に暗くなる。置き場所(この本)と実効果(全書籍)が食い違う可能性（GE冷蔵庫）。
- **根拠(検証済)**: 設定シートは ChapterScreenContent(665行〜)内の NativeReadingScreen.kt:988 `if (showSettings) { ReadingSettingsSheet(` で表示＝特定書籍の読書画面に置かれる。しかしテーマは MainActivity.kt:85 `var appTheme by remember { mutableStateOf(loadInitialTheme(prefs, systemDark)) }` の単一正本で本棚(NovelReaderTheme)と共有し、NativeReadingScreen.kt:118 コメント『テーマは MainActivity が持つ単一正本を受け取る（本棚と共有して全体を同期させるため）』が意図を明示。字サイズ/行間/余白も app_prefs 全体キー（NativeReadingScreen.kt:203 `getInt("reading_font_size"…)`,218,230）で全書籍共通。『この本のみ』バッジや本ごと上書きは grep で不在。置き場所と効果範囲(全書籍)の一致が読者へ予告されない。
- **修正案**: 人間テストで『この本だけ変えたつもり』の誤解が出るか確認。出るならシート見出しを『全書籍の表示設定』等でスコープを一語予告するか、本ごと上書き（本詳細メニュー＋『この本のみ』バッジ、差分→全体→既定の単一解決関数）を追加。
- **検証ノート**: コード事実は全数CONFIRMED（配置=ChapterScreenContent内988-1004・単一正本共有・全体キー・上書き不在）。ただしテーマ全体共有は『どの画面で変えても同期』の意図的設計（指摘[3]が良い点として評価する同じ機構）で、公理G違反の成立=実害の有無は人間の誤解実測に依存する。よってCONFIRMEDではなくPLAUSIBLE、severityは指摘どおり『人間テスト送り』を維持。


### 良い点

#### [a11y] 公理11 B/F (Canvas 描画の等価物提供)  (CONFIRMED)
- **場所**: `ShioriCover.kt:281-283`
- **症状**: -
- **根拠(検証済)**: :283 `Canvas(modifier.semantics { contentDescription = title })` で、text ノードを持たない Canvas 直描画の栞書影に作品名を明示付与。:281-282 に『題字は Canvas 描画で text ノードを持たないため、表紙自体に contentDescription=題名 を与える。なぜ必須か: これが無いとスクリーンリーダーがグリッドの作品名を読めない（a11y 退行）』と明確な why コメント。charter が最大リスクに挙げる『Canvas 描画テキストが TalkBack に無音』の最悪ケースを本棚の作品名について正しく回避。
- **修正案**: -
- **検証ノート**: contentDescription 付与と why コメントを現物確認。良い点として妥当。※同 Canvas の drawShioriTitle(:305)による題字自体は装飾扱いで問題なし。

#### [a11y] 公理11 / 05H 色に意味を載せない・意味テキストは4.5:1  (CONFIRMED)
- **場所**: `BookCard.kt:80-95・:102-118・:62-85 / Color.kt:26-42`
- **症状**: -
- **根拠(検証済)**: 状態は常に色+テキスト/形で表す: :88 `text = "未読"`(未読ラベル)、:111-116 `Text(text = "続き ${newCount}話", …)` + :105-109 の青磁ドット、:80-84 `Text(text = "$percent%", …)` + :71-78 バー。色のみで意味を運ぶ箇所が無い。加えて意味を運ぶ補助文字は Color.kt:29 `UnreadSeiji = Color(0xFF50685C)`・:40-42 InfoText 群として色相維持のまま暗化し、:26-28/:31-39 コメントで各面の4.5:1充足を明記(ADR 0014-D)。
- **修正案**: -
- **検証ノート**: 色+テキスト併記と4.5:1トークンを現物確認。良い点として妥当。ただし同じ4.5:1原則がルビ(Finding 0)には適用漏れであり、この良い点はその自己矛盾の反面教師でもある。

#### [add] 公理12 D Empty状態はオンボーディングの主戦場  (CONFIRMED)
- **場所**: `app/src/main/java/com/novelreader/ui/ProcessingBanner.kt:27-85, app/src/main/java/com/novelreader/ui/BookshelfScreen.kt:558-565`
- **症状**: 空の本棚が『状態の一文＋主CTA』を備え、0件時は補助UIを隠せている（初回の第一印象を白紙で捨てていない）。
- **根拠(検証済)**: EmptyBookshelf は状態文 :68 `"本棚はまだ空です"`＋補助文 :75 `"右下の＋からPDFを追加してください"`＋主CTA :81-83 `FilledTonalButton(onClick = onAddClick) { Text("PDFを追加する") }`。BookshelfContent(:558) `if (shelfItems.isEmpty() && !isProcessing && selectedStatus == null)` の排他分岐で :565 `EmptyBookshelf(onAddClick = onFabClick, ...)` を出し、FindGuideBand/StatusChipRow は :582/:637 の `if (shelfItems.isNotEmpty() || selectedStatus != null)` で 0件時に隠す。Loading は :551-554 `BookshelfSkeleton` で区別し空フラッシュ回避。
- **修正案**: 現状維持。次善策として著作権フリー短編サンプル同梱（副経路『サンプルを読む』）で TTV をファイル準備に依存させない案を検討可（11-D・本棚を偽物で埋めない前提）。
- **検証ノート**: 空状態の構造（状態文＋CTA＋0件時の補助UI抑制）は全て実引用で確認＝妥当な良い点。1点補足＝EmptyBookshelf の onAddClick は onFabClick(:565) を共有するため、CTA も finding[0] のバッテリーダイアログ段差を通る（finder[5]の『その場でピッカーへ』は [0] により不正確）。良い点の評価自体には影響せず。

#### [add] 公理12 B 説明ではなく経路（経路独立の初回版）／G 空フラッシュ回避  (CONFIRMED)
- **場所**: `app/src/main/java/com/novelreader/ui/BookshelfScreen.kt:551-565, app/src/main/java/com/novelreader/MainActivity.kt:169`
- **症状**: 初回起動でも専用オンボーディングActivity/チュートリアルに分岐せず通常の本棚画面へ着地し、差は『本棚が空か否か』だけ。起動直後の一括権限要求も無い。
- **根拠(検証済)**: tutorial/onboarding/firstLaunch は res/コードとも grep ヒットゼロ。MainActivity(:169) `NavHost(navController = navController, startDestination = "bookshelf")`＝初回も通常本棚へ。MainActivity に requestPermission/checkSelfPermission は grep ゼロ（起動時一括権限要求なし）。BookshelfContent は :551-554 Loading 時 BookshelfSkeleton、:558-565 Content(空)確定で EmptyBookshelf の型区別で cold start の空フラッシュ回避。
- **修正案**: 現状維持。
- **検証ノート**: オンボーディング分岐・起動時権限要求の不在を grep ゼロで、startDestination と型分岐を実引用で確認＝妥当な良い点。

#### [continuity] 公理14候補B/E 再入場の最短性・最頻タスク1タップ  (CONFIRMED)
- **場所**: `BookshelfScreen.kt:203-207 / NativeReadingScreen.kt:176-183,344-345 / BookDao.kt:11-15`
- **症状**: PDF蔵書は起動→本棚(最近読書順で先頭に読みかけ)→カード1タップで保存章かつ章内スクロール位置まで復元して本文に着地する。
- **根拠(検証済)**: BookshelfScreen.kt:203-207 `onOpenBook = { book -> scope.launch { val lastReadFile = viewModel.getLastRead(BookId(book.id)) ?: "index.html"; onOpenBook(book.id, lastReadFile) } }`＝1タップで最終章解決。NativeReadingScreen.kt:176-183 入場時 `viewModel.getProgress(bookId)` で復元位置取得、:344-345 `initialScrollIndex = if (resolvedFile == restore.targetFile) restore.scrollIndex else 0`(offset同)で先頭→保存位置のジャンプを見せず注入。BookDao.kt:11-15 の MAX(addedAt,lastReadAt) DESC で読みかけが先頭。charter 13 §E適用例の『良い』行に合致。
- **修正案**: 維持。Webカード(finding[1])を同挙動へ揃える基準。
- **検証ノート**: 全 loc 厳密一致。専用『続きから読む』カードや DataStore 先出しは無いがソート+1タップ+スクロール復元で core を満たす。過大主張なし。良い点として妥当。

#### [continuity] 公理14候補D(戻り遷移で自動保存を巻き戻さない)・公理8 表示=現実  (CONFIRMED)
- **場所**: `WebReaderScreen.kt:127-142`
- **症状**: WebViewで読み進めた後に戻る連打で退出しても読書位置がセッション先頭話へ巻き戻らない。過去の実機再現バグ(2話戻り)を構造的に封じている。
- **根拠(検証済)**: WebReaderScreen.kt:135-142 `val history = view.copyBackForwardList(); val reachedByBack = history.currentIndex < history.size - 1; if (!reachedByBack) { parseNarouEpisodeNumber(url, ncode)?.let { episode -> viewModel.onEpisodeReached(ncode, episode) } }`＝フラグでなく履歴スタック位置の構造判定。コメント :127-133 に機序と実測根拠『2026-07-11 実機で2作品再現・ADR 0012 追補』。
- **修正案**: 維持。ただし前方への参照ジャンプ(finding[2])は本ガードの対象外(reachedByBack は履歴戻りのみ検出)である点は正しく留意。
- **検証ノート**: 行番号厳密一致。finding本文の相互参照『finding 3』は文脈上 finding[2]の誤記だが判定に影響なし。戻り経路の先端保護として妥当な良い実装。

#### [d-chrome] Design/09 B タイムアウト自動退場=しない（公理9）  (CONFIRMED)
- **場所**: `NativeReadingScreen.kt:585・944-966`
- **症状**: ユーザーが出した上下バーをアプリがタイマーで勝手にしまうことがない。
- **根拠(検証済)**: heightOffset を変えるのは中央タップ(:783-788)・スクロール(:727-745)・onPostFling スナップ(:753-756 `settleTopBar`)のみで delay 自走退避は無い。唯一の delay は :585 `delay(2600)`＝復帰ヒントラベル(showChromeHint)の自動消灯で、対象は chrome でなく :944-966 の一過性ピル。charter B『ユーザーが閉じるまで居る』を満たす。
- **修正案**: 維持。復帰ヒントの一回性・自動消灯(:566-589)も charter B の『初回のみの一回性ヒント』に整合。
- **検証ノート**: chrome を消すタイマー不在・delay 対象がヒントラベルのみである点を確認。良い点として妥当。

#### [d-chrome] Design/09 D バー出入りで本文をリフローしない（公理6 恒常性）  (CONFIRMED)
- **場所**: `NativeReadingScreen.kt:894-897・846-853・ChapterContent.kt:103-106`
- **症状**: バーが退避/復帰しても本文の行位置が一切動かない。
- **根拠(検証済)**: 上下バーは Scaffold スロットでなくオーバーレイ：TopAppBar(:894-897)`graphicsLayer { translationY = topAppBarState.heightOffset }`＋コメント :895『レイアウトを再計算せず描画位置のみを変える』、BottomAppBar(:850-853)`graphicsLayer { translationY = bottomBarHeightPx * collapsedFraction }`。本文の版面 padding(ChapterContent.kt:103-106) はバー可視性と独立の固定値。charter D を満たしリフロー事故が構造的に起きない。
- **修正案**: 維持。ただし finding#1（システムバー hide 導入）時は :103-106 の statusBars/navigationBars 依存 inset を IgnoringVisibility 系へ替えないとシステムバー出入りで初めてリフローが発生する点に注意（この注記は正確）。
- **検証ノート**: graphicsLayer オーバーレイ化・固定 contentPadding を確認。良い点として妥当で、将来 finding#1 との依存注記も正しい。

#### [d-motion] 08 禁止則②（duration/easing/spring の直書き禁止＝トークン正本化）  (CONFIRMED)
- **場所**: `Motion.kt:20/24/28（token）→ BookCard.kt:171,309・WebBookCard.kt:62,155・NativeReadingScreen.kt:1026・ProcessingBanner.kt:177`
- **症状**: spring/duration の生数値が各画面へ散在せず Motion.kt に集約され値変更が1箇所で完結する。
- **根拠(検証済)**: grep 実測で MotionSpringCard は BookCard.kt:171,309／WebBookCard.kt:62,155、MotionSpringBarSettle は NativeReadingScreen.kt:1026、MotionDurationProgress は ProcessingBanner.kt:177 が参照。ツリー全体で `tween(` の出現は ProcessingBanner.kt:177 の1件のみでそれもトークン経由（durationMillis = MotionDurationProgress）＝野良直書きゼロ。
- **修正案**: 維持。未トークン化の fadeIn/fadeOut・既定 spring（finding[2][3][4]）も Motion.kt へ寄せれば完全一元化。
- **検証ノート**: spring/duration 系のトークン集約と野良 tween ゼロを grep で確認＝正当な良い点。

#### [d-motion] 08 禁止則⑤⑥・公理9（装飾ループ/自動再生/stagger/揺れ禁止・勝手に動かない）／G（ページめくりは直接操作）  (CONFIRMED)
- **場所**: `全ツリー（rememberInfiniteTransition/pulse/shake/animateContentSize/Crossfade は grep 0件）／読書本文 NativeReadingScreen.kt（縦スクロール）`
- **症状**: 装飾ループ・注意喚起の明滅/揺れ・初回 stagger 入場が皆無で、ページ送りは指1:1の縦スクロール直接操作。
- **根拠(検証済)**: grep 実測: rememberInfiniteTransition/InfiniteTransition/animateContentSize/Crossfade/shake/pulse すべて0件。無限ループ相当は Material 進捗のみ＝CircularProgressIndicator（ProcessingBanner.kt:105／NativeReadingScreen.kt:794 `is ParseResult.Loading -> CircularProgressIndicator()`）と LinearProgressIndicator（ProcessingBanner.kt:180）＝④進行の唯一許可枠。BookshelfScreen.kt:609,621,667,678 の Modifier.animateItem() はコメント（:606-608『削除時の詰め直しアニメ』／:666）どおり並び替え/フィルタの③連続性追跡で初回 stagger ではない（08-G 適用例合致）。page curl/slide 演出なし。
- **修正案**: 維持（追加不要）。
- **検証ノート**: 装飾ループ/揺れ系 API の不在を grep、進捗インジケータのみが④進行枠、animateItem がコメント上も③連続性用途であることを確認＝正当な良い点。

#### [d-token] 公理5 意匠版=トークン単一の真実 / ADR 0014 §D 意味色の役割分離  (CONFIRMED)
- **場所**: `ui/theme/Color.kt・Theme.kt / tools/check_design_tokens.py`
- **症状**: 色トークンの SSOT が実効的に守られている
- **根拠(検証済)**: `python3 tools/check_design_tokens.py` → `OK=96 NG=0 SKIP=42`(モックCSS⇄Compose色 全数一致)。ship-UI の Color(0x 直書きは ShioriCover:279 の1件のみ(RubyText は @Preview)。Color.kt:32/40-42 で OnSurfaceVariant(装飾3.79:1)と InfoText Light/Sepia/Dark(意味テキスト用 4.5:1↑)を『装飾 vs 意味を運ぶ文字』で役割分離し実測コントラストをコメント記録＝ADR 0014 §D を値レベルで具現。
- **修正案**: 維持。この色トークン規律を sp(finding0)・dp(finding1)へ同形(トークン+一致検査)で拡張するのが解。
- **検証ノート**: token check の OK=96 NG=0 を実行で再現確認。良い点として妥当、維持。

#### [d-token] ADR 0014 §C 禁止則『静謐: motion はフィードバックのみ・自動ループ無し』 / KB03 §5 ループ台帳  (CONFIRMED)
- **場所**: `ui/theme/Motion.kt / 各 tween・spring 呼び出し側`
- **症状**: motion の生数値散在が無く静謐原則が守られている
- **根拠(検証済)**: tween(/spring( を theme/ 外・トークン参照行除外で grep → ヒット0(生 spring()・生 tween 数値なし)。rememberInfiniteTransition/infiniteRepeatable → 0ヒット(装飾ループ無し=KB §5 ループ台帳が空で正)。ProcessingBanner.kt:177 は `tween(durationMillis = MotionDurationProgress)` とトークン経由。呼び出し側は全て Motion.kt スロット経由。
- **修正案**: 維持。値は実機調整でよくスロットが正本という Motion.kt 方針は ADR 0014 §C と整合。
- **検証ノート**: 生 spring/tween 不在・infinite ループ不在を grep で再現確認。良い点として妥当、維持。

#### [d-type] Design/05 §1 JLREQ（ルビ字サイズ=親文字1/2・ルビ行分離）  (CONFIRMED)
- **場所**: `RubyText.kt:65,69 / ChapterContent.kt:72-73 / ReadingSettingsSheet.kt:210`
- **症状**: —
- **根拠(検証済)**: RubyText.kt:69 `rubyFontSizeRatio: Float = 0.5f`＝JLREQ『親文字の1/2』準拠。RubyText.kt:65 デフォルト `lineHeight = 2.5.em`、実使用は ChapterContent.kt:73 `lineHeight = lineHeightEm.em` を bodyStyle 経由で RubyText と ParagraphItem に統一（:72 コメント『RubyText も style=bodyStyle 経由でこの lineHeight を受け取る』）。ReadingSettingsSheet.kt:210 `valueRange = 2.3f..2.8f`＝行間下限2.3emで前行とのルビ被りを抑制。
- **修正案**: 維持。比率で余白がスケールし JLREQ 圏の実装として守れている。
- **検証ノート**: 全参照 CONFIRMED。em 統一・比率0.5・行間下限2.3em はいずれも実在。良い点として妥当。

#### [d-type] Design/05 §2 コントラスト定石（純白×純黒回避・ダーク9:1域・和文 letterSpacing=0）  (CONFIRMED ⚠️調整)
- **場所**: `Theme.kt:54-55,81-82,101-102 / Typography.kt:24-114`
- **症状**: —
- **根拠(検証済)**: Theme.kt LIGHT bg #FBFAF8/text #1C1F26、SEPIA #F2E7CE/#3D3121、DARK #14171C/#C7CDD3＝純白#FFFFFF・純黒#000000 を回避。実測本文コントラスト LIGHT 15.81:1・SEPIA 10.30:1・DARK 11.21:1 で申告値(15.8/10.3/11.2)と一致、DARK は寒色暗面で研究値9:1域。Typography はほぼ全スタイル letterSpacing=0。
- **修正案**: 維持。本文数値層は研究値/規格に接地。
- **検証ノート**: コントラスト全数一致・純白純黒回避も確認。1点補正: 『全Typographyスタイルが letterSpacing=0』は不正確＝Typography.kt:28 displayLarge のみ `letterSpacing = (-0.25).sp`（Material 既定を保持）。ただし 57sp の display は和文本文に不使用のため良い点の趣旨に影響せず。良い点 維持。

#### [errtext] 公理10§J 静かな縮退 / 公理8 誠実さ（成功と偽らない沈黙）  (CONFIRMED)
- **場所**: `NativeReadingScreen.kt:484-490（continuationInfo の失敗握り）/ 本文・目次はローカル File 読み`
- **症状**: オフラインでも本棚・章本文・目次はローカルHTMLで読め、最終章の『続き』照会(網)が失敗しても静かに何も出さず読書没入を壊さない。
- **根拠(検証済)**: NativeReadingScreen.kt:484-485 コメント『オフライン等の失敗時は静かに何も出さない（読書の没入を通信エラーで壊さない）。次に最終章を開き直せば produceState が再起動し自然に再試行される。』、:486-490 `value = try { narouRepository.novelDetail(ncode)?.let { … } } catch (e: NarouApiException) { null }`。本文パスは :438 `ChapterHtmlParser.parse(File(htmlDirPath, currentFile))`＝ローカルFile依存で網を画面必須条件にしていない。
- **修正案**: 維持。継続カードの縮退は正直な沈黙で公理8にも反しない。
- **検証ノート**: 良い点として妥当。公理10§J/§Eの理想形（ローカルにあるデータをオフラインでエラーにしない）を満たす。

#### [errtext] 公理10（内部事情の翻訳）/ 08§C 消す先はログ・消す元はUI  (CONFIRMED)
- **場所**: `NovelApiRepository.kt:105-131（wrapApiException）`
- **症状**: 生 HTTP コード(429/5xx/4xx)・JSON例外・IO例外を、次の一手が伝わる平易な日本語へ正しく分類翻訳し、原コードは Log に保全。
- **根拠(検証済)**: NovelApiRepository.kt:109 `Log.w(TAG, "なろうAPI HTTPエラー: code=${e.code()}", e)` で原コード保全、:110-115 で 429→『アクセスが集中…』/500..599→『サーバが一時的に混み合って…』/400..499→『リクエストを処理できませんでした…』へ状態別写像。:123-127 で JsonEncodingException を IOException より先に分離し『ネットワーク未接続』の誤案内(:130)を回避。4xx を自動リトライ対象にせず持続性非リトライを遵守。露出しすぎ/伏せすぎの中庸。
- **修正案**: 維持。指摘[0]の自動リトライ層をこの集約点(wrapApiException)に足せば翻訳の質はそのまま残せる。
- **検証ノート**: 良い点として妥当。分類・ログ保全・JsonEncoding先行分岐すべてコードで確認。

#### [evolve] 公理6 永続性（更新で一括破棄しない）  (CONFIRMED)
- **場所**: `AppDatabase.kt:22,275-280`
- **症状**: 更新で蔵書・読書位置を一括破棄する経路が無い（良い側）。
- **根拠(検証済)**: grep fallbackToDestructive ＝NO HITS で破壊的フォールバック不在を確認。AppDatabase.kt:276-279 で MIGRATION_3_4/4_5/5_6/6_7/7_8/8_9/9_10/10_11/11_12/12_13/13_14/14_15/15_16/16_17 の14本を全て明示登録（数えて14本一致）。line22 `version = 17`。no-op 再スタンプは line145-149 (9_10) と line266-270 (16_17) が identity hash 前進のみで破壊なしを実確認。
- **修正案**: 現状維持（引く必要なし）。今後 version を上げる PR でも fallbackToDestructiveMigration を足さないことを維持する。
- **検証ノート**: 良い点として妥当・全事実 CONFIRMED。14本登録・fallback 不在・no-op 非破壊いずれもコードで裏取り。最終統合の件数として残す。

#### [evolve] 公理13 沈黙が既定値 / 公理23-D What's New は割り込みではない  (CONFIRMED)
- **場所**: `src/main grep / NovelReaderApplication.kt:165-181`
- **症状**: 更新のたびの新装開店（起動時 What's New モーダル・更新お知らせ通知）で再入場経路を塞ぐことが無い（良い側）。
- **根拠(検証済)**: grep -riE 'whatsnew|changelog|release.?note|新機能|更新情報|新装' src/main ＝NO HITS で What's New/changelog 実装の不在を確認。NovelReaderApplication.kt:165-181 の createNotificationChannel は CHANNEL_ID『PDF変換』(IMPORTANCE_LOW, line168) と NEW_EPISODE_CHANNEL_ID『新着話のお知らせ』(IMPORTANCE_DEFAULT, line178) の2チャネルのみで『新機能のお知らせ』チャネルは存在しない。
- **修正案**: 現状維持（引く必要なし）。changelog を置くなら設定最奥＋Play release notes の pull 型に留める（公理23-D-3）。
- **検証ノート**: 良い点として妥当・全事実 CONFIRMED。ファインダー引用 line173-178 は該当コメント＋episodeChannel 定義の範囲で line178 が『新着話のお知らせ』文字列＝主張どおり。最終統合の件数として残す。

#### [gesture] 公理17 メタファーの単一性(充足) / C-2 タップゾーン上限(充足)  (CONFIRMED)
- **場所**: `NativeReadingScreen.kt:781-790,:866,:887 / ChapterContent.kt:87 / RubyText.kt:45-46`
- **症状**: 方向を持つ操作が単一メタファー(moving-text・横書きLTR・縦スクロール)から一貫導出され、導出元の割れる競合(スワイプめくり/進捗スライダー)が皆無＝メタファー分裂なし。
- **根拠(検証済)**: RubyText.kt:45-46 コメント『ルビだけ Canvas で親文字の上に重ね描きする』＝ルビ=親文字の上の横書き(縦書きwritingMode/グリフ回転なし)。ChapterContent.kt:87 `LazyColumn(state = lazyListState, ...)`＝縦スクロール。章送りは NativeReadingScreen.kt:866 `Icons.AutoMirrored.Filled.ArrowBack contentDescription="前の章"` / :887 `ArrowForward ... "次の章"`＝横書きLTR整合。grep 全走査で HorizontalPager/VerticalPager=0件、detectHorizontalDrag/draggable(本文)=0件、Slider は ReadingSettingsSheet.kt:171/204/236 の設定3本のみ＝進捗シークバー不在。窓メタファー混入は構造的に起きない。
- **修正案**: 維持＝横書きLTRの単一メタファーを崩す方向操作(スワイプめくり/進捗スライダー)を後付けしない。追加時は公理17 B-1 導出表で向きを機械検算。
- **検証ノート**: 全ての充足主張(ruby上置き横書き・LazyColumn縦・LTR章送り・競合ジェスチャ不在)をコード/grepで裏取り。良い点として妥当。

#### [gesture] 公理11/D 可視代替の義務(充足) / C-2 単一ゾーン(充足) / B-2 JIT  (CONFIRMED)
- **場所**: `NativeReadingScreen.kt:781,:583,:859-890,:871 / ReadingSettingsSheet.kt:121-143,:171,:204,:236`
- **症状**: ジェスチャ専用機能が皆無。章送り=可視ボタン、文字サイズ/行間/余白=設定スライダー、目次/テーマ=ボタン。chromeトグルは全面単一タップゾーンに集約しJIT初回告知。破壊的ジェスチャ無し。
- **根拠(検証済)**: 全機能に可視経路: 下部バー章送り NativeReadingScreen.kt:859-889、目次ボタン :871-879、ReadingSettingsSheet.kt テーマ FilterChip×3(:121-143)＋Slider×3(文字サイズ:171/行間:204/本文余白:236)。tap は単一 `pointerInput(Unit)`(:781、コード全体でこれ1箇所)で全面=chromeのみ＝ゾーン実質1。grep 全走査で systemGestureExclusion=0件、独自端スワイプ/draggable(本文)/削除破壊ジェスチャ=0件。JITヒントは :583 で `putBoolean("immersive_hint_shown", true)` により1回で永続消灯。
- **修正案**: 維持＝四隅/多ゾーン化や破壊的ジェスチャを足さない。新語彙追加時は公理17 C-3 の4問審査(頻度・導出可能・可視代替・衝突なし)を関門にする。
- **検証ノート**: 可視代替の網羅・単一ゾーン・JIT一回性をコード/grepで全数裏取り。良い点として妥当。

#### [ia] 15-§E 現在位置の表明＋認識＞想起／公理5 単一真実源  (CONFIRMED)
- **場所**: `NativeTableOfContentsScreen.kt:195-246`
- **症状**: 目次を開くと現在章が強調され1つ手前まで自動スクロール済みで『いまどこ・残りどれだけ』が即分かる。
- **根拠(検証済)**: NativeTableOfContentsScreen.kt:195 `val currentIndex = entries.indexOfFirst { it.fileName == currentChapterFile }`、:199-204 `LaunchedEffect(entries, currentChapterFile){ if(currentIndex>=0) listState.scrollToItem((currentIndex-1).coerceAtLeast(0)) }`(先頭張り付き回避)、:222/234/245 でaccent背景+左バー、:242 太字＝色だけに頼らず単一真実源(currentChapterFile)から派生。§E行88-89・公理5・公理9例外を満たす。
- **修正案**: 維持。
- **検証ノート**: 全実体一致でCONFIRMED。良い点として妥当。

#### [ia] 15-§F 0件3原則（明示・語を保持した編集足場・茶化さない）／公理6  (CONFIRMED)
- **場所**: `DiscoveryResultScreen.kt:503-527／DiscoveryViewModel.kt:374-383`
- **症状**: 検索0件で『条件に合う作品が見つかりませんでした』と明示し、『検索条件を変える』で検索語・条件を保持したまま検索画面へ戻れて袋小路にならない。
- **根拠(検証済)**: DiscoveryResultScreen.kt:516-524 ResultEmpty で `Text("条件に合う作品が見つかりませんでした"...)`＋`OutlinedButton(onClick = if(isSearch) onAdjust else onBackToDiscovery){ Text(if(isSearch)"検索条件を変える" else "ほかの条件で探す") }`(中立文言＋CTA)。DiscoveryViewModel.kt:374-383 `executeSearch()` は `_searchDraft` を消さず openResult のみ、:379-381 で `draft.word` を履歴へ addRecent。戻り先 DiscoverySearchScreen.kt:94 `val draft by viewModel.searchDraft.collectAsStateWithLifecycle()` が同じ draft を読む＝語・範囲・条件が残る。
- **修正案**: 維持（次善で0件画面に『検索範囲を広げる』直接導線を足すと§F②スコープ拡大提案として更に良い）。
- **検証ノート**: 0件明示・draft非破棄・戻り先がdraft参照・履歴二重安全とも実体一致でCONFIRMED。良い点として妥当。

#### [idempo] 公理3 べき等性  (CONFIRMED)
- **場所**: `PdfProcessingService.kt:117-142 / PdfImportViewModel.kt:96 / BookshelfViewModel.kt:288`
- **症状**: 同一PDFの連続追加・DLボタン連打でも二重変換・二重登録にならない。
- **根拠(検証済)**: PdfProcessingService.kt:122-132 `lock.withLock { if (!activeUris.register(uri.toString())) Pair(true,false) else { uriQueue.add(...); … } }` でキュー段の同一URIを弾き、:134-142 で黙殺せず showDuplicateNotification により『取込済み』通知（公理8 整合）。多層防御として BookEntity.kt:23-28 の contentSha256（内容指紋）＋title+author 照合が変換後にも弾く。縦書き取込は PdfImportViewModel.kt:96 `if (_uiState.value is PdfImportUiState.Downloading) return` で再捕捉を無視。BookshelfViewModel.kt:288-289 `val prompt = _importPrompt.value ?: return; _importPrompt.value = null` で confirmImport 再入も防止。全取込経路で冪等化が担保。
- **修正案**: 変更不要（公理3 を満たす）。
- **検証ノート**: 引用4点すべて該当行に実在し主張どおり。register の重複時 Pair(true,false)→重複通知→return の流れ、contentSha256 多層防御いずれも確認。良い点として妥当。

#### [idempo] 公理4 可逆性（可逆な操作に確認を出さない＝確認疲れ回避）  (CONFIRMED)
- **場所**: `BookshelfScreen.kt:611-612,669 / BookshelfViewModel.kt:334 / DefaultBookRepository.kt:87`
- **症状**: Webカードを本棚から外す操作に確認を挟まず、詳細画面の『本棚に置く』で即戻せる。読書進捗は ncode キーで残り再追加で復元される。
- **根拠(検証済)**: BookshelfScreen.kt:611-612 コメント『(b) Web由来カード。外す操作は確認ダイアログを挟まない: 蔵書削除と違い読書進捗等の失うものが無く、詳細画面の「本棚に置く」で即座に戻せるため』、:669『グリッドと同じ判断（確認ダイアログ無し＝失うものが無く即座に戻せる）』。BookshelfViewModel.kt:334-336 `fun removeWebNovel(ncode) { … repository.removeWebNovel(Ncode(ncode)) }`。DefaultBookRepository.kt:87-88 `override suspend fun removeWebNovel(ncode) = webNovelDao.deleteByNcode(...)` は webNovel 行のみ削除し web_reading_progress（webReadingProgressDao）に触れない＝進捗保持を裏付け。破壊的削除（Finding 0）と可逆な除去を意図的に非対称扱い。
- **修正案**: 変更不要（公理4 を満たす）。この可逆/不可逆の非対称原則を蔵書削除側（Finding 1→Undo化）へ同論理で拡張するのが筋。
- **検証ノート**: コメント文言・removeWebNovel の deleteByNcode 単独実行（progress 非削除）を実コードで確認。可逆操作に確認を課さない設計判断は公理4 に整合。良い点として妥当。

#### [measure] 公理3 べき等性（二度取込＝1冊）＋24層 C表#4  (CONFIRMED)
- **場所**: `android/app/src/main/java/com/novelreader/PdfProcessingService.kt:574 ActiveUriTracker（ActiveUriTrackerTest）＋repository/BookRepositoryTest`
- **症状**: 同一URI連続投入・別URI同内容・ハッシュ無し旧取込の再取込を3層で弾き、べき等性が機械検証で再発不能化
- **根拠(検証済)**: PdfProcessingService.kt:574 `internal class ActiveUriTracker`（L578 register=add の真偽、L581 release、L584 clear）。ActiveUriTrackerTest が L14 register 初回true/再投入false、L23 別URI独立、L33 release 後新規化、L43 clear を固定。BookRepositoryTest が L245/253 findExistingBookByHash、L262/269 sha256Hex 既知ベクタ、L230/236 findByTitleAndAuthor を固定。純ロジック抽出＋テストで公理3を守る。
- **修正案**: 変更不要（維持）。
- **検証ノート**: 位置・テスト名・assert すべて実在。良い点として妥当。

#### [measure] 24層 C表#7 マイグレーション／identity hash（静かに壊れる回復パスをテストへ）  (CONFIRMED)
- **場所**: `android/app/src/androidTest/java/com/novelreader/data/MigrationTest.kt:62 migrate7to17_validatesSchemaAtEachStep／:210 freshInstallAtV17_passesIdentityHashCheck`
- **症状**: version↔schema-hash 不整合による起動即クラッシュ（task_diary #39 の実機2回踏み）を実機投入前に捕捉
- **根拠(検証済)**: L62 `fun migrate7to17_validatesSchemaAtEachStep()`＝L67 以降 `helper.runMigrationsAndValidate(TEST_DB_CHAIN, N, true/false, AppDatabase.MIGRATION_*)` で 7→17 全段を検証。L210 `fun freshInstallAtV17_passesIdentityHashCheck()`。本番 `AppDatabase.MIGRATION_*` を internal 直参照（L34 コメント／L67-136 各段）で SQL 写経の二重真実源を排除。並列レーン分岐（8→9・15→16）は L69/74・L95/100 で `validateDroppedTables=false` を明示 encode。
- **修正案**: 変更不要（維持）。
- **検証ノート**: テスト名・行番号・MIGRATION_* 直参照・validateDroppedTables=false の分岐 encode すべて確認。24層 A『テストされて初めて公理』の DB 層実装として良い点妥当。

#### [nav] 公理1 経路独立性（固定開始地点＋疑似バックスタック）  (CONFIRMED)
- **場所**: `MainActivity.kt:155-160,178,368 / BookshelfScreen.kt:206`
- **症状**: 読書画面はどの経路（本棚カード / 変換完了通知 deep link / cold start / onNewIntent）で来ても Up が必ず本棚へ戻り、初期ファイルは getLastRead で一意に決まる＝完全な経路独立。
- **根拠(検証済)**: deep link着地(MainActivity.kt:156-159) `navController.navigate("reading/$bookId/$startFile"){ launchSingleTop=true; popUpTo("bookshelf"){ inclusive=false } }` が固定起点を保証。カード経路(:178) `navController.navigate("reading/$bookId/$startFile"){ launchSingleTop = true }`。Up は(:368) `onNavigateToBookshelf = { navController.popBackStack("bookshelf", false) }`。開始ファイルは両経路とも getLastRead: MainActivity.kt:155 `viewModel.getLastRead(BookId(bookId)) ?: "index.html"`、BookshelfScreen.kt:206 `viewModel.getLastRead(BookId(book.id)) ?: "index.html"` で一致。
- **修正案**: 維持（変更不要）。Android UX原則A『ディープリンクは疑似バックスタックを合成』の教科書的実装。deep link 不在ケース(:163)も popBackStack("bookshelf", false) で固定起点へフォールバックしている。
- **検証ノート**: 全 file:line 実在・引用どおり。card/deep link 双方が getLastRead で開始ファイルを決め、Up は destination 名指しの固定Up。経路独立の主張は成立。

#### [nav] 公理1 経路独立性 ＋ 公理5 SSOT  (CONFIRMED)
- **場所**: `DiscoveryResultScreen.kt:143-148,348 / MainActivity.kt:248,282`
- **症状**: 結果一覧の Up（App bar ←）が経路（検索/ジャンル/気分/キーワード）に依らず常に発見ホームへ固定。履歴Backは端末Back/『条件を変更』へ分離。キーワード経由も result 単一保持で SSOT と経路独立を両立。
- **根拠(検証済)**: MainActivity.kt:248 `onUp = { navController.popBackStack("discovery", false) }` が全経路共通の固定Up。キーワード経由(:282) `popUpTo("discovery") { inclusive = false }` で既存 result/detail を畳み [bookshelf, discovery, result] に固定。DiscoveryResultScreen.kt:143-147 `IconButton(onClick = onUp)`＋contentDescription "発見に戻る"。:348 `if (ctx.source == ResultSource.SEARCH)` で『条件を変更』チップを SEARCH 発のみに限定し騙し導線を回避。
- **修正案**: 維持（変更不要）。Up（destination固定）と Back（履歴/条件変更）を明示分離した正しい実装で、他画面の基準にできる。
- **検証ノート**: 全 file:line 実在・引用どおり。onUp の popBackStack("discovery",false)・キーワードの popUpTo・SEARCH 限定チップすべて確認。SSOT は resultContext の VM 単一保持で担保。

#### [notify] 公理13-E/G 進捗の作法と読書中の沈黙  (CONFIRMED)
- **場所**: `PdfProcessingService.kt:467-549 / BookshelfScreen.kt:102,226`
- **症状**: ユーザー起点の取込に対し、単一進捗通知・IMPORTANCE_LOW(無音)・『停止』アクション・完了は進捗バー無し通知へ差し替え・失敗も報告・完了は書名+deep link を備える。
- **根拠(検証済)**: 進捗は単一 ID 更新（PdfProcessingService.kt:488 notify(NOTIFICATION_ID,…)）＋setOngoing(true)(:478)＋addAction(0,"停止",…)(:482)＝E§1/4。IMPORTANCE_LOW（NovelReaderApplication.kt:169）＝E§2。完了通知は setProgress を持たず非 ongoing で上書き（:514-524）＝E§3。onFailure→showErrorNotification（:373）＝E§5。完了は書名+openBookIntent(bookId) deep link（:517,521）＝E§6/D。読書中保護も成立: processingState/errorEvents は BookshelfScreen のみ収集（:102 processingState.collect、:226 errorEvents.collect）、NativeReadingScreen 等の読書画面は購読せず（grep 0）＝G§155。PDF 通知は LOW で読書中に鳴らない＝G§141。
- **修正案**: 維持。この作法（無音化・in-app 優先）を新着話チェック側にも適用すれば指摘[0]の解消に向かう。
- **検証ノート**: 全根拠を実体で確認。正当な良い点。

#### [notify] 公理13-F §119/§121 権限要求タイミングとグレースフルデグレード  (CONFIRMED ⚠️調整)
- **場所**: `BookshelfScreen.kt:118-165`
- **症状**: 通知権限を起動直後でなく最初の取込操作の文脈で要求し、拒否されても取込は完走する。
- **根拠(検証済)**: 権限チェック/要求は launchPdfPicker（取込 FAB 経路 :152-165）に限定され起動時には求めない＝F§119。notificationPermissionLauncher コールバックは付与結果に関わらず `pdfPicker.launch(...)` を呼ぶ（BookshelfScreen.kt:121-123）、SDK<33 は else 分岐で素通り（:162-164）＝通知不可でも取込成立（F§121 グレースフルデグレード）。チャネルは createNotificationChannel で 2 本のみ生成（NovelReaderApplication.kt:165-181）。
- **修正案**: 維持。
- **検証ノート**: 主眼（F§119 タイミング・F§121 デグレード）は CONFIRMED で良い点として成立。ただし付随主張『お知らせ系チャネルが無い』は不正確＝NEW_EPISODE_CHANNEL_ID の表示名は文字どおり『新着話のお知らせ』（IMPORTANCE_DEFAULT）で、これは指摘[0]が問題視する再訪リマインダ用チャネルそのもの。この一点のみ REFUTED として調整（adjusted）、良い点の核は維持。

#### [persist] 公理6 永続性（読書位置・章/目次遷移・シート開閉の徹底保存）  (CONFIRMED)
- **場所**: `NativeReadingScreen.kt:133-144,:176-183,:344-345,:517-519,:529-561,:709-714`
- **症状**: 回転・ダーク切替・プロセスdeath・アプリ切替をまたいでも、章・章内スクロール・章⇄目次の遷移履歴（Back段階）・表示/紐付けシート開閉が保たれる。
- **根拠(検証済)**: navHistory は NativeReadingScreen.kt:133-136 `var navHistory by rememberSaveable(key = "navHistory_${bookId.value}", stateSaver = listSaver<List<String>, String>(save = { it }, restore = { it }))`（bookId をキーに混線防止）、lastChapterFile も :142 `var lastChapterFile by rememberSaveable(key = "lastChapter_${bookId.value}")`。スクロールはDBを正本に :529-535 の snapshotFlow+`.debounce(400)` 継続保存＋:550-561 の ON_STOP 即時フラッシュ（コメントで生命線の安全性＝目次除外/ゼロ上書き回避/CONFLATED冪等を明示）で取りこぼしを塞ぐ。復元は :176-183 getProgress→chapterRestore を :344-345 `initialScrollIndex = if (resolvedFile == restore.targetFile) restore.scrollIndex else 0`（章一致時のみ）で :517-519 `LazyListState(initialScrollIndex, initialScrollOffset)` へ注入し先頭→保存位置ジャンプを防ぐ。シート開閉も :709 `var showSettings by rememberSaveable { mutableStateOf(false) }` / :714 showLinkSheet。
- **修正案**: 維持。一時UI状態と永続データの分離が正しく、この画面が公理6の模範。
- **検証ノート**: 引用行すべて実在・主張どおり＝良い点として確定。留意点として lazyListState は :517 `remember(currentFile)`（rememberSaveableでない）ため、章内スクロールの構成変更/プロセスdeath跨ぎ復元は完全にDB(getProgress)経由に依存する＝[3]の低確率レースが唯一の理論的取りこぼし窓だが、プロセスdeathはON_STOP後に背景で書込完了の余地があり実害はほぼ無い。

#### [persist] 公理6 永続性（検索ドラフト・スクロール・展開状態の生存）  (CONFIRMED)
- **場所**: `DiscoverySearchScreen.kt:94,:98,:155,:212,:433-442,:490 / DiscoveryViewModel.kt:298-304,:462-464 / SearchDraft.kt:98`
- **症状**: 検索語・除外語・検索範囲・条件シートの絞り込み・カスタム数値の生入力・縦スクロール・カテゴリ展開・『ジャンル別を見る』展開が、条件を練る往復や回転・プロセスdeathをまたいで残る。
- **根拠(検証済)**: SearchDraft は SearchDraft.kt:98 `@Parcelize` の data class で、DiscoveryViewModel.kt:298 `private val _searchDraft = MutableStateFlow(SearchDraft())` に持ち、:301-304 `fun setSearchDraft(draft: SearchDraft) { _searchDraft.value = draft; savedStateHandle[KEY_SEARCH_DRAFT] = draft }` でSavedStateHandleへミラー＝process death 復帰の :462-464 `savedStateHandle.get<SearchDraft>(KEY_SEARCH_DRAFT)?.let { restored -> _searchDraft.value = restored }` まで生存。条件シート開閉は DiscoverySearchScreen.kt:98 `var showSheet by rememberSaveable { mutableStateOf(false) }`、縦スクロールは :212 `.verticalScroll(rememberScrollState())`（Saver有り）、カテゴリ展開は :433-442 `rememberSaveable(saver = listSaver(save = { map -> map.filterValues { it }.keys.toList() }, restore = {...mutableStateMapOf...}))`、ジャンル別展開は :490 `var showGenreKeywords by rememberSaveable { mutableStateOf(false) }`。一過性の isFocused だけ :155 `var isFocused by remember { mutableStateOf(false) }`＝構成変更で再フォーカスされ得る一過性で適切。
- **修正案**: 維持。VM+SavedStateHandle と rememberSaveable の使い分けが公理6の手本。
- **検証ノート**: 引用行すべて実在・主張どおり（@Parcelize は SearchDraft.kt:98 で確認、SnapshotStateMap の listSaver は展開中キーのみ保存で復元、rememberScrollState/rememberSaveable も確認）＝良い点として確定。

#### [portable] 公理18候補 D 再結合キーの材料  (CONFIRMED)
- **場所**: `BookEntity.kt:28 / DefaultBookRepository.kt:172,468`
- **症状**: —
- **根拠(検証済)**: BookEntity.kt:23-28 に `val contentSha256: String? = null`（コメント『取込元 PDF バイト列の SHA-256（小文字16進）』）。DefaultBookRepository.kt:172 `val contentSha256 = tempFile.inputStream().use { sha256Hex(it) }`、実装は :468 `internal fun sha256Hex(input: InputStream)`。取込時にPDFバイトのSHA-256を計算し books 行へ保持＝D節が名指しで推奨する再結合キー材料が既に敷かれている（現状は重複排除用途だが土台として妥当）。
- **修正案**: —
- **検証ノート**: コード実体を BookEntity.kt:28・DefaultBookRepository.kt:172/468 で確認。将来メタデータ層バックアップ＋ハッシュ再結合を実装する際の基盤として妥当な良い点として確定。

#### [portable] 公理18候補 D 位置レコードを絶対パスFKにしない  (CONFIRMED)
- **場所**: `ProgressEntity.kt:8-9`
- **症状**: —
- **根拠(検証済)**: ProgressEntity.kt:8 `@PrimaryKey val bookId: String`、:9 `val lastReadFilename: String`（パスでなくファイル名）。読書位置は bookId + ファイル名に紐付き絶対パスをFKに持たない。D節が警告する『位置レコードが絶対パスをFK→復元は全揃うか全無効かの博打』アンチパターンを位置レコード側で回避（絶対パスは BookEntity.htmlDirPath に隔離）。可搬経路さえ通せば位置の再結合が現実的な設計。
- **修正案**: —
- **検証ノート**: ProgressEntity.kt:8-9 の実体で確認。位置レコードの疎結合設計は妥当な良い点として確定。

#### [privacy] 公理15 A(集めないことは機能=Privacy by Design 既定値)  (CONFIRMED ⚠️調整)
- **場所**: `AndroidManifest.xml:23 ＋ 全 gradle`
- **症状**: 読書位置・しおり・本棚・検索履歴が全て端末内 DB/DataStore に閉じ、外部送信・計測・クラッシュ収集が構造的に存在しない。
- **根拠(検証済)**: AndroidManifest.xml:23 `android:allowBackup="false"`(理由コメント :13-20)で Auto Backup 経由の端末外流出を遮断。gradle の firebase/crashlytics/analytics/sentry/bugsnag 等 grep = NONE。外部通信は api.syosetu.com メタ＋WebView＋PDF DL のみで本文アップロード無し(charter(d)充足)。
- **修正案**: 維持。将来 SDK/同期を足す際は F(申告更新)と C(送信パス1箇所集約)を通すこと。
- **検証ノート**: allowBackup=false と解析/クラッシュSDK皆無という核の良い点は事実で CONFIRMED。ただし2点補正(adjusted): (1)『title/word/book を載せる Log NONE』は DB のタイトル/文字数フィールドについては真だが、PdfImportViewModel.kt:92 が disposition の filename(書名を含みうる)をログ出力する反例あり(指摘[1]参照)。(2)allowBackup=false のコード上の動機はコメント(:14-19)では DB/ファイル整合であってプライバシーではない—ただしプライバシー正の効果は実在する。

#### [privacy] 公理15 E(消したら本当に消える=カスケードの1箇所実装)／公理8  (CONFIRMED)
- **場所**: `DefaultBookRepository.kt:385-394 deleteBook`
- **症状**: 蔵書削除で本体行と読書位置を原子的トランザクションで束ね、HTML実体も削除し、失敗分は次回起動の孤立掃除が拾う。
- **根拠(検証済)**: DefaultBookRepository.kt:385-388 `runInTransaction { bookDao.deleteById(book.id); progressDao.deleteByBookId(book.id) }`＋why コメント :381-384(『本体は消えたが progress だけ残る孤児』を原子性で排除)。:392 `File(book.htmlDirPath).deleteRecursively()`(トランザクション外=DB外副作用の分離を :389-391 で説明)。:319-328 cleanOrphanHtmlDirs が books に無い bookId ディレクトリを次回起動で回収。
- **修正案**: 維持。ただし ncode 紐付け由来の web_reading_progress は本カスケードの射程外(指摘[0]参照)=ここへ deleteByNcode を相乗りさせると完全化する。
- **検証ノート**: 引用実体・行番号とも一致し設計は正確。web_reading_progress がカスケード射程外である旨を自認しており([0]と整合)、良い点評価は妥当。

#### [reach] 21-D 版面の自律性（タブレット横持ちで本文を全幅に引き伸ばさない）  (CONFIRMED)
- **場所**: `ChapterContent.kt:87-93（LazyColumn horizontalAlignment=CenterHorizontally）＋ :142（widthIn(max=600.dp)）`
- **症状**: （守れている）広幅端末でも本文ブロックが 600dp で頭打ち＋中央寄せになり、1行が60字超に伸びる可読性破壊を構造的に回避。
- **根拠(検証済)**: ChapterContent.kt:93 `horizontalAlignment = Alignment.CenterHorizontally`、:91-92 コメント『widthIn(max=600dp) が効く広幅端末（タブレット等）で本文ブロックが左に張り付くのを防ぐ』、:142 `.widthIn(max = 600.dp)`。標準21-D line83『タブレット横持ちで全幅に引き伸ばすと1行60字超＝可読性破壊』の悪例を回避。
- **修正案**: 現状維持。字数基準化（別Minor=finding[3]）は将来の上積み。
- **検証ノート**: 上限cap+中央寄せの両方が実体として存在しCONFIRMED。charter(b) の Major攻撃『タブレット/横で全幅引き伸ばし』を通過＝良い点。

#### [reach] 21-A（保持を検出しない・どの保持でも成立する既定）／公理9  (CONFIRMED)
- **場所**: `全画面（センサ/利き手検出コードなし）＋ NativeReadingScreen.kt:846-891（下端バー3ボタン weight(1f) 左右対称）`
- **症状**: （守れている）加速度センサ等でグリップ・利き手を推定して UI を鏡像化・移動させるコードが皆無。下端バーとシート類は左右対称で、保持推定に追従してUIが逃げる公理9違反を作っていない。
- **根拠(検証済)**: grep 実測: app/src/main 全体で `SensorManager|accelerometer|Sensor\.|handed|leftHand|rightHand|reverseLayout` の一致ゼロ。NativeReadingScreen.kt:859-890 下端バーは IconButton×3（前の章/目次/次の章）を各 `modifier = Modifier.weight(1f)` で等分。表示設定・紐付けシートは下端 ModalBottomSheet。標準21-A line24『保持を検出してレイアウトを動かさない』/適用例 line160『しない。ゾーンとシートは左右対称＝推定不要の設計』。
- **修正案**: 現状維持（保持は状態として持たない設計を保つ）。
- **検証ノート**: 検出コード不在(grep NONE)・下端バー等分対称ともCONFIRMED。charter(d)『利き手/グリップ推定でレイアウトを動かすコード』該当なし＝良い点。

#### [settings] 19-E 即時プレビュー（適用/OK/保存ボタン・変更確認ダイアログ・編集バッファを置かない）／公理4  (CONFIRMED)
- **場所**: `ReadingSettingsSheet.kt:171-250 / NativeReadingScreen.kt:205-210,220-224,232-236`
- **症状**: フォント・行間・余白を動かすと下の実本文がその瞬間に変わる。適用ボタン・OK・確認ダイアログ・往復が無く、0.1em刻みの探索が課税されない。
- **根拠(検証済)**: NativeReadingScreen.kt:205-206 `// ドラッグ中の毎値：本文プレビュー追従のため状態のみ更新（永続化しない）` `val onFontSizeChange: (Int) -> Unit = { size -> fontSize = size }`（行間221・余白233も同型）。永続化は確定時に一度だけ：:208-210 `val onFontSizePersist: () -> Unit = { prefs.edit().putInt("reading_font_size", fontSize).apply() }`、UI側 ReadingSettingsSheet.kt:175 `onValueChangeFinished = onFontSizePersist`（行間209・余白240も同型）。テーマも ReadingSettingsSheet.kt:124 `onClick = { onThemeChange(theme) }` で即反映。シート(37-252)内に適用ボタン・確認ダイアログは一切無し＝編集バッファを作らない即時プレビューの模範。
- **修正案**: 現状維持。重い再ページネーションが将来生じても適用ボタンへ逃げず debounce＋可視ページ即時反映を保つ。
- **検証ノート**: onXxxChange=state更新のみ・onXxxPersist=onValueChangeFinished一括の2コールバック構造、apply/OK/ダイアログ皆無をコードで確認。公理E充足＝良い点として妥当。

#### [settings] 19-B 既定値は設計・変更は宣言（優先順位: アプリ内明示＞OS追従＞既定）／19-D 微調整は活動を解除しない（直交軸）  (CONFIRMED)
- **場所**: `MainActivity.kt:81-88,125-129 / NativeReadingScreen.kt:203,218,230,335-342`
- **症状**: テーマ未指定なら端末のライト/ダークに従い、一度選ぶとOSが夜になっても勝手に覆さない。テーマを替えても字サイズ・行間・余白は巻き戻らず、字間を触ってもテーマは解除されない。
- **根拠(検証済)**: loadInitialTheme(MainActivity.kt:126-128) `val systemFallback = if (systemDark) ReadingTheme.DARK else ReadingTheme.LIGHT` / `val saved = prefs.getString("reading_theme", null) ?: return systemFallback` / `return runCatching { ReadingTheme.valueOf(saved) }.getOrDefault(systemFallback)`＝キー不在で宣言の有無を分離し、優先順位(アプリ明示＞OS追従＞既定)と不正値フォールバックを実装。字サイズ/行間/余白は独立キー（NativeReadingScreen.kt:203 reading_font_size・218 reading_line_height・230 reading_body_margin）でテーマ(reading_theme)と直交、335-342で各々別コールバックとして ChapterScreen へ渡され相互に触れない。getInt/getFloat 第2引数フォールバックで未宣言ユーザーは既定値の進化に追従。
- **修正案**: 現状維持。将来テーマ従属の微調整（夜だけ明るさ下げ等）を足す際も差分をテーマごとに保持し直交を崩さない。
- **検証ノート**: loadInitialThemeのnullフォールバックとrunCatching・独立prefキー・直交コールバック配線を実地確認。指摘[0]（未宣言へ戻す経路の欠如）と矛盾しない＝[3]は読み出し側の優先順位実装を評価、[0]は書き込み側の未宣言化欠如を指摘で両立。公理B/D-2充足＝良い点として妥当。

#### [ssot] 公理5 SSOT（守れている）  (CONFIRMED)
- **場所**: `app/src/main/java/com/novelreader/viewmodel/ShelfItems.kt:101-137 と消費側 BookCard.kt:164 / BookshelfScreen.kt:384`
- **症状**: カードの%表示・朱印『了』・状態フィルタ（すべて/よみかけ/未読/読了）が同一計算から導かれ、章数も chapterCountMap 一本に集約。
- **根拠(検証済)**: readingStatusFor(ShelfItems.kt:125-137)は :126 で progressFractionFor を経由し、カード表示（BookCard.kt:157 progressFractionFor / :164 readingStatusFor / :255,377 BookProgressRow）と状態フィルタ（BookshelfScreen.kt:384 `filterShelfByStatus(books, webNovels, selectedStatus, progressMap, chapterCountMap)`→ ShelfItems.kt:156 `readingStatusFor(progressMap[it.id], chapterCounts[it.id] ?: 0)`）が同じ progressMap+chapterCountMap から同じ関数で導出。『カードは未読なのにフィルタでは読了』という食い違いを構造的に排除。
- **修正案**: 維持（変更不要）。この単一真実源を崩す派生値の二重保持を今後も入れないこと。ただし単一ソース側の 1f 断定は指摘[0]で是正すること。
- **検証ノート**: SSOT（表面間の無矛盾）は実際に守られており good-point は正当。ただし指摘[0]と両立: 共有する単一ソース progressFractionFor 自体が最終章スクロール時に 1f の過大主張をするため、その嘘は3表面へ一貫して伝播する（公理5は満たすが公理8は別問題）。

#### [ssot] 公理5 SSOT / 公理8 嘘をつかない（守れている）  (CONFIRMED)
- **場所**: `app/src/main/java/com/novelreader/viewmodel/BookshelfViewModel.kt:205-226`
- **症状**: 章移動とスクロール位置の保存要求を1本のチャネルに統合し『最後に送られたユーザー操作』が確実に最後に DB へ着地。
- **根拠(検証済)**: BookshelfViewModel.kt:214 `private val progressChannel = Channel<ProgressEntity>(Channel.CONFLATED)`。コメント :205-213 が旧2チャネル構成の競合（章送り直後に旧章スクロール書き込みが後着し lastReadFilename が旧章へ巻き戻る）を明記。init(:216-226)で単一コルーチンがチャネルを排出し :221-223 で repository.saveScrollPosition へ順序保証付きで書く。
- **修正案**: 維持（変更不要）。
- **検証ノート**: コメントとコード実体が一致。CONFLATED により中間値破棄・最新値のみ着地で、読書位置が古い値へ巻き戻り本棚/読書画面へ嘘の再開位置を出す事故を排除＝good-point 正当。
