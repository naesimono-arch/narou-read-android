# Robolectric の LEGACY GraphicsMode では文字幅が「文字数」になる（溢れ系テストが静かに空振り）

**重要度**: ★★★
**確定日**: 2026-08-14（Robolectric 4.11.1 の shadow を逆アセンブルして確認・実害と対処まで済み）
**一行要約**: `@GraphicsMode` 未指定＝LEGACY では文字幅が文字数の定数になり fontScale を上げても幅が動かないので、「溢れる／折り返す／はみ出す」ことを前提にしたテストは前提が成立せず空振りする。

## 症状

幅の実測に依存するテストが、実機では起きる現象を再現できない。
実例: タブ6本の文字が合計 **13px** 固定になり、**fontScale 2.0 でも溢れが起こらなかった**。

## 真因

- **`@GraphicsMode` 未指定は LEGACY が既定。**
- LEGACY では Compose の計測経路 `ShadowPaint.nGetRunAdvance` が、`TextLayoutMode=REALISTIC` のとき
  **`end - start`（＝文字数）をそのまま px として返す**（それ以外は `0.0f`）。`measureText` も `String.length()`。
- つまり**文字幅が文字数の定数**になり、fontScale をいくら上げても幅は1px も動かない。

## 対処

**`@GraphicsMode(GraphicsMode.Mode.NATIVE)` は*メソッド単位*で付与できる**
（アノテーションの `@Target` に METHOD が含まれることを jar の bytecode で確認済み）
＝溢れを見る1本のためにクラス全体を NATIVE にしなくてよい。

NATIVE 側の裏付け: `nativeruntime-dist-compat-1.0.2.jar` に CJK フォント `fonts/DroidSansFallback.ttf` が
同梱されており、全角が約 1em で測られる。上の実例では
13文字×13sp×2.0 ≒ 338dp ＋ 溝 5×16dp＝80dp ＝ **約 418dp** が可視域 **312dp**（360dp − 左右 S24）を溢れる。

**代償**: NATIVE はグラフィクス実体を使うので実行時間が数百 ms 増える。だからメソッド単位で絞る。

## このリポジトリの規約と、それが守られなかった事実（2026-08-21 追記）

規約は当初から**「実測幅に依存するテストは NATIVE を明示する（既定に任せない）」**。対象は幅だけでなく
**寸法アサート全般**（高さ・位置・行数・折り返し数＝いずれも文字送りから決まる）。

⚠️ ただし旧記述の「実測幅を要する試験は**例外なく NATIVE を明示している**」は**事実でなくなっていた**。
寸法アサートを持ちながら NATIVE 未指定のテストが放置され、**検出力ゼロのまま全緑**で通り続けていた
（LEGACY では文字幅＝文字数の定数なので、溢れも折り返しも起こらず、アサートは自明に真になる）。

**教訓は「規約の中身」ではなく「規約の置き場所」**: テストを書く瞬間に読まれない場所
（この knowledge だけ）に書いた規約は守られない。⇒ ゲートの導線である
`.claude/skills/build/SKILL.md`（テストを回すときに必ず開く）へ規約行を置いた。
**この節を増補するときは、置き場所の側も同時に直すこと。**

現況の点検（`rg` はシェル関数なので `xargs` へは渡さずコマンド置換で回す）:

```bash
# GraphicsMode に触れているのに NATIVE でないテスト
comm -23 <(rg -l 'GraphicsMode' android/app/src/test --glob '*.kt' | sort) \
         <(rg -l 'GraphicsMode.Mode.NATIVE' android/app/src/test --glob '*.kt' | sort)
```

## あわせて: 「溢れていないのに静かに通る」を防ぐ

空振りが怖いのは、**前提が消えても本アサートは通ってしまう**から
（溢れないなら「選択タブは可視域に居る」は自明に真）。

→ **溢れていることを前提アサートで名指しする。**
今回のテストは冒頭で「右端のタブが可視域の外に居る」ことを先に主張しており、
LEGACY のまま走らせたとき**その前提アサートが落ちて空振りを検出できた**。通ってしまうより遥かによい。

一般則: 「××が起きている状況で○○が成り立つ」型のテストは、**××が起きていること自体をアサートする**。
環境要因で××が消えたときに赤くなる側へ倒す。

実装現物＝`android/app/src/test/java/com/novelreader/ui/discovery/DiscoveryHomeKRankingTest.kt`
（`溢れる幅でも選択中の期間タブは可視域へ追従する`・KDoc に同じ機序を記載）。
関連: `robolectric-vert-feature-noop.md`（NATIVE でも実機と等価にならない別クラスの限界）。
