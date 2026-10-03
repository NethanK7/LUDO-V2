package ludot.player;

import java.util.ArrayList;
import java.util.List;
import ludot.board.GameBoard;
import ludot.board.PlayerColour;
import ludot.movement.PathNavigator;
import ludot.mystery.MysteryCellScheduler;
import ludot.strategy.AdvanceBlockRule;
import ludot.strategy.BlockInspector;
import ludot.strategy.CaptureLeadingOpponentRule;
import ludot.strategy.DefaultMoveRule;
import ludot.strategy.MoveSelectionStrategy;
import ludot.strategy.NearestToHomeRule;
import ludot.strategy.ReleaseFromBaseRule;
import ludot.strategy.RequiredCaptureRule;

/** Factory Method: builds each colour's strategy, one rule per line of the brief. */
public final class GamePlayerFactory {

    private final GameBoard board;
    private final PathNavigator pathCalculator;
    private final MysteryCellScheduler mysteryCell;

    public GamePlayerFactory(GameBoard board, PathNavigator pathCalculator, MysteryCellScheduler mysteryCell) {
        this.board = board;
        this.pathCalculator = pathCalculator;
        this.mysteryCell = mysteryCell;
    }

    public GamePlayer create(PlayerColour colour) {
        return new GamePlayer(colour, createStrategy(colour));
    }

    public List<GamePlayer> createAll() {
        List<GamePlayer> players = new ArrayList<>();
        for (PlayerColour colour : PlayerColour.values()) {
            players.add(create(colour));
        }
        return players;
    }

    private MoveSelectionStrategy createStrategy(PlayerColour colour) {
        BlockInspector blocks = new BlockInspector(board, colour);
        return switch (colour) {
            case RED -> buildRedStrategy(blocks);
            case GREEN -> buildGreenStrategy(blocks);
            case YELLOW -> buildYellowStrategy();
            case BLUE -> new BlueMysteryStrategy(mysteryCell);
        };
    }

    private MoveSelectionStrategy buildRedStrategy(BlockInspector blocks) {
        return new CaptureLeadingOpponentRule(pathCalculator,
                new ReleaseFromBaseRule(
                new NearestToHomeRule(pathCalculator, move -> !blocks.endsInBlock(move),
                new NearestToHomeRule(pathCalculator, move -> true,
                new DefaultMoveRule()))));
    }

    private MoveSelectionStrategy buildGreenStrategy(BlockInspector blocks) {
        return new NearestToHomeRule(pathCalculator,
                        move -> blocks.formsNewBlock(move) && !blocks.breaksBlock(move),
                new ReleaseFromBaseRule(
                new AdvanceBlockRule(pathCalculator,
                new RequiredCaptureRule(pathCalculator, move -> !blocks.breaksBlock(move),
                new NearestToHomeRule(pathCalculator, move -> !blocks.breaksBlock(move),
                new NearestToHomeRule(pathCalculator, move -> true,
                new DefaultMoveRule()))))));
    }

    private MoveSelectionStrategy buildYellowStrategy() {
        return new ReleaseFromBaseRule(
                new RequiredCaptureRule(pathCalculator, move -> true,
                new NearestToHomeRule(pathCalculator, move -> true,
                new DefaultMoveRule())));
    }
}
