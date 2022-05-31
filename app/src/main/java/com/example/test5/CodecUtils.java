package com.example.test5;

import java.nio.ByteBuffer;

public class CodecUtils {

    public static final int WIDTH = 1080;
    public static final int HEIGHT = 1920;

    public static final int TIMEOUT_USEC = 1000;

    public static final String MIME_TYPE = "video/avc";

    public static ByteBuffer clone(ByteBuffer original) {
        ByteBuffer clone = ByteBuffer.allocate(original.capacity());
        original.rewind();//copy from the beginning
        clone.put(original);
        original.rewind();
        clone.flip();
        return clone;
    }
}
