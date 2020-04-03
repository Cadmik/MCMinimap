package dev.cadmik.minimap.render;

import net.minecraft.block.material.MapColor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.BlockPos;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.awt.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * The singleton texture atlas manager.
 *
 * <p> Use when rendering bound chunks.
 */
public class ChunkAtlas implements Iterable<ChunkTile> {
    /**
     * A direct method handle to {@code World::isChunkLoaded}.
     *
     * <p> Initially, I used {@code World::isBlockLoaded} to determine
     * if a chunk was loaded, as it called {@code World::isChunkLoaded}
     * directly. However, it also performed a completely redundant check on
     * the validity of the specified {@code BlockPos}.
     *
     * <p> So I opted to use {@code World::isChunkLoaded} directly, and while
     * using Reflection is too slow and would've made the optimization
     * worthless, static-final MethodHandles come very close to the
     * performance of a direct method call, since they're inlined by the JVM.
     *
     * <p> Sure, it's a premature optimization, but at least I'm sleeping well
     * because of it!
     */
    private static final MethodHandle m_isChunkLoaded;

    private static ChunkAtlas instance;

    /**
     * Bound chunk tiles. Index in the array maps directly to offset in texture
     * atlas.
     */
    private final ChunkCoordIntPair[] chunkCoords;
    private final BitSet reusableChunks;

    /**
     * The chunk viewing radius.
     */
    private final int radius;

    /**
     * The number of chunk tiles that can fit within the texture atlas,
     * in log-base-2 representation.
     *
     * <p> The area of the texture atlas is a power-of-two, and so too will
     * the atlas's width and height. Thus we can optimize any scaling by the
     * atlas width into a shift.
     */
    private final int chunkSpanL2;

    /**
     * The normalized dimensions of a chunk in the texture atlas.
     */
    private final double chunkWidth, chunkHeight;

    /**
     * The chunk tile upload buffer. Used to transfer chunk color data to the
     * OpenGL implementation.
     */
    private final IntBuffer pixels;
    private final int texture;

    /**
     * Initializes the ChunkAtlas singleton with the maximum chunk rendering
     * distance.
     *
     * @param maxChunkRadius Maximum chunk rendering distance.
     */
    public static void init(int maxChunkRadius) {
        if (instance == null) {
            instance = new ChunkAtlas(maxChunkRadius);
        }
    }

    public static ChunkAtlas getInstance() {
        return instance;
    }

    private ChunkAtlas(int maxChunkRadius) {
        int maxChunks = maxChunkRadius * maxChunkRadius << 2;

        /*
         * Rounds texture area up to the next power-of-two.
         * - Resolves visual artifacts from floating point imperfections.
         *      - Might not be important in practice bc. of how small floats can be.
         * - AFAIK, improves rendering performance.
         *
         * Also converts from chunk space to block space, hence the shift of 5
         * instead of 1.
         */
        int texWidth = Integer.highestOneBit(maxChunks - 1) << 5;
        int texHeight = 16;

        { // For fault tolerance (see next comment)
            int texLimit = Minecraft.getGLMaximumTextureSize();
            while (texWidth > texLimit) {
                texWidth >>= 1;
                texHeight <<= 1;
            }

            while (texHeight > texLimit) {
                texHeight >>= 1;
            }

            int chunkCapacity = texWidth * texHeight >> 8;

            if (maxChunks > chunkCapacity) {
                maxChunks = chunkCapacity;
            }
        }

        this.radius = (int) Math.sqrt(maxChunks >> 2);
        this.chunkSpanL2 = Integer.numberOfTrailingZeros(texWidth >> 4);

        this.chunkWidth = 16.0 / texWidth;
        this.chunkHeight = 16.0 / texHeight;

        /*
         * We're now guaranteed to have enough texture area to store all chunks
         * within the recalculated radius. This code is probably one giant
         * no-op on modern computers, but it only runs once with every world
         * load, so I figured I'd put in some fault tolerance.
         */

        this.chunkCoords = new ChunkCoordIntPair[maxChunks];
        this.reusableChunks = new BitSet(maxChunks);

        this.texture = GL11.glGenTextures();
        GlStateManager.bindTexture(this.texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D, 0,
                GL11.GL_RGBA,
                texWidth, texHeight, 0,
                GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV,
                (IntBuffer) null
        );

        // I'm compelled to make a method that deletes the texture (+ other cleanup)
        // but FMLModDisabledEvent was never properly hooked up. Skipping.

        this.pixels = GLAllocation.createDirectIntBuffer(256);
    }

    /**
     * Returns the recalculated chunk viewing radius.
     *
     * <p> On modern computers, this will be equivalent to the radius provided
     * to {@code ChunkAtlas::init()}. However, this value may be reduced
     * if the maximum texture dimensions cannot contain enough chunk tiles for
     * seamless rendering.
     *
     * @return Recalculated chunk viewing radius.
     */
    public int getChunkRadius() {
        return this.radius;
    }

    /**
     * Returns the OpenGL texture handle.
     *
     * @return OpenGL texture handle.
     */
    public int getTextureHandle() {
        return this.texture;
    }

    /**
     * Returns the chunk tile's normalized texture atlas X coordinate based on
     * the specified offset.
     *
     * @param offset Chunk offset.
     * @return Chunk tile's texture X coordinate
     */
    public double getSpriteX(int offset) {
        return (offset & ((1 << this.chunkSpanL2) - 1)) * this.chunkWidth;
    }

    /**
     * Returns the chunk tile's normalized texture atlas Y coordinate based on
     * the specified offset.
     *
     * @param offset Chunk offset.
     * @return Chunk tile's texture Y coordinate
     */
    public double getSpriteY(int offset) {
        return (offset >> this.chunkSpanL2) * this.chunkHeight;
    }

    /**
     * Returns the normalized width of a chunk tile on the texture atlas.
     *
     * @return Bormalized width of a chunk tile on the texture atlas.
     */
    public double getSpriteWidth() {
        return this.chunkWidth;
    }

    /**
     * Returns the normalized height of a chunk tile on the texture atlas.
     *
     * @return Normalized height of a chunk tile on the texture atlas.
     */
    public double getSpriteHeight() {
        return this.chunkHeight;
    }

    /**
     * Clears the chunk bindings to discard loaded chunk textures.
     */
    public void clear() {
        Arrays.fill(this.chunkCoords, null);
    }

    /**
     * Binds unloaded chunks within rendering distance.
     *
     * @param chunkX X coordinate of central chunk.
     * @param chunkZ Y coordinate of central chunk.
     */
    public void loadChunks(int chunkX, int chunkZ) {
        World w = Minecraft.getMinecraft().theWorld;
        if (w == null) {
            return;
        }

        this.reusableChunks.clear();

        // Mark any chunks that are loaded.
        // If any lie outside the render distance, discard them.
        for (int offs = 0; offs < this.chunkCoords.length; offs++) {
            ChunkCoordIntPair coords = this.chunkCoords[offs];
            if (coords == null) {
                continue;
            }

            int offsX = coords.chunkXPos - chunkX;
            int offsZ = coords.chunkZPos - chunkZ;

            if (offsX < -this.radius || this.radius <= offsX) {
                this.chunkCoords[offs] = null;
                continue;
            }

            if (offsZ < -this.radius || this.radius <= offsZ) {
                this.chunkCoords[offs] = null;
                continue;
            }

            offsX += this.radius;
            offsZ += this.radius;

            this.reusableChunks.set(offsX + offsZ * this.radius * 2);
        }

        // Identify any unloaded chunks and bind them.
        for (int relZ = this.radius * 2 - 1; relZ >= 0; relZ--) {
            for (int relX = this.radius * 2 - 1; relX >= 0; relX--) {
                int checkIdx = relX + relZ * this.radius * 2;

                if (this.reusableChunks.get(checkIdx)) {
                    continue;
                }

                int x = chunkX + relX - this.radius;
                int z = chunkZ + relZ - this.radius;

                Chunk c = this.getLoadedChunk(x, z);
                if (c == null) {
                    continue;
                }

                this.reserveOffset(c);

                // Ensure correct shading of south chunk.
                this.recolorChunk(x, z + 1);
            }
        }
    }

    /**
     * Recolors the chunk at the specified coordinates, as well as the chunk
     * immediately to the south, to ensure correct shading.
     *
     * <p> No changes will be applied to any unloaded chunks referenced by this
     * invocation.
     *
     * @param x Chunk's X coordinate.
     * @param z Chunk's Z coordinate.
     */
    public void refreshChunk(int x, int z) {
        this.recolorChunk(x, z);
        this.recolorChunk(x, z + 1);
    }

    /**
     * Returns an iterator over all occupied chunk tiles. Use this to
     * render all available chunks in one sweep.
     *
     * @return An iterator over all occupied chunk tiles.
     */
    @Override
    public Iterator<ChunkTile> iterator() {
        return IntStream.range(0, this.chunkCoords.length)
                .filter(offs -> this.chunkCoords[offs] != null)
                .mapToObj(offs -> {
                    ChunkCoordIntPair coords = this.chunkCoords[offs];
                    return new ChunkTile(coords.chunkXPos, coords.chunkZPos, offs);
                }).iterator();
    }

    /**
     * Reserves first free chunk tile and uploads chunk color data to the
     * texture atlas.
     *
     * @param c Chunk to be bound.
     */
    private void reserveOffset(Chunk c) {
        int offs = this.searchChunkAtlas(null);
        if (offs == -1) {
            /*
             * The way this code works, shouldn't ever happen.
             * Still, would probably remove this if I were releasing the mod,
             * and have it fail silently instead.
             */
            throw new IllegalStateException("Chunk coordinate array full.");
        }

        this.chunkCoords[offs] = c.getChunkCoordIntPair();

        this.updateColorData(c, offs);
    }

    /**
     * Recolors chunk at specified coordinates.
     *
     * @param x Chunk's X coordinate.
     * @param z Chunk's Z coordinate.
     */
    private void recolorChunk(int x, int z) {
        Chunk c = this.getLoadedChunk(x, z);
        if (c == null) {
            return;
        }

        int offs = this.searchChunkAtlas(new ChunkCoordIntPair(x, z));
        if (offs == -1) {
            return;
        }

        this.updateColorData(c, offs);
    }

    /**
     * Uploads the chunk's color data to the texture atlas.
     *
     * @param src  Chunk to scan and upload.
     * @param offs Texture atlas offset of chunk tile.
     */
    private void updateColorData(Chunk src, int offs) {
        this.computeColors(src);

        int x = offs & ((1 << this.chunkSpanL2) - 1);
        int y = offs >> this.chunkSpanL2;

        x <<= 4;
        y <<= 4;

        GlStateManager.bindTexture(this.texture);
        GL11.glTexSubImage2D(
                GL11.GL_TEXTURE_2D, 0,
                x, y, 16, 16,
                GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV,
                this.pixels
        );
    }

    /**
     * Computes the color and shading of the specified chunk, and stores the
     * result in {@code ChunkAtlas::pixels}.
     *
     * <p> This method will attempt to access the chunk immediately to the north of
     * the one specified to ensure correct shading if possible, and defaults to
     * 86% brightness if access fails.
     *
     * @param src Source chunk for color computation.
     */
    private void computeColors(Chunk src) {
        int[] northHeights = new int[16];
        Arrays.fill(northHeights, -1);

        Chunk north = this.getLoadedChunk(src.xPosition, src.zPosition - 1);
        if (north != null) {
            for (int x = 0; x < 16; x++) {
                northHeights[x] = this.getTopColoredBlockState(north, x, 15).getY();
            }
        }

        for (int x = 0; x < 16; x++) {
            int northHeight = northHeights[x];

            for (int z = 0; z < 16; z++) {
                BlockPos pos = this.getTopColoredBlockState(src, x, z);
                IBlockState state = src.getBlockState(pos);
                MapColor color = state.getBlock().getMapColor(state);

                // Solid block shading
                int height = pos.getY();
                int shade = 1;

                if (northHeight > height) {
                    shade = 0;
                } else if (northHeight >= 0 && northHeight < height) {
                    shade = 2;
                }

                // Liquid shading
                int depth = 0;
                while (pos.getY() >= 0 && !state.getBlock().getMaterial().isSolid()) {
                    pos = pos.add(0, -1, 0);
                    state = src.getBlockState(pos);
                    depth++;
                }

                // Optimized version of code located in ItemMap::updateMapData
                if (depth > 0) {
                    int dither = depth + (((x ^ z) & 1) << 1);

                    if (dither < 5) {
                        shade = 2;
                    } else if (dither > 9) {
                        shade = 0;
                    }
                }

                int rgb;

                // Void shading
                if (height > 0) {
                    rgb = color.func_151643_b(shade);
                } else if (((x ^ z) & 3) == 0) {
                    rgb = 0x2d2d5a;
                } else {
                    rgb = 0x1e1e3c;
                }

                northHeight = height;

                this.pixels.put(x | z << 4, rgb);
            }
        }
    }

    /**
     * Returns the topmost colored block at the specified block coordinates.
     *
     * @param src Chunk to search.
     * @param x   Block's X coordinate.
     * @param z   Block's Z coordinate.
     * @return Topmost colored block at the specified coordinates.
     */
    private BlockPos getTopColoredBlockState(Chunk src, int x, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int y = src.getTopFilledSegment() + 15; y >= 0; y--) {
            IBlockState state = src.getBlockState(pos.set(x, y, z));

            if (state.getBlock().getMapColor(state) != MapColor.airColor) {
                break;
            }
        }

        return pos;
    }

    /**
     * Returns index of bound chunk.
     *
     * @param c Chunk's coordinates.
     * @return Index of bound chunk, {@code -1} otherwise.
     */
    private int searchChunkAtlas(ChunkCoordIntPair c) {
        for (int offs = 0; offs < this.chunkCoords.length; offs++) {
            if (Objects.equals(c, this.chunkCoords[offs])) {
                return offs;
            }
        }

        return -1;
    }

    /**
     * Returns the requested chunk only if it's already loaded within the
     * world, to avoid forcing chunk loading in local play.
     *
     * @param x Chunk's X coordinate.
     * @param z Chunk's Z coordinate.
     * @return Requested chunk if it's loaded, {@code null} otherwise.
     */
    private Chunk getLoadedChunk(int x, int z) {
        World w = Minecraft.getMinecraft().theWorld;
        if (w == null) {
            return null;
        }

        if (!isChunkLoaded(w, x, z)) {
            return null;
        }

        Chunk c = w.getChunkFromChunkCoords(x, z);
        if (c.isEmpty()) {
            return null;
        }

        return c;
    }

    /**
     * Determines whether or not the specified chunk has been loaded by the
     * containing World.
     *
     * @param w Chunk's domain.
     * @param x Chunk's X coordinate.
     * @param z Chunk's Z coordinate.
     * @return {@code true} iff specified chunk is loaded.
     */
    private static boolean isChunkLoaded(World w, int x, int z) {
        try {
            return (boolean) m_isChunkLoaded.invokeExact(w, x, z, true);
        } catch (Throwable t) {
            throw new RuntimeException("Exception thrown by World::isChunkLoaded.", t);
        }
    }

    static {
        Method reflect;

        try {
            reflect = World.class.getDeclaredMethod("isChunkLoaded", int.class, int.class, boolean.class);
        } catch (NoSuchMethodException ignored) {
            try {
                reflect = World.class.getDeclaredMethod("func_175680_a", int.class, int.class, boolean.class);
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("World::isChunkLoaded could not be found.", e);
            }
        }

        try {
            reflect.setAccessible(true);

            m_isChunkLoaded = MethodHandles.lookup().unreflect(reflect);

            reflect.setAccessible(false);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("World::isChunkLoaded is inaccessible.", e);
        }
    }
}
