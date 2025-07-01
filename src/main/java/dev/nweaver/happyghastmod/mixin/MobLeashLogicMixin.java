package dev.nweaver.happyghastmod.mixin;

import dev.nweaver.happyghastmod.leash.MultiLeashData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.UUID;

@Mixin(Mob.class)
public abstract class MobLeashLogicMixin extends LivingEntity {

    // макс дистанция поводка
    private static final double MAX_LEASH_DISTANCE = 12.0;
    private static final double MAX_LEASH_DISTANCE_SQ = MAX_LEASH_DISTANCE * MAX_LEASH_DISTANCE;

    // мин расстояние для начала притяжения
    private static final double MIN_PULL_DISTANCE = 6.0;
    private static final double MIN_PULL_DISTANCE_SQ = MIN_PULL_DISTANCE * MIN_PULL_DISTANCE;

    // счетчик тиков для более плавного применения притяжения
    private int pullTickCounter = 0;

    @Shadow public abstract boolean isLeashed();
    @Shadow public abstract @Nullable Entity getLeashHolder();

    protected MobLeashLogicMixin(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
    }

    private Mob hg_self() {
        return (Mob)(Object)this;
    }

    @Inject(method = "tickLeash", at = @At("HEAD"), cancellable = true)
    private void hg_onTickLeash(CallbackInfo ci) {
        Mob mob = hg_self();

        // обработка отложенной загрузки привязки к забору
        CompoundTag leashInfoTag = ((MobAccessor)mob).getLeashInfoTag();
        if (leashInfoTag != null && !mob.level().isClientSide()) {
            if (leashInfoTag.contains("X") && leashInfoTag.contains("Y") && leashInfoTag.contains("Z")) {
                BlockPos fencePos = new BlockPos(
                        leashInfoTag.getInt("X"),
                        leashInfoTag.getInt("Y"),
                        leashInfoTag.getInt("Z")
                );

                // ищем или создаем узел поводка на заборе
                LeashFenceKnotEntity knot = LeashFenceKnotEntity.getOrCreateKnot(mob.level(), fencePos);
                if (knot != null) {
                    mob.setLeashedTo(knot, true);
                }
            }
            ((MobAccessor)mob).setLeashInfoTag(null);
        }

        // если привязан к забору, используем упрощенную логику
        Entity leashHolder = mob.getLeashHolder();
        if (leashHolder instanceof LeashFenceKnotEntity) {
            if (!mob.level().isClientSide()) {
                // проверяем, существует ли узел; если нет - отвязываемся
                if (leashHolder.isRemoved()) {
                    mob.dropLeash(true, true);
                    ci.cancel(); // прекращаем обработку для этого тика
                    return;
                }

                // проверяем расстояние до забора
                double distSq = mob.distanceToSqr(leashHolder);

                // если слишком далеко - разрываем поводок
                if (distSq > MAX_LEASH_DISTANCE_SQ) {
                    mob.dropLeash(true, true);
                } else if (distSq > MIN_PULL_DISTANCE_SQ) {
                    // применяем притяжение к забору
                    applyLeashPull(mob, leashHolder);
                }
            }
            ci.cancel();
            return;
        }

        // остальная логика для привязки между мобами
        if (!mob.level().isClientSide()) {
            Entity mainHolder = mob.getLeashHolder();

            boolean holderValid = mainHolder != null && mainHolder.level() == mob.level() && !mainHolder.isRemoved();

            if (!holderValid) {
                if (mainHolder != null) {
                    mob.dropLeash(true, true);
                }
            } else {
                List<Entity> allHolders = MultiLeashData.getLeashHolders(mob, mob.level());
                boolean primaryHolderInList = false;

                if (!allHolders.contains(mainHolder)) {
                    allHolders.add(mainHolder);
                }

                pullTickCounter++;

                if (pullTickCounter >= 2) {
                    pullTickCounter = 0;

                    for (Entity holder : allHolders) {
                        if (holder == null || holder.isRemoved()) {
                            if (holder != mainHolder) {
                                MultiLeashData.removeLeashHolder(mob, holder);
                            }
                            continue;
                        }
                        if (holder == mainHolder) {
                            primaryHolderInList = true;
                        }
                        applyLeashPull(mob, holder);
                    }

                    if (!primaryHolderInList && mainHolder != null) {
                        applyLeashPull(mob, mainHolder);
                    }
                }
            }
        }

        ci.cancel();
    }

    // полностью переработанный метод применения силы поводка
    private void applyLeashPull(Mob mob, Entity holder) {
        // особая обработка для гаста
        if (mob.getClass().getName().contains("HappyGhast")) {
            applyFlyingMobLeashPull(mob, holder);
            return;
        }

        // используем центры bounding box
        Vec3 mobPos = mob.getBoundingBox().getCenter();
        Vec3 holderPos = holder.getBoundingBox().getCenter();

        // вычисляем расстояние между мобом и держателем
        double distSq = mobPos.distanceToSqr(holderPos);

        // применяем притяжение только если расстояние больше минимального
        if (distSq > MIN_PULL_DISTANCE_SQ) {
            // вычисляем вектор направления от моба к держателю
            Vec3 vecToHolder = holderPos.subtract(mobPos);
            double dist = Math.sqrt(distSq);

            // защита от деления на ноль
            if (dist < 1.0E-4D) return;

            // нормализуем вектор направления
            Vec3 pullDir = vecToHolder.scale(1.0 / dist);

            // рассчитываем силу притяжения в зависимости от расстояния
            // чем дальше моб от держателя, тем сильнее притяжение
            double distFactor = Math.min(1.0, (distSq - MIN_PULL_DISTANCE_SQ) / (MAX_LEASH_DISTANCE_SQ - MIN_PULL_DISTANCE_SQ));
            double basePullStrength = 0.02; // очень слабое базовое притяжение
            double maxExtraPull = 0.06;     // максимальное дополнительное притяжение при максимальном расстоянии
            double pullStrength = basePullStrength + (distFactor * maxExtraPull);

            // уменьшаем вертикальное притяжение
            double verticalFactor = 0.2; // 20% от обычного притяжения

            // создаем вектор силы притяжения
            Vec3 pullForce = new Vec3(
                    pullDir.x * pullStrength,
                    pullDir.y * pullStrength * verticalFactor,
                    pullDir.z * pullStrength
            );

            // применяем притяжение в зависимости от того, где находится моб
            if (mob.getVehicle() instanceof Boat boat) {
                // если моб в лодке, применяем силу к лодке
                boat.push(pullForce.x, pullForce.y * 0.5, pullForce.z);
            } else if (!mob.isPassenger()) {
                // если моб не пассажир, применяем силу к мобу
                Vec3 currentMotion = mob.getDeltaMovement();

                // плавно добавляем силу к текущему движению
                Vec3 newMotion = new Vec3(
                        currentMotion.x + pullForce.x,
                        currentMotion.y + pullForce.y,
                        currentMotion.z + pullForce.z
                );

                // устанавливаем новое движение
                mob.setDeltaMovement(newMotion);
            }
        }
    }

    // специальный метод притяжения для летающих мобов (счастливый гаст)
    private void applyFlyingMobLeashPull(Mob mob, Entity holder) {
        Vec3 mobPos = mob.position();
        Vec3 holderPos = holder.position();

        // для забора используем позицию чуть выше
        if (holder instanceof LeashFenceKnotEntity) {
            holderPos = holderPos.add(0, 0.5, 0);
        }

        double distance = mobPos.distanceTo(holderPos);

        // применяем силу только если расстояние больше минимального
        if (distance > MIN_PULL_DISTANCE) {
            Vec3 direction = holderPos.subtract(mobPos).normalize();

            // более сильное притяжение для летающих мобов
            double pullStrength = 0.15 * ((distance - MIN_PULL_DISTANCE) / (MAX_LEASH_DISTANCE - MIN_PULL_DISTANCE));
            pullStrength = Math.min(pullStrength, 0.3); // максимальная сила

            // для вертикального движения используем большую силу
            double verticalPullStrength = pullStrength * 1.5;

            Vec3 pullForce = new Vec3(
                    direction.x * pullStrength,
                    direction.y * verticalPullStrength,
                    direction.z * pullStrength
            );

            // получаем текущее движение
            Vec3 currentMotion = mob.getDeltaMovement();

            // добавляем демпфирование для плавности
            double damping = 0.8;
            Vec3 newMotion = new Vec3(
                    currentMotion.x * damping + pullForce.x,
                    currentMotion.y * damping + pullForce.y,
                    currentMotion.z * damping + pullForce.z
            );

            // ограничиваем максимальную скорость
            double maxSpeed = 0.5;
            if (newMotion.length() > maxSpeed) {
                newMotion = newMotion.normalize().scale(maxSpeed);
            }

            mob.setDeltaMovement(newMotion);
            mob.hasImpulse = true;
        }
    }

    @Inject(method = "setLeashedTo", at = @At("HEAD"), cancellable = true)
    private void hg_onSetLeashedTo(Entity entity, boolean broadcastPacket, CallbackInfo ci) {
        Mob mob = hg_self();

        // предотвращаем привязку к самому себе
        if (entity == mob) {
            ci.cancel();
            return;
        }

        // позволяем ванильной логике работать для leashfenceknotentity
        if (entity instanceof LeashFenceKnotEntity || entity == null) {
            // не отменяем для забора - пусть работает ванильная логика
            return;
        }

        ci.cancel(); // отменяем ванильный метод только для не-заборов

        if (mob.level().isClientSide()) {
            if (entity != null && !entity.isRemoved()) {
                ((MobAccessor) mob).setLeashHolder(entity);
            } else {
                ((MobAccessor) mob).setLeashHolder(null);
            }
            return;
        }

        // серверная логика для привязки к другим мобам
        if (!mob.isPassenger()) {
            mob.stopRiding();
        }

        MultiLeashData.addLeashHolder(mob, entity);
        MultiLeashData.updateMainLeashHolder(mob, entity);
    }

    @Inject(method = "dropLeash", at = @At("HEAD"), cancellable = true)
    private void hg_onDropLeash(boolean broadcastPacket, boolean dropLead, CallbackInfo ci) {
        Mob mob = hg_self();

        ci.cancel();

        if (mob.level().isClientSide()) return;

        // запоминаем, был ли моб привязан к забору, перед тем как очистить держателя
        Entity currentHolder = mob.getLeashHolder();
        boolean wasLeashedToFence = currentHolder instanceof LeashFenceKnotEntity;

        boolean wasLeashed = currentHolder != null || MultiLeashData.hasMultiLeashData(mob);

        if (currentHolder != null) {
            // логика для забора остается прежней: просто отвязываем
            if (currentHolder instanceof LeashFenceKnotEntity) {
                ((MobAccessor) mob).setLeashHolder(null);

                if (mob.level() instanceof ServerLevel serverLevel && broadcastPacket) {
                    serverLevel.getChunkSource().broadcastAndSend(mob,
                            new ClientboundSetEntityLinkPacket(mob, null));
                }
            } else {
                // для других держателей используем multileashdata
                MultiLeashData.removeLeashHolder(mob, currentHolder);
            }
        } else {
            MultiLeashData.clearLeashHolders(mob);
        }

        MultiLeashData.updateMainLeashHolder(mob, null);

        // дропаем поводок, только если droplead=true и моб не был привязан к забору
        // если он был привязан к забору, ванильная логика сама даст поводок игроку
        // и нам не нужно дропать второй, чтобы избежать дюпа
        if (dropLead && wasLeashed && !wasLeashedToFence) {
            mob.spawnAtLocation(Items.LEAD);
        }
    }

    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void hg_leashToPlayerInteract(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        Mob mob = hg_self();
        ItemStack itemstack = player.getItemInHand(hand);

        if (itemstack.is(Items.LEAD)) {
            if (!mob.canBeLeashed(player)) {
                return;
            }
            if (!mob.level().isClientSide) {
                MultiLeashData.addLeashHolder(mob, player);
                MultiLeashData.updateMainLeashHolder(mob, player);
                if (!player.getAbilities().instabuild) {
                    itemstack.shrink(1);
                }
                mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                        net.minecraft.sounds.SoundEvents.LEASH_KNOT_PLACE,
                        net.minecraft.sounds.SoundSource.NEUTRAL, 0.5F, 0.8F);
            }
            cir.setReturnValue(InteractionResult.sidedSuccess(mob.level().isClientSide()));
        }
    }


    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void hg_transferLeashInteract(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        Mob targetMob = hg_self(); // моб b

        Mob leashedMob = null; // моб a
        List<Entity> nearbyEntities = player.level().getEntities(player, player.getBoundingBox().inflate(10.0D),
                entity -> entity instanceof Mob mob && mob.getLeashHolder() == player);

        if (!nearbyEntities.isEmpty()) {
            leashedMob = (Mob)nearbyEntities.get(0);
        }

        boolean holdingLeashed = leashedMob != null;
        boolean notSameMob = leashedMob != targetMob;
        boolean isEmptyHand = player.getItemInHand(hand).isEmpty();

        if (holdingLeashed && notSameMob && isEmptyHand) {
            cir.setReturnValue(InteractionResult.SUCCESS); // отменяем сразу

            if (!targetMob.level().isClientSide()) {
                MultiLeashData.removeLeashHolder(leashedMob, player);
                MultiLeashData.addLeashHolder(leashedMob, targetMob);
                MultiLeashData.updateMainLeashHolder(leashedMob, targetMob);

                targetMob.level().playSound(null, targetMob.getX(), targetMob.getY(), targetMob.getZ(),
                        net.minecraft.sounds.SoundEvents.LEASH_KNOT_PLACE,
                        net.minecraft.sounds.SoundSource.NEUTRAL, 0.5F, 0.8F);
            }
        }
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void hg_onAddAdditionalSaveData(CompoundTag compound, CallbackInfo ci) {
        Mob mob = hg_self();
        Entity mainHolder = ((MobAccessor)mob).getLeashHolderAccessor();

        if (mainHolder != null) {
            // для забора сохраняем позицию
            if (mainHolder instanceof LeashFenceKnotEntity knot) {
                CompoundTag leashTag = new CompoundTag();
                leashTag.putInt("X", knot.getPos().getX());
                leashTag.putInt("Y", knot.getPos().getY());
                leashTag.putInt("Z", knot.getPos().getZ());
                compound.put("Leash", leashTag);
            } else {
                // для других сущностей сохраняем uuid
                compound.putUUID("MainLeashHolderUUID", mainHolder.getUUID());
            }
        }

        MultiLeashData.save(mob, compound);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void hg_onReadAdditionalSaveData(CompoundTag compound, CallbackInfo ci) {
        Mob mob = hg_self();
        MultiLeashData.load(mob, compound, mob.level());

        if (mob.level() instanceof ServerLevel serverLevel) {
            // сначала пробуем загрузить привязку к забору
            if (compound.contains("Leash", 10)) {
                CompoundTag leashTag = compound.getCompound("Leash");
                if (leashTag.contains("X") && leashTag.contains("Y") && leashTag.contains("Z")) {
                    BlockPos fencePos = new BlockPos(
                            leashTag.getInt("X"),
                            leashTag.getInt("Y"),
                            leashTag.getInt("Z")
                    );

                    // отложенная загрузка через leashinfotag
                    CompoundTag delayedLeashTag = new CompoundTag();
                    delayedLeashTag.putInt("X", fencePos.getX());
                    delayedLeashTag.putInt("Y", fencePos.getY());
                    delayedLeashTag.putInt("Z", fencePos.getZ());
                    ((MobAccessor) mob).setLeashInfoTag(delayedLeashTag);
                }
            } else if (compound.hasUUID("MainLeashHolderUUID")) {
                // загружаем привязку к обычной сущности
                UUID mainHolderUUID = compound.getUUID("MainLeashHolderUUID");
                Entity potentialMainHolder = MultiLeashData.findEntityByUUID(serverLevel, mainHolderUUID);

                if (potentialMainHolder != null && !potentialMainHolder.isRemoved() &&
                        MultiLeashData.isLeashedTo(mob, potentialMainHolder)) {
                    ((MobAccessor) mob).setLeashHolder(potentialMainHolder);
                }
            }
        } else {
            // на клиенте основной держатель установится через пакет
            ((MobAccessor) mob).setLeashHolder(null);
        }
    }
}