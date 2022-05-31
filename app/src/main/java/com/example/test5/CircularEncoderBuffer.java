package com.example.test5;

import android.media.MediaCodec;
import android.util.Log;
import java.nio.ByteBuffer;

public class CircularEncoderBuffer {
    private static final String TAG = "CircularEncoderBuffer";
    private static final boolean EXTRA_DEBUG = true;
    private static final boolean VERBOSE = false;
    private ByteBuffer mDataBufferWrapper;
    private byte[] mDataBuffer;
    private int[] mPacketFlags;
    private long[] mPacketPtsUsec;
    private int[] mPacketStart;
    private int[] mPacketLength;
    private int mMetaHead;
    private int mMetaTail;
    public CircularEncoderBuffer(int bitRate, int frameRate, int desiredSpanSec) {
        int dataBufferSize = bitRate * desiredSpanSec / 8;
        mDataBuffer = new byte[dataBufferSize];
        mDataBufferWrapper = ByteBuffer.wrap(mDataBuffer);
        int metaBufferCount = frameRate * desiredSpanSec * 2;
        mPacketFlags = new int[metaBufferCount];
        mPacketPtsUsec = new long[metaBufferCount];
        mPacketStart = new int[metaBufferCount];
        mPacketLength = new int[metaBufferCount];

        if (VERBOSE) {
            Log.d(TAG, "CBE: bitRate=" + bitRate + " frameRate=" + frameRate +
                    " desiredSpan=" + desiredSpanSec + ": dataBufferSize=" + dataBufferSize +
                    " metaBufferCount=" + metaBufferCount);
        }
    }

    /**
     * Adds a new encoded data packet to the buffer.
     *
     * @param buf The data.  Set position() to the start offset and limit() to position+size.
     *     The position and limit may be altered by this method.
     * @param flags MediaCodec.BufferInfo flags.
     * @param ptsUsec Presentation time stamp, in microseconds.
     */
    public void add(ByteBuffer buf, int flags, long ptsUsec) {
        int size = buf.limit() - buf.position();
        if (VERBOSE) {
            Log.d(TAG, "add size=" + size + " flags=0x" + Integer.toHexString(flags) +
                    " pts=" + ptsUsec);
        }
        while (!canAdd(size)) {
            removeTail();
        }

        final int dataLen = mDataBuffer.length;
        final int metaLen = mPacketStart.length;
        int packetStart = getHeadStart();
        mPacketFlags[mMetaHead] = flags;
        mPacketPtsUsec[mMetaHead] = ptsUsec;
        mPacketStart[mMetaHead] = packetStart;
        mPacketLength[mMetaHead] = size;

        // Copy the data in.  Take care if it gets split in half.
        if (packetStart + size < dataLen) {
            // one chunk
            buf.get(mDataBuffer, packetStart, size);
        } else {
            // two chunks
            int firstSize = dataLen - packetStart;
            if (VERBOSE) { Log.v(TAG, "split, firstsize=" + firstSize + " size=" + size); }
            buf.get(mDataBuffer, packetStart, firstSize);
            buf.get(mDataBuffer, 0, size - firstSize);
        }

        mMetaHead = (mMetaHead + 1) % metaLen;

        if (EXTRA_DEBUG) {
            // The head packet is the next-available spot.
            mPacketFlags[mMetaHead] = 0x77aaccff;
            mPacketPtsUsec[mMetaHead] = -1000000000L;
            mPacketStart[mMetaHead] = -100000;
            mPacketLength[mMetaHead] = Integer.MAX_VALUE;
        }
    }

    /**
     * Returns the index of the oldest sync frame.  Valid until the next add().
     * <p>
     * When sending output to a MediaMuxer, start here.
     */
    public int getFirstIndex() {
        final int metaLen = mPacketStart.length;

        int index = mMetaTail;
        while (index != mMetaHead) {
            if ((mPacketFlags[index] & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                break;
            }
            index = (index + 1) % metaLen;
        }

        if (index == mMetaHead) {
            Log.w(TAG, "HEY: could not find sync frame in buffer");
            index = -1;
        }
        return index;
    }

    public int getNextIntCustom(int index) throws InterruptedException {
        int next = getNextIndex(index);
        while (next == -1) {
            next = getNextIndex(index);
            Thread.sleep(100);
        }
        return next;
    }

    /**
     * Returns the index of the next packet, or -1 if we've reached the end.
     */
    public int getNextIndex(int index) {
        final int metaLen = mPacketStart.length;
        int next = (index + 1) % metaLen;
        if (next == mMetaHead) {
            next = -1;
        }
        return next;
    }

    /**
     * Returns a reference to a "direct" ByteBuffer with the data, and fills in the
     * BufferInfo.
     * <p>
     * The caller must not modify the contents of the returned ByteBuffer.  Altering
     * the position and limit is allowed.
     */
    public ByteBuffer getChunk(int index, MediaCodec.BufferInfo info) {
        final int dataLen = mDataBuffer.length;
        int packetStart = mPacketStart[index];
        int length = mPacketLength[index];

        info.flags = mPacketFlags[index];
        info.offset = packetStart;
        info.presentationTimeUs = mPacketPtsUsec[index];
        info.size = length;

        if (packetStart + length <= dataLen) {
            // one chunk; return full buffer to avoid copying data
            return mDataBufferWrapper;
        } else {
            // two chunks
            ByteBuffer tempBuf = ByteBuffer.allocateDirect(length);
            int firstSize = dataLen - packetStart;
            tempBuf.put(mDataBuffer, mPacketStart[index], firstSize);
            tempBuf.put(mDataBuffer, 0, length - firstSize);
            info.offset = 0;
            return tempBuf;
        }
    }

    /**
     * Computes the data buffer offset for the next place to store data.
     * <p>
     * Equal to the start of the previous packet's data plus the previous packet's length.
     */
    private int getHeadStart() {
        if (mMetaHead == mMetaTail) {
            // list is empty
            return 0;
        }

        final int dataLen = mDataBuffer.length;
        final int metaLen = mPacketStart.length;

        int beforeHead = (mMetaHead + metaLen - 1) % metaLen;
        return (mPacketStart[beforeHead] + mPacketLength[beforeHead] + 1) % dataLen;
    }

    /**
     * Determines whether this is enough space to fit "size" bytes in the data buffer, and
     * one more packet in the meta-data buffer.
     *
     * @return True if there is enough space to add without removing anything.
     */
    private boolean canAdd(int size) {
        final int dataLen = mDataBuffer.length;
        final int metaLen = mPacketStart.length;

        if (size > dataLen) {
            throw new RuntimeException("Enormous packet: " + size + " vs. buffer " +
                    dataLen);
        }
        if (mMetaHead == mMetaTail) {
            // empty list
            return true;
        }

        // Make sure we can advance head without stepping on the tail.
        int nextHead = (mMetaHead + 1) % metaLen;
        if (nextHead == mMetaTail) {
            if (VERBOSE) {
                Log.v(TAG, "ran out of metadata (head=" + mMetaHead + " tail=" + mMetaTail +")");
            }
            return false;
        }

        // Need the byte offset of the start of the "tail" packet, and the byte offset where
        // "head" will store its data.
        int headStart = getHeadStart();
        int tailStart = mPacketStart[mMetaTail];
        int freeSpace = (tailStart + dataLen - headStart) % dataLen;
        if (size > freeSpace) {
            if (VERBOSE) {
                Log.v(TAG, "ran out of data (tailStart=" + tailStart + " headStart=" + headStart +
                        " req=" + size + " free=" + freeSpace + ")");
            }
            return false;
        }

        if (VERBOSE) {
            Log.v(TAG, "OK: size=" + size + " free=" + freeSpace + " metaFree=" +
                    ((mMetaTail + metaLen - mMetaHead) % metaLen - 1));
        }

        return true;
    }

    /**
     * Removes the tail packet.
     */
    private void removeTail() {
        if (mMetaHead == mMetaTail) {
            throw new RuntimeException("Can't removeTail() in empty buffer");
        }
        final int metaLen = mPacketStart.length;
        mMetaTail = (mMetaTail + 1) % metaLen;
    }
}
