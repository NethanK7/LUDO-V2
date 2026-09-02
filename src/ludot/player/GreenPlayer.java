package ludot.player;

import java.util.List;
import ludot.board.Board;
import ludot.board.PieceColour;
import ludot.movement.PathResolver;
import ludot.movement.PlannedMove;

/**
 * Green: "prioritises winning by blocking" (Section 2.1.2).
 *
 * <p>Green's preferences, strongest first:
 *
 * <ol>
 *   <li>form a block - this outranks even emptying the base, because the specification empties the
 *       base on a six "unless moving six cells enables green to create a block";</li>
 *   <li>otherwise keep an empty base whenever a six is rolled;</li>
 *   <li>otherwise move a whole block forward with Rule&nbsp;T-4, which green "always attempts";</li>
 *   <li>otherwise move a piece that is not in a block, since green "prioritises moving its other
 *       pieces home before breaking a block";</li>
 *   <li>and only when nothing else can use the roll is a block finally broken.</li>
 * </ol>
 */
public final class GreenPlayer extends Player {

    public GreenPlayer(Board board, PathResolver pathResolver) {
        super(PieceColour.GREEN, board, pathResolver);
    }

    @Override
    public String behaviourSummary() {
        return "blocker - builds and keeps blocks, breaks one only as a last resort";
    }

    @Override
    protected PlannedMove selectMove(List<PlannedMove> options, int rollValue) {
        PlannedMove newBlock = closestToHome(options.stream().filter(this::createsBlock).toList());
        if (newBlock != null) {
            return newBlock;
        }

        PlannedMove enterBoard = enterBoardMove(options);
        if (enterBoard != null) {
            return enterBoard;
        }

        PlannedMove blockMove = closestToHome(blockMoves(options));
        if (blockMove != null) {
            return blockMove;
        }

        List<PlannedMove> keepingBlocksIntact = options.stream()
                .filter(move -> !movesPieceOutOfBlock(move))
                .toList();
        if (!keepingBlocksIntact.isEmpty()) {
            return closestToHome(keepingBlocksIntact);
        }

        // Every remaining option breaks a block, which the specification permits only when "the
        // value of the roll cannot be performed by green using the pieces in front of the block".
        return closestToHome(options);
    }
}
