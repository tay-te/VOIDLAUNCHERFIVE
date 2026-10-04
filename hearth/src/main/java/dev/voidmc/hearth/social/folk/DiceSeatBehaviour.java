package dev.voidmc.hearth.social.folk;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.social.dice.DiceTables;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** A folk playing dice with a player stays at the table, faces them, and throws the bones on their turn. */
public final class DiceSeatBehaviour implements NpcService.FolkBehaviour {
	public static final Identifier ID = Hearth.id("social/dice_seat");
	public static final int PRIORITY = 150;

	@Override
	public Identifier id() {
		return ID;
	}

	@Override
	public int priority() {
		return PRIORITY;
	}

	private static ServerPlayer opponent(NpcService.FolkHandle folk) {
		UUID player = DiceTables.opponentOf(folk.resident().id);
		if (player == null || !(folk.entity().level() instanceof net.minecraft.server.level.ServerLevel level)) {
			return null;
		}
		ServerPlayer p = level.getServer().getPlayerList().getPlayer(player);
		return p != null && p.level() == level && p.distanceToSqr(folk.entity()) < 12 * 12 ? p : null;
	}

	@Override
	public boolean wants(NpcService.FolkHandle folk) {
		return opponent(folk) != null;
	}

	@Override
	public void start(NpcService.FolkHandle folk) {
		folk.stop();
		folk.hold(new ItemStack(Items.BONE));
	}

	@Override
	public boolean tick(NpcService.FolkHandle folk) {
		ServerPlayer p = opponent(folk);
		if (p == null) {
			return false;
		}
		folk.lookAt(p);
		if (DiceTables.takeMoved(folk.resident().id)) {
			folk.swing();
		}
		return true;
	}

	@Override
	public void stop(NpcService.FolkHandle folk) {
		folk.hold(ItemStack.EMPTY);
	}
}
