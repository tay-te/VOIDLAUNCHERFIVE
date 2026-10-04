package dev.voidmc.lod.gui;

import com.mojang.serialization.Codec;
import dev.voidmc.lod.LodSettings;
import dev.voidmc.lod.LodSettings.Preset;
import dev.voidmc.lod.VoidLod;
import dev.voidmc.lod.core.AutoTune;
import dev.voidmc.lod.render.LodRenderer;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

/**
 * VOID LOD's settings, as a vanilla options screen: the quality preset, then what it sets and the rest, with
 * what the LOD draws and costs right now under the title. Every change applies at once (the reach, detail and
 * haze from the next frame, threads by starting or stopping workers, the memory budget at the next sweep) and
 * the file is written when the screen closes.
 *
 * <p>The ways in: a button in vanilla's Options screen (which mods that replace Video Settings, like Sodium,
 * leave alone), another at the end of Video Settings, and a key, unbound by default.
 *
 * <p>The detail sliders run from coarse on the left to sharp on the right, like every other quality slider,
 * though what they set is the most pixels a cell may cover, where less is sharper.
 */
public final class LodSettingsScreen extends OptionsSubScreen {
	private static final Component TITLE = Component.translatable("options.void_lod.title");
	private static final Component HEADER = Component.translatable("options.void_lod.header").withStyle(ChatFormatting.UNDERLINE, ChatFormatting.BOLD);
	private static final Component BUTTON = Component.translatable("options.void_lod.button");
	/** Never written: the settings have their own file. The option framework just wants one. */
	private static final Codec<Preset> PRESET_CODEC = Codec.STRING.xmap(Preset::byName, Preset::serializedName);
	/** The detail sliders: 16 px per cell at the left, 2 at the right, in half pixels. */
	private static final double COARSEST = 16;
	private static final int DETAIL_STEPS = 28;

	private final VoidLod lod;
	private final LinearLayout header = LinearLayout.vertical().spacing(2);
	private StringWidget status;
	private int ticks;
	/** Set while the screen moves the sliders itself, so they do not count as edits by hand. */
	private boolean showing;
	private final OptionInstance<Preset> preset;
	private final OptionInstance<Boolean> enabled;
	private final OptionInstance<Boolean> autoTune;
	private final OptionInstance<Integer> radius;
	private final OptionInstance<Integer> haze;
	private final OptionInstance<Double> detail;
	private final OptionInstance<Double> farDetail;
	private final OptionInstance<Integer> threads;
	private final OptionInstance<Integer> budget;

	public LodSettingsScreen(Screen lastScreen) {
		super(lastScreen, Minecraft.getInstance().options, TITLE);
		this.lod = VoidLod.get();
		LodSettings s = this.lod.settings();
		this.preset = new OptionInstance<>("options.void_lod.preset", tooltip("options.void_lod.preset.tooltip"),
			(caption, value) -> Options.genericValueLabel(caption, Component.translatable(value.translationKey())),
			new OptionInstance.SliderableEnum<>(List.of(Preset.values()), PRESET_CODEC), s.preset, this::choose);
		this.enabled = OptionInstance.createBoolean("options.void_lod.enabled", tooltip("options.void_lod.enabled.tooltip"), this.lod.isEnabled(), value -> {
			this.lod.change(settings -> settings.enabled = value);
			this.lod.setEnabled(value);
		});
		this.autoTune = OptionInstance.createBoolean("options.void_lod.autoTune", tooltip("options.void_lod.autoTune.tooltip"), s.autoTune,
			value -> this.lod.change(settings -> settings.autoTune = value));
		this.radius = new OptionInstance<>("options.void_lod.radius", tooltip("options.void_lod.radius.tooltip"),
			(caption, value) -> Options.genericValueLabel(caption, kilometres(value)),
			new OptionInstance.IntRange(1, LodSettings.MAX_RADIUS / 1024).<Integer>xmap(km -> km * 1024,
				blocks -> Math.clamp(Math.round(blocks / 1024.0F), 1, LodSettings.MAX_RADIUS / 1024), true),
			s.radius, value -> this.edit(settings -> settings.radius = value));
		this.haze = new OptionInstance<>("options.void_lod.haze", tooltip("options.void_lod.haze.tooltip"),
			(caption, value) -> Options.genericValueLabel(caption, value == 0 ? Component.translatable("options.void_lod.haze.auto") : kilometres(value)),
			new OptionInstance.IntRange(0, 64).<Integer>xmap(i -> i * 512, blocks -> Math.clamp(Math.round(blocks / 512.0F), 0, 64), true),
			s.haze, value -> this.edit(settings -> settings.haze = value));
		this.detail = detailOption("options.void_lod.detail", s.detail, value -> this.edit(settings -> settings.detail = value));
		this.farDetail = detailOption("options.void_lod.farDetail", s.farDetail, value -> this.edit(settings -> settings.farDetail = value));
		int cores = Math.max(1, Math.min(LodSettings.MAX_THREADS, Runtime.getRuntime().availableProcessors()));
		// applied once the slider rests: each step starts or stops a thread
		this.threads = new OptionInstance<>("options.void_lod.threads", tooltip("options.void_lod.threads.tooltip"), Options::genericValueLabel,
			new OptionInstance.IntRange(1, cores, false), Math.min(s.threads, cores), value -> this.edit(settings -> settings.threads = value));
		this.budget = new OptionInstance<>("options.void_lod.budget", tooltip("options.void_lod.budget.tooltip"),
			(caption, value) -> Options.genericValueLabel(caption, Component.translatable("options.void_lod.megabytes", value)),
			new OptionInstance.IntRange(1, 32).<Integer>xmap(i -> i * 64, mb -> Math.clamp(Math.round(mb / 64.0F), 1, 32), true),
			s.budget, value -> this.edit(settings -> settings.budget = value));
	}

	/** A button that opens this screen from {@code parent}, and returns there when it closes. */
	public static Button button(Screen parent, int width) {
		return Button.builder(BUTTON, button -> Minecraft.getInstance().gui.setScreen(new LodSettingsScreen(parent))).width(width).build();
	}

	/** Adds a heading and the button to the end of an options list (Video Settings'). */
	public static void addTo(OptionsList list, Screen parent) {
		list.addHeader(HEADER);
		list.addBig(button(parent, 310));
	}

	private static OptionInstance<Double> detailOption(String key, double value, OptionInstance.ValueUpdateListener<Double> onChange) {
		return new OptionInstance<>(key, tooltip("options.void_lod.detail.tooltip"),
			(caption, px) -> Options.genericValueLabel(caption, Component.translatable("options.void_lod.pixels", number(px))),
			new OptionInstance.IntRange(0, DETAIL_STEPS).<Double>xmap(i -> COARSEST - i * 0.5,
				px -> Math.clamp(Math.round((COARSEST - px) * 2), 0, DETAIL_STEPS), true),
			value, onChange);
	}

	private static <T> OptionInstance.TooltipSupplier<T> tooltip(String key) {
		return OptionInstance.cachedConstantTooltip(Component.translatable(key));
	}

	private static Component kilometres(int blocks) {
		return Component.translatable("options.void_lod.kilometres", number(blocks / 1024.0));
	}

	private static String number(double value) {
		return value == Math.rint(value) ? Integer.toString((int) value) : String.format(Locale.ROOT, "%.1f", value);
	}

	@Override
	protected void addTitle() {
		this.header.defaultCellSetting().alignHorizontallyCenter();
		this.header.addChild(new StringWidget(this.title, this.font));
		this.status = this.header.addChild(new StringWidget(this.statusLine(), this.font).setMaxWidth(this.width - 20));
		this.layout.addToHeader(this.header);
	}

	@Override
	protected void addOptions() {
		this.list.addBig(this.preset);
		this.list.addSmall(this.enabled, this.autoTune, this.radius, this.haze, this.detail, this.farDetail, this.threads, this.budget);
	}

	@Override
	public void tick() {
		if (++this.ticks % 10 == 0 && this.status != null) {
			int width = this.status.getWidth();
			this.status.setMessage(this.statusLine());
			if (this.status.getWidth() != width) {
				this.repositionElements();
			}
		}
		super.tick();
	}

	@Override
	public void removed() {
		super.removed();
		this.lod.save();
	}

	/** Chooses a quality preset, as the preset slider does, and moves the other sliders to match. */
	public void choose(Preset preset) {
		if (this.showing) {
			return;
		}
		this.lod.change(settings -> settings.apply(preset));
		LodSettings s = this.lod.settings();
		this.showing = true;
		this.show(this.preset, preset);
		this.show(this.radius, s.radius);
		this.show(this.detail, s.detail);
		this.show(this.farDetail, s.farDetail);
		this.show(this.threads, s.threads);
		this.show(this.budget, s.budget);
		this.showing = false;
	}

	/** A change by hand: in effect at once, and the preset becomes the one the values now match, or Custom. */
	private void edit(Consumer<LodSettings> edit) {
		if (this.showing) {
			return;
		}
		this.lod.change(settings -> {
			edit.accept(settings);
			settings.preset = settings.matching();
		});
		this.showing = true;
		this.show(this.preset, this.lod.settings().preset);
		this.showing = false;
	}

	private <T> void show(OptionInstance<T> option, T value) {
		if (!value.equals(option.get())) {
			option.set(value);
			this.resetOption(option);
		}
	}

	/** What the LOD is drawing and what it costs, or why it is not drawing. */
	private Component statusLine() {
		String line;
		LodRenderer r = this.lod.renderer();
		if (!this.lod.isEnabled()) {
			line = Component.translatable("options.void_lod.status.off").getString();
		} else if (!this.lod.active()) {
			line = Component.translatable("options.void_lod.status.none").getString();
		} else if (r == null) {
			line = Component.translatable("options.void_lod.status.starting").getString();
		} else {
			line = Component.translatable("options.void_lod.status", r.lastTiles, String.format(Locale.ROOT, "%,d", r.lastQuads),
				r.gpuMillis < 0 ? "-" : String.format(Locale.ROOT, "%.2f", r.gpuMillis), String.format(Locale.ROOT, "%.2f", r.lastPrepareNanos / 1e6)).getString();
			AutoTune tuner = this.lod.tuner();
			if (this.lod.settings().autoTune && tuner.step() > 0) {
				line += Component.translatable("options.void_lod.status.tuned", number(this.lod.detail)).getString();
			}
		}
		return Component.literal(line).withStyle(ChatFormatting.GRAY);
	}
}
