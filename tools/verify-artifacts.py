"""Check standalone JARs for languages, Java compatibility and excluded development fixtures."""
import argparse
import json
from pathlib import Path
import re
import struct
from zipfile import ZipFile


def verify(path):
    match = re.search(r'-mc([0-9.]+)-(forge|neoforge)\.jar$', path.name)
    if not match:
        raise AssertionError(f'Unrecognized artifact name: {path.name}')
    minecraft = tuple(map(int, match[1].split('.')))
    maximum = 52 if minecraft in ((1, 12, 2), (1, 16, 5)) else 65 if minecraft >= (1, 20, 5) else 61
    with ZipFile(path) as jar:
        names = jar.namelist()
        assert 'com/pangod512/modhub/core/Messages.class' in names, 'Missing translation bridge'
        assert not any('/probe/' in name or '/smoke/' in name for name in names), 'Development classes in standalone JAR'
        assert 'LICENSE.txt' in names and 'CREDITS.txt' in names, 'Missing project notices'
        if match[2] == 'neoforge':
            pack = json.loads(jar.read('pack.mcmeta'))
            assert 'pack' in pack, 'Missing resource pack metadata'
        for name in names:
            if name.endswith('.class'):
                assert name.startswith('com/pangod512/modhub/'), f'Unexpected bundled class: {name}'
                major = struct.unpack('>H', jar.read(name)[6:8])[0]
                assert major <= maximum, f'Wrong Java target {major}: {name}'
        locales = ['en_us', 'en_gb', 'es_es', 'es_ar', 'es_cl', 'es_ec', 'es_mx', 'es_uy', 'es_ve']
        english = json.loads(jar.read('assets/modhub/lang/en_us.json'))
        resources = Path(__file__).resolve().parents[1] / 'shared/src/main/resources/assets/modhub/lang'
        for locale in locales:
            data = json.loads(jar.read(f'assets/modhub/lang/{locale}.json'))
            assert data.keys() == english.keys(), f'Missing language keys: {locale}'
            expected = json.loads((resources / f'{locale}.json').read_text(encoding='utf-8'))
            assert data == expected, f'Packaged translations are stale: {locale}'
            if minecraft == (1, 12, 2):
                legacy = jar.read(f'assets/modhub/lang/{locale}.lang')
                assert legacy == (resources / f'{locale}.lang').read_bytes(), f'Packaged legacy translations are stale: {locale}'
        assert english['modhub.client'] == 'Client'
        spanish = json.loads(jar.read('assets/modhub/lang/es_es.json'))
        assert spanish['modhub.client'] == 'Cliente' and spanish['modhub.server'] == 'Servidor'
    return {'file': str(path), 'minecraft': match[1], 'loader': match[2], 'result': 'passed'}


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('jars', nargs='+', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    results = [verify(path) for path in args.jars]
    text = json.dumps(results, ensure_ascii=False, indent=2) + '\n'
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(text, encoding='utf-8')
    print(text)
