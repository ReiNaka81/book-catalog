package com.example.sa_pro;

import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class FavoritesActivity extends AppCompatActivity {

    private static class FavoriteItem {
        String bookId;
    }

    private AuthManager authManager;
    private ProgressBar loadingView;
    private TextView emptyView;
    private ListView listView;
    private Button reloadButton;
    private SimpleAdapter adapter;

    private final List<FavoriteItem> favoriteItems = new ArrayList<>();
    private final ArrayList<HashMap<String, String>> displayData = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_favorites);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.favoritesRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        authManager = AuthManager.getInstance(getApplicationContext());
        loadingView = findViewById(R.id.favoriteLoading);
        emptyView = findViewById(R.id.favoriteEmptyView);
        listView = findViewById(R.id.favoriteListView);
        reloadButton = findViewById(R.id.reloadFavoritesButton);

        adapter = new SimpleAdapter(
                this,
                displayData,
                android.R.layout.simple_list_item_1,
                new String[]{"line1"},
                new int[]{android.R.id.text1}
        );
        listView.setAdapter(adapter);
        listView.setEmptyView(emptyView);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < favoriteItems.size()) {
                    FavoriteItem item = favoriteItems.get(position);
                    confirmDelete(item.bookId);
                }
            }
        });

        reloadButton.setOnClickListener(v -> loadFavorites());

        loadFavorites();
    }

    private void loadFavorites() {
        if (!authManager.isLoggedIn()) {
            Toast.makeText(this, getString(R.string.login_required_message), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        new LoadFavoritesTask().execute();
    }

    private void renderFavorites() {
        displayData.clear();
        for (FavoriteItem item : favoriteItems) {
            HashMap<String, String> row = new HashMap<>();
            row.put("line1", item.bookId);
            displayData.add(row);
        }
        adapter.notifyDataSetChanged();
    }

    private void confirmDelete(String bookId) {
        if (TextUtils.isEmpty(bookId)) {
            return;
        }
        new AlertDialog.Builder(this)
                .setMessage(R.string.favorites_delete_confirm)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> deleteFavorite(bookId))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void deleteFavorite(String bookId) {
        if (!authManager.isLoggedIn()) {
            Toast.makeText(this, getString(R.string.login_required_message), Toast.LENGTH_SHORT).show();
            return;
        }
        new DeleteFavoriteTask().execute(bookId);
    }

    private class LoadFavoritesTask extends AsyncTask<Void, Void, List<FavoriteItem>> {
        private String errorMessage;
        private boolean unauthorized;

        @Override
        protected void onPreExecute() {
            loadingView.setVisibility(View.VISIBLE);
        }

        @Override
        protected List<FavoriteItem> doInBackground(Void... voids) {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(buildFavoritesUrl(null));
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                String authHeader = authManager.getAuthorizationHeader();
                if (TextUtils.isEmpty(authHeader)) {
                    unauthorized = true;
                    return null;
                }
                connection.setRequestProperty("Authorization", authHeader);

                int status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 300 ?
                        connection.getInputStream() : connection.getErrorStream();
                String response = readStream(stream);

                if (status >= 200 && status < 300) {
                    ArrayList<FavoriteItem> result = new ArrayList<>();
                    JSONObject payload = new JSONObject(response);
                    JSONArray items = payload.optJSONArray("items");
                    if (items != null) {
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject obj = items.optJSONObject(i);
                            if (obj == null) {
                                continue;
                            }
                            String bookId = obj.optString("bookId");
                            if (TextUtils.isEmpty(bookId)) {
                                continue;
                            }
                            FavoriteItem item = new FavoriteItem();
                            item.bookId = bookId;
                            result.add(item);
                        }
                    }
                    return result;
                }

                if (status == 401) {
                    unauthorized = true;
                    return null;
                }

                errorMessage = !TextUtils.isEmpty(response) ? response : getString(R.string.favorites_load_failure);
            } catch (Exception e) {
                errorMessage = e.getMessage();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(List<FavoriteItem> result) {
            loadingView.setVisibility(View.GONE);
            if (result != null) {
                favoriteItems.clear();
                favoriteItems.addAll(result);
                renderFavorites();
                return;
            }

            if (unauthorized) {
                authManager.clear();
                Toast.makeText(FavoritesActivity.this, getString(R.string.auth_failed), Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            Toast.makeText(FavoritesActivity.this,
                    errorMessage != null ? errorMessage : getString(R.string.favorites_load_failure),
                    Toast.LENGTH_LONG).show();
        }
    }

    private class DeleteFavoriteTask extends AsyncTask<String, Void, Boolean> {
        private String errorMessage;
        private boolean unauthorized;

        @Override
        protected void onPreExecute() {
            loadingView.setVisibility(View.VISIBLE);
        }

        @Override
        protected Boolean doInBackground(String... params) {
            String bookId = params[0];
            HttpURLConnection connection = null;
            try {
                URL url = new URL(buildFavoritesUrl(bookId));
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("DELETE");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                String authHeader = authManager.getAuthorizationHeader();
                if (TextUtils.isEmpty(authHeader)) {
                    unauthorized = true;
                    return false;
                }
                connection.setRequestProperty("Authorization", authHeader);

                int status = connection.getResponseCode();
                if (status >= 200 && status < 300) {
                    return true;
                }

                if (status == 401) {
                    unauthorized = true;
                    return false;
                }

                InputStream stream = connection.getErrorStream();
                String response = readStream(stream);
                errorMessage = !TextUtils.isEmpty(response) ? response : getString(R.string.favorites_delete_failure);
            } catch (Exception e) {
                errorMessage = e.getMessage();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            loadingView.setVisibility(View.GONE);
            if (success) {
                Toast.makeText(FavoritesActivity.this, getString(R.string.favorites_delete_success), Toast.LENGTH_SHORT).show();
                loadFavorites();
                return;
            }

            if (unauthorized) {
                authManager.clear();
                Toast.makeText(FavoritesActivity.this, getString(R.string.auth_failed), Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            Toast.makeText(FavoritesActivity.this,
                    errorMessage != null ? errorMessage : getString(R.string.favorites_delete_failure),
                    Toast.LENGTH_LONG).show();
        }
    }

    private String buildFavoritesUrl(String bookId) throws Exception {
        String baseUrl = getString(R.string.api_base_url);
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (TextUtils.isEmpty(bookId)) {
            return baseUrl + "/favorites";
        }
        return baseUrl + "/favorites/" + URLEncoder.encode(bookId, "UTF-8");
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
}
