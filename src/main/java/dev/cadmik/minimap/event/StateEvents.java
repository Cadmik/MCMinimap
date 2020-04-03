package dev.cadmik.minimap.event;

import dev.cadmik.minimap.render.ChunkAtlas;
import net.minecraft.client.Minecraft;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

import java.util.List;

/**
 * The singleton handler for all state-based minimap events.
 */
public class StateEvents {
    private static final StateEvents instance = new StateEvents();

    public static StateEvents getInstance() {
        return instance;
    }

    private StateEvents() {
    }

    /**
     * Handles the client world loading event that occurs when changing between
     * dimensions of a vanilla server, or sub-servers of a Bungee proxy.
     *
     * @param e World loading event (received twice if world is local).
     */
    @SubscribeEvent
    public void worldLoaded(WorldEvent.Load e) {
        // This can't possibly be the best way to isolate the client-side event...
        if (!e.world.isRemote) {
            return;
        }

        Minecraft.getMinecraft().addScheduledTask(
                () -> ChunkAtlas.getInstance().clear()
        );
    }

    /**
     * Attaches packet handler upon client connection to server. Needed to
     * handle block events that have no adequate equivalent as Forge events.
     *
     * @param e Client connection event.
     */
    @SubscribeEvent
    public void clientConnected(FMLNetworkEvent.ClientConnectedToServerEvent e) {
        e.manager.channel().pipeline().addBefore(
                "packet_handler",
                "cdk_minimap_handler",
                new BlockUpdates()
        );
    }
}
