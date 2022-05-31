package com.example.test5.async.http;

import android.net.Uri;

public class AsyncHttpHead extends AsyncHttpRequest {
    public AsyncHttpHead(Uri uri) {
        super(uri, METHOD);
    }

    public static final String METHOD = "HEAD";
}
