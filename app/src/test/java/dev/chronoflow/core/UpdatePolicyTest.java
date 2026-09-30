package dev.chronoflow.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class UpdatePolicyTest {
    private static final String URL = "https://github.com/nagarete/chrono-flow/releases/download/v0.2.0/chrono-flow-v0.2.0.apk";
    private static final String SHA = "a".repeat(64);
    @Test public void acceptsReleaseMetadata() {
        assertTrue(UpdatePolicy.valid(2, "0.2.0", URL, SHA, 1024));
    }
    @Test public void rejectsUntrustedDownloads() {
        assertFalse(UpdatePolicy.trustedDownload(URL.replace("https:", "http:")));
        assertFalse(UpdatePolicy.trustedDownload(URL.replace("github.com", "github.com.evil.test")));
        assertFalse(UpdatePolicy.trustedDownload(URL.replace("nagarete", "someone")));
        assertFalse(UpdatePolicy.trustedDownload(URL.replace("github.com", "user@github.com")));
        assertFalse(UpdatePolicy.trustedDownload(URL + "?redirect=evil"));
        assertFalse(UpdatePolicy.trustedDownload("not a URL"));
    }
    @Test public void rejectsInvalidOrUnboundedArtifacts() {
        assertFalse(UpdatePolicy.valid(0, "0.2.0", URL, SHA, 1024));
        assertFalse(UpdatePolicy.valid(2, "0.2.0", URL, "abc", 1024));
        assertFalse(UpdatePolicy.valid(2, "0.2.0", URL, SHA, 0));
        assertFalse(UpdatePolicy.valid(2, "0.2.0", URL, SHA, 100L * 1024 * 1024 + 1));
        assertFalse(UpdatePolicy.valid(2, "", URL, SHA, 1024));
    }
}
