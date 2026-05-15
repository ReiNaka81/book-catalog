package com.example.sa_pro;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class LoginActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private WebView webView;
    private AuthManager authManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        progressBar = findViewById(R.id.loadingIndicator);
        webView = findViewById(R.id.authWebView);
        authManager = AuthManager.getInstance(this);

        setupWebView();
        loadLoginPage();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleRedirect(request.getUrl().toString());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleRedirect(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
            }
        });
    }

    private void loadLoginPage() {
        progressBar.setVisibility(View.VISIBLE);
        webView.setVisibility(View.VISIBLE);
        webView.loadUrl(buildAuthorizeUrl());
    }

    private String buildAuthorizeUrl() {
        Uri uri = Uri.parse(getString(R.string.api_cognito_domain) + "/oauth2/authorize")
                .buildUpon()
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("client_id", getString(R.string.api_cognito_client_id))
                .appendQueryParameter("redirect_uri", getString(R.string.api_cognito_redirect_uri))
                .appendQueryParameter("scope", "openid email profile aws.cognito.signin.user.admin")
                .appendQueryParameter("prompt", "login")
                .build();
        return uri.toString();
    }

    private boolean handleRedirect(String url) {
        if (url == null) {
            return false;
        }
        String redirectUri = getString(R.string.api_cognito_redirect_uri);
        if (!url.startsWith(redirectUri)) {
            return false;
        }

        Uri uri = Uri.parse(url);
        String error = uri.getQueryParameter("error");
        if (!TextUtils.isEmpty(error)) {
            Toast.makeText(this, getString(R.string.auth_failed), Toast.LENGTH_LONG).show();
            setResult(RESULT_CANCELED);
            finish();
            return true;
        }

        String code = uri.getQueryParameter("code");
        if (TextUtils.isEmpty(code)) {
            Toast.makeText(this, getString(R.string.auth_failed), Toast.LENGTH_LONG).show();
            setResult(RESULT_CANCELED);
            finish();
            return true;
        }

        exchangeCodeForToken(code);
        return true;
    }

    private void exchangeCodeForToken(String code) {
        progressBar.setVisibility(View.VISIBLE);
        webView.setVisibility(View.INVISIBLE);
        new TokenExchangeTask().execute(code);
    }

    private class TokenExchangeTask extends AsyncTask<String, Void, Boolean> {

        private String errorMessage;

        @Override
        protected Boolean doInBackground(String... params) {
            String code = params[0];
            HttpURLConnection connection = null;
            try {
                URL url = new URL(getString(R.string.api_cognito_domain) + "/oauth2/token");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                String body = buildTokenRequestBody(code);
                if (TextUtils.isEmpty(body)) {
                    errorMessage = getString(R.string.auth_failed);
                    return false;
                }
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }

                int status = connection.getResponseCode();
                InputStream responseStream = status >= 200 && status < 300
                        ? connection.getInputStream()
                        : connection.getErrorStream();
                String response = readStream(responseStream);

                if (status >= 200 && status < 300) {
                    JSONObject json = new JSONObject(response);
                    String idToken = json.optString("id_token");
                    String accessToken = json.optString("access_token");
                    if (TextUtils.isEmpty(idToken) || TextUtils.isEmpty(accessToken)) {
                        errorMessage = getString(R.string.auth_failed);
                        return false;
                    }
                    authManager.saveTokens(idToken, accessToken);
                    return true;
                }

                errorMessage = !TextUtils.isEmpty(response) ? response : getString(R.string.auth_failed);
            } catch (Exception e) {
                errorMessage = e.getMessage();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            return false;
        }

        private String buildTokenRequestBody(String code) {
            try {
                String redirectUri = URLEncoder.encode(getString(R.string.api_cognito_redirect_uri), "UTF-8");
                String encodedCode = URLEncoder.encode(code, "UTF-8");
                String clientId = URLEncoder.encode(getString(R.string.api_cognito_client_id), "UTF-8");
                return "grant_type=authorization_code"
                        + "&client_id=" + clientId
                        + "&code=" + encodedCode
                        + "&redirect_uri=" + redirectUri
                        + "&scope=" + URLEncoder.encode("openid email profile aws.cognito.signin.user.admin", "UTF-8");
            } catch (Exception e) {
                return null;
            }
        }

        private String readStream(InputStream stream) throws IOException {
            if (stream == null) {
                return "";
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
                return builder.toString();
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            progressBar.setVisibility(View.GONE);
            if (success) {
                Toast.makeText(LoginActivity.this, getString(R.string.auth_success), Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            } else {
                webView.setVisibility(View.VISIBLE);
                Toast.makeText(LoginActivity.this,
                        TextUtils.isEmpty(errorMessage) ? getString(R.string.auth_failed) : errorMessage,
                        Toast.LENGTH_LONG).show();
                setResult(RESULT_CANCELED);
                finish();
            }
        }
    }
}
