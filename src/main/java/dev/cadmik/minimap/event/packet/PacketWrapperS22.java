package dev.cadmik.minimap.event.packet;

import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;

import java.io.IOException;

/**
 * A wrapper for the client-bound multi-block update packet.
 */
public abstract class PacketWrapperS22 extends S22PacketMultiBlockChange {
    private final S22PacketMultiBlockChange packet;

    /*
     * I considered making a generic PacketWrapper class where I could
     * implement (read/write)PacketData for all wrappers, but it results
     * in a net loss in terms of memory usage, since we end up keeping
     * two instances of the same packet. Besides, it's only 3 classes.
     * What's the point?
     *
     * If there was potential to have way more, I'd probably just write a
     * bytecode-based generic packet wrapper. Any overriding code could be
     * woven in at runtime.
     */

    public PacketWrapperS22(S22PacketMultiBlockChange packet) {
        this.packet = packet;
    }

    @Override
    public void readPacketData(PacketBuffer buf) throws IOException {
        this.packet.readPacketData(buf);
    }

    @Override
    public void writePacketData(PacketBuffer buf) throws IOException {
        this.packet.writePacketData(buf);
    }

    /**
     * Packet handling override to ensure the wrapper's code is called if it's
     * rescheduled by the handler.
     *
     * @param handler Client packet handler.
     */
    @Override
    public void processPacket(INetHandlerPlayClient handler) {
        handler.handleMultiBlockChange(this);
    }

    @Override
    public S22PacketMultiBlockChange.BlockUpdateData[] getChangedBlocks() {
        return this.packet.getChangedBlocks();
    }
}

