package com.example.test5.async.future;

import com.example.test5.async.AsyncSemaphore;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class SimpleFuture<T> extends SimpleCancellable implements DependentFuture<T> {
    AsyncSemaphore waiter;
    Exception exception;
    T result;
    boolean silent;
    FutureCallback<T> callback;

    public SimpleFuture() {
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return cancel();
    }

    private boolean cancelInternal(boolean silent) {
        if (!super.cancel())
            return false;
        FutureCallback<T> callback;
        synchronized (this) {
            exception = new CancellationException();
            releaseWaiterLocked();
            callback = handleCompleteLocked();
            this.silent = silent;
        }
        handleCallbackUnlocked(callback);
        return true;
    }

    @Override
    public boolean cancel() {
        return cancelInternal(silent);
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        AsyncSemaphore waiter;
        synchronized (this) {
            if (isCancelled() || isDone())
                return getResultOrThrow();
            waiter = ensureWaiterLocked();
        }
        waiter.acquire();
        return getResultOrThrow();
    }

    private T getResultOrThrow() throws ExecutionException {
        if (exception != null)
            throw new ExecutionException(exception);
        return result;
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        AsyncSemaphore waiter;
        synchronized (this) {
            if (isCancelled() || isDone())
                return getResultOrThrow();
            waiter = ensureWaiterLocked();
        }
        if (!waiter.tryAcquire(timeout, unit))
            throw new TimeoutException();
        return getResultOrThrow();
    }

    @Override
    public boolean setComplete() {
        return setComplete((T)null);
    }

    private FutureCallback<T> handleCompleteLocked() {
        FutureCallback<T> callback = this.callback;
        this.callback = null;
        return callback;
    }

    private void handleCallbackUnlocked(FutureCallback<T> callback) {
        if (callback != null && !silent)
            callback.onCompleted(exception, result);
    }

    void releaseWaiterLocked() {
        if (waiter != null) {
            waiter.release();
            waiter = null;
        }
    }

    AsyncSemaphore ensureWaiterLocked() {
        if (waiter == null)
            waiter = new AsyncSemaphore();
        return waiter;
    }

    public boolean setComplete(Exception e) {
        return setComplete(e, null);
    }

    public boolean setComplete(T value) {
        return setComplete(null, value);
    }

    public boolean setComplete(Exception e, T value) {
        FutureCallback<T> callback;
        synchronized (this) {
            if (!super.setComplete())
                return false;
            result = value;
            exception = e;
            releaseWaiterLocked();
            callback = handleCompleteLocked();
        }
        handleCallbackUnlocked(callback);
        return true;
    }

    public FutureCallback<T> getCompletionCallback() {
        return new FutureCallback<T>() {
            @Override
            public void onCompleted(Exception e, T result) {
                setComplete(e, result);
            }
        };
    }

    public SimpleFuture<T> setComplete(Future<T> future) {
        future.setCallback(getCompletionCallback());
        setParent(future);
        return this;
    }


    @Override
    public SimpleFuture<T> setCallback(FutureCallback<T> callback) {
        synchronized (this) {
            this.callback = callback;
            if (isDone() || isCancelled())
                callback = handleCompleteLocked();
            else
                callback = null;
        }
        handleCallbackUnlocked(callback);
        return this;
    }

    @Override
    public final <C extends FutureCallback<T>> C then(C callback) {
        if (callback instanceof DependentCancellable)
            ((DependentCancellable)callback).setParent(this);
        setCallback(callback);
        return callback;
    }

    @Override
    public SimpleFuture<T> setParent(Cancellable parent) {
        super.setParent(parent);
        return this;
    }
    public SimpleFuture<T> reset() {
        super.reset();

        result = null;
        exception = null;
        waiter = null;
        callback = null;
        silent = false;

        return this;
    }

    @Override
    public Exception tryGetException() {
        return exception;
    }

    @Override
    public T tryGet() {
        return result;
    }
}
