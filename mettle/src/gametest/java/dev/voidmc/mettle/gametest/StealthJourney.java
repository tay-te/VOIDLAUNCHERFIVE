package dev.voidmc.mettle.gametest;

import com.google.gson.JsonObject;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.Progress;
import dev.voidmc.mettle.character.cards.Cards;
import dev.voidmc.mettle.character.skill.StealthSkill;
import dev.voidmc.mettle.character.skill.stealth.StealthRules;
import dev.voidmc.mettle.client.overlay.MettleActions;
import dev.voidmc.mettle.client.overlay.MettleState;
import dev.voidmc.mettle.compat.HearthKeys;
import dev.voidmc.mettle.compat.HearthLink;
import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.CardStore;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.Curves;
import dev.voidmc.mettle.core.DraftState;
import dev.voidmc.mettle.core.EffectsApplier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Stealth played for real (docs/BUILD_FEEL.md §14.1) on a stone platform in the sky at midnight: a Stealth level from
 * real XP and a card kept from its draft through the overlay's action; XP for sneaking near a hostile that hasn't seen
 * the player (the client holds the sneak key); a sneak attack's XP and Ambusher's 30 %; Vanish shaking off a zombie
 * that can't see the player; Ghost in the dark (unseen beyond 4 blocks, Hearth's key or its fallback); and Shadowstep's
 * sneak-jump behind a mob. The character, inventory, time and place are put back after.
 */
final class StealthJourney {
	private static final String ID = StealthSkill.STEALTH.identifier().toString();
	private static final String TAG = "stealth_t";
	private static final int Y = 210;

	private StealthJourney() {
	}

	static void run(ClientGameTestContext context, TestSingleplayerContext world) {
		TestServerContext server = world.getServer();
		double[] home = on(server, p -> new double[] {p.getX(), p.getY(), p.getZ()});
		List<ItemStack> inventory = on(server, p -> {
			List<ItemStack> copy = new ArrayList<>();
			for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
				copy.add(p.getInventory().getItem(i).copy());
			}
			return copy;
		});
		Set<Long> cardsBefore = on(server, p -> {
			Set<Long> ids = new HashSet<>();
			CardStore.get(p).cards().forEach(c -> ids.add(c.id()));
			return ids;
		});
		// The decks earlier journeys left slotted go to the binder for this journey (their sets would colour its numbers) and back after.
		Map<Long, CardData.Place> slotted = on(server, p -> {
			Map<Long, CardData.Place> places = new HashMap<>();
			CardStore.get(p).slotted().forEach(card -> places.put(card.id(), card.place()));
			Cards.unslotAll(p);
			return places;
		});
		// Perks earlier journeys left (Whirlwind spins on a sneaking swing) likewise, back after.
		Map<ResourceKey<Perk>, Integer> perksBefore = on(server, p -> {
			Map<ResourceKey<Perk>, Integer> perks = new HashMap<>(CharacterStore.get(p).perks());
			CharacterStore.set(p, CharacterStore.get(p).withPerks(Map.of()));
			EffectsApplier.apply(p);
			return perks;
		});
		server.runCommand("mettle background set mettle:ranger @a");
		server.runCommand("effect clear @a");
		server.runCommand("gamerule spawn_mobs false");
		server.runCommand("weather clear");
		server.runCommand("time set 18000");
		server.runCommand("execute as @p at @s run fill ~-12 " + (Y - 1) + " ~-12 ~12 " + (Y - 1) + " ~12 minecraft:stone");
		server.runCommand("execute as @p at @s run fill ~-12 " + Y + " ~-12 ~12 " + (Y + 6) + " ~12 minecraft:air");
		server.runCommand("execute as @p at @s run tp @s ~ " + Y + " ~ 0 0");
		context.waitTicks(10);
		double[] centre = on(server, p -> new double[] {p.getX(), p.getY(), p.getZ()});
		check(on(server, p -> {
			clean(p);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
			p.setHealth(p.getMaxHealth());
			return null;
		}));
		try {
			levelAndDraft(context, server);
			sneakingNear(context, server, centre);
			sneakAttack(context, server, centre);
			vanish(context, server, centre);
			ghost(context, server, centre);
			shadowstep(context, server, centre);
		} finally {
			context.getInput().releaseKey(o -> o.keyShift);
			context.waitTicks(3);
			server.runCommand("kill @e[tag=" + TAG + "]");
			check(on(server, p -> {
				clean(p);
				// The Stealth cards this journey kept go back (released; ids are never reused).
				for (CardData c : List.copyOf(CardStore.get(p).cards())) {
					if (!cardsBefore.contains(c.id())) {
						Cards.ledger(p.level().getServer()).update(c.id(), x -> x.withPlace(CardData.Place.RELEASED));
					}
				}
				CharacterStore.set(p, CharacterStore.get(p).withPerks(perksBefore));
				EffectsApplier.apply(p);
				slotted.forEach((id, place) -> Cards.ledger(p.level().getServer()).update(id, x -> x.withPlace(place)));
				Cards.sync(p);
				for (int i = 0; i < inventory.size(); i++) {
					p.getInventory().setItem(i, inventory.get(i));
				}
				p.setHealth(p.getMaxHealth());
				return null;
			}));
			server.runCommand("execute as @p at @s run fill ~-12 " + Y + " ~-12 ~12 " + (Y + 6) + " ~12 minecraft:air");
			server.runCommand("time set 1000");
			server.runCommand("effect clear @a");
			server.runCommand("tp @p " + home[0] + " " + home[1] + " " + home[2]);
			context.waitTicks(5);
		}
	}

	// ---------------------------------------------------------------- a level, a draft, a card kept

	private static void levelAndDraft(ClientGameTestContext context, TestServerContext server) {
		check(on(server, p -> {
			CharacterState state = CharacterStore.get(p);
			var background = CharacterStore.background(p.level().registryAccess(), state);
			Progress.award(p, StealthSkill.STEALTH, Curves.skillXpFor(2) / Progress.multiplier(state, background, StealthSkill.STEALTH) + 0.5);
			DraftState d = CharacterStore.get(p).drafts();
			if (CharacterStore.get(p).level(StealthSkill.STEALTH) != 2) {
				return "Stealth is level " + CharacterStore.get(p).level(StealthSkill.STEALTH) + " after real XP, expected 2";
			}
			if (d.queued(StealthSkill.STEALTH) != 2 || d.hand(StealthSkill.STEALTH).isEmpty()) {
				return "Stealth drafts " + d.queued(StealthSkill.STEALTH) + ", hand " + d.hand(StealthSkill.STEALTH);
			}
			for (DraftState.Card c : d.hand(StealthSkill.STEALTH)) {
				if (!c.perk().identifier().getPath().startsWith("stealth/") && !c.wildcard()) {
					return "a Stealth hand dealt " + c.perk().identifier();
				}
			}
			return null;
		}));
		context.waitTicks(3);
		check(context.computeOnClient(mc -> {
			for (var e : MettleState.build(mc).getAsJsonArray("skills")) {
				JsonObject s = e.getAsJsonObject();
				if (s.get("id").getAsString().equals(ID)) {
					return s.has("hand") && s.getAsJsonArray("hand").size() > 0 ? null : "the overlay shows Stealth without its hand: " + s;
				}
			}
			return "Stealth is not in the overlay's skills";
		}));
		ResourceKey<Perk> kept = on(server, p -> CharacterStore.get(p).drafts().hand(StealthSkill.STEALTH).getFirst().perk());
		context.runOnClient(mc -> MettleActions.pickCard(ID, 0));
		context.waitTicks(5);
		check(on(server, p -> {
			if (CardStore.get(p).cards().stream().noneMatch(c -> c.perk().equals(kept))) {
				return "keeping " + kept.identifier() + " from the Stealth draft made no card";
			}
			return CharacterStore.get(p).drafts().queued(StealthSkill.STEALTH) == 1 ? null : "the kept draft was not spent";
		}));
		context.takeScreenshot("mettle_stealth_draft");
	}

	// ---------------------------------------------------------------- XP for sneaking near an unaware hostile

	private static void sneakingNear(ClientGameTestContext context, TestServerContext server, double[] c) {
		// A zombified piglin: a hostile that doesn't hunt the player until provoked.
		summon(server, "zombified_piglin", c[0] + 6, c[2], "near");
		double before = xp(server);
		context.getInput().holdKey(o -> o.keyShift);
		context.waitTicks(100);
		context.getInput().releaseKey(o -> o.keyShift);
		context.waitTicks(3);
		check(on(server, p -> {
			double gained = CharacterStore.get(p).xp(StealthSkill.STEALTH) - before;
			double one = StealthRules.NEAR_XP * multiplier(p);
			return gained >= one - 0.01 && gained <= 2 * one + 0.01 ? null
				: "five seconds sneaking near an unaware hostile gave " + gained + " Stealth XP, expected one or two of " + one;
		}));
		// Not sneaking: nothing.
		double flat = xp(server);
		context.waitTicks(100);
		check(on(server, p -> Math.abs(CharacterStore.get(p).xp(StealthSkill.STEALTH) - flat) < 1e-9 ? null : "walking past it taught Stealth"));
		server.runCommand("kill @e[tag=" + TAG + "]");
	}

	// ---------------------------------------------------------------- a sneak attack and Ambusher

	private static void sneakAttack(ClientGameTestContext context, TestServerContext server, double[] c) {
		grant(server, StealthSkill.AMBUSHER);
		// A first swing with the sword spends what a weapon switch may hold (Weapon Master, a synergy earlier skills earned).
		summon(server, "zombified_piglin", c[0] + 1.5, c[2], "warm");
		context.waitTicks(25);
		hit(server, "warm");
		server.runCommand("kill @e[tag=" + TAG + "]");
		// One fresh piglin at a time: a hurt one would rouse the other, and a roused mob is no one's sneak attack.
		summon(server, "zombified_piglin", c[0] + 1.5, c[2], "sneak");
		context.getInput().holdKey(o -> o.keyShift);
		context.waitTicks(25);
		double before = xp(server);
		float sneak = hit(server, "sneak");
		context.getInput().releaseKey(o -> o.keyShift);
		context.waitTicks(3);
		check(on(server, p -> {
			double gained = CharacterStore.get(p).xp(StealthSkill.STEALTH) - before;
			// A point a point of the hit as it lands, before armour (as weapon XP counts it): at least the health it took.
			double low = sneak * multiplier(p);
			return gained >= low - 0.05 && gained <= low * 1.3 ? null : "the sneak attack gave " + gained + " Stealth XP, expected about " + low;
		}));
		server.runCommand("kill @e[tag=" + TAG + "]");
		summon(server, "zombified_piglin", c[0] + 1.5, c[2], "plain");
		context.waitTicks(25);
		float plain = hit(server, "plain");
		String state = on(server, p -> "active " + CardStore.get(p).active() + ", tiers " + CardStore.get(p).tiers() + ", traits " + CardStore.get(p).traits()
			+ ", perks " + CharacterStore.get(p).perks() + ", background " + CharacterStore.get(p).background() + ", attack "
			+ p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) + ", effects " + p.getActiveEffects());
		check(plain > 0 && Math.abs(sneak - plain * (1 + StealthRules.AMBUSHER)) < 0.05 ? null
			: "a sneak attack with Ambusher took " + sneak + " health, a plain hit " + plain + " (expected 30 % more); " + state);
		server.runCommand("kill @e[tag=" + TAG + "]");

		// A sneak kill: the health it had left, and a quarter of its max health.
		summon(server, "zombified_piglin", c[0] + 1.5, c[2], "kill");
		check(on(server, p -> {
			mob(p, "kill").setHealth(2);
			return null;
		}));
		context.getInput().holdKey(o -> o.keyShift);
		context.waitTicks(25);
		double beforeKill = xp(server);
		check(on(server, p -> {
			Mob m = mob(p, "kill");
			float max = m.getMaxHealth();
			p.attack(m);
			if (m.isAlive()) {
				return "the sneak kill didn't kill";
			}
			double gained = CharacterStore.get(p).xp(StealthSkill.STEALTH) - beforeKill;
			double want = (2 + StealthRules.killBonus(max)) * multiplier(p);
			return Math.abs(gained - want) < 0.05 ? null : "a sneak kill gave " + gained + " Stealth XP, expected " + want;
		}));
		context.getInput().releaseKey(o -> o.keyShift);
		context.waitTicks(3);
		server.runCommand("kill @e[tag=" + TAG + "]");
		server.runCommand("kill @e[type=item]");
	}

	/** Hits a tagged mob as the player (a full swing) and returns the health it lost. */
	private static float hit(TestServerContext server, String tag) {
		return on(server, p -> {
			Mob m = mob(p, tag);
			float before = m.getHealth();
			p.attack(m);
			return before - m.getHealth();
		});
	}

	// ---------------------------------------------------------------- Vanish

	private static void vanish(ClientGameTestContext context, TestServerContext server, double[] c) {
		grant(server, StealthSkill.VANISH);
		// A zombie walled in six blocks off: it hunts the player but can't see them.
		int x = (int) Math.floor(c[0]) + 6;
		int z = (int) Math.floor(c[2]);
		server.runCommand("fill " + (x - 1) + " " + Y + " " + (z - 1) + " " + (x + 1) + " " + (Y + 2) + " " + (z + 1) + " minecraft:stone");
		server.runCommand("fill " + x + " " + Y + " " + z + " " + x + " " + (Y + 1) + " " + z + " minecraft:air");
		summon(server, "zombie", x + 0.5, z + 0.5, "hunter");
		context.waitTicks(5);
		check(on(server, p -> {
			Mob m = mob(p, "hunter");
			m.setTarget(p);
			return m.hasLineOfSight(p) ? "the walled-in zombie can see the player" : null;
		}));
		context.getInput().holdKey(o -> o.keyShift);
		context.waitTicks(StealthRules.VANISH_SNEAK_TICKS + 15);
		context.getInput().releaseKey(o -> o.keyShift);
		context.waitTicks(3);
		check(on(server, p -> mob(p, "hunter").getTarget() == p ? "Vanish: the zombie still hunts the player after 2 s of sneaking out of sight" : null));
		server.runCommand("kill @e[tag=" + TAG + "]");
		server.runCommand("fill " + (x - 1) + " " + Y + " " + (z - 1) + " " + (x + 1) + " " + (Y + 2) + " " + (z + 1) + " minecraft:air");
	}

	// ---------------------------------------------------------------- Ghost in the dark

	private static void ghost(ClientGameTestContext context, TestServerContext server, double[] c) {
		grant(server, StealthSkill.GHOST);
		summon(server, "zombified_piglin", c[0] + 10, c[2], "far");
		summon(server, "zombified_piglin", c[0] + 3, c[2], "close");
		// Not attacking: three seconds since the last hit.
		context.waitTicks(StealthRules.ATTACK_GRACE + 5);
		context.getInput().holdKey(o -> o.keyShift);
		context.waitTicks(10);
		check(on(server, p -> {
			ServerLevel level = (ServerLevel) p.level();
			if (!dev.voidmc.mettle.character.skill.stealth.Sneaking.dark(p)) {
				return "midnight on the platform is not dark: light " + dev.voidmc.mettle.character.skill.stealth.Sneaking.light(p);
			}
			double far = p.getVisibilityPercent(level, mob(p, "far"));
			double close = p.getVisibilityPercent(level, mob(p, "close"));
			if (far != 0 || close <= 0) {
				return "Ghost: a hostile 10 blocks off sees " + far + ", one 3 blocks off " + close + " (expected 0 and more than 0)";
			}
			if (StealthSkill.bonus(p, HearthKeys.STEALTH_UNSEEN_BEYOND) != StealthRules.GHOST_RANGE) {
				return "Ghost: hearth:stealth/unseen_beyond reads " + StealthSkill.bonus(p, HearthKeys.STEALTH_UNSEEN_BEYOND) + ", expected 4";
			}
			int level0 = CharacterStore.get(p).level(StealthSkill.STEALTH);
			// The card kept from the draft may be Shadow (Stealth counts its rank higher too).
			int shadow = Perks.rank(p, StealthSkill.SHADOW);
			int want = Math.min(20, level0 + shadow + (HearthLink.bonus() ? 0 : StealthRules.FALLBACK_GHOST));
			return StealthSkill.effectiveLevel(p) == want ? null
				: "Ghost: the effective Stealth is " + StealthSkill.effectiveLevel(p) + ", expected " + want + " (bonus keys read: " + HearthLink.bonus() + ")";
		}));
		context.takeScreenshot("mettle_stealth_ghost");
		context.getInput().releaseKey(o -> o.keyShift);
		context.waitTicks(3);
		check(on(server, p -> StealthSkill.bonus(p, HearthKeys.STEALTH_UNSEEN_BEYOND) == 0 ? null : "Ghost holds without sneaking"));
		server.runCommand("kill @e[tag=" + TAG + "]");
	}

	// ---------------------------------------------------------------- Shadowstep

	private static void shadowstep(ClientGameTestContext context, TestServerContext server, double[] c) {
		grant(server, StealthSkill.SHADOWSTEP);
		// A piglin five blocks north, facing north (away from the player).
		server.runCommand("summon zombified_piglin " + c[0] + " " + Y + " " + (c[2] - 5) + " {Tags:[\"" + TAG + "\",\"" + TAG + "_step\"],NoAI:1b,Rotation:[180f,0f],PersistenceRequired:1b}");
		server.runCommand("tp @p " + c[0] + " " + Y + " " + c[2] + " 180 10");
		context.waitTicks(10);
		context.getInput().holdKey(o -> o.keyShift);
		context.waitTicks(5);
		context.getInput().holdKey(o -> o.keyJump);
		context.waitTicks(3);
		context.getInput().releaseKey(o -> o.keyJump);
		context.waitTicks(15);
		context.getInput().releaseKey(o -> o.keyShift);
		context.waitTicks(3);
		check(on(server, p -> {
			Mob m = mob(p, "step");
			Vec3 forward = Vec3.directionFromRotation(0, m.getYRot());
			Vec3 toPlayer = p.position().subtract(m.position());
			double behind = -(toPlayer.x * forward.x + toPlayer.z * forward.z);
			return toPlayer.horizontalDistance() < 2.5 && behind > 0.3 ? null
				: "Shadowstep: after a sneak-jump at the piglin the player is " + String.format("%.2f", toPlayer.horizontalDistance()) + " blocks from it, "
					+ String.format("%.2f", behind) + " behind it";
		}));
		server.runCommand("kill @e[tag=" + TAG + "]");
	}

	// ---------------------------------------------------------------- helpers

	/** No Stealth XP, perks, drafts or hand; Shadow and the rest gone. */
	private static void clean(ServerPlayer p) {
		CharacterState state = CharacterStore.get(p);
		Map<ResourceKey<Perk>, Integer> kept = new HashMap<>(state.perks());
		kept.keySet().removeIf(k -> k.identifier().getPath().startsWith("stealth/"));
		DraftState d = state.drafts().withQueued(StealthSkill.STEALTH, 0).withHand(StealthSkill.STEALTH, List.of());
		CharacterStore.set(p, state.withXp(StealthSkill.STEALTH, 0).withPerks(kept).withDraft(d));
		EffectsApplier.apply(p);
	}

	private static void grant(TestServerContext server, net.minecraft.resources.Identifier perk) {
		check(on(server, p -> {
			CharacterState state = CharacterStore.get(p);
			Map<ResourceKey<Perk>, Integer> perks = new HashMap<>(state.perks());
			perks.put(Perks.key(perk), 1);
			CharacterStore.set(p, state.withPerks(perks));
			EffectsApplier.apply(p);
			return Perks.has(p, perk) ? null : perk + " was not granted";
		}));
	}

	private static void summon(TestServerContext server, String type, double x, double z, String tag) {
		server.runCommand("summon " + type + " " + x + " " + Y + " " + z + " {Tags:[\"" + TAG + "\",\"" + TAG + "_" + tag + "\"],PersistenceRequired:1b}");
	}

	private static Mob mob(ServerPlayer p, String tag) {
		for (Entity e : ((ServerLevel) p.level()).getAllEntities()) {
			if (e instanceof Mob m && m.entityTags().contains(TAG + "_" + tag)) {
				return m;
			}
		}
		throw new AssertionError("no " + tag + " mob");
	}

	private static double multiplier(ServerPlayer p) {
		CharacterState state = CharacterStore.get(p);
		return Progress.multiplier(state, CharacterStore.background(p.level().registryAccess(), state), StealthSkill.STEALTH);
	}

	private static double xp(TestServerContext server) {
		return on(server, p -> CharacterStore.get(p).xp(StealthSkill.STEALTH));
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().getFirst();
	}

	private static <T> T on(TestServerContext server, Function<ServerPlayer, T> f) {
		return server.computeOnServer(s -> f.apply(player(s)));
	}

	private static void check(String problem) {
		if (problem != null) {
			throw new AssertionError("Stealth journey: " + problem);
		}
	}
}
