package dev.voidmc.hearth.api;

import java.util.Objects;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

/**
 * Where modules find each other. Each module registers what it provides during its {@code init()} (see Hearth.java for
 * the order); consumers look services up when they need them, never in static initialisers.
 *
 * <pre>
 *   blueprints  build module    required
 *   planner     plan module     required
 *   worlds      sim module      required
 *   culture     sim module      required
 *   residents   sim (default) → npc module replaces it
 *   economy     economy module  optional (null if absent)
 *   quests      quest module    optional (null if absent)
 *   npc         npc module      optional: folk lookups, perception, behaviours other modules add
 *   standing    standing module optional: reputation layers, gossip, charisma
 *   law         law module      optional: ownership, stolen goods, wanted status, crime reports
 *   repairs     repair module   optional: damage and repair queues
 *   skills      another mod     optional: a player's skills, attributes and perks ({@link PlayerSkills}, e.g. VOID Mettle);
 *                               never null, {@link PlayerSkills#NONE} until one registers
 * </pre>
 */
public final class HearthServices {
	private static volatile @Nullable BlueprintService blueprints;
	private static volatile @Nullable SettlementPlanner planner;
	private static volatile @Nullable Function<ServerLevel, @Nullable HearthWorld> worlds;
	private static volatile @Nullable Function<Holder<Biome>, Culture> culture;
	private static volatile @Nullable ResidentFactory residents;
	private static volatile @Nullable EconomyService economy;
	private static volatile @Nullable QuestService quests;
	private static volatile @Nullable NpcService npc;
	private static volatile @Nullable StandingService standing;
	private static volatile @Nullable LawService law;
	private static volatile @Nullable RepairService repairs;
	private static volatile PlayerSkills playerSkills = PlayerSkills.NONE;

	private HearthServices() {
	}

	public static BlueprintService blueprints() {
		return Objects.requireNonNull(blueprints, "the build module has not registered a BlueprintService");
	}

	public static void setBlueprints(BlueprintService service) {
		blueprints = service;
	}

	public static boolean hasBlueprints() {
		return blueprints != null;
	}

	public static boolean hasPlanner() {
		return planner != null;
	}

	public static SettlementPlanner planner() {
		return Objects.requireNonNull(planner, "the plan module has not registered a SettlementPlanner");
	}

	public static void setPlanner(SettlementPlanner service) {
		planner = service;
	}

	public static @Nullable HearthWorld world(ServerLevel level) {
		Function<ServerLevel, @Nullable HearthWorld> w = worlds;
		return w == null ? null : w.apply(level);
	}

	public static void setWorlds(Function<ServerLevel, @Nullable HearthWorld> lookup) {
		worlds = lookup;
	}

	/** The culture a settlement founded in this biome follows. */
	public static Culture culture(Holder<Biome> biome) {
		return Objects.requireNonNull(culture, "the sim module has not registered a culture mapping").apply(biome);
	}

	public static void setCulture(Function<Holder<Biome>, Culture> mapping) {
		culture = mapping;
	}

	public static ResidentFactory residents() {
		return Objects.requireNonNull(residents, "no ResidentFactory registered");
	}

	public static void setResidents(ResidentFactory factory) {
		residents = factory;
	}

	public static @Nullable EconomyService economy() {
		return economy;
	}

	public static void setEconomy(EconomyService service) {
		economy = service;
	}

	public static @Nullable QuestService quests() {
		return quests;
	}

	public static void setQuests(QuestService service) {
		quests = service;
	}

	public static @Nullable NpcService npc() {
		return npc;
	}

	public static void setNpc(NpcService service) {
		npc = service;
	}

	public static @Nullable StandingService standing() {
		return standing;
	}

	public static void setStanding(StandingService service) {
		standing = service;
	}

	public static @Nullable LawService law() {
		return law;
	}

	public static void setLaw(LawService service) {
		law = service;
	}

	public static @Nullable RepairService repairs() {
		return repairs;
	}

	public static void setRepairs(RepairService service) {
		repairs = service;
	}

	/** The player-skills service: a character mod's, or {@link PlayerSkills#NONE} (neutral; Hearth's checks unchanged). */
	public static PlayerSkills playerSkills() {
		return playerSkills;
	}

	/** Registered by a character mod when Hearth is loaded (from its own compat code); null puts back {@link PlayerSkills#NONE}. */
	public static void setPlayerSkills(@Nullable PlayerSkills service) {
		playerSkills = service == null ? PlayerSkills.NONE : service;
	}
}
