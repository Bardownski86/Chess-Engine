package engine;

/**
 * A single chess move, encoded in an int.
 *
 * <pre>
 *   bits  0-5   from square (0-63)
 *   bits  6-11  to square   (0-63)
 *   bits 12-15  flag        (one of the FLAG_* constants)
 * </pre>
 *
 * Moves are ints rather than objects so that generating them allocates nothing.
 * Perft and search produce millions of moves, and per-move object churn would
 * cost more than the move generation itself. This class is therefore a holder
 * for static helpers and is never instantiated.
 *
 * <p>Note what is deliberately <em>not</em> stored: the captured piece, the old
 * castling rights, the old en passant square, the old halfmove clock. None of
 * that is recoverable from the move alone, so undoing a move needs a separate
 * stack of saved state in {@link Board}. Keeping it out of the move is what
 * lets a move stay 16 bits.
 */
public final class Move {

    /**
     * Sentinel for "no move". This is really the encoding of a1-a1 quiet, but a
     * move from a square to itself is never legal, so the value is free to use.
     */
    public static final int NONE = 0;

    public static final int FLAG_QUIET                = 0b0000;
    public static final int FLAG_DOUBLE_PAWN_PUSH     = 0b0001;
    public static final int FLAG_CASTLE_KINGSIDE      = 0b0010;
    public static final int FLAG_CASTLE_QUEENSIDE     = 0b0011;
    public static final int FLAG_CAPTURE              = 0b0100;
    public static final int FLAG_EN_PASSANT           = 0b0101;
    public static final int FLAG_PROMO_KNIGHT         = 0b1000;
    public static final int FLAG_PROMO_BISHOP         = 0b1001;
    public static final int FLAG_PROMO_ROOK           = 0b1010;
    public static final int FLAG_PROMO_QUEEN          = 0b1011;
    public static final int FLAG_PROMO_KNIGHT_CAPTURE = 0b1100;
    public static final int FLAG_PROMO_BISHOP_CAPTURE = 0b1101;
    public static final int FLAG_PROMO_ROOK_CAPTURE   = 0b1110;
    public static final int FLAG_PROMO_QUEEN_CAPTURE  = 0b1111;

    /** Set in every promotion flag. */
    private static final int PROMOTION_BIT = 0b1000;
    /** Set in every flag that removes an enemy piece, en passant included. */
    private static final int CAPTURE_BIT = 0b0100;
    /** The two low flag bits pick which piece a pawn promotes to. */
    private static final int PROMOTION_PIECE_MASK = 0b0011;

    private static final int SQUARE_MASK = 0x3F;
    private static final int FLAG_MASK = 0xF;

    private static final int TO_SHIFT = 6;
    private static final int FLAG_SHIFT = 12;

    private Move() {
    }

    public static int of(int from, int to, int flag) {
        return from | (to << TO_SHIFT) | (flag << FLAG_SHIFT);
    }

    public static int of(int from, int to) {
        return of(from, to, FLAG_QUIET);
    }

    public static int from(int move) {
        return move & SQUARE_MASK;
    }

    public static int to(int move) {
        return (move >>> TO_SHIFT) & SQUARE_MASK;
    }

    public static int flag(int move) {
        return (move >>> FLAG_SHIFT) & FLAG_MASK;
    }

    public static boolean isPromotion(int move) {
        return (flag(move) & PROMOTION_BIT) != 0;
    }

    public static boolean isCapture(int move) {
        return (flag(move) & CAPTURE_BIT) != 0;
    }

    public static boolean isEnPassant(int move) {
        return flag(move) == FLAG_EN_PASSANT;
    }

    public static boolean isDoublePawnPush(int move) {
        return flag(move) == FLAG_DOUBLE_PAWN_PUSH;
    }

    public static boolean isCastle(int move) {
        int flag = flag(move);
        return flag == FLAG_CASTLE_KINGSIDE || flag == FLAG_CASTLE_QUEENSIDE;
    }

    /**
     * The piece a promoting pawn becomes, as an unsigned piece constant from
     * {@link Board}. Returns 0 when the move is not a promotion.
     */
    public static int promotionPiece(int move) {
        if (!isPromotion(move)) {
            return 0;
        }
        return Board.KNIGHT + (flag(move) & PROMOTION_PIECE_MASK);
    }

    /**
     * The move in UCI long algebraic notation, for example {@code e2e4} or
     * {@code e7e8q}. This is what perft output and any UCI front end expect.
     */
    public static String toUci(int move) {
        String text = Board.squareName(from(move)) + Board.squareName(to(move));
        if (isPromotion(move)) {
            return text + promotionChar(move);
        }
        return text;
    }

    private static char promotionChar(int move) {
        switch (promotionPiece(move)) {
            case Board.KNIGHT:
                return 'n';
            case Board.BISHOP:
                return 'b';
            case Board.ROOK:
                return 'r';
            case Board.QUEEN:
                return 'q';
            default:
                return '?';
        }
    }
}
