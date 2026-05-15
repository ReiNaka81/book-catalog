package com.example.sa_pro;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class BookInfoActivity extends AppCompatActivity {

    private String bookId;
    private AuthManager authManager;
    private Button favoriteButton;
    private boolean isFavorite;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_book_info);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Intentからの情報の取り出し
        Intent intent = getIntent();
        bookId = intent.getStringExtra("bookId");
        String title = nullToEmpty(intent.getStringExtra("title"));
        String author = nullToEmpty(intent.getStringExtra("author"));
        String publisher = nullToEmpty(intent.getStringExtra("publisher"));
        double price = intent.getDoubleExtra("price", 0);
        String isbn = intent.getStringExtra("isbn");
        String publishedDate = intent.getStringExtra("publishedDate");
        isFavorite = intent.getBooleanExtra("isFavorite", false);

        authManager = AuthManager.getInstance(getApplicationContext());

        // ユーザインタフェースコンポーネントへの参照の取得
        TextView titleView = (TextView) findViewById(R.id.titleView);
        TextView authorView = (TextView) findViewById(R.id.authorView);
        TextView publisherView = (TextView) findViewById(R.id.publisherView);
        TextView priceView = (TextView) findViewById(R.id.priceView);
        TextView isbnView = (TextView) findViewById(R.id.isbnView);
        favoriteButton = findViewById(R.id.favoriteButton);

        // 書籍情報の表示
        String fullTitle = title;
        if (!TextUtils.isEmpty(bookId)) {
            fullTitle = title + " (" + bookId + ")";
        }

        titleView.setText(fullTitle);
        authorView.setText(author);
        publisherView.setText(publisher);
        priceView.setText(formatPrice(price));

        StringBuilder isbnBuilder = new StringBuilder();
        if (!TextUtils.isEmpty(isbn)) {
            isbnBuilder.append("ISBN: ").append(isbn);
        }
        if (!TextUtils.isEmpty(publishedDate)) {
            if (isbnBuilder.length() > 0) {
                isbnBuilder.append("\n");
            }
            isbnBuilder.append("発売日: ").append(publishedDate);
        }
        isbnView.setText(isbnBuilder.toString());

        favoriteButton.setOnClickListener(v -> handleFavoriteButton());
        updateFavoriteButtonState();
    }

    private void handleFavoriteButton() {
        if (TextUtils.isEmpty(bookId)) {
            Toast.makeText(this, getString(R.string.favorite_add_failure), Toast.LENGTH_SHORT).show();
            return;
        }
        if (isFavorite) {
            Toast.makeText(this, getString(R.string.favorite_registered_label), Toast.LENGTH_SHORT).show();
            return;
        }
        if (!authManager.isLoggedIn()) {
            Toast.makeText(this, getString(R.string.login_required_message), Toast.LENGTH_SHORT).show();
            return;
        }
        new FavoriteTask().execute(bookId);
    }

    private void updateFavoriteButtonState() {
        if (favoriteButton == null) {
            return;
        }
        if (TextUtils.isEmpty(bookId)) {
            favoriteButton.setEnabled(false);
            favoriteButton.setText(R.string.favorite_button_text);
            return;
        }
        if (isFavorite) {
            favoriteButton.setEnabled(false);
            favoriteButton.setText(R.string.favorite_registered_label);
        } else {
            favoriteButton.setEnabled(true);
            favoriteButton.setText(R.string.favorite_button_text);
        }
    }

    private String formatPrice(double price) {
        if (price <= 0) {
            return "価格情報なし";
        }
        long rounded = Math.round(price);
        return String.format(Locale.JAPAN, "¥%,d", rounded);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private class FavoriteTask extends AsyncTask<String, Void, Boolean> {

        private String errorMessage;

        @Override
        protected Boolean doInBackground(String... params) {
            String id = params[0];
            HttpURLConnection connection = null;
            try {
                String baseUrl = getString(R.string.api_base_url);
                if (baseUrl.endsWith("/")) {
                    baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
                }
                URL url = new URL(baseUrl + "/favorites");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");

                String authHeader = authManager.getAuthorizationHeader();
                if (TextUtils.isEmpty(authHeader)) {
                    errorMessage = getString(R.string.login_required_message);
                    return false;
                }
                connection.setRequestProperty("Authorization", authHeader);

                JSONObject payload = new JSONObject();
                payload.put("bookId", id);
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                }

                int status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 300 ?
                        connection.getInputStream() : connection.getErrorStream();
                String response = readStream(stream);

                if (status >= 200 && status < 300) {
                    return true;
                }

                if (status == 401) {
                    authManager.clear();
                    errorMessage = getString(R.string.auth_failed);
                    return false;
                }

                errorMessage = !TextUtils.isEmpty(response)
                        ? response
                        : getString(R.string.favorite_add_failure);
            } catch (Exception e) {
                errorMessage = e.getMessage();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            return false;
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
            if (success) {
                isFavorite = true;
                updateFavoriteButtonState();
                Toast.makeText(BookInfoActivity.this,
                        getString(R.string.favorite_add_success),
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(BookInfoActivity.this,
                        TextUtils.isEmpty(errorMessage) ? getString(R.string.favorite_add_failure) : errorMessage,
                        Toast.LENGTH_LONG).show();
            }
        }
    }
}
