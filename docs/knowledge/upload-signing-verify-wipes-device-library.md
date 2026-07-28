# upload 鍵署名の実機インストール検証は蔵書本文の全損を伴う（Auto Backup は DB だけの片肺復元）

- **事象（2026-07-29 検出）**: 実機（OPPO PGEM10）の全4蔵書が本文欠落。`files/novels/` ディレクトリ自体が不存在・Room は v21 で books 4行健在（`htmlDirPath` は `files/novels/<id>` を指すが実体なし）→ 全蔵書が「本文データがこの端末にありません」で読書・目次に入れない。
- **機序（推定・強い状況証拠）**: 同日の upload 鍵署名 universal APK の実機インストール検証。debug 署名と不一致のため **uninstall を伴う**→ 再インストール時に Auto Backup が層別設定（ADR 0015）どおり **Room DB のみ復元**し、本文 HTML（`files/novels/`）はバックアップ対象外＝復元されない。結果、「DB に本はあるが本文が無い」片肺状態が静かに成立する。
- **教訓**: **署名が変わる install 検証（debug⇄upload/release 鍵）は必ず uninstall を挟む＝実機蔵書の本文は全損する**。蔵書のある実機でやるなら、事前に本文込みバックアップ（debug ビルドなら `run-as` で `files/novels/` を tar 退避）を取るか、捨て端末/捨てプロファイルで行う。「Auto Backup があるから戻る」は半分だけ正しい（DB は戻る・本文は戻らない）——むしろ DB だけ戻ることで欠落が発見しにくくなる。
- **復旧の正道**: PDF 再取込（ユーザー操作）。Web 蔵書は再取込で復元可。
- **改善候補（handover 登録済み）**: 本文欠落検出時の再取込導線。v20 の `books.sourceUri`＋永続 READ 権限保持が土台としてあるため、PDF の自動再抽出の提案が可能（要裁定）。
