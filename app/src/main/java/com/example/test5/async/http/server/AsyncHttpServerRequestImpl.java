package com.example.test5.async.http.server;

import com.example.test5.async.AsyncSocket;
import com.example.test5.async.DataEmitter;
import com.example.test5.async.FilteredDataEmitter;
import com.example.test5.async.LineEmitter;
import com.example.test5.async.LineEmitter.StringCallback;
import com.example.test5.async.callback.CompletedCallback;
import com.example.test5.async.callback.DataCallback;
import com.example.test5.async.http.Headers;
import com.example.test5.async.http.HttpUtil;
import com.example.test5.async.http.Protocol;
import com.example.test5.async.http.body.AsyncHttpRequestBody;

import java.util.regex.Matcher;

public abstract class AsyncHttpServerRequestImpl extends FilteredDataEmitter implements AsyncHttpServerRequest, CompletedCallback {
    private String statusLine;
    private Headers mRawHeaders = new Headers();
    AsyncSocket mSocket;
    Matcher mMatcher;

    public String getStatusLine() {
        return statusLine;
    }

    private CompletedCallback mReporter = new CompletedCallback() {
        @Override
        public void onCompleted(Exception error) {
            AsyncHttpServerRequestImpl.this.onCompleted(error);
        }
    };

    @Override
    public void onCompleted(Exception e) {
        report(e);
    }

    abstract protected void onHeadersReceived();
    
    protected void onNotHttp() {
        System.out.println("not http!");
    }

    protected AsyncHttpRequestBody onUnknownBody(Headers headers) {
        return null;
    }
    
    StringCallback mHeaderCallback = new StringCallback() {
        @Override
        public void onStringAvailable(String s) {
            try {
                if (statusLine == null) {
                    statusLine = s;
                    if (!statusLine.contains("HTTP/")) {
                        onNotHttp();
                        mSocket.setDataCallback(null);
                    }
                }
                else if (!"\r".equals(s)){
                    mRawHeaders.addLine(s);
                }
                else {
                    DataEmitter emitter = HttpUtil.getBodyDecoder(mSocket, Protocol.HTTP_1_1, mRawHeaders, true);
                    mBody = HttpUtil.getBody(emitter, mReporter, mRawHeaders);
                    if (mBody == null) {
                        mBody = onUnknownBody(mRawHeaders);
                    }
                    mBody.parse(emitter, mReporter);
                    onHeadersReceived();
                }
            }
            catch (Exception ex) {
                onCompleted(ex);
            }
        }
    };

    String method;
    @Override
    public String getMethod() {
        return method;
    }
    
    void setSocket(AsyncSocket socket) {
        mSocket = socket;

        LineEmitter liner = new LineEmitter();
        mSocket.setDataCallback(liner);
        liner.setLineCallback(mHeaderCallback);
        mSocket.setEndCallback(new NullCompletedCallback());
    }
    
    @Override
    public AsyncSocket getSocket() {
        return mSocket;
    }

    @Override
    public Headers getHeaders() {
        return mRawHeaders;
    }

    @Override
    public void setDataCallback(DataCallback callback) {
        mSocket.setDataCallback(callback);
    }

    @Override
    public DataCallback getDataCallback() {
        return mSocket.getDataCallback();
    }

    @Override
    public boolean isChunked() {
        return mSocket.isChunked();
    }

    @Override
    public Matcher getMatcher() {
        return mMatcher;
    }

    AsyncHttpRequestBody mBody;
    @Override
    public AsyncHttpRequestBody getBody() {
        return mBody;
    }

    @Override
    public void pause() {
        mSocket.pause();
    }

    @Override
    public void resume() {
        mSocket.resume();
    }

    @Override
    public boolean isPaused() {
        return mSocket.isPaused();
    }

    @Override
    public String toString() {
        if (mRawHeaders == null)
            return super.toString();
        return mRawHeaders.toPrefixString(statusLine);
    }
}
