package ludot.mystery;

import static ludot.BoardFixtures.createFixedRandom;
import static ludot.BoardFixtures.place;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ludot.board.BoardSpecification;
import ludot.board.BoardSquare;
import ludot.board.GameBoard;
import ludot.board.GamePiece;
import ludot.board.PlayerColour;
import ludot.board.TravelDirection;
import ludot.effects.MovementModifier;
import ludot.ui.GameEventReporter;
import org.junit.jupiter.api.Test;

/** Tests for the six teleport destinations and their effects. */
class TeleportServiceTest {

    private static final int ALPHA = 0;
    private static final int BETA = 1;
    private static final int GAMMA = 2;
    private static final int BASE = 3;
    private static final int START = 4;
    private static final int APPROACH = 5;

    private final GameBoard board = new GameBoard();
    private final GameEventReporter listener = mock(GameEventReporter.class);
    private final GamePiece piece = place(board, PlayerColour.RED, 1, 30, TravelDirection.CLOCKWISE, 1);

    private void landOnMysteryCell(int destinationIndex, boolean heads) {
        new TeleportService(board, createFixedRandom(destinationIndex, heads), listener)
                .resolveLandingOnMysteryCell(piece);
    }

    @Test
    void alphaCanEnergiseThePiece() {
        landOnMysteryCell(ALPHA, true);

        assertEquals(BoardSquare.ofRing(BoardSpecification.ALPHA_CELL), piece.getSquare());
        assertEquals(8, piece.getEffects().adjustRoll(4));
        verify(listener).reportMysteryCellLanding(piece, TeleportTarget.ALPHA);
        verify(listener).reportTeleport(piece, TeleportTarget.ALPHA);
        verify(listener).reportAlphaAura(piece, MovementModifier.DOUBLED);
    }

    @Test
    void alphaCanMakeThePieceSick() {
        landOnMysteryCell(ALPHA, false);

        assertEquals(2, piece.getEffects().adjustRoll(4));
        verify(listener).reportAlphaAura(piece, MovementModifier.HALVED);
    }

    @Test
    void betaSendsThePieceToABriefing() {
        landOnMysteryCell(BETA, true);

        assertEquals(BoardSquare.ofRing(BoardSpecification.BETA_CELL), piece.getSquare());
        assertTrue(piece.getEffects().isAttendingBriefing());
        verify(listener).reportBetaBriefing(piece);
    }

    @Test
    void gammaTurnsAClockwisePieceAround() {
        landOnMysteryCell(GAMMA, true);

        assertEquals(BoardSquare.ofRing(BoardSpecification.GAMMA_CELL), piece.getSquare());
        assertEquals(TravelDirection.COUNTER_CLOCKWISE, piece.getDirection());
        verify(listener).reportGammaReversal(piece);
    }

    @Test
    void gammaSendsACounterClockwisePieceOnToBeta() {
        piece.setDirection(TravelDirection.COUNTER_CLOCKWISE);

        landOnMysteryCell(GAMMA, true);

        assertEquals(BoardSquare.ofRing(BoardSpecification.BETA_CELL), piece.getSquare());
        assertTrue(piece.getEffects().isAttendingBriefing());
        verify(listener).reportGammaToBeta(piece);
        verify(listener).reportTeleport(piece, TeleportTarget.BETA);
    }

    @Test
    void baseSendsThePieceHomeWithItsHistoryWiped() {
        landOnMysteryCell(BASE, true);

        assertEquals(BoardSquare.ofBase(PlayerColour.RED), piece.getSquare());
        assertNull(piece.getDirection());
        assertEquals(0, piece.getCaptureCount());
    }

    @Test
    void startSendsThePieceToTheXOfItsOwnColour() {
        landOnMysteryCell(START, true);

        assertEquals(BoardSquare.ofRing(PlayerColour.RED.getStartCell()), piece.getSquare());
        assertEquals(TravelDirection.CLOCKWISE, piece.getDirection());
    }

    @Test
    void approachSendsThePieceToItsApproachCellAndCountsAsAVisit() {
        landOnMysteryCell(APPROACH, true);

        assertEquals(BoardSquare.ofRing(PlayerColour.RED.getApproachCell()), piece.getSquare());
        assertEquals(1, piece.getApproachPasses());
    }

    @Test
    void theSixDestinationsAreInTheOrderOfRuleT11() {
        assertEquals(BoardSquare.ofRing(7), TeleportTarget.ALPHA.resolveSquare(PlayerColour.BLUE));
        assertEquals(BoardSquare.ofRing(25), TeleportTarget.BETA.resolveSquare(PlayerColour.BLUE));
        assertEquals(BoardSquare.ofRing(44), TeleportTarget.GAMMA.resolveSquare(PlayerColour.BLUE));
        assertEquals(BoardSquare.ofBase(PlayerColour.BLUE), TeleportTarget.BASE.resolveSquare(PlayerColour.BLUE));
        assertEquals(BoardSquare.ofRing(13), TeleportTarget.START.resolveSquare(PlayerColour.BLUE));
        assertEquals(BoardSquare.ofRing(11), TeleportTarget.APPROACH.resolveSquare(PlayerColour.BLUE));
        assertEquals("X", TeleportTarget.START.getDisplayName());
    }
}
