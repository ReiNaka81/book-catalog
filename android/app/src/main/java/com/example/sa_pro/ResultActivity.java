package com.example.sa_pro;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.nio.charset.StandardCharsets;

public class ResultActivity extends AppCompatActivity {

    private static final int PAGE_SIZE = 10;

    private class BookInfo {
        String bookId;
        String title;
        String author;
        String publisher;
        double price;
        String isbn;
        String publishedDate;
        boolean isFavorite;
    }

    private final List<BookInfo> bookList = new ArrayList<>();
    private final ArrayList<HashMap<String, String>> displayData = new ArrayList<>();
    private SimpleAdapter adapter;
    private ListView listView;
    private TextView pageIndicator;
    private Button prevButton;
    private Button nextButton;
    private int currentPage = 0;
    private AuthManager authManager;
    private final Set<String> favoriteBookIds = new HashSet<>();
    private boolean isFavoriteTaskRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        Intent intent = getIntent();
        String query = intent.getStringExtra("QUERY");
        String mode = intent.getStringExtra("MODE");
        if (mode == null) {
            mode = "all";
        }

        pageIndicator = findViewById(R.id.pageIndicator);
        prevButton = findViewById(R.id.prevButton);
        nextButton = findViewById(R.id.nextButton);
        listView = findViewById(R.id.listView);

        adapter = new SimpleAdapter(
                this,
                displayData,
                android.R.layout.simple_list_item_2,
                new String[]{"title", "author"},
                new int[]{android.R.id.text1, android.R.id.text2}
        );
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView parent, View view, int position, long id) {
                int actualIndex = currentPage * PAGE_SIZE + position;
                if (actualIndex >= 0 && actualIndex < bookList.size()) {
                    openBookDetail(bookList.get(actualIndex));
                }
            }
        });

        prevButton.setOnClickListener(v -> showPreviousPage());
        nextButton.setOnClickListener(v -> showNextPage());

        authManager = AuthManager.getInstance(getApplicationContext());

        SearchTask task = new SearchTask(getString(R.string.api_base_url), authManager);
        task.setListener(new SearchTask.Listener() {
            @Override
            public void onSuccess(String result) {
                try {
                    JSONObject payload = new JSONObject(result);
                    JSONArray jsonArray = payload.optJSONArray("items");
                    if (jsonArray == null) {
                        Toast.makeText(ResultActivity.this, "書籍が見つかりません", Toast.LENGTH_SHORT).show();
                        bookList.clear();
                        renderCurrentPage();
                        return;
                    }

                    bookList.clear();
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject jsonObject = jsonArray.getJSONObject(i);
                        BookInfo bookInfo = new BookInfo();
                        bookInfo.bookId = jsonObject.optString("bookId");
                        bookInfo.title = jsonObject.optString("title");
                        bookInfo.author = jsonObject.optString("author");
                        bookInfo.publisher = jsonObject.optString("publisher");
                        bookInfo.price = jsonObject.optDouble("price", 0);
                        bookInfo.isbn = jsonObject.optString("isbn");
                        bookInfo.publishedDate = jsonObject.optString("publishedDate");
                        bookList.add(bookInfo);
                    }

                    currentPage = 0;
                    renderCurrentPage();
                } catch (JSONException e) {
                    Toast.makeText(ResultActivity.this, "データの解析に失敗しました", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ResultActivity.this, "検索に失敗しました: " + message, Toast.LENGTH_LONG).show();
            }
        });

        task.execute(mode, query);

        if (authManager.isLoggedIn()) {
            loadFavoriteIds();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (authManager != null && authManager.isLoggedIn()) {
            loadFavoriteIds();
        } else {
            favoriteBookIds.clear();
            renderCurrentPage();
        }
    }

    private void renderCurrentPage() {
        displayData.clear();
        int start = currentPage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, bookList.size());

        for (int i = start; i < end; i++) {
            BookInfo b = bookList.get(i);
            HashMap<String, String> row = new HashMap<>();
            row.put("title", b.title);
            b.isFavorite = favoriteBookIds.contains(b.bookId);
            String authorLine = b.author;
            if (b.isFavorite) {
                String label = getString(R.string.favorite_registered_label);
                if (TextUtils.isEmpty(authorLine)) {
                    authorLine = label;
                } else {
                    authorLine = authorLine + " (" + label + ")";
                }
            }
            row.put("author", authorLine);
            displayData.add(row);
        }

        adapter.notifyDataSetChanged();

        int totalPages = Math.max(1, (int) Math.ceil(bookList.size() / (double) PAGE_SIZE));
        pageIndicator.setText((currentPage + 1) + " / " + totalPages);

        prevButton.setEnabled(currentPage > 0);
        nextButton.setEnabled(end < bookList.size());
    }

    private void showNextPage() {
        if ((currentPage + 1) * PAGE_SIZE >= bookList.size()) {
            Toast.makeText(this, "これ以上ありません", Toast.LENGTH_SHORT).show();
            return;
        }
        currentPage++;
        renderCurrentPage();
    }

    private void showPreviousPage() {
        if (currentPage == 0) {
            Toast.makeText(this, "最初のページです", Toast.LENGTH_SHORT).show();
            return;
        }
        currentPage--;
        renderCurrentPage();
    }

    private void openBookDetail(BookInfo bookInfo) {
        Intent intent = new Intent(ResultActivity.this, BookInfoActivity.class);
        intent.putExtra("bookId", bookInfo.bookId);
        intent.putExtra("title", bookInfo.title);
        intent.putExtra("author", bookInfo.author);
        intent.putExtra("publisher", bookInfo.publisher);
        intent.putExtra("price", bookInfo.price);
        intent.putExtra("isbn", bookInfo.isbn);
        intent.putExtra("publishedDate", bookInfo.publishedDate);
        intent.putExtra("isFavorite", bookInfo.isFavorite);
        startActivity(intent);
    }

    private void loadFavoriteIds() {
        if (!authManager.isLoggedIn() || isFavoriteTaskRunning) {
            return;
        }
        new LoadFavoriteIdsTask().execute();
    }

    private class LoadFavoriteIdsTask extends AsyncTask<Void, Void, ArrayList<String>> {
        private boolean unauthorized;
        private String errorMessage;

        @Override
        protected void onPreExecute() {
            isFavoriteTaskRunning = true;
        }

        @Override
        protected ArrayList<String> doInBackground(Void... voids) {
            HttpURLConnection connection = null;
            try {
                String baseUrl = getString(R.string.api_base_url);
                if (baseUrl.endsWith("/")) {
                    baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
                }
                URL url = new URL(baseUrl + "/favorites");
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
                    ArrayList<String> ids = new ArrayList<>();
                    JSONObject payload = new JSONObject(response);
                    JSONArray items = payload.optJSONArray("items");
                    if (items != null) {
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject obj = items.optJSONObject(i);
                            if (obj == null) {
                                continue;
                            }
                            String bookId = obj.optString("bookId");
                            if (!TextUtils.isEmpty(bookId)) {
                                ids.add(bookId);
                            }
                        }
                    }
                    return ids;
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
        protected void onPostExecute(ArrayList<String> result) {
            isFavoriteTaskRunning = false;
            if (result != null) {
                favoriteBookIds.clear();
                favoriteBookIds.addAll(result);
                renderCurrentPage();
                return;
            }

            if (unauthorized) {
                authManager.clear();
                Toast.makeText(ResultActivity.this, getString(R.string.auth_failed), Toast.LENGTH_LONG).show();
                return;
            }

            if (errorMessage != null) {
                Toast.makeText(ResultActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
            }
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
}
