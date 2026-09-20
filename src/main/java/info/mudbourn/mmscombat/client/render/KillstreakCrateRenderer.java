package info.mudbourn.mmscombat.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import info.mudbourn.mmscombat.MmsCombat;
import info.mudbourn.mmscombat.killstreak.KillstreakCrateEntity;
import info.mudbourn.mmsrendercommon.client.geo.GeoModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

// Draws the killstreak crate from its ported Bedrock geo, bobbing gently and turned to the owner's facing.
public class KillstreakCrateRenderer extends EntityRenderer<KillstreakCrateEntity, KillstreakCrateRenderer.State> {

    private static final Identifier GEO =
        Identifier.fromNamespaceAndPath(MmsCombat.MOD_ID, "geo/killstreak_crate.geo.json");
    private static final Identifier TEXTURE =
        Identifier.fromNamespaceAndPath(MmsCombat.MOD_ID, "textures/entity/killstreak_crate.png");

    public KillstreakCrateRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.6F;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(KillstreakCrateEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.yaw = entity.getYRot();
        state.age = entity.tickCount + partialTick;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        super.submit(state, poseStack, collector, camera);
        GeoModel model = GeoModel.load(GEO);
        if (model == null) {
            return;
        }
        float bob = Mth.sin(state.age * 0.08F) * 0.06F;
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.4F + bob, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.yaw));
        RenderType renderType = RenderType.entityCutoutNoCull(TEXTURE);
        collector.submitCustomGeometry(poseStack, renderType, (pose, consumer) ->
            model.draw(pose, consumer, state.lightCoords, OverlayTexture.NO_OVERLAY, -1));
        poseStack.popPose();
    }

    public static class State extends EntityRenderState {
        public float yaw;
        public float age;
    }
}
