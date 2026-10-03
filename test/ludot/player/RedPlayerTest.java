package ludot.player;

import static ludot.Fixtures.place;
import static ludot.player.PlayerTestSupport.choiceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ludot.board.Board;
import ludot.board.Direction;
import ludot.board.PieceColour;
import ludot.board.Square;
import ludot.movement.PlannedMove;
import org.junit.jupiter.api.Test;

/** Section 2.1.1: the aggressive red player. */
class RedPlayerTest {

    private final Board board = new Board();
    private final Player red = PlayerTestSupport.playerFor(board, PieceColour.RED);

    @Test
    void aCaptureComesBeforeBringingAPieceOutOnASix() {
        place(board, PieceColour.RED, 1, 10, Direction.CLOCKWISE, 0);
        place(board, PieceColour.GREEN, 1, 16, Direction.CLOCKWISE, 0);

        PlannedMove choice = choiceOf(red, board, 6);

        assertTrue(choice.capturesAnything());
        assertEquals("R1", choice.primaryPiece().name());
    }

    @Test
    void ofTwoCapturesRedTakesTheVictimClosestToItsHome() {
        // blue on 13 has a full lap to go; green on 33 is 10 cells from home
        place(board, PieceColour.RED, 1, 10, Direction.CLOCKWISE, 0);
        place(board, PieceColour.RED, 2, 30, Direction.CLOCKWISE, 0);
        place(board, PieceColour.BLUE, 1, 13, Direction.CLOCKWISE, 0);
        place(board, PieceColour.GREEN, 1, 33, Direction.CLOCKWISE, 0);

        PlannedMove choice = choiceOf(red, board, 3);

        assertEquals("G1", choice.capturedPieces().get(0).name());
    }

    @Test
    void withNothingToCaptureASixBringsAPieceOut() {
        place(board, PieceColour.RED, 1, 10, Direction.CLOCKWISE, 0);

        assertTrue(choiceOf(red, board, 6).isEnteringBoard());
    }

    @Test
    void redAvoidsMovesThatEndInABlock() {
        // R1 (nearest home) and R2 would each land on the other; only R3 stays alone
        place(board, PieceColour.RED, 1, 20, Direction.CLOCKWISE, 0);
        place(board, PieceColour.RED, 2, 23, Direction.COUNTER_CLOCKWISE, 0);
        place(board, PieceColour.RED, 3, 40, Direction.CLOCKWISE, 0);

        PlannedMove choice = choiceOf(red, board, 3);

        assertEquals("R3", choice.primaryPiece().name());
        assertEquals(Square.ring(43), choice.destination());
    }

    @Test
    void aBlockIsFormedWhenItCannotBeAvoided() {
        place(board, PieceColour.RED, 1, 20, Direction.CLOCKWISE, 0);
        place(board, PieceColour.RED, 2, 23, Direction.COUNTER_CLOCKWISE, 0);

        PlannedMove choice = choiceOf(red, board, 3);

        assertFalse(choice.isEnteringBoard());
        assertTrue(choice.destination().isRing());
    }
}
