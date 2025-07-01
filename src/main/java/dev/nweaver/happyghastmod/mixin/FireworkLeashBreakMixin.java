package dev.nweaver.happyghastmod.mixin;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

// миксин для разрыва поводков при использовании фейерверка во время полета
@Mixin(FireworkRocketItem.class)
public class FireworkLeashBreakMixin {

    private static final String LOG_PREFIX = "[FireworkLeash] ";

    // инъекция в метод use фейерверка для разрыва поводков при использовании
    @Inject(method = "use", at = @At("HEAD"))
    private void hg_breakLeashOnFireworkUse(Level level, Player player, InteractionHand hand,
                                            CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {

        // проверяем, что игрок летит (использует элитры)
        if (!level.isClientSide && player.isFallFlying()) {

            // получаем список всех сущностей, привязанных к игроку
            List<Entity> leashedEntities = level.getEntities(player, player.getBoundingBox().inflate(12.0D),
                    entity -> entity instanceof Mob mob && mob.getLeashHolder() == player);

            if (!leashedEntities.isEmpty()) {
                System.out.println(LOG_PREFIX + "Player(" + player.getId() +
                        ") used firework while flying with " + leashedEntities.size() + " leashed entities. Breaking leashes.");

                // разрываем все поводки
                for (Entity entity : leashedEntities) {
                    if (entity instanceof Mob mob) {
                        mob.dropLeash(true, true);

                        // воспроизводим звук разрыва поводка
                        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                                net.minecraft.sounds.SoundEvents.LEASH_KNOT_BREAK,
                                net.minecraft.sounds.SoundSource.NEUTRAL, 0.5F, 0.8F);
                    }
                }
            }
        }
    }
}