// Author: Engr. Raizel Sablan

package com.example.smartwattv2;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.RequestBody;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "SmartWattApp";
    private static final int FETCH_INTERVAL = 1000; // Fetch data every 1 second
    private static final int CONNECTION_TIMEOUT = 5000; // 5 seconds
    private static final String DEFAULT_IP = "192.168.4.1"; // Default ESP32 AP IP
    private static final String IP_PREFERENCE_KEY = "ESP32_IP";
    private static final int PERMISSION_REQUEST_CODE = 123;

    // Test variables
    private float testEnergy = 0.0f;
    private Handler testHandler = new Handler(Looper.getMainLooper());
    private final float ENERGY_INCREMENT = 0.1f; // Increase by 0.1 kWh each update
    private boolean isTestMode = false; // Set to true to enable test mode
    private boolean hasExceededLimit = false;

    private TextView tvVoltage, tvCurrent, tvPower, tvEnergy, tvConnectionStatus;
    private EditText etConsumptionLimit, etEsp32IpAddress;
    private Button btnUpdate, btnSaveIp;
    private MaterialButton btnResetTest;
    private ProgressBar progressBar;
    private MaterialCardView alertBanner;
    private TextView alertText;
    private ImageButton dismissAlert;
    private NotificationHelper notificationHelper;
    private FloatingActionButton fabRecommendations;

    private OkHttpClient client;
    private Handler handler;
    private Runnable fetchRunnable;
    private SharedPreferences sharedPreferences;

    private boolean isConnecting = false;
    private int connectionAttempts = 0;
    private static final int MAX_CONNECTION_ATTEMPTS = 3;
    private float consumptionLimit = 3.6f; // Default value changed to 3.6 kWh

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                .permitAll()
                .build();
        StrictMode.setThreadPolicy(policy);

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Request notification permission
        requestNotificationPermission();

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        initializeViews();
        setupOkHttpClient();

        notificationHelper = new NotificationHelper(this);
        handler = new Handler(Looper.getMainLooper());

        setupFetchRunnable();
        setupListeners();
        initializeAlertBanner();

        String savedIpAddress = sharedPreferences.getString(IP_PREFERENCE_KEY, DEFAULT_IP);
        etEsp32IpAddress.setText(savedIpAddress);

        // Load saved consumption limit if exists
        consumptionLimit = sharedPreferences.getFloat("CONSUMPTION_LIMIT", 3.6f);
        etConsumptionLimit.setText(String.valueOf(consumptionLimit));

        if (isTestMode) {
            // Disable actual data fetching
            handler.removeCallbacks(fetchRunnable);
            // Start test mode
            startTestMode();
            // Show toast to indicate test mode
            Toast.makeText(this, "Running in Test Mode", Toast.LENGTH_LONG).show();
        }

        Log.i(TAG, "SmartWatt App Initialized");
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        PERMISSION_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this,
                        "Notification permission denied. Some features may not work.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showRecommendationsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_recommendations, null);
        builder.setView(dialogView);

        TextView titleUsageStatus = dialogView.findViewById(R.id.titleUsageStatus);
        TextView textCurrentStatus = dialogView.findViewById(R.id.textCurrentStatus);
        LinearLayout recommendationsContainer = dialogView.findViewById(R.id.recommendationsContainer);
        LinearLayout generalTipsContainer = dialogView.findViewById(R.id.generalTipsContainer);

        // Get current energy value
        float currentEnergy = isTestMode ? testEnergy : 0; // Use actual energy value in non-test mode

        // Set current status
        titleUsageStatus.setText("Current Usage Status");
        textCurrentStatus.setText(String.format(Locale.US,
                "Current consumption: %.2f kWh\nLimit: %.2f kWh",
                currentEnergy, consumptionLimit));

        // Add consumption-based recommendations
        List<PowerRecommendation.Recommendation> consumptionRecs =
                PowerRecommendation.getConsumptionBasedRecommendations(currentEnergy, consumptionLimit);

        for (PowerRecommendation.Recommendation rec : consumptionRecs) {
            addRecommendationView(recommendationsContainer, rec);
        }

        // Add general recommendations
        List<PowerRecommendation.Recommendation> generalRecs =
                PowerRecommendation.getGeneralRecommendations();

        for (PowerRecommendation.Recommendation rec : generalRecs) {
            addRecommendationView(generalTipsContainer, rec);
        }

        builder.setTitle("Power Saving Recommendations")
                .setPositiveButton("Close", null);

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void addRecommendationView(LinearLayout container, PowerRecommendation.Recommendation rec) {
        View recView = getLayoutInflater().inflate(R.layout.item_recommendation, container, false);

        TextView titleView = recView.findViewById(R.id.recTitle);
        TextView descView = recView.findViewById(R.id.recDescription);
        TextView categoryView = recView.findViewById(R.id.recCategory);

        titleView.setText(rec.title);
        descView.setText(rec.description);
        categoryView.setText(rec.category);

        container.addView(recView);
    }

    private void startTestMode() {
        Runnable testRunnable = new Runnable() {
            @Override
            public void run() {
                if (isTestMode) {
                    // Simulate increasing energy consumption
                    testEnergy += ENERGY_INCREMENT;

                    // Create test data
                    String testData = String.format(Locale.US,
                            "<div id='data'>220.0,5.0,1.1,%.2f</div>", testEnergy);

                    // Update UI with test data
                    updateUI(testData);

                    // Schedule next update
                    testHandler.postDelayed(this, 5000); // Update every 5 seconds
                }
            }
        };

        // Start the test updates
        testHandler.post(testRunnable);
    }

    private void resetTest() {
        testEnergy = 0.0f;
        hasExceededLimit = false;
        alertBanner.setVisibility(View.GONE);
        String testData = String.format(Locale.US,
                "<div id='data'>220.0,5.0,1.1,%.2f</div>", testEnergy);
        updateUI(testData);
    }

    private void initializeAlertBanner() {
        alertBanner = findViewById(R.id.alertBanner);
        alertText = findViewById(R.id.alertText);
        dismissAlert = findViewById(R.id.dismissAlert);

        dismissAlert.setOnClickListener(v -> alertBanner.setVisibility(View.GONE));
    }

    private void setupOkHttpClient() {
        client = new OkHttpClient.Builder()
                .connectTimeout(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS)
                .readTimeout(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS)
                .writeTimeout(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    private void initializeViews() {
        tvVoltage = findViewById(R.id.tvVoltage);
        tvCurrent = findViewById(R.id.tvCurrent);
        tvPower = findViewById(R.id.tvPower);
        tvEnergy = findViewById(R.id.tvEnergy);
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        etConsumptionLimit = findViewById(R.id.etConsumptionLimit);
        etEsp32IpAddress = findViewById(R.id.etEsp32IpAddress);
        btnUpdate = findViewById(R.id.btnUpdate);
        btnSaveIp = findViewById(R.id.btnSaveIp);
        progressBar = findViewById(R.id.progressBar);
        btnResetTest = findViewById(R.id.btnResetTest);
        fabRecommendations = findViewById(R.id.fabRecommendations);

        if (isTestMode) {
            btnResetTest.setVisibility(View.VISIBLE);
            btnResetTest.setOnClickListener(v -> resetTest());
        }

        fabRecommendations.setOnClickListener(v -> showRecommendationsDialog());
    }

    private void setupFetchRunnable() {
        fetchRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isConnecting && isConnectedToNetwork()) {
                    fetchData();
                }
                handler.postDelayed(this, FETCH_INTERVAL);
            }
        };
    }

    private void setupListeners() {
        btnUpdate.setOnClickListener(v -> updateSettings());
        btnSaveIp.setOnClickListener(v -> saveIpAddress());
        handler.post(fetchRunnable);
    }

    private boolean isConnectedToNetwork() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    private void saveIpAddress() {
        String ipAddress = etEsp32IpAddress.getText().toString().trim();
        if (ipAddress.isEmpty()) {
            Toast.makeText(this, "Please enter an IP address", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(IP_PREFERENCE_KEY, ipAddress);
        editor.apply();

        Toast.makeText(this, "IP Address Saved: " + ipAddress, Toast.LENGTH_SHORT).show();
        Log.i(TAG, "IP Address Updated: " + ipAddress);
    }

    private void checkConsumptionLimit(float currentConsumption) {
        if (currentConsumption > consumptionLimit) {
            // Show banner
            alertText.setText(String.format(Locale.US,
                    "Warning: Current consumption (%.2f kWh) has exceeded the limit (%.2f kWh)",
                    currentConsumption, consumptionLimit));
            alertBanner.setVisibility(View.VISIBLE);

            // Show notification only when first exceeding the limit
            if (!hasExceededLimit) {
                notificationHelper.showConsumptionAlert(currentConsumption, consumptionLimit);
                hasExceededLimit = true;
            }
        } else {
            alertBanner.setVisibility(View.GONE);
            hasExceededLimit = false;
        }
    }

    private void fetchData() {
        String ipAddress = etEsp32IpAddress.getText().toString().trim();
        String url = "http://" + ipAddress + "/raw";

        Log.d(TAG, "Fetching data from: " + url);

        isConnecting = true;
        progressBar.setVisibility(View.VISIBLE);
        tvConnectionStatus.setText("Connecting...");

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Connection", "close")
                .addHeader("Cache-Control", "no-cache")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Connection Error: " + e.getMessage(), e);
                runOnUiThread(() -> handleConnectionFailure(e));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    final String responseData = response.body().string();
                    Log.d(TAG, "Full response received: " + responseData);

                    runOnUiThread(() -> {
                        isConnecting = false;
                        progressBar.setVisibility(View.GONE);
                        connectionAttempts = 0;

                        if (response.isSuccessful()) {
                            try {
                                updateUI(responseData);
                                tvConnectionStatus.setText("Connected ✓");
                            } catch (Exception e) {
                                Log.e(TAG, "Error in updateUI: " + e.getMessage());
                                handleDataParseError(e);
                            }
                        } else {
                            handleServerError(response.code());
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error processing response", e);
                    runOnUiThread(() -> handleConnectionFailure(
                            new IOException("Error processing response: " + e.getMessage())));
                }
            }
        });
    }

    private void updateUI(String htmlResponse) {
        try {
            Log.d(TAG, "Raw HTML response: " + htmlResponse);

            int startIndex = htmlResponse.indexOf("<div id='data'>") + "<div id='data'>".length();
            int endIndex = htmlResponse.indexOf("</div>", startIndex);

            if (startIndex != -1 && endIndex != -1) {
                String data = htmlResponse.substring(startIndex, endIndex).trim();
                Log.d(TAG, "Extracted data: " + data);

                String[] values = data.split(",");
                Log.d(TAG, "Number of values: " + values.length);

                for (int i = 0; i < values.length; i++) {
                    Log.d(TAG, "Value " + i + ": '" + values[i].trim() + "'");
                }

                if (values.length >= 4) {
                    float voltage = Float.parseFloat(values[0].trim());
                    float current = Float.parseFloat(values[1].trim());
                    float power = Float.parseFloat(values[2].trim());
                    float energy = Float.parseFloat(values[3].trim());

                    tvVoltage.setText(String.format(Locale.US, "Voltage: %.2f V", voltage));
                    tvCurrent.setText(String.format(Locale.US, "Current: %.2f A", current));
                    tvPower.setText(String.format(Locale.US, "Power: %.2f kW", power));
                    tvEnergy.setText(String.format(Locale.US, "Energy: %.2f kWh", energy));

                    // Check consumption limit
                    checkConsumptionLimit(energy);

                    Log.d(TAG, String.format("Parsed values - V: %.2f, I: %.2f, P: %.2f, E: %.2f",
                            voltage, current, power, energy));
                } else {
                    throw new IllegalArgumentException("Insufficient data values");
                }
            } else {
                throw new IllegalArgumentException("Could not find data in response");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating UI: " + e.getMessage());
            throw new IllegalArgumentException("Error processing data: " + e.getMessage());
        }
    }

    private void handleDataParseError(Exception e) {
        tvConnectionStatus.setText("Data Error");
        String errorMessage = "Error parsing data: " + e.getMessage();
        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
        Log.e(TAG, errorMessage, e);
    }

    private void handleConnectionFailure(IOException e) {
        isConnecting = false;
        progressBar.setVisibility(View.GONE);
        connectionAttempts++;

        String errorMessage;
        if (e instanceof java.net.SocketTimeoutException) {
            errorMessage = "Connection timed out. Check IP address and network.";
        } else if (e instanceof java.net.UnknownHostException) {
            errorMessage = "Cannot find ESP32. Check IP address.";
        } else {
            errorMessage = "Connection Failed: " + e.getMessage();
        }

        tvConnectionStatus.setText("Connection Error");
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();

        if (connectionAttempts >= MAX_CONNECTION_ATTEMPTS) {
            Toast.makeText(this,
                    "Multiple connection failures. Check IP and network.",
                    Toast.LENGTH_LONG).show();
            connectionAttempts = 0;
        }
    }

    private void handleServerError(int code) {
        tvConnectionStatus.setText("ESP32 Not Reachable");
        Toast.makeText(this, "ESP32 returned error: " + code, Toast.LENGTH_SHORT).show();
        Log.w(TAG, "ESP32 not reachable. Response code: " + code);
    }

    private void updateSettings() {
        String limit = etConsumptionLimit.getText().toString().trim();

        if (limit.isEmpty()) {
            Toast.makeText(this, "Please enter a consumption limit", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            consumptionLimit = Float.parseFloat(limit);
            // Save the new consumption limit
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putFloat("CONSUMPTION_LIMIT", consumptionLimit);
            editor.apply();

            Toast.makeText(this, "Consumption limit updated to: " + consumptionLimit + " kWh",
                    Toast.LENGTH_SHORT).show();

            if (isTestMode) {
                // For testing, immediately check if current test value exceeds the new limit
                checkConsumptionLimit(testEnergy);
            }

            String url = String.format("http://%s/set_time?consumption_limit=%s",
                    etEsp32IpAddress.getText().toString().trim(), limit);
            Log.d(TAG, "Updating settings: " + url);

            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(null, new byte[0]))
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        String errorMessage = "Failed to update settings: " + e.getMessage();
                        Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Settings update failed", e);
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            Toast.makeText(MainActivity.this, "Settings Updated", Toast.LENGTH_SHORT).show();
                            Log.i(TAG, "Settings successfully updated");
                        } else {
                            Toast.makeText(MainActivity.this,
                                    "Failed to update settings. Response code: " + response.code(),
                                    Toast.LENGTH_SHORT).show();
                            Log.w(TAG, "Settings update failed. Response code: " + response.code());
                        }
                    });
                }
            });

        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid consumption limit", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(fetchRunnable);
        if (isTestMode) {
            testHandler.removeCallbacksAndMessages(null);
        }
        Log.i(TAG, "Activity destroyed, fetch callbacks removed");
    }
}