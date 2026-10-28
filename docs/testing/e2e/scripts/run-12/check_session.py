#!/usr/bin/env python3
"""Usage: check_session.py <session dir>. Prints processing status, skippedTakes, clipFile/video per task,
ffprobe duration of master parts and clips, and the ZIP entries. Read-only."""
import json, sys, glob, os, subprocess, zipfile
d = sys.argv[1]
e = json.load(open(os.path.join(d, 'examination.json'), encoding='utf-8-sig'))
print('processing', e.get('processing'), 'skippedTakes', e.get('skippedTakes'))
for t in e.get('tasks', []):
    print(t.get('clipFile'), t.get('videoFile'))
def dur(f):
    return subprocess.run(['ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', f], capture_output=True, text=True).stdout.strip()
for f in sorted(glob.glob(os.path.join(d, 'master', '*.wav')) + glob.glob(os.path.join(d, 'clips', '*'))):
    print(os.path.relpath(f, d), dur(f))
for z in glob.glob(os.path.join(d, 'archive', '*.zip')):
    for i in zipfile.ZipFile(z).infolist(): print('zip', i.filename, i.file_size)
