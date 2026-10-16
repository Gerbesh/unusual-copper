package io.github.gerbesh.unusualcopper.menu;

import io.github.gerbesh.unusualcopper.gameplay.ReaperSettings;
import io.github.gerbesh.unusualcopper.gameplay.*;
import io.github.gerbesh.unusualcopper.item.AreaWandItem;
import io.github.gerbesh.unusualcopper.network.AreaNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import java.util.*;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.network.ReaperNetwork;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;

/** Settings-only menu; loot and machine inventory are never sent through this channel. */
public final class ReaperMenu extends AbstractContainerMenu {
    public final BlockPos pos;
    private final CopperMachineBlockEntity machine;
    private final Player viewer;
    private ReaperSettings sent, snapshot;
    private long lastSettingsTick = Long.MIN_VALUE;
    public int snapshotSerial, result;
    public ReaperNetwork.Reply view;
    private final UUID catalogueId=UUID.randomUUID();
    private MobPopulations.Ledger catalogue;
    private long lastReply=Long.MIN_VALUE,lastScan=Long.MIN_VALUE;
    private static final ReaperRule ALL=new ReaperRule(true,false,Set.of(),Set.of(),EnumSet.allOf(MobCategory.class));
    private void scan(){if(machine.workArea()!=null&&AreaAccess.valid((ServerLevel)machine.getLevel(),pos,machine.workArea(),machine.owner())){var next=MobPopulations.ledger((ServerLevel)machine.getLevel(),catalogueId,pos,machine.workArea(),ALL);if(next!=catalogue){catalogue=next;catalogue.enableCatalogue();}long now=machine.getLevel().getGameTime();if(lastScan!=now){catalogue.step(64);lastScan=now;}}else catalogue=null;}
    public ReaperMenu(int id, Inventory inventory, FriendlyByteBuf buffer) { this(id,inventory,null,buffer.readBlockPos()); }
    public ReaperMenu(int id, Inventory inventory, CopperMachineBlockEntity machine) { this(id,inventory,machine,machine.getBlockPos()); }
    private ReaperMenu(int id, Inventory inventory, CopperMachineBlockEntity machine, BlockPos pos) {
        super(ModContent.REAPER_MENU.get(),id); this.machine=machine; this.pos=pos; viewer=inventory.player;
    }
    public static void open(ServerPlayer player, CopperMachineBlockEntity machine) {
        if (machine.reaper != null && machine.canAccess(player)) NetworkHooks.openScreen(player,new SimpleMenuProvider((id,inventory,p) -> new ReaperMenu(id,inventory,machine),machine.getDisplayName()),machine.getBlockPos());
    }
    @Override public boolean stillValid(Player player) {
        return machine == null || (player==viewer && player.level()==machine.getLevel() && !machine.isRemoved() && machine.reaper != null && player.level().hasChunkAt(pos) && player.level().getBlockEntity(pos)==machine && machine.canAccess(player) && player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64 && player.level() instanceof ServerLevel level && ProtectionPolicy.allowed(level,pos,machine.owner()));
    }
    @Override public ItemStack quickMoveStack(Player player,int slot) { return ItemStack.EMPTY; }
    @Override public boolean clickMenuButton(Player player,int button) {
        if (machine == null || !stillValid(player) || !(player instanceof ServerPlayer server)) return false;
        if(button==0){NetworkHooks.openScreen(server,machine,pos);return true;}
        if(button==1){if(!AreaWandItem.beginReaperSelection(server,machine))reply(server,4);return true;}
        if(button==2&&machine.workArea()!=null){AreaNetwork.preview(server,machine.workArea().min(),machine.workArea().max(),true);return true;}return false;
    }
    public ReaperSettings snapshot() { return snapshot; }
    public void receive(ReaperNetwork.Reply reply) { if (reply.menu()==containerId && reply.pos().equals(pos)) { view=reply;snapshot=reply.values(); result=reply.result(); snapshotSerial++; } }
    private void reply(ServerPlayer player,int result) {
        sent=ReaperSettings.of(machine.reaper.rule,machine.reaper.trigger,machine.reaper.keep);
        var species=new ArrayList<ReaperNetwork.Species>();if(catalogue!=null)for(var s:catalogue.catalogue())species.add(new ReaperNetwork.Species(net.minecraft.world.entity.EntityType.getKey(s.type()),s.total(),s.adults()));
        for(var type:machine.reaper.rule.types())if(species.stream().noneMatch(s->s.type().equals(type)))species.add(new ReaperNetwork.Species(type,0,0));
        ReaperNetwork.reply(player,new ReaperNetwork.Reply(containerId,pos,sent,result,machine.runtimeIdentity,machine.workArea(),catalogue!=null&&catalogue.ready(),species));lastReply=player.level().getGameTime();
    }
    public boolean choose(ReaperNetwork.Choose choice,ServerPlayer player){
        if(machine==null||!stillValid(player)||!machine.runtimeIdentity.equals(choice.runtime())||choice.area()==null||!choice.area().equals(machine.workArea()))return false;
        scan();var values=choice.values();
        try{var rule=values.validate();if(catalogue==null||!catalogue.ready()||!rule.tags().isEmpty()||!rule.categories().isEmpty()||!rule.allowPassive())throw new IllegalArgumentException();
            var available=new HashSet<net.minecraft.resources.ResourceLocation>();catalogue.catalogue().forEach(s->available.add(net.minecraft.world.entity.EntityType.getKey(s.type())));available.addAll(machine.reaper.rule.types());if(!available.containsAll(rule.types()))throw new IllegalArgumentException();return configure(values,player);
        }catch(IllegalArgumentException invalid){reply(player,2);return false;}
    }
    public boolean configure(ReaperSettings settings,ServerPlayer player) {
        if (machine == null || !stillValid(player)) return false;
        long now=player.level().getGameTime();
        if (lastSettingsTick!=Long.MIN_VALUE && now-lastSettingsTick<2) { reply(player,3); return false; }
        lastSettingsTick=now;
        try {
            var rule=settings.validate();
            machine.reaper.settings(rule,settings.trigger(),settings.keep()); machine.setChanged(); reply(player,1); return true;
        } catch (IllegalArgumentException invalid) { reply(player,2); return false; }
    }
    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (machine != null && viewer instanceof ServerPlayer server && stillValid(server)) {
            var current=ReaperSettings.of(machine.reaper.rule,machine.reaper.trigger,machine.reaper.keep);
            scan();if (!current.equals(sent)||lastReply==Long.MIN_VALUE||server.level().getGameTime()-lastReply>=10) reply(server,0);
        }
    }
}
