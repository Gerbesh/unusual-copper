"""Convert authored image_gen originals to the game's existing 64x64 item format.

This does not redraw assets: square transparent masters are scaled nearest-neighbor
and retain alpha. Originals and exact prompts remain in art/industrial/.
"""
from pathlib import Path
import json
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
ART=ROOT/'art/industrial'
ASSETS=ROOT/'src/main/resources/assets/unusualcopper'

def main():
    manifest=json.loads((ART/'prompts.json').read_text(encoding='utf-8'))
    for asset in manifest['assets']:
        name=asset['id'];source=ART/'sources'/f'{name}.png'
        image=Image.open(source).convert('RGBA')
        assert image.width==image.height and image.getextrema()[3][0]==0,(name,'Expected transparent square master')
        image=image.resize((64,64),Image.Resampling.NEAREST)
        assert image.getextrema()[3][1]>0,(name,'Empty sprite')
        (ART/'sprites').mkdir(exist_ok=True)
        image.save(ART/'sprites'/f'{name}.png')
        image.save(ASSETS/'textures/item'/f'{name}.png')
        model={'parent':'minecraft:item/generated','textures':{'layer0':f'unusualcopper:item/{name}'}}
        (ASSETS/'models/item'/f'{name}.json').write_text(json.dumps(model,indent=2)+'\n',encoding='utf-8',newline='\n')
    print(f"Imported {len(manifest['assets'])} industrial sprites; original alpha and masters retained.")

if __name__=='__main__':main()
