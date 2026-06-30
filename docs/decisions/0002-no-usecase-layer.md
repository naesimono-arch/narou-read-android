# 0002. UseCase 層（Clean Architecture 的な中間層）不採用

> 旧 `task_diary.md` §22（意図的に採用しなかったアーキテクチャとその理由）

**Status**: 採用済み（不採用の判断）

## 判断

**UseCase 層（Clean Architecture 的な中間層）は不採用**。ViewModel → Repository 直結の素直な MVVM を採用。

## 理由

ビジネスロジックの大部分が Python（`app.py` 以下）にカプセル化されており、Kotlin は UseCase 層を設けても `repository.xxx()` を呼ぶだけの薄いラッパーになる。中間層が値を生まないため、層を増やさず直結する。

## 関連

- [0001 Hilt 不採用](0001-no-hilt.md)
