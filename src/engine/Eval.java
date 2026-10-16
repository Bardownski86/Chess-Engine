package engine;

/**
 * Static evaluation: material plus piece-square tables.
 *
 * <p>The score is in centipawns and is returned from the point of view of the
 * side to move, which is what negamax needs. A positive score means the side to
 * move is better off.
 *
 * <p>Tables are written rank 8 first, the way they are normally published, so a
 * table reads like a board seen from white's side. Square 0 of this file is a8
 * while square 0 of {@link Board} is a1, which is why white reads the table at
 * {@code (7 - rank) * 8 + file} and black, whose tables are mirrored, reads it
 * at {@code rank * 8 + file}.
 */
public final class Eval {

    public static final int[] PIECE_VALUE = { 0, 100, 320, 330, 500, 900, 0 };

    /** Below this much non-pawn material the king wants the centre, not a corner. */
    private static final int ENDGAME_MATERIAL = 1300;

    private static final int[] PAWN_TABLE = {
          0,  0,  0,  0,  0,  0,  0,  0,
         50, 50, 50, 50, 50, 50, 50, 50,
         10, 10, 20, 30, 30, 20, 10, 10,
          5,  5, 10, 25, 25, 10,  5,  5,
          0,  0,  0, 20, 20,  0,  0,  0,
          5, -5,-10,  0,  0,-10, -5,  5,
          5, 10, 10,-20,-20, 10, 10,  5,
          0,  0,  0,  0,  0,  0,  0,  0,
    };

    private static final int[] KNIGHT_TABLE = {
        -50,-40,-30,-30,-30,-30,-40,-50,
        -40,-20,  0,  0,  0,  0,-20,-40,
        -30,  0, 10, 15, 15, 10,  0,-30,
        -30,  5, 15, 20, 20, 15,  5,-30,
        -30,  0, 15, 20, 20, 15,  0,-30,
        -30,  5, 10, 15, 15, 10,  5,-30,
        -40,-20,  0,  5,  5,  0,-20,-40,
        -50,-40,-30,-30,-30,-30,-40,-50,
    };

    private static final int[] BISHOP_TABLE = {
        -20,-10,-10,-10,-10,-10,-10,-20,
        -10,  0,  0,  0,  0,  0,  0,-10,
        -10,  0,  5, 10, 10,  5,  0,-10,
        -10,  5,  5, 10, 10,  5,  5,-10,
        -10,  0, 10, 10, 10, 10,  0,-10,
        -10, 10, 10, 10, 10, 10, 10,-10,
        -10,  5,  0,  0,  0,  0,  5,-10,
        -20,-10,-10,-10,-10,-10,-10,-20,
    };

    private static final int[] ROOK_TABLE = {
          0,  0,  0,  0,  0,  0,  0,  0,
          5, 10, 10, 10, 10, 10, 10,  5,
         -5,  0,  0,  0,  0,  0,  0, -5,
         -5,  0,  0,  0,  0,  0,  0, -5,
         -5,  0,  0,  0,  0,  0,  0, -5,
         -5,  0,  0,  0,  0,  0,  0, -5,
         -5,  0,  0,  0,  0,  0,  0, -5,
          0,  0,  0,  5,  5,  0,  0,  0,
    };

    private static final int[] QUEEN_TABLE = {
        -20,-10,-10, -5, -5,-10,-10,-20,
        -10,  0,  0,  0,  0,  0,  0,-10,
        -10,  0,  5,  5,  5,  5,  0,-10,
         -5,  0,  5,  5,  5,  5,  0, -5,
          0,  0,  5,  5,  5,  5,  0, -5,
        -10,  5,  5,  5,  5,  5,  0,-10,
        -10,  0,  5,  0,  0,  0,  0,-10,
        -20,-10,-10, -5, -5,-10,-10,-20,
    };

    private static final int[] KING_MIDDLE_TABLE = {
        -30,-40,-40,-50,-50,-40,-40,-30,
        -30,-40,-40,-50,-50,-40,-40,-30,
        -30,-40,-40,-50,-50,-40,-40,-30,
        -30,-40,-40,-50,-50,-40,-40,-30,
        -20,-30,-30,-40,-40,-30,-30,-20,
        -10,-20,-20,-20,-20,-20,-20,-10,
         20, 20,  0,  0,  0,  0, 20, 20,
         20, 30, 10,  0,  0, 10, 30, 20,
    };

    private static final int[] KING_END_TABLE = {
        -50,-40,-30,-20,-20,-30,-40,-50,
        -30,-20,-10,  0,  0,-10,-20,-30,
        -30,-10, 20, 30, 30, 20,-10,-30,
        -30,-10, 30, 40, 40, 30,-10,-30,
        -30,-10, 30, 40, 40, 30,-10,-30,
        -30,-10, 20, 30, 30, 20,-10,-30,
        -30,-30,  0,  0,  0,  0,-30,-30,
        -50,-30,-30,-30,-30,-30,-30,-50,
    };

    private Eval() {
    }

    /** Centipawn score from the point of view of the side to move. */
    public static int evaluate(Board board) {
        int nonPawnMaterial = 0;
        for (int sq = 0; sq < 64; sq++) {
            int type = Math.abs(board.squares[sq]);
            if (type != 0 && type != Board.PAWN && type != Board.KING) {
                nonPawnMaterial += PIECE_VALUE[type];
            }
        }
        boolean endgame = nonPawnMaterial <= ENDGAME_MATERIAL;

        int whiteRelative = 0;
        for (int sq = 0; sq < 64; sq++) {
            int piece = board.squares[sq];
            if (piece == 0) {
                continue;
            }
            int type = Math.abs(piece);
            int tableIndex = piece > 0
                ? (7 - Board.rankOf(sq)) * 8 + Board.fileOf(sq)
                : Board.rankOf(sq) * 8 + Board.fileOf(sq);
            int score = PIECE_VALUE[type] + tableFor(type, endgame)[tableIndex];
            whiteRelative += piece > 0 ? score : -score;
        }
        return board.sideToMove == Board.WHITE ? whiteRelative : -whiteRelative;
    }

    private static int[] tableFor(int type, boolean endgame) {
        switch (type) {
            case Board.PAWN: return PAWN_TABLE;
            case Board.KNIGHT: return KNIGHT_TABLE;
            case Board.BISHOP: return BISHOP_TABLE;
            case Board.ROOK: return ROOK_TABLE;
            case Board.QUEEN: return QUEEN_TABLE;
            case Board.KING: return endgame ? KING_END_TABLE : KING_MIDDLE_TABLE;
            default: throw new IllegalArgumentException("not a piece type: " + type);
        }
    }
}
