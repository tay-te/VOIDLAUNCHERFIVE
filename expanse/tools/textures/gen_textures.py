#!/usr/bin/env python3
"""VOID Expanse texture generator - regenerates every texture deterministically.

Usage:
    python3 tools/textures/gen_textures.py                 # write all textures + icon + preview
    python3 tools/textures/gen_textures.py --preview PATH  # custom preview location
    python3 tools/textures/gen_textures.py --only wood     # just one group (dev)

Each texture gets its own RNG seeded from a CRC of its name, so tweaking one
texture never reshuffles the others.
"""
import argparse
import os
import sys
import zlib

import numpy as np

sys.dont_write_bytecode = True  # keep the source tree clean
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import icon  # noqa: E402
import items  # noqa: E402
import plants  # noqa: E402
import stone  # noqa: E402
import woodset  # noqa: E402
from preview import build_sheet  # noqa: E402
from texlib import save  # noqa: E402


ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'expanse')
DEFAULT_PREVIEW = ('/tmp/claude-0/-home-user-VOIDLAUNCHERFIVE/'
                   '79d59971-c67c-5b44-8e8b-b806fe12b6d4/scratchpad/texture_preview.png')
GLOBAL_SEED = 0x5EED


def rng_for(name):
    return np.random.default_rng(zlib.crc32(name.encode()) ^ GLOBAL_SEED)


GROUPS = {
    'wood': woodset.generate,
    'stone': stone.generate,
    'plants': plants.generate,
    'items': items.generate,
    'icon': icon.generate,
}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--only', choices=sorted(GROUPS), action='append')
    ap.add_argument('--preview', default=DEFAULT_PREVIEW)
    ap.add_argument('--no-write', action='store_true', help='only build the preview')
    ap.add_argument('--match', action='append', help='(dev) preview only labels containing this')
    ap.add_argument('--cols', type=int, default=4)
    args = ap.parse_args()

    groups = args.only or list(GROUPS)
    entries = []
    written = []
    for g in groups:
        for rel, img, tileable in GROUPS[g](rng_for):
            if not args.no_write:
                path = os.path.join(ASSETS, rel)
                os.makedirs(os.path.dirname(path), exist_ok=True)
                save(img, path)
                written.append(path)
            label = os.path.splitext(rel)[0].replace('textures/', '')
            if args.match and not any(m in label for m in args.match):
                continue
            entries.append((label, img, tileable))
    if args.preview:
        os.makedirs(os.path.dirname(args.preview), exist_ok=True)
        build_sheet(entries, args.preview, cols=args.cols,
                    title='VOID Expanse textures  (8x, tileables also shown 3x3 at 4x)')
    print(f'wrote {len(written)} textures; preview -> {args.preview}')


if __name__ == '__main__':
    main()
