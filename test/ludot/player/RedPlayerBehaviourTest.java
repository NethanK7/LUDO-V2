package ludot.player;

import static ludot.BoardFixtures.place;
import static ludot.player.PlayerTestHelper.chooseMoveFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ludot.board.BoardSquare;
import ludot.board.GameBoard;
import ludot.board.PlayerColour;
import ludot.board.TravelDirection;
import ludot.movement.CandidateMove;
import org.junit.jupiter.api.Test;

/** Tests for red's capturing behaviour. */
class RedPlayerBehaviourTest {

    private final GameBoard board = new GameBoard();
    private final GamePlayer red = PlayerTestHelper.createPlayer(board, PlayerColour.RED);

    @Test
    void aCaptureComesBeforeBringingAPieceOutOnASix() {
        place(board, PlayerColour.RED, 1, 10, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.GREEN, 1, 16, TravelDirection.CLOCKWISE, 0);

        CandidateMove choice = chooseMoveFor(red, board, 6);

        assertTrue(choice.capturesAnything());
        assertEquals("R1", choice.getPrimaryPiece().getName());
    }

    @Test
    void ofTwoCapturesRedTakesTheVictimClosestToItsHome() {
        place(board, PlayerColour.RED, 1, 10, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.RED, 2, 30, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.BLUE, 1, 13, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.GREEN, 1, 33, TravelDirection.CLOCKWISE, 0);

        CandidateMove choice = chooseMoveFor(red, board, 3);

        assertEquals("G1", choice.capturedPieces().get(0).getName());
    }

    @Test
    void withNothingToCaptureASixBringsAPieceOut() {
        place(board, PlayerColour.RED, 1, 10, TravelDirection.CLOCKWISE, 0);

        assertTrue(chooseMoveFor(red, board, 6).isEnteringBoard());
    }

    @Test
    void redAvoidsMovesThatEndInABlock() {
        place(board, PlayerColour.RED, 1, 20, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.RED, 2, 23, TravelDirection.COUNTER_CLOCKWISE, 0);
        place(board, PlayerColour.RED, 3, 40, TravelDirection.CLOCKWISE, 0);

        CandidateMove choice = chooseMoveFor(red, board, 3);

        assertEquals("R3", choice.getPrimaryPiece().getName());
        assertEquals(BoardSquare.ofRing(43), choice.getDestination());
    }

    @Test
    void aBlockIsFormedWhenItCannotBeAvoided() {
        place(board, PlayerColour.RED, 1, 20, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.RED, 2, 23, TravelDirection.COUNTER_CLOCKWISE, 0);

        CandidateMove choice = chooseMoveFor(red, board, 3);

        assertFalse(choice.isEnteringBoard());
        assertTrue(choice.getDestination().isRing());
    }
}
