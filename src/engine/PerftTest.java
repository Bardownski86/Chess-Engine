package engine;

/**
 * The standard perft positions with their published node counts.
 *
 * <p>Run with no argument to test to depth 4, or pass a depth. Depth 4 over all
 * six positions is about ten million nodes and finishes in seconds; depth 5 is
 * roughly three hundred million and takes a while.
 *
 * <p>The six positions are not arbitrary. Each was chosen to break a different
 * thing: the start position catches nothing subtle, Kiwipete exercises castling
 * and pins heavily, position 3 is a sparse endgame full of en passant and
 * promotion edge cases, position 4 is dense with promotions, and 5 and 6 are
 * known to catch generators that pass everything else. Passing all six to depth
 * 4 is strong evidence the generator is right.
 *
 * <p>Counts are from the Chess Programming Wiki perft results page.
 */
public class PerftTest {

    private static final int DEFAULT_DEPTH = 4;

    private static final String[] NAMES = {
        "start position",
        "kiwipete",
        "position 3",
        "position 4",
        "position 5",
        "position 6",
    };

    private static final String[] FENS = {
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
        "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq -",
        "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
        "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
        "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
        "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10",
    };

    /** Index is depth, so slot 0 is unused. */
    private static final long[][] EXPECTED = {
        { 0, 20, 400, 8902, 197281, 4865609, 119060324 },
        { 0, 48, 2039, 97862, 4085603, 193690690, 8031647685L },
        { 0, 14, 191, 2812, 43238, 674624, 11030083 },
        { 0, 6, 264, 9467, 422333, 15833292, 706045033 },
        { 0, 44, 1486, 62379, 2103487, 89941194 },
        { 0, 46, 2079, 89890, 3894594, 164075551, 6923051137L },
    };

    public static void main(String[] args) {
        int maxDepth = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_DEPTH;
        int failures = 0;
        long grandTotal = 0;
        long start = System.nanoTime();

        for (int p = 0; p < FENS.length; p++) {
            System.out.println(NAMES[p]);
            Board board;
            try {
                board = Fen.parse(FENS[p]);
            } catch (RuntimeException e) {
                System.out.println("  FEN did not parse: " + e.getMessage());
                failures++;
                continue;
            }

            for (int depth = 1; depth <= maxDepth && depth < EXPECTED[p].length; depth++) {
                long expected = EXPECTED[p][depth];
                long t0 = System.nanoTime();
                long actual;
                try {
                    actual = Perft.perft(board, depth);
                } catch (UnsupportedOperationException e) {
                    System.out.println("  " + e.getMessage());
                    System.out.println();
                    System.out.println("Write MoveGen.generate, then run this again.");
                    return;
                }
                long ms = (System.nanoTime() - t0) / 1_000_000;
                grandTotal += actual;

                if (actual == expected) {
                    System.out.println("  depth " + depth + "  " + actual + "  ok  (" + ms + " ms)");
                } else {
                    long diff = actual - expected;
                    System.out.println("  depth " + depth + "  " + actual
                        + "  WRONG, expected " + expected
                        + "  (" + (diff > 0 ? "+" : "") + diff + ")");
                    failures++;
                    System.out.println("    to find it:  Perft.divide(Fen.parse(fen), " + depth + ")");
                    break; // deeper counts are meaningless once one is wrong
                }
            }
            System.out.println();
        }

        long totalMs = (System.nanoTime() - start) / 1_000_000;
        System.out.println(grandTotal + " nodes in " + totalMs + " ms"
            + (totalMs > 0 ? "  (" + (grandTotal / Math.max(1, totalMs)) + "k nodes/sec)" : ""));
        System.out.println(failures == 0 ? "ALL POSITIONS PASSED" : failures + " FAILURES");
    }
}
