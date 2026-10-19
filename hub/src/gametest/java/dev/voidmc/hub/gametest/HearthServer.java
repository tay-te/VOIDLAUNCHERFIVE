package dev.voidmc.hub.gametest;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/**
 * VOID Hearth's server side for the hub's Hearth test, by reflection (the hub never links against Hearth): the
 * conversation a right-click opens, and the law's events a guard, a lock or a pocket would send. Server thread only.
 * Everything else the test does goes through Hearth's own commands and real input.
 */
final class HearthServer {

	private HearthServer() {
	}

	static Class<?> cls(String name) {
		try {
			return Class.forName(name);
		} catch (ClassNotFoundException e) {
			throw new AssertionError("Hearth class " + name + " is missing", e);
		}
	}

	static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}

	/** The nearest embodied folk to the player (within 16 blocks), or null. */
	static Entity nearestFolk(MinecraftServer server) {
		ServerPlayer p = player(server);
		Class<?> folk = cls("dev.voidmc.hearth.npc.entity.Folk");
		Entity best = null;
		for (Entity e : p.level().getEntities((Entity) null, new AABB(p.blockPosition()).inflate(16), folk::isInstance)) {
			if (best == null || e.distanceTo(p) < best.distanceTo(p)) {
				best = e;
			}
		}
		return best;
	}

	/** Opens a conversation with a folk as right-clicking it does ({@code NpcModule.conversations().open}). */
	static void talk(MinecraftServer server, Entity folk) {
		try {
			Object conversations = cls("dev.voidmc.hearth.npc.NpcModule").getMethod("conversations").invoke(null);
			Method open = conversations.getClass().getMethod("open", ServerPlayer.class, cls("dev.voidmc.hearth.npc.entity.Folk"));
			open.invoke(conversations, player(server), folk);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("Hearth conversation", e.getCause() == null ? e : e.getCause());
		}
	}

	private static Object make(String cls, Object... args) {
		Class<?> c = cls(cls);
		for (Constructor<?> k : c.getDeclaredConstructors()) {
			if (k.getParameterCount() == args.length) {
				try {
					k.setAccessible(true);
					return k.newInstance(args);
				} catch (ReflectiveOperationException | IllegalArgumentException e) {
					// another shape
				}
			}
		}
		throw new AssertionError("no " + cls + " constructor for " + args.length + " arguments");
	}

	private static void send(MinecraftServer server, Object payload) {
		ServerPlayNetworking.send(player(server), (CustomPacketPayload) payload);
	}

	/** A guard stops the player (Hearth's own payloads, as Confrontations sends them): the choices and their facts. */
	static void confront(MinecraftServer server, int entity) {
		String law = "dev.voidmc.hearth.law.net.LawPayloads$";
		List<Object> choices = new ArrayList<>();
		String[][] c = {{"pay", "hearth.law.choice.pay", "1 crown, 2 marks"}, {"submit", "hearth.law.choice.jail", "5"}, {"pillory", "hearth.law.choice.pillory", "3"},
			{"persuade", "hearth.law.choice.persuade", "41"}, {"bribe", "hearth.law.choice.bribe", "6 marks"}, {"flee", "hearth.law.choice.flee", ""},
			{"resist", "hearth.law.choice.resist", ""}};
		for (String[] ch : c) {
			Component label = ch[2].isEmpty() ? Component.translatable(ch[1]) : ch[0].equals("bribe") ? Component.translatable(ch[1], ch[2], 55)
				: Component.translatable(ch[1], ch[2]);
			choices.add(make(law + "Choice", ch[0], label, true));
		}
		send(server, make(law + "Confront", entity, true, "Hal Brennick",
			Component.translatable("hearth.law.guard.halt", Component.translatable("hearth.offence.theft"), 120), 120L, choices));
		List<Object> info = new ArrayList<>();
		Object[][] facts = {{"pay", "pay", -1, 120L, -1}, {"submit", "cell", -1, -1L, 5}, {"pillory", "pillory", -1, -1L, 3}, {"persuade", "persuade", 41, -1L, -1},
			{"bribe", "bribe", 55, 60L, -1}, {"flee", "run", -1, -1L, -1}, {"resist", "fight", -1, -1L, -1}};
		for (Object[] f : facts) {
			info.add(make(law + "ChoiceInfo", f));
		}
		send(server, make(law + "ConfrontDetail", entity, "arrest", "hearth.npc.job.guard", List.of("brave", "grumpy"), -12, 24, "known", "Bradfield",
			"hearth.offence.theft", 1, "Edda Miller", info));
	}

	/** A lock is started (as LockpickSessions does on a locked chest with picks in hand). */
	static void lockpick(MinecraftServer server) {
		send(server, make("dev.voidmc.hearth.law.net.LawPayloads$LockpickStart", 7001, 3, 4242L, 6, 4, Component.literal("Edda Miller’s chest")));
	}

	/** Pockets open (as Pickpocketing does when the player reaches into a folk's pockets). */
	static void pickpocket(MinecraftServer server) {
		List<ItemStack> items = List.of(new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("hearth", "copper_bit")), 12),
			new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("hearth", "iron_mark")), 2), new ItemStack(Items.BREAD, 1),
			new ItemStack(Items.APPLE, 3), new ItemStack(Items.PAPER, 1), new ItemStack(Items.TRIPWIRE_HOOK, 1));
		send(server, make("dev.voidmc.hearth.law.net.LawPayloads$Pocket", 7002, "Edda Miller", false, items, 0.42F, 0.0F, -1, 0.0F, 0.0F, 0));
	}

	/** The notice board block the quest sandbox put up near the player, or null. */
	static BlockPos board(MinecraftServer server) {
		ServerPlayer p = player(server);
		Identifier id = Identifier.fromNamespaceAndPath("hearth", "notice_board");
		BlockPos c = p.blockPosition();
		for (BlockPos q : BlockPos.betweenClosed(c.offset(-6, -4, -6), c.offset(6, 6, 6))) {
			if (BuiltInRegistries.BLOCK.getKey(p.level().getBlockState(q).getBlock()).equals(id)) {
				return q.immutable();
			}
		}
		return null;
	}
}
