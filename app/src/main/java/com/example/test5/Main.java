package com.example.test5;

import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;

import com.example.test5.async.callback.CompletedCallback;
import com.example.test5.async.http.WebSocket;
import com.example.test5.async.http.server.AsyncHttpServer;
import com.example.test5.async.http.server.AsyncHttpServerRequest;

import org.json.JSONObject;

public class Main {

    private static EventInput input;
    static Looper looper;

    public static final String TAG = "RemoteDroid_MAIN";
    public static void main() {

        Looper.prepare();
        looper = Looper.myLooper();

        Log.d(TAG, "current process id = " + Process.myPid());
        Log.d(TAG, "current process uid = " + Process.myUid());
        try {
            input = new EventInput();
        } catch (Exception e) {
            e.printStackTrace();
        }

        AsyncHttpServer server = new AsyncHttpServer();
        server.websocket("/", null, new AsyncHttpServer.WebSocketRequestCallback() {

            @Override
            public void onConnected(WebSocket webSocket, AsyncHttpServerRequest request) {
                Log.d(TAG, "Touch client connected");
                webSocket.setClosedCallback(new CompletedCallback() {
                    @Override
                    public void onCompleted(Exception ex) {
                        if (ex != null) {
                            ex.printStackTrace();
                        }
                        Main.looper.quit();
                        Log.d(TAG, "Main WebSocket closed");
                    }
                });
                webSocket.setStringCallback(new WebSocket.StringCallback() {
                    @Override
                    public void onStringAvailable(String s) {
                        Log.d(TAG, "Received string = " + s);
                        try {
                            JSONObject touch = new JSONObject(s);
                            float x = Float.parseFloat(touch.getString("x")) * ServerService.deviceWidth;
                            float y = Float.parseFloat(touch.getString("y")) * ServerService.deviceHeight;
                            String eventType = touch.getString(ClientActivity.KEY_EVENT_TYPE);
                            if (eventType.equals(ClientActivity.KEY_FINGER_DOWN)) {
                                input.injectMotionEvent(InputDevice.SOURCE_TOUCHSCREEN, 0,
                                        SystemClock.uptimeMillis(), x, y, 1.0f);
                            } else if (eventType.equals(ClientActivity.KEY_FINGER_UP)) {
                                input.injectMotionEvent(InputDevice.SOURCE_TOUCHSCREEN, 1,
                                        SystemClock.uptimeMillis(), x, y, 1.0f);
                            } else if (eventType.equals(ClientActivity.KEY_FINGER_MOVE)) {
                                input.injectMotionEvent(InputDevice.SOURCE_TOUCHSCREEN, 2,
                                        SystemClock.uptimeMillis(), x, y, 1.0f);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        });
        server.listen(6059);
        Log.d(TAG, "Touch server listening at port 6059");

        if (input == null) {
            Log.e(TAG, "THIS SHIT IS NULL");
        } else {
            Log.e(TAG, "THIS SHIT NOT NULL");
        }

        Log.d(TAG, "Waiting for main to finish");
        Looper.loop();
        Log.d(TAG, "Returning from MAIN");
    }
}
