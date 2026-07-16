# 画面遷移 jank 実機計測 — debug/release 切り分け（2026-07-16）

> **対象ブランチ**: `ui/polish`（この worktree＝ext4）
> handover P2「画面遷移（slide push）が毎回軽くカクつく」の**まず debug/release で切り分け**を実施した一次情報。
> 結論の要約は handover ★残タスク P2 に反映。ここは再現手順＋全数値。

## 目的

「毎回軽くカクつく」（2026-07-15 実使用フィードバック）が **debug 特有のオーバーヘッド**（Compose の composition デバッグ計装。現 debug は R8 未適用だが release も `minifyEnabled false`＝両者の差は実質 `debuggable` のみ）か、**release でも残る実問題**かを切り分ける。

## 計測環境

- 実機 OPPO PGEM10 / ColorOS・`192.168.1.210:5555`
- **ディスプレイ実測 60Hz**（1440x3168 modeId4・`presDeadline 16.67ms`）＝jank 判定境界は 16.67ms。※端末は 90/120Hz 対応だがこの時点は 60Hz 稼働。
- 計測ツール: `adb shell dumpsys gfxinfo com.novelreader`（reset→操作ループ→dump）
- 条件統一: 各計測前にウォームアップ（in-memory キャッシュを温める）・同一操作ループ・各 ≈460〜475 フレーム。

### 操作ループ（input 注入・座標は 1440x3168 実寸）
- **目次⇄本文**（8往復=16遷移）: 目次で章タップ `input tap 450 530`→本文、`keyevent BACK`→目次。各 sleep 1.3s。
- **本棚⇄本文**（6サイクル=18遷移）: 本棚で1冊目タップ `input tap 380 1340`→本文、BACK→目次、BACK→本棚。
- 対象書籍＝単話作品「中身無男」（目次1エントリ＝作品情報・プロローグ）。Back は現行の固定2階層 collapse で 本文→目次→本棚。

### release を蔵書DB無傷で計測した手順（再利用可）
1. `:app:assembleRelease`（未署名 APK 産出。release に signingConfig 無し）。
2. debug キーで署名: `apksigner sign --ks ~/.android/debug.keystore --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android`。
3. `adb install -r`（**署名一致で上書き＝DB保持**。不一致でも失敗するだけで uninstall しない＝DB消失は明示 uninstall のみ）。
4. `pkgFlags` から `DEBUGGABLE` が消えて release 稼働を確認。
5. 計測後は現 HEAD の `assembleDebug` を `install -r` で戻す（release=debug署名なので上書き可）。**端末は debug 復帰済み・蔵書保持を目視確認済み**。

## 結果

| 遷移 | ビルド | Total | **Janky%** | 50th | 90th | 95th | **99th** | Slow UI thread | Slow draw cmds | Bitmap uploads |
|---|---|---|---|---|---|---|---|---|---|---|
| 目次⇄本文 | debug | 466 | 9.01% | 12 | 21 | 53 | 101 | 41 | 17 | 0 |
| 目次⇄本文 | release | 475 | **6.53%** | 9 | 16 | 38 | **61** | 30 | 11 | 0 |
| 本棚⇄本文 | debug | 464 | 11.21% | 12 | 25 | 46 | 150 | 50 | 18 | 0 |
| 本棚⇄本文 | release | 460 | **9.35%** | 11 | 18 | 36 | **93** | 41 | 12 | 0 |

（ms・percentile はフレーム時間）

## 結論

1. **debug は jank を誇張している（handover 仮説は一部正しい）**: release で tail が大きく改善（本棚⇄本文 99%tile 150→93ms・目次⇄本文 101→61ms）。出荷版の体感は debug で見るより滑らか。
2. **だが release でも残る＝実問題**: 本棚⇄本文で Janky 9.35%・最悪 93ms（≒6フレーム落ち）。「debug 特有」では片付かない。ユーザー報告は本物。
3. **本棚⇄本文が最悪**（ユーザーの勘が的中）。**中央値は両ビルドとも健全（9〜12ms）＝定常スクロールは滑らか・遷移の瞬間だけ落ちる**。
4. **律速は UI スレッド**: janky ≈ `Slow UI thread`・`Slow bitmap uploads=0`・`Missed Vsync≈0`＝GPU/テクスチャ転送でなく**メインスレッドの composition/measure/layout/draw-command 記録**が遷移フレームで重い。コンテンツを温めても残る＝重い読込（仮説②）は主因でなく、**遷移先の初回コンポジション＋戻りの本棚グリッド〔LazyVerticalGrid＋栞書影 `ShioriCover` の Canvas 描画〕がアニメ第1フレーム群と同居（仮説①）が有力**。

## 次の一手（推奨）

**狙い撃ち実験 → 同一 gfxinfo ループで delta 実測**（決め打ち修正でなく実測駆動）:
- 遷移先の初回フレーム負荷をアニメ後へ回す（本棚グリッドの `ShioriCover` Canvas 描画をアニメ中は簡略/遅延・読書画面コンテンツをアニメ完了後ロード 等）。
- 改善すれば仮説①確定＋修正が手に入る。収束しなければ Perfetto trace（`runtime-tracing` 計装込み再ビルド）へ escalate。
- **Perfetto は現段階では限界的価値が低い**: gfxinfo で UI スレッド律速まで特定済み・生 trace は Compose 計装なしだと Composable 名が出ず解析も重い。狙い撃ち実験が収束しない場合のみ。

---

## 追試: 仮説①狙い撃ち実験（2026-07-16 同日・結果＝棄却）

**実験パッチ（A+B）**: `compositionLocalOf` の `LocalIsTransitioning` を新設し、NavHost 各 composable（MainActivity の bookshelf/reading）と目次⇄本文 AnimatedContent の `transition.isRunning` を provide。A＝遷移中は ShioriCover の先端意匠＋縦組み題字の描画をスキップ（紙地・罫・棒のみ）、B＝遷移中は ChapterScreen を非コンポーズ（既存の chapterRestore==null ゲートに OR）。release ビルド＋debug 鍵署名（4ファイル・49+/7-）。

**条件**: 直前の D2 実機確認の副作用で対象書籍に読了「了」バッジが付き本棚描画コストが変わったため、前回値とは比較せず**同一端末状態でベースライン（パッチなし release）を再計測**してから比較。60Hz 同条件（mActiveModeId=4）・同一操作ループ。

| 遷移 | ビルド | Total | Janky% | 50th | 90th | 95th | 99th | Slow UI thread | Missed Vsync |
|---|---|---|---|---|---|---|---|---|---|
| 目次⇄本文 | baseline | 468 | 7.69% | 11 | 16 | 36 | 57 | 34 | 0 |
| 目次⇄本文 | exp(A+B) | 581 | 9.12% | 11 | 23 | 38 | 57 | 50 | 5 |
| 本棚⇄本文 | baseline | 460 | 7.83% | 10 | 18 | 27 | 77 | 33 | 0 |
| 本棚⇄本文 | exp(A+B) | 555 | 6.31% | 10 | 20 | 36 | 81 | 34 | 2 |

**判定＝仮説①棄却**:
1. 目次⇄本文は明確に悪化（Slow UI 34→50・Missed Vsync 0→5・90th 16→23ms）＝実験Bのゲート解除がアニメ直後の一括再コンポーズを生み、負荷は消えず移動しただけ＋余計なフレームを追加。
2. 本棚⇄本文の率改善は Total 増（+95〜113 フレーム＝isRunning 反転起因の追加描画）による希釈が疑わしく、テールは悪化（95th 27→36ms・99th 77→81ms）。
3. ベースラインの回間ブレ（前回 release: toc 6.53%/shelf 9.35% ↔ 今回 7.69%/7.83%＝±1.5%程度）に対し exp delta は同オーダー＝「効果なし」が最も堅い読み。
4. 副作用の目視は異常なし（遷移後に本文・書影とも正常描画へ復帰＝パッチ自体は仕様どおり動作した上での効果なし）。

**含意**: 遷移フレームの Slow UI thread は「ShioriCover の Canvas 描画」「本文の初回コンポーズ」の除去では減らない＝主因は別のメインスレッド負荷（NavHost 遷移自体のレイヤ記録・目次画面のコンポーズ・テキスト measure 等は未切り分け）。gfxinfo での狙い撃ちはここで収束せず→**Perfetto（androidx `runtime-tracing` 計装込み再ビルド）へ escalate**（当初基準どおり）。

実験パッチは revert 済み（gfx dump 4本は scratchpad・セッション終了で揮発。数値は上表が正本）。端末は計測終了時点で実験 release APK のまま＝次の APK 投入で上書きされる。

---

## Perfetto escalate（2026-07-16 同日・主因確定）

**計装**: debug 依存に `androidx.compose.runtime:runtime-tracing`（compose-bom 2025.02.00 管理＝1.7.8）＋`androidx.tracing:tracing-perfetto-binary:1.0.0`（runtime-tracing 単独では不足＝`libtracing_perfetto.so` の実体はこちら。ENABLE_TRACING ブロードキャストで有効化）を一時追加→採取後 revert 済み（未コミット・作業樹クリーン確認済み）。

**採取**（60Hz 同条件＝mActiveModeId=4・presDeadline 16.67ms を実測突合）:
- `run1c`＝本棚⇄目次⇄本文 6周（本棚グリッド復帰込み・主データ）／`run1`＝本棚⇄本文の裏取り／`run2c`＝目次⇄本文のみ（本棚を介さない切り分け差分）。
- 手順上の罠: system BACK は非決定的に二段 pop してアプリ終了する→画面内「目次に戻る／本棚に戻る」ボタンを使う決定的ドライバに切替。
- config: 64MB RING_BUFFER・20s・track_event＋atrace(view/gfx等・atrace_apps com.novelreader)＋surfaceflinger.frametimeline。解析は uv `perfetto` trace_processor（jank フレームに time-overlap するメインスレッドスライスの名前別合計）。

**結果（frametimeline deadline-miss の支配スライス実名・ms）**:
- 最悪フレーム（run1c・104.79ms）の UI スレッド内訳: `doFrame` 90ms → `traversal` 72ms のうち **`measure` 51ms → `AndroidOwner:onMeasure` 51ms（単独最大・フレームの約半分）**、内側に `BookshelfContent (BookshelfScreen.kt:733)`。ほか `draw-VRI/Record View#draw` 14ms・`Recomposer:recompose` 18ms（`NavHost.kt:195` 14.6ms・`AnimatedContent.kt:713` 11.4ms）。
- 条件差分: 本棚を介す run1c は `AndroidOwner:onMeasure` 合計 **1406ms**／目次⇄本文のみの run2c は **253ms**・最悪 jank 104.8→66.8ms。`ShioriCover` は全体 3.3ms（max 0.36ms）・run2c では 0。

**判定**:
- **主因＝本棚 LazyVerticalGrid の measure/layout が遷移中に走ること（確定）**。本棚⇄本文が最悪という gfxinfo/体感と一致。
- **ShioriCover Canvas は主因でない（確定＝仮説①棄却の裏付け）**。狙い撃ち実験が効かなかったのは、切ったレバーが「描画/コンポーズ」で、真の重さが「grid の measure」だったため。
- 副次: 目次⇄本文単独でも 67ms 級の jank が残る＝テキスト measure（`ChapterContent.kt:77`）・View#draw 記録・遷移アニメ毎フレームの recompose（NavHost/AnimatedContent）の複合。NavHost レイヤ記録は寄与するが多数派でない（最悪フレームで 14.6ms）。

**修正方向の候補（未実装・要方式選定）**:
1. **遷移中は本棚グリッドの measure/layout を凍結し、アニメ完了後に実グリッドへ差替え**（最有力＝支配コスト直撃）。trade-off: 差替え時のポップ対策にプレースホルダの同寸担保が要る。
2. グリッド項目の測定コスト削減（skippable 化・固定サイズ付与で intrinsic 測定回避・ネスト削減）。trade-off: 部分的削減に留まる見込み。
3. 遷移を graphicsLayer 平行移動主体にし遷移中の re-measure を排す。trade-off: カスタム遷移/描画スナップショットが要り工数大（最も根治的）。

**端末状態**: 計装 debug APK が実機に残存（蔵書 DB 無傷・後で通常版を install -r すれば戻る）。トレース 3本と解析スクリプトは scratchpad（セッション終了で揮発。数値は本節が正本）。

## 修正実装と再計測（2026-07-16 同日・案1で実装）

**実装＝案1（スケルトン差替え）**: 本棚 destination の enter アニメ中だけ `LazyVerticalGrid`/`LazyColumn` を既存 `BookshelfSkeleton` へ差替え（`MainActivity.kt` composable("bookshelf") で `transition.currentState/targetState` の離散比較→`BookshelfScreen`→`BookshelfContent(deferHeavyContent)` へ素通し）。案(a) GraphicsLayer 静止画は enter（戻り）で録画対象が dispose 済みのため原理的に不成立、案(b) サブツリー measure 凍結は Compose に API が無く不成立＝(c) 差替えが唯一 enter/exit 両立（設計比較は Plan エージェント検討・本節が要旨の正本）。

**検証**:
- 発動確認（一時ログ・revert 済み）: popEnter で defer=true→約300ms 後 false＝設計どおり遷移窓のみ差替え。
- framestats（pop＝目次→本棚の1遷移）: **アニメ中フレームは 6〜15ms に浄化**・最悪は遷移冒頭の 71ms 1枚（旧 Perfetto 最悪 104.8ms〔うち grid measure 51ms〕→約30ms 短縮＝残りは dispose/NavHost レイヤ等の非grid成分と整合）。**アニメ後の実グリッド差戻しに 17ms 超のヒッチ無し**（LazyGrid 単独 measure はアニメ併走時より軽く済む）。
- 6周 gfxinfo 集計（本棚⇄目次⇄本文・メニュータップ込みの同一ドライバ）: 修正前 Janky 7.96%/99th 93ms → 修正後 8.28%/105ms＝**誤差内**。理由＝①重い measure は「窓外へ移送」する設計で総量は不変（gfxinfo は窓内外を区別しない）②残る支配ジャンクは**目次画面の初回コンポーズ（93/81ms・P2 対象外の既知の副次）**で集計を握る。
- 判定: **本棚 pop はもう最悪経路ではない**（71ms＜目次の93ms）。体感の最終判定はユーザー目視（スケルトン一瞬表示の見え方含む）。残るなら次の的は目次初回コンポーズ＝別タスク。
