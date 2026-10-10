package dev.voidmc.mettle.character.respec;

import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.signature.SignaturePerks;
import dev.voidmc.mettle.core.CharacterStore;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * A respec draught, drunk like a potion (1.6 s). The client checks first so an empty draught never starts drinking; the
 * server decides when the drink finishes, and only then is the draught used up.
 */
public final class DraughtItem extends Item {
	public enum Kind {
		/** Opens one skill's deck for a rebuild at a Card Shrine or trainer (the skill is the stack's {@link Draughts#FORGETS}). */
		FORGETTING,
		/** Returns every placed attribute point. */
		UNBINDING
	}

	private final Kind kind;

	public DraughtItem(Kind kind, Properties properties) {
		super(properties);
		this.kind = kind;
	}

	public Kind kind() {
		return this.kind;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		Optional<Component> refusal = refusal(player, player.getItemInHand(hand));
		if (refusal.isPresent()) {
			if (player instanceof ServerPlayer sp) {
				SignaturePerks.feedback(sp, BuiltInRegistries.ITEM.getKey(this), refusal.get());
			}
			return InteractionResult.FAIL;
		}
		return super.use(level, player, hand);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (!(entity instanceof ServerPlayer player)) {
			// The client predicts the sip; the server's answer (and the inventory sync) settles it.
			return super.finishUsingItem(stack, level, entity);
		}
		Identifier chip = BuiltInRegistries.ITEM.getKey(this);
		Optional<Component> refusal = refusal(player, stack);
		if (refusal.isPresent()) {
			// Something changed while drinking: keep the draught.
			SignaturePerks.feedback(player, chip, refusal.get());
			player.containerMenu.sendAllDataToRemote();
			return stack;
		}
		Component text;
		if (this.kind == Kind.FORGETTING) {
			ResourceKey<Skill> skill = skill(stack).orElseThrow();
			RespecRules.Forgetting forgot = Perks.forget(player, skill);
			text = Component.translatable(forgot.drafts() == 1 ? "mettle.feedback.forgetting.one" : "mettle.feedback.forgetting",
				skillName(player.level(), skill), forgot.drafts());
		} else {
			int points = Perks.unbind(player);
			text = Component.translatable(points == 1 ? "mettle.feedback.unbinding.one" : "mettle.feedback.unbinding", points);
		}
		SignaturePerks.feedback(player, chip, text);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, SoundSource.PLAYERS, 0.5f, 1.3f);
		return super.finishUsingItem(stack, level, entity);
	}

	/** Why this draught can't be drunk now, or empty when it would give something back. Same answer on both sides. */
	public Optional<Component> refusal(Player player, ItemStack stack) {
		if (this.kind == Kind.UNBINDING) {
			return RespecRules.placed(CharacterStore.get(player).spent()) > 0
				? Optional.empty()
				: Optional.of(Component.translatable("mettle.feedback.unbinding.nothing"));
		}
		Optional<ResourceKey<Skill>> skill = skill(stack);
		if (skill.isEmpty() || player.level().registryAccess().lookupOrThrow(MettleRegistries.SKILL).getOptional(skill.get()).isEmpty()) {
			return Optional.of(Component.translatable("mettle.feedback.forgetting.unknown"));
		}
		// Cards v2 (docs/CARDS.md §7.6): the draught is the key to a rebuild at a Card Shrine or a trainer, not a drink. It is
		// still drunk to forget drafted perks held outside the deck (from before cards, or given by an op).
		boolean nothing = RespecRules.forget(CharacterStore.get(player).perks(), Perks.registry(player)::getOptional, skill.get()).nothing();
		return nothing
			? Optional.of(Component.translatable("mettle.feedback.forgetting.shrine", skillName(player.level(), skill.get())))
			: Optional.empty();
	}

	public static Optional<ResourceKey<Skill>> skill(ItemStack stack) {
		Identifier id = stack.get(Draughts.FORGETS);
		return id == null ? Optional.empty() : Optional.of(ResourceKey.create(MettleRegistries.SKILL, id));
	}

	@Override
	public Component getName(ItemStack stack) {
		Identifier skill = stack.get(Draughts.FORGETS);
		if (this.kind == Kind.FORGETTING && skill != null) {
			return Component.translatable("item.mettle.draught_of_forgetting.of", skillName(skill));
		}
		return super.getName(stack);
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
		Identifier skill = stack.get(Draughts.FORGETS);
		if (this.kind == Kind.UNBINDING) {
			out.accept(Component.translatable("item.mettle.draught_of_unbinding.tooltip").withStyle(ChatFormatting.GRAY));
			out.accept(Component.translatable("item.mettle.draught_of_unbinding.tooltip2").withStyle(ChatFormatting.DARK_GRAY));
		} else if (skill != null) {
			out.accept(Component.translatable("item.mettle.draught_of_forgetting.tooltip", skillName(skill)).withStyle(ChatFormatting.GRAY));
			out.accept(Component.translatable("item.mettle.draught_of_forgetting.tooltip2").withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	private static Component skillName(Level level, ResourceKey<Skill> skill) {
		return level.registryAccess().lookupOrThrow(MettleRegistries.SKILL).getOptional(skill).map(Skill::name).orElseGet(() -> skillName(skill.identifier()));
	}

	/** Without a level (item names can be asked for anywhere): the skill's lang key, as the skill data uses. */
	private static Component skillName(Identifier skill) {
		return Component.translatableWithFallback(skill.getNamespace() + ".skill." + skill.getPath(), skill.getPath());
	}
}
