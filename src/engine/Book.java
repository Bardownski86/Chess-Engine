package engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A small opening book of mainline theory.
 *
 * <p>Without one the engine plays the same move every time, because the search
 * is deterministic and the opening position never changes. Randomising the
 * search instead would buy variety by playing worse moves; a book buys it while
 * every move stays theory.
 *
 * <p>Lines are stored as the move sequence from the start position. To pick a
 * move, every line whose opening moves match the game so far contributes its
 * next move as a candidate, and one is drawn at random. Lines that share a move
 * contribute it more than once, so popular moves come up more often -- most
 * lines here begin 1.e4, so the engine opens with it about half the time,
 * which is roughly how often humans do.
 *
 * <p>This is deliberately a few dozen lines rather than a Polyglot .bin of
 * millions. A Polyglot reader needs that format's own 781-number Zobrist table,
 * which is not the one {@link Zobrist} uses, plus its own move encoding where
 * castling is written as king-takes-rook. Worth doing later; not worth it for
 * opening variety alone.
 */
public final class Book {

    private static final String[] LINES = {
        // --- 1.e4 e5 ---
        "e2e4 e7e5 g1f3 b8c6 f1b5 a7a6 b5a4 g8f6",      // Ruy Lopez, Morphy
        "e2e4 e7e5 g1f3 b8c6 f1b5 g8f6",                // Ruy Lopez, Berlin
        "e2e4 e7e5 g1f3 b8c6 f1c4 f8c5 c2c3 g8f6",      // Italian, Giuoco Piano
        "e2e4 e7e5 g1f3 b8c6 f1c4 g8f6 d2d3 f8c5",      // Italian, Giuoco Pianissimo
        "e2e4 e7e5 g1f3 b8c6 d2d4 e5d4 f3d4 f8c5",      // Scotch
        "e2e4 e7e5 g1f3 g8f6 f3e5 d7d6 e5f3 f6e4",      // Petroff
        "e2e4 e7e5 g1f3 b8c6 b1c3 g8f6 f1b5 f8b4",      // Four Knights
        "e2e4 e7e5 b1c3 g8f6 f2f4 d7d5",                // Vienna

        // --- 1.e4 c5, Sicilian ---
        "e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f3d4 g8f6 b1c3 a7a6",  // Najdorf
        "e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f3d4 g8f6 b1c3 g7g6",  // Dragon
        "e2e4 c7c5 g1f3 b8c6 d2d4 c5d4 f3d4 g8f6 b1c3 d7d6",  // Classical
        "e2e4 c7c5 g1f3 e7e6 d2d4 c5d4 f3d4 a7a6",            // Kan
        "e2e4 c7c5 c2c3 d7d5 e4d5 d8d5 d2d4 g8f6",            // Alapin
        "e2e4 c7c5 b1c3 b8c6 g2g3 g7g6 f1g2 f8g7",            // Closed Sicilian

        // --- 1.e4 other ---
        "e2e4 e7e6 d2d4 d7d5 b1c3 f8b4",                // French, Winawer
        "e2e4 e7e6 d2d4 d7d5 b1c3 g8f6 c1g5 f8e7",      // French, Classical
        "e2e4 e7e6 d2d4 d7d5 e4e5 c7c5 c2c3 b8c6",      // French, Advance
        "e2e4 c7c6 d2d4 d7d5 b1c3 d5e4 c3e4 c8f5",      // Caro-Kann, Classical
        "e2e4 c7c6 d2d4 d7d5 e4e5 c8f5",                // Caro-Kann, Advance
        "e2e4 d7d5 e4d5 d8d5 b1c3 d5a5",                // Scandinavian
        "e2e4 g8f6 e4e5 f6d5 d2d4 d7d6",                // Alekhine
        "e2e4 d7d6 d2d4 g8f6 b1c3 g7g6 f2f4 f8g7",      // Pirc, Austrian
        "e2e4 g7g6 d2d4 f8g7 b1c3 d7d6",                // Modern

        // --- 1.d4 d5 ---
        "d2d4 d7d5 c2c4 e7e6 b1c3 g8f6 c1g5 f8e7",      // Queen's Gambit Declined
        "d2d4 d7d5 c2c4 d5c4 g1f3 g8f6 e2e3",           // Queen's Gambit Accepted
        "d2d4 d7d5 c2c4 c7c6 g1f3 g8f6 b1c3 d5c4",      // Slav
        "d2d4 d7d5 c2c4 c7c6 g1f3 g8f6 b1c3 e7e6",      // Semi-Slav
        "d2d4 d7d5 c1f4 g8f6 e2e3 e7e6",                // London

        // --- 1.d4 Nf6 ---
        "d2d4 g8f6 c2c4 e7e6 b1c3 f8b4 e2e3 e8g8",      // Nimzo-Indian
        "d2d4 g8f6 c2c4 e7e6 g1f3 b7b6 g2g3 c8b7",      // Queen's Indian
        "d2d4 g8f6 c2c4 g7g6 b1c3 f8g7 e2e4 d7d6",      // King's Indian
        "d2d4 g8f6 c2c4 g7g6 b1c3 d7d5 c4d5 f6d5",      // Gruenfeld
        "d2d4 g8f6 c2c4 c7c5 d4d5 e7e6",                // Benoni
        "d2d4 f7f5 g2g3 g8f6 f1g2 e7e6",                // Dutch

        // --- flank openings ---
        "c2c4 e7e5 b1c3 g8f6 g2g3 d7d5",                // English, reversed Sicilian
        "c2c4 c7c5 g1f3 g8f6 b1c3 b8c6",                // English, symmetrical
        "g1f3 d7d5 c2c4 e7e6 g2g3 g8f6",                // Reti
        "g1f3 d7d5 g2g3 g8f6 f1g2 e7e6",                // King's Indian Attack
    };

    private static final Random RNG = new Random();

    private Book() {
    }

    /** The raw lines, so a test can check every one of them is legal. */
    static String[] lines() {
        return LINES;
    }

    /**
     * A book move for the position reached by {@code played} from the start,
     * or {@link Move#NONE} if the game has left the book.
     *
     * <p>The chosen move is checked against the legal moves before being
     * returned, so a typo in the table above can never put an illegal move on
     * the board -- it just drops the engine out of book.
     */
    public static int probe(List<String> played, Board board) {
        List<String> candidates = new ArrayList<>();
        for (String line : LINES) {
            String[] moves = line.split(" ");
            if (moves.length <= played.size()) {
                continue;
            }
            boolean matches = true;
            for (int i = 0; i < played.size(); i++) {
                if (!moves[i].equals(played.get(i))) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                candidates.add(moves[played.size()]);
            }
        }
        if (candidates.isEmpty()) {
            return Move.NONE;
        }

        String chosen = candidates.get(RNG.nextInt(candidates.size()));
        int[] legal = new int[MoveGen.MAX_MOVES];
        int count = MoveGen.generate(board, legal);
        for (int i = 0; i < count; i++) {
            if (Move.toUci(legal[i]).equals(chosen)) {
                return legal[i];
            }
        }
        return Move.NONE;
    }
}
