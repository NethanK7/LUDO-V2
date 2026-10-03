package ludot.strategy;

import static ludot.BoardFixtures.place;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import ludot.board.GameBoard;
import ludot.board.PlayerColour;
import ludot.board.TravelDirection;
import ludot.movement.CandidateMove;
import ludot.movement.MoveOptionFinder;
import ludot.movement.PathNavigator;
import org.junit.jupiter.api.Test;

/** Tests for the block questions used by red and green. */
class BlockInspectorTest {

    private final GameBoard board = new GameBoard();
    private final BlockInspector blocks = new BlockInspector(board, PlayerColour.GREEN);

    private CandidateMove findOnlyMove(int roll, boolean blockMove) {
        List<CandidateMove> moves = new MoveOptionFinder(board, new PathNavigator(board))
                .listAvailableMoves(PlayerColour.GREEN, roll).playableMoves().stream()
                .filter(move -> move.isBlockMove() == blockMove)
                .toList();
        return moves.get(0);
    }

    @Test
    void landingOnAnOwnPieceFormsANewBlock() {
        place(board, PlayerColour.GREEN, 1, 10, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.GREEN, 2, 13, TravelDirection.CLOCKWISE, 0);

        CandidateMove g1OntoG2 = findOnlyMove(3, false);

        assertTrue(blocks.formsNewBlock(g1OntoG2));
        assertTrue(blocks.endsInBlock(g1OntoG2));
    }

    @Test
    void movingAWholeBlockEndsInABlockButDoesNotFormANewOne() {
        place(board, PlayerColour.GREEN, 1, 10, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.GREEN, 2, 10, TravelDirection.CLOCKWISE, 0);

        CandidateMove blockMove = findOnlyMove(4, true);

        assertFalse(blocks.formsNewBlock(blockMove));
        assertTrue(blocks.endsInBlock(blockMove));
    }

    @Test
    void movingOnePieceOutOfABlockBreaksIt() {
        place(board, PlayerColour.GREEN, 1, 10, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.GREEN, 2, 10, TravelDirection.CLOCKWISE, 0);

        assertTrue(blocks.breaksBlock(findOnlyMove(4, false)));
        assertFalse(blocks.breaksBlock(findOnlyMove(4, true)));
    }
}
