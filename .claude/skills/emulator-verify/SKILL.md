---
name: emulator-verify
description: エミュレータ（AVD）検証の作法。KVMロード・唯一動く起動列・複数台運用・蔵書スナップショットの配布（SELinux の罠）・低スペック端末の作り方・ストア用スクショ撮影。「エミュで確認したい」「AVDを起動したい」「低スペック端末で見たい」「別の画面サイズで確認」「ストアのスクリーンショットを撮る」等の依頼で使う。
---

# エミュレータ検証の作法（WSL / AVD）

## 0. まず棲み分け — `/device-verify` とは前提が根本的に違う

**⚠️ 開発端末は Find X6 Pro（PGEM10 / Snapdragon 8 Gen 2 / RAM 16GB）＝最速級のハイエンド。
低スペック端末で起きる問題は、実機テストに構造的に一生映らない。それを見られるのはエミュだけ。**
「実機で確認したから大丈夫」は**速い側の1点しか見ていない**という意味でしかない。

| | `/device-verify`（実機・OPPO/ColorOS） | このスキル（エミュ） |
|---|---|---|
| 端末の持ち主 | **ユーザーの日常使いの端末**＝触る前に一声かける関門あり | 使い捨て＝**無断で起動・破壊してよい**（関門なし） |
| root | 不可（user ビルド・`ro.debuggable=0`） | **`adb root` が効く**＝`/data/data` 直操作・`setprop`・`stop; start` が使える |
| 蔵書 | **実蔵書が人質**＝削除・wipe 系の破壊フローは踏ませない | 捨ててよい＝**破壊フローの本命の検証場所** |
| 台数 | 1台（＋第三者端末） | 増やせる＝**機種・画面サイズ・API level を横断できる** |
| 見えないもの | 低スペック・切り欠き・別解像度 | **ColorOS/OPPO 固有の挙動・実 GPU の描画性能** |

**環境の事実（KVM の機序・GLES segfault の中身・不足ライブラリ・なぜ永続化しないか）の正本は
memory `wsl-android-emulator-kvm`**。このスキルは操作手順と判断の入口に徹する。
見た目の合否判定は従来どおり `/visual-language`（HTML モックが正本）が正本。

## 1. 起動 — KVM ロード → 唯一動く起動列

**KVM は WSL 再起動のたびに手動ロードが要る**（WSL カーネルは `kvm-amd.ko` を自動ロードしない・
`sudo` は対話パスワード必須で非対話から使えないので root は Windows 経由で取る）:

```bash
/mnt/c/WINDOWS/system32/wsl.exe -u root -e sh -c 'modprobe kvm_amd; chmod 666 /dev/kvm'
~/Android/Sdk/emulator/emulator -accel-check      # "KVM ... is installed and usable." を確認
```

永続化していない理由（＝`/etc/wsl.conf` の `[boot] command` 枠が既に埋まっている）は memory 側にある。
**「前は動いたのに `/dev/kvm` が無い」は WSL が再起動しただけ**＝原因を探る前にこの1行を打つ。

```bash
~/Android/Sdk/emulator/emulator -avd <avd> -no-window -no-audio -no-snapshot -no-boot-anim \
  -gpu host -feature -Vulkan
```

- ⚠️ **`-gpu swiftshader_indirect` は使えない**（アプリ描画の瞬間に `RenderThread` が segfault する）。
  `-gpu host -feature -Vulkan` **だけ**が動く組み合わせ＝ここは選択肢ではない。
- 非対話 Bash は PATH に adb を持たない → **`~/Android/Sdk/platform-tools/adb` を絶対パスで**呼ぶ
  （機序は memory `bash-tool-no-bashrc-gradle-env` と同じ）。

## 2. 台を増やす・複数台で回す

```bash
avdmanager create avd -n <名> -k "system-images;android-<api>;google_apis;x86_64" -d <device>
```

機種プロファイル（`-d`）はシステムイメージと独立＝**同じイメージのまま追加ダウンロードなしで機種だけ替えられる**。
これが「画面サイズ・密度・切り欠きの横断」を安く回せる理由。

**⚠️ 複数台あるときは adb の全コマンドに `-s emulator-XXXX` が要る**（無指定は
"more than one device" で失敗する。本ラウンドで実際に踏んだ）。スクリプトへ渡す `--serial` も同様。

**負荷の見積もり**: 起動は重い（4台同時ブートで load 20 / 20コア）が、**定常はほぼ無負荷**（アイドル4台で 0.24 コア）。
実際の制約は**メモリ（1台 3GB）と同時起動のピークだけ**＝並べること自体は安い。
⚠️ **`ps` の CPU 値はプロセス生涯の累積平均**で、起動時の重さをいつまでも引きずる。
**瞬間値と誤読して「エミュが CPU を食い続けている」と判断しない**——見るのは `top -bn2` か load average。

## 3. 蔵書スナップショットを別の台へ配る（⚠️ SELinux の罠・最重要）

複数台に同じ蔵書を入れると比較検証が一気に楽になるが、**`/data/data/<pkg>` を root の `tar` で移すと
SELinux ラベルが `system_data_file` に化けて DB が書けなくなる**（本ラウンドで4台とも踏んだ）。

- **症状**＝アプリが起動直後に死に、logcat に `avc: denied` が出るだけで**スタックトレースは出ない**
  （実体は `SQLiteCantOpenDatabaseException: Permission denied`）。
  ⚠️ **R8 やマイグレーションのせいだと誤診しやすい**（実際に「R8 が Room を壊した」と誤報しかけた便がある）。
  **アプリを入れ替えていないのに落ちるようになったら、まずラベルを疑う**。
- ⚠️ **`restorecon` では直らない**——app データのラベルは `seapp_contexts` 管理の**カテゴリ付き**
  （`app_data_file:s0:c216,c256,c512,c768` のような形）で、`file_contexts` しか見ない `restorecon` の管轄外。
- **正解＝install 直後の正しいラベルと uid を「展開の前に」控え、`chcon -R` でコピーする**。
  展開後に親を見ても**既に化けている場合がある**＝控えるのは必ず先。

```bash
# 送り出す側（蔵書が入っている台）
adb -s <src> root
adb -s <src> shell "cd /data/data && tar cf /data/local/tmp/appdata.tar com.novelreader"
adb -s <src> pull /data/local/tmp/appdata.tar <保存先>/

# 受け取る側 — 先に install して「正しいラベルと uid」を生やしてから控える
adb -s <dst> install -r <apk>
adb -s <dst> root
CTX=$(adb -s <dst> shell "ls -Zd /data/data/com.novelreader" | awk '{print $1}')      # ★展開の前
UID=$(adb -s <dst> shell "stat -c %u /data/data/com.novelreader" | tr -d '\r')        # ★展開の前
adb -s <dst> push <保存先>/appdata.tar /data/local/tmp/
adb -s <dst> shell "cd /data/data && rm -rf com.novelreader && tar xf /data/local/tmp/appdata.tar \
  && chown -R $UID:$UID com.novelreader && chcon -R '$CTX' com.novelreader"
adb -s <dst> shell am force-stop com.novelreader
```

## 4. 低スペック台を作る（弱小端末を見る唯一の手段）

⚠️ **AVD の `config.ini` に `hw.ramSize` / `vm.heapSize` を書いても効かない**（反映されない＝実測）。
効くのは setprop ＋ **フレームワーク再起動**（`stop; start` で zygote に読み直させる。再起動しないと反映されない）:

```bash
adb -s <serial> root
adb -s <serial> shell "setprop dalvik.vm.heapgrowthlimit 96m; setprop dalvik.vm.heapsize 128m; setprop ro.config.low_ram true"
adb -s <serial> shell "stop; start"
```

⚠️ **`ro.config.low_ram=true` のとき ART は `heapgrowthlimit` でなく `heapsize` 側を実効天井に採る**
（`heapgrowthlimit` に 96m を書いても効かず実効 128m だった＝OOM メッセージの `growth limit` の値で実測）。
**絞りたいなら `heapsize` を下げる**——`heapgrowthlimit` だけ下げて「絞ったつもり」になるのが罠。

## 5. 何を代替し、何を代替しないか

**代替する**: 画面サイズ・密度・fontScale・API level・ダークテーマ・言語・**低スペック端末**・**切り欠き**・
**release（R8）の実走**・**壊してよい破壊フロー**（削除・wipe・復元＝実機は実蔵書が人質で踏めない）。

**代替しない**: **ColorOS/OPPO 固有の挙動**（ベンダースキン・独自スケジューラ・Hans フリーザ・省電力）と
**実 GPU の描画性能**。実機関門（`/device-verify`）は残す。

⚠️ **ただし「実機なら本当の数字が出る」ではない**——実機もハイエンドで速い方へ振れる。
**低スペックの体感はエミュでも実機でも測れていない**（遅い方へ振る手段は `handover.md` に task 化済み）。
性能の回帰を止めたいなら時間でなく**端末非依存の「仕事量」**（組版回数など）で測る＝§7 の `measure_typeset_work.sh`。

## 6. ストア用スクリーンショットを撮る

- **機種の要件＝長辺 ≦ 短辺×2**。⚠️ **`pixel_7`(1080x2400＝20:9) は 2.22倍で不適合**＝
  日常検証用の AVD でそのまま撮ると全部弾かれる。適合＝`pixel_2`(1080x1920)・`pixel_tablet`(2560x1600)。
- ⚠️ **実在作品を宣伝物に写さない**。`sample_pdfs/` とエミュに入っている蔵書は**実在の「小説家になろう」作品**で、
  本文・題名・話タイトルを掲載素材に写すと、`docs/store/listing-draft.md` §0 の自衛線と正面衝突する
  （撮る前に**書き下ろしのデモ蔵書へ入れ替える**）:

```bash
python3 docs/store/assets/screenshots/seed-demo-library.py --serial <serial> --push
```

- ⚠️ **`screencap` の出力は RGBA（PNG color type 6）で Play に弾かれる**＝24bit PNG への変換が必須。
  `finalize-screenshots.py` が変換と検算（寸法・辺比・色深度）まで持つので**手で変換しない**。
- ⚠️ **release APK で撮る**。debug は設定に「開発」節が出て、きせかえ行と装いの間も見える＝
  **初回リリースに存在しないものが写る**（ADR 0027）。

## 7. 既に置いてある道具（再発明しない）

| 道具 | 何をするか |
|---|---|
| `tools/cutout_probe.py` | 切り欠きへの食い込みを**座標で**判定（`setup` / `check` / `restore` / `checklist`）。⚠️ AVD は overlay を全部切っても切り欠きを持つことがある＝「無効＝帯なし」と決めつけない |
| `tools/measure_typeset_work.sh` | 組版コストを**端末非依存の回数**で測る（ms ではない理由はスクリプト冒頭） |
| `docs/store/assets/screenshots/seed-demo-library.py` | デモ蔵書の生成と流し込み（`--serial` が `emulator-` 以外なら停止する安全弁つき） |
| `docs/store/assets/screenshots/finalize-screenshots.py` | Play 受入形式への変換と検算 |
