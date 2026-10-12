package dev.voidmc.mettle.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.Progress;
import dev.voidmc.mettle.character.cards.CardCooldowns;
import dev.voidmc.mettle.character.cards.CardHooks;
import dev.voidmc.mettle.character.cards.CardPhysics;
import dev.voidmc.mettle.character.cards.Cards;
import dev.voidmc.mettle.character.cards.Ceilings;
import dev.voidmc.mettle.character.cards.MythicEffects;
import dev.voidmc.mettle.character.cards.Mythics;
import dev.voidmc.mettle.character.skill.MiningSkill;
import dev.voidmc.mettle.client.overlay.MettleState;
import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.CardStore;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.DraftState;
import dev.voidmc.mettle.core.EffectsApplier;
import dev.voidmc.mettle.mixin.blades.BladesLivingAccess;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.storage.LevelData;

/**
 * The card effects as the production audit (2026-10-12) asked them fixed, on a real server: Hoard 4 never copies a
 * shulker box, a silk-touched ore or anything with data of its own; hits turned away by the hurt frames and spam clicks
 * never arm Weak Point; the movement and jump ceilings hold, and the deck says when a stat is capped; Heart of the
 * Mountain stops at obsidian; Homeward lands beside the bed. The cooldowns it starts are checked again after the world is
 * saved, quit and opened again ({@link #afterRestart}). The character is put back as it was; the cooldowns stay.
 */
final class CardEffectsJourney {
	private static final Identifier HOARD = Mettle.id("hoard");
	private static final Identifier KEEN = Mettle.id("keen");
	private static final Identifier SWIFT = Mettle.id("swift");
	private static final String TAG = "mettle_card_effects";

	private CardEffectsJourney() {
	}

	static void run(ClientGameTestContext context, TestSingleplayerContext world) {
		var server = world.getServer();
		CharacterState[] saved = new CharacterState[1];
		ItemStack[] held = new ItemStack[1];

		// Four Mining cards (Hoard and Keen) and two Blades cards (Keen): Hoard 4 and Keen 6 (two decks).
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			saved[0] = Cards.ensure(p);
			held[0] = p.getMainHandItem().copy();
			Cards.clear(p);
			level(s, p, "mining", 8);
			level(s, p, "blades", 5);
			level(s, p, "athletics", 2);
			level(s, p, "survival", 2);
			for (String perk : new String[] {"mining/quarryman", "mining/prospector", "mining/rock_hauler", "mining/tunneler"}) {
				String no = give(p, perk, List.of(HOARD, KEEN), List.of());
				if (no != null) {
					return no;
				}
			}
			for (String perk : new String[] {"blades/fencer", "blades/quick_draw"}) {
				String no = give(p, perk, List.of(KEEN), List.of());
				if (no != null) {
					return no;
				}
			}
			var cards = CardStore.get(p);
			return cards.tier(HOARD) >= 4 && cards.tier(KEEN) >= 6 ? null : "Hoard 4 and Keen 6 not active: " + cards.sets() + " active " + cards.tiers();
		}));

		// G1: Hoard 4 copies a natural drop one time in 25, and never a shulker box, a silk-touched ore, a placed ore or an
		// enchanted sword.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ServerLevel level = s.overworld();
			BlockPos pos = p.blockPosition().above(12);
			ItemStack box = new ItemStack(Items.SHULKER_BOX);
			box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND_BLOCK, 64))));
			ItemStack sword = new ItemStack(Items.IRON_SWORD);
			sword.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Heirloom"));
			if (CardHooks.plain(box) || CardHooks.plain(new ItemStack(Items.BUNDLE)) || CardHooks.plain(sword) || !CardHooks.plain(new ItemStack(Items.RAW_IRON, 3))) {
				return "plain stacks: box " + CardHooks.plain(box) + ", sword " + CardHooks.plain(sword);
			}
			Mob zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
			int boxes = 0;
			int ores = 0;
			int silk = 0;
			int swords = 0;
			int flesh = 0;
			for (int i = 0; i < 2000; i++) {
				boxes += CardHooks.extraDrops(p, level, pos, Blocks.SHULKER_BOX.defaultBlockState(), List.of(box)).size();
				ores += CardHooks.extraDrops(p, level, pos, Blocks.IRON_ORE.defaultBlockState(), List.of(new ItemStack(Items.RAW_IRON))).size();
				silk += CardHooks.extraDrops(p, level, pos, Blocks.IRON_ORE.defaultBlockState(), List.of(new ItemStack(Items.IRON_ORE))).size();
				for (ItemStack extra : CardHooks.extraLoot(p, zombie, List.of(sword, new ItemStack(Items.ROTTEN_FLESH, 2)))) {
					if (extra.is(Items.IRON_SWORD)) {
						swords++;
					} else {
						flesh++;
					}
				}
			}
			dev.voidmc.mettle.character.PlacedBlocks.mark(level, pos);
			int placed = 0;
			for (int i = 0; i < 500; i++) {
				placed += CardHooks.extraDrops(p, level, pos, Blocks.IRON_ORE.defaultBlockState(), List.of(new ItemStack(Items.RAW_IRON))).size();
			}
			dev.voidmc.mettle.character.PlacedBlocks.takePlaced(level, pos);
			zombie.discard();
			if (boxes > 0 || silk > 0 || swords > 0 || placed > 0) {
				return "Hoard 4 copied what it must not: shulker boxes " + boxes + ", silk-touched ores " + silk + ", named swords " + swords + ", placed ores " + placed;
			}
			if (ores < 30 || ores > 160 || flesh < 30 || flesh > 160) {
				return "Hoard 4 one time in 25: " + ores + " raw iron and " + flesh + " rotten flesh in 2000 (about 80 each)";
			}
			return null;
		}));

		// Minor: hits turned away by the hurt frames, and weak swings, never count for Weak Point (Keen 6); full swings do.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
			Mob zombie = EntityTypes.ZOMBIE.create(p.level(), EntitySpawnReason.COMMAND);
			zombie.setPos(p.getX() + 2, p.getY(), p.getZ());
			zombie.setNoAi(true);
			zombie.addTag(TAG);
			zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
			zombie.setHealth(1000);
			p.level().addFreshEntity(zombie);
			try {
				for (int i = 0; i < 3; i++) {
					((BladesLivingAccess) p).mettle$setAttackStrengthTicker(100);
					p.attack(zombie);
				}
				if (CardHooks.mark(zombie) > 0) {
					return "Weak Point armed from hits the hurt frames turned away";
				}
				for (int i = 0; i < 4; i++) {
					((BladesLivingAccess) p).mettle$setAttackStrengthTicker(0);
					zombie.damageCooldownTime = 0;
					p.attack(zombie);
				}
				if (CardHooks.mark(zombie) > 0) {
					return "Weak Point armed from spam clicks";
				}
				for (int i = 0; i < 3; i++) {
					((BladesLivingAccess) p).mettle$setAttackStrengthTicker(100);
					zombie.damageCooldownTime = 0;
					p.attack(zombie);
				}
				return CardHooks.mark(zombie) >= 0.2 ? null : "three full swings did not expose the target: mark " + CardHooks.mark(zombie);
			} finally {
				zombie.discard();
			}
		}));

		// G3 / G6: the cards' share of movement is held to +35 %, and a jump to under two blocks.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
			Identifier fake = Mettle.id("trait_timed/test_fleet");
			speed.addTransientModifier(new AttributeModifier(fake, 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			Ceilings.movement(p);
			double share = Ceilings.cardSum(speed);
			AttributeModifier correction = speed.getModifier(Mettle.id("ceiling/movement_speed"));
			speed.removeModifier(fake);
			Ceilings.movement(p);
			if (correction == null || share + correction.amount() > Ceilings.MOVEMENT + 1e-9 || speed.getModifier(Mettle.id("ceiling/movement_speed")) != null) {
				return "movement ceiling: cards " + share + ", correction " + correction;
			}
			AttributeInstance jump = p.getAttribute(Attributes.JUMP_STRENGTH);
			Identifier spring = Mettle.id("trait/athletics/springing");
			jump.addTransientModifier(new AttributeModifier(spring, 0.8, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			float power = CardHooks.jumpPower(p, (float) jump.getValue());
			jump.removeModifier(spring);
			double apex = CardPhysics.apex(power);
			return apex < 2.0 ? null : "a jump with +80 % strength peaks at " + apex + " blocks";
		}));

		// The deck says when a stat is capped: movement traits on Athletics, Blades and Survival cards pass +35 %.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			String no = give(p, "athletics/runner", List.of(SWIFT), List.of(new CardData.TraitRoll(Mettle.id("athletics/fleet"), 6),
				new CardData.TraitRoll(Mettle.id("athletics/long_winded"), 8), new CardData.TraitRoll(Mettle.id("athletics/springing"), 10)));
			no = no != null ? no : give(p, "blades/measure", List.of(SWIFT), List.of(new CardData.TraitRoll(Mettle.id("blades/fleet"), 6),
				new CardData.TraitRoll(Mettle.id("blades/flourishing"), 12)));
			no = no != null ? no : give(p, "survival/pathfinder", List.of(SWIFT), List.of(new CardData.TraitRoll(Mettle.id("survival/wayfaring"), 10)));
			if (no != null) {
				return no;
			}
			var capped = Ceilings.capped(Ceilings.Build.of(CardStore.get(p), Perks.registry(p)::getOptional));
			return capped.containsKey(Ceilings.Stat.MOVEMENT) ? null : "the deck's movement is not shown capped: " + capped;
		}));
		context.waitTicks(5);
		check(context.computeOnClient(mc -> {
			JsonObject cards = MettleState.build(mc).getAsJsonObject("cards");
			boolean line = false;
			for (JsonElement e : cards.getAsJsonArray("ceilings")) {
				JsonObject o = e.getAsJsonObject();
				if (o.get("stat").getAsString().equals("movement")) {
					line = o.get("capped").getAsBoolean() && o.get("text").getAsString().contains("capped at +35 %");
				}
			}
			boolean trait = false;
			for (JsonElement e : cards.getAsJsonArray("traits")) {
				JsonObject o = e.getAsJsonObject();
				if (o.get("id").getAsString().equals("mettle:athletics/fleet")) {
					trait = o.get("text").getAsString().contains("capped at +35 %");
				}
			}
			return line && trait ? null : "the overlay doesn't say movement is capped: " + cards.get("ceilings") + " " + cards.get("traits");
		}));
		context.runOnClient(mc -> {
			mc.gui.hud.getChat().clearMessages(false);
			mc.player.connection.sendCommand("mettle cards");
		});
		context.waitTicks(10);
		context.takeScreenshot("mettle_cards_ceilings");

		// Heart of the Mountain bores stone and stops at obsidian; Homeward lands beside the bed, and not again today.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ServerLevel level = s.overworld();
			var heart = Mythics.grant(p, Mettle.id("heart_of_the_mountain"), true).orElseThrow();
			var horizon = Mythics.grant(p, Mettle.id("far_horizon"), true).orElseThrow();
			var a = Cards.slot(p, heart.id(), -1, MiningSkill.MINING);
			var b = Cards.slot(p, horizon.id(), -1, Progress.skill("blades"));
			if (a.isPresent() || b.isPresent() || !Mythics.active(p, "heart_of_the_mountain") || !Mythics.active(p, "far_horizon")) {
				return "the Mythics could not be slotted: " + a + " " + b;
			}
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
			Direction dir = p.getDirection();
			BlockPos start = p.blockPosition().above(40).relative(dir, 2);
			for (int d = 0; d < 8; d++) {
				for (int y = 0; y < 2; y++) {
					level.setBlockAndUpdate(start.relative(dir, d).above(y), d == 3 && y == 0 ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.STONE.defaultBlockState());
				}
			}
			if (MythicEffects.boreable(p, level, start.relative(dir, 3)) || !MythicEffects.boreable(p, level, start)) {
				return "boreable: obsidian " + MythicEffects.boreable(p, level, start.relative(dir, 3)) + ", stone " + MythicEffects.boreable(p, level, start);
			}
			level.setBlockAndUpdate(start.relative(dir, 9), Blocks.BEDROCK.defaultBlockState());
			level.setBlockAndUpdate(start.relative(dir, 10), Blocks.REINFORCED_DEEPSLATE.defaultBlockState());
			level.setBlockAndUpdate(start.relative(dir, 11), Blocks.ANCIENT_DEBRIS.defaultBlockState());
			boolean hard = MythicEffects.boreable(p, level, start.relative(dir, 9)) || MythicEffects.boreable(p, level, start.relative(dir, 10))
				|| MythicEffects.boreable(p, level, start.relative(dir, 11));
			MythicEffects.bore(p, start);
			StringBuilder left = new StringBuilder();
			for (int d = 0; d < 8; d++) {
				for (int y = 0; y < 2; y++) {
					BlockState st = level.getBlockState(start.relative(dir, d).above(y));
					boolean gone = st.isAir();
					boolean want = d < 3;
					if (gone != want) {
						left.append(" ").append(d).append("/").append(y).append(" ").append(st.getBlock());
					}
				}
			}
			for (int d = 0; d < 12; d++) {
				for (int y = 0; y < 2; y++) {
					level.setBlockAndUpdate(start.relative(dir, d).above(y), Blocks.AIR.defaultBlockState());
				}
			}
			s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), "kill @e[type=item]");
			if (hard) {
				return "the bore would take bedrock, reinforced deepslate or ancient debris";
			}
			if (!left.isEmpty()) {
				return "the bore did not stop at the obsidian:" + left;
			}
			// A bed four blocks away is the respawn point; Homeward from 30 blocks off lands on a free spot beside it.
			BlockPos foot = p.blockPosition().relative(Direction.EAST, 4);
			level.setBlockAndUpdate(foot, Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState().setValue(BedBlock.FACING, Direction.EAST).setValue(BedBlock.PART, BedPart.FOOT));
			level.setBlockAndUpdate(foot.east(), Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState().setValue(BedBlock.FACING, Direction.EAST).setValue(BedBlock.PART, BedPart.HEAD));
			p.setRespawnPosition(new ServerPlayer.RespawnConfig(new LevelData.RespawnData(GlobalPos.of(level.dimension(), foot), 0, 0), false), false);
			CardCooldowns.reset(p, CardCooldowns.HOMEWARD);
			double x = p.getX();
			double y0 = p.getY();
			double z = p.getZ();
			p.teleportTo(x + 30, y0 + 2, z);
			boolean went = MythicEffects.homeward(p);
			BlockPos at = p.blockPosition();
			boolean free = !level.getBlockState(at).isSuffocating(level, at) && !level.getBlockState(at.above()).isSuffocating(level, at.above());
			double away = Math.sqrt(at.distSqr(foot));
			p.teleportTo(x + 30, y0 + 2, z);
			boolean again = MythicEffects.homeward(p);
			p.teleportTo(x, y0, z);
			p.setRespawnPosition(null, false);
			level.setBlockAndUpdate(foot.east(), Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(foot, Blocks.AIR.defaultBlockState());
			if (!went || away > 3 || !free) {
				return "Homeward: went " + went + ", landed " + away + " blocks from the bed, free " + free;
			}
			if (again || CardCooldowns.remaining(p, CardCooldowns.HOMEWARD) <= 0) {
				return "Homeward fired twice in a day";
			}
			// Updraft's cooldown, too, for the client and the restart.
			CardCooldowns.start(p, CardCooldowns.UPDRAFT, 24000);
			return null;
		}));
		context.waitTicks(5);
		check(context.computeOnClient(mc -> CardCooldowns.readyAt(mc.player, CardCooldowns.UPDRAFT) > mc.player.level().getGameTime() ? null
			: "the client doesn't see the saved Updraft cooldown"));

		// Put the character back (the cooldowns stay for afterRestart).
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Cards.clear(p);
			Cards.ledger(s).unclaim("heart_of_the_mountain");
			Cards.ledger(s).unclaim("far_horizon");
			CharacterStore.set(p, saved[0]);
			EffectsApplier.apply(p);
			p.setItemInHand(InteractionHand.MAIN_HAND, held[0]);
			p.setHealth(p.getMaxHealth());
			return null;
		}));
		context.waitTicks(3);
	}

	/** G2: after the world was saved, quit and opened again, the cooldowns started before are still running. */
	static void afterRestart(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getConnection().waitForChunksRender();
		check(world.getServer().computeOnServer(s -> {
			ServerPlayer p = player(s);
			long homeward = CardCooldowns.remaining(p, CardCooldowns.HOMEWARD);
			long updraft = CardCooldowns.remaining(p, CardCooldowns.UPDRAFT);
			return homeward > 0 && updraft > 0 ? null : "cooldowns lost in the restart: Homeward " + homeward + ", Updraft " + updraft;
		}));
		context.waitTicks(5);
		check(context.computeOnClient(mc -> CardCooldowns.readyAt(mc.player, CardCooldowns.UPDRAFT) > mc.player.level().getGameTime() ? null
			: "the client lost the saved Updraft cooldown in the restart"));
		check(world.getServer().computeOnServer(s -> {
			ServerPlayer p = player(s);
			CardCooldowns.reset(p, CardCooldowns.HOMEWARD);
			CardCooldowns.reset(p, CardCooldowns.UPDRAFT);
			return null;
		}));
	}

	/** Raises a skill to at least {@code level}. */
	private static void level(MinecraftServer s, ServerPlayer p, String skill, int level) {
		for (int i = 0; i < 12 && CharacterStore.get(p).level(Progress.skill(skill)) < level; i++) {
			s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), "mettle xp mettle:" + skill + " 3000 @a");
		}
	}

	/** Gives a Common card of a perk with these keywords and traits (needs 2) and slots it in its deck; null when it worked. */
	private static String give(ServerPlayer p, String perk, List<Identifier> keywords, List<CardData.TraitRoll> traits) {
		CardData card = Cards.giveExact(p, new DraftState.Card(Perks.key(Mettle.id(perk)), 1, DraftState.Rarity.COMMON, false, keywords, traits, 2));
		var no = Cards.slot(p, card.id(), -1);
		return no.isEmpty() ? null : "could not slot " + perk + ": " + no.get();
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().getFirst();
	}

	private static void check(String problem) {
		if (problem != null) {
			throw new AssertionError(problem);
		}
	}
}
