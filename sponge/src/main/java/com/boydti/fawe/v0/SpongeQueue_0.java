package com.boydti.fawe.v0;

import com.boydti.fawe.object.FaweChunk;
import com.boydti.fawe.util.FaweQueue;
import com.sk89q.worldedit.world.biome.BaseBiome;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.world.Chunk;
import org.spongepowered.api.world.World;

public abstract class SpongeQueue_0 extends FaweQueue {

    /**
     * Map of chunks in the queue
     */
    private final ConcurrentHashMap<Long, FaweChunk<Chunk>> blocks = new ConcurrentHashMap<>();
    private LinkedBlockingDeque<FaweChunk<Chunk>> chunks = new LinkedBlockingDeque<>();

    public SpongeQueue_0(String world) {
        super(world);
    }

    @Override
    public boolean isChunkLoaded(int x, int z) {
        World world = Sponge.getServer().getWorld(this.world).get();
        Chunk chunk = world.getChunk(x << 4, 0, z << 4).orElse(null);
        return chunk != null && chunk.isLoaded();
    }

    @Override
    public void addTask(int x, int z, Runnable runnable) {
        long pair = (long) (x) << 32 | (z) & 0xFFFFFFFFL;
        FaweChunk<Chunk> result = this.blocks.get(pair);
        if (result == null) {
            result = this.getChunk(x, z);
            result.addTask(runnable);
            final FaweChunk<Chunk> previous = this.blocks.put(pair, result);
            if (previous == null) {
                chunks.add(result);
                return;
            }
            this.blocks.put(pair, previous);
            result = previous;
        }
        result.addTask(runnable);
    }

    private FaweChunk lastChunk;
    private int lastX = Integer.MIN_VALUE;
    private int lastZ = Integer.MIN_VALUE;

    @Override
    public boolean setBlock(int x, final int y, int z, final short id, final byte data) {
        if ((y > 255) || (y < 0)) {
            return false;
        }
        int cx = x >> 4;
        int cz = z >> 4;
        if (cx != lastX || cz != lastZ) {
            lastX = cx;
            lastZ = cz;
            long pair = (long) (cx) << 32 | (cz) & 0xFFFFFFFFL;
            lastChunk = this.blocks.get(pair);
            if (lastChunk == null) {
                lastChunk = this.getChunk(x >> 4, z >> 4);
                lastChunk.setBlock(x & 15, y, z & 15, id, data);
                FaweChunk<Chunk> previous = this.blocks.put(pair, lastChunk);
                if (previous == null) {
                    chunks.add(lastChunk);
                    return true;
                }
                this.blocks.put(pair, previous);
                lastChunk = previous;
            }
        }
        lastChunk.setBlock(x & 15, y, z & 15, id, data);
        return true;
    }

    @Override
    public boolean setBiome(int x, int z, final BaseBiome biome) {
        long pair = (long) (x >> 4) << 32 | (z >> 4) & 0xFFFFFFFFL;
        FaweChunk<Chunk> result = this.blocks.get(pair);
        if (result == null) {
            result = this.getChunk(x >> 4, z >> 4);
            final FaweChunk<Chunk> previous = this.blocks.put(pair, result);
            if (previous != null) {
                this.blocks.put(pair, previous);
                result = previous;
            }
        }
        result.setBiome(x & 15, z & 15, biome);
        return true;
    }

    @Override
    public FaweChunk<Chunk> next() {
        lastX = Integer.MIN_VALUE;
        lastZ = Integer.MIN_VALUE;
        try {
            if (this.blocks.size() == 0) {
                return null;
            }
            synchronized (blocks) {
                FaweChunk<Chunk> chunk = chunks.poll();
                if (chunk != null) {
                    blocks.remove(chunk.longHash());
                    this.execute(chunk);
                    return chunk;
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public int size() {
        return chunks.size();
    }

    private final LinkedBlockingDeque<FaweChunk<Chunk>> toUpdate = new LinkedBlockingDeque<>();

    public boolean execute(final FaweChunk<Chunk> fc) {
        if (fc == null) {
            return false;
        }
        // Load chunk
        final Chunk chunk = fc.getChunk();
        chunk.loadChunk(true);
        // Set blocks / entities / biome
        if (!this.setComponents(fc)) {
            return false;
        }
        fc.executeTasks();
        return true;
    }

    @Override
    public void clear() {
        this.blocks.clear();
    }

    @Override
    public void setChunk(final FaweChunk<?> chunk) {
        FaweChunk<Chunk> previous = this.blocks.put(chunk.longHash(), (FaweChunk<Chunk>) chunk);
        if (previous != null) {
            chunks.remove(previous);
        }
        chunks.add((FaweChunk<Chunk>) chunk);
    }

    public abstract Collection<FaweChunk<Chunk>> sendChunk(final Collection<FaweChunk<Chunk>> fcs);

    public abstract boolean setComponents(final FaweChunk<Chunk> fc);

    @Override
    public abstract FaweChunk<Chunk> getChunk(int x, int z);

    @Override
    public abstract boolean fixLighting(final FaweChunk<?> fc, final boolean fixAll);
}