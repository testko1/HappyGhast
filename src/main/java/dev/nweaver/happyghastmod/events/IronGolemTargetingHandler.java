package dev.nweaver.happyghastmod.events;

import dev.nweaver.happyghastmod.entity.Ghastling;
import dev.nweaver.happyghastmod.entity.HappyGhast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod.EventBusSubscriber(modid = "happyghastmod")
public class IronGolemTargetingHandler {
    private static final Logger LOGGER = LogManager.getLogger("happyghastmod");

    @SubscribeEvent
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity attacker = event.getEntity();
        LivingEntity target = event.getNewTarget();

        // проверяем, является ли цель нашими дружелюбными гастами
        if (target instanceof HappyGhast || target instanceof Ghastling) {
            // проверяем тип атакующего
            boolean shouldCancel = false;

            // железный голем
            if (attacker instanceof IronGolem) {
                shouldCancel = true;
            }

            // снежный голем
            else if (attacker instanceof SnowGolem) {
                shouldCancel = true;
            }

            // стражник из guard villagers
            else if (attacker instanceof Mob &&
                    attacker.getType().getDescriptionId().contains("guardvillagers")) {
                shouldCancel = true;
            }

            // если нужно отменить атаку
            if (shouldCancel) {
                event.setCanceled(true);

                // дополнительно сбрасываем цель
                if (attacker instanceof Mob mobAttacker) {
                    mobAttacker.setTarget(null);
                }

                // логируем для отладки
                //LOGGER.debug("Prevented attack on Happy Ghast by: " +
                //        attacker.getType().getDescriptionId());
            }
        }
    }
}