"""Record released Minecraft versions and official loader artifacts for the requested branches."""
import json
import re
from pathlib import Path
import urllib.request
import xml.etree.ElementTree as ET


def fetch(url):
    request = urllib.request.Request(url, headers={'User-Agent': 'ModHub-development/1.1.1'})
    with urllib.request.urlopen(request, timeout=45) as response:
        return response.read()


def loader_versions(url):
    return [node.text for node in ET.fromstring(fetch(url)).findall('./versioning/versions/version')]


def version_order(value):
    return tuple(int(part) for part in re.findall(r'\d+', value))


def main():
    out = Path('adapters/modern-targets.json')
    previous = {row['minecraft']: row for row in json.loads(out.read_text(encoding='utf-8'))} if out.exists() else {}
    manifest = json.loads(fetch('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'))
    releases = [v for v in manifest['versions'] if v['type'] == 'release' and
                (v['id'] in ('1.20', '1.21') or v['id'].startswith(('1.20.', '1.21.')))]
    forge = loader_versions('https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml')
    neo = loader_versions('https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml')
    old_neo = loader_versions('https://maven.neoforged.net/releases/net/neoforged/forge/maven-metadata.xml')
    rows = []
    for release in reversed(releases):
        mc = release['id']
        f = [v for v in forge if v.startswith(mc + '-')]
        parts = mc.split('.')
        prefix = parts[1] + '.' + (parts[2] if len(parts) > 2 else '0') + '.'
        n = [v for v in neo if v.startswith(prefix)]
        if mc == '1.20.1':
            n = [v for v in old_neo if v.startswith(mc + '-')]
        stable = [v for v in n if 'beta' not in v and 'alpha' not in v]
        row = {'minecraft': mc, 'forge': max(f, key=version_order) if f else None,
               'neoforge': max(stable or n, key=version_order) if n else None,
               'java': 21 if tuple(map(int, parts)) >= (1, 20, 5) else 17}
        old = previous.get(mc, {})
        row['validation'] = {loader: (old.get('validation', {}).get(loader, 'pending_implementation')
                                      if old.get(loader) == row[loader] else 'pending_implementation')
                             if row[loader] else 'unavailable' for loader in ('forge', 'neoforge')}
        rows.append(row)
    out.write_text(json.dumps(rows, indent=2) + '\n', encoding='utf-8')
    latest = releases[0]
    version = json.loads(fetch(latest['url']))
    index = json.loads(fetch(version['assetIndex']['url']))
    languages = sorted(name for name in index['objects'] if '/lang/es_' in name and name.endswith('.json'))
    print(json.dumps({'targets': rows, 'spanish_assets': languages}, indent=2))


if __name__ == '__main__':
    main()
