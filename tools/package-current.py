"""Create a local delivery only after every target passes artifact and runtime checks."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import runpy
from zipfile import ZipFile, ZIP_DEFLATED

ROOT = Path(__file__).resolve().parents[1]
VERSION = '1.1.1'
FULL_PACKS = {('1.12.2', 'forge'): 'calamity', ('1.20.1', 'forge'): 'vanilla'}
verify = runpy.run_path(str(ROOT / 'tools/verify-artifacts.py'))['verify']


def modern_module(mc, loader):
    if loader == 'neoforge' and mc in ('1.20.2', '1.20.3', '1.20.5'):
        return 'neoforge-legacy', mc
    if loader == 'neoforge' and mc != '1.20.1':
        return 'neoforge', mc
    if loader == 'forge' and tuple(map(int, mc.split('.'))) >= (1, 20, 6):
        return 'forge-next', f'{mc}-{loader}'
    return 'modern', f'{mc}-{loader}'


def inputs():
    legacy = json.loads((ROOT / 'adapters/targets.json').read_text(encoding='utf-8-sig'))
    for mc in legacy:
        folder = ROOT / f'adapters/forge-{mc}/build'
        yield mc, 'forge', folder / 'libs', folder / 'port-probe/results.txt'
    catalog = json.loads((ROOT / 'adapters/modern-targets.json').read_text(encoding='utf-8-sig'))
    for row in catalog:
        for loader in ('forge', 'neoforge'):
            if not row[loader]:
                continue
            mc = row['minecraft']
            module, context = modern_module(mc, loader)
            folder = ROOT / f'adapters/{module}/build/{context}'
            yield mc, loader, folder / 'libs', folder / 'runtime-probe/results.txt'


def public_sources():
    roots = ['src/main', 'src/smoke', 'core/src', 'shared/src',
             'third_party/forge-mdk', 'gradle/wrapper', 'adapters/probes/src']
    files = ['README.md', 'LICENSE.txt', 'CREDITS.txt', '.gitignore', 'build.gradle',
             'settings.gradle', 'gradle.properties', 'gradlew', 'gradlew.bat',
             'gradle-java17.cmd', 'core/build.gradle', 'core/settings.gradle',
             'adapters/targets.json', 'adapters/modern-targets.json', 'shared/translations.json']
    legacy = json.loads((ROOT / 'adapters/targets.json').read_text(encoding='utf-8-sig'))
    for module in [f'forge-{mc}' for mc in legacy] + ['modern', 'neoforge', 'neoforge-legacy', 'forge-next']:
        folder = f'adapters/{module}'
        roots.extend([f'{folder}/src', f'{folder}/gradle/wrapper'])
        files.extend(f'{folder}/{name}' for name in
                     ['build.gradle', 'settings.gradle', 'gradle.properties', 'gradlew', 'gradlew.bat'])
    paths = {ROOT / name for name in files if (ROOT / name).is_file()}
    paths.update((ROOT / 'gradle').glob('*.gradle'))
    paths.update(ROOT / 'tools' / name for name in [
        'build-ports.ps1', 'build-modern.ps1', 'prepare-real-pack.ps1',
        'discover-targets.py', 'generate-languages.py', 'localize-sources.py',
        'verify-artifacts.py', 'prepare-assets.py', 'package-current.py'])
    for folder in roots:
        paths.update(path for path in (ROOT / folder).rglob('*') if path.is_file())
    for path in sorted(paths):
        relative = path.relative_to(ROOT).as_posix()
        if any(part in ('.git', '.gradle', 'build', '__pycache__') for part in path.relative_to(ROOT).parts):
            raise ValueError(f'Private build file in source allowlist: {relative}')
        data = path.read_bytes()
        if re.search(rb'[A-Za-z]:[/\\]Users[/\\]', data):
            raise ValueError(f'Personal path in source: {relative}')
        yield relative, data


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, default=ROOT / f'build/delivery/{VERSION}')
    args = parser.parse_args()
    artifacts, manifest = [], []
    for mc, loader, folder, report in inputs():
        artifact = folder / f'modhub-{VERSION}-mc{mc}-{loader}.jar'
        verify(artifact)
        result = report.read_text(encoding='utf-8-sig').strip()
        expected = 85
        if mc == '1.12.2':
            expected = 41
        elif mc in ('1.16.5', '1.18.2', '1.19.2'):
            expected = 39
        if result != f'PASSED: {expected}':
            raise ValueError(f'Expected {expected} passing runtime checks: {report}')
        data = artifact.read_bytes()
        entry = {'minecraft': mc, 'loader': loader, 'file': artifact.name,
                 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest(),
                 'artifactCheck': 'passed', 'runtimeProbe': result,
                 'status': 'private-candidate'}
        pack = FULL_PACKS.get((mc, loader))
        if pack:
            evidence = ROOT / 'build/full-pack-verification' / pack
            installed = json.loads((evidence / 'artifact.json').read_text(encoding='utf-8-sig'))
            expected_jar = {key: entry[key] for key in ('minecraft', 'loader', 'file', 'sha256')}
            pack_result = (evidence / 'results.txt').read_text(encoding='utf-8-sig').strip()
            if (any(installed.get(key) != value for key, value in expected_jar.items())
                    or installed.get('nativeResult') != result or pack_result != result):
                raise ValueError(f'Full pack {pack} has no passing result for {artifact.name}')
            entry['fullPackProbe'] = {'pack': pack, 'result': pack_result}
        artifacts.append((artifact.name, data))
        manifest.append(entry)
    sources = list(public_sources())
    args.output.mkdir(parents=True, exist_ok=True)
    for filename, data in artifacts:
        (args.output / filename).write_bytes(data)
    with ZipFile(args.output / f'modhub-{VERSION}-sources.zip', 'w', ZIP_DEFLATED) as archive:
        for relative, data in sources:
            archive.writestr(relative, data)
    (args.output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    (args.output / 'SHA256SUMS.txt').write_text(''.join(
        f"{item['sha256']}  {item['file']}\n" for item in manifest), encoding='ascii')
    print(f'LOCAL_DELIVERY_PACKAGED: {len(manifest)} JARs; {len(sources)} source files; {args.output}')


if __name__ == '__main__':
    main()
