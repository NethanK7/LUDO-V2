package ludot.mystery;

import static ludot.Fixtures.fixedRandom;
import static ludot.Fixtures.place;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ludot.board.Board;
import ludot.board.BoardGeometry;
import ludot.board.Direction;
import ludot.board.Piece;
import ludot.board.PieceColour;
import ludot.board.Square;
import ludot.effects.SpeedModifier;
import ludot.ui.GameListener;
import org.junit.jupiter.api.Test;

/** Rules T-11 to T-15: the six teleport destinations and their effects. */
class MysteryEffectResolverTest {

    private static final int ALPHA = 0;
    private static final int BETA = 1;
    private static final int GAMMA = 2;
    private static final int BASE = 3;
    private static final int START = 4;
    private static final int APPROACH = 5;

    private final Board board = new Board();
    private final GameListener listener = mock(GameListener.class);
    private final Piece piece = place(board, PieceColour.RED, 1, 30, Direction.CLOCKWISE, 1);

    private void landOnMysteryCell(int destinationIndex, boolean heads) {
        new MysteryEffectResolver(board, fixedRandom(destinationIndex, heads), listener)
                .resolveLandingOnMysteryCell(piece);
    }

    @Test
    void alphaCanEnergiseThePiece() {
        // T-12
        landOnMysteryCell(ALPHA, true);

        assertEquals(Square.ring(BoardGeometry.ALPHA_CELL), piece.square());
        assertEquals(8, piece.effects().adjustRoll(4));
        verify(listener).landsOnMysteryCell(piece, TeleportDestination.ALPHA);
        verify(listener).teleported(piece, TeleportDestination.ALPHA);
        verify(listener).alphaAura(piece, SpeedModifier.DOUBLED);
    }

    @Test
    void alphaCanMakeThePieceSick() {
        // T-12
        landOnMysteryCell(ALPHA, false);

        assertEquals(2, piece.effects().adjustRoll(4));
        verify(listener).alphaAura(piece, SpeedModifier.HALVED);
    }

    @Test
    void betaSendsThePieceToABriefing() {
        // T-13
        landOnMysteryCell(BETA, true);

        assertEquals(Square.ring(BoardGeometry.BETA_CELL), piece.square());
        assertTrue(piece.effects().isAttendingBriefing());
        verify(listener).betaBriefing(piece);
    }

    @Test
    void gammaTurnsAClockwisePieceAround() {
        // T-14
        landOnMysteryCell(GAMMA, true);

        assertEquals(Square.ring(BoardGeometry.GAMMA_CELL), piece.square());
        assertEquals(Direction.COUNTER_CLOCKWISE, piece.direction());
        verify(listener).gammaTurnedPieceAround(piece);
    }

    @Test
    void gammaSendsACounterClockwisePieceOnToBeta() {
        // T-14
        piece.setDirection(Direction.COUNTER_CLOCKWISE);

        landOnMysteryCell(GAMMA, true);

        assertEquals(Square.ring(BoardGeometry.BETA_CELL), piece.square());
        assertTrue(piece.effects().isAttendingBriefing());
        verify(listener).gammaSendsPieceToBeta(piece);
        verify(listener).teleported(piece, TeleportDestination.BETA);
    }

    @Test
    void baseSendsThePieceHomeWithItsHistoryWiped() {
        // T-11 option 4, and T-9 by analogy
        landOnMysteryCell(BASE, true);

        assertEquals(Square.base(PieceColour.RED), piece.square());
        assertNull(piece.direction());
        assertEquals(0, piece.captureCount());
    }

    @Test
    void startSendsThePieceToTheXOfItsOwnColour() {
        // T-11 option 5
        landOnMysteryCell(START, true);

        assertEquals(Square.ring(PieceColour.RED.startCell()), piece.square());
        assertEquals(Direction.CLOCKWISE, piece.direction());
    }

    @Test
    void approachSendsThePieceToItsApproachCellAndCountsAsAVisit() {
        // T-11 option 6 + T-1
        landOnMysteryCell(APPROACH, true);

        assertEquals(Square.ring(PieceColour.RED.approachCell()), piece.square());
        assertEquals(1, piece.approachPasses());
    }

    @Test
    void theSixDestinationsAreInTheOrderOfRuleT11() {
        assertEquals(Square.ring(7), TeleportDestination.ALPHA.squareFor(PieceColour.BLUE));
        assertEquals(Square.ring(25), TeleportDestination.BETA.squareFor(PieceColour.BLUE));
        assertEquals(Square.ring(44), TeleportDestination.GAMMA.squareFor(PieceColour.BLUE));
        assertEquals(Square.base(PieceColour.BLUE), TeleportDestination.BASE.squareFor(PieceColour.BLUE));
        assertEquals(Square.ring(13), TeleportDestination.START.squareFor(PieceColour.BLUE));
        assertEquals(Square.ring(11), TeleportDestination.APPROACH.squareFor(PieceColour.BLUE));
        assertEquals("X", TeleportDestination.START.displayName());
    }
}
