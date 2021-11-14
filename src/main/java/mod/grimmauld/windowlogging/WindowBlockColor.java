package mod.grimmauld.windowlogging;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.color.BlockColors;
import net.minecraft.client.renderer.color.IBlockColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockDisplayReader;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

@OnlyIn(Dist.CLIENT)
public class WindowBlockColor implements IBlockColor {
	public static void registerFor(WindowInABlockBlock block) {
		BlockColors colors = Minecraft.getInstance().getBlockColors();
		colors.register(new WindowBlockColor(), block);
	}

	@Override
	public int getColor(BlockState state, @Nullable IBlockDisplayReader world, @Nullable BlockPos pos, int p_getColor_4_) {
		Block block = state.getBlock();
		if (!(block instanceof WindowInABlockBlock) || world == null || pos == null)
			return -1;
		return Minecraft.getInstance().getBlockColors().getColor(((WindowInABlockBlock) block).getSurroundingBlockState(world, pos), world, pos, p_getColor_4_);
	}
}
