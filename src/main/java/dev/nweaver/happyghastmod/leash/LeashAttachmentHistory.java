package dev.nweaver.happyghastmod.leash;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedList;
import java.util.Queue;

// класс для хранения и обработки истории точек привязки поводка
// с продвинутым сглаживанием для плавной анимации
public class LeashAttachmentHistory {
    // храним несколько последних точек для продвинутого сглаживания
    private final Queue<Vec3> recentPoints = new LinkedList<>();

    // максимальное количество точек в истории
    private static final int MAX_HISTORY_SIZE = 10;

    // последняя вычисленная сглаженная точка
    private Vec3 smoothedPoint = null;

    // время последнего обновления
    private long lastUpdateTime = 0;

    // добавляет новую точку в историю
    public void addPoint(Vec3 newPoint) {
        recentPoints.add(newPoint);
        if (recentPoints.size() > MAX_HISTORY_SIZE) {
            recentPoints.poll(); // удаляем самую старую точку
        }
        lastUpdateTime = System.currentTimeMillis();
    }

    // получает сглаженную точку на основе истории
    public Vec3 getSmoothedPoint(Vec3 currentPoint) {
        // если история пуста, используем текущую точку
        if (recentPoints.isEmpty()) {
            smoothedPoint = currentPoint;
            return currentPoint;
        }

        // проверяем, не прошло ли слишком много времени с последнего обновления
        long currentTime = System.currentTimeMillis();
        boolean isStale = currentTime - lastUpdateTime > 500; // 500 мс

        if (isStale) {
            // если данные устарели, сбрасываем историю и начинаем заново
            recentPoints.clear();
            smoothedPoint = currentPoint;
            lastUpdateTime = currentTime;
            return currentPoint;
        }

        // применяем двойное сглаживание
        if (smoothedPoint == null) {
            // если это первая точка, просто используем среднее
            double avgX = recentPoints.stream().mapToDouble(vec -> vec.x).average().orElse(currentPoint.x);
            double avgY = recentPoints.stream().mapToDouble(vec -> vec.y).average().orElse(currentPoint.y);
            double avgZ = recentPoints.stream().mapToDouble(vec -> vec.z).average().orElse(currentPoint.z);
            smoothedPoint = new Vec3(avgX, avgY, avgZ);
        } else {
            // иначе применяем сглаживание с очень низким фактором
            double alpha = 0.02; // крайне низкий фактор сглаживания для максимальной плавности

            // находим среднюю точку
            double avgX = recentPoints.stream().mapToDouble(vec -> vec.x).average().orElse(currentPoint.x);
            double avgY = recentPoints.stream().mapToDouble(vec -> vec.y).average().orElse(currentPoint.y);
            double avgZ = recentPoints.stream().mapToDouble(vec -> vec.z).average().orElse(currentPoint.z);

            // двойное сглаживание
            Vec3 intermediateSmooth = new Vec3(
                    Mth.lerp(0.3, currentPoint.x, avgX),
                    Mth.lerp(0.3, currentPoint.y, avgY),
                    Mth.lerp(0.3, currentPoint.z, avgZ)
            );

            // финальное сглаживание с очень маленьким фактором для предельной плавности
            smoothedPoint = new Vec3(
                    Mth.lerp(alpha, smoothedPoint.x, intermediateSmooth.x),
                    Mth.lerp(alpha, smoothedPoint.y, intermediateSmooth.y),
                    Mth.lerp(alpha, smoothedPoint.z, intermediateSmooth.z)
            );
        }

        return smoothedPoint;
    }
}