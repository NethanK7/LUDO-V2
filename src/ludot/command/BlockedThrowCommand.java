package ludot.command;

import java.util.Optional;
import ludot.board.PieceColour;
import ludot.movement.BlockedAttempt;
import ludot.movement.PlannedMove;
import ludot.ui.GameListener;

/**
 * Rules 7 and T-3: the only piece that could use the roll is stopped by an opponent block.
 *
 * <p>The piece either moves up to the cell before the block or the throw is lost. Both cases print
 * the messages Section 3.1 asks for; the actual walk, if any, is delegated to another command.
 */
public final class BlockedThrowCommand implements GameCommand {

    private final BlockedAttempt attempt;
    private final GameCommand moveUpToTheBlock;
    private final GameListener log;

    BlockedThrowCommand(BlockedAttempt attempt, GameCommand moveUpToTheBlock, GameListener log) {
        this.attempt = attempt;
        this.moveUpToTheBlock = moveUpToTheBlock;
        this.log = log;
    }

    @Override
    public boolean execute() {
        PieceColour colour = attempt.piece().colour();
        log.pieceIsBlocked(attempt);
        moveUpToTheBlock.playedMove().ifPresentOrElse(
                partialMove -> log.blockedButMovedUpToTheBlock(colour, partialMove),
                () -> log.blockedWithNothingElseToMove(colour));
        return moveUpToTheBlock.execute();
    }

    @Override
    public Optional<PlannedMove> playedMove() {
        return moveUpToTheBlock.playedMove();
    }
}
