package engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Play the engine from the terminal.
 *
 * <p>Moves are typed in the same long algebraic form the UCI protocol uses:
 * the square moved from, then the square moved to, then a promotion piece if
 * there is one. So {@code e2e4}, {@code g1f3}, {@code e7e8q}. Castling is
 * entered as the king's move, {@code e1g1} or {@code e1c1}.
 *
 * <p>This exists for convenience; {@link Uci} is the real interface and is what
 * a chess GUI talks to.
 */
public class Play {

    private static final long DEFAULT_THINK_MS = 2_000;

    public static void main(String[] args) throws IOException {
        int humanSide = Board.WHITE;
        long thinkMs = DEFAULT_THINK_MS;

        for (String arg : args) {
            if (arg.equalsIgnoreCase("black")) {
                humanSide = Board.BLACK;
            } else if (arg.equalsIgnoreCase("white")) {
                humanSide = Board.WHITE;
            } else {
                try {
                    thinkMs = Long.parseLong(arg);
                } catch (NumberFormatException ignored) {
                    // not a number, not a colour: ignore it
                }
            }
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        Board board = Fen.parse(Fen.START);
        Search search = new Search();
        int[] legal = new int[MoveGen.MAX_MOVES];

        // Kept so 'undo' can hand the same moves back to unmakeMove, which
        // needs the move to know what it is reversing.
        int[] played = new int[1024];
        int playedCount = 0;

        System.out.println("You are " + (humanSide == Board.WHITE ? "white" : "black")
            + ". The engine thinks for " + thinkMs + " ms a move.");
        System.out.println("Type moves like e2e4 or e7e8q. Other commands:");
        System.out.println("  moves   list every legal move");
        System.out.println("  fen     print the position as FEN");
        System.out.println("  undo    take back your move and the reply");
        System.out.println("  quit    stop");
        System.out.println();

        while (true) {
            System.out.println(render(board));

            int count = MoveGen.generate(board, legal);
            if (count == 0) {
                if (MoveGen.isInCheck(board, board.sideToMove)) {
                    boolean humanLost = board.sideToMove == humanSide;
                    System.out.println(humanLost ? "Checkmate. The engine wins." : "Checkmate. You win.");
                } else {
                    System.out.println("Stalemate. Drawn.");
                }
                return;
            }
            if (board.halfmoveClock >= 100) {
                System.out.println("Draw by the fifty move rule.");
                return;
            }
            if (MoveGen.isInCheck(board, board.sideToMove)) {
                System.out.println("Check!");
            }

            if (board.sideToMove == humanSide) {
                System.out.print("your move: ");
                System.out.flush();
                String line = in.readLine();
                if (line == null) {
                    return;
                }
                line = line.trim();

                if (line.isEmpty()) {
                    continue;
                }
                if (line.equalsIgnoreCase("quit") || line.equalsIgnoreCase("exit")) {
                    return;
                }
                if (line.equalsIgnoreCase("fen")) {
                    System.out.println(Fen.toFen(board));
                    continue;
                }
                if (line.equalsIgnoreCase("moves")) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < count; i++) {
                        sb.append(Move.toUci(legal[i])).append(' ');
                    }
                    System.out.println(sb.toString().trim());
                    continue;
                }
                if (line.equalsIgnoreCase("undo")) {
                    if (playedCount < 2) {
                        System.out.println("Nothing to undo yet.");
                        continue;
                    }
                    // Two plies: the engine's reply and your own move.
                    board.unmakeMove(played[--playedCount]);
                    board.unmakeMove(played[--playedCount]);
                    System.out.println("took back the last move and the reply");
                    System.out.println();
                    continue;
                }

                int chosen = Move.NONE;
                for (int i = 0; i < count; i++) {
                    if (Move.toUci(legal[i]).equalsIgnoreCase(line)) {
                        chosen = legal[i];
                        break;
                    }
                }
                if (chosen == Move.NONE) {
                    System.out.println("Not a legal move here. Type 'moves' to see what is.");
                    continue;
                }
                board.makeMove(chosen);
                played[playedCount++] = chosen;
                System.out.println("you played " + Move.toUci(chosen));
                System.out.println();
            } else {
                java.util.List<String> uciSoFar = new java.util.ArrayList<>();
                for (int i = 0; i < playedCount; i++) {
                    uciSoFar.add(Move.toUci(played[i]));
                }
                int reply = Book.probe(uciSoFar, board);
                if (reply != Move.NONE) {
                    System.out.println("(book)");
                } else {
                    System.out.println("thinking...");
                    reply = search.findBestMove(board, 64, thinkMs);
                }
                if (reply == Move.NONE) {
                    System.out.println("The engine has no move.");
                    return;
                }
                board.makeMove(reply);
                played[playedCount++] = reply;
                System.out.println("engine plays " + Move.toUci(reply)
                    + "   (depth " + search.lastDepth() + ", " + search.nodes() + " nodes)");
                System.out.println();
            }
        }
    }

    /** The board with rank and file labels, always from white's point of view. */
    private static String render(Board board) {
        StringBuilder sb = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            sb.append(rank + 1).append("  ");
            for (int file = 0; file < 8; file++) {
                sb.append(pieceChar(board.squares[Board.square(rank, file)])).append(' ');
            }
            sb.append('\n');
        }
        sb.append("\n   a b c d e f g h");
        return sb.toString();
    }

    private static char pieceChar(int piece) {
        if (piece == 0) {
            return '.';
        }
        char letter;
        switch (Math.abs(piece)) {
            case Board.PAWN: letter = 'p'; break;
            case Board.KNIGHT: letter = 'n'; break;
            case Board.BISHOP: letter = 'b'; break;
            case Board.ROOK: letter = 'r'; break;
            case Board.QUEEN: letter = 'q'; break;
            case Board.KING: letter = 'k'; break;
            default: letter = '?'; break;
        }
        return piece > 0 ? Character.toUpperCase(letter) : letter;
    }
}
