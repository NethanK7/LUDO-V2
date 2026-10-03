package ludot.command;

import ludot.board.Board;
import ludot.movement.BlockedAttempt;
import ludot.movement.PlannedMove;
import ludot.mystery.MysteryCell;
import ludot.mystery.MysteryEffectResolver;
import ludot.random.Coin;
import ludot.ui.GameListener;

/**
 * Builds the right command for each situation (Factory Method pattern).
 *
 * <p>The command classes' constructors are package-private, so this factory is the only way to get
 * one. The turn engine asks for a command and runs it, without knowing which class it received.
 */
public final class CommandFactory {

    private final Board board;
    private final Coin coin;
    private final MysteryCell mysteryCell;
    private final MysteryEffectResolver mysteryEffects;
    private final GameListener log;

    public CommandFactory(Board board, Coin coin, MysteryCell mysteryCell,
            MysteryEffectResolver mysteryEffects, GameListener log) {
        this.board = board;
        this.coin = coin;
        this.mysteryCell = mysteryCell;
        this.mysteryEffects = mysteryEffects;
        this.log = log;
    }

    /** The command that plays a legal move the player has chosen. */
    public GameCommand create(PlannedMove move) {
        if (move.isEnteringBoard()) {
            return new EnterBoardCommand(move, board, coin, mysteryCell, mysteryEffects, log);
        }
        return new AdvanceCommand(move, board, mysteryCell, mysteryEffects, log);
    }

    /** The command for a roll whose only possible move was stopped by an opponent block. */
    public GameCommand createForBlocked(BlockedAttempt attempt) {
        GameCommand moveUpToTheBlock = attempt.partialMove()
                .map(this::create)
                .orElse(NoMoveCommand.INSTANCE);
        return new BlockedThrowCommand(attempt, moveUpToTheBlock, log);
    }

    /** The command for a roll nothing can use. */
    public GameCommand noMove() {
        return NoMoveCommand.INSTANCE;
    }
}
