package zw.co.tonytogara.keepusane;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity
{

    /* Local UI */
    private TextView mLocalTimeView;
    /* Bluetooth API */
    private BluetoothManager mBluetoothManager;
    BluetoothAdapter btAdapter;

    private static final int REQUEST_ENABLE_BT = 1;
    TextView mTextView, mDisconnectedTV;
    BluetoothDevice mDevice;
    BluetoothSocket mmSocket;
    OutputStream mmOutputStream;

    private final int REQ_CODE = 100;
    TextView textView;

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // widgets
        mTextView = (TextView)findViewById(R.id.connectedDevices);
        mDisconnectedTV = (TextView)findViewById(R.id.disconnectedTV);

        // Application should not go to sleep
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Getting the Bluetooth adapter
        btAdapter = BluetoothAdapter.getDefaultAdapter();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
        {
            mBluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        }

        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();

        // We can't continue without proper Bluetooth support
        if (!checkBluetoothSupport(bluetoothAdapter))
        {
            finish();
        }

        // Register for system Bluetooth events
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mBluetoothReceiver, filter);
        if (!bluetoothAdapter.isEnabled())
        {
            Log.d("SYSTEM_STATUS", "Bluetooth is currently disabled...enabling");
            bluetoothAdapter.enable();
        }
        else
            {
            Log.d("SYSTEM_STATUS", "Bluetooth enabled...starting services");
        }

        // check if the device is connected and show status
        checkBluetoothDeviceConnectivityState();

        textView = findViewById(R.id.text);
        ImageView speak = findViewById(R.id.speak);
        speak.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
                intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Need to speak");
                try {
                    startActivityForResult(intent, REQ_CODE);
                } catch (ActivityNotFoundException a) {
                    Toast.makeText(getApplicationContext(),
                            "Sorry your device not supported",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case R.id.action_view_devices:

                String message = "";
                mTextView.setText("");

                if(btAdapter==null)
                {
                    Log.d("SYSTEM_STATUS", "Bluetooth NOT supported. Aborting.");
                    message = "Device is not supported";
                } else {
                    if (btAdapter.isEnabled())
                    {
                        Log.d("SYSTEM_STATUS", "Bluetooth is enabled...");

                        // Listing paired devices
                        mTextView.append("Paired Devices are:");
                        Set<BluetoothDevice> devices = btAdapter.getBondedDevices();
                        for (BluetoothDevice device : devices) {
//                            mTextView.append("\n->" + device.getName() + ", " + device);
                            mTextView.append("\n->" + device.getName());
                        }

                        message = mTextView.getText().toString();
                    }
                }

                showMessage("Devices", message);
                break;
            case R.id.action_reconnect_to_device:
                checkBluetoothDeviceConnectivityState();
                break;
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQ_CODE:
                {
                if (resultCode == RESULT_OK && data != null)
                {
                    ArrayList result = data
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    textView.setText(result.get(0).toString());

                    // send the speech to the device
                    sendTextToDevice(result.get(0).toString());
                }
                break;
            }
        }
    }

    // check bluetooth device connectivity
    private void checkBluetoothDeviceConnectivityState()
    {
        // Checks for the Bluetooth support and then makes sure it is turned on
        // If it isn't turned on, request to turn it on
        // List paired devices
        if(btAdapter==null)
        {
            Log.d("SYSTEM_STATUS", "Bluetooth NOT supported. Aborting.");
            return;
        } else {
            if (btAdapter.isEnabled())
            {
                Log.d("SYSTEM_STATUS", "Bluetooth is enabled...");

                // Listing paired devices
                mTextView.append("\nPaired Devices are:");
                Set<BluetoothDevice> devices = btAdapter.getBondedDevices();
                for (BluetoothDevice device : devices)
                {
                    mTextView.append("\n  Device: " + device.getName() + ", " + device);
                }

                if(devices.size() > 0)
                {
                    for(BluetoothDevice device : devices)
                    {
                        if(device.getName().startsWith("HC-"))
                        {
                            mDevice = device;
                            Toast.makeText(getBaseContext(), "Device Connected", Toast.LENGTH_LONG).show();

                            Log.d("ArduinoBT", "findBT found device named " + device.getName());
                            Log.d("ArduinoBT", "device address is " + device.getAddress());
                            Log.d("ArduinoBT", "UUIDS " + device.getUuids()[0]);
                            Log.d("ArduinoBT", "UUIDS_SIZE " + device.getUuids().length);

                            // retrieve the accurate connected device UUID
//                            ParcelUuid[] idArray = device.getUuids();
//                            java.util.UUID uuid = null;
//
//                            for (int x = 0; x < idArray.length; x++)
//                            {
//                                java.util.UUID uuidYouCanUse = java.util.UUID.fromString(idArray[x].toString());
//
//                                try {
//                                    mmSocket = device.createRfcommSocketToServiceRecord(uuidYouCanUse);
//                                } catch (IOException e) {
//                                    Toast.makeText(getBaseContext(), "S", Toast.LENGTH_SHORT).show();
//                                }
//
//                                try {
//                                    mmSocket.connect();
//                                    uuid = uuidYouCanUse;
//                                    break;
//                                } catch (IOException e)
//                                {
//
//                                    Log.d("SYSTEM_STATUS", "ERROR_" + e.getLocalizedMessage());
//
//                                }
//                            }


//                            UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"); //Standard SerialPortService ID
//                            UUID uuid = UUID.fromString("UUIDS 00001101-0000-1000-8000-00805f9b34fb"); //Standard SerialPortService ID

                            UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
                            try {
                                mmSocket = device.createRfcommSocketToServiceRecord(uuid);
                            } catch (IOException e) {
                                Log.e("SYSTEM_STATUS", e.toString());
                            }
                            try {
                                mmSocket.connect();
                                Log.d("SYSTEM_STATUS", "socket connected");
                                Toast.makeText(getApplicationContext(), "Connected", Toast.LENGTH_SHORT).show();

                                if (mmSocket.isConnected())
                                {
                                    // device is connected
                                    mDisconnectedTV.setText("Connected");
                                    mDisconnectedTV.setTextColor(Color.GREEN);

                                    Log.d("SYSTEM_STATUS", " socket Connected");
                                    Toast.makeText(getApplicationContext(), "Connected", Toast.LENGTH_SHORT).show();
                                }else
                                {
                                    Log.d("SYSTEM_STATUS", "Socket Not Connected");
                                    Toast.makeText(getApplicationContext(), "Socket Not Connected", Toast.LENGTH_SHORT).show();
                                }
                            } catch (IOException e)
                            {
                                try
                                {
                                    // Failed to connect
                                    Log.d("SYSTEM_STATUS", "trying fallback...");

                                    mDisconnectedTV.setText("Disconnected");
                                    mDisconnectedTV.setTextColor(Color.RED);

                                } catch (Exception e2)
                                {
                                    if (!mmSocket.isConnected())
                                    {
                                        Log.e("SYSTEM_STATUS", "Couldn't establish Bluetooth connection!");
                                        finish();
                                    }
                                }
                            }
                            break;
                        }else
                        {
                            mDisconnectedTV.setText("Disconnected");
                            mDisconnectedTV.setTextColor(Color.RED);
                        }
                    }
                }
            } else
            {
                //Prompt user to turn on Bluetooth
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
    }

    private void sendTextToDevice(String speech)
    {
        String s = speech;

        Log.d("SYSTEM_STATUS", "NOW ATTEMPTING TO WRITE");

        try {
            mmOutputStream = mmSocket.getOutputStream();
            Log.d("TAG", "got output stream");
        } catch (IOException ex) {
            Log.d("SYSTEM_STATUS", ex.toString());
        }

        try {
            // sending data to bluetooth
            mmOutputStream.write(s.getBytes());
            Log.d("SYSTEM_STATUS", "wrote value " + s + " on serial out");
        } catch (IOException ex) {
            Log.d("SYSTEM_STATUS", ex.toString());
        }
    }

    // send speech to bluetooth device
    private void checkBluetoothState(String speech)
    {
        // Checks for the Bluetooth support and then makes sure it is turned on
        // If it isn't turned on, request to turn it on
        // List paired devices
        if(btAdapter==null)
        {
            Log.d("SYSTEM_STATUS", "Bluetooth NOT supported. Aborting.");
            return;
        } else {
            if (btAdapter.isEnabled())
            {
                Log.d("SYSTEM_STATUS", "Bluetooth is enabled...");

                // Listing paired devices
                mTextView.append("\nPaired Devices are:");
                Set<BluetoothDevice> devices = btAdapter.getBondedDevices();
                for (BluetoothDevice device : devices)
                {
                    mTextView.append("\n  Device: " + device.getName() + ", " + device);
                }

                if(devices.size() > 0)
                {
                    for(BluetoothDevice device : devices)
                    {
                        if(device.getName().startsWith("HC-"))
                        {
                            mDevice = device;
                            Toast.makeText(getBaseContext(), "Device Connected", Toast.LENGTH_LONG).show();

                            Log.d("ArduinoBT", "findBT found device named " + device.getName());
                            Log.d("ArduinoBT", "device address is " + device.getAddress());
                            Log.d("ArduinoBT", "UUIDS " + device.getUuids()[0]);
                            Log.d("ArduinoBT", "UUIDS_SIZE " + device.getUuids().length);

                            // retrieve the accurate connected device UUID
//                            ParcelUuid[] idArray = device.getUuids();
//                            java.util.UUID uuid = null;
//
//                            for (int x = 0; x < idArray.length; x++)
//                            {
//                                java.util.UUID uuidYouCanUse = java.util.UUID.fromString(idArray[x].toString());
//
//                                try {
//                                    mmSocket = device.createRfcommSocketToServiceRecord(uuidYouCanUse);
//                                } catch (IOException e) {
//                                    Toast.makeText(getBaseContext(), "S", Toast.LENGTH_SHORT).show();
//                                }
//
//                                try {
//                                    mmSocket.connect();
//                                    uuid = uuidYouCanUse;
//                                    break;
//                                } catch (IOException e)
//                                {
//
//                                    Log.d("SYSTEM_STATUS", "ERROR_" + e.getLocalizedMessage());
//
//                                }
//                            }


//                            UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"); //Standard SerialPortService ID
//                            UUID uuid = UUID.fromString("UUIDS 00001101-0000-1000-8000-00805f9b34fb"); //Standard SerialPortService ID

                            UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
                            try {
                                mmSocket = device.createRfcommSocketToServiceRecord(uuid);
                            } catch (IOException e) {
                                Log.e("SYSTEM_STATUS", e.toString());
                            }
                            try {
                                mmSocket.connect();
                                Log.d("SYSTEM_STATUS", "socket connected");
                                Toast.makeText(getApplicationContext(), "Connected", Toast.LENGTH_SHORT).show();

                                if (mmSocket.isConnected())
                                {
                                    // device is connected
                                    mDisconnectedTV.setText("Connected");
                                    mDisconnectedTV.setTextColor(Color.GREEN);

                                    Log.d("SYSTEM_STATUS", " socket Connected");
                                    Toast.makeText(getApplicationContext(), "Connected", Toast.LENGTH_SHORT).show();
                                }else
                                {
                                    Log.d("SYSTEM_STATUS", "Socket Not Connected");
                                    Toast.makeText(getApplicationContext(), "Socket Not Connected", Toast.LENGTH_SHORT).show();
                                }

//                                    createSocket("Hi Tony");
                                String s = speech;

                                Log.d("SYSTEM_STATUS", "NOW ATTEMPTING TO WRITE");

                                try {
                                    mmOutputStream = mmSocket.getOutputStream();
                                    Log.d("TAG", "got output stream");
                                } catch (IOException ex) {
                                    Log.d("SYSTEM_STATUS", ex.toString());
                                }

                                try {
                                    // sending data to bluetooth
                                    mmOutputStream.write(s.getBytes());
                                    Log.d("SYSTEM_STATUS", "wrote value " + s + " on serial out");
                                } catch (IOException ex) {
                                    Log.d("SYSTEM_STATUS", ex.toString());
                                }

                            } catch (IOException e)
                            {
                                try
                                {
                                    // Failed to connect
                                    Log.d("SYSTEM_STATUS", "trying fallback...");

                                    // show load page
                                    Snackbar.make(
                                            findViewById(android.R.id.content),
                                            "Failed to connect to unit",
                                            Snackbar.LENGTH_INDEFINITE)
                                            .setAction("Reconnect", new View.OnClickListener()
                                            {
                                                @Override
                                                public void onClick(View v)
                                                {
                                                    // check if location has been found
                                                    sendTextToDevice(speech);
                                                }
                                            }).show();

                                } catch (Exception e2)
                                {
                                    if (!mmSocket.isConnected())
                                    {
                                        Log.e("SYSTEM_STATUS", "Couldn't establish Bluetooth connection!");
                                        finish();
                                    }
                                }
                            }
                            break;
                        }else
                            {
                                mDisconnectedTV.setText("Disconnected");
                                mDisconnectedTV.setTextColor(Color.RED);
                            }
                    }
                }
            } else
                {
                    //Prompt user to turn on Bluetooth
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
    }

    /**
     * Verify the level of Bluetooth support provided by the hardware.
     * @param bluetoothAdapter System {@link BluetoothAdapter}.
     * @return true if Bluetooth is properly supported, false otherwise.
     */
    private boolean checkBluetoothSupport(BluetoothAdapter bluetoothAdapter) {

        if (bluetoothAdapter == null) {
            Log.w("SYSTEM_STATUS", "Bluetooth is not supported");
            return false;
        }

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
        {
            Log.w("SYSTEM_STATUS", "Bluetooth LE is not supported");
            return false;
        }

        return true;
    }

    /**
     * Listens for Bluetooth adapter events to enable/disable
     * advertising and server functionality.
     */
    private BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);

            switch (state)
            {
                case BluetoothAdapter.STATE_ON:
                    Toast.makeText(getBaseContext(), "Bluetooth TURNED ON", Toast.LENGTH_LONG).show();
                    break;
                case BluetoothAdapter.STATE_OFF:
                    Toast.makeText(getBaseContext(), "Bluetooth TURNED OFF", Toast.LENGTH_LONG).show();
                    break;
                default:
                    // Do nothing
            }
        }
    };

    // show message dialog
    public void showMessage(String title, String message)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setCancelable(false);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.show();
    }
}