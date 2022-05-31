package com.example.test5.async;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Looper;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.PriorityQueue;

@TargetApi(Build.VERSION_CODES.GINGERBREAD)
public class ByteBufferList {
    ArrayDeque<ByteBuffer> mBuffers = new ArrayDeque<ByteBuffer>();
    
    ByteOrder order = ByteOrder.BIG_ENDIAN;
    public ByteOrder order() {
        return order;
    }

    public ByteBufferList order(ByteOrder order) {
        this.order = order;
        return this;
    }

    public ByteBufferList() {
    }

    public ByteBufferList(ByteBuffer... b) {
        addAll(b);
    }

    public ByteBufferList(byte[] buf) {
        super();
        ByteBuffer b = ByteBuffer.wrap(buf);
        add(b);
    }

    public ByteBufferList addAll(ByteBuffer... bb) {
        for (ByteBuffer b: bb)
            add(b);
        return this;
    }


    public byte[] getAllByteArray() {
        // fast path to return the contents of the first and only byte buffer,
        // if that's what we're looking for. avoids allocation.
        if (mBuffers.size() == 1) {
            ByteBuffer peek = mBuffers.peek();
            if (peek.capacity() == remaining() && peek.isDirect()) {
                remaining = 0;
                return mBuffers.remove().array();
            }
        }

        byte[] ret = new byte[remaining()];
        get(ret);

        return ret;
    }

    public ByteBuffer[] getAllArray() {
        ByteBuffer[] ret = new ByteBuffer[mBuffers.size()];
        ret = mBuffers.toArray(ret);
        mBuffers.clear();
        remaining = 0;
        return ret;
    }

    private int remaining = 0;
    public int remaining() {
        return remaining;
    }

    public boolean hasRemaining() {
        return remaining() > 0;
    }

    public ByteBufferList skip(int length) {
        get(null, 0, length);
        return this;
    }

    public int getInt() {
        int ret = read(4).getInt();
        remaining -= 4;
        return ret;
    }
    
    public byte get() {
        byte ret = read(1).get();
        remaining--;
        return ret;
    }
    
    public long getLong() {
        long ret = read(8).getLong();
        remaining -= 8;
        return ret;
    }

    public void get(byte[] bytes) {
        get(bytes, 0, bytes.length);
    }

    public void get(byte[] bytes, int offset, int length) {
        if (remaining() < length)
            throw new IllegalArgumentException("length");

        int need = length;
        while (need > 0) {
            ByteBuffer b = mBuffers.peek();
            int read = Math.min(b.remaining(), need);
            if (bytes != null)
                b.get(bytes, offset, read);
            need -= read;
            offset += read;
            if (b.remaining() == 0) {
                ByteBuffer removed = mBuffers.remove();
                assert b == removed;
                reclaim(b);
            }
        }

        remaining -= length;
    }

    public void get(ByteBufferList into, int length) {
        if (remaining() < length)
            throw new IllegalArgumentException("length");
        int offset = 0;

        while (offset < length) {
            ByteBuffer b = mBuffers.remove();
            int remaining = b.remaining();

            if (remaining == 0) {
                reclaim(b);
                continue;
            }

            if (offset + remaining > length) {
                int need = length - offset;
                // this is shared between both
                ByteBuffer subset = obtain(need);
                subset.limit(need);
                b.get(subset.array(), 0, need);
                into.add(subset);
                mBuffers.addFirst(b);
                assert subset.capacity() >= need;
                assert subset.position() == 0;
                break;
            }
            else {
                // this belongs to the new list
                into.add(b);
            }

            offset += remaining;
        }

        remaining -= length;
    }

    public void get(ByteBufferList into) {
        get(into, remaining());
    }

    public ByteBufferList get(int length) {
        ByteBufferList ret = new ByteBufferList();
        get(ret, length);
        return ret.order(order);
    }

    public ByteBuffer getAll() {
        if (remaining() == 0)
            return EMPTY_BYTEBUFFER;
        read(remaining());
        return remove();
    }

    private ByteBuffer read(int count) {
        if (remaining() < count)
            throw new IllegalArgumentException("count : " + remaining() + "/" + count);

        ByteBuffer first = mBuffers.peek();
        while (first != null && !first.hasRemaining()) {
            reclaim(mBuffers.remove());
            first = mBuffers.peek();
        }
        
        if (first == null) {
            return EMPTY_BYTEBUFFER;
        }

        if (first.remaining() >= count) {
            return first.order(order);
        }

        ByteBuffer ret = obtain(count);
        ret.limit(count);
        byte[] bytes = ret.array();
        int offset = 0;
        ByteBuffer bb = null;
        while (offset < count) {
            bb = mBuffers.remove();
            int toRead = Math.min(count - offset, bb.remaining());
            bb.get(bytes, offset, toRead);
            offset += toRead;
            if (bb.remaining() == 0) {
                reclaim(bb);
                bb = null;
            }
        }
        // if there was still data left in the last buffer we popped
        // toss it back into the head
        if (bb != null && bb.remaining() > 0)
            mBuffers.addFirst(bb);
        mBuffers.addFirst(ret);
        return ret.order(order);
    }
    
    public void trim() {
        // this clears out buffers that are empty in the beginning of the list
        read(0);
    }

    public ByteBufferList add(ByteBufferList b) {
        b.get(this);
        return this;
    }

    public ByteBufferList add(ByteBuffer b) {
        if (b.remaining() <= 0) {
//            System.out.println("reclaiming remaining: " + b.remaining());
            reclaim(b);
            return this;
        }
        addRemaining(b.remaining());
        // see if we can fit the entirety of the buffer into the end
        // of the current last buffer
        if (mBuffers.size() > 0) {
            ByteBuffer last = mBuffers.getLast();
            if (last.capacity() - last.limit() >= b.remaining()) {
                last.mark();
                last.position(last.limit());
                last.limit(last.capacity());
                last.put(b);
                last.limit(last.position());
                last.reset();
                reclaim(b);
                trim();
                return this;
            }
        }
        mBuffers.add(b);
        trim();
        return this;
    }

    public void addFirst(ByteBuffer b) {
        if (b.remaining() <= 0) {
            reclaim(b);
            return;
        }
        addRemaining(b.remaining());
        // see if we can fit the entirety of the buffer into the beginning
        // of the current first buffer
        if (mBuffers.size() > 0) {
            ByteBuffer first = mBuffers.getFirst();
            if (first.position() >= b.remaining()) {
                first.position(first.position() - b.remaining());
                first.mark();
                first.put(b);
                first.reset();
                reclaim(b);
                return;
            }
        }
        mBuffers.addFirst(b);
    }

    private void addRemaining(int remaining) {
        if (this.remaining() >= 0)
            this.remaining += remaining;
    }

    public void recycle() {
        while (mBuffers.size() > 0) {
            reclaim(mBuffers.remove());
        }
        assert mBuffers.size() == 0;
        remaining = 0;
    }
    
    public ByteBuffer remove() {
        ByteBuffer ret = mBuffers.remove();
        remaining -= ret.remaining();
        return ret;
    }
    
    public int size() {
        return mBuffers.size();
    }

    public String peekString(Charset charset) {
        if (charset == null)
            charset = Charset.forName("US-ASCII");
        StringBuilder builder = new StringBuilder();
        for (ByteBuffer bb: mBuffers) {
            byte[] bytes;
            int offset;
            int length;
            if (bb.isDirect()) {
                bytes = new byte[bb.remaining()];
                offset = 0;
                length = bb.remaining();
                bb.get(bytes);
            }
            else {
                bytes = bb.array();
                offset = bb.arrayOffset() + bb.position();
                length = bb.remaining();
            }
            builder.append(new String(bytes, offset, length, charset));
        }
        return builder.toString();
    }

    public String readString(Charset charset) {
        String ret = peekString(charset);
        recycle();
        return ret;
    }

    static class Reclaimer implements Comparator<ByteBuffer> {
        @Override
        public int compare(ByteBuffer byteBuffer, ByteBuffer byteBuffer2) {
            // keep the smaller ones at the head, so they get tossed out quicker
            if (byteBuffer.capacity() == byteBuffer2.capacity())
                return 0;
            if (byteBuffer.capacity() > byteBuffer2.capacity())
                return 1;
            return -1;
        }
    }

    static PriorityQueue<ByteBuffer> reclaimed = new PriorityQueue<ByteBuffer>(8, new Reclaimer());

    private static PriorityQueue<ByteBuffer> getReclaimed() {
        Looper mainLooper = Looper.getMainLooper();
        if (mainLooper != null) {
            if (Thread.currentThread() == mainLooper.getThread())
                return null;
        }
        return reclaimed;
    }

    private static int MAX_SIZE = 1024 * 1024;
    public static int MAX_ITEM_SIZE = 1024 * 256;
    static int currentSize = 0;
    static int maxItem = 0;

    private static boolean reclaimedContains(ByteBuffer b) {
        for (ByteBuffer other: reclaimed) {
            if (other == b)
                return true;
        }
        return false;
    }

    public static void reclaim(ByteBuffer b) {
        if (b == null || b.isDirect())
            return;
        if (b.arrayOffset() != 0 || b.array().length != b.capacity())
            return;
        if (b.capacity() < 8192)
            return;
        if (b.capacity() > MAX_ITEM_SIZE)
            return;

        PriorityQueue<ByteBuffer> r = getReclaimed();
        if (r == null)
            return;

        synchronized (LOCK) {
            while (currentSize > MAX_SIZE && r.size() > 0 && r.peek().capacity() < b.capacity()) {
//                System.out.println("removing for better: " + b.capacity());
                ByteBuffer head = r.remove();
                currentSize -= head.capacity();
            }

            if (currentSize > MAX_SIZE) {
//                System.out.println("too full: " + b.capacity());
                return;
            }

            assert !reclaimedContains(b);

            b.position(0);
            b.limit(b.capacity());
            currentSize += b.capacity();

            r.add(b);
            assert r.size() != 0 ^ currentSize == 0;

            maxItem = Math.max(maxItem, b.capacity());
        }
    }

    private static final Object LOCK = new Object();

    public static ByteBuffer obtain(int size) {
        if (size <= maxItem) {
            PriorityQueue<ByteBuffer> r = getReclaimed();
            if (r != null) {
                synchronized (LOCK) {
                    while (r.size() > 0) {
                        ByteBuffer ret = r.remove();
                        if (r.size() == 0)
                            maxItem = 0;
                        currentSize -= ret.capacity();
                        assert r.size() != 0 ^ currentSize == 0;
                        if (ret.capacity() >= size) {
//                            System.out.println("using " + ret.capacity());
                            return ret;
                        }
//                        System.out.println("dumping " + ret.capacity());
                    }
                }
            }
        }

//        System.out.println("alloc for " + size);
        ByteBuffer ret = ByteBuffer.allocate(Math.max(8192, size));
        return ret;
    }

    public static final ByteBuffer EMPTY_BYTEBUFFER = ByteBuffer.allocate(0);
}
