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

1. **公開専用リポジトリを作る**（GitHub アカウント＝`naesimono-arch`。本体リポジトリ `narou-read-android` は非公開のまま分離する）。
   URL の形が2通りある。**どちらでも Play の要件は満たす**ので好みで選ぶ。

   | リポジトリ名 | 公開される URL | 備考 |
   |---|---|---|
   | `yosari`（推奨） | `https://naesimono-arch.github.io/yosari/privacy.html` | 普通のプロジェクトページ。他のサイトを将来足しても衝突しない |
   | `naesimono-arch.github.io` | `https://naesimono-arch.github.io/privacy.html` | ユーザーページ。URL は短いが、アカウントに1つしか作れない枠を使う |

2. **このディレクトリの中身をコピーして push**（`build.py` と `README.md` は公開に不要＝入れても害はないが、入れないなら `privacy.html` `index.html` `style.css` `.nojekyll` の4つ）。
3. **`【公開日】` を push する日の日付に置換する**（`privacy.html` の 1 箇所）。
   **仮の日付を先に入れない**——制定日は「実際に公開した日」であるべきで、前倒しの日付を書くと事実と食い違う。
   md 側も同じ日付に揃える（正本が md のため）。
   ```bash
   # 例: 2026年9月1日に公開する場合（md を直してから再生成するのが正しい順序）
   #   ../privacy-policy-draft.md の 【公開日】 を 2026年9月1日 に置換 → python3 build.py
   ```
4. **リポジトリの Settings → Pages** で公開元ブランチ（`main` / `/root`）を指定して有効化。
5. **公開された URL を実際に開いて確認**（反映まで数分かかる）。確認するのは以下。
   - `privacy.html` が表示される（404 でない）
   - 制定日が置換済み（`【公開日】` が画面に出ていない）
   - 連絡先 `colophon.apps@gmail.com` が Play Console の「デベロッパー用メールアドレス」と**同じ値**
6. **Play Console に URL を登録**（「アプリのコンテンツ → プライバシーポリシー」＋ ストアの掲載情報の「連絡先ウェブサイト」）。

## 独自ドメイン（`yosari.app`）を取得したあと

ドメインは**未取得**（取得を待たずに公開してよい＝Play は `github.io` のままで通る）。取得したら:

1. 公開リポジトリのルートに `CNAME` ファイル（中身は `yosari.app` の 1 行のみ）を置く。
2. DNS に GitHub Pages の A/AAAA レコード（apex）または CNAME（サブドメイン）を設定する。
3. Settings → Pages で独自ドメインを入力し、**Enforce HTTPS** を有効にする。
4. **Play Console の URL を差し替える**（差し替え自体はいつでも可能）。
   ⚠️ 旧 `github.io` の URL も**リダイレクトで生かしたまま**にする（審査中に旧 URL が 404 になると指摘の対象になりうる）。
