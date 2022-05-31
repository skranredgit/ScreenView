package com.example.test5;


import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Point;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import com.example.test5.async.ByteBufferList;
import com.example.test5.async.DataEmitter;
import com.example.test5.async.callback.CompletedCallback;
import com.example.test5.async.http.WebSocket;
import com.example.test5.async.callback.DataCallback;
import com.example.test5.async.http.AsyncHttpClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class ClientActivity extends Activity implements SurfaceHolder.Callback, View.OnTouchListener{

    private static final String TAG = "TAG";

    SurfaceView surfaceView;

    MediaCodec decoder;
    boolean decoderConfigured = false;
    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

    CircularEncoderBuffer encBuffer = new CircularEncoderBuffer((int)(1920 * 1080 * 0.5), 60, 10);

    private WebSocket webSocket;
    private WebSocket touchSocket;
    private LaptopServer mServer;



    ImageView mess;

    ImageView cube, circle, treangl;

    int deviceWidth;
    int deviceHeight;
    Point videoResolution = new Point();

    int i = -1;
    String[] infoStringParts;

    private boolean firstIFrameAdded;

    public static final String KEY_FINGER_DOWN = "fingerdown";
    public static final String KEY_FINGER_UP = "fingerup";
    public static final String KEY_FINGER_MOVE = "fingermove";
    public static final String KEY_EVENT_TYPE = "type";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client);

        cube = findViewById(R.id.cube);
        circle = findViewById(R.id.circle);
        treangl = findViewById(R.id.treangl);

        surfaceView = (SurfaceView) findViewById(R.id.main_surface_view);
        surfaceView.getHolder().addCallback(this);
        surfaceView.setOnTouchListener(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mServer = new LaptopServer();

        cube.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mServer.openConnection();
                } catch (Exception e) {
                    Log.e("LOG_TAG", LaptopServer.mServerName);
                    Log.e("LOG_TAG", e.getMessage());
                    Log.e("LOG_TAG", e.getMessage());
                    mServer = null;
                }
            }
        }).start();


        mess = findViewById(R.id.mess);
        cube = findViewById(R.id.cube);
        circle = findViewById(R.id.circle);
        treangl = findViewById(R.id.treangl);

        cube.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mServer == null) {
                    Log.e("LOG_TAG", "Сервер не создан");
                }
                new Thread(new Runnable() {
                    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
                    @Override
                    public void run() {
                        try {
                            mServer.sendData("__cube__".getBytes(StandardCharsets.UTF_8));
                        } catch (Exception e) {
                            Log.e("LOG_TAG", e.getMessage());
                        }
                    }
                }).start();
            }
        });

        circle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mServer == null) {
                    Log.e("LOG_TAG", "Сервер не создан");
                }
                new Thread(new Runnable() {
                    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
                    @Override
                    public void run() {
                        try {
                            mServer.sendData("__circle__".getBytes(StandardCharsets.UTF_8));
                        } catch (Exception e) {
                            Log.e("LOG_TAG", e.getMessage());
                        }
                    }
                }).start();
            }
        });

        treangl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mServer == null) {
                    Log.e("LOG_TAG", "Сервер не создан");
                }
                new Thread(new Runnable() {
                    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
                    @Override
                    public void run() {
                        try {
                            mServer.sendData("__treangl__".getBytes(StandardCharsets.UTF_8));
                        } catch (Exception e) {
                            Log.e("LOG_TAG", e.getMessage());
                        }
                    }
                }).start();
            }
        });

        mess.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onCreateDialogsValue();
            }
        });
    }

    public void onCreateDialogsValue() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = (LinearLayout) getLayoutInflater().inflate(R.layout.message, null);
        final EditText valueKey = (EditText) view.findViewById(R.id.message);
        builder.setTitle("message")
                .setView(view)
                .setCancelable(false);
        builder.setNegativeButton("back",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,
                                        int which) {
                    }
                });
        builder.setPositiveButton("send",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,
                                        int which) {
                        Log.d("123", "Send text to server");
                        if (mServer == null) {
                            Log.e("LOG_TAG", "Сервер не создан");
                        }
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    mServer.sendData(String.valueOf(valueKey.getText()).getBytes());
                                } catch (Exception e) {
                                    Log.e("LOG_TAG", e.getMessage());
                                }
                            }
                        }).start();
                    }
                });

        builder.show();
    }


    private AsyncHttpClient.WebSocketConnectCallback websocketCallback = new AsyncHttpClient
            .WebSocketConnectCallback() {
        @Override
        public void onCompleted(final Exception ex, WebSocket webSocket) {

            if (ex != null) {
                ex.printStackTrace();
                return;
            }
            ClientActivity.this.webSocket = webSocket;
            showToast("Connection Completed");
//            setTimer();
            webSocket.setClosedCallback(new CompletedCallback() {
                @Override
                public void onCompleted(Exception e) {
                    ClientActivity.this.webSocket = null;
                    showToast("Closed");
                }
            });
            webSocket.setStringCallback(new WebSocket.StringCallback() {
                public void onStringAvailable(String s) {
                    String[] parts = s.split(",");
                    try {
                        info.set(Integer.parseInt(parts[0]),
                                Integer.parseInt(parts[1]),
                                Long.parseLong(parts[2]),
                                Integer.parseInt(parts[3]));
                        if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            videoResolution.x = Integer.parseInt(parts[4]);
                            videoResolution.y = Integer.parseInt(parts[5]);
                        }
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                        Log.d(TAG, "===========Exception = " + e.getMessage() + " =================");
                        //TODO: Need to stop the decoder or to skip the current decoder loop
                        showToast(e.getMessage());
                    }

                }
            });
            webSocket.setDataCallback(new DataCallback() {
                @Override
                public void onDataAvailable(DataEmitter dataEmitter, ByteBufferList byteBufferList) {
                    ++i;
                    ByteBuffer b = byteBufferList.getAll();
//                        Log.d(TAG, "Received buffer = " + b);
                    if (i % 2 == 0) {
                        String temp = new String(b.array());
//                            Log.d(TAG, "Received String = " + temp);
                        infoStringParts = temp.split(",");
                        info.set(Integer.parseInt(infoStringParts[0]), Integer.parseInt(infoStringParts[1]),
                                Long.parseLong(infoStringParts[2]), Integer.parseInt(infoStringParts[3]));
                    } else {
                        setData(b, info);
                    }
                    byteBufferList.recycle();
                }
            });
        }
    };

    /**
     * Main decoder function which reads the encoded frames from the CircularBuffer and renders them
     * on to the Surface
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void doDecoderThingie() {
        boolean outputDone = false;

        while(!decoderConfigured) {
        }

        if (MainActivity.DEBUG) Log.d(TAG, "Decoder Configured");

        while(!firstIFrameAdded) {}

        if (MainActivity.DEBUG) Log.d(TAG, "Main Body");

        int index = encBuffer.getFirstIndex();
        if (index < 0) {
            Log.e(TAG, "CircularBuffer Error");
            return;
        }
        ByteBuffer encodedFrames;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (!outputDone) {
            encodedFrames = encBuffer.getChunk(index, info);
            encodedFrames.limit(info.size + info.offset);
            encodedFrames.position(info.offset);

            try {
                index = encBuffer.getNextIntCustom(index);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            int inputBufIndex = decoder.dequeueInputBuffer(-1);
            if (inputBufIndex >= 0) {
                ByteBuffer inputBuf = decoder.getInputBuffer(inputBufIndex);
                inputBuf.clear();
                inputBuf.put(encodedFrames);
                decoder.queueInputBuffer(inputBufIndex, 0, info.size,
                        info.presentationTimeUs, info.flags);
            }

            if (decoderConfigured) {
                int decoderStatus = decoder.dequeueOutputBuffer(info, CodecUtils.TIMEOUT_USEC);
                if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (MainActivity.DEBUG) Log.d(TAG, "no output from decoder available");
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    if (MainActivity.DEBUG) Log.d(TAG, "decoder output buffers changed");
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // this happens before the first frame is returned
                    MediaFormat decoderOutputFormat = decoder.getOutputFormat();
                    Log.d(TAG, "decoder output format changed: " +
                            decoderOutputFormat);
                } else {
                    decoder.releaseOutputBuffer(decoderStatus, true);
                }
            }
        }
    }

    /**
     * Add a new frame to the CircularBuffer
     * @param encodedFrame The new frame to be added to the CircularBuffer
     * @param info The BufferInfo object for the encodedFrame
     */
    private void setData(ByteBuffer encodedFrame, MediaCodec.BufferInfo info) {
        if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            Log.d(TAG, "Configuring Decoder");
            MediaFormat format =
                    MediaFormat.createVideoFormat(CodecUtils.MIME_TYPE, CodecUtils.WIDTH, CodecUtils.HEIGHT);
            format.setByteBuffer("csd-0", encodedFrame);
            decoder.configure(format, surfaceView.getHolder().getSurface(),
                    null, 0);
            decoder.start();
            decoderConfigured = true;
            Log.d(TAG, "decoder configured (" + info.size + " bytes)");
            return;
        }

        encBuffer.add(encodedFrame, info.flags, info.presentationTimeUs);
        if (MainActivity.DEBUG) Log.d(TAG, "Adding frames to the Buffer");
        if ((info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
            firstIFrameAdded = true;
            if (MainActivity.DEBUG) Log.d(TAG, "First I-Frame added");
        }
    }

    private void showToast(final String message) {
        ClientActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ClientActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        deviceWidth = surfaceView.getWidth();
        deviceHeight = surfaceView.getHeight();
        try {
            decoder = MediaCodec.createDecoderByType(CodecUtils.MIME_TYPE);
            new Thread(new Runnable() {
                @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void run() {
                    doDecoderThingie();
                }
            }).start();
            String ss = starting_streem.ip_string;
            System.out.println(starting_streem.ip_string);
            AsyncHttpClient.getDefaultInstance().websocket("ws://" + ss, null, websocketCallback);
            String ip = ss.split(":")[0];
            showToast("IP = " + ip);
            AsyncHttpClient.getDefaultInstance().websocket("ws://" + ip + ":6059", null,
                    new AsyncHttpClient.WebSocketConnectCallback() {
                        @Override
                        public void onCompleted(Exception ex, WebSocket tSocket) {
                            Log.d("ccc", "111111111");
                            if (ex != null) {
                                ex.printStackTrace();
                                showToast(ex.getMessage());
                                Log.d("222", "server is null");
                                return;
                            }
                            Log.d("333", "server is not null");
                            touchSocket = tSocket;
                        }
                    });
        } catch (IOException e) {
            e.printStackTrace();
            Log.d("333", "server is null");
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i2, int i3) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        JSONObject touchData = new JSONObject();
        try {
            switch (motionEvent.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    touchData.put("type", KEY_FINGER_DOWN);
                    break;
                case MotionEvent.ACTION_MOVE:
                    touchData.put(KEY_EVENT_TYPE, KEY_FINGER_MOVE);
                    break;
                case MotionEvent.ACTION_UP:
                    touchData.put(KEY_EVENT_TYPE, KEY_FINGER_UP);
                    break;
                default:
                    return true;
            }
            touchData.put("x", motionEvent.getX()/deviceWidth);
            touchData.put("y", motionEvent.getY()/deviceHeight);
            Log.d(TAG, "Sending = " + touchData.toString());
            if (touchSocket != null) {
                Log.d("000000", touchData.toString());
                touchSocket.send(touchData.toString());
            } else {
                Log.e(TAG, "Can't send touch events. Socket is null.");
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return true;
    }
}
