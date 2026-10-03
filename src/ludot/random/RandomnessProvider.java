package ludot.random;

import java.util.List;

/** Every random choice in the game comes from here, so a seed can replay a game. */
public interface RandomnessProvider {

    int generateInt(int boundExclusive);

    boolean generateBoolean();

    default <T> T pick(List<T> candidates) {
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("Cannot pick from an empty list");
        }
        return candidates.get(generateInt(candidates.size()));
    }
}
