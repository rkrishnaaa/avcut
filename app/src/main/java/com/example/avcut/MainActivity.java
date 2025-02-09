package com.example.avcut;

import static com.example.avcut.FileUtils.getRealPathFromURI;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.MediaController;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.ReturnCode;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private static final int PICK_FILE_REQUEST = 1;
    private static final int STORAGE_PERMISSION_CODE = 100;
    private Uri fileUri;
    private EditText etStartTime, etEndTime;
    private Button btnSelectFile, btnPreview, btnCut;
    private VideoView videoView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnSelectFile = findViewById(R.id.btnSelectFile);
        etStartTime = findViewById(R.id.etStartTime);
        etEndTime = findViewById(R.id.etEndTime);
        etStartTime.setText("00:00:05");
        etEndTime.setText("00:00:10");
        btnPreview = findViewById(R.id.btnPreview);
        btnCut = findViewById(R.id.btnCut);

        btnSelectFile.setOnClickListener(view -> checkPermissionAndSelectFile());
        btnCut.setOnClickListener(view -> cutFile());
        videoView = findViewById(R.id.videoView);
        // Add media controls (play, pause, seek)
        MediaController mediaController = new MediaController(this);
        mediaController.setAnchorView(videoView);
        videoView.setMediaController(mediaController);

        btnPreview.setOnClickListener(view -> playSelectedVideo());


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Android 11 & 12
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 100);
        } else { // Below Android 11
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
        }

    }

    private void requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14 (API 34)
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO}, 100);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13 (API 33)
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO}, 100);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Android 11 & 12 (API 30, 31)
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 100);
        } else { // Android 10 and below
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }

    private void checkPermissionAndSelectFile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO}, STORAGE_PERMISSION_CODE);
            } else {
                selectFile();
            }
        } else {
            // For Android 13 and below
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, STORAGE_PERMISSION_CODE);
            } else {
                selectFile();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("Permissions", "Storage permission granted.");
                selectFile();
            } else {
                Log.e("Permissions", "Storage permission denied!");
                Toast.makeText(this, "App needs media access to function properly.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void selectFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"video/*", "audio/*"});
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_FILE_REQUEST);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE_REQUEST && resultCode == RESULT_OK && data != null) {
            fileUri = data.getData();
        }
    }

    private void playSelectedVideo() {
        if (fileUri != null) {
            videoView.setVideoURI(fileUri);
            videoView.start();
        } else {
            Toast.makeText(this, "No file selected", Toast.LENGTH_SHORT).show();
        }
    }

    private void cutFile() {
        if (fileUri == null) {
            Toast.makeText(this, "Please select a file", Toast.LENGTH_SHORT).show();
            return;
        }

        String inputPath = getRealPathFromURI(getApplicationContext(), fileUri);
        if (inputPath == null) {
            Toast.makeText(this, "Invalid file path", Toast.LENGTH_SHORT).show();
            return;
        }

        // Set output path to Downloads folder
        File outputDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        String fileName = new Date().getTime() + "_cut_video.mp4";
        File outputFile = new File(outputDir, fileName);
        String outputPath = outputFile.getAbsolutePath();

        String startTime = etStartTime.getText().toString();
        String endTime = etEndTime.getText().toString();

        String[] cmdArray = {"-i", inputPath,  // Input video
                "-ss", startTime, // Start time (5 seconds)
                "-to", endTime, // End time (10 seconds)
                "-c", "copy",      // Use copy mode (faster, no re-encoding)
                "-y",              // Overwrite without asking
                outputPath         // Output file
        };
        String cmd = TextUtils.join(" ", cmdArray);
        FFmpegSession session = FFmpegKit.execute(cmd);

        if (ReturnCode.isSuccess(session.getReturnCode())) {
            Toast.makeText(MainActivity.this, "Cutting Done! Saved to Downloads", Toast.LENGTH_SHORT).show();
            saveVideoToDownloadFolder(fileName, "video/mp4");
        } else if (ReturnCode.isCancel(session.getReturnCode())) {
            Toast.makeText(MainActivity.this, "Cutting Cancelled.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(MainActivity.this, "Failed: " + session.getFailStackTrace(), Toast.LENGTH_LONG).show();
            Log.d("MainActivity", String.format("Command failed with state %s and rc %s.%s", session.getState(), session.getReturnCode(), session.getFailStackTrace()));
        }
    }

    public String saveVideoToDownloadFolder(String fileName, String mimeType) {
        ContentResolver resolver = getContentResolver();
        ContentValues values = new ContentValues();

        values.put(MediaStore.Video.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Video.Media.MIME_TYPE, mimeType);
        values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES);

        Uri videoUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);

        try {
            if (videoUri != null) {
                OutputStream outputStream = resolver.openOutputStream(videoUri);
                FileInputStream inputStream = new FileInputStream(new File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), fileName));

                byte[] buffer = new byte[1024];
                int length;
                while ((length = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                }

                inputStream.close();
                outputStream.close();
                return videoUri.toString();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

}