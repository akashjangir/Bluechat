package com.black.ak.bluechat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.Toast;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter bluetoothAdapter;
    ListView listViewDevices;
    Switch switchSearch;
    ArrayAdapter<String> arrayAdapterDevices;
    private String strUUID="00000000-0000-0000-0000-0123456789AB";
    private AcceptThread acceptThread;
    private ConnectThread connectThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        int hasPermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION);
        if(hasPermission == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, 102);
        }

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if(bluetoothAdapter == null) {
            Toast.makeText(this,"Bluetooth Device Not Found...",Toast.LENGTH_LONG).show();
            finishActivity(RESULT_CANCELED);
        } else if(!bluetoothAdapter.isEnabled()) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivityForResult(discoverableIntent, 101);
        }

        listViewDevices = (ListView) findViewById(R.id.listView_devices);
        switchSearch = (Switch) findViewById(R.id.switch_search);

        arrayAdapterDevices = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
        listViewDevices.setAdapter(arrayAdapterDevices);

        //Set the Switch OFF
        switchSearch.setChecked(false);
        switchSearch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    arrayAdapterDevices.clear();
                    bluetoothAdapter.startDiscovery();
                } else {
                    bluetoothAdapter.cancelDiscovery();
                }
            }
        });

        listViewDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String info = arrayAdapterDevices.getItem(position);
                String array[] = info.split("\n");
                String name = array[0];
                String macAddress = array[1];
                Toast.makeText(MainActivity.this, name+" "+macAddress, Toast.LENGTH_LONG ).show();

                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(macAddress);
                connectThread = new ConnectThread(device);
                connectThread.start();
            }
        });

        //Register the Broadcast Receiver
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mReceiver, filter);

        acceptThread = new AcceptThread();
        acceptThread.start();

    }

    private void getPairedDevices() {
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if(pairedDevices.size() > 0) {
            for(BluetoothDevice device : pairedDevices) {
                //Add the name and address to array adapter to show in listview
                arrayAdapterDevices.add(device.getName()+"\n"+device.getAddress());
            }
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            //When discovery finds a device
            if(BluetoothDevice.ACTION_FOUND.equals(action)) {
                //Get the bluetooth device form the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                //Add the name and address to array adapter to show in listview
                arrayAdapterDevices.add(device.getName()+"\n"+device.getAddress());
            }
        }
    };


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode == 101) {
            Toast.makeText(this,"Bluetooth Enabled",Toast.LENGTH_LONG).show();
        } else if(resultCode == RESULT_CANCELED) {
            Toast.makeText(this,"Something went wrong",Toast.LENGTH_LONG).show();
            finish();
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
        acceptThread.cancel();
        connectThread.cancel();
    }

    private class AcceptThread extends Thread {
        private final BluetoothServerSocket bluetoothServerSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;
            try {
                // MY_UUID is the app's UUID string, also used by the client code
                UUID uuid = UUID.fromString(strUUID);
                tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord("Bluechat", uuid);
            } catch (IOException e) { }
            bluetoothServerSocket = tmp;
        }

        @Override
        public void run() {
            BluetoothSocket socket = null;
            // Keep listening until exception occurs or a socket is returned
            while (true) {
                try {
                    socket = bluetoothServerSocket.accept();
                } catch (IOException e) {
                    break;
                }
                // If a connection was accepted
                if (socket != null) {
                    // Do work to manage the connection (in a separate thread)
                    //manageConnectedSocket(socket);
                    try {
                        bluetoothServerSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
        }

        /** Will cancel the listening socket, and cause the thread to finish */
        public void cancel() {
            try {
                bluetoothServerSocket.close();
            } catch (IOException e) { }
        }
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket,
            // because mmSocket is final
            BluetoothSocket tmp = null;
            mmDevice = device;

            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                // MY_UUID is the app's UUID string, also used by the server code
                UUID uuid = UUID.fromString(strUUID);
                tmp = device.createRfcommSocketToServiceRecord(uuid);
            } catch (IOException e) { }
            mmSocket = tmp;
        }

        @Override
        public void run() {
            // Cancel discovery because it will slow down the connection
            bluetoothAdapter.cancelDiscovery();

            try {
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception
                mmSocket.connect();
                Toast.makeText(MainActivity.this, "Connected", Toast.LENGTH_LONG ).show();
            } catch (IOException connectException) {
                Toast.makeText(MainActivity.this, "Unable to connect", Toast.LENGTH_LONG ).show();
                try {
                    mmSocket.close();
                } catch (IOException closeException) { }
                return;
            }

            // Do work to manage the connection (in a separate thread)
            //manageConnectedSocket(mmSocket);
        }

        /** Will cancel an in-progress connection, and close the socket */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }

    }
}
