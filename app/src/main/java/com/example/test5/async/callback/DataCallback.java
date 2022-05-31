package com.example.test5.async.callback;

import com.example.test5.async.ByteBufferList;
import com.example.test5.async.DataEmitter;

public interface DataCallback {
    public class NullDataCallback implements DataCallback {
        @Override
        public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
            bb.recycle();
        }
    }

    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb);
}
