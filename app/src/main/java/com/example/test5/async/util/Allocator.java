package com.example.test5.async.util;

import com.example.test5.async.ByteBufferList;

import java.nio.ByteBuffer;

public class Allocator {
    final int maxAlloc;
    int currentAlloc = 0;
    int minAlloc = 2 << 11;

    public Allocator(int maxAlloc) {
        this.maxAlloc = maxAlloc;
    }

    public Allocator() {
        maxAlloc = ByteBufferList.MAX_ITEM_SIZE;
    }

    public ByteBuffer allocate() {
        return allocate(currentAlloc);
    }

    public ByteBuffer allocate(int currentAlloc) {
        return ByteBufferList.obtain(Math.min(Math.max(currentAlloc, minAlloc), maxAlloc));
    }

    public void track(long read) {
        currentAlloc = (int)read * 2;
    }

    public int getMinAlloc() {
        return minAlloc;
    }

    public Allocator setMinAlloc(int minAlloc ) {
        this.minAlloc = minAlloc;
        return this;
    }
}

