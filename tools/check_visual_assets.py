"""Check actual exported surface geometry, PNG alpha and asset references."""
from pathlib import Path
import json
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/unusualcopper'
AXES={'west':(0,-1),'east':(0,1),'down':(1,-1),'up':(1,1),'north':(2,-1),'south':(2,1)}

def main():
    checked=0
    for path in [*(ASSETS/'models/block').glob('*.json'),*(ASSETS/'models/item').glob('*.json')]:
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
    sprites=json.loads((ROOT/'art/sprites/prompts.json').read_text(encoding='utf-8'))['sprites']+json.loads((ROOT/'art/batteries/prompts.json').read_text(encoding='utf-8'))['sprites']
    for entry in sprites:
        icon=Image.open(ASSETS/'textures/item'/f'{entry["name"]}.png')
        assert icon.size==(64,64) and icon.mode=='RGBA' and icon.getextrema()[3][0]==0,entry['name']
        assert (ROOT/'art/sprites'/f'{entry["name"]}.png').read_bytes()==(ASSETS/'textures/item'/f'{entry["name"]}.png').read_bytes(),entry['name']
    industrial=ROOT/'art/industrial/prompts.json'
    added=[]
    if industrial.is_file():
        added=json.loads(industrial.read_text(encoding='utf-8'))['assets']
        for entry in added:
            name=entry['id'];path=ASSETS/'textures/item'/f'{name}.png'
            icon=Image.open(path)
            assert icon.size==(64,64) and icon.mode=='RGBA' and icon.getextrema()[3][0]==0,name
            assert path.read_bytes()==(ROOT/'art/industrial/sprites'/f'{name}.png').read_bytes(),name
            assert (ROOT/'art/industrial'/entry['source']).is_file(),name
        from generate_industrial_assets import MACHINES
        hardware=json.loads((ROOT/'art/industrial/component-models.json').read_text(encoding='utf-8'))['models']
        armor=[f'programmable_{piece}_tier_{tier}' for tier in (1,2,3) for piece in ('helmet','chestplate','leggings','boots')]
        for name in [*MACHINES,*hardware,*armor]:
            source=ROOT/'art/blockbench'/f'{name}.bbmodel'
            assert source.is_file() and json.loads(source.read_text(encoding='utf-8'))['elements'],name
            assert (ASSETS/'models/item'/f'{name}.json').is_file(),name
        for tier in (1,2,3):
            for layer in (1,2):
                name=f'copper_laminate_tier_{tier}_layer_{layer}.png'
                path=ASSETS/'textures/models/armor'/name;texture=Image.open(path)
                assert texture.size==(256,128) and texture.mode=='RGBA' and texture.getbbox(),name
                assert path.read_bytes()==(ROOT/'art/armor/uv'/name).read_bytes(),name
                if layer==1:
                    # All tiers keep the central eyes, nose and mouth clear.
                    assert texture.getchannel('A').crop((9*4,10*4,15*4,16*4)).getbbox() is None,('covered face',name)
    for name in ('copper_energy_pipe','reinforced_energy_pipe','crystalline_energy_pipe'):
        for path in (ASSETS/'models/block').glob(f'{name}_*.json'):
            model=json.loads(path.read_text(encoding='utf-8'))
            assert all(face['texture']!='#aqua' for part in model['elements'] for face in part['faces'].values()),'Permanent opaque energy core'
    bridge=json.loads((ASSETS/'blockstates/ssn_bridge.json').read_text(encoding='utf-8'))['multipart']
    sockets=[part for part in bridge if part['apply']['model']=='unusualcopper:block/ssn_bridge_socket']
    assert len(sockets)==6 and {part['when']['facing'] for part in sockets}=={'north','south','east','west','up','down'}
    assert all(part['when']['attached']=='true' for part in sockets),'SSN socket visible without cable'
    assert next(part for part in sockets if part['when']['facing']=='up')['apply']['x']==270
    assert next(part for part in sockets if part['when']['facing']=='down')['apply']['x']==90
    socket=json.loads((ASSETS/'models/block/ssn_bridge_socket.json').read_text(encoding='utf-8'))
    # SSN's native small cable core occupies 6..10: in the neighboring north block, z=-10..-6.
    assert min(cube['from'][2] for cube in socket['elements']) < -6,'Socket leaves an air gap to SSN cable core'
    print(f'PASS: {checked} block/item models; no overlapping coplanar quads, valid UV/texture paths; {len(sprites)+len(added)} transparent authored sprites; no baked energy cores.')

if __name__=='__main__':main()
