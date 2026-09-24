# 他社APK逆解析から抜いた「遷移スケルトン」の規範 — 裁定材料（2026-08-26）

> ⚠️ **2026-08-26 の精査で「CR 帯」は無効と判明した**（`book-api-analysis/_work/review/08-audit-2026-08-26.md`）。
> §3.4 由来の〈ライト 1.27〜1.44／ダーク 1.59〜2.09〉は **X＝骨↔背景・Photos＝base↔highlight・カクヨム＝lerp両端**と
> **測っている量が違う値の混成**で、比較に使えない。統一した測り方だと X は **1.116/1.275/1.210** で、
> 我々の現在値（LIGHT 1.093・1.243／DARK 1.087・1.336）と大差ない＝**「帯の下＝薄すぎる」という §7-1 の結論は取り下げ**。
> 骨の濃さを決めるなら**同じ定義で測り直す**こと。他の項目（寸法・幅の相違・lineHeight 一致・先例ゼロの判定）は精査で追認済み。

- 立場: **材料の整理のみ。採否は監督とユーザーが決める**（本書はどれを採るべきかを書かない）。
- 出所リポジトリ: `/mnt/c/Users/naesimono/Desktop/project/book-api-analysis`（novel-reader リポジトリ**外**）。
  以下、`08` = `08-startup-profile-and-ui-constants.md`、`F/<pkg>` = `_work/findings/<pkg>.md`。
  さらにその中の `ファイル:行` は**逆解析対象APKの中の位置**（jadx 出力 or apktool 展開 res）。
- 対象13本: 競合5（tscsoft / zyunto / sampleb3 / なろう公式 / カクヨム）＋模範8（X・Google製7）。

## 0. 表記の規約（推測と実測を混ぜない）

| 印 | 意味 |
|---|---|
| **【実測】** | 逆解析の生データ（dex の定数・res の XML・JSON ヘッダ）に**その数値が literal で出ている**。`ファイル:行` を必ず添える |
| **【解読】** | 生データから**機械的に一意に導ける**もの（難読名⇄実体の対応を `toString()` で確定した上での読み替え、WCAG 式での比の計算、マスク bit からの既定/上書き判定） |
| **【推測】** | そう読めるが確証がない（意図の推定・分類の当てはめ）。**数値そのものを推測で作った箇所は無い** |
| **先例なし** | 13本を探して**存在しないことが確認できた**、または**この dex/res セットからは取れなかった**（両者を区別して書く） |

⚠️ 相手は難読化されている。カクヨム 96% / X 55% / Google 系 95〜97% が難読化済み（`08` §1.4）。
本書の値は**難読名からの復元を経ている**ものが多く、その復元根拠は各行の出所欄に残した。

---

## 1. 骨を出すまでの遅延閾値／最低表示時間

### 1-1. 実在する先例は Gmail 1本だけ

| 値 | 出所 | 印 |
|---|---|---|
| リスト: **表示遅延 750ms** (`threadlistview_show_loading_delay_ms`) | Gmail `values/integers.xml:170`（`F/google-apps-skeleton` §0-4） | 【実測】 |
| リスト: **最低表示 500ms** (`threadlistview_min_show_loading_ms`) | 同 `values/integers.xml:169` | 【実測】 |
| 詳細: **表示遅延 500ms** (`conversationview_show_loading_delay_ms`) | 同 `values/integers.xml:25` | 【実測】 |
| 詳細: **最低表示 200ms** (`conversationview_min_show_loading_ms`) | 同 `values/integers.xml:24` | 【実測】 |
| （別経路）native SAPI 版の表示遅延 **500ms** | 同 `values/integers.xml:171` | 【実測】 |

**二段構え（delay + min_show）を持つのは 13本中 Gmail のみ**（`08` §3.2）。

### 1-2. 残り12本

| アプリ | 値 | 出所 | 印 |
|---|---|---|---|
| X | **遅延なし**。`com/x/ui/common/shimmer/` 配下に `delay(` / `postDelayed(` が0件 | `F/com.twitter.android` §A-4 | 【実測（不在の確認）】 |
| カクヨム | **遅延なし**。`bt7.Y0()`(=onAttach) で即起動・`infiniteRepeatable` の `initialStartOffset` 既定0（`wk.d0(spec,4)` の mask bit2）・状態判定 `hw8.c()` にも遅延なし | `defpackage/bt7.java:1-31`, `wk.java:554-556`, `hw8.java:47-49`, `ye0.java:59-63`（`F/jp.kadokawa.el.kakuyomu` §A-1） | 【解読】 |
| tscsoft | **0ms（即時）**。`ProgressVisibilityManager.showProgressView()` が同期で `setVisibility` | `utils/ProgressVisibilityManager.java:128-146` | 【実測】 |
| zyunto | **0ms（即時）**。アプリコードに `postDelayed`/`Handler` が0件 | `defpackage/fn0.java:31-36`, `as0.java:44-48` | 【実測（不在の確認）】 |
| sampleb3 | ダイアログのみ **1000ms**／**100ms**（`WaitDialog(activity, delayMs)` → `postDelayed`、間に合えば出さない）。**本命の一覧オーバーレイは遅延なし** | `dialog/WaitDialog.java:12-34`／`ui/EpisodeListActivity.java:657`(1000ms)・`:1651`(100ms)／`ui/NovelListActivity.java:1696`(即時) | 【実測】 |
| なろう公式 | 該当実装なし（骨自体が無い・Flutter のスピナー） | `F/com.syosetu.android` §A-0/§A-5 | 【実測（不在の確認）】 |
| YouTube / YT Music / Maps / Photos | **見つからなかった**（`integers.xml`・`bools.xml` に該当0件） | `F/google-apps-skeleton` §0-4 | 先例なし（res 限定・dex 未捜索） |

### 1-3. 誤読しやすい近傍の値（骨の遅延ではない）

X の `com/x/urt/i.java:705-710` に **500ms / 750ms** の Duration 定数があるが、これは
**タイムライン UI 状態のデバウンス**（`j.java:45-47` = 500ms 後に UI 状態を落とす／`p.java:49-51` = 750ms 後に保留中の新着を反映）で、
骨の表示遅延ではない（`F/com.twitter.android` §A-4 が明示的に「誤読しないこと」と注記）。【実測】

### 1-4. こちらの構造との前提差（借用可否の判定に要る事実）

我々の骨は**不定長のロード待ち**ではなく、**尺の決まった遷移窓 250ms**（`MotionDurationNavTransition`＝`ui/theme/Motion.kt:62`）に
出て `settle+0ms` で差し替わる（横モック `tl` 節・`NativeReadingScreen.kt:537-543`）。
→ **Gmail の 750ms 遅延を字義どおり適用すると、骨は一度も表示されない**（窓 250ms < 遅延 750ms）。
**最低表示 500ms** も同様に、適用すれば骨が窓を 250ms 超過して残る＝差し替え点が動く。
これは原則との衝突ではなく**前提の不一致**（我々のは「待ち時間の可視化」ではなく「遷移中の場所取り」）。【解読】

---

## 2. shimmer / pulse の定数

### 2-1. 周期・easing・進行方向・帯

| アプリ | 周期 | easing | 進行方向・形状 | 既定/上書き | 出所 | 印 |
|---|---|---|---|---|---|---|
| **X（Compose, compose-shimmer）** | **sweep 800ms + repeatDelay 1500ms ＝ 1周期 2300ms** | **LinearEasing**（`l0.d` = 恒等） | 傾き **15°**・帯幅 **400dp**・colorStops **[0, 0.5, 1]**・blendMode **DstIn(6)**・`RepeatMode.Restart` | **タイミングは既定のまま**（mask 55 = animationSpec 既定・shaderColors だけ上書き） | `com/valentinilk/shimmer/m.java:22-26`、キーフレーム `j.java:10-19`、mask 判定 `k.java:38-55` | 【実測＋解読】 |
| X（同・唯一の上書き） | **1600ms・repeatDelay 0** | LinearEasing / Restart | 休止なしで連続 | **上書き**（mask 62） | `com/x/spaces/ui/room/q5.java:3697` | 【実測】 |
| X（自前 ShaderBrush・ShareSheet） | **1200ms・休止なし** | LinearEasing | 横方向 LinearGradient・`-width → +width` ＝**幅の2倍**を掃引・6行ループ | 自前実装 | `com/x/ui/common/shimmer/v.java:114`, `:47-53`, `:87` | 【実測】 |
| **カクヨム** | **片道 800ms / 往復 1600ms**（`infiniteRepeatable(tween(800), Reverse)`） | **LinearEasing**（`ia2.c`→`dt1.java:147-150` の `f(x)=x`） | **掃引ではない**。単色矩形の **2色 lerp フェード**（`Brush.linearGradient` はアプリ本体から0件） | 自前実装（`tween` 既定300を800で上書き） | `defpackage/a67.java:164-179`, `wk.java:104-106`, `tz6.java:12-17`, `bt7.java:1-31` | 【解読】 |
| **Photos** | **3500ms**（FB Shimmer 既定 1000 の 3.5倍） | 未指定＝既定 | tilt / direction / repeat_delay / start_delay とも**未指定＝既定**、`dropoff=0.8`（既定 0.5）、`colored=true`、`base_alpha=1.0`／`highlight_alpha=1.0` | **上書き** | `values/styles.xml:3954-3962`（style `FocusModeLoadingShimmer`。参照する全6レイアウトが style 経由・個別 `app:shimmer_*` は0件） | 【実測】 |
| Maps | **1000ms**（＝既定） | 既定 | 既定 | **同じライブラリを積んで一切無調整**（`style` 属性なし・`app:shimmer_*` なし） | `layout/alternateprofile_main_fragment.xml:9-14` | 【実測（不在の確認）】 |
| zyunto | Lottie ループ **約708.3ms**（`fr=24, ip=0, op=17`） | Lottie 内部ベジェ（アプリ側上書きなし） | 本が開くモーション（骨ではない） | — | `RES/raw/open_book.json` ヘッダ実測 | 【実測】 |
| YouTube / YT Music | **res に定数が無い** | — | — | — | `F/google-apps-skeleton` §A-2/§A-3 | 先例なし（res 限定・dex 未捜索） |
| tscsoft / sampleb3 / なろう公式 | **shimmer もパルスも無い** | — | — | — | `F/*` §A-2 | 【実測（不在の確認）】 |

**注意**: 「shimmer の周期は 1000ms 前後」という一般論は成立しない。
compose-shimmer 系（800+1500=2300）と Facebook shimmer 系（1000+400）は**別物**（`08` §3.3）。
FB Shimmer の既定 `repeat_delay=400ms` は本解析の基準値として置かれたもので、
compose-shimmer 側は dex 上の実数値 **1500ms** が正（`F/com.twitter.android` §A-0 が明記）。【解読】

### 2-2. 色差（骨色と地色のΔ・shimmer の振れ幅）

**骨バー ↔ 背景**（＝静止時に骨がどれだけ見えるか）

| アプリ / テーマ | 骨 | 背景 | CR | 出所 | 印 |
|---|---|---|---|---:|---|---|
| X STANDARD（ライト） | `#CFD9DE` | `#FFFFFF` | **1.435:1** | `08` §3.4（View 側 `?abstractColorLightGray`＝`res/values/styles.xml` と完全一致で裏取り） | 【解読】 |
| X DIM | `#3D5466` | `#15202B` | **2.088:1** | 同 | 【解読】 |
| X LIGHTS OUT | `#2F3336` | `#030303` | **1.618:1** | 同 | 【解読】 |
| カクヨム ライト | `#F2F1F0`(surfaceContainer) → `#D8D7D6`(surfaceContainerHighest) | — | **1.274:1** | `defpackage/rk8.java:35-54`、ロール対応は `r21.java:120-172` の `toString()` から確定 | 【解読】 |
| カクヨム ダーク | `#484B4D` → `#65696B` | — | **1.585:1** | `rk8.java:12-34` | 【解読】 |
| カクヨム セピア | ≒`#EEEDE3` → `#D8D7D6` | — | （明度差はライトの約2倍 ≒39/255） | `rk8.java:55-57`（`compositeOver` の計算値） | 【解読】 |
| Photos ライト | `#E1E3E1`(neutral_variant90) | `#FFFFFF`(`?colorSurface`) | **1.290:1** | `values/colors.xml:845→170→334→212`／`styles.xml:487` | 【解読】 |
| Photos ダーク | `#444746`(neutral_variant30) | `#131314` | **1.977:1**（**ハイライトの方が暗い＝暗い帯が走る反転**） | `values-night/colors.xml:25`／`styles.xml:1101` | 【解読】 |
| Maps | `?colorSurfaceVariant` → Photos と同じ GM3 トークンに着地 | — | 1.290 / 1.977 : 1 | `F/google-apps-skeleton` §A-5 | 【解読】 |
| YouTube ライト | `#1A000000` over `#FFFFFF` = `#E5E5E5` | `#FFFFFF` | **1.260:1** | `drawable/ghost_card_block_light.xml`, `values/colors.xml:1433` | 【解読】 |
| YouTube ダーク | `#33FFFFFF` over `#0F0F0F` = `#3F3F3F` | `#0F0F0F` | **1.820:1** | `ghost_card_block_dark.xml`, `colors.xml:1505` | 【解読】 |
| YouTube live chat ダーク | base↔middle | — | **1.007:1（ほぼ差ゼロ）** | `colors.xml:1050-1052`・**`values-night` に上書き無し** | 【解読】 |
| YT Music（ghost・既定ダーク基調） | `#26000000` over `#0F0F0F` | `#0F0F0F` | **1.014:1（ほぼ不可視）** | `colors.xml:994`・**`values-night` 全11行に上書き無し** | 【解読】 |

**収束帯**（`08` §3.4 の読み。3社が独立に同じ帯に着地）:
**ライト CR 1.27〜1.44 : 1 / ダーク CR 1.59〜2.09 : 1**。【解読】
⚠️ **この帯は 2026-08-26 の精査で無効**（測る量の混成＝冒頭の警告を見る）。
（YouTube live chat と YT Music の 1.01 は night 上書き漏れの取り残しで、帯の外＝**巨大アプリでも骨のダーク対応は落ちる**実例。実機未確認。【推測】）

**shimmer の振れ幅（同一バーの最小↔最大）**

| 系統 | 振れ幅 | 出所 | 印 |
|---|---|---|---|
| compose-shimmer 既定 | `White@0.25 → @1.0 → @0.25` | `com/valentinilk/shimmer/m.java:26` | 【実測】 |
| **X の上書き（全15箇所同型）** | **`tertiary@0.7 → @1.0 → @0.7`**（mask 55） | `com/x/ui/common/shimmer/m.java:30-31` ほか（`c.java:36`, `f.java:52`, `j.java:40/:309`, `l.java:36`, `p.java:43`, `s.java:43`, `a0.java:286/:363`, `d0.java:198/:269`, `com/x/communities/impl/detail/z.java:151` 等） | 【実測】 |
| X の例外1件 | `White@0.2 / 0.7 / 0.2` | `com/x/ui/common/shimmer/x.java:33-35` | 【実測】 |
| X の α0.7↔1.0 の実効 CR | ライト **1.116:1** / DIM **1.275:1** / LIGHTS OUT **1.210:1** | `08` §3.4 | 【解読】 |

→ **X はライブラリ既定より振れ幅を 1/4 以下に絞っている**（DstIn 合成なので α だけが効く＝色相・明度は不変）。【解読】

### 2-3. 角丸

| アプリ | 角丸 | 出所 | 印 |
|---|---|---|---|
| **カクヨム** | **2dp**（全箇所 `RoundedCornerShape(2.dp)`。骨を包む外側カードのみ 4dp） | `defpackage/d57.java:13-16` + 呼び出し `l71.java:185/:194`, `k71.java:35/:44`／外側 `ye0.java:32` | 【解読】 |
| YT Music | **2dp** | `values/dimens.xml:549` `ghost_card_corner_radius` | 【実測】 |
| Gmail | **4dp** | `values/dimens.xml:3682` `smart_draft_shimmer_corner_radius` | 【実測】 |
| **X（Compose）** | テキストバー **4dp** / メディア **8〜16dp** / アバター **`RoundedCornerShape(50%)`** | `com/x/ui/common/shimmer/m.java:90-94` ほか（8dp: `p.java:294`, `x.java:36-37`／12dp: `l.java:246`, `a0.java:145`／16dp: `d0.java:102`／50%: `m.java:86`, `j.java:211/:221` 等） | 【実測】 |
| X（View） | **6dp** | `drawable/placeholder_shimmer_rectangle.xml` の `<corners android:radius="6.0dp"/>` | 【実測】 |
| YouTube | 旧 **2dp** → 現行(Amsterdam) **6dp**（ブロック）/ **8dp**（サムネ）/ **15〜16dp**（ボタン・チップ） | `drawable/ghost_card_block_{dark,light}.xml`／`amsterdam_ghost_card_block.xml`／`values/dimens.xml:1087-1088` | 【実測】 |
| Photos | カード **24dp** / テキスト骨 **8dp** | `values/dimens.xml:1464`, `:1468` | 【実測】 |
| Maps | **0dp**（shape drawable 不使用・背景色を直指定） | `layout/alternateprofile_main_fragment.xml:11-12` | 【実測】 |

→ **2dp（控えめ）と 6〜8dp（現代的）に二分**し、YouTube だけが 2dp → 6/8dp へ移行済み（`08` §3.5）。【解読】

### 2-4. 骨の高さと行間

| アプリ | 行高 | 行間 | 段落間 | 出所 | 印 |
|---|---|---|---|---|---|
| **Gmail**（8本バー） | **18dp** | **12dp** | **32dp** | `values/dimens.xml:3682-3695`（全体高 268dp と検算一致: 8×18 + 12×5 + 32×2 = 268） | 【実測】 |
| **カクヨム** | **置換するテキストの `lineHeight`**（`lineHeight` 未指定なら `fontSize`、両方未指定なら **16dp** フォールバック） | テキスト2行間 **4dp** | Row 内要素間・Column 縦間隔とも **16dp** | `defpackage/bd1.java:561`（`bd1.f`）／レイアウト `w00.java:1243`, `:1264-1281` | 【解読】 |
| X（View `timeline_placeholder.xml`） | 名前バー **20dp** / 本文行 **14dp** | 行間 **12dp** | 上 32dp・水平 12dp・行内 7dp | `08` §3.6 | 【実測】 |
| X（Compose） | 見出しバー **16〜20dp** / 本文行 **14dp** | 縦間隔 `Arrangement.spacedBy(8)` ほか 4/6/16 | — | `com/x/ui/common/shimmer/m.java:34/:91/:94/:152/:154`, `p.java:185-194`, `x.java:39/:71/:99/:120` | 【実測】 |
| YT Music | **9dp**（`ghost_card_text_height`） | 4dp / 8dp（small / large） | — | `values/dimens.xml:548-557` | 【実測】 |
| Maps | **12dp** | **12dp** | — | `layout/alternateprofile_main_fragment.xml:11-13` | 【実測】 |
| Photos（partneraccount 系） | **18sp / 14sp**（※単位が **sp**＝フォントスケール追従） | 4〜8sp | — | `layout/photos_partneraccount_promo_share_back_loading.xml` | 【実測】 |

**カクヨムの「行骨の高さ＝置換するテキストの lineHeight」**は、骨→実データでレイアウトが跳ねないことを構造的に保証する手口（`08` §3.6・§5.1-6）。【解読】

---

## 3. 骨が何を描くか＝粒度（アプリごと）

⚠️ **こちらの4段（位置だけ／面を埋める／段落の呼吸まで／紙面まるごと）への当てはめは【推測】**（4段はこちらの語彙で、向こうはそう分類していない）。当てはめの根拠となる形状の事実は【実測】。

| アプリ | 骨の実体（【実測】） | こちらの4段への当てはめ（【推測】） |
|---|---|---|
| **Maps** | 幅 match_parent × 高 12dp のバーを **2本**だけ。角丸なし。1画面に1箇所 | **位置だけ**（面は埋めない・表情なし） |
| **YT Music** | `ghost_card`＝サムネ 48×48 + 86×9 + 186×9 の3要素を、`music_hidden_queue_info.xml` で **5回 include**（上下端は fade drawable で消し込み） | **面を埋める**（同一カードの反復・表情なし） |
| **YouTube** | `home_browse_ghostcard.xml` ＝ chip 骨 88×32 ×4 ＋ `ghost_card_block` を **3回 include**。1枚は 16:9 サムネ + アバター 32 + 題16dp×2 + 副題 120×16dp | **面を埋める**（同一カードの反復。題2本のうち1本だけ 120dp 固定＝ごく弱い表情） |
| **カクヨム** | `Row(spacedBy 16){ 20×20 骨, Column(weight 1, spacedBy 4){ 全幅行骨 ×2 }, 幅122dp 骨 }` を `Column(spacedBy 16)` に **1アイテムぶんだけ**。骨が出るのは**1画面1コンポーネントのみ**（一覧全体は `CircularProgressIndicator`） | **位置だけ**（面は埋めない）。ただし**行高＝lineHeight の一致**という点では「紙面まるごと」寄りの厳密さ |
| **Gmail** | 8本バーが幅 **121/313/210/237/179/210/146/263dp** と**すべて異なる**。行高18・行間12・段落間32 で「3行＋段落＋2行＋段落＋3行」を構成 | **段落の呼吸まで**（幅のばらつきと段落間隔で「文章」に見せる） |
| **X（Compose）** | 本文行を `fillMaxWidth(0.85〜0.95)`、**最終行だけ 0.55〜0.7**。区切り線 0.5dp。行数は 4行 / 6行ループ。アバター・メディア・アクション骨も置く | **段落の呼吸まで**（最終行を短くする＝文末の自然さ） |
| **X（View `timeline_placeholder.xml`）** | ツイート3行分を固定配置（アバター・名前120×20・本文200×14 / 150×14・メディア全幅×200・アクション 20×15 ×4） | **面を埋める〜段落の呼吸まで**の中間（幅は違うが固定値2種） |
| **Photos** | **実ウィジェット（MaterialCardView / MaterialButton / TextView）を tint して骨にする**。テキスト寸法は `android:visibility="invisible"` の TextView に**本物の文字列を入れて幅を確保** | **紙面まるごと**（骨と実データでレイアウトが構造的に一致） |
| tscsoft / zyunto / sampleb3 / なろう公式 | **骨なし**（不定 ProgressBar / Lottie 1個 / 全画面スクリム+ProgressBar / Flutter スピナー） | 該当なし |

**zyunto に骨が無い理由の構造的説明**: `item_novel_list.xml` は**全文テキストで画像 View がゼロ**（表紙サムネのスロットが構造上存在しない）＝骨で埋める対象が無い（`F/com.zyunto.naroreader` §A-5・`08` §3.6-(C)）。【解読】

---

## 4. 骨→実内容の切り替え方

**該当する明示的な実装は 13本から1件も取れなかった。**

| アプリ | 分かったこと | 出所 | 印 |
|---|---|---|---|
| カクヨム | 状態 `hw8.c()`（= isLoading && data==null）の分岐で `w00.q(骨)` / `w00.n(実データ)` を**そのまま出し分け**。フェード・`AnimatedContent`・`Crossfade` の介在は記述なし＝**差し替え** | `defpackage/ye0.java:59-70` | 【実測】 |
| tscsoft | ローディング面⇄本体面を **`android.R.anim.fade_in` / `fade_out`（OS 既定リソース）**でクロスフェード。duration も interpolator も**アプリは一切上書きしていない** | `utils/ProgressVisibilityManager.java:56-70`（1行目 `import android.R;`） | 【実測】 |
| sampleb3 | `View.setVisibility()` の直切り替え。**フェードすら入れていない** | `F/com.sampleb3.novel` §A-2 | 【実測】 |
| zyunto | `setVisibility(0)/(8)` の直切り替え | `defpackage/fn0.java:31-36` | 【実測】 |
| X / Photos / Gmail / YouTube / YT Music / Maps | **見つからなかった**（骨→実データの遷移を規定する定数・アニメが res/dex に見当たらない） | `F/*` | 先例なし |
| 画像の crossfade（骨とは別物） | X = **無効**（`crossfade(` setter が R8 に削られている＝未上書き）／カクヨム = **無効**（Coil3 は crossfade がオプトイン、`extras` 設定0件） | `coil3/q.java:14-40`／`defpackage/j44.java:68`, `sa9.java:71-79`, `m93.java:198-203`, `qn2.java:403-406` | 【解読】 |
| （参考）Coil3 の crossfade ヘルパ既定 duration | **200ms**（アプリが有効化した場合の値。X もカクヨムも有効化していない） | `coil3/request/i.java:29` | 【実測】 |
| （参考）Flutter `cached_network_image` 既定 | fadeIn 500ms / fadeOut 1000ms。**なろう公式が上書きしているかは判定不能**（数値定数のため Dart AOT から取れない） | `F/com.syosetu.android` §A-5 | 【実測（既定値）＋取得不能】 |

→ **「骨をクロスフェードで実内容へ渡す」先例は無い。あるのは①即差し替え（カクヨム・sampleb3・zyunto）と②OS 既定フェードの丸投げ（tscsoft）だけ。**【解読】

---

## 5. ページングの設定値と画像キャッシュ上限

### 5-0. 前提

**`androidx.paging` は 13本中 0本**（`08` §2.3・§4.1）。よって `pageSize` / `prefetchDistance` / `initialLoadSize` / `maxSize` という**名前の定数はどこにも存在しない**。以下は各社の自前実装から同じ役割の値を掘ったもの。【実測（不在の確認）】

### 5-1. prefetch 距離（残り N 件で次を取る）

| アプリ | N | 実体 | 出所 | 印 |
|---|---:|---|---|---|
| **X（URT / Compose）** | **3** | `DefaultUrtTimelinePagingPolicy` の `bottomPagingTriggerThreshold` 既定3。判定は `totalItemsCount − lastVisibleIndex <= 3` | `com/x/urt/paging/a.java`（`toString` `:68-75` でフィールド名確定）／判定 `com/x/ui/common/d3.java:18-29`／呼び出し `com/x/urt/l0.java:29`, `com/x/video/tab/z.java:944` | 【解読】 |
| X（上方向） | **5**（既定は **-1 ＝無効**） | `topPagingTriggerThreshold`。判定 `firstVisibleItemIndex <= 5` | `com/x/urt/paging/a.java`／`com/x/ui/common/d3.java:41-52` | 【解読】 |
| X（汎用 Compose ページング） | **1 / 1** | `PaginationConfig(pageDownPrefetchDistance=1, pageUpPrefetchDistance=1)`。6dex 内に非既定の生成が0件＝全呼び出しで 1/1 | `com/twitter/pagination/e.java`（`toString` `:55`）／`com/twitter/pagination/compose/a.java:31-48` | 【解読】 |
| **カクヨム** | **0**（最終アイテムが可視になった瞬間） | `combine(snapshotFlow{totalItemsCount}, snapshotFlow{firstVisibleItemIndex + visibleItemsInfo.size == totalItemsCount}.distinctUntilChanged())` | `defpackage/bp0.java:743-770`(List)／`web.java:878-905`(Grid)／判定ラムダ `zq4`・`ys4`（再デコンパイルで回収） | 【解読】 |
| tscsoft | **0**（フッタ行が bind された瞬間） | `ImportNovelBaseAdapter.java:58-79` | 【実測】 |
| zyunto / sampleb3 | **無し**（ページング未実装 / 無限スクロール自体が無い） | `F/*` §B-1 | 【実測（不在の確認）】 |
| なろう公式 | 無限スクロールでなく**ページ番号ページャ**（`_buildPagination` / `_PagerButton` / `_maxPage`） | `F/com.syosetu.android` §B-1 | 【実測】 |

→ **0 か 3 の二択**。「LazyList/RecyclerView の1画面ぶんだけ先に取る」が実務上の上限（`08` §4.2）。【解読】

### 5-2. 1ページの件数 / 初回

| アプリ | 件数 | 出所 | 印 |
|---|---|---|---|
| X | 引用ツイート検索 **20**／xchat ローカル **50**（受信箱も 50）／汎用 `PaginationConfig` は 1 | `com/twitter/navigation/timeline/f.java:27`／`com/twitter/feature/xchat/a.java:213`, `:119` | 【実測】 |
| X（ホーム TL の `count=`） | **取れなかった**（`com/twitter/api/legacy/request/` がこの6dex に無い） | `F/com.twitter.android` §B-2 | 先例なし（dex 欠落） |
| **カクヨム** | 一覧 **50**（`worksInLastEpisodePublishedOrder` / `publicWorks` / `followingWorksForVisitor` / `recommendedWorks`）／**ランキング 20**（`rankedWorks`）／ユーザー公開作品 10／コメント **200（offset 方式）**／ホームのカルーセル一括 3・6・9・10・20・30・50・100 | `defpackage/at6.java:22`, `bz8.java:25`, `hy8.java:25`, `xt6.java:23`／`yp6.java:22`／`p99.java:23`／`p39.java:26`／`tk6.java:19`, `ux3.java:44-47`, `bs9.java:44-46/:67`, `qw3.java:69`, `p64.java:19` | 【実測】 |
| カクヨム（初回） | **上表と同数**（`after` に null を渡すだけ。初回だけ別件数という分岐は見つからなかった） | `F/jp.kadokawa.el.kakuyomu` §B-1(b) | 【実測（不在の確認）】 |
| tscsoft | **100**（`st=位置+1` で進む）。初回も 100 | `SearchCondition.java:24,49` | 【実測】 |
| zyunto | **`lim=500` を1回**（ランキングは 100）。API に `st=` が無い＝全件 | `F/com.zyunto.naroreader` §B-1 | 【実測】 |
| sampleb3 | `lim=500` だが用途は ncode 一括の更新チェック専用 | `F/com.sampleb3.novel` §B-1 | 【実測】 |
| X（初回ロード件数） | **見つからなかった**（同上・dex 欠落） | — | 先例なし |

→ **1ページ 20〜50 が主流**（X 検索 20 / カクヨム ランキング 20・一覧 50）。tscsoft 100 と zyunto 500 は外れ値（`08` §4.2）。【解読】

### 5-3. 保持上限（maxSize 相当）

| アプリ | 上限 | 出所 | 印 |
|---|---|---|---|
| **X（レガシー TL）** | **400 件**でページング停止 | `com/twitter/timeline/t.java:29-31`／効き方 `TimelineBottomPagingPolicy.java:70,94` | 【実測】 |
| X（ツイート詳細 TL） | **400**（FS `android_tweet_detail_timeline_view_limit`。0 以下なら 400 へフォールバック） | `com/twitter/tweetdetail/g0.java:26-30` | 【実測】 |
| X（ハイドレーションキャッシュ） | **20 件** | `com/twitter/timeline/loader/g.java:84` | 【実測】 |
| X（Compose / URT 側） | **見つからなかった**（`com/x/repositories/urt/` が dex 外） | `F/com.twitter.android` §B-4 | 先例なし（dex 欠落） |
| tscsoft | **オフセット 2000 で打ち切り** | `ApiUtil.java:15` | 【実測】 |
| カクヨム | **無し**（append のみ・trim ロジック不在） | `F/jp.kadokawa.el.kakuyomu` §B-1(b) | 【実測（不在の確認）】 |
| zyunto / sampleb3 / 公式 | 無し | `F/*` | 【実測（不在の確認）】 |

### 5-4. リスト系のチューニング（参考）

| 項目 | 値 | 出所 | 印 |
|---|---|---|---|
| `setItemViewCacheSize(8)` | **X の1箇所のみ**（他12本は既定 2 のまま） | `com/twitter/rooms/ui/audiospace/a2.java:747` | 【実測】 |
| `setMaxRecycledViews` / `setInitialPrefetchItemCount` | **13本すべて未呼び出し** | `08` §4.3 | 【実測（不在の確認）】 |
| `setOffscreenPageLimit(1)` | X・tscsoft（5画面）・zyunto・sampleb3 で共通。tscsoft の設定ダイアログのみ 3 | `com/twitter/communities/detail/k.java:350` ほか | 【実測】 |
| Compose Lazy の近傍レンジ（ライブラリ既定・上書きではない） | `slidingWindowSize=90, extraItemCount=200` | `androidx/compose/foundation/lazy/grid/s0.java:28` | 【実測】 |
| `beyondBoundsItemCount` | **Compose の Lazy 系に存在しない API**（`HorizontalPager` 用）。APK にも無し | `F/jp.kadokawa.el.kakuyomu` §B-1(c) | 【解読】 |
| カクヨムの一覧列数 | ウィンドウ幅 **≤600dp で 1列 / 超で 2列** | `defpackage/ve4.java:917-918`, `vi1.java:877-878` | 【解読】 |

### 5-5. 画像キャッシュ上限

| アプリ | ローダ | メモリ | ディスク | crossfade | 上書き | 出所 | 印 |
|---|---|---|---|---|---|---|---|
| **X** | Coil3 | **20%**（低RAM 15%） | 空き **2%**・**10MiB〜250MiB**（`coil3_disk_cache`） | **無効** | **一切上書きしていない**（差しているのは EventListener `com.x.media.imageloader.telemetry.p` のみ） | `coil3/q.java:14-40`, `:63-90`／`coil3/disk/a.java:24-47`, `h.java:16` | 【解読】 |
| **カクヨム** | Coil3 | 20%（低RAM 15%） | 空き 2%・10MB〜250MB | **無効** | **全部既定**（シングルトンは素の `ImageLoader.Builder(context).build()`。`SingletonImageLoader.Factory` 実装が0件で R8 が分岐ごと削除） | `defpackage/fs7.java:7-14`, `es7.java:11-41`, `bic.java:721-732`, `b44.java:58-95`, `hf8.java:58-70` | 【解読】 |
| X（maxBitmapSize・既定） | — | — | **4096×4096** | — | — | `coil3/request/i.java:18` | 【実測】 |
| sampleb3 | Glide（本文中の挿絵1箇所のみ） | memoryClass × **0.4**（低RAM 0.33）、ArrayPool **4MB** | **250MB** | 無し | `AppGlideModule` 未実装＝全部既定 | `F/com.sampleb3.novel` §B-2 | 【解読】 |
| なろう公式 | `cached_network_image` + `DefaultCacheManager` | — | 既定キー `libCachedImageData` | — | カスタム Config の痕跡なし | `F/com.syosetu.android` §B-3 | 【実測】 |
| tscsoft / zyunto | **画像ローダ無し**（書影を扱わない） | — | — | — | — | `F/*` §B-2 | 【実測（不在の確認）】 |

> ⚠️ **20% / 2% / 10MB / 250MB は Coil3 のライブラリ既定値であって、X やカクヨムの設計判断ではない**
> （両社は「Coil3 を素で使う」判断をしているだけ。`F/jp.kadokawa.el.kakuyomu` §B-2 の明示注記）。【解読】

### 5-6. HTTP キャッシュ（参考・骨とは無関係）

| アプリ | OkHttp `Cache` | タイムアウト | 出所 | 印 |
|---|---|---|---|---|
| tscsoft | **500MB**（524,288,000） | connect 10s / read 15s / write 15s | `08` §4.5 | 【解読】 |
| X | **2MiB**（2,097,152） | connect **90s** / read・write 20s / pool 50・300s | `com/twitter/network/b1.java:112-115`, `b0.java:8-31` | 【解読】 |
| カクヨム | **未使用**（`OkHttp-Sent-Millis` 等の文字列が APK に不在） | — | `F/jp.kadokawa.el.kakuyomu` §B-3 | 【実測（不在の確認）】 |
| zyunto | 未設定 | — | `F/com.zyunto.naroreader` §B-2 | 【実測（不在の確認）】 |
| sampleb3 | 未設定。API アクセス間隔ガード **60秒** | connect 5s / read 10s | `08` §4.5 | 【実測】 |

---

## 6. 縦書き・縦スクロール読書での先例

### **先例なし。13本のうち1本も、縦書き本文の骨を持たない。**

否定の根拠は2段:

1. **縦書き本文を実装している3本は、そもそも骨を持たない**（骨が無いので縦書きの骨も無い）。【実測（不在の確認）】
   - tscsoft: 自前 `com.tscsoft.verticaltextview`（`VerticalTextLayout.java:997` で `setHasFixedSize(true)`）を持つが、
     ローディングは素の不定 ProgressBar（`F/com.tscsoft.naroureader` §A-0/§A-1）
   - sampleb3: 縦書きリーダの ViewPager2 は `setOffscreenPageLimit` すら未設定＝既定（`08` §4.3）。
     ローディングは全画面スクリム + ProgressBar オーバーレイ（`F/com.sampleb3.novel` §A-0/§A-5）
   - なろう公式: Flutter ビューア。骨は APK 全体に文字列すら1バイトも無い
     （`unzip -p base.apk | grep -aoiE "shimmer|skeletonizer"` が 0 件・`F/com.syosetu.android` §A-0）
2. **骨を持つ唯一の競合（カクヨム）の骨は、横組みのリストアイテム1種のみ**（`Row`+`Column` の横並び 4スロット。
   `defpackage/w00.java:1243`）で、縦書き組版の記述は findings のどこにも無い。【実測】
   X・Google 製7本の骨もすべて横組み（LinearLayout vertical / GridLayout / Compose Column）。【実測】

**含意（【解読】）**: 縦書きの骨は**借りる先が存在しない領域**。案V1〜V3 の判断材料は他社から一切供給されない。
（横書きの骨から流用しうるのは「行高＝置換テキストの `lineHeight`」〔カクヨム〕・「末端だけ短くする」〔X〕・
「幅を全部変える」〔Gmail〕といった**組方向に依存しない原理**だけで、これらは列高・列末に読み替えれば成立する。読み替えの妥当性自体は先例で裏付けられない＝【推測】。）

---

## 7. 借りられない規範（ADR 0014 / 0005 との衝突）

| 規範 | 衝突相手 | 衝突の内容 |
|---|---|---|
| **shimmer / pulse そのもの**（X 2300ms・カクヨム 800ms 往復・Photos 3500ms・Maps 1000ms・zyunto Lottie 708ms のすべて） | **0014 §C 禁止則「静謐」行**（「motion はフィードバック（状態変化の伝達）のみ。**装飾アニメ・自動ループは無し**」）／**0014 §B 原則5**（静謐は機能である） | いずれも `infiniteRepeatable` / `repeatCount=-1` / `lottie_loop=true` ＝**自動ループ**。フィードバック（状態変化の伝達）ではなく待ち時間の装飾。→ **周期・easing・進行方向・振れ幅の一切が借用不可**。※我々の実装は既にこの裁定に沿っている（`TransitionSkeletons.kt:177-184` の KDoc＝骨にアニメを一切付けない） |
| **Photos の `shimmer_duration=3500ms`** | **0014 §C 禁止則①**（duration ≤350ms 上限） | 10倍の超過。0014 の「motion 進行類型の 350ms 上限免除」裁定（`MotionDurationProgress=400ms`）は**連続的に"進行中"を示すフィードバック**への免除であって、装飾ループには及ばない（当てはめは【推測】） |
| **X の shimmer 振れ幅 α0.7↔1.0**（および compose-shimmer 既定 α0.25↔1.0） | 同上（静謐） | 「振れ幅を控えめに」は**振れること自体を前提**にした規範。振れない骨には適用対象が無い＝**借用不可**（衝突というより無効化） |
| **Photos の「invisible な本物のテキストで幅を確保」** | 0014 §A 層構造とは非衝突。ただし**現行裁定「内容非依存の汎形」**（`TransitionSkeletons.kt:177-181` の KDoc）と衝突 | 実文字列を使うには**その章の本文が既にロード済み**である必要があるが、遷移窓 250ms の骨は本文未取得の時点で描く＝**構造的に適用不能**（章題だけはトップバーに既出だが、それを骨の幅に使うと骨が内容依存になる。この観察は【推測】） |
| **YouTube 現行の角丸 6〜8dp / Photos のカード 24dp** | **0014 §A（③姿＝モックが正本）** および「プレースホルダは実データの色域・外形を模す」恒久ルール（横モック `subcap` 節） | 我々の実 `.block` は角丸 **2px**（`reading-D.html`／`TransitionSkeletons.kt` 写経）。骨だけ 6〜8dp にすると**差し替えの瞬間に角丸が変わる**＝骨が実データの外形を模していない。→ **借りられるのはカクヨム/YT Music の 2dp 側だけ**（偶然こちらの実装値と一致） |
| **Gmail の 750ms / 500ms 遅延閾値** | 原則との衝突ではなく**前提の不一致** | §1-4 のとおり、窓 250ms の遷移骨に適用すると骨が一度も出ないか、窓を超えて残る |
| **Coil3 / paging の各値**（§5 全体） | 衝突なし。ただし**適用対象が無い** | 本アプリは 100% ローカル（INTERNET 権限も HTTP クライアントも無い＝`book-api-analysis/AGENTS.md`）。ページング・画像キャッシュは**現時点で存在しない層**の値 |

**衝突しない（借用の余地がある）もの**:
- 骨と地色の**コントラスト比の帯**（ライト 1.27〜1.44 / ダーク 1.59〜2.09）。0014 §D の WCAG 4.5:1 は
  「**意味を運ぶ文字**」への規律で、骨は文字ではなく場所取り＝§D の適用対象外。【解読】
- **バー幅を全部変える**（Gmail）・**最終行だけ 0.55〜0.7**（X）・**行高＝lineHeight**（カクヨム）＝
  いずれも静止画で成立し、ループアニメを伴わない。【解読】
- **ダークの骨色を必ず別途指定する**（YouTube / YT Music の night 上書き漏れが反例）。【解読】

### 7-1. 参考：我々の現在値を測定帯に当てた結果（【解読】・WCAG 式で計算）

| スキンD テーマ | 段落骨/素地 | 見出し骨/素地 | 測定帯 |
|---|---:|---:|---|
| LIGHT（`#F1F0EC` / `#E4E2DB` on `#FBFAF8`） | **1.093:1** | **1.243:1** | ライト帯 1.27〜1.44 → **段落骨は帯の下・見出し骨も僅かに下** |
| SEPIA（`#EBDEBE` / `#DCCC9F` on `#F2E7CE`） | **1.087:1** | **1.297:1** | （セピアの先例はカクヨムのみ・比の記載なし） |
| DARK（`#1B1F26` / `#2A2F38` on `#14171C`） | **1.087:1** | **1.336:1** | ダーク帯 1.59〜2.09 → **両方とも帯の下**（YT Music の 1.014 ほどではないが同じ方向） |

（値の出所: `ui/theme/skins/SkinD.kt:154-155`・`:183-184`・`:207-208` ほかの `blockBackground`/`blockBorder` と各テーマの `background`。
横モックの `--skel-block` / `--skel-line` と同値。**この比較は事実の提示であって、変えるべきという主張ではない**。）

---

## 8. こちらの7案との噛み合わせ

⚠️ **どれを採るべきかは書かない。**〈支持／反する／無関係〉は「§1〜§6 の規範が、その案の方向を後押しするか・引き戻すか」だけを示す。

### 8-1. 横書き4案

| 案 | 判定 | 理由（規範の該当項目番号つき） |
|---|---|---|
| **案1 位置だけ直す** | **支持** | §3 で **Maps・カクヨムが同粒度**（バー2本 / 1アイテムぶん）で実在。§2-4 のカクヨム「行高＝置換テキストの lineHeight」が狙う**差し替えでレイアウトが跳ねない**という目的そのものが、案1の「始まる位置を実本文と揃える」と同一（§2-4・§3）。§4 の「即差し替え」先例（カクヨム・sampleb3・zyunto）とも整合 |
| **案2 面を埋める** | **支持** | §3 で **YouTube（`ghost_card_block` を3回 include）・YT Music（`ghost_card` を5回 include）**が「同一要素の反復で面を埋める」を実装済み＝最多数派の粒度。§2-4 の Gmail 行高18/行間12 も一様ピッチの反復を前提とした値 |
| **案3 段落の呼吸まで** | **支持** | §3 の **Gmail（8本の幅が 121/313/210/237/179/210/146/263dp と全部違う）**と **X（本文行 `fillMaxWidth(0.85〜0.95)`・最終行だけ 0.55〜0.7）**が、案3の①〜④（章題2行折り返し・段落行数のばらし・段落末の余り・短い会話行）と**同じ操作**。§2-4 の Gmail 段落間32dp/行間12dp も「段落の塊」を作るための値 |
| **案4 紙面まるごと** | **一部支持・一部反する** | 支持側＝§3 の **Photos が「実ウィジェットを tint して骨にする」＝紙面まるごと**を実装済み（先例あり）。反する側＝§7 のとおり Photos の手口は**実データが既にある**ことが前提で、遷移窓 250ms の骨には構造的に適用できない。案4 が写す前書きブロック・ルビ・区切りは**内容依存**で、現行裁定「内容非依存の汎形」（`TransitionSkeletons.kt:177-181`）と衝突する。**この衝突は他社規範ではなく我々の既存裁定との衝突**（Photos は内容依存を許した上で構造一致を取っている＝設計思想が違う） |

**4案すべてに共通で無関係**: §1（遅延閾値・最低表示時間＝§1-4 の前提不一致）／§2-1・2-2 の shimmer 周期・easing・振れ幅（§7 のとおり静謐則で借用不可）／§5（ページング・画像キャッシュ＝適用対象なし）。

### 8-2. 縦書き3案

| 案 | 判定 | 理由（規範の該当項目番号つき） |
|---|---|---|
| **案V1 列に立てる** | **無関係（先例ゼロ）** | §6 のとおり縦書きの骨は13本中0本。**支持も反対も規範側から供給されない**。強いて言えば §2-4 カクヨムの「行高＝置換テキストの lineHeight」を列高へ読み替えれば案V1 の「列送り・列高を実本文と揃える」と同型だが、**読み替えの妥当性は先例で裏付けられない**（【推測】） |
| **案V2 段落の切れ目まで** | **無関係（先例ゼロ）**。ただし操作の原理だけは §3 に対応物あり | §6。原理の対応＝Gmail「幅を全部変える」・X「最終行だけ 0.55〜0.7」を**列末の余り 38/64/47%** へ読み替えたもの。組方向に依存しない原理なので移せる可能性はあるが、縦組みでの検証例は無い（【推測】） |
| **案V3 紙面まるごと** | **無関係（先例ゼロ）＋案4 と同じ内部衝突** | §6。加えて §7 のとおり、前書き板・ルビ粒・区切りは内容依存で現行裁定「内容非依存の汎形」と衝突する（横の案4 と同構造） |

**縦書き固有の追加事実**: 3案が依拠する「上下バーは本文の上に浮き、クリアランスを足さない」という版面
（縦モック `subcap` 節・`VerticalChapterContent.kt:137-146`）についても、**他社に先例は無い**（§6）。

---

## 9. 「先例なし」と判定した項目（一覧）

| 項目 | 種別 | 根拠 |
|---|---|---|
| **縦書き・縦スクロール読書の骨** | **存在しないことを確認** | §6（13本すべて。縦書き実装3本は骨を持たず、骨を持つ8本は全部横組み） |
| **骨→実内容のクロスフェード規定**（時間・カーブ） | **存在しないことを確認** | §4（あるのは即差し替えと OS 既定フェードの丸投げだけ） |
| **遅延閾値・最低表示時間**（Gmail 以外の12本） | X・カクヨム・tscsoft・zyunto・なろう公式は**不在を確認**／YouTube・YT Music・Maps・Photos は**res 限定で見つからず**（dex 未捜索） | §1-2 |
| YouTube の自前 `VerticalShimmerLoadingFrameLayout` の duration / tilt / dropoff / repeat_delay | 取得できず（res に無く、dex を追っていない） | `F/google-apps-skeleton` 末尾 |
| Gmail の骨のベース色・ハイライト色 | 取得できず（`colors.xml` に無い＝コード側で生成） | 同上 |
| X の骨ベース色 `HorizonThemeColors.tertiary` の ARGB（Compose 側） | 取得できず（`com/x/compose/core/` がこの6dex に無い）。※`08` §3.4 の3テーマ値は **View 側 `?abstractColorLightGray`** から解決したもの | `F/com.twitter.android` §A-2/§D |
| X のホーム TL の `count=` / 初回ロード件数 / Compose(URT) 側のアイテム上限 | 取得できず（該当パッケージが dex 外） | `F/com.twitter.android` §B-2/§B-3/§B-4 |
| カクヨムの `LazyRow` の `contentPadding` | 取得できず（本体関数が jadx でデコンパイル不能） | `F/jp.kadokawa.el.kakuyomu` §C |
| カクヨムのリスト保持上限（trim） | **存在しないことを確認**（append のみ） | 同 §B-1(b) |
| `beyondBoundsItemCount` | **API 自体が Compose Lazy 系に存在しない** | 同 §B-1(c) |
| なろう公式の `cached_network_image` フェード値の上書き有無 | 判定不能（Dart AOT の数値定数） | `F/com.syosetu.android` §A-5/§5 |
| `pageSize` / `prefetchDistance` / `initialLoadSize` / `maxSize` という**名前の定数** | **13本すべてに存在しない**（`androidx.paging` 不使用） | §5-0 |

---

## 10. 出所の再確認手順（値を疑ったとき）

```bash
# 元の findings（本書の全数値の一次根拠）
ls /mnt/c/Users/naesimono/Desktop/project/book-api-analysis/_work/findings/
# 横断表と読み
sed -n '295,400p' /mnt/c/Users/naesimono/Desktop/project/book-api-analysis/08-startup-profile-and-ui-constants.md
# 対象APKからの再生成（付録の手順が正本）
sed -n '540,588p' /mnt/c/Users/naesimono/Desktop/project/book-api-analysis/08-startup-profile-and-ui-constants.md
```
