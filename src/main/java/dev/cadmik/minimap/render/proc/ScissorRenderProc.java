package dev.cadmik.minimap.render.proc;

import dev.cadmik.minimap.render.ChunkAtlas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/**
 * A scissor-based map rendering procedure.
 *
 * <p> Used as a fallback when stencil-based rendering is unavailable.
 */
public class ScissorRenderProc extends MapRenderProc {
    /**
     * {@inheritDoc}
     */
    @Override
    public void render(double screenX, double screenY, double camX, double camZ, double yaw) {
        int windowDiag = (ChunkAtlas.getInstance().getChunkRadius() - 1) << 4;
        float windowRadius = (float) Math.sqrt(windowDiag * windowDiag >> 1);

        // Would be a ton better if this was just a field in Minecraft.class.
        ScaledResolution res = new ScaledResolution(Minecraft.getMinecraft());

        GlStateManager.disableAlpha();
        GlStateManager.disableBlend();

        GL11.glPushMatrix();
        GL11.glTranslated(screenX, screenY, 0);

        this.renderBorder(windowRadius + 4);

        GL11.glPushMatrix();
        GL11.glRotated(180 - yaw, 0, 0, 1);

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(
                (int) (screenX - windowRadius) * res.getScaleFactor(),
                (int) (res.getScaledHeight() - screenY - windowRadius) * res.getScaleFactor(),
                (int) (windowRadius * res.getScaleFactor() * 2),
                (int) (windowRadius * res.getScaleFactor() * 2)
        );

        this.renderChunks(camX, camZ);

        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        GL11.glPopMatrix();

        this.renderCursor();

        double cardinalDist = getCardinalDist(windowRadius + 4, yaw);

        this.renderCardinal('N', cardinalDist, yaw);
        this.renderCardinal('W', cardinalDist, yaw + 90);
        this.renderCardinal('S', cardinalDist, yaw + 180);
        this.renderCardinal('E', cardinalDist, yaw + 270);

        GL11.glPopMatrix();
    }

    private void renderBorder(double radius) {
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();

        GlStateManager.color(1 / 3.f, 1 / 3.f, 1 / 3.f);
        GlStateManager.disableTexture2D();

        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
        wr.pos(radius, -radius, 0).endVertex();
        wr.pos(-radius, -radius, 0).endVertex();
        wr.pos(-radius, radius, 0).endVertex();
        wr.pos(radius, radius, 0).endVertex();

        tess.draw();
    }

    /**
     * Calculates the distance of cardinals from center depending on angle.
     *
     * <p> GL11::glScissor cannot be rotated, and text popping off the map
     * border looks bad, so this method allows the text to remain attached to
     * the border.
     *
     * @param windowRadius Distance from middle of map assuming no rotation.
     * @param angle        Viewing angle of minimap.
     * @return Adjusted cardinal rendering distance.
     */
    private static double getCardinalDist(double windowRadius, double angle) {
        while (angle < 0) {
            angle += 360;
        }

        angle %= 90;
        if (angle > 45) {
            angle = 90 - angle;
        }

        double cardinalDist = Math.tan(Math.toRadians(angle));
        cardinalDist *= cardinalDist;
        cardinalDist += 1;
        cardinalDist *= windowRadius * windowRadius;

        return Math.sqrt(cardinalDist);
    }
}
