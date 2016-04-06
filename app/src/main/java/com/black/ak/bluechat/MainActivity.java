package com.black.ak.bluechat;

import android.Manifest;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter bluetoothAdapter;
    ListView listViewDevices;
    Switch switchSearch;
    ToggleButton toggleButtonVisible;
    ArrayAdapter<String> arrayAdapterDevices;
    private ProgressDialog searchDialog;
    private String strUUID="00000000-0000-0000-0000-0123456789AB";
    private AcceptThread acceptThread;

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
        } else if(!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 101);
        }

        //Getting objects from ids
        listViewDevices = (ListView) findViewById(R.id.listView_devices);
        switchSearch = (Switch) findViewById(R.id.switch_search);
        toggleButtonVisible = (ToggleButton) findViewById(R.id.toggleButton_visible);

        arrayAdapterDevices = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
        listViewDevices.setAdapter(arrayAdapterDevices);

        //Set the Switch OFF
        switchSearch.setChecked(false);
        switchSearch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    searchDialog = new ProgressDialog(MainActivity.this);
                    searchDialog.setTitle("Searching Devices");
                    searchDialog.setMessage("Please wait... ");
                    searchDialog.show();
                    arrayAdapterDevices.clear();
                    getPairedDevices();
                    bluetoothAdapter.startDiscovery();
                } else {
                    bluetoothAdapter.cancelDiscovery();
                }
            }
        });

        //Set the Toggle Button OFF
        toggleButtonVisible.setChecked(false);
        toggleButtonVisible.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Intent discoverableIntent = null;
                if(isChecked) {
                    discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                    discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
                    startActivityForResult(discoverableIntent, 102);
                } else {
                    discoverableIntent = null;
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
                Toast.makeText(MainActivity.this, "Connecting to "+name, Toast.LENGTH_LONG ).show();

                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(macAddress);
                ConnectTast task = new ConnectTast();
                task.execute(device);
            }
        });

        //Register the Broadcast Receiver
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mReceiver, filter);



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

                if(!arrayAdapterDevices.isEmpty()) {
                    if(searchDialog != null) {
                        searchDialog.dismiss();
                        searchDialog = null;
                    }
                }
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
            acceptThread = new AcceptThread();
            acceptThread.start();
        } else if(resultCode == RESULT_CANCELED) {
            Toast.makeText(this,"Something went wrong",Toast.LENGTH_LONG).show();
            finish();
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
        if(acceptThread != null)
            acceptThread.cancel();

    }

    private class AcceptThread extends Thread {
        private final BluetoothServerSocket bluetoothServerSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;
            try {
                // MY_UUID is the app's UUID string, also used by the client code
                UUID uuid = UUID.fromString(strUUID);
                tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord("Bluechat", uuid);
            } catch (IOException e) {
                e.printStackTrace();
            }
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
                    Log.e("IOException","Unable to accept socket connection");
                    e.printStackTrace();
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
            } catch (IOException e) {
                Log.e("Socket Exception","Unable to close socket connection");
                e.printStackTrace();
            }
        }
    }

    private class ConnectTast extends AsyncTask<BluetoothDevice, String, String> {

        private ProgressDialog dialog;
        private BluetoothSocket mmSocket;
        private BluetoothDevice device;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            dialog = new ProgressDialog(MainActivity.this);
            dialog.setTitle("Connecting");
            dialog.setMessage("Please Wait");
            dialog.setCancelable(false);
            dialog.show();
        }

        @Override
        protected String doInBackground(BluetoothDevice... params) {
            device = params[0];
            UUID uuid = UUID.fromString(strUUID);
            try {
                mmSocket = device.createRfcommSocketToServiceRecord(uuid);
                // Cancel discovery because it will slow down the connection
                bluetoothAdapter.cancelDiscovery();
                mmSocket.connect();
            } catch (IOException e) {
                e.printStackTrace();
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    Log.e("Socket Exception","Unable to close socket connection");
                    closeException.printStackTrace();
                }
                return "fail";
            }

            return "success";
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            dialog.dismiss();
            if(result.equalsIgnoreCase("success")) {
                Toast.makeText(MainActivity.this,"Connected",Toast.LENGTH_LONG).show();
                //do something with the mmSockect here..
                Intent intent = new Intent(MainActivity.this, DashboardActivity.class);
                Object object = mmSocket;
                intent.putExtra("socket", (Bundle) object);
                intent.putExtra("device", device);
                startActivity(intent);

            } else if(result.equalsIgnoreCase("fail")) {
                Toast.makeText(MainActivity.this,"Connection Failed",Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(MainActivity.this,"Something went wrong",Toast.LENGTH_LONG).show();
            }
        }
    }


}
