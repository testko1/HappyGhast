package dev.nweaver.happyghastmod.datagen;

import dev.nweaver.happyghastmod.HappyGhastMod;
import dev.nweaver.happyghastmod.core.registration.ModConfiguredFeatures;
import dev.nweaver.happyghastmod.core.registration.ModFeatures;
import dev.nweaver.happyghastmod.core.registration.ModPlacedFeatures;
import dev.nweaver.happyghastmod.worldgen.biome.AddIncubatorModifier;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.*;
import net.minecraftforge.common.data.DatapackBuiltinEntriesProvider;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class ModWorldGenProvider extends DatapackBuiltinEntriesProvider {

    // собираем все реестры, которые заполняем через датаген
    public static final RegistrySetBuilder BUILDER = new RegistrySetBuilder()
            .add(Registries.CONFIGURED_FEATURE, ModWorldGenProvider::bootstrapConfiguredFeatures)
            .add(Registries.PLACED_FEATURE, ModWorldGenProvider::bootstrapPlacedFeatures)
            .add(ForgeRegistries.Keys.BIOME_MODIFIERS, ModWorldGenProvider::bootstrapBiomeModifiers);

    public ModWorldGenProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries, BUILDER, Set.of(HappyGhastMod.MODID));
    }

    // метод для заполнения configuredfeatures
    private static void bootstrapConfiguredFeatures(BootstapContext<ConfiguredFeature<?, ?>> context) {
        // регистрируем configuredfeature
        context.register(ModConfiguredFeatures.GHASTLING_INCUBATOR_CONFIGURED, // используем ключ из класса
                new ConfiguredFeature<>(
                        ModFeatures.GHASTLING_INCUBATOR_FEATURE.get(), // ссылка на зарегистрированную фичу
                        NoneFeatureConfiguration.INSTANCE // фича не требует конфигурации
                )
        );
    }

    // метод для заполнения placedfeatures
    private static void bootstrapPlacedFeatures(BootstapContext<PlacedFeature> context) {
        // получаем доступ к реестру configuredfeature
        var configuredFeatureRegistry = context.lookup(Registries.CONFIGURED_FEATURE);
        // получаем холдер для нашей configuredfeature
        var incubatorConfiguredHolder = configuredFeatureRegistry.getOrThrow(ModConfiguredFeatures.GHASTLING_INCUBATOR_CONFIGURED);

        // регистрируем нашу placedfeature с улучшенной настройкой спавна
        context.register(ModPlacedFeatures.GHASTLING_INCUBATOR_PLACED,
                new PlacedFeature(
                        incubatorConfiguredHolder,
                        List.of(
                                // используем один из двух вариантов:

                                // 100% шанс
                                CountPlacement.of(1),  // одна попытка на чанк
                                InSquarePlacement.spread(),
                                // диапазон высот для нижнего мира - используем треугольное распределение
                                // для концентрации инкубаторов на средних высотах
                                HeightRangePlacement.triangle(VerticalAnchor.absolute(10), VerticalAnchor.absolute(120)),
                                BiomeFilter.biome() // фильтр по биому
                        )
                )
        );
    }

    // метод для заполнения biomemodifiers
    private static void bootstrapBiomeModifiers(BootstapContext<BiomeModifier> context) {
        var placedFeatureRegistry = context.lookup(Registries.PLACED_FEATURE);
        var biomeRegistry = context.lookup(Registries.BIOME);
        var incubatorPlacedHolder = placedFeatureRegistry.getOrThrow(ModPlacedFeatures.GHASTLING_INCUBATOR_PLACED);

        ResourceKey<BiomeModifier> addIncubatorModifierKey = ResourceKey.create(
                ForgeRegistries.Keys.BIOME_MODIFIERS,
                HappyGhastMod.rl("add_ghastling_incubator")
        );

        // добавляем все нужные биомы нижнего мира, где могут генерироваться nether fossils
        context.register(
                addIncubatorModifierKey,
                new AddIncubatorModifier(
                        HolderSet.direct(
                                biomeRegistry.getOrThrow(Biomes.SOUL_SAND_VALLEY),
                                biomeRegistry.getOrThrow(Biomes.CRIMSON_FOREST),  // добавляем другие биомы нижнего мира
                                biomeRegistry.getOrThrow(Biomes.WARPED_FOREST),   // где могут быть nether fossils
                                biomeRegistry.getOrThrow(Biomes.BASALT_DELTAS),
                                biomeRegistry.getOrThrow(Biomes.NETHER_WASTES)
                        ),
                        incubatorPlacedHolder
                )
        );
    }

    // имя провайдера (для логов)
    @Override
    public String getName() {
        return "World Gen Provider: " + HappyGhastMod.MODID;
    }
}