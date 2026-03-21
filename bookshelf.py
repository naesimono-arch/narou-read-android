# coding: utf-8
import os
import json
import uuid
import re
import tempfile
import shutil
import threading
from pathlib import Path
from flask import Flask, send_file, send_from_directory, request, jsonify

import app as novel_app_engine

BASE_DIR = Path(__file__).resolve().parent
NOVEL_APP_DIR = BASE_DIR / "novel_app"
LIBRARY_DIR = BASE_DIR / "library"
MANIFEST_PATH = LIBRARY_DIR / "books.json"
PROGRESS_PATH = LIBRARY_DIR / "progress.json"
LIBRARY_INDEX_HTML = BASE_DIR / "index.html"

app = Flask(__name__, static_folder=None)

# 排他制御用のロックオブジェクトを追加
library_lock = threading.Lock()
progress_lock = threading.Lock()

# ジョブ管理
jobs = {}
jobs_lock = threading.Lock()
JOB_TTL = 600  # 完了後10分でメモリから削除

def ensure_library():
    LIBRARY_DIR.mkdir(exist_ok=True)
    if not MANIFEST_PATH.exists():
        MANIFEST_PATH.write_text("[]", encoding="utf-8")

def load_books():
    ensure_library()
    with library_lock:
        with open(MANIFEST_PATH, "r", encoding="utf-8") as f:
            return json.load(f)

def save_books(books):
    ensure_library()
    with library_lock:
        with open(MANIFEST_PATH, "w", encoding="utf-8") as f:
            json.dump(books, f, ensure_ascii=False, indent=2)

def load_progress():
    if not PROGRESS_PATH.exists():
        return {}
    try:
        with progress_lock:
            with open(PROGRESS_PATH, "r", encoding="utf-8") as f:
                return json.load(f)
    except Exception:
        return {}

def save_progress(progress):
    ensure_library()
    with progress_lock:
        with open(PROGRESS_PATH, "w", encoding="utf-8") as f:
            json.dump(progress, f, ensure_ascii=False, indent=2)

def update_book_title(book_id, new_title):
    books = load_books()
    for b in books:
        if b.get("id") == book_id:
            b["title"] = new_title
            save_books(books)
            return True
    return False

def remove_book(book_id):
    books = load_books()
    new_books = [b for b in books if b.get("id") != book_id]
    if len(new_books) == len(books):
        return False, "本が見つかりません"

    target_dir = NOVEL_APP_DIR / book_id
    try:
        if target_dir.exists() and target_dir.is_dir():
            shutil.rmtree(target_dir)
    except Exception as e:
        return False, f"削除に失敗しました: {e}"

    save_books(new_books)
    return True, None

def scan_existing_novel_app():
    books = load_books()
    ids = {b["id"] for b in books}
    changed = False

    if NOVEL_APP_DIR.exists():
        for sub in sorted(NOVEL_APP_DIR.iterdir()):
            if sub.is_dir() and not sub.name.startswith(".") and sub.name != "__pycache__":
                idx = sub / "index.html"
                if idx.exists() and sub.name not in ids:
                    title = "作品"
                    try:
                        raw = idx.read_text(encoding="utf-8")
                        m = re.search(r"<h1[^>]{0,}>([^<]+)</h1>", raw)
                        if m:
                            title = m.group(1).strip() or title
                    except Exception:
                        pass
                    books.append({"id": sub.name, "title": title, "path": f"novel_app/{sub.name}"})
                    ids.add(sub.name)
                    changed = True

    if changed:
        save_books(books)
    return books

def get_book_path(book_id):
    books = load_books()
    for b in books:
        if b["id"] == book_id:
            return BASE_DIR / b["path"]
    return BASE_DIR / "novel_app" / book_id

@app.route("/")
def index():
    if LIBRARY_INDEX_HTML.exists():
        return send_file(LIBRARY_INDEX_HTML)
    return "<h1>本棚</h1><p>index.html を配置してください。</p>", 404

@app.route("/api/books")
def api_books():
    books = scan_existing_novel_app()
    progress = load_progress()
    for b in books:
        b["last_read"] = progress.get(b["id"], "index.html")
    return jsonify(books)

def _set_progress(job_id, percent, phase):
    with jobs_lock:
        job = jobs.get(job_id)
        if job is None:
            return
        job["percent"] = max(job["percent"], percent)
        job["phase"] = phase

def _expire_job(job_id):
    with jobs_lock:
        jobs.pop(job_id, None)

def _run_job(job_id, book_id, tmp_path):
    def progress_callback(percent, phase):
        _set_progress(job_id, percent, phase)
    try:
        real_title = novel_app_engine.process_pdf(tmp_path, book_id,
                                                   progress_callback=progress_callback)
        books = load_books()
        books.append({"id": book_id, "title": real_title, "path": f"novel_app/{book_id}"})
        save_books(books)
        with jobs_lock:
            jobs[job_id]["status"] = "done"
            jobs[job_id]["percent"] = 100
            jobs[job_id]["book_id"] = book_id
    except Exception as e:
        target_dir = NOVEL_APP_DIR / book_id
        try:
            if target_dir.exists():
                shutil.rmtree(target_dir)
        except Exception:
            pass
        with jobs_lock:
            jobs[job_id]["status"] = "error"
            jobs[job_id]["error"] = str(e)
    finally:
        try:
            os.unlink(tmp_path)
        except Exception:
            pass
        threading.Timer(JOB_TTL, _expire_job, args=(job_id,)).start()

@app.route("/api/job/<job_id>")
def api_job(job_id):
    with jobs_lock:
        job = jobs.get(job_id)
    if job is None:
        return jsonify({"ok": False, "error": "ジョブが見つかりません"}), 404
    return jsonify({"ok": True, **job})

@app.route("/api/add", methods=["POST"])
def api_add():
    if "pdf" not in request.files:
        return jsonify({"ok": False, "error": "PDFファイルを選択してください"}), 400
    f = request.files["pdf"]
    if not f.filename or not f.filename.lower().endswith(".pdf"):
        return jsonify({"ok": False, "error": "PDFファイルを選択してください"}), 400

    book_id = str(uuid.uuid4())[:8]
    job_id = str(uuid.uuid4())[:8]

    with tempfile.NamedTemporaryFile(suffix=".pdf", delete=False) as tmp:
        f.save(tmp.name)
        tmp_path = tmp.name

    with jobs_lock:
        jobs[job_id] = {
            "status": "running",
            "percent": 0,
            "phase": "",
            "book_id": None,
            "error": None
        }

    threading.Thread(target=_run_job, args=(job_id, book_id, tmp_path), daemon=True).start()
    return jsonify({"ok": True, "job_id": job_id})

@app.route("/api/progress", methods=["POST"])
def api_progress():
    data = request.get_json(silent=True) or {}
    book_id = (data.get("book_id") or "").strip()
    last_read = (data.get("last_read") or "").strip()

    if not book_id or not last_read:
        return jsonify({"ok": False, "error": "不正なリクエスト"}), 400

    from pathlib import PurePosixPath
    safe_name = PurePosixPath(last_read).name
    if not safe_name or safe_name != last_read:
        return jsonify({"ok": False, "error": "不正なファイル名"}), 400

    try:
        progress = load_progress()
        progress[book_id] = safe_name
        save_progress(progress)
        return jsonify({"ok": True})
    except Exception as e:
        return jsonify({"ok": False, "error": str(e)}), 500

@app.route("/api/rename", methods=["POST"])
def api_rename():
    data = request.get_json(silent=True) or {}
    book_id = (data.get("id") or "").strip()
    title = (data.get("title") or "").strip()
    if not book_id or not title:
        return jsonify({"ok": False, "error": "不足している情報があります"}), 400
    
    ok = update_book_title(book_id, title)
    if ok:
        return jsonify({"ok": True})
    else:
        return jsonify({"ok": False, "error": "失敗"}), 404

@app.route("/api/delete", methods=["POST"])
def api_delete():
    data = request.get_json(silent=True) or {}
    book_id = (data.get("id") or "").strip()
    
    ok, err = remove_book(book_id)
    if ok:
        return jsonify({"ok": True})
    else:
        return jsonify({"ok": False, "error": err}), 400

@app.route("/book/<book_id>/")
@app.route("/book/<book_id>/<path:filename>")
def serve_book(book_id, filename=None):
    folder = get_book_path(book_id)
    if not folder.exists():
        return "Not Found", 404
    if filename is None:
        filename = "index.html"
    
    path = (folder / filename).resolve()
    try:
        path.relative_to(folder.resolve())
    except (ValueError, AttributeError):
        return "Not Found", 404
        
    if not path.is_file():
        return "Not Found", 404
    return send_from_directory(str(folder), filename)

def main():
    ensure_library()
    scan_existing_novel_app()
    port = int(os.environ.get("PORT", 5000))
    app.run(host="0.0.0.0", port=port, debug=False, threaded=True)

if __name__ == "__main__":
    main()