package com.example.test5.async.http.server;

import com.example.test5.async.DataEmitter;
import com.example.test5.async.DataSink;
import com.example.test5.async.Util;
import com.example.test5.async.callback.CompletedCallback;
import com.example.test5.async.callback.DataCallback;
import com.example.test5.async.http.AsyncHttpRequest;
import com.example.test5.async.http.body.AsyncHttpRequestBody;

public class UnknownRequestBody implements AsyncHttpRequestBody<Void> {
    public UnknownRequestBody(String contentType) {
        mContentType = contentType;
    }

    int length = -1;

    @Override
    public void write(final AsyncHttpRequest request, DataSink sink, final CompletedCallback completed) {
        Util.pump(emitter, sink, completed);
        if (emitter.isPaused())
            emitter.resume();
    }

    private String mContentType;
    @Override
    public String getContentType() {
        return mContentType;
    }

    @Override
    public boolean readFullyOnRequest() {
        return false;
    }

    @Override
    public int length() {
        return length;
    }

    @Override
    public Void get() {
        return null;
    }

    @Deprecated
    public void setCallbacks(DataCallback callback, CompletedCallback endCallback) {
        emitter.setEndCallback(endCallback);
        emitter.setDataCallback(callback);
    }

    public DataEmitter getEmitter() {
        return emitter;
    }

    DataEmitter emitter;
    @Override
    public void parse(DataEmitter emitter, CompletedCallback completed) {
        this.emitter = emitter;
        emitter.setEndCallback(completed);
        emitter.setDataCallback(new DataCallback.NullDataCallback());
    }
}
