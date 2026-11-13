package engine;

/**
 * Alpha-beta search with iterative deepening and a quiescence search.
 *
 * <p>Negamax: one routine serves both sides by always scoring from the point of
 * view of the side to move and negating the child's score. Alpha-beta prunes
 * any line the opponent would never allow, which costs nothing in accuracy and
 * is why ordering matters so much -- searching the best move first makes the
 * cutoffs happen early. Captures are therefore tried first, ordered by most
 * valuable victim and least valuable attacker.
 *
 * <p>Quiescence search is what stops the engine hallucinating. A fixed depth
 * search can stop in the middle of a capture sequence and evaluate a position
 * where it is a queen up, having missed that the queen is recaptured next move.
 * At depth zero the search therefore keeps going, considering only captures and
 * promotions, until the position is quiet.
 */
public final class Search {

    public static final int MATE = 1_000_000;
    private static final int INFINITY = 2_000_000;

    /** Search depth plus however far quiescence runs on top of it. */
    private static final int MAX_PLY = 128;

    /**
     * Rough cost of the next depth as a multiple of the one just finished.
     * Alpha-beta with decent ordering lands somewhere around three to six; the
     * low end is deliberate, so a borderline iteration is attempted rather than
     * skipped.
     */
    private static final int BRANCHING_ESTIMATE = 3;

    private final int[][] moveBuffers = new int[MAX_PLY][MoveGen.MAX_MOVES];
    private final int[][] scoreBuffers = new int[MAX_PLY][MoveGen.MAX_MOVES];

    private long deadline;
    private boolean stopped;
    private long nodes;
    private int bestMoveRoot;

    private int lastScore;
    private int lastDepth;

    public long nodes() {
        return nodes;
    }

    public int lastScore() {
        return lastScore;
    }

    public int lastDepth() {
        return lastDepth;
    }

    /**
     * Searches to progressively greater depth until {@code maxDepth} or the time
     * runs out, printing a UCI info line per completed depth, and returns the
     * best move found.
     *
     * <p>Deepening iteratively rather than going straight to the target depth
     * means there is always a usable answer when the clock stops: a depth that
     * did not finish is thrown away and the previous one stands.
     */
    public int findBestMove(Board board, int maxDepth, long timeMs) {
        long start = System.currentTimeMillis();
        long budget = Math.max(1, timeMs);
        deadline = start + budget;
        stopped = false;
        nodes = 0;
        bestMoveRoot = Move.NONE;
        lastScore = 0;
        lastDepth = 0;

        // Fallback, so there is always something legal to play.
        int[] rootMoves = moveBuffers[0];
        int rootCount = MoveGen.generate(board, rootMoves);
        if (rootCount == 0) {
            return Move.NONE;
        }
        int best = rootMoves[0];

        // Only one legal reply: thinking about it cannot change it, and the
        // time is worth more on a move that has a decision in it.
        if (rootCount == 1) {
            lastDepth = 1;
            System.out.println("info depth 1 nodes 0 time 0 pv " + Move.toUci(best));
            return best;
        }

        long iterationStart = start;
        for (int depth = 1; depth <= Math.min(maxDepth, MAX_PLY - 2); depth++) {
            int score = negamax(board, depth, -INFINITY, INFINITY, 0, true);
            if (stopped) {
                break;
            }
            best = bestMoveRoot;
            lastScore = score;
            lastDepth = depth;

            long now = System.currentTimeMillis();
            long iterationMs = now - iterationStart;
            iterationStart = now;
            long elapsed = now - start;

            System.out.println("info depth " + depth + " score " + scoreToUci(score)
                + " nodes " + nodes + " time " + elapsed + " pv " + Move.toUci(best));

            // A forced mate is found; no deeper search changes the move.
            if (Math.abs(score) > MATE - MAX_PLY) {
                break;
            }

            // Each depth costs several times the one before it. Starting one
            // that cannot finish spends the rest of the allowance on a result
            // that is then thrown away, so stop here instead and keep the time
            // for a move that can still use it.
            if (elapsed + iterationMs * BRANCHING_ESTIMATE > budget) {
                break;
            }
        }
        return best;
    }

    private int negamax(Board board, int depth, int alpha, int beta, int ply, boolean isRoot) {
        nodes++;
        if (outOfTime()) {
            return 0;
        }

        // Draws. Not at the root, where a move must still be returned.
        // Scoring a repetition as nothing is what stops the engine shuffling a
        // winning position back and forth: repeating is worth zero, so any line
        // that makes progress beats it.
        if (!isRoot && (board.halfmoveClock >= 100 || board.isRepetition())) {
            return 0;
        }
        if (depth <= 0) {
            return quiesce(board, alpha, beta, ply);
        }

        int[] moves = moveBuffers[ply];
        int count = MoveGen.generate(board, moves);
        if (count == 0) {
            // Mate is scored by distance to it, so the engine prefers the
            // quickest mate and the slowest loss rather than treating all
            // mates as equal.
            return MoveGen.isInCheck(board, board.sideToMove) ? -MATE + ply : 0;
        }
        orderMoves(board, moves, count, ply);

        int best = -INFINITY;
        for (int i = 0; i < count; i++) {
            board.makeMove(moves[i]);
            int score = -negamax(board, depth - 1, -beta, -alpha, ply + 1, false);
            board.unmakeMove(moves[i]);

            if (stopped) {
                return 0;
            }
            if (score > best) {
                best = score;
                if (isRoot) {
                    bestMoveRoot = moves[i];
                }
            }
            if (score > alpha) {
                alpha = score;
            }
            if (alpha >= beta) {
                break; // the opponent would never let this line happen
            }
        }
        return best;
    }

    private int quiesce(Board board, int alpha, int beta, int ply) {
        nodes++;
        if (outOfTime()) {
            return 0;
        }
        if (ply >= MAX_PLY - 2) {
            return Eval.evaluate(board);
        }

        int[] moves = moveBuffers[ply];
        int count = MoveGen.generate(board, moves);
        if (count == 0) {
            return MoveGen.isInCheck(board, board.sideToMove) ? -MATE + ply : 0;
        }

        // Standing pat: the side to move is never obliged to capture, so the
        // static score is a lower bound on what this position is worth.
        int standPat = Eval.evaluate(board);
        if (standPat >= beta) {
            return standPat;
        }
        if (standPat > alpha) {
            alpha = standPat;
        }

        int tactical = 0;
        for (int i = 0; i < count; i++) {
            if (Move.isCapture(moves[i]) || Move.isPromotion(moves[i])) {
                moves[tactical++] = moves[i];
            }
        }
        orderMoves(board, moves, tactical, ply);

        int best = standPat;
        for (int i = 0; i < tactical; i++) {
            board.makeMove(moves[i]);
            int score = -quiesce(board, -beta, -alpha, ply + 1);
            board.unmakeMove(moves[i]);

            if (stopped) {
                return 0;
            }
            if (score > best) {
                best = score;
            }
            if (score > alpha) {
                alpha = score;
            }
            if (alpha >= beta) {
                break;
            }
        }
        return best;
    }

    /** Most valuable victim, least valuable attacker, promotions near the front. */
    private void orderMoves(Board board, int[] moves, int count, int ply) {
        int[] scores = scoreBuffers[ply];
        for (int i = 0; i < count; i++) {
            scores[i] = scoreMove(board, moves[i]);
        }
        // Selection sort: counts are small and this avoids boxing into a list.
        for (int i = 0; i < count - 1; i++) {
            int pick = i;
            for (int j = i + 1; j < count; j++) {
                if (scores[j] > scores[pick]) {
                    pick = j;
                }
            }
            if (pick != i) {
                int m = moves[i];
                moves[i] = moves[pick];
                moves[pick] = m;
                int s = scores[i];
                scores[i] = scores[pick];
                scores[pick] = s;
            }
        }
    }

    private int scoreMove(Board board, int move) {
        int score = 0;
        if (Move.isCapture(move)) {
            // An en passant victim is not on the destination square.
            int victim = Move.isEnPassant(move)
                ? Board.PAWN
                : Math.abs(board.squares[Move.to(move)]);
            int attacker = Math.abs(board.squares[Move.from(move)]);
            score += 100_000 + Eval.PIECE_VALUE[victim] * 16 - Eval.PIECE_VALUE[attacker];
        }
        if (Move.isPromotion(move)) {
            score += 90_000 + Eval.PIECE_VALUE[Move.promotionPiece(move)];
        }
        return score;
    }

    /** Checked every 1024 nodes; asking the clock on every node is slower than searching. */
    private boolean outOfTime() {
        if (!stopped && (nodes & 1023) == 0 && System.currentTimeMillis() >= deadline) {
            stopped = true;
        }
        return stopped;
    }

    /** UCI wants mates reported in moves to mate rather than as a centipawn score. */
    private static String scoreToUci(int score) {
        if (Math.abs(score) > MATE - MAX_PLY) {
            int plyToMate = MATE - Math.abs(score);
            int movesToMate = (plyToMate + 1) / 2;
            return "mate " + (score > 0 ? movesToMate : -movesToMate);
        }
        return "cp " + score;
    }
}
