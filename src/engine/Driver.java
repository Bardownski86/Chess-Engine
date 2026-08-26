package engine;

public class Driver {
    public static void main(String[] args) {
        Board board = new Board();
        System.out.println(board);

        int e2e4 = Move.of(Board.square(1, 4), Board.square(3, 4), Move.FLAG_DOUBLE_PAWN_PUSH);
        int promo = Move.of(Board.square(6, 1), Board.square(7, 0), Move.FLAG_PROMO_QUEEN_CAPTURE);

        System.out.println(describe(e2e4));
        System.out.println(describe(promo));
    }

    private static String describe(int move) {
        return Move.toUci(move)
            + "  capture=" + Move.isCapture(move)
            + "  promotion=" + Move.isPromotion(move)
            + "  doublePush=" + Move.isDoublePawnPush(move);
    }
}
