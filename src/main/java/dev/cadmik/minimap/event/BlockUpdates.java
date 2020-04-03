package dev.cadmik.minimap.event;

import dev.cadmik.minimap.event.packet.PacketWrapperS22;
import dev.cadmik.minimap.event.packet.PacketWrapperS23;
import dev.cadmik.minimap.event.packet.PacketWrapperS27;
import dev.cadmik.minimap.render.ChunkAtlas;
import gnu.trove.set.hash.THashSet;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.util.BlockPos;
import net.minecraft.world.ChunkCoordIntPair;

import java.util.Collections;
import java.util.Set;

/**
 * A Netty inbound channel handler for all block-related update packets.
 */
public class BlockUpdates extends SimpleChannelInboundHandler<Packet> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet msg) {
        if (msg instanceof S23PacketBlockChange) {
            msg = new PacketWrapperS23((S23PacketBlockChange) msg) {
                @Override
                public void processPacket(INetHandlerPlayClient handler) {
                    super.processPacket(handler);

                    BlockPos pos = this.getBlockPosition();
                    Set<ChunkCoordIntPair> single = Collections.singleton(
                            new ChunkCoordIntPair(pos.getX() >> 4, pos.getZ() >> 4)
                    );

                    refreshChunk(single);
                }
            };
        } else if (msg instanceof S22PacketMultiBlockChange) {
            msg = new PacketWrapperS22((S22PacketMultiBlockChange) msg) {
                @Override
                public void processPacket(INetHandlerPlayClient handler) {
                    super.processPacket(handler);

                    BlockPos pos = this.getChangedBlocks()[0].getPos();
                    Set<ChunkCoordIntPair> single = Collections.singleton(
                            new ChunkCoordIntPair(pos.getX() >> 4, pos.getZ() >> 4)
                    );

                    refreshChunk(single);
                }
            };
        } else if (msg instanceof S27PacketExplosion) {
            msg = new PacketWrapperS27((S27PacketExplosion) msg) {
                @Override
                public void processPacket(INetHandlerPlayClient handler) {
                    super.processPacket(handler);

                    Set<ChunkCoordIntPair> affectedChunks = new THashSet<>();
                    for (BlockPos pos : this.getAffectedBlockPositions()) {
                        affectedChunks.add(new ChunkCoordIntPair(pos.getX() >> 4, pos.getZ() >> 4));
                    }

                    refreshChunk(affectedChunks);
                }
            };
        }

        ctx.fireChannelRead(msg);
    }

    private static void refreshChunk(Iterable<ChunkCoordIntPair> coords) {
        // If called from Netty thread, ChunkAtlas::refreshChunk would run
        // before the chunk is updated in-game. Scheduling guarantees that
        // we'll run *after* any block updates.
        Minecraft.getMinecraft().addScheduledTask(() -> {
            for (ChunkCoordIntPair c : coords) {
                ChunkAtlas.getInstance().refreshChunk(c.chunkXPos, c.chunkZPos);
            }
        });
    }
}
