"""Read-only F-129 check for a session dir: interruptions[0].sampleOffset (examination.json)
vs the RECORDING_INTERRUPTED event sample (timeline.events.jsonl) vs frames in master part 1.
Usage: python check_interruption.py <session dir>   (prints no IDs; run output is redacted of 32-hex ids)"""
import json, os, struct, sys, glob, re
d = sys.argv[1]
ex = json.load(open(os.path.join(d, 'examination.json'), encoding='utf-8'))
ints = ex.get('interruptions') or ex.get('processing', {}).get('interruptions') or []
print('interruptions:', [{k: v for k, v in i.items()} for i in ints])
ev = []
for line in open(os.path.join(d, 'timeline.events.jsonl'), encoding='utf-8'):
    line = line.strip()
    if 'INTERRUPT' in line or 'RESUM' in line:
        ev.append(line)
print('events:'); [print(' ', e) for e in ev]
for p in sorted(glob.glob(os.path.join(d, 'master', '*.wav'))):
    b = open(p, 'rb').read(64)
    i = b.find(b'data'); size = struct.unpack('<I', b[i+4:i+8])[0]
    ch, = struct.unpack('<H', b[22:24]); bits, = struct.unpack('<H', b[34:36])
    ba = ch * bits // 8
    print(os.path.basename(p), 'data bytes', size, 'blockAlign', ba, 'frames', size // ba, 'filesize', os.path.getsize(p))
