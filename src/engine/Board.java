package engine;

public class Board {
    public static final int PAWN = 1;
    public static final int KNIGHT = 2;
    public static final int BISHOP = 3;
    public static final int ROOK = 4;
    public static final int QUEEN = 5;
    public static final int KING = 6;
    public static final int WHITE = 1;
    public static final int BLACK = -1;
    private static final int[] backRank = { ROOK, KNIGHT, BISHOP, QUEEN, KING, BISHOP, KNIGHT, ROOK};

    /** Rook home squares. Castling rights die when a rook leaves or is taken on one. */
    private static final int A1 = 0, H1 = 7, A8 = 56, H8 = 63;

    /** Deepest sequence of unmade moves supported. Game length plus search depth. */
    private static final int MAX_PLY = 1024;

    int[] squares = new int[64];
    int sideToMove;
    boolean whiteCanCastleKingside, whiteCanCastleQueenside, blackCanCastleKingside, blackCanCastleQueenside;
    int enPassantSquare;
    int halfmoveClock;
    int fullmoveNumber;

    /**
     * Zobrist key of the current position, maintained incrementally. Used to
     * recognise a position the game has already been in.
     */
    long hash;

    /**
     * State that a move destroys and that cannot be reconstructed from the move
     * itself, saved so unmakeMove can put it back. Preallocated once: search
     * calls make/unmake millions of times and should not allocate per move.
     */
    private final Undo[] history = new Undo[MAX_PLY];
    private int ply;

    public Board() {
        createStart();
        sideToMove = WHITE;
        whiteCanCastleKingside = true;
        whiteCanCastleQueenside = true;
        blackCanCastleKingside = true;
        blackCanCastleQueenside = true;
        enPassantSquare = -1;
        halfmoveClock = 0;
        fullmoveNumber = 1;
        for (int i = 0; i < MAX_PLY; i++) {
            history[i] = new Undo();
        }
        ply = 0;
        hash = Zobrist.compute(this);
    }

    public long hash() {
        return hash;
    }

    /**
     * Recomputes the key from scratch. Needed after anything sets up a position
     * by writing squares and flags directly rather than by playing moves.
     */
    public void refreshHash() {
        hash = Zobrist.compute(this);
    }

    /**
     * Whether the current position has occurred before in this game. Only looks
     * back as far as the halfmove clock, since a pawn move or a capture makes
     * every earlier position unreachable, and in steps of two because a
     * repetition has to have the same side to move.
     */
    public boolean isRepetition() {
        int limit = Math.min(ply, halfmoveClock);
        for (int back = 2; back <= limit; back += 2) {
            if (history[ply - back].hash == hash) {
                return true;
            }
        }
        return false;
    }

    public static int square(int rank, int file) {
        return rank * 8 + file;
    }

    private void createStart() {
        for(int file = 0; file < 8; file++) {
            squares[square(0, file)] = backRank[file];
            squares[square(1, file)] = PAWN;
            squares[square(6, file)] = -PAWN;
            squares[square(7, file)] = -backRank[file];
        }
    }

    public static int fileOf(int sq) {
        return sq%8;
    }

    public static int rankOf(int sq) {
        return sq/8;
    }

    /**
     * The square in algebraic notation, for example {@code e4}. Square 0 is a1,
     * so file 0 is the a file and rank 0 is rank 1.
     */
    public static String squareName(int sq) {
        return "" + (char) ('a' + fileOf(sq)) + (char) ('1' + rankOf(sq));
    }

    /**
     * The square holding the pawn taken by an en passant capture. It is not the
     * square the capturing pawn lands on: the victim sits beside the capturing
     * pawn, on its rank and on the destination file.
     */
    private static int enPassantVictimSquare(int from, int to) {
        return square(rankOf(from), fileOf(to));
    }

    public void makeMove(int move) {
        int from = Move.from(move);
        int to = Move.to(move);
        int piece = squares[from];
        int pieceType = Math.abs(piece);

        assert ply < MAX_PLY : "undo stack overflow at ply " + ply;
        assert piece != 0 : "no piece to move on " + squareName(from);

        Undo undo = history[ply];
        undo.whiteCanCastleKingside = whiteCanCastleKingside;
        undo.whiteCanCastleQueenside = whiteCanCastleQueenside;
        undo.blackCanCastleKingside = blackCanCastleKingside;
        undo.blackCanCastleQueenside = blackCanCastleQueenside;
        undo.enPassantSquare = enPassantSquare;
        undo.halfmoveClock = halfmoveClock;
        undo.hash = hash;

        // Castling rights and the en passant file are XORed out as a block
        // before they change, and the new value XORed back in at the end.
        hash ^= Zobrist.state(this);

        // Read the victim off the board rather than trusting the capture flag,
        // so an imprecise flag cannot corrupt the saved undo state.
        if (Move.isEnPassant(move)) {
            int victim = enPassantVictimSquare(from, to);
            undo.captured = squares[victim];
            squares[victim] = 0;
            hash ^= Zobrist.piece(undo.captured, victim);
        } else {
            undo.captured = squares[to];
            if (undo.captured != 0) {
                hash ^= Zobrist.piece(undo.captured, to);
            }
        }
        ply++;

        squares[to] = Move.isPromotion(move) ? Move.promotionPiece(move) * sideToMove : piece;
        squares[from] = 0;
        hash ^= Zobrist.piece(piece, from);
        hash ^= Zobrist.piece(squares[to], to);

        if (Move.isCastle(move)) {
            boolean kingside = Move.flag(move) == Move.FLAG_CASTLE_KINGSIDE;
            int rookFrom = kingside ? to + 1 : to - 2;
            int rookTo = kingside ? to - 1 : to + 1;
            int rook = squares[rookFrom];
            squares[rookTo] = rook;
            squares[rookFrom] = 0;
            hash ^= Zobrist.piece(rook, rookFrom);
            hash ^= Zobrist.piece(rook, rookTo);
        }

        if (pieceType == KING) {
            if (sideToMove == WHITE) {
                whiteCanCastleKingside = false;
                whiteCanCastleQueenside = false;
            } else {
                blackCanCastleKingside = false;
                blackCanCastleQueenside = false;
            }
        }
        // Covers a rook leaving home and a rook being captured on home in one test.
        if (from == A1 || to == A1) whiteCanCastleQueenside = false;
        if (from == H1 || to == H1) whiteCanCastleKingside = false;
        if (from == A8 || to == A8) blackCanCastleQueenside = false;
        if (from == H8 || to == H8) blackCanCastleKingside = false;

        enPassantSquare = Move.isDoublePawnPush(move) ? (from + to) / 2 : -1;
        halfmoveClock = (pieceType == PAWN || undo.captured != 0) ? 0 : halfmoveClock + 1;
        if (sideToMove == BLACK) {
            fullmoveNumber++;
        }
        sideToMove = -sideToMove;

        hash ^= Zobrist.state(this);
        hash ^= Zobrist.SIDE;
        assert hash == Zobrist.compute(this)
            : "incremental hash diverged after " + Move.toUci(move);
    }

    public void unmakeMove(int move) {
        int from = Move.from(move);
        int to = Move.to(move);

        assert ply > 0 : "unmakeMove with nothing left to unmake";

        // Flip first, so sideToMove names the side that played this move.
        sideToMove = -sideToMove;
        if (sideToMove == BLACK) {
            fullmoveNumber--;
        }

        ply--;
        Undo undo = history[ply];

        squares[from] = Move.isPromotion(move) ? PAWN * sideToMove : squares[to];
        squares[to] = 0;

        if (Move.isEnPassant(move)) {
            squares[enPassantVictimSquare(from, to)] = undo.captured;
        } else {
            squares[to] = undo.captured;
        }

        if (Move.isCastle(move)) {
            boolean kingside = Move.flag(move) == Move.FLAG_CASTLE_KINGSIDE;
            int rookFrom = kingside ? to + 1 : to - 2;
            int rookTo = kingside ? to - 1 : to + 1;
            squares[rookFrom] = squares[rookTo];
            squares[rookTo] = 0;
        }

        whiteCanCastleKingside = undo.whiteCanCastleKingside;
        whiteCanCastleQueenside = undo.whiteCanCastleQueenside;
        blackCanCastleKingside = undo.blackCanCastleKingside;
        blackCanCastleQueenside = undo.blackCanCastleQueenside;
        enPassantSquare = undo.enPassantSquare;
        halfmoveClock = undo.halfmoveClock;

        // Restoring the saved key is exact, so unmake cannot drift.
        hash = undo.hash;
        assert hash == Zobrist.compute(this)
            : "hash wrong after unmaking " + Move.toUci(move);
    }

    private char pieceChar(int piece) {
        if(piece == 0) {
            return '.';
        }
        char letter = ' ';
        switch (Math.abs(piece)) {
            case PAWN -> letter = 'p';
            case KNIGHT-> letter = 'n';
            case BISHOP -> letter = 'b';
            case ROOK -> letter = 'r';
            case QUEEN -> letter = 'q';
            case KING-> letter = 'k';
        }
        if(piece > 0) {
            letter = Character.toUpperCase(letter);
        }
        return letter;

    }
    @Override
    public String toString() {
        String notedBoard = "";
        for(int rank = 7; rank >= 0; rank--){
            for(int file = 0; file < 8; file++) {
                notedBoard += " " + pieceChar(squares[square(rank, file)]);
            }
            notedBoard += "\n";
        }
        return notedBoard;
    }

    private static final class Undo {
        int captured;
        long hash;
        boolean whiteCanCastleKingside, whiteCanCastleQueenside;
        boolean blackCanCastleKingside, blackCanCastleQueenside;
        int enPassantSquare;
        int halfmoveClock;
    }
}
