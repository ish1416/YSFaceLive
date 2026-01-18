package com.ttv.livedemo;

import android.app.Activity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

public class HeaderHelper {
    
    public static void setupHeader(Activity activity, String title, boolean showBackButton) {
        TextView headerTitle = activity.findViewById(R.id.headerTitle);
        ImageView btnBack = activity.findViewById(R.id.btnBack);
        
        if (headerTitle != null) {
            headerTitle.setText(title);
        }
        
        if (btnBack != null) {
            if (showBackButton) {
                btnBack.setVisibility(View.VISIBLE);
                btnBack.setOnClickListener(v -> activity.onBackPressed());
            } else {
                btnBack.setVisibility(View.GONE);
            }
        }
    }
}