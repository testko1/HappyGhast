package dev.nweaver.happyghastmod.init; // убедись что пакет правильный

import dev.nweaver.happyghastmod.HappyGhastMod;
import dev.nweaver.happyghastmod.block.entity.GhastlingIncubatorBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class BlockEntityInit {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, HappyGhastMod.MODID);

    // регистрируем тип нашего blockentity
    public static final RegistryObject<BlockEntityType<GhastlingIncubatorBlockEntity>> GHASTLING_INCUBATOR =
            BLOCK_ENTITIES.register("ghastling_incubator", () ->
                    BlockEntityType.Builder.of(GhastlingIncubatorBlockEntity::new, // ссылка на конструктор
                                    BlockInit.GHASTLING_INCUBATOR.get()) // блок к которому он привязан
                            .build(null)); // datafixertype (обычно null)

}