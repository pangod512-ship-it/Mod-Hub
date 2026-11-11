"""Populate a Minecraft asset cache from verified local objects or Mojang downloads."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
from pathlib import Path
import re
from urllib.request import urlopen


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--index', type=Path, required=True)
    parser.add_argument('--objects', type=Path, required=True)
    parser.add_argument('--cache', type=Path, action='append', default=[])
    args = parser.parse_args()
    assets = json.loads(args.index.read_text(encoding='utf-8'))['objects']
    hashes = {entry['hash']: entry['size'] for entry in assets.values()}
    if not all(re.fullmatch('[0-9a-f]{40}', value) for value in hashes):
        raise ValueError('Invalid asset hash in index')

    def valid(path, digest, size):
        return (path.is_file() and path.stat().st_size == size
                and hashlib.sha1(path.read_bytes()).hexdigest() == digest)

    def prepare(item):
        digest, size = item
        relative = Path(digest[:2]) / digest
        target = args.objects / relative
        if valid(target, digest, size):
            return 'cached'
        target.parent.mkdir(parents=True, exist_ok=True)
        data = None
        for cache in args.cache:
            candidate = cache / relative
            if valid(candidate, digest, size):
                data = candidate.read_bytes()
                break
        result = 'copied'
        if data is None:
            result = 'downloaded'
            for attempt in range(3):
                try:
                    with urlopen(f'https://resources.download.minecraft.net/{relative.as_posix()}', timeout=20) as response:
                        data = response.read()
                    if len(data) != size or hashlib.sha1(data).hexdigest() != digest:
                        raise ValueError(f'Asset hash mismatch: {digest}')
                    break
                except Exception:
                    if attempt == 2:
                        raise
        temporary = target.with_suffix('.modhub-download')
        temporary.write_bytes(data)
        temporary.replace(target)
        return result

    counts = {'cached': 0, 'copied': 0, 'downloaded': 0}
    with ThreadPoolExecutor(max_workers=4) as workers:
        for index, result in enumerate(workers.map(prepare, hashes.items()), 1):
            counts[result] += 1
            if index % 500 == 0:
                print(f'Assets checked: {index}/{len(hashes)}', flush=True)
    print(json.dumps({'verified': len(hashes), **counts}), flush=True)


if __name__ == '__main__':
    main()
