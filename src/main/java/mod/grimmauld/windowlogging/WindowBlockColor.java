package mod.grimmauld.windowlogging;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

@OnlyIn(Dist.CLIENT)
public class WindowBlockColor implements BlockColor {
	public static void registerFor(WindowInABlockBlock block) {
		BlockColors colors = Minecraft.getInstance().getBlockColors();
		colors.register(new WindowBlockColor(), block);
	}

	@Override
	public int getColor(BlockState state, @Nullable BlockAndTintGetter world, @Nullable BlockPos pos, int color) {
		Block block = state.getBlock();
		if (!(block instanceof WindowInABlockBlock) || world == null || pos == null)
			return -1;
		return Minecraft.getInstance().getBlockColors().getColor(((WindowInABlockBlock) block).getSurroundingBlockState(world, pos), world, pos, color);
	}
}
