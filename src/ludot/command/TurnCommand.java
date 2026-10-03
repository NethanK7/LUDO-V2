package ludot.command;

import java.util.Optional;
import ludot.movement.CandidateMove;

/** Command: one action taken with a roll of the dice. */
public interface TurnCommand {

    boolean execute();

    default Optional<CandidateMove> getPlayedMove() {
        return Optional.empty();
    }
}
