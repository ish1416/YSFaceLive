package com.ttv.livedemo;


import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.ttv.face.FaceEngine;
import com.ttv.face.FaceResult;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import dmax.dialog.SpotsDialog;
import io.fotoapparat.Fotoapparat;
import io.fotoapparat.FotoapparatSwitcher;
import io.fotoapparat.parameter.LensPosition;
import io.fotoapparat.parameter.Size;
import io.fotoapparat.parameter.selector.SelectorFunction;
import io.fotoapparat.parameter.selector.SizeSelectors;
import io.fotoapparat.result.PendingResult;
import io.fotoapparat.view.CameraView;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.os.Build.VERSION.SDK_INT;
import static io.fotoapparat.parameter.selector.LensPositionSelectors.lensPosition;

public class CameraActivity extends AppCompatActivity {

    private final PermissionsDelegate permissionsDelegate = new PermissionsDelegate(this);
    private boolean hasPermission;
    private CameraView cameraView;
    private FaceRectView rectanglesView;

    private FotoapparatSwitcher fotoapparatSwitcher;
    private Fotoapparat frontFotoapparat;
    private Fotoapparat backFotoapparat;
    private FaceRectTransformer faceRectTransformer;

    ImageView back;
    TextView resultView, partial, progressText, progressDetails;
    ProgressBar circularProgress;
    LinearLayout progressOverlay;
    boolean mSwitchCamera = false;
    boolean isNavigating = false;
    
    // Temporal Consistency Engine
    private TemporalConsistencyEngine temporalEngine;
    private Handler uiHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        resultView=findViewById(R.id.resultView);
        partial=findViewById(R.id.partial);
        progressText=findViewById(R.id.progressText);
        progressDetails=findViewById(R.id.progressDetails);
        circularProgress=findViewById(R.id.circularProgress);
        progressOverlay=findViewById(R.id.progressOverlay);
        cameraView = (CameraView) findViewById(R.id.camera_view);
        rectanglesView = (FaceRectView) findViewById(R.id.rectanglesView);
        
        // Initialize temporal consistency engine
        temporalEngine = new TemporalConsistencyEngine();
        uiHandler = new Handler(Looper.getMainLooper());
        
        // Check camera permission first
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, 100);
            return;
        }
        
        initializeCamera();
    }
    
    private void initializeCamera() {
        try {
            // Show camera view immediately
            cameraView.setVisibility(View.VISIBLE);
            
            hasPermission = true; // We already checked permissions

            // Try to initialize FaceEngine, but don't fail if it doesn't work
            try {
                FaceEngine.createInstance(this);
                FaceEngine.getInstance().init();
            } catch (Exception e) {
                Log.e("CameraActivity", "FaceEngine init failed, continuing without it", e);
            }

            frontFotoapparat = createFotoapparat(LensPosition.FRONT);
            backFotoapparat = createFotoapparat(LensPosition.BACK);
            fotoapparatSwitcher = FotoapparatSwitcher.withDefault(frontFotoapparat);

            View switchCameraButton = findViewById(R.id.switchCamera);
            switchCameraButton.setVisibility(
                    canSwitchCameras()
                            ? View.VISIBLE
                            : View.GONE
            );
            switchCameraButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switchCamera();
                }
            });

            resultView.setText("Position your face in the frame");
        resultView.setTextColor(Color.WHITE);
            partial.setText("Keep your face steady and look directly at the camera");
        partial.setTextColor(Color.WHITE);
            
            // Start camera
            if (fotoapparatSwitcher != null) {
                fotoapparatSwitcher.start();
            }
        } catch (Exception e) {
            Log.e("CameraActivity", "Camera initialization failed", e);
            // Show error message
            resultView.setText("Camera initialization failed");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeCamera();
            } else {
                finish(); // Close activity if camera permission denied
            }
        }
    }

    private boolean canSwitchCameras() {
        return frontFotoapparat.isAvailable() == backFotoapparat.isAvailable();
    }

    private Fotoapparat createFotoapparat(LensPosition position) {
        return Fotoapparat
                .with(this)
                .into(cameraView)
                .lensPosition(lensPosition(position))
                .frameProcessor(
                        LivenessDetectorProcesser.with(this)
                                .listener(new LivenessDetectorProcesser.OnFacesDetectedListener() {
                                    @Override
                                    public void onFacesDetected(List<FaceResult> faces, Size frameSize) {

                                        LensPosition lensPosition;
                                        if (fotoapparatSwitcher.getCurrentFotoapparat() == frontFotoapparat) {
                                            lensPosition = LensPosition.FRONT;
                                        } else {
                                            lensPosition = LensPosition.BACK;
                                        }

                                        if(faceRectTransformer == null || mSwitchCamera == true)
                                        {
                                            mSwitchCamera =false;
                                            int displayOrientation = 0;
                                            ViewGroup.LayoutParams layoutParams = adjustPreviewViewSize(cameraView,
                                                    cameraView, rectanglesView,
                                                    new Size(frameSize.width, frameSize.height), displayOrientation, 1.0f);

                                            faceRectTransformer = new FaceRectTransformer(
                                                    frameSize.height, frameSize.width,
                                                    cameraView.getLayoutParams().width, cameraView.getLayoutParams().height,
                                                    displayOrientation, lensPosition, false,
                                                    false,
                                                    false);
                                        }

                                        List<FaceRectView.DrawInfo> drawInfoList = new ArrayList<>();
                                        
                                        // Handle no faces detected
                                        if (faces.size() == 0) {
                                            // Reset temporal engine when no face detected
                                            temporalEngine.reset();
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    hideProgressOverlay();
                                                    resultView.setText("Position your face in the frame");
                                                    resultView.setTextColor(Color.WHITE);
                                                }
                                            });
                                            rectanglesView.clearFaceInfo();
                                            return;
                                        }
                                        
                                        // Handle multiple faces
                                        if (faces.size() > 1) {
                                            temporalEngine.reset();
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    hideProgressOverlay();
                                                    resultView.setText("⚠ Multiple faces detected - show only one face");
                                                    resultView.setTextColor(Color.YELLOW);
                                                }
                                            });
                                            // Still draw rectangles for all faces but mark as invalid
                                            for(int i = 0; i < faces.size(); i++) {
                                                Rect rect = faceRectTransformer.adjustRect(new Rect(faces.get(i).left, faces.get(i).top, faces.get(i).right, faces.get(i).bottom));
                                                FaceRectView.DrawInfo drawInfo = new FaceRectView.DrawInfo(rect, 0, 0, 0, Color.YELLOW, "MULTIPLE", faces.get(i).livenessScore, -1);
                                                drawInfoList.add(drawInfo);
                                            }
                                            rectanglesView.clearFaceInfo();
                                            rectanglesView.addFaceInfo(drawInfoList);
                                            return;
                                        }
                                        
                                        // Process single face with temporal consistency
                                        for(int i = 0; i < faces.size(); i++) {
                                            Rect rect = faceRectTransformer.adjustRect(new Rect(faces.get(i).left, faces.get(i).top, faces.get(i).right, faces.get(i).bottom));
                                            float score = faces.get(i).livenessScore;
                                            
                                            // Add frame to temporal consistency engine
                                            TemporalConsistencyEngine.VerificationResult result = temporalEngine.addFrame(
                                                score, 
                                                new Rect(faces.get(i).left, faces.get(i).top, faces.get(i).right, faces.get(i).bottom),
                                                1.0f - faces.get(i).blur // Quality score (inverse of blur)
                                            );
                                            
                                            // Update UI based on temporal analysis
                                            updateUIWithTemporalResult(result, rect, score, drawInfoList);
                                        }

                                        rectanglesView.clearFaceInfo();
                                        rectanglesView.addFaceInfo(drawInfoList);
                                    }
                                })
                                .build()
                )
                .previewSize(new SelectorFunction<Size>() {
                    @Override
                    public Size select(Collection<Size> collection) {
                        return new Size(1280, 720);
                    }
                })
                .build();
    }

    private void switchCamera() {
        if (fotoapparatSwitcher.getCurrentFotoapparat() == frontFotoapparat) {
            fotoapparatSwitcher.switchTo(backFotoapparat);
        } else {
            fotoapparatSwitcher.switchTo(frontFotoapparat);
        }

        mSwitchCamera = true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Camera is started in initializeCamera()
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Camera is handled in initializeCamera()
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (hasPermission) {
            try {
                fotoapparatSwitcher.stop();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private ViewGroup.LayoutParams adjustPreviewViewSize(View rgbPreview, View previewView, FaceRectView faceRectView, Size previewSize, int displayOrientation, float scale) {
        ViewGroup.LayoutParams layoutParams = previewView.getLayoutParams();
        int measuredWidth = previewView.getMeasuredWidth();
        int measuredHeight = previewView.getMeasuredHeight();

        layoutParams.width = measuredWidth;
        layoutParams.height = measuredHeight;
        previewView.setLayoutParams(layoutParams);
        faceRectView.setLayoutParams(layoutParams);
        return layoutParams;
    }
    
    /**
     * Update UI based on temporal consistency analysis results
     */
    private void updateUIWithTemporalResult(TemporalConsistencyEngine.VerificationResult result, 
                                          Rect rect, float score, List<FaceRectView.DrawInfo> drawInfoList) {
        
        FaceRectView.DrawInfo drawInfo;
        
        switch (result.status) {
            case ANALYZING:
                drawInfo = new FaceRectView.DrawInfo(rect, 0, 0, -1, Color.BLUE, "ANALYZING", score, -1);
                runOnUiThread(() -> {
                    showProgressOverlay();
                    updateProgress(temporalEngine.getProgress(), result.message, "Please hold steady");
                    resultView.setText("Analyzing liveness...");
                    resultView.setTextColor(Color.BLUE);
                });
                break;
                
            case VERIFYING:
                drawInfo = new FaceRectView.DrawInfo(rect, 0, 0, 1, Color.YELLOW, "VERIFYING", score, -1);
                runOnUiThread(() -> {
                    showProgressOverlay();
                    updateProgress(temporalEngine.getProgress(), result.message, 
                        String.format("Confidence: %.0f%% | Consistency: %.0f%%", 
                            result.averageScore * 100, result.consistencyScore * 100));
                    resultView.setText("⚠ Verification in progress...");
                    resultView.setTextColor(Color.YELLOW);
                });
                break;
                
            case SUCCESS:
                drawInfo = new FaceRectView.DrawInfo(rect, 0, 0, 1, Color.GREEN, "VERIFIED", score, -1);
                runOnUiThread(() -> {
                    hideProgressOverlay();
                    resultView.setText("✓ Live Person Verified!");
                    resultView.setTextColor(Color.GREEN);
                    navigateToResults(true, result.averageScore, result.verificationDuration);
                });
                break;
                
            case FAILED_INCONSISTENT:
                drawInfo = new FaceRectView.DrawInfo(rect, 0, 0, 0, Color.RED, "INCONSISTENT", score, -1);
                runOnUiThread(() -> {
                    hideProgressOverlay();
                    resultView.setText("✗ Inconsistent detection - Please try again");
                    resultView.setTextColor(Color.RED);
                    scheduleFailureNavigation("Inconsistent liveness detection");
                });
                break;
                
            case FAILED_ANOMALY:
                drawInfo = new FaceRectView.DrawInfo(rect, 0, 0, 0, Color.RED, "SPOOF DETECTED", score, -1);
                runOnUiThread(() -> {
                    hideProgressOverlay();
                    resultView.setText("✗ Spoof attempt detected!");
                    resultView.setTextColor(Color.RED);
                    scheduleFailureNavigation("Spoofing attempt detected");
                });
                break;
                
            case FAILED_INSUFFICIENT:
                drawInfo = new FaceRectView.DrawInfo(rect, 0, 0, 0, Color.rgb(255, 165, 0), "POOR QUALITY", score, -1);
                runOnUiThread(() -> {
                    hideProgressOverlay();
                    resultView.setText("⚠ Unable to verify - Improve lighting");
                    resultView.setTextColor(Color.rgb(255, 165, 0));
                    scheduleFailureNavigation("Insufficient image quality");
                });
                break;
                
            case FAILED_TIMEOUT:
                drawInfo = new FaceRectView.DrawInfo(rect, 0, 0, 0, Color.RED, "TIMEOUT", score, -1);
                runOnUiThread(() -> {
                    hideProgressOverlay();
                    resultView.setText("✗ Verification timeout");
                    resultView.setTextColor(Color.RED);
                    scheduleFailureNavigation("Verification timeout");
                });
                break;
                
            default:
                drawInfo = new FaceRectView.DrawInfo(rect, 0, 0, 0, Color.GRAY, "UNKNOWN", score, -1);
                break;
        }
        
        drawInfoList.add(drawInfo);
    }
    
    /**
     * Show progress overlay with verification status
     */
    private void showProgressOverlay() {
        if (progressOverlay != null) {
            progressOverlay.setVisibility(View.VISIBLE);
        }
    }
    
    /**
     * Hide progress overlay
     */
    private void hideProgressOverlay() {
        if (progressOverlay != null) {
            progressOverlay.setVisibility(View.GONE);
        }
    }
    
    /**
     * Update progress display
     */
    private void updateProgress(int progress, String message, String details) {
        if (circularProgress != null) {
            circularProgress.setProgress(progress);
        }
        if (progressText != null) {
            progressText.setText(message);
        }
        if (progressDetails != null) {
            progressDetails.setText(details);
        }
    }
    
    /**
     * Navigate to results screen with verification data
     */
    private void navigateToResults(boolean isLive, float averageScore, long duration) {
        if (!isNavigating) {
            isNavigating = true;
            new Handler().postDelayed(() -> {
                Intent intent = new Intent(CameraActivity.this, ResultActivity.class);
                intent.putExtra("isLive", isLive);
                intent.putExtra("score", averageScore);
                intent.putExtra("duration", duration);
                startActivity(intent);
                finish();
            }, 2000); // 2 second delay to show success message
        }
    }
    
    /**
     * Schedule navigation to failure result after delay
     */
    private void scheduleFailureNavigation(String reason) {
        if (!isNavigating) {
            isNavigating = true;
            new Handler().postDelayed(() -> {
                Intent intent = new Intent(CameraActivity.this, ResultActivity.class);
                intent.putExtra("isLive", false);
                intent.putExtra("score", 0.0f);
                intent.putExtra("reason", reason);
                startActivity(intent);
                finish();
            }, 3000); // 3 second delay to show failure message
        }
    }
}