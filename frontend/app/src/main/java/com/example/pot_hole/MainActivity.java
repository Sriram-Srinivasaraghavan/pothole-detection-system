package com.example.pot_hole;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Toast;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import android.net.Uri;
import android.content.ContentValues;

import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity implements LocationListener {

    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private static final int REQUEST_LOCATION_PERMISSION = 2;
    private static final int REQUEST_SEND_SMS = 3;
    private static final String TAG = "MainActivity";
    private static final String PREFS_NAME = "PotholePrefs";
    private static final String DEVICE_ID_KEY = "device_id";
    private static final String API_URL = "https://pothole-detection-system-hwwr.onrender.com/detect";
    private static final String POTHOLES_URL = "https://pothole-detection-system-hwwr.onrender.com/potholes";
    private static final String VIEW_URL = "https://pothole-detection-system-hwwr.onrender.com/view/";
    private ImageView imageView;
    private WebView webView;
    private LocationManager locationManager;
    private Location currentLocation;
    private ArrayList<String> potholeList = new ArrayList<>();
    private boolean toastMessagesEnabled = true;
    private boolean sendSmsEnabled = true;

    private final ActivityResultLauncher<Intent> takePictureLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    new ActivityResultCallback<ActivityResult>() {
                        @Override
                        public void onActivityResult(ActivityResult result) {
                            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                                Bundle extras = result.getData().getExtras();
                                Bitmap imageBitmap = (Bitmap) extras.get("data");
                                imageView.setImageBitmap(imageBitmap);
                                classifyImage(imageBitmap);
                            }
                        }
                    });

    // ── Device ID ─────────────────────────────────────────────────────────────
    private String fetchDeviceId() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String deviceId = prefs.getString(DEVICE_ID_KEY, null);
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString();
            prefs.edit().putString(DEVICE_ID_KEY, deviceId).apply();
            Log.d(TAG, "Generated new device_id: " + deviceId);
        }
        return deviceId;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageView = findViewById(R.id.image_view);
        webView = findViewById(R.id.webview);
        ImageButton takePictureButton = findViewById(R.id.take_picture_button);
        ImageButton refreshButton = findViewById(R.id.refresh_button);
        ImageButton potholeListButton = findViewById(R.id.pothole_list_button);
        ImageButton settingsButton = findViewById(R.id.settings_button);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (currentLocation != null) {
                    String jsCode = "window.postMessage({type: 'currentLocation', lat: "
                            + currentLocation.getLatitude() + ", lon: "
                            + currentLocation.getLongitude() + "}, '*');";
                    webView.evaluateJavascript(jsCode, null);
                }
                loadSavedPotholes();
            }
        });

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webView.loadUrl("file:///android_asset/map.html");

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        checkLocationServices();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_PERMISSION);
        } else {
            startLocationUpdates();
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        }

        takePictureButton.setOnClickListener(v -> dispatchTakePictureIntent());
        refreshButton.setOnClickListener(v -> refreshMap());
        potholeListButton.setOnClickListener(v -> showPotholeListDialog());
        settingsButton.setOnClickListener(v -> showSettingsDialog());
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            takePictureLauncher.launch(takePictureIntent);
        }
    }

    private void classifyImage(Bitmap bitmap) {
        if (toastMessagesEnabled) {
            Toast.makeText(this, "Detecting pothole...", Toast.LENGTH_SHORT).show();
        }

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream);
        byte[] imageBytes = stream.toByteArray();
        String deviceId = fetchDeviceId();

        new Thread(() -> {
            try {
                String boundary = "----Boundary" + System.currentTimeMillis();
                URL url = new URL(API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(120000);
                conn.setReadTimeout(120000);
                conn.setRequestProperty("Content-Type",
                        "multipart/form-data; boundary=" + boundary);

                OutputStream outputStream = conn.getOutputStream();
                PrintWriter writer = new PrintWriter(
                        new OutputStreamWriter(outputStream, "UTF-8"), true);

                // Send latitude
                writer.append("--").append(boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"latitude\"").append("\r\n\r\n");
                writer.append(currentLocation != null ? String.valueOf(currentLocation.getLatitude()) : "0").append("\r\n");

                // Send longitude
                writer.append("--").append(boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"longitude\"").append("\r\n\r\n");
                writer.append(currentLocation != null ? String.valueOf(currentLocation.getLongitude()) : "0").append("\r\n");

                // Send device_id
                writer.append("--").append(boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"device_id\"").append("\r\n\r\n");
                writer.append(deviceId).append("\r\n");

                // Send image
                writer.append("--").append(boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"image\"; filename=\"image.jpg\"").append("\r\n");
                writer.append("Content-Type: image/jpeg").append("\r\n\r\n");
                writer.flush();
                outputStream.write(imageBytes);
                outputStream.flush();
                writer.append("\r\n");
                writer.append("--").append(boundary).append("--").append("\r\n");
                writer.close();

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JSONObject json = new JSONObject(response.toString());
                    boolean potholeDetected = json.getBoolean("pothole_detected");
                    int potholeCount = json.getInt("pothole_count");
                    String severity = json.getString("severity");
                    String imageId = json.optString("image_id", null);

                    String base64Image = json.getString("annotated_image");
                    byte[] decodedBytes = android.util.Base64.decode(
                            base64Image, android.util.Base64.DEFAULT);
                    Bitmap annotatedBitmap = android.graphics.BitmapFactory
                            .decodeByteArray(decodedBytes, 0, decodedBytes.length);

                    runOnUiThread(() -> {
                        if (potholeDetected) {
                            double lat = currentLocation != null ? currentLocation.getLatitude() : 0.0;
                            double lon = currentLocation != null ? currentLocation.getLongitude() : 0.0;

                            imageView.setImageBitmap(annotatedBitmap);

                            // Pin on map
                            String jsCode = "window.postMessage({type: 'location', lat: "
                                    + lat + ", lon: " + lon + "}, '*');";
                            webView.evaluateJavascript(jsCode, null);

                            if (sendSmsEnabled) {
                                sendMms(annotatedBitmap, lat, lon, severity, imageId);
                            }

                            // Reload list from MongoDB (user-specific)
                            loadSavedPotholes();

                            if (toastMessagesEnabled) {
                                Toast.makeText(MainActivity.this,
                                        "Pothole detected!\nSeverity: " + severity +
                                                "\nCount: " + potholeCount,
                                        Toast.LENGTH_LONG).show();
                            }
                        } else {
                            if (toastMessagesEnabled) {
                                Toast.makeText(MainActivity.this,
                                        "Not a Pothole", Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                }
                conn.disconnect();

            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "API call failed", e);
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "Detection failed: " + e.getMessage(),
                        Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void sendMms(Bitmap annotatedBitmap, double lat, double lon, String severity, String imageId) {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "pothole_" + System.currentTimeMillis() + ".jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Potholes");
            Uri imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            try (OutputStream os = getContentResolver().openOutputStream(imageUri)) {
                annotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, os);
            }

            String viewUrl = VIEW_URL + imageId;
            String googleMapsUrl = "https://www.google.com/maps?q=" + lat + "," + lon;
            String message = "Pothole detected! Severity: " + severity +
                    " | Location: " + googleMapsUrl +
                    " | View image: " + viewUrl;

            Intent smsIntent = new Intent(Intent.ACTION_VIEW);
            smsIntent.setData(Uri.parse("sms:9884875776"));
            smsIntent.putExtra("sms_body", message);
            startActivity(smsIntent);

            Toast.makeText(this, "Opening messaging app...", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Log.e(TAG, "SMS failed", e);
            Toast.makeText(this, "SMS failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 10, this);
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 10, this);
            Location lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastKnownLocation != null) {
                currentLocation = lastKnownLocation;
                onLocationChanged(lastKnownLocation);
            }
        }
    }

    private void checkLocationServices() {
        boolean gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        if (!gpsEnabled && !networkEnabled) {
            new AlertDialog.Builder(this)
                    .setTitle("Enable Location")
                    .setMessage("Please enable location to use this app.")
                    .setPositiveButton("Location Settings", (dialog, which) ->
                            startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        currentLocation = location;
        String jsCode = "window.postMessage({type: 'currentLocation', lat: "
                + location.getLatitude() + ", lon: " + location.getLongitude() + "}, '*');";
        webView.evaluateJavascript(jsCode, null);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                dispatchTakePictureIntent();
            } else {
                new AlertDialog.Builder(this)
                        .setTitle("Permission Required")
                        .setMessage("Camera permission is required.")
                        .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                        .show();
            }
        } else if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            } else {
                new AlertDialog.Builder(this)
                        .setTitle("Permission Required")
                        .setMessage("Location permission is required.")
                        .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                        .show();
            }
        } else if (requestCode == REQUEST_SEND_SMS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted. Please take photo again.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "SMS permission denied.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void refreshMap() {
        webView.evaluateJavascript("window.clearPotholes();", null);
        webView.reload();
        if (toastMessagesEnabled) {
            Toast.makeText(this, "Map refreshed", Toast.LENGTH_SHORT).show();
        }
    }

    private void showPotholeListDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("My Potholes");
        ListView listView = new ListView(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, potholeList);
        listView.setAdapter(adapter);
        builder.setView(listView);
        builder.setPositiveButton("OK", (dialog, which) -> dialog.dismiss());
        builder.create().show();
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Settings");
        LayoutInflater inflater = getLayoutInflater();
        View settingsView = inflater.inflate(R.layout.dialog_settings, null);
        CheckBox toastMessagesCheckBox = settingsView.findViewById(R.id.toast_messages_checkbox);
        toastMessagesCheckBox.setChecked(toastMessagesEnabled);
        CheckBox sendSmsCheckBox = settingsView.findViewById(R.id.send_sms_checkbox);
        sendSmsCheckBox.setChecked(sendSmsEnabled);
        builder.setView(settingsView);
        builder.setPositiveButton("Save", (dialog, which) -> {
            toastMessagesEnabled = toastMessagesCheckBox.isChecked();
            sendSmsEnabled = sendSmsCheckBox.isChecked();
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.create().show();
    }

    // ── Load potholes from MongoDB filtered by this device ────────────────────
    private void loadSavedPotholes() {
        String deviceId = fetchDeviceId();
        new Thread(() -> {
            try {
                URL url = new URL(POTHOLES_URL + "?device_id=" + deviceId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                conn.disconnect();

                JSONArray jsonArray = new JSONArray(response.toString());
                potholeList.clear();

                runOnUiThread(() ->
                        webView.evaluateJavascript("window.clearPotholes();", null));

                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject obj = jsonArray.getJSONObject(i);
                    double lat = obj.getDouble("latitude");
                    double lon = obj.getDouble("longitude");
                    String severity = obj.getString("severity");
                    String timestamp = obj.getString("timestamp");

                    String pothole = "Severity: " + severity
                            + "\nLat: " + lat
                            + "\nLon: " + lon
                            + "\nTime: " + timestamp;
                    potholeList.add(pothole);

                    runOnUiThread(() -> {
                        String jsCode = "window.postMessage({type: 'location', lat: "
                                + lat + ", lon: " + lon + "}, '*');";
                        webView.evaluateJavascript(jsCode, null);
                    });
                }

            } catch (Exception e) {
                Log.e(TAG, "Failed to load potholes", e);
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "Failed to load potholes", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}