package dev.cadmik.minimap;

import dev.cadmik.minimap.event.StateEvents;
import dev.cadmik.minimap.render.ChunkAtlas;
import dev.cadmik.minimap.render.MapRenderer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

/**
 * My minimap challenge submission. Hope you enjoy!
 *
 * <p> This mod provides a live view of the world terrain. Any block
 * placements, removals, or explosions that occur are immediately
 * displayed.
 */
@Mod(
        modid = "cdk_minimap",
        version = "0.1.0",
        useMetadata = true,
        clientSideOnly = true,
        acceptedMinecraftVersions = "[1.8.9]"
        // Probably runs fine in 1.8.x entirely, but I'm not risking it.
)
public class Minimap {
    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        // Limit the rendering distance to 5 chunks.
        ChunkAtlas.init(5);

        MinecraftForge.EVENT_BUS.register(StateEvents.getInstance());
        MinecraftForge.EVENT_BUS.register(MapRenderer.getInstance());
    }
}
