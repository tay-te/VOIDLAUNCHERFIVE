package dev.voidmc.bestiary.gametest;

import dev.voidmc.fray.client.Animator;
import dev.voidmc.fray.client.ClientMotion;
import dev.voidmc.fray.motion.MotionServer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The showcase's stage: a stone platform in the sky, creatures summoned on the server, a camera on an invisible armour
 * stand (so the local player can be filmed like any entity), screenshots and a report.
 */
final class Stage {
	/** The platform's top: blocks at Y - 1, creatures stand at Y. */
	static final int Y = 200;
	static final List<String> REPORT = new ArrayList<>();

	final ClientGameTestContext c;
	final TestServerContext s;
	final String tag;
	private int camId = -1;

	Stage(ClientGameTestContext c, TestServerContext s, String tag) {
		this.c = c;
		this.s = s;
		this.tag = tag;
	}

	static void log(String line) {
		System.out.println("[fray] " + line);
		REPORT.add(line);
	}

	static void expect(boolean ok, String what) {
		log((ok ? "OK " : "PROBLEM ") + what);
		if (!ok) {
			PROBLEMS.add(what);
		}
	}

	static final List<String> PROBLEMS = new ArrayList<>();

	static void writeReport(String name) {
		try {
			Path out = Path.of("fray-report" + (name.isEmpty() ? "" : "-" + name) + ".txt");
			Files.write(out, REPORT, StandardCharsets.UTF_8);
			System.out.println("[fray] report written to " + out.toAbsolutePath());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/** A world ready for filming: fixed time and weather, no spawns, a 33 x 33 stone platform at Y - 1. */
	void build() {
		for (String cmd : new String[] {"gamerule advance_time false", "gamerule advance_weather false", "gamerule spawn_mobs false", "gamerule mob_griefing false",
			"gamerule fall_damage false", "time set 1800", "weather clear", "difficulty normal"}) {
			this.s.runCommand(cmd);
		}
		this.s.runCommand("forceload add -32 -32 32 32");
		this.c.waitTicks(10);
		this.s.runCommand("fill -16 " + (Y - 1) + " -16 16 " + (Y - 1) + " 16 minecraft:smooth_stone");
		this.s.runCommand("fill -16 " + (Y - 1) + " -16 16 " + (Y - 1) + " -16 minecraft:polished_andesite");
		this.s.runCommand("fill -16 " + (Y - 1) + " 16 16 " + (Y - 1) + " 16 minecraft:polished_andesite");
		this.s.runCommand("fill -16 " + (Y - 1) + " -16 -16 " + (Y - 1) + " 16 minecraft:polished_andesite");
		this.s.runCommand("fill 16 " + (Y - 1) + " -16 16 " + (Y - 1) + " 16 minecraft:polished_andesite");
		// A few pillars for depth (and something to cast shadows on).
		for (int[] p : new int[][] {{-9, -9}, {9, -9}, {-9, 9}, {9, 9}}) {
			this.s.runCommand("fill " + p[0] + " " + Y + " " + p[1] + " " + p[0] + " " + (Y + 3) + " " + p[1] + " minecraft:stone_bricks");
		}
		this.s.runCommand("tp @p 12 " + Y + " 12 135 10");
	}

	/** Removes every creature and dropped item (discarded: nothing drops), keeping players and the camera. */
	void clearCreatures() {
		this.s.runOnServer(server -> {
			List<Entity> gone = new ArrayList<>();
			for (Entity e : server.overworld().getAllEntities()) {
				if (!(e instanceof net.minecraft.world.entity.player.Player) && !e.entityTags().contains("fray_cam")) {
					gone.add(e);
				}
			}
			gone.forEach(Entity::discard);
		});
		this.c.waitTicks(2);
		this.c.runOnClient(mc -> mc.gui.toastManager().clear());
	}

	/** A mannequin (an Avatar drawn with the player model) in iron armour with a sword: player clips from any angle. */
	int mannequin(double x, double z, float yaw) {
		return this.s.computeOnServer(server -> {
			ServerLevel level = server.overworld();
			net.minecraft.world.entity.decoration.Mannequin m = EntityTypes.MANNEQUIN.create(level, EntitySpawnReason.COMMAND);
			m.snapTo(x, Y, z, yaw, 0);
			m.setYHeadRot(yaw);
			m.setYBodyRot(yaw);
			m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			m.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
			m.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
			m.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
			m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			level.addFreshEntity(m);
			return m.getId();
		});
	}

	/** Third person around the local player (the only way vanilla draws the local player), HUD hidden. */
	void thirdPerson(CameraType type) {
		this.c.runOnClient(mc -> {
			mc.setCameraEntity(mc.player);
			mc.options.setCameraType(type);
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		this.c.waitTicks(3);
	}

	// ------------------------------------------------------------------ camera

	/** Films from (x, y, z) towards (tx, ty, tz), with the HUD and hands hidden. */
	void cam(double x, double y, double z, double tx, double ty, double tz) {
		float yaw = (float) Math.toDegrees(Math.atan2(-(tx - x), tz - z));
		float pitch = (float) -Math.toDegrees(Math.atan2(ty - y, Math.hypot(tx - x, tz - z)));
		if (this.camId < 0) {
			this.s.runCommand(String.format(Locale.ROOT, "summon armor_stand %.3f %.3f %.3f {Invisible:1b,NoGravity:1b,Marker:1b,Tags:[\"fray_cam\"]}", x, y, z));
			this.camId = this.s.computeOnServer(server -> {
				for (Entity e : server.overworld().getAllEntities()) {
					if (e instanceof ArmorStand && e.entityTags().contains("fray_cam")) {
						return e.getId();
					}
				}
				throw new AssertionError("no camera stand");
			});
		}
		this.s.runCommand(String.format(Locale.ROOT, "tp @e[tag=fray_cam,limit=1] %.3f %.3f %.3f %.2f %.2f", x, y, z, yaw, pitch));
		this.c.waitTicks(4);
		int id = this.camId;
		this.c.runOnClient(mc -> {
			Entity e = mc.level.getEntity(id);
			if (e != null) {
				mc.setCameraEntity(e);
			}
			mc.options.setCameraType(CameraType.FIRST_PERSON);
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		this.c.waitTicks(2);
	}

	/** Back to the player's own eyes, HUD shown (first-person hands need it). */
	void playerCam(CameraType type) {
		this.c.runOnClient(mc -> {
			mc.setCameraEntity(mc.player);
			mc.options.setCameraType(type);
			if (mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
			mc.gui.hud.getChat().clearMessages(false);
			mc.gui.toastManager().clear();
		});
		this.c.waitTicks(3);
	}

	void shot(String name) {
		this.c.waitTick();
		this.c.takeScreenshot("fray_" + (this.tag.isEmpty() ? "" : this.tag + "_") + name);
	}

	// ------------------------------------------------------------------ creatures

	/** A NoAI zombie facing {@code yaw}, a zombie head on (it stays a zombie in daylight without burning). */
	int zombie(double x, double z, float yaw, boolean armour) {
		return this.s.computeOnServer(server -> {
			ServerLevel level = server.overworld();
			Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
			zombie.snapTo(x, Y, z, yaw, 0);
			zombie.setYHeadRot(yaw);
			zombie.setYBodyRot(yaw);
			zombie.setNoAi(true);
			zombie.setPersistenceRequired();
			zombie.setSilent(true);
			zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.ZOMBIE_HEAD));
			if (armour) {
				zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
				zombie.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
				zombie.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
				zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			}
			level.addFreshEntity(zombie);
			return zombie.getId();
		});
	}

	/** A NoAI pig to be hit: the dummy of the authority test. */
	int dummy(double x, double z) {
		return this.s.computeOnServer(server -> {
			ServerLevel level = server.overworld();
			Pig pig = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
			pig.snapTo(x, Y, z, 180, 0);
			pig.setNoAi(true);
			pig.setPersistenceRequired();
			pig.setSilent(true);
			level.addFreshEntity(pig);
			return pig.getId();
		});
	}

	float health(int id) {
		return this.s.computeOnServer(server -> server.overworld().getEntity(id) instanceof LivingEntity le ? le.getHealth() : -1F);
	}

	void play(int id, Identifier clip) {
		this.s.runOnServer(server -> {
			if (!(server.overworld().getEntity(id) instanceof LivingEntity le) || !MotionServer.play(le, clip, 1)) {
				throw new AssertionError("couldn't play " + clip + " on " + id);
			}
		});
	}

	long serverTime() {
		return this.s.computeOnServer(server -> server.overworld().getGameTime());
	}

	static int localPlayer(Minecraft mc) {
		return mc.player.getId();
	}

	/** Holds the ACTION layer of an entity's animator at {@code ticks} after its start (NaN: live again). */
	void freeze(int id, double ticks) {
		this.c.runOnClient(mc -> {
			Animator a = ClientMotion.get(id);
			if (a == null) {
				throw new AssertionError("no animator on " + id);
			}
			a.frozenElapsed = ticks;
		});
	}
}
