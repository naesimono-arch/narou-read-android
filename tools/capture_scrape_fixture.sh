#!/usr/bin/env bash
# カクヨム抽出の fixture ゴールデン（破損監視の核）を撮り直すための保守スクリプト。
#
# いつ使うか: `KakuyomuGoldenTest` が赤くなった＝カクヨムが HTML/JSON 構造を変えたとき。
#   ① 本スクリプトで最新スナップショットを取得 → ② 差分を見て KakuyomuAdapter のセレクタ/JSON経路を追随
#   → ③ 期待値（件数・先頭章題）を実値へ更新 → ④ testDebugUnitTest 緑を確認。
#
# 設計の正本＝docs/decisions/0024／構造の正本＝.claude/plans/scraping-foundation-design-2026-07-20.md。
# robots: /works/{id}/episodes/{id} は許容（/read$ のみ Disallow）。Crawl-delay:1 尊重で --sleep を挟む。
#
# 使い方:
#   tools/capture_scrape_fixture.sh <workId> <episodeId>
# 例（現行 fixture の作品）:
#   tools/capture_scrape_fixture.sh 16816927859675616240 16816927859675631302
set -euo pipefail

UA="NovelReader-Android/1.0"
DEST="android/app/src/test/resources/scrape_fixtures/kakuyomu"

if [ "$#" -ne 2 ]; then
  echo "usage: $0 <workId> <episodeId>" >&2
  echo "  現行 fixture: workId=16816927859675616240 episodeId=16816927859675631302" >&2
  exit 2
fi
WID="$1"; EID="$2"

mkdir -p "$DEST"
toc="$DEST/toc_${WID}.html"
ep="$DEST/episode_${EID}.html"

echo "[1/3] robots.txt を再確認（本文パスが Disallow に入っていないか）"
curl -fsSL -A "$UA" --max-time 20 "https://kakuyomu.jp/robots.txt" | sed -n '1,12p'

echo "[2/3] TOC を取得 -> $toc"
curl -fsSL -A "$UA" --max-time 30 "https://kakuyomu.jp/works/${WID}" -o "$toc"
sleep 1  # Crawl-delay:1

echo "[3/3] エピソードを取得 -> $ep"
curl -fsSL -A "$UA" --max-time 30 "https://kakuyomu.jp/works/${WID}/episodes/${EID}" -o "$ep"

echo "done. bytes:"; wc -c "$toc" "$ep"
echo
echo "次: 期待値の実測値を確認して KakuyomuGoldenTest を更新する。"
echo "  エピソード総数の目安（apollo ストアの Episode 件数）:"
grep -o '"__typename":"Episode"' "$toc" | wc -l
echo "  ※ ゴールデンの断定値（件数・先頭章題・URL）は撮り直しごとに実値へ更新すること。"
