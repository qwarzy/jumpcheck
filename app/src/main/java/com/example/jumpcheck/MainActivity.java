package com.example.jumpcheck;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.PoseLandmark;
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;

public class MainActivity extends AppCompatActivity {
    private PreviewView previewView;
    private DrawView drawView;
    private TextView statusText;
    private PoseDetector poseDetector;
    private int lensFacing = CameraSelector.LENS_FACING_FRONT;

    private float lastLeftAnkleY = -1f, lastRightAnkleY = -1f;
    private float lastHipY = -1f;

    private float smoothedIntensity = 0f;
    private String currentActivity = "СТОИТ";
    private long lastUpdateTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        drawView = findViewById(R.id.drawView);
        statusText = findViewById(R.id.statusText);
        Button flipBtn = findViewById(R.id.flipCameraBtn);

        poseDetector = PoseDetection.getClient(new PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE).build());

        flipBtn.setOnClickListener(v -> {
            lensFacing = (lensFacing == CameraSelector.LENS_FACING_BACK) ?
                    CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;
            startCamera();
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 101);
        } else {
            startCamera();
        }
    }

    @androidx.camera.core.ExperimentalGetImage
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> providerFuture = ProcessCameraProvider.getInstance(this);
        providerFuture.addListener(() -> {
            try {
                ProcessCameraProvider provider = providerFuture.get();
                provider.unbindAll();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();

                analysis.setAnalyzer(ContextCompat.getMainExecutor(this), imageProxy -> {
                    if (imageProxy.getImage() != null) {
                        InputImage input = InputImage.fromMediaImage(imageProxy.getImage(),
                                imageProxy.getImageInfo().getRotationDegrees());
                        poseDetector.process(input)
                                .addOnSuccessListener(pose -> {
                                    drawView.setPose(pose);
                                    analyzeMovement(pose);
                                    imageProxy.close();
                                }).addOnFailureListener(e -> imageProxy.close());
                    } else imageProxy.close();
                });

                provider.bindToLifecycle(this, new CameraSelector.Builder().requireLensFacing(lensFacing).build(),
                        preview, analysis);
            } catch (Exception e) { e.printStackTrace(); }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyzeMovement(Pose pose) {
        PoseLandmark lHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP);
        PoseLandmark rHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP);
        PoseLandmark lAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE);
        PoseLandmark rAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE);
        PoseLandmark lShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);

        if (lHip == null || rHip == null || lShoulder == null) return;

        float bodySize = Math.abs(lHip.getPosition().y - lShoulder.getPosition().y);
        float curHipY = (lHip.getPosition().y + rHip.getPosition().y) / 2;

        // Прыжок
        boolean isJumping = false;
        if (lastHipY != -1) {
            float hipMove = (lastHipY - curHipY) / bodySize;
            if (hipMove > 0.10f) isJumping = true;
        }
        lastHipY = curHipY;

        // Интенсивность ног
        float currentLegMovement = 0;
        if (lAnkle != null && rAnkle != null) {
            if (lastLeftAnkleY != -1 && lastRightAnkleY != -1) {
                float lMove = Math.abs(lAnkle.getPosition().y - lastLeftAnkleY);
                float rMove = Math.abs(rAnkle.getPosition().y - lastRightAnkleY);
                currentLegMovement = (lMove + rMove) / bodySize;
            }
            lastLeftAnkleY = lAnkle.getPosition().y;
            lastRightAnkleY = rAnkle.getPosition().y;
        }

        // Фильтрация
        smoothedIntensity = (smoothedIntensity * 0.90f) + (currentLegMovement * 0.10f);

        // Пороги активности[cite: 1]
        String detected = currentActivity;
        if (isJumping) {
            detected = "ПРЫЖОК";
        } else {
            // ИСПРАВЛЕНИЕ: Чуть повысили чувствительность бега (с 0.08 до 0.06)[cite: 1]
            if (smoothedIntensity > 0.06f) detected = "БЕГ";
            else if (smoothedIntensity > 0.025f) detected = "ХОДЬБА";
            else detected = "СТОИТ";
        }

        long now = System.currentTimeMillis();
        if (!detected.equals(currentActivity)) {
            // Задержка UI 1 секунда для плавности[cite: 1]
            if (detected.equals("ПРЫЖОК") || (now - lastUpdateTime > 1000)) {
                currentActivity = detected;
                lastUpdateTime = now;
                statusText.setText("ДЕЙСТВИЕ: " + currentActivity);
            }
        }
    }
}