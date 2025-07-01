package dev.nweaver.happyghastmod.core.registration;

import com.mojang.serialization.Codec;
import dev.nweaver.happyghastmod.HappyGhastMod;
import dev.nweaver.happyghastmod.worldgen.biome.AddIncubatorModifier;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBiomeModifiers {
    // реестр для кодеков biome modifiers
    public static final DeferredRegister<Codec<? extends BiomeModifier>> BIOME_MODIFIER_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, HappyGhastMod.MODID);
}