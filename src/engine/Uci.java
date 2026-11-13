package engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * The Universal Chess Interface, the text protocol every chess GUI speaks.
 * Running this class is what makes the engine usable in Arena, Cute Chess,
 * Banksia or En Croissant, and what lets it play other engines.
 *
 * <p>The protocol is deliberately stateless about the game: a GUI does not send
 * one move at a time, it re-sends the whole game on every turn as
 * {@code position startpos moves e2e4 e7e5 ...}. So each position command
 * rebuilds the board from scratch and replays the moves.
 *
 * <p>Moves arrive as text like {@code e2e4} or {@code e7e8q}, with no indication
 * of whether they capture, castle or take en passant. Rather than infer the
 * flags, the move is matched against the generated legal moves, which is both
 * shorter and impossible to get wrong.
 */
public final class Uci {

    private static final String NAME = "Chess-Engine";
    private static final String AUTHOR = "Bardownski86";

    /** Used when the GUI gives no clock information at all. */
    private static final long DEFAULT_MOVE_MS = 3_000;

    /** Fraction of the remaining clock to spend on one move. */
    private static final int MOVES_TO_GO_ASSUMED = 30;

    /**
     * Held back from every time allowance. An engine that spends its whole
     * budget still arrives late, because the clock the referee keeps also
     * covers writing the move out and reading it back; arbiters score that as
     * losing on time. Cute Chess forfeited every game until this existed.
     */
    private static final long MOVE_OVERHEAD_MS = 60;

    private Uci() {
    }

    public static void main(String[] args) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        Search search = new Search();
        Board board = Fen.parse(Fen.START);

        String line;
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] tokens = line.split("\\s+");

            switch (tokens[0]) {
                case "uci":
                    send("id name " + NAME);
                    send("id author " + AUTHOR);
                    send("uciok");
                    break;
                case "isready":
                    send("readyok");
                    break;
                case "ucinewgame":
                    board = Fen.parse(Fen.START);
                    break;
                case "position":
                    board = parsePosition(tokens);
                    break;
                case "go":
                    go(board, search, tokens);
                    break;
                case "stop":
                    break; // single threaded: the search has already returned
                case "quit":
                    return;
                case "d":
                    // Not part of UCI, but useful when driving the engine by hand.
                    System.out.print(board);
                    send(Fen.toFen(board));
                    break;
                default:
                    send("info string unknown command: " + tokens[0]);
                    break;
            }
        }
    }

    private static void send(String text) {
        System.out.println(text);
        System.out.flush();
    }

    private static Board parsePosition(String[] tokens) {
        Board board;
        int i;

        if (tokens.length > 1 && tokens[1].equals("fen")) {
            StringBuilder fen = new StringBuilder();
            i = 2;
            while (i < tokens.length && !tokens[i].equals("moves")) {
                fen.append(tokens[i]).append(' ');
                i++;
            }
            board = Fen.parse(fen.toString().trim());
        } else {
            board = Fen.parse(Fen.START);
            i = 2;
        }

        if (i < tokens.length && tokens[i].equals("moves")) {
            for (i++; i < tokens.length; i++) {
                int move = findMove(board, tokens[i]);
                if (move == Move.NONE) {
                    send("info string could not play " + tokens[i]);
                    break;
                }
                board.makeMove(move);
            }
        }
        return board;
    }

    /** Matches UCI move text against the legal moves, so flags never have to be guessed. */
    private static int findMove(Board board, String text) {
        int[] moves = new int[MoveGen.MAX_MOVES];
        int count = MoveGen.generate(board, moves);
        for (int i = 0; i < count; i++) {
            if (Move.toUci(moves[i]).equals(text)) {
                return moves[i];
            }
        }
        return Move.NONE;
    }

    private static void go(Board board, Search search, String[] tokens) {
        int depth = 64;
        long moveTime = -1;
        long myTime = -1;
        long myIncrement = 0;
        long movesToGo = 0;
        boolean infinite = false;

        for (int i = 1; i < tokens.length; i++) {
            switch (tokens[i]) {
                case "depth":
                    depth = Integer.parseInt(tokens[++i]);
                    break;
                case "movetime":
                    moveTime = Long.parseLong(tokens[++i]);
                    break;
                case "wtime":
                    if (board.sideToMove == Board.WHITE) {
                        myTime = Long.parseLong(tokens[++i]);
                    } else {
                        i++;
                    }
                    break;
                case "btime":
                    if (board.sideToMove == Board.BLACK) {
                        myTime = Long.parseLong(tokens[++i]);
                    } else {
                        i++;
                    }
                    break;
                case "winc":
                    if (board.sideToMove == Board.WHITE) {
                        myIncrement = Long.parseLong(tokens[++i]);
                    } else {
                        i++;
                    }
                    break;
                case "binc":
                    if (board.sideToMove == Board.BLACK) {
                        myIncrement = Long.parseLong(tokens[++i]);
                    } else {
                        i++;
                    }
                    break;
                case "movestogo":
                    movesToGo = Long.parseLong(tokens[++i]);
                    break;
                case "infinite":
                    infinite = true;
                    break;
                default:
                    break;
            }
        }

        long budget;
        if (moveTime > 0) {
            // Never spend the whole allowance: see MOVE_OVERHEAD_MS. On a very
            // short allowance keep most of it rather than reserving it all.
            budget = Math.max(moveTime / 2, moveTime - MOVE_OVERHEAD_MS);
        } else if (myTime > 0) {
            // Spread the clock over the moves actually left when the GUI says
            // how many there are, rather than guessing. The two spare moves
            // stop the allowance ballooning on the last move before the clock
            // is topped up.
            long horizon = movesToGo > 0 ? movesToGo + 2 : MOVES_TO_GO_ASSUMED;
            // Most of the increment is spendable: it is credited back after
            // the move, so using it does not eat into the base clock.
            long share = myTime / horizon + myIncrement * 3 / 4;
            // Never commit more than a third of what is left. One slow move
            // should not be able to lose the game on time by itself.
            long cap = Math.min(myTime - MOVE_OVERHEAD_MS, myTime / 3);
            budget = Math.max(10, Math.min(share, cap));
        } else {
            budget = infinite ? 60_000 : DEFAULT_MOVE_MS;
        }

        int best = search.findBestMove(board, depth, budget);
        send("bestmove " + (best == Move.NONE ? "0000" : Move.toUci(best)));
    }
}
