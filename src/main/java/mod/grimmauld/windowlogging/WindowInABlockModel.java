package mod.grimmauld.windowlogging;

import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeLookup;
import net.minecraft.client.renderer.model.BakedQuad;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.tags.BlockTags;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3i;
import net.minecraft.world.IBlockReader;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.EmptyModelData;
import net.minecraftforge.client.model.data.IModelData;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static mod.grimmauld.windowlogging.WindowInABlockTileEntity.*;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class WindowInABlockModel extends BakedModelWrapper<IBakedModel> {

	public WindowInABlockModel(IBakedModel original) {
		super(original);
	}

	private static void fightZfighting(BakedQuad q) {
		int[] data = q.getVertices();
		Vector3i vec = q.getDirection().getNormal();
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

	private static boolean hasSolidSide(BlockState state, IBlockReader worldIn, BlockPos pos, Direction side) {
		return !state.getBlock().is(BlockTags.LEAVES) && Block.isFaceFull(state.getBlockSupportShape(worldIn, pos), side);
	}

	@Override
	public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, Random rand, IModelData data) {
		BlockRendererDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
		BlockState partialState = data.getData(PARTIAL_BLOCK);
		BlockState windowState = data.getData(WINDOW_BLOCK);
		BlockPos position = data.getData(POSITION);
		TileEntity partialTE = data.getData(PARTIAL_TE);
		ClientWorld world = Minecraft.getInstance().level;
		List<BakedQuad> quads = new ArrayList<>();
		if (world == null || position == null)
			return quads;

		if (partialState == null || windowState == null)
			return dispatcher.getBlockModel(Blocks.DIRT.defaultBlockState()).getQuads(state, side, rand, data);
		RenderType renderType = MinecraftForgeClient.getRenderLayer();
		if (RenderTypeLookup.canRenderInLayer(partialState, renderType) && partialState.getRenderShape() == BlockRenderType.MODEL) {
			IBakedModel partialModel = dispatcher.getBlockModel(partialState);
			IModelData modelData = partialModel.getModelData(world, position, partialState,
				partialTE == null ? EmptyModelData.INSTANCE : partialTE.getModelData());
			quads.addAll(partialModel.getQuads(partialState, side, rand, modelData));
		}
		if (RenderTypeLookup.canRenderInLayer(windowState, renderType)) {
			IBakedModel windowModel = dispatcher.getBlockModel(windowState);
			IModelData glassModelData = windowModel.getModelData(world, position, windowState, EmptyModelData.INSTANCE);
			dispatcher.getBlockModel(windowState).getQuads(windowState, side, rand, glassModelData)
				.forEach(bakedQuad -> {
					if (!hasSolidSide(partialState, world, position, bakedQuad.getDirection())) {
						fightZfighting(bakedQuad);
						quads.add(bakedQuad);
					}
				});
		}
		return quads;
	}

	@Override
	public TextureAtlasSprite getParticleTexture(IModelData data) {
		BlockRendererDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
		BlockState partialState = data.getData(PARTIAL_BLOCK);
		TileEntity partialTE = data.getData(PARTIAL_TE);
		if (partialState == null)
			return super.getParticleTexture(data);
		return dispatcher.getBlockModel(partialState).getParticleTexture(partialTE == null ? data : partialTE.getModelData());
	}

	@Override
	public boolean useAmbientOcclusion() {
		RenderType renderLayer = MinecraftForgeClient.getRenderLayer();
		return renderLayer == RenderType.solid();
	}
}
