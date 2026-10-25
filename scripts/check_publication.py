#!/usr/bin/env python3
"""Focused guard for tracked files: local data, key material, size and doc links."""
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
DENIED_PARTS = {'.state', '.venv', '.test-output', '.reference', '.wheelhouse',
                '__pycache__', 'work', 'evidence', 'backup', 'backups'}
SECRET_PATTERNS = [
    re.compile(r'-----BEGIN (?:RSA |EC |OPENSSH |ENCRYPTED )?PRIVATE KEY-----'),
    re.compile(r'AIza[0-9A-Za-z_-]{35}'),
    re.compile(r'gh[pousr]_[A-Za-z0-9]{20,}'),
    re.compile(r'github_pat_[A-Za-z0-9_]{40,}'),
    re.compile(r'AKIA[0-9A-Z]{16}'),
    re.compile(r'[/]Users[/][^/\s]+[/]'),
]


def main():
    result = subprocess.run(['git', 'ls-files', '-z'], cwd=ROOT, check=True, capture_output=True)
    paths = [Path(p) for p in result.stdout.decode().split('\0') if p]
    if not paths:
        print('No tracked files to inspect; stage the intended public files first.', file=sys.stderr)
        return 1
    errors = []
    total = 0
    for rel in paths:
        path = ROOT / rel
        if path.is_symlink():
            errors.append(f'{rel}: symlinks are not expected in this distribution')
            continue
        if not path.is_file():
            errors.append(f'{rel}: tracked file is missing')
            continue
        if DENIED_PARTS.intersection(rel.parts) or rel.parts[0] in {'cleanyourtv', '.publication', 'photos'}:
            errors.append(f'{rel}: local/private path must not be tracked')
        if path.suffix.lower() in {'.pem', '.key', '.keystore', '.jks', '.apk', '.aab', '.zip', '.log'} or path.name.startswith('.env'):
            errors.append(f'{rel}: credentials, archives and generated binaries are excluded')
        if rel.as_posix().startswith(tuple('cinema-launcher/app/src/main/assets/' + p + '/' for p in ('art', 'characters', 'home'))):
            errors.append(f'{rel}: personal movie imagery must stay local')
        data = path.read_bytes()
        total += len(data)
        if len(data) > 8 * 1024 * 1024:
            errors.append(f'{rel}: exceeds the 8 MiB per-file limit')
        try:
            text = data.decode('utf-8')
        except UnicodeDecodeError:
            continue
        for pattern in SECRET_PATTERNS:
            for match in pattern.finditer(text):
                line = text.count('\n', 0, match.start()) + 1
                errors.append(f'{rel}:{line}: possible credential or personal absolute path (value redacted)')
        if path.suffix.lower() == '.md':
            for link in re.findall(r'!?\[[^\]]*\]\(([^)\s]+)\)', text):
                if '://' in link or link.startswith(('#', 'mailto:')):
                    continue
                target = link.split('#', 1)[0]
                if target and not (path.parent / target).exists():
                    errors.append(f'{rel}: missing local documentation target {target}')
    for error in errors:
        print(error, file=sys.stderr)
    print(f'Inspected {len(paths)} tracked files, {total / 1024 / 1024:.2f} MiB; {len(errors)} finding(s).')
    return bool(errors)


if __name__ == '__main__':
    raise SystemExit(main())
