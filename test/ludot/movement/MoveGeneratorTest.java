package ludot.movement;

import static ludot.Fixtures.place;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import ludot.board.Board;
import ludot.board.Direction;
import ludot.board.Piece;
import ludot.board.PieceColour;
import ludot.board.Square;
import ludot.effects.SpeedModifier;
import org.junit.jupiter.api.Test;

/** Turning "red rolled a 4" into every legal move: Rules 2, 4, T-3, T-4, T-5, T-8, T-12, T-13. */
class MoveGeneratorTest {

    private final Board board = new Board();
    private final MoveGenerator generator = new MoveGenerator(board, new PathResolver(board));

    @Test
    void withEveryPieceInTheBaseOnlyASixCanBeUsed() {
        // Rule 2
        MoveOptions options = generator.optionsFor(PieceColour.RED, 5);

        assertTrue(options.playableMoves().isEmpty());
        assertFalse(options.hasBlockedAttempt());
    }

    @Test
    void aSixBringsTheLowestNumberedPieceOutOntoX() {
        // Rules 2 and 4
        List<PlannedMove> moves = generator.optionsFor(PieceColour.RED, 6).playableMoves();

        assertEquals(1, moves.size());
        PlannedMove entry = moves.get(0);
        assertTrue(entry.isEnteringBoard());
        assertEquals("R1", entry.primaryPiece().name());
        assertEquals(Square.ring(26), entry.destination());
    }

    @Test
    void anOpponentBlockOnXBlocksTheEntryAndIsReported() {
        // T-3
        place(board, PieceColour.GREEN, 1, 26, Direction.CLOCKWISE, 0);
        place(board, PieceColour.GREEN, 2, 26, Direction.CLOCKWISE, 0);

        MoveOptions options = generator.optionsFor(PieceColour.RED, 6);

        assertTrue(options.playableMoves().isEmpty());
        BlockedAttempt attempt = options.blockedAttempts().get(0);
        assertEquals(Square.base(PieceColour.RED), attempt.from());
        assertEquals(Square.ring(26), attempt.intendedDestination());
        assertTrue(attempt.partialMove().isEmpty());
    }

    @Test
    void enteringOntoALoneOpponentCapturesIt() {
        // Rule 6
        place(board, PieceColour.GREEN, 1, 26, Direction.CLOCKWISE, 0);

        PlannedMove entry = generator.optionsFor(PieceColour.RED, 6).playableMoves().get(0);

        assertEquals("G1", entry.capturedPieces().get(0).name());
    }

    @Test
    void theWorkedExampleOfRuleT3OffersAMoveUpToTheCellBeforeTheBlock() {
        // T-3: G1 on 0, block R1 + R2 on 4, roll 6
        place(board, PieceColour.GREEN, 1, 0, Direction.CLOCKWISE, 0);
        place(board, PieceColour.RED, 1, 4, Direction.CLOCKWISE, 0);
        place(board, PieceColour.RED, 2, 4, Direction.CLOCKWISE, 0);

        MoveOptions options = generator.optionsFor(PieceColour.GREEN, 6);

        assertTrue(options.playableMoves().stream().noneMatch(move -> !move.isEnteringBoard()));
        BlockedAttempt attempt = options.blockedAttempts().get(0);
        assertEquals(Square.ring(6), attempt.intendedDestination());
        assertEquals("R1", attempt.blockingPiece().name());
        PlannedMove partial = attempt.partialMove().orElseThrow();
        assertEquals(Square.ring(3), partial.destination());
        assertEquals(MoveKind.PARTIAL_ADVANCE, partial.kind());
    }

    @Test
    void aPieceAtABriefingCannotMove() {
        // T-13
        Piece piece = place(board, PieceColour.BLUE, 1, 25, Direction.CLOCKWISE, 0);
        piece.effects().beginBriefing();

        assertTrue(generator.optionsFor(PieceColour.BLUE, 4).playableMoves().isEmpty());
    }

    @Test
    void anEnergisedPieceMovesDoubleTheRoll() {
        // T-12
        Piece piece = place(board, PieceColour.BLUE, 1, 7, Direction.CLOCKWISE, 0);
        piece.effects().applyAlphaAura(SpeedModifier.DOUBLED);

        PlannedMove move = generator.optionsFor(PieceColour.BLUE, 4).playableMoves().get(0);

        assertEquals(Square.ring(15), move.destination());
        assertEquals(8, move.stepsTaken());
    }

    @Test
    void aSickPieceCannotUseARollOfOne() {
        // T-12: half of 1 rounds down to 0
        Piece piece = place(board, PieceColour.BLUE, 1, 7, Direction.CLOCKWISE, 0);
        piece.effects().applyAlphaAura(SpeedModifier.HALVED);

        assertTrue(generator.optionsFor(PieceColour.BLUE, 1).playableMoves().isEmpty());
    }

    @Test
    void aBlockMovesTheRollDividedByItsSize() {
        // T-4: 6 / 2 = 3 cells
        place(board, PieceColour.GREEN, 1, 30, Direction.CLOCKWISE, 0);
        place(board, PieceColour.GREEN, 2, 30, Direction.CLOCKWISE, 0);

        PlannedMove blockMove = onlyBlockMove(generator.optionsFor(PieceColour.GREEN, 6));

        assertEquals(Square.ring(33), blockMove.destination());
        assertEquals(3, blockMove.stepsTaken());
        assertEquals(2, blockMove.groupSize());
    }

    @Test
    void aBlockOfThreeCannotMoveOnARollOfTwo() {
        // T-4: 2 / 3 rounds down to 0
        place(board, PieceColour.GREEN, 1, 30, Direction.CLOCKWISE, 0);
        place(board, PieceColour.GREEN, 2, 30, Direction.CLOCKWISE, 0);
        place(board, PieceColour.GREEN, 3, 30, Direction.CLOCKWISE, 0);

        assertTrue(generator.optionsFor(PieceColour.GREEN, 2).playableMoves().stream()
                .noneMatch(PlannedMove::isBlockMove));
    }

    @Test
    void aMixedBlockFollowsThePieceWithTheLongestJourneyHome() {
        // T-4: from 20 the counter-clockwise yellow piece has much further to go
        place(board, PieceColour.YELLOW, 1, 20, Direction.CLOCKWISE, 1);
        place(board, PieceColour.YELLOW, 2, 20, Direction.COUNTER_CLOCKWISE, 1);

        PlannedMove blockMove = onlyBlockMove(generator.optionsFor(PieceColour.YELLOW, 6));

        assertEquals(Direction.COUNTER_CLOCKWISE, blockMove.direction());
        assertEquals(Square.ring(17), blockMove.destination());
    }

    @Test
    void aPieceLeavingABlockTravelsInItsOriginalDirection() {
        // T-5: Y1 was placed clockwise on X but is now facing the other way
        Piece piece = place(board, PieceColour.YELLOW, 1, 20, Direction.CLOCKWISE, 0);
        place(board, PieceColour.YELLOW, 2, 20, Direction.COUNTER_CLOCKWISE, 0);
        piece.setDirection(Direction.COUNTER_CLOCKWISE);

        PlannedMove single = generator.optionsFor(PieceColour.YELLOW, 2).playableMoves().stream()
                .filter(move -> !move.isBlockMove())
                .filter(move -> move.primaryPiece() == piece)
                .findFirst().orElseThrow();

        assertEquals(Direction.CLOCKWISE, single.direction());
        assertEquals(Square.ring(22), single.destination());
    }

    @Test
    void anEqualBlockadeCapturesABlockade() {
        // T-8
        place(board, PieceColour.YELLOW, 1, 10, Direction.CLOCKWISE, 0);
        place(board, PieceColour.YELLOW, 2, 10, Direction.CLOCKWISE, 0);
        place(board, PieceColour.BLUE, 1, 12, Direction.CLOCKWISE, 0);
        place(board, PieceColour.BLUE, 2, 12, Direction.CLOCKWISE, 0);

        PlannedMove blockMove = onlyBlockMove(generator.optionsFor(PieceColour.YELLOW, 4));

        assertEquals(Square.ring(12), blockMove.destination());
        assertEquals(2, blockMove.capturedPieces().size());
    }

    @Test
    void aPairCannotCaptureATrio() {
        // T-8: only a blockade of the same size
        place(board, PieceColour.YELLOW, 1, 10, Direction.CLOCKWISE, 0);
        place(board, PieceColour.YELLOW, 2, 10, Direction.CLOCKWISE, 0);
        for (int number = 1; number <= 3; number++) {
            place(board, PieceColour.BLUE, number, 12, Direction.CLOCKWISE, 0);
        }

        assertTrue(generator.optionsFor(PieceColour.YELLOW, 4).playableMoves().stream()
                .noneMatch(PlannedMove::isBlockMove));
    }

    @Test
    void aForcedMoveIsEmptyWhenTheWayIsBlocked() {
        // T-6 break-up cannot pass an opponent block
        Piece piece = place(board, PieceColour.RED, 1, 26, Direction.CLOCKWISE, 0);
        place(board, PieceColour.BLUE, 1, 28, Direction.CLOCKWISE, 0);
        place(board, PieceColour.BLUE, 2, 28, Direction.CLOCKWISE, 0);

        assertTrue(generator.forcedMove(piece, Direction.CLOCKWISE, 4).isEmpty());
        assertEquals(Square.ring(27),
                generator.forcedMove(piece, Direction.CLOCKWISE, 1).orElseThrow().destination());
    }

    private static PlannedMove onlyBlockMove(MoveOptions options) {
        List<PlannedMove> blockMoves =
                options.playableMoves().stream().filter(PlannedMove::isBlockMove).toList();
        assertEquals(1, blockMoves.size(), "block moves");
        return blockMoves.get(0);
    }
}
