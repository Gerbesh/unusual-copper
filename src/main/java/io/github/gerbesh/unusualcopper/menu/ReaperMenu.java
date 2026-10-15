package io.github.gerbesh.unusualcopper.menu;

import io.github.gerbesh.unusualcopper.gameplay.ReaperSettings;
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
    public ReaperMenu(int id, Inventory inventory, FriendlyByteBuf buffer) { this(id,inventory,null,buffer.readBlockPos()); }
    public ReaperMenu(int id, Inventory inventory, CopperMachineBlockEntity machine) { this(id,inventory,machine,machine.getBlockPos()); }
    private ReaperMenu(int id, Inventory inventory, CopperMachineBlockEntity machine, BlockPos pos) {
        super(ModContent.REAPER_MENU.get(),id); this.machine=machine; this.pos=pos; viewer=inventory.player;
    }
    public static void open(ServerPlayer player, CopperMachineBlockEntity machine) {
        if (machine.reaper != null && machine.canAccess(player)) NetworkHooks.openScreen(player,new SimpleMenuProvider((id,inventory,p) -> new ReaperMenu(id,inventory,machine),machine.getDisplayName()),machine.getBlockPos());
    }
    @Override public boolean stillValid(Player player) {
        return machine == null || (!machine.isRemoved() && machine.reaper != null && player.level().hasChunkAt(pos) && player.level().getBlockEntity(pos)==machine && machine.canAccess(player) && player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64);
    }
    @Override public ItemStack quickMoveStack(Player player,int slot) { return ItemStack.EMPTY; }
    @Override public boolean clickMenuButton(Player player,int button) {
        if (button != 0 || machine == null || !stillValid(player) || !(player instanceof ServerPlayer server)) return false;
        NetworkHooks.openScreen(server,machine,pos); return true;
    }
    public ReaperSettings snapshot() { return snapshot; }
    public void receive(ReaperNetwork.Reply reply) { if (reply.menu()==containerId && reply.pos().equals(pos)) { snapshot=reply.values(); result=reply.result(); snapshotSerial++; } }
    private void reply(ServerPlayer player,int result) {
        sent=ReaperSettings.of(machine.reaper.rule,machine.reaper.trigger,machine.reaper.keep);
        ReaperNetwork.reply(player,new ReaperNetwork.Reply(containerId,pos,sent,result));
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
            if (!current.equals(sent)) reply(server,0);
        }
    }
}
