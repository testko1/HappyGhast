package dev.nweaver.happyghastmod.init;

import dev.nweaver.happyghastmod.HappyGhastMod;
import dev.nweaver.happyghastmod.entity.GhastPlatformEntity;
import dev.nweaver.happyghastmod.entity.Ghastling;
import dev.nweaver.happyghastmod.entity.HappyGhast;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import static net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES;


public class EntityInit {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ENTITY_TYPES, HappyGhastMod.MODID);
    public static final RegistryObject<EntityType<HappyGhast>> HAPPY_GHAST = ENTITIES.register("happy_ghast",
            () -> EntityType.Builder.of(HappyGhast::new, MobCategory.MISC)
                    .sized(4.0f, 4.0f)
                    .clientTrackingRange(10)   // дистанция отрисовки
                    .updateInterval(3)         // интервал обновления
                    .build(HappyGhastMod.rl("happy_ghast").toString())
    );
    public static final RegistryObject<EntityType<GhastPlatformEntity>> GHAST_PLATFORM =
            ENTITIES.register("ghast_platform",
                    () -> EntityType.Builder.<GhastPlatformEntity>of(GhastPlatformEntity::new, MobCategory.MISC)
                            // устанавливаем ширину платформы равной ширине гаста
                            .sized(4.5F, 0.5F) // ширина 4.5, высота 0.5
                            .noSummon()
                            .fireImmune()
                            .clientTrackingRange(10)
                            .updateInterval(3)
                            .build(HappyGhastMod.rl("ghast_platform").toString()));

    public static final RegistryObject<EntityType<Ghastling>> GHASTLING =
            ENTITIES.register("ghastling",
                    () -> EntityType.Builder.of(Ghastling::new, MobCategory.MISC)
                            // ставим меньший размер
                            .sized(1.6F, 1.6F) // 4.0 / 2.5 = 1.6
                            .clientTrackingRange(8) // стандартная дальность для большинства мобов
                            .fireImmune() // как и гасты
                            .build(HappyGhastMod.rl("ghastling").toString()));

}