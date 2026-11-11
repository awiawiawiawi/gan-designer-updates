/*
 * Install allowlist for the custom ROM.
 *
 * Every package install (store, file manager, adb, updates) passes through
 * InstallPackageHelper.preparePackage(), which calls isAllowed() below.
 *
 * The list comes from the management server: the GanAgent app stores the
 * signed policy in Settings.Global, and the signature is checked here, inside
 * the package manager, against the public key built into the ROM. Until a
 * validly signed policy arrives, the list built into the ROM is used.
 */
package com.android.server.pm;

import android.content.Context;
import android.provider.Settings;
import android.util.Slog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

final class GanInstallPolicy {
    private static final String TAG = "GanInstallPolicy";
    /** Settings.Global key written by GanAgent: base64(payload) + "." + base64(signature). */
    private static final String SETTING = "gan_signed_policy";
    private static final File BUILT_IN_LIST = new File("/system/etc/gan/allowed_packages.txt");
    private static final File DEVICE_CONFIG = new File("/system/etc/gan/device_config.json");

    private static final Object sLock = new Object();
    private static boolean sBuiltInLoaded;
    private static boolean sEnforcing;
    private static List<String> sBuiltIn = new ArrayList<>();
    private static PublicKey sPublicKey;
    private static String sCachedBlob;
    private static List<String> sCachedRemote;

    private GanInstallPolicy() {}

    /** Returns true if a package with this name may be installed or updated. */
    static boolean isAllowed(Context context, String packageName) {
        synchronized (sLock) {
            if (!sBuiltInLoaded) {
                loadBuiltIn();
                sBuiltInLoaded = true;
            }
            if (!sEnforcing) {
                return true;
            }
            final List<String> remote = remoteList(context);
            return matches(remote != null ? remote : sBuiltIn, packageName);
        }
    }

    private static boolean matches(List<String> patterns, String packageName) {
        for (int i = 0; i < patterns.size(); i++) {
            final String pattern = patterns.get(i);
            if (pattern.endsWith("*")
                    ? packageName.startsWith(pattern.substring(0, pattern.length() - 1))
                    : packageName.equals(pattern)) {
                return true;
            }
        }
        return false;
    }

    /** The server's list, or null if there is no validly signed policy. */
    private static List<String> remoteList(Context context) {
        if (sPublicKey == null) {
            return null;
        }
        final String blob = Settings.Global.getString(context.getContentResolver(), SETTING);
        if (blob == null) {
            return null;
        }
        if (blob.equals(sCachedBlob)) {
            return sCachedRemote;
        }
        List<String> result = null;
        try {
            final int dot = blob.indexOf('.');
            final byte[] payload = Base64.getDecoder().decode(blob.substring(0, dot));
            final byte[] signature = Base64.getDecoder().decode(blob.substring(dot + 1));
            final Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(sPublicKey);
            verifier.update(payload);
            if (verifier.verify(signature)) {
                final JSONArray names = new JSONObject(new String(payload, StandardCharsets.UTF_8))
                        .getJSONArray("allowed_packages");
                result = new ArrayList<>();
                for (int i = 0; i < names.length(); i++) {
                    result.add(names.getString(i));
                }
            } else {
                Slog.e(TAG, "Stored policy has a bad signature, ignoring it");
            }
        } catch (Exception e) {
            Slog.e(TAG, "Stored policy is unreadable, ignoring it", e);
        }
        sCachedBlob = blob;
        sCachedRemote = result;
        return result;
    }

    private static void loadBuiltIn() {
        if (!BUILT_IN_LIST.exists()) {
            // No list in this build: do not block anything.
            Slog.w(TAG, BUILT_IN_LIST + " missing, install allowlist disabled");
            sEnforcing = false;
            return;
        }
        // From here on a read error blocks everything rather than allowing everything.
        sEnforcing = true;
        try (BufferedReader reader = new BufferedReader(new FileReader(BUILT_IN_LIST))) {
            String line;
            while ((line = reader.readLine()) != null) {
                final int comment = line.indexOf('#');
                if (comment >= 0) {
                    line = line.substring(0, comment);
                }
                line = line.trim();
                if (!line.isEmpty()) {
                    sBuiltIn.add(line);
                }
            }
        } catch (IOException e) {
            Slog.e(TAG, "Failed to read " + BUILT_IN_LIST, e);
        }
        if (DEVICE_CONFIG.exists()) {
            try {
                final JSONObject config = new JSONObject(new String(
                        Files.readAllBytes(DEVICE_CONFIG.toPath()), StandardCharsets.UTF_8));
                sPublicKey = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(
                        Base64.getDecoder().decode(config.getString("policy_public_key"))));
            } catch (Exception e) {
                Slog.e(TAG, "Failed to read " + DEVICE_CONFIG + ", remote policy disabled", e);
            }
        }
        Slog.i(TAG, "Install allowlist: " + sBuiltIn.size() + " built-in entries, remote policy "
                + (sPublicKey != null ? "enabled" : "disabled"));
    }
}
