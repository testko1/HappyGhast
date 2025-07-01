package dev.nweaver.happyghastmod.client.model;

import net.minecraft.client.model.GhastModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class HappyGhastModel<T extends Entity> extends GhastModel<T> {

    public HappyGhastModel(ModelPart modelPart) {
        super(modelPart);
    }

    private static String createTentacleName(int index) {
        return "tentacle" + index;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshDefinition = new MeshDefinition();
        PartDefinition partDefinition = meshDefinition.getRoot();

        // тело гаста остаётся таким же как в оригинале
        partDefinition.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, -8.0F, -8.0F, 16.0F, 16.0F, 16.0F), PartPose.offset(0.0F, 17.6F, 0.0F));

        // используем тот же сид для рандома, чтобы x и z координаты тентаклей совпадали с оригиналом
        RandomSource randomSource = RandomSource.create(1660L);

        // массив новых длин для каждого тентакля (индивидуальные настройки)
        int[] tentacleLengths = {
                4, // тентакль 0
                5, // тентакль 1
                4, // тентакль 2
                5, // тентакль 3
                6, // тентакль 4
                5, // тентакль 5
                4, // тентакль 6
                5, // тентакль 7
                4  // тентакль 8
        };

        // создаём 9 тентаклей с уменьшенной высотой
        for(int i = 0; i < 9; ++i) {
            // следующие две строки такие же как в оригинале для сохранения позиций
            float f = (((float)(i % 3) - (float)(i / 3 % 2) * 0.5F + 0.25F) / 2.0F * 2.0F - 1.0F) * 5.0F;
            float f1 = ((float)(i / 3) / 2.0F * 2.0F - 1.0F) * 5.0F;

            // пропускаем вызов nextint для совместимости с оригиналом
            randomSource.nextInt(7);

            // используем предопределённую длину для этого тентакля
            int length = tentacleLengths[i];

            // создаём тентакль с новой длиной
            partDefinition.addOrReplaceChild(
                    createTentacleName(i),
                    CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, 0.0F, -1.0F, 2.0F, (float)length, 2.0F),
                    PartPose.offset(f, 24.6F, f1)
            );
        }

        return LayerDefinition.create(meshDefinition, 64, 32);
    }
}