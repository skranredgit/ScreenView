package com.example.test5.async.http;

import com.example.test5.async.AsyncSocket;
import com.example.test5.async.DataEmitter;
import com.example.test5.async.DataSink;
import com.example.test5.async.callback.CompletedCallback;
import com.example.test5.async.callback.ConnectCallback;
import com.example.test5.async.future.Cancellable;

import java.util.Hashtable;


public interface AsyncHttpClientMiddleware {
    public interface ResponseHead  {
        public AsyncSocket socket();
        public String protocol();
        public String message();
        public int code();
        public ResponseHead protocol(String protocol);
        public ResponseHead message(String message);
        public ResponseHead code(int code);
        public Headers headers();
        public ResponseHead headers(Headers headers);
        public DataSink sink();
        public ResponseHead sink(DataSink sink);
        public DataEmitter emitter();
        public ResponseHead emitter(DataEmitter emitter);
    }

    public static class OnRequestData {
        public UntypedHashtable state = new UntypedHashtable();
        public AsyncHttpRequest request;
    }

    public static class GetSocketData extends OnRequestData {
        public ConnectCallback connectCallback;
        public Cancellable socketCancellable;
        public String protocol;
    }

    public static class OnExchangeHeaderData extends GetSocketData {
        public AsyncSocket socket;
        public ResponseHead response;
        public CompletedCallback sendHeadersCallback;
        public CompletedCallback receiveHeadersCallback;
    }

    public static class OnRequestSentData extends OnExchangeHeaderData {
    }

    public static class OnHeadersReceivedDataOnRequestSentData extends OnRequestSentData {
    }

    public static class OnBodyDataOnRequestSentData extends OnHeadersReceivedDataOnRequestSentData {
        public DataEmitter bodyEmitter;
    }

    public static class OnResponseCompleteDataOnRequestSentData extends OnBodyDataOnRequestSentData {
        public Exception exception;
    }

    public void onRequest(OnRequestData data);

    public Cancellable getSocket(GetSocketData data);

    public boolean exchangeHeaders(OnExchangeHeaderData data);

    public void onRequestSent(OnRequestSentData data);

    public void onHeadersReceived(OnHeadersReceivedDataOnRequestSentData data);

    public void onBodyDecoder(OnBodyDataOnRequestSentData data);

    public void onResponseComplete(OnResponseCompleteDataOnRequestSentData data);

    public class UntypedHashtable {
        private Hashtable<String, Object> hash = new Hashtable<String, Object>();

        public void put(String key, Object value) {
            hash.put(key, value);
        }

        public void remove(String key) {
            hash.remove(key);
        }

        public <T> T get(String key, T defaultValue) {
            T ret = get(key);
            if (ret == null)
                return defaultValue;
            return ret;
        }

        public <T> T get(String key) {
            return (T)hash.get(key);
        }
    }
}
