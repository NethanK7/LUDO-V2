package ludot.player.rule;

import static ludot.Fixtures.place;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import ludot.board.Board;
import ludot.board.Direction;
import ludot.board.PieceColour;
import ludot.movement.MoveGenerator;
import ludot.movement.PathResolver;
import ludot.movement.PlannedMove;
import org.junit.jupiter.api.Test;

/** Rule T-3 questions used by the red and green rule chains. */
class BlockFactsTest {

    private final Board board = new Board();
    private final BlockFacts blocks = new BlockFacts(board, PieceColour.GREEN);

    private PlannedMove onlyMove(int roll, boolean blockMove) {
        List<PlannedMove> moves = new MoveGenerator(board, new PathResolver(board))
                .optionsFor(PieceColour.GREEN, roll).playableMoves().stream()
                .filter(move -> move.isBlockMove() == blockMove)
                .toList();
        return moves.get(0);
    }

    @Test
    void landingOnAnOwnPieceFormsANewBlock() {
        place(board, PieceColour.GREEN, 1, 10, Direction.CLOCKWISE, 0);
        place(board, PieceColour.GREEN, 2, 13, Direction.CLOCKWISE, 0);

        PlannedMove g1OntoG2 = onlyMove(3, false);

        assertTrue(blocks.formsNewBlock(g1OntoG2));
        assertTrue(blocks.endsInBlock(g1OntoG2));
    }

    @Test
    void movingAWholeBlockEndsInABlockButDoesNotFormANewOne() {
        // T-4
        place(board, PieceColour.GREEN, 1, 10, Direction.CLOCKWISE, 0);
        place(board, PieceColour.GREEN, 2, 10, Direction.CLOCKWISE, 0);

        PlannedMove blockMove = onlyMove(4, true);

        assertFalse(blocks.formsNewBlock(blockMove));
        assertTrue(blocks.endsInBlock(blockMove));
    }

    @Test
    void movingOnePieceOutOfABlockBreaksIt() {
        place(board, PieceColour.GREEN, 1, 10, Direction.CLOCKWISE, 0);
        place(board, PieceColour.GREEN, 2, 10, Direction.CLOCKWISE, 0);

        assertTrue(blocks.breaksBlock(onlyMove(4, false)));
        assertFalse(blocks.breaksBlock(onlyMove(4, true)));
    }
}
