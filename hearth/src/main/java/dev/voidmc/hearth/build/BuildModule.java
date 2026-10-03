package dev.voidmc.hearth.build;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.build.archetype.Archetypes;
import dev.voidmc.hearth.build.command.BuildCommand;
import dev.voidmc.hearth.build.style.StyleRegistry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;

/**
 * The build module: styles, archetypes and blueprint generation. Owned by the build agent; see docs/ARCHITECTURE.md §6
 * and docs/BUILDING_GUIDE.md.
 *
 * <p>{@link #init()} wires the module into the game (service, style reload, {@code /hearth build}); {@link #standalone()}
 * gives the same service without Fabric, for unit tests and the offline preview.
 */
public final class BuildModule {
	private static volatile BlueprintServiceImpl service;

	private BuildModule() {
	}

	public static void init() {
		BlueprintServiceImpl s = standalone();
		service = s;
		HearthServices.setBlueprints(s);
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(Hearth.id("build/styles"), new StyleReloader(s));
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> BuildCommand.register(dispatcher));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> s.clearCache());
	}

	/** A fresh service with the built-in styles and every archetype, independent of the game's lifecycle. */
	public static BlueprintServiceImpl standalone() {
		ArchetypeRegistry registry = new ArchetypeRegistry();
		Archetypes.registerAll(registry);
		return new BlueprintServiceImpl(registry.freeze(), StyleRegistry.builtin());
	}

	/** The service registered by {@link #init()}, or a standalone one when the module was never initialised. */
	public static BlueprintServiceImpl service() {
		BlueprintServiceImpl s = service;
		if (s == null) {
			synchronized (BuildModule.class) {
				s = service;
				if (s == null) {
					s = standalone();
					service = s;
				}
			}
		}
		return s;
	}

	/** Reloads styles from data packs (server data: {@code data/<ns>/hearth_style/*.json}). */
	private record StyleReloader(BlueprintServiceImpl service) implements PreparableReloadListener {
		@Override
		public CompletableFuture<Void> reload(SharedState state, Executor prepare, PreparationBarrier barrier, Executor apply) {
			return CompletableFuture.completedFuture(null).thenCompose(barrier::wait).thenRunAsync(() -> {
				this.service.styles().reload(state.resourceManager());
				this.service.clearCache();
			}, apply);
		}
	}
}
