package ludot.command;

import ludot.board.GameBoard;
import ludot.mystery.MysteryCellScheduler;
import ludot.mystery.TeleportService;
import ludot.random.CoinToss;
import ludot.ui.GameEventReporter;

/** Everything a command needs from the rest of the game, bundled so commands stay small. */
record CommandContext(GameBoard board, CoinToss coin, MysteryCellScheduler mysteryCell,
        TeleportService mysteryTeleporter, GameEventReporter log) {
}
