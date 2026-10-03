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

    private final GameBoard board;
    private final CoinToss coin;
    private final MysteryCellScheduler mysteryCell;
    private final TeleportService teleporter;
    private final GameEventReporter log;

    public MoveCommandFactory(GameBoard board, CoinToss coin, MysteryCellScheduler mysteryCell,
            TeleportService teleporter, GameEventReporter log) {
        this.board = board;
        this.coin = coin;
        this.mysteryCell = mysteryCell;
        this.teleporter = teleporter;
        this.log = log;
    }

    public TurnCommand create(CandidateMove move) {
        if (move.isEnteringBoard()) {
            return new ReleaseFromBaseCommand(move, board, coin, mysteryCell, teleporter, log);
        }
        return new AdvancePieceCommand(move, board, mysteryCell, teleporter, log);
    }

    public TurnCommand createForBlocked(BlockedMoveAttempt attempt) {
        TurnCommand moveUpToTheBlock = attempt.partialMove()
                .map(this::create)
                .orElse(NoActionCommand.INSTANCE);
        return new BlockedRollCommand(attempt, moveUpToTheBlock, log);
    }

    public TurnCommand createNoAction() {
        return NoActionCommand.INSTANCE;
    }
}
