package dev.voidmc.bestiary.api;

import dev.voidmc.bestiary.ai.Behaviours;
import dev.voidmc.bestiary.ai.Brain;
import dev.voidmc.bestiary.ai.CreatureData;
import dev.voidmc.bestiary.ai.Perception;
import dev.voidmc.bestiary.ai.VanillaTakeover;
import dev.voidmc.bestiary.ai.profile.CreatureProfile;
import dev.voidmc.bestiary.ai.profile.CreatureProfiles;
import dev.voidmc.bestiary.codex.Codex;
import dev.voidmc.bestiary.danger.Danger;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/**
 * Bestiary's api for the other mods and the boss lane (CONTRACTS.md §13.1, docs/AI.md §3). Server side; additive only.
 *
 * @since 0.1.0 (2026-10-22)
 */
public final class BestiaryApi {
	/** What Bestiary knows of a creature: its danger, elite flag and affix ids. */
	public record Info(float danger, boolean elite, List<String> affixes) {
	}

	private static final Set<LivingEntity> BOTS = Collections.newSetFromMap(new WeakHashMap<>());

	private BestiaryApi() {
	}

	public static @Nullable CreatureProfile profile(EntityType<?> type) {
		return CreatureProfiles.forType(type);
	}

	public static @Nullable CreatureProfile profile(LivingEntity e) {
		Brain b = e instanceof Mob m ? Brain.get(m) : null;
		return b != null ? b.profile : CreatureProfiles.forType(e.getType());
	}

	public static float danger(ServerLevel level, BlockPos pos) {
		return Danger.at(level, pos);
	}

	public static @Nullable Info info(LivingEntity e) {
		if (!(e instanceof Mob m) || CreatureData.get(m) == null) {
			return null;
		}
		CreatureData d = CreatureData.of(m);
		return new Info(d.danger(), d.elite(), d.affixes());
	}

	/** The creature's AI state ({@code brain.force(move)} for phase scripts), or null if Bestiary doesn't run it. */
	public static @Nullable Brain brain(Mob mob) {
		return Brain.get(mob);
	}

	/** Gives a mob the Bestiary loop with a profile (Hearth folk, bosses). Returns null for an unknown profile. */
	public static @Nullable Brain attach(Mob mob, Identifier profile) {
		CreatureProfile p = CreatureProfiles.get(profile);
		if (p == null || !(mob.level() instanceof ServerLevel level)) {
			return null;
		}
		Brain b = Brain.get(mob);
		return b != null ? b : VanillaTakeover.attach(mob, p, level);
	}

	public static void registerBehaviour(Identifier id, MoveBehaviour behaviour) {
		Behaviours.register(id, behaviour);
	}

	public static void addStealth(StealthModifier m) {
		Perception.addStealth(m);
	}

	public static void addDanger(DangerModifier m) {
		Danger.addModifier(m);
	}

	public static Codex.Book codex(ServerPlayer player) {
		return Codex.book(player);
	}

	/**
	 * Non-player entities creatures should treat as players (test mannequins standing in for bot players). Not saved.
	 */
	public static void addBot(LivingEntity bot) {
		BOTS.add(bot);
	}

	public static void removeBot(LivingEntity bot) {
		BOTS.remove(bot);
	}

	public static List<LivingEntity> bots() {
		return BOTS.isEmpty() ? List.of() : List.copyOf(BOTS);
	}
}
