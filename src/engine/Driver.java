package engine;

public class Driver {
    public static void main(String[] args) {
        Board board = new Board();
        System.out.println("start position");
        System.out.println(board);

        int[] line = {
            Move.of(Board.square(1, 4), Board.square(3, 4), Move.FLAG_DOUBLE_PAWN_PUSH),
            Move.of(Board.square(6, 4), Board.square(4, 4), Move.FLAG_DOUBLE_PAWN_PUSH),
            Move.of(Board.square(0, 6), Board.square(2, 5), Move.FLAG_QUIET),
            Move.of(Board.square(7, 1), Board.square(5, 2), Move.FLAG_QUIET),
        };

        StringBuilder played = new StringBuilder();
        for (int move : line) {
            board.makeMove(move);
            played.append(Move.toUci(move)).append(' ');
        }
        System.out.println("after " + played.toString().trim());
        System.out.println(board);

        for (int i = line.length - 1; i >= 0; i--) {
            board.unmakeMove(line[i]);
        }
        System.out.println("after unmaking all of it");
        System.out.println(board);
        System.out.println("side to move = " + (board.sideToMove == Board.WHITE ? "white" : "black")
            + ", fullmove " + board.fullmoveNumber
            + ", ep square " + board.enPassantSquare);
    }
}
