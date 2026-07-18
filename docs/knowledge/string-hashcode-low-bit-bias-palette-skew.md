# String.hashCode() % N のパレット割当は下位ビット偏りで色が偏る

**症状**: bookId 等の短い文字列 id から `hashCode() % 4` でパレットを選ぶと、実データで特定パレットに集中する（実測: J扉6作中4作が同系＝2026-07-17 実機所見[med]）。

**機序**: `String.hashCode`（31進多項式）は下位ビットの雪崩性が弱く、似た形式の id（連番・共通接頭辞など）では `% 小さいN` が同値に固まりやすい。

**対処**: `% N` の前に **fmix32（Murmur3 finalizer）等のビット撹拌**を挟む（`h ^= h >>> 16; h *= 0x85EBCA6B; …`）。純粋な決定的変換なので**並び替え不変（id のみ依存）は維持**される。分散はテストで固定（実 id 風サンプル群で全パレット出現を assert）。注意: Kotlin では 0x85EBCA6B 等は Int リテラル範囲外＝`.toInt()` か負数リテラルで書く（実装時に符号付きリテラル誤りで1敗）。

**横展開候補**: 同型の hashCode 直割当は M の学名色 `idColorFor`（SeizuIdPalette 4色）・P のラベル色 `labelColorFor`（w1-4）にもある。偏りが目視で気になったら同じ fmix32 を適用（handover 記載）。

一次情報: `ui/skins/j/BookshelfPortalJ.kt` の `fmix32`/`portalDoorPaletteFor`（テスト=BookshelfPortalJTest「4世界全出現」）。
