package com.maxjokel.lens;

import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.Intent;
import android.os.Bundle;
import android.Manifest;
import android.content.pm.PackageManager;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;


/* + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + +

    08.07.2020, 15:00

    This is the first activity that is shown to the user. Asks for permissions (camera, etc.) and
    states reasons why they are necessary.

+ + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + + */

public class StartScreen extends AppCompatActivity {

    private int PERMISSION_CODE_CAMERA = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // prevent display from being dimmed down
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // set corresponding layout
        setContentView(R.layout.activity_start_screen);


        // check if permissions are already granted
        // if so, then launch directly into the view-finder activity
        checkForPermissions();

        // no permission, show the corresponding view...

        // UI elements of view
        Button btn_requestPermission = findViewById(R.id.button);

        // set on-click-listener for button to request permission
        btn_requestPermission.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // check status of permission:

                if (ContextCompat.checkSelfPermission(StartScreen.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {

                    // should not get called!!


                    // permission granted
                    Toast toast = Toast.makeText(StartScreen.this, "You have already granted this permission!", Toast.LENGTH_SHORT);
                    toast.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 32);
                    toast.show();

                    // open "viewfinder" view
                    Intent intent = new Intent(StartScreen.this, ViewFinder.class);
                    startActivity(intent);


                } else {

                    // should get called!

                    // request permission as it hasn't been granted yet
                    ActivityCompat.requestPermissions(StartScreen.this, new String[] {Manifest.permission.CAMERA}, PERMISSION_CODE_CAMERA);


                }

            }
        });

    }


    // this function checks if the necessary permissions are already granted
    // if so, it then launches the view-finder activity
    private void checkForPermissions() {

        if (ContextCompat.checkSelfPermission(StartScreen.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {

            // permission to use CAMERA is given, start right into VIEWFINDER and do NOT push the StartScreen to HISTORY

            Intent intent = new Intent(StartScreen.this, ViewFinder.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            startActivity(intent);

            finish();

        }

    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_CODE_CAMERA)  {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                // display Toast message
//                Toast toast = Toast.makeText(getApplicationContext(), "Permission GRANTED", Toast.LENGTH_SHORT);
//                toast.setGravity(Gravity.TOP| Gravity.CENTER_HORIZONTAL, 0, 32);
//                toast.show();

                // change view to "ViewFinder"
                Intent intent = new Intent(StartScreen.this, ViewFinder.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                startActivity(intent);

                finish();

            } else {

                // Permission is denied!

                // display Toast message
//                Toast toast = Toast.makeText(getApplicationContext(), "Permission DENIED", Toast.LENGTH_SHORT);
//                toast.setGravity(Gravity.TOP| Gravity.CENTER_HORIZONTAL, 0, 32);
//                toast.show();

                // switch to view that explains again and offers a redirect to settings
                Intent intent = new Intent(StartScreen.this, PermissionDenied.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                startActivity(intent);

                finish();

            }
        }
    }
}