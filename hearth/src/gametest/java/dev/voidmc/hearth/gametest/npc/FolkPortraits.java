package dev.voidmc.hearth.gametest.npc;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.client.npc.ConversationScreen;
import dev.voidmc.hearth.npc.NpcConfig;
import dev.voidmc.hearth.npc.NpcModule;
import dev.voidmc.hearth.npc.dev.NpcSandbox;
import dev.voidmc.hearth.npc.dev.SandboxSettlement;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.npc.entity.FolkEntities;
import dev.voidmc.hearth.npc.entity.FolkPose;
import dev.voidmc.hearth.npc.talk.TalkPayloads;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Photographs hearthfolk for judging their looks and behaviour: line-ups of every culture in their trades, families
 * (children and elders), close-ups of faces, the poses of their work, a conversation, and a builder putting up a
 * watchtower on scaffolding. Screenshots land in build/run/clientGameTest/screenshots as {@code hearth_npc_*}.
 *
 * <p>Entrypoint: {@code dev.voidmc.hearth.gametest.npc.FolkPortraits}. System property {@code hearth.npc.tour.settle}
 * (ms per still shot, default 6000) and {@code hearth.npc.tour.build} (seconds to watch the builder, default 150); environment
 * {@code HEARTH_NPC_TOUR=builder} watches only the builder.
 */
public class FolkPortraits implements FabricClientGameTest {
	private static final long SETTLE_MS = Long.getLong("hearth.npc.tour.settle", 6_000L);
	private static final int BUILD_SECONDS = Integer.getInteger("hearth.npc.tour.build", 150);
	private static final int Y = 220;

	private record Spec(Culture culture, JobType job, AgeStage age, boolean female, FolkPose pose) {
		Spec(Culture culture, JobType job, boolean female) {
			this(culture, job, AgeStage.ADULT, female, FolkPose.NONE);
		}
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
			mc.options.renderDistance().set(5);
		});
		try (TestSingleplayerContext world = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> {
				s.setSeed("1234");
				s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
				s.setAllowCommands(true);
			})
			.create()) {
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("gamerule advance_weather false");
			world.getServer().runCommand("weather clear");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("gamemode spectator @a");
			world.getServer().runCommand(String.format("tp @a 0 %d -6 0 10", Y + 2));
			settle(context, 6_000);
			world.getServer().runCommand(String.format("fill -16 %d -12 16 %d 16 minecraft:grass_block", Y - 1, Y - 1));
			world.getServer().runCommand(String.format("fill -16 %d 4 16 %d 4 minecraft:oak_leaves", Y, Y + 3));

			// HEARTH_NPC_TOUR=builder (environment) skips the stills and watches only the builder.
			if ("builder".equals(System.getenv("HEARTH_NPC_TOUR"))) {
				builder(context, world);
				return;
			}
			lineup(context, world, "lineup_1", List.of(
				new Spec(Culture.TEMPERATE, JobType.BUILDER, false), new Spec(Culture.WISTERIA, JobType.MERCHANT, true),
				new Spec(Culture.NORTHERN, JobType.GUARD, false), new Spec(Culture.DESERT, JobType.PRIEST, true),
				new Spec(Culture.TROPICAL, JobType.FISHER, true), new Spec(Culture.KARST, JobType.SCHOLAR, false),
				new Spec(Culture.ALPINE, JobType.BAKER, true)));
			lineup(context, world, "lineup_2", List.of(
				new Spec(Culture.MEDITERRANEAN, JobType.FARMER, false), new Spec(Culture.STEPPE, JobType.HERDER, true),
				new Spec(Culture.MARITIME, JobType.FISHER, false), new Spec(Culture.MOORLAND, JobType.SMITH, false),
				new Spec(Culture.BAYOU, JobType.INNKEEPER, true), new Spec(Culture.MESA, JobType.TAILOR, true),
				new Spec(Culture.TUNDRA, JobType.HUNTER, false)));
			lineup(context, world, "lineup_3", List.of(
				new Spec(Culture.WISTERIA, JobType.NONE, true), new Spec(Culture.KARST, JobType.FARMER, true),
				new Spec(Culture.ALPINE, JobType.GUARD, false), new Spec(Culture.DESERT, JobType.MERCHANT, false),
				new Spec(Culture.MEDITERRANEAN, JobType.NONE, true), new Spec(Culture.NORTHERN, JobType.CARPENTER, true),
				new Spec(Culture.TEMPERATE, JobType.MINTER, false)));
			lineup(context, world, "families", List.of(
				new Spec(Culture.TEMPERATE, JobType.NONE, AgeStage.ELDER, false, FolkPose.NONE),
				new Spec(Culture.TEMPERATE, JobType.NONE, AgeStage.CHILD, true, FolkPose.CHEER),
				new Spec(Culture.TROPICAL, JobType.NONE, AgeStage.CHILD, false, FolkPose.NONE),
				new Spec(Culture.KARST, JobType.NONE, AgeStage.ELDER, true, FolkPose.NONE),
				new Spec(Culture.NORTHERN, JobType.NONE, AgeStage.CHILD, false, FolkPose.WAVE),
				new Spec(Culture.DESERT, JobType.NONE, AgeStage.ELDER, true, FolkPose.NONE),
				new Spec(Culture.WISTERIA, JobType.NONE, AgeStage.CHILD, true, FolkPose.NONE)));
			lineup(context, world, "poses", List.of(
				new Spec(Culture.MOORLAND, JobType.SMITH, AgeStage.ADULT, false, FolkPose.HAMMER),
				new Spec(Culture.KARST, JobType.SCHOLAR, AgeStage.ADULT, true, FolkPose.READ),
				new Spec(Culture.DESERT, JobType.PRIEST, AgeStage.ADULT, false, FolkPose.PRAY),
				new Spec(Culture.TEMPERATE, JobType.FARMER, AgeStage.ADULT, true, FolkPose.TEND),
				new Spec(Culture.ALPINE, JobType.BAKER, AgeStage.ADULT, false, FolkPose.CRAFT),
				new Spec(Culture.BAYOU, JobType.NONE, AgeStage.ADULT, true, FolkPose.TALK),
				new Spec(Culture.MARITIME, JobType.NONE, AgeStage.ADULT, false, FolkPose.COWER)));
			faces(context, world);
			conversation(context, world);
			builder(context, world);
		}
	}

	private void lineup(ClientGameTestContext context, TestSingleplayerContext world, String name, List<Spec> specs) {
		List<Integer> ids = world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			List<Integer> out = new ArrayList<>();
			for (int i = 0; i < specs.size(); i++) {
				double x = (i - (specs.size() - 1) / 2.0) * 1.25;
				out.add(spawnStill(level, specs.get(i), x, 0.0, i).getId());
			}
			return out;
		});
		shoot(context, world, name, 0.0, Y + 0.6, -4.4, 0.0F, 8.0F);
		clear(world, ids);
	}

	private void faces(ClientGameTestContext context, TestSingleplayerContext world) {
		List<Spec> specs = List.of(new Spec(Culture.WISTERIA, JobType.NONE, true), new Spec(Culture.NORTHERN, JobType.GUARD, false),
			new Spec(Culture.STEPPE, JobType.MERCHANT, false));
		List<Integer> ids = world.getServer().computeOnServer(server -> {
			List<Integer> out = new ArrayList<>();
			for (int i = 0; i < specs.size(); i++) {
				out.add(spawnStill(server.overworld(), specs.get(i), (i - 1) * 0.8, 0.0, 30 + i).getId());
			}
			return out;
		});
		shoot(context, world, "faces", 0.0, Y - 0.05, -1.7, 0.0F, 0.0F);
		clear(world, ids);
	}

	private void conversation(ClientGameTestContext context, TestSingleplayerContext world) {
		Spec spec = new Spec(Culture.TEMPERATE, JobType.BAKER, true);
		int id = world.getServer().computeOnServer(server -> spawnStill(server.overworld(), spec, 0.0, 0.0, 77).getId());
		world.getServer().runCommand(String.format("tp @a 0 %d -3 0 10", Y));
		settle(context, 2_000);
		context.setScreen(() -> new ConversationScreen(new TalkPayloads.Show(id, true, "Hester Thatcher", "hearth.npc.job.baker", "Ashby Green",
			Component.literal("Morning! Fresh from the oven this morning, if you're hungry. They've finished the windmill, too. Took long enough!"),
			List.of(new TalkPayloads.Option("npc:news", Component.translatable("hearth.npc.talk.news")),
				new TalkPayloads.Option("economy:trade", Component.literal("Let's trade.")),
				new TalkPayloads.Option("quest:offer/1", Component.literal("Need any help?")),
				new TalkPayloads.Option("npc:rumours", Component.translatable("hearth.npc.talk.rumours")),
				new TalkPayloads.Option("npc:about", Component.translatable("hearth.npc.talk.about")),
				new TalkPayloads.Option("npc:bye", Component.translatable("hearth.npc.talk.bye"))))));
		settle(context, 4_000);
		context.takeScreenshot("hearth_npc_conversation");
		context.setScreen(() -> null);
		clear(world, List.of(id));
	}

	/** A builder raising a watchtower in a sandbox camp, sped up: watch for scaffolding going up and coming down. */
	private void builder(ClientGameTestContext context, TestSingleplayerContext world) {
		// Mid-morning: working hours (at noon they would be at lunch).
		world.getServer().runCommand("time set 3000");
		world.getServer().runOnServer(server -> {
			NpcConfig c = NpcModule.config();
			NpcModule.setConfig(new NpcConfig(c.embodiment(), new NpcConfig.Ai(1, 1, 1, 64, 128, 20, 5, true), c.threats(), c.talk()));
			ServerLevel level = server.overworld();
			SandboxSettlement camp = NpcSandbox.camp(level, new BlockPos(0, Y, 0), Culture.ALPINE);
			NpcSandbox.build(level, camp, new BlockPos(-2, Y, 6), "tower", level.getRandom());
		});
		world.getServer().runCommand(String.format("fill -16 %d 4 16 %d 4 minecraft:air", Y, Y + 3));
		int shots = 6;
		for (int i = 0; i < shots; i++) {
			settle(context, BUILD_SECONDS * 1000L / shots);
			String progress = world.getServer().computeOnServer(server -> {
				StringBuilder sb = new StringBuilder();
				for (SandboxSettlement s : NpcSandbox.settlements(server.overworld())) {
					for (PlotView p : s.plots()) {
						sb.append(p.spec().archetype().getPath()).append(' ').append(p.state()).append(" stage ").append(p.stage()).append(" step ")
							.append(p.step()).append("; ");
					}
				}
				for (Folk f : NpcModule.embodiment().entities()) {
					if (f.brain() != null) {
						sb.append(f.folkName()).append(": ").append(f.brain().describe()).append(" at ").append(f.blockPosition().toShortString()).append("; ");
					}
				}
				return sb.toString();
			});
			System.out.println("[hearth npc tour] builder " + i + ": " + progress);
			shoot(context, world, "builder_" + i, 9.0, Y + 6.0, -4.0, 40.0F, 18.0F);
		}
	}

	private Folk spawnStill(ServerLevel level, Spec spec, double x, double z, int seed) {
		SandboxSettlement camp = NpcSandbox.camp(level, new BlockPos(0, Y, 0), spec.culture());
		RandomSource random = RandomSource.create(seed * 7919L + spec.culture().ordinal());
		Resident r = HearthServices.residents().create(camp, spec.age(), random, null, null);
		r.female = spec.female();
		r.job = spec.job();
		Folk folk = FolkEntities.FOLK.create(level, EntitySpawnReason.COMMAND);
		folk.setIdentity(r.id, r.fullName(), r.appearance, spec.culture(), r.job, r.age, r.female);
		folk.setNoAi(true);
		folk.snapTo(x, Y, z, 180.0F, 0.0F);
		folk.setYHeadRot(180.0F);
		folk.setYBodyRot(180.0F);
		folk.setFolkPose(spec.pose());
		folk.setItemSlot(EquipmentSlot.MAINHAND, held(spec));
		if (spec.job() == JobType.GUARD) {
			folk.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		}
		level.addFreshEntity(folk);
		return folk;
	}

	private static ItemStack held(Spec spec) {
		return switch (spec.job()) {
			case BUILDER -> new ItemStack(Items.OAK_PLANKS);
			case FARMER -> new ItemStack(Items.IRON_HOE);
			case GUARD -> new ItemStack(Items.IRON_SWORD);
			case SMITH -> new ItemStack(Items.MACE);
			case BAKER -> new ItemStack(Items.BREAD);
			case SCHOLAR, PRIEST -> new ItemStack(Items.BOOK);
			case FISHER -> new ItemStack(Items.FISHING_ROD);
			case HERDER -> new ItemStack(Items.WHEAT);
			case HUNTER -> new ItemStack(Items.BOW);
			case CARPENTER -> new ItemStack(Items.IRON_AXE);
			case INNKEEPER -> new ItemStack(Items.HONEY_BOTTLE);
			case MINTER -> new ItemStack(Items.GOLD_INGOT);
			case TAILOR -> new ItemStack(Items.SHEARS);
			default -> ItemStack.EMPTY;
		};
	}

	private void clear(TestSingleplayerContext world, List<Integer> ids) {
		world.getServer().runOnServer(server -> {
			for (int id : ids) {
				var e = server.overworld().getEntity(id);
				if (e instanceof Folk f) {
					f.markFolded();
					f.discard();
				}
			}
		});
	}

	private void shoot(ClientGameTestContext context, TestSingleplayerContext world, String name, double x, double y, double z, float yaw, float pitch) {
		world.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, Mth.wrapDegrees(yaw), pitch));
		settle(context, SETTLE_MS);
		context.takeScreenshot("hearth_npc_" + name);
	}

	private void settle(ClientGameTestContext context, long ms) {
		long until = System.currentTimeMillis() + ms;
		while (System.currentTimeMillis() < until) {
			context.waitTick();
		}
	}
}
