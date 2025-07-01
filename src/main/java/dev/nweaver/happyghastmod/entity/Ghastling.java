package dev.nweaver.happyghastmod.entity;

import dev.nweaver.happyghastmod.entity.goals.GhastlingFollowPlayerWithSnowballGoal;
import dev.nweaver.happyghastmod.entity.goals.GhastlingLookGoal;
import dev.nweaver.happyghastmod.entity.goals.GhastlingRandomFloatGoal;
import dev.nweaver.happyghastmod.init.EntityInit;
import dev.nweaver.happyghastmod.init.SoundInit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Ghastling extends Ghast {

    // константы для механики превращения
    private static final int TRANSFORM_TIME = 24000; // 1 день в тиках (24000 тиков)
    private static final int FEED_TRANSFORM_BOOST = 1200; // 1 минута времени на которую ускоряет кормление
    private static final int PARTICLE_COUNT = 5; // кол-во частиц при кормлении
    // константы для логики поводка
    private static final double GHASTLING_MAX_LEASH_DISTANCE = 5.0; // макс дистанция
    private static final double GHASTLING_MAX_LEASH_DISTANCE_SQR = GHASTLING_MAX_LEASH_DISTANCE * GHASTLING_MAX_LEASH_DISTANCE;
    private static final double GHASTLING_COMFORT_ZONE_RATIO = 0.8; // зона комфорта
    private static final double GHASTLING_COMFORT_ZONE_SQR = GHASTLING_MAX_LEASH_DISTANCE_SQR * GHASTLING_COMFORT_ZONE_RATIO;
    private static final double GHASTLING_LEASH_PULL_STRENGTH = 0.06; // базовая сила притяжения
    private static final double GHASTLING_LEASH_VERTICAL_PULL_STRENGTH = 0.04; // сила вертикальной коррекции
    private static final double GHASTLING_MOTION_DAMPING = 0.92D; // демпфирование движения
    private static final double GHASTLING_VERTICAL_DAMPING = 0.95D; // демпфирование вертикального движения
    private static final double GHASTLING_PULL_FORCE_SMOOTHING = 0.2; // коэффициент сглаживания силы
    private Vec3 smoothedPullForce = Vec3.ZERO; // хранит сглаженную силу между тиками
    private static final double MAX_LEASHED_SPEED = 0.45;
    // константы для ограничения высоты
    public static final int MAX_HEIGHT_ABOVE_GROUND = 2; // макс высота над землей
    private boolean anchorInitialized = false;
    private static final Logger LOGGER = LogManager.getLogger();
    // данные для синхронизации
    private static final EntityDataAccessor<Integer> DATA_TRANSFORM_PROGRESS =
            SynchedEntityData.defineId(Ghastling.class, EntityDataSerializers.INT);

    public Ghastling(EntityType<? extends Ghast> entityType, Level level) {
        super(entityType, level);
        this.xpReward = 1; // меньше опыта чем за большого гаста
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_TRANSFORM_PROGRESS, 0); // начальный прогресс превращения
    }

    // геттер и сеттер для прогресса
    public int getTransformProgress() {
        return this.entityData.get(DATA_TRANSFORM_PROGRESS);
    }

    public void setTransformProgress(int progress) {
        this.entityData.set(DATA_TRANSFORM_PROGRESS, progress);
    }

    // находит высоту блока под гастлингом
    public int getGroundHeight() {
        int entityX = Mth.floor(this.getX());
        int entityZ = Mth.floor(this.getZ());
        int startY = Mth.floor(this.getY()) - 1;
        // проверяем гораздо больше блоков вниз
        int minY = Math.max(this.level().getMinBuildHeight(), startY - 128);

        // проверяем блоки сверху вниз
        for (int y = startY; y >= minY; y--) {
            // получаем блок на этой позиции
            BlockPos blockPos = new BlockPos(entityX, y, entityZ);
            BlockState blockState = this.level().getBlockState(blockPos);

            // если блок не воздух и не жидкость
            if (!blockState.isAir() && !blockState.liquid()) {
                // проверяем коллизию
                VoxelShape collisionShape = blockState.getCollisionShape(this.level(), blockPos);
                if (!collisionShape.isEmpty()) {
                    // возвращаем верхнюю границу блока
                    return y + 1;
                }
            }
        }

        // если не нашли блок, возвращаем -1
        return -1;
    }

    @Override
    public void tick() {
        super.tick(); // важно! вызывает базовую логику, включая movecontrol

        if (!this.level().isClientSide()) {

            // инициализация якоря при первом тике
            if (!anchorInitialized) {
                AnchorManager.setAnchor(this, this.getX(), this.getY(), this.getZ());
                anchorInitialized = true;
                //LOGGER.debug("Initialized anchor for Ghastling at spawn location: {}, {}, {}",
                //        this.getX(), this.getY(), this.getZ());
            }
            // логика превращения
            int currentProgress = getTransformProgress();
            if (currentProgress < TRANSFORM_TIME) {
                setTransformProgress(currentProgress + 1);
            } else {
                transformToHappyGhast();
                return;
            }
            int groundY = getGroundHeight();
            if (groundY != -1) {
                double maxAllowedY = groundY + MAX_HEIGHT_ABOVE_GROUND;

                // если гастлинг слишком высоко, опускаем его
                if (this.getY() > maxAllowedY + 5.0) { // добавляем буфер в 5 блоков
                    // более сильная коррекция вниз
                    Vec3 motion = this.getDeltaMovement();
                    double heightDiff = this.getY() - maxAllowedY;
                    // чем выше тем сильнее гравитация (макс 0.15)
                    double gravityFactor = Math.min(0.15, 0.05 + (heightDiff / 100.0) * 0.1);
                    this.setDeltaMovement(motion.x, Math.max(motion.y - gravityFactor, -0.2), motion.z);
                    this.hasImpulse = true;
                }
            } else {
                // если земли нет, проверяем y-координату
                // находится ли моб выше 30 блоков от мин высоты мира
                int minBuildHeight = this.level().getMinBuildHeight();
                int safeHeight = minBuildHeight + 30;

                if (this.getY() > safeHeight && !AnchorManager.isFollowing(this)) {
                    // если моб слишком высоко и не следует, опускаем
                    Vec3 motion = this.getDeltaMovement();
                    // чем выше тем сильнее опускаемся
                    double heightExcess = this.getY() - safeHeight;
                    double descent = Math.min(0.08, 0.02 + (heightExcess / 100.0) * 0.06);
                    this.setDeltaMovement(motion.x, Math.max(motion.y - descent, -0.15), motion.z);
                    this.hasImpulse = true;
                }
            }
            // ограничение радиуса передвижения
            if (AnchorManager.hasAnchor(this)) {
                Vec3 anchor = AnchorManager.getAnchor(this);
                double maxRadius = AnchorManager.getMaxRadius(this); // должно вернуть 320 для ghastling

                // рассчитываем расстояние до якоря (только xz)
                double dx = this.getX() - anchor.x;
                double dz = this.getZ() - anchor.z;
                double distSquared = dx * dx + dz * dz;

                // если за пределами радиуса, корректируем движение
                if (distSquared > maxRadius * maxRadius) {
                    // нормализуем вектор и находим точку на границе
                    double dist = Math.sqrt(distSquared);
                    double scale = maxRadius / dist;

                    // вычисляем новую позицию на границе
                    double newX = anchor.x + dx * scale;
                    double newZ = anchor.z + dz * scale;

                    // плавно двигаем гастлинга обратно
                    Vec3 motion = this.getDeltaMovement();
                    double pullStrength = 0.05; // сила "притяжения" к якорю

                    // вектор в сторону якоря (только xz)
                    double moveX = (newX - this.getX()) * pullStrength;
                    double moveZ = (newZ - this.getZ()) * pullStrength;

                    // применяем корректирующее движение
                    this.setDeltaMovement(motion.x + moveX, motion.y, motion.z + moveZ);
                    this.hasImpulse = true;
                }
            }


            // плавная логика притяжения поводком
            if (this.isLeashed()) {
                Entity leashHolder = this.getLeashHolder();
                if (leashHolder != null && leashHolder.level() == this.level()) {
                    double distanceSqr = this.distanceToSqr(leashHolder);

                    // применяем базовое замедление
                    Vec3 currentDelta = this.getDeltaMovement();
                    Vec3 dampedDelta = currentDelta.multiply(GHASTLING_MOTION_DAMPING, GHASTLING_VERTICAL_DAMPING, GHASTLING_MOTION_DAMPING);

                    // если за пределами зоны комфорта, применяем силу
                    if (distanceSqr > GHASTLING_COMFORT_ZONE_SQR) {
                        Vec3 currentPos = this.position();
                        Vec3 holderPos = leashHolder.position();
                        Vec3 vecToHolder = holderPos.subtract(currentPos);

                        // рассчитываем силу натяжения
                        double currentDist = Math.sqrt(distanceSqr);
                        double maxDist = Math.sqrt(GHASTLING_MAX_LEASH_DISTANCE_SQR);
                        double comfortDist = Math.sqrt(GHASTLING_COMFORT_ZONE_SQR);

                        double overshootRatio = Math.min(1.0, (currentDist - comfortDist) / (maxDist - comfortDist));
                        // квадратичная интерполяция для плавности
                        double pullStrengthFactor = overshootRatio * GHASTLING_LEASH_PULL_STRENGTH;

                        // рассчитываем сырую силу
                        Vec3 rawPullForce = vecToHolder.normalize().scale(pullStrengthFactor);

                        // сглаживаем силу натяжения
                        smoothedPullForce = smoothedPullForce.scale(1 - GHASTLING_PULL_FORCE_SMOOTHING)
                                .add(rawPullForce.scale(GHASTLING_PULL_FORCE_SMOOTHING));

                        // вертикальная коррекция (очень плавная)
                        double verticalDiff = holderPos.y - currentPos.y;
                        Vec3 extraVerticalForce = Vec3.ZERO;
                        if (Math.abs(verticalDiff) > 1.0) {
                            double verticalRatio = Math.min(Math.abs(verticalDiff) / 10.0, 1.0);
                            double yPullStrength = verticalRatio * verticalRatio * GHASTLING_LEASH_VERTICAL_PULL_STRENGTH;
                            extraVerticalForce = new Vec3(0, Math.signum(verticalDiff) * yPullStrength, 0);
                        }

                        // применяем сглаженную силу
                        Vec3 newDelta = dampedDelta.add(smoothedPullForce).add(extraVerticalForce);

                        // ограничиваем макс скорость
                        if (newDelta.lengthSqr() > MAX_LEASHED_SPEED * MAX_LEASHED_SPEED) {
                            newDelta = newDelta.normalize().scale(MAX_LEASHED_SPEED);
                        }

                        this.setDeltaMovement(newDelta);
                        this.hasImpulse = true; // сообщаем ии о внешнем воздействии
                    } else {
                        // в зоне комфорта просто демпфируем
                        this.setDeltaMovement(dampedDelta);
                        // постепенно уменьшаем силу
                        if (smoothedPullForce.lengthSqr() > 1.0E-6) { // проверка на почти ноль
                            smoothedPullForce = smoothedPullForce.scale(0.8); // уменьшаем быстрее чем накапливаем
                        } else {
                            smoothedPullForce = Vec3.ZERO;
                        }
                    }
                } else {
                    // держатель потерян, сбрасываем силу
                    smoothedPullForce = Vec3.ZERO;
                    if (leashHolder == null || leashHolder.isRemoved()) {
                        this.dropLeash(true, true);
                    }
                }
            } else {
                // если не привязан, сбрасываем силу
                smoothedPullForce = Vec3.ZERO;
            }
            handleWaterAvoidance();
        }
    }

    // обработка взаимодействия с игроком
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        Level level = this.level(); // сохраняем уровень для краткости

        // проверка кормления снежком
        if (itemstack.is(Items.SNOWBALL)) {
            if (!level.isClientSide) {
                // уменьшаем стак если не креатив
                if (!player.getAbilities().instabuild) {
                    itemstack.shrink(1);
                }

                // звук поедания
                level.playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL,
                        1.0F, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.2F);

                // лечим (15 единицы)
                this.heal(1.5F);

                // ускоряем превращение
                int currentProgress = getTransformProgress();
                setTransformProgress(Math.min(currentProgress + FEED_TRANSFORM_BOOST, TRANSFORM_TIME));

                // частицы
                spawnGreenParticles();

                // если прогресс макс, превращаемся
                if (getTransformProgress() >= TRANSFORM_TIME) {
                    transformToHappyGhast();
                }
            }
            // возвращаем успех, чтобы предотвратить другие взаимодействия
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // проверка использования поводка
        if (itemstack.is(Items.LEAD)) {
            // не можем привязать если уже привязаны
            if (!level.isClientSide && this.canBeLeashed(player)) {
                // привязываем к игроку
                this.setLeashedTo(player, true);
                // событие взаимодействия
                this.gameEvent(GameEvent.ENTITY_INTERACT, player);
                // уменьшаем кол-во поводков если не креатив
                if (!player.getAbilities().instabuild) {
                    itemstack.shrink(1);
                }
                // звук привязывания
                level.playSound(null, this, SoundEvents.LEASH_KNOT_PLACE, SoundSource.NEUTRAL, 0.5F, 1.0F);
                // возвращаем успех
                return InteractionResult.SUCCESS; // используем success для серверной логики
            }
            // если на клиенте или нельзя привязать, возвращаем consume
            return InteractionResult.CONSUME;
        }

        // если не снежок и не поводок, передаем дальше
        // это позволяет отвязать пустой рукой
        return super.mobInteract(player, hand);
    }

    // определяет, можно ли привязать гастлинга
    @Override
    public boolean canBeLeashed(Player player) {
        // проверяем не привязан ли он уже
        return !this.isLeashed();
    }

    @Override
    public Vec3 getLeashOffset() {
        return new Vec3(0.0D, this.getBbHeight() * 0.5F, 0.0D);
    }

    // создает зеленые частицы при кормлении
    private void spawnGreenParticles() {
        if (this.level() instanceof ServerLevel serverLevel) {
            double d0 = this.getBoundingBox().getCenter().x;
            double d1 = this.getBoundingBox().getCenter().y;
            double d2 = this.getBoundingBox().getCenter().z;

            // частицы счастья
            for (int i = 0; i < PARTICLE_COUNT; i++) {
                double offsetX = this.random.nextGaussian() * 0.8D;
                double offsetY = this.random.nextGaussian() * 0.8D;
                double offsetZ = this.random.nextGaussian() * 0.8D;

                serverLevel.sendParticles(
                        ParticleTypes.HAPPY_VILLAGER, // зеленые частицы счастья
                        d0 + offsetX,
                        d1 + offsetY,
                        d2 + offsetZ,
                        1, 0.0D, 0.0D, 0.0D, 0.0D
                );
            }
        }
    }

    // превращает гастлинга в счстл гаста
    private void transformToHappyGhast() {
        if (this.level().isClientSide || this.isRemoved()) {
            return;
        }

        HappyGhast happyGhast = EntityInit.HAPPY_GHAST.get().create(this.level());

        if (happyGhast != null) {
            // отвязываем перед удалением
            if (this.isLeashed()) {
                this.dropLeash(true, true); // отвязать и дропнуть поводок
            }

            // переносим позицию и поворот
            happyGhast.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot());

            // переносим другие атрибуты (имя, здоровье и тд)
            if (this.hasCustomName()) {
                happyGhast.setCustomName(this.getCustomName());
            }

            // звук и частицы для эффекта
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.PLAYER_LEVELUP, SoundSource.HOSTILE,
                    1.0F, 1.0F);

            if (this.level() instanceof ServerLevel serverLevel) {
                // создаем облако частиц
                for (int i = 0; i < 30; i++) {
                    double offsetX = this.random.nextGaussian() * 1.2D;
                    double offsetY = this.random.nextGaussian() * 1.2D;
                    double offsetZ = this.random.nextGaussian() * 1.2D;

                    serverLevel.sendParticles(
                            ParticleTypes.HAPPY_VILLAGER,
                            this.getX() + offsetX,
                            this.getY() + offsetY,
                            this.getZ() + offsetZ,
                            1, 0.0D, 0.0D, 0.0D, 0.0D
                    );
                }
            }

            // добавляем нового гаста в мир
            this.level().addFreshEntity(happyGhast);

            // удаляем гастлинга
            this.discard();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag c) {
        super.addAdditionalSaveData(c);
        c.putInt("TransformProgress", getTransformProgress());
        c.putBoolean("AnchorInitialized", this.anchorInitialized);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag c) {
        super.readAdditionalSaveData(c);
        if (c.contains("TransformProgress")) setTransformProgress(c.getInt("TransformProgress"));
        this.anchorInitialized = c.getBoolean("AnchorInitialized");
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new GhastlingFollowPlayerWithSnowballGoal(this, 1.0D, 30.0F));
        // заменяем happyghastrandomfloatgoal на свой
        this.goalSelector.addGoal(5, new GhastlingRandomFloatGoal(this)); // используем свой, если создали
        // заменяем happyghastlookgoal на ghastlinglookgoal
        this.goalSelector.addGoal(7, new GhastlingLookGoal(this)); // используем новую цель
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes() // используем createMobAttributes() тк гаст уже добавляет свои
                .add(Attributes.MAX_HEALTH, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 64.0D); // дальность следования важна и для поводка
    }

    @Override
    public boolean causeFallDamage(float distance, float damageMultiplier, DamageSource source) {
        return false;
    }

    @Override
    public void setCharging(boolean charging) {
        super.setCharging(false); // гастлинг не стреляет
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false; // не исчезает в мирном режиме
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
    }

    // гарантируем что nbt поводка обрабатывается правильно
    @Override
    public void dropLeash(boolean broadcastPacket, boolean dropLeashItem) {
        this.smoothedPullForce = Vec3.ZERO; // сброс при отвязывании
        super.dropLeash(broadcastPacket, dropLeashItem);
    }

    @Override
    public boolean isPersistenceRequired() {
        // гаст никогда не исчезает
        return true;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundInit.GHASTLING_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundInit.GHASTLING_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundInit.GHASTLING_DEATH.get();
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (!this.isSilent()) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundInit.GHASTLING_SPAWN.get(),
                    this.getSoundSource(),
                    1.0F, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.2F);
        }
    }

    @Override
    public boolean canDrownInFluidType(FluidType type) {
        return false; // не может утонуть
    }

    // отключает плавание в воде
    @Override
    public boolean canSwimInFluidType(FluidType type) {
        return false; // не "плавает" в традиционном смысле
    }

    // отключает погружение в воду
    @Override
    public boolean isPushedByFluid() {
        return false; // жидкости не толкают
    }
    // доп вертикальное движение над водой
    private void handleWaterAvoidance() {
        if (this.level().isClientSide) {
            return;
        }

        // проверяем блоки под гастлингом
        int entityX = Mth.floor(this.getX());
        int entityZ = Mth.floor(this.getZ());
        int entityY = Mth.floor(this.getY() - 0.2); // немного ниже центра

        boolean waterBelow = false;
        int waterSurfaceY = -1;

        // проверяем до 5 блоков вниз
        for (int y = entityY; y > entityY - 5 && y > 0; y--) {
            BlockPos blockPos = new BlockPos(entityX, y, entityZ);
            FluidState fluidState = this.level().getFluidState(blockPos);

            if (!fluidState.isEmpty() && fluidState.is(FluidTags.WATER)) {
                // нашли воду
                waterBelow = true;
                waterSurfaceY = y + 1; // поверхность воды на 1 выше
                break;
            } else if (!this.level().getBlockState(blockPos).isAir()) {
                // нашли твердый блок - дальше не проверяем
                break;
            }
        }

        // если гастлинг в воде
        BlockPos currentPos = this.blockPosition();
        boolean inWater = this.level().getFluidState(currentPos).is(FluidTags.WATER);

        if (inWater) {
            // гастлинг в воде - быстро вверх
            Vec3 motion = this.getDeltaMovement();
            this.setDeltaMovement(motion.x, Math.max(0.1, motion.y + 0.08), motion.z);
            this.hasImpulse = true;
        } else if (waterBelow && waterSurfaceY != -1) {
            // вода под гастлингом - определяем близость
            double distanceToWater = this.getY() - waterSurfaceY;

            if (distanceToWater < 2.0) { // если менее 2 блоков от воды
                // определяем силу отталкивания
                double repulsionForce = Math.max(0.02, 0.05 * (2.0 - distanceToWater));

                Vec3 motion = this.getDeltaMovement();
                this.setDeltaMovement(motion.x, Math.max(0.0, motion.y + repulsionForce), motion.z);
                this.hasImpulse = true;
            }
        }
    }
    @Override
    public void travel(Vec3 travelVector) {
        // если в воде, двигаем вверх
        if (this.isInWater()) {
            // увеличиваем вертикальную скорость
            travelVector = new Vec3(travelVector.x, Math.max(0.05, travelVector.y + 0.05), travelVector.z);
        }

        super.travel(travelVector);

        // дополнительно после перемещения
        if (this.isInWater()) {
            // применяем восходящее движение
            this.move(MoverType.SELF, new Vec3(0, 0.05, 0));
        }
    }
    // переопределяем обнаружение контакта с водой
    @Override
    public boolean isInWater() {
        return super.isInWater();
    }
}