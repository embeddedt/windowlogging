package mod.grimmauld.windowlogging;

import net.minecraft.tileentity.TileEntityType;
import net.minecraftforge.registries.ObjectHolder;

import static mod.grimmauld.windowlogging.BuildConfig.MODID;

public class RegistryEntries {
	@ObjectHolder(MODID + ":window_in_a_block")
	public static WindowInABlockBlock WINDOW_IN_A_BLOCK;

	@ObjectHolder(MODID + ":window_in_a_block")
	public static TileEntityType<WindowInABlockTileEntity> WINDOW_IN_A_BLOCK_TILE_ENTITY;

	private RegistryEntries() {
	}
}
