package dev.cadmik.minimap.render.proc;

import dev.cadmik.minimap.render.ChunkAtlas;
import dev.cadmik.minimap.render.ChunkTile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/**
 * The base map rendering procedure implementation. Contains rendering methods
 * commonly used within all implementations.
 */
public abstract class MapRenderProc {
    /**
     * Renders all minimap components at respective screen coordinates.
     *
     * @param screenX X coordinate of minimap middle.
     * @param screenY Y coordinate of minimap middle.
     * @param camX    Player's world X coordinate.
     * @param camZ    Player's world Z coordinate.
     * @param yaw     Player's horizontal viewing angle.
     */
    public abstract void render(double screenX, double screenY, double camX, double camZ, double yaw);

    /**
     * Renders all available chunks stored in ChunkAtlas.
     *
     * @param x Player's world X coordinate.
     * @param z Player's world Z coordinate.
     */
    protected void renderChunks(double x, double z) {
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        ChunkAtlas atlas = ChunkAtlas.getInstance();

        GlStateManager.color(1, 1, 1);
        GlStateManager.enableTexture2D();
        GlStateManager.bindTexture(atlas.getTextureHandle());

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);

        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);

        double chunkWidth = atlas.getSpriteWidth();
        double chunkHeight = atlas.getSpriteHeight();

        for (ChunkTile sprite : atlas) {
            double minX = atlas.getSpriteX(sprite.getOffset());
            double minY = atlas.getSpriteY(sprite.getOffset());

            double maxX = minX + chunkWidth;
            double maxY = minY + chunkHeight;

            double renderX = (sprite.getChunkX() << 4) - x;
            double renderY = (sprite.getChunkZ() << 4) - z;

            wr.pos(renderX, renderY, 0).tex(minX, minY).endVertex();
            wr.pos(renderX, renderY + 16, 0).tex(minX, maxY).endVertex();
            wr.pos(renderX + 16, renderY + 16, 0).tex(maxX, maxY).endVertex();
            wr.pos(renderX + 16, renderY + 0, 0).tex(maxX, minY).endVertex();
        }

        tess.draw();
    }

    /**
     * Renders player's current position, in the middle of the map.
     */
    protected void renderCursor() {
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();

        GlStateManager.color(0, 0, 0);
        GlStateManager.disableTexture2D();

        wr.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION);
        wr.pos(0, -8, 0).endVertex();
        wr.pos(-3, 1, 0).endVertex();
        wr.pos(0, 0, 0).endVertex();
        wr.pos(3, 1, 0).endVertex();

        tess.draw();
    }

    /**
     * Renders the specified cardinal direction.
     *
     * @param c      Cardinal letter.
     * @param radius Distance from middle of map to render at.
     * @param angle  Angle around map to render at.
     */
    protected void renderCardinal(char c, double radius, double angle) {
        GlStateManager.enableTexture2D();

        FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
        double rad = Math.toRadians(angle);

        // Integer cast to avoid squeezed pixels in text.
        font.drawStringWithShadow(
                String.valueOf(c),
                (int) (radius * Math.sin(rad)) - (font.getCharWidth(c) >> 1),
                (int) (radius * Math.cos(rad)) - 4,
                0xffffff
        );
    }
}
