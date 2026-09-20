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
import net.minecraft.client.renderer.rendertype.RenderTypes;
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
    private static final Identifier PARACHUTE_GEO =
        Identifier.fromNamespaceAndPath(MmsCombat.MOD_ID, "geo/killstreak_parachute.geo.json");
    private static final Identifier PARACHUTE_TEXTURE =
        Identifier.fromNamespaceAndPath(MmsCombat.MOD_ID, "textures/entity/killstreak_parachute.png");
    // Fits the tall create_parachute canopy above the small crate.
    private static final float PARACHUTE_SCALE = 0.35F;
    // Blocks the crate rises through as it fades in, and again as it fades out.
    private static final float RISE = 0.6F;

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
        float appear = smoothstep(entity.appearProgress(partialTick));
        float despawn = smoothstep(entity.despawnProgress(partialTick));
        state.alpha = appear * (1.0F - despawn);
        state.animOffset = (appear - 1.0F) * RISE + despawn * RISE;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        super.submit(state, poseStack, collector, camera);
        if (state.alpha <= 0.0F) {
            return;
        }
        GeoModel crate = GeoModel.load(GEO);
        if (crate == null) {
            return;
        }
        int alpha = Mth.floor(Mth.clamp(state.alpha, 0.0F, 1.0F) * 255.0F) << 24;
        float bob = Mth.sin(state.age * 0.08F) * 0.06F;
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.4F + bob + state.animOffset, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.yaw));
        RenderType crateType = RenderTypes.entityTranslucent(TEXTURE);
        collector.submitCustomGeometry(poseStack, crateType, (pose, consumer) ->
            crate.draw(pose, consumer, state.lightCoords, OverlayTexture.NO_OVERLAY, alpha | 0x00FFFFFF));
        GeoModel parachute = GeoModel.load(PARACHUTE_GEO);
        if (parachute != null) {
            poseStack.scale(PARACHUTE_SCALE, PARACHUTE_SCALE, PARACHUTE_SCALE);
            RenderType parachuteType = RenderTypes.entityTranslucent(PARACHUTE_TEXTURE);
            collector.submitCustomGeometry(poseStack, parachuteType, (pose, consumer) ->
                parachute.draw(pose, consumer, state.lightCoords, OverlayTexture.NO_OVERLAY, alpha | 0x00FFFFFF));
        }
        poseStack.popPose();
    }

    // Eases a linear 0..1 progress into a soft start and stop so the fade and tween settle instead of snapping.
    private static float smoothstep(float t) {
        return t * t * (3.0F - 2.0F * t);
    }

    public static class State extends EntityRenderState {
        public float yaw;
        public float age;
        public float alpha;
        public float animOffset;
    }
}
