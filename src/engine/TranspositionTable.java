package engine;

/**
 * A transposition table: a cache of positions the search has already resolved.
 *
 * <p>The same position is reached by many different move orders -- 1.e4 e5 2.Nf3
 * and 1.Nf3 e5 2.e4 arrive at the same board -- and a plain search solves each
 * arrival from scratch. Remembering the result turns that repeated work into a
 * lookup, which is where the extra depth comes from.
 *
 * <p>Two separate benefits, and the second is the larger one:
 * <ul>
 *   <li>A stored result deep enough to answer the current question is returned
 *       immediately, pruning the whole subtree.</li>
 *   <li>Even when the stored depth is too shallow to use, the move that was
 *       best last time is still worth trying first. Alpha-beta only prunes well
 *       when good moves come first, so this improves ordering everywhere.</li>
 * </ul>
 *
 * <p>Entries are two longs, a key and a packed payload, in flat arrays indexed
 * by the low bits of the key. Collisions overwrite: a wrong answer is not
 * possible because the full key is checked on the way out, and a lost entry
 * only costs the work of searching that position again.
 */
public final class TranspositionTable {

    /** The stored score is exact: the search neither failed high nor low. */
    public static final int EXACT = 0;
    /** The real score is at least this: the search failed high (beta cutoff). */
    public static final int LOWER = 1;
    /** The real score is at most this: the search failed low. */
    public static final int UPPER = 2;

    private static final int BYTES_PER_ENTRY = 16;

    private long[] keys;
    private long[] data;
    private int mask;

    public TranspositionTable(int megabytes) {
        resize(megabytes);
    }

    /** Rebuilds the table at the given size, rounded down to a power of two entries. */
    public void resize(int megabytes) {
        long wanted = Math.max(1, (long) megabytes) * 1024 * 1024 / BYTES_PER_ENTRY;
        int entries = Integer.highestOneBit((int) Math.min(wanted, 1 << 24));
        if (entries < 1024) {
            entries = 1024;
        }
        keys = new long[entries];
        data = new long[entries];
        mask = entries - 1;
    }

    public void clear() {
        java.util.Arrays.fill(keys, 0L);
        java.util.Arrays.fill(data, 0L);
    }

    /**
     * The packed entry for this position, or 0 if there is none. The caller
     * unpacks with the depth/flag/score/move accessors below.
     */
    public long probe(long hash) {
        int index = (int) (hash & mask);
        return keys[index] == hash ? data[index] : 0L;
    }

    public void store(long hash, int depth, int score, int flag, int move) {
        int index = (int) (hash & mask);
        // Depth-preferred, except that an entry for a different position is
        // always replaced: a deep result for a position nobody is asking about
        // is worth less than a shallow one for the position in front of us.
        if (keys[index] == hash && depth(data[index]) > depth) {
            return;
        }
        keys[index] = hash;
        data[index] = (move & 0xFFFFL)
            | ((long) (depth & 0xFF) << 16)
            | ((long) (flag & 0x3) << 24)
            | ((long) score << 32);
    }

    public static int move(long entry) {
        return (int) (entry & 0xFFFF);
    }

    public static int depth(long entry) {
        return (int) ((entry >>> 16) & 0xFF);
    }

    public static int flag(long entry) {
        return (int) ((entry >>> 24) & 0x3);
    }

    public static int score(long entry) {
        return (int) (entry >> 32);
    }
}
