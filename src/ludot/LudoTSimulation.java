package ludot;

import java.io.PrintStream;
import java.util.List;
import ludot.board.Board;
import ludot.game.FirstPlayerSelector;
import ludot.game.LudoGame;
import ludot.game.TurnEngine;
import ludot.movement.MoveExecutor;
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
 * Wires the whole simulation together.
 *
 * <p>This is the one place in the program where objects are constructed, which is what allows every
 * other class to receive its collaborators through its constructor and to depend on nothing it did
 * not ask for. Because the source of randomness and the output stream are both parameters, a whole
 * game can be replayed exactly, or captured for inspection, without changing a single rule class.
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
        MoveExecutor moveExecutor =
                new MoveExecutor(board, coin, mysteryCell, mysteryEffectResolver, log);

        TurnEngine turnEngine =
                new TurnEngine(board, dice, moveGenerator, moveExecutor, pathResolver, log);
        List<Player> players = new PlayerFactory(board, pathResolver, mysteryCell).createAll();

        this.game = new LudoGame(board, players, turnEngine,
                new FirstPlayerSelector(dice, log), mysteryCell, log);
    }

    /** Convenience constructor: a reproducible game printed to standard output. */
    public LudoTSimulation(long seed) {
        this(new SeededRandomSource(seed), System.out);
    }

    public void run() {
        game.play();
    }
}
