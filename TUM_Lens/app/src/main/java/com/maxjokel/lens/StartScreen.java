package com.maxjokel.lens;

import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.content.Intent;
import android.os.Bundle;
import android.Manifest;
import android.content.pm.PackageManager;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

/* + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

    08.07.2020, 15:00

    This is the first activity that is shown to the user. Asks for permissions (camera, etc.) and
    states reasons why they are necessary.

+ + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + */

public class StartScreen extends AppCompatActivity {

    // init global permission related variables
    private int ALL_PERMISSIONS = 101;
    private final String[] permissions = new String[]{Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE};


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // switch back to default theme [source: https://android.jlelse.eu/the-complete-android-splash-screen-guide-c7db82bce565]
        setTheme(R.style.AppTheme);

        super.onCreate(savedInstanceState);

        // prevent display from being dimmed down
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // set status bar background to black
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.black));

        // set corresponding layout
        setContentView(R.layout.activity_start_screen);


        // check if permissions are already granted
        // if so, then launch directly into the 'ViewFinder' activity
        checkForPermissions();

        // no permission, show the corresponding view...

        // UI elements of view
        Button btn_requestPermission = findViewById(R.id.button);

        // set on-click-listener for button to request permission
        btn_requestPermission.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // check permissions status:
                if (
                        (ContextCompat.checkSelfPermission(StartScreen.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                        &&
                        (ContextCompat.checkSelfPermission(StartScreen.this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
                ) {

                    // permissions granted -> open 'ViewFinder' activity
                    Intent intent = new Intent(StartScreen.this, ViewFinder.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                    startActivity(intent);

                } else {

                    // request permissions as they haven't been granted yet
                    ActivityCompat.requestPermissions(StartScreen.this, permissions, ALL_PERMISSIONS);

                }

            }
        });

    }


    // this function checks if the necessary permissions are already granted
    // if so, it then launches the view-finder activity
    private void checkForPermissions() {

        // check permissions status:
        if (
                (ContextCompat.checkSelfPermission(StartScreen.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                &&
                (ContextCompat.checkSelfPermission(StartScreen.this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
        ) {

            // permissions granted -> open 'ViewFinder' activity
            Intent intent = new Intent(StartScreen.this, ViewFinder.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            startActivity(intent);

            finish();

        }

    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        if (requestCode == ALL_PERMISSIONS)  {

            // now verify, if both permissions were granted:

            boolean isGrantedForAll = false;

            if ( (grantResults.length > 0) && (permissions.length == grantResults.length) ) {

                for (int i = 0; i < permissions.length; i++){

                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED){
                        isGrantedForAll = true;
                    } else {
                        isGrantedForAll = false;
                    }

                }

            }

            if(isGrantedForAll) {

                // permissions granted -> open 'ViewFinder' activity
                Intent intent = new Intent(StartScreen.this, ViewFinder.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                startActivity(intent);

                finish();

            } else { // permissions denied

                // switch to view that explains again and offers a redirect to settings
                Intent intent = new Intent(StartScreen.this, PermissionDenied.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                startActivity(intent);

                finish();

            }
        }
    }
}