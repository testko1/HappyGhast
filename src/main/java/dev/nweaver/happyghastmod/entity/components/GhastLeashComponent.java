package dev.nweaver.happyghastmod.entity.components;

import dev.nweaver.happyghastmod.entity.HappyGhast;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gameevent.GameEvent;
// import net.minecraft.world.phys.Vec3;

// компонент для обычного поводка (игрок <-> гаст)
public class GhastLeashComponent {
    private final HappyGhast owner;

    public GhastLeashComponent(HappyGhast owner) {
        this.owner = owner;
    }

    // обработка клика поводком по гасту
    public InteractionResult handleStandardLeashInteraction(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (itemstack.is(Items.LEAD)) {
            // проверяет все условия (квадро-привязь, транспорт и т.д.)
            if (owner.canBeLeashed(player)) {
                if (!owner.level().isClientSide) {
                    // цепляем поводок
                    owner.setLeashedTo(player, true);
                    owner.gameEvent(GameEvent.ENTITY_INTERACT, player);
                }
                // тратим поводок, если не креатив
                if (!player.getAbilities().instabuild) {
                    itemstack.shrink(1);
                }
                owner.level().playSound(null, owner, SoundEvents.LEASH_KNOT_PLACE, SoundSource.NEUTRAL, 0.5F, 1.0F);
                return InteractionResult.sidedSuccess(owner.level().isClientSide);
            } else {
                // уже привязан или используется как транспорт
                return InteractionResult.CONSUME;
            }
        }
        return InteractionResult.PASS; // если в руке не поводок
    }

    // можно ли привязать гаста обычным поводком? (вызывается из самого гаста)
    public boolean canBeLeashedByPlayer(Player player, GhastPlatformComponent platformComponent) {
        // основная проверка: свободен, не транспорт, не пассажир, платформа выключена
        return owner.getLeashHolder() == null &&
                !owner.isVehicle() &&
                !owner.hasPassenger(player) &&
                (platformComponent == null || !platformComponent.isActive());
    }

}