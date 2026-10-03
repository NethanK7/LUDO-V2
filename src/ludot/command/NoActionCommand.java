package ludot.command;

/** Null Object and Singleton: the action for a roll nobody can use. It does nothing. */
public enum NoActionCommand implements TurnCommand {

    INSTANCE;

    @Override
    public boolean execute() {
        return false;
    }
}
