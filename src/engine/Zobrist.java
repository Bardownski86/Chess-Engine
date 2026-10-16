package engine;

import java.util.Random;

/**
 * Zobrist hashing: a 64-bit fingerprint of a position.
 *
 * <p>Every (piece, square) combination gets a random number, and a position's
 * key is all of its occupied squares XORed together with keys for the side to
 * move, the castling rights and the en passant file. Because XOR is its own
 * inverse, a move can update the key by XORing out what left and XORing in what
 * arrived, rather than rescanning the board, which is what makes the key cheap
 * enough to maintain inside a search.
 *
 * <p>The keys come from a fixed seed, so a given position always hashes to the
 * same value between runs. That makes a disagreement reproducible instead of
 * appearing at random.
 */
public final class Zobrist {

    /** Indexed by piece + 6, so black king is 0 and white king is 12. */
    private static final long[][] PIECE = new long[13][64];
    private static final long[] CASTLING = new long[16];
    private static final long[] EN_PASSANT_FILE = new long[8];

    /** XORed in when it is black to move. */
    public static final long SIDE;

    static {
        Random rng = new Random(0x9E3779B97F4A7C15L);
        for (int p = 0; p < PIECE.length; p++) {
            for (int sq = 0; sq < 64; sq++) {
                PIECE[p][sq] = rng.nextLong();
            }
        }
        for (int i = 0; i < CASTLING.length; i++) {
            CASTLING[i] = rng.nextLong();
        }
        for (int i = 0; i < EN_PASSANT_FILE.length; i++) {
            EN_PASSANT_FILE[i] = rng.nextLong();
        }
        SIDE = rng.nextLong();
    }

    private Zobrist() {
    }

    public static long piece(int piece, int square) {
        assert piece != 0 : "empty squares contribute no key";
        return PIECE[piece + 6][square];
    }

    /**
     * The part of the key that is not piece placement: castling rights and the
     * en passant file. Kept together so a move can XOR the whole lot out before
     * it changes them and XOR the new value back in afterwards, instead of
     * tracking each flag separately.
     */
    public static long state(Board board) {
        int rights = (board.whiteCanCastleKingside ? 1 : 0)
            | (board.whiteCanCastleQueenside ? 2 : 0)
            | (board.blackCanCastleKingside ? 4 : 0)
            | (board.blackCanCastleQueenside ? 8 : 0);
        long key = CASTLING[rights];
        if (board.enPassantSquare >= 0) {
            key ^= EN_PASSANT_FILE[Board.fileOf(board.enPassantSquare)];
        }
        return key;
    }

    /** The key computed from scratch. Used to seed a position and to check the incremental updates. */
    public static long compute(Board board) {
        long key = 0;
        for (int sq = 0; sq < 64; sq++) {
            int p = board.squares[sq];
            if (p != 0) {
                key ^= PIECE[p + 6][sq];
            }
        }
        if (board.sideToMove == Board.BLACK) {
            key ^= SIDE;
        }
        return key ^ state(board);
    }
}
