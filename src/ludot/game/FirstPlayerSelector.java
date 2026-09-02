package ludot.game;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ludot.board.PieceColour;
import ludot.random.Dice;
import ludot.ui.GameLog;

/**
 * Decides who starts: "Each player rolls the dice to identify who will be the first to roll. The
 * player who rolls the highest will be the first to roll."
 *
 * <p>The specification does not say what happens on a tie, so the tied players simply roll again
 * until one of them is clearly highest. Each round of rolling is reported, as Section&nbsp;3
 * requires.
 */
public final class FirstPlayerSelector {

    private final Dice dice;
    private final GameLog log;

    public FirstPlayerSelector(Dice dice, GameLog log) {
        this.dice = dice;
        this.log = log;
    }

    /** The colour that will take the first turn. */
    public PieceColour determineFirstPlayer() {
        List<PieceColour> contenders = new ArrayList<>(List.of(PieceColour.values()));
        while (contenders.size() > 1) {
            List<PieceColour> highestRollers = rollOffBetween(contenders);
            if (highestRollers.size() == 1) {
                return highestRollers.get(0);
            }
            log.openingRollTie();
            contenders = highestRollers;
        }
        return contenders.get(0);
    }

    /**
     * The order of a single round, starting from the given player and then passing the dice "to the
     * player to the left", which the enum's declaration order already encodes.
     */
    public List<PieceColour> roundOrderStartingWith(PieceColour first) {
        List<PieceColour> order = new ArrayList<>();
        PieceColour colour = first;
        for (int index = 0; index < PieceColour.values().length; index++) {
            order.add(colour);
            colour = colour.nextInTurnOrder();
        }
        return order;
    }

    /** Rolls once for each contender and returns everyone who shares the highest value. */
    private List<PieceColour> rollOffBetween(List<PieceColour> contenders) {
        Map<PieceColour, Integer> rolls = new LinkedHashMap<>();
        int highest = 0;
        for (PieceColour colour : contenders) {
            int value = dice.roll();
            log.openingRoll(colour, value);
            rolls.put(colour, value);
            highest = Math.max(highest, value);
        }

        List<PieceColour> highestRollers = new ArrayList<>();
        for (Map.Entry<PieceColour, Integer> roll : rolls.entrySet()) {
            if (roll.getValue() == highest) {
                highestRollers.add(roll.getKey());
            }
        }
        return highestRollers;
    }
}
