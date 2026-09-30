package dev.chronoflow.core;

import org.junit.Test;
import static dev.chronoflow.core.LockPolicy.*;
import static org.junit.Assert.*;

public class LockPolicyTest {
    @Test public void lockedContentIsRedactedByDefaultIncludingPublic() {
        for (int visibility : new int[] {PRIVATE, PUBLIC}) {
            assertEquals(Presentation.REDACTED, presentation(true, true, false, visibility, NO_OVERRIDE));
        }
    }
    @Test public void systemPrivacySettingsWinOverUserOptIn() {
        assertEquals(Presentation.HIDDEN, presentation(true, false, true, PUBLIC, NO_OVERRIDE));
    }
    @Test public void secretIsNeverDisplayedWhenLocked() {
        assertEquals(Presentation.HIDDEN, presentation(true, true, true, SECRET, PUBLIC));
        assertEquals(Presentation.HIDDEN, presentation(true, true, true, PUBLIC, SECRET));
    }
    @Test public void channelPrivacyOverridesPublicNotification() {
        assertEquals(Presentation.REDACTED, presentation(true, true, true, PUBLIC, PRIVATE));
        assertEquals(Presentation.REDACTED, presentation(true, true, true, PRIVATE, PUBLIC));
    }
    @Test public void publicContentNeedsExplicitOptIn() {
        assertEquals(Presentation.PUBLIC_CONTENT, presentation(true, true, true, PUBLIC, NO_OVERRIDE));
        assertEquals(Presentation.REDACTED, presentation(true, true, true, PRIVATE, NO_OVERRIDE));
    }
    @Test public void unlockedPanelCanShowPrivateAndSecretNotifications() {
        assertEquals(Presentation.PUBLIC_CONTENT, presentation(false, false, false, SECRET, SECRET));
    }
}
