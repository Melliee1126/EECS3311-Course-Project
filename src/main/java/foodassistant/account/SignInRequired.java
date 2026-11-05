package foodassistant.account;

/** The page asked for something that belongs to a signed-in person. */
public final class SignInRequired extends RuntimeException {
    public SignInRequired(String message) {
        super(message);
    }
}
