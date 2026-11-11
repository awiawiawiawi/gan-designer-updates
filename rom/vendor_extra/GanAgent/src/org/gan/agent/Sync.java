package org.gan.agent;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Fetches the signed policy from the server, stores it, and reports the device status. */
final class Sync {
    private static final String TAG = GanApp.TAG;
    /** Read by GanInstallPolicy in the package manager, which checks the signature again. */
    private static final String SETTING = "gan_signed_policy";
    private static final File DEVICE_CONFIG = new File("/system/etc/gan/device_config.json");
    private static final int TIMEOUT_MS = 20_000;
    private static final long MIN_INTERVAL_MS = 30_000;

    private static final Object sLock = new Object();
    private static JSONObject sConfig;
    private static String sCachedBlob;
    private static Policy sCachedPolicy;
    private static long sLastRun;

    private Sync() {}

    static void runAsync(Context context) {
        final Context app = context.getApplicationContext();
        new Thread(() -> run(app), "gan-sync").start();
    }

    /** The stored policy, or null if none has been received or it fails verification. */
    static Policy currentPolicy(Context context) {
        synchronized (sLock) {
            final JSONObject config = config();
            final String blob = Settings.Global.getString(context.getContentResolver(), SETTING);
            if (config == null || blob == null) {
                return null;
            }
            if (!blob.equals(sCachedBlob)) {
                sCachedBlob = blob;
                try {
                    sCachedPolicy = Policy.parseSigned(blob, config.getString("policy_public_key"));
                } catch (Exception e) {
                    Log.e(TAG, "Stored policy rejected", e);
                    sCachedPolicy = null;
                }
            }
            return sCachedPolicy;
        }
    }

    static void run(Context context) {
        final JSONObject config = config();
        if (config == null) {
            return;
        }
        synchronized (sLock) {
            final long now = android.os.SystemClock.elapsedRealtime();
            if (sLastRun != 0 && now - sLastRun < MIN_INTERVAL_MS) {
                return;
            }
            sLastRun = now;
        }
        try {
            final String base = config.getString("server_url");
            final String token = config.getString("device_token");

            final JSONObject reply = new JSONObject(request(base + "/api/policy", token, null));
            final String blob = reply.getString("payload") + "." + reply.getString("signature");
            final Policy fresh = Policy.parseSigned(blob, config.getString("policy_public_key"));
            final Policy current = currentPolicy(context);
            // Never go back to an older policy, so a recorded old reply cannot be replayed.
            if (current == null || fresh.version > current.version) {
                Settings.Global.putString(context.getContentResolver(), SETTING, blob);
                Log.i(TAG, "Policy updated to version " + fresh.version);
            }
            GanApp.enforceLock(context);

            request(base + "/api/report", token, status(context).toString());
        } catch (Exception e) {
            Log.w(TAG, "Sync failed: " + e);
        }
    }

    private static JSONObject status(Context context) throws Exception {
        final JSONArray packages = new JSONArray();
        for (ApplicationInfo app : context.getPackageManager().getInstalledApplications(0)) {
            if ((app.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                packages.put(app.packageName);
            }
        }
        final Intent battery = context.registerReceiver(
                null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int percent = -1;
        if (battery != null) {
            final int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            final int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level >= 0 && scale > 0) {
                percent = level * 100 / scale;
            }
        }
        final Policy policy = currentPolicy(context);
        return new JSONObject()
                .put("packages", packages)
                .put("battery", percent)
                .put("policy_version", policy != null ? policy.version : 0)
                .put("build", Build.DISPLAY);
    }

    private static JSONObject config() {
        synchronized (sLock) {
            if (sConfig == null && DEVICE_CONFIG.exists()) {
                try {
                    sConfig = new JSONObject(new String(
                            Files.readAllBytes(DEVICE_CONFIG.toPath()), StandardCharsets.UTF_8));
                } catch (Exception e) {
                    Log.e(TAG, "Cannot read " + DEVICE_CONFIG, e);
                }
            }
            return sConfig;
        }
    }

    /** GET when body is null, otherwise POST of a JSON body. Returns the response text. */
    private static String request(String url, String token, String body) throws Exception {
        final HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("X-Device-Token", token);
            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            if (conn.getResponseCode() != 200) {
                throw new java.io.IOException(url + " returned " + conn.getResponseCode());
            }
            try (InputStream in = conn.getInputStream()) {
                final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                final byte[] chunk = new byte[8192];
                int n;
                while ((n = in.read(chunk)) > 0) {
                    buffer.write(chunk, 0, n);
                }
                return buffer.toString("UTF-8");
            }
        } finally {
            conn.disconnect();
        }
    }
}
