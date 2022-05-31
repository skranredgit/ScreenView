package com.example.test5;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.stealthcopter.networktools.SubnetDevices;
import com.stealthcopter.networktools.subnet.Device;

import java.util.ArrayList;

public class starting_streem extends AppCompatActivity {
    Button connecting, close;
    EditText ip, port;
    ImageButton reload;
    static String ip_string;
    TextView tvScanning;
    ListView ips;

    ArrayList<Dataip> ipList;
    MyListAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.steeming);


        tvScanning = findViewById(R.id.Scanning);
        ips = findViewById(R.id.ips);
        close = findViewById(R.id.close);
        connecting = findViewById(R.id.connecting);
        ip = findViewById(R.id.ip);

        reload = findViewById(R.id.reload);
        port = findViewById(R.id.port);
        ipList = new ArrayList();
        adapter = new MyListAdapter(this, ipList);
        ips.setAdapter(adapter);
        scan();

        connecting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ip_string = ip.getText().toString() + ":" + port.getText().toString();
                LaptopServer.mServerName = ip.getText().toString().split(":")[0];
                System.out.println(ip.getText().toString());
                Intent intent = new Intent(view.getContext(), ClientActivity.class);
                startActivity(intent);
            }
        });

        reload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ipList.clear();
                adapter.notifyDataSetInvalidated();
                scan();
            }
        });

        ips.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ip.setText(ipList.get(position).getip());
            }
        });

        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                simulateKey(KeyEvent.KEYCODE_BACK);
            }
        });

    }

    public static void simulateKey(final int KeyCode) {

        new Thread() {
            @Override
            public void run() {
                try {
                    Instrumentation inst = new Instrumentation();
                    inst.sendKeyDownUpSync(KeyCode);
                } catch (Exception e) {
                    Log.e("Exception", e.toString());
                }
            }

        }.start();
    }

    private void findSubnetDevices() {
        tvScanning.setText("in progress...");
        SubnetDevices subnetDevices = SubnetDevices.fromLocalAddress().findDevices(new SubnetDevices.OnSubnetDeviceFound() {

            @Override
            public void onDeviceFound(Device device) {

            }

            public void onFinished(ArrayList<Device> devicesFound) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvScanning.setText("Scan Finished");
                        for(Device device:devicesFound) {
                            ipList.add(new Dataip(device.hostname, device.ip, device.mac));
                            adapter.notifyDataSetInvalidated();
                        }
                    }
                });
            }
        });
    }


    public void scan() {
        if (isOnline(getApplicationContext())) {
            tvScanning.setTextColor(getResources().getColor(R.color.dark_grey));
            findSubnetDevices();
        } else {
            tvScanning.setTextColor(getResources().getColor(R.color.red));
            Toast.makeText(getApplicationContext(), "you don't connect to internet", Toast.LENGTH_SHORT).show();
            tvScanning.setText("error");
        }
    }

    public static boolean isOnline(Context context)
    {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        if (netInfo != null && netInfo.isConnectedOrConnecting())
        {
            return true;
        }
        return false;
    }
}
