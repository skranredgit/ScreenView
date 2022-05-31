package com.example.test5.async.future;

public class SimpleCancellable implements DependentCancellable {
    boolean complete;
    @Override
    public boolean isDone() {
        return complete;
    }

    protected void cancelCleanup() {
    }

    protected void cleanup() {
    }

    protected void completeCleanup() {
    }

    public boolean setComplete() {
        synchronized (this) {
            if (cancelled)
                return false;
            if (complete) {
                assert false;
                return true;
            }
            complete = true;
            parent = null;
        }
        completeCleanup();
        cleanup();
        return true;
    }

    @Override
    public boolean cancel() {
        Cancellable parent;
        synchronized (this) {
            if (complete)
                return false;
            if (cancelled)
                return true;
            cancelled = true;
            parent = this.parent;
            this.parent = null;
        }
        if (parent != null)
            parent.cancel();
        cancelCleanup();
        cleanup();
        return true;
    }
    boolean cancelled;

    private Cancellable parent;
    @Override
    public SimpleCancellable setParent(Cancellable parent) {
        synchronized (this) {
            if (!isDone())
                this.parent = parent;
        }
        return this;
    }

    @Override
    public boolean isCancelled() {
        synchronized (this) {
            return cancelled || (parent != null && parent.isCancelled());
        }
    }


    public Cancellable reset() {
        cancel();
        complete = false;
        cancelled = false;
        return this;
    }
}
