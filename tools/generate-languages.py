"""Generate Minecraft language files from a catalog with stable translation keys."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPANISH = ('es_es', 'es_mx', 'es_ar', 'es_cl', 'es_ec', 'es_uy', 'es_ve')


def main():
    catalog = json.loads((ROOT / 'shared/translations.json').read_text(encoding='utf-8'))
    english, spanish = {}, {}
    for key, values in catalog.items():
        if not key.startswith('modhub.') or not all(isinstance(values.get(locale), str) for locale in ('en_us', 'es_es')):
            raise ValueError(f'Invalid translation entry: {key}')
        english[key], spanish[key] = values['en_us'], values['es_es']
    output = ROOT / 'shared/src/main/resources/assets/modhub/lang'
    output.mkdir(parents=True, exist_ok=True)
    for locale, values in [('en_us', english), ('en_gb', english)] + [(locale, spanish) for locale in SPANISH]:
        (output / (locale + '.json')).write_text(json.dumps(values, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        # The legacy format has no multiline values; 1.12 screens use the single-line entries.
        (output / (locale + '.lang')).write_text(''.join(key + '=' + value + '\n' for key, value in values.items() if '\n' not in value), encoding='utf-8')
    print(f'Generated {len(catalog)} translation keys for 9 locales.')


if __name__ == '__main__':
    main()
