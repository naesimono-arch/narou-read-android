# backlog-frozen — 凍結・見送り済みの作業（handover から退避）

> **ここは「やらないと決まっているもの」の置き場**。`handover.md` は「Claude が**今すぐ着手できる**やること」だけを置く
> 台帳なので、①裁定で凍結された ②前提データが欠けていて成立しない ③won't-fix と判断した ④着想段階で
> スコープ外、のいずれかに該当する項目はここへ退避する。**捨てていない**——解禁条件が来たら handover へ戻す。
>
> **開くタイミング**: 各節の「解凍条件」が満たされたとき（＝課金アップデート便の着手時・前提データが揃ったとき）。
> **毎セッションでは開かない**——それが handover と分けている理由そのもの（ADR 0028 の症状3＝台帳のトークン固定費）。
> ここへ移すことが痩身になるのは「開く頻度が違う」からであって、単なる移し替えではない。
> **戻し方**: 解凍条件が満たされた節を handover の該当位置へ戻し、ここからは消す（両方に置かない）。

## スキン磨き込み backlog（M/P/J）

> **解凍条件＝課金アップデート便の着手**（ADR 0027 追記で初回公開までは新規着手しない・既存実装は削除せず温存）。
> 例外＝共通実装の波及・exhaustive when を通す最小翻訳・クラッシュ/データ破損（これらは凍結中でも handover 側で扱う）。
> 意匠の裁定待ち分は `awaiting-human.md` §3-2。
> 検分の一次情報＝`.claude/plans/ui-refine-richness-round-2026-07-18.md`／リッチ化の型＝`.claude/plans/richness-expansion-brief-2026-07-19.md`（R1の型10技法・着手時は全読）。

- **[高負荷モード横展開（ADR 0023）]**: ①星図M v8（月齢/惑星/流星群）・v9（変光星/ジャイロ視差）＝ロードマップと全裁定は
  `.claude/plans/richness-expansion-round-2026-07-19.md` ②知見の和モダンD展開→以降各画面 ③**製品トグルの置き場所・既定値・reduce-motion 優先関係の確定**
  （現状は debug トグルのみ＝ADR 0023 の宿題）。
- **[リッチ化の横展開]**（fresh セッションで実施＝2026-07-19 裁定）: 深空リッチ化は本棚Mのみ→M目次/発見・P質感・J発光層へ「R1級」展開。
- **[取込バナーのスキン残]** M `SkyProcessingBanner`／P `WritingBanner` の Web 0%凍結（`source` フィールド配布済み＝各1行の出し分け）。
  Web 一括の本間でバナーが一瞬畳まれる軽微ちらつきは割り切り済み（気になったら）。
- **[構造穴]** `NativeReadingScreen`/`ReadingSettingsSheet` のスキン差分は「加算的クローム/値選択」で exhaustive when 化の対象外＝
  **新スキン追加時にシート色・クローム欠落が無音で起きる**残存リスク（是正は SkinTokens 化など別機構。ルーター30分岐は when 化済み）。
- **[J のトークン整備]** 発見Jの不足トークン棚卸し（扉固有森リニア #1A2A1F 等・回廊森 rgba(31,52,38,.55)・光条α群＝`DiscoveryPortalJ.kt` 設計コメント参照。
  本棚Jは Amb*Portal パレット化済み＝同じ流儀で。本棚Jの「薬と草の base が近縁」も実機で弱ければ base ストップ追い込み）／
  `settings-J` 不足4値（--sheet #141C15・--sheet-line・扉プレビュー大気3値）／
  **内側半透明白の base val 新設**（目次Jの --soft/--dim/--line は GlyphDarkPortal の RGB 借用＝意味が読みにくい。`SoftTocPortal` 等 or base val 1本で意図明快化・値不変。本棚Jの .resume ≒InkPortal 近似の厳密化も同時に）。
- **[J のその他]** 目次に書籍文脈が届かない（目次画面が書籍ID/題名を受けないため象徴文字glyph を省略中＝出すなら骨格のシグネチャ拡張）／
  象徴文字glyph の semantics（極淡96spの装飾テキストが TalkBack 読み上げ対象になり得る→ノイズなら `clearAndSetSemantics {}`）／
  **時刻大気の発展**（①時刻3相の base/floor 色相トークン化 ②長時間常駐で時間帯を跨いだときの追従＝現状は起動時1回固定・produceState＋5〜10分ポーリングが拡張余地）。
- **[M/P のアニメ・データ]** M昇華アニメ＆P1押印アニメのトリガ配線（同型課題＝「読了の瞬間」イベントが本棚Composableに流入しない。
  栞の `playSealStamp/onSealStamped` ラッチと同型の配線を骨格から通せば一度きり再生可能）／
  P2現像の実カード昇格（ProcessingState から仮カセットカードをラック先頭に＝未生成の本を並べる placeholder 方針の設計判断が先）／
  カセットカードの semantics 整備（読了カセットに「読了・CLEAR」等の contentDescription）／
  **読書時間の計測データ新設**（P本棚LCDの TIME 表示は捏造回避で現在非表示・セッション累積の記録機構が要る）／
  **連続読書日数（streak）の記録新設**（P3『連続プレイの炎』のデータ源。prefs で日付集合を持つ最小実装から）／
  J扉の incipit（BookEntity に synopsis 相当が無く省略。抽出時に第1章冒頭を保存すれば表示可能）。
- **[P の見送り分]** 読書の浮遊puck（モックの没入時浮遊操作は共有 tap-to-reveal に畳んだ＝実機で不便なら独立部品化）／
  設定のLCD値チップ・液晶スウォッチ型テーマ選択（標準部品を優先して未採用＝P密度を上げたければ再検討）／
  **ヒンジのアクセシブル代替**（ドラッグ専用でキーボード/スイッチ操作の段送りが無い。tap-to-cycle か semantics カスタムアクション。
  段の取り分 HingeDetentsDp=56/180/260 の体感も実機微調整可）。
- **[横展開候補]** ①「続きに戻る」チップ（`NativeReadingScreen` 参照ジャンプ）も同構造の半透明ピル＝暗色スキン×明色要素で透けうる（稀な状態のため未対処）
  ②題末区切りダッシュのトリムは J目次のみ＝データ由来なので D/M/P 目次・章扉にも潜在（要すれば共通ユーティリティ化）
  ③**hashCode 直割当の偏り**は J扉パレットのみ fmix32 で是正済み（`docs/knowledge/string-hashcode-low-bit-bias-palette-skew.md`）＝
  同型の M `idColorFor`・P `labelColorFor` も目視で気になったら同適用
  ④M視差の信号源精密化（代表セル高150dp×index の近似＝境界で最大12px段差の理論値。実機で目につけば実測高の累積へ）。
- **[磨き込み候補・グレー所見]**（裁定不要・リッチ化ラウンドの入力）: M発見のカード枠/ジャンルchip境界が星空地で薄い・M本棚の未読/読了chipの淡さ・
  J/P の非選択チップ/タブ文字が4.5:1近傍・J発見「今夜の一気読み」カードのみ青紫（パレット外）・P情報密度/太ベゼル/没入SAVE帯の声量・
  P本棚LCD版の主CTA（上端の小さな赤▶）が弱い・M本棚一覧の天体ドット多色の意味整理。
- **[精査待ち]** ①P章扉のpixel話数を一段強調（モック追補要）④J一覧の栞先端色を扉ambientに連動（一覧是正の設計と合流して精査）。

## UIスキン機構（M/P/J 統合済み＝main／残＝C 夜行・将来送り）

> **解凍条件＝C 夜行はユーザーからのイメージ聴取（`awaiting-human.md` §4）／その他は課金アップデート便**。
> 機構の裁定＝ADR 0021・0022／フェーズ詳細＝`.claude/plans/ui-skin-framework-2026-07-17.md`・`.claude/plans/skin-compose-implementation-2026-07-17.md`／
> 生成規範＝`.claude/plans/skin-design-digest-2026-07-17.md`＋memory `feedback-skin-design-judgment-criteria`。

- **[C 夜行の「らしさ本体」＝構造・演出層]**（別タスク・都度追加）: モックの体感は
  〈本棚=続きからヒーロー＋静かな1列リスト・栞書影の C 用ミュート・ember の効かせどころ（章番号エブロウ/上端ヘアライン進捗/続きからラベル）・
  極小クローム＋読書の浮きピル〉に宿っており**色トークンだけでは D ダークと区別がつかない**（実機で確認済み）。
  **着手はモック作成（発見/目次/設定の C 版含む欠落分）から**＝スキンごと構造切替の枠は外枠に含めず、実装は画面単位で都度。
- **[保留中の候補]** Q 読書の庭＝差し戻し保留（最終版は `skins/candidates/*-Q.html`）／候補 L/N/O/R/S＝本棚1枚のみ（同 candidates/）／
  P はっちゃけ試作の不採用4画面＝`skins/candidates/hatchake/`。旧A〜J原本は claude.ai/design `ui-n-phase0/`。
- **[ツール]** 候補比較＝`tools/build_skin_gallery.py`・画面別ギャラリー（等倍・スキン単体可）＝`tools/build_screen_gallery.py [ID]`。
  プレビューは必ず `mockview`。DesignSync は主セッション限定。任意＝claude.ai 側への収蔵バックアップ同期は未実施。
- **[将来送り（ADR 0021）]** 栞「型」軸（A箔/C小口/D蔵書印/E綴じ紐）／D 以外のテーマ変種／旧候補の移植（I は退役のまま）。
  A〜J 資産は claude.ai/design（プロジェクト `Novel Reader UI`・projectId `bb5a35c8-70ac-4efa-bb03-1579d3f11d93` の `ui-n-phase0/`・`DesignSync: get_file` で再取得可）に保持。
  `bookshelf-D` へのセピア変種追加も再検討枠（現状は `SepiaColorScheme` が本棚セピアの正本）。

## UX/Design 全層監査の残り（2026-07-12）

> **解凍条件＝各項目に個別記載**（いずれも「前提が欠けていて今は成立しない」型）。
> **これは何か**: `/mnt/c/Users/qingj/Desktop/project/UX`（UX24層＋Design10層＋公理候補）に対する全体監査（45体・敵対的検証済み）の残り。
> 消化済み分の一次情報＝`.claude/plans/ux-design-full-audit-2026-07-12.md`（§A 統合報告／§B 全指摘詳細）＋
> `.claude/plans/ux-audit-batch-execution-20260712.md`。ゲート＝`cd android && testDebugUnitTest`＋`python3 tools/check_design_tokens.py`。
> **意匠絡みは Compose で自己判断せず ADR0005/0014＋モック正本に先に接地**。

- **蔵書内フィルタ/series 束ね UI**: ロジック `filterBooksByQuery` は実装済み・**UI はモック未表現のため保留**（`BookshelfScreen.kt:442`／`ShelfItems.kt:37`）。
  series 束ねはスキーマ変更要（設計案のみ）。**解凍条件＝モックを起こすと決めたとき**。
- **目次の部/編 折り畳み**: 抽出パイプラインに階層データ無し＝**抽出側の新機能**。実PDF→HTML は「フラット確定」＝畳みは前提データ欠如で現状不成立。
  **解凍条件＝抽出側が階層を持つようになったとき**。
- **lint 残 warnings（任意改善・非ブロック）**: UsableSpace×2（`DefaultBookRepository.kt` の抽出前空き容量チェック）＝
  `getAllocatableBytes` は消去可能キャッシュ込みの楽観値で事前チェックが甘くなり ENOSPC で変換終盤失敗を招くため、**現状の保守的 `usableSpace` は意図的**。
  触るなら API26 分岐・例外処理込みの設計判断が要る（純機械修正ではない）。

## 長期・品質

- **超長編抽出エッジ残差の③アポストロフィ座標順**（N6169DZ・章題ドリフト残2件）: `兎'ｓ`↔`'鳥…` の座標順ずれで**1:1コードポイント置換不可**＝**実質 won't-fix**。
  基準＝`ab-review/golden_regression`、詳細＝`task_diary.md` #35。

## 実行捏造検知器（ADR 0006）の将来課題

> **解凍条件＝各項目に個別記載**（多くは「真陽性サンプルが貯まったら」）。
> エンジン＝`.claude/hooks/detect_fabricated_execution_core.py`。完了分は **ADR 0006（増補含む）と git log が正本**。以下は開きのみ。

- **Tier B 汎用主張の免罪の限界**（事象D）: 「セッション内に成功実行が1回でもあれば免罪」で後半の汎用捏造を取りこぼす。
  Tier E カテゴリ別突合が**現ターン分**の同根系列を吸収したが、**過去ターンの汎用主張の掘り下げは将来課題**。
- **[保留設計] 案3＝委譲主張の E2 突合（opt-in）**: 「〜を委譲した／agy に生成させた」等の委譲完了主張を、
  委譲先 transcript（`subagents/agent-<id>.jsonl`）の tool_use とカテゴリ突合して裏取りする案。**真陽性サンプルが皆無のため保留**（設計要点のみ保全）。
- **D5 対象語突合の字面依存FPクラス**: D5 は帰属対象の名詞（違和感/懸念/指摘…）の**字面**を実入力に探すため、
  ユーザーが真に指摘したが当該名詞を書かなかった場合「あなたの指摘は的を射て」型が潜在FP化しうる（現コーパス実測 0件・非ブロック Tier D で被害限定）。同義語・意味突合は将来課題。
- **Tier E の Stop 昇格の再判断**: 新既定 ABCDE での CLI 運用実績（真陽性の積み上がり・FP 率）が揃ったら再判断。
  昇格には conf 設計の引き上げ（現 0.55-0.7 → Stop 閾値 0.8）または Stop 側の per-rule 閾値の新設計が必要。
- **意味照合系検知器**（着想段階・スコープ外構想）＝生成コード不具合・外部リサーチ捏造（正解データ事象B/C）。

## 見送り・保留（第2次退避・2026-07-31）

> 第1次（上の各節）が「節まるごと凍結」だったのに対し、こちらは **handover の各節に散っていた単発の見送り**。
> いずれも「やらないと決めた」のではなく「**今は着手しない**」型なので、解凍条件を項目ごとに併記する。

- **[リファクタ大バッチ 2026-07-27 の残り]**（裁定と依存グラフ＝`.claude/plans/refactor-batch-2026-07-27.md`）:
  ③ Baseline Profile 生成＝**見送り裁定**（profileinstaller/macrobenchmark/StartupBudget は揃っており generator 1本で起動20〜30%改善見込み・要実機）
  ④ 計測・調査群＝**見送り裁定**: ShioriCover の Path 毎フレーム確保（drawWithCache 候補・`BookshelfScrollBenchmark` が予算内なら実害なし＝先に測る）／
  OkHttp ディスクキャッシュ未設定／Room AutoMigration 不使用の方針 ADR 1行／Native 接頭辞・ビュー切替名の整理。
  **解凍条件＝起動性能を課題として再度取り上げるとき**（③はそのとき単独で効く）。
- **[検索画面 S3＝カテゴリ列の LazyColumn 化]**: 重さの正体は「カテゴリ展開状態での操作毎の全画面再コンポーズ」で、
  S1/S2 は解消済み・実機体感は軽快（2026-07-11 実測）。残る理論コスト＝非 Lazy Column 上の22カテゴリ/115チップ
  （`DiscoverySearchScreen.kt:203-207`）の画面外存在コストと「全展開のまま再訪」の初回構成。
  **解凍条件＝体感問題が再報告されたとき**（それまで保留が妥当と判定済み）。
- **[発見系モックの情報/装飾テキスト再分類]**: `InfoText` トークン（実装済み＝発見系の情報メタ6箇所を AA(4.5:1) へ・Light #5C606D／Sepia #6C6148／Dark #8A929B）の
  `discovery/*.html` への追従は、`--ink-soft` を共有する**10〜16箇所/ファイルの個別再分類**＋`--info-ink` 変数の新設＋`tools/check_design_tokens.py` への
  マッピング追加が必要＝**構造的大改修と判定して留置**。
  ⚠️ **現状の一致検査は InfoText を未トラッキングで PASS＝この層ズレは未検知**（無防備であること自体は `docs/known-bugs-registry.md` の管轄）。
  **解凍条件＝発見系モックをまとめて描き直す便が立つとき**。
- **[モーション P1・ボタン送りのスライド化]**: 章→章はスワイプ経由のみスライド化済み（引っ張りプレビュー）で、
  **ボタン（前章/次章）経由は瞬間のまま据え置き**。ADR 0019・競合解析＝`docs/reference/06-competitor-reading-motion.md`・
  全数値＝`.claude/plans/reading-transition-jank-measurement-2026-07-16.md`。
  ⚠️ 着手するなら**章送りの重フレーム1枚（48〜113ms・`draw/record` 59.6ms）を先に軽くしないとアニメが必ず引っかかる**。
  **解凍条件＝ユーザーから要望が出たとき**。
- **[本棚の長押し haptic]** 触覚フィードバックの追加＝**後回しでOK とユーザー明言**（2026-07-30）。**解凍条件＝ユーザーが再度求めたとき**。
- **[非Kスキンの気分]** 現状 CLASSIC 固定。ページャ化・日替わりの各スキン適用は別ラウンド（J の扉 glyph が P1 前提）。
  **解凍条件＝スキン系の解禁便（ADR 0027）**。
- **[スキン候補・生存2案の正式スキン化]** 2026-07-25 モデルA/B生成実験の生存2案＝製図室（青写真）`skins/candidates/bookshelf-seizushitsu.html`・
  カプセル売場（ガチャ）`skins/candidates/bookshelf-capsule.html`（12案中この2つのみユーザー合格）。
  起案手順の知見＝`docs/knowledge/skin-concept-first-mock-second.md`（コンセプト行で数打ち→当たりだけモック化）。
  **解凍条件＝スキン起案ラウンドを再開するとき（ADR 0027 の解禁便）**。
- **[スキン着想・キャラクター系]** アニメ等のキャラクターをもとにしたスキンモック（2026-07-17 ユーザー着想）。
  着手時の論点＝**実在IPの意匠・名称は権利面の検討が要る**（特定作品の直写でなく「キャラクター的な世界観の翻案」に留めるか、の裁定から）。
  **解凍条件＝同上**。
- **[agy 解除時に再燃する宿題] antigravity-delegate サブエージェントの同期実行が保証されない**（委譲5件中3件で再発＝バックグラウンド起動のまま完了通知が来ない）。
  運用回避＝完了判定を報告でなく**成果物の存在**（`git status`/grep/`ps`）で行う。**根治候補**＝プラグイン側で agy 起動を同期実行へ強制するか wrapper にポーリング内蔵。
  **解凍条件＝agy の使用禁止が解除され、かつプラグインを再有効化するとき**（2026-07-26 にプラグインごと無効化＝当面発生しない。禁止の経緯＝memory `feedback-avoid-agy-low-trust`）。

## agy 委譲（2026-08-13 再凍結・ユーザー裁定）

> 2026-08-09 に「三層委譲」で復活着手したが、**今回は配線を作るところまでで打ち切り**＝プラグインも無効化した
> （`~/.claude/settings.json` の `enabledPlugins` で `antigravity@antigravity-for-claude-code: false`）。
> 設計・実測・却下理由の正本は **ADR 0031**（再凍結の裁定は同 ADR の決定5）。ここは**解凍時に拾う成果物の所在**だけを持つ。

- **[成果物①・plugin-level ゲート配線]** fork の `fix/plugin-level-gate-wiring` に**コミット済み**。
  `hooks/hooks.json` へ PreToolUse(Bash) を置き、`validate-delegate-bash.sh` が `agent_type` を見て自分のサブエージェントのときだけ enforce する
  （**スコープ判定は fail-open・コマンド検査は fail-closed** の非対称＝python3 が無いと誰の Bash か判別できず主セッションを誤ブロックする害の方が大きいため）。
  テスト PASS 181→192・残る FAIL 2件は master 由来。**セッション再起動後のライブ検証で発火を確認済み**（陽性＝委譲サブの `echo` が exit 2 で停止／陰性＝同ターンの主セッション Bash は通る）。
  ⚠️ master へ入れるか上流へ PR するかは**未裁定**（`awaiting-human.md` 案件）。
- **[成果物②・break-even ゲート]** `scripts/agy-break-even.py` とサーキットブレーカーは自作ブランチ `feat/delegation-break-even-gate` に残置。
  **cherry-pick は成立しない**（2026-08-11 に実測＝独自3コミットとも `hooks/validate-delegate-bash.sh` で競合し、master 側のその中身が command-injection 修正そのもの）。
  残る道は「master 版を土台に手で足す」の一方向のみ。
- **[成果物③・ブリーフ検査ハーネス]** `tools/agy-probe/`（ブリーフ4水準＋変異採点 `score.py`）。
  ⚠️ 再利用時は**変異をベースラインを測ってから設計し直す**こと（既存テストが巻き添えで殺す変異が多く、L0 検査は識別力が 7変異中2つしか無かった）。
- **[凍結しても残る知見]** ブリーフに要るのは分量でなく〈対象／枠／完了条件／配置規約〉の4点＝約600字
  （正本＝`docs/knowledge/agy-brief-needs-frame-and-done-not-volume.md`）。症状の正確な記述＝**枠内では最大化するが、枠と完了条件を検算しない**。
- ⚠️ **再有効化するときの必須手順**: Claude Code はプラグイン提供サブエージェントの frontmatter `hooks` を**黙ってドロップする**
  （memory `plugin-subagent-hooks-ignored`）。**配線が生きていることをライブ検証してから**書き込み・実行を伴う委譲を許すこと——
  2026-08-11 の実測では「唯一の制限」と呼ばれる防御が1つも発火しておらず、任意コマンドが通る状態だった。
- **[解凍時に CLAUDE.md へ戻す運用規律]**（本便で「委譲は三層」の行から撤去した分＝ここが唯一の控え）:
  ①**フォアグラウンド同期で呼ぶ**（バックグラウンドはサブの駐機事故と合成して自走復旧が不能になる）
  ②**plan モード中は書き込み厳禁・read-only digest のみ**（機序＝`task_diary.md` #40）
  ③呼び出しは **`--dangerously-skip-permissions`**（`--yolo` は agy CLI に**無く** wrapper 側のフラグ）＝`-p` 単独は
  `toolPermission=request-review` で書き込みが通らない（read-only なら `-p` だけでよい）
  ④既定モデルは **Gemini 3.6 Flash (High)**・Bash 側の表示が2分で切れても**タスクは走り続ける**（結果はファイルで拾う）
  ⑤認証が切れたら**独立した WSL ターミナルで `agy -p` を起動しコードを貼る**（Claude Code 内は stdin が対話的でなく不可）。トークンは `~/.gemini/antigravity-cli/`
  ⑥**agy の権限設定を全域拡張へ戻さない**（`settings.json` は `trustedWorkspaces` だけの素の状態へ復元済み・当時の版は `settings.json.probe-era`）。
- **解凍条件＝①ユーザーが再度 agy 活用を判断したとき、または②コスト構造が「生成が支配的」へ変わったとき**
  （現状は `cache_read` が金額換算の約85%で、**生成だけを移しても総計の約3%**にしかならない＝ADR 0031 判断材料6）。
  上の「[agy 解除時に再燃する宿題]（同期実行が保証されない）」も同時に解凍する。
