package com.example.test5.async.future;

public interface Cancellable {
    boolean isDone();
    boolean isCancelled();
    boolean cancel();
}
