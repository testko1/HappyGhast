// в blockinit.java

package dev.nweaver.happyghastmod.init;

import dev.nweaver.happyghastmod.HappyGhastMod;
import dev.nweaver.happyghastmod.block.GhastlingIncubatorBlock;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class BlockInit {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, HappyGhastMod.MODID);

    // добавляем блок засохш гаста
    public static final RegistryObject<Block> GHASTLING_INCUBATOR = BLOCKS.register("ghastling_incubator",
            GhastlingIncubatorBlock::new); // регистрируем наш блок

}