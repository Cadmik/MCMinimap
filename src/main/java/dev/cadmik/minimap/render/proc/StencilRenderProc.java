package dev.cadmik.minimap.render.proc;

import dev.cadmik.minimap.render.ChunkAtlas;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/**
 * A stencil-based map rendering procedure.
 *
 * <p> This is used by default and should be available on most computers.
 */
public class StencilRenderProc extends MapRenderProc {
    /**
     * {@inheritDoc}
     */
    @Override
    public void render(double screenX, double screenY, double camX, double camZ, double yaw) {
        int windowRadius = (ChunkAtlas.getInstance().getChunkRadius() - 1) << 4;

        GlStateManager.disableAlpha();
        GlStateManager.disableBlend();

        GL11.glPushMatrix();
        GL11.glTranslated(screenX, screenY, 0);

        GL11.glPushMatrix();
        GL11.glRotated(180 - yaw, 0, 0, 1);

        this.renderBorder(windowRadius + 4);

        GlStateManager.colorMask(false, false, false, false);

        GL11.glStencilMask(0xff);
        GL11.glClearStencil(0);
        GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GL11.glStencilFunc(GL11.GL_ALWAYS, 1, 0xff);
        GL11.glStencilOp(GL11.GL_REPLACE, GL11.GL_REPLACE, GL11.GL_REPLACE);

        this.renderBorder(windowRadius);

        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        GL11.glStencilFunc(GL11.GL_EQUAL, 1, 0xff);
        GlStateManager.colorMask(true, true, true, true);

        this.renderChunks(camX, camZ);

        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glPopMatrix();

        this.renderCursor();

        windowRadius += 4;

        this.renderCardinal('N', windowRadius, yaw);
        this.renderCardinal('W', windowRadius, yaw + 90);
        this.renderCardinal('S', windowRadius, yaw + 180);
        this.renderCardinal('E', windowRadius, yaw + 270);

        GL11.glPopMatrix();
    }

    private void renderBorder(int radius) {
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();

        GlStateManager.color(1 / 3.f, 1 / 3.f, 1 / 3.f);
        GlStateManager.disableTexture2D();

        wr.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION);

        wr.pos(0, 0, 0).endVertex();
        for (int i = 0; i < 8; i++) {
            double ang = 2 * Math.PI * i / 8;
            wr.pos(-radius * Math.cos(ang), radius * Math.sin(ang), 0).endVertex();
        }

        wr.pos(-radius, 0, 0).endVertex();

        tess.draw();
    }
}
