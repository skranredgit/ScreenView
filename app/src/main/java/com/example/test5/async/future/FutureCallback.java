package com.example.test5.async.future;

public interface FutureCallback<T> {
    public void onCompleted(Exception e, T result);
}
