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
- **[目次/本文] モーション最適化 — 残りは P1＋P2体感確認のみ**（2026-07-15）: 確定差分は適用・**遷移は実機確認済みで slide push に統一**（ADR 0019・ルート遷移＋目次⇄本文を同じ向き/尺 250ms＝進む右→左/戻る左→右）／本文 stretch オーバースクロール無効化（D2・目次/本棚は据え置き）。競合解析＝`docs/reference/06-competitor-reading-motion.md`。**残**: **P1**＝章→章（話送り）は**スワイプ経由はスライド化済み**（2026-07-16 引っ張りプレビュー実装＝ドラッグ追従＋確定スライド）・**ボタン（前章/次章）経由のみ瞬間のまま据え置き**＝要望が出たらスライド化／**P2＝実装済み・体感確認待ち**（2026-07-16 スケルトン差替えで実装＝遷移中は本棚グリッドを BookshelfSkeleton へ差替え。framestats で pop アニメ中 6〜15ms に浄化・最悪 104.8→71ms・差戻しヒッチ無しを実測。**残＝①ユーザー体感確認〔本棚へ戻る際スケルトンが一瞬見える見え方の可否含む〕②残るなら次の的は目次画面の初回コンポーズ 93/81ms〔P2対象外の副次〕を別タスク化**。経緯・全数値＝`.claude/plans/reading-transition-jank-measurement-2026-07-16.md` 末尾「修正実装と再計測」節）。
- **[本棚/通知] スナックバー「閉じる」疑い＝白・残るは複数重複時の連続再表示UXの要否判断**（2026-07-16 実機切り分け済み）: 配線健全・bounds 中心の精密タップで確実に効く＝真のバグではない。無反応に見えた正体＝adb タップ精度＋**複数重複の一括投入時に `Channel.BUFFERED` 直列消費で同型スナックバー（Indefinite）が閉じた直後に即再表示される複合**（実機で確定再現。スワイプ dismiss も正常）。改善するなら重複メッセージの集約（「N件は取り込み済み」）or duration 有限化＝要否はユーザー判断。一次情報＝`.claude/plans/device-verify-followup-2026-07-16.md`。
- **[読書画面] 縦書き表示モードの実装**（2026-07-15 実機フィードバック・新機能）: 縦書きで読めるモードをまだ実装していない（現状は横書きのみ＝`717045e`で確定した実装事実）。新規実装として要設計（意匠絡み＝`/visual-language`ゲート）。
- **[取込/削除] 本削除時にPDF本体も削除するか確認するダイアログ**（2026-07-15 ユーザー提起）: 現状は前提が無い——取込時のPDFは`cacheDir`への一時コピー→変換→即削除（`DefaultBookRepository.kt:184`付近）で、`BookEntity`スキーマに取込元URIを保持する列が無い（books は取込元 URI を持たない、と既存コメントに明記済み）。変換完了後は`takePersistableUriPermission`も解放される。**単純なダイアログ追加ではなく設計変更が要る**: ①取込元URIをスキーマへ永続化するmigration ②変換後も読み取り+書き込み永続権限を保持し続ける必要（端末上限128件の予算を消費し続ける・現状は用済み次第解放する設計と衝突） ③削除実行時、プロバイダ都合でdeleteが失敗しうる（既に移動/削除済み・権限失効・書き込み非対応プロバイダ）ハンドリングが要る。

---

## ★UX/Design 全層監査 — 残タスク（2026-07-12）

> **これは何か**: `/mnt/c/Users/qingj/Desktop/project/UX`（UX24層＋Design10層＋公理候補）に対し novel-reader 全体を多エージェント監査（45体・敵対的検証済み）した指摘の、**残っている作業だけ**の action list（消化したら行を消す）。
> 消化済み分の一次情報＝`.claude/plans/ux-design-full-audit-2026-07-12.md`（§A 統合報告／§B 全指摘詳細・良い点含む）＋`.claude/plans/ux-audit-batch-execution-20260712.md`（実行記録）・実装＝git log（`ui/polish`）。
> 監査バッチの実装ブランチ（ui/polish）は main 統合・worktree 撤去済み＝残りは deferred/backlog のみで専用ブランチ無し。ゲート＝`cd android && testDebugUnitTest`＋`python3 tools/check_design_tokens.py`。**意匠絡みは Compose で自己判断せず ADR0005/0014＋モック正本に先に接地**。実機絡みは PushNotification→目視OK→コミット。

### 残1: 発見帯 collapse 退避アニメ 体感の追い込み（deferred polish・ADR0005-B 実機後詰め層）
- 本棚発見帯『新しい物語を見つける』の **collapse は「完全退避」で確定・実装・実機OK 済み**（2026-07-14）。裁定の経緯＝案B「スクロール退避」の初回翻訳（下スクロールで帯を1行版へ restyle）を実機却下→**同一要素の restyle をやめ、帯は restyle せず `shrinkVertically`＋fade で高さ0へ畳んで退避・状態フィルタは sticky で常時 top に残す**へ再設計（ユーザー原則「位置/役割が同じなら restyle しない・退避のときだけ消す」）。帯＋フィルタを Lazy 外の固定ヘッダへ hoist（`LazyVerticalGrid` に stickyHeader 無し＝グリッド/リスト両モード一律 sticky の素直な解）・帯の可視は `derivedStateOf`（先頭到達＝先頭書影が最上部付近〔8dp デッドゾーン〕のみ true）で駆動。
- **残るのは退避アニメの体感が『不足』**（2026-07-14 実機・ユーザー所見・「まぁいい」で現状採用）＝終了時スナップ（slideOut の予約スペース一括除去）は shrink 化で解消したが、**閾値トリガの AnimatedVisibility（8dp 超で 150ms 縮小）はスクロールと完全連動しない**ため、退避開始のタイミングに軽い不連続感が残る。**要すればスクロール連動方式へ再設計**＝band 高さ∝スクロールオフセットを nestedScroll で連続縮小する collapsing header 本来型（duration/easing のトークン調整でも一部改善余地）。**発見は第二の柱＝いずれ本節ごと再調整予定**（ユーザー 2026-07-14）。
- 試作/裁定の記録＝`docs/design-candidates/discovery/bookshelf-band-collapse-D.html`（却下1行restyle vs 完全退避の実スクロール対比）・`bookshelf-band-tailtile-D.html`（完全退避 vs 案C末尾タイル）。原型4案＝`bookshelf-band-reposition-D.html`。ADR0005/0014 接地。

### 残3: 人間テスト（第三者便のみ残）
> 本人＋実機で可能な分は 2026-07-14 に消化済み（T1/T5/T6/T7=OK・T2=要改修→★残7⑤。結果＝`.claude/plans/usability-test-results-2026-07-14.md`・派生改修＝★残5〜8）。実機検証6件（旧残2）は 2026-07-16 消化済み＝一次情報 `.claude/plans/device-verify-6items-2026-07-16.md`（5 PASS・TalkBack没入時ナビ不達 FAIL は customActions 方式で同日是正・JVM semantics テストで固定）。残るのは**初見/時間差が本質で本人テスト不成立の2件**＝T3（二読書面の操作言語混乱）・T4（中央タップトグルの再発見。コード事実＝ヒントは通算1回きり・再表示条件なし）＋**実TalkBackの音声走査での是正確認**（uiautomator 静的確認では customActions が見えないため人間便に合流）。実施は第三者ユーザビリティテスト便で（プロトコル＝`.claude/plans/usability-test-protocol-2026-07-12.md`）。

### 残4: 監査派生 backlog（新規タスク）
- **蔵書内フィルタ/series 束ね UI**（確認バッチC④＝保留）: ロジック `filterBooksByQuery` は実装済み・UI はモック未表現のため保留（`BookshelfScreen.kt:442`／`ShelfItems.kt:37`）。series 束ねはスキーマ変更要（設計案のみ）。
- **目次の部/編 折り畳み**: 抽出パイプラインに階層データ無し＝**抽出側の新機能**。実PDF→HTML の階層有無は要検証で「フラット確定」＝畳みは前提データ欠如で現状不成立。
- **Macrobenchmark 残フェーズ**（基盤＋コールド起動計測＋起動予算 assert〔median≤350ms/max≤500ms・`-e enableBudgetAssert true` ゲート〕＋実行スクリプト `tools/run_macrobenchmark.sh`〔SIGQUIT 除細動ループ同梱〕まで完了・**PASS/FAIL 両経路を実機実証済み**。実測・設計の一次情報＝`.claude/plans/macrobenchmark-kickoff-2026-07-17.md`＋`docs/knowledge/coloros-uiautomation-shell-pipe-eof-hang.md`）: ②10倍蔵書シーダー（src/benchmark ソースセット）＋本棚スクロール jank ③長時間章送り jank ④大PDF取込（TraceSectionMetric）。
- **lint 残 warnings（任意改善・非ブロック）**: UsableSpace×2（`DefaultBookRepository.kt` の抽出前空き容量チェック）＝`getAllocatableBytes` は消去可能キャッシュ込みの楽観値で事前チェックが甘くなり ENOSPC で変換終盤失敗を招くため、現状の保守的 `usableSpace` は意図的。触るなら API26 分岐・例外処理込みの設計判断が要る（純機械修正ではない）。※ ModifierParameter×3 は Compose 規約準拠で解消済み。

## UI/UX 宿題

- **[モック逆同期・2026-07-16 棚卸し]** 一次情報＝`.claude/plans/mock-drift-inventory-2026-07-16.md`（正本モック全数の未反映リスト・優先順位・恒久ルール）。注記済み＝fusion-D（発見帯の完全退避構造のみ未反映⚠️・全面描き直しは★残1の方式確定後。栞ランダム先端/棒長 `f208608`・取込バナー `ProcessingBanner` は 2026-07-16 に fusion-D／multiselect-D へ逆同期済み）／bookshelf-D（旧世代＝退役・提案基盤に使わない）。**恒久ルール**: コード先行の視覚変更を入れたら正本モックへの逆同期 or「未反映」注記をセットで／モックのプレビューは必ず `mockview`（素の `chrome <file>` 禁止）。
- **[モック追従・構造] 発見系モックの情報/装飾テキスト再分類**（2026-07-12 `a9a6a5c` 実装時に留置）: `InfoText` トークン（実装済み＝発見系の情報メタ6箇所を AA(4.5:1) へ引き上げ・Light #5C606D／Sepia #6C6148／Dark #8A929B）の discovery/*.html モックへの追従は、`--ink-soft` を共有する **10〜16箇所/ファイルの情報・装飾テキストの個別再分類**＋`--info-ink` 変数の新設＋`tools/check_design_tokens.py` へのマッピング追加が必要＝構造的大改修と判定し留置。**現状の一致検査は InfoText を未トラッキングで PASS＝この層ズレ（コードが AA へ引き上げた面をモックが `--ink-soft` のまま持つ）は未検知**になる点に注意。

## なろうAPI 発見・検索機能（第2の柱）

> Phase 0〜4 完了（現況＝`STATUS.md` §0・実装＝git log）。目標ロードマップ・作る機能一覧の一次情報＝plan `~/.claude/plans/api-agy-woolly-swan.md`。監査残課題（構造系）は下の「リファクタ / 技術的負債」。

## リファクタ / 技術的負債（deferred）

- **検索画面 S3＝カテゴリ列の LazyColumn 化（保留・要否判断）**: 重さの正体は「カテゴリ展開状態での操作毎の全画面再コンポーズ」で、S1（選択判定 Set 化・Regex 定数化）/S2（strong skipping＋@Immutable）は解消済み・実機体感は軽快（2026-07-11 実測）。残る理論コスト＝非 Lazy Column 上の22カテゴリ/115チップ（`DiscoverySearchScreen.kt:203-207`）の画面外存在コストと「全展開のまま再訪」の初回構成。**体感問題が再報告されるまで保留が妥当**。
- **R8/リソース収縮が無効**（`android/app/build.gradle` `minifyEnabled false`・`shrinkResources` 無し）: 有効化が**単独最大の軽量化レバー**（APK 24MiB の dead code/未使用リソース分）。Moshi/Room は codegen/KSP で keep は軽微見込みだが PDFBox/Retrofit/OkHttp の keep 確認＋実機回帰（`/device-verify`・収縮起因クラッシュはリリースでしか出ない）必須。
- MigrationTest が「16.json 形状（web_reading_progress 無し）→17」経路を構造的に検証できない（chain テストは 14→15 でテーブルが生まれる系譜のみ通過）。既知の実機 v16→v17 未検証と同根の coverage-hole として記録。

## workflow / tooling

- **[bestpractice 突合の回収候補（2026-07-12 調査）]**: ①`block_destructive_migration.py` の Bash 経路が素朴な部分文字列一致（`FOO=1 cmd`・`$()` ですり抜け）＝settings permissions の `if` フィールド化を検討（主経路の Edit/Write 捕捉は健在で実害小） ②サブエージェントの部品別モデル配分（fan-out/読み=haiku・照合=sonnet・監査=opus。現状は env `CLAUDE_CODE_SUBAGENT_MODEL` で opus 固定＝見直しは settings 変更を伴う）。※旧①`triggers:` は 2026-07-16 検証済み＝標準外と公式ドキュメントで確定し、全8スキルを公式 `when_to_use` へ移行済み。

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

- **超長編抽出エッジ残差の③アポストロフィ座標順**（N6169DZ・章題ドリフト残2件）: `兎'ｓ`↔`'鳥…` の座標順ずれで**1:1コードポイント置換不可**＝実質 won't-fix。基準＝`ab-review/golden_regression`、詳細＝task_diary #35。

## A2. UIスキン着せ替え（将来送り・保留）

> フェーズ0で D「和モダン・余白」をデフォルト視覚言語に採用済み（設計判断＝`docs/decisions/0005-ui-n-visual-language-D.md`／モック地図は `.claude/plans/archive/UI-n_DESIGN_PLAN-archived-2026-07-02.md` §6.1）。

- **方針確定（2026-06-27・ユーザー指示）＝UIスキン着せ替え（A〜J 選択）はまだ実装しない。main は現状 D のみ。** A〜J は資産として claude.ai/design（プロジェクト `Novel Reader UI`・projectId `bb5a35c8-70ac-4efa-bb03-1579d3f11d93` の `ui-n-phase0/`・`DesignSync: get_file` で再取得可）に保持。栞の他4型（A箔/C小口/D蔵書印/E綴じ紐）もスキン資産として保持（ADR 0005 C 方針）。
- **着手時はここから**: 「UI着せ替え」設定画面のモック化（選択肢=A〜J・既定=D・切替粒度の決定）／A〜J スキンの Compose 実装（スキン×読書テーマの関係・トークン体系）。bookshelf-D へのセピア変種追加もスキン着せ替え実装時に再検討（現状は `SepiaColorScheme` が本棚セピアの正本）。
