package dev.nweaver.happyghastmod.entity.components;

import com.ibm.icu.impl.Pair;
import dev.nweaver.happyghastmod.api.IQuadLeashTarget;
import dev.nweaver.happyghastmod.entity.HappyGhast;
import dev.nweaver.happyghastmod.init.ItemInit;
import dev.nweaver.happyghastmod.item.CustomHarnessItem;
import dev.nweaver.happyghastmod.item.HarnessItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class GhastInteractionComponent {
    private static final Logger LOGGER = LogManager.getLogger();
    private final HappyGhast owner;
    private final GhastDataComponent dataComponent;
    private final GhastLeashComponent leashComponent;
    private final GhastPlatformComponent platformComponent;


    public GhastInteractionComponent(HappyGhast owner, GhastDataComponent dataComponent,
                                     GhastLeashComponent leashComponent, GhastPlatformComponent platformComponent) {
        this.owner = owner;
        this.dataComponent = dataComponent;
        this.leashComponent = leashComponent;
        this.platformComponent = platformComponent;
    }
    private Pair<Entity, Mob> findQuadLeashTargetAndSourceMob(Player player) {
        List<Entity> nearbyEntities = owner.level().getEntities(owner, owner.getBoundingBox().inflate(15.0D),
                e -> e instanceof Mob && ((Mob) e).isLeashed() && ((Mob) e).getLeashHolder() == player);

        if (!nearbyEntities.isEmpty()) {
            Mob leashedMob = (Mob) nearbyEntities.get(0);
            //LOGGER.debug("Found mob {} ({}) leashed to player {}", leashedMob.getDisplayName().getString(), leashedMob.getUUID(), player.getName().getString());

            // можем подвязывать любого моба, если он в лодке
            if (leashedMob.getVehicle() instanceof Boat boat) {
                //LOGGER.debug("Leashed mob is in boat {}. Targeting boat", boat.getUUID());

                // проверяем, не привязана ли уже эта лодка к счстл гасту
                if (owner.getQuadLeashedEntityUUIDs().contains(boat.getUUID())) {
                    //LOGGER.debug("Boat is already leashed to this ghast. Ignoring");
                    return null;
                }

                return Pair.of(boat, leashedMob);
            }

            // если не лодка, то проверям - можем ли привязать это существо
            boolean isValidType = leashedMob instanceof AbstractHorse || leashedMob instanceof Camel || leashedMob instanceof Sniffer;
            if (isValidType) {
                // проверяем, не привязан ли уже этот моб к счстл гасту
                if (owner.getQuadLeashedEntityUUIDs().contains(leashedMob.getUUID())) {
                    //LOGGER.debug("Entity is already leashed to this ghast. Ignoring");
                    return null;
                }

                //LOGGER.debug("Leashed mob's TYPE is valid for initiating direct quad-leash");
                return Pair.of(leashedMob, leashedMob);
            } else {
                //LOGGER.warn("Leashed mob's TYPE ({}) is NOT valid for direct quad-leash", leashedMob.getType().getDescriptionId());
            }
        }
        return null;
    }



    /**
     * Обрабатывает взаимодействие игрока с счстл гастом
     */
    public InteractionResult handleInteraction(Player player, InteractionHand hand) {
        // только основная рука
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

        ItemStack itemstack = player.getItemInHand(hand);

        // НОВОЕ: обработка отвязывания поводка при клике по привязанному гасту
        if (owner.isLeashed() && !owner.isQuadLeashing()) {
            Entity leashHolder = owner.getLeashHolder();

            // если игрок кликает пустой рукой по привязанному гасту
            if (itemstack.isEmpty() && !player.isSecondaryUseActive()) {
                // только если привязан к забору - отвязываем
                if (leashHolder instanceof net.minecraft.world.entity.decoration.LeashFenceKnotEntity) {
                    if (!owner.level().isClientSide) {
                        owner.dropLeash(true, false);
                        if (!player.getInventory().add(new ItemStack(Items.LEAD))) {
                            owner.spawnAtLocation(Items.LEAD);
                        }
                        owner.level().playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                                SoundEvents.LEASH_KNOT_BREAK, SoundSource.NEUTRAL, 0.5F, 1.0F);
                    }
                    return InteractionResult.sidedSuccess(owner.level().isClientSide);
                }
                // если привязан к игроку - позволяем сесть на гаста, если это тот же игрок
                else if (leashHolder == player) {
                    // пропускаем, чтобы обработать попытку сесть на гаста ниже
                } else {
                    // если привязан к другому игроку - блокируем взаимодействие
                    return InteractionResult.FAIL;
                }
            }
        }

        // ищем цель и источник
        Pair<Entity, Mob> targetAndSource = findQuadLeashTargetAndSourceMob(player);
        Entity quadLeashTarget = targetAndSource != null ? targetAndSource.getFirst() : null;
        Mob sourceLeashedMob = targetAndSource != null ? targetAndSource.getSecond() : null;

        /**
         * инициализируем квадропривязку
         */
        // Условие: пкм пустой рукой, найдена валидная цель (лодка или моб) -> гаст ОСЁДЛАН.
        if (itemstack.isEmpty() && quadLeashTarget != null && sourceLeashedMob != null) {
            //LOGGER.debug("Attempting Quad-Leash with target {} (Source mob: {})", quadLeashTarget.getUUID(), sourceLeashedMob.getUUID());

            if (!owner.level().isClientSide) {
                // Условия: оседлан, не привязан стандартно, не транспорт
                if (owner.isSaddled() && owner.getLeashHolder() == null && !owner.isVehicle()) {
                    // проверяем, не достигнут ли лимит привязки
                    if (owner.getQuadLeashedEntityCount() < 4) {
                        //LOGGER.info("Quad-Leash conditions MET. Target: {}. Source Mob: {}",
                        //        quadLeashTarget.getUUID(), sourceLeashedMob.getUUID());

                        sourceLeashedMob.dropLeash(true, false); // Отвязываем исходного моба от игрока
                        boolean success = owner.startQuadLeash(quadLeashTarget);

                        if (success) {
                            owner.level().playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                                    SoundEvents.LEASH_KNOT_PLACE, SoundSource.NEUTRAL, 0.7F, 1.2F);
                            return InteractionResult.SUCCESS;
                        } else {
                            //LOGGER.warn("Failed to add entity to quad leash list");
                            return InteractionResult.FAIL;
                        }
                    } else {
                        // уже привязано макс кол-во сущностей
                        //LOGGER.warn("Max quad leash entities ({}) already reached", 4);
                        return InteractionResult.FAIL;
                    }
                } else {
                    // не выполнены условия на гасте
                    //LOGGER.warn("Quad-Leash conditions on Ghast NOT MET. isSaddled={}, LeashHolder={}, isVehicle={}",
                    //       owner.isSaddled(), owner.getLeashHolder(), owner.isVehicle());
                    // Возвращаем PASS, чтобы не блокировать другие взаимодействия (например, седлание)
                    return InteractionResult.PASS;
                }
            } else {
                return InteractionResult.SUCCESS; // Предсказание клиента
            }
        }

        /**
         * обычная привязка
         */
        if (itemstack.is(Items.LEAD)) {
            // позволяем, только если игрок НЕ держит уже привязанного моба (чтобы избежать конфликта с квадро)
            if (findLeashedMob(player) == null) {
                //LOGGER.debug("Attempting standard leash interaction with lead item");
                return this.leashComponent.handleStandardLeashInteraction(player, hand);
            } else {
                //LOGGER.debug("Player holding lead attached to a mob, preventing standard leash interaction");
                return InteractionResult.CONSUME; // Игрок уже кого-то ведет
            }
        }

        boolean tryingToMount = owner.isSaddled() && !player.isSecondaryUseActive() && !itemstack.is(Items.LEAD);
        if (tryingToMount) {
            //LOGGER.debug("Attempting mounting regardless of leash state");
            return handleMounting(player);
        }

        // предотвращение некоторых конфликтных взаимодействий, если гаст привязан
        if (owner.isQuadLeashing() || owner.getLeashHolder() != null) {
            // блокируем только определенные взаимодействия при привязанном гасте
            if (itemstack.is(Items.SHEARS) || itemstack.getItem() instanceof HarnessItem || itemstack.is(Items.SNOWBALL)) {
                //LOGGER.debug("Preventing specific item use while Happy Ghast is leashed");
                return InteractionResult.CONSUME;
            }
        } else {
            // разрешаем действия, если гаст НЕ привязан
            // кормление
            if (itemstack.is(Items.SNOWBALL)) { return handleSnowballFeeding(player, itemstack); }
            // снятие сбруи
            if (itemstack.is(Items.SHEARS) && owner.isSaddled()) { return handleSaddleRemoval(player); }
            // надевание сбруи
            if (!owner.isSaddled() && itemstack.getItem() instanceof HarnessItem) { return handleSaddleInteraction(player, itemstack, ((HarnessItem)itemstack.getItem()).getColor()); }
        }

        // LOGGER.debug("Interaction passed through GhastInteractionComponent without action");
        return InteractionResult.PASS; // ни одно действие не подошло
    }


    private Mob findLeashedMob(Player player) {
        List<Entity> entities = owner.level().getEntities(owner, owner.getBoundingBox().inflate(15.0D),
                e -> e instanceof Mob && ((Mob) e).isLeashed() && ((Mob) e).getLeashHolder() == player);
        return entities.isEmpty() ? null : (Mob) entities.get(0);
    }
    private static class Pair<F, S> {
        private final F first;
        private final S second;
        private Pair(F first, S second) { this.first = first; this.second = second; }
        public static <F, S> Pair<F, S> of(F first, S second) { return new Pair<>(first, second); }
        public F getFirst() { return first; }
        public S getSecond() { return second; }
    }



    private InteractionResult handleSaddleInteraction(Player player, ItemStack itemstack, String color) {
        if (!player.getAbilities().instabuild) {
            itemstack.shrink(1);
        }

        // устанавливаем оседлание
        dataComponent.setSaddled(true);

        // смотрим айди кастомного седла
        String customHarnessId = CustomHarnessItem.getCustomHarnessId(itemstack);
        if (customHarnessId != null) {
            // устанавливаем цвет кастомного седла
            dataComponent.setHarnessColor("custom:" + customHarnessId);
        } else {
            // если не получилось - то ставим синий
            dataComponent.setHarnessColor(color);
        }

        if (owner.getInventoryComponent() != null) {
            owner.getInventoryComponent().syncInventoryWithState();
        }


        // проигрываем звук одевания седла
        owner.playHarnessEquipSound();

        return InteractionResult.sidedSuccess(owner.level().isClientSide);
    }

    /**
     * обрабатывает кормление гаста снежками
     */
    private InteractionResult handleSnowballFeeding(Player player, ItemStack itemstack) {
        if (!owner.level().isClientSide) {
            // уменьшаем стак снежков, если игрок не в креативе
            if (!player.getAbilities().instabuild) {
                itemstack.shrink(1);
            }

            // восстанавливаем здоровье (2 единицы за снежок)
            owner.heal(2.0F);

            // воспроизводим звук поедания
            owner.level().playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                    SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL,
                    1.0F, 1.0F + (owner.getRandom().nextFloat() - owner.getRandom().nextFloat()) * 0.2F);

            // создаем частицы
            if (owner.level() instanceof ServerLevel serverLevel) {
                double d0 = owner.getBoundingBox().getCenter().x;
                double d1 = owner.getBoundingBox().getCenter().y;
                double d2 = owner.getBoundingBox().getCenter().z;

                // частицы счастья
                for (int i = 0; i < 5; i++) {
                    double offsetX = owner.getRandom().nextGaussian() * 0.8D;
                    double offsetY = owner.getRandom().nextGaussian() * 0.8D;
                    double offsetZ = owner.getRandom().nextGaussian() * 0.8D;

                    serverLevel.sendParticles(
                            ParticleTypes.HEART, // сердечкии эффекта лечения
                            d0 + offsetX,
                            d1 + offsetY,
                            d2 + offsetZ,
                            1, // кол-во частиц в одной точке
                            0.0D, // скорость по X
                            0.0D, // скорость по Y
                            0.0D, // скорость по Z
                            0.0D // скорость разлета
                    );
                }
            }
        }

        return InteractionResult.sidedSuccess(owner.level().isClientSide);
    }


    /**
     * обрабатывает посадку игрока на гаста
     */
    private InteractionResult handleMounting(Player player) {
        // проверка на стороне сервера
        if (!owner.level().isClientSide) {
            // обработка получения поводка при посадке (если привязан)
            if (owner.isLeashed()) {
                Entity leashHolder = owner.getLeashHolder();
                owner.dropLeash(true, false); // Отвязываем без дропа
                player.getInventory().add(new ItemStack(Items.LEAD)); // Даем поводок игроку
                if (leashHolder instanceof net.minecraft.world.entity.decoration.LeashFenceKnotEntity knot) {
                    knot.discard();
                }
            }

            // деактивируем платформу, если она была активна
            if (platformComponent != null && platformComponent.isActive()) {
                platformComponent.deactivate();
            }

            // сажаем игрока
            boolean success = player.startRiding(owner);

            if (success) {
                // звук
                owner.playGogglesDownSound();
            } else {
                return InteractionResult.CONSUME;
            }
        }

        return InteractionResult.sidedSuccess(owner.level().isClientSide);
    }

    private InteractionResult handleSaddleRemoval(Player player) {
        if (!owner.level().isClientSide) {
            // получаем текущий цвет сбруи перед снятием
            String harnessColor = dataComponent.getHarnessColor();

            // снимаем седло
            dataComponent.setSaddled(false);

            // создаем и даем игроку соответствующий предмет сбруи
            ItemStack harnessStack;

            // проверяем, кастомная ли сбруя
            if (harnessColor.startsWith("custom:")) {
                // получаем айди кастомной сбруи
                String customId = harnessColor.substring(7); // Убираем "custom:"

                // находим базовый предмет сбруи для создания кастомной сбруи
                Item baseItem = null;
                for (RegistryObject<HarnessItem> harnessRegObj : ItemInit.HARNESS_ITEMS.values()) {
                    baseItem = harnessRegObj.get();
                    break;
                }

                if (baseItem != null) {
                    // создаем стак предмета
                    harnessStack = new ItemStack(baseItem);

                    // получаем данные о кастомной сбруе
                    dev.nweaver.happyghastmod.custom.CustomHarnessManager.CustomHarnessData data =
                            dev.nweaver.happyghastmod.custom.CustomHarnessManager.getCustomHarnessData(customId);

                    if (data != null) {
                        // добавляем кастомные данные
                        CompoundTag tag = harnessStack.getOrCreateTag();
                        tag.putString("CustomHarnessId", customId);
                        tag.putString("CustomHarnessName", data.getName());

                        // при необходимости добавляем флаги для текстур
                        if (data.hasSaddleTexture()) {
                            tag.putBoolean("HasCustomSaddleTexture", true);
                        }
                        if (data.hasGlassesTexture()) {
                            tag.putBoolean("HasCustomGlassesTexture", true);
                        }
                        if (data.hasAccessoryTexture()) {
                            tag.putBoolean("HasCustomAccessoryTexture", true);
                        }

                        // добавляем отображаемое имя
                        CompoundTag display = new CompoundTag();
                        display.putString("Name", "{\"text\":\"" + data.getName() + "\",\"italic\":false}");
                        tag.put("display", display);
                    } else {
                        // если не удалось получить данные о кастомной сбруе, используем стандартную
                        harnessStack = new ItemStack(getHarnessItemForColor("blue"));
                    }
                } else {
                    // если не удалось найти базовый предмет, используем стандартную сбрую
                    harnessStack = new ItemStack(getHarnessItemForColor("blue"));
                }
            } else {
                // создаем стандартную сбрую нужного цвета
                harnessStack = new ItemStack(getHarnessItemForColor(harnessColor));
            }

            // добавляем предмет в инвентарь игрока или выбрасываем рядом с гастом
            if (!player.getInventory().add(harnessStack)) {
                player.drop(harnessStack, false);
            }

            // звук снятия сбруи
            owner.playHarnessUnequipSound();

            // эффект частиц
            if (owner.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(
                        ParticleTypes.CRIT,
                        owner.getX(),
                        owner.getY() + 1.0D,
                        owner.getZ(),
                        10, // количество частиц
                        0.5D, // радиус по X
                        0.5D, // радиус по Y
                        0.5D, // радиус по Z
                        0.1D  // скорость
                );
            }
        }

        return InteractionResult.sidedSuccess(owner.level().isClientSide);
    }

    private net.minecraft.world.item.Item getHarnessItemForColor(String color) {
        // используем Map из ItemInit для получения предмета сбруи по цвету
        RegistryObject<HarnessItem> harnessItemObj = ItemInit.HARNESS_ITEMS.get(color);
        if (harnessItemObj != null) {
            return harnessItemObj.get();
        }

        // если цвет не найден, возвращаем синюю сбрую по умолчанию
        return ItemInit.HARNESS_ITEMS.getOrDefault("blue", ItemInit.HARNESS_ITEMS.values().iterator().next()).get();
    }

}