"""Editable industrial cuboid models; reuses the project's authored metal textures.

Only the ten industrial devices are exported. Existing extractor art is untouched.
Moving parts are baked into inventory previews and retained in Blockbench sources;
their world counterparts are drawn by IndustrialMachineVisuals.
"""
import json
from generate_assets import ASSETS, ART, PALETTE, part, clean_surfaces, export_model, write_bbmodel, write_json

MACHINES = (
    'resonance_crusher', 'copper_press', 'vortex_purifier',
    'resonance_crystallizer', 'circuit_etcher', 'precision_assembler',
    'copper_fluid_pump', 'copper_fluid_tank', 'production_controller', 'armor_workbench',
)


def base():
    return [part([1, 0, 1], [15, 2, 15], 'dark'),
            part([1.5, 2, 1.5], [14.5, 3.4, 14.5], 'copper')]


def ring(y, inner=3, outer=14, height=1, material='copper'):
    low=16-outer
    return [part([low,y,low],[outer,y+height,inner],material),
            part([low,y,16-inner],[outer,y+height,outer],material),
            part([low,y,inner],[inner,y+height,16-inner],material),
            part([16-inner,y,inner],[outer,y+height,16-inner],material)]


def posts(bottom=3.4, top=14, inset=1.5, width=1.7):
    return [part([x,bottom,z],[x+width,top,z+width],'copper')
            for x in (inset,16-inset-width) for z in (inset,16-inset-width)]


def windows(bottom=3.5, top=12.8):
    return [part([3.2,bottom,2.1],[12.8,top,2.35],'glass'),
            part([3.2,bottom,13.65],[12.8,top,13.9],'glass'),
            part([2.1,bottom,3.2],[2.35,top,12.8],'glass'),
            part([13.65,bottom,3.2],[13.9,top,12.8],'glass')]


def export(name, body, glass=(), moving=()):
    # export_model adds the shared feet/rivets and clips hidden coplanar faces.
    glass=list(glass)
    export_model(name, body, glass)
    if moving:
        model=json.loads((ASSETS/f'models/item/{name}.json').read_text(encoding='utf-8'))
        model['elements']+=clean_surfaces(list(moving))
        write_json(ASSETS/f'models/item/{name}.json', model)
        write_bbmodel(name,body+glass+list(moving),list(PALETTE)+['glass'])


def models():
    crusher=base()+[part([2,3.4,2],[14,7,14],'patina'),
                    part([1.5,7,3],[3.5,13,13],'dark'),part([12.5,7,3],[14.5,13,13],'dark'),
                    part([3.5,7,12],[12.5,13,14],'patina')]
    crusher+=ring(13,4,15,1.5)+ring(11.8,5,13.5,.9,'patina')
    crusher+=[part([4,4,1.1],[12,5.6,2],'dark'),part([5,4.4,.8],[11,5.1,1.1],'copper')]
    rollers=[part([3.7,9,4.5],[12.3,11,7.5],'dark'),part([3.7,9,8.5],[12.3,11,11.5],'dark')]
    export('resonance_crusher',crusher,(),rollers)

    press=base()+[part([3,3.4,3],[13,5,13],'patina'),part([4.5,5,4.5],[11.5,5.8,11.5],'dark'),
                  part([1.5,3.4,5],[4,14.5,11],'dark'),part([12,3.4,5],[14.5,14.5,11],'dark'),
                  part([1,14,4],[15,16,12],'copper'),part([6,12,6],[10,14,10],'patina')]
    press+=[part([2,6,4.6],[3.5,12,5],'copper'),part([12.5,6,4.6],[14,12,5],'copper')]
    ram=[part([7,9,7],[9,12,9],'copper'),part([4.5,8,4.5],[11.5,9,11.5],'dark')]
    export('copper_press',press,(),ram)

    purifier=base()+posts(3.4,13.8)+ring(3.4,3.4,14.5,1.0,'patina')+ring(12.9,3.4,14.5,1.4)
    purifier+=[part([5,14.3,5],[11,15.3,11],'dark'),part([6.5,15.3,6.5],[9.5,16,9.5],'copper')]
    export('vortex_purifier',purifier,windows(4.5,12.8),[part([7.6,5,7.6],[8.4,13,8.4],'copper'),part([4,7,7.5],[12,7.7,8.5],'dark')])

    crystallizer=base()+posts(3.4,14.5)+ring(3.4,3.7,14,1.3,'patina')+ring(13.2,3.7,14,1.3)
    crystallizer+=[part([5,4.7,5],[11,5.5,11],'dark'),part([5,14.5,5],[11,15.4,11],'patina'),
                   part([6.5,15.4,6.5],[9.5,16,9.5],'aqua')]
    crystal=part([6.5,7,6.5],[9.5,11.5,9.5],'aqua',{'origin':[8,9.25,8],'axis':'y','angle':45})
    export('resonance_crystallizer',crystallizer,windows(5,13),[crystal])

    etcher=base()+[part([2,3.4,2],[14,4.7,14],'patina')]+ring(7.7,3.7,14.5,1.2)
    etcher+=[part([1.5,3.4,10.8],[3,12.8,13.5],'dark'),part([13,3.4,10.8],[14.5,12.8,13.5],'dark'),
             part([1.5,12.8,10.8],[14.5,14,13.5],'copper')]
    export('circuit_etcher',etcher,windows(4.7,7.7),[part([4,9.2,5],[12,10.2,6.3],'copper'),part([5,6.1,5],[11,6.5,11],'patina')])

    assembler=base()+[part([2,3.4,2],[14,5,14],'patina'),part([3,5,3],[13,5.5,13],'dark'),
                      part([1.5,3.4,11],[3.3,14,13.5],'dark'),part([12.7,3.4,11],[14.5,14,13.5],'dark'),
                      part([1.5,14,10.7],[14.5,15.4,13.8],'copper')]
    for x in (4,7,10):
        for z in (4,7,10):assembler.append(part([x,5.5,z],[x+2,5.7,z+2],'copper'))
    export('precision_assembler',assembler,(),[part([7,11.8,5],[9,13.3,13],'patina'),part([7.2,8.7,5.2],[8.8,11.8,6.8],'copper'),part([7.6,8.1,5.6],[8.4,8.7,6.4],'aqua')])

    pump=base()+[part([2,3.4,2],[14,5,14],'patina')]+posts(5,12.5)+ring(12.5,3.5,14.5,1.3)
    pump+=[part([6,13.8,6],[10,15,10],'dark'),part([5,15,5],[11,16,11],'copper'),
            part([5,5,5],[11,6,11],'dark')]
    export('copper_fluid_pump',pump,windows(5.1,12.5),[part([7.5,6,7.5],[8.5,12.5,8.5],'copper'),part([4.5,9,7.5],[11.5,9.7,8.5],'patina')])

    tank=base()+posts(3.4,14.5)+ring(3.4,3.3,14.5,.9,'patina')+ring(13.5,3.3,14.5,1.2)
    tank+=[part([4,14.7,4],[12,15.5,12],'dark'),part([6,15.5,6],[10,16,10],'copper')]
    for y in (5,7,9,11):tank.append(part([12.9,y,1.5],[13.4,y+.25,2.1],'dark'))
    export('copper_fluid_tank',tank,windows(4.3,13.4))

    controller=base()+[part([2,3.4,3],[14,13,14],'patina'),part([1.5,12.5,2],[14.5,14,14.5],'copper'),
                       part([2.5,5,1],[13.5,12,3],'copper'),part([3.4,5.8,.7],[12.6,11.3,1],'dark'),
                       part([3,3.4,.8],[13,4.8,3],'dark'),part([4,4.5,.5],[5.5,5,.8],'copper'),
                       part([6,4.5,.5],[7.5,5,.8],'copper'),part([8,4.5,.5],[9.5,5,.8],'copper'),
                       part([3,14,11],[4,16,12],'dark'),part([12,14,11],[13,15.3,12],'copper')]
    export('production_controller',controller,(),[part([4.2,9,.58],[10.8,9.3,.68],'aqua'),part([4.2,7.7,.58],[8.7,8,.68],'aqua')])

    workbench=base()+[part([3,3.4,3],[13,5,13],'patina'),part([4,5,3.5],[12,5.7,10.5],'dark'),
                      part([7,5,11],[9,14,13],'dark'),part([3,13,10],[13,14.5,13],'copper'),
                      part([3,10,10],[4.5,13,12],'patina'),part([11.5,10,10],[13,13,12],'patina'),
                      part([5.7,10,10.7],[10.3,12.8,11.5],'dark'),part([6.5,11,10.3],[9.5,12,10.7],'copper'),
                      part([1.5,3.4,5],[3,7.5,10],'copper'),part([13,3.4,5],[14.5,7.5,10],'copper')]
    export('armor_workbench',workbench,(),[part([3,7.3,5.3],[4,8.3,9.7],'aqua'),part([12,7.3,5.3],[13,8.3,9.7],'aqua')])


if __name__=='__main__':
    models()
    print(f'Exported {len(MACHINES)} industrial model families and editable Blockbench sources.')
