package engine;

/**
 * Move generation. This is the part you are writing.
 *
 * <h2>The contract Perft relies on</h2>
 *
 * {@link #generate} must write every <em>legal</em> move for the side to move
 * into {@code moves}, starting at index 0, and return how many it wrote. Legal
 * means the move does not leave your own king attacked. Perft counts legal
 * moves, so if you return pseudo-legal moves the node counts will come out too
 * high and every reference number will disagree.
 *
 * <p>You write into a caller-supplied array rather than returning a list so
 * that generating moves allocates nothing, the same reasoning behind encoding a
 * move as an int. The array is always at least {@link #MAX_MOVES} long.
 *
 * <h2>A workable order to build it</h2>
 *
 * <ol>
 *   <li>Knights and the king. Fixed offsets, no sliding, no special cases.
 *       Check perft depth 1 on a simple position as soon as this works.</li>
 *   <li>Rooks, bishops, queens. Slide along each direction until you leave the
 *       board or hit a piece. Own piece: stop. Enemy piece: emit a capture,
 *       then stop.</li>
 *   <li>Pawns. Single push, double push from the starting rank, both diagonal
 *       captures, then promotions on the last rank, which multiply each move by
 *       four. Pawns are where most of the bugs live.</li>
 *   <li>En passant, using {@link Board#enPassantSquare}.</li>
 *   <li>Castling. Rights, empty squares between, and the king may not start in
 *       check, pass through an attacked square, or land on one.</li>
 *   <li>Legality filtering. The simple version: generate pseudo-legal moves,
 *       make each one, ask whether your own king is attacked, unmake. Slow but
 *       obviously correct, and correct is what perft is checking.</li>
 * </ol>
 *
 * <h2>Traps worth knowing before you hit them</h2>
 *
 * <ul>
 *   <li><b>Edge wrapping.</b> In a 64 square array, adding 1 to h4 lands on a5.
 *       A knight on h1 stepping "two right, one up" silently reappears on the a
 *       file. Every mailbox generator has to deal with this, usually by
 *       checking that the file did not jump too far. It is the single most
 *       common bug in this file.</li>
 *   <li><b>Promotion counts as four moves</b>, not one, and a promotion that is
 *       also a capture is another four. Perft depth 1 on a position with a pawn
 *       on the seventh rank will catch you here immediately.</li>
 *   <li><b>The double pawn push flag matters.</b> {@link Board#makeMove} only
 *       sets the en passant square when the move carries
 *       {@link Move#FLAG_DOUBLE_PAWN_PUSH}, so a double push tagged as a plain
 *       quiet move breaks en passant one ply later, which shows up as a wrong
 *       number at depth 3 and not before.</li>
 *   <li><b>Castling rights are not permission.</b> Having the right does not
 *       mean the squares are empty or unattacked.</li>
 * </ul>
 *
 * <p>Assertions are on under both launch configs, so an off-board square
 * handed to {@link Move#of} will stop at the line that produced it.
 */
public final class MoveGen {

    /** No chess position has more than 218 legal moves; 256 leaves room. */
    public static final int MAX_MOVES = 256;

    private MoveGen() {
    }

    /**
     * Writes every legal move for the side to move into {@code moves} and
     * returns the count.
     */
    public static int generate(Board board, int[] moves) {
        throw new UnsupportedOperationException(
            "MoveGen.generate is not written yet -- this one is yours");
    }

    /**
     * Whether {@code square} is attacked by {@code bySide}, which is
     * {@link Board#WHITE} or {@link Board#BLACK}. Needed twice over: to decide
     * whether a move leaves your own king in check, and to decide whether
     * castling is allowed.
     *
     * <p>Note this asks whether the square is attacked, not whether a piece
     * there could be captured, so it is asked about empty squares too, such as
     * the squares a castling king passes over.
     */
    public static boolean isSquareAttacked(Board board, int square, int bySide) {
        throw new UnsupportedOperationException(
            "MoveGen.isSquareAttacked is not written yet -- this one is yours");
    }
}
