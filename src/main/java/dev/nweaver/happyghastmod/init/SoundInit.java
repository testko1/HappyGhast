package dev.nweaver.happyghastmod.init;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class SoundInit {
    // DeferredRegister для регистрации звуков
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "happyghastmod");

    // регистрируем звуковые события для гастлинга
    public static final RegistryObject<SoundEvent> GHASTLING_AMBIENT = register("entity.ghastling.ambient");
    public static final RegistryObject<SoundEvent> GHASTLING_DEATH = register("entity.ghastling.death");
    public static final RegistryObject<SoundEvent> GHASTLING_HURT = register("entity.ghastling.hurt");
    public static final RegistryObject<SoundEvent> GHASTLING_SPAWN = register("entity.ghastling.spawn");
    public static final RegistryObject<SoundEvent> HAPPY_GHAST_AMBIENT = register("entity.happy_ghast.ambient");
    public static final RegistryObject<SoundEvent> HAPPY_GHAST_DEATH = register("entity.happy_ghast.death");
    public static final RegistryObject<SoundEvent> HAPPY_GHAST_HURT = register("entity.happy_ghast.hurt");
    public static final RegistryObject<SoundEvent> HAPPY_GHAST_RIDE = register("entity.happy_ghast.ride");
    public static final RegistryObject<SoundEvent> HAPPY_GHAST_GOGGLES_DOWN = register("entity.happy_ghast.goggles_down");
    public static final RegistryObject<SoundEvent> HAPPY_GHAST_GOGGLES_UP = register("entity.happy_ghast.goggles_up");
    public static final RegistryObject<SoundEvent> HAPPY_GHAST_HARNESS_EQUIP = register("entity.happy_ghast.harness_equip");
    public static final RegistryObject<SoundEvent> HAPPY_GHAST_HARNESS_UNEQUIP = register("entity.happy_ghast.harness_unequip");

    // звуки блока засохшего гаста
    public static final RegistryObject<SoundEvent> DRIED_GHAST_AMBIENT = register("block.dried_ghast.ambient");
    public static final RegistryObject<SoundEvent> DRIED_GHAST_AMBIENT_WATER = register("block.dried_ghast.ambient_water");
    public static final RegistryObject<SoundEvent> DRIED_GHAST_PLACE_IN_WATER = register("block.dried_ghast.place_in_water");
    public static final RegistryObject<SoundEvent> DRIED_GHAST_BREAK = register("block.dried_ghast.break");
    public static final RegistryObject<SoundEvent> DRIED_GHAST_HIT = register("block.dried_ghast.hit");
    public static final RegistryObject<SoundEvent> DRIED_GHAST_STATE_CHANGE = register("block.dried_ghast.state_change");

    public static final RegistryObject<SoundEvent> MUSIC_DISC_TEARS = register("music_disc.tears");

    // вспомог метод для регистрации звуков
    private static RegistryObject<SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name.replace('.', '_'),
                () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("happyghastmod", name)));
    }

    // метод вызван из основного класса мода при инициализации
    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
    }
}