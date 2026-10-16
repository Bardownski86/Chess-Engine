package engine;

public class SelfPlay {
    public static void main(String[] args) {
        int maxMoves = args.length > 0 ? Integer.parseInt(args[0]) : 120;
        long perMoveMs = args.length > 1 ? Long.parseLong(args[1]) : 120;

        Board board = Fen.parse(Fen.START);
        Search search = new Search();
        int[] legal = new int[MoveGen.MAX_MOVES];
        StringBuilder log = new StringBuilder();
        int played = 0;
        String ending = "move limit reached";

        for (int i = 0; i < maxMoves; i++) {
            int count = MoveGen.generate(board, legal);
            if (count == 0) {
                ending = MoveGen.isInCheck(board, board.sideToMove)
                    ? (board.sideToMove == Board.WHITE ? "white is mated" : "black is mated")
                    : "stalemate";
                break;
            }
            if (board.halfmoveClock >= 100) { ending = "fifty move rule"; break; }

            int move = search.findBestMove(board, 64, perMoveMs);
            if (move == Move.NONE) { ending = "search returned no move"; break; }

            boolean isLegal = false;
            for (int j = 0; j < count; j++) {
                if (legal[j] == move) { isLegal = true; break; }
            }
            if (!isLegal) {
                System.out.println("ILLEGAL MOVE " + Move.toUci(move) + " in " + Fen.toFen(board));
                return;
            }
            board.makeMove(move);
            log.append(Move.toUci(move)).append(' ');
            played++;
        }

        System.out.println();
        System.out.println("played " + played + " moves, ended: " + ending);
        System.out.println(log.toString().trim());
        System.out.println();
        System.out.print(board);
        System.out.println("ALL MOVES LEGAL");
    }
}
