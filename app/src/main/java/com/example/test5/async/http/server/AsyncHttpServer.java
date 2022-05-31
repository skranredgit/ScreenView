package com.example.test5.async.http.server;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.AssetManager;
import android.os.Build;
import android.text.TextUtils;

import com.example.test5.async.AsyncServer;
import com.example.test5.async.AsyncServerSocket;
import com.example.test5.async.AsyncSocket;
import com.example.test5.async.ByteBufferList;
import com.example.test5.async.DataEmitter;
import com.example.test5.async.Util;
import com.example.test5.async.callback.CompletedCallback;
import com.example.test5.async.callback.ListenCallback;
import com.example.test5.async.http.AsyncHttpClient;
import com.example.test5.async.http.AsyncHttpHead;
import com.example.test5.async.http.Headers;
import com.example.test5.async.http.HttpUtil;
import com.example.test5.async.http.Protocol;
import com.example.test5.async.http.WebSocket;
import com.example.test5.async.http.WebSocketImpl;
import com.example.test5.async.http.body.AsyncHttpRequestBody;
import com.example.test5.async.util.StreamUtility;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@TargetApi(Build.VERSION_CODES.ECLAIR)
public class AsyncHttpServer {
    ArrayList<AsyncServerSocket> mListeners = new ArrayList<AsyncServerSocket>();
    public void stop() {
        if (mListeners != null) {
            for (AsyncServerSocket listener: mListeners) {
                listener.stop();
            }
        }
    }

    protected void onRequest(HttpServerRequestCallback callback, AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
        if (callback != null)
            callback.onRequest(request, response);
    }

    protected AsyncHttpRequestBody onUnknownBody(Headers headers) {
        return new UnknownRequestBody(headers.get("Content-Type"));
    }

    ListenCallback mListenCallback = new ListenCallback() {
        @Override
        public void onAccepted(final AsyncSocket socket) {
            AsyncHttpServerRequestImpl req = new AsyncHttpServerRequestImpl() {
                HttpServerRequestCallback match;
                String fullPath;
                String path;
                boolean responseComplete;
                boolean requestComplete;
                AsyncHttpServerResponseImpl res;
                boolean hasContinued;

                @Override
                protected AsyncHttpRequestBody onUnknownBody(Headers headers) {
                    return AsyncHttpServer.this.onUnknownBody(headers);
                }

                @Override
                protected void onHeadersReceived() {
                    Headers headers = getHeaders();
                    if (!hasContinued && "100-continue".equals(headers.get("Expect"))) {
                        pause();
                        Util.writeAll(mSocket, "HTTP/1.1 100 Continue\r\n\r\n".getBytes(), new CompletedCallback() {
                            @Override
                            public void onCompleted(Exception ex) {
                                resume();
                                if (ex != null) {
                                    report(ex);
                                    return;
                                }
                                hasContinued = true;
                                onHeadersReceived();
                            }
                        });
                        return;
                    }
                    String statusLine = getStatusLine();
                    String[] parts = statusLine.split(" ");
                    fullPath = parts[1];
                    path = fullPath.split("\\?")[0];
                    method = parts[0];
                    synchronized (mActions) {
                        ArrayList<Pair> pairs = mActions.get(method);
                        if (pairs != null) {
                            for (Pair p: pairs) {
                                Matcher m = p.regex.matcher(path);
                                if (m.matches()) {
                                    mMatcher = m;
                                    match = p.callback;
                                    break;
                                }
                            }
                        }
                    }
                    res = new AsyncHttpServerResponseImpl(socket, this) {
                        @Override
                        protected void report(Exception e) {
                            super.report(e);
                            if (e != null) {
                                socket.setDataCallback(new NullDataCallback());
                                socket.setEndCallback(new NullCompletedCallback());
                                socket.close();
                            }
                        }

                        @Override
                        protected void onEnd() {
                            super.onEnd();
                            mSocket.setEndCallback(null);
                            responseComplete = true;
                            handleOnCompleted();
                        }
                    };

                    boolean handled = false;

                    if (match == null && !handled) {
                        res.code(404);
                        res.end();
                        return;
                    }

                    if (!getBody().readFullyOnRequest()) {
                        onRequest(match, this, res);
                    }
                    else if (requestComplete) {
                        onRequest(match, this, res);
                    }
                }

                @Override
                public void onCompleted(Exception e) {
                    if (res.code() == 101)
                        return;
                    requestComplete = true;
                    super.onCompleted(e);
                    mSocket.setDataCallback(new NullDataCallback() {
                        @Override
                        public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                            super.onDataAvailable(emitter, bb);
                            mSocket.close();
                        }
                    });
                    handleOnCompleted();

                    if (getBody().readFullyOnRequest()) {
                        onRequest(match, this, res);
                    }
                }
                
                private void handleOnCompleted() {
                    if (requestComplete && responseComplete) {
                        if (HttpUtil.isKeepAlive(Protocol.HTTP_1_1, getHeaders())) {
                            onAccepted(socket);
                        }
                        else {
                            socket.close();
                        }
                    }
                }

                @Override
                public String getPath() {
                    return path;
                }

            };
            req.setSocket(socket);
            socket.resume();
        }

        @Override
        public void onCompleted(Exception error) {
            report(error);
        }

        @Override
        public void onListening(AsyncServerSocket socket) {
            mListeners.add(socket);
        }
    };

    public AsyncServerSocket listen(AsyncServer server, int port) {
        return server.listen(null, port, mListenCallback);
    }

    private void report(Exception ex) {
        if (mCompletedCallback != null)
            mCompletedCallback.onCompleted(ex);
    }
    
    public AsyncServerSocket listen(int port) {
        return listen(AsyncServer.getDefault(), port);
    }

    CompletedCallback mCompletedCallback;

    private static class Pair {
        Pattern regex;
        HttpServerRequestCallback callback;
    }
    
    final Hashtable<String, ArrayList<Pair>> mActions = new Hashtable<String, ArrayList<Pair>>();

    public void removeAction(String action, String regex) {
        synchronized (mActions) {
            ArrayList<Pair> pairs = mActions.get(action);
            if (pairs == null)
                return;
            for (int i = 0; i < pairs.size(); i++) {
                Pair p = pairs.get(i);
                if (regex.equals(p.regex.toString())) {
                    pairs.remove(i);
                    return;
                }
            }
        }
    }
    
    public void addAction(String action, String regex, HttpServerRequestCallback callback) {
        Pair p = new Pair();
        p.regex = Pattern.compile("^" + regex);
        p.callback = callback;
        
        synchronized (mActions) {
            ArrayList<Pair> pairs = mActions.get(action);
            if (pairs == null) {
                pairs = new ArrayList<Pair>();
                mActions.put(action, pairs);
            }
            pairs.add(p);
        }
    }

    public static interface WebSocketRequestCallback {
        public void onConnected(WebSocket webSocket, AsyncHttpServerRequest request);
    }


    public void websocket(String regex, final String protocol, final WebSocketRequestCallback callback) {
        get(regex, new HttpServerRequestCallback() {
            @Override
            public void onRequest(final AsyncHttpServerRequest request, final AsyncHttpServerResponse response) {
                boolean hasUpgrade = false;
                String connection = request.getHeaders().get("Connection");
                if (connection != null) {
                    String[] connections = connection.split(",");
                    for (String c: connections) {
                        if ("Upgrade".equalsIgnoreCase(c.trim())) {
                            hasUpgrade = true;
                            break;
                        }
                    }
                }
                if (!"websocket".equalsIgnoreCase(request.getHeaders().get("Upgrade")) || !hasUpgrade) {
                    response.code(404);
                    response.end();
                    return;
                }
                String peerProtocol = request.getHeaders().get("Sec-WebSocket-Protocol");
                if (!TextUtils.equals(protocol, peerProtocol)) {
                    response.code(404);
                    response.end();
                    return;
                }
                callback.onConnected(new WebSocketImpl(request, response), request);
            }
        });
    }
    
    public void get(String regex, HttpServerRequestCallback callback) {
        addAction(AsyncHttpClient.AsyncHttpGet.METHOD, regex, callback);
    }


    public static android.util.Pair<Integer, InputStream> getAssetStream(final Context context, String asset) {
        AssetManager am = context.getAssets();
        try {
            InputStream is = am.open(asset);
            return new android.util.Pair<Integer, InputStream>(is.available(), is);
        }
        catch (IOException e) {
            return null;
        }
    }

    static Hashtable<String, String> mContentTypes = new Hashtable<String, String>();
    {
        mContentTypes.put("js", "application/javascript");
        mContentTypes.put("json", "application/json");
        mContentTypes.put("png", "image/png");
        mContentTypes.put("jpg", "image/jpeg");
        mContentTypes.put("html", "text/html");
        mContentTypes.put("css", "text/css");
        mContentTypes.put("mp4", "video/mp4");
        mContentTypes.put("mov", "video/quicktime");
        mContentTypes.put("wmv", "video/x-ms-wmv");
    }

    public static String getContentType(String path) {
        String type = tryGetContentType(path);
        if (type != null)
            return type;
        return "text/plain";
    }

    public static String tryGetContentType(String path) {
        int index = path.lastIndexOf(".");
        if (index != -1) {
            String e = path.substring(index + 1);
            String ct = mContentTypes.get(e);
            if (ct != null)
                return ct;
        }
        return null;
    }

    public void directory(Context context, String regex, final String assetPath) {
        final Context _context = context.getApplicationContext();
        addAction(AsyncHttpClient.AsyncHttpGet.METHOD, regex, new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, final AsyncHttpServerResponse response) {
                String path = request.getMatcher().replaceAll("");
                android.util.Pair<Integer, InputStream> pair = getAssetStream(_context, assetPath + path);
                if (pair == null || pair.second == null) {
                    response.code(404);
                    response.end();
                    return;
                }
                final InputStream is = pair.second;
                response.getHeaders().set("Content-Length", String.valueOf(pair.first));
                response.code(200);
                response.getHeaders().add("Content-Type", getContentType(assetPath + path));
                Util.pump(is, response, new CompletedCallback() {
                    @Override
                    public void onCompleted(Exception ex) {
                        response.end();
                        StreamUtility.closeQuietly(is);
                    }
                });
            }
        });
        addAction(AsyncHttpHead.METHOD, regex, new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, final AsyncHttpServerResponse response) {
                String path = request.getMatcher().replaceAll("");
                android.util.Pair<Integer, InputStream> pair = getAssetStream(_context, assetPath + path);
                if (pair == null || pair.second == null) {
                    response.code(404);
                    response.end();
                    return;
                }
                final InputStream is = pair.second;
                StreamUtility.closeQuietly(is);
                response.getHeaders().set("Content-Length", String.valueOf(pair.first));
                response.code(200);
                response.getHeaders().add("Content-Type", getContentType(assetPath + path));
                response.writeHead();
                response.end();
            }
        });
    }

    private static Hashtable<Integer, String> mCodes = new Hashtable<Integer, String>();
    static {
        mCodes.put(200, "OK");
        mCodes.put(206, "Partial Content");
        mCodes.put(101, "Switching Protocols");
        mCodes.put(301, "Moved Permanently");
        mCodes.put(302, "Found");
        mCodes.put(404, "Not Found");
    }
    
    public static String getResponseCodeDescription(int code) {
        String d = mCodes.get(code);
        if (d == null)
            return "Unknown";
        return d;
    }

    public interface HttpServerRequestCallback {
        public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response);
    }

}
