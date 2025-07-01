package dev.nweaver.happyghastmod.mixin;

import dev.nweaver.happyghastmod.api.IQuadLeashTarget;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// миксин для отключения ии мобов, привязанных к гасту через квадро-привязь
@Mixin(Mob.class)
public abstract class MobQuadLeashBehaviorMixin implements IQuadLeashTarget {

    private static final Logger LOGGER = LoggerFactory.getLogger(MobQuadLeashBehaviorMixin.class);

    @Shadow protected GoalSelector goalSelector;
    @Shadow protected GoalSelector targetSelector;

    // сохраняем все флаги ии, чтобы отключить и восстановить их
    private static final Goal.Flag[] ALL_FLAGS = Goal.Flag.values();

    // создаем флаги для отслеживания состояния
    private boolean wasQuadLeashedLastTick = false;

    // проверяет, едет ли игрок на мобе
    private boolean isRiddenByPlayer() {
        Mob self = (Mob)(Object)this;
        return !self.getPassengers().isEmpty() && self.getPassengers().get(0) instanceof Player;
    }

    // отключает ии моба, когда он привязан к гасту через квадро-привязь
    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
    private void hg_preventAiWhenQuadLeashed(CallbackInfo ci) {
        // если на мобе едет игрок, не отменяем ии
        if (isRiddenByPlayer()) {
            return;
        }

        Optional<UUID> ghastUUID = this.getQuadLeashingGhastUUID();
        if (ghastUUID.isPresent()) {
            // моб привязан к гасту, отменяем выполнение ии
            ci.cancel();
        }
    }

    // отключает стандартную логику поводка у моба, когда он привязан к гасту через квадро-привязь
    @Inject(method = "tickLeash", at = @At("HEAD"), cancellable = true)
    private void hg_preventLeashLogicWhenQuadLeashed(CallbackInfo ci) {
        // если на мобе едет игрок, не отменяем логику поводка
        if (isRiddenByPlayer()) {
            return;
        }

        Optional<UUID> ghastUUID = this.getQuadLeashingGhastUUID();
        if (ghastUUID.isPresent()) {
            // моб привязан к гасту через квадро-привязку, отменяем стандартную логику поводка
            ci.cancel();
        }
    }

    // останавливает навигацию и управляет флагами ии при обновлении тика
    @Inject(method = "tick", at = @At("HEAD"))
    private void hg_updateRotationWhenQuadLeashed(CallbackInfo ci) {
        Mob self = (Mob)(Object)this;

        // если на мобе едет игрок, не применяем ограничения поводка
        if (isRiddenByPlayer()) {
            // проверяем, был ли моб ранее привязан - если да, восстанавливаем управление
            if (wasQuadLeashedLastTick) {
                // включаем все флаги управления для goalselector
                if (this.goalSelector != null) {
                    for (Goal.Flag flag : ALL_FLAGS) {
                        this.goalSelector.enableControlFlag(flag);
                    }
                }

                // включаем все флаги управления для targetselector
                if (this.targetSelector != null) {
                    for (Goal.Flag flag : ALL_FLAGS) {
                        this.targetSelector.enableControlFlag(flag);
                    }
                }

                // восстанавливаем ии, если был отключен
                if (self.isNoAi()) {
                    self.setNoAi(false);
                }

                wasQuadLeashedLastTick = false;
            }
            return;
        }

        // проверяем, привязан ли моб к какому-либо гасту
        Optional<UUID> ghastUUID = this.getQuadLeashingGhastUUID();
        boolean isQuadLeashed = ghastUUID.isPresent();

        // если статус привязки изменился
        if (isQuadLeashed != wasQuadLeashedLastTick) {
            if (isQuadLeashed) {
                // только что привязали

                // отключаем все флаги управления для goalselector
                if (this.goalSelector != null) {
                    for (Goal.Flag flag : ALL_FLAGS) {
                        this.goalSelector.disableControlFlag(flag);
                    }
                }

                // отключаем все флаги управления для targetselector
                if (this.targetSelector != null) {
                    for (Goal.Flag flag : ALL_FLAGS) {
                        this.targetSelector.disableControlFlag(flag);
                    }
                }

                // останавливаем навигацию
                if (self.getNavigation().isInProgress()) {
                    self.getNavigation().stop();
                }

                // устанавливаем нулевое движение только при первоначальной привязке
                self.setDeltaMovement(0, 0, 0);
            } else {
                // только что отвязали - восстанавливаем ai

                // включаем все флаги управления для goalselector
                if (this.goalSelector != null) {
                    for (Goal.Flag flag : ALL_FLAGS) {
                        this.goalSelector.enableControlFlag(flag);
                    }
                }

                // включаем все флаги управления для targetselector
                if (this.targetSelector != null) {
                    for (Goal.Flag flag : ALL_FLAGS) {
                        this.targetSelector.enableControlFlag(flag);
                    }
                }

                // восстанавливаем ии, если был отключен
                if (self.isNoAi()) {
                    self.setNoAi(false);
                }
            }

            // запоминаем текущее состояние для следующего тика
            wasQuadLeashedLastTick = isQuadLeashed;
        }

        // дополнительная проверка в каждом тике для надежности
        if (isQuadLeashed) {
            // если всё ещё привязан, убеждаемся что навигация остановлена
            if (self.getNavigation().isInProgress()) {
                self.getNavigation().stop();
            }
        }
    }
}