"""Deterministic native localization of physical module variants; no NBT-only recipes."""
from pathlib import Path
import json

ROOT=Path(__file__).resolve().parents[1]
LEVELS={'optic_visor':2,'aqua_lungs':2,'stride_drive':2,'mining_servo':3,'shield_field':3,'jump_drive':2}

def main():
    for language in ('en_us','ru_ru'):
        path=ROOT/f'src/main/resources/assets/unusualcopper/lang/{language}.json'
        data=json.loads(path.read_text(encoding='utf-8'))
        for module,maximum in LEVELS.items():
            name=data[f'item.unusualcopper.{module}_module']
            for level in range(2,maximum+1):
                data[f'item.unusualcopper.{module}_module_tier_{level}']=name+' '+('II' if level==2 else 'III')
        data['armor.unusualcopper.module_level']='Уровень модуля: %s' if language=='ru_ru' else 'Module level: %s'
        data['armor.unusualcopper.module_chassis']='Минимальный уровень шасси: %s' if language=='ru_ru' else 'Minimum chassis tier: %s'
        path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')
    print('Imported eight physical module variants and RU/EN level tooltips.')

if __name__=='__main__':main()
