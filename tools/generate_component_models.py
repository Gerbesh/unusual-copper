"""Authored cuboid inventory art for industrial hardware, modules and battery banks.

These are individual editable models, not vanilla item-icon aliases. Surfaces reuse
the project's copper/patina/charcoal materials. Grit and dust use authored sprites.
"""
from generate_assets import ASSETS, ART, PALETTE, part, clean_surfaces, write_bbmodel, write_json


def block(a,b,material='copper'):
    return part(a,b,material)


def frame(x0,y0,x1,y1,z0,z1,material='copper',width=1):
    return [block([x0,y0,z0],[x0+width,y1,z1],material),block([x1-width,y0,z0],[x1,y1,z1],material),
            block([x0+width,y0,z0],[x1-width,y0+width,z1],material),block([x0+width,y1-width,z0],[x1-width,y1,z1],material)]


def export(name,body):
    textures={m:f'unusualcopper:block/{m}' for m in PALETTE}
    textures.update(glass='unusualcopper:block/glass')
    glass=[p for p in body if any(f['texture']=='#glass' for f in p['faces'].values())]
    opaque=[p for p in body if p not in glass]
    model={'render_type':'minecraft:translucent' if glass else 'minecraft:cutout',
           'textures':textures,'elements':clean_surfaces(opaque)+clean_surfaces(glass,opaque),
           'display':{'gui':{'rotation':[20,215,0],'translation':[0,0,0],'scale':[.83,.83,.83]},
                      'ground':{'translation':[0,3,0],'scale':[.35,.35,.35]},
                      'thirdperson_righthand':{'rotation':[75,45,0],'scale':[.4,.4,.4]}}}
    write_json(ASSETS/f'models/item/{name}.json',model)
    write_bbmodel(name,body,list(PALETTE)+['glass'])
    return name


def cartridge():
    return [block([3,2,7],[13,14,9],'dark'),block([4,3,6.5],[12,13,7],'patina')]+frame(3,2,13,14,6.6,7.2,width=.7)+[
        block([3.3,3,6.1],[4.2,5,6.6]),block([11.8,11,6.1],[12.7,13,6.6])]


def models():
    names=[]
    def out(name,body):names.append(export(name,body))
    out('inert_tailings',[block([2,2,4],[8,5,10],'dark'),block([6,3,7],[12,8,12],'dark'),block([10,2,4],[14,5,8],'patina')])
    out('copper_plate',[block([2,4,4],[12,4.7,13],'dark'),block([3,5.3,3],[14,6.1,13]),
                        block([3.5,6.1,3.5],[13.5,6.35,12.5],'patina')])
    wire=frame(3,4,13,12,3,4,'dark')+frame(3,4,13,12,12,13,'dark')
    wire+=[block([6,7,4],[10,9,12],'dark')]
    for z in (4.3,5.8,7.3,8.8,10.3):wire+=frame(4,5,12,11,z,z+.9,width=.85)
    out('copper_wire_spool',wire)
    out('reinforced_frame',frame(2,2,14,14,6,9,'dark',2)+frame(2,2,14,14,5.5,6,width=1)+[
        block([2,2,5],[5,5,5.5]),block([11,11,5],[14,14,5.5]),block([2,11,5],[5,14,5.5]),block([11,2,5],[14,5,5.5])])
    blank=[block([3,2,7],[13,14,8],'patina')]+frame(3,2,13,14,6.7,7,width=.55)
    for x in (4.4,6.2,8,9.8):blank.append(block([x,2.1,6.5],[x+.8,3.5,6.7]))
    out('circuit_blank',blank)
    etched=blank+[block([6,6,6],[10,10,6.7],'dark'),block([7,7,5.8],[9,9,6],'aqua')]
    for y in (4.5,10.8):etched.append(block([4.2,y,6.3],[11.8,y+.45,6.7]))
    for x in (4.8,10.7):etched.append(block([x,4.7,6.3],[x+.5,11.2,6.7]))
    out('etched_circuit',etched)
    chip=[block([4,4,6],[12,12,9],'patina'),block([5,5,5],[11,11,6],'dark'),block([6.5,6.5,4.7],[9.5,9.5,5],'aqua')]
    for k in (5,7.5,10):
        chip+=[block([2.8,k,7],[4,k+.7,8]),block([12,k,7],[13.2,k+.7,8]),block([k,2.8,7],[k+.7,4,8]),block([k,12,7],[k+.7,13.2,8])]
    out('precision_processor',chip)
    out('module_blank_i',cartridge()+frame(5,5,11,11,6.1,6.5,'dark',1))
    out('plate_die',frame(2,2,14,14,6,8,'dark',2)+frame(5,5,11,11,6,7,'copper',.7))
    die=[block([2,2,7],[14,14,9],'dark')]
    for x in (4,7,10):die+=frame(x,4,x+2,12,6.4,7,'copper',.45)
    out('wire_die',die)
    card=[block([3,2,7.5],[13,14,8.5],'patina')]+frame(3,2,13,14,7,7.5,width=.6)
    for x in (4.2,7.1,10):
        for y in (3.5,6.6,9.7):card+=frame(x,y,x+1.8,y+2.2,6.8,7,'copper',.3)
    out('pattern_card',card)
    out('etching_reagent',[block([4,3,5],[12,11,11],'glass'),block([4.7,3.4,5.7],[11.3,8.5,10.3],'aqua'),
                          block([6,11,6.5],[10,13,9.5],'glass'),block([5.5,13,6],[10.5,14,10])])
    for tier,name in ((5,'prismatic_battery'),(6,'sovereign_battery')):
        bank=[block([2,2,3],[14,3.5,13],'dark'),block([2,12.5,3],[14,14,13],'dark')]
        for x in (2,12):bank.append(block([x,3.5,3],[x+2,12.5,13]))
        count=tier-3
        for i in range(count):
            x=4.3+i*(7.4/max(1,count-1))
            bank+=[block([x-.65,4,5],[x+.65,12,11],'aqua'),block([x-.85,3.5,4.8],[x+.85,4.4,11.2],'patina'),
                   block([x-.85,11.6,4.8],[x+.85,12.5,11.2],'patina')]
        bank+=[block([5,14,6],[7,15,8]),block([9,14,8],[11,15,10])]
        out(name,bank)
    glyphs={
        'optic_visor':[block([5,7,5.8],[7.5,9.5,6.5],'aqua'),block([8.5,7,5.8],[11,9.5,6.5],'aqua'),block([7.5,8,6],[8.5,8.5,6.5])],
        'aqua_lungs':[block([5,5,5.8],[7,11.5,6.5],'aqua'),block([9,5,5.8],[11,11.5,6.5],'aqua'),block([6,11,5.5],[10,12,6.5])],
        'stride_drive':[block([5,4.5,5.8],[7,8,6.5]),block([8.5,7,5.8],[10.5,11.5,6.5]),block([5,4.5,5.6],[8,5.5,5.8],'aqua')],
        'mining_servo':[block([5,9.5,5.8],[11,11,6.5]),block([7.4,5,5.8],[8.6,9.5,6.5],'aqua'),block([4.5,8,5.8],[6,11,6.5]),block([10,8,5.8],[11.5,11,6.5])],
        'fall_damper':[block([7.5,5,5.8],[8.5,12,6.5],'dark'),block([5,5,5.8],[11,6,6.5]),block([5,11,5.8],[11,12,6.5])],
        'jump_drive':[block([7.3,5,5.8],[8.7,11,6.5],'aqua'),block([5.5,9,5.8],[10.5,10,6.5]),block([6.3,10,5.8],[9.7,11,6.5])],
        'hover_drive':[block([4.5,6.5,5.8],[11.5,7.5,6.5],'aqua'),block([5.5,9,5.8],[10.5,10,6.5],'aqua'),block([7.2,5,5.6],[8.8,11.5,5.8])],
        'flight_core_ii':[block([7,6,5.6],[9,11,6.5],'aqua'),block([4.6,5,5.8],[6,10.5,6.5]),block([10,5,5.8],[11.4,10.5,6.5])],
    }
    for name,glyph in glyphs.items():
        if name=='fall_damper':
            for y in (6.5,8,9.5):glyph.append(block([5.5,y,5.6],[10.5,y+.6,6.5],'aqua'))
        out(name+'_module',cartridge()+glyph)
    write_json(ART/'industrial/component-models.json',{'authorship':'Original cuboid geometry; materials reused from project art','models':names})
    return names


if __name__=='__main__':print('Generated',len(models()),'individual industrial hardware models.')
