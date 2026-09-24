# APK の `.so` は8本入っている＝16KB ページ要件は該当する（「0本＝非該当」は偽測定だった）

- 重要度: ★★★（依存バンプ・SDK 引き上げのたびに引く。**「0件」を返す測定系を疑わず結論にした偽測定**の実例でもある）
- 確定日: 2026-08-06（監査で偽測定を特定。旧結論の確定日 2026-07-30 の「実測」自体が無効だった）
- 1行要約: 実 APK には上流由来の `.so` が入っており 16KB ページ要件の検査対象は実在する。実測は `python3 -m zipfile -l`（`unzip` はこの環境に無い）。

## 結論

**debug/release とも APK にネイティブライブラリが入っている**。2026-08-06 の実測では8本:
`libandroidx.graphics.path.so`（4ABI）＋ `libdatastore_shared_counter.so`（4ABI）。
供給元は `androidx.graphics:graphics-path:1.0.1`（ui-graphics 経由）と `androidx.datastore:datastore-core-android:1.1.1`。
release APK は8本すべて ELF `p_align = 0x4000` で現状合格（2026-07-30 実測＝`build.gradle` の手順コメント末尾）。

したがって **Android 15+ の 16KB ページ要件は該当**し、依存バンプのたびに
`android/app/build.gradle` の `packagingOptions` 内コメント（16KB ページ要件の確認手順①②）を実施する。
本数・供給元は依存バンプで変わるので、この md の数値を信じず毎回実測する:

```bash
python3 -m zipfile -l <apk> | grep '\.so'
```

（`unzip` 非導入環境でも動く作法＝同ディレクトリ `agp-srcdir-taskprovider-drops-builtby.md` の「検証の型」②と同じ）

## 偽測定の機序（この知見の本体）

旧版は「`unzip -l <apk> | grep '\.so$'` が両ビルドで0件＝ `.so` は1本も無い＝16KB 非該当」と断定していたが、
**この環境には `unzip` が導入されていない**。`unzip -l | grep` は unzip の "command not found" が stderr へ流れ、
stdout は空＝grep が0件を返す。この「0件」を「`.so` 無し」と読んだのが偽測定の全て
（パイプが先頭コマンドの失敗を隠す機序は memory `bash-pipe-masks-exit-code-false-green` と同根）。

教訓: **「0件」を結論にする測定は、先に陽性対照で測定系の生存を確認する**
（例: `grep '\.'` など必ずヒットする検索が実際に出力を返すこと・パイプ先頭の exit code を単独で見ること）。

## 実害の構造（なぜ★★★か）

旧版を信じると `build.gradle` の16KB整列確認（zipalign -P 16 ＋ ELF p_align 検分）を「非該当だから不要」として
恒久的に飛ばす。現在の8本はたまたま 0x4000 で合格しているだけで、上流が非対応版に差し替わった瞬間、
16KB ページ端末で**起動不能な APK を無検査で出荷**する。さらに `build.gradle` 側の正しい記述
（「Compose(ui/graphics) と datastore 由来の .so が 4ABI×2＝8本入る」）を「旧記述＝誤り」として削除する二次被害もあった。

## 併記: JVM テストは targetSdk の実行時挙動を一切捕まえない

JVM テストは全ファイルが `@Config(sdk = [34])` 固定。
つまり **targetSdk 35/36 固有の実行時挙動はテストでは一切検出されない**——
この層は**実機が唯一の検証手段**である。16KB ページに限らず、SDK 引き上げ時の挙動変化全般に効く注意。
