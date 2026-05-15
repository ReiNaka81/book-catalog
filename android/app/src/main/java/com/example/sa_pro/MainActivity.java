package com.example.sa_pro;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    private String[] searchModeValues;
    private AuthManager authManager;
    private TextView authStatusView;
    private Button authButton;
    private Button favoritesButton;
    private ActivityResultLauncher<Intent> loginLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        searchModeValues = getResources().getStringArray(R.array.search_mode_values);
        authManager = AuthManager.getInstance(getApplicationContext());
        authStatusView = findViewById(R.id.authStatusView);
        authButton = findViewById(R.id.authButton);
        favoritesButton = findViewById(R.id.favoritesButton);

        loginLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        Toast.makeText(this, getString(R.string.auth_success), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, getString(R.string.auth_cancelled), Toast.LENGTH_SHORT).show();
                    }
                    updateAuthUi();
                }
        );

        authButton.setOnClickListener(v -> {
            if (authManager.isLoggedIn()) {
                authManager.clear();
                Toast.makeText(this, getString(R.string.logout_success), Toast.LENGTH_SHORT).show();
                updateAuthUi();
            } else {
                Intent intent = new Intent(this, LoginActivity.class);
                loginLauncher.launch(intent);
            }
        });

        favoritesButton.setOnClickListener(v -> openFavorites());

        updateAuthUi();
    }

    private void updateAuthUi() {
        boolean loggedIn = authManager.isLoggedIn();
        favoritesButton.setEnabled(loggedIn);

        if (loggedIn) {
            authButton.setText(R.string.logout_button_text);
            String name = authManager.getDisplayName();
            if (name == null || name.isEmpty()) {
                name = "ユーザー";
            }
            authStatusView.setText(getString(R.string.auth_status_signed_in, name));
        } else {
            authButton.setText(R.string.login_button_text);
            authStatusView.setText(R.string.auth_status_signed_out);
        }
    }

    private void openFavorites() {
        if (!authManager.isLoggedIn()) {
            Toast.makeText(this, getString(R.string.login_required_message), Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, FavoritesActivity.class);
        startActivity(intent);
    }

    public void onSearchButtonClicked(View view) {
        Intent intent = new Intent(this, ResultActivity.class);
        EditText keywordText = findViewById(R.id.keywordText);
        Spinner modeSpinner = findViewById(R.id.searchModeSpinner);

        String query = keywordText.getText().toString();
        String mode = searchModeValues[Math.max(0, modeSpinner.getSelectedItemPosition())];

        if (!"all".equals(mode) && TextUtils.isEmpty(query.trim())) {
            Toast.makeText(this, "キーワードを入力してください", Toast.LENGTH_SHORT).show();
            return;
        }

        intent.putExtra("QUERY", query);
        intent.putExtra("MODE", mode);
        startActivity(intent);
    }

}
