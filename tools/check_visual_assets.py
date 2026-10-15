"""Check actual exported surface geometry, PNG alpha and asset references."""
from pathlib import Path
import json
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/unusualcopper'
AXES={'west':(0,-1),'east':(0,1),'down':(1,-1),'up':(1,1),'north':(2,-1),'south':(2,1)}

def main():
    checked=0
    for path in (ASSETS/'models/block').glob('*.json'):
        model=json.loads(path.read_text(encoding='utf-8'));faces={}
        for cube in model.get('elements',[]):
            for face,data in cube['faces'].items():
                axis,sign=AXES[face];u,v=[a for a in range(3) if a!=axis]
                plane=cube['to' if sign>0 else 'from'][axis]
                key=(face,round(plane,6),json.dumps(cube.get('rotation'),sort_keys=True))
                rect=(cube['from'][u],cube['from'][v],cube['to'][u],cube['to'][v])
                assert rect[2]>rect[0] and rect[3]>rect[1],(path,face,'zero-area quad')
                for old in faces.get(key,[]):
                    overlap=min(rect[2],old[2])-max(rect[0],old[0])>1e-6 and min(rect[3],old[3])-max(rect[1],old[1])>1e-6
                    assert not overlap,(path.name,face,plane,rect,old,'coplanar overlap')
                faces.setdefault(key,[]).append(rect)
                texture=data['texture']
                if texture.startswith('#'):texture=model['textures'][texture[1:]]
                if texture.startswith('unusualcopper:'):
                    assert (ASSETS/'textures'/f'{texture.split(":",1)[1]}.png').is_file(),(path,texture)
                assert len(data['uv'])==4 and all(-.001<=c<=16.001 for c in data['uv']),(path,'invalid UV',data['uv'])
        checked+=1
    for entry in json.loads((ROOT/'art/sprites/prompts.json').read_text(encoding='utf-8'))['sprites']:
        icon=Image.open(ASSETS/'textures/item'/f'{entry["name"]}.png')
        assert icon.size==(64,64) and icon.mode=='RGBA' and icon.getextrema()[3][0]==0,entry['name']
        assert (ROOT/'art/sprites'/f'{entry["name"]}.png').read_bytes()==(ASSETS/'textures/item'/f'{entry["name"]}.png').read_bytes(),entry['name']
    for name in ('copper_energy_pipe','reinforced_energy_pipe','crystalline_energy_pipe'):
        for path in (ASSETS/'models/block').glob(f'{name}_*.json'):
            model=json.loads(path.read_text(encoding='utf-8'))
            assert all(face['texture']!='#aqua' for part in model['elements'] for face in part['faces'].values()),'Permanent opaque energy core'
    print(f'PASS: {checked} block models; no overlapping coplanar quads, valid UV/texture paths; nine transparent authored sprites; no baked energy cores.')

if __name__=='__main__':main()
