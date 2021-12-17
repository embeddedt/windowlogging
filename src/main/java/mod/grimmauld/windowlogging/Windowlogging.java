package mod.grimmauld.windowlogging;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fml.common.Mod;

import static mod.grimmauld.windowlogging.BuildConfig.MODID;

@Mod(MODID)
public class Windowlogging {
	public static final Tag.Named<Block> WINDOWABLE = BlockTags.createOptional(new ResourceLocation(MODID, "windowable"));
	public static final Tag.Named<Block> WINDOW = BlockTags.createOptional(new ResourceLocation(MODID, "window"));

	public Windowlogging() {
	}
}
