# 公開用の静的ファイル（GitHub Pages へ置くもの）

Play が要求するプライバシーポリシーの公開 URL（**常時公開・静的・非PDF・安定 URL**）を満たすための一式。
ホスティング先を GitHub Pages にする裁定は済んでいる（公開専用リポジトリ）。

**このディレクトリはビルド済みの成果物**＝ここのファイルを**そのまま公開リポジトリへコピーするだけ**で公開できる。
外部サービスへの登録・push は**すべて人間の作業**（この一式を作った便では一切行っていない）。

| ファイル | 役割 | 手で編集してよいか |
|---|---|---|
| `privacy.html` | プライバシーポリシー本体。**Play Console に登録するのはこの URL** | ❌ **生成物**。直さない（`build.py` が上書きする） |
| `index.html` | ルートの案内ページ。無いとサイト直下が 404 になる | ⭕ |
| `style.css` | 上記2枚が共有するスタイル | ⭕ |
| `.nojekyll` | GitHub Pages の Jekyll 処理を止める（`_` 始まりのファイルが将来落ちる事故の予防） | — 空ファイル |
| `build.py` | `../privacy-policy-draft.md` の公開本文区間から `privacy.html` を起こす | ⭕ |

## 本文を直すとき

本文の正本は **`../privacy-policy-draft.md` の「▼公開本文▼」〜「▲公開本文ここまで▲」だけ**。
html を手で直すと Data safety 申告との整合チェックが md 側しか見なくなるので、必ず md を直して再生成する。

```bash
python3 docs/store/pages/build.py           # 再生成
python3 docs/store/pages/build.py --check    # md と html が食い違っていないか（差分ありなら exit 1）
```

⚠️ **プライバシーポリシーを直したら `../data-safety-draft.md` との整合も必ず見る**（乖離はアプリ停止事由）。

## 公開の手順（人間の作業）

> ⚠️ **下の 1〜3・5 に当たる機械作業は `stage-publish.py` が一発でやる**（2026-08-25 追加）。
>
> ```bash
> python docs/store/pages/stage-publish.py       # --dry-run で何をするかだけ見られる
> ```
>
> 制定日を当日で置換 → `privacy.html` 再生成 → `../yosari-pages/` へ4ファイルを平置きコピー → commit まで進み、
> **残る GitHub 側の手続き（リポジトリ作成 → push → Pages 有効化 → 確認項目）がコマンド付きで表示される**。
> 以下は**そのスクリプトが何をやっているか**の説明＝手で追うとき・スクリプトが壊れたときの正本。

1. **公開専用リポジトリ `yosari` を作る**（**public**。GitHub アカウント＝`naesimono-arch`。
   本体リポジトリ `narou-read-android` は非公開のまま分離する）。**確定済み（2026-08-21 ユーザー裁定）**＝
   プロジェクトページ方式。ユーザーサイトの枠（`naesimono-arch.github.io` はアカウントに1つだけ）は温存する。

   | | 値 |
   |---|---|
   | リポジトリ | `naesimono-arch/yosari`（public） |
   | サイトの URL | `https://naesimono-arch.github.io/yosari/` |
   | **Play に登録するポリシー URL** | `https://naesimono-arch.github.io/yosari/privacy.html` |

2. **このディレクトリの中身をコピーして push**（`build.py` と `README.md` は公開に不要＝入れても害はないが、入れないなら `privacy.html` `index.html` `style.css` `.nojekyll` の4つ）。
   ⚠️ **4つは同じ階層に平置きする**（`privacy.html` は `style.css` と `index.html` を**相対パス**で参照している）。
   サブフォルダに入れると CSS が当たらない。逆に相対にしてあるおかげで、リポジトリ名が URL に挟まる
   プロジェクトページ方式（`/yosari/` が付く）でも、後で独自ドメインへ移して `/yosari/` が消えても、**どちらもそのまま動く**。
3. **`【公開日】` を push する日の日付に置換する**（`privacy.html` の 1 箇所）。
   **仮の日付を先に入れない**——制定日は「実際に公開した日」であるべきで、前倒しの日付を書くと事実と食い違う。
   md 側も同じ日付に揃える（正本が md のため）。
   ```bash
   # 例: 2026年9月1日に公開する場合（md を直してから再生成するのが正しい順序）
   #   ../privacy-policy-draft.md の 【公開日】 を 2026年9月1日 に置換 → python3 build.py
   ```
4. **リポジトリの Settings → Pages** で公開元ブランチ（`main` / `/root`）を指定して有効化。
5. **公開された URL を実際に開いて確認**（反映まで数分かかる）。開くのは
   `https://naesimono-arch.github.io/yosari/privacy.html`。確認するのは以下。
   - ページが表示される（404 でない）＋ **文字が組まれている**（素の白地に見えるなら CSS が 404＝手順2 の平置きを間違えている）
   - 制定日が置換済み（`【公開日】` が画面に出ていない）
   - 連絡先 `colophon.apps@gmail.com`（Play Console の「デベロッパー用メールアドレス」も**同じ値で確定済み**＝2026-08-21 裁定）
6. **Play Console に URL を登録**。
   - 「アプリのコンテンツ → プライバシーポリシー」＝ `https://naesimono-arch.github.io/yosari/privacy.html`
   - ストアの掲載情報の「連絡先ウェブサイト」＝ `https://naesimono-arch.github.io/yosari/`
   - デベロッパー用メールアドレス（アカウント設定側・**一般公開される**）＝ `colophon.apps@gmail.com`

## 独自ドメイン（`yosari.app`）を取得したあと

ドメインは**未取得**（取得を待たずに公開してよい＝Play は `github.io` のままで通る）。取得したら:

1. 公開リポジトリのルートに `CNAME` ファイル（中身は `yosari.app` の 1 行のみ）を置く。
2. DNS に GitHub Pages の A/AAAA レコード（apex）または CNAME（サブドメイン）を設定する。
3. Settings → Pages で独自ドメインを入力し、**Enforce HTTPS** を有効にする。
4. **Play Console の URL を差し替える**（差し替え自体はいつでも可能）。
   ⚠️ **`/yosari/` は URL から消える**——プロジェクトページに独自ドメインを当てると、サイトはドメインの直下に出る
   （`https://yosari.app/privacy.html`。`yosari.app/yosari/` にはならない）。ページ内のリンクは全て相対なので**修正は要らない**。
   ⚠️ 旧 `github.io` の URL は GitHub Pages 側がリダイレクトするが、**差し替え後に旧 URL を実際に開いて確かめる**
   （審査中に旧 URL が 404 になると指摘の対象になりうる）。
