# 目次/本文モーション最適化 — 競合解析(06)と自作実装の突合・適用案

- **対象ブランチ**: ui/polish
- **作成**: 2026-07-15（分析完了・適用は未実施）
- **入力**: `docs/reference/06-competitor-reading-motion.md`（競合5アプリ静的解析。以下「06」）
- **背景**: handover「[目次/本文] スクロール・アニメーションの最適化」（症状未言語化）。06 の切り分けチェックリスト（§6）へ自作実装を当てて差分を確定させた。

## 1. 自作実装の実態（コード確認で確定した事実）

| 観点 | 自作の現状 | 出典 |
|---|---|---|
| 依存版 | compose-bom 2025.02.00（foundation/animation-core 1.7.8・material3 1.3.1）・navigation-compose 2.7.5 | `android/app/build.gradle:111,117` |
| 本文描画 | LazyColumn 連続縦スクロール（Pager 不使用） | `ChapterContent.kt:117` |
| 目次描画 | LazyColumn | `NativeTableOfContentsScreen.kt:212` |
| 目次⇄本文・章切替 | 同一 nav ルート内の state swap（`currentFile` 変更で丸ごと差し替え）＝無アニメ即時 | `NativeReadingScreen.kt:344,398` |
| 画面遷移 | NavHost に enterTransition/exitTransition の指定ゼロ＝navigation-compose 既定 | `MainActivity.kt:206` |
| overscroll | 全画面未加工（grep 0件）＝API31+ 端末では stretch バウンドが既定 ON | — |
| 目次の位置ジャンプ | `scrollToItem`（即時・無アニメ） | `NativeTableOfContentsScreen.kt:208` |
| tween/spring | 明示箇所は全数 `theme/Motion.kt` トークン経由（grep 全数照合済み） | `Motion.kt` |
| 没入バー出没 | 自前 settle spring(StiffnessMediumLow)＋fade 250ms | `Motion.kt:29,47`・`NativeReadingScreen.kt:1175` |

## 2. 突合結果

### 確定差分（コード事実として 06 の物差しと食い違う＝違和感の有力候補）

**D1. 画面遷移が nav 既定の 700ms フェードのまま**（06 §4-5 の落とし穴に完全該当）
- NavHost（`MainActivity.kt:206`）に遷移指定が無く、navigation-compose 2.7 系の既定
  `fadeIn/fadeOut(tween(700))` が**全ルート遷移**（本棚→読書〔目次〕・discovery 系・web-reader）に効いている。
- 物差し: 各社の読書系実尺は 100〜250ms・章送りでも 400ms（06 §3-C）。700ms は倍以上＝「もっさり」の第一容疑。
- 副次: Motion.kt 禁止則②「duration/easing を野良既定に委ねない」に NavHost だけが抵触している状態の是正でもある。
- **適用案**: `Motion.kt` へ `MotionDurationNavTransition`（起点 250ms・禁止則①の 350ms 上限内）を新設し、
  NavHost へ enter=fadeIn / exit=fadeOut（tween(トークン)）を明示。**フェードのまま尺だけ締める**＝意匠の新設ではない
  （ADR 0005-B の実機後詰め層の範疇・HTMLモック不要。/visual-language 確認済み 2026-07-15）。

**D2. 読書本文の stretch オーバースクロールが既定 ON**（06 §6-1 の筆頭容疑）
- 自作は overscroll 加工が全画面ゼロ＝本文端で「ゴム伸び」バウンドが出る（実機 PGEM10 は API31+）。
- 物差し: View 各社は**読書面**を `OVER_SCROLL_NEVER` で消す（06 §3-C。読書面で残すのは設定切替つき tscsoft 話送りのみ）。
  **一覧（目次・本棚）は各社とも既定のまま**＝一覧据え置きが業界一致（06 §2/§5）。
- **適用案**: 本文（ChapterContent の LazyColumn）のみ `CompositionLocalProvider(LocalOverscrollConfiguration provides null)`
  で無効化（foundation 1.7 系の API。将来 1.8+ へ上げる際は LocalOverscrollFactory へ読み替え）。目次・本棚は据え置き。

### 一致（触らない——06 が「既定で正しい」と裏書きした箇所）
- 目次の位置ジャンプ `scrollToItem` 即時＝全社「即時・無アニメ」と一致（06 §2）。
- フリング減速物理は View と同一（06 §4-2）＝フリングが主犯の線は薄い。
- Pager 不使用＝§6-3（1フリング1ページ問題）非該当。縦書き未実装＝§6-5 の縦書き Canvas ジャンクも現状非該当。
- ページめくり演出を作り込まない現状＝5社中0の実態と一致（過剰設計回避のエビデンス。06 §3-C）。
- トークン実尺（reveal250/dismiss150/crossfade250/seal220）は各社実尺レンジ 100〜400ms 内。

### 判断保留（症状の指差し待ち・決め打ちしない）
- **P1. 章送り（話送り）の完全即時切替**: 業界作法は「**送りは滑らせ・ジャンプは瞬間**」の二択（06 §3-C）。
  自作は送り（次話への連続導線）も瞬間＝半分ずれている。指差しがここに来たら `currentFile` 切替を
  AnimatedContent 等で演出する選択肢（実在目安: kakuyomu 話送り 400ms）。新規意匠なので症状未言語化のうちは着手しない。
- **P2. カーブでなくフレーム落ち**（06 §6-5/6-6）: 「カクつく」系ならアニメ調整でなく計測
  （memory `feedback-perf-jank-is-real-signal`＝gfxinfo/recomposition）。nestedScroll
  （`NativeReadingScreen.kt:983` nonStealingConnection）×バー連動の取り合いも指差し後に確認。

## 3. 進め方（実行順）
1. **D1 → D2 の順で実装**（各1論理変更=1コミット・`testDebugUnitTest` 通し）。どちらも「Compose 既定 vs 業界実測」の
   確定食い違いの是正であり、症状の指差しを待たず先行適用してよい（06 の5社実測がエビデンス＝決め打ちではない）。
2. 実機答え合わせは **APK 投入前に一度確認を取る**（memory `feedback-ask-before-device-testing`）。
   コード上の定数と実機描画は一致するとは限らない前提で、体感確認とセットで扱う（handover 元記述の原則を踏襲）。
3. D1/D2 適用後も残る違和感は**画面・操作を指差してもらってから** P1（演出追加）か P2（計測）へ振り分ける。
   複数項目を同時にいじらない（06 §6 末尾）。
