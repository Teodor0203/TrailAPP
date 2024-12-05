package com.example.testing;

import static androidx.core.content.ContextCompat.startActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.w3c.dom.Text;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class ConnectionManager implements DataCallback{

    //region TAG and UUID
    private final String TAG = "ConnectionManager";
    private final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    //endregion

    //region Handler and BluetoothAdapter
    private Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();// UUID pentru SPP
    //endregion

    //region booleans
    private boolean isConnected = false;
    private boolean isListVisible = false;
    //endregion

    //region other
    private static ConnectionManager INSTACE;
    private Context context;
    private BluetoothSocket bluetoothSocket;
    private InputStream inputStream;
    private ProgressBar progressBar;
    private TextView textView;
    private Button startButton;
    //endregion

    public ConnectionManager(Context context, ProgressBar progressBar, TextView textView, Button startButton)
    {
        this.context = context;
        this.progressBar = progressBar;
        this.textView = textView;
        this.startButton = startButton;
        checkIfBluetoothIsSupported();
    }

    public ConnectionManager()
    {
        checkIfBluetoothIsSupported();
    }

    public static ConnectionManager getInstance()
    {
        if(INSTACE == null)
        {
            INSTACE = new ConnectionManager();
        }

        return INSTACE;
    }
    public static ConnectionManager getInstance(Context context, ProgressBar progressBar, TextView textView, Button startButton)
    {
        if(INSTACE == null)
        {
            INSTACE = new ConnectionManager(context, progressBar, textView, startButton);
        }

        return INSTACE;
    }

    public void checkIfBluetoothIsSupported()
    {
        if (adapter == null) {
            Toast.makeText(context, "Bluetooth is not supported!!", Toast.LENGTH_LONG).show();
        }
    }

    @SuppressLint("MissingPermission")
    public void enableBluetooth(Activity activity) {
        if (!adapter.isEnabled()) {
            Log.d(TAG, "onEnable: Enabling bluetooth");
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            activity.startActivityForResult(intent, 1); // Request code pentru activare Bluetooth

            // Înregistrăm un receiver pentru a detecta când Bluetooth-ul a fost activat
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                        int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                        if (state == BluetoothAdapter.STATE_ON) {
                            Log.d(TAG, "Bluetooth activated");
                            context.unregisterReceiver(this);
                            ((Activity) context).runOnUiThread(() ->
                                    Toast.makeText(context, "Looking for device!", Toast.LENGTH_LONG).show()
                            );
                            connectToESP(); // Apelează după ce Bluetooth-ul este activat
                        }
                    }
                }
            };
            IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            activity.registerReceiver(receiver, filter);
        }
    }

    @SuppressLint("MissingPermission")
    public void disableBluetooth()
    {
        if (adapter.isEnabled())
        {
            adapter.disable();
            Log.d(TAG, "onDisable: Disabling bluetooth");
            Toast.makeText(context, "Bluetooth disabled", Toast.LENGTH_SHORT).show();
        }
            Log.d(TAG, "onEnable: already enabled");
    }

    //region Show devices list, maybe I will need it one day
    @SuppressLint("MissingPermission")
    public void showConnectedDevices(TextView textView)
    {
        if (isListVisible) {
            textView.setText("");
            Log.d(TAG, "hideList: List hidden");
            isListVisible = false;
        } else {
            StringBuilder sb = new StringBuilder();
            Set<BluetoothDevice> ad = adapter.getBondedDevices();
            for (BluetoothDevice temp : ad) {
                sb.append("\n").append(temp.getName()).append(" - ").append(temp.getAddress());
                Log.d(TAG, "showingList: List of devices");
            }
            Toast.makeText(context, "Device list", Toast.LENGTH_SHORT).show();
            textView.setText(sb.toString());
            isListVisible = true;
            Log.d(TAG, "showList: List displayed.");
        }
    }
    //endregion

    private boolean isConnectionActive() {
        return bluetoothSocket != null && bluetoothSocket.isConnected();
    }

    @SuppressLint("MissingPermission")
    public void connectToESP()
    {
        String targetDeviceName = "ESP32";
        final BluetoothDevice[] device = {null};

        if (adapter.isDiscovering())
        {
            Log.d(TAG, "connectToESP: Starts descovering");
            adapter.cancelDiscovery();
        }
        adapter.startDiscovery();

        BroadcastReceiver receiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context context, Intent intent)
            {
                String action = intent.getAction();

                if(progressBar != null)
                {
                    progressBar.setVisibility(View.VISIBLE);
                }
                if(textView !=null)
                    textView.setText("");

                if (BluetoothDevice.ACTION_FOUND.equals(action))
                {
                    BluetoothDevice foundDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (foundDevice != null && targetDeviceName.equals(foundDevice.getName()))
                    {
                        device[0] = foundDevice;
                        adapter.cancelDiscovery(); // Oprește descoperirea
                        context.unregisterReceiver(this); // Deregistrează receiver-ul
                        connectToDevice(device[0]); // Conectează-te la dispozitiv

                        if(progressBar != null)
                        {
                            progressBar.setVisibility(View.INVISIBLE);
                        }

                        if(textView != null)
                        {
                            textView.setText("Device connected" + "\n" + ":)");
                        }

                        if(startButton != null)
                        {
                            startButton.setVisibility(View.VISIBLE);
                        }

                        ((Activity) context).runOnUiThread(() ->
                            Toast.makeText(context, "Connected to " + foundDevice.getName(), Toast.LENGTH_LONG).show()
                        );
                    }
                }
                else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action))
                {
                    if (device[0] == null)
                    {
                        if(progressBar != null)
                        {
                            progressBar.setVisibility(View.INVISIBLE);
                        }

                        if(textView != null)
                        {
                            textView.setText("Device not found"  + "\n" + ":(");
                        }

                        if(startButton != null)
                        {
                            startButton.setVisibility(View.INVISIBLE);
                        }

                        ((Activity) context).runOnUiThread(() ->
                                Toast.makeText(context, "Device not found", Toast.LENGTH_LONG).show()
                        );
                        Log.d(TAG, "connectToESP: Device not found during discovery");
                    }
                    context.unregisterReceiver(this); // Deregistrează receiver-ul
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        context.registerReceiver(receiver, filter);
    }


    @SuppressLint("MissingPermission")
    private void connectToDevice(BluetoothDevice device) {
        try {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
            bluetoothSocket.connect();
            if (bluetoothSocket != null && bluetoothSocket.isConnected()) {
                inputStream = bluetoothSocket.getInputStream();
                isConnected = true; // Setează flag-ul de conexiune activă
                Log.d(TAG, "Connection established and inputStream initialized.");
            } else {
                Log.e(TAG, "Failed to initialize inputStream.");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error connecting to device: " + e.getMessage());
            try {
                bluetoothSocket.close();
            } catch (IOException closeException) {
                Log.e(TAG, "Error closing socket: " + closeException.getMessage());
            }
        }
    }

    public void disconnect()
    {
        try
        {
            if (bluetoothSocket != null)
            {
                bluetoothSocket.close();
                Log.d(TAG, "Disconnected");
            }
        }
        catch (IOException e)
        {
            Log.e(TAG, "Error closing connection: " + e.getMessage());
        }
    }

    public void readData(DataCallback callback)  {
        new Thread(() -> {

            StringBuilder dataBuilder = new StringBuilder();
            try {
                byte[] buffer = new byte[1024];
                int bytes;
                while ((bytes = inputStream.read(buffer)) != -1) {
                    String data = new String(buffer, 0, bytes);
                    dataBuilder.append(data);
                    handler.post(() -> {
                        if (callback != null) {
                            callback.onDataReceived(data);
                        }
                    });
                }
                if (callback != null) {
                    handler.post(() -> callback.onDataReceived(dataBuilder.toString()));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in readData(): " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    @Override
    public void onDataReceived(String data) {

    }

    public boolean isConnected() {
        return isConnected;
    }
}
