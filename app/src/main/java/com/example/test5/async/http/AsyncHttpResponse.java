package com.example.test5.async.http;

import com.example.test5.async.AsyncSocket;
import com.example.test5.async.DataEmitter;

public interface AsyncHttpResponse extends DataEmitter {
    public String protocol();
    public String message();
    public int code();
    public Headers headers();
    public AsyncSocket detachSocket();
    public AsyncHttpRequest getRequest();
}
