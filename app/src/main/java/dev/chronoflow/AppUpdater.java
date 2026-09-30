package dev.chronoflow;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;
import dev.chronoflow.core.UpdatePolicy;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.HttpsURLConnection;

/** Foreground-only GitHub updater. Notification content never enters a request. */
final class AppUpdater {
    private static final String METADATA = "https://github.com/nagarete/chrono-flow/releases/latest/download/update.json";
    private static final long INTERVAL = 24 * 60 * 60 * 1000L;
    private final Context context;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Activity foreground;
    private boolean busy;
    private boolean waitingPermission;
    private boolean readyToInstall;
    private Release offered;
    private Release downloaded;
    private long dismissedVersion;

    AppUpdater(Context context) { this.context = context; }
    void resumed(Activity activity) {
        foreground = activity;
        if (!visible(activity)) return;
        if (waitingPermission && context.getPackageManager().canRequestPackageInstalls()) {
            waitingPermission = false;
            if (downloaded != null) install(activity);
        } else if (readyToInstall && downloaded != null && visible(activity)) {
            readyToInstall = false;
            new AlertDialog.Builder(activity).setTitle("Update ready")
                    .setMessage("Your verified update is downloaded. Install it now?")
                    .setNegativeButton("Later", (dialog, which) -> readyToInstall = true)
                    .setPositiveButton("Install", (dialog, which) -> install(activity)).show();
        } else {
            if (offered != null) offer();
            check(activity, false);
        }
    }
    void paused(Activity activity) { if (foreground == activity) foreground = null; }
    private boolean visible(Activity activity) {
        return foreground == activity && !activity.isFinishing() && !activity.isDestroyed() && !Preferences.locked(context);
    }
    void check(Activity activity, boolean manual) {
        if (busy || !visible(activity) || (!manual && !Preferences.get(context).getBoolean("automatic_updates", true))) return;
        long last = Preferences.get(context).getLong("update_check", 0);
        long elapsed = System.currentTimeMillis() - last;
        if (!manual && elapsed >= 0 && elapsed < INTERVAL) return;
        busy = true;
        Preferences.get(context).edit().putLong("update_check", System.currentTimeMillis()).apply();
        worker.execute(() -> {
            Release release = null;
            String error = null;
            try {
                byte[] bytes = fetch(METADATA, 16 * 1024);
                JSONObject json = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
                release = new Release(json.getLong("versionCode"), json.getString("versionName"),
                        json.getString("url"), json.getString("sha256"), json.getLong("size"));
                if (!UpdatePolicy.valid(release.code, release.name, release.url, release.sha, release.size))
                    throw new IOException("Invalid release metadata");
                Preferences.get(context).edit().putLong("update_check", System.currentTimeMillis()).apply();
                if (release.code <= installed().getLongVersionCode()) release = null;
            } catch (Exception e) { error = "Could not check for updates. Try again later."; }
            Release result = release;
            String failure = error;
            main.post(() -> {
                busy = false;
                offered = result;
                if (manual && visible(activity) && (failure != null || result == null))
                    Toast.makeText(activity, failure != null ? failure : "You’re up to date.", Toast.LENGTH_LONG).show();
                if (manual) dismissedVersion = 0;
                offer();
            });
        });
    }
    private void offer() {
        Activity activity = foreground;
        if (offered == null || busy || activity == null || !visible(activity) || dismissedVersion == offered.code) return;
        Release release = offered;
        dismissedVersion = release.code;
        new AlertDialog.Builder(activity).setTitle("chrono-flow " + release.name + " is available")
                .setMessage("Download the update from GitHub? Android will ask you to confirm installation. Your settings and notification access are kept.")
                .setNegativeButton("Later", null)
                .setPositiveButton("Download update", (dialog, which) -> download(activity, release)).show();
    }
    private void download(Activity activity, Release release) {
        if (busy) return;
        busy = true;
        Toast.makeText(activity, "Downloading update…", Toast.LENGTH_LONG).show();
        worker.execute(() -> {
            String error = null;
            try {
                File apk = new File(context.getCacheDir(), "update.apk");
                try (InputStream input = connect(release.url); FileOutputStream output = new FileOutputStream(apk)) {
                    byte[] buffer = new byte[8192];
                    long total = 0;
                    for (int count; (count = input.read(buffer)) != -1;) {
                        total += count;
                        if (total > release.size) throw new IOException("Oversized APK");
                        output.write(buffer, 0, count);
                    }
                    if (total != release.size) throw new IOException("Incomplete APK");
                }
                verify(release);
            } catch (Exception e) { error = "Update download or verification failed. Please try again."; }
            String failure = error;
            main.post(() -> {
                busy = false;
                if (failure == null) { downloaded = release; readyToInstall = true; }
                if (!visible(activity)) return;
                if (failure != null) Toast.makeText(activity, failure, Toast.LENGTH_LONG).show();
                else install(activity);
            });
        });
    }
    private PackageInfo installed() throws PackageManager.NameNotFoundException {
        return context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
    }
    private void verify(Release release) throws Exception {
        File apk = new File(context.getCacheDir(), "update.apk");
        if (apk.length() != release.size) throw new IOException("APK size mismatch");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(apk)) {
            byte[] buffer = new byte[8192];
            for (int n; (n = input.read(buffer)) != -1;) digest.update(buffer, 0, n);
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        if (!release.sha.equalsIgnoreCase(hex.toString())) throw new IOException("APK checksum mismatch");
        PackageInfo candidate = context.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
        PackageInfo current = installed();
        if (candidate == null || !context.getPackageName().equals(candidate.packageName)
                || candidate.getLongVersionCode() != release.code || release.code <= current.getLongVersionCode()
                || candidate.signingInfo == null || current.signingInfo == null) throw new IOException("Invalid APK identity");
        HashSet<Signature> expected = new HashSet<>(Arrays.asList(current.signingInfo.getApkContentsSigners()));
        if (expected.isEmpty() || !expected.equals(new HashSet<>(Arrays.asList(candidate.signingInfo.getApkContentsSigners()))))
            throw new IOException("APK signing certificate mismatch");
    }
    private void install(Activity activity) {
        readyToInstall = false;
        if (!context.getPackageManager().canRequestPackageInstalls()) {
            waitingPermission = true;
            new AlertDialog.Builder(activity).setTitle("Allow app updates")
                    .setMessage("Allow chrono-flow to install apps on the next Android Settings screen, then return here to install this update.")
                    .setNegativeButton("Later", (dialog, which) -> waitingPermission = false)
                    .setPositiveButton("Open settings", (dialog, which) -> {
                        try { activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.getPackageName()))); }
                        catch (android.content.ActivityNotFoundException e) { waitingPermission = false; toast(activity, "This device does not allow app installation settings."); }
                    }).show();
            return;
        }
        // Verification runs off the UI thread again before handing the file to Android.
        Release release = downloaded;
        if (busy || release == null) return;
        busy = true;
        worker.execute(() -> {
            boolean valid;
            try { verify(release); valid = true; } catch (Exception e) { valid = false; }
            boolean verified = valid;
            main.post(() -> {
                busy = false;
                if (!visible(activity)) return;
                if (!verified) { toast(activity, "Update verification failed. Download it again."); return; }
                Uri uri = Uri.parse("content://" + context.getPackageName() + ".updates/update.apk");
                try {
                    activity.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
                } catch (android.content.ActivityNotFoundException | SecurityException e) { toast(activity, "Android could not open the app installer."); }
            });
        });
    }
    private void toast(Activity activity, String text) { Toast.makeText(activity, text, Toast.LENGTH_LONG).show(); }
    private static byte[] fetch(String url, int limit) throws IOException {
        try (InputStream input = connect(url); java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            for (int n; (n = input.read(buffer)) != -1;) {
                if (output.size() + n > limit) throw new IOException("Oversized metadata");
                output.write(buffer, 0, n);
            }
            return output.toByteArray();
        }
    }
    private static InputStream connect(String address) throws IOException {
        for (int redirect = 0; redirect < 6; redirect++) {
            URL url = new URL(address);
            String host = url.getHost();
            if (!"https".equals(url.getProtocol()) || url.getUserInfo() != null || (url.getPort() != -1 && url.getPort() != 443)
                    || !(host.equals("github.com") || host.equals("release-assets.githubusercontent.com") || host.equals("objects.githubusercontent.com")))
                throw new IOException("Untrusted update host");
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setConnectTimeout(15000); connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "chrono-flow-updater");
            int status = connection.getResponseCode();
            if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null) throw new IOException("Missing redirect");
                address = new URL(url, location).toString(); continue;
            }
            if (status != 200) { connection.disconnect(); throw new IOException("Update unavailable"); }
            return new java.io.FilterInputStream(connection.getInputStream()) {
                @Override public void close() throws IOException { try { super.close(); } finally { connection.disconnect(); } }
            };
        }
        throw new IOException("Too many redirects");
    }
    private static final class Release {
        final long code, size;
        final String name, url, sha;
        Release(long code, String name, String url, String sha, long size) {
            this.code = code; this.name = name; this.url = url; this.sha = sha; this.size = size;
        }
    }
}
