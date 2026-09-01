package engine;

import java.util.Arrays;

/**
 * Perft counts the leaf nodes of the move tree to a fixed depth. Compared
 * against published numbers it is the only practical way to know a move
 * generator is right, because the counts only match when castling, en passant,
 * promotion, check evasion and make/unmake are all correct together.
 *
 * <p>{@link #divide} is the debugging tool. When a total is wrong, divide the
 * position by root move, compare each line against a reference, then walk into
 * whichever move disagrees and divide again. Repeat and the tree narrows to the
 * one position where generation is wrong. That bisection is the whole technique.
 */
public final class Perft {

    private Perft() {
    }

    public static long perft(Board board, int depth) {
        if (depth <= 0) {
            return 1;
        }
        // One scratch buffer per level, so a recursive call cannot overwrite
        // the move list its caller is still walking.
        int[][] buffers = new int[depth + 1][MoveGen.MAX_MOVES];
        return perft(board, depth, buffers);
    }

    private static long perft(Board board, int depth, int[][] buffers) {
        if (depth == 0) {
            return 1;
        }
        int[] moves = buffers[depth];
        int count = MoveGen.generate(board, moves);

        long nodes = 0;
        for (int i = 0; i < count; i++) {
            board.makeMove(moves[i]);
            nodes += perft(board, depth - 1, buffers);
            board.unmakeMove(moves[i]);
        }
        return nodes;
    }

    /**
     * Prints the node count under each root move, sorted so the output lines up
     * with other engines for comparison.
     */
    public static void divide(Board board, int depth) {
        int[] moves = new int[MoveGen.MAX_MOVES];
        int count = MoveGen.generate(board, moves);

        String[] lines = new String[count];
        long total = 0;
        for (int i = 0; i < count; i++) {
            board.makeMove(moves[i]);
            long sub = perft(board, depth - 1);
            board.unmakeMove(moves[i]);
            lines[i] = Move.toUci(moves[i]) + " " + sub;
            total += sub;
        }

        Arrays.sort(lines);
        for (String line : lines) {
            System.out.println(line);
        }
        System.out.println();
        System.out.println("moves " + count + ", nodes " + total);
    }

    /**
     * Runs divide on a position given as FEN. Handy from a scratch main while
     * hunting a disagreement.
     */
    public static void divide(String fen, int depth) {
        divide(Fen.parse(fen), depth);
    }
}
