package com.ttv.livedemo;

import android.graphics.Rect;
import java.util.ArrayList;
import java.util.List;

/**
 * Temporal Consistency Engine for Enhanced Liveness Detection Security
 * Prevents photo swapping attacks by analyzing frame consistency over time
 */
public class TemporalConsistencyEngine {
    
    private static final int WINDOW_SIZE = 25; // 5 seconds at 5 FPS
    private static final float HIGH_CONFIDENCE_THRESHOLD = 0.85f;
    private static final float CONSISTENCY_THRESHOLD = 0.8f;
    private static final float ANOMALY_THRESHOLD = 0.4f;
    private static final int MIN_VERIFICATION_FRAMES = 20; // 4 seconds minimum
    
    private List<FrameData> frameBuffer;
    private long verificationStartTime;
    private boolean isVerifying;
    
    public TemporalConsistencyEngine() {
        this.frameBuffer = new ArrayList<>();
        this.verificationStartTime = 0;
        this.isVerifying = false;
    }
    
    /**
     * Data structure to hold frame analysis results
     */
    public static class FrameData {
        public float livenessScore;
        public Rect faceRect;
        public long timestamp;
        public float quality;
        
        public FrameData(float livenessScore, Rect faceRect, long timestamp, float quality) {
            this.livenessScore = livenessScore;
            this.faceRect = new Rect(faceRect);
            this.timestamp = timestamp;
            this.quality = quality;
        }
    }
    
    /**
     * Verification result with detailed analysis
     */
    public static class VerificationResult {
        public enum Status {
            ANALYZING,      // Still collecting data
            VERIFYING,      // High confidence, building consistency
            SUCCESS,        // Verified as live person
            FAILED_INCONSISTENT, // Score inconsistency detected
            FAILED_ANOMALY,      // Sudden changes detected
            FAILED_INSUFFICIENT, // Not enough good frames
            FAILED_TIMEOUT       // Verification timeout
        }
        
        public Status status;
        public float averageScore;
        public float consistencyScore;
        public int goodFrames;
        public int totalFrames;
        public String message;
        public long verificationDuration;
        
        public VerificationResult(Status status, String message) {
            this.status = status;
            this.message = message;
        }
    }
    
    /**
     * Add new frame data and analyze temporal consistency
     */
    public VerificationResult addFrame(float livenessScore, Rect faceRect, float quality) {
        long currentTime = System.currentTimeMillis();
        
        // Initialize verification start time
        if (verificationStartTime == 0) {
            verificationStartTime = currentTime;
        }
        
        // Add frame to buffer
        FrameData frameData = new FrameData(livenessScore, faceRect, currentTime, quality);
        frameBuffer.add(frameData);
        
        // Maintain rolling window
        if (frameBuffer.size() > WINDOW_SIZE) {
            frameBuffer.remove(0);
        }
        
        // Analyze current state
        return analyzeConsistency(currentTime);
    }
    
    /**
     * Analyze temporal consistency and make verification decision
     */
    private VerificationResult analyzeConsistency(long currentTime) {
        if (frameBuffer.isEmpty()) {
            return new VerificationResult(VerificationResult.Status.ANALYZING, "Initializing...");
        }
        
        // Calculate verification duration
        long duration = currentTime - verificationStartTime;
        
        // Check for timeout (30 seconds max)
        if (duration > 30000) {
            reset();
            return new VerificationResult(VerificationResult.Status.FAILED_TIMEOUT, "Verification timeout");
        }
        
        // Calculate statistics
        float averageScore = calculateAverageScore();
        float consistencyScore = calculateConsistencyScore();
        int goodFrames = countGoodFrames();
        boolean hasAnomaly = detectAnomalies();
        
        // Create result
        VerificationResult result = new VerificationResult(VerificationResult.Status.ANALYZING, "Analyzing...");
        result.averageScore = averageScore;
        result.consistencyScore = consistencyScore;
        result.goodFrames = goodFrames;
        result.totalFrames = frameBuffer.size();
        result.verificationDuration = duration;
        
        // Check for anomalies (immediate failure)
        if (hasAnomaly) {
            result.status = VerificationResult.Status.FAILED_ANOMALY;
            result.message = "Suspicious activity detected";
            return result;
        }
        
        // Determine verification status
        if (averageScore >= HIGH_CONFIDENCE_THRESHOLD && consistencyScore >= CONSISTENCY_THRESHOLD) {
            if (goodFrames >= MIN_VERIFICATION_FRAMES && duration >= 5000) {
                // Success: High confidence + consistency + sufficient duration
                result.status = VerificationResult.Status.SUCCESS;
                result.message = "Live person verified";
                isVerifying = false;
            } else if (duration >= 3000) {
                // Verifying: Building confidence
                result.status = VerificationResult.Status.VERIFYING;
                result.message = "Verification in progress...";
                isVerifying = true;
            } else {
                // Still analyzing
                result.status = VerificationResult.Status.ANALYZING;
                result.message = "Please hold steady...";
            }
        } else if (consistencyScore < 0.6f && frameBuffer.size() >= 10) {
            // Failed: Poor consistency
            result.status = VerificationResult.Status.FAILED_INCONSISTENT;
            result.message = "Inconsistent detection - please try again";
        } else if (duration >= 15000 && goodFrames < 10) {
            // Failed: Insufficient good frames after reasonable time
            result.status = VerificationResult.Status.FAILED_INSUFFICIENT;
            result.message = "Unable to verify - please improve lighting";
        } else {
            // Continue analyzing
            result.status = VerificationResult.Status.ANALYZING;
            result.message = "Analyzing liveness...";
        }
        
        return result;
    }
    
    /**
     * Calculate average liveness score
     */
    private float calculateAverageScore() {
        if (frameBuffer.isEmpty()) return 0f;
        
        float sum = 0f;
        for (FrameData frame : frameBuffer) {
            sum += frame.livenessScore;
        }
        return sum / frameBuffer.size();
    }
    
    /**
     * Calculate consistency score (lower variance = higher consistency)
     */
    private float calculateConsistencyScore() {
        if (frameBuffer.size() < 2) return 1f;
        
        float average = calculateAverageScore();
        float variance = 0f;
        
        for (FrameData frame : frameBuffer) {
            float diff = frame.livenessScore - average;
            variance += diff * diff;
        }
        variance /= frameBuffer.size();
        
        // Convert variance to consistency score (0-1, higher is better)
        float standardDeviation = (float) Math.sqrt(variance);
        return Math.max(0f, 1f - (standardDeviation * 2f));
    }
    
    /**
     * Count frames with high confidence scores
     */
    private int countGoodFrames() {
        int count = 0;
        for (FrameData frame : frameBuffer) {
            if (frame.livenessScore >= HIGH_CONFIDENCE_THRESHOLD) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Detect anomalies that indicate spoofing attempts
     */
    private boolean detectAnomalies() {
        if (frameBuffer.size() < 2) return false;
        
        for (int i = 1; i < frameBuffer.size(); i++) {
            FrameData current = frameBuffer.get(i);
            FrameData previous = frameBuffer.get(i - 1);
            
            // Check for sudden score jumps (photo swapping)
            float scoreDiff = Math.abs(current.livenessScore - previous.livenessScore);
            if (scoreDiff > ANOMALY_THRESHOLD) {
                return true;
            }
            
            // Check for face position jumps (device swapping)
            if (current.faceRect != null && previous.faceRect != null) {
                float centerXDiff = Math.abs(current.faceRect.centerX() - previous.faceRect.centerX());
                float centerYDiff = Math.abs(current.faceRect.centerY() - previous.faceRect.centerY());
                
                if (centerXDiff > 100 || centerYDiff > 100) {
                    return true;
                }
            }
            
            // Check for quality jumps (photo to real transition)
            float qualityDiff = Math.abs(current.quality - previous.quality);
            if (qualityDiff > 0.5f) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Get verification progress (0-100)
     */
    public int getProgress() {
        if (frameBuffer.isEmpty()) return 0;
        
        long duration = System.currentTimeMillis() - verificationStartTime;
        int timeProgress = Math.min(100, (int) (duration / 50)); // 5 seconds = 100%
        
        int frameProgress = Math.min(100, (frameBuffer.size() * 100) / MIN_VERIFICATION_FRAMES);
        
        return Math.max(timeProgress, frameProgress);
    }
    
    /**
     * Check if currently in verification phase
     */
    public boolean isVerifying() {
        return isVerifying;
    }
    
    /**
     * Reset the engine for new verification attempt
     */
    public void reset() {
        frameBuffer.clear();
        verificationStartTime = 0;
        isVerifying = false;
    }
}