package ludot.command;

import ludot.board.GameBoard;
import ludot.movement.BlockedMoveAttempt;
import ludot.movement.CandidateMove;
import ludot.mystery.MysteryCellScheduler;
import ludot.mystery.TeleportService;
import ludot.random.CoinToss;
import ludot.ui.GameEventReporter;

/** Factory Method: picks the right command for each situation. */
public final class MoveCommandFactory {

    private final CommandContext context;

    public MoveCommandFactory(GameBoard board, CoinToss coin, MysteryCellScheduler mysteryCell,
            TeleportService mysteryTeleporter, GameEventReporter log) {
        this.context = new CommandContext(board, coin, mysteryCell, mysteryTeleporter, log);
    }

    public TurnCommand create(CandidateMove move) {
        if (move.isEnteringBoard()) {
            return new ReleaseFromBaseCommand(move, context);
        }
        return new AdvancePieceCommand(move, context);
    }

    public TurnCommand createForBlocked(BlockedMoveAttempt attempt) {
        TurnCommand moveUpToTheBlock = attempt.partialMove()
                .map(this::create)
                .orElse(NoActionCommand.INSTANCE);
        return new BlockedRollCommand(attempt, moveUpToTheBlock, context.log());
    }

    public TurnCommand createNoAction() {
        return NoActionCommand.INSTANCE;
    }
}
