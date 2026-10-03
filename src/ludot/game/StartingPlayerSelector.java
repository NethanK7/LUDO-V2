package ludot.game;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import ludot.board.PlayerColour;
import ludot.random.SixSidedDie;
import ludot.ui.GameEventReporter;

/** Decides who starts: the highest roller, with only tied players rolling again. */
public final class StartingPlayerSelector {

    private final SixSidedDie dice;
    private final GameEventReporter log;

    public StartingPlayerSelector(SixSidedDie dice, GameEventReporter log) {
        this.dice = dice;
        this.log = log;
    }

    public PlayerColour determineFirstPlayer() {
        List<PlayerColour> contenders = List.of(PlayerColour.values());
        while (contenders.size() > 1) {
            contenders = rollOffBetween(contenders);
        }
        return contenders.get(0);
    }

    public List<PlayerColour> buildRoundOrder(PlayerColour first) {
        return Stream.iterate(first, PlayerColour::getNextInTurnOrder)
                .limit(PlayerColour.values().length)
                .toList();
    }

    private List<PlayerColour> rollOffBetween(List<PlayerColour> contenders) {
        Map<PlayerColour, Integer> rolls = new EnumMap<>(PlayerColour.class);
        for (PlayerColour colour : contenders) {
            int value = dice.roll();
            log.reportOpeningRoll(colour, value);
            rolls.put(colour, value);
        }
        int highest = Collections.max(rolls.values());
        return contenders.stream().filter(colour -> rolls.get(colour) == highest).toList();
    }
}
