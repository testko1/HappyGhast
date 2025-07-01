package dev.nweaver.happyghastmod.core.registration;

import dev.nweaver.happyghastmod.HappyGhastMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

// этот класс не регистрируется через deferredregister, он просто хранит ключи
public class ModConfiguredFeatures {

    // ключ для конфига
    public static final ResourceKey<ConfiguredFeature<?, ?>> GHASTLING_INCUBATOR_CONFIGURED =
            createKey("ghastling_incubator_configured");


    // вспомогательный метод для создания ключа
    public static ResourceKey<ConfiguredFeature<?, ?>> createKey(String name) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE, HappyGhastMod.rl(name));
    }
}