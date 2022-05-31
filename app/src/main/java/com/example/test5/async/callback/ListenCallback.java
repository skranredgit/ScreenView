package com.example.test5.async.callback;

import com.example.test5.async.AsyncServerSocket;
import com.example.test5.async.AsyncSocket;


public interface ListenCallback extends CompletedCallback {
    public void onAccepted(AsyncSocket socket);
    public void onListening(AsyncServerSocket socket);
}
