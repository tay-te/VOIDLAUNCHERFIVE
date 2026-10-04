package dev.voidmc.hearth.social;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.HearthConfig;
import dev.voidmc.hearth.api.HearthDataKey;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.social.calendar.SocialTime;
import dev.voidmc.hearth.social.command.SocialCommands;
import dev.voidmc.hearth.social.dice.DiceTables;
import dev.voidmc.hearth.social.folk.CrierBehaviour;
import dev.voidmc.hearth.social.folk.DiceSeatBehaviour;
import dev.voidmc.hearth.social.folk.GatherBehaviour;
import dev.voidmc.hearth.social.folk.StallKeeperBehaviour;
import dev.voidmc.hearth.social.life.TownLife;
import dev.voidmc.hearth.social.service.Inn;
import dev.voidmc.hearth.social.service.PlayerLife;
import dev.voidmc.hearth.social.service.Stables;
import dev.voidmc.hearth.social.service.VisitingWares;
import dev.voidmc.hearth.social.service.WageTasks;
import dev.voidmc.hearth.social.talk.SocialDialogue;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The social module: town life (market days, bells, festivals, weddings, funerals, births), officials, and the
 * player's life in town (inns, bathhouses, stables, work for wages, tavern dice). See docs/SOCIAL.md and
 * docs/LIVING_WORLD.md §5–§6.
 */
public final class SocialModule {
	/** The social module's record of each settlement. */
	public static final HearthDataKey<SocialLedger> LEDGER = HearthDataKey.register(Hearth.id("social/ledger"), HearthDataKey.Scope.SETTLEMENT,
		SocialLedger.CODEC, SocialLedger::new);

	private static volatile SocialConfig config = SocialConfig.DEFAULTS;
	private static boolean behavioursRegistered;

	private SocialModule() {
	}

	public static void init() {
		config = HearthConfig.load("social", SocialConfig.CODEC, SocialConfig.DEFAULTS);
		PlayerLife.init();
		SocialItems.init();
		DiceTables.init();
		TownLife.init();
		DialogueProvider.register(new SocialDialogue());

		// folk behaviours: now if the npc module is up, else once the server starts (modules may init in any order)
		registerBehaviours();
		ServerLifecycleEvents.SERVER_STARTING.register(server -> registerBehaviours());

		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(Hearth.id("social/wares"),
			new SimpleReloadListener<Map<Culture, List<VisitingWares.Ware>>>() {
				@Override
				protected Map<Culture, List<VisitingWares.Ware>> prepare(PreparableReloadListener.SharedState state) {
					return VisitingWares.load(state.resourceManager());
				}

				@Override
				protected void apply(Map<Culture, List<VisitingWares.Ware>> data, PreparableReloadListener.SharedState state) {
					VisitingWares.set(data);
				}
			});

		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> WageTasks.onBreak(level, player, pos, state));
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> !lockedAgainst(level, player, pos));
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (lockedAgainst(level, player, hit.getBlockPos())) {
				player.sendOverlayMessage(Component.translatable("hearth.social.inn.room_locked").withStyle(ChatFormatting.RED));
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof AbstractHorse horse && horse.entityTags().contains(Stables.HIRED_TAG) && entity.level() instanceof ServerLevel level) {
				for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
					var hire = PlayerLife.of(p).hire();
					if (hire.isPresent() && hire.get().horse().equals(horse.getUUID())) {
						Stables.mountDied(p);
					}
				}
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(SocialModule::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			WageTasks.clear();
			DiceTables.clear();
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> SocialCommands.register(dispatcher));
	}

	private static synchronized void registerBehaviours() {
		NpcService npc = HearthServices.npc();
		if (behavioursRegistered || npc == null) {
			return;
		}
		npc.registerBehaviour(new DiceSeatBehaviour());
		npc.registerBehaviour(new CrierBehaviour());
		npc.registerBehaviour(new StallKeeperBehaviour());
		npc.registerBehaviour(new GatherBehaviour());
		behavioursRegistered = true;
	}

	/** Once a second, wage jobs; every five seconds, inn checkouts of online players. */
	private static void tick(MinecraftServer server) {
		long gt = server.overworld().getGameTime();
		if (gt % 20 == 0) {
			WageTasks.tick(server);
		}
		if (gt % 100 == 0) {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (PlayerLife.of(p).bed().isPresent() && p.level() instanceof ServerLevel level) {
					HearthWorld w = HearthWorld.of(server.overworld());
					Inn.checkouts(p, w == null ? SocialTime.now(level) : SocialTime.now(w));
				}
			}
		}
	}

	/** A chest let at the inn is locked to everyone but its guest (and creative players). */
	static boolean lockedAgainst(Level level, Player player, BlockPos pos) {
		if (level.isClientSide() || !(level instanceof ServerLevel server) || player.isCreative() || player.isSpectator()) {
			return false;
		}
		HearthWorld w = HearthWorld.of(server);
		if (w == null) {
			return false;
		}
		Settlement s = w.settlementAt(pos);
		if (s == null) {
			return false;
		}
		SocialLedger ledger = s.data(LEDGER);
		if (ledger.rooms.isEmpty()) {
			return false;
		}
		UUID renter = Inn.rentedTo(ledger, pos);
		return renter != null && !renter.equals(player.getUUID());
	}

	public static SocialConfig config() {
		return config;
	}

	/** Replaces the settings (tests and the dev harness). */
	public static void setConfig(SocialConfig c) {
		config = c;
	}
}
