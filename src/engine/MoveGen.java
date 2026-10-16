package engine;

/**
 * Move generation.
 *
 * <p>{@link #generate} writes every legal move for the side to move into the
 * caller's array and returns the count. Legal means the move does not leave the
 * mover's own king attacked, which is what perft counts.
 *
 * <p>Targets are computed in rank and file arithmetic rather than by adding
 * offsets to a square index. Adding 1 to h4 in a flat 64 array lands on a5, so
 * offset arithmetic needs a wrap check on every single step; working in rank
 * and file and bounds checking both makes wrapping impossible by construction.
 * It costs a multiply per square and buys away the most common bug in a mailbox
 * generator.
 */
public final class MoveGen {

    /** No chess position has more than 218 legal moves; 256 leaves room. */
    public static final int MAX_MOVES = 256;

    private static final int[] KNIGHT_DR = { 1, 1, 2, 2, -1, -1, -2, -2 };
    private static final int[] KNIGHT_DF = { 2, -2, 1, -1, 2, -2, 1, -1 };

    private static final int[] KING_DR = { 1, 1, 1, 0, 0, -1, -1, -1 };
    private static final int[] KING_DF = { 1, 0, -1, 1, -1, 1, 0, -1 };

    private static final int[] ROOK_DR = { 1, -1, 0, 0 };
    private static final int[] ROOK_DF = { 0, 0, 1, -1 };

    private static final int[] BISHOP_DR = { 1, 1, -1, -1 };
    private static final int[] BISHOP_DF = { 1, -1, 1, -1 };

    private static final int[] PROMO_FLAGS = {
        Move.FLAG_PROMO_KNIGHT, Move.FLAG_PROMO_BISHOP,
        Move.FLAG_PROMO_ROOK, Move.FLAG_PROMO_QUEEN,
    };

    private static final int[] PROMO_CAPTURE_FLAGS = {
        Move.FLAG_PROMO_KNIGHT_CAPTURE, Move.FLAG_PROMO_BISHOP_CAPTURE,
        Move.FLAG_PROMO_ROOK_CAPTURE, Move.FLAG_PROMO_QUEEN_CAPTURE,
    };

    /**
     * Scratch space for the pseudo-legal list. One shared buffer is safe because
     * generate never runs inside itself: it only calls make, attack detection
     * and unmake, none of which generate moves. Single threaded only.
     */
    private static final int[] PSEUDO = new int[MAX_MOVES];

    private MoveGen() {
    }

    private static boolean onBoard(int rank, int file) {
        return rank >= 0 && rank < 8 && file >= 0 && file < 8;
    }

    /**
     * Writes every legal move for the side to move into {@code moves} and
     * returns the count.
     */
    public static int generate(Board board, int[] moves) {
        int side = board.sideToMove;
        int pseudoCount = generatePseudoLegal(board, PSEUDO);
        int kingSquare = findKing(board, side);
        assert kingSquare >= 0 : "no king for side " + side;

        int count = 0;
        for (int i = 0; i < pseudoCount; i++) {
            int move = PSEUDO[i];
            // The king moving takes its own square with it.
            int checkSquare = Math.abs(board.squares[Move.from(move)]) == Board.KING
                ? Move.to(move)
                : kingSquare;

            board.makeMove(move);
            boolean leftKingAttacked = isSquareAttacked(board, checkSquare, -side);
            board.unmakeMove(move);

            if (!leftKingAttacked) {
                moves[count++] = move;
            }
        }
        return count;
    }

    /** Whether {@code side} is in check right now. */
    public static boolean isInCheck(Board board, int side) {
        int kingSquare = findKing(board, side);
        return kingSquare >= 0 && isSquareAttacked(board, kingSquare, -side);
    }

    private static int findKing(Board board, int side) {
        int king = Board.KING * side;
        for (int sq = 0; sq < 64; sq++) {
            if (board.squares[sq] == king) {
                return sq;
            }
        }
        return -1;
    }

    /**
     * Every move that follows the movement rules, without checking whether it
     * leaves the mover's king attacked. Castling is the exception: its square
     * attack conditions are part of the rule itself, so they are tested here.
     */
    private static int generatePseudoLegal(Board board, int[] moves) {
        int side = board.sideToMove;
        int count = 0;

        for (int from = 0; from < 64; from++) {
            int piece = board.squares[from];
            if (piece == 0 || piece * side < 0) {
                continue;
            }
            switch (Math.abs(piece)) {
                case Board.PAWN:
                    count = pawnMoves(board, from, side, moves, count);
                    break;
                case Board.KNIGHT:
                    count = stepMoves(board, from, side, KNIGHT_DR, KNIGHT_DF, moves, count);
                    break;
                case Board.BISHOP:
                    count = slideMoves(board, from, side, BISHOP_DR, BISHOP_DF, moves, count);
                    break;
                case Board.ROOK:
                    count = slideMoves(board, from, side, ROOK_DR, ROOK_DF, moves, count);
                    break;
                case Board.QUEEN:
                    count = slideMoves(board, from, side, ROOK_DR, ROOK_DF, moves, count);
                    count = slideMoves(board, from, side, BISHOP_DR, BISHOP_DF, moves, count);
                    break;
                case Board.KING:
                    count = stepMoves(board, from, side, KING_DR, KING_DF, moves, count);
                    break;
                default:
                    throw new IllegalStateException("bad piece " + piece + " on " + from);
            }
        }
        return castleMoves(board, side, moves, count);
    }

    /** Knights and kings: one hop per direction, no sliding. */
    private static int stepMoves(Board board, int from, int side,
            int[] deltaRank, int[] deltaFile, int[] moves, int count) {
        int rank = Board.rankOf(from);
        int file = Board.fileOf(from);
        for (int d = 0; d < deltaRank.length; d++) {
            int r = rank + deltaRank[d];
            int f = file + deltaFile[d];
            if (!onBoard(r, f)) {
                continue;
            }
            int to = Board.square(r, f);
            int target = board.squares[to];
            if (target * side > 0) {
                continue; // own piece
            }
            moves[count++] = Move.of(from, to,
                target == 0 ? Move.FLAG_QUIET : Move.FLAG_CAPTURE);
        }
        return count;
    }

    /** Rooks, bishops and queens: run until the edge or the first piece. */
    private static int slideMoves(Board board, int from, int side,
            int[] deltaRank, int[] deltaFile, int[] moves, int count) {
        int rank = Board.rankOf(from);
        int file = Board.fileOf(from);
        for (int d = 0; d < deltaRank.length; d++) {
            int r = rank;
            int f = file;
            while (true) {
                r += deltaRank[d];
                f += deltaFile[d];
                if (!onBoard(r, f)) {
                    break;
                }
                int to = Board.square(r, f);
                int target = board.squares[to];
                if (target == 0) {
                    moves[count++] = Move.of(from, to, Move.FLAG_QUIET);
                    continue;
                }
                if (target * side < 0) {
                    moves[count++] = Move.of(from, to, Move.FLAG_CAPTURE);
                }
                break; // blocked either way
            }
        }
        return count;
    }

    private static int pawnMoves(Board board, int from, int side, int[] moves, int count) {
        int rank = Board.rankOf(from);
        int file = Board.fileOf(from);
        int forward = side;                                 // white goes up a rank
        int startRank = side == Board.WHITE ? 1 : 6;
        int promoRank = side == Board.WHITE ? 7 : 0;

        int pushRank = rank + forward;
        if (onBoard(pushRank, file)) {
            int to = Board.square(pushRank, file);
            if (board.squares[to] == 0) {
                if (pushRank == promoRank) {
                    for (int flag : PROMO_FLAGS) {
                        moves[count++] = Move.of(from, to, flag);
                    }
                } else {
                    moves[count++] = Move.of(from, to, Move.FLAG_QUIET);

                    // Double push only from home, and only through an empty square.
                    if (rank == startRank) {
                        int doubleRank = rank + 2 * forward;
                        int doubleTo = Board.square(doubleRank, file);
                        if (board.squares[doubleTo] == 0) {
                            moves[count++] = Move.of(from, doubleTo, Move.FLAG_DOUBLE_PAWN_PUSH);
                        }
                    }
                }
            }
        }

        for (int deltaFile = -1; deltaFile <= 1; deltaFile += 2) {
            int captureFile = file + deltaFile;
            if (!onBoard(pushRank, captureFile)) {
                continue;
            }
            int to = Board.square(pushRank, captureFile);
            int target = board.squares[to];

            if (target * side < 0) {
                if (pushRank == promoRank) {
                    for (int flag : PROMO_CAPTURE_FLAGS) {
                        moves[count++] = Move.of(from, to, flag);
                    }
                } else {
                    moves[count++] = Move.of(from, to, Move.FLAG_CAPTURE);
                }
            } else if (to == board.enPassantSquare) {
                // The en passant target square is empty, so the test above
                // cannot find it. The victim sits beside the capturing pawn.
                moves[count++] = Move.of(from, to, Move.FLAG_EN_PASSANT);
            }
        }
        return count;
    }

    private static int castleMoves(Board board, int side, int[] moves, int count) {
        int homeRank = side == Board.WHITE ? 0 : 7;
        int kingSquare = Board.square(homeRank, 4);
        int enemy = -side;

        boolean kingsideRight = side == Board.WHITE
            ? board.whiteCanCastleKingside : board.blackCanCastleKingside;
        boolean queensideRight = side == Board.WHITE
            ? board.whiteCanCastleQueenside : board.blackCanCastleQueenside;

        if (!kingsideRight && !queensideRight) {
            return count;
        }
        if (board.squares[kingSquare] != Board.KING * side) {
            return count;
        }
        // Having the right does not mean the king is safe where it stands.
        if (isSquareAttacked(board, kingSquare, enemy)) {
            return count;
        }

        if (kingsideRight
                && board.squares[Board.square(homeRank, 7)] == Board.ROOK * side
                && board.squares[Board.square(homeRank, 5)] == 0
                && board.squares[Board.square(homeRank, 6)] == 0
                && !isSquareAttacked(board, Board.square(homeRank, 5), enemy)
                && !isSquareAttacked(board, Board.square(homeRank, 6), enemy)) {
            moves[count++] = Move.of(kingSquare, Board.square(homeRank, 6),
                Move.FLAG_CASTLE_KINGSIDE);
        }

        // b1 and b8 must be empty but may be attacked: the king never crosses them.
        if (queensideRight
                && board.squares[Board.square(homeRank, 0)] == Board.ROOK * side
                && board.squares[Board.square(homeRank, 1)] == 0
                && board.squares[Board.square(homeRank, 2)] == 0
                && board.squares[Board.square(homeRank, 3)] == 0
                && !isSquareAttacked(board, Board.square(homeRank, 3), enemy)
                && !isSquareAttacked(board, Board.square(homeRank, 2), enemy)) {
            moves[count++] = Move.of(kingSquare, Board.square(homeRank, 2),
                Move.FLAG_CASTLE_QUEENSIDE);
        }
        return count;
    }

    /**
     * Whether {@code square} is attacked by {@code bySide}. Asked about empty
     * squares too, such as the ones a castling king crosses, so this reports
     * attack on the square and not capturability of anything standing there.
     */
    public static boolean isSquareAttacked(Board board, int square, int bySide) {
        int rank = Board.rankOf(square);
        int file = Board.fileOf(square);

        // Pawns. A bySide pawn attacking this square sits one rank back along
        // its own direction of travel, on an adjacent file.
        int pawnRank = rank - bySide;
        if (pawnRank >= 0 && pawnRank < 8) {
            int pawn = Board.PAWN * bySide;
            if (file > 0 && board.squares[Board.square(pawnRank, file - 1)] == pawn) {
                return true;
            }
            if (file < 7 && board.squares[Board.square(pawnRank, file + 1)] == pawn) {
                return true;
            }
        }

        if (stepAttacks(board, rank, file, KNIGHT_DR, KNIGHT_DF, Board.KNIGHT * bySide)) {
            return true;
        }
        if (stepAttacks(board, rank, file, KING_DR, KING_DF, Board.KING * bySide)) {
            return true;
        }
        if (slideAttacks(board, rank, file, ROOK_DR, ROOK_DF, bySide, Board.ROOK)) {
            return true;
        }
        return slideAttacks(board, rank, file, BISHOP_DR, BISHOP_DF, bySide, Board.BISHOP);
    }

    private static boolean stepAttacks(Board board, int rank, int file,
            int[] deltaRank, int[] deltaFile, int attacker) {
        for (int d = 0; d < deltaRank.length; d++) {
            int r = rank + deltaRank[d];
            int f = file + deltaFile[d];
            if (onBoard(r, f) && board.squares[Board.square(r, f)] == attacker) {
                return true;
            }
        }
        return false;
    }

    /**
     * Walks out from the square; the first piece met in each direction is the
     * only one that can attack along it. Counts as an attack when that piece
     * belongs to {@code bySide} and is either {@code straightLine} or a queen.
     */
    private static boolean slideAttacks(Board board, int rank, int file,
            int[] deltaRank, int[] deltaFile, int bySide, int straightLine) {
        for (int d = 0; d < deltaRank.length; d++) {
            int r = rank;
            int f = file;
            while (true) {
                r += deltaRank[d];
                f += deltaFile[d];
                if (!onBoard(r, f)) {
                    break;
                }
                int piece = board.squares[Board.square(r, f)];
                if (piece == 0) {
                    continue;
                }
                if (piece * bySide > 0) {
                    int type = Math.abs(piece);
                    if (type == straightLine || type == Board.QUEEN) {
                        return true;
                    }
                }
                break; // blocked
            }
        }
        return false;
    }
}
