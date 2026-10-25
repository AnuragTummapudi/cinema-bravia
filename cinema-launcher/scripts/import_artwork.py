#!/usr/bin/env python3
"""Copy owner-supplied licensed artwork into ignored local build assets. No downloads."""
import argparse
import json
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('--dry-run', action='store_true')
    args = parser.parse_args()
    source = args.source.resolve(strict=True)
    if not source.is_dir():
        parser.error('Source must be a directory.')
    assets = ROOT / 'app/src/main/assets'
    catalog = json.loads((assets / 'catalog.json').read_text())
    paths = {catalog.get('homeArtwork', '')}
    for movie in catalog['movies']:
        paths.update(movie.get(key, '') for key in ('art', 'characterArt'))
    copied = 0
    for value in sorted(paths - {''}):
        rel = Path(value)
        if rel.is_absolute() or '..' in rel.parts or rel.parts[0] not in {'art', 'characters', 'home'}:
            raise ValueError('Unsafe catalogue artwork path.')
        src = source / rel
        if not src.is_file():
            continue
        if src.is_symlink() or source not in src.resolve().parents:
            raise ValueError('Artwork must be a regular file within the supplied directory.')
        dst = assets / rel
        if dst.exists():
            print(f'Keep existing: {rel}')
            continue
        if dst.parent.is_symlink() or assets.resolve() not in dst.parent.resolve().parents:
            raise ValueError('Unsafe destination directory.')
        print(f'{"Would copy" if args.dry_run else "Copy"}: {rel}')
        if not args.dry_run:
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(src, dst)
        copied += 1
    print(f'{copied} matching artwork file(s){" available to import" if args.dry_run else " imported"}.')


if __name__ == '__main__':
    main()
