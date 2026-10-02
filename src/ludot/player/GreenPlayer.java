package ludot.player;

import java.util.List;
import java.util.Optional;
import ludot.board.Board;
import ludot.board.PieceColour;
import ludot.movement.PathResolver;
import ludot.movement.PlannedMove;

/**
 * Green: "prioritises winning by blocking. It will not look to capture opponent pieces more than
 * what is required to enter the home straight" (Section 2.1.2).
 *
 * <p>Green's preferences, strongest first:
 *
 * <ol>
 *   <li>form a new block - this outranks even emptying the base, because the specification empties
 *       the base on a six "unless moving six cells enables green to create a block";</li>
 *   <li>otherwise keep an empty base whenever a six is rolled;</li>
 *   <li>otherwise move a whole block forward with Rule&nbsp;T-4, which green "always attempts";</li>
 *   <li>otherwise capture with a piece that still needs its one capture for Rule&nbsp;T-7, as long as
 *       that does not break a block - the only capturing green is interested in;</li>
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
    protected Optional<PlannedMove> selectMove(List<PlannedMove> options, int rollValue) {
        Optional<PlannedMove> newBlock = closestToHome(options.stream()
                .filter(this::formsNewBlock)
                .filter(move -> !movesPieceOutOfBlock(move))
                .toList());
        if (newBlock.isPresent()) {
            return newBlock;
        }

        Optional<PlannedMove> enterBoard = enterBoardMove(options);
        if (enterBoard.isPresent()) {
            return enterBoard;
        }

        Optional<PlannedMove> blockMove = closestToHome(blockMoves(options));
        if (blockMove.isPresent()) {
            return blockMove;
        }

        Optional<PlannedMove> neededCapture = closestToHome(capturesNeededForHomeStraight(options)
                .stream()
                .filter(move -> !movesPieceOutOfBlock(move))
                .toList());
        if (neededCapture.isPresent()) {
            return neededCapture;
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
