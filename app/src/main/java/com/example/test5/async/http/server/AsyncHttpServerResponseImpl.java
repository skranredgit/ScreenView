package com.example.test5.async.http.server;

import android.text.TextUtils;

import com.example.test5.async.AsyncServer;
import com.example.test5.async.AsyncSocket;
import com.example.test5.async.ByteBufferList;
import com.example.test5.async.DataSink;
import com.example.test5.async.Util;
import com.example.test5.async.callback.CompletedCallback;
import com.example.test5.async.callback.DataCallback;
import com.example.test5.async.callback.WritableCallback;
import com.example.test5.async.http.AsyncHttpHead;
import com.example.test5.async.http.AsyncHttpResponse;
import com.example.test5.async.http.Headers;
import com.example.test5.async.http.HttpUtil;
import com.example.test5.async.http.Protocol;
import com.example.test5.async.util.StreamUtility;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

public class AsyncHttpServerResponseImpl implements AsyncHttpServerResponse {
    private Headers mRawHeaders = new Headers();
    private long mContentLength = -1;

    @Override
    public Headers getHeaders() {
        return mRawHeaders;
    }
    
    public AsyncSocket getSocket() {
        return mSocket;
    }

    AsyncSocket mSocket;
    AsyncHttpServerRequestImpl mRequest;
    AsyncHttpServerResponseImpl(AsyncSocket socket, AsyncHttpServerRequestImpl req) {
        mSocket = socket;
        mRequest = req;
        if (HttpUtil.isKeepAlive(Protocol.HTTP_1_1, req.getHeaders()))
            mRawHeaders.set("Connection", "Keep-Alive");
    }

    @Override
    public void write(ByteBufferList bb) {
        assert !mEnded;
        if (!headWritten)
            initFirstWrite();
        if (bb.remaining() == 0)
            return;
        if (mSink == null)
            return;
        mSink.write(bb);
    }

    boolean headWritten = false;
    DataSink mSink;
    void initFirstWrite() {
        if (headWritten)
            return;

        headWritten = true;

        String statusLine = String.format("HTTP/1.1 %s %s", code, AsyncHttpServer.getResponseCodeDescription(code));
        String rh = mRawHeaders.toPrefixString(statusLine);

        Util.writeAll(mSocket, rh.getBytes(), new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                if (ex != null) {
                    report(ex);
                    return;
                }
                mSink = mSocket;

                mSink.setClosedCallback(closedCallback);
                closedCallback = null;
                mSink.setWriteableCallback(writable);
                writable = null;
                if (ended) {
                    // the response ended while headers were written
                    end();
                    return;
                }
                getServer().post(new Runnable() {
                    @Override
                    public void run() {
                        WritableCallback wb = getWriteableCallback();
                        if (wb != null)
                            wb.onWriteable();
                    }
                });
            }
        });
    }

    WritableCallback writable;
    @Override
    public void setWriteableCallback(WritableCallback handler) {
        if (mSink != null)
            mSink.setWriteableCallback(handler);
        else
            writable = handler;
    }

    @Override
    public WritableCallback getWriteableCallback() {
        if (mSink != null)
            return mSink.getWriteableCallback();
        return writable;
    }

    boolean ended;
    @Override
    public void end() {
        if (ended)
            return;
        ended = true;
        if (headWritten && mSink == null) {
            return;
        }
        if (!headWritten) {
            mRawHeaders.remove("Transfer-Encoding");
        }
        if (!headWritten) {
            if (!mRequest.getMethod().equalsIgnoreCase(AsyncHttpHead.METHOD))
                send("text/html", "");
            else {
                writeHead();
                onEnd();
            }
        }
        else {
            onEnd();
        }
    }

    @Override
    public void writeHead() {
        initFirstWrite();
    }

    @Override
    public void setContentType(String contentType) {
        mRawHeaders.set("Content-Type", contentType);
    }

    @Override
    public void send(String contentType, byte[] bytes) {
        assert mContentLength < 0;
        mContentLength = bytes.length;
        mRawHeaders.set("Content-Length", Integer.toString(bytes.length));
        mRawHeaders.set("Content-Type", contentType);

        Util.writeAll(this, bytes, new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                onEnd();
            }
        });
    }

    @Override
    public void send(String contentType, final String string) {
        try {
            send(contentType, string.getBytes("UTF-8"));
        }
        catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }
    
    boolean mEnded;
    protected void onEnd() {
        mEnded = true;
    }
    
    protected void report(Exception e) {
    }


    @Override
    public void send(String string) {
        String contentType = mRawHeaders.get("Content-Type");
        if (contentType == null)
            contentType = "text/html; charset=utf-8";
        send(contentType, string);
    }

    @Override
    public void send(JSONObject json) {
        send("application/json; charset=utf-8", json.toString());
    }

    @Override
    public void sendStream(final InputStream inputStream, long totalLength) {
        long start = 0;
        long end = totalLength - 1;

        String range = mRequest.getHeaders().get("Range");
        if (range != null) {
            String[] parts = range.split("=");
            if (parts.length != 2 || !"bytes".equals(parts[0])) {
                code(416);
                end();
                return;
            }

            parts = parts[1].split("-");
            try {
                if (parts.length > 2)
                    throw new Exception();
                if (!TextUtils.isEmpty(parts[0]))
                    start = Long.parseLong(parts[0]);
                if (parts.length == 2 && !TextUtils.isEmpty(parts[1]))
                    end = Long.parseLong(parts[1]);
                else
                    end = totalLength - 1;

                code(206);
                getHeaders().set("Content-Range", String.format("bytes %d-%d/%d", start, end, totalLength));
            }
            catch (Exception e) {
                code(416);
                end();
                return;
            }
        }
        try {
            if (start != inputStream.skip(start))
                throw new Exception("skip failed to skip requested amount");
            mContentLength = end - start + 1;
            mRawHeaders.set("Content-Length", String.valueOf(mContentLength));
            mRawHeaders.set("Accept-Ranges", "bytes");
            if (mRequest.getMethod().equals(AsyncHttpHead.METHOD)) {
                writeHead();
                onEnd();
                return;
            }
            Util.pump(inputStream, mContentLength, this, new CompletedCallback() {
                @Override
                public void onCompleted(Exception ex) {
                    StreamUtility.closeQuietly(inputStream);
                    onEnd();
                }
            });
        }
        catch (Exception e) {
            code(500);
            end();
        }
    }

    @Override
    public void sendFile(File file) {
        try {
            if (mRawHeaders.get("Content-Type") == null)
                mRawHeaders.set("Content-Type", AsyncHttpServer.getContentType(file.getAbsolutePath()));
            FileInputStream fin = new FileInputStream(file);
            sendStream(new BufferedInputStream(fin, 64000), file.length());
        }
        catch (FileNotFoundException e) {
            code(404);
            end();
        }
    }

    @Override
    public void proxy(final AsyncHttpResponse remoteResponse) {
        code(remoteResponse.code());
        remoteResponse.headers().removeAll("Transfer-Encoding");
        remoteResponse.headers().removeAll("Content-Encoding");
        remoteResponse.headers().removeAll("Connection");
        getHeaders().addAll(remoteResponse.headers());
        // TODO: remove?
        remoteResponse.headers().set("Connection", "close");
        Util.pump(remoteResponse, this, new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                remoteResponse.setEndCallback(new NullCompletedCallback());
                remoteResponse.setDataCallback(new DataCallback.NullDataCallback());
                end();
            }
        });
    }

    int code = 200;
    @Override
    public AsyncHttpServerResponse code(int code) {
        this.code = code;
        return this;
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public void redirect(String location) {
        code(302);
        mRawHeaders.set("Location", location);
        end();
    }

    @Override
    public void onCompleted(Exception ex) {
        end();
    }

    @Override
    public boolean isOpen() {
        if (mSink != null)
            return mSink.isOpen();
        return mSocket.isOpen();
    }

    CompletedCallback closedCallback;
    @Override
    public void setClosedCallback(CompletedCallback handler) {
        if (mSink != null)
            mSink.setClosedCallback(handler);
        else
            closedCallback = handler;
    }

    @Override
    public CompletedCallback getClosedCallback() {
        if (mSink != null)
            return mSink.getClosedCallback();
        return closedCallback;
    }

    @Override
    public AsyncServer getServer() {
        return mSocket.getServer();
    }

    @Override
    public String toString() {
        if (mRawHeaders == null)
            return super.toString();
        String statusLine = String.format("HTTP/1.1 %s %s", code, AsyncHttpServer.getResponseCodeDescription(code));
        return mRawHeaders.toPrefixString(statusLine);
    }
}
