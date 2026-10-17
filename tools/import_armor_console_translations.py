from pathlib import Path
import json

ROOT=Path(__file__).resolve().parents[1]
entries=json.loads((ROOT/'tools/armor_console_translations.json').read_text(encoding='utf-8'))
for index,language in enumerate(('en_us','ru_ru')):
    path=ROOT/f'src/main/resources/assets/unusualcopper/lang/{language}.json'
    data=json.loads(path.read_text(encoding='utf-8'))
    for key,pair in entries.items():data[key]=pair[index]
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')
print('Updated armor console RU/EN translations.')
