package com.example.test5;

import android.hardware.input.InputManager;
import android.os.Build;
import android.util.Log;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

public class EventInput {

    Method injectInputEventMethod;
    InputManager im;

    ArrayList<ArrayList<Integer>> clicks = new ArrayList<>();

    public EventInput() throws Exception {
        //Get the instance of InputManager class using reflection
        String methodName = "getInstance";
        Object[] objArr = new Object[0];
        im = (InputManager) InputManager.class.getDeclaredMethod(methodName, new Class[0])
                .invoke(null, objArr);

        //Make MotionEvent.obtain() method accessible
        methodName = "obtain";
        MotionEvent.class.getDeclaredMethod(methodName, new Class[0]).setAccessible(true);

        //Get the reference to injectInputEvent method
        methodName = "injectInputEvent";
        injectInputEventMethod = InputManager.class.getMethod(
                methodName, new Class[]{InputEvent.class, Integer.TYPE});
    }

    public void injectMotionEvent(int inputSource, int action, long when, float x, float y,
                                  float pressure) throws InvocationTargetException, IllegalAccessException {
        if (android.os.Build.VERSION.SDK_INT >= 24) {

            if (action == 0) {
                clicks.clear();
                ArrayList<Integer> cl = new ArrayList<>();
                cl.add((int) x);
                cl.add((int) y);
                clicks.add(cl);
            }
            else if (action == 2) {
                ArrayList<Integer> cl = new ArrayList<>();
                cl.add((int) x);
                cl.add((int) y);
                clicks.add(cl);
            }
            else {
                AutoClickService.instance.click(clicks);
            }
        } else {
            MotionEvent event = MotionEvent.obtain(when, when, action, x, y, pressure, 1.0f, 0, 1.0f, 1.0f, 0, 0);
            event.setSource(inputSource);
            injectInputEventMethod.invoke(im, new Object[]{event, Integer.valueOf(0)});
        }
    }

    private void injectKeyEvent(KeyEvent event)
            throws InvocationTargetException, IllegalAccessException {
        injectInputEventMethod.invoke(im, new Object[]{event, Integer.valueOf(0)});
    }
}
