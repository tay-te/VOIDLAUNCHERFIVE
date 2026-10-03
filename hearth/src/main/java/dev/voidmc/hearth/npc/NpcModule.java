package dev.voidmc.hearth.npc;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.HearthConfig;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.SettlementEvents;
import dev.voidmc.hearth.mixin.npc.MobAccessor;
import dev.voidmc.hearth.npc.dev.NpcCommands;
import dev.voidmc.hearth.npc.dev.NpcSandbox;
import dev.voidmc.hearth.npc.embody.EmbodimentManager;
import dev.voidmc.hearth.npc.embody.TownState;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.npc.entity.FolkEntities;
import dev.voidmc.hearth.npc.person.HearthResidentFactory;
import dev.voidmc.hearth.npc.person.NameBank;
import dev.voidmc.hearth.npc.talk.Conversations;
import dev.voidmc.hearth.npc.talk.DialogueTemplates;
import dev.voidmc.hearth.npc.talk.Greetings;
import dev.voidmc.hearth.npc.talk.NpcDialogue;
import dev.voidmc.hearth.npc.talk.TalkPayloads;
import dev.voidmc.hearth.npc.talk.TownNews;
import dev.voidmc.hearth.npc.work.ScaffoldLedger;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.Mob;

/**
 * The NPC module: hearthfolk. Residents embodied as entities near players, their looks, routines and trades, builders
 * raising their town block by block, conversation, quest markers and the dangers of the night. See docs/FOLK.md.
 */
public final class NpcModule {
	private static NpcConfig config = NpcConfig.DEFAULTS;
	private static final EmbodimentManager EMBODIMENT = new EmbodimentManager();
	private static final Conversations CONVERSATIONS = new Conversations();

	private NpcModule() {
	}

	public static NpcConfig config() {
		return config;
	}

	public static EmbodimentManager embodiment() {
		return EMBODIMENT;
	}

	public static Conversations conversations() {
		return CONVERSATIONS;
	}

	public static void init() {
		config = HearthConfig.load("npc", NpcConfig.CODEC, NpcConfig.DEFAULTS);
		FolkEntities.init();
		// Touch the key so it is registered before any world loads.
		Hearth.LOG.debug("npc data key {}", ScaffoldLedger.KEY.id());
		HearthServices.setResidents(new HearthResidentFactory(NpcTime::today));
		DialogueProvider.register(new NpcDialogue());
		NpcSandbox.init();
		registerNetworking();
		registerReloaders();
		registerEvents();
		CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> NpcCommands.register(dispatcher));
	}

	private static void registerNetworking() {
		TalkPayloads.register();
		ServerPlayNetworking.registerGlobalReceiver(TalkPayloads.Choose.TYPE,
			(payload, ctx) -> CONVERSATIONS.choose(ctx.player(), payload.entity(), payload.option()));
		ServerPlayNetworking.registerGlobalReceiver(TalkPayloads.End.TYPE, (payload, ctx) -> CONVERSATIONS.end(ctx.player(), payload.entity()));
	}

	private static void registerEvents() {
		ServerLifecycleEvents.SERVER_STARTED.register(NpcTime::setServer);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			// Everyone back into their records before the sim saves them.
			EMBODIMENT.foldAll(server);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			EMBODIMENT.onServerStopped();
			NpcSandbox.clearAll();
			NpcTime.setServer(null);
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			EMBODIMENT.tick(server);
			if (server.getTickCount() % 100 == 0) {
				CONVERSATIONS.tick();
			}
		});
		// Monsters hunt folk as they hunt villagers.
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (config.threats().hostilesTargetFolk() && entity instanceof Mob mob && huntsFolk(mob)) {
				((MobAccessor) mob).hearth$targetSelector().addGoal(3, new NearestAttackableTargetGoal<>(mob, Folk.class, true));
			}
		});

		// The town's news, for folk to talk about. Cheap: these fire during abstract simulation too.
		SettlementEvents.BUILDING_COMPLETED.register((world, s, plot) -> {
			TownState town = EMBODIMENT.town(s);
			town.news.add(new TownNews.Item(TownNews.Kind.BUILT, world.day(), Greetings.buildingName(plot.spec().archetype()), null, 0, null));
			town.clearStalled(plot.id());
			town.places.invalidate(plot.id());
		});
		SettlementEvents.CONSTRUCTION_STALLED.register((world, s, plot, goods, missing) -> {
			TownState town = EMBODIMENT.town(s);
			town.news.add(new TownNews.Item(TownNews.Kind.STALLED, world.day(), Greetings.buildingName(plot.spec().archetype()), goods, missing, null));
			// Builders leave a stalled plot for a couple of minutes; the sim will start the stage when goods arrive.
			town.markStalled(plot.id(), world.level().getGameTime() + 2400);
		});
		SettlementEvents.TIER_CHANGED.register((world, s, from, to) -> {
			if (to.ordinal() > from.ordinal()) {
				EMBODIMENT.town(s).news.add(new TownNews.Item(TownNews.Kind.TIER, world.day(), s.name(), null, 0, to));
			}
		});
		SettlementEvents.RESIDENT_ADDED.register((world, s, r) -> {
			if (world.day() - s.foundedDay() > 0) {
				EMBODIMENT.town(s).news.add(new TownNews.Item(TownNews.Kind.ARRIVED, world.day(), r.givenName, null, 0, null));
			}
		});
		SettlementEvents.RESIDENT_REMOVED.register((world, s, r) -> {
			EMBODIMENT.town(s).news.add(new TownNews.Item(TownNews.Kind.DIED, world.day(), r.fullName(), null, 0, null));
			EMBODIMENT.onResidentRemoved(r.id);
		});
		SettlementEvents.FOUNDED.register((world, s) ->
			EMBODIMENT.town(s).news.add(new TownNews.Item(TownNews.Kind.FOUNDED, world.day(), s.name(), null, 0, null)));
		SettlementEvents.EMBODIMENT_CHANGED.register((world, s, embodied) -> {
			if (!embodied) {
				EMBODIMENT.onSettlementAbstract(world.level(), s.id());
			}
		});
	}

	private static boolean huntsFolk(Mob mob) {
		return mob instanceof Zombie || mob instanceof AbstractSkeleton || mob instanceof Spider || mob instanceof AbstractIllager
			|| mob instanceof Ravager || mob instanceof Vex;
	}

	/** Names and dialogue lines come from data, so data packs can add to or replace them. */
	private static void registerReloaders() {
		ResourceLoader loader = ResourceLoader.get(PackType.SERVER_DATA);
		loader.registerReloadListener(Hearth.id("npc/names"), new SimpleReloadListener<Map<Culture, NameBank.Names>>() {
			@Override
			protected Map<Culture, NameBank.Names> prepare(PreparableReloadListener.SharedState state) {
				Map<Culture, NameBank.Names> out = new EnumMap<>(Culture.class);
				for (Map.Entry<Identifier, Resource> e : state.resourceManager().listResources("hearth_names", id -> id.getPath().endsWith(".json")).entrySet()) {
					String name = e.getKey().getPath().substring("hearth_names/".length()).replace(".json", "");
					for (Culture c : Culture.values()) {
						if (c.getSerializedName().equals(name)) {
							try (Reader r = e.getValue().openAsReader()) {
								out.put(c, NameBank.parse(r));
							} catch (IOException | RuntimeException ex) {
								Hearth.LOG.warn("could not read names {}", e.getKey(), ex);
							}
						}
					}
				}
				return out;
			}

			@Override
			protected void apply(Map<Culture, NameBank.Names> prepared, PreparableReloadListener.SharedState state) {
				NameBank.replace(prepared);
			}
		});
		loader.registerReloadListener(Hearth.id("npc/dialogue"), new SimpleReloadListener<Map<String, List<DialogueTemplates.Line>>>() {
			@Override
			protected Map<String, List<DialogueTemplates.Line>> prepare(PreparableReloadListener.SharedState state) {
				Map<String, List<DialogueTemplates.Line>> out = new HashMap<>();
				for (Map.Entry<Identifier, Resource> e : state.resourceManager().listResources("hearth_dialogue", id -> id.getPath().endsWith(".json"))
					.entrySet()) {
					try (Reader r = e.getValue().openAsReader()) {
						DialogueTemplates.parseInto(r, out);
					} catch (IOException | RuntimeException ex) {
						Hearth.LOG.warn("could not read dialogue {}", e.getKey(), ex);
					}
				}
				if (out.isEmpty()) {
					out.putAll(DialogueTemplates.loadBuiltin());
				}
				out.replaceAll((k, v) -> new ArrayList<>(v));
				return out;
			}

			@Override
			protected void apply(Map<String, List<DialogueTemplates.Line>> prepared, PreparableReloadListener.SharedState state) {
				DialogueTemplates.replace(prepared);
			}
		});
	}
}
