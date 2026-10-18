package io.github.gerbesh.unusualcopper.client;

import io.github.gerbesh.unusualcopper.UnusualCopper;
import io.github.gerbesh.unusualcopper.gameplay.AreaSelectionGeometry;
import io.github.gerbesh.unusualcopper.item.AreaWandItem;
import io.github.gerbesh.unusualcopper.network.AreaNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = UnusualCopper.MOD_ID, value = Dist.CLIENT)
public final class AreaPreviewRenderer {
    private static AreaNetwork.Preview preview;
    private static AreaNetwork.Selection selection;
    private static net.minecraft.client.multiplayer.ClientLevel world;
    private static long expires;
    public static void clear() { preview = null;selection=null; }
    public static void selection(AreaNetwork.Selection value) {
        var level=Minecraft.getInstance().level;
        if(level==null||!level.dimension().location().equals(value.dimension()))return;
        world=level;selection=value;
    }
    public static void accept(AreaNetwork.Preview value) {
        var level = Minecraft.getInstance().level; if (level == null || !level.dimension().location().equals(value.dimension())) return;
        world=level;preview = value; expires = level.getGameTime() + 240;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(world!=mc.level||mc.player==null){world=mc.level;clear();}
    }
    private static boolean matches(ItemStack stack) {
        var tag=stack.getTag();return stack.getItem() instanceof AreaWandItem&&tag!=null&&tag.getInt("Mode")==0
                &&tag.hasUUID(AreaWandItem.PREVIEW_SESSION)&&tag.getUUID(AreaWandItem.PREVIEW_SESSION).equals(selection.token());
    }
    private static AreaSelectionGeometry.Candidate candidate() {
        var mc=Minecraft.getInstance();
        if(selection==null||mc.level==null||mc.player==null||mc.screen!=null||!mc.level.dimension().location().equals(selection.dimension())
                ||!(matches(mc.player.getMainHandItem())||matches(mc.player.getOffhandItem())))return null;
        BlockPos second=mc.hitResult instanceof BlockHitResult hit&&hit.getType()==HitResult.Type.BLOCK?hit.getBlockPos():selection.first();
        return AreaSelectionGeometry.between(selection.anchor(),selection.first(),second,selection.mining());
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) {
        var mc=Minecraft.getInstance();if(mc.options.hideGui)return;
        var area=candidate();if(area==null)return;
        var g=event.getGuiGraphics();int width=g.guiWidth(),height=g.guiHeight();
        int color=area.valid()?0xff86efd5:0xffff9980;
        var dimensions=area.mining()?Component.translatable("wand.unusualcopper.preview_mining",area.width(),area.depth())
                :Component.translatable("wand.unusualcopper.preview_size",area.width(),area.height(),area.depth(),area.width()*area.height()*area.depth());
        String key=area.problem()==AreaSelectionGeometry.Problem.TOO_LARGE?(area.mining()?"preview_limit_mining":"preview_limit")
                :area.problem()==AreaSelectionGeometry.Problem.TOO_FAR?"preview_range"
                :mc.hitResult==null||mc.hitResult.getType()!=HitResult.Type.BLOCK?"preview_aim":"preview_confirm";
        int panel=Math.min(width-16,Math.max(220,Math.max(mc.font.width(dimensions),mc.font.width(Component.translatable("wand.unusualcopper."+key))))+18);
        var lines=mc.font.split(Component.translatable("wand.unusualcopper."+key),panel-16);
        int h=27+lines.size()*10,x=(width-panel)/2,y=Math.max(4,height-64-h);
        g.fill(x,y,x+panel,y+h,0xe6152e2a);g.fill(x,y,x+3,y+h,color);
        g.drawString(mc.font,dimensions,x+9,y+7,color,false);
        for(int i=0;i<lines.size();i++)g.drawString(mc.font,lines.get(i),x+9,y+22+i*10,0xffe0dfca,false);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var mc = Minecraft.getInstance();
        if(mc.level==null||mc.player==null)return;
        var live=candidate();
        if(live==null&&(preview==null||mc.level.getGameTime()>=expires||!mc.level.dimension().location().equals(preview.dimension()))){preview=null;return;}
        var min=live==null?preview.min():live.min();var max=live==null?preview.max():live.max();
        var box = new AABB(Math.min(min.getX(), max.getX()), Math.min(min.getY(), max.getY()), Math.min(min.getZ(), max.getZ()),
                (double)Math.max(min.getX(), max.getX()) + 1, (double)Math.max(min.getY(), max.getY()) + 1, (double)Math.max(min.getZ(), max.getZ()) + 1);
        var pose = event.getPoseStack(); var camera = event.getCamera().getPosition(); var buffers = mc.renderBuffers().bufferSource();
        boolean valid=live==null?preview.valid():live.valid();
        pose.pushPose(); pose.translate(-camera.x, -camera.y, -camera.z);
        var lines=buffers.getBuffer(AreaRenderTypes.LINES);
        LevelRenderer.renderLineBox(pose,lines,box,valid?.2F:1F,valid?1F:.2F,valid?.8F:.2F,.9F);
        if(live!=null) {
            var first=selection.first();if(live.mining())first=new BlockPos(first.getX(),selection.anchor().getY(),first.getZ());
            // A copper corner marker distinguishes the fixed first block from the moving bounds.
            LevelRenderer.renderLineBox(pose,lines,new AABB(first).inflate(.015),1F,.67F,.32F,1F);
        }
        buffers.endBatch(AreaRenderTypes.LINES);pose.popPose();
    }
}
