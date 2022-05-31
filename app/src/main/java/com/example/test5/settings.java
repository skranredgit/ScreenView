package com.example.test5;


import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

public class settings extends AppCompatActivity {
    Button save, back;
    Button permission;
    VideoView videoPlayer;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings);
        save = findViewById(R.id.save);
        back = findViewById(R.id.back);
        permission = findViewById(R.id.permission);

        permission.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!checkAccess()) {
                    onCreateDialogsValue();
                } else {
                    Toast.makeText(getApplicationContext(), "permission already issued", Toast.LENGTH_SHORT);
                }
            }
        });

        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                simulateKey(KeyEvent.KEYCODE_BACK);
            }
        });

        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v){
                Toast.makeText(getApplicationContext(), "save successfully", Toast.LENGTH_SHORT);
            }
        });
    }

    public void onCreateDialogsValue() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = (LinearLayout) getLayoutInflater().inflate(R.layout.example, null);
        builder.setTitle("")
                .setView(view)
                .setCancelable(false);

        final VideoView videoPlayer = (VideoView) view.findViewById(R.id.videoPlayer);
        Uri myVideoUri= Uri.parse( "android.resource://" + getPackageName() + "/" + R.raw.example);
        videoPlayer.setVideoURI(myVideoUri);
        videoPlayer.start();

        builder.setNegativeButton("back",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,
                                        int which) {
                    }
                });
        builder.setPositiveButton("ok",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
                        startActivityForResult(intent, 0);
                    }
                });

        builder.show();
    }

    protected boolean checkAccess() {
        String string = "com.example.test5/.KeyRecordService";
        for (AccessibilityServiceInfo id : ((AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE)).getEnabledAccessibilityServiceList(AccessibilityEvent.TYPES_ALL_MASK)) {
            if (string.equals(id.getId())) {
                return true;
            }
        }
        return false;
    }

    public static void simulateKey(final int KeyCode) {

        new Thread() {
            @Override
            public void run() {
                try {
                    Instrumentation inst = new Instrumentation();
                    inst.sendKeyDownUpSync(KeyCode);
                } catch (Exception e) {
                    Log.e("Exception", e.toString());
                }
            }

        }.start();
    }
}
