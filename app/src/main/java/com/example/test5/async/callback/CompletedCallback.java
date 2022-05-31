package com.example.test5.async.callback;

public interface CompletedCallback {
    public class NullCompletedCallback implements CompletedCallback {
        @Override
        public void onCompleted(Exception ex) {

        }
    }

    public void onCompleted(Exception ex);
}
