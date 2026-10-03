package ludot.command;

import static ludot.BoardFixtures.createFixedRandom;
import static ludot.BoardFixtures.place;
import static ludot.BoardFixtures.placeOn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ludot.board.BoardSquare;
import ludot.board.GameBoard;
import ludot.board.GamePiece;
import ludot.board.PlayerColour;
import ludot.board.TravelDirection;
import ludot.effects.MovementModifier;
import ludot.movement.BlockedMoveAttempt;
import ludot.movement.CandidateMove;
import ludot.movement.MoveOptionFinder;
import ludot.movement.PathNavigator;
import ludot.mystery.MysteryCellScheduler;
import ludot.mystery.TeleportService;
import ludot.random.CoinToss;
import ludot.ui.GameEventReporter;
import org.junit.jupiter.api.Test;

/** Tests for every command: entering, moving, capturing, blocked and no move. */
class TurnCommandTest {

    private final GameBoard board = new GameBoard();
    private final MoveOptionFinder moveFinder = new MoveOptionFinder(board, new PathNavigator(board));
    private final GameEventReporter listener = mock(GameEventReporter.class);
    private final MysteryCellScheduler mysteryCell = mock(MysteryCellScheduler.class);
    private final TeleportService teleporter = mock(TeleportService.class);

    private MoveCommandFactory createCommandsTossing(boolean heads) {
        return new MoveCommandFactory(board, new CoinToss(createFixedRandom(0, heads)), mysteryCell,
                teleporter, listener);
    }

    @Test
    void enteringTheBoardTossesACoinForTheDirection() {
        CandidateMove entry = moveFinder.listAvailableMoves(PlayerColour.RED, 6).playableMoves().get(0);

        createCommandsTossing(false).create(entry).execute();

        GamePiece piece = entry.getPrimaryPiece();
        assertFalse(piece.isInBase());
        assertEquals(BoardSquare.ofRing(26), piece.getSquare());
        assertEquals(TravelDirection.COUNTER_CLOCKWISE, piece.getDirection());
        assertEquals(TravelDirection.COUNTER_CLOCKWISE, piece.getInitialDirection());
        verify(listener).reportPieceReleased(piece);
        verify(listener).reportPieceCounts(board.createSnapshot(PlayerColour.RED));
    }

    @Test
    void aCaptureSendsTheVictimHomeWithEverythingReset() {
        GamePiece attacker = place(board, PlayerColour.RED, 1, 26, TravelDirection.CLOCKWISE, 0);
        GamePiece victim = place(board, PlayerColour.GREEN, 1, 29, TravelDirection.CLOCKWISE, 2);
        victim.getEffects().applyAlphaAura(MovementModifier.DOUBLED);
        victim.setApproachPasses(1);
        CandidateMove capture = moveFinder.listAvailableMoves(PlayerColour.RED, 3).playableMoves().get(0);

        boolean captured = createCommandsTossing(true).create(capture).execute();

        assertTrue(captured);
        assertEquals(BoardSquare.ofBase(PlayerColour.GREEN), victim.getSquare());
        assertEquals(0, victim.getCaptureCount());
        assertEquals(0, victim.getApproachPasses());
        assertNull(victim.getDirection());
        assertEquals(5, victim.getEffects().adjustRoll(5));
        assertEquals(1, attacker.getCaptureCount());
        verify(listener).reportCapture(attacker, victim, "29");
    }

    @Test
    void aPlainMoveCapturesNothing() {
        place(board, PlayerColour.RED, 1, 26, TravelDirection.CLOCKWISE, 0);
        CandidateMove move = moveFinder.listAvailableMoves(PlayerColour.RED, 3).playableMoves().get(0);

        assertFalse(createCommandsTossing(true).create(move).execute());
        verify(listener).reportPieceMoved(move.movements().get(0));
        verify(listener, never()).reportCapture(any(), any(), any());
    }

    @Test
    void everyPieceOfACapturingBlockadeIsCreditedWithOneCapture() {
        GamePiece first = place(board, PlayerColour.YELLOW, 1, 10, TravelDirection.CLOCKWISE, 0);
        GamePiece second = place(board, PlayerColour.YELLOW, 2, 10, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.BLUE, 1, 12, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.BLUE, 2, 12, TravelDirection.CLOCKWISE, 0);
        CandidateMove blockMove = moveFinder.listAvailableMoves(PlayerColour.YELLOW, 4).playableMoves().stream()
                .filter(CandidateMove::isBlockMove).findFirst().orElseThrow();

        createCommandsTossing(true).create(blockMove).execute();

        assertEquals(1, first.getCaptureCount());
        assertEquals(1, second.getCaptureCount());
        assertEquals(4, board.getPiecesInBase(PlayerColour.BLUE).size());
        verify(listener, times(2)).reportPieceMoved(any());
    }

    @Test
    void landingOnTheMysteryCellTeleportsThePiece() {
        GamePiece piece = place(board, PlayerColour.RED, 1, 26, TravelDirection.CLOCKWISE, 0);
        when(mysteryCell.isOn(BoardSquare.ofRing(30))).thenReturn(true);
        CandidateMove move = moveFinder.listAvailableMoves(PlayerColour.RED, 4).playableMoves().get(0);

        createCommandsTossing(true).create(move).execute();

        verify(teleporter).resolveLandingOnMysteryCell(piece);
    }

    @Test
    void aPieceReachesHomeOnTheExactRoll() {
        GamePiece piece = placeOn(board, PlayerColour.RED, 1, BoardSquare.ofHomeStraight(PlayerColour.RED, 4),
                TravelDirection.CLOCKWISE, 1);
        CandidateMove move = moveFinder.listAvailableMoves(PlayerColour.RED, 1).playableMoves().get(0);

        createCommandsTossing(true).create(move).execute();

        assertTrue(piece.isAtHome());
    }

    @Test
    void aBlockedPieceThatCanMoveUpPrintsBothMessagesAndMoves() {
        GamePiece g1 = place(board, PlayerColour.GREEN, 1, 0, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.RED, 1, 4, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.RED, 2, 4, TravelDirection.CLOCKWISE, 0);
        BlockedMoveAttempt attempt = moveFinder.listAvailableMoves(PlayerColour.GREEN, 6).blockedMoves().get(0);

        TurnCommand command = createCommandsTossing(true).createForBlocked(attempt);
        command.execute();

        assertEquals(BoardSquare.ofRing(3), g1.getSquare());
        assertTrue(command.getPlayedMove().isPresent());
        verify(listener).reportPieceBlocked(attempt);
        verify(listener).reportMovedUpToBlock(eq(PlayerColour.GREEN), any());
    }

    @Test
    void aBlockedPieceWithNoRoomLosesTheThrow() {
        GamePiece g1 = place(board, PlayerColour.GREEN, 1, 3, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.RED, 1, 4, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.RED, 2, 4, TravelDirection.CLOCKWISE, 0);
        BlockedMoveAttempt attempt = moveFinder.listAvailableMoves(PlayerColour.GREEN, 2).blockedMoves().get(0);

        TurnCommand command = createCommandsTossing(true).createForBlocked(attempt);

        assertFalse(command.execute());
        assertEquals(BoardSquare.ofRing(3), g1.getSquare());
        assertTrue(command.getPlayedMove().isEmpty());
        verify(listener).reportThrowIgnored(PlayerColour.GREEN);
    }

    @Test
    void aRollNobodyCanUseIsTheSharedDoNothingCommand() {
        TurnCommand noMove = createCommandsTossing(true).createNoAction();

        assertSame(NoActionCommand.INSTANCE, noMove);
        assertFalse(noMove.execute());
        assertTrue(noMove.getPlayedMove().isEmpty());
        verifyNoInteractions(listener);
    }

    @Test
    void walkingOntoAlphaWithoutATeleportHasNoEffect() {
        GamePiece piece = place(board, PlayerColour.RED, 1, 4, TravelDirection.CLOCKWISE, 0);
        CandidateMove move = moveFinder.listAvailableMoves(PlayerColour.RED, 3).playableMoves().get(0);

        createCommandsTossing(true).create(move).execute();

        assertEquals(BoardSquare.ofRing(7), piece.getSquare());
        assertEquals(4, piece.getEffects().adjustRoll(4));
        verify(teleporter, never()).resolveLandingOnMysteryCell(any());
    }

    @Test
    void enteringTheBoardOntoALoneOpponentCapturesIt() {
        GamePiece victim = place(board, PlayerColour.GREEN, 1, 26, TravelDirection.CLOCKWISE, 0);
        CandidateMove entry = moveFinder.listAvailableMoves(PlayerColour.RED, 6).playableMoves().get(0);

        boolean captured = createCommandsTossing(true).create(entry).execute();

        assertTrue(captured);
        assertTrue(victim.isInBase());
        assertEquals(1, entry.getPrimaryPiece().getCaptureCount());
    }
}
