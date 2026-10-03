package ludot.player;

import static ludot.strategy.MoveFilters.anyMove;
import static ludot.strategy.MoveFilters.capturing;
import static ludot.strategy.MoveFilters.capturingForTheFirstTime;
import static ludot.strategy.MoveFilters.movingABlock;
import static ludot.strategy.MoveFilters.releasingFromBase;
import static ludot.strategy.MoveRankings.closestToHome;
import static ludot.strategy.MoveRankings.listOrder;
import static ludot.strategy.MoveRankings.victimClosestToHome;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import ludot.board.GameBoard;
import ludot.board.PlayerColour;
import ludot.movement.CandidateMove;
import ludot.movement.PathNavigator;
import ludot.mystery.MysteryCellScheduler;
import ludot.strategy.BlockInspector;
import ludot.strategy.MoveSelectionStrategy;
import ludot.strategy.PreferenceChain;

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
        return new GamePlayer(colour, selectStrategy(colour));
    }

    public List<GamePlayer> createAll() {
        List<GamePlayer> players = new ArrayList<>();
        for (PlayerColour colour : PlayerColour.values()) {
            players.add(create(colour));
        }
        return players;
    }

    private MoveSelectionStrategy selectStrategy(PlayerColour colour) {
        BlockInspector blocks = new BlockInspector(board, colour);
        return switch (colour) {
            case RED -> buildRedStrategy(blocks);
            case GREEN -> buildGreenStrategy(blocks);
            case YELLOW -> buildYellowStrategy();
            case BLUE -> new BlueMysteryStrategy(mysteryCell);
        };
    }

    private MoveSelectionStrategy buildRedStrategy(BlockInspector blocks) {
        Comparator<CandidateMove> nearestHome = closestToHome(pathCalculator);
        Predicate<CandidateMove> avoidsBlocks = Predicate.not(blocks::endsInBlock);
        return PreferenceChain.builder()
                .prefer(capturing(), victimClosestToHome(pathCalculator))
                .prefer(releasingFromBase(), listOrder())
                .prefer(avoidsBlocks, nearestHome)
                .prefer(anyMove(), nearestHome)
                .build();
    }

    private MoveSelectionStrategy buildGreenStrategy(BlockInspector blocks) {
        Comparator<CandidateMove> nearestHome = closestToHome(pathCalculator);
        Predicate<CandidateMove> keepsBlocks = Predicate.not(blocks::breaksBlock);
        Predicate<CandidateMove> formsNewBlock = blocks::formsNewBlock;
        return PreferenceChain.builder()
                .prefer(formsNewBlock.and(keepsBlocks), nearestHome)
                .prefer(releasingFromBase(), listOrder())
                .prefer(movingABlock(), nearestHome)
                .prefer(capturingForTheFirstTime().and(keepsBlocks), nearestHome)
                .prefer(keepsBlocks, nearestHome)
                .prefer(anyMove(), nearestHome)
                .build();
    }

    private MoveSelectionStrategy buildYellowStrategy() {
        Comparator<CandidateMove> nearestHome = closestToHome(pathCalculator);
        return PreferenceChain.builder()
                .prefer(releasingFromBase(), listOrder())
                .prefer(capturingForTheFirstTime(), nearestHome)
                .prefer(anyMove(), nearestHome)
                .build();
    }
}
