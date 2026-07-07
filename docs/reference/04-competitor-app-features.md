# 04 競合アプリの「非読書機能」横断解析

> 目的: 自作 **novel-reader_andloid**（なろう系PDF→ふりがなHTMLローカルリーダー）に足すAPI機能の参照として、
> 実在する競合5アプリが「**小説を読む以外に**」何を実装し、それを**何のAPI/技術**で実現しているかを、
> 全アプリのデコンパイルコードから確定した。前提は `01`（自作＝完全ローカル）/`02`（なろうAPI＝メタ専用・本文は返らない）/`03`（案A＝ディスカバリ先行）。
> 解析対象: `apks_decompiled/{pkg}/`。裏付けは各所にファイル/行で明記。

---

> ⚠️ **重大訂正（2026-07-06・実機ストア表記で確定）— A/B は「なろう本文スクレイプ」から撤退済み**
> 初版は「A・B はなろう本文をスクレイプする」と結論したが、これは**デコンパイルAPK内に残る“死蔵コード”を現役と誤認**したもの。実機のストア説明文がグラウンドトゥルースで、実態は以下：
> - **A なろうリーダ**: ストア更新履歴 **v1.36** に「**目次ページをWebサイトをそのまま表示するように変更**／**キャッシュ機能を削除**／過去データ用に**アーカイブ機能**追加」と明記。＝本文スクレイプ（＝旧「キャッシュ機能」）は**削除済み**。現行の本文体験は**WebView閲覧**、既取得分は**アーカイブ（読み取り専用・通信なし）**のみ。公式APIメタ（ランキング/検索/ジャンル/作品情報）は継続。
> - **B Web小説リーダー**: ストアの**【非対応サイト一覧】に「小説家になろうグループ」**（＋「マグネット！」）を明記＝「要望を頂いても対応できません」。＝**なろうは本文取得対象から除外**。カクヨム/Arcadia/暁/Pixiv/アルファポリス/エブリスタ 等**なろう以外は継続対応**。
> - コード上の辻褄: `GetCacheUseCase.task(){ return mUseArchive ? loadFromArchive() : load(); }` の `load()`（=`CacheManager.loadCache`→`httpGet.get(chapterUrl)`）が旧スクレイプ本体だが、UI導線は `loadFromArchive()`/WebView に振り替え済み。`remote_config_defaults.xml` の `.p-novel__*` セレクタや B の `NarouConverter` も**残るが到達しない**。
> - **構造的結論**: なろうの規約強化を受け、**専用リーダー勢（A・B）は本文スクレイプから撤退**（A=WebView回帰＋過去分アーカイブ、B=なろう非対応化）。C は元々WebView丸表示。＝**この5本の現行版で「なろう本文をアクティブにスクレイプ」しているものは無い**（D/E は自社BE）。
> 本文中の該当箇所（§1表・§2マトリクス・§3-3・§4-A/4-B・§5-3・付録A/B）はこの訂正に沿って更新済み。**「コードが在る」≠「実機UIが出している」**（静的解析の限界＝本書末尾の注記どおり、動的検証で判明）。

---

## 1. Context ／ 対象5アプリ

| # | pkg | 名称 | 版 | 実装基盤 | 難読化 | 本文の出所 | 解析到達度 |
|---|---|---|---|---|---|---|---|
| A | com.tscsoft.naroureader | なろうリーダ | 1.36.17 | 複数Activity・独自http層 | 緩い（可読） | **WebView閲覧**（v1.36でスクレイプ削除）＋過去分アーカイブ | **コード精読＋ストア表記で確定** |
| B | com.sampleb3.novel | Web小説リーダー | 2.638 | 複数Activity・OkHttp | 緩い（可読） | **なろう以外の複数サイトをパース**（なろうは非対応化・縦書きパーサ／登録約30ドメイン） | **コード精読＋ストア表記で確定** |
| C | com.zyunto.naroreader | なろうブック | 4.10.0 | Compose単一Act・Retrofit2 | 中 | ncode.syosetu をWebView表示 | **Retrofit interface確定** |
| D | com.syosetu.android | 小説家になろう（**公式**） | 1.6.5 | Compose単一Act・Room/WorkManager | **極度＋pairip** | **不明（内部API秘匿）** | 文字列/ホスト解析＝**大半が不明** |
| E | jp.kadokawa.el.kakuyomu | Kakuyomu（**KADOKAWA公式**） | 6.12.0 | RootActivity・Apollo GraphQL | 中 | 自社GraphQL | **GraphQL operation名まで確定** |

**この解析で判明した最重要の分岐**: 5本は「本文をどこから取るか」で類型に割れ、**なろう本文については現行版で全社がスクレイプを回避**している。
- **公式サイトをWebViewで丸ごと表示**（**A**（v1.36で回帰）・C）＝ 本文パースを放棄し、サイトを（広告ごと）そのまま見せる。A は過去取得分のみアーカイブで読める。
- **なろう以外をスクレイプ**（B）＝ なろうは非対応化。カクヨム/Pixiv/アルファポリス等 他サイトの本文HTMLは継続パース。
- **自社バックエンドで本文まで配信**（D・公式／E・公式）＝ プラットフォーム事業者だけが取れる、非公開の第一者API。
- ＝**「なろう本文をアクティブにスクレイプする現役アプリ」はこの5本に存在しない**（A/B は撤退、C は丸表示、D/E は自社BE）。旧版 A/B にはスクレイプ実装が在ったが規約強化で撤去された（冒頭の重大訂正）。

> ⚠️ **準備reconの訂正（D 公式アプリ）**: 準備セッションの `_recon/com.syosetu.android.md` は「ja splitの文字列でログイン/検索履歴/DL/レビューを実証」としていたが、本番で `split_config.ja.apk` を aapt2 で全ダンプした結果、**それらは第三者SDKの定型文字列の誤認だった**。例:「検索キーワードを送信」= androidx SearchView の accessibility 文字列、「ダウンロード（中/一時停止/完了/失敗）」= ExoPlayer（`exo_download_*`／広告動画のキャッシュ）、「ログイン」= Google Play services の `common_signin_button_text`。**公式アプリの資源には第一者の機能文字列が実質ゼロ**で、内部会員API/本文APIは平文に一切出ない（後述 §4-D）。

---

## 2. 機能横断マトリクス（本体）

凡例: **✓**=実装確認 / **△**=部分的・間接的 / **?**=未確認（難読化等で不明） / **—**=無し・非該当。
太字セルは各アプリの“看板機能”。

| 機能グループ | 機能 | A なろうリーダ | B Web小説 | C なろうブック | D なろう公式 | E Kakuyomu |
|---|---|---|---|---|---|---|
| **ディスカバリ** | ランキング（order別） | ✓ `rank/rankget`＋独自CDN | — （ブラウザ経由） | ✓ Retrofit `order=` | ? | ✓ `RankingWorks`＋ウィジェット |
| | フリーワード検索 | ✓ `word`＋範囲/属性フル | △ サイト内検索 | ✓ `word`/`notword`（URL可） | ? | ? （検索operation未確認） |
| | ジャンル/タグ絞り込み | ✓ `genre`/`nocgenre`＋ジャンル表 | △ 蔵書のタグ検索（主にPixiv） | ✓ `genre=`＋`genre_list` | ? | △ ラベル/タグ |
| | サジェスト/おすすめ | ✓ Suggest＋`yomou/rank/top` | — | — | ? | ✓ `Recommended`/`Related`/`Topical` |
| | 作品情報/詳細カード | ✓ `infotop` | ✓ NovelDetail | ✓ `infotop`/`mypage` | ? | ✓ `WorkQuery`/`WorkEnd` |
| **アカウント&同期** | サイトログイン同期 | △ 閲覧Cookieのみ（同期無） | **✓ `favnovelmain/list`** | — | ? | **✓ 自社OAuth `/login`・`/oauth`** |
| | ブックマーク（ローカル） | ✓ BookmarkList／しおり | ✓ NarouBookmark | ✓ 既読管理 | △ Room（推定） | ✓ フォロー/ラベル |
| **更新** | 更新チェック（新着話） | ✓ UpdateNovel | ✓ BackgroundUpdate（bulkメタ） | △ likely | △ WorkManager（推定） | ✓ `Unread…WorkList`（フォローfeed） |
| | 更新通知（push/local） | △ local（CommonReceiver） | ✓ FCM＋local | ✓ FCM | ✓ FCM | ✓ FCM |
| **コンテンツ取得** | オフラインDL/本文取込 | ✗ **v1.36でキャッシュ削除**（過去分アーカイブ閲覧のみ） | ✓ DownloadService/Worker（**なろうは非対応**・他サイトのみ） | △ WebView表示のみ | ? （DLマネージャは誤認） | △ episodeはGraphQL（明示DL未確認） |
| | 本文取得方式 | **WebView閲覧**（目次もWebそのまま・スクレイプ撤去） | **なろう以外**の複数サイトをスクレイプ（なろうは非対応化） | ncode を**WebView丸表示**（広告付） | **不明（内部API秘匿）** | **自社GraphQL** |
| | 全文（本文串刺し）検索 | ✓ 第三者 `narou.xii.jp` | — | — | — | — |
| **読書補助** | 縦書き表示 | △ TTFParser（埋込フォント） | ✓ Tategaki＋サイト別変換 | ✓ | ? | ? |
| | TTS読み上げ | — | **✓ TTSService** | — | ? （ExoPlayer有・用途不明） | — |
| | 内蔵ブラウザ | △ WebViewWrapper | ✓ Browser（フル・設定付） | △ 本文がWebView | ? | ? |
| **整理・入出力** | 分類（フォルダ/ラベル） | ✓ TreeList | ✓ 階層フォルダ（`/`区切り・`FileDir`） | — | ? | ✓ Label（一覧/編集/付与） |
| | フィルタ/除外（NG） | ✓ Filter/Excluded | △ 目次フィルタ（未読/削除） | △ 除外ワード（`notword`） | ? | △ 通報（ReportSpam） |
| | PDF取込/ビューア | — | **✓ PdfList/Viewer**（自作と同路線） | — | — | — |
| | レビュー/感想 | ✓ review/impression 表示 | —（表示なし） | ✓ 感想・レビュー表示 | ? | ✓ `AddOrUpdateWorkReview` |
| | 誤字/内容の通報 | ✓ `novelreport/input` | — | — | ? | ✓ `CreateTypoReport`/`ReportSpam` |
| | ホーム画面ウィジェット | ? | —（receiver無し） | —（receiver無し） | ? | ✓ ランキング＋読書中（2種） |
| **収益化** | 広告 | ✓ AdMob | ✓ AdMob/APS/AppLovin/PubNative | ✓ リワード動画 | ✓ SDK大量 | — |
| | 課金/サブスク | — | ✓ 広告除去購入 | — | ? | ✓ ネクスト（サブスク）＋ギフト |
| | ソーシャル（フォロー/投銭/共有） | — | △ Twitter/LINE共有 | — | ? | ✓ フォロー/ギフト/近況/共有 |

**マトリクスから読める構造**:
1. **APIメタだけで綺麗に立つ機能**（ランキング/検索/ジャンル/作品カード）は A・C が明快に実装。**ここは `02` の言う「本文が要らない」領域**で、A/C のコードがほぼそのまま `03` 案A の設計図になる。
2. **B は「発見」をAPIでなくスクレイピング/ブラウザで解いている**（なろうAPIは“ブックマーク更新差分のバルク照会”だけに使用）。代わりに **多サイト対応・DL・TTS・PDF** と、機能の幅が突出。自作アプリの将来像（発見＋取込＋PDF）に最も近い。**ただし本文取得の対象から「なろう」は外れた**（ストア非対応サイト明記）＝多サイトパーサ保守の限界／規約リスクが表面化した例。
3. **D（なろう公式）は解析不能が正しい結論**。プラットフォーム側の内部APIは非公開で、平文の手掛かりを残していない。
4. **E（Kakuyomu）は「公式プラットフォーム・アプリの全部入り」**。本文もソーシャルも課金も全部 GraphQL 一本で捌く。自作アプリが真似できる範囲は限定的だが、**“公式が実装する機能の総目録”**として価値。

---

## 3. 機能グループ別の掘り下げ（実API＋自作への示唆）

### 3-1. ディスカバリ（ランキング・検索・ジャンル）

**A なろうリーダ — なろうAPIを教科書的に叩く実装（`03` 案Aの完成形サンプル）**
- 検索: `utils/SearchCondition.java` の `toApiString(offset)` が `Uri.Builder` で全パラメータを組む。
  `st`（offset）・`word`・`notword`・範囲フラグ `title=1`/`ex=1`（あらすじ）/`keyword=1`/`wname=1`（作者）・`genre`|`nocgenre`（ハイフン連結）・`notgenre`|`notnocgenre`・`type`・`order`・`userid`|`xid`。
  範囲はビット（TITLE=1/WRITER=2/STORY=4/KEYWORD=8）。**検索履歴は最大20件・ピン留め対応**（`GS.setSearchHistory`）。
- ランキング: `domain/datasources/NarouApiDataSourceImpl.java#getRanking()` →
  `https://api.syosetu.com/rank/rankget/?rtype=<yyyyMMdd>-<d|w|m|q>&out=json`（`enums/RankingType`：Daily/Weekly/Monthly/Quarter、集計基準時 7:00 JST）。
  取得は**2段**（`rankget` で ncode＋順位＋pt → 別途メタ取得）。`ranking_{d/w/m/q}_rank_cached_at` と `_novel_cached_at` を分けてキャッシュ。
- ランキング配布の裏技: `url_ranking_cache = https://tscsoft.net/naroureader/ranking/rank.zip`（自社CDNに焼いたランキングをzipで配布し、公式APIへの集中を回避）。
- 全文検索は自前でなく**第三者サービス** `https://narou.xii.jp/search?n=…&t=…` に委譲。

**C なろうブック — Retrofit interface が最小構成のお手本**
- `defpackage/kl0.java`（＝Retrofit interface）:
  ```
  @GET("novelapi/api?out=json")
  a(@Query word, notword, genre, type, ncode, @Query lim:Int, @Query order, @Query userid:Int)
  ```
  baseUrl=`https://api.syosetu.com/`、OkHttp＋Gson（`defpackage/x3.java`）。**`novel18api` interfaceは無し＝R18非対応**。
- order値（strings.xml）: `order_favnovelcnt`（ブックマーク数）/`order_ncodedesc`（新着投稿）/`order_new`（新着更新）/`order_old`（更新が古い）。

**B Web小説** はディスカバリをAPIで解かない。`logic/NarouApi.java` の唯一のAPI用途は**ブックマークの更新差分バルク照会**（後述 3-4）。発見は内蔵ブラウザ＋サイト内検索で代替。

**→ 自作への示唆**: `03` 案A の縦スライスは **A の `SearchCondition` と C の Retrofit interface をそのまま設計参照**にできる。最小実装は C の一枚interfaceで足り（`word/genre/type/order/lim`）、UXを詰めるなら A の「範囲フラグ＋検索履歴＋ピン留め」まで。ランキングは `rank/rankget` の2段取得＋端末キャッシュが競合の共通解。**A の“ランキングをCDN配布”は負荷/レート対策として賢い**が、自作アプリ単体なら端末キャッシュ（`02`の推奨=最長2週間）で十分。

### 3-2. アカウント & 同期（なろうログイン）

**B が唯一「なろう本体ログイン同期」を実装**。方式が重要:
- 呼ぶ先（dex実証）: `syosetu.com/favnovelmain/list/`（お気に入り一覧）・`favnovelmain/isnoticelist/`（更新通知リスト）・R18 `favnovelmain18/isnoticelist/`・`/mypage`。
- **認証はアプリが資格情報をPOSTしない**。`EpisodeWebViewClient.saveCookie()` がWebViewログイン後のCookieを回収 → `PreferenceValues.setCookieJson()` にJSON永続化 → `WebNovelApplication.loadCookie()` が `CookieManager` に戻し、以降のHTTPに付与。つまり **「WebViewでユーザー自身がログイン → Cookieを間借りして認証済みGETを撃つ」** 方式（pixivのメンバー/R18も同じ `no_login_pixiv`）。
- A も `useBrowserCookie()`＋`viewer_cookie_host=https://syosetu.com` を持つが、用途は**閲覧/年齢認証Cookie**でお気に入り同期ではない（△）。
- E（Kakuyomu）は**自社アカウントのOAuth**（`auth.AuthenticationService`＋deep-link `/login`・`/oauth`）。D（公式）も会員があるはずだがアプリ実装は不明。

**→ 自作への示唆**: なろうお気に入りの取り込みは技術的には「WebViewログイン→Cookie間借り」で可能だが、**これは `03` の案B/C相当の“非公式・規約グレー”領域**。認証情報の保持・Cookieの取り扱いはプライバシー責任も伴う。**案A（メタのみ）では不要。やるなら独立フェーズで規約判断とセット**（安易に前提化しない＝`03`の方針を踏襲）。

### 3-3. コンテンツ取得（API vs スクレイピング vs 自社BE）

| | 方式（**現行版**） | 実装 | 自作への含意 |
|---|---|---|---|
| A | **WebView閲覧**（v1.36でスクレイプ撤去） | 目次も本文も `ncode.syosetu.com` をWebViewでそのまま表示。既取得分のみローカル**アーカイブ**で読める（新規取得なし）。旧スクレイプ本体 `CacheManager.loadCache`→`httpGet.get(chapterUrl)` は`GetCacheUseCase.load()`側に**死蔵** | ネイティブ本文描画すら手放し「実サイトを見せる」に回帰。**規約対応で撤退した実例** |
| B | **なろう以外**をスクレイプ | `logic/tategaki/` に Kakuyomu/Hameln/Alphapolis/Pixiv 等のコンバータ（本文HTML→縦書き）。**Narou は非対応化**（ストア【非対応サイト】明記）。`NarouConverter.java` はコードは残るが対象外 | 「サイトごとにパーサ＝保守コスト」に加え、**特定サイトが規約で丸ごと落ちるリスク**の実例 |
| C | **WebView丸表示** | 本文パースを放棄し `ncode.syosetu.com` を広告ごと表示 | パースの保守から降りる代わり、ネイティブ描画の資産を捨てる（自作の方針＝ネイティブ描画と真逆） |
| D | 不明 | 平文に第一者ホスト無し（§4-D） | 参照不可 |
| E | 自社GraphQL | `AndroidEpisodeViewerAdditionalData`／`…MediaFranchisedWork` で本文取得 | プラットフォーム事業者だけの特権。自作には非該当 |

**→ 自作への示唆**: `03` の分水嶺（メタはAPI／本文は別経路）が実データで裏付いた上、さらに強い教訓が出た——**なろう本文に関しては、専用リーダー勢（A・B）ですら非公式スクレイピングを諦め、WebView閲覧／非対応化へ退いた**。本文取得は「作れるが規約で維持できない」領域であり、自作が中核に据えるのは非推奨。事業者だけが持つ自社API（D/E）は参照不可。**＝現実的な最小形は「メタはAPI／本文は実サイトへWebViewで送る」**（＝A/C の現行着地点）。

#### 3-3補足. 本文取得の“現況”実測（初版の結論を訂正）

> ⚠️ **初版（および直前の検証応答）の結論「A・B はなろう本文をスクレイプする」は誤り**で、本節はそれを訂正する。誤りの原因は**デコンパイルAPKに残る“撤去済み機能の残骸コード”を現役と読んだこと**。決定的な一次証拠は実機のストア説明文（2026-07-06 確認）。

- **A（tscsoft／なろうリーダ v1.36）＝本文スクレイプは削除済み**。
  - ストア「更新の内容 v1.36」に明記：**「目次ページをWebサイトをそのまま表示するように変更」「キャッシュ機能を削除」「過去データ用にアーカイブ機能追加」**。
  - コードの辻褄：`GetCacheUseCase.task(){ return this.mUseArchive ? loadFromArchive() : load(); }`。`load()` が旧スクレイプ本体（`CacheManager.loadCache`→`NarouApiManager.getChapterUrl`→`httpGet.get()`→`TextMappingQuery` で `.p-novel__*` 抽出）だが、UI導線は `loadFromArchive()`（ローカル既取得データを**通信なしで読むだけ**）と WebView へ振り替え済み。`remote_config_defaults.xml` の `cache_text_mapping`（`.p-novel__body`/`.p-novel__text`）や `novel_info_pattern`（`p-infotop-*`）は**残骸**（作品情報表示等に一部流用の可能性はあるが、本文の新規スクレイプ導線は無い）。
  - 旧目次パーサ `NovelHtmlObject.java`（`.index_box`/`.subtitle a`/`.long_update`）や `utils/parser/EpisodeFetcher.java` も同様に旧世代の残存。現行の目次・本文表示は `ViewerPageFragment` の WebView（注入JSが `article.p-novel` 等ライブDOMを操作）。
- **B（sampleb3／Web小説リーダー）＝なろうは非対応化**。
  - ストア対応サイト詳細の**【非対応サイト一覧】に「小説家になろうグループ」**（＋「マグネット！」）＝「要望を頂いても対応できません」。
  - コード上は `NarouConverter.java`（`convertV2()`=`p-novel__body`/`p-novel__text`、`convert()`=旧`novel_honbun`）や `TategakiTextConverter` の振り分けが**残る**が、現行では対象外。**継続対応は カクヨム/Arcadia/暁/Pixiv/アルファポリス/ベリーズカフェ/エブリスタ/フォレストページ/魔法のiらんど/エムペ/ナノ/BLove/…/Wayback Machine 等、なろう以外**。
- **C（zyunto／なろうブック）＝元から本文非パース**。`ncode.syosetu.com` を **WebViewで丸表示**するのみ（本文DOM参照ゼロ・grep空）。

**まとめ（現況の真値）**: **現行5本で「なろう本文をアクティブにスクレイプ」しているアプリは無い**。A=v1.36でスクレイプ削除しWebView回帰（＋過去分アーカイブ）、B=なろう非対応化（他サイトは継続）、C=WebView丸表示、D/E=自社BE。旧版 A/B にスクレイプ実装が在ったこと自体は事実だが、**なろうの規約強化に追随して撤去された**。
> 自作への含意: 競合の撤退は「本文スクレイプは規約リスクが高く、専用アプリでも維持できない」ことの強い裏付け。`03`(4)同様「規約・robots・再配布は別レイヤの意思決定」を守り、**自作は本文取得を中核化しない**（WebView送客＋APIメタに徹する）方針を補強する。

### 3-4. 更新チェック & 通知

- **B（模範例）**: `logic/NarouApi.java#fetchData()` が **最大500 Nコードをハイフン連結で1リクエスト**（`?of=t-n-ga-gl-nt-w&libtype=2&out=json&lim=500&ncode=n1-n2-…`）にまとめ、`general_all_no`/`general_lastup` の差分で新着話を検知。**種別ごと60秒のレート制御**（`canFetch`）。実行は WorkManager（`UpdateWorker`/`BackgroundUpdate`、`WORKER_TIME_LIMIT=540s`）、通知は FCM（`MessagingService`）＋ローカル。
- A: `UpdateNovel`＋`CommonReceiver` でスケジュール更新（通知はローカル寄り）。
- C/D/E: FCM。E はフォロー作品の未読フィード（`UnreadLatestEpisodeWorkList`）でサーバ主導。

**→ 自作への示唆**: 蔵書の更新チェックは **B のバルク照会（`of` 最小＋ハイフン連結＋レート自制）がベストプラクティス**。`02` の転送量作法（`out=json`/`gzip=5`/`of`最小/キャッシュ）とも一致。案Aの発見だけなら不要だが、**将来「取り込んだ作品の新着話通知」を足すならこの型を採用**（既存の前景サービス＋WakeLock設計に載る）。

### 3-5. 読書補助・整理・入出力

- **PDFは B だけが持つ**（`PdfListActivity`/`PdfViewerActivity`/`PdfViewerSettingsActivity`）＝**自作アプリと唯一の直接競合**。ただし B のPDFは「取り込んだPDFを見るビューア」で、自作の「PDF→ふりがなHTML変換」ほど作り込んでいない。**自作のルビ/縦組み変換は差別化点として生きる**。
- 縦書き: A=埋込フォント解決（TTFParser）、B=サイト別テキスト整形、C=表示オプション。TTSは B のみ。
- 整理: A=フォルダツリー＋NGフィルタ／除外、E=ラベル（一覧/編集/付与）。**“大量の蔵書を捌くUI”は A と E が充実**。
- 通報/誤字: A（`novelreport`）と E（`CreateTypoReport`/`ReportSpam`）。

### 3-6. 収益化

- 広告主体: A（AdMob）、B（AdMob/Amazon APS/AppLovin/PubNative）、C（リワード動画でバナー3h非表示）、D（applovin/moloco/bytedance/bigo/digitalturbine/inmobi/fyber/five_corp＝**広告てんこ盛り**）。
- 課金: B=**広告除去のワンタイム購入**（`BILLING_STATE_ACTIVE`＋Remote Config `enable_purchase_no_ad`）。E=**サブスク（カクヨムネクスト）＋ギフト投げ銭**（`VerifyGooglePlayPurchase`/`SendGift`）。
- E だけ**広告なし**（サブスク＆ギフトで成立）。

**→ 自作への示唆**: 収益化は今回のスコープ外だが、競合の“地雷”として記録。**個人開発規模なら B 型（広告＋広告除去の少額課金）が現実解**、E 型（サブスク/投げ銭）は事業者規模の話。

---

## 4. アプリ別・補足の確定事項

### 4-A なろうリーダ（tscsoft）
最も“なろうAPIの教科書”。ジャンル表（異世界〔恋愛〕101…ノンジャンル9801）内蔵、R18フル（noc/mnlt/mid.syosetu.com＋`/redirect/ageauth/`＋NocGenre 男性/女性/BL/大人）。自社BEフォールバック `nrs.tscsoft.net/api/v1/{news,novels,rankings,users}`。
> **本文取得の現況（重要）**: **v1.36 で「キャッシュ機能（＝本文スクレイプ）」を削除**、目次も本文も**WebViewで実サイトをそのまま表示**する方式に回帰。既取得分だけ**アーカイブ（読み取り専用・通信なし）**で読める。`GetCacheUseCase.load()`/`CacheManager.loadCache` や `remote_config_defaults.xml` の `.p-novel__*` セレクタは**残るが本文の新規取得導線は無い**（死蔵）。**自作が下敷きにできるのは「公式APIメタ（ランキング/検索/ジャンル/作品情報）＋WebView送客」の部分**であって、本文スクレイプは**真似すべきでない撤退済み機能**。

### 4-B Web小説リーダー（sampleb3）
機能最多。本文取得は**縦書きコンバータ**（カクヨム/ハーメルン`syosetu.org`/アルファポリス/Pixiv 等、`logic/tategaki/*Converter`）＋`Constants.java` に登録可能な約30ドメイン（estar/maho/berrys-cafe/novelism/akatsuki/daysneo/note/atwiki 等）を目次解析＋内蔵ブラウザで横断。DL・TTS・PDF・内蔵ブラウザ・広告除去課金まで揃う。**自作アプリの将来像に最も近いが、その分「多サイトパーサの保守」という重荷を可視化している**（strings 更新履歴が各サイトの仕様変更追随ログで埋まっている）。
> **なろうの現況（重要）**: ストアの**【非対応サイト一覧】に「小説家になろうグループ」を明記**＝**なろうは本文取得の対象外**。`NarouConverter`（`p-novel__body`等）と旧【小説家になろう様】対応の残骸はコードに在るが到達しない。＝多サイト対応でも**規約強化で特定サイトが丸ごと落ちる**実例。なお「ブックマーク更新のバルク照会（公式APIメタ）＋WebViewログインCookie間借り」の2点は別枠（§3-2/§3-4）。

### 4-C なろうブック（zyunto）
Retrofit一枚（`novelapi/api`）でランキング/検索、本文は**ncode.syosetuをWebViewで広告ごと丸表示**する割り切り。R18・ログイン同期・TTSは無し。**「最小コストで公式サイトを見せる」設計の実例。**

### 4-D 小説家になろう（公式・syosetu.android）— **解析不能が結論**
- `split_config.ja.apk` を aapt2 全ダンプ → **第一者の機能文字列は実質ゼロ**（全て androidx/material/ExoPlayer/広告SDKの定型）。
- base dex のホスト平文を総ざらい → `syosetu.com`/`narou`/`kadokawa` の**APIホストは一つも出ない**（出るのはGoogle/Firebaseと広告SDKのみ）。
- 第一者BEの痕跡は Firebase プロジェクト `narouapp2025-product`（storage: `narouapp2025-product.firebasestorage.app`）と FCM のみ。Room＋WorkManager＋pairip＋AppsFlyer OneLink（`jump-narouapp.onelink.me`）。ExoPlayer在中だが広告動画か音声かは**断定不可**。
- **結論**: 公式アプリは内部会員API/本文APIを難読化・秘匿し、平文の手掛かりを残していない。**「公式は内部APIを非公開にしている」こと自体が確定した結論**であり、これ以上の内部仕様は本解析の手法（静的・平文）では取得不能（憶測しない）。

### 4-E Kakuyomu（KADOKAWA公式）
Apollo GraphQL、エンドポイント `https://kakuyomu.jp/graphql`（host は `App.onCreate` で `https://kakuyomu.jp`）。operation名を復元でき、**公式プラットフォーム機能の総目録**が得られた（§付録E）。本文・フォロー・近況ノート・レビュー・誤字報告・ギフト投げ銭・サブスク（サポーター/ネクスト）・Google Play課金・ウィジェット2種を GraphQL 一本で提供。R18・バナー広告は無し。**自作が直接真似できる範囲は狭いが、“公式が何を機能とみなすか”の網羅リストとして参照価値が高い。**

---

## 5. 自作アプリ novel-reader_andloid への推奨

`03` の推奨（段階導入・**案A＝ディスカバリ先行**）を、競合実装で裏付け・具体化する。

### 5-1. 競合の“当たり前”＝最低ライン（案Aで満たすべき）
5本すべて（実装できる4本）が持ち、**なろうAPIメタだけで作れる**もの:
- **ランキング（order切替: 総合/日/週/月＋ブックマーク/新着）** … A/C が `rank/rankget`＋`order=` で実装。
- **フリーワード検索（範囲: タイトル/あらすじ/キーワード/作者）** … A の `SearchCondition` が完成形。
- **ジャンル絞り込み＋作品情報カード（title/作者/あらすじ/文字数/話数/完結・連載/pt）** … `of=` で取得。
- **端末キャッシュ**（`02`推奨=最長2週間、ランキングは分単位で変わらない）。

→ この4点が「発見機能を名乗るなら外せない最低ライン」。実装は **C のRetrofit一枚 + A の検索/キャッシュ設計** をそのまま参照でよい（`03`§4 の技術レイヤに合致）。

### 5-2. 自作の差別化点（competitorが弱い/やっていない所）
- **PDF→ふりがな(ルビ)HTMLのネイティブ縦組み描画**。B のPDFは単なるビューアで変換をしない。C はWebView依存。**自作のルビ/章分割/前後書き整形＋ネイティブ描画は競合に無い強み** → 発見機能は“この読書体験へ送り込む導線”に徹すればよい（`03`案Aの UI 素案どおり）。
- **広告ゼロ・完全ローカルの軽さ**（自作は24MiB、権限も最小）。D/B の広告過多・肥大と対極。**「静かに手元の本を読む」路線**は差別化になる。

### 5-3. 規約リスクの切り分け（競合の実装＝そのまま採用してはいけない線引き）
競合が“やっている”からといって自作が前提化してよいわけではない。リスク順に:
- **低リスク（案A・採用可）**: なろうAPIメタの取得（ランキング/検索/ジャンル/作品カード）。A/C と同じ範囲。R18を扱うなら `novel18api`＋年齢認証を A に倣う（要別途規約確認）。
- **中〜高リスク（案B/C・独立判断）**: **本文HTMLスクレイピング**。利用規約・robots・負荷配慮・キャッシュ/再配布可否は**技術外の意思決定**（`03`(4)）。**かつて A・B が実施していたが、なろうの規約強化で両社とも撤退した**（A=v1.36でキャッシュ削除しWebView回帰、B=なろう非対応化）。＝**「競合がやっているから」ではなく「専用アプリでも維持できず降りた」領域**。自作が踏み込むなら維持不能リスクを織り込む必要がある。
- **高リスク（保留推奨）**: **なろうログインCookieの間借り同期**（B が実施）。認証情報の保持＝プライバシー責任。案Aでは不要。やるなら規約判断＋独立フェーズ。
- **スコープ外**: 多サイト対応（B）＝保守地獄、収益化（B/D/E）、ソーシャル/課金（E）。

### 5-4. 具体的な次アクション（`03`§7 案A採用時に接続）
1. `INTERNET`＋`ACCESS_NETWORK_STATE` 権限追加、OkHttp＋kotlinx.serialization 依存追加。
2. **C を参考にRetrofit/またはOkHttp直の一枚 interface**（`novelapi/api`：`word/genre/type/order/lim/ncode/of/out=json/gzip=5`）で、まず「週間ランキング一覧が出る」縦スライス1本。
3. ランキングは `rank/rankget?rtype=<date>-<w>` の2段取得＋端末キャッシュ（**A の設計参照**）。
4. 検索は **A の `SearchCondition` を参照**して範囲フラグ＋履歴を段階的に。
5. UIは `03`§5（本棚に「さがす」導線→ランキング/ジャンル/検索/作品カード）＋ADR0005（和モダン、HTMLモック先行）。
6. 本文取込（案B/C）は**別フェーズ**として、規約・robots調査メモ（`04-content-fetch-legal.md` 等）を独立に固めてから判断。

---

## 付録: 各アプリの実測エンドポイント／API一覧

### 付録A なろうリーダ（tscsoft・`utils/ApiUtil.java`／`SearchCondition.java`／`NarouApiDataSourceImpl.java`／`res/values/strings.xml`）
| 用途 | URL / パラメータ |
|---|---|
| メタ一括 | `https://api.syosetu.com/{novelapi\|novel18api}/api/?out=json&of=n-t-w-u-[n]g-s-gl-nu-nt-e-k-ga-a&lim=300&ncode=…`（R18時のみ`n`=nocgenre、MAX_OFFSET=2000） |
| 検索（追加） | `&st=` `&word=` `&notword=` `&title=1` `&ex=1` `&keyword=1` `&wname=1` `&genre=`\|`&nocgenre=`（`-`連結） `&notgenre=`\|`&notnocgenre=` `&type=` `&order=`（`ncodedesc`等） `&userid=`\|`&xid=` |
| ランキング | `https://api.syosetu.com/rank/rankget/?rtype=<yyyyMMdd>-<d\|w\|m\|q>&out=json` |
| ランキングCDN | `https://tscsoft.net/naroureader/ranking/rank.zip`（自社配布・header `PX-Header: ranking`） |
| 目次 ⚠️ | `https://{ncode\|novel18}.syosetu.com/{ncode}/?p={N}`（**v1.36で目次もWebView表示へ／スクレイプ撤去・下記は死蔵URL**） |
| 本文 ⚠️ | `https://{ncode\|novel18}.syosetu.com/{ncode}/`（**v1.36でキャッシュ削除・現行はWebView閲覧＋過去分アーカイブのみ**） |
| 作品情報 | `https://{server}.syosetu.com/novelview/infotop/ncode/{ncode}/` |
| レビュー | `https://{server}.syosetu.com/novelreview/list/ncode/{ncode}/` |
| 感想 | `https://{server}.syosetu.com/impression/list/ncode/{ncode}/`（`/no/{N}/`付も） |
| 誤字報告 | `https://novelcom.syosetu.com/novelreport/input/ncode/{ncode}/no/{N}/` |
| ユーザページ | `https://{server}mypage.syosetu.com/{uid}/` |
| 全文検索(第三者) | `https://narou.xii.jp/search?n={ncode}&t={title}` |
| 自社BE(fallback) | `https://nrs.tscsoft.net/api/v1/{news\|novels\|rankings\|users}` |
| R18 | server=`novel18`（noc/mnlt/mid.syosetu.com）、年齢認証 `/redirect/ageauth/` |

### 付録B Web小説リーダー（sampleb3・`logic/NarouApi.java`／`Constants.java`／dex）
| 用途 | URL / パラメータ |
|---|---|
| 更新差分バルク | `https://api.syosetu.com/{novelapi\|novel18api}/api/?of=t-n-ga-gl-nt-w&libtype=2&out=json&lim=500&ncode={n1-n2-…}`（最大500・種別60秒レート） |
| ログイン同期 | `https://syosetu.com/favnovelmain/list/`・`…/favnovelmain/isnoticelist/`・R18 `…/favnovelmain18/isnoticelist/`・`/mypage`（WebViewログインのCookieを間借り） |
| 本文（縦書きパーサ・**現行対応**） | `syosetu.org`(Hameln)/`kakuyomu.jp`/`alphapolis.co.jp`/`pixiv.net` 他。振り分けは `TategakiTextConverter.java:312-324`＋各 `isTargetStatic`。⚠️ **`syosetu.com`(なろう)は【非対応サイト】に明記＝対象外**（`NarouConverter` はコードのみ残存・到達せず） |
| 本文（登録可能ドメイン＝約30） | `novelup.plus`/`estar.jp`/`maho.jp`/`berrys-cafe.jp`/`novelism.jp`/`akatsuki-novels.com`/`daysneo.com`/`no-ichigo.jp`/`novema.jp`/`tugikuru.jp`/`nanos.jp`/`nijikana.net` 他（`Constants.java`。目次解析＋内蔵ブラウザ対象・**なろうグループ／マグネットは非対応**） |
| なろう本文DOM（**死蔵**） | `NarouConverter.java` に 新:`p-novel__body`/`p-novel__text`/`p-novel__subtitle`（:25-124）・旧:`novel_honbun`/`novel_subtitle`（:134-168）が残るが、なろう非対応化により未使用 |
| UA | mobile: `…Android 5.0.2…Chrome/45…Mobile`／pc: `…X11; Linux x86_64…Chrome/45` |

### 付録C なろうブック（zyunto・`defpackage/kl0.java`・`x3.java`）
| 用途 | URL / パラメータ |
|---|---|
| 検索/ランキング | `GET https://api.syosetu.com/novelapi/api?out=json` ＋ `@Query word,notword,genre,type,ncode,lim,order,userid`（Gson/OkHttp、**R18 interface無し**） |
| order値 | `favnovelcnt`／`ncodedesc`／`new`／`old` |
| 本文/情報 | `https://ncode.syosetu.com/`（WebView表示）・`https://ncode.syosetu.com/novelview/infotop/ncode/`・`https://mypage.syosetu.com/` |

### 付録D 小説家になろう（公式・syosetu.android）
| 項目 | 実測 |
|---|---|
| 第一者APIホスト | **平文に存在せず**（内部API秘匿） |
| 第一者BE痕跡 | Firebase `narouapp2025-product`（`narouapp2025-product.firebasestorage.app`）、FCM |
| 平文で出るホスト | Google/Firebase＋広告SDK（moloco `sdkapi.dsp-api.moloco.com`、inmobi、appsflyer 等）のみ |
| deep-link | `jump-narouapp.onelink.me`（AppsFlyer OneLink） |
| 基盤 | Room／WorkManager／okhttp3／pairip／ExoPlayer（用途不明） |

### 付録E Kakuyomu（KADOKAWA公式・Apollo GraphQL、`https://kakuyomu.jp/graphql`）
埋め込み operation 名で復元した機能総目録:
| 分類 | GraphQL operation |
|---|---|
| ディスカバリ | `RankingWorks` / `RecentWorks` / `RecommendedCompletedWorks` / `RelatedWorks` / `TopicalWorkSelection` / `HomeMainForCommon` / `HomeMainForVisitor` / `PublicationWorksForVisitor` |
| 作品・本文 | `WorkQuery` / `WorkEnd` / `AndroidEpisodeViewerAdditionalData` / `AndroidEpisodeViewerMediaFranchisedWork` / `MediaFranchisedWorkPreview` |
| フォロー/未読feed | `FollowUser` / `UnfollowUser` / `UnreadLatestEpisodeWorkList` / `UnreadWorkListByUser` |
| 近況ノート(UserNews) | `AddUserNewsEntryComment` / `AddUserNewsEntryLike` / `Delete…Comment` / `Delete…Like` / `UserNewsEntryComments` / `…Detail` / `…Likes` / `…Reactions` |
| レビュー/報告 | `AddOrUpdateWorkReview` / `VisitorWorkReviewByWork` / `CreateTypoReport` |
| 履歴/プロフィール | `DeleteReadingHistory` / `Profile` / `Settings` / `UserWorks` |
| ギフト（投げ銭） | `SendGift` / `GiftByUser` / `CurrentGiftStatus` / `GiftGrantHistory` / `SentGiftHistory` / `SentGiftDetailHistory` / `GooglePurchaseGifts` / `Entry\|Change\|CancelGiftSponsorContinuation`（enum `GiftRoute_Kind`） |
| サブスク/サポーター | `CurrentSubscriptionPlans` / `CurrentSupporterStatus` / `SupporterDetail` / `SupporterHistory` |
| 課金 | `VerifyGooglePlayPurchase` / `GooglePlayInApp` / `GooglePlaySubscription` / `ProductDetails` |
| ウィジェット/共有 | `InReadingAppWidget`（＋`RankingConfigureActivity`） / `ShareSheetByUser` |
| 認証 | `auth.AuthenticationService` ＋ deep-link `/login`・`/oauth` |

---

*本書の裏付けは全てデコンパイル済みコード（`apks_decompiled/`）とAPK資源（aapt2ダンプ）に基づく静的解析。動的検証（実通信キャプチャ）は未実施のため、URLの一部（クエリの完全形・GraphQL変数）は実行時に変わり得る。D（公式）の内部APIは本手法では取得不能＝「不明」と明記した。*
