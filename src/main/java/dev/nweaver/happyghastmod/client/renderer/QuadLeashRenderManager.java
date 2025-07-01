package dev.nweaver.happyghastmod.client.renderer;

import dev.nweaver.happyghastmod.entity.HappyGhast;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;

// менеджер рендера квадро-поводков, в том числе вне поля зрения
public class QuadLeashRenderManager {

    // обработанные в кадре гасты
    private static final Set<UUID> processedGhastsThisFrame = new HashSet<>();
    // отрендеренные в кадре пары
    private static final Set<String> renderedPairsThisFrame = new HashSet<>();
    // все известные пары гаст-цель
    private static final Set<String> knownQuadLeashPairs = new HashSet<>();
    // кэш активных пар для рендера
    private static final Map<UUID, List<UUID>> activeLeashPairs = new HashMap<>();
    // время последнего обновления кэша
    private static long lastFullCacheUpdate = 0;
    // интервал обновления кэша
    private static final long FULL_CACHE_UPDATE_INTERVAL = 2000; // 2 секунды
    // радиус поиска сущностей
    private static final double SEARCH_RADIUS = 512.0;
    // счетчик кадров
    private static int frameCounter = 0;
    // частота проверки всех гастов
    private static final int CHECK_ALL_GHASTS_INTERVAL = 50;


    // новый кадр: сброс и обновление кэша
    public static void startNewRenderFrame() {
        processedGhastsThisFrame.clear();
        renderedPairsThisFrame.clear();

        frameCounter++;

        if (frameCounter % 100 == 0) {
            cleanupEntityCache();
        }

        // проверка каждые 50 кадров
        if (Minecraft.getInstance().player != null && frameCounter % CHECK_ALL_GHASTS_INTERVAL == 0) {
            forceCheckAllGhasts();
        }

        // обновление кэша по интервалу
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastFullCacheUpdate > FULL_CACHE_UPDATE_INTERVAL) {
            updateGlobalCache();
            lastFullCacheUpdate = currentTime;
        }
    }

    // проверка, отрендерена ли пара в кадре
    public static boolean isPairRendered(UUID ghastUUID, UUID targetUUID) {
        if (ghastUUID == null || targetUUID == null) return false;
        String pairId = ghastUUID.toString() + "-" + targetUUID.toString();
        return renderedPairsThisFrame.contains(pairId);
    }

    // отметить пару как отрендеренную и добавить в кэш
    public static void markPairRendered(UUID ghastUUID, UUID targetUUID) {
        if (ghastUUID == null || targetUUID == null) return;

        String pairId = ghastUUID.toString() + "-" + targetUUID.toString();
        renderedPairsThisFrame.add(pairId);

        // добавить в известные пары
        knownQuadLeashPairs.add(pairId);

        // добавить в активные пары
        activeLeashPairs.computeIfAbsent(ghastUUID, k -> new ArrayList<>());
        if (!activeLeashPairs.get(ghastUUID).contains(targetUUID)) {
            activeLeashPairs.get(ghastUUID).add(targetUUID);
        }
    }

    // обновление глобального кэша
    private static void updateGlobalCache() {
        if (Minecraft.getInstance().level == null || Minecraft.getInstance().player == null) {
            return;
        }

        // сохраняем известные пары
        Set<String> allKnownPairs = new HashSet<>(knownQuadLeashPairs);

        // ищем новые пары от гастов
        Entity player = Minecraft.getInstance().player;
        AABB worldSearchBox = new AABB(
                player.getX() - SEARCH_RADIUS,
                player.getY() - SEARCH_RADIUS,
                player.getZ() - SEARCH_RADIUS,
                player.getX() + SEARCH_RADIUS,
                player.getY() + SEARCH_RADIUS,
                player.getZ() + SEARCH_RADIUS
        );

        // найти всех гастов
        List<HappyGhast> allGhasts = Minecraft.getInstance().level.getEntitiesOfClass(
                HappyGhast.class, worldSearchBox
        );

        // очистка активных пар
        activeLeashPairs.clear();

        // обработка каждого гаста
        for (HappyGhast ghast : allGhasts) {
            if (!ghast.isQuadLeashing()) continue;

            // получить целевые uuid
            List<UUID> targetUUIDs = new ArrayList<>(ghast.getQuadLeashedEntityUUIDs());

            // проверка легаси uuid для совместимости
            ghast.getQuadLeashedEntityUUID().ifPresent(uuid -> {
                if (!targetUUIDs.contains(uuid)) {
                    targetUUIDs.add(uuid);
                }
            });

            if (!targetUUIDs.isEmpty()) {
                // сохранить пары
                UUID ghastUUID = ghast.getUUID();
                activeLeashPairs.put(ghastUUID, targetUUIDs);

                for (UUID targetUUID : targetUUIDs) {
                    String pairId = ghastUUID.toString() + "-" + targetUUID.toString();
                    knownQuadLeashPairs.add(pairId);
                    allKnownPairs.add(pairId);
                }
            }
        }

        // добавить старые пары в активные
        for (String pairId : allKnownPairs) {
            String[] parts = pairId.split("-");
            if (parts.length == 2) {
                try {
                    UUID ghastUUID = UUID.fromString(parts[0]);
                    UUID targetUUID = UUID.fromString(parts[1]);

                    // проверка наличия сущностей
                    if (isEntityExist(ghastUUID, HappyGhast.class) &&
                            isAnyTargetEntityExist(targetUUID)) {
                        activeLeashPairs.computeIfAbsent(ghastUUID, k -> new ArrayList<>());
                        if (!activeLeashPairs.get(ghastUUID).contains(targetUUID)) {
                            activeLeashPairs.get(ghastUUID).add(targetUUID);
                        }
                    }
                } catch (IllegalArgumentException e) {
                    // игнорирование некорректных uuid
                }
            }
        }
    }

    // проверка существования сущности по uuid и типу
    private static <T extends Entity> boolean isEntityExist(UUID entityUUID, Class<T> entityClass) {
        if (Minecraft.getInstance().level == null || entityUUID == null) {
            return false;
        }

        AABB worldSearchBox = Minecraft.getInstance().player.getBoundingBox().inflate(SEARCH_RADIUS);
        List<T> entities = Minecraft.getInstance().level.getEntitiesOfClass(
                entityClass, worldSearchBox, entity -> entity.getUUID().equals(entityUUID)
        );

        return !entities.isEmpty();
    }

    // проверка существования цели по uuid
    private static boolean isAnyTargetEntityExist(UUID entityUUID) {
        if (Minecraft.getInstance().level == null || entityUUID == null) {
            return false;
        }

        AABB worldSearchBox = Minecraft.getInstance().player.getBoundingBox().inflate(SEARCH_RADIUS);

        // проверка лодок
        List<Boat> boats = Minecraft.getInstance().level.getEntitiesOfClass(
                Boat.class, worldSearchBox, entity -> entity.getUUID().equals(entityUUID)
        );
        if (!boats.isEmpty()) return true;

        // проверка лошадей
        List<AbstractHorse> horses = Minecraft.getInstance().level.getEntitiesOfClass(
                AbstractHorse.class, worldSearchBox, entity -> entity.getUUID().equals(entityUUID)
        );
        if (!horses.isEmpty()) return true;

        // проверка верблюдов
        List<Camel> camels = Minecraft.getInstance().level.getEntitiesOfClass(
                Camel.class, worldSearchBox, entity -> entity.getUUID().equals(entityUUID)
        );
        if (!camels.isEmpty()) return true;

        // проверка нюхачей
        List<Sniffer> sniffers = Minecraft.getInstance().level.getEntitiesOfClass(
                Sniffer.class, worldSearchBox, entity -> entity.getUUID().equals(entityUUID)
        );

        return !sniffers.isEmpty();
    }

    // принудительная проверка всех привязей у гастов
    public static void forceCheckAllGhasts() {
        if (Minecraft.getInstance().level == null || Minecraft.getInstance().player == null) return;

        // ищем всех гастов в радиусе
        List<Entity> ghasts = Minecraft.getInstance().level.getEntities(Minecraft.getInstance().player,
                Minecraft.getInstance().player.getBoundingBox().inflate(SEARCH_RADIUS),
                entity -> entity instanceof HappyGhast);

        ghasts.forEach(entity -> {
            if (entity instanceof HappyGhast ghast) {
                // получаем айди привязанных сущностей
                List<UUID> targetUUIDs = ghast.getQuadLeashedEntityUUIDs();

                // также проверяем легаси uuid
                ghast.getQuadLeashedEntityUUID().ifPresent(uuid -> {
                    if (!targetUUIDs.contains(uuid)) {
                        targetUUIDs.add(uuid);
                    }
                });

                int count = targetUUIDs.size();

                if (count > 0) {
                    // добавить в активные пары
                    UUID ghastUUID = ghast.getUUID();
                    activeLeashPairs.put(ghastUUID, new ArrayList<>(targetUUIDs));

                    // предзагрузка целей для корректного рендера
                    for (UUID targetUUID : targetUUIDs) {
                        // добавляем пару в известные, даже если цель не найдена
                        String pairId = ghastUUID.toString() + "-" + targetUUID.toString();
                        knownQuadLeashPairs.add(pairId);

                        // прогрев кэша, ищем цели поблизости
                        Minecraft.getInstance().level.getEntitiesOfClass(
                                Entity.class,
                                Minecraft.getInstance().player.getBoundingBox().inflate(SEARCH_RADIUS),
                                e -> e.getUUID().equals(targetUUID) &&
                                        (e instanceof Boat || e instanceof AbstractHorse ||
                                                e instanceof Camel || e instanceof Sniffer)
                        );
                    }
                }
            }
        });

        // также предзагружаем потенциальные цели
        AABB worldSearchBox = new AABB(
                Minecraft.getInstance().player.getX() - SEARCH_RADIUS,
                Minecraft.getInstance().player.getY() - SEARCH_RADIUS,
                Minecraft.getInstance().player.getZ() - SEARCH_RADIUS,
                Minecraft.getInstance().player.getX() + SEARCH_RADIUS,
                Minecraft.getInstance().player.getY() + SEARCH_RADIUS,
                Minecraft.getInstance().player.getZ() + SEARCH_RADIUS
        );

        // ищем всех, кого можно привязать
        Minecraft.getInstance().level.getEntitiesOfClass(
                Entity.class,
                worldSearchBox,
                e -> e instanceof Boat || e instanceof AbstractHorse ||
                        e instanceof Camel || e instanceof Sniffer
        );
    }

    private static class EntityRenderCache {
        public UUID entityUUID;
        public double lastX;
        public double lastY;
        public double lastZ;
        public float lastYaw;
        public float lastYawBody;
        public long lastUpdateTime;

        public EntityRenderCache(UUID uuid, double x, double y, double z, float yaw, float yawBody) {
            entityUUID = uuid;
            lastX = x;
            lastY = y;
            lastZ = z;
            lastYaw = yaw;
            lastYawBody = yawBody;
            lastUpdateTime = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - lastUpdateTime > 5000; // удаляем данные через 5 сек неактивности
        }
    }

    private static final Map<UUID, EntityRenderCache> entityRenderCache = new HashMap<>();

    // обновление кэша сущности для сглаживания рендера
    public static EntityRenderCache updateEntityCache(Entity entity) {
        UUID uuid = entity.getUUID();

        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();
        float yaw = entity.getYRot();
        float yawBody = (entity instanceof LivingEntity) ? ((LivingEntity)entity).yBodyRot : yaw;

        EntityRenderCache cache = entityRenderCache.get(uuid);
        if (cache == null) {
            cache = new EntityRenderCache(uuid, x, y, z, yaw, yawBody);
            entityRenderCache.put(uuid, cache);
        } else {
            // обновить данные
            cache.lastX = x;
            cache.lastY = y;
            cache.lastZ = z;
            cache.lastYaw = yaw;
            cache.lastYawBody = yawBody;
            cache.lastUpdateTime = System.currentTimeMillis();
        }

        return cache;
    }


    // очистка устаревшего кэша
    private static void cleanupEntityCache() {
        Set<UUID> toRemove = new HashSet<>();
        for (Map.Entry<UUID, EntityRenderCache> entry : entityRenderCache.entrySet()) {
            if (entry.getValue().isExpired()) {
                toRemove.add(entry.getKey());
            }
        }

        for (UUID uuid : toRemove) {
            entityRenderCache.remove(uuid);
        }
    }
}