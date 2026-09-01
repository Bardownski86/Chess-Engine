package engine;

/**
 * Reads and writes Forsyth-Edwards Notation, the standard way to write down a
 * chess position. Every published test position is given as FEN, so this is
 * what lets the engine be checked against known-good numbers instead of only
 * ever starting from move one.
 *
 * <p>A FEN has six fields, separated by spaces:
 *
 * <pre>
 *   rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1
 *   |                                           | |    | | |
 *   pieces, rank 8 first                        | |    | | fullmove number
 *                                  side to move | |    | halfmove clock
 *                                 castling rights |    en passant target
 * </pre>
 *
 * The last two fields are often omitted in published positions, so they default
 * to 0 and 1 when missing.
 */
public final class Fen {

    public static final String START =
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private Fen() {
    }

    public static Board parse(String fen) {
        String[] fields = fen.trim().split("\\s+");
        if (fields.length < 4) {
            throw new IllegalArgumentException("FEN needs at least 4 fields: " + fen);
        }

        Board board = new Board();
        for (int sq = 0; sq < 64; sq++) {
            board.squares[sq] = 0;
        }

        String[] rows = fields[0].split("/");
        if (rows.length != 8) {
            throw new IllegalArgumentException("FEN needs 8 ranks: " + fields[0]);
        }
        // FEN writes rank 8 first, so row 0 is rank index 7.
        for (int row = 0; row < 8; row++) {
            int rank = 7 - row;
            int file = 0;
            for (int i = 0; i < rows[row].length(); i++) {
                char c = rows[row].charAt(i);
                if (c >= '1' && c <= '8') {
                    file += c - '0';
                } else {
                    if (file > 7) {
                        throw new IllegalArgumentException("rank overflows: " + rows[row]);
                    }
                    board.squares[Board.square(rank, file)] = pieceFromChar(c);
                    file++;
                }
            }
            if (file != 8) {
                throw new IllegalArgumentException("rank is not 8 files wide: " + rows[row]);
            }
        }

        board.sideToMove = fields[1].equals("b") ? Board.BLACK : Board.WHITE;

        board.whiteCanCastleKingside = fields[2].indexOf('K') >= 0;
        board.whiteCanCastleQueenside = fields[2].indexOf('Q') >= 0;
        board.blackCanCastleKingside = fields[2].indexOf('k') >= 0;
        board.blackCanCastleQueenside = fields[2].indexOf('q') >= 0;

        board.enPassantSquare = fields[3].equals("-") ? -1 : squareFromName(fields[3]);

        board.halfmoveClock = fields.length > 4 ? Integer.parseInt(fields[4]) : 0;
        board.fullmoveNumber = fields.length > 5 ? Integer.parseInt(fields[5]) : 1;

        return board;
    }

    public static String toFen(Board board) {
        StringBuilder sb = new StringBuilder();

        for (int rank = 7; rank >= 0; rank--) {
            int empty = 0;
            for (int file = 0; file < 8; file++) {
                int piece = board.squares[Board.square(rank, file)];
                if (piece == 0) {
                    empty++;
                    continue;
                }
                if (empty > 0) {
                    sb.append(empty);
                    empty = 0;
                }
                sb.append(charFromPiece(piece));
            }
            if (empty > 0) {
                sb.append(empty);
            }
            if (rank > 0) {
                sb.append('/');
            }
        }

        sb.append(board.sideToMove == Board.WHITE ? " w " : " b ");

        int before = sb.length();
        if (board.whiteCanCastleKingside) sb.append('K');
        if (board.whiteCanCastleQueenside) sb.append('Q');
        if (board.blackCanCastleKingside) sb.append('k');
        if (board.blackCanCastleQueenside) sb.append('q');
        if (sb.length() == before) {
            sb.append('-');
        }

        sb.append(' ');
        sb.append(board.enPassantSquare < 0 ? "-" : Board.squareName(board.enPassantSquare));
        sb.append(' ').append(board.halfmoveClock);
        sb.append(' ').append(board.fullmoveNumber);
        return sb.toString();
    }

    private static int pieceFromChar(char c) {
        int type;
        switch (Character.toLowerCase(c)) {
            case 'p': type = Board.PAWN; break;
            case 'n': type = Board.KNIGHT; break;
            case 'b': type = Board.BISHOP; break;
            case 'r': type = Board.ROOK; break;
            case 'q': type = Board.QUEEN; break;
            case 'k': type = Board.KING; break;
            default: throw new IllegalArgumentException("not a piece: " + c);
        }
        return Character.isUpperCase(c) ? type : -type;
    }

    private static char charFromPiece(int piece) {
        char letter;
        switch (Math.abs(piece)) {
            case Board.PAWN: letter = 'p'; break;
            case Board.KNIGHT: letter = 'n'; break;
            case Board.BISHOP: letter = 'b'; break;
            case Board.ROOK: letter = 'r'; break;
            case Board.QUEEN: letter = 'q'; break;
            case Board.KING: letter = 'k'; break;
            default: throw new IllegalArgumentException("not a piece: " + piece);
        }
        return piece > 0 ? Character.toUpperCase(letter) : letter;
    }

    /** Turns algebraic like {@code e3} back into a square index. */
    public static int squareFromName(String name) {
        int file = name.charAt(0) - 'a';
        int rank = name.charAt(1) - '1';
        if (file < 0 || file > 7 || rank < 0 || rank > 7) {
            throw new IllegalArgumentException("not a square: " + name);
        }
        return Board.square(rank, file);
    }
}
