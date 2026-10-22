package dev.voidmc.fray.api;

import java.util.Objects;

/**
 * Fray's service locator (CONTRACTS.md §3). Never null after mod init; consumers look services up lazily, never in
 * static initialisers. (Named FrayApi because {@code dev.voidmc.fray.Fray} is the mod's entrypoint.)
 *
 * <p>Versioning: {@link #API} follows semver and is additive only inside a major version; {@link #PROTOCOL} is bumped
 * whenever server and client must match (payload formats), and the combat handshake refuses a mismatch at login.
 *
 * @since 0.1.0 (2026-10-21)
 */
public final class FrayApi {
	/** The combat api's version (semver). */
	public static final String API = "0.1.0";
	/** The combat network protocol; part of the login handshake. */
	public static final int PROTOCOL = 1;

	private static DamageService damage;
	private static StatusService statuses;
	private static ResourceService resources;
	private static ThreatService threat;
	private static ActionService actions;
	private static EncounterService encounters;
	private static TelegraphService telegraphs;
	private static ProfileService profiles;
	private static DefenseHooks defense;
	private static volatile BalanceProvider balance = BalanceProvider.NEUTRAL;

	private FrayApi() {
	}

	public static DamageService damage() {
		return damage;
	}

	public static StatusService statuses() {
		return statuses;
	}

	public static ResourceService resources() {
		return resources;
	}

	public static ThreatService threat() {
		return threat;
	}

	public static ActionService actions() {
		return actions;
	}

	public static EncounterService encounters() {
		return encounters;
	}

	public static TelegraphService telegraphs() {
		return telegraphs;
	}

	public static ProfileService profiles() {
		return profiles;
	}

	public static DefenseHooks defense() {
		return defense;
	}

	public static BalanceProvider balance() {
		return balance;
	}

	/** Mettle replaces {@link BalanceProvider#NEUTRAL} during its init. */
	public static void setBalance(BalanceProvider provider) {
		balance = Objects.requireNonNull(provider);
	}

	/** Fray's own init wires the implementations; not for other mods. */
	public static void install(DamageService d, StatusService s, ResourceService r, ThreatService t, ActionService a, EncounterService e, TelegraphService tg,
		ProfileService p, DefenseHooks df) {
		damage = d;
		statuses = s;
		resources = r;
		threat = t;
		actions = a;
		encounters = e;
		telegraphs = tg;
		profiles = p;
		defense = df;
	}
}
