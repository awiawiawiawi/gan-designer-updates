/*
 * Install allowlist for the custom ROM.
 *
 * Every package install (store, file manager, adb, updates) passes through
 * InstallPackageHelper.preparePackage(), which calls isAllowed() below.
 * The list lives on the read-only system partition, so it can only be
 * changed by building and flashing a new ROM.
 */
package com.android.server.pm;

import android.util.Slog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class GanInstallPolicy {
    private static final String TAG = "GanInstallPolicy";
    private static final File LIST_FILE = new File("/system/etc/gan/allowed_packages.txt");

    private static final Object sLock = new Object();
    private static boolean sLoaded;
    private static boolean sEnforcing;
    private static final Set<String> sExact = new HashSet<>();
    private static final List<String> sPrefixes = new ArrayList<>();

    private GanInstallPolicy() {}

    /** Returns true if a package with this name may be installed or updated. */
    static boolean isAllowed(String packageName) {
        synchronized (sLock) {
            if (!sLoaded) {
                load();
                sLoaded = true;
            }
            if (!sEnforcing) {
                return true;
            }
            if (sExact.contains(packageName)) {
                return true;
            }
            for (int i = 0; i < sPrefixes.size(); i++) {
                if (packageName.startsWith(sPrefixes.get(i))) {
                    return true;
                }
            }
            return false;
        }
    }

    private static void load() {
        if (!LIST_FILE.exists()) {
            // No list in this build: do not block anything.
            Slog.w(TAG, LIST_FILE + " missing, install allowlist disabled");
            sEnforcing = false;
            return;
        }
        // From here on a read error blocks everything rather than allowing everything.
        sEnforcing = true;
        try (BufferedReader reader = new BufferedReader(new FileReader(LIST_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                final int comment = line.indexOf('#');
                if (comment >= 0) {
                    line = line.substring(0, comment);
                }
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (line.endsWith("*")) {
                    sPrefixes.add(line.substring(0, line.length() - 1));
                } else {
                    sExact.add(line);
                }
            }
        } catch (IOException e) {
            Slog.e(TAG, "Failed to read " + LIST_FILE, e);
        }
        Slog.i(TAG, "Install allowlist: " + sExact.size() + " packages, "
                + sPrefixes.size() + " prefixes");
    }
}
