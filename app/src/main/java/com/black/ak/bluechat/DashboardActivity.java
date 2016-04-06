package com.black.ak.bluechat;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

public class DashboardActivity extends AppCompatActivity {

    TextView textViewDeviceName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        textViewDeviceName = (TextView) findViewById(R.id.textView_device_name);

        Intent intent = getIntent();
        BluetoothDevice device = (BluetoothDevice) intent.getParcelableExtra("device");
        textViewDeviceName.setText(device.getName());
    }

}
