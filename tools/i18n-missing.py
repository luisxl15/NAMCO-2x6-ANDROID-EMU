#!/usr/bin/env python3
"""Which strings the interface will show in English.

The Android launcher keeps its English strings inline in I18n.kt and every other language in
assets/i18n/*.json. A key with no entry in a translation falls back to English at lookup time --
silently, and per string, so the result is a Portuguese settings screen with an English paragraph
in the middle of it. Nothing fails; it just reads badly, and only on the screen nobody opened.

That is exactly what an upstream merge produces: upstream adds strings, the machine-translated
tables catch up later or never. So run this after one.

    tools/i18n-missing.py              # the language this fork ships in
    tools/i18n-missing.py es fr        # any other

Prints the key and its English text, so the output can be translated as it stands.
"""
import io
import json
import os
import re
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..',
                    'platforms', 'android', 'app', 'src', 'main')
DEFAULT_LANGS = ['pt-BR']


def english():
    """The EN table, read out of the Kotlin source.

    Values are written as `"key" to "text"`, sometimes continued over several lines with `+`.
    Read each key's whole block and join the quoted pieces back together.
    """
    src = io.open(os.path.join(ROOT, 'java/com/armsx2/i18n/I18n.kt'), encoding='utf-8').read()
    lines = src.split('\n')
    starts = []
    for i, line in enumerate(lines):
        m = re.match(r'^\s*"([a-zA-Z0-9._-]+)" to(\s|$)(.*)$', line)
        if m:
            starts.append((i, m.group(1), m.group(3)))
    out = {}
    for n, (i, key, first) in enumerate(starts):
        end = starts[n + 1][0] if n + 1 < len(starts) else len(lines)
        chunk = first + ' ' + ' '.join(l.strip() for l in lines[i + 1:end])
        parts = re.findall(r'"((?:[^"\\]|\\.)*)"', chunk)
        out[key] = ''.join(parts) if parts else ''
    return out


def main(langs):
    en = english()
    worst = 0
    for lang in langs:
        path = os.path.join(ROOT, 'assets/i18n/%s.json' % lang)
        if not os.path.isfile(path):
            print('%s: sem arquivo de traducao' % lang)
            continue
        table = json.load(io.open(path, encoding='utf-8'))
        missing = [k for k in sorted(en) if not str(table.get(k, '')).strip()]
        worst = max(worst, len(missing))
        print('\n%s: %d de %d chaves cairiam para o ingles' % (lang, len(missing), len(en)))
        for key in missing:
            print('  %-44s %s' % (key, en[key][:100]))
    # Nonzero when something is untranslated, so this can gate a check if that is ever wanted.
    return 1 if worst else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:] or DEFAULT_LANGS))
