package mod.grimmauld.windowlogging;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.ParticleFactoryRegisterEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@SuppressWarnings("unused")
public class EventListener {
	public static void clientInit(FMLClientSetupEvent event) {
		registerRenderers();
	}

	@OnlyIn(Dist.CLIENT)
	public static void registerRenderers() {
		ItemBlockRenderTypes.setRenderLayer(RegistryEntries.WINDOW_IN_A_BLOCK, renderType -> true);
		BlockEntityRenderers.register(RegistryEntries.WINDOW_IN_A_BLOCK_TILE_ENTITY, WindowInABlockTileEntityRenderer::new);
	}

	@OnlyIn(Dist.CLIENT)
	public static void onModelBake(ModelBakeEvent event) {
		Map<ResourceLocation, BakedModel> modelRegistry = event.getModelRegistry();
		swapModels(modelRegistry, getAllBlockStateModelLocations(RegistryEntries.WINDOW_IN_A_BLOCK), RegistryEntries.WINDOW_IN_A_BLOCK::createModel);
	}

	@OnlyIn(Dist.CLIENT)
	protected static List<ModelResourceLocation> getAllBlockStateModelLocations(Block block) {
		List<ModelResourceLocation> models = new ArrayList<>();
		block.getStateDefinition().getPossibleStates().forEach(state -> {
			ModelResourceLocation rl = getBlockModelLocation(block, BlockModelShaper.statePropertiesToString(state.getValues()));
			if (rl != null)
				models.add(rl);
		});
		return models;
	}

	@OnlyIn(Dist.CLIENT)
	@Nullable
	protected static ModelResourceLocation getBlockModelLocation(Block block, String suffix) {
		ResourceLocation rl = block.getRegistryName();
		if (rl == null)
			return null;
		return new ModelResourceLocation(rl, suffix);
	}

	@OnlyIn(Dist.CLIENT)
	protected static <T extends BakedModel> void swapModels(Map<ResourceLocation, BakedModel> modelRegistry,
															ModelResourceLocation location, Function<BakedModel, T> factory) {
		modelRegistry.put(location, factory.apply(modelRegistry.get(location)));
	}

	@OnlyIn(Dist.CLIENT)
	protected static <T extends BakedModel> void swapModels(Map<ResourceLocation, BakedModel> modelRegistry,
															List<ModelResourceLocation> locations, Function<BakedModel, T> factory) {
		locations.forEach(location -> swapModels(modelRegistry, location, factory));
	}

	@SubscribeEvent
	public void rightClickPartialBlockWithPaneMakesItWindowLogged(PlayerInteractEvent.RightClickBlock event) {
		if (event.getUseItem() == Event.Result.DENY)
			return;
		if (event.getEntityLiving().isShiftKeyDown())
			return;
		if (!event.getPlayer().mayBuild())
			return;

		ItemStack stack = event.getItemStack();
		if (stack.isEmpty())
			return;
		if (!(stack.getItem() instanceof BlockItem item))
			return;
		Block block = item.getBlock();
		if (!block.getTags().contains(Windowlogging.WindowBlockTagLocation) || !(block instanceof CrossCollisionBlock)) {
			return;
		}

		BlockPos pos = event.getPos();
		Level world = event.getWorld();
		BlockState blockState = world.getBlockState(pos);
		if (!blockState.getBlock().getTags().contains(Windowlogging.WindowableBlockTagLocation))
			return;
		if (blockState.getBlock() instanceof WindowInABlockBlock)
			return;
		if (blockState.getOptionalValue(BlockStateProperties.SLAB_TYPE).orElse(null) == SlabType.DOUBLE)
			return;

		BlockState defaultState = RegistryEntries.WINDOW_IN_A_BLOCK.defaultBlockState();
		CompoundTag partialBlockTileData = new CompoundTag();
		BlockEntity currentTE = world.getBlockEntity(pos);
		if (currentTE != null)
			partialBlockTileData = currentTE.serializeNBT();
		world.setBlockAndUpdate(pos, defaultState);
		BlockEntity te = world.getBlockEntity(pos);
		if (te instanceof WindowInABlockTileEntity wte) {
			wte.setWindowBlock(item.getBlock().defaultBlockState());
			wte.updateWindowConnections();
			SoundType soundtype = wte.getWindowBlock().getSoundType(world, pos, event.getPlayer());
			world.playSound(null, pos, soundtype.getPlaceSound(), SoundSource.BLOCKS, (soundtype.getVolume() + 1.0F) / 2.0F, soundtype.getPitch() * 0.8F);

			if (blockState.getBlock() instanceof CrossCollisionBlock) {
				for (BooleanProperty side : Arrays.asList(CrossCollisionBlock.EAST, CrossCollisionBlock.NORTH, CrossCollisionBlock.SOUTH,
					CrossCollisionBlock.WEST))
					blockState = blockState.setValue(side, false);
			}
			if (blockState.getBlock() instanceof WallBlock)
				blockState = blockState.setValue(WallBlock.UP, true);

			wte.setPartialBlock(blockState);
			wte.setPartialBlockTileData(partialBlockTileData);
			wte.requestModelDataUpdate();

			if (!event.getPlayer().isCreative())
				stack.shrink(1);
			event.getPlayer().swing(event.getHand());
		}

		event.setCanceled(true);
	}

	@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
	public static class RegistryEvents {
		private RegistryEvents() {
		}

		@SubscribeEvent
		public static void registerBlocks(final RegistryEvent.Register<Block> event) {
			Windowlogging.LOGGER.debug("blocks registering");
			event.getRegistry().register(new WindowInABlockBlock().setRegistryName("window_in_a_block"));
		}

		@SubscribeEvent
		public static void registerTEs(final RegistryEvent.Register<BlockEntityType<?>> event) {
			Windowlogging.LOGGER.debug("TEs registering");
			event.getRegistry().register(BlockEntityType.Builder.of(WindowInABlockTileEntity::new, RegistryEntries.WINDOW_IN_A_BLOCK)
				.build(null).setRegistryName("window_in_a_block"));
		}

		@OnlyIn(Dist.CLIENT)
		@SubscribeEvent
		public static void registerColorProviders(ParticleFactoryRegisterEvent event) {
			WindowBlockColor.registerFor(RegistryEntries.WINDOW_IN_A_BLOCK);
		}
	}
}
