"""Read-only inspection of a processed session ZIP (run 03 step 13).
Usage: python -I inspect_zip.py <zip> <extract_dir>   (extract_dir: new empty dir outside the repo; used for ffprobe)
Prints entries, manifest hash check, clip durations, examination.json summary."""
import sys, zipfile, hashlib, json, subprocess, os, wave
zp, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)
z = zipfile.ZipFile(zp)
names = [i.filename for i in z.infolist()]
print("ENTRIES (order):")
for i in z.infolist(): print(f"  {i.filename}  {i.file_size} bytes")
man = json.loads(z.read("manifest.json"))
print("manifest keys:", {k: (v if k != 'files' else len(v)) for k, v in man.items()})
listed = {f["path"]: f["sha256"] for f in man["files"]}
bad = [p for p in listed if hashlib.sha256(z.read(p)).hexdigest() != listed[p]]
print("hash mismatches:", bad, "| entries not in manifest:", [n for n in names if n not in listed and n != "manifest.json"], "| manifest last:", names[-1] == "manifest.json")
for n in names:
    if n.endswith(".wav"):
        p = os.path.join(out, os.path.basename(n)); open(p, "wb").write(z.read(n))
        w = wave.open(p); print(f"WAV {n}: {w.getframerate()} Hz, {w.getsampwidth()*8} bit, {w.getnchannels()} ch, {w.getnframes()/w.getframerate():.2f} s")
ex = json.loads(z.read("examination.json"))
print("examination.json:")
print(json.dumps(ex, indent=1, ensure_ascii=False))
print("participant.json:", z.read("participant.json").decode())
tl = json.loads(z.read("timeline_original.json"))
for e in tl["events"]: print("  ", e["type"], e["sampleOffset"], e["taskIndex"], e["repetition"], e["take"], e.get("reason", ""))
print("sessionId consistent:", man["sessionId"] == ex["sessionId"])
