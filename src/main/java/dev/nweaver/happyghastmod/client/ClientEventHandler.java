package dev.nweaver.happyghastmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@OnlyIn(Dist.CLIENT)
public class ClientEventHandler {
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        tickCounter++;
        if (tickCounter % 20 == 0) { // каждую секунду
            Minecraft minecraft = Minecraft.getInstance();

            if (minecraft.level != null && minecraft.player != null) {
                // обновляем состояние мобов на поводке
                for (Entity entity : minecraft.level.getEntities(minecraft.player,
                        minecraft.player.getBoundingBox().inflate(24.0))) {

                    if (entity instanceof Mob mob && mob.isLeashed()) {
                        Entity leashHolder = mob.getLeashHolder();

                        // принудительное обновление для игрока
                        if (leashHolder instanceof Player) {
                            mob.handleEntityEvent((byte)21);
                        }
                    }
                }
            }
        }
    }
}