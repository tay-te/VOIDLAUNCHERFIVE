package dev.voidmc.hearth.quest.block;

import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.quest.model.SettlementQuests;
import dev.voidmc.hearth.quest.net.QuestPayloads;
import dev.voidmc.hearth.quest.service.QuestManager;
import dev.voidmc.hearth.quest.world.QuestWorlds;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * {@code hearth:notice_board}: a framed timber board on two posts under a little gable roof, pinned with the
 * settlement's requests. Right-click opens the board of the settlement it stands in: its offers to take, and a hand-in
 * for anything the player has brought. The papers pinned to it ({@link #PAPERS}) follow how many offers are up, so an
 * empty board looks empty. The build module places it in plazas by id.
 */
public class NoticeBoardBlock extends Block {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	/** Papers pinned: 0 to 4. Placed boards start with 3. */
	public static final IntegerProperty PAPERS = IntegerProperty.create("papers", 0, 4);
	private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Shapes.or(
		Block.box(1, 0, 7, 3, 22, 9),
		Block.box(13, 0, 7, 15, 22, 9),
		Block.box(0, 7, 6.5, 16, 24, 9.5)));
	private static final Map<Direction, VoxelShape> COLLISION = Shapes.rotateHorizontal(Block.box(0, 0, 6.5, 16, 24, 9.5));

	public NoticeBoardBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PAPERS, 3));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return COLLISION.get(state.getValue(FACING));
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return false;
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, PAPERS);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		// Holding the goods you came to deliver should still open the board.
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		Settlement settlement = QuestWorlds.at(server, pos);
		if (settlement == null) {
			sp.sendSystemMessage(Component.translatable("hearth.quest.board.none").withStyle(ChatFormatting.GRAY), true);
			return InteractionResult.SUCCESS_SERVER;
		}
		open(sp, server, settlement, pos);
		return InteractionResult.SUCCESS_SERVER;
	}

	/** Sends the board to the player and pins as many papers as there are offers. */
	public static void open(ServerPlayer player, ServerLevel level, Settlement settlement, BlockPos pos) {
		QuestPayloads.Board board = QuestManager.view(player, level, settlement, pos);
		updatePapers(level, pos, QuestManager.board(level, settlement, false));
		level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.8F, 0.9F + level.getRandom().nextFloat() * 0.2F);
		if (ServerPlayNetworking.canSend(player, QuestPayloads.Board.TYPE)) {
			ServerPlayNetworking.send(player, board);
		} else {
			// A client without the mod still learns what is posted.
			player.sendSystemMessage(Component.literal(settlement.name()).withStyle(ChatFormatting.GOLD));
			for (QuestPayloads.Board.Entry e : board.entries()) {
				player.sendSystemMessage(Component.literal(" • " + e.quest().text.title()));
			}
		}
	}

	public static void updatePapers(Level level, BlockPos pos, SettlementQuests data) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof NoticeBoardBlock)) {
			return;
		}
		int untaken = (int) data.offers.stream().filter(q -> q.takenBy.isEmpty()).count();
		int papers = Math.min(4, untaken);
		if (state.getValue(PAPERS) != papers) {
			level.setBlock(pos, state.setValue(PAPERS, papers), Block.UPDATE_CLIENTS);
		}
	}
}
