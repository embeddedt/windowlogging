package mod.grimmauld.windowlogging;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.data.IModelData;
import net.minecraftforge.client.model.data.ModelDataMap;
import net.minecraftforge.client.model.data.ModelProperty;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class WindowInABlockTileEntity extends BlockEntity {

	public static final ModelProperty<BlockState> PARTIAL_BLOCK = new ModelProperty<>();
	public static final ModelProperty<BlockState> WINDOW_BLOCK = new ModelProperty<>();
	public static final ModelProperty<BlockPos> POSITION = new ModelProperty<>();
	public static final ModelProperty<BlockEntity> PARTIAL_TE = new ModelProperty<>();
	private BlockState partialBlock = Blocks.AIR.defaultBlockState();
	private BlockState windowBlock = Blocks.AIR.defaultBlockState();
	private CompoundTag partialBlockTileData;
	private BlockEntity partialBlockTileEntity = null;
	@OnlyIn(Dist.CLIENT)
	private IModelData modelData;

	public WindowInABlockTileEntity(BlockPos pos, BlockState blockState) {
		super(RegistryEntries.WINDOW_IN_A_BLOCK_TILE_ENTITY, pos, blockState);
		setPartialBlockTileData(new CompoundTag());
		DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> this::initDataMap);
	}

	public CompoundTag getPartialBlockTileData() {
		return partialBlockTileData;
	}

	public void setPartialBlockTileData(CompoundTag partialBlockTileData) {
		this.partialBlockTileData = partialBlockTileData;
	}

	@OnlyIn(value = Dist.CLIENT)
	private void initDataMap() {
		modelData = new ModelDataMap.Builder().withInitial(WINDOW_BLOCK, Blocks.AIR.defaultBlockState())
			.withInitial(PARTIAL_BLOCK, Blocks.AIR.defaultBlockState()).withInitial(POSITION, BlockPos.ZERO).withInitial(PARTIAL_TE, null).build();
	}


	@Override
	public void load(CompoundTag compound) {
		partialBlock = NbtUtils.readBlockState(compound.getCompound("PartialBlock"));
		windowBlock = NbtUtils.readBlockState(compound.getCompound("WindowBlock"));
		setPartialBlockTileData(compound.getCompound("PartialData"));
		super.load(compound);
	}

	@Override
	public CompoundTag save(CompoundTag compound) {
		compound.put("PartialBlock", NbtUtils.writeBlockState(getPartialBlock()));
		compound.put("WindowBlock", NbtUtils.writeBlockState(getWindowBlock()));
		compound.put("PartialData", partialBlockTileData);
		return super.save(compound);
	}

	public void updateWindowConnections() {
		if (level == null)
			return;
		for (Direction side : Direction.values()) {
			BlockPos offsetPos = worldPosition.relative(side);
			windowBlock = getWindowBlock().updateShape(side, level.getBlockState(offsetPos), level, worldPosition,
				offsetPos);
		}
		level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 2 | 16);
		setChanged();
	}

	@OnlyIn(value = Dist.CLIENT)
	@Override
	@Nonnull
	public IModelData getModelData() {
		modelData.setData(PARTIAL_BLOCK, partialBlock);
		modelData.setData(WINDOW_BLOCK, windowBlock);
		modelData.setData(POSITION, worldPosition);
		modelData.setData(PARTIAL_TE, getPartialBlockTileEntityIfPresent());
		return modelData;
	}

	public BlockState getPartialBlock() {
		return partialBlock;
	}

	public void setPartialBlock(BlockState partialBlock) {
		this.partialBlock = partialBlock;
	}

	public BlockState getWindowBlock() {
		return windowBlock;
	}

	public void setWindowBlock(BlockState windowBlock) {
		this.windowBlock = windowBlock;
	}

	@Override
	public CompoundTag getUpdateTag() {
		return save(new CompoundTag());
	}

	@Override
	public void handleUpdateTag(CompoundTag tag) {
		load(tag);
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return new ClientboundBlockEntityDataPacket(getBlockPos(), 1, save(new CompoundTag()));
	}

	@Override
	public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
		load(pkt.getTag());
	}

	@Nullable
	public BlockEntity getPartialBlockTileEntityIfPresent() {
		if (!(getPartialBlock() instanceof EntityBlock entityBlock) || level == null)
			return null;
		if (partialBlockTileEntity == null) {
			try {
				partialBlockTileEntity = entityBlock.newBlockEntity(worldPosition, partialBlock);
				if (partialBlockTileEntity != null) {
					partialBlockTileEntity.blockState = getPartialBlock();
					partialBlockTileEntity.deserializeNBT(partialBlockTileData);
					partialBlockTileEntity.setLevel(level);
				}
			} catch (Exception e) {
				partialBlockTileEntity = null;
			}
		}
		return partialBlockTileEntity;
	}

	@Override
	public void requestModelDataUpdate() {
		try {
			super.requestModelDataUpdate();
		} catch (IllegalArgumentException e) {
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> this::requestModelUpdateOnClient);
		}
	}

	@OnlyIn(Dist.CLIENT)
	private void requestModelUpdateOnClient() {
		Level world = this.level;
		try {
			this.level = Minecraft.getInstance().level;
			super.requestModelDataUpdate();
		} finally {
			this.level = world;
		}
	}
}
