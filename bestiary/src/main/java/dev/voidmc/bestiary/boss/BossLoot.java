package dev.voidmc.bestiary.boss;

import dev.voidmc.bestiary.Bestiary;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jspecify.annotations.Nullable;

/**
 * Personal loot (BESTIARY.md §8.2): every participant who earned it rolls the boss's table on their own. A player online
 * gets the items straight into their inventory (or at their feet when it is full); anyone else (offline, a test bot)
 * gets them dropped where the boss fell, marked so only they can pick them up, and kept until they do.
 */
public final class BossLoot {
	/** A roll that was awarded (tests and the log). */
	public record Award(UUID who, String whoName, Identifier table, List<String> items, boolean inventory) {
	}

	public static final List<Award> AWARDS = new CopyOnWriteArrayList<>();

	private BossLoot() {
	}

	public static void award(Mob boss, Identifier table, UUID who, @Nullable Entity entity, DamageSource source, ServerLevel level) {
		LootTable t = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, table));
		LootParams.Builder b = new LootParams.Builder(level).withParameter(LootContextParams.THIS_ENTITY, boss)
			.withParameter(LootContextParams.ORIGIN, boss.position()).withParameter(LootContextParams.DAMAGE_SOURCE, source)
			.withOptionalParameter(LootContextParams.ATTACKING_ENTITY, entity).withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, entity);
		if (entity instanceof Player p) {
			b = b.withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, p).withLuck(p.getLuck());
		}
		List<ItemStack> items = t.getRandomItems(b.create(LootContextParamSets.ENTITY));
		List<String> names = items.stream().map(s -> s.getCount() + " " + s.getHoverName().getString()).toList();
		boolean inv = entity instanceof ServerPlayer sp && sp.isAlive() && !sp.hasDisconnected();
		for (ItemStack s : items) {
			if (inv) {
				ServerPlayer sp = (ServerPlayer) entity;
				if (!sp.getInventory().add(s) && !s.isEmpty()) {
					ItemEntity ie = new ItemEntity(level, sp.getX(), sp.getY() + 0.5, sp.getZ(), s);
					ie.setTarget(who);
					ie.setNoPickUpDelay();
					level.addFreshEntity(ie);
				}
			} else {
				ItemEntity ie = new ItemEntity(level, boss.getX(), boss.getY() + 0.5, boss.getZ(), s);
				ie.setTarget(who);
				ie.setUnlimitedLifetime();
				ie.setPickUpDelay(10);
				level.addFreshEntity(ie);
			}
		}
		String name = entity == null ? who.toString() : entity.getName().getString();
		AWARDS.add(new Award(who, name, table, names, inv));
		Bestiary.LOGGER.info("personal loot for {}: {}", name, names);
	}
}
