#!/usr/bin/env python3
"""Layout checks C1-C3 on semantic-tree dumps.
Usage: tree_check.py <tree dir> <screen> [--sizes W1,W2,...] [--baseline W4]
Files: <dir>/<screen>_<size>.json (tree as dumped) and optional <dir>/<screen>_<size>_s<n>.json (after scrolling).
A node is a control if it has no children and has text, contentDescription or an action.
C1: control inside the client area [0,w]x[0,h] in at least one dump of that size (else UNREACHABLE).
C2: height (or, for buttons, width) < 50% of the baseline (same key: testTag, else text) height, or <6 px high/wide.
C3: two controls intersect by more than 2 px in both axes (containment reported as 'nested', informational).
Settings dumps must be redacted first (see redact_tree.py)."""
import json, sys, glob, os, re, argparse

def flat(n, out):
    out.append(n)
    for c in n.get('children', []): flat(c, out)

def load(path):
    t = json.load(open(path, encoding='utf-8-sig'))
    roots = t if isinstance(t, list) else [t]
    nodes = []
    for r in roots: flat(r, nodes)
    w = roots[0]['bounds']['width']; h = roots[0]['bounds']['height']
    return nodes, w, h

def controls(nodes):
    res = []
    for n in nodes:
        if n.get('children'): continue
        if not (n.get('text') or n.get('contentDescription') or n.get('actions') or n.get('testTag')): continue
        if not n.get('bounds'): continue
        res.append(n)
    return res

def key(n):
    return n.get('testTag') or (n.get('text') or n.get('contentDescription') or str(n['id']))[:40]

def inside(b, w, h):
    return b['width'] > 0 and b['height'] > 0 and b['x'] >= -1 and b['y'] >= -1 and b['x'] + b['width'] <= w + 1 and b['y'] + b['height'] <= h + 1

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('dir'); ap.add_argument('screen')
    ap.add_argument('--sizes', default='W1,W2,W3,W4,W5,W6'); ap.add_argument('--baseline', default='W4')
    a = ap.parse_args()
    base = {}
    bp = os.path.join(a.dir, f'{a.screen}_{a.baseline}.json')
    if os.path.exists(bp):
        for n in controls(load(bp)[0]): base[key(n)] = n['bounds']
    for s in a.sizes.split(','):
        files = sorted(glob.glob(os.path.join(a.dir, f'{a.screen}_{s}.json')) + glob.glob(os.path.join(a.dir, f'{a.screen}_{s}_s*.json')))
        if not files: print(f'{a.screen} {s}: no dump'); continue
        dumps = [load(f) for f in files]
        w, h = dumps[0][1], dumps[0][2]
        seen = {}  # key -> best bounds (inside if any)
        for nodes, _, _ in dumps:
            for n in controls(nodes):
                k = key(n); b = n['bounds']
                if k not in seen or (inside(b, w, h) and (not inside(seen[k], w, h) or b['height'] > seen[k]['height'])): seen[k] = b
        probs = []
        for k, b in seen.items():
            if not inside(b, w, h): probs.append(f'C1 UNREACHABLE/clipped {k!r} {b}')
            if b['width'] < 6 or b['height'] < 6: probs.append(f'C2 tiny {k!r} {b}')
            if k in base and base[k]['height'] > 0 and b['height'] < 0.5 * base[k]['height']:
                probs.append(f'C2 squashed {k!r} h={b["height"]} vs {a.baseline} h={base[k]["height"]}')
            if k in base and base[k]['width'] > 0 and b['width'] < 0.5 * base[k]['width'] and (k.startswith('calibration') or 'Button' in k or 'button' in k.lower()):
                probs.append(f'C2 narrow {k!r} w={b["width"]} vs {a.baseline} w={base[k]["width"]}')
        for nodes, _, _ in dumps:  # overlaps within one dump
            cs = controls(nodes)
            for i in range(len(cs)):
                for j in range(i + 1, len(cs)):
                    p, q = cs[i]['bounds'], cs[j]['bounds']
                    ox = min(p['x'] + p['width'], q['x'] + q['width']) - max(p['x'], q['x'])
                    oy = min(p['y'] + p['height'], q['y'] + q['height']) - max(p['y'], q['y'])
                    if ox > 2 and oy > 2:
                        nested = (p['x'] <= q['x'] and p['y'] <= q['y'] and p['x']+p['width'] >= q['x']+q['width'] and p['y']+p['height'] >= q['y']+q['height']) or \
                                 (q['x'] <= p['x'] and q['y'] <= p['y'] and q['x']+q['width'] >= p['x']+p['width'] and q['y']+q['height'] >= p['y']+p['height'])
                        probs.append(f'C3 {"nested" if nested else "OVERLAP"} {key(cs[i])!r} {p} x {key(cs[j])!r} {q}')
        probs = sorted(set(probs))
        print(f'{a.screen} {s} client={w}x{h} dumps={len(dumps)} controls={len(seen)}: ' + ('pass' if not [p for p in probs if 'nested' not in p] else 'FAIL'))
        for p in probs: print('   ', p)
main()
