package dev.chronoflow.core;

/** Values match Notification visibility constants without depending on Android. */
public final class LockPolicy {
    public static final int SECRET = -1, PRIVATE = 0, PUBLIC = 1, NO_OVERRIDE = -1000;
    public enum Presentation { HIDDEN, REDACTED, PUBLIC_CONTENT }

    public static Presentation presentation(boolean locked, boolean systemAllowsNotifications,
            boolean allowPublicContent, int visibility, int override) {
        if (!locked) return Presentation.PUBLIC_CONTENT;
        if (!systemAllowsNotifications || visibility == SECRET || override == SECRET) return Presentation.HIDDEN;
        int effective = override == NO_OVERRIDE ? visibility : override;
        return allowPublicContent && visibility == PUBLIC && effective == PUBLIC
                ? Presentation.PUBLIC_CONTENT : Presentation.REDACTED;
    }

    private LockPolicy() {}
}
