package com.example.test5;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import com.michaldrabik.tapbarmenulib.TapBarMenu;

import java.util.Timer;
import java.util.TimerTask;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;


public class MainActivity extends AppCompatActivity {

    Button s_stream, c_device;
    public static final boolean DEBUG = false;
    private static String CHANNEL_ID = "mess channel";
    private static final int NOTIFY_ID = 101;
    public Timer timer;


    @BindView(R.id.tapBarMenu) TapBarMenu tapBarMenu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        c_device = findViewById(R.id.connect);
        s_stream = findViewById(R.id.start_stream);

        c_device.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(view.getContext(), connect_device.class);
                startActivity(intent);
            }
        });

        s_stream.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(view.getContext(), starting_streem.class);
                startActivity(intent);
            }
        });
    }


    @OnClick(R.id.tapBarMenu)
    public void onMenuButtonClick() {
        Log.d("123", "1233333");
        tapBarMenu.toggle();
    }

    @OnClick({R.id.settings})
    public void onMenuItemClick(View view) {
        tapBarMenu.close();
        switch (view.getId()) {
            case R.id.settings:
                Intent intent = new Intent(this, settings.class);
                startActivity(intent);
                Toast toast = Toast.makeText(getApplicationContext(), "settings open", Toast.LENGTH_SHORT);
                toast.show();
                break;
        }
    }

}
