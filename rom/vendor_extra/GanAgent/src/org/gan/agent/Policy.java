package org.gan.agent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** A policy from the management server, accepted only if its signature is valid. */
final class Policy {
    final int version;
    final boolean locked;
    /** Each entry is {startMinute, endMinute} counted from midnight. */
    final List<int[]> lockHours = new ArrayList<>();

    private Policy(JSONObject json) throws JSONException {
        version = json.getInt("version");
        locked = json.getBoolean("locked");
        final JSONArray hours = json.getJSONArray("lock_hours");
        for (int i = 0; i < hours.length(); i++) {
            final String[] range = hours.getString(i).split("-");
            lockHours.add(new int[] {minutes(range[0]), minutes(range[1])});
        }
    }

    private static int minutes(String hhmm) {
        final String[] parts = hhmm.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }

    /**
     * Parses "base64(payload).base64(signature)".
     *
     * @throws Exception if the signature does not match the public key or the content is malformed
     */
    static Policy parseSigned(String blob, String publicKeyBase64) throws Exception {
        final int dot = blob.indexOf('.');
        final byte[] payload = Base64.getDecoder().decode(blob.substring(0, dot));
        final byte[] signature = Base64.getDecoder().decode(blob.substring(dot + 1));
        final PublicKey key = KeyFactory.getInstance("EC").generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64)));
        final Signature verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(key);
        verifier.update(payload);
        if (!verifier.verify(signature)) {
            throw new SecurityException("Policy signature is not valid");
        }
        return new Policy(new JSONObject(new String(payload, StandardCharsets.UTF_8)));
    }

    /** True if the device should be locked at this minute of the day. */
    boolean isLockedAt(int minuteOfDay) {
        if (locked) {
            return true;
        }
        for (int[] range : lockHours) {
            final int start = range[0];
            final int end = range[1];
            // A range such as 22:00-06:00 wraps past midnight.
            if (start <= end
                    ? (minuteOfDay >= start && minuteOfDay < end)
                    : (minuteOfDay >= start || minuteOfDay < end)) {
                return true;
            }
        }
        return false;
    }
}
