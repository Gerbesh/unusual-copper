"""Reactor architecture and component models in the existing copper art style. No extractor edits."""
from generate_assets import ASSETS, ROOT, PALETTE, part, clean_surfaces, write_json, write_bbmodel
import json


def export(name,parts,block=False):
    materials=list(PALETTE)+['glass']
    textures={m:'unusualcopper:block/'+m for m in materials};textures['particle']='unusualcopper:block/copper'
    model={'render_type':'minecraft:translucent' if name=='reactor_assembly' else 'minecraft:cutout','textures':textures,
           'elements':clean_surfaces(parts),'display':{'gui':{'rotation':[25,225,0],'scale':[.65,.65,.65]},
                                                    'ground':{'scale':[.3,.3,.3]},'firstperson_righthand':{'scale':[.6,.6,.6]}}}
    write_json(ASSETS/f'models/{"block" if block else "item"}/{name}.json',model)
    write_bbmodel(name,parts,materials)


def generate():
    # A single building block, physically identical in all 36 positions.
    brick=[part([0,0,0],[16,16,16],'dark')]
    for y in (0,14.6):brick.extend([part([0,y,-.03],[16,y+1.4,.06],'copper'),part([0,y,15.94],[16,y+1.4,16.03],'copper')])
    for x in (0,14.6):brick.extend([part([x,1.4,-.04],[x+1.4,14.6,.07],'copper'),part([x,1.4,15.93],[x+1.4,14.6,16.04],'copper')])
    brick.extend([part([3,3,-.05],[13,13,.01],'patina'),part([5,6,-.08],[11,7,-.05],'aqua'),part([5,9,-.08],[11,10,-.05],'copper')])
    export('reactor_block',brick,True)
    write_json(ASSETS/'models/item/reactor_block.json',{'parent':'unusualcopper:block/reactor_block'})
    write_json(ASSETS/'blockstates/reactor_block.json',{'variants':{'formed=false':{'model':'unusualcopper:block/reactor_block'},'formed=true':{'model':'unusualcopper:block/reactor_assembly'}}})
    # Normalized mesh: world renderer scales to exactly 3×4×3 blocks.
    body=[part([0,0,0],[16,1.3,16],'dark'),part([.2,1.3,.2],[15.8,2.4,15.8],'copper'),
          part([.8,2.4,.8],[15.2,4.3,15.2],'patina'),part([.4,13.8,.4],[15.6,15,15.6],'copper'),
          part([1,15,1],[15,16,15],'dark'),part([5,4.3,5],[11,5,11],'dark'),part([5,13.1,5],[11,13.8,11],'patina')]
    for x in (.6,13.9):
        for z in (.6,13.9):
            body.append(part([x,4.3,z],[x+1.5,13.8,z+1.5],'copper'))
            for y in (5,8.7,12.7):body.append(part([x-.12,y,z-.12],[x+1.62,y+.45,z+1.62],'dark'))
    # Cooling fins along four lower walls. The fans are separate interpolated geometry.
    for axis in range(4):
        for x in (2.8,4.1,5.4,6.7,8,9.3,10.6,11.9):
            a=[x,2.5,.25];b=[x+.55,4.1,.65]
            points=[]
            for xx in (a[0],b[0]):
                for zz in (a[2],b[2]):
                    px,pz=xx,zz
                    for _ in range(axis):px,pz=pz,16-px
                    points.append((px,pz))
            body.append(part([min(p[0] for p in points),a[1],min(p[1] for p in points)],[max(p[0] for p in points),b[1],max(p[1] for p in points)],'dark'))
    # Eight lower power sockets align with real pipe centres on the physical block grid.
    outlets=[(.5,.0,0),(1.5,.0,0),(3,.5,1),(3,1.5,1),(2.5,3,2),(1.5,3,2),(0,2.5,3),(0,1.5,3)]
    for x,z,side in outlets:
        cx,cz=x*16/3,z*16/3
        if side in (0,2):
            low,high=(-.03,.4) if side==0 else (15.6,16.03)
            body.append(part([cx-1.35,1.2,low],[cx+1.35,2.8,high],'copper'))
            low,high=(-.06,-.03) if side==0 else (16.03,16.06)
            body.append(part([cx-.8,1.55,low],[cx+.8,2.45,high],'aqua'))
        else:
            low,high=(-.03,.4) if side==3 else (15.6,16.03)
            body.append(part([low,1.2,cz-1.35],[high,2.8,cz+1.35],'copper'))
            low,high=(-.06,-.03) if side==3 else (16.03,16.06)
            body.append(part([low,1.55,cz-.8],[high,2.45,cz+.8],'aqua'))
    glass=[part([2.12,4.35,1.04],[13.88,13.75,1.08],'glass'),part([2.12,4.35,14.92],[13.88,13.75,14.96],'glass'),
           part([1.04,4.35,2.12],[1.08,13.75,13.88],'glass'),part([14.92,4.35,2.12],[14.96,13.75,13.88],'glass')]
    for x,z in ((8,2),(14,8),(8,14),(2,8)):
        body.append(part([x-1,8.4,z-.6],[x+1,9.6,z+.6],'copper'))
    body.extend([part([7.6,4.3,.94],[8.4,13.8,1.5],'copper'),part([7.6,4.3,14.5],[8.4,13.8,15.06],'copper'),
                 part([.94,4.3,7.6],[1.5,13.8,8.4],'copper'),part([14.5,4.3,7.6],[15.06,13.8,8.4],'copper')])
    export('reactor_assembly',body+glass,True)
    lens=[part([2,2,6],[14,14,10],'dark'),part([3,3,5],[13,13,6],'copper'),
          part([4.5,4.5,4.4],[11.5,11.5,5],'aqua',{'origin':[8,8,5],'axis':'z','angle':45})]
    for x,z in ((1,7),(13,7)):lens.append(part([x,6,z],[x+2,10,z+2],'copper'))
    export('reactor_diamond_lens',lens)
    filt=[part([2,2,6],[14,14,9],'dark'),part([1,1,6],[15,3,10],'copper'),part([1,13,6],[15,15,10],'copper')]
    for x in (4,7,10):filt.append(part([x,3,5.4],[x+.7,13,5.9],'aqua'))
    for y in (4,7,10):filt.append(part([3,y,5],[13,y+.7,5.4],'copper'))
    export('reactor_diamond_filter',filt)
    insulation=[part([1,2,4],[15,14,12],'dark'),part([1,2,3.5],[15,3.5,4],'copper'),part([1,12.5,3.5],[15,14,4],'copper'),part([7.3,3.5,3.3],[8.7,12.5,3.5],'patina')]
    export('reactor_insulation',insulation)
    concentrate=[part([4,2,4],[12,3.5,12],'copper'),part([4.5,3.5,4.5],[11.5,12,11.5],'patina'),
                 part([3.8,12,3.8],[12.2,13,12.2],'copper'),part([5.5,13,5.5],[10.5,15,10.5],'dark'),part([5.5,5,4.1],[10.5,10,4.5],'aqua')]
    export('coolant_concentrate',concentrate)
    bucket=[part([4,2,4],[12,3,12],'dark'),part([4,3,4],[5,11,12],'dark'),part([11,3,4],[12,11,12],'dark'),
            part([5,3,4],[11,11,5],'dark'),part([5,3,11],[11,11,12],'dark'),part([5,3,5],[11,9.5,11],'aqua'),
            part([3,9,7.4],[4,15,8.6],'dark'),part([12,9,7.4],[13,15,8.6],'dark'),part([3,14,7.4],[13,15,8.6],'copper')]
    export('reactor_coolant_bucket',bucket)
    write_json(ASSETS/'models/block/reactor_fluid_empty.json',{'textures':{'particle':'minecraft:block/water_still'},'elements':[]})
    write_json(ASSETS/'blockstates/reactor_coolant.json',{'variants':{'':{'model':'unusualcopper:block/reactor_fluid_empty'}}})
    recipes=ROOT/'src/main/resources/data/unusualcopper/recipes'
    def shaped(name,pattern,key,count=1):
        write_json(recipes/f'{name}.json',{'type':'minecraft:crafting_shaped','pattern':pattern,'key':{k:{'item':v} for k,v in key.items()},'result':{'item':'unusualcopper:'+name,'count':count}})
    shaped('reactor_block',['POP','GFG','PCP'],{'P':'unusualcopper:copper_plate','O':'minecraft:obsidian','G':'minecraft:glass','F':'unusualcopper:reinforced_frame','C':'unusualcopper:etched_circuit'},4)
    shaped('reactor_diamond_lens',['COC','ADA','COC'],{'C':'unusualcopper:copper_wire_spool','O':'minecraft:obsidian','A':'minecraft:amethyst_shard','D':'minecraft:diamond'})
    shaped('reactor_diamond_filter',['CDC','DND','CDC'],{'C':'unusualcopper:copper_wire_spool','D':'minecraft:diamond','N':'minecraft:quartz'})
    shaped('reactor_insulation',['POP','OFO','POP'],{'P':'unusualcopper:copper_plate','O':'minecraft:obsidian','F':'unusualcopper:reinforced_frame'})
    write_json(recipes/'coolant_concentrate.json',{'type':'minecraft:crafting_shapeless','ingredients':[{'item':'minecraft:snowball'}]*4+[{'item':'minecraft:amethyst_shard'},{'item':'unusualcopper:washed_copper_dust'}],'result':{'item':'unusualcopper:coolant_concentrate'}})
    write_json(recipes/'industrial/mix_reactor_coolant.json',{'type':'unusualcopper:industrial_processing','machine':'vortex_purifier','ingredient':{'item':'unusualcopper:coolant_concentrate'},'input_count':1,'result':{'item':'unusualcopper:reactor_coolant_bucket'},'ticks':40,'cue':200,'water_mb':1000,'coolant_mb':1000})
    write_json(ROOT/'src/main/resources/data/unusualcopper/loot_tables/blocks/reactor_block.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'unusualcopper:reactor_block'}]}]})
    for index,lang in enumerate(('en_us','ru_ru')):
        path=ASSETS/f'lang/{lang}.json';data=json.loads(path.read_text(encoding='utf-8'))
        data.update({key:pair[index] for key,pair in json.loads((ROOT/'tools/reactor_translations.json').read_text(encoding='utf-8')).items()});write_json(path,data)
    tag=ROOT/'src/generated/resources/data/minecraft/tags/blocks/mineable/pickaxe.json'
    data=json.loads(tag.read_text(encoding='utf-8'))
    if 'unusualcopper:reactor_block' not in data['values']:data['values'].append('unusualcopper:reactor_block')
    write_json(tag,data)
    write_json(ROOT/'src/main/resources/data/minecraft/tags/blocks/needs_diamond_tool.json',{'replace':False,'values':['unusualcopper:reactor_block']})
    names=['reactor_block','reactor_diamond_lens','reactor_diamond_filter','reactor_insulation','coolant_concentrate']
    write_json(ROOT/'src/main/resources/data/unusualcopper/advancements/recipes/reactor_components.json',{'criteria':{'material':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':['unusualcopper:reinforced_frame']}]}}},'requirements':[['material']],'rewards':{'recipes':['unusualcopper:'+n for n in names]}})
    print('Reactor block, normalized assembly, four components, coolant bucket, recipes and Blockbench sources exported.')


if __name__=='__main__':generate()
