package dev.nweaver.happyghastmod.mixin;

import dev.nweaver.happyghastmod.api.IQuadLeashTarget;
import dev.nweaver.happyghastmod.entity.HappyGhast;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// миксин для добавления возможности разрезать поводки ножницами
@Mixin(Mob.class)
public class MobShearsInteractionMixin {

    private static final String LOG_PREFIX = "[LeashShears] ";

    // инъекция в метод interact для обработки разрезания поводка ножницами
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void hg_shearsInteract(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        Mob mob = (Mob) (Object) this;
        ItemStack itemstack = player.getItemInHand(hand);

        // если игрок использует ножницы на привязанной сущности
        if (itemstack.is(Items.SHEARS) && mob.isLeashed()) {
            System.out.println(LOG_PREFIX + "Player(" + player.getId() + ") used shears on leashed Mob(" + mob.getId() + ")");

            if (!mob.level().isClientSide) {
                // разрезаем поводок и выбрасываем предмет поводка
                mob.dropLeash(true, true);

                // звук разрезания
                mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                        net.minecraft.sounds.SoundEvents.SHEEP_SHEAR,
                        net.minecraft.sounds.SoundSource.NEUTRAL, 0.5F, 1.0F);

                // немного повреждаем ножницы
                if (!player.getAbilities().instabuild) {
                    itemstack.hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(hand));
                }
            }

            cir.setReturnValue(InteractionResult.sidedSuccess(mob.level().isClientSide()));
        }
    }

    // дополнительная инъекция для разрезания всех поводков, привязанных к сущности
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void hg_shearsBreakAttachedLeashes(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        Mob thisEntity = (Mob) (Object) this;
        ItemStack itemstack = player.getItemInHand(hand);

        // если игрок использует ножницы на сущности, к которой что-либо привязано
        if (itemstack.is(Items.SHEARS) && !thisEntity.level().isClientSide) {
            boolean didCutAnyLeash = false;

            // проверяем, не является ли эта сущность целью квадро-привязки от гаста
            if (thisEntity instanceof IQuadLeashTarget quadLeashTarget) {
                Optional<UUID> ghastUUID = quadLeashTarget.getQuadLeashingGhastUUID();
                if (ghastUUID.isPresent()) {
                    // находим гаста по uuid
                    Entity leashingGhast = ((ServerLevel)thisEntity.level()).getEntity(ghastUUID.get());
                    if (leashingGhast instanceof HappyGhast happyGhast) {
                        System.out.println(LOG_PREFIX + "Player(" + player.getId() + ") used shears on Target(" + thisEntity.getId() +
                                ") that is quad-leashed to Ghast(" + happyGhast.getId() + ")");

                        // находим uuid этой сущности
                        UUID thisEntityUUID = thisEntity.getUUID();

                        // удаляем только эту сущность из привязей гаста
                        happyGhast.removeQuadLeashedEntity(thisEntityUUID);

                        // очищаем обратную ссылку
                        quadLeashTarget.setQuadLeashingGhastUUID(Optional.empty());

                        // выдаем поводок игроку
                        player.getInventory().add(new ItemStack(Items.LEAD));

                        // звук разрезания
                        thisEntity.level().playSound(null, thisEntity.getX(), thisEntity.getY(), thisEntity.getZ(),
                                net.minecraft.sounds.SoundEvents.SHEEP_SHEAR,
                                net.minecraft.sounds.SoundSource.NEUTRAL, 0.5F, 1.0F);

                        didCutAnyLeash = true;
                    }
                }
            }

            // проверяем, если это лодка - нет ли к ней квадро-привязи от гаста
            if (thisEntity.getVehicle() instanceof Boat boat) {
                ServerLevel level = (ServerLevel)thisEntity.level();

                // найти всех гастов в радиусе
                List<HappyGhast> nearbyGhasts = level.getEntitiesOfClass(
                        HappyGhast.class,
                        thisEntity.getBoundingBox().inflate(100.0D),
                        ghast -> ghast.isQuadLeashing()
                );

                for (HappyGhast ghast : nearbyGhasts) {
                    // проверить, привязана ли лодка к гасту
                    if (ghast.getQuadLeashedEntityUUIDs().contains(boat.getUUID())) {
                        //System.out.println(LOG_PREFIX + "Player(" + player.getId() + ") used shears on Mob(" + thisEntity.getId() +
                        //        ") in Boat(" + boat.getId() + ") that is quad-leashed to Ghast(" + ghast.getId() + ")");

                        // удаляем лодку из привязей гаста
                        ghast.removeQuadLeashedEntity(boat.getUUID());

                        // выдаем поводок игроку
                        player.getInventory().add(new ItemStack(Items.LEAD));

                        // воспроизводим звук разрезания
                        thisEntity.level().playSound(null, thisEntity.getX(), thisEntity.getY(), thisEntity.getZ(),
                                net.minecraft.sounds.SoundEvents.SHEEP_SHEAR,
                                net.minecraft.sounds.SoundSource.NEUTRAL, 0.5F, 1.0F);

                        didCutAnyLeash = true;
                    }
                }
            }

            // проверяем стандартный поводок
            if (thisEntity.isLeashed()) {
                thisEntity.dropLeash(true, true); // разрезаем поводок и выбрасываем предмет

                // воспроизводим звук разрезания
                thisEntity.level().playSound(null, thisEntity.getX(), thisEntity.getY(), thisEntity.getZ(),
                        net.minecraft.sounds.SoundEvents.SHEEP_SHEAR,
                        net.minecraft.sounds.SoundSource.NEUTRAL, 0.5F, 1.0F);

                didCutAnyLeash = true;
            }

            // проверяем привязанных мобов к этой сущности
            List<Mob> leashedToThis = thisEntity.level().getEntitiesOfClass(
                    Mob.class,
                    thisEntity.getBoundingBox().inflate(10.0D),
                    (entity) -> entity.isLeashed() && entity.getLeashHolder() == thisEntity
            );

            if (!leashedToThis.isEmpty()) {
                System.out.println(LOG_PREFIX + "Player(" + player.getId() + ") used shears on Mob(" + thisEntity.getId() +
                        ") that has " + leashedToThis.size() + " entities leashed to it");

                // разрезаем все поводки
                for (Mob leashedMob : leashedToThis) {
                    leashedMob.dropLeash(true, true);
                }

                didCutAnyLeash = true;
            }

            // применяем износ ножниц и возвращаем результат
            if (didCutAnyLeash) {
                // немного повреждаем ножницы (только один раз, даже если разрезали несколько поводков)
                if (!player.getAbilities().instabuild) {
                    itemstack.hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(hand));
                }

                cir.setReturnValue(InteractionResult.SUCCESS);
            }
        }
    }
}