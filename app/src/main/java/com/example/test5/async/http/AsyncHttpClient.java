package com.example.test5.async.http;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import com.example.test5.async.AsyncSSLException;
import com.example.test5.async.AsyncServer;
import com.example.test5.async.AsyncSocket;
import com.example.test5.async.DataEmitter;
import com.example.test5.async.callback.CompletedCallback;
import com.example.test5.async.callback.ConnectCallback;
import com.example.test5.async.callback.DataCallback;
import com.example.test5.async.future.Cancellable;
import com.example.test5.async.future.Future;
import com.example.test5.async.future.SimpleFuture;

import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

public class AsyncHttpClient {
    private static AsyncHttpClient mDefaultInstance;
    public static AsyncHttpClient getDefaultInstance() {
        if (mDefaultInstance == null)
            mDefaultInstance = new AsyncHttpClient(AsyncServer.getDefault());

        return mDefaultInstance;
    }

    final ArrayList<AsyncHttpClientMiddleware> mMiddleware = new ArrayList<AsyncHttpClientMiddleware>();
    public ArrayList<AsyncHttpClientMiddleware> getMiddleware() {
        return mMiddleware;
    }
    public void insertMiddleware(AsyncHttpClientMiddleware middleware) {
        mMiddleware.add(0, middleware);
    }

    AsyncSocketMiddleware socketMiddleware;
    HttpTransportMiddleware httpTransportMiddleware;
    AsyncServer mServer;
    public AsyncHttpClient(AsyncServer server) {
        mServer = server;
        insertMiddleware(socketMiddleware = new AsyncSocketMiddleware(this));
        insertMiddleware(httpTransportMiddleware = new HttpTransportMiddleware());
    }

    @SuppressLint("NewApi")
    private static void setupAndroidProxy(AsyncHttpRequest request) {
        // using a explicit proxy?
        if (request.proxyHost != null)
            return;

        List<Proxy> proxies;
        try {
            proxies = ProxySelector.getDefault().select(URI.create(request.getUri().toString()));
        }
        catch (Exception e) {
            return;
        }
        if (proxies.isEmpty())
            return;
        Proxy proxy = proxies.get(0);
        if (proxy.type() != Proxy.Type.HTTP)
            return;
        if (!(proxy.address() instanceof InetSocketAddress))
            return;
        InetSocketAddress proxyAddress = (InetSocketAddress) proxy.address();
        String proxyHost;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            proxyHost = proxyAddress.getHostString();
        }
        else {
            InetAddress address = proxyAddress.getAddress();
            if (address!=null)
                proxyHost = address.getHostAddress();
            else
                proxyHost = proxyAddress.getHostName();
        }
        request.enableProxy(proxyHost, proxyAddress.getPort());
    }


    public Future<AsyncHttpResponse> execute(final AsyncHttpRequest request, final HttpConnectCallback callback) {
        FutureAsyncHttpResponse ret;
        execute(request, 0, ret = new FutureAsyncHttpResponse(), callback);
        return ret;
    }

    private class FutureAsyncHttpResponse extends SimpleFuture<AsyncHttpResponse> {
        public AsyncSocket socket;
        public Object scheduled;
        public Runnable timeoutRunnable;

        @Override
        public boolean cancel() {
            if (!super.cancel())
                return false;

            if (socket != null) {
                socket.setDataCallback(new DataCallback.NullDataCallback());
                socket.close();
            }

            if (scheduled != null)
                mServer.removeAllCallbacks(scheduled);

            return true;
        }
    }

    private void reportConnectedCompleted(FutureAsyncHttpResponse cancel, Exception ex, AsyncHttpResponseImpl response, AsyncHttpRequest request, final HttpConnectCallback callback) {
        assert callback != null;
        mServer.removeAllCallbacks(cancel.scheduled);
        boolean complete;
        if (ex != null) {
            request.loge("Connection error", ex);
            complete = cancel.setComplete(ex);
        }
        else {
            request.logd("Connection successful");
            complete = cancel.setComplete(response);
        }
        if (complete) {
            callback.onConnectCompleted(ex, response);
            assert ex != null || response.socket() == null || response.getDataCallback() != null || response.isPaused();
            return;
        }

        if (response != null) {
            // the request was cancelled, so close up shop, and eat any pending data
            response.setDataCallback(new DataCallback.NullDataCallback());
            response.close();
        }
    }

    private void execute(final AsyncHttpRequest request, final int redirectCount, final FutureAsyncHttpResponse cancel, final HttpConnectCallback callback) {
        if (mServer.isAffinityThread()) {
            executeAffinity(request, redirectCount, cancel, callback);
        }
        else {
            mServer.post(new Runnable() {
                @Override
                public void run() {
                    executeAffinity(request, redirectCount, cancel, callback);
                }
            });
        }
    }

    private static long getTimeoutRemaining(AsyncHttpRequest request) {
        return request.getTimeout();
    }

    private static void copyHeader(AsyncHttpRequest from, AsyncHttpRequest to, String header) {
        String value = from.getHeaders().get(header);
        if (!TextUtils.isEmpty(value))
            to.getHeaders().set(header, value);
    }

    private void executeAffinity(final AsyncHttpRequest request, final int redirectCount, final FutureAsyncHttpResponse cancel, final HttpConnectCallback callback) {
        assert mServer.isAffinityThread();
        if (redirectCount > 15) {
            reportConnectedCompleted(cancel, new Exception("too many redirects"), null, request, callback);
            return;
        }
        final Uri uri = request.getUri();
        final AsyncHttpClientMiddleware.OnResponseCompleteDataOnRequestSentData data = new AsyncHttpClientMiddleware.OnResponseCompleteDataOnRequestSentData();
        request.executionTime = System.currentTimeMillis();
        data.request = request;

        request.logd("Executing request.");

        synchronized (mMiddleware) {
            for (AsyncHttpClientMiddleware middleware: mMiddleware) {
                middleware.onRequest(data);
            }
        }


        if (request.getTimeout() > 0) {
            // set connect timeout
            cancel.timeoutRunnable = new Runnable() {
                @Override
                public void run() {
                    // we've timed out, kill the connections
                    if (data.socketCancellable != null) {
                        data.socketCancellable.cancel();
                        if (data.socket != null)
                            data.socket.close();
                    }
                    reportConnectedCompleted(cancel, new TimeoutException(), null, request, callback);
                }
            };
            cancel.scheduled = mServer.postDelayed(cancel.timeoutRunnable, getTimeoutRemaining(request));
        }

        // 2) wait for a connect
        data.connectCallback = new ConnectCallback() {
            boolean reported;
            @Override
            public void onConnectCompleted(Exception ex, AsyncSocket socket) {
                if (reported) {
                    if (socket != null) {
                        socket.setDataCallback(new DataCallback.NullDataCallback());
                        socket.setEndCallback(new CompletedCallback.NullCompletedCallback());
                        socket.close();
                        throw new AssertionError("double connect callback");
                    }
                }
                reported = true;

                request.logv("socket connected");
                if (cancel.isCancelled()) {
                    if (socket != null)
                        socket.close();
                    return;
                }
                if (cancel.timeoutRunnable != null)
                    mServer.removeAllCallbacks(cancel.scheduled);

                if (ex != null) {
                    reportConnectedCompleted(cancel, ex, null, request, callback);
                    return;
                }

                data.socket = socket;
                cancel.socket = socket;

                executeSocket(request, redirectCount, cancel, callback, data);
            }
        };
        setupAndroidProxy(request);
        if (request.getBody() != null) {
            if (request.getHeaders().get("Content-Type") == null)
                request.getHeaders().set("Content-Type", request.getBody().getContentType());
        }

        final Exception unsupportedURI;
        synchronized (mMiddleware) {
            for (AsyncHttpClientMiddleware middleware: mMiddleware) {
                Cancellable socketCancellable = middleware.getSocket(data);
                if (socketCancellable != null) {
                    data.socketCancellable = socketCancellable;
                    cancel.setParent(socketCancellable);
                    return;
                }
            }
            unsupportedURI = new IllegalArgumentException("invalid uri="+request.getUri()+" middlewares="+mMiddleware);
        }
        reportConnectedCompleted(cancel, unsupportedURI, null, request, callback);
    }

    private void executeSocket(final AsyncHttpRequest request, final int redirectCount,
                               final FutureAsyncHttpResponse cancel, final HttpConnectCallback callback,
                               final AsyncHttpClientMiddleware.OnResponseCompleteDataOnRequestSentData data) {
        final AsyncHttpResponseImpl ret = new AsyncHttpResponseImpl(request) {
            @Override
            protected void onRequestCompleted(Exception ex) {
                if (ex != null) {
                    reportConnectedCompleted(cancel, ex, null, request, callback);
                    return;
                }

                request.logv("request completed");
                if (cancel.isCancelled())
                    return;
                // 5) after request is sent, set a header timeout
                if (cancel.timeoutRunnable != null && mHeaders == null) {
                    mServer.removeAllCallbacks(cancel.scheduled);
                    cancel.scheduled = mServer.postDelayed(cancel.timeoutRunnable, getTimeoutRemaining(request));
                }

                synchronized (mMiddleware) {
                    for (AsyncHttpClientMiddleware middleware: mMiddleware) {
                        middleware.onRequestSent(data);
                    }
                }
            }

            @Override
            public void setDataEmitter(DataEmitter emitter) {
                data.bodyEmitter = emitter;
                synchronized (mMiddleware) {
                    for (AsyncHttpClientMiddleware middleware: mMiddleware) {
                        middleware.onBodyDecoder(data);
                    }
                }

                super.setDataEmitter(data.bodyEmitter);

                Headers headers = mHeaders;
                int responseCode = code();
                if ((responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == HttpURLConnection.HTTP_MOVED_TEMP || responseCode == 307) && request.getFollowRedirect()) {
                    String location = headers.get("Location");
                    Uri redirect;
                    try {
                        redirect = Uri.parse(location);
                        if (redirect.getScheme() == null) {
                            redirect = Uri.parse(new URL(new URL(request.getUri().toString()), location).toString());
                        }
                    }
                    catch (Exception e) {
                        reportConnectedCompleted(cancel, e, this, request, callback);
                        return;
                    }
                    final String method = request.getMethod().equals(AsyncHttpHead.METHOD) ? AsyncHttpHead.METHOD : AsyncHttpGet.METHOD;
                    AsyncHttpRequest newReq = new AsyncHttpRequest(redirect, method);
                    newReq.executionTime = request.executionTime;
                    newReq.logLevel = request.logLevel;
                    newReq.LOGTAG = request.LOGTAG;
                    newReq.proxyHost = request.proxyHost;
                    newReq.proxyPort = request.proxyPort;
                    setupAndroidProxy(newReq);
                    copyHeader(request, newReq, "User-Agent");
                    copyHeader(request, newReq, "Range");
                    request.logi("Redirecting");
                    newReq.logi("Redirected");
                    execute(newReq, redirectCount + 1, cancel, callback);

                    setDataCallback(new NullDataCallback());
                    return;
                }

                request.logv("Final (post cache response) headers:\n" + toString());
                reportConnectedCompleted(cancel, null, this, request, callback);
            }

            protected void onHeadersReceived() {
                super.onHeadersReceived();
                if (cancel.isCancelled())
                    return;

                // 7) on headers, cancel timeout
                if (cancel.timeoutRunnable != null)
                    mServer.removeAllCallbacks(cancel.scheduled);

                // allow the middleware to massage the headers before the body is decoded
                request.logv("Received headers:\n" + toString());

                synchronized (mMiddleware) {
                    for (AsyncHttpClientMiddleware middleware: mMiddleware) {
                        middleware.onHeadersReceived(data);
                    }
                }
            }

            @Override
            protected void report(Exception ex) {
                if (ex != null)
                    request.loge("exception during response", ex);
                if (cancel.isCancelled())
                    return;
                if (ex instanceof AsyncSSLException) {
                    request.loge("SSL Exception", ex);
                    AsyncSSLException ase = (AsyncSSLException)ex;
                    request.onHandshakeException(ase);
                    if (ase.getIgnore())
                        return;
                }
                final AsyncSocket socket = socket();
                if (socket == null)
                    return;
                super.report(ex);
                if (!socket.isOpen() || ex != null) {
                    if (headers() == null && ex != null)
                        reportConnectedCompleted(cancel, ex, null, request, callback);
                }

                data.exception = ex;
                synchronized (mMiddleware) {
                    for (AsyncHttpClientMiddleware middleware: mMiddleware) {
                        middleware.onResponseComplete(data);
                    }
                }
            }

            @Override
            public AsyncSocket detachSocket() {
                request.logd("Detaching socket");
                AsyncSocket socket = socket();
                if (socket == null)
                    return null;
                socket.setWriteableCallback(null);
                socket.setClosedCallback(null);
                socket.setEndCallback(null);
                socket.setDataCallback(null);
                setSocket(null);
                return socket;
            }
        };

        data.sendHeadersCallback = new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                if (ex != null)
                    ret.report(ex);
                else
                    ret.onHeadersSent();
            }
        };
        data.receiveHeadersCallback = new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                if (ex != null)
                    ret.report(ex);
                else
                    ret.onHeadersReceived();
            }
        };
        data.response = ret;
        ret.setSocket(data.socket);

        synchronized (mMiddleware) {
            for (AsyncHttpClientMiddleware middleware: mMiddleware) {
                if (middleware.exchangeHeaders(data))
                    break;
            }
        }
    }

    public static interface WebSocketConnectCallback {
        public void onCompleted(Exception ex, WebSocket webSocket);
    }

    public Future<WebSocket> websocket(final AsyncHttpRequest req, String protocol, final WebSocketConnectCallback callback) {
        WebSocketImpl.addWebSocketUpgradeHeaders(req, protocol);
        final SimpleFuture<WebSocket> ret = new SimpleFuture<WebSocket>();
        Cancellable connect = execute(req, new HttpConnectCallback() {
            @Override
            public void onConnectCompleted(Exception ex, AsyncHttpResponse response) {
                if (ex != null) {
                    if (ret.setComplete(ex)) {
                        if (callback != null)
                            callback.onCompleted(ex, null);
                    }
                    return;
                }
                WebSocket ws = WebSocketImpl.finishHandshake(req.getHeaders(), response);
                if (ws == null) {
                    return;
                }
                else {
                    if (!ret.setComplete(ws))
                        return;
                }
                if (callback != null)
                    callback.onCompleted(ex, ws);
            }
        });

        ret.setParent(connect);
        return ret;
    }

    public Future<WebSocket> websocket(String uri, String protocol, final WebSocketConnectCallback callback) {
//        assert callback != null;
        final AsyncHttpGet get = new AsyncHttpGet(uri.replace("ws://", "http://").replace("wss://", "https://"));
        return websocket(get, protocol, callback);
    }

    public AsyncServer getServer() {
        return mServer;
    }
    public interface HttpConnectCallback {
        public void onConnectCompleted(Exception ex, AsyncHttpResponse response);
    }

    public class AsyncHttpGet extends AsyncHttpRequest {
        public static final String METHOD = "GET";

        public AsyncHttpGet(String uri) {
            super(Uri.parse(uri), METHOD);
        }
    }

}
