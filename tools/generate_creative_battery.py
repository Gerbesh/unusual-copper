"""Export only the creative battery; shared copper textures and editable Blockbench geometry."""
from math import sqrt
from generate_assets import ASSETS, PALETTE, part, clean_surfaces, write_bbmodel, write_json


def generate():
    parts=[part([3,2,4],[13,14,12],'patina'),part([2,1.5,3],[14,3,13],'copper'),
           part([2,13,3],[14,14.5,13],'copper'),part([3.2,3.5,3.25],[12.8,12.5,4],'dark'),
           part([3.2,3.5,12],[12.8,12.5,12.6],'dark')]
    for x in (2,12.8):
        parts.append(part([x,3,4],[x+1.2,13,12],'copper'))
    for x in (4.2,9.2):
        parts.extend([part([x,14.5,5.2],[x+2.6,15.6,10.8],'dark'),
                      part([x+.3,15.6,5.5],[x+2.3,16,10.5],'aqua')])
    # Two joined diamond loops read as infinity, even in the small inventory silhouette.
    loops=[[(3.5,8),(5.75,10.25),(8,8),(5.75,5.75)],
           [(8,8),(10.25,10.25),(12.5,8),(10.25,5.75)]]
    for loop in loops:
        for a,b in zip(loop,loop[1:]+loop[:1]):
            x,y=(a[0]+b[0])/2,(a[1]+b[1])/2
            length=sqrt((b[0]-a[0])**2+(b[1]-a[1])**2)
            angle=45 if (b[0]-a[0])*(b[1]-a[1])>0 else -45
            parts.append(part([x-length/2,y-.25,2.82],[x+length/2,y+.25,3.18],'aqua',
                              {'origin':[x,y,3],'axis':'z','angle':angle}))
    for y in (5,7.5,10):
        parts.append(part([5,y,12.6],[11,y+.7,12.8],'aqua'))
    textures={key:'unusualcopper:block/'+key for key in PALETTE}
    model={'render_type':'minecraft:cutout','textures':textures,'elements':clean_surfaces(parts),
           'display':{'gui':{'rotation':[20,25,0],'scale':[.8,.8,.8]},
                      'ground':{'translation':[0,2,0],'scale':[.35,.35,.35]},
                      'thirdperson_righthand':{'rotation':[0,0,0],'scale':[.45,.45,.45]},
                      'firstperson_righthand':{'rotation':[0,-15,0],'translation':[1,2,0],'scale':[.6,.6,.6]}}}
    write_json(ASSETS/'models/item/creative_battery.json',model)
    write_bbmodel('creative_battery',parts,list(PALETTE))
    print('Creative battery: copper case, infinity core, two contacts; JSON + Blockbench exported.')


if __name__=='__main__':
    generate()
