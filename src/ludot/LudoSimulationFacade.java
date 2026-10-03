package ludot;

import java.io.PrintStream;
import java.util.List;
import ludot.board.GameBoard;
import ludot.command.MoveCommandFactory;
import ludot.game.GameController;
import ludot.game.GameOutcome;
import ludot.game.StartingPlayerSelector;
import ludot.game.TurnController;
import ludot.movement.MoveOptionFinder;
import ludot.movement.PathNavigator;
import ludot.mystery.MysteryCellScheduler;
import ludot.mystery.TeleportService;
import ludot.player.GamePlayer;
import ludot.player.GamePlayerFactory;
import ludot.random.CoinToss;
import ludot.random.RandomnessProvider;
import ludot.random.SixSidedDie;
import ludot.ui.ConsoleEventPrinter;

/** Facade: builds every part of the game in one place, so LudoApplication only calls run(). */
public final class LudoSimulationFacade {

    private final GameController game;

    public LudoSimulationFacade(RandomnessProvider randomSource, PrintStream out) {
        ConsoleEventPrinter log = new ConsoleEventPrinter(out);

        GameBoard board = new GameBoard();
        PathNavigator pathNavigator = new PathNavigator(board);
        MysteryCellScheduler mysteryCell = new MysteryCellScheduler(board, randomSource);

        SixSidedDie dice = new SixSidedDie(randomSource);
        CoinToss coin = new CoinToss(randomSource);

        TeleportService teleporter =
                new TeleportService(board, randomSource, log);
        MoveOptionFinder moveFinder = new MoveOptionFinder(board, pathNavigator);
        MoveCommandFactory commands =
                new MoveCommandFactory(board, coin, mysteryCell, teleporter, log);

        TurnController turnEngine =
                new TurnController(board, dice, moveFinder, commands, pathNavigator, log);
        List<GamePlayer> players = new GamePlayerFactory(board, pathNavigator, mysteryCell).createAll();

        this.game = new GameController(board, players, turnEngine,
                new StartingPlayerSelector(dice, log), mysteryCell, log);
    }

    public GameOutcome run() {
        return game.play();
    }
}
