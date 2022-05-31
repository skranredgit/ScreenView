package com.example.test5.async;

import com.example.test5.async.callback.CompletedCallback;
import com.example.test5.async.callback.WritableCallback;

public interface DataSink {
    public void write(ByteBufferList bb);
    public void setWriteableCallback(WritableCallback handler);
    public WritableCallback getWriteableCallback();
    
    public boolean isOpen();
    public void end();
    public void setClosedCallback(CompletedCallback handler);
    public CompletedCallback getClosedCallback();
    public AsyncServer getServer();
}
