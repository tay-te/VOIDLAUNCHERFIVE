package dev.voidmc.mettle.gear;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.TransmuteRecipe;

/**
 * A shaped recipe that upgrades a piece of gear (Satchel to Leather Pack, Belt to Tool Belt…) and keeps what the old one
 * had: its pockets' contents, its dye, its name. Type {@code mettle:gear_upgrade}, same JSON as {@code crafting_shaped}.
 */
public final class GearUpgradeRecipe extends ShapedRecipe {
	public static final MapCodec<GearUpgradeRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		Recipe.CommonInfo.MAP_CODEC.forGetter(o -> o.commonInfo),
		CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(o -> o.bookInfo),
		ShapedRecipePattern.MAP_CODEC.forGetter(o -> o.shape),
		ItemStackTemplate.CODEC.fieldOf("result").forGetter(o -> o.output)
	).apply(i, GearUpgradeRecipe::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, GearUpgradeRecipe> STREAM_CODEC = StreamCodec.composite(
		Recipe.CommonInfo.STREAM_CODEC, o -> o.commonInfo,
		CraftingRecipe.CraftingBookInfo.STREAM_CODEC, o -> o.bookInfo,
		ShapedRecipePattern.STREAM_CODEC, o -> o.shape,
		ItemStackTemplate.STREAM_CODEC, o -> o.output,
		GearUpgradeRecipe::new);
	public static final RecipeSerializer<ShapedRecipe> SERIALIZER = new RecipeSerializer<>(
		MAP_CODEC.xmap(r -> (ShapedRecipe) r, r -> (GearUpgradeRecipe) r),
		STREAM_CODEC.map(r -> (ShapedRecipe) r, r -> (GearUpgradeRecipe) r));

	private final ShapedRecipePattern shape;
	private final ItemStackTemplate output;

	public GearUpgradeRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo, ShapedRecipePattern pattern, ItemStackTemplate result) {
		super(commonInfo, bookInfo, pattern, result);
		this.shape = pattern;
		this.output = result;
	}

	@Override
	public ItemStack assemble(CraftingInput input) {
		for (int i = 0; i < input.size(); i++) {
			ItemStack stack = input.getItem(i);
			if (stack.has(GearComponents.GEAR)) {
				return TransmuteRecipe.createWithOriginalComponents(output, stack);
			}
		}
		return super.assemble(input);
	}

	@Override
	public RecipeSerializer<ShapedRecipe> getSerializer() {
		return SERIALIZER;
	}
}
