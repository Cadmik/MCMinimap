package dev.cadmik.minimap.render;

import dev.cadmik.minimap.render.proc.MapRenderProc;
import dev.cadmik.minimap.render.proc.ScissorRenderProc;
import dev.cadmik.minimap.render.proc.StencilRenderProc;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * The singleton minimap renderer. Actual rendering is delegated to
 * implementations of the {@code MapRenderProc} interface.
 *
 * <p> Two rendering procedures are included, to ensure that the minimap
 * is displayed properly, even if the graphics card (somehow) doesn't
 * support a stencil buffer.
 *
 * <p> Edit: It seems that OptiFine can disable the stencil buffer...
 * Well, I'm glad I wrote a fallback!
 */
public class MapRenderer {
    private static final MapRenderer instance = new MapRenderer();

    private final MapRenderProc proc;

    public static MapRenderer getInstance() {
        return instance;
    }

    private MapRenderer() {
        Framebuffer fb = Minecraft.getMinecraft().getFramebuffer();

        if (fb.isStencilEnabled() || fb.enableStencil()) {
            this.proc = new StencilRenderProc();
        } else {
            // Overkill? Almost definitely. But now I can say that this
            // mod will work even if the stencil buffer *isn't* enabled.
            this.proc = new ScissorRenderProc();
        }
    }

    @SubscribeEvent
    public void render(RenderGameOverlayEvent.Post e) {
        // This should render beneath any debugging text or scoreboards, but
        // above any visual effects such as the portal animation.
        if (e.type != RenderGameOverlayEvent.ElementType.EXPERIENCE) {
            return;
        }

        EntityPlayer p = Minecraft.getMinecraft().thePlayer;

        double x = lerp(p.prevPosX, p.posX, e.partialTicks);
        double z = lerp(p.prevPosZ, p.posZ, e.partialTicks);
        double yaw = lerp(p.prevRotationYaw, p.rotationYaw, e.partialTicks);

        // Ensure maximal chunk binding.
        ChunkAtlas.getInstance().loadChunks((int) x >> 4, (int) z >> 4);

        ScaledResolution res = new ScaledResolution(Minecraft.getMinecraft());
        this.proc.render(res.getScaledWidth() - 100, 100, x, z, yaw);
    }

    private static double lerp(double prev, double current, float partialTicks) {
        return prev + (current - prev) * partialTicks;
    }
}
