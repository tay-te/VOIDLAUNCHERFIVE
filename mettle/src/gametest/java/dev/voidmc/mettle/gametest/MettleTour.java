package dev.voidmc.mettle.gametest;

import dev.voidmc.mettle.api.CharacterAttribute;
import dev.voidmc.mettle.client.character.BackgroundPickerScreen;
import dev.voidmc.mettle.client.character.CharacterScreen;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/**
 * The client tour: a fresh survival world, the background picker opening by itself, every state of it at several
 * window sizes and GUI scales, choosing a background end to end, and the K screen. Screenshots land in
 * {@code build/run/clientGameTest/screenshots}.
 */
public class MettleTour implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (BalanceReport.enabled()) {
			// A balance run (-Dmettle.balance=true) only measures; BalanceReport does that.
			return;
		}
		// Load every class Mettle mixes into, so a broken injection fails the tour instead of a player's game.
		for (String target : new String[] {"net.minecraft.world.inventory.AnvilMenu", "net.minecraft.world.entity.npc.villager.Villager",
			"net.minecraft.world.item.BlockItem", "net.minecraft.world.level.block.entity.BrewingStandBlockEntity",
			"net.minecraft.client.gui.screens.inventory.AnvilScreen", "net.minecraft.world.entity.player.Player",
			"net.minecraft.world.entity.LivingEntity", "net.minecraft.world.level.block.Block", "net.minecraft.world.item.ItemStack",
			"net.minecraft.world.entity.monster.Creeper", "net.minecraft.world.item.BowItem", "net.minecraft.world.item.CrossbowItem",
			"net.minecraft.world.entity.projectile.arrow.AbstractArrow", "net.minecraft.advancements.triggers.InventoryChangeTrigger",
			"net.minecraft.world.food.FoodData"}) {
			try {
				Class.forName(target);
			} catch (ClassNotFoundException e) {
				throw new AssertionError(e);
			}
		}
		size(context, 1920, 1080, 3);
		try (TestSingleplayerContext world = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> {
				s.setSeed("8675309");
				s.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
				s.setAllowCommands(true);
			})
			.create()) {
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("time set 1000");
			world.getServer().runCommand("weather clear");
			world.getConnection().waitForChunksRender();

			// The picker opens on its own on first join.
			context.waitForScreen(BackgroundPickerScreen.class);
			context.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
			context.waitTicks(10);
			context.takeScreenshot("mettle_picker_1a_1080p");

			pick(context, s -> s.select("ranger"));
			context.takeScreenshot("mettle_picker_1b_ranger_1080p");

			pick(context, s -> {
				s.select("wanderer");
				s.placePoints(CharacterAttribute.STRENGTH, 1);
				s.placePoints(CharacterAttribute.AGILITY, 2);
				s.placePoints(CharacterAttribute.WITS, 1);
				s.pickSkill("mining");
			});
			context.takeScreenshot("mettle_picker_1c_wanderer_1080p");

			// Other window sizes and GUI scales: the panels follow the window, not the GUI scale; text stays 1:1.
			pick(context, s -> s.select("ranger"));
			size(context, 1920, 1080, 2);
			context.takeScreenshot("mettle_picker_1b_1080p_gui2");
			size(context, 1920, 1080, 4);
			context.takeScreenshot("mettle_picker_1b_1080p_gui4");
			size(context, 2560, 1440, 3);
			context.takeScreenshot("mettle_picker_1b_1440p");
			size(context, 1366, 768, 3);
			context.takeScreenshot("mettle_picker_1b_768p");
			size(context, 1280, 720, 2);
			context.takeScreenshot("mettle_picker_1b_720p");
			size(context, 1920, 1080, 3);

			// Begin as Ranger: the server accepts, the picker closes, the kit arrives.
			pick(context, BackgroundPickerScreen::begin);
			context.waitFor(mc -> mc.gui.screen() == null, 100);
			context.waitTicks(5);
			context.takeScreenshot("mettle_after_begin_hint");
			String problem = world.getServer().computeOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				CharacterState state = CharacterStore.get(player);
				if (state.background().isEmpty() || !state.background().get().identifier().getPath().equals("ranger")) {
					return "background not recorded: " + state;
				}
				if (!player.getInventory().contains(Items.BOW.getDefaultInstance())) {
					return "kit missing a bow";
				}
				// Kit gear is worn, not carried: the Ranger starts with the quiver on. Take it off so later journeys
				// count arrows from the inventory as they expect.
				if (!dev.voidmc.mettle.api.MettleServices.gear().worn(player, dev.voidmc.mettle.api.GearSlot.QUIVER).is(dev.voidmc.mettle.gear.GearItems.QUIVER)) {
					return "kit quiver not worn";
				}
				dev.voidmc.mettle.gear.GearInventory.of(player).takeAll();
				double capacity = player.getAttributeValue(dev.voidmc.mettle.core.MettleAttributes.CARRY_CAPACITY);
				// Ranger: Strength 3, so base + 3 × per point.
				double want = dev.voidmc.mettle.core.MettleAttributes.BASE_CARRY_CAPACITY + 3 * dev.voidmc.mettle.core.EffectsApplier.CAPACITY_PER_STRENGTH;
				if (capacity != want) {
					return "carry capacity " + capacity + ", expected " + want;
				}
				return null;
			});
			if (problem != null) {
				throw new AssertionError(problem);
			}

			// -Dmettle.tour.cards_only=true plays only the card journeys (cards v2), for a quick check.
			if (Boolean.getBoolean("mettle.tour.cards_only")) {
				DraftJourney.run(context, world);
				CardJourney.run(context, world);
				ShrineJourney.run(context, world);
				TradeJourney.run(context, world);
				MythicJourney.run(context, world);
			}
			// -Dmettle.tour.hearth_only=true (with -Phearth) plays only the Hearth journey, for a quick check.
			if (!Boolean.getBoolean("mettle.tour.hearth_only") && !Boolean.getBoolean("mettle.tour.cards_only")) {
				// Progress moments first, while the character is fresh (it is put back as it was).
				MomentJourney.run(context, world);
				signaturePerks(world);
				miningJourney(world);
				BladesJourney.run(world);
				HeavyWeaponsJourney.run(world);
				MarksmanshipJourney.run(world);
				DefenseJourney.run(world);
				BurdenJourney.run(context, world);
				GearJourney.run(context, world);
				AthleticsJourney.run(context, world);
				SurvivalJourney.run(context, world);
				SmithingJourney.run(context, world);
				TomeJourney.run(world);
				RespecJourney.run(context, world);
				DraftJourney.run(context, world);
				CardJourney.run(context, world);
				ShrineJourney.run(context, world);
				TradeJourney.run(context, world);
				MythicJourney.run(context, world);
			}
			// With VOID Hearth loaded (-Phearth): its slots filled by Mettle. HearthJourney names Hearth types, so it is
			// only touched then.
			if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("hearth")) {
				HearthJourney.run(world);
			}

			context.setScreen(CharacterScreen::new);
			context.waitTicks(5);
			context.takeScreenshot("mettle_character_k_1080p");
			context.setScreen(() -> null);
		}
	}

	private static void pick(ClientGameTestContext context, java.util.function.Consumer<BackgroundPickerScreen> action) {
		context.runOnClient(mc -> {
			if (mc.gui.screen() instanceof BackgroundPickerScreen s) {
				action.accept(s);
			}
		});
		context.waitTicks(3);
	}

	private static void size(ClientGameTestContext context, int w, int h, int guiScale) {
		context.getInput().resizeWindow(w, h);
		context.runOnClient(mc -> {
			mc.options.guiScale().set(guiScale);
			mc.resizeGui();
		});
		context.waitTicks(3);
	}

	/** Each event-driven signature perk, triggered for real on the server. Ends with the player back as a Ranger. */
	private static void signaturePerks(TestSingleplayerContext world) {
		var server = world.getServer();
		server.runCommand("gamerule spawn_mobs false");
		server.runCommand("difficulty easy");

		// Rally: a hostile kill heals a heart.
		server.runCommand("mettle background set mettle:soldier @a");
		server.runCommand("effect clear @a");
		check(server.computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
			p.setHealth(10f);
			return null;
		}));
		server.runCommand("execute at @p run summon minecraft:zombie ~3 ~ ~ {NoAI:1b,Tags:[\"mettle_test\"]}");
		server.runCommand("damage @e[tag=mettle_test,limit=1] 100 minecraft:player_attack by @p");
		check(server.computeOnServer(s -> {
			float health = s.getPlayerList().getPlayers().getFirst().getHealth();
			return health == 12f ? null : "Rally: health " + health + ", expected 12";
		}));

		// Backstab: a sneaking hit on a mob that is not hunting you does double damage. A pig, because a zombie's natural
		// armour would shave the doubled hit.
		server.runCommand("mettle background set mettle:rogue @a");
		server.runCommand("execute at @p run summon minecraft:pig ~3 ~ ~ {NoAI:1b,Tags:[\"mettle_back\"]}");
		check(server.computeOnServer(s -> {
			s.getPlayerList().getPlayers().getFirst().setShiftKeyDown(true);
			return null;
		}));
		server.runCommand("damage @e[tag=mettle_back,limit=1] 4 minecraft:player_attack by @p");
		check(server.computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
			p.setShiftKeyDown(false);
			var zombie = p.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(8),
				z -> z.entityTags().contains("mettle_back")).stream().findFirst();
			if (zombie.isEmpty()) {
				return "Backstab: test pig missing";
			}
			float health = zombie.get().getHealth();
			zombie.get().discard();
			return health == zombie.get().getMaxHealth() - 8f ? null : "Backstab: pig at " + health + ", expected " + (zombie.get().getMaxHealth() - 8f);
		}));

		// Field Dressing: a chicken always drops exactly one raw chicken, so a second roll shows as two.
		server.runCommand("mettle background set mettle:ranger @a");
		server.runCommand("kill @e[type=item]");
		server.runCommand("execute at @p run summon minecraft:chicken ~2 ~ ~ {NoAI:1b,Tags:[\"mettle_hen\"]}");
		server.runCommand("damage @e[tag=mettle_hen,limit=1] 100 minecraft:player_attack by @p");
		check(server.computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
			int raw = p.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, p.getBoundingBox().inflate(8),
				e -> e.getItem().is(Items.CHICKEN)).stream().mapToInt(e -> e.getItem().getCount()).sum();
			return raw == 2 ? null : "Field Dressing: " + raw + " raw chicken, expected 2";
		}));
		server.runCommand("kill @e[type=item]");

		// Herb Lore: a brewed Swiftness (3:00) becomes 4:30 and is marked.
		check(server.computeOnServer(s -> {
			var stack = net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION, net.minecraft.world.item.alchemy.Potions.SWIFTNESS);
			dev.voidmc.mettle.character.signature.HerbLore.extend(stack);
			var contents = stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
			int duration = contents.getAllEffects().iterator().next().getDuration();
			if (duration != 5400 || !stack.has(dev.voidmc.mettle.character.signature.SignaturePerks.HERB_LORE_BREWED)) {
				return "Herb Lore: duration " + duration + ", expected 5400";
			}
			return contents.getName("item.minecraft.potion.effect.").getString().contains("Swiftness") ? null : "Herb Lore: lost its name";
		}));
	}


	/**
	 * A Mining career end to end, the way a player would go: XP, ranked roots, attribute points, two Excavator perks,
	 * the Motherlode mastery breaking a real vein, a feat, a synergy, and a respec that keeps what was earned.
	 */
	private static void miningJourney(TestSingleplayerContext world) {
		var server = world.getServer();
		String learn = "execute as @p run mettle perk learn mettle:mining/";
		server.runCommand("mettle background set mettle:smith @a");
		float[] base = new float[1];
		check(server.computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
			p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(Items.IRON_PICKAXE));
			base[0] = p.getDestroySpeed(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
			return null;
		}));

		server.runCommand("mettle xp mettle:mining 800 @a");
		for (int i = 0; i < 3; i++) {
			server.runCommand(learn + "quarryman");
		}
		server.runCommand(learn + "prospector");
		check(server.computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
			var state = CharacterStore.get(p);
			if (state.level(dev.voidmc.mettle.character.skill.MiningSkill.MINING) != 5) {
				return "Mining level " + state.level(dev.voidmc.mettle.character.skill.MiningSkill.MINING) + ", expected 5";
			}
			if (dev.voidmc.mettle.character.Perks.rank(p, dev.voidmc.mettle.character.skill.MiningSkill.QUARRYMAN) != 3
				|| !dev.voidmc.mettle.character.Perks.has(p, dev.voidmc.mettle.character.skill.MiningSkill.PROSPECTOR)) {
				return "roots not learned: " + state.perks();
			}
			float speed = p.getDestroySpeed(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
			return Math.abs(speed - base[0] * 1.3f) < 0.01f ? null : "Quarryman III: speed " + speed + ", expected " + base[0] * 1.3f;
		}));

		// To Mining 18; character levels buy Strength up to the mastery gate (Smith starts at 6).
		server.runCommand("mettle xp mettle:mining 22000 @a");
		for (int i = 0; i < 8; i++) {
			server.runCommand("execute as @p run mettle attribute strength");
		}
		server.runCommand(learn + "tunneler");
		server.runCommand(learn + "rock_hauler");
		server.runCommand(learn + "motherlode");
		check(server.computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
			if (!dev.voidmc.mettle.character.Perks.has(p, dev.voidmc.mettle.character.skill.MiningSkill.MOTHERLODE)) {
				return "Motherlode not learned: " + CharacterStore.get(p).perks() + " strength " + CharacterStore.get(p).spent();
			}
			// A natural vein of five iron ore; sneak-mining the first breaks them all.
			var level = p.level();
			net.minecraft.core.BlockPos start = p.blockPosition().offset(2, 3, 0);
			for (int i = 0; i < 5; i++) {
				level.setBlock(start.offset(i, 0, 0), net.minecraft.world.level.block.Blocks.IRON_ORE.defaultBlockState(), 3);
			}
			p.setShiftKeyDown(true);
			p.gameMode.destroyBlock(start);
			p.setShiftKeyDown(false);
			for (int i = 0; i < 5; i++) {
				if (!level.getBlockState(start.offset(i, 0, 0)).isAir()) {
					return "Motherlode left ore at +" + i;
				}
			}
			return null;
		}));

		// A feat and a synergy are earned, never bought.
		check(server.computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
			dev.voidmc.mettle.character.FeatCounters.add(p, "mine_diamond_ore", 64);
			return dev.voidmc.mettle.character.Perks.has(p, dev.voidmc.mettle.character.skill.MiningSkill.DIAMOND_HANDS) ? null : "Diamond Hands not earned";
		}));
		server.runCommand("mettle xp mettle:smithing 5000 @a");
		check(server.computeOnServer(s -> dev.voidmc.mettle.character.Perks.has(s.getPlayerList().getPlayers().getFirst(),
			dev.voidmc.mettle.character.skill.MiningSkill.FORGEMASTER) ? null : "Forgemaster not unlocked at Mining 18 and Smithing 10"));

		// A respec refunds bought perks and attribute points, and keeps XP, feats and synergies.
		server.runCommand("execute as @p run mettle respec");
		check(server.computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
			var state = CharacterStore.get(p);
			if (dev.voidmc.mettle.character.Perks.has(p, dev.voidmc.mettle.character.skill.MiningSkill.MOTHERLODE) || !state.spent().isEmpty()) {
				return "respec kept bought things: " + state.perks() + " " + state.spent();
			}
			if (!dev.voidmc.mettle.character.Perks.has(p, dev.voidmc.mettle.character.skill.MiningSkill.DIAMOND_HANDS)
				|| !dev.voidmc.mettle.character.Perks.has(p, dev.voidmc.mettle.character.skill.MiningSkill.FORGEMASTER)
				|| state.level(dev.voidmc.mettle.character.skill.MiningSkill.MINING) != 18) {
				return "respec lost earned things: " + state;
			}
			float speed = p.getDestroySpeed(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
			return Math.abs(speed - base[0]) < 0.01f ? null : "speed after respec " + speed + ", expected " + base[0];
		}));
		server.runCommand("mettle background set mettle:ranger @a");
	}

	private static void check(String problem) {
		if (problem != null) {
			throw new AssertionError(problem);
		}
	}
}
