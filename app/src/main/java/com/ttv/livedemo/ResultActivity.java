package com.ttv.livedemo;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class ResultActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        boolean isLive = getIntent().getBooleanExtra("isLive", false);
        float score = getIntent().getFloatExtra("score", 0.0f);
        
        TextView resultText = findViewById(R.id.txtResult);
        TextView scoreText = findViewById(R.id.txtScore);
        TextView statusText = findViewById(R.id.txtStatus);
        ImageView resultIcon = findViewById(R.id.imgResult);
        
        if (isLive) {
            resultText.setText(R.string.result_live);
            resultText.setTextColor(getResources().getColor(R.color.gov_green));
            scoreText.setText(String.format("Confidence: %.1f%%", score * 100));
            scoreText.setTextColor(getResources().getColor(R.color.gov_green));
            statusText.setText("Identity verification successful. You are a live person.");
            resultIcon.setImageResource(R.drawable.ic_done);
        } else {
            resultText.setText(R.string.result_not_live);
            resultText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            scoreText.setText(String.format("Confidence: %.1f%%", score * 100));
            scoreText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            statusText.setText("Verification failed. Please try again with proper lighting.");
            resultIcon.setImageResource(R.drawable.ic_stop);
        }

        Button tryAgainButton = findViewById(R.id.btnTryAgain);
        tryAgainButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ResultActivity.this, WelcomeActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
            }
        });
    }
}