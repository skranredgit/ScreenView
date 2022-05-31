package com.example.test5.async.http;

import com.example.test5.async.AsyncServer;
import com.example.test5.async.AsyncSocket;
import com.example.test5.async.ByteBufferList;
import com.example.test5.async.DataEmitter;
import com.example.test5.async.DataSink;
import com.example.test5.async.FilteredDataEmitter;
import com.example.test5.async.callback.CompletedCallback;
import com.example.test5.async.callback.WritableCallback;
import com.example.test5.async.http.body.AsyncHttpRequestBody;

abstract class AsyncHttpResponseImpl extends FilteredDataEmitter implements AsyncSocket, AsyncHttpResponse, AsyncHttpClientMiddleware.ResponseHead {
    public AsyncSocket socket() {
        return mSocket;
    }

    @Override
    public AsyncHttpRequest getRequest() {
        return mRequest;
    }

    void setSocket(AsyncSocket exchange) {
        mSocket = exchange;
        if (mSocket == null)
            return;

        mSocket.setEndCallback(mReporter);
    }

    protected void onHeadersSent() {
        AsyncHttpRequestBody requestBody = mRequest.getBody();
        if (requestBody != null) {
            requestBody.write(mRequest, AsyncHttpResponseImpl.this, new CompletedCallback() {
                @Override
                public void onCompleted(Exception ex) {
                    onRequestCompleted(ex);
                }
            });
        } else {
            onRequestCompleted(null);
        }
    }

    protected void onRequestCompleted(Exception ex) {
    }
    
    private CompletedCallback mReporter = new CompletedCallback() {
        @Override
        public void onCompleted(Exception error) {
            if (error != null && !mCompleted) {
                report(new Exception("connection closed before response completed.", error));
            }
            else {
                report(error);
            }
        }
    };

    protected void onHeadersReceived() {
    }


    @Override
    public DataEmitter emitter() {
        return getDataEmitter();
    }

    @Override
    public AsyncHttpClientMiddleware.ResponseHead emitter(DataEmitter emitter) {
        setDataEmitter(emitter);
        return this;
    }

    private void terminate() {
        mSocket.setDataCallback(new NullDataCallback() {
            @Override
            public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                super.onDataAvailable(emitter, bb);
                mSocket.close();
            }
        });
    }

    @Override
    protected void report(Exception e) {
        super.report(e);

        terminate();
        mSocket.setWriteableCallback(null);
        mSocket.setClosedCallback(null);
        mSocket.setEndCallback(null);
        mCompleted = true;
    }

    @Override
    public void close() {
        super.close();
        terminate();
    }

    private AsyncHttpRequest mRequest;
    private AsyncSocket mSocket;
    protected Headers mHeaders;
    public AsyncHttpResponseImpl(AsyncHttpRequest request) {
        mRequest = request;
    }

    boolean mCompleted = false;

    @Override
    public Headers headers() {
        return mHeaders;
    }

    @Override
    public AsyncHttpClientMiddleware.ResponseHead headers(Headers headers) {
        mHeaders = headers;
        return this;
    }

    int code;
    @Override
    public int code() {
        return code;
    }

    @Override
    public AsyncHttpClientMiddleware.ResponseHead code(int code) {
        this.code = code;
        return this;
    }

    @Override
    public AsyncHttpClientMiddleware.ResponseHead protocol(String protocol) {
        this.protocol = protocol;
        return this;
    }

    @Override
    public AsyncHttpClientMiddleware.ResponseHead message(String message) {
        this.message = message;
        return this;
    }

    String protocol;
    @Override
    public String protocol() {
        return protocol;
    }

    String message;
    @Override
    public String message() {
        return message;
    }

    @Override
    public String toString() {
        if (mHeaders == null)
            return super.toString();
        return mHeaders.toPrefixString(protocol + " " + code + " " + message);
    }

    private boolean mFirstWrite = true;
    private void assertContent() {
        if (!mFirstWrite)
            return;
        mFirstWrite = false;
        assert null != mRequest.getHeaders().get("Content-Type");
        assert mRequest.getHeaders().get("Transfer-Encoding") != null || HttpUtil.contentLength(mRequest.getHeaders()) != -1;
    }

    DataSink mSink;

    @Override
    public DataSink sink() {
        return mSink;
    }

    @Override
    public AsyncHttpClientMiddleware.ResponseHead sink(DataSink sink) {
        mSink = sink;
        return this;
    }

    @Override
    public void write(ByteBufferList bb) {
        assertContent();
        mSink.write(bb);
    }

    @Override
    public void end() {
        throw new AssertionError("end called?");
    }

    @Override
    public void setWriteableCallback(WritableCallback handler) {
        mSink.setWriteableCallback(handler);
    }

    @Override
    public WritableCallback getWriteableCallback() {
        return mSink.getWriteableCallback();
    }


    @Override
    public boolean isOpen() {
        return mSink.isOpen();
    }

    @Override
    public void setClosedCallback(CompletedCallback handler) {
        mSink.setClosedCallback(handler);
    }

    @Override
    public CompletedCallback getClosedCallback() {
        return mSink.getClosedCallback();
    }
    
    @Override
    public AsyncServer getServer() {
        return mSocket.getServer();
    }
}
