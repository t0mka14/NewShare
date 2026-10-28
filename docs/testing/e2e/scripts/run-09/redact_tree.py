#!/usr/bin/env python3
"""Redact a semantic-tree dump in place: drops editableText/text/contentDescription values of nodes that look like a token or an
installation ID (long hex/base64-ish strings, or UUIDs) and any text field with 'editableText'. Usage: redact_tree.py <file>..."""
import json, sys, re
pat = re.compile(r'[A-Za-z0-9_\-]{24,}')
def walk(n):
    if 'editableText' in n: n['editableText'] = '<redacted>'
    for k in ('text', 'contentDescription'):
        if isinstance(n.get(k), str) and pat.search(n[k]): n[k] = pat.sub('<redacted>', n[k])
    for c in n.get('children', []): walk(c)
for f in sys.argv[1:]:
    t = json.load(open(f, encoding='utf-8-sig'))
    for r in (t if isinstance(t, list) else [t]): walk(r)
    json.dump(t, open(f, 'w', encoding='utf-8'), ensure_ascii=False)
