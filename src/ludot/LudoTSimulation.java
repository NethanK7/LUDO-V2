package ludot;

import java.io.PrintStream;
import java.util.List;
import ludot.board.Board;
import ludot.command.CommandFactory;
import ludot.game.FirstPlayerSelector;
import ludot.game.GameResult;
import ludot.game.LudoGame;
import ludot.game.TurnEngine;
import ludot.movement.MoveGenerator;
import ludot.movement.PathResolver;
import ludot.mystery.MysteryCell;
import ludot.mystery.MysteryEffectResolver;
import ludot.player.Player;
import ludot.player.PlayerFactory;
import ludot.random.Coin;
import ludot.random.Dice;
import ludot.random.RandomSource;
import ludot.random.SeededRandomSource;
import ludot.ui.GameLog;

/**
 * The single, simple entry into the whole game (Facade pattern).
 *
 * <p>Behind {@link #run()} sit about fifteen cooperating objects - the board, the rules, the four
 * players, the dice, the mystery cell and the log. {@code Main} needs none of them: it builds this
 * facade and calls {@code run()}.
 *
 * <p>This is also the one place where those objects are constructed, so every other class receives
 * its collaborators through its constructor and depends on nothing it did not ask for. Because the
 * source of randomness and the output stream are both parameters, a whole game can be replayed
 * exactly, or captured for inspection, without changing a single rule class.
 */
public final class LudoTSimulation {

    private final LudoGame game;

    public LudoTSimulation(RandomSource randomSource, PrintStream out) {
        GameLog log = new GameLog(out);

        Board board = new Board();
        PathResolver pathResolver = new PathResolver(board);
        MysteryCell mysteryCell = new MysteryCell(board, randomSource);

        Dice dice = new Dice(randomSource);
        Coin coin = new Coin(randomSource);

        MysteryEffectResolver mysteryEffectResolver =
                new MysteryEffectResolver(board, randomSource, log);
        MoveGenerator moveGenerator = new MoveGenerator(board, pathResolver);
        CommandFactory commands =
                new CommandFactory(board, coin, mysteryCell, mysteryEffectResolver, log);

        TurnEngine turnEngine =
                new TurnEngine(board, dice, moveGenerator, commands, pathResolver, log);
        List<Player> players = new PlayerFactory(board, pathResolver, mysteryCell).createAll();

        this.game = new LudoGame(board, players, turnEngine,
                new FirstPlayerSelector(dice, log), mysteryCell, log);
    }

    /** Plays one complete game, printing it as it goes, and returns how it ended. */
    public GameResult run() {
        return game.play();
    }
}
