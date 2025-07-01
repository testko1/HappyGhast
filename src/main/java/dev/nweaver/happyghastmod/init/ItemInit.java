package dev.nweaver.happyghastmod.init;

import dev.nweaver.happyghastmod.HappyGhastMod;
import dev.nweaver.happyghastmod.item.HarnessItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.RecordItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.HashMap;
import java.util.Map;

public class ItemInit {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HappyGhastMod.MODID);

    public static final RegistryObject<Item> MUSIC_DISC_TEARS = ITEMS.register("music_disc_tears",
            () -> new RecordItem(
                    10, // значение для компаратора
                    SoundInit.MUSIC_DISC_TEARS, // ссылка на звуковое событие
                    new Item.Properties().stacksTo(1).rarity(Rarity.RARE), // свойства предмета
                    2980 // длительность в тиках
            ));

    public static final RegistryObject<Item> GHASTLING_INCUBATOR_ITEM = ITEMS.register("ghastling_incubator",
            () -> new BlockItem(BlockInit.GHASTLING_INCUBATOR.get(), new Item.Properties()));
    public static final Map<String, RegistryObject<HarnessItem>> HARNESS_ITEMS = new HashMap<>();
    static {
        // регистрируем все цвета сбруи, включая синий
        String[] colors = {
                "blue", "black", "brown", "cyan", "gray", "green", "light_blue", "light_gray",
                "lime", "magenta", "orange", "pink", "purple", "red", "white", "yellow", "pwgood",
                "pwgoods", "moddy"
        };

        for (String color : colors) {
            HARNESS_ITEMS.put(color, ITEMS.register(color + "_harness",
                    () -> new HarnessItem(new Item.Properties().stacksTo(1), color)));
        }
    }
}