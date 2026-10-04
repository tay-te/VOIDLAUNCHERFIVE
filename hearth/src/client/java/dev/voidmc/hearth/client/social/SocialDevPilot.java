package dev.voidmc.hearth.client.social;

import com.mojang.blaze3d.platform.InputConstants;
import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.social.dice.DiceGame;
import dev.voidmc.hearth.social.dice.DiceMenu;
import dev.voidmc.hearth.social.dice.DicePayloads;
import dev.voidmc.hearth.social.dice.DiceView;
import dev.voidmc.hearth.social.dice.Farkle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Dev tooling, off unless the environment variable {@code HEARTH_DEV_SOCIAL=shots} (or {@code -Dhearth.dev.social.shots=true})
 * is set: makes a test world, founds a camp at spawn, sits down to dice with one of its folk, plays a few turns (and a
 * game with loaded dice), holds a market and a festival in the camp, and saves a screenshot of each step to
 * {@code run/screenshots/social/}, then quits.
 *
 * <pre>HEARTH_DEV_SOCIAL=shots ./gradlew runClient</pre>
 */
public final class SocialDevPilot {
	private static final int SETTLE = 30;
	private final List<Step> steps = new ArrayList<>();
	private int step;
	private int wait;
	private boolean worldRequested;

	private record Step(int delay, Consumer<Minecraft> action) {
	}

	private SocialDevPilot() {
		script();
	}

	public static void initIfRequested() {
		if ("shots".equals(System.getenv("HEARTH_DEV_SOCIAL")) || Boolean.getBoolean("hearth.dev.social.shots")) {
			SocialDevPilot pilot = new SocialDevPilot();
			ClientTickEvents.END_CLIENT_TICK.register(pilot::tick);
			Hearth.LOG.info("Social dev pilot: on");
		}
	}

	private void script() {
		run(60, c -> {
			c.getTutorial().setStep(TutorialSteps.NONE);
			c.options.pauseOnLostFocus = false;
			command(c, "gamemode survival");
			command(c, "time set 6000");
			command(c, "weather clear");
			command(c, "hearth settlement found temperate");
		});
		run(100, c -> {
			command(c, "hearth money give @s 3c");
			command(c, "give @s hearth:loaded_dice");
			command(c, "hearth social offices");
		});
		run(40, c -> command(c, "hearth social dice 0"));
		shot("dice_stake");
		run(5, c -> act(c, DicePayloads.Action.STAKE, lastStake(c)));
		run(30, c -> {
		});
		shot("dice_rolled");
		run(5, c -> pickScoring(c));
		shot("dice_picked");
		run(5, c -> act(c, DicePayloads.Action.BANK, 0));
		run(60, c -> {
		});
		shot("dice_folk_turn");
		// play on for a while: pick scoring dice and bank, pass after a bust
		for (int i = 0; i < 30; i++) {
			run(70, SocialDevPilot::autoplay);
		}
		shot("dice_later");
		run(5, c -> c.player.closeContainer());
		run(20, c -> {
			command(c, "time set 1000");
			command(c, "hearth settlement tp 1");
		});
		run(20, c -> command(c, "hearth social force market"));
		run(80, c -> command(c, "hearth social force festival lanterns"));
		run(260, c -> {
			c.gui.setScreen(null);
			// creative and flying, not spectator: a spectator does not count as someone near the town
			command(c, "gamemode creative");
			command(c, "tp @s ~ ~18 ~-16 0 45");
		});
		run(5, c -> {
			c.player.getAbilities().flying = true;
			c.player.onUpdateAbilities();
		});
		shot("market_and_festival");
		run(5, c -> command(c, "tp @s ~-16 ~ ~16 270 45"));
		shot("market_and_festival_side");
		run(20, c -> {
			command(c, "hearth social events");
			command(c, "hearth social status");
		});
		run(10, c -> c.gui.setScreen(new net.minecraft.client.gui.screens.ChatScreen("", false)));
		shot("events_chat");
		run(10, Minecraft::stop);
	}

	private static int lastStake(Minecraft c) {
		if (c.player.containerMenu instanceof DiceMenu menu) {
			return Math.max(0, menu.view().stakes().size() - 1);
		}
		return 0;
	}

	private static void autoplay(Minecraft c) {
		if (!(c.player.containerMenu instanceof DiceMenu menu)) {
			return;
		}
		DiceView v = menu.view();
		switch (v.phaseEnum()) {
			case PLAYER_ROLLED -> {
				if (v.selectedMask() == 0) {
					pickScoring(c);
				} else {
					act(c, v.turnPoints() + v.selectionScore() >= 350 ? DicePayloads.Action.BANK : DicePayloads.Action.ROLL, 0);
				}
			}
			case PLAYER_BUST -> act(c, DicePayloads.Action.PASS, 0);
			case OVER -> {
			}
			default -> {
			}
		}
	}

	/** Picks every scoring die (falling back to a single 1 or 5 if together they are not a valid set). */
	private static void pickScoring(Minecraft c) {
		if (!(c.player.containerMenu instanceof DiceMenu menu) || menu.view().phaseEnum() != DiceGame.Phase.PLAYER_ROLLED) {
			return;
		}
		DiceView v = menu.view();
		int pick = v.scoringMask();
		if (Farkle.scoreMask(v.dice(), pick) <= 0) {
			pick = 0;
			for (int i = 0; i < 6; i++) {
				if ((v.keptMask() >> i & 1) == 0 && (v.dice()[i] == 1 || v.dice()[i] == 5)) {
					pick = 1 << i;
					break;
				}
			}
		}
		for (int i = 0; i < 6; i++) {
			if ((pick >> i & 1) != 0) {
				act(c, DicePayloads.Action.TOGGLE, i);
			}
		}
	}

	private static void act(Minecraft c, int action, int arg) {
		if (c.player.containerMenu instanceof DiceMenu menu) {
			ClientPlayNetworking.send(new DicePayloads.Action(menu.containerId, action, arg));
		}
	}

	private void run(int delay, Consumer<Minecraft> action) {
		this.steps.add(new Step(delay, action));
	}

	private void shot(String name) {
		run(SETTLE - 2, c -> {
			c.gui.toastManager().clear();
			if (c.gui.screen() instanceof net.minecraft.client.gui.screens.PauseScreen) {
				c.gui.setScreen(null);
			}
		});
		run(2, c -> Screenshot.takeScreenshot(c.gameRenderer.mainRenderTarget(), image -> {
			try {
				Path dir = c.gameDirectory.toPath().resolve("screenshots").resolve("social");
				Files.createDirectories(dir);
				image.writeToFile(dir.resolve(name + ".png"));
				Hearth.LOG.info("Social dev pilot: saved {}", name);
			} catch (Exception e) {
				Hearth.LOG.error("Social dev pilot: could not save {}", name, e);
			} finally {
				image.close();
			}
		}));
	}

	private void tick(Minecraft c) {
		if (c.level == null || c.player == null) {
			if (!this.worldRequested && c.gui.screen() instanceof TitleScreen) {
				this.worldRequested = true;
				createWorld(c);
			}
			return;
		}
		if (this.step >= this.steps.size()) {
			return;
		}
		Step s = this.steps.get(this.step);
		if (++this.wait < s.delay()) {
			return;
		}
		this.wait = 0;
		this.step++;
		try {
			s.action().accept(c);
		} catch (RuntimeException e) {
			Hearth.LOG.error("Social dev pilot: step {} failed", this.step, e);
		}
	}

	private static void createWorld(Minecraft c) {
		CreateWorldScreen.openFresh(c, () -> c.gui.setScreen(new TitleScreen()));
		if (!(c.gui.screen() instanceof CreateWorldScreen screen)) {
			Hearth.LOG.error("Social dev pilot: no create-world screen");
			return;
		}
		WorldCreationUiState ui = screen.getUiState();
		ui.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
		ui.setAllowCommands(true);
		ui.setSeed("20261003");
		for (GuiEventListener child : screen.children()) {
			if (child instanceof Button b && b.getMessage().getContents() instanceof TranslatableContents t && t.getKey().equals("selectWorld.create")) {
				b.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
				return;
			}
		}
		Hearth.LOG.error("Social dev pilot: no create button");
	}

	private static void command(Minecraft c, String command) {
		c.player.connection.sendCommand(command);
	}
}
