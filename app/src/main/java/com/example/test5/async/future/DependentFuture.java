package com.example.test5.async.future;

public interface DependentFuture<T> extends Future<T>, DependentCancellable {
}
