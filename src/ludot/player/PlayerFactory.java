package ludot.player;

import java.util.ArrayList;
import java.util.List;
import ludot.board.Board;
import ludot.board.PieceColour;
import ludot.movement.PathResolver;
import ludot.mystery.MysteryCell;
import ludot.player.rule.BlockFacts;
import ludot.player.rule.BlockMove;
import ludot.player.rule.CaptureClosestToVictimHome;
import ludot.player.rule.CaptureNeededForHome;
import ludot.player.rule.ClosestToHome;
import ludot.player.rule.EnterFromBase;
import ludot.player.rule.FirstLegalMove;

/**
 * Creates each player with the strategy that belongs to its colour (Factory Method pattern).
 *
 * <p>This is the one place that knows "which colour behaves how". Each chain below is the matching
 * paragraph of Section 2.1, one rule per priority, strongest first. The rest of the program only
 * ever sees {@link Player}.
 */
public final class PlayerFactory {

    private final Board board;
    private final PathResolver pathResolver;
    private final MysteryCell mysteryCell;

    public PlayerFactory(Board board, PathResolver pathResolver, MysteryCell mysteryCell) {
        this.board = board;
        this.pathResolver = pathResolver;
        this.mysteryCell = mysteryCell;
    }

    public Player create(PieceColour colour) {
        return new Player(colour, strategyFor(colour));
    }

    /** All four players, in the fixed board order yellow, blue, red, green. */
    public List<Player> createAll() {
        List<Player> players = new ArrayList<>();
        for (PieceColour colour : PieceColour.values()) {
            players.add(create(colour));
        }
        return players;
    }

    private PlayerStrategy strategyFor(PieceColour colour) {
        BlockFacts blocks = new BlockFacts(board, colour);
        return switch (colour) {
            case RED -> redStrategy(blocks);
            case GREEN -> greenStrategy(blocks);
            case YELLOW -> yellowStrategy();
            case BLUE -> new CyclicMysteryStrategy(mysteryCell);
        };
    }

    /**
     * Section 2.1.1: capture first (the victim closest to its home), bring a piece out only when
     * nothing can be captured, and avoid ending in a block unless every move would.
     */
    private PlayerStrategy redStrategy(BlockFacts blocks) {
        return new CaptureClosestToVictimHome(pathResolver,
                new EnterFromBase(
                new ClosestToHome(pathResolver, move -> !blocks.endsInBlock(move),
                new ClosestToHome(pathResolver, move -> true,
                new FirstLegalMove()))));
    }

    /**
     * Section 2.1.2: form a new block (even before emptying the base), empty the base, move as a
     * block, capture only what Rule T-7 needs, move pieces outside blocks, and break a block last.
     */
    private PlayerStrategy greenStrategy(BlockFacts blocks) {
        return new ClosestToHome(pathResolver,
                        move -> blocks.formsNewBlock(move) && !blocks.breaksBlock(move),
                new EnterFromBase(
                new BlockMove(pathResolver,
                new CaptureNeededForHome(pathResolver, move -> !blocks.breaksBlock(move),
                new ClosestToHome(pathResolver, move -> !blocks.breaksBlock(move),
                new ClosestToHome(pathResolver, move -> true,
                new FirstLegalMove()))))));
    }

    /**
     * Section 2.1.3: empty the base on every six, capture only with a piece that still needs its
     * capture for Rule T-7, otherwise move the piece closest to home.
     */
    private PlayerStrategy yellowStrategy() {
        return new EnterFromBase(
                new CaptureNeededForHome(pathResolver, move -> true,
                new ClosestToHome(pathResolver, move -> true,
                new FirstLegalMove())));
    }
}
