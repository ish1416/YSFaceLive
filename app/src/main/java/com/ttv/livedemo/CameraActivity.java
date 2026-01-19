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
import android.widget.Toast;

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
    TextView resultView,partial;
    boolean mSwitchCamera = false;
    boolean isNavigating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        resultView=findViewById(R.id.resultView);
        partial=findViewById(R.id.partial);
        cameraView = (CameraView) findViewById(R.id.camera_view);
        rectanglesView = (FaceRectView) findViewById(R.id.rectanglesView);
        
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
                                        final boolean[] hasLiveFaceArray = {false};
                                        final int[] faceCount = {0};
                                        
                                        // Handle no faces detected
                                        if (faces.size() == 0) {
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    resultView.setText("Position your face in the frame");
                                                    resultView.setTextColor(Color.WHITE);
                                                }
                                            });
                                            rectanglesView.clearFaceInfo();
                                            return;
                                        }
                                        
                                        // Handle multiple faces
                                        if (faces.size() > 1) {
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
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
                                        
                                        // Process single face
                                        for(int i = 0; i < faces.size(); i ++) {
                                            faceCount[0]++;
                                            Rect rect = faceRectTransformer.adjustRect(new Rect(faces.get(i).left, faces.get(i).top, faces.get(i).right, faces.get(i).bottom));

                                            FaceRectView.DrawInfo drawInfo;
                                            float score = faces.get(i).livenessScore;
                                            
                                        // Enhanced liveness detection logic with stricter security
                                        if(score > 0.85) { // Much higher threshold for live person
                                            drawInfo = new FaceRectView.DrawInfo(rect, 0, 0, 1, Color.GREEN, "LIVE", score, -1);
                                            hasLiveFaceArray[0] = true;
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    resultView.setText("✓ Live Person Detected - High Confidence");
                                                    resultView.setTextColor(Color.GREEN);
                                                }
                                            });
                                        } else if(score > 0.75 && score <= 0.85) { // High confidence but need verification
                                            drawInfo = new FaceRectView.DrawInfo(rect, 0, 0, -1, Color.YELLOW, "VERIFYING", score, -1);
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    resultView.setText("⚠ Please blink or move your head slightly");
                                                    resultView.setTextColor(Color.YELLOW);
                                                }
                                            });
                                        } else if(score > 0.5 && score <= 0.75) { // Medium - likely photo/screen
                                            drawInfo = new FaceRectView.DrawInfo(rect, 0, 0, 0, Color.rgb(255, 165, 0), "PHOTO DETECTED", score, -1);
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    resultView.setText("⚠ Static image detected - Use live camera");
                                                    resultView.setTextColor(Color.rgb(255, 165, 0));
                                                }
                                            });
                                        } else { // Low confidence - definitely fake
                                            drawInfo = new FaceRectView.DrawInfo(rect, 0, 0, 0, Color.RED, "FAKE", score, -1);
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    resultView.setText("✗ Verification Failed - Spoof attempt detected");
                                                    resultView.setTextColor(Color.RED);
                                                }
                                            });
                                        }
                                            drawInfo.setMaskInfo(faces.get(i).mask);
                                            drawInfoList.add(drawInfo);
                                        }

                                        rectanglesView.clearFaceInfo();
                                        rectanglesView.addFaceInfo(drawInfoList);
                                        
                                        // Only auto-navigate if we have a very high confidence live face (score > 0.85)
                                        if (hasLiveFaceArray[0] && !isNavigating) {
                                            isNavigating = true;
                                            new android.os.Handler().postDelayed(new Runnable() {
                                                @Override
                                                public void run() {
                                                    Intent intent = new Intent(CameraActivity.this, ResultActivity.class);
                                                    intent.putExtra("isLive", hasLiveFaceArray[0]);
                                                    intent.putExtra("score", faces.size() > 0 ? faces.get(0).livenessScore : 0.0f);
                                                    startActivity(intent);
                                                    finish();
                                                }
                                            }, 3000); // Reduced to 3 seconds for better UX
                                        }
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
}