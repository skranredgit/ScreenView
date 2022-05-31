package com.example.test5.async.http.body;

import com.example.test5.async.DataEmitter;
import com.example.test5.async.DataSink;
import com.example.test5.async.callback.CompletedCallback;
import com.example.test5.async.http.AsyncHttpRequest;

public interface AsyncHttpRequestBody<T> {
    public void write(AsyncHttpRequest request, DataSink sink, CompletedCallback completed);
    public void parse(DataEmitter emitter, CompletedCallback completed);
    public String getContentType();
    public boolean readFullyOnRequest();
    public int length();
    public T get();
}
