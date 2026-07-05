# 0002. UseCase 層（Clean Architecture 的な中間層）不採用

> 旧 `task_diary.md` §22（意図的に採用しなかったアーキテクチャとその理由）

**Status**: 採用済み（不採用の判断）

## 判断

**UseCase 層（Clean Architecture 的な中間層）は不採用**。ViewModel → Repository 直結の素直な MVVM を採用。

## 理由

抽出ロジックの実体が Repository の外（`pdf/` パッケージの `PdfBookExtractor` facade 以下）に凝集しており、UseCase 層を設けても `repository.xxx()` を呼ぶだけの薄いラッパーになる。中間層が値を生まないため、層を増やさず直結する。

> 判断当時はロジックの実体が Python（`app.py` 以下・Chaquopy）側にあった。2026-07-05 Phase 5 で Kotlin `pdf/` へ全面移植・Python 撤去したが、「ロジックは Repository 外に凝集し中間層が値を生まない」という構図は不変のため、本判断は引き続き有効。

## 関連

- [0001 Hilt 不採用](0001-no-hilt.md)
