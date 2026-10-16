package engine;

public class SelfTest {
    static int failures = 0;

    static void expect(boolean ok, String what) {
        if (!ok) {
            System.out.println("  FAIL: " + what);
            failures++;
        }
    }

    /** Full observable state of the board, so a round trip can be compared exactly. */
    static String sig(Board b) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 64; i++) {
            sb.append(b.squares[i]).append(',');
        }
        sb.append('|').append(b.sideToMove);
        sb.append('|').append(b.whiteCanCastleKingside).append(b.whiteCanCastleQueenside);
        sb.append(b.blackCanCastleKingside).append(b.blackCanCastleQueenside);
        sb.append('|').append(b.enPassantSquare);
        sb.append('|').append(b.halfmoveClock);
        sb.append('|').append(b.fullmoveNumber);
        return sb.toString();
    }

    static Board empty() {
        Board b = new Board();
        for (int i = 0; i < 64; i++) {
            b.squares[i] = 0;
        }
        b.whiteCanCastleKingside = false;
        b.whiteCanCastleQueenside = false;
        b.blackCanCastleKingside = false;
        b.blackCanCastleQueenside = false;
        b.enPassantSquare = -1;
        return b;
    }

    static void roundTrip(String name, Board b, int move) {
        // These positions are built by writing squares directly, so the Zobrist
        // key has to be reseeded before any move is made on them.
        b.refreshHash();
        String before = sig(b);
        b.makeMove(move);
        b.unmakeMove(move);
        expect(before.equals(sig(b)), name + " -- state not restored");
    }

    static int sq(int rank, int file) {
        return Board.square(rank, file);
    }

    public static void main(String[] args) {
        System.out.println("make/unmake checks");

        // --- quiet move, clock and side to move ---
        Board b = new Board();
        int nf3 = Move.of(sq(0, 6), sq(2, 5), Move.FLAG_QUIET);
        roundTrip("quiet knight move", b, nf3);
        b.makeMove(nf3);
        expect(b.sideToMove == Board.BLACK, "side flips to black");
        expect(b.halfmoveClock == 1, "quiet move increments halfmove clock");
        expect(b.fullmoveNumber == 1, "white move does not bump fullmove number");
        int nf6 = Move.of(sq(7, 6), sq(5, 5), Move.FLAG_QUIET);
        b.makeMove(nf6);
        expect(b.fullmoveNumber == 2, "black move bumps fullmove number");
        b.unmakeMove(nf6);
        expect(b.fullmoveNumber == 1, "unmake restores fullmove number");
        b.unmakeMove(nf3);
        expect(sig(b).equals(sig(new Board())), "back to the start position");

        // --- double pawn push sets the en passant square ---
        b = new Board();
        int e2e4 = Move.of(sq(1, 4), sq(3, 4), Move.FLAG_DOUBLE_PAWN_PUSH);
        roundTrip("double pawn push", b, e2e4);
        b.makeMove(e2e4);
        expect(b.enPassantSquare == sq(2, 4), "ep square is e3");
        expect(b.halfmoveClock == 0, "pawn move resets halfmove clock");
        b.unmakeMove(e2e4);
        expect(b.enPassantSquare == -1, "unmake clears the ep square");

        // --- en passant capture removes the pawn beside, not the one behind ---
        b = empty();
        b.squares[sq(4, 4)] = Board.PAWN;          // white pawn e5
        b.squares[sq(4, 3)] = -Board.PAWN;         // black pawn d5, just double pushed
        b.squares[sq(0, 4)] = Board.KING;
        b.squares[sq(7, 4)] = -Board.KING;
        b.enPassantSquare = sq(5, 3);              // d6
        b.sideToMove = Board.WHITE;
        int exd6 = Move.of(sq(4, 4), sq(5, 3), Move.FLAG_EN_PASSANT);
        roundTrip("en passant capture", b, exd6);
        b.makeMove(exd6);
        expect(b.squares[sq(5, 3)] == Board.PAWN, "capturing pawn lands on d6");
        expect(b.squares[sq(4, 4)] == 0, "e5 vacated");
        expect(b.squares[sq(4, 3)] == 0, "captured black pawn removed from d5");
        b.unmakeMove(exd6);
        expect(b.squares[sq(4, 3)] == -Board.PAWN, "black pawn restored to d5");

        // --- castling, all four ---
        b = empty();
        b.squares[sq(0, 4)] = Board.KING;
        b.squares[sq(0, 7)] = Board.ROOK;
        b.squares[sq(7, 4)] = -Board.KING;
        b.whiteCanCastleKingside = true;
        int wOO = Move.of(sq(0, 4), sq(0, 6), Move.FLAG_CASTLE_KINGSIDE);
        roundTrip("white kingside castle", b, wOO);
        b.makeMove(wOO);
        expect(b.squares[sq(0, 6)] == Board.KING, "king to g1");
        expect(b.squares[sq(0, 5)] == Board.ROOK, "rook to f1");
        expect(b.squares[sq(0, 4)] == 0 && b.squares[sq(0, 7)] == 0, "e1 and h1 vacated");
        expect(!b.whiteCanCastleKingside && !b.whiteCanCastleQueenside, "white loses both rights");
        b.unmakeMove(wOO);
        expect(b.squares[sq(0, 7)] == Board.ROOK && b.squares[sq(0, 5)] == 0, "rook back on h1");
        expect(b.whiteCanCastleKingside, "castling right restored");

        b = empty();
        b.squares[sq(0, 4)] = Board.KING;
        b.squares[sq(0, 0)] = Board.ROOK;
        b.squares[sq(7, 4)] = -Board.KING;
        b.whiteCanCastleQueenside = true;
        int wOOO = Move.of(sq(0, 4), sq(0, 2), Move.FLAG_CASTLE_QUEENSIDE);
        roundTrip("white queenside castle", b, wOOO);
        b.makeMove(wOOO);
        expect(b.squares[sq(0, 2)] == Board.KING, "king to c1");
        expect(b.squares[sq(0, 3)] == Board.ROOK, "rook to d1");
        b.unmakeMove(wOOO);

        b = empty();
        b.squares[sq(7, 4)] = -Board.KING;
        b.squares[sq(7, 7)] = -Board.ROOK;
        b.squares[sq(0, 4)] = Board.KING;
        b.blackCanCastleKingside = true;
        b.sideToMove = Board.BLACK;
        int bOO = Move.of(sq(7, 4), sq(7, 6), Move.FLAG_CASTLE_KINGSIDE);
        roundTrip("black kingside castle", b, bOO);
        b.makeMove(bOO);
        expect(b.squares[sq(7, 6)] == -Board.KING, "black king to g8");
        expect(b.squares[sq(7, 5)] == -Board.ROOK, "black rook to f8");
        b.unmakeMove(bOO);

        b = empty();
        b.squares[sq(7, 4)] = -Board.KING;
        b.squares[sq(7, 0)] = -Board.ROOK;
        b.squares[sq(0, 4)] = Board.KING;
        b.blackCanCastleQueenside = true;
        b.sideToMove = Board.BLACK;
        int bOOO = Move.of(sq(7, 4), sq(7, 2), Move.FLAG_CASTLE_QUEENSIDE);
        roundTrip("black queenside castle", b, bOOO);
        b.makeMove(bOOO);
        expect(b.squares[sq(7, 2)] == -Board.KING, "black king to c8");
        expect(b.squares[sq(7, 3)] == -Board.ROOK, "black rook to d8");
        b.unmakeMove(bOOO);

        // --- promotion, quiet and capturing ---
        b = empty();
        b.squares[sq(6, 4)] = Board.PAWN;
        b.squares[sq(0, 4)] = Board.KING;
        b.squares[sq(7, 0)] = -Board.KING;
        int promo = Move.of(sq(6, 4), sq(7, 4), Move.FLAG_PROMO_QUEEN);
        roundTrip("queen promotion", b, promo);
        b.makeMove(promo);
        expect(b.squares[sq(7, 4)] == Board.QUEEN, "pawn becomes a white queen");
        expect(b.squares[sq(6, 4)] == 0, "e7 vacated");
        b.unmakeMove(promo);
        expect(b.squares[sq(6, 4)] == Board.PAWN, "queen turns back into a pawn");

        b = empty();
        b.squares[sq(6, 1)] = Board.PAWN;          // white pawn b7
        b.squares[sq(7, 0)] = -Board.ROOK;         // black rook a8
        b.squares[sq(7, 4)] = -Board.KING;
        b.squares[sq(0, 4)] = Board.KING;
        b.blackCanCastleQueenside = true;
        int promoCap = Move.of(sq(6, 1), sq(7, 0), Move.FLAG_PROMO_KNIGHT_CAPTURE);
        roundTrip("knight promotion with capture", b, promoCap);
        b.makeMove(promoCap);
        expect(b.squares[sq(7, 0)] == Board.KNIGHT, "promotes to a white knight on a8");
        expect(!b.blackCanCastleQueenside, "capturing the a8 rook kills black queenside rights");
        b.unmakeMove(promoCap);
        expect(b.squares[sq(7, 0)] == -Board.ROOK, "black rook restored on a8");
        expect(b.blackCanCastleQueenside, "black queenside right restored");

        // --- castling rights lost when a rook simply moves ---
        b = new Board();
        int ra1a2 = Move.of(sq(0, 0), sq(1, 0), Move.FLAG_QUIET);
        b.squares[sq(1, 0)] = 0;                   // clear the a2 pawn out of the way
        roundTrip("rook leaves a1", b, ra1a2);
        b.makeMove(ra1a2);
        expect(!b.whiteCanCastleQueenside, "queenside right lost");
        expect(b.whiteCanCastleKingside, "kingside right untouched");
        b.unmakeMove(ra1a2);
        expect(b.whiteCanCastleQueenside, "queenside right restored");

        // --- ordinary capture resets the clock ---
        b = empty();
        b.squares[sq(3, 3)] = Board.KNIGHT;
        b.squares[sq(5, 4)] = -Board.BISHOP;
        b.squares[sq(0, 4)] = Board.KING;
        b.squares[sq(7, 4)] = -Board.KING;
        b.halfmoveClock = 17;
        int nxb = Move.of(sq(3, 3), sq(5, 4), Move.FLAG_CAPTURE);
        roundTrip("knight takes bishop", b, nxb);
        b.makeMove(nxb);
        expect(b.squares[sq(5, 4)] == Board.KNIGHT, "knight occupies the square");
        expect(b.halfmoveClock == 0, "capture resets halfmove clock");
        b.unmakeMove(nxb);
        expect(b.squares[sq(5, 4)] == -Board.BISHOP, "bishop restored");
        expect(b.halfmoveClock == 17, "halfmove clock restored");

        // --- nested: play a line, then take it all back ---
        b = new Board();
        String start = sig(b);
        int[] line = {
            Move.of(sq(1, 4), sq(3, 4), Move.FLAG_DOUBLE_PAWN_PUSH),
            Move.of(sq(6, 4), sq(4, 4), Move.FLAG_DOUBLE_PAWN_PUSH),
            Move.of(sq(0, 6), sq(2, 5), Move.FLAG_QUIET),
            Move.of(sq(7, 1), sq(5, 2), Move.FLAG_QUIET),
            Move.of(sq(0, 5), sq(3, 2), Move.FLAG_QUIET),
            Move.of(sq(7, 5), sq(4, 2), Move.FLAG_QUIET),
        };
        for (int m : line) {
            b.makeMove(m);
        }
        expect(!sig(b).equals(start), "the line actually changed the position");
        for (int i = line.length - 1; i >= 0; i--) {
            b.unmakeMove(line[i]);
        }
        expect(sig(b).equals(start), "six ply unwound exactly to the start position");

        // --- FEN round trips ---
        expect(sig(Fen.parse(Fen.START)).equals(sig(new Board())),
            "FEN start position matches the hand built one");
        expect(Fen.toFen(new Board()).equals(Fen.START), "start position writes back to FEN");

        String[] fens = {
            Fen.START,
            "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
            "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
            "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
            "4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 3",
        };
        for (String fen : fens) {
            expect(Fen.toFen(Fen.parse(fen)).equals(fen), "FEN round trip: " + fen);
        }

        // fields 5 and 6 are optional and must default rather than throw
        Board short4 = Fen.parse("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq -");
        expect(short4.halfmoveClock == 0 && short4.fullmoveNumber == 1,
            "four field FEN defaults the clocks");
        expect(short4.sideToMove == Board.WHITE, "four field FEN reads side to move");
        expect(short4.whiteCanCastleKingside && short4.blackCanCastleQueenside,
            "four field FEN reads castling rights");

        Board ep = Fen.parse("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 3");
        expect(ep.enPassantSquare == sq(5, 3), "FEN reads the en passant square");

        System.out.println(failures == 0 ? "ALL CHECKS PASSED" : failures + " CHECKS FAILED");
    }
}
