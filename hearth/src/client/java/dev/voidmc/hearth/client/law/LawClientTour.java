package dev.voidmc.hearth.client.law;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.law.item.LawItems;
import dev.voidmc.hearth.law.lock.LockpickSessions;
import dev.voidmc.hearth.law.lock.Locks;
import dev.voidmc.hearth.law.net.LawPayloads;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A scripted look at the law's screens and HUD in a singleplayer world, for screenshots without anyone at the keyboard.
 * Off unless {@code -Dhearth.dev.law.tour=true}; start the client straight into a world with live settlements (e.g. a
 * copy of the dev server's world: {@code --quickPlaySingleplayer <world>}). It walks the player up to a locked door or
 * chest in the nearest town (the red hand and the lock come from the real server), kneels at it with lockpicks (the real
 * lockpicking screen), then shows the pickpocketing and confrontation screens, the sneaking eye and the wanted line with
 * made-up data (they need embodied folk, which are the npc module's), and writes {@code run/screenshots/hearth_law_*.png}.
 */
final class LawClientTour {
	private static int ticks;
	private static @Nullable BlockPos lockPos;

	private LawClientTour() {
	}

	static void install() {
		if (!Boolean.getBoolean("hearth.dev.law.tour")) {
			return;
		}
		ClientTickEvents.END_CLIENT_TICK.register(LawClientTour::tick);
	}

	private static void tick(Minecraft mc) {
		MinecraftServer server = mc.getSingleplayerServer();
		if (mc.player == null || mc.level == null || server == null) {
			return;
		}
		ticks++;
		switch (ticks) {
			case 60 -> server.execute(() -> walkToLock(server, mc));
			case 160 -> shot(mc, "red_hand");
			case 170 -> server.execute(() -> kneel(server, mc));
			case 200 -> shot(mc, "lockpick");
			case 205 -> {
				if (mc.gui.screen() instanceof LockpickScreen s) {
					s.devPose(0.55F, 0.6F);
				}
			}
			case 215 -> shot(mc, "lockpick_strain");
			case 220 -> {
				mc.gui.setScreen(null);
				pickpocket(mc);
			}
			case 245 -> shot(mc, "pickpocket");
			case 250 -> {
				mc.gui.setScreen(null);
				confront(mc);
			}
			case 275 -> shot(mc, "confront");
			case 280 -> {
				mc.gui.setScreen(null);
				LawHud.stealth(new LawPayloads.Stealth(true, 0.3F, false));
				LawHud.status(new LawPayloads.Status("Ashbrook", 45, false, 0, 0, false, false));
			}
			case 300 -> shot(mc, "hud_sneaking");
			case 305 -> LawHud.stealth(new LawPayloads.Stealth(true, 0.9F, true));
			case 310 -> shot(mc, "hud_seen");
			case 320 -> {
				LawHud.status(new LawPayloads.Status("Ashbrook", 0, false, 0, 20 * 263, false, false));
				LawHud.stealth(new LawPayloads.Stealth(false, 0, false));
			}
			case 330 -> shot(mc, "hud_jail");
			case 340 -> {
				LawHud.reset();
				mc.gui.setScreen(new InventoryScreen(mc.player));
				ContainerOwnerOverlay.accept(new LawPayloads.ContainerOwner(mc.player.inventoryMenu.containerId, Component.literal("Edda Miller")));
			}
			case 360 -> shot(mc, "container_owner");
			case 380 -> mc.stop();
			default -> {
			}
		}
	}

	/** On the server: finds a lockable block in a town's built plots and stands the player in front of it, looking at it. */
	private static void walkToLock(MinecraftServer server, Minecraft mc) {
		ServerPlayer player = player(server, mc);
		HearthWorld world = player == null ? null : HearthWorld.of(player.level());
		if (world == null) {
			Hearth.LOG.info("[law tour] no Hearth world");
			return;
		}
		ServerLevel level = player.level();
		List<Settlement> towns = new ArrayList<>(world.settlements());
		towns.sort(java.util.Comparator.comparingDouble(s -> s.center().distSqr(player.blockPosition())));
		for (Settlement s : towns) {
			for (PlotView p : s.plots()) {
				if (p.state() != PlotState.BUILT) {
					continue;
				}
				var spec = p.spec();
				for (int x = spec.origin().getX(); x < spec.origin().getX() + spec.sizeX(); x++) {
					for (int z = spec.origin().getZ(); z < spec.origin().getZ() + spec.sizeZ(); z++) {
						for (int y = spec.floorY(); y < spec.floorY() + 6; y++) {
							BlockPos pos = new BlockPos(x, y, z);
							level.getChunkAt(pos);
							BlockState state = level.getBlockState(pos);
							Locks.Lock lock = Locks.lockable(state) ? Locks.at(level, pos, state) : null;
							if (lock == null) {
								continue;
							}
							BlockPos stand = standing(level, pos);
							if (stand == null) {
								continue;
							}
							lockPos = pos;
							Vec3 eye = Vec3.atBottomCenterOf(stand).add(0, player.getEyeHeight(), 0);
							Vec3 at = Vec3.atCenterOf(pos);
							double dx = at.x - eye.x;
							double dy = at.y - eye.y;
							double dz = at.z - eye.z;
							float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
							float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
							player.teleportTo(level, stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, java.util.Set.of(), yaw, pitch, true);
							Hearth.LOG.info("[law tour] at {} in {}: {} tier {} locked {} ({} of plot {} {})", pos, s.name(), lock.door() ? "door" : "chest",
								lock.tier(), lock.locked(), lock.owner().kind(), p.id(), spec.archetype());
							return;
						}
					}
				}
			}
		}
		Hearth.LOG.info("[law tour] no lock found in {} towns", towns.size());
	}

	private static @Nullable BlockPos standing(ServerLevel level, BlockPos lock) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos p = lock.relative(d, 2);
			for (int dy = -1; dy <= 1; dy++) {
				BlockPos q = p.above(dy);
				if (level.getBlockState(q).isAir() && level.getBlockState(q.above()).isAir() && level.getBlockState(q.below()).isSolid()) {
					return q;
				}
			}
		}
		return null;
	}

	/** On the server: lockpicks in hand, and use the lock (the real path: UseBlockCallback would do the same). */
	private static void kneel(MinecraftServer server, Minecraft mc) {
		ServerPlayer player = player(server, mc);
		if (player == null || lockPos == null) {
			return;
		}
		ServerLevel level = player.level();
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(LawItems.LOCKPICK, 5));
		BlockState state = level.getBlockState(lockPos);
		Locks.Lock lock = Locks.at(level, lockPos, state);
		if (lock != null) {
			LockpickSessions.start(player, lock, new BlockHitResult(Vec3.atCenterOf(lockPos), Direction.UP, lockPos, false));
		}
	}

	private static void pickpocket(Minecraft mc) {
		List<ItemStack> items = List.of(new ItemStack(Items.GOLD_NUGGET, 7), LawItems.key(new dev.voidmc.hearth.law.item.KeyData(1, 1, "Edda Miller's cottage",
			"Ashbrook")), new ItemStack(Items.BREAD, 2), new ItemStack(Items.IRON_NUGGET, 5), ItemStack.EMPTY);
		mc.gui.setScreen(new PickpocketScreen(new LawPayloads.Pocket(-1, "Edda Miller", true, items, 0.42F, 0.0F, 1, 0.55F, 0.0F,
			LawPayloads.Pocket.OPEN)));
	}

	private static void confront(Minecraft mc) {
		List<LawPayloads.Choice> choices = List.of(
			new LawPayloads.Choice("pay", Component.translatable("hearth.law.choice.pay", "4 marks, 5 bits"), true),
			new LawPayloads.Choice("submit", Component.translatable("hearth.law.choice.jail", 5), true),
			new LawPayloads.Choice("pillory", Component.translatable("hearth.law.choice.pillory", 3), true),
			new LawPayloads.Choice("persuade", Component.translatable("hearth.law.choice.persuade", 41), true),
			new LawPayloads.Choice("bribe", Component.translatable("hearth.law.choice.bribe", "2 marks, 7 bits", 38), false),
			new LawPayloads.Choice("flee", Component.translatable("hearth.law.choice.flee"), true),
			new LawPayloads.Choice("resist", Component.translatable("hearth.law.choice.resist"), true));
		mc.gui.setScreen(new ConfrontScreen(new LawPayloads.Confront(mc.player.getId(), true, "Wulf Harding",
			Component.translatable("hearth.law.guard.halt", Component.translatable("hearth.offence.theft"), 45), 45, choices)));
	}

	private static @Nullable ServerPlayer player(MinecraftServer server, Minecraft mc) {
		return mc.player == null ? null : server.getPlayerList().getPlayer(mc.player.getUUID());
	}

	private static void shot(Minecraft mc, String name) {
		Screenshot.grab(mc.gameDirectory, "hearth_law_" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
			message -> Hearth.LOG.info("[law tour] {}", message.getString()));
	}
}
