package com.example.test5.async.future;


public interface Future<T> extends Cancellable, java.util.concurrent.Future<T> {
    public Future<T> setCallback(FutureCallback<T> callback);
    public <C extends FutureCallback<T>> C then(C callback);
    public T tryGet();
    public Exception tryGetException();
}
