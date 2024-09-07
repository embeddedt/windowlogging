package mod.grimmauld.windowlogging;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.List;

import static mod.grimmauld.windowlogging.WindowInABlockTileEntity.WINDOWLOGGED_TE;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class WindowInABlockModel extends BakedModelWrapper<BakedModel> {
	private static final BlockRenderDispatcher DISPATCHER = Minecraft.getInstance().getBlockRenderer();

	private record WindowloggedRenderData(BakedModel partialModel, BakedModel windowModel, BlockState partialState,
										  BlockState windowState, ModelData partialModelData,
										  ModelData windowModelData) {}

	private static final ModelProperty<WindowloggedRenderData> RENDER_DATA = new ModelProperty<>();

	public WindowInABlockModel(BakedModel original) {
		super(original);
	}

	private static void fightZfighting(BakedQuad q) {
		int[] data = q.getVertices();
		Vec3i vec = q.getDirection().getNormal();
		int dirX = vec.getX();
		int dirY = vec.getY();
		int dirZ = vec.getZ();
		for (int i = 0; i < 4; ++i) {
			int j = data.length / 4 * i;
			float x = Float.intBitsToFloat(data[j]);
			float y = Float.intBitsToFloat(data[j + 1]);
			float z = Float.intBitsToFloat(data[j + 2]);
			double offset = q.getDirection().getAxis().choose(x, y, z);

			if (offset < 1 / 1024d || offset > 1023 / 1024d) {
				data[j] = Float.floatToIntBits(x - 1 / 512f * dirX);
				data[j + 1] = Float.floatToIntBits(y - 1 / 512f * dirY);
				data[j + 2] = Float.floatToIntBits(z - 1 / 512f * dirZ);
			}
		}
	}

	private static boolean hasSolidSide(BlockState state, Direction side) {
		return state.canOcclude() && state.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, side) &&
				Block.isShapeFullBlock(state.getFaceOcclusionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, side));
	}

	@Override
	@Nonnull
	public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand, @NotNull ModelData data, @Nullable RenderType renderType) {
		var renderData = data.get(RENDER_DATA);

		if (renderData == null) {
			return List.of();
		}

		List<BakedQuad> quads = new ArrayList<>();

		// Add all quads from the partial model if it should render on this render type
		if (renderType == null || renderData.partialModel().getRenderTypes(renderData.partialState(), rand, renderData.partialModelData()).contains(renderType)) {
			quads.addAll(renderData.partialModel().getQuads(renderData.partialState(), side, rand, renderData.partialModelData(), renderType));
		}

		// Add all quads from the window model if it should render on this render type
		if (renderType == null || renderData.windowModel().getRenderTypes(renderData.windowState(), rand, renderData.windowModelData()).contains(renderType)) {
			renderData.windowModel().getQuads(renderData.windowState(), side, rand, renderData.windowModelData(), renderType).forEach(bakedQuad -> {
				if (!hasSolidSide(renderData.partialState(), bakedQuad.getDirection())) {
					fightZfighting(bakedQuad);
					quads.add(bakedQuad);
				}
			});
		}

		return quads;
	}

	@Override
	public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ModelData data) {
		WindowInABlockTileEntity windowInABlockTileEntity = data.get(WINDOWLOGGED_TE);
		if (windowInABlockTileEntity == null) {
			return data;
		}

		// Precompute as much information as possible for rendering the model, and store it in the model data

		BlockState partialState = windowInABlockTileEntity.getPartialBlock();
		BlockState windowState = windowInABlockTileEntity.getWindowBlock();
		BlockPos position = windowInABlockTileEntity.getBlockPos();
		BlockEntity partialTE = windowInABlockTileEntity.getPartialBlockTileEntityIfPresent();

		BakedModel partialModel = DISPATCHER.getBlockModel(partialState);
		ModelData partialModelData = partialModel.getModelData(level, position, partialState, partialTE == null ? ModelData.EMPTY : partialTE.getModelData());

		BakedModel windowModel = DISPATCHER.getBlockModel(windowState);
		ModelData windowModelData = windowModel.getModelData(level, position, windowState, ModelData.EMPTY);

		return ModelData.builder().with(RENDER_DATA, new WindowloggedRenderData(partialModel, windowModel, partialState, windowState, partialModelData, windowModelData)).build();
	}

	@Override
	public ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand, @NotNull ModelData data) {
		var renderData = data.get(RENDER_DATA);

		if (renderData == null) {
			return ChunkRenderTypeSet.none();
		}

		return ChunkRenderTypeSet.union(
				renderData.partialModel().getRenderTypes(renderData.partialState(), rand, renderData.partialModelData()),
				renderData.windowModel().getRenderTypes(renderData.windowState(), rand, renderData.windowModelData())
		);
	}

	@Override
	public TextureAtlasSprite getParticleIcon(ModelData data) {
		var renderData = data.get(RENDER_DATA);
		if (renderData == null)
			return super.getParticleIcon(data);

		return renderData.partialModel().getParticleIcon(renderData.partialModelData());
	}


	@Override
	public boolean useAmbientOcclusion(BlockState state, RenderType renderType) {
		return renderType == RenderType.solid();
	}
}
