package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.compat.jei.UnusualCopperJeiPlugin;
import io.github.gerbesh.unusualcopper.recipe.SolarRecipe;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.List;

/** Isolated real-client JEI registration and rendering smoke test. Never enabled in production. */
@Mod.EventBusSubscriber(modid=UnusualCopper.MOD_ID,value=Dist.CLIENT)
public final class JeiSmokeTest {
    // Do not resolve JEI classes during subscriber initialization: other dev smokes run without JEI.
    private static int ticks,screenTicks,exitTicks;
    private static boolean opened,screenshotRequested,saved,closed;

    @SubscribeEvent public static void clientTick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!Boolean.getBoolean("unusualcopper.jeiSmoke"))return;
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
        if(++ticks>900)throw new IllegalStateException("CuE JEI CLIENT SMOKE TIMEOUT: opened="+opened+", screen="+(mc.screen==null?"null":mc.screen.getClass().getName()));
        var type=RecipeType.create(UnusualCopper.MOD_ID,"solar_infusion",SolarRecipe.class);
        if(!opened) {
            var runtime=UnusualCopperJeiPlugin.activeRuntime();
            if(runtime==null)return;
            var manager=runtime.getRecipeManager();
            var category=manager.getRecipeCategory(type);
            var recipes=manager.createRecipeLookup(type).get().toList();
            if(recipes.size()!=4)throw new IllegalStateException("JEI registered "+recipes.size()+" solar recipes, expected 4");
            var ingots=recipes.stream().filter(r->r.ingredient().test(Items.COPPER_INGOT.getDefaultInstance())).toList();
            if(ingots.size()!=1||ingots.get(0).multiplier()!=2||recipes.stream().filter(r->r.multiplier()==1).count()!=3)
                throw new IllegalStateException("JEI recipe multipliers differ from server datapack");
            boolean catalyst=manager.createRecipeCatalystLookup(type).getItemStack()
                    .anyMatch(s->s.is(ModContent.MACHINES.get(io.github.gerbesh.unusualcopper.machine.MachineKind.SOLAR_INFUSER).get().asItem()));
            if(!catalyst)throw new IllegalStateException("JEI solar infuser catalyst is missing");
            long serverRecipes=mc.level.getRecipeManager().getAllRecipesFor(ModContent.SOLAR_RECIPE.get()).size();
            if(serverRecipes!=4)throw new IllegalStateException("Minecraft exposed "+serverRecipes+" solar recipes");
            UnusualCopper.LOGGER.info("CuE JEI CLIENT REGISTRY PASS: {} actual registered recipes, real input multipliers and matching infuser catalyst; title={}", recipes.size(),category.getTitle().getString());
            runtime.getRecipesGui().showTypes(List.of(type));
            opened=true;
            return;
        }
        if(saved&&!closed){
            // A JEI screen must be removed BEFORE JEI runtime cleanup in Minecraft.stop().
            mc.setScreen(null);
            closed=true;
            return;
        }
        if(closed){
            if(++exitTicks>=4){
                UnusualCopper.LOGGER.info("CuE JEI CLIENT PASS: four real recipes, correct catalyst, rendered screenshot and clean GUI teardown");
                mc.stop();
            }
            return;
        }
        if(mc.screen==null)return;
        if(++screenTicks>=35&&!screenshotRequested) {
            screenshotRequested=true;
            Screenshot.grab(mc.gameDirectory,"cue-jei-solar.png",mc.getMainRenderTarget(),message->{
                UnusualCopper.LOGGER.info("CuE JEI CLIENT SCREENSHOT: {} ({})",message.getString(),mc.screen==null?"closed":mc.screen.getClass().getName());
                saved=true;
            });
        }
    }
    private JeiSmokeTest(){}
}
