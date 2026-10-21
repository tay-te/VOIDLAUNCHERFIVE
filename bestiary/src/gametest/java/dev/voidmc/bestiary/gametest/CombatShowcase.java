package dev.voidmc.bestiary.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.voidmc.fray.api.CombatEvents;
import dev.voidmc.fray.api.DamageBreakdown;
import dev.voidmc.fray.api.DamageKind;
import dev.voidmc.fray.api.DamageSpec;
import dev.voidmc.fray.api.FrayApi;
import dev.voidmc.fray.api.FrayAttributes;
import dev.voidmc.fray.api.GuardOutcome;
import dev.voidmc.fray.api.HitContext;
import dev.voidmc.fray.api.HitFlags;
import dev.voidmc.fray.api.Rank;
import dev.voidmc.fray.api.StatusType;
import dev.voidmc.fray.api.TellColour;
import dev.voidmc.fray.client.combat.ClientCombat;
import dev.voidmc.fray.client.combat.CombatKeys;
import dev.voidmc.fray.client.combat.FrayClientOptions;
import dev.voidmc.fray.client.combat.LockOn;
import dev.voidmc.fray.client.combat.hud.FrayHud;
import dev.voidmc.fray.combat.CombatState;
import dev.voidmc.fray.combat.Defense;
import dev.voidmc.fray.combat.Pipeline;
import dev.voidmc.fray.combat.Statuses;
import dev.voidmc.fray.combat.action.Arts;
import dev.voidmc.fray.combat.action.PlayerCombat;
import dev.voidmc.fray.combat.action.Sweep;
import dev.voidmc.fray.combat.math.DamageMath;
import dev.voidmc.fray.combat.net.CombatHandshake;
import dev.voidmc.fray.combat.net.CombatSync;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Fray combat's client game tests (stage 1B): the pipeline on a live server, every player action through real input,
 * the HUD states with screenshots (Figma 3a H-1 … H-4, H1-H10), performance (HUD draw cost per frame, server tick cost
 * with 50 creatures in combat) and a dedicated server with the combat handshake. Report:
 * {@code build/run/clientGameTest/fray-report-combat.txt}; screenshots {@code fray_combat_*}.
 * <pre>bash tools/gametest.sh -Dfray.show.only=none -Dfray.combat=all</pre>
 */
public class CombatShowcase implements FabricClientGameTest {
	private static final Set<String> PARTS = new HashSet<>(Arrays.asList(System.getProperty("fray.combat", "").split(",")));
	static final int Y = Stage.Y;

	private static final List<String> GUARDS = new CopyOnWriteArrayList<>();
	private static final List<String> DODGES = new CopyOnWriteArrayList<>();
	private static boolean listening;

	private static boolean want(String part) {
		return PARTS.contains(part) || PARTS.contains("all");
	}

	@Override
	public void runTest(ClientGameTestContext c) {
		if (PARTS.equals(Set.of("")) || !MotionDuo.ROLE.isEmpty()) {
			return;
		}
		Stage.REPORT.clear();
		Stage.PROBLEMS.clear();
		Stage.log("Fray combat showcase: parts " + PARTS + ", api " + FrayApi.API + ", protocol " + FrayApi.PROTOCOL + ", movesets " + CombatHandshake.contentHash());
		Stage.log("key conflicts found at start: " + CombatKeys.CONFLICTS);
		if (!listening) {
			listening = true;
			CombatEvents.GUARDED.register((d, a, k, n) -> GUARDS.add(k.name()));
			CombatEvents.DODGED.register((e, a, perfect) -> DODGES.add(perfect ? "SLIP" : "DODGED"));
		}
		c.getInput().resizeWindow(1920, 1080);
		c.runOnClient(mc -> {
			mc.options.guiScale().set(3);
			mc.resizeGui();
			mc.options.enableVsync().set(false);
			mc.options.framerateLimit().set(260);
			mc.options.bobView().set(false);
			mc.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
			FrayClientOptions.get().damageNumbers = false;
		});
		if (want("pipeline") || want("actions") || want("hud") || want("perf")) {
			try (TestSingleplayerContext world = c.worldBuilder().setUseConsistentSettings(false).adjustSettings(s -> {
				s.setSeed("20261021");
				s.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
				s.setAllowCommands(true);
			}).create()) {
				TestServerContext server = world.getServer();
				Stage st = new Stage(c, server, "combat");
				st.build();
				server.runCommand("gamerule spawn_monsters false");
				server.runCommand("gamerule send_command_feedback false");
				server.runCommand("gamemode survival @a");
				server.runCommand("tp @p 0.5 " + Y + " 0.5 180 0");
				world.getConnection().waitForChunksRender();
				c.waitTicks(20);
				st.playerCam(CameraType.FIRST_PERSON);
				if (want("pipeline")) {
					pipeline(st);
				}
				if (want("actions")) {
					actions(st);
				}
				if (want("hud")) {
					hud(st);
				}
				if (want("perf")) {
					perf(st);
				}
			}
		}
		if (want("dedicated")) {
			dedicated(c);
		}
		Stage.log("problems: " + Stage.PROBLEMS);
		Stage.writeReport("combat");
		if (!Stage.PROBLEMS.isEmpty()) {
			throw new AssertionError("combat showcase problems: " + Stage.PROBLEMS);
		}
	}

	// ------------------------------------------------------------------ helpers

	static ServerPlayer sp(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	static PlayerCombat pc(MinecraftServer server) {
		return CombatState.of(sp(server)).player;
	}

	/** A NoAI zombie with no armour and a fixed max health at (x, z), facing the player at the origin. */
	static int zombie(Stage st, double x, double z, float health) {
		return st.s.computeOnServer(server -> {
			ServerLevel level = server.overworld();
			Zombie zb = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
			float yaw = (float) Math.toDegrees(Math.atan2(-(0.5 - x), 0.5 - z));
			zb.snapTo(x, Y, z, yaw, 0);
			zb.setYHeadRot(yaw);
			zb.setYBodyRot(yaw);
			zb.setNoAi(true);
			zb.setPersistenceRequired();
			zb.setSilent(true);
			zb.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.ZOMBIE_HEAD));
			zb.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
			zb.getAttribute(Attributes.ARMOR).setBaseValue(0);
			zb.setHealth(health);
			level.addFreshEntity(zb);
			return zb.getId();
		});
	}

	static LivingEntity entity(MinecraftServer server, int id) {
		return (LivingEntity) server.overworld().getEntity(id);
	}

	static float health(Stage st, int id) {
		return st.health(id);
	}

	/** Puts the player at the origin facing north (yaw 180), sword in hand, shield off hand, full stamina. */
	static void ready(Stage st, boolean shield) {
		st.s.runCommand("tp @p 0.5 " + Y + " 0.5 180 0");
		st.s.runOnServer(server -> {
			ServerPlayer p = sp(server);
			p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			p.setItemSlot(EquipmentSlot.OFFHAND, shield ? new ItemStack(Items.SHIELD) : ItemStack.EMPTY);
			p.setHealth(p.getMaxHealth());
			PlayerCombat pc = CombatState.of(p).player;
			pc.stamina.value = pc.stamina.max;
			pc.stamina.windedUntil = Long.MIN_VALUE / 2;
			pc.riposteUntil = Long.MIN_VALUE / 2;
			pc.slipBonusUntil = Long.MIN_VALUE / 2;
			pc.artReadyAt = Long.MIN_VALUE / 2;
			for (StatusType s : StatusType.VALUES) {
				FrayApi.statuses().clear(p, s);
			}
		});
		st.c.waitTicks(4);
	}

	static float stamina(Stage st) {
		return st.s.computeOnServer(server -> pc(server).stamina.value);
	}

	static String lastAction(Stage st) {
		return PlayerCombat.ACTIONS.isEmpty() ? "" : PlayerCombat.ACTIONS.getLast();
	}

	/** A screenshot with the chat and toasts cleared. */
	static void shot(Stage st, String name) {
		st.c.runOnClient(mc -> {
			mc.gui.hud.getChat().clearMessages(false);
			mc.gui.toastManager().clear();
		});
		st.shot(name);
	}

	static void attack(Stage st, int holdTicks) {
		if (holdTicks <= 1) {
			st.c.getInput().pressKey(o -> o.keyAttack);
		} else {
			st.c.getInput().holdKeyFor(o -> o.keyAttack, holdTicks);
		}
	}

	// ------------------------------------------------------------------ 1. the pipeline on a live server

	private static void pipeline(Stage st) {
		st.clearCreatures();
		Stage.log("--- pipeline");
		int a = zombie(st, 0.5, -2, 40);
		int b = zombie(st, 2.5, -2, 40);
		st.c.waitTicks(5);
		// Stage 5: armour off, a plain slash: exactly the spec.
		DamageBreakdown r = st.s.computeOnServer(server -> FrayApi.damage().deal(entity(server, a),
			new HitContext(sp(server), ItemStack.EMPTY, Identifier.fromNamespaceAndPath("fray", "test"), DamageSpec.of(DamageKind.SLASH, 6, 6), HitFlags.LIGHT, 0, null)));
		Stage.expect(Math.abs(r.total() - 6) < 0.01 && Math.abs(health(st, a) - 34) < 0.01, "a 6-slash hit on an unarmoured zombie deals 6 (" + r.total() + ", health " + health(st, a) + ")");
		// The 4-tick floor per attacker; another attacker isn't held back.
		DamageBreakdown again = st.s.computeOnServer(server -> FrayApi.damage().deal(entity(server, a),
			new HitContext(sp(server), ItemStack.EMPTY, Identifier.fromNamespaceAndPath("fray", "test"), DamageSpec.of(DamageKind.SLASH, 6, 6), HitFlags.LIGHT, 0, null)));
		Stage.expect(again.total() == 0, "a second hit from the same attacker within 4 ticks is floored");
		DamageBreakdown other = st.s.computeOnServer(server -> FrayApi.damage().deal(entity(server, a),
			new HitContext(entity(server, b), ItemStack.EMPTY, Identifier.fromNamespaceAndPath("fray", "test"), DamageSpec.of(DamageKind.BLUNT, 2, 2), HitFlags.LIGHT, 0, null)));
		Stage.expect(other.total() > 0, "another attacker's hit in the same tick lands (Fray bypasses vanilla's 10-tick cooldown)");
		st.c.waitTicks(5);
		DamageBreakdown later = st.s.computeOnServer(server -> FrayApi.damage().deal(entity(server, a),
			new HitContext(sp(server), ItemStack.EMPTY, Identifier.fromNamespaceAndPath("fray", "test"), DamageSpec.of(DamageKind.SLASH, 6, 6), HitFlags.LIGHT, 0, null)));
		Stage.expect(later.total() > 5.9, "after 4 ticks the same attacker lands again");

		// Resistances: fire 0.5 halves a Fray fire hit and a foreign lava hit (by its damage profile).
		st.s.runOnServer(server -> entity(server, b).getAttribute(FrayAttributes.RESIST.get(DamageKind.FIRE)).setBaseValue(0.5));
		float hb = health(st, b);
		DamageBreakdown fire = st.s.computeOnServer(server -> FrayApi.damage().deal(entity(server, b),
			new HitContext(sp(server), ItemStack.EMPTY, Identifier.fromNamespaceAndPath("fray", "test"), DamageSpec.of(DamageKind.FIRE, 10, 0), 0, 0, null)));
		Stage.expect(Math.abs(fire.total() - 5) < 0.01, "fire resist 0.5 halves a Fray fire hit (" + fire.total() + ")");
		st.c.waitTicks(12);
		float hb2 = health(st, b);
		st.s.runOnServer(server -> entity(server, b).hurtServer(server.overworld(), server.overworld().damageSources().lava(), 4));
		float lava = hb2 - health(st, b);
		Stage.expect(Math.abs(lava - 2) < 0.05, "a foreign lava hit of 4 is resisted by its profile (fire 1.0): took " + lava);
		st.s.runOnServer(server -> entity(server, b).hurtServer(server.overworld(), server.overworld().damageSources().lava(), 4));
		Stage.expect(Math.abs(hb2 - lava - health(st, b)) < 0.01, "vanilla's damage cooldown still holds for lava (second hit within 10 ticks did nothing)");
		Stage.log("health b " + hb + " -> " + health(st, b));

		// Statuses: build-up procs; Wet makes shock build ×1.5.
		int c1 = zombie(st, -2.5, -2, 200);
		st.c.waitTicks(3);
		boolean burn = st.s.computeOnServer(server -> {
			LivingEntity z = entity(server, c1);
			FrayApi.statuses().addBuildUp(z, StatusType.BURN, 60, sp(server));
			boolean before = z.isOnFire();
			FrayApi.statuses().addBuildUp(z, StatusType.BURN, 50, sp(server));
			return !before && z.isOnFire() && FrayApi.statuses().has(z, StatusType.BURN);
		});
		Stage.expect(burn, "Burn procs when its meter fills (on fire, Burn active)");
		boolean poison = st.s.computeOnServer(server -> {
			LivingEntity z = entity(server, c1);
			FrayApi.statuses().addBuildUp(z, StatusType.POISON, 100, sp(server));
			return z.hasEffect(net.minecraft.world.effect.MobEffects.POISON) == false; // zombies are undead: immune
		});
		Stage.expect(poison, "undead are immune to Poison build-up");
		float[] shock = st.s.computeOnServer(server -> {
			LivingEntity z = entity(server, c1);
			FrayApi.statuses().addBuildUp(z, StatusType.SHOCK, 40, sp(server));
			float dry = FrayApi.statuses().meter(z, StatusType.SHOCK);
			FrayApi.statuses().clear(z, StatusType.SHOCK);
			CombatState.of(z).meters.value[StatusType.SHOCK.meterIndex()] = 0;
			FrayApi.statuses().apply(z, StatusType.WET, 100, null);
			FrayApi.statuses().addBuildUp(z, StatusType.SHOCK, 40, sp(server));
			return new float[] {dry, FrayApi.statuses().meter(z, StatusType.SHOCK)};
		});
		Stage.expect(Math.abs(shock[0] - 0.4F) < 0.01 && Math.abs(shock[1] - 0.6F) < 0.01, "Wet: shock build-up ×1.5 (" + shock[0] + " dry, " + shock[1] + " wet)");
		boolean frozen = st.s.computeOnServer(server -> {
			LivingEntity z = entity(server, c1);
			FrayApi.statuses().addBuildUp(z, StatusType.CHILL, 70, sp(server));
			FrayApi.statuses().addBuildUp(z, StatusType.CHILL, 40, sp(server));
			return FrayApi.statuses().has(z, StatusType.FROZEN) && FrayApi.statuses().incapacitated(z);
		});
		Stage.expect(frozen, "a full Chill meter freezes (Frozen: can't act)");
		float bleed = st.s.computeOnServer(server -> {
			LivingEntity z = entity(server, c1);
			float h0 = z.getHealth();
			FrayApi.statuses().addBuildUp(z, StatusType.BLEED, 120, sp(server));
			return h0 - z.getHealth();
		});
		Stage.expect(Math.abs(bleed - (200 * 0.08F + 4)) < 0.1, "Bleed bursts for 8 % max health + 4 (" + bleed + ")");

		// Poise: heavies stagger a zombie (poise 20); threat names the player.
		int d = zombie(st, 0.5, -1.6, 200);
		st.c.waitTicks(3);
		boolean staggered = st.s.computeOnServer(server -> {
			LivingEntity z = entity(server, d);
			FrayApi.damage().deal(z, new HitContext(sp(server), new ItemStack(Items.MACE), Identifier.fromNamespaceAndPath("fray", "test"),
				DamageSpec.of(DamageKind.BLUNT, 8, 8), HitFlags.HEAVY, 0, null, 1, 1.8F));
			return FrayApi.statuses().has(z, StatusType.STAGGER);
		});
		Stage.expect(staggered, "a heavy blunt hit (poise 8 × 1.8 × 1.5 = 21.6) staggers a zombie (poise 20)");
		boolean threat = st.s.computeOnServer(server -> FrayApi.threat().top((net.minecraft.world.entity.Mob) entity(server, d)) == sp(server));
		Stage.expect(threat, "threat: the player tops the zombie's table after hitting it");
		st.clearCreatures();
	}

	// ------------------------------------------------------------------ 2. the actions through real input

	private static void actions(Stage st) {
		Stage.log("--- actions");
		st.clearCreatures();
		ready(st, false);
		int z = zombie(st, 0.5, -1.4, 200);
		st.c.waitTicks(10);
		float s0 = stamina(st);
		float h0 = health(st, z);
		attack(st, 1);
		st.c.waitTicks(5);
		float s1 = stamina(st);
		st.c.waitTicks(11);
		Stage.log("after a tap: " + lastAction(st) + ", stamina " + s0 + " -> " + stamina(st) + ", zombie " + h0 + " -> " + health(st, z));
		Stage.expect(lastAction(st).endsWith("LIGHT 0"), "a tap is a light attack (combo 0)");
		Stage.expect(health(st, z) < h0, "the light attack hit the zombie in front (server sweep)");
		Stage.expect(Math.abs(s0 - s1 - 8) < 0.01, "it cost the sword light's 8 stamina (" + s0 + " -> " + s1 + ")");
		// The string: three taps chain into the combo.
		PlayerCombat.ACTIONS.clear();
		for (int i = 0; i < 3; i++) {
			attack(st, 1);
			st.c.waitTicks(i == 2 ? 20 : 9);
		}
		Stage.log("string: " + PlayerCombat.ACTIONS);
		Stage.expect(PlayerCombat.ACTIONS.size() == 3 && PlayerCombat.ACTIONS.get(2).endsWith("LIGHT 2"), "three taps chain into the 3-hit sword string");
		ready(st, false);
		attack(st, 10);
		st.c.waitTicks(30);
		Stage.expect(lastAction(st).endsWith("HEAVY"), "held 10 ticks: a heavy on release (" + lastAction(st) + ")");
		ready(st, false);
		float hc = health(st, z);
		attack(st, 40);
		st.c.waitTicks(30);
		Stage.expect(lastAction(st).endsWith("CHARGED"), "held 40 ticks: a charged attack (" + lastAction(st) + ")");
		Stage.log("charged hit took " + (hc - health(st, z)) + " (" + Pipeline.last + ")");

		// Dodge: a roll's i-frames take a zombie's hit; inside the first three ticks it is a Slip.
		ready(st, false);
		DODGES.clear();
		st.c.getInput().holdKey(o -> o.keyUp);
		st.c.getInput().pressKey(CombatKeys.dodge);
		st.c.getInput().releaseKey(o -> o.keyUp);
		st.c.waitTicks(1);
		float ph = st.s.computeOnServer(server -> sp(server).getHealth());
		st.s.runOnServer(server -> ((Zombie) entity(server, z)).doHurtTarget(server.overworld(), sp(server)));
		float ph2 = st.s.computeOnServer(server -> sp(server).getHealth());
		Stage.log("dodge: " + lastAction(st) + ", events " + DODGES + ", health " + ph + " -> " + ph2);
		Stage.expect(lastAction(st).contains("DODGE") || lastAction(st).contains("BACKSTEP"), "Left Alt dodges");
		Stage.expect(ph2 == ph && !DODGES.isEmpty(), "a hit inside the dodge's i-frames does nothing (" + DODGES + ")");
		st.c.waitTicks(20);

		// Parry: guard pressed with a shield; the zombie's strike lands inside the window.
		ready(st, true);
		GUARDS.clear();
		st.c.getInput().holdKey(o -> o.keyUse);
		st.c.waitTicks(2);
		float sBefore = stamina(st);
		st.s.runOnServer(server -> ((Zombie) entity(server, z)).doHurtTarget(server.overworld(), sp(server)));
		boolean zStag = st.s.computeOnServer(server -> FrayApi.statuses().has(entity(server, z), StatusType.STAGGER));
		Stage.log("parry: guards " + GUARDS + ", stamina " + sBefore + " -> " + stamina(st));
		Stage.expect(GUARDS.contains("PARRIED") && zStag, "a strike inside the parry window is parried and staggers the zombie");
		st.c.waitTicks(2);
		shot(st, "parry_ring_riposte");
		st.c.getInput().releaseKey(o -> o.keyUse);
		st.c.waitTicks(2);
		attack(st, 1);
		st.c.waitTicks(12);
		Stage.expect(lastAction(st).endsWith("RIPOSTE"), "a light right after the parry is a riposte (" + lastAction(st) + ")");

		// Block: the guard held past the window; a shield blocks all physical damage at a stamina cost.
		ready(st, true);
		GUARDS.clear();
		st.c.getInput().holdKey(o -> o.keyUse);
		st.c.waitTicks(12);
		float sb = stamina(st);
		float hb = st.s.computeOnServer(server -> sp(server).getHealth());
		st.s.runOnServer(server -> ((Zombie) entity(server, z)).doHurtTarget(server.overworld(), sp(server)));
		float hb2 = st.s.computeOnServer(server -> sp(server).getHealth());
		Stage.log("block: guards " + GUARDS + ", stamina " + sb + " -> " + stamina(st) + ", health " + hb + " -> " + hb2);
		Stage.expect(GUARDS.contains("BLOCKED") && hb2 == hb && stamina(st) < sb, "a held guard blocks: no damage, stamina spent");
		shot(st, "block_hit_marker");
		// Guard break: blocking with no stamina left.
		st.s.runOnServer(server -> pc(server).stamina.value = 0.5F);
		st.c.waitTicks(2);
		st.s.runOnServer(server -> ((Zombie) entity(server, z)).doHurtTarget(server.overworld(), sp(server)));
		boolean broke = st.s.computeOnServer(server -> FrayApi.statuses().has(sp(server), StatusType.STAGGER) && pc(server).winded());
		Stage.expect(GUARDS.contains("GUARD_BREAK") && broke, "blocking with no stamina breaks the guard (Staggered, Winded)");
		st.c.waitTicks(3);
		shot(st, "guard_broken_winded");
		st.c.getInput().releaseKey(o -> o.keyUse);
		st.c.waitTicks(30);

		// Lock-on: middle mouse on the zombie.
		ready(st, false);
		st.c.getInput().pressMouse(InputConstants.MOUSE_BUTTON_MIDDLE);
		st.c.waitTicks(4);
		int locked = st.c.computeOnClient(mc -> LockOn.target);
		int serverLock = st.s.computeOnServer(server -> pc(server).lockTarget);
		Stage.expect(locked == z && serverLock == z, "middle mouse locks the zombie (client " + locked + ", server " + serverLock + ")");
		shot(st, "lock_on_target_frame");
		st.c.getInput().pressMouse(InputConstants.MOUSE_BUTTON_MIDDLE);
		st.c.waitTicks(4);
		Stage.expect(st.c.computeOnClient(mc -> LockOn.target) < 0, "middle mouse again unlocks");

		// Plunging strike: a light while falling 1.5+ blocks.
		ready(st, false);
		st.s.runCommand("tp @p 0.5 " + (Y + 5) + " 0.5 180 60");
		st.c.waitTicks(8);
		attack(st, 1);
		st.c.waitTicks(20);
		Stage.expect(lastAction(st).endsWith("PLUNGE"), "a light while falling is the plunging strike (" + lastAction(st) + ")");

		// The Art: sneak + C with Whirlwind slotted hits everything around.
		ready(st, false);
		st.clearCreatures();
		int[] ring = {zombie(st, 0.5, -1.5, 100), zombie(st, 2.0, 0.5, 100), zombie(st, -1.0, 0.5, 100), zombie(st, 0.5, 2.0, 100)};
		st.s.runOnServer(server -> FrayApi.actions().setArt(sp(server), Arts.WHIRLWIND));
		st.c.waitTicks(10);
		st.c.getInput().holdShift();
		st.c.waitTicks(2);
		st.c.getInput().pressKey(CombatKeys.technique);
		st.c.waitTicks(2);
		st.c.getInput().releaseShift();
		st.c.waitTicks(20);
		int hits = 0;
		for (int id : ring) {
			if (health(st, id) < 100) {
				hits++;
			}
		}
		Stage.expect(lastAction(st).endsWith("WHIRLWIND") && hits == 4, "sneak + C performs Whirlwind, hitting all four around (" + hits + ")");
		int cd = st.s.computeOnServer(server -> FrayApi.actions().artCooldown(sp(server)));
		Stage.expect(cd > 100, "the Art goes on cooldown (" + cd + " ticks)");
		shot(st, "art_cooldown");
		st.c.getInput().pressKey(CombatKeys.technique);
		st.c.waitTicks(4);
		Stage.log("technique without sneak (stage 3): " + lastAction(st));
		st.clearCreatures();
	}

	// ------------------------------------------------------------------ 3. the HUD states

	private static void hud(Stage st) {
		Stage.log("--- hud");
		// The HUD's screenshots: the player's own first-person swing clips (the animation lane's) stay out of the frame.
		st.c.runOnClient(mc -> dev.voidmc.fray.client.combat.CombatInput.predictClips = false);
		st.clearCreatures();
		ready(st, true);
		st.s.runCommand("time set 1800");
		st.s.runOnServer(server -> {
			ServerPlayer p = sp(server);
			p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40);
			p.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(4);
			p.setHealth(31);
			p.setAbsorptionAmount(4);
			p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
		});
		// Effects without particles (they fill a first-person screenshot) but with their icons.
		st.s.runOnServer(server -> {
			sp(server).addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.STRENGTH, 2600, 1, false, false, true));
			sp(server).addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 480, 0, false, false, true));
		});
		// H-1: an elite with affixes, locked; two hurt commons with nameplates; holding a heavy (the cost ghost); Art ready.
		int elite = zombie(st, 0.5, -3.0, 60);
		int left = zombie(st, -3.5, -4.5, 20);
		int right = zombie(st, 4.0, -5.0, 20);
		st.s.runOnServer(server -> {
			FrayApi.profiles().set(entity(server, elite), Rank.ELITE, Component.literal("Elite · champion pack · danger 3"),
				List.of(Identifier.fromNamespaceAndPath("bestiary", "ironbound"), Identifier.fromNamespaceAndPath("bestiary", "shielded")));
			entity(server, elite).setCustomName(Component.literal("the Revenant of Kestrel Heath"));
			entity(server, elite).setHealth(38);
			entity(server, left).setHealth(9);
			entity(server, right).setHealth(14);
			FrayApi.statuses().addBuildUp(entity(server, elite), StatusType.BLEED, 30, sp(server));
			FrayApi.statuses().addBuildUp(entity(server, elite), StatusType.SHOCK, 20, sp(server));
			FrayApi.actions().setArt(sp(server), Arts.WHIRLWIND);
			pc(server).stamina.value = 72;
			pc(server).tagCombat(400);
		});
		st.c.waitTicks(10);
		st.c.getInput().pressMouse(InputConstants.MOUSE_BUTTON_MIDDLE);
		st.c.waitTicks(6);
		st.c.getInput().holdKey(o -> o.keyAttack);
		st.c.waitTicks(9);
		Stage.log("holding attack: client held " + st.c.computeOnClient(mc -> dev.voidmc.fray.client.combat.CombatInput.attackHeld) + ", ghost "
			+ st.c.computeOnClient(mc -> dev.voidmc.fray.client.combat.CombatInput.ghostCost(mc)));
		shot(st, "H1_elite_lock_ghost");
		Stage.expect(st.c.computeOnClient(mc -> FrayHud.clusterShown), "the player cluster is drawn (survival)");
		st.c.getInput().releaseKey(o -> o.keyAttack);
		st.c.waitTicks(30);
		st.c.getInput().pressMouse(InputConstants.MOUSE_BUTTON_MIDDLE);
		st.c.waitTicks(4);

		// H-2: the boss at night: phases, Break meter, status meters, a purple tell cue, Art cooling.
		st.clearCreatures();
		st.s.runCommand("time set 18000");
		int boss = zombie(st, 0.5, -4.0, 300);
		st.c.waitTicks(4);
		st.s.runOnServer(server -> {
			LivingEntity b = entity(server, boss);
			b.setCustomName(Component.literal("The Gallows Knight"));
			b.getAttribute(Attributes.SCALE).setBaseValue(1.6);
			FrayApi.encounters().start((net.minecraft.world.entity.Mob) b, Component.literal("The Gallows Knight"), Component.literal("Gallows Hill · lair"),
				new float[] {0.55F});
		});
		st.c.waitTicks(8);
		shot(st, "H7_banner_start");
		st.s.runOnServer(server -> {
			LivingEntity b = entity(server, boss);
			b.setHealth(300 * 0.62F);
			FrayApi.encounters().addBreak((net.minecraft.world.entity.Mob) b, 0.3F);
			FrayApi.statuses().addBuildUp(b, StatusType.BLEED, 120, sp(server));
			FrayApi.statuses().addBuildUp(b, StatusType.BURN, 80, sp(server));
		});
		st.c.waitTicks(4);
		st.s.runOnServer(server -> entity(server, boss).setHealth(300 * 0.48F));
		st.s.runOnServer(server -> {
			pc(server).artReadyAt = server.overworld().getGameTime() + 120;
			pc(server).artTotal = 160;
			FrayApi.telegraphs().tell(entity(server, boss), sp(server), TellColour.PURPLE, 40, Component.literal("Gallows Knight swings the chain"));
		});
		st.c.waitTicks(52);
		st.c.waitTicks(1);
		st.s.runOnServer(server -> FrayApi.telegraphs().tell(entity(server, boss), sp(server), TellColour.PURPLE, 40, Component.literal("Gallows Knight swings the chain")));
		st.c.waitTicks(6);
		shot(st, "H2_boss_phase2_purple_tell");
		Stage.expect(st.c.computeOnClient(mc -> ClientCombat.bar != null && ClientCombat.bar.phase() == 1), "the boss bar is up, in phase 2");
		// Broken: the Break meter full.
		st.s.runOnServer(server -> FrayApi.encounters().addBreak((net.minecraft.world.entity.Mob) entity(server, boss), 1));
		st.c.waitTicks(8);
		shot(st, "H6_boss_broken");
		// Felled.
		st.s.runOnServer(server -> entity(server, boss).hurtServer(server.overworld(), server.overworld().damageSources().playerAttack(sp(server)), 1000));
		st.c.waitTicks(10);
		shot(st, "H7_banner_felled");
		Stage.expect(st.c.computeOnClient(mc -> ClientCombat.bar == null && ClientCombat.banner != null), "the boss falls: bar gone, Felled banner");
		st.c.waitTicks(60);

		// H-3: a parry moment on an elite in daylight: ring, riposte window, +15, Staggered.
		st.clearCreatures();
		st.s.runCommand("time set 6000");
		ready(st, true);
		st.s.runOnServer(server -> pc(server).stamina.value = 55);
		int e3 = zombie(st, 0.5, -2.6, 60);
		st.s.runOnServer(server -> {
			FrayApi.profiles().set(entity(server, e3), Rank.ELITE, Component.literal("Elite"), List.of(Identifier.fromNamespaceAndPath("bestiary", "frenzied")));
			entity(server, e3).setCustomName(Component.literal("the Revenant of Frostwatch"));
		});
		st.c.waitTicks(6);
		st.c.getInput().pressMouse(InputConstants.MOUSE_BUTTON_MIDDLE);
		st.c.waitTicks(4);
		st.c.getInput().holdKey(o -> o.keyUse);
		st.c.waitTicks(2);
		st.s.runOnServer(server -> ((Zombie) entity(server, e3)).doHurtTarget(server.overworld(), sp(server)));
		st.c.waitTicks(3);
		shot(st, "H3_parry_riposte_staggered");
		st.c.getInput().releaseKey(o -> o.keyUse);
		st.c.getInput().pressMouse(InputConstants.MOUSE_BUTTON_MIDDLE);
		st.c.waitTicks(20);

		// H-4: low health, Poison 80 % and Bleed 35 % building on the player, a red tell from the left, Winded.
		st.clearCreatures();
		ready(st, true);
		int e4 = zombie(st, -3.5, -0.5, 60);
		st.s.runOnServer(server -> {
			ServerPlayer p = sp(server);
			p.setHealth(7);
			FrayApi.statuses().addBuildUp(p, StatusType.POISON, 80, null);
			FrayApi.statuses().addBuildUp(p, StatusType.BLEED, 35, null);
			FrayApi.statuses().apply(p, StatusType.WET, 100, null);
			pc(server).stamina.spend(pc(server).stamina.value + 1, server.overworld().getGameTime());
			FrayApi.profiles().set(entity(server, e4), Rank.ELITE, Component.literal("Elite · danger 3"), List.of());
			entity(server, e4).setCustomName(Component.literal("the Revenant of Kestrel Gorge"));
			FrayApi.telegraphs().tell(entity(server, e4), p, TellColour.RED, 30, Component.literal("Revenant charges"));
		});
		st.c.waitTicks(8);
		shot(st, "H4_low_health_buildup_red_tell_winded");
		Stage.expect(st.c.computeOnClient(mc -> ClientCombat.meter(mc.player.getId(), StatusType.POISON) > 0.7F), "the player's own Poison meter reached the client (80 %)");

		// H8: hit marker, critical, kill; damage numbers as an option.
		st.clearCreatures();
		ready(st, false);
		st.s.runCommand("effect clear @p");
		int t8 = zombie(st, 0.5, -2.4, 200);
		st.c.waitTicks(6);
		st.c.runOnClient(mc -> FrayClientOptions.get().damageNumbers = true);
		attack(st, 1);
		st.c.waitTicks(6);
		shot(st, "H8_hit_marker_numbers");
		st.c.runOnClient(mc -> FrayClientOptions.get().damageNumbers = false);
		// Slip: a perfect dodge.
		ready(st, false);
		st.c.getInput().pressKey(CombatKeys.dodge);
		st.c.waitTicks(1);
		st.s.runOnServer(server -> ((Zombie) entity(server, t8)).doHurtTarget(server.overworld(), sp(server)));
		st.c.waitTicks(3);
		shot(st, "H8_slip");
		st.s.runOnServer(server -> {
			ServerPlayer p = sp(server);
			p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20);
			p.setHealth(20);
			p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
		});
		st.c.runOnClient(mc -> dev.voidmc.fray.client.combat.CombatInput.predictClips = true);
		st.clearCreatures();
	}

	// ------------------------------------------------------------------ 4. performance

	private static void perf(Stage st) {
		Stage.log("--- perf");
		st.clearCreatures();
		ready(st, false);
		st.s.runCommand("time set 6000");
		// Client: the HUD with everything up (boss bar, 20 nameplates, status row, cluster, Art well).
		List<Integer> crowd = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			crowd.add(zombie(st, -6 + (i % 5) * 3, -4 - (i / 5) * 2.5, 40));
		}
		int boss = zombie(st, 0.5, -14, 300);
		st.c.waitTicks(5);
		st.s.runOnServer(server -> {
			for (int id : crowd) {
				entity(server, id).setHealth(25);
				FrayApi.statuses().addBuildUp(entity(server, id), StatusType.BLEED, 30, sp(server));
			}
			FrayApi.profiles().set(entity(server, crowd.get(0)), Rank.ELITE, Component.literal("Elite"), List.of(Identifier.fromNamespaceAndPath("bestiary", "swift")));
			FrayApi.encounters().start((net.minecraft.world.entity.Mob) entity(server, boss), Component.literal("The Gallows Knight"), null, new float[] {0.55F});
			FrayApi.actions().setArt(sp(server), Arts.WHIRLWIND);
			pc(server).tagCombat(2000);
		});
		st.s.runCommand("effect give @p strength 600 1 true");
		st.c.waitTicks(80);
		st.c.runOnClient(mc -> {
			FrayHud.drawNanos = 0;
			FrayHud.frames = 0;
			java.util.Arrays.fill(FrayHud.SECTION, 0);
		});
		long t0 = System.nanoTime();
		st.c.waitTicks(200);
		long wall = System.nanoTime() - t0;
		double[] hud = st.c.computeOnClient(mc -> new double[] {FrayHud.drawNanos / 1000.0 / Math.max(1, FrayHud.frames), FrayHud.frames});
		Stage.log(String.format(Locale.ROOT, "perf HUD: %.1f us a frame over %d frames (%.0f fps): boss bar, 20 nameplates, cluster, status row, Art well", hud[0], (long) hud[1],
			hud[1] / (wall / 1e9)));
		String sections = st.c.computeOnClient(mc -> {
			StringBuilder b = new StringBuilder();
			for (int i = 0; i < FrayHud.SECTION.length; i++) {
				b.append(FrayHud.SECTIONS[i]).append(' ').append(String.format(Locale.ROOT, "%.1f", FrayHud.SECTION[i] / 1000.0 / Math.max(1, FrayHud.frames))).append(", ");
			}
			return b.toString();
		});
		Stage.log("perf HUD by section (us a frame): " + sections);
		Stage.expect(hud[0] < 300, "HUD draw cost under 0.3 ms a frame");
		shot(st, "perf_hud_crowd");
		st.clearCreatures();

		// Server: 50 creatures in combat; 10 Fray hits a tick (pipeline, statuses, poise, threat), meters ticking.
		List<Integer> fifty = new ArrayList<>();
		for (int i = 0; i < 50; i++) {
			fifty.add(zombie(st, -12 + (i % 10) * 2.5, -3 - (i / 10) * 2.5, 2000));
		}
		st.c.waitTicks(20);
		st.s.runOnServer(server -> {
			Statuses.tickNanos = 0;
			Statuses.entityTicks = 0;
			Pipeline.dealNanos = 0;
			Pipeline.deals = 0;
			Pipeline.handoffNanos = 0;
		});
		final long[] serverTicks = {0};
		for (int t = 0; t < 200; t++) {
			int tick = t;
			st.s.runOnServer(server -> {
				ServerPlayer p = sp(server);
				for (int i = 0; i < 10; i++) {
					LivingEntity z = entity(server, fifty.get((tick * 10 + i) % 50));
					java.util.Map<DamageKind, Float> m = new java.util.EnumMap<>(DamageKind.class);
					m.put(DamageKind.SLASH, 3F);
					m.put(DamageKind.FIRE, 1F);
					m.put(DamageKind.SHOCK, 1F);
					FrayApi.damage().deal(z, new HitContext(p, ItemStack.EMPTY, Identifier.fromNamespaceAndPath("fray", "perf"), new DamageSpec(m, 5, java.util.Map.of()),
						HitFlags.LIGHT | HitFlags.MULTI_HIT, 0, null));
				}
			});
			st.c.waitTick();
			serverTicks[0]++;
		}
		double[] cost = st.s.computeOnServer(server -> new double[] {Statuses.tickNanos / 1e6 / Math.max(1, serverTicks[0]), Pipeline.dealNanos / 1e3 / Math.max(1, Pipeline.deals),
			Statuses.entityTicks / (double) Math.max(1, serverTicks[0]), server.getAverageTickTimeNanos() / 1e6, Statuses.tracked(),
			Pipeline.handoffNanos / 1e3 / Math.max(1, Pipeline.deals)});
		Stage.log(String.format(Locale.ROOT, "perf server, 50 creatures in combat: upkeep %.3f ms a tick (%.0f entities ticked a tick), %.1f us a pipeline hit (10 a tick = %.3f ms), whole server tick %.2f ms average",
			cost[0], cost[2], cost[1], cost[1] * 10 / 1000, cost[3]));
		Stage.log(String.format(Locale.ROOT, "perf server: of each pipeline hit, %.1f us is vanilla's hurtServer (stage 8: hurt sound, damage event, combat tracker)", cost[5]));
		Stage.log("network so far: " + CombatSync.packets + " combat packets, ~" + CombatSync.bytes / 1024 + " KB");
		Stage.expect(cost[0] + cost[1] * 10 / 1000 < 2.0, "Fray's server cost with 50 creatures in combat stays under 2 ms a tick");
		st.clearCreatures();
	}

	// ------------------------------------------------------------------ 5. dedicated server

	private static void dedicated(ClientGameTestContext c) {
		Stage.log("--- dedicated");
		Properties props = new Properties();
		int port = 25613;
		props.setProperty("server-port", String.valueOf(port));
		props.setProperty("online-mode", "false");
		props.setProperty("white-list", "false");
		props.setProperty("view-distance", "6");
		props.setProperty("simulation-distance", "4");
		props.setProperty("pause-when-empty-seconds", "0");
		props.setProperty("spawn-protection", "0");
		props.setProperty("level-seed", "20261021");
		props.setProperty("gamemode", "survival");
		try (TestDedicatedServerContext server = c.worldBuilder().createServer(props)) {
			Stage.log("dedicated server up: dedicated " + server.computeOnServer(s -> s.isDedicatedServer()));
			// 1. A client whose combat rules differ is refused with a plain message.
			CombatHandshake.clientOverride = "0000000000000000";
			c.runOnClient(mc -> ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString("localhost:" + port),
				new ServerData("fray", "localhost:" + port, ServerData.Type.OTHER), false, null));
			c.waitFor(mc -> mc.gui.screen() instanceof DisconnectedScreen, 1200);
			String reason = c.computeOnClient(mc -> mc.gui.screen().getNarrationMessage().getString());
			c.waitTicks(5);
			c.takeScreenshot("fray_combat_handshake_refused");
			Stage.log("refused: " + reason);
			Stage.expect(reason.contains("combat rules") && CombatHandshake.lastRefusal.contains("movesets"), "a client with other combat rules is refused at login");
			CombatHandshake.clientOverride = null;
			c.setScreen(TitleScreen::new);
			int before = CombatHandshake.accepted;
			try (TestDedicatedServerConnection connection = server.connect()) {
				connection.waitForChunksRender();
				Stage.expect(CombatHandshake.accepted > before, "the matching client passes the combat handshake");
				Stage st = new Stage(c, server, "combat_ds");
				st.build();
				server.runCommand("gamemode survival @a");
				server.runCommand("op @a");
				connection.waitForChunksRender();
				c.waitTicks(20);
				st.playerCam(CameraType.FIRST_PERSON);
				ready(st, false);
				int z = zombie(st, 0.5, -1.4, 100);
				c.waitTicks(10);
				float h0 = health(st, z);
				attack(st, 1);
				c.waitTicks(16);
				Stage.expect(health(st, z) < h0 && lastAction(st).contains("LIGHT"), "dedicated server: a light attack through real input hits (" + h0 + " -> " + health(st, z) + ")");
				st.c.getInput().pressKey(CombatKeys.dodge);
				c.waitTicks(3);
				Stage.expect(lastAction(st).contains("BACKSTEP") || lastAction(st).contains("DODGE"), "dedicated server: dodge");
				boolean hud = c.computeOnClient(mc -> FrayHud.clusterShown && ClientCombat.self != null);
				Stage.expect(hud, "dedicated server: the HUD has the server's resources");
				shot(st, "dedicated_hud");
			}
		}
	}
}
