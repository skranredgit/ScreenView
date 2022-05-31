package com.example.test5;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Path;
import android.graphics.Point;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class AutoClickService extends AccessibilityService {

    public static AutoClickService instance;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        System.out.println("Accessibility was connected!");
        instance = this;

    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    public void click(ArrayList<ArrayList<Integer>> clicks) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                Path swipePath = new Path();
                swipePath.moveTo(clicks.get(0).get(0), clicks.get(0).get(1));

                for (int i = 1; i < clicks.size(); ++i) {
                    swipePath.lineTo(clicks.get(i).get(0), clicks.get(i).get(1));
                }
                GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                if (clicks.size() > 15) {
                    gestureBuilder.addStroke(new GestureDescription.StrokeDescription(swipePath, 0, 1000));
                }
                else if (clicks.size() > 10){
                    gestureBuilder.addStroke(new GestureDescription.StrokeDescription(swipePath, 0, 500));
                }
                else {
                    gestureBuilder.addStroke(new GestureDescription.StrokeDescription(swipePath, 0, 200));
                }
                dispatchGesture(gestureBuilder.build(), null, null);
            }
            catch(Exception ex){
            }
        }
    }
}
