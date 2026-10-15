package io.github.gerbesh.unusualcopper.network;

import io.github.gerbesh.unusualcopper.UnusualCopper;
import io.github.gerbesh.unusualcopper.gameplay.PotionPlans;
import io.github.gerbesh.unusualcopper.menu.BrewerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.*;
import java.util.function.Consumer;

/** Recipe descriptions and owner menu actions only; never client supplied potion stacks. */
public final class BrewerNetwork {
    public record Settings(ResourceLocation potion,int vessel,int count,boolean maintain,int priority){}
    public record Configure(int menu,UUID runtime,Settings settings){}
    public record Option(PotionPlans.Plan plan,int costEach){}
    public record Need(ItemStack stack,int required,int available){public Need{stack=stack.copyWithCount(1);}@Override public ItemStack stack(){return stack.copy();}}
    public record Reply(int menu,BlockPos pos,UUID runtime,Settings settings,int result,List<Option> options,List<Need> needs,boolean sourceComplete,int energy,int status,int stage,int stages,int batch,boolean job,Option active){public Reply{pos=pos.immutable();options=List.copyOf(options);needs=List.copyOf(needs);}}
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(UnusualCopper.MOD_ID,"brewer"),()->"1","1"::equals,"1"::equals);
    private static Consumer<Reply> receiver=ignored->{};
    private static void settings(Settings s,FriendlyByteBuf b){b.writeResourceLocation(s.potion);b.writeByte(s.vessel);b.writeVarInt(s.count);b.writeBoolean(s.maintain);b.writeByte(s.priority);}
    private static Settings settings(FriendlyByteBuf b){return new Settings(b.readResourceLocation(),b.readUnsignedByte(),b.readVarInt(),b.readBoolean(),b.readUnsignedByte());}
    private static void option(Option o,FriendlyByteBuf b){var p=o.plan;b.writeResourceLocation(p.potion());b.writeByte(p.vessel());b.writeItem(p.output());b.writeVarInt(o.costEach);b.writeByte(p.steps().size());for(var step:p.steps()){b.writeItem(step.ingredient());b.writeItem(step.output());}}
    private static Option option(FriendlyByteBuf b){var potion=b.readResourceLocation();int vessel=b.readUnsignedByte();var output=b.readItem();int cost=b.readVarInt(),n=b.readUnsignedByte();if(n>PotionPlans.MAX_STEPS)throw new IllegalArgumentException("Too many brewing stages");var steps=new ArrayList<PotionPlans.Step>();for(int i=0;i<n;i++)steps.add(new PotionPlans.Step(b.readItem(),b.readItem()));return new Option(new PotionPlans.Plan(potion,vessel,output,steps),cost);}
    private static void reply(Reply r,FriendlyByteBuf b){
        b.writeVarInt(r.menu);b.writeBlockPos(r.pos);b.writeUUID(r.runtime);settings(r.settings,b);b.writeByte(r.result);b.writeVarInt(r.options.size());r.options.forEach(o->option(o,b));b.writeByte(r.needs.size());for(var n:r.needs){b.writeItem(n.stack());b.writeVarInt(n.required);b.writeVarInt(n.available);}b.writeBoolean(r.sourceComplete);b.writeVarInt(r.energy);b.writeVarInt(r.status);b.writeByte(r.stage);b.writeByte(r.stages);b.writeByte(r.batch);b.writeBoolean(r.job);if(r.job)option(r.active,b);
    }
    private static Reply reply(FriendlyByteBuf b){
        int menu=b.readVarInt();var pos=b.readBlockPos();var runtime=b.readUUID();var settings=settings(b);int result=b.readUnsignedByte(),n=b.readVarInt();if(n<0||n>PotionPlans.MAX_OPTIONS)throw new IllegalArgumentException("Too many brewing options");var options=new ArrayList<Option>();for(int i=0;i<n;i++)options.add(option(b));int count=b.readUnsignedByte();if(count>9)throw new IllegalArgumentException("Too many brewing reagents");var needs=new ArrayList<Need>();for(int i=0;i<count;i++)needs.add(new Need(b.readItem(),b.readVarInt(),b.readVarInt()));boolean complete=b.readBoolean();int energy=b.readVarInt(),status=b.readVarInt(),stage=b.readUnsignedByte(),stages=b.readUnsignedByte(),batch=b.readUnsignedByte();boolean job=b.readBoolean();return new Reply(menu,pos,runtime,settings,result,options,needs,complete,energy,status,stage,stages,batch,job,job?option(b):null);
    }
    public static void register(){
        CHANNEL.registerMessage(0,Configure.class,(v,b)->{b.writeVarInt(v.menu);b.writeUUID(v.runtime);settings(v.settings,b);},b->new Configure(b.readVarInt(),b.readUUID(),settings(b)),(v,supplier)->{var context=supplier.get();context.enqueueWork(()->{var player=context.getSender();if(player!=null&&player.containerMenu instanceof BrewerMenu menu&&menu.containerId==v.menu)menu.configure(v.runtime,v.settings,player);});context.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1,Reply.class,BrewerNetwork::reply,BrewerNetwork::reply,(v,supplier)->{var context=supplier.get();context.enqueueWork(()->receiver.accept(v));context.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void configure(int menu,UUID runtime,Settings settings){CHANNEL.sendToServer(new Configure(menu,runtime,settings));}
    public static void reply(ServerPlayer player,Reply reply){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),reply);}
    public static void clientReceiver(Consumer<Reply> handler){receiver=handler;}
    private BrewerNetwork(){}
}
