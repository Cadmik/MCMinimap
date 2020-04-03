package dev.cadmik.minimap.event.packet;

import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.util.BlockPos;

import java.io.IOException;
import java.util.List;

/**
 * A wrapper for the client-bound explosion packet.
 */
public abstract class PacketWrapperS27 extends S27PacketExplosion {
    private final S27PacketExplosion packet;

    public PacketWrapperS27(S27PacketExplosion packet) {
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
        handler.handleExplosion(this);
    }

    @Override
    public float func_149149_c() {
        return this.packet.func_149149_c();
    }

    @Override
    public float func_149144_d() {
        return this.packet.func_149144_d();
    }

    @Override
    public float func_149147_e() {
        return this.packet.func_149147_e();
    }

    @Override
    public double getX() {
        return this.packet.getX();
    }

    @Override
    public double getY() {
        return this.packet.getY();
    }

    @Override
    public double getZ() {
        return this.packet.getZ();
    }

    @Override
    public float getStrength() {
        return this.packet.getStrength();
    }

    @Override
    public List<BlockPos> getAffectedBlockPositions() {
        return this.packet.getAffectedBlockPositions();
    }
}
