"""Native armor UV painting and cuboid item models using the authored CuE materials.

The UV layout is Minecraft's 64x32 humanoid armor net, painted at four times
resolution. No vanilla armor image is copied or recolored. Materials come from
the existing project art; all plates, seams, sockets and visor marks are explicit.
"""
from pathlib import Path
from PIL import Image, ImageDraw
from generate_assets import ASSETS, ART, part, clean_surfaces, write_bbmodel, write_json, PALETTE

S=4
HEAD=[(0,8,8,16),(8,8,16,16),(16,8,24,16),(24,8,32,16),(8,0,16,8),(16,0,24,8)]
BODY=[(16,20,20,32),(20,20,28,32),(28,20,32,32),(32,20,40,32),(20,16,28,20),(28,16,36,20)]
ARM=[(40,20,44,32),(44,20,48,32),(48,20,52,32),(52,20,56,32),(44,16,48,20),(48,16,52,20)]
LEG=[(0,20,4,32),(4,20,8,32),(8,20,12,32),(12,20,16,32),(4,16,8,20),(8,16,12,20)]
COLORS={'copper':(182,107,66,255),'edge':(227,160,106,255),'dark':(34,44,43,255),
        'patina':(49,91,80,255),'aqua':(55,214,211,255),'gold':(188,153,80,255)}


def rect(draw,box,color,width=0):
    a,b,c,d=box;coords=(round(a*S),round(b*S),round(c*S)-1,round(d*S)-1)
    if width:draw.rectangle(coords,outline=COLORS.get(color,color),width=width)
    else:draw.rectangle(coords,fill=COLORS.get(color,color))


def atlas(tier,layer):
    image=Image.new('RGBA',(64*S,32*S));d=ImageDraw.Draw(image)
    material='copper' if tier==1 else 'patina' if tier==2 else 'dark'
    texture=Image.open(ASSETS/f'textures/block/{material}.png').convert('RGBA')
    faces=HEAD+BODY+ARM+LEG if layer==1 else BODY+LEG
    for x0,y0,x1,y1 in faces:
        region=texture.resize(((x1-x0)*S,(y1-y0)*S),Image.Resampling.NEAREST)
        image.paste(region,(x0*S,y0*S));rect(d,(x0,y0,x1,y1),'dark',2)
        rect(d,(x0+.5,y0+.5,x1-.5,y1-.5),'copper',2)
        for x in (x0+.65,x1-1.1):
            for y in (y0+.65,y1-1.1):rect(d,(x,y,x+.4,y+.4),'gold')
    if layer==1:
        # Every tier leaves eyes, nose and mouth visible. Optics sit on the brow;
        # upgrading armor never silently replaces the face opening with a mask.
        rect(d,(8.8,9.5,15.2,16),(0,0,0,0))
        rect(d,(8.7,8.5,15.3,9.2),'dark')
        rect(d,(9,8.7,15,9),'aqua' if tier>1 else 'gold')
        rect(d,(11.5,.5,12.5,3),'patina' if tier==1 else 'aqua')
        for index in range(tier):
            rect(d,(1.5+index,11,2+index,13),'aqua' if tier>1 else 'gold')
            rect(d,(18+index,11,18.5+index,13),'aqua' if tier>1 else 'gold')
        # Distinct chest plates, central power socket, seams and shoulder accents.
        rect(d,(20.8,21,23.5,27.5),'copper');rect(d,(24.5,21,27.2,27.5),'copper')
        rect(d,(23.4,22,24.6,25.5),'dark')
        rect(d,(23.65,22.5,24.35,25),'aqua' if tier>1 else 'gold')
        rect(d,(21,28,27,28.7),'dark');rect(d,(21,30,27,31),'copper')
        for x in (44,48,52):rect(d,(x+.5,21,x+3.5,23),'copper')
        # Boots cover the lower leg. Their upper half is genuinely transparent.
        for x in (0,4,8,12):
            rect(d,(x,20,x+4,25),(0,0,0,0))
            rect(d,(x+.2,25,x+3.8,26),'copper');rect(d,(x+.2,30.8,x+3.8,32),'dark')
            if tier>1:rect(d,(x+1.5,26.5,x+2.5,29.8),'aqua')
        # Battery harness and tier stripes are on the rear chest, not the player's skin.
        rect(d,(33.2,22,38.8,29.5),'dark');rect(d,(33.8,22.5,38.2,23.3),'copper')
        for i in range(tier):rect(d,(34.3+i,24,34.8+i,28.7),'aqua' if tier>1 else 'gold')
    else:
        # Layer two body is a waist band; do not paint a second opaque chestplate.
        for x0,y0,x1,y1 in BODY:rect(d,(x0,y0,x1,y1),(0,0,0,0))
        for x in (16,20,28,32):rect(d,(x,29,x+(4 if x in (16,28) else 8),32),'patina')
        rect(d,(20,29.5,28,30.5),'copper');rect(d,(23,30,25,31.5),'gold')
        for x in (0,4,8,12):
            rect(d,(x+.6,20.6,x+3.4,24.5),'copper');rect(d,(x+.5,25,x+3.5,27.5),'dark')
            rect(d,(x+.9,25.5,x+3.1,26.9),'copper' if tier==1 else 'patina')
            if tier>1:rect(d,(x+1.6,21.2,x+2.4,23.7),'aqua')
    folder=ASSETS/'textures/models/armor';folder.mkdir(parents=True,exist_ok=True)
    image.save(folder/f'copper_laminate_tier_{tier}_layer_{layer}.png')
    master=ART/'armor/uv';master.mkdir(parents=True,exist_ok=True)
    image.save(master/f'copper_laminate_tier_{tier}_layer_{layer}.png')


def icons(tier):
    shell='copper' if tier==1 else 'patina' if tier==2 else 'dark'
    armor={
        'helmet':[part([3,4,10],[13,13,13],shell),part([3,4,3],[5,13,10],shell),
                  part([11,4,3],[13,13,10],shell),part([5,11,3],[11,13,10],'copper'),
                  part([5.3,12,2.8],[10.7,12.5,3],'aqua' if tier>1 else 'dark')],
        'chestplate':[part([4,4,5],[12,13,11],shell),part([1,10,5],[4,13,10],'copper'),
                      part([12,10,5],[15,13,10],'copper'),part([5,6,3.5],[11,11,5],'copper'),
                      part([7,7.5,3],[9,10,3.5],'aqua' if tier>1 else 'dark'),part([4,12,5],[12,14,11],'dark')],
        'leggings':[part([3,11,5],[13,14,11],'copper'),part([3,2,5],[7.5,11,11],shell),
                    part([8.5,2,5],[13,11,11],shell),part([3.5,5,4],[7,7,5],'copper'),
                    part([9,5,4],[12.5,7,5],'copper')],
        'boots':[part([3,6,5],[7,13,10],shell),part([9,6,5],[13,13,10],shell),
                 part([2.5,3,2.5],[7.5,6,11],'copper'),part([8.5,3,2.5],[13.5,6,11],'copper'),
                 part([2.5,2,2.5],[7.5,3,11],'dark'),part([8.5,2,2.5],[13.5,3,11],'dark')],
    }
    for kind,body in armor.items():
        for i in range(tier):
            if kind=='helmet':body.append(part([3,5+i*1.6,2.8],[4.2,5.7+i*1.6,3],'copper'))
            elif kind=='chestplate':body.append(part([5.3,5+i*.7,3.1],[6.7,5.35+i*.7,3.5],'patina'))
            elif kind=='leggings':body.append(part([4,8+i*.6,4.6],[6.7,8.25+i*.6,5],'aqua' if tier>1 else 'dark'))
            else:body.append(part([9.5,7+i*.8,4.7],[12.5,7.3+i*.8,5],'aqua' if tier>1 else 'dark'))
        name=f'programmable_{kind}_tier_{tier}'
        model={'textures':{m:f'unusualcopper:block/{m}' for m in PALETTE},'elements':clean_surfaces(body),
               'display':{'gui':{'rotation':[15,205,0],'translation':[0,0,0],'scale':[.8,.8,.8]},
                          'ground':{'translation':[0,3,0],'scale':[.4,.4,.4]},
                          'thirdperson_righthand':{'rotation':[75,45,0],'scale':[.4,.4,.4]}}}
        write_json(ASSETS/f'models/item/{name}.json',model)
        write_bbmodel(name,body,list(PALETTE))


if __name__=='__main__':
    for tier in (1,2,3):
        for layer in (1,2):atlas(tier,layer)
        icons(tier)
    print('Generated 6 copper armor UV layers and 12 editable cuboid inventory models.')
