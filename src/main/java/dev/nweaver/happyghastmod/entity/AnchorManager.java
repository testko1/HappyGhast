package dev.nweaver.happyghastmod.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// класс для управления точками привязки сущностей
// ограничивает перемещение гастов определенным радиусом от точки привязки
public class AnchorManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Map<UUID, Vec3> anchors = new HashMap<>();

    // хранение изначальных высот спавна для каждой сущности
    private static final Map<UUID, Double> entitySpawnHeights = new HashMap<>();

    // хранение данных о режиме следования
    private static final Map<UUID, Long> followingTimestamps = new HashMap<>();
    private static final Map<UUID, Vec3> lastPlayerPositions = new HashMap<>();
    private static final Map<UUID, Vec3> movementVectors = new HashMap<>();

    // константы для настройки поведения
    private static final long FOLLOWING_TIMEOUT = 3000; // 3 секунды для режима следования

    // увеличиваем вертикальный диапазон для лучшего следования
    private static final double DEFAULT_VERTICAL_MOVEMENT_RANGE = 40.0D; // увеличено с 300d

    private static final Map<UUID, Long> lastAnchorUpdateTime = new HashMap<>();
    // уменьшаем время между обновлениями для более плавного следования
    private static final long ANCHOR_UPDATE_COOLDOWN = 1500; // уменьшено с 3000 мс

    // отслеживание последних якорей для более плавного перемещения
    private static final Map<UUID, Vec3> lastUpdatedAnchors = new HashMap<>();

    // устанавливает точку привязки для сущности
    public static void setAnchor(Entity entity, double x, double y, double z) {
        UUID entityId = entity.getUUID();
        Vec3 newAnchor = new Vec3(x, y, z);

        // сохраняем предыдущий якорь для более плавного перехода
        if (anchors.containsKey(entityId)) {
            lastUpdatedAnchors.put(entityId, anchors.get(entityId));
        }

        anchors.put(entityId, newAnchor);

        // если это первая установка якоря, сохраняем высоту как высоту спавна
        if (!entitySpawnHeights.containsKey(entityId)) {
            entitySpawnHeights.put(entityId, y);

            // также обновляем поле spawnheight в happyghast, если это возможно
            if (entity instanceof HappyGhast) {
                HappyGhast happyGhast = (HappyGhast) entity;
                try {
                    java.lang.reflect.Field spawnHeightField = HappyGhast.class.getDeclaredField("spawnHeight");
                    java.lang.reflect.Field initializedField = HappyGhast.class.getDeclaredField("hasInitializedSpawnHeight");

                    spawnHeightField.setAccessible(true);
                    initializedField.setAccessible(true);

                    spawnHeightField.set(happyGhast, y);
                    initializedField.set(happyGhast, true);
                } catch (Exception e) {
                    // если не удалась, просто продолжаем
                    LOGGER.debug("Could not set spawnHeight field in HappyGhast: {}", e.getMessage());
                }
            }
        }

        //LOGGER.debug("Set anchor for entity {} at {}, {}, {}", entityId, x, y, z);
    }

    // устанавливает точку привязки для сущности с учетом кулдауна
    public static boolean setAnchorWithCooldown(Entity entity, double x, double y, double z) {
        UUID entityId = entity.getUUID();
        long currentTime = System.currentTimeMillis();
        Long lastUpdate = lastAnchorUpdateTime.get(entityId);

        // проверяем, прошло ли достаточно времени с последнего обновления
        if (lastUpdate != null && currentTime - lastUpdate < ANCHOR_UPDATE_COOLDOWN) {
            // еще не прошло достаточно времени, пропускаем обновление
            return false;
        }

        // обновляем якорь и время последнего обновления
        setAnchor(entity, x, y, z);
        lastAnchorUpdateTime.put(entityId, currentTime);
        return true;
    }

    public static boolean canUpdateAnchor(Entity entity) {
        UUID entityId = entity.getUUID();
        long currentTime = System.currentTimeMillis();
        Long lastUpdate = lastAnchorUpdateTime.get(entityId);

        return lastUpdate == null || currentTime - lastUpdate >= ANCHOR_UPDATE_COOLDOWN;
    }

    // проверяет, имеет ли сущность точку привязки
    public static boolean hasAnchor(Entity entity) {
        return anchors.containsKey(entity.getUUID());
    }

    // возвращает точку привязки для сущности или null, если точка не установлена
    public static Vec3 getAnchor(Entity entity) {
        return anchors.get(entity.getUUID());
    }

    // получает начальную высоту спавна сущности
    public static double getSpawnHeight(Entity entity) {
        UUID entityId = entity.getUUID();
        Double spawnHeight = entitySpawnHeights.get(entityId);

        // если нет сохраненной высоты, используем текущую высоту сущности
        if (spawnHeight == null) {
            // проверяем, можем ли получить высоту из happyghast
            if (entity instanceof HappyGhast) {
                HappyGhast happyGhast = (HappyGhast) entity;
                try {
                    java.lang.reflect.Field spawnHeightField = HappyGhast.class.getDeclaredField("spawnHeight");
                    java.lang.reflect.Field initializedField = HappyGhast.class.getDeclaredField("hasInitializedSpawnHeight");

                    spawnHeightField.setAccessible(true);
                    initializedField.setAccessible(true);

                    boolean initialized = (boolean) initializedField.get(happyGhast);

                    if (initialized) {
                        spawnHeight = (double) spawnHeightField.get(happyGhast);
                        entitySpawnHeights.put(entityId, spawnHeight);
                    } else {
                        spawnHeight = entity.getY();
                        entitySpawnHeights.put(entityId, spawnHeight);

                        // обновляем поле spawnheight в happyghast
                        spawnHeightField.set(happyGhast, spawnHeight);
                        initializedField.set(happyGhast, true);
                    }
                } catch (Exception e) {
                    // если не удалась, используем текущую высоту
                    spawnHeight = entity.getY();
                    entitySpawnHeights.put(entityId, spawnHeight);
                }
            } else {
                spawnHeight = entity.getY();
                entitySpawnHeights.put(entityId, spawnHeight);
            }
        }

        return spawnHeight;
    }

    // удаляет точку привязки для сущности
    public static void removeAnchor(Entity entity) {
        UUID entityId = entity.getUUID();
        anchors.remove(entityId);
        lastUpdatedAnchors.remove(entityId);
        // оставляем высоту спавна для возможного повторного использования
    }

    // отмечает, что гаст следует за игроком в данный момент
    public static void markAsFollowing(Entity entity) {
        followingTimestamps.put(entity.getUUID(), System.currentTimeMillis());
    }

    // останавливает режим следования для сущности
    public static void stopFollowing(Entity entity) {
        followingTimestamps.remove(entity.getUUID());
        // сохраняем текущий вектор движения для плавного замедления
        if (entity.getDeltaMovement().lengthSqr() > 0.01) {
            movementVectors.put(entity.getUUID(), entity.getDeltaMovement());
        }
    }

    // проверяет, следует ли гаст за игроком в данный момент
    public static boolean isFollowing(Entity entity) {
        Long timestamp = followingTimestamps.get(entity.getUUID());
        if (timestamp == null) {
            return false;
        }

        // проверяем таймаут
        return System.currentTimeMillis() - timestamp < FOLLOWING_TIMEOUT;
    }

    // обновляет последнюю известную позицию игрока для гаста
    public static void updateLastPlayerPosition(Entity entity, double x, double y, double z) {
        lastPlayerPositions.put(entity.getUUID(), new Vec3(x, y, z));
    }

    // получает последнюю известную позицию игрока для гаста
    public static Vec3 getLastPlayerPosition(Entity entity) {
        return lastPlayerPositions.get(entity.getUUID());
    }

    // возвращает максимальный радиус горизонтального перемещения от точки привязки
    public static double getMaxRadius(Entity entity) {
        // если гаст следует за игроком, увеличиваем радиус
        if (isFollowing(entity)) {
            return 64.0D; // увеличенный радиус для следования
        }
        // проверяем, является ли сущность ghastling
        if (entity instanceof Ghastling) {
            return 32.0D; // жесткое ограничение для ghastling в 32 блока
        }

        // проверяем, является ли сущность happyghast
        if (entity instanceof HappyGhast) {
            HappyGhast happyGhast = (HappyGhast) entity;
            return happyGhast.isSaddled() ? 32.0D : 64.0D;
        }
        // для других типов сущностей возвращаем стандартный радиус
        return 64.0D;
    }

    // возвращает максимальное отклонение по высоте вверх
    public static double getMaxHeightOffset(Entity entity) {
        // если гаст следует за игроком, используем увеличенный диапазон
        if (isFollowing(entity)) {
            return DEFAULT_VERTICAL_MOVEMENT_RANGE + 40.0; // значительное увеличение для следования
        }

        return DEFAULT_VERTICAL_MOVEMENT_RANGE;
    }

    // возвращает максимальное отклонение по высоте вниз
    public static double getMinHeightOffset(Entity entity) {
        // если гаст следует за игроком, используем увеличенный диапазон
        if (isFollowing(entity)) {
            return -DEFAULT_VERTICAL_MOVEMENT_RANGE - 40.0; // значительное увеличение для следования вниз (было -20)
        }

        return -DEFAULT_VERTICAL_MOVEMENT_RANGE;
    }

    // проверяет, находится ли точка в пределах допустимого радиуса от точки привязки
    public static boolean isWithinAllowedRadius(Entity entity, double x, double y, double z) {
        // если гаст следует за игроком, разрешаем перемещение
        if (isFollowing(entity)) {
            return true;
        }

        if (!hasAnchor(entity)) {
            return true;
        }

        Vec3 anchor = getAnchor(entity);
        double maxRadius = getMaxRadius(entity);

        // проверяем горизонтальное расстояние
        double dx = x - anchor.x;
        double dz = z - anchor.z;
        double horizontalDistSq = dx * dx + dz * dz;

        // проверяем вертикальное расстояние относительно якоря, а не высоты спавна
        double dy = y - anchor.y;
        boolean withinHorizontalRadius = horizontalDistSq <= maxRadius * maxRadius;

        // используем увеличенный вертикальный диапазон относительно якоря
        double maxVertOffset = getMaxHeightOffset(entity);
        double minVertOffset = getMinHeightOffset(entity);
        boolean withinVerticalRange = dy >= minVertOffset && dy <= maxVertOffset;

        return withinHorizontalRadius && withinVerticalRange;
    }

    // создает временный якорь - промежуточную точку между текущей позицией и якорем
    // которая позволяет гасту плавно перемещаться за пределы исходного радиуса
    public static boolean createTemporaryAnchor(Entity entity, double playerX, double playerY, double playerZ) {
        // проверяем кулдаун
        if (!canUpdateAnchor(entity)) {
            return false;
        }

        if (!hasAnchor(entity)) {
            return setAnchorWithCooldown(entity, entity.getX(), entity.getY(), entity.getZ());
        }

        Vec3 anchor = getAnchor(entity);
        // сохраняем текущую высоту спавна
        double currentSpawnHeight = getSpawnHeight(entity);

        // вычисляем вектор от якоря к игроку
        double dx = playerX - anchor.x;
        double dz = playerZ - anchor.z;
        double dy = playerY - anchor.y; // добавляем вертикальное смещение
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        double verticalDist = Math.abs(dy);

        // если расстояние небольшое, не меняем якорь
        // более агрессивное обновление якоря - меньший порог для запуска обновления
        if (horizontalDist < getMaxRadius(entity) * 0.5 &&
                verticalDist < getMaxHeightOffset(entity) * 0.5) {
            return false;
        }

        // используем одинаковую скорость как для подъема, так и для спуска
        double verticalScale = 0.7; // унифицированная скорость для обоих направлений движения
        double horizontalScale = 0.3; // стандартное горизонтальное следование

        // оставляем небольшое преимущество для движения вниз, чтобы избежать бага с остановкой
        if (dy < 0) {
            verticalScale = 0.75; // чуть быстрее для спуска, но разница минимальна
        }

        // вычисляем новое положение якоря
        double newX = anchor.x + dx * horizontalScale;
        double newZ = anchor.z + dz * horizontalScale;

        // обновляем вертикальную позицию якоря, если сущность следует за игроком
        double newY;
        if (isFollowing(entity)) {
            // используем разную скорость для вертикального и горизонтального следования
            newY = anchor.y + dy * verticalScale;

            // защита для суперплоских миров
            double minWorldHeight = entity.level().getMinBuildHeight() + 5.0;
            if (newY < minWorldHeight) {
                newY = minWorldHeight;
            }
        } else {
            // если не в режиме следования, сохраняем якорь на той же высоте
            newY = anchor.y;
        }

        // устанавливаем новый якорь с учетом кулдауна
        boolean updated = setAnchorWithCooldown(entity, newX, newY, newZ);
        if (updated) {
            // если не в режиме следования, сохраняем оригинальную высоту спавна
            if (!isFollowing(entity)) {
                entitySpawnHeights.put(entity.getUUID(), currentSpawnHeight);

                // обновляем поле spawnheight в HappyGhast
                if (entity instanceof HappyGhast) {
                    HappyGhast happyGhast = (HappyGhast) entity;
                    try {
                        java.lang.reflect.Field spawnHeightField = HappyGhast.class.getDeclaredField("spawnHeight");
                        spawnHeightField.setAccessible(true);
                        spawnHeightField.set(happyGhast, currentSpawnHeight);
                    } catch (Exception e) {
                        // если не удалась, просто продолжаем
                    }
                }
            } else {
                // в режиме следования обновляем высоту спавна на новую высоту якоря
                entitySpawnHeights.put(entity.getUUID(), newY);

                // обновляем поле spawnheight в happyghast для режима следования
                if (entity instanceof HappyGhast) {
                    HappyGhast happyGhast = (HappyGhast) entity;
                    try {
                        java.lang.reflect.Field spawnHeightField = HappyGhast.class.getDeclaredField("spawnHeight");
                        spawnHeightField.setAccessible(true);
                        spawnHeightField.set(happyGhast, newY);
                    } catch (Exception e) {
                        // если не удалась, просто продолжаем
                    }
                }
            }

            LOGGER.debug("Created temporary anchor at {}, {}, {} (spawn height updated: {})",
                    newX, newY, newZ, isFollowing(entity));
        }
        return updated;
    }

    // возвращает вектор замедления для плавной остановки
    // после выхода из режима следования
    public static Vec3 getDecelerationVector(Entity entity) {
        Vec3 vector = movementVectors.get(entity.getUUID());
        if (vector == null) {
            return Vec3.ZERO;
        }

        // постепенно уменьшаем скорость
        double deceleration = 0.8; // коэффициент замедления
        Vec3 newVector = vector.scale(deceleration);

        // если скорость стала очень малой, убираем вектор
        if (newVector.lengthSqr() < 0.001) {
            movementVectors.remove(entity.getUUID());
            return Vec3.ZERO;
        }

        // сохраняем уменьшенный вектор для следующей итерации
        movementVectors.put(entity.getUUID(), newVector);
        return newVector;
    }
}