package dev.cadmik.minimap.event.packet;

import net.minecraft.block.state.IBlockState;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.util.BlockPos;

import java.io.IOException;

/**
 * A wrapper for the client-bound individual block update packet.
 */
public abstract class PacketWrapperS23 extends S23PacketBlockChange {
    private final S23PacketBlockChange packet;

    public PacketWrapperS23(S23PacketBlockChange packet) {
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
        handler.handleBlockChange(this);
    }

    @Override
    public IBlockState getBlockState() {
        return this.packet.getBlockState();
    }

    @Override
    public BlockPos getBlockPosition() {
        return this.packet.getBlockPosition();
    }
}

