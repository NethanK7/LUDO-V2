package ludot.player;

import static ludot.Fixtures.place;
import static ludot.player.PlayerTestSupport.choiceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ludot.board.Board;
import ludot.board.Direction;
import ludot.board.PieceColour;
import ludot.board.Square;
import ludot.movement.PlannedMove;
import ludot.mystery.MysteryCell;
import org.junit.jupiter.api.Test;

/** Section 2.1.4: blue cycles through its pieces and chases or dodges the mystery cell. */
class BluePlayerTest {

    private final Board board = new Board();
    private final MysteryCell mysteryCell = mock(MysteryCell.class);
    private final CyclicMysteryStrategy cycle = new CyclicMysteryStrategy(mysteryCell);
    private final Player blue = new Player(PieceColour.BLUE, cycle);

    @Test
    void theCycleStartsWithB1() {
        place(board, PieceColour.BLUE, 1, 13, Direction.CLOCKWISE, 0);
        place(board, PieceColour.BLUE, 2, 20, Direction.CLOCKWISE, 0);

        assertEquals("B1", choiceOf(blue, board, 2).primaryPiece().name());
    }

    @Test
    void theScheduledPieceStaysTheSameForTheWholeRound() {
        // "if B1 is moved in the current round, B2 is considered in the next"
        place(board, PieceColour.BLUE, 1, 13, Direction.CLOCKWISE, 0);
        place(board, PieceColour.BLUE, 2, 20, Direction.CLOCKWISE, 0);

        blue.onMoveExecuted(choiceOf(blue, board, 6));

        assertEquals("B1", choiceOf(blue, board, 2).primaryPiece().name());
    }

    @Test
    void theNextRoundConsidersTheNextPiece() {
        place(board, PieceColour.BLUE, 1, 13, Direction.CLOCKWISE, 0);
        place(board, PieceColour.BLUE, 2, 20, Direction.CLOCKWISE, 0);
        blue.onMoveExecuted(choiceOf(blue, board, 2));

        blue.onRoundCompleted();

        assertEquals(2, cycle.scheduledPieceNumber());
        assertEquals("B2", choiceOf(blue, board, 2).primaryPiece().name());
    }

    @Test
    void anImmovablePieceIsSkippedAndTheCycleContinuesAfterTheOneMoved() {
        // B1 is still in its base and a 2 cannot bring it out
        place(board, PieceColour.BLUE, 2, 20, Direction.CLOCKWISE, 0);
        PlannedMove choice = choiceOf(blue, board, 2);
        blue.onMoveExecuted(choice);

        blue.onRoundCompleted();

        assertEquals("B2", choice.primaryPiece().name());
        assertEquals(3, cycle.scheduledPieceNumber());
    }

    @Test
    void theCycleStaysPutInARoundWhereBlueMovedNothing() {
        blue.onRoundCompleted();

        assertEquals(1, cycle.scheduledPieceNumber());
    }

    @Test
    void aCounterClockwisePiecePrefersTheMoveThatLandsOnTheMysteryCell() {
        // B1 alone would go 20 -> 16; the block of B1 + B2 goes 4 / 2 = 2 cells to 18
        place(board, PieceColour.BLUE, 1, 20, Direction.COUNTER_CLOCKWISE, 0);
        place(board, PieceColour.BLUE, 2, 20, Direction.COUNTER_CLOCKWISE, 0);
        when(mysteryCell.isOn(Square.ring(18))).thenReturn(true);

        PlannedMove choice = choiceOf(blue, board, 4);

        assertTrue(choice.isBlockMove());
        assertEquals(Square.ring(18), choice.destination());
    }

    @Test
    void aClockwisePieceThatWouldLandOnTheMysteryCellIsSkipped() {
        place(board, PieceColour.BLUE, 1, 20, Direction.CLOCKWISE, 0);
        place(board, PieceColour.BLUE, 2, 30, Direction.CLOCKWISE, 0);
        when(mysteryCell.isOn(Square.ring(23))).thenReturn(true);

        assertEquals("B2", choiceOf(blue, board, 3).primaryPiece().name());
    }

    @Test
    void theDodgeIsGivenUpWhenNoOtherPieceCanMove() {
        place(board, PieceColour.BLUE, 1, 20, Direction.CLOCKWISE, 0);
        when(mysteryCell.isOn(Square.ring(23))).thenReturn(true);

        assertEquals(Square.ring(23), choiceOf(blue, board, 3).destination());
    }
}
