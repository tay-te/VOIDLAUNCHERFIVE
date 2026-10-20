package dev.voidmc.hearth.social.events;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * {@code config/hearth/events.json} (docs/LIVING_WORLD_2.md §3). Every field is optional, so an old file keeps working
 * when fields are added. Caps are the most at a comfortable tick time and shrink as the server slows
 * ({@link dev.voidmc.hearth.sim.LoadCaps}).
 */
public record EventsConfig(Events events) {
	public static final EventsConfig DEFAULTS = new EventsConfig(Events.DEFAULTS);

	public static final Codec<EventsConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
		Events.CODEC.optionalFieldOf("events", Events.DEFAULTS).forGetter(EventsConfig::events)
	).apply(i, EventsConfig::new));

	/**
	 * @param enabled town events (fairs, trials, raids, tax days, weddings and funerals of note)
	 * @param liveFires scripted fires burn for real: "auto" (on in single player, off on servers), "on" or "off"
	 * @param raidDeaths a raid may, rarely, kill a resident for good
	 * @param executions "staged" (shown up to the gallows steps, then a cut) or "never"
	 * @param oneMajorScene one major scene in a town at a time
	 */
	public record Events(boolean enabled, String liveFires, boolean raidDeaths, String executions, boolean oneMajorScene) {
		public static final Events DEFAULTS = new Events(true, "auto", true, "staged", true);
		public static final Codec<Events> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", DEFAULTS.enabled).forGetter(Events::enabled),
			Codec.STRING.optionalFieldOf("live_fires", DEFAULTS.liveFires).forGetter(Events::liveFires),
			Codec.BOOL.optionalFieldOf("raid_deaths", DEFAULTS.raidDeaths).forGetter(Events::raidDeaths),
			Codec.STRING.optionalFieldOf("executions", DEFAULTS.executions).forGetter(Events::executions),
			Codec.BOOL.optionalFieldOf("one_major_scene", DEFAULTS.oneMajorScene).forGetter(Events::oneMajorScene)
		).apply(i, Events::new));
	}
}
