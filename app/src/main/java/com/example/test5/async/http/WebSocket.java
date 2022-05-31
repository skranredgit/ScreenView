package com.example.test5.async.http;

import com.example.test5.async.AsyncSocket;
import com.example.test5.async.callback.CompletedCallback;
import com.example.test5.async.callback.DataCallback;


public interface WebSocket extends AsyncSocket {
    void setClosedCallback(CompletedCallback closed);

    void setDataCallback(DataCallback dataCallback);

    static public interface StringCallback {
        public void onStringAvailable(String s);
    }
    static public interface PingCallback {
        public void onPingReceived(String s);
    }
    static public interface PongCallback {
        public void onPongReceived(String s);
    }

    public void send(byte[] bytes);
    public void send(String string);
    public void send(byte [] bytes, int offset, int len);
    public void ping(String message);
    
    public void setStringCallback(StringCallback callback);
    public StringCallback getStringCallback();

    public void setPingCallback(PingCallback callback);

    public void setPongCallback(PongCallback callback);
    public PongCallback getPongCallback();

    public boolean isBuffering();
    
    public AsyncSocket getSocket();
}
