package com.example.sa_pro;

import android.os.AsyncTask;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class SearchTask extends AsyncTask<String, Void, String> {
    private Listener listener;
    private final String apiBaseUrl;
    private final AuthManager authManager;
    private String errorMessage;

    SearchTask(String apiBaseUrl, AuthManager authManager) {
        this.apiBaseUrl = apiBaseUrl.endsWith("/") ?
                apiBaseUrl.substring(0, apiBaseUrl.length() - 1) : apiBaseUrl;
        this.authManager = authManager;
    }

    protected String doInBackground(String... params) {
        String mode = params.length > 0 && params[0] != null ? params[0] : "all";
        String query = params.length > 1 && params[1] != null ? params[1] : "";

        try {
            String url = buildUrl(mode, query);
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            String authHeader = authManager != null ? authManager.getAuthorizationHeader() : null;
            if (!TextUtils.isEmpty(authHeader)) {
                connection.setRequestProperty("Authorization", authHeader);
            }

            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ?
                    connection.getInputStream() : connection.getErrorStream();
            String response = readStream(stream);

            if (status >= 200 && status < 300) {
                return response;
            }

            if (status == 401 && authManager != null) {
                authManager.clear();
            }

            errorMessage = response != null && !response.isEmpty() ?
                    response : (status == 401 ? "認証エラー: 再度ログインしてください" : "Server error: " + status);
        } catch (Exception e) {
            errorMessage = e.getMessage();
        }

        return null;
    }

    private String buildUrl(String mode, String query) throws Exception {
        StringBuilder builder = new StringBuilder(apiBaseUrl);
        builder.append("/");
        if ("title".equals(mode) || "author".equals(mode)) {
            builder.append(mode)
                    .append("?q=")
                    .append(URLEncoder.encode(query.trim(), "UTF-8"));
        } else {
            builder.append("all");
        }
        return builder.toString();
    }

    private String readStream(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
            StringBuilder buffer = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                buffer.append(line);
            }
            return buffer.toString();
        }
    }

    protected void onPostExecute(String result) {
        super.onPostExecute(result);

        // サーバとの通信が終了したら，画面を更新する
        if (listener != null) {
            if (result != null) {
                listener.onSuccess(result);
            } else {
                listener.onError(errorMessage != null ? errorMessage : "検索に失敗しました");
            }
        }
    }

    // 画面更新処理を登録するためのメソッド
    void setListener(Listener listener) {
        this.listener = listener;
    }

    // 画面更新処理を呼び出すためのインタフェース
    interface Listener {
        void onSuccess(String result);
        void onError(String message);
    }
}
