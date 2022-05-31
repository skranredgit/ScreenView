package com.example.test5.async.http.server;

import com.example.test5.async.AsyncSocket;
import com.example.test5.async.DataEmitter;
import com.example.test5.async.http.Headers;
import com.example.test5.async.http.body.AsyncHttpRequestBody;

import java.util.regex.Matcher;

public interface AsyncHttpServerRequest extends DataEmitter {
    public Headers getHeaders();
    public Matcher getMatcher();
    public AsyncHttpRequestBody getBody();
    public AsyncSocket getSocket();
    public String getPath();
    public String getMethod();
}
