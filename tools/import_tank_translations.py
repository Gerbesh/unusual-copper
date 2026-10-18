"""Import the shared fluid tank RU/EN interface and item explanations."""
from pathlib import Path
import json
root=Path(__file__).resolve().parents[1]
entries=json.loads((root/'tools/tank_translations.json').read_text(encoding='utf-8'))
for index,language in enumerate(('en_us','ru_ru')):
    path=root/f'src/main/resources/assets/unusualcopper/lang/{language}.json'
    data=json.loads(path.read_text(encoding='utf-8'))
    for key,values in entries.items():data[key]=values[index]
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')
print('Imported fluid tank RU/EN explanations.')
