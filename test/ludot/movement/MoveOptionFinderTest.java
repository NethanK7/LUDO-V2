package ludot.movement;

import static ludot.BoardFixtures.place;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import ludot.board.BoardSquare;
import ludot.board.GameBoard;
import ludot.board.GamePiece;
import ludot.board.PlayerColour;
import ludot.board.TravelDirection;
import ludot.effects.MovementModifier;
import org.junit.jupiter.api.Test;

/** Tests for the list of legal moves. */
class MoveOptionFinderTest {

    private final GameBoard board = new GameBoard();
    private final MoveOptionFinder moveFinder = new MoveOptionFinder(board, new PathNavigator(board));

    @Test
    void withEveryPieceInTheBaseOnlyASixCanBeUsed() {
        AvailableMoves options = moveFinder.listAvailableMoves(PlayerColour.RED, 5);

        assertTrue(options.playableMoves().isEmpty());
        assertTrue(options.blockedMoves().isEmpty());
    }

    @Test
    void aSixBringsTheLowestNumberedPieceOutOntoX() {
        List<CandidateMove> moves = moveFinder.listAvailableMoves(PlayerColour.RED, 6).playableMoves();

        assertEquals(1, moves.size());
        CandidateMove entry = moves.get(0);
        assertTrue(entry.isEnteringBoard());
        assertEquals("R1", entry.getPrimaryPiece().getName());
        assertEquals(BoardSquare.ofRing(26), entry.getDestination());
    }

    @Test
    void anOpponentBlockOnXBlocksTheEntryAndIsReported() {
        place(board, PlayerColour.GREEN, 1, 26, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.GREEN, 2, 26, TravelDirection.CLOCKWISE, 0);

        AvailableMoves options = moveFinder.listAvailableMoves(PlayerColour.RED, 6);

        assertTrue(options.playableMoves().isEmpty());
        BlockedMoveAttempt attempt = options.blockedMoves().get(0);
        assertEquals(BoardSquare.ofBase(PlayerColour.RED), attempt.from());
        assertEquals(BoardSquare.ofRing(26), attempt.intendedDestination());
        assertTrue(attempt.partialMove().isEmpty());
    }

    @Test
    void enteringOntoALoneOpponentCapturesIt() {
        place(board, PlayerColour.GREEN, 1, 26, TravelDirection.CLOCKWISE, 0);

        CandidateMove entry = moveFinder.listAvailableMoves(PlayerColour.RED, 6).playableMoves().get(0);

        assertEquals("G1", entry.capturedPieces().get(0).getName());
    }

    @Test
    void theWorkedExampleOfRuleT3OffersAMoveUpToTheCellBeforeTheBlock() {
        place(board, PlayerColour.GREEN, 1, 0, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.RED, 1, 4, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.RED, 2, 4, TravelDirection.CLOCKWISE, 0);

        AvailableMoves options = moveFinder.listAvailableMoves(PlayerColour.GREEN, 6);

        assertTrue(options.playableMoves().stream().noneMatch(move -> !move.isEnteringBoard()));
        BlockedMoveAttempt attempt = options.blockedMoves().get(0);
        assertEquals(BoardSquare.ofRing(6), attempt.intendedDestination());
        assertEquals("R1", attempt.blockingPiece().getName());
        CandidateMove partial = attempt.partialMove().orElseThrow();
        assertEquals(BoardSquare.ofRing(3), partial.getDestination());
        assertEquals(MoveCategory.PARTIAL_ADVANCE, partial.type());
    }

    @Test
    void aPieceAtABriefingCannotMove() {
        GamePiece piece = place(board, PlayerColour.BLUE, 1, 25, TravelDirection.CLOCKWISE, 0);
        piece.getEffects().beginBriefing();

        assertTrue(moveFinder.listAvailableMoves(PlayerColour.BLUE, 4).playableMoves().isEmpty());
    }

    @Test
    void anEnergisedPieceMovesDoubleTheRoll() {
        GamePiece piece = place(board, PlayerColour.BLUE, 1, 7, TravelDirection.CLOCKWISE, 0);
        piece.getEffects().applyAlphaAura(MovementModifier.DOUBLED);

        CandidateMove move = moveFinder.listAvailableMoves(PlayerColour.BLUE, 4).playableMoves().get(0);

        assertEquals(BoardSquare.ofRing(15), move.getDestination());
        assertEquals(8, move.getStepsTaken());
    }

    @Test
    void aSickPieceCannotUseARollOfOne() {
        GamePiece piece = place(board, PlayerColour.BLUE, 1, 7, TravelDirection.CLOCKWISE, 0);
        piece.getEffects().applyAlphaAura(MovementModifier.HALVED);

        assertTrue(moveFinder.listAvailableMoves(PlayerColour.BLUE, 1).playableMoves().isEmpty());
    }

    @Test
    void aBlockMovesTheRollDividedByItsSize() {
        place(board, PlayerColour.GREEN, 1, 30, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.GREEN, 2, 30, TravelDirection.CLOCKWISE, 0);

        CandidateMove blockMove = findOnlyBlockMove(moveFinder.listAvailableMoves(PlayerColour.GREEN, 6));

        assertEquals(BoardSquare.ofRing(33), blockMove.getDestination());
        assertEquals(3, blockMove.getStepsTaken());
        assertEquals(2, blockMove.getGroupSize());
    }

    @Test
    void aBlockOfThreeCannotMoveOnARollOfTwo() {
        place(board, PlayerColour.GREEN, 1, 30, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.GREEN, 2, 30, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.GREEN, 3, 30, TravelDirection.CLOCKWISE, 0);

        assertTrue(moveFinder.listAvailableMoves(PlayerColour.GREEN, 2).playableMoves().stream()
                .noneMatch(CandidateMove::isBlockMove));
    }

    @Test
    void aMixedBlockFollowsThePieceWithTheLongestJourneyHome() {
        place(board, PlayerColour.YELLOW, 1, 20, TravelDirection.CLOCKWISE, 1);
        place(board, PlayerColour.YELLOW, 2, 20, TravelDirection.COUNTER_CLOCKWISE, 1);

        CandidateMove blockMove = findOnlyBlockMove(moveFinder.listAvailableMoves(PlayerColour.YELLOW, 6));

        assertEquals(TravelDirection.COUNTER_CLOCKWISE, blockMove.getDirection());
        assertEquals(BoardSquare.ofRing(17), blockMove.getDestination());
    }

    @Test
    void aPieceLeavingABlockTravelsInItsOriginalDirection() {
        GamePiece piece = place(board, PlayerColour.YELLOW, 1, 20, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.YELLOW, 2, 20, TravelDirection.COUNTER_CLOCKWISE, 0);
        piece.setDirection(TravelDirection.COUNTER_CLOCKWISE);

        CandidateMove single = moveFinder.listAvailableMoves(PlayerColour.YELLOW, 2).playableMoves().stream()
                .filter(move -> !move.isBlockMove())
                .filter(move -> move.getPrimaryPiece() == piece)
                .findFirst().orElseThrow();

        assertEquals(TravelDirection.CLOCKWISE, single.getDirection());
        assertEquals(BoardSquare.ofRing(22), single.getDestination());
    }

    @Test
    void anEqualBlockadeCapturesABlockade() {
        place(board, PlayerColour.YELLOW, 1, 10, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.YELLOW, 2, 10, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.BLUE, 1, 12, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.BLUE, 2, 12, TravelDirection.CLOCKWISE, 0);

        CandidateMove blockMove = findOnlyBlockMove(moveFinder.listAvailableMoves(PlayerColour.YELLOW, 4));

        assertEquals(BoardSquare.ofRing(12), blockMove.getDestination());
        assertEquals(2, blockMove.capturedPieces().size());
    }

    @Test
    void aPairCannotCaptureATrio() {
        place(board, PlayerColour.YELLOW, 1, 10, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.YELLOW, 2, 10, TravelDirection.CLOCKWISE, 0);
        for (int number = 1; number <= 3; number++) {
            place(board, PlayerColour.BLUE, number, 12, TravelDirection.CLOCKWISE, 0);
        }

        assertTrue(moveFinder.listAvailableMoves(PlayerColour.YELLOW, 4).playableMoves().stream()
                .noneMatch(CandidateMove::isBlockMove));
    }

    @Test
    void aForcedMoveIsEmptyWhenTheWayIsBlocked() {
        GamePiece piece = place(board, PlayerColour.RED, 1, 26, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.BLUE, 1, 28, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.BLUE, 2, 28, TravelDirection.CLOCKWISE, 0);

        assertTrue(moveFinder.planForcedMove(piece, TravelDirection.CLOCKWISE, 4).isEmpty());
        assertEquals(BoardSquare.ofRing(27),
                moveFinder.planForcedMove(piece, TravelDirection.CLOCKWISE, 1).orElseThrow().getDestination());
    }

    private static CandidateMove findOnlyBlockMove(AvailableMoves options) {
        List<CandidateMove> blockMoves =
                options.playableMoves().stream().filter(CandidateMove::isBlockMove).toList();
        assertEquals(1, blockMoves.size(), "block moves");
        return blockMoves.get(0);
    }

    @Test
    void aPieceMayLandOnItsOwnPieceAndFormABlock() {
        place(board, PlayerColour.RED, 1, 10, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.RED, 2, 13, TravelDirection.CLOCKWISE, 0);

        CandidateMove onToR2 = moveFinder.listAvailableMoves(PlayerColour.RED, 3).playableMoves().stream()
                .filter(move -> move.getPrimaryPiece().getName().equals("R1"))
                .findFirst().orElseThrow();

        assertEquals(BoardSquare.ofRing(13), onToR2.getDestination());
        assertTrue(onToR2.capturedPieces().isEmpty());
    }

    @Test
    void aBlockOfThreeMovesTheRollDividedByThree() {
        for (int number = 1; number <= 3; number++) {
            place(board, PlayerColour.GREEN, number, 30, TravelDirection.CLOCKWISE, 0);
        }

        CandidateMove blockMove = findOnlyBlockMove(moveFinder.listAvailableMoves(PlayerColour.GREEN, 6));

        assertEquals(BoardSquare.ofRing(32), blockMove.getDestination());
        assertEquals(3, blockMove.getGroupSize());
    }
}
