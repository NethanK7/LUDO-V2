package ludot.command;

/**
 * The action for a roll that cannot be used at all (Null Object and Singleton patterns).
 *
 * <p>Instead of returning {@code null} when a player has nothing to move, the factory returns this
 * command, which simply does nothing. The turn engine runs it like any other command, so it needs no
 * special case. It holds no state, so one shared instance is enough: an enum with a single constant
 * is the simplest safe way to write a singleton in Java.
 */
public enum NoMoveCommand implements GameCommand {

    INSTANCE;

    @Override
    public boolean execute() {
        return false;
    }
}
