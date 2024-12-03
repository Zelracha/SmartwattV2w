package com.example.smartwattv2;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceManager;

import java.io.IOException;
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

    private TextView tvVoltage, tvCurrent, tvPower, tvEnergy, tvConnectionStatus;
    private EditText etConsumptionLimit, etEsp32IpAddress;
    private Button btnUpdate, btnSaveIp;
    private ProgressBar progressBar;
    private OkHttpClient client;
    private Handler handler;
    private Runnable fetchRunnable;
    private SharedPreferences sharedPreferences;

    private boolean isConnecting = false;
    private int connectionAttempts = 0;
    private static final int MAX_CONNECTION_ATTEMPTS = 3;

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

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        initializeViews();
        setupOkHttpClient();
        handler = new Handler(Looper.getMainLooper());
        setupFetchRunnable();
        setupListeners();

        String savedIpAddress = sharedPreferences.getString(IP_PREFERENCE_KEY, DEFAULT_IP);
        etEsp32IpAddress.setText(savedIpAddress);

        Log.i(TAG, "SmartWatt App Initialized");
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

            // Find the data between <div id='data'> and </div>
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
        String ipAddress = etEsp32IpAddress.getText().toString().trim();
        String limit = etConsumptionLimit.getText().toString().trim();

        if (limit.isEmpty()) {
            Toast.makeText(this, "Please enter a consumption limit", Toast.LENGTH_SHORT).show();
            return;
        }

        String url = String.format("http://%s/set_time?consumption_limit=%s", ipAddress, limit);
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
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(fetchRunnable);
        Log.i(TAG, "Activity destroyed, fetch callbacks removed");
    }
}