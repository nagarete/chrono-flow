package dev.chronoflow.core;

import java.net.URI;

/** Strict release metadata validation, independent of Android. */
public final class UpdatePolicy {
    public static boolean trustedDownload(String url) {
        try {
            URI uri = URI.create(url);
            return "https".equals(uri.getScheme()) && "github.com".equals(uri.getHost())
                    && uri.getPort() == -1 && uri.getUserInfo() == null && uri.getQuery() == null
                    && uri.getFragment() == null
                    && uri.getPath().matches("/nagarete/chrono-flow/releases/download/v[0-9A-Za-z.\\-]+/chrono-flow-v[0-9A-Za-z.\\-]+\\.apk");
        } catch (IllegalArgumentException e) { return false; }
    }
    public static boolean valid(long version, String name, String url, String sha256, long size) {
        return version > 0 && name != null && name.matches("[0-9A-Za-z.\\-]{1,64}")
                && trustedDownload(url) && sha256 != null && sha256.matches("[a-fA-F0-9]{64}")
                && size > 0 && size <= 100L * 1024 * 1024;
    }
    private UpdatePolicy() {}
}
