package dev.nweaver.happyghastmod.worldgen.biome;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.common.world.ModifiableBiomeInfo;

// используем record для простоты создания кодека
public record AddIncubatorModifier(HolderSet<Biome> biomes, Holder<PlacedFeature> feature) implements BiomeModifier {

    public static final Codec<AddIncubatorModifier> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Biome.LIST_CODEC.fieldOf("biomes").forGetter(AddIncubatorModifier::biomes),
            PlacedFeature.CODEC.fieldOf("feature").forGetter(AddIncubatorModifier::feature)
    ).apply(inst, AddIncubatorModifier::new));


    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase == Phase.ADD && this.biomes.contains(biome)) {
            builder.getGenerationSettings().addFeature(
                    GenerationStep.Decoration.UNDERGROUND_DECORATION, // оставляем пока так
                    this.feature);
        }
    }

    // метод codec() возвращает статическое поле
    @Override
    public Codec<? extends BiomeModifier> codec() {
        return CODEC;
    }

}