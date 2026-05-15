package com.example.sa_pro;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

public class AuthManager {

    private static final String PREF_NAME = "sa_pro_auth";
    private static final String KEY_ID_TOKEN = "id_token";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_EXPIRES_AT = "expires_at";
    private static final String KEY_DISPLAY_NAME = "display_name";

    private static AuthManager instance;

    private final SharedPreferences preferences;

    private AuthManager(Context context) {
        this.preferences = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized AuthManager getInstance(Context context) {
        if (instance == null) {
            instance = new AuthManager(context);
        }
        return instance;
    }

    public boolean isLoggedIn() {
        String token = preferences.getString(KEY_ID_TOKEN, null);
        long expiresAt = preferences.getLong(KEY_EXPIRES_AT, 0);
        if (TextUtils.isEmpty(token) || expiresAt <= 0) {
            return false;
        }
        if (System.currentTimeMillis() >= expiresAt) {
            clear();
            return false;
        }
        return true;
    }

    public void saveTokens(String idToken, String accessToken) {
        long expiresAt = parseExpiration(idToken);
        String displayName = parseDisplayName(idToken);

        preferences.edit()
                .putString(KEY_ID_TOKEN, idToken)
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putLong(KEY_EXPIRES_AT, expiresAt)
                .putString(KEY_DISPLAY_NAME, displayName)
                .apply();
    }

    private long parseExpiration(String idToken) {
        try {
            JSONObject payload = parsePayload(idToken);
            long exp = payload.optLong("exp", 0);
            if (exp <= 0) {
                return 0;
            }
            return exp * 1000;
        } catch (Exception e) {
            return 0;
        }
    }

    private String parseDisplayName(String idToken) {
        try {
            JSONObject payload = parsePayload(idToken);
            if (payload.has("email")) {
                return payload.optString("email");
            }
            if (payload.has("name")) {
                return payload.optString("name");
            }
            if (payload.has("cognito:username")) {
                return payload.optString("cognito:username");
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private JSONObject parsePayload(String token) throws JSONException {
        if (TextUtils.isEmpty(token)) {
            throw new JSONException("token is empty");
        }
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            throw new JSONException("invalid token");
        }
        byte[] decoded = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        String payloadJson = new String(decoded, StandardCharsets.UTF_8);
        return new JSONObject(payloadJson);
    }

    public String getAuthorizationHeader() {
        String idToken = getValidIdToken();
        if (TextUtils.isEmpty(idToken)) {
            return null;
        }
        return "Bearer " + idToken;
    }

    public String getValidIdToken() {
        return isLoggedIn() ? preferences.getString(KEY_ID_TOKEN, null) : null;
    }

    public String getAccessToken() {
        return isLoggedIn() ? preferences.getString(KEY_ACCESS_TOKEN, null) : null;
    }

    public String getDisplayName() {
        if (!isLoggedIn()) {
            return "";
        }
        return preferences.getString(KEY_DISPLAY_NAME, "");
    }

    public void clear() {
        preferences.edit().clear().apply();
    }
}
