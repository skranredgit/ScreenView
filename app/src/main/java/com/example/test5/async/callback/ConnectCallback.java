package com.example.test5.async.callback;

import com.example.test5.async.AsyncSocket;

public interface ConnectCallback {
    public void onConnectCompleted(Exception ex, AsyncSocket socket);
}
