package com.example.test5.async.http;

import com.example.test5.async.AsyncServer;
import com.example.test5.async.DataEmitter;
import com.example.test5.async.FilteredDataEmitter;
import com.example.test5.async.callback.CompletedCallback;
import com.example.test5.async.http.body.AsyncHttpRequestBody;

public class HttpUtil {
    public static AsyncHttpRequestBody getBody(DataEmitter emitter, CompletedCallback reporter, Headers headers) {
        String contentType = headers.get("Content-Type");
        if (contentType != null) {
            String[] values = contentType.split(";");
            for (int i = 0; i < values.length; i++) {
                values[i] = values[i].trim();
            }
        }

        return null;
    }

    static class EndEmitter extends FilteredDataEmitter {
        private EndEmitter() {
        }
        
        public static EndEmitter create(AsyncServer server, final Exception e) {
            final EndEmitter ret = new EndEmitter();
            server.post(new Runnable() {
                @Override
                public void run() {
                    ret.report(e);
                }
            });
            return ret;
        }
    }

    public static DataEmitter getBodyDecoder(DataEmitter emitter, Protocol protocol, Headers headers, boolean server) {
        long _contentLength;
        try {
            _contentLength = Long.parseLong(headers.get("Content-Length"));
        }
        catch (Exception ex) {
            _contentLength = -1;
        }
        final long contentLength = _contentLength;
        if (-1 != contentLength) {
            if (contentLength < 0) {
                EndEmitter ender = EndEmitter.create(emitter.getServer(), new Exception("not using chunked encoding, and no content-length found."));
                ender.setDataEmitter(emitter);
                emitter = ender;
                return emitter;
            }
            if (contentLength == 0) {
                EndEmitter ender = EndEmitter.create(emitter.getServer(), null);
                ender.setDataEmitter(emitter);
                emitter = ender;
                return emitter;
            }
        }
        else {
            if ((server || protocol == Protocol.HTTP_1_1) && !"close".equalsIgnoreCase(headers.get("Connection"))) {
                // if this is the server, and the client has not indicated a request body, the client is done
                EndEmitter ender = EndEmitter.create(emitter.getServer(), null);
                ender.setDataEmitter(emitter);
                emitter = ender;
                return emitter;
            }
        }
        return emitter;
    }

    public static boolean isKeepAlive(Protocol protocol, Headers headers) {
        String connection = headers.get("Connection");
        if (connection == null)
            return protocol == Protocol.HTTP_1_1;
        return "keep-alive".equalsIgnoreCase(connection);
    }

    public static boolean isKeepAlive(String protocol, Headers headers) {
        String connection = headers.get("Connection");
        if (connection == null)
            return Protocol.get(protocol) == Protocol.HTTP_1_1;
        return "keep-alive".equalsIgnoreCase(connection);
    }

    public static int contentLength(Headers headers) {
        String cl = headers.get("Content-Length");
        if (cl == null)
            return -1;
        try {
            return Integer.parseInt(cl);
        }
        catch (NumberFormatException e) {
            return -1;
        }
    }
}
