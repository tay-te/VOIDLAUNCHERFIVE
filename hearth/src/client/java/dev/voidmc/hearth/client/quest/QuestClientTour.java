package dev.voidmc.hearth.client.quest;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import dev.voidmc.hearth.quest.block.NoticeBoardBlock;
import dev.voidmc.hearth.quest.model.Objective;
import dev.voidmc.hearth.quest.model.QuestInstance;
import dev.voidmc.hearth.quest.model.QuestType;
import dev.voidmc.hearth.quest.service.QuestManager;
import dev.voidmc.hearth.quest.service.QuestStore;
import dev.voidmc.hearth.quest.world.QuestSandbox;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * A scripted look at the quest module in a singleplayer world, for screenshots without anyone at the keyboard. Off
 * unless {@code -Dhearth.dev.quest.tour=true}; start the client straight into a world (e.g. with
 * {@code --quickPlaySingleplayer}). It founds a sandbox town in front of the player on the integrated server, opens the
 * notice board, reads an offer, takes three quests, shows the HUD tracker and the journal, hands in a supply quest, and
 * writes {@code run/screenshots/hearth_quest_*.png}, then quits.
 */
final class QuestClientTour {
	private static int ticks;
	private static @Nullable SimpleSettlement town;
	private static @Nullable BlockPos boardPos;

	private QuestClientTour() {
	}

	static void install() {
		if (!Boolean.getBoolean("hearth.dev.quest.tour")) {
			return;
		}
		ClientTickEvents.END_CLIENT_TICK.register(QuestClientTour::tick);
	}

	private static void tick(Minecraft mc) {
		MinecraftServer server = mc.getSingleplayerServer();
		if (mc.player == null || mc.level == null || server == null) {
			return;
		}
		ticks++;
		switch (ticks) {
			case 60 -> server.execute(() -> setUp(server, mc));
			case 140 -> shot(mc, "board_block");
			case 160 -> server.execute(() -> {
				ServerPlayer p = player(server, mc);
				if (p != null && town != null && boardPos != null) {
					NoticeBoardBlock.open(p, p.level(), town, boardPos);
				}
			});
			case 190 -> shot(mc, "board");
			case 200 -> {
				if (mc.gui.screen() instanceof NoticeBoardScreen board) {
					board.readFirst();
				}
			}
			case 220 -> shot(mc, "board_reading");
			case 230 -> {
				mc.gui.setScreen(null);
				server.execute(() -> takeQuests(server, mc));
			}
			case 280 -> shot(mc, "hud");
			case 300 -> mc.gui.setScreen(new JournalScreen());
			case 330 -> shot(mc, "journal");
			case 340 -> {
				mc.gui.setScreen(null);
				server.execute(() -> handIn(server, mc));
			}
			case 352 -> shot(mc, "complete");
			case 420 -> mc.stop();
			default -> {
			}
		}
	}

	private static @Nullable ServerPlayer player(MinecraftServer server, Minecraft mc) {
		return mc.player == null ? null : server.getPlayerList().getPlayer(mc.player.getUUID());
	}

	private static void setUp(MinecraftServer server, Minecraft mc) {
		ServerPlayer p = player(server, mc);
		if (p == null) {
			return;
		}
		ServerLevel level = p.level();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set 1500");
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "weather clear");
		BlockPos at = p.blockPosition();
		BlockPos ground = new BlockPos(at.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()), at.getZ());
		town = QuestSandbox.create(level, ground, Direction.NORTH, Culture.TEMPERATE, 18);
		BlockPos front = ground.relative(Direction.NORTH, 2);
		for (int dy = -3; dy <= 3; dy++) {
			BlockPos b = new BlockPos(front.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, front.getX(), front.getZ()) + dy, front.getZ());
			if (level.getBlockState(b).getBlock() instanceof NoticeBoardBlock) {
				boardPos = b;
			}
		}
		// Stand back a little and look at the board.
		BlockPos stand = ground.relative(Direction.SOUTH, 2);
		p.connection.teleport(stand.getX() + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, stand.getX(), stand.getZ()), stand.getZ() + 0.5,
			180F, 12F);
		Hearth.LOG.info("[quest tour] sandbox {} with board at {}", town.name(), boardPos);
	}

	private static void takeQuests(MinecraftServer server, Minecraft mc) {
		ServerPlayer p = player(server, mc);
		if (p == null || town == null) {
			return;
		}
		List<QuestInstance> offers = List.copyOf(QuestStore.board(town).offers);
		int taken = 0;
		for (QuestInstance q : offers) {
			if (taken < 3 && (q.type != QuestType.SUPPLY || taken == 0) && QuestManager.accept(p, town, q.id)) {
				taken++;
			}
		}
		// Track the second quest taken, so the HUD shows a needle toward somewhere out in the land.
		var active = QuestStore.get(p).active;
		if (active.size() > 1) {
			QuestManager.track(p, active.get(1).id);
		}
	}

	private static void handIn(MinecraftServer server, Minecraft mc) {
		ServerPlayer p = player(server, mc);
		if (p == null || town == null) {
			return;
		}
		for (QuestInstance q : QuestStore.get(p).active) {
			if (q.type == QuestType.SUPPLY) {
				Objective o = q.objectives.getFirst();
				ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(o.target)));
				int count = o.count;
				while (count > 0) {
					int n = Math.min(count, stack.getMaxStackSize());
					p.getInventory().add(new ItemStack(stack.getItem(), n));
					count -= n;
				}
			}
		}
		QuestManager.handIn(p, town, null);
	}

	private static void shot(Minecraft mc, String name) {
		Screenshot.grab(mc.gameDirectory, "hearth_quest_" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
			message -> Hearth.LOG.info("[quest tour] {}", message.getString()));
	}
}
