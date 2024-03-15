package com.radzdev.appupdater;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import android.Manifest;

import android.app.Dialog;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private int currentVersionCode;
    private int fileLength;
    private long totalBytesDownloaded = 0;
    private String updateLink;
    private String size;
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE = 1;

    private void requestWriteExternalStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_EXTERNAL_STORAGE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_WRITE_EXTERNAL_STORAGE) {
            // If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, proceed with downloadAndUpdate()
                downloadAndUpdate(updateLink, size);
            } else {
                // Permission denied, show a message or take appropriate action
                Toast.makeText(this, "Permission denied, unable to download update", Toast.LENGTH_SHORT).show();
            }
        }
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkForUpdates();

        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            currentVersionCode = packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e("MainActivity", "Error fetching current version code", e);
        }
    }


    private JSONObject getRemoteVersionData() {
        try {
            URL url = new URL(Constants.APP_UPDATER);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.connect();

            fileLength = connection.getContentLength();

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                InputStream inputStream = connection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                return new JSONObject(response.toString());
            }
        } catch (IOException | JSONException e) {
            Log.e("MainActivity", "Error fetching remote version data", e);
        }

        return null;
    }
    private void checkForUpdates() {
        new AsyncTask<Void, Void, JSONObject>() {
            @Override
            protected JSONObject doInBackground(Void... params) {
                return getRemoteVersionData();
            }

            @Override
            protected void onPostExecute(JSONObject jsonData) {
                if (jsonData != null) {
                    try {
                        int remoteVersionCode = jsonData.getInt("versionCode");
                        String updateLink = jsonData.getString("updateLink");
                        String size = jsonData.getString("size");
                        String updateContent = jsonData.getString("updateContent");

                        if (remoteVersionCode > currentVersionCode) {
                            showUpdateDialog(remoteVersionCode, size, updateContent, updateLink);
                        } else {
                            // Optional: Notify user that the app is up to date
                            Toast.makeText(MainActivity.this, "App is up to date", Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        Log.e("MainActivity", "Error parsing JSON response", e);
                    }
                } else {
                    // Optional: Handle case where JSON data is null
                    Toast.makeText(MainActivity.this, "Failed to fetch update data", Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
    }
    private void showUpdateDialog(int remoteVersionCode, String size, String updateContent, final String updateLink) {

        Dialog dialog = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
        // Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.custom_dialog);

        Window window = dialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
            layoutParams.copyFrom(window.getAttributes());
            layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
            layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
            window.setAttributes(layoutParams);
        }

        TextView sizeTextView = dialog.findViewById(R.id.size);
        TextView updateContentTextView = dialog.findViewById(R.id.updatecontent);

        sizeTextView.setText("Download Size: " + size);
        updateContentTextView.setText(updateContent);

        Button updateButton = dialog.findViewById(R.id.updateButton);
        updateButton.setOnClickListener(v -> {
            dialog.dismiss();
            downloadAndUpdate(updateLink, size);
        });

        dialog.setCancelable(false);
        dialog.show();
    }
    private PowerManager.WakeLock wakeLock;
    private void downloadAndUpdate(String updateLink, String size) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
        // Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.progress_dialog);

        // Acquire wake lock to keep the screen on
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::MyWakelockTag");
        wakeLock.acquire();

        ProgressBar progressBar = dialog.findViewById(R.id.progressBar);
        TextView progressPercentageTextView = dialog.findViewById(R.id.progressPercentageTextView);
        TextView progressMBTextView = dialog.findViewById(R.id.progressMBTextView);
        TextView sizeTextView = dialog.findViewById(R.id.sizeTextView);

        // Set the download size text
        sizeTextView.setText(" / " + size);

        dialog.setCancelable(false);
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
            layoutParams.copyFrom(window.getAttributes());
            layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
            layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
            window.setAttributes(layoutParams);
        }



        new AsyncTask<String, Integer, Boolean>() {
            @Override
            protected Boolean doInBackground(String... params) {
                try {
                    URL url = new URL(params[0]);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.connect();

                    int fileLength = connection.getContentLength();
                    InputStream input = new BufferedInputStream(url.openStream());

                    // Output file
                    File outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    File outputFile = new File(outputDir, "update.apk");
                    OutputStream output = new FileOutputStream(outputFile);

                    byte[] data = new byte[1024];
                    int count;
                    while ((count = input.read(data)) != -1) {
                        totalBytesDownloaded += count; // Update total bytes downloaded
                        int progress = (int) (totalBytesDownloaded * 100 / fileLength);
                        publishProgress(progress);
                        output.write(data, 0, count);
                    }

                    output.flush();
                    output.close();
                    input.close();
                    return true;
                } catch (Exception e) {
                    Log.e("Download", "Error downloading update: " + e.getMessage(), e);
                    return false;
                }
            }


            @Override
            protected void onProgressUpdate(Integer... values) {
                super.onProgressUpdate(values);

                // Update progress percentage TextView
                progressPercentageTextView.setText(values[0] + "%");

                // Calculate downloading size based on total bytes downloaded
                double progressSize;
                String sizeUnit;
                if (totalBytesDownloaded < 1024) {
                    // If less than 1KB, display in bytes
                    progressSize = totalBytesDownloaded;
                    sizeUnit = "Bytes";
                } else if (totalBytesDownloaded < 1024 * 1024) {
                    // If less than 1MB, display in KB
                    progressSize = totalBytesDownloaded / 1024.0;
                    sizeUnit = "kB";
                } else {
                    // Otherwise, display in MB
                    progressSize = totalBytesDownloaded / (1024.0 * 1024.0);
                    sizeUnit = "MB";
                }

                String progressText = String.format(Locale.getDefault(), "%.2f %s", progressSize, sizeUnit);

                // Update progress size TextView
                progressMBTextView.setText(progressText);

                // Update progress bar
                progressBar.setProgress(values[0]);
            }





            @Override
            protected void onPostExecute(Boolean result) {
                dialog.dismiss();
                if (result) {
                    installUpdate();
                } else {
                    Toast.makeText(MainActivity.this, "Update download failed", Toast.LENGTH_SHORT).show();
                }
            }
        }.execute(updateLink);
    }
    private void installUpdate() {
        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "update.apk");
        Uri apkUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".fileprovider", file);

        PackageInstaller.Session session = null;
        try {
            PackageInstaller packageInstaller = getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            int sessionId = packageInstaller.createSession(params);
            session = packageInstaller.openSession(sessionId);
            OutputStream out = session.openWrite("update", 0, -1);
            InputStream in = getContentResolver().openInputStream(apkUri);
            byte[] buffer = new byte[65536];
            int c;
            while ((c = in.read(buffer)) != -1) {
                out.write(buffer, 0, c);
            }
            session.fsync(out);
            in.close();
            out.close();

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("InstallUpdate", "Error installing update: " + e.getMessage());
            Toast.makeText(MainActivity.this, "Error installing update", Toast.LENGTH_SHORT).show();
        } finally {
            if (session != null) {
                session.close();
            }
        }
    }

}
