package ludot.player;

import static ludot.BoardFixtures.place;
import static ludot.player.PlayerTestHelper.chooseMoveFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ludot.board.BoardSquare;
import ludot.board.GameBoard;
import ludot.board.PlayerColour;
import ludot.board.TravelDirection;
import ludot.movement.CandidateMove;
import ludot.mystery.MysteryCellScheduler;
import ludot.strategy.BlueMysteryStrategy;
import org.junit.jupiter.api.Test;

/** Tests for blue's cycle and its mystery-cell choices. */
class BluePlayerBehaviourTest {

    private final GameBoard board = new GameBoard();
    private final MysteryCellScheduler mysteryCell = mock(MysteryCellScheduler.class);
    private final BlueMysteryStrategy cycle = new BlueMysteryStrategy(mysteryCell);
    private final GamePlayer blue = new GamePlayer(PlayerColour.BLUE, cycle);

    @Test
    void theCycleStartsWithB1() {
        place(board, PlayerColour.BLUE, 1, 13, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.BLUE, 2, 20, TravelDirection.CLOCKWISE, 0);

        assertEquals("B1", chooseMoveFor(blue, board, 2).getPrimaryPiece().getName());
    }

    @Test
    void theScheduledPieceStaysTheSameForTheWholeRound() {
        place(board, PlayerColour.BLUE, 1, 13, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.BLUE, 2, 20, TravelDirection.CLOCKWISE, 0);

        blue.rememberMove(chooseMoveFor(blue, board, 6));

        assertEquals("B1", chooseMoveFor(blue, board, 2).getPrimaryPiece().getName());
    }

    @Test
    void theNextRoundConsidersTheNextPiece() {
        place(board, PlayerColour.BLUE, 1, 13, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.BLUE, 2, 20, TravelDirection.CLOCKWISE, 0);
        blue.rememberMove(chooseMoveFor(blue, board, 2));

        blue.finishRound();

        assertEquals(2, cycle.getCycleStartNumber());
        assertEquals("B2", chooseMoveFor(blue, board, 2).getPrimaryPiece().getName());
    }

    @Test
    void anImmovablePieceIsSkippedAndTheCycleContinuesAfterTheOneMoved() {
        place(board, PlayerColour.BLUE, 2, 20, TravelDirection.CLOCKWISE, 0);
        CandidateMove choice = chooseMoveFor(blue, board, 2);
        blue.rememberMove(choice);

        blue.finishRound();

        assertEquals("B2", choice.getPrimaryPiece().getName());
        assertEquals(3, cycle.getCycleStartNumber());
    }

    @Test
    void theCycleStaysPutInARoundWhereBlueMovedNothing() {
        blue.finishRound();

        assertEquals(1, cycle.getCycleStartNumber());
    }

    @Test
    void aCounterClockwisePiecePrefersTheMoveThatLandsOnTheMysteryCell() {
        place(board, PlayerColour.BLUE, 1, 20, TravelDirection.COUNTER_CLOCKWISE, 0);
        place(board, PlayerColour.BLUE, 2, 20, TravelDirection.COUNTER_CLOCKWISE, 0);
        when(mysteryCell.isOn(BoardSquare.ofRing(18))).thenReturn(true);

        CandidateMove choice = chooseMoveFor(blue, board, 4);

        assertTrue(choice.isBlockMove());
        assertEquals(BoardSquare.ofRing(18), choice.getDestination());
    }

    @Test
    void aClockwisePieceThatWouldLandOnTheMysteryCellIsSkipped() {
        place(board, PlayerColour.BLUE, 1, 20, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.BLUE, 2, 30, TravelDirection.CLOCKWISE, 0);
        when(mysteryCell.isOn(BoardSquare.ofRing(23))).thenReturn(true);

        assertEquals("B2", chooseMoveFor(blue, board, 3).getPrimaryPiece().getName());
    }

    @Test
    void theDodgeIsGivenUpWhenNoOtherPieceCanMove() {
        place(board, PlayerColour.BLUE, 1, 20, TravelDirection.CLOCKWISE, 0);
        when(mysteryCell.isOn(BoardSquare.ofRing(23))).thenReturn(true);

        assertEquals(BoardSquare.ofRing(23), chooseMoveFor(blue, board, 3).getDestination());
    }
}
