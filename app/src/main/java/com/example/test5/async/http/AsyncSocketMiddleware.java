package com.example.test5.async.http;

import android.net.Uri;

import com.example.test5.async.AsyncSocket;
import com.example.test5.async.ByteBufferList;
import com.example.test5.async.DataEmitter;
import com.example.test5.async.callback.CompletedCallback;
import com.example.test5.async.callback.ConnectCallback;
import com.example.test5.async.callback.ContinuationCallback;
import com.example.test5.async.callback.DataCallback;
import com.example.test5.async.future.Cancellable;
import com.example.test5.async.future.Continuation;
import com.example.test5.async.future.SimpleCancellable;
import com.example.test5.async.future.TransformFuture;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.Hashtable;

public class AsyncSocketMiddleware extends SimpleMiddleware {
    String scheme;
    int port;
    int idleTimeoutMs = 300 * 1000;

    public AsyncSocketMiddleware(AsyncHttpClient client, String scheme, int port) {
        mClient = client;
        this.scheme = scheme;
        this.port = port;
    }

    public int getSchemePort(Uri uri) {
        if (uri.getScheme() == null || !uri.getScheme().equals(scheme))
            return -1;
        if (uri.getPort() == -1) {
            return port;
        }
        else {
            return uri.getPort();
        }
    }

    public AsyncSocketMiddleware(AsyncHttpClient client) {
        this(client, "http", 80);
    }

    protected AsyncHttpClient mClient;

    protected ConnectCallback wrapCallback(GetSocketData data, Uri uri, int port, boolean proxied, ConnectCallback callback) {
        return callback;
    }

    boolean connectAllAddresses;
    String proxyHost;
    int proxyPort;

    String computeLookup(Uri uri, int port, String proxyHost, int proxyPort) {
        String proxy;
        if (proxyHost != null)
            proxy = proxyHost + ":" + proxyPort;
        else
            proxy = "";

        if (proxyHost != null)
            proxy = proxyHost + ":" + proxyPort;

        return uri.getScheme() + "//" + uri.getHost() + ":" + port + "?proxy=" + proxy;
    }

    class IdleSocketHolder {
        public IdleSocketHolder(AsyncSocket socket) {
            this.socket = socket;
        }
        AsyncSocket socket;
        long idleTime = System.currentTimeMillis();
    }

    static class ConnectionInfo {
        int openCount;
        ArrayDeque<GetSocketData> queue = new ArrayDeque<GetSocketData>();
        ArrayDeque<IdleSocketHolder> sockets = new ArrayDeque<IdleSocketHolder>();
    }
    Hashtable<String, ConnectionInfo> connectionInfo = new Hashtable<String, ConnectionInfo>();

    int maxConnectionCount = Integer.MAX_VALUE;

    @Override
    public Cancellable getSocket(final GetSocketData data) {
        final Uri uri = data.request.getUri();
        final int port = getSchemePort(data.request.getUri());
        if (port == -1) {
            return null;
        }

        data.state.put("socket-owner", this);

        final String lookup = computeLookup(uri, port, data.request.getProxyHost(), data.request.getProxyPort());
        ConnectionInfo info = getOrCreateConnectionInfo(lookup);
        synchronized (AsyncSocketMiddleware.this) {
            if (info.openCount >= maxConnectionCount) {
                SimpleCancellable queueCancel = new SimpleCancellable();
                info.queue.add(data);
                return queueCancel;
            }

            info.openCount++;

            while (!info.sockets.isEmpty()) {
                IdleSocketHolder idleSocketHolder = info.sockets.pop();
                final AsyncSocket socket = idleSocketHolder.socket;
                if (idleSocketHolder.idleTime + idleTimeoutMs < System.currentTimeMillis()) {
                    socket.setClosedCallback(null);
                    socket.close();
                    continue;
                }
                if (!socket.isOpen())
                    continue;

                data.request.logd("Reusing keep-alive socket");
                data.connectCallback.onConnectCompleted(null, socket);

                // just a noop/dummy, as this can't actually be cancelled.
                SimpleCancellable ret = new SimpleCancellable();
                ret.setComplete();
                return ret;
            }
        }

        if (!connectAllAddresses || proxyHost != null || data.request.getProxyHost() != null) {
            // just default to connecting to a single address
            data.request.logd("Connecting socket");
            String unresolvedHost;
            int unresolvedPort;
            boolean proxied = false;
            if (data.request.getProxyHost() == null && proxyHost != null)
                data.request.enableProxy(proxyHost, proxyPort);
            if (data.request.getProxyHost() != null) {
                unresolvedHost = data.request.getProxyHost();
                unresolvedPort = data.request.getProxyPort();
                proxied = true;
            }
            else {
                unresolvedHost = uri.getHost();
                unresolvedPort = port;
            }
            if (proxied) {
                data.request.logv("Using proxy: " + unresolvedHost + ":" + unresolvedPort);
            }
            return mClient.getServer().connectSocket(unresolvedHost, unresolvedPort,
                wrapCallback(data, uri, port, proxied, data.connectCallback));
        }
        data.request.logv("Resolving domain and connecting to all available addresses");
        return mClient.getServer().getAllByName(uri.getHost())
        .then(new TransformFuture<AsyncSocket, InetAddress[]>() {
            Exception lastException;

            @Override
            protected void error(Exception e) {
                super.error(e);
                wrapCallback(data, uri, port, false, data.connectCallback).onConnectCompleted(e, null);
            }

            @Override
            protected void transform(final InetAddress[] result) throws Exception {
                Continuation keepTrying = new Continuation(new CompletedCallback() {
                    @Override
                    public void onCompleted(Exception ex) {
                        // if it completed, that means that the connection failed
                        if (lastException == null)
                            lastException = new Exception("Unable to connect to remote address");
                        if (setComplete(lastException)) {
                            wrapCallback(data, uri, port, false, data.connectCallback).onConnectCompleted(lastException, null);
                        }
                    }
                });

                for (final InetAddress address: result) {
                    final String inetSockAddress = String.format("%s:%s", address, port);
                    keepTrying.add(new ContinuationCallback() {
                        @Override
                        public void onContinue(Continuation continuation, final CompletedCallback next) throws Exception {
                            data.request.logv("attempting connection to " + inetSockAddress);
                            mClient.getServer().connectSocket(new InetSocketAddress(address, port),
                                wrapCallback(data, uri, port, false, new ConnectCallback() {
                                @Override
                                public void onConnectCompleted(Exception ex, AsyncSocket socket) {
                                    if (isDone()) {
                                        lastException = new Exception("internal error during connect to " + inetSockAddress);
                                        next.onCompleted(null);
                                        return;
                                    }

                                    // try the next address
                                    if (ex != null) {
                                        lastException = ex;
                                        next.onCompleted(null);
                                        return;
                                    }

                                    // if the socket is no longer needed, just hang onto it...
                                    if (isDone() || isCancelled()) {
                                        data.request.logd("Recycling extra socket leftover from cancelled operation");
                                        idleSocket(socket);
                                        recycleSocket(socket, data.request);
                                        return;
                                    }

                                    if (setComplete(null, socket)) {
                                        data.connectCallback.onConnectCompleted(null, socket);
                                    }
                                }
                            }));
                        }
                    });
                }

                keepTrying.start();
            }
        });
    }

    private ConnectionInfo getOrCreateConnectionInfo(String lookup) {
        ConnectionInfo info = connectionInfo.get(lookup);
        if (info == null) {
            info = new ConnectionInfo();
            connectionInfo.put(lookup, info);
        }
        return info;
    }

    private void maybeCleanupConnectionInfo(String lookup) {
        ConnectionInfo info = connectionInfo.get(lookup);
        if (info == null)
            return;
        while (!info.sockets.isEmpty()) {
            IdleSocketHolder idleSocketHolder = info.sockets.peekLast();
            AsyncSocket socket = idleSocketHolder.socket;
            if (idleSocketHolder.idleTime + idleTimeoutMs > System.currentTimeMillis())
                break;
            info.sockets.pop();
            socket.setClosedCallback(null);
            socket.close();
        }
        if (info.openCount == 0 && info.queue.isEmpty() && info.sockets.isEmpty())
            connectionInfo.remove(lookup);
    }

    private void recycleSocket(final AsyncSocket socket, AsyncHttpRequest request) {
        if (socket == null)
            return;
        Uri uri = request.getUri();
        int port = getSchemePort(uri);
        final String lookup = computeLookup(uri, port, request.getProxyHost(), request.getProxyPort());
        final ArrayDeque<IdleSocketHolder> sockets;
        final IdleSocketHolder idleSocketHolder = new IdleSocketHolder(socket);
        synchronized (AsyncSocketMiddleware.this) {
            ConnectionInfo info = getOrCreateConnectionInfo(lookup);
            sockets = info.sockets;
            sockets.push(idleSocketHolder);
        }
        socket.setClosedCallback(new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                synchronized (AsyncSocketMiddleware.this) {
                    sockets.remove(idleSocketHolder);
                    maybeCleanupConnectionInfo(lookup);
                }
            }
        });
    }

    private void idleSocket(final AsyncSocket socket) {
        socket.setEndCallback(new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                socket.setClosedCallback(null);
                socket.close();
            }
        });
        socket.setWriteableCallback(null);
        socket.setDataCallback(new DataCallback.NullDataCallback() {
            @Override
            public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                super.onDataAvailable(emitter, bb);
                bb.recycle();
                socket.setClosedCallback(null);
                socket.close();
            }
        });
    }

    private void nextConnection(AsyncHttpRequest request) {
        Uri uri = request.getUri();
        final int port = getSchemePort(uri);
        String key = computeLookup(uri, port, request.getProxyHost(), request.getProxyPort());
        synchronized (AsyncSocketMiddleware.this) {
            ConnectionInfo info = connectionInfo.get(key);
            if (info == null)
                return;
            --info.openCount;
            while (info.openCount < maxConnectionCount && info.queue.size() > 0) {
                GetSocketData gsd = info.queue.remove();
                SimpleCancellable socketCancellable = (SimpleCancellable)gsd.socketCancellable;
                if (socketCancellable.isCancelled())
                    continue;
                Cancellable connect = getSocket(gsd);
                socketCancellable.setParent(connect);
            }
            maybeCleanupConnectionInfo(key);
        }
    }

    @Override
    public void onResponseComplete(final OnResponseCompleteDataOnRequestSentData data) {
        if (data.state.get("socket-owner") != this)
            return;

        try {
            idleSocket(data.socket);

            if (data.exception != null || !data.socket.isOpen()) {
                data.request.logv("closing out socket (exception)");
                data.socket.setClosedCallback(null);
                data.socket.close();
                return;
            }
            if (!HttpUtil.isKeepAlive(data.response.protocol(), data.response.headers())
                || !HttpUtil.isKeepAlive(Protocol.HTTP_1_1, data.request.getHeaders())) {
                data.request.logv("closing out socket (not keep alive)");
                data.socket.setClosedCallback(null);
                data.socket.close();
                return;
            }
            data.request.logd("Recycling keep-alive socket");
            recycleSocket(data.socket, data.request);
        }
        finally {
            nextConnection(data.request);
        }
    }
}
