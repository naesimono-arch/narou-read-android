# Compose の `lineHeight` は下限であって、CSS の `line-height` ではない

**★★☆ / 2026-08-21** — HTMLモックの行送りをそのまま `lineHeight` へ写すと、Compose では
フォントの自然行高までしか縮まないため版面の足し算が合わない。**行送り(sp)と箱の高さ(dp)は必ず対で置く。**

（作品詳細の書影 案2-c を HTML モックから Compose へ翻訳中に実測）

## 事実

CSS の `line-height` は行箱の高さを**上下どちらへも**動かせる——フォント本来の行高より小さい値を
指定すれば、行はその値まで詰まる（行同士が重なることさえできる）。

Compose の `TextStyle.lineHeight` は**下限としてしか効かない**。フォントの自然行高より小さい値を
渡しても、行箱はそこまで縮まない。

実測（Robolectric NATIVE・xhdpi・スキンK＝D パレット）:

| 要素 | 指定 fontSize / lineHeight | モックの想定 | Compose の実測 |
|---|---|---|---|
| 題名（明朝） | 16sp / 22sp | 2行で 44dp | **45.5dp**（1行あたり約 22.75dp） |
| 作者（ゴシック） | 12.5sp / 16sp | 1行で 16dp | **18.5dp** |
| チップ（ゴシック） | 11.5sp / 14sp | 1行で 14dp | **約 17dp** |

`LineHeightStyle.Trim.Both` ＋ `PlatformTextStyle(includeFontPadding = false)` を併用すると
**行箱の外側の余白**（先頭行の上・最終行の下）は落とせるが、それでも自然行高までしか縮まない
（題名は 46 → 45.5dp にしかならなかった）。

## なぜ効くか（実害）

モックが「44 + 6 + 16 + 6 + 24 = 96dp」のように**行送りの足し算で版面を規定している**とき、
`lineHeight` をそのまま写すだけでは総高が合わない。上の例では 8dp 膨らみ、
「情報列を帯の内側へ上下対称に据える」という意匠の根拠がその場で崩れた。

しかも**静かに崩れる**: 見た目は「なんとなく下に寄っている」だけで、モック検分では気づけない。

## 対処

**行送り（sp）と箱の高さ（dp）を必ず対で置き、箱は `Modifier.height()` で確定させる。**

```kotlin
Text(text = title, fontSize = 16.sp, lineHeight = 22.sp, maxLines = 2,
     style = TextStyle(
         platformStyle = PlatformTextStyle(includeFontPadding = false),
         lineHeightStyle = LineHeightStyle(
             alignment = LineHeightStyle.Alignment.Center,   // 箱の中で字面を中央に置く
             trim = LineHeightStyle.Trim.Both,
         ),
     ),
     modifier = Modifier.height(44.dp))                      // 44 = 22 × 2 を**箱側で**確定させる
```

落とされるのは**行間であって字面ではない**ので、字面が切れることはない
（`Alignment.Center` で字面が箱の中央に来るため、上下に均等な余りが出るだけ）。

### `minLines` は代用にならない

「1行の題名でも箱を2行ぶんにする」用途で `minLines = 2` を使うと、Compose は
**「minLines の合成高」と「実際に2行組んだ高さ」を別々に計算する**ため両者が食い違う。
同じ実装で **41dp（短題名・minLines 経由）** vs **45.5dp（長題名・実2行）** が出た＝
題名の長さによって列の埋まり方が変わる。内容非依存の箱が要るなら `Modifier.height()` を使う。

## 併せて踏んだ罠: `testTag` の位置で測る箱が変わる

`Modifier.border(...).padding(...).testTag(TAG)` と書くと、semantics ノードの bounds は
**padding の内側**（＝字面の箱）になる。枠込みの総高を測りたい回帰テストは 24dp ではなく
14dp を読んでしまう。**枠・padding より前に `testTag` を置く**こと。

## 現物

- 実装と定数の why: `android/app/src/main/java/com/novelreader/ui/discovery/NovelDetailScreen.kt`
  （`DetailInfoTextStyle`・`DetailTitleBlockHeight` ほか）
- 数で守る側: `android/app/src/test/java/com/novelreader/ui/discovery/NovelDetailCoverBlockLayoutTest.kt`
  （⚠️ この種の検証は `@GraphicsMode(GraphicsMode.Mode.NATIVE)` が必須＝
  `robolectric-legacy-graphicsmode-text-width-is-char-count.md`）

## 2026-09-02: 「正本の比が入らない」実例3つ（本棚K・目次K の掃討で確定）

下限なので**自然行高より小さい比は書いても効かない**。掃討で実際に当たった3例:

- **`.lc .m`（目録行のメタ・13px / 比 1.4）**: 13sp×1.4 = 18.2sp に対し自然行高は 14.7sp 相当。
  指定した比そのものは効かず、達成できるのは「継承していた 28sp を切る」ことだけ（行箱 28.0→15.5dp）。
  正本の版面へ厳密に寄せるなら**行送りと箱高 dp を対で置く**＝行構造の作り直しが要る。
- **`.htxt h1`（比 1.2）**: クランプで効かず、かつ器も無い＝**触っても何も起きない**ので未着手のまま残した
  （目次ヘッダは正本比 +約9dp のズレが残る）。
- **Web グリッド題名（正本比 1.4・13sp 明朝）**: クランプで 19.0dp になり、蔵書グリッドカード 53dp に対し
  Web カードだけ 40dp へ縮んで**グリッド内で高さが割れる**＝比を入れる方が壊れる。除外が正しい。

⚠️ **比は Robolectric NATIVE・xhdpi・fontScale 1.0 で行箱の dp を実測してから採る**——
「正本が 1.4 だから 1.4 を当てれば正本どおり」は成り立たない。下限として効くかどうかが先。

⚠️ 器が固定寸法で行箱を包み込んでいる場合も対象外——蔵書グリッドの題名は `heightIn(min=32dp)` の
内側で 28sp が収まっており、継承を切ってもカード高は動かない（＝直しても見た目が変わらない）。

## 掃討の対象範囲（2026-09-03 裁定）

**器を持たない面は掃討の対象外**。目次のエラー画面（`NativeTableOfContentsScreen` のエラー表示）は
テキストを囲う器そのものが無く、行箱が版面をずらす相手が居ない＝`lineHeight` を触る理由が無い。
「全画面に同じ掃討を掛ける」と読むと、器のない面にまで固定値を撒いて逆に壊す。

対象になるのは〈器の高さが実装で決まっている面〉だけで、残る P/J/M の3面はいずれもそれに当たった。
