package dev.nweaver.happyghastmod.core.registration;

import dev.nweaver.happyghastmod.HappyGhastMod;
import dev.nweaver.happyghastmod.worldgen.feature.GhastlingIncubatorFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(ForgeRegistries.FEATURES, HappyGhastMod.MODID);

    // регистрация фичи
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> GHASTLING_INCUBATOR_FEATURE =
            FEATURES.register("ghastling_incubator_feature",
                    () -> new GhastlingIncubatorFeature(NoneFeatureConfiguration.CODEC));

}