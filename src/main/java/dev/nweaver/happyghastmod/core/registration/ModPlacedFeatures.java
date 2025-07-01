package dev.nweaver.happyghastmod.core.registration;

import dev.nweaver.happyghastmod.HappyGhastMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

// этот класс не регистрируется через deferredregister, он просто хранит ключи
public class ModPlacedFeatures {

    // ключ для размещения
    public static final ResourceKey<PlacedFeature> GHASTLING_INCUBATOR_PLACED =
            createKey("ghastling_incubator_placed");


    // вспомогательный метод для создания ключа
    public static ResourceKey<PlacedFeature> createKey(String name) {
        return ResourceKey.create(Registries.PLACED_FEATURE, HappyGhastMod.rl(name));
    }
}