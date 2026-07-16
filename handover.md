# handover — やること台帳（main）

> **次に何をやろうか悩んだら、まずここを見る。**
> 作る予定のもの・あとで拾う思いつき・その場から漏れた取りこぼしを書き溜める場所。
> 思いついたら「思いつき・取りこぼし」へ追記して育てる。
>
> **ここは「やること」だけを置く。** 完了したら打ち消し線で残さず**消す**（完了の正本は **git log**・現況は `STATUS.md`・
> 実コミット等の一次情報は `.claude/plans/`・腐りにくい知見は `docs/knowledge/`〔1知見=1ファイル。`task_diary.md` は凍結アーカイブ〕）。
> 打ち消し線を溜めると「やったことリスト」に化けて台帳の役目を失う（運用: memory `docs-status-vs-handover-split`）。

## 思いつき・取りこぼし（随時追記）

> レビュー中・実装中に出た宿題や着想で、まだ下の各節に整理していないものをここへ。育ったら該当節へ移す。

- **[機能②・要判断] U1 新着チェックとの整合**: 続きあり判定は現状 PDF 蔵書の generalAllNo 突合のまま。Web カードの既読話数（web_reading_progress）を U1 基準へ組み込むかは別タスク（機能②実装時からの持ち越し）。
- **[取込] 「PDFを取り込む」ボタンの不安定さ**（2026-07-10 ユーザー報告・**再現条件未特定**）: FAB/空状態ボタンからの picker 起動が不安定に感じられることがある。まず再現条件の聞き取り（どの画面のボタンか・無反応か遅延か・バッテリー最適化ダイアログ絡みか）→ logcat での再現観察から。推定候補: 通知権限→picker の2段ゲート・バッテリー最適化ダイアログの分岐（`launchPdfPicker` 周り）だが未確定のため決め打ち修正はしない。
- **[目次/本文] モーション最適化 — 残りは P1/P2 のみ**（2026-07-15）: 確定差分は適用・**遷移は実機確認済みで slide push に統一**（ADR 0019・ルート遷移＋目次⇄本文を同じ向き/尺 250ms＝進む右→左/戻る左→右）／本文 stretch オーバースクロール無効化（D2・目次/本棚は据え置き）。競合解析＝`docs/reference/06-competitor-reading-motion.md`。**残**: **P1**＝章→章（話送り）は現状どおり瞬間で据え置き中＝要望が出たらスライド化（kakuyomu 400ms が実在目安・新規演出のため症状指差し待ち）／**P2〔切り分け済み・要対策〕**＝**画面遷移（slide push）が毎回軽くカクつく**（2026-07-15 報告）を **2026-07-16 に debug/release 切り分け実機計測済み**（一次情報＝`.claude/plans/reading-transition-jank-measurement-2026-07-16.md`＝再現手順＋全数値）。結果: **debug は jank を誇張するが release でも残る実問題**（本棚⇄本文が最悪＝Janky 9.35%・99%tile 93ms／目次⇄本文 6.53%・61ms＠60Hz・各≈460〜475フレーム）。中央値は両ビルド健全（9〜12ms＝定常は滑らか・遷移の瞬間だけ落ちる）・**UI スレッド律速**（gfxinfo `Slow UI thread` 支配・`Slow bitmap uploads=0`・`Missed Vsync≈0`＝GPU転送でなくメインスレッド composition/measure/layout/draw記録）。コンテンツ温めても残る＝**遷移先の初回コンポジション＋戻りの本棚グリッド〔`ShioriCover` の Canvas 描画〕がアニメ第1フレーム群と同居（仮説①）が有力**・重い読込（仮説②）は主因でない。**2026-07-16 仮説①の狙い撃ち実験を実施し棄却**（`LocalIsTransitioning`＝遷移中フラグで ShioriCover の先端＋題字スキップ＋本文コンポーズのアニメ後送り→同一端末状態のベースライン再計測との gfxinfo 比較で効果なし・目次⇄本文はむしろ悪化〔Slow UI 34→50・Missed Vsync 0→5〕＝負荷は消えず移動しただけ。全数値と判定根拠＝同 plan 追試節・パッチは revert 済み）。**次の一手＝Perfetto escalate**（`runtime-tracing` 計装込み再ビルド。gfxinfo の狙い撃ちでは収束せず＝当初基準どおり。候補の主因＝NavHost 遷移自体のレイヤ記録・目次画面コンポーズ・テキスト measure 等は未切り分け）。
- **[本棚/通知] スナックバーの「閉じる」アクションがタップで効かない疑い**（2026-07-16 実機観察・要確認レベル）: 重複取込スナックバーで「閉じる」タップが無反応に見え、スワイプでは消えた（`f8ffff1` の swipe-to-dismiss は機能）。adb input 注入のタップ精度起因の可能性もあり未確定＝手指での再現確認から。
- **[読書画面] 最上部へ戻るボタンの追加**（2026-07-15 実機フィードバック・新機能）: 本文内で最上部まで一気に移動できるボタンを新設。
- **[読書画面] 縦書き表示モードの実装 — P2完了（描画層＋golden8枚。Robolectric では vert 位置替えが no-op＝実機で句読点の縦字形を要確認）・次はP3（VerticalChapterContent 配線）**（2026-07-16 方針合意／2026-07-17 P0実測・P1・P2完了）: 連続横スクロール（右→左）× 自前Compose組版（公式 text-vertical 成熟までのつなぎ・品質妥協なし）。裁定と根拠＝**ADR 0020**・フェーズ分割＝`.claude/plans/vertical-reading-mode.md`。**P0の全実測＝`.claude/plans/vertical-mode-p0-measurements-2026-07-17.md`が正本**（要点: vertは効くが…；−を回さず‥は書体割れ→CharClassifier表確定／半角縦中横は実蔵書ゼロ＝合成テスト担保・実データの主役は全角！？30k run／列窓サブチャンク化は不要・実データ上限1.5k字は余裕／reverseLayoutは現行(index,offset)と完全同型）。vert実効性の知見＝`docs/knowledge/vert-feature-pgem10-coverage.md`。スパイク計測器は`android/app/src/debug/`に未コミットで留置（P6 OPPO較正で再利用しうる）。触感モック＝`docs/design-candidates/reading-vertical-scroll-D.html`（採用A）/`reading-vertical-paged-D.html`（不採用B・記録）。**同プランP2.5で本棚書影題字の正立崩れ（`ShioriCover.drawShioriTitle`＝「（）」「～」「ー」が回転せず死ぬほど見づらい・2026-07-16 ユーザー報告）も共有文字クラス部品で修正する**。
- **[取込/削除] 本削除時にPDF本体も削除するか確認するダイアログ**（2026-07-15 ユーザー提起）: 現状は前提が無い——取込時のPDFは`cacheDir`への一時コピー→変換→即削除（`DefaultBookRepository.kt:184`付近）で、`BookEntity`スキーマに取込元URIを保持する列が無い（books は取込元 URI を持たない、と既存コメントに明記済み）。変換完了後は`takePersistableUriPermission`も解放される。**単純なダイアログ追加ではなく設計変更が要る**: ①取込元URIをスキーマへ永続化するmigration ②変換後も読み取り+書き込み永続権限を保持し続ける必要（端末上限128件の予算を消費し続ける・現状は用済み次第解放する設計と衝突） ③削除実行時、プロバイダ都合でdeleteが失敗しうる（既に移動/削除済み・権限失効・書き込み非対応プロバイダ）ハンドリングが要る。
- **[PDF変換] 抽出ロジックのハードコード脱却（座標/フォントサイズの絶対値依存を解消）**（2026-07-15 ユーザー提起）: `ParserRules.kt`の判定定数（`FONT_SIZE_BODY_TITLE=14.0`・`FONT_SIZE_RUBY=7.0`・`FONT_SIZE_PAGE=12.0`・`PAGE_NUM_Y=528.98`・`RUBY_OFFSET_X=14.84`・`LINE_STEP_X=22.68`等）が現行のPDF出力形状に絶対値で超ハードコード＝**PDF提供元（なろう側）がほんの少しでも生成側の数値を変更したら一瞬で全滅する**という脆さが本質（「別形状のPDFへの対応」ではなく、同じ形状のまま起きる微小変更への耐性が目的）。`TextProcessor.kt`（本文分類/ルビ結合/空行判定/ページ番号除去）・`PdfExtractor.kt`（表紙著者名判定/最大フォントサイズ判定）が全面依存。**方向性**: 絶対値ではなく相対値/自動検出（本文フォントサイズは最頻値から動的検出・ルビは絶対オフセットでなく親文字サイズ比、ページ番号は絶対Y座標でなくページ下部相対位置、等）へ寄せて微小な数値変更を吸収できる柔軟性を持たせる。**注意**: 変更はゴールデン回帰ハーネス（`ab-review/golden_regression`・N2959KI/N6169DZ等の既知の際どい確定挙動を含む）で検証必須＝所在は memory `golden-regression-baseline`。
- **[意匠・任意] 栞先端 174化に伴う consistency-D / palette-D の tip 同期**（2026-07-13 増補時に留置）: 栞先端意匠を31→174へ増補（正本 `shiori-tips-D.html`＋Kotlin `SHIORI_TIPS`＋書影モック `bookshelf-shiori-grid-D.html`＋`ShioriGeneratorTest` は同期済み）。`bookshelf-shiori-consistency-D.html`・`bookshelf-shiori-palette-D.html` も独自に TIPS(31) を埋め込むが、両者は**色相共有/色域が主眼で tip 描画は付随**のため未同期で留置（デモ表紙が旧31種の先端を引くだけ・色の実証には影響なし）。同期するなら scratchpad の `build_gridD_sync.js` と同手法で両ファイルの `const TIPS=[…]` を174へ差し替え。**増補の手順は `/shiori-tips` スキルが正本**。

---

## ★UX/Design 全層監査 — 残タスク（2026-07-12）

> **これは何か**: `/mnt/c/Users/qingj/Desktop/project/UX`（UX24層＋Design10層＋公理候補）に対し novel-reader 全体を多エージェント監査（45体・敵対的検証済み）した指摘の、**残っている作業だけ**の action list（消化したら行を消す）。
> 消化済み分の一次情報＝`.claude/plans/ux-design-full-audit-2026-07-12.md`（§A 統合報告／§B 全指摘詳細・良い点含む）＋`.claude/plans/ux-audit-batch-execution-20260712.md`（実行記録）・実装＝git log（`ui/polish`）。
> **対象ブランチ**: `ui/polish`（この worktree＝ext4）。ゲート＝`cd android && testDebugUnitTest`（init-script 不要）＋`python3 tools/check_design_tokens.py`。**意匠絡みは Compose で自己判断せず ADR0005/0014＋モック正本に先に接地**。実機絡みは PushNotification→目視OK→コミット。

### 残0: main への統合
- 残1（発見帯 collapse）の完全退避が実機OKで一段落＝`ui/polish` を main へ --no-ff 統合可（コミットは worktree 内セッションから＝guard の機序は memory `ff-merge-sentinel-not-consumed`）。

### 残1: 発見帯 collapse 退避アニメ 体感の追い込み（deferred polish・ADR0005-B 実機後詰め層）
- 本棚発見帯『新しい物語を見つける』の **collapse は「完全退避」で確定・実装・実機OK 済み**（2026-07-14）。裁定の経緯＝案B「スクロール退避」の初回翻訳（下スクロールで帯を1行版へ restyle）を実機却下→**同一要素の restyle をやめ、帯は restyle せず `shrinkVertically`＋fade で高さ0へ畳んで退避・状態フィルタは sticky で常時 top に残す**へ再設計（ユーザー原則「位置/役割が同じなら restyle しない・退避のときだけ消す」）。帯＋フィルタを Lazy 外の固定ヘッダへ hoist（`LazyVerticalGrid` に stickyHeader 無し＝グリッド/リスト両モード一律 sticky の素直な解）・帯の可視は `derivedStateOf`（先頭到達＝先頭書影が最上部付近〔8dp デッドゾーン〕のみ true）で駆動。
- **残るのは退避アニメの体感が『不足』**（2026-07-14 実機・ユーザー所見・「まぁいい」で現状採用）＝終了時スナップ（slideOut の予約スペース一括除去）は shrink 化で解消したが、**閾値トリガの AnimatedVisibility（8dp 超で 150ms 縮小）はスクロールと完全連動しない**ため、退避開始のタイミングに軽い不連続感が残る。**要すればスクロール連動方式へ再設計**＝band 高さ∝スクロールオフセットを nestedScroll で連続縮小する collapsing header 本来型（duration/easing のトークン調整でも一部改善余地）。**発見は第二の柱＝いずれ本節ごと再調整予定**（ユーザー 2026-07-14）。
- 試作/裁定の記録＝`docs/design-candidates/discovery/bookshelf-band-collapse-D.html`（却下1行restyle vs 完全退避の実スクロール対比）・`bookshelf-band-tailtile-D.html`（完全退避 vs 案C末尾タイル）。原型4案＝`bookshelf-band-reposition-D.html`。ADR0005/0014 接地。

### 残3: 人間テスト（第三者便のみ残）
> 本人＋実機で可能な分は 2026-07-14 に消化済み（T1/T5/T6/T7=OK・T2=要改修→★残7⑤。結果＝`.claude/plans/usability-test-results-2026-07-14.md`・派生改修＝★残5〜8）。実機検証6件（旧残2）は 2026-07-16 消化済み＝一次情報 `.claude/plans/device-verify-6items-2026-07-16.md`（5 PASS・TalkBack没入時ナビ不達 FAIL は customActions 方式で同日是正・JVM semantics テストで固定）。残るのは**初見/時間差が本質で本人テスト不成立の2件**＝T3（二読書面の操作言語混乱）・T4（中央タップトグルの再発見。コード事実＝ヒントは通算1回きり・再表示条件なし）＋**実TalkBackの音声走査での是正確認**（uiautomator 静的確認では customActions が見えないため人間便に合流）。実施は第三者ユーザビリティテスト便で（プロトコル＝`.claude/plans/usability-test-protocol-2026-07-12.md`）。

### 残5: 人間テストT6派生＝読書クローム出没の視覚ノイズ2件（2026-07-14 本人実機・要改修）
- **①バー出没時に本文がちらつく**（中央タップ・スワイプ復帰の両経路で発生）: 候補＝バー出没に伴うインセット/パディング変化で本文がリフロー or 再コンポーズの一瞬の空白。**決め打ち修正禁止**（memory `feedback-perf-jank-is-real-signal`）＝まず再現を録画/レイヤ検査で機序特定してから。
- **②没入⇄復帰のたびにステータスバー領域が黒⇄明灰で明滅**（目がチカチカする・ユーザー原文）: 候補＝システムバー hide/show でステータスバー背景が「黒（非表示時の地）⇄明灰（表示時の背景色）」に急変。edge-to-edge で本文をステータスバー背後まで敷き常時同色にする／読書中は常時非表示に固定する等の方式比較から（意匠絡み＝ADR0005/0014 接地の上で）。
- 一次情報＝`.claude/plans/usability-test-results-2026-07-14.md` T6。

### 残6: 作品詳細の固定バー＝既読分岐でPDF取込が降格される一貫性欠如（2026-07-14 ユーザー裁定・要改修）
- **症状**: 未読では「縦書きPDFを取り込む」が藍の主CTA最上段だが、一度「ブラウザで読む」を押す（`lastReadEpisode > 0`）と主CTAが「第N話のはじめから読む」＋「最初から（目次）」に変わり、取込がゴースト第3位へ追いやられる。**UIとしての一貫性がない＝修正対象**（ユーザー裁定 2026-07-14。2026-07-12 の「既読では続きからが主」裁定を上書き）。
- **コード所在**: `NovelDetailScreen.kt:260-299`（既読分岐の主CTA）・`:391-413`（降格された取込ゴースト）。付随バグ的事実＝「最初から（目次）」（`:289`）だけ .btn-ghost トークン未適用の素 OutlinedButton（contentColor=primary藍）＝兄弟のゴーストより明るく見える。
- **進め方**: 意匠絡み＝`/visual-language` ゲート・discovery-detail-D.html（モック正本）の改訂とセットで階層を再設計（取込を既読でも上位に保つ形）。一次情報＝`.claude/plans/usability-test-results-2026-07-14.md` 派生所見。**進捗（2026-07-16）**: 対比モック作成済み（`docs/design-candidates/discovery/discovery-detail-cta-consistency-D.html`＝現状/案A完全一貫〔取込を常に主〕/案B続き主のまま取込第2固定）＝**ユーザー裁定待ち**（`mockview` で開く）。裁定後にモック正本改訂＋Compose 翻訳をセットで。

### 残8: 本棚操作の要望（2026-07-14 実使用フィードバック・新機能）
- **①複数選択→まとめて削除**: 対比モック作成済み（`docs/design-candidates/bookshelf-multiselect-D.html`＝fusion-D 基盤・案A上部文脈バー/案B下端固定バー＋削除確認ダイアログ）＝**ユーザー裁定待ち**（`mockview` で開く）。裁定後に Compose 実装。削除系＝実機検証は捨て本で（memory `device-verify-delegation-no-destructive-on-real-library`）。

### 残4: 監査派生 backlog（新規タスク）
- **蔵書内フィルタ/series 束ね UI**（確認バッチC④＝保留）: ロジック `filterBooksByQuery` は実装済み・UI はモック未表現のため保留（`BookshelfScreen.kt:442`／`ShelfItems.kt:37`）。series 束ねはスキーマ変更要（設計案のみ）。
- **目次の部/編 折り畳み**: 抽出パイプラインに階層データ無し＝**抽出側の新機能**。実PDF→HTML の階層有無は要検証で「フラット確定」＝畳みは前提データ欠如で現状不成立。
- **Macrobenchmark 新設**: measure 要検証（大PDF/10倍蔵書/長時間送りの予算漸進劣化を P90/P99 で assert）＝独立タスク。INTERNET 無しで出荷後テレメトリ不能の代替。
- **lint 残 warnings（任意改善・非ブロック）**: UsableSpace×2（`DefaultBookRepository.kt` の抽出前空き容量チェック）＝`getAllocatableBytes` は消去可能キャッシュ込みの楽観値で事前チェックが甘くなり ENOSPC で変換終盤失敗を招くため、現状の保守的 `usableSpace` は意図的。触るなら API26 分岐・例外処理込みの設計判断が要る（純機械修正ではない）。※ ModifierParameter×3 は Compose 規約準拠で解消済み。

## UI/UX 宿題

- **[モック逆同期・2026-07-16 棚卸し]** 一次情報＝`.claude/plans/mock-drift-inventory-2026-07-16.md`（正本モック全数の未反映リスト・優先順位・恒久ルール）。注記済み＝fusion-D（退避構造未反映⚠️・全面描き直しは★残1の方式確定後）／bookshelf-D（旧世代＝退役・提案基盤に使わない）。**残る逆同期**: ①栞書影ランダム先端/棒長（`f208608`）の fusion-D 反映 ②取込バナー（2行・spring 進捗）は正本モックに**要素自体が未カバー**＝fusion-D へ追加から。**恒久ルール**: コード先行の視覚変更を入れたら正本モックへの逆同期 or「未反映」注記をセットで／モックのプレビューは必ず `mockview`（素の `chrome <file>` 禁止）。
- **[モック追従・構造] 発見系モックの情報/装飾テキスト再分類**（2026-07-12 `a9a6a5c` 実装時に留置）: `InfoText` トークン（実装済み＝発見系の情報メタ6箇所を AA(4.5:1) へ引き上げ・Light #5C606D／Sepia #6C6148／Dark #8A929B）の discovery/*.html モックへの追従は、`--ink-soft` を共有する **10〜16箇所/ファイルの情報・装飾テキストの個別再分類**＋`--info-ink` 変数の新設＋`tools/check_design_tokens.py` へのマッピング追加が必要＝構造的大改修と判定し留置。**現状の一致検査は InfoText を未トラッキングで PASS＝この層ズレ（コードが AA へ引き上げた面をモックが `--ink-soft` のまま持つ）は未検知**になる点に注意。

## なろうAPI 発見・検索機能（第2の柱）

> Phase 0〜4 完了（現況＝`STATUS.md` §0・実装＝git log）。目標ロードマップ・作る機能一覧の一次情報＝plan `~/.claude/plans/api-agy-woolly-swan.md`。監査残課題（構造系）は下の「リファクタ / 技術的負債」。

## リファクタ / 技術的負債（deferred）

- **検索画面 S3＝カテゴリ列の LazyColumn 化（保留・要否判断）**: 重さの正体は「カテゴリ展開状態での操作毎の全画面再コンポーズ」で、S1（選択判定 Set 化・Regex 定数化）/S2（strong skipping＋@Immutable）は解消済み・実機体感は軽快（2026-07-11 実測）。残る理論コスト＝非 Lazy Column 上の22カテゴリ/115チップ（`DiscoverySearchScreen.kt:203-207`）の画面外存在コストと「全展開のまま再訪」の初回構成。**体感問題が再報告されるまで保留が妥当**。
- **R8/リソース収縮が無効**（`android/app/build.gradle` `minifyEnabled false`・`shrinkResources` 無し）: 有効化が**単独最大の軽量化レバー**（APK 24MiB の dead code/未使用リソース分）。Moshi/Room は codegen/KSP で keep は軽微見込みだが PDFBox/Retrofit/OkHttp の keep 確認＋実機回帰（`/device-verify`・収縮起因クラッシュはリリースでしか出ない）必須。
- MigrationTest が「16.json 形状（web_reading_progress 無し）→17」経路を構造的に検証できない（chain テストは 14→15 でテーブルが生まれる系譜のみ通過）。既知の実機 v16→v17 未検証と同根の coverage-hole として記録。

## workflow / tooling

- **[bestpractice 突合の回収候補（2026-07-12 調査）]**: ①スキル frontmatter の `triggers:` は標準外フィールドの可能性（自動発動は `description` 単体が正）＝ハーネスに解釈されているか検証 ②`block_destructive_migration.py` の Bash 経路が素朴な部分文字列一致（`FOO=1 cmd`・`$()` ですり抜け）＝settings permissions の `if` フィールド化を検討（主経路の Edit/Write 捕捉は健在で実害小） ③サブエージェントの部品別モデル配分（fan-out/読み=haiku・照合=sonnet・監査=opus。現状は env `CLAUDE_CODE_SUBAGENT_MODEL` で opus 固定＝見直しは settings 変更を伴う）。

- **antigravity-delegate サブエージェントの同期実行が保証されない**（2026-07-07・委譲5件中3件で再発): agy をバックグラウンド起動したまま「待機中」で終了し完了通知が来ない。プロンプト明記・SendMessage 再開でも再発。運用回避（CLAUDE.md 委譲判断節に反映済み）＝完了判定を報告でなく**成果物の存在**（`git status`/grep/`ps`）で行う。**根治候補**＝プラグイン側で agy 起動を同期実行へ強制するか wrapper にポーリング内蔵。優先度中（運用回避が効き非ブロッキング）。
- **worktree(ext4) 作業の冒頭で `gw :app:lintDebug` を回す運用**: Lint コミットゲート（`check_lint_on_commit.py`）は drvfs でスキップされる設計＝canonical 作業が続く限り事実上無効。ext4 worktree なら in-tree で回るので冒頭で1回スイープする。基準＝0 errors/28 warnings（2026-07-15 時点＝ModifierParameter×3 解消後）。

## 実行捏造検知器（ADR 0006）残タスク

> エンジン＝`.claude/hooks/detect_fabricated_execution_core.py`。完了分は **ADR 0006（増補含む）と git log が正本**。以下は開きのみ。

- **Tier B 汎用主張の免罪の限界**（事象D）: 「セッション内に成功実行が1回でもあれば免罪」で後半の汎用捏造を取りこぼす。具体値主張は具体照合に絞ったが汎用主張の掘り下げは将来課題。**進捗（2026-07-11・増補6）**: Tier E カテゴリ別突合が**現ターン分**の同根系列を吸収した。**過去ターンの汎用主張の掘り下げは引き続き将来課題**（Tier E は現ターン境界での判定）。
- **[2026-07-11・増補6 で切り出し] L44/L55 型「先行実行フレーミング」（完了主張形を持たない幻の先行実行参照）の検知**: 事象N の L44「出力が返っていないので結果を確認します」（存在しない先行 tool_use への参照）・L55「再作成します」（未実行の再作成宣言）は**完了・検証の断言ではなく段取り宣言**＝照合キーが無く Tier E の完了語トリガに当たらない。検知には「主張以前に対応する tool_use が無い先行実行参照」という別系統の突合が要る。要較正・真陽性は台帳N の L44/L55。
- **[2026-07-11・増補6 保留設計] 案3＝委譲主張の E2 突合（opt-in）**: 「〜を委譲した／agy に生成させた」等の委譲完了主張を、**委譲先 transcript（`subagents/agent-<id>.jsonl`）の tool_use とカテゴリ突合**して裏取りする案。**真陽性サンプルが皆無のため保留**（設計要点のみ保全）。真陽性が観測されたら着手。
- **[2026-07-11・増補6 副産物] 370800c1 の台帳レター事象化（人間確認待ち）**: D4 較正の slug 全走査で発見した未登録真陽性＝assistant text 内に `user[Request interrupted for tool use]`＋幻のユーザー発話を自己生成（幻発話テキストの実人間入力不在は transcript 直読で確認済み）。D4（CLI）と A3 変種拡張（live）の両方で active。人間確認が取れたら台帳の追記手順（事後検証モード）でレター事象化。
- **[2026-07-11・増補6 残課題] D5 対象語突合の字面依存FPクラス**: D5 は帰属対象の名詞（違和感/懸念/指摘…）の**字面**を実入力に探すため、ユーザーが真に指摘したが当該名詞を書かなかった場合「あなたの指摘は的を射て」型が潜在FP化しうる（現コーパス実測 0件・非ブロック Tier D で被害限定）。同義語・意味突合の導入は将来課題。
- **[2026-07-11・増補6 で切り出し] Tier E の Stop 昇格の再判断**: 新既定 ABCDE での CLI 運用実績（真陽性の積み上がり・FP 率）が揃ったら再判断。昇格には conf 設計の引き上げ（現 0.55-0.7 → Stop 閾値 0.8）または Stop 側の per-rule 閾値の新設計が必要。
- **意味照合系検知器**（着想段階・スコープ外構想）＝生成コード不具合・外部リサーチ捏造（正解データ事象B/C）。

## D. 長期・品質（backlog）

- **左右スワイプで章遷移**: 旧 `experiment`/`lab-old` は WebView 実装で流用不可。`HorizontalPager`/`pointerInput` で新規。チューニング知見＝軸ロック(`de60869`)/EMA+isDragging(`a07dd3e`)/距離OR速度複合(`4a0719b`)。元コミット `23b5f33`（main 未取り込み）。
- **[抽出] 単話（1話完結）作品の縦書きPDF変換で、本文が「作品情報（プロローグ）」側に乗り章題名も出ない**（2026-07-09 PDF取り込み導線の実機通し検証中にユーザー観測・対象 n2959ki）: 単話作品は章見出し／目次構造が無いため、章分割が本文を作品情報ページの続き扱いで流し込むと推定（**未確定**・要調査）。**やること**: ①n2959ki の抽出結果現物（`novels/<id>/index.html`・`chap_N.html` 構成）で事象を再現確認 ②単話 PDF の構造に対する分割ルール（`ParserRules`/`ChapterProcessor`）の扱いを設計 ③**ゴールデン基準との整合に注意**＝N2959KI はゴールデン本（`ab-review/golden_regression`）であり、基準自体がこの挙動を「正」として固定している可能性がある——修正はゴールデン更新とセットで判断すること。
- **超長編抽出エッジ残差の③アポストロフィ座標順**（N6169DZ・章題ドリフト残2件）: `兎'ｓ`↔`'鳥…` の座標順ずれで**1:1コードポイント置換不可**＝実質 won't-fix。基準＝`ab-review/golden_regression`、詳細＝task_diary #35。

## A2. UIスキン着せ替え（将来送り・保留）

> フェーズ0で D「和モダン・余白」をデフォルト視覚言語に採用済み（設計判断＝`docs/decisions/0005-ui-n-visual-language-D.md`／モック地図は `.claude/plans/archive/UI-n_DESIGN_PLAN-archived-2026-07-02.md` §6.1）。

- **方針確定（2026-06-27・ユーザー指示）＝UIスキン着せ替え（A〜J 選択）はまだ実装しない。main は現状 D のみ。** A〜J は資産として claude.ai/design（プロジェクト `Novel Reader UI`・projectId `bb5a35c8-70ac-4efa-bb03-1579d3f11d93` の `ui-n-phase0/`・`DesignSync: get_file` で再取得可）に保持。栞の他4型（A箔/C小口/D蔵書印/E綴じ紐）もスキン資産として保持（ADR 0005 C 方針）。
- **着手時はここから**: 「UI着せ替え」設定画面のモック化（選択肢=A〜J・既定=D・切替粒度の決定）／A〜J スキンの Compose 実装（スキン×読書テーマの関係・トークン体系）。bookshelf-D へのセピア変種追加もスキン着せ替え実装時に再検討（現状は `SepiaColorScheme` が本棚セピアの正本）。
