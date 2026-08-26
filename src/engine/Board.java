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

    int[] squares = new int[64];
    int sideToMove;
    boolean whiteCanCastleKingside, whiteCanCastleQueenside, blackCanCastleKingside, blackCanCastleQueenside;
    int enPassantSquare;
    int halfmoveClock;
    int fullmoveNumber;



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
}
