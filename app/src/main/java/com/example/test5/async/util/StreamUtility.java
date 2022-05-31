package com.example.test5.async.util;

import java.io.Closeable;
import java.io.IOException;

public class StreamUtility {
    
    public static void closeQuietly(Closeable... closeables) {
        if (closeables == null)
            return;
        for (Closeable closeable : closeables) {
            if (closeable != null) {
                try {
                    closeable.close();
                } catch (IOException e) {
                    // http://stackoverflow.com/a/156525/9636
                }
            }
        }
    }
}

