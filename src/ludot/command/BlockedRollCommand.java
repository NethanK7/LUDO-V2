package ludot.command;

import java.util.Optional;
import ludot.board.PlayerColour;
import ludot.movement.BlockedMoveAttempt;
import ludot.movement.CandidateMove;
import ludot.ui.GameEventReporter;

/** Rules 7 and T-3: the piece is blocked, so it moves up to the block or the throw is lost. */
public final class BlockedRollCommand implements TurnCommand {

    private final BlockedMoveAttempt attempt;
    private final TurnCommand moveUpToTheBlock;
    private final GameEventReporter log;

    BlockedRollCommand(BlockedMoveAttempt attempt, TurnCommand moveUpToTheBlock, GameEventReporter log) {
        this.attempt = attempt;
        this.moveUpToTheBlock = moveUpToTheBlock;
        this.log = log;
    }

    @Override
    public boolean execute() {
        PlayerColour colour = attempt.piece().getColour();
        log.reportPieceBlocked(attempt);
        moveUpToTheBlock.getPlayedMove().ifPresentOrElse(
                partialMove -> log.reportMovedUpToBlock(colour, partialMove),
                () -> log.reportThrowIgnored(colour));
        return moveUpToTheBlock.execute();
    }

    @Override
    public Optional<CandidateMove> getPlayedMove() {
        return moveUpToTheBlock.getPlayedMove();
    }
}
