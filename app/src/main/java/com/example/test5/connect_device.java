package com.example.test5;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.RingtoneManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import java.util.Timer;
import java.util.TimerTask;

public class connect_device extends AppCompatActivity {
    static TextView connection_code;
    Button close;
    static MediaProjection mMediaProjection;
    private MediaProjectionManager mMediaProjectionManager;
    Intent startServerIntent;
    public Timer timer;
    private static final int NOTIFY_ID = 101;
    private static String CHANNEL_ID = "mess channel";
    Button permission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.connect);

        Server server = Server.getServer();
        Thread t = new Thread(server);
        t.start();
        System.out.println("123" +  " " + " server");


        permission = findViewById(R.id.permission);
        connection_code = findViewById(R.id.connection_code);
        close = findViewById(R.id.close);

        this.timer = new Timer();
        this.timer.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(() -> {
                    if (ConnectionWorker.f) {
                        ConnectionWorker.f = false;
                        Log.d("__treangl__", ConnectionWorker.mess);
                        if (ConnectionWorker.mess.equals("__treangl__")) {
                            simulateKey(KeyEvent.KEYCODE_BACK);
                        }
                        else if (ConnectionWorker.mess == "__circle__") {
                            simulateKey(KeyEvent.KEYCODE_MENU);
                        }
                        else {
                            int reqCode = 1;
                            Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                            showNotification(connect_device.this, "message", ConnectionWorker.mess, intent, reqCode);
                        }
                    }
                });
            }
        }, 100, 100);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mMediaProjectionManager = (MediaProjectionManager)
                    getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        }

        startScreenCapture();

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

        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startServerIntent.setAction("STOP");
                startService(startServerIntent);
                simulateKey(KeyEvent.KEYCODE_BACK);
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

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void startScreenCapture() {
        startActivityForResult(
                mMediaProjectionManager.createScreenCaptureIntent(),
                1);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1) {
            if (resultCode != Activity.RESULT_OK) {
                Toast.makeText(this, "User cancelled the access", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, "3333333", Toast.LENGTH_SHORT).show();
            mMediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, data);
            startServerIntent = new Intent(getApplicationContext(), ServerService.class);
            startServerIntent.setAction("START");
            startService(startServerIntent);
        }
    }

    public void showNotification(Context context, String title, String message, Intent intent, int reqCode) {
        PendingIntent pendingIntent = PendingIntent.getActivity(context, reqCode, intent, PendingIntent.FLAG_ONE_SHOT);
        String CHANNEL_ID = "channel_name";// The id of the channel.
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.mess)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setContentIntent(pendingIntent);
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Channel Name";// The user-visible name of the channel.
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel mChannel = new NotificationChannel(CHANNEL_ID, name, importance);
            notificationManager.createNotificationChannel(mChannel);
        }
        notificationManager.notify(reqCode, notificationBuilder.build()); // 0 is the request code, it should be unique id

        Log.d("showNotification", "showNotification: " + reqCode);
    }

//
//    public String getWifiApIpAddress() {
//        try {
//            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en
//                    .hasMoreElements(); ) {
//                NetworkInterface intf = en.nextElement();
//                if (intf.getName().contains("wlan")) {
//                    for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr
//                            .hasMoreElements(); ) {
//                        InetAddress inetAddress = enumIpAddr.nextElement();
//                        if (!inetAddress.isLoopbackAddress()
//                                && (inetAddress.getAddress().length == 4)) {
//                            return inetAddress.getHostAddress();
//                        }
//                    }
//                }
//            }
//        } catch (SocketException ex) {
//            Log.e("TAG", ex.toString());
//        }
//        return null;
//    }
}