package com.maxjokel.lens;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;

/* + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

    08.07.2020, 15:30

    User is shown this explanatory activity in case the necessary permissions are not granted.

+ + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + */

public class PermissionDenied extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // set corresponding layout
        setContentView(R.layout.activity_permission_denied);

        // UI elements of view
        Button btn_exit = findViewById(R.id.buttonExit);
        Button btn_settings = findViewById(R.id.buttonSettings);


        // EXIT the app
        btn_exit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // Source: https://developer.android.com/reference/android/app/Activity#finishAffinity()
                finishAffinity();

            }
        });

        // redirect user to SETTINGS
        btn_settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // Source: https://stackoverflow.com/a/32983128
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                startActivity(intent);

            }
        });
    }
}