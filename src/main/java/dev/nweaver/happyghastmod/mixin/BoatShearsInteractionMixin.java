package dev.nweaver.happyghastmod.mixin;

import dev.nweaver.happyghastmod.entity.HappyGhast;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

// миксин для разрезания поводка напрямую на лодке
@Mixin(Boat.class)
public class BoatShearsInteractionMixin {

    private static final String LOG_PREFIX = "[BoatLeashShears] ";

    // инъекция в метод interact для обработки разрезания поводка ножницами у лодки
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void hg_shearsBoatInteract(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        Boat boat = (Boat) (Object) this;
        ItemStack itemstack = player.getItemInHand(hand);

        // если игрок использует ножницы на лодке
        if (itemstack.is(Items.SHEARS) && !boat.level().isClientSide) {
            boolean didCutAnyLeash = false;

            // найти всех гастов в радиусе 100 блоков
            ServerLevel level = (ServerLevel)boat.level();
            List<HappyGhast> nearbyGhasts = level.getEntitiesOfClass(
                    HappyGhast.class,
                    boat.getBoundingBox().inflate(100.0D),
                    ghast -> ghast.isQuadLeashing()
            );

            // проверить, является ли лодка целью квадро-привязи для какого-либо гаста
            for (HappyGhast ghast : nearbyGhasts) {
                if (ghast.getQuadLeashedEntityUUIDs().contains(boat.getUUID())) {
                    System.out.println(LOG_PREFIX + "Player(" + player.getId() + ") used shears on Boat(" + boat.getId() +
                            ") that is quad-leashed to Ghast(" + ghast.getId() + ")");

                    // удаляем лодку из привязей гаста
                    ghast.removeQuadLeashedEntity(boat.getUUID());

                    // выдаем поводок игроку
                    player.getInventory().add(new ItemStack(Items.LEAD));

                    // воспроизводим звук разрезания
                    boat.level().playSound(null, boat.getX(), boat.getY(), boat.getZ(),
                            net.minecraft.sounds.SoundEvents.SHEEP_SHEAR,
                            net.minecraft.sounds.SoundSource.NEUTRAL, 0.5F, 1.0F);

                    didCutAnyLeash = true;
                }
            }

            // если разрезали поводки, наносим урон ножницам и возвращаем результат
            if (didCutAnyLeash) {
                // немного повреждаем ножницы
                if (!player.getAbilities().instabuild) {
                    itemstack.hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(hand));
                }

                cir.setReturnValue(InteractionResult.SUCCESS);
            }
        }
    }
}