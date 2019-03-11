package com.example.ds.internetprotocols_bleapp;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.HandlerThread;
import android.os.Message;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.util.UUID;


public class MainActivity extends AppCompatActivity implements SensorEventListener{
    private final static int REQUEST_ENABLE_BT = 1;
    private static final String TAG = "MainActivity";
    private ConnectedThread connThread;
    private Handler callbackHandler; // Our main handler that will receive callback notifications
    private Handler toastHandler; //Toast text handler
    private ArrayAdapter<String> mBTArrayAdapter;

    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private BluetoothSocket mBTSocket = null; // bi-directional client-to-client data path
    private final static int MESSAGE_READ = 2; //
    private final static int CONNECTING_STATUS = 3; // used in bluetooth handler to identify message status

    BluetoothDevice device;
    BluetoothAdapter bAdapter;
    private SensorManager sensorManager;
    private Sensor accelerometer;

    private Button button;
    private TextView readStatus;
    private EditText macAddress;

    TextView xCoor; //X axis object
    TextView yCoor; //Y axis object
    TextView zCoor; //Z axis object

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        button = (Button) findViewById(R.id.button);
        readStatus = (TextView) findViewById(R.id.readStatus);
        macAddress = (EditText) findViewById(R.id.editText);
        xCoor=(TextView)findViewById(R.id.xCoor);
        yCoor=(TextView)findViewById(R.id.yCoor);
        zCoor=(TextView)findViewById(R.id.zCoor);

        //initialize Accelerometer sensor
        sensorManager=(SensorManager)getSystemService(SENSOR_SERVICE);
        // add listener. The listener will be  (this) class
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);


        //initialize Location
        ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1001);

        //initialize Bluetooth
        final BluetoothManager bManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bAdapter = bManager.getAdapter();


        //enable Bluetooth
        if (bAdapter == null || !bAdapter.isEnabled()) {
            Intent enableBIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBIntent, REQUEST_ENABLE_BT);
        }
        callbackHandler = new Handler(new Handler.Callback(){
            public boolean handleMessage(android.os.Message msg){
                if(msg.what == MESSAGE_READ){
                    String readMessage = null;
                    try {
                        readMessage = new String((byte[]) msg.obj, "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                    readStatus.setText(readMessage);
                }
                return true;
            }
        });
        toastHandler = new Handler(new Handler.Callback(){
            public boolean handleMessage(android.os.Message msg){
                runOnUiThread(new Runnable() {
                    public void run() {
                        Toast toast = Toast.makeText(MainActivity.this,"Invalid MAC Address",Toast.LENGTH_LONG);
                        toast.setGravity(Gravity.CENTER|Gravity.CENTER_HORIZONTAL,0,0);
                        toast.show();
                    }
                });
                return true;
            }
        });

        // Get the device MAC address, which is the last 17 chars in the View
        final String address = macAddress.getText().toString();
        // Spawn a new thread to avoid blocking the GUI one

        button.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View v){
                final String address = macAddress.getText().toString();

                new Thread()
                {
                    public void run() {
                        boolean fail = false;
                        try{
                            device = bAdapter.getRemoteDevice(address);
                        }
                        catch(IllegalArgumentException | IllegalStateException e){
                            Message msg = toastHandler.obtainMessage(CONNECTING_STATUS, "Invalid MAC Address");
                            msg.sendToTarget();
                            return;
                        }

                        try {
                            mBTSocket = createBluetoothSocket(device);
                        } catch (IOException e) {
                            fail = true;
                            Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_SHORT).show();
                        }
                        // Establish the Bluetooth socket connection.
                        try {
                            mBTSocket.connect();
                        } catch (IOException e) {
                            try {
                                fail = true;
                                mBTSocket.close();
                                callbackHandler.obtainMessage(CONNECTING_STATUS, -1, -1)
                                        .sendToTarget();
                            } catch (IOException e2) {
                                //insert code to deal with this
                                Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_SHORT).show();
                            }
                        }
                        if(fail == false) {
                            connThread = new ConnectedThread(mBTSocket);
                            connThread.start();

                            callbackHandler.obtainMessage(CONNECTING_STATUS, 1, -1, "ewe")
                                    .sendToTarget();
                        }
                    }
                }.start();

                if(connThread != null){
                    connThread.write("hi there");
                }
            }
        });

    }
    public void onAccuracyChanged(Sensor sensor,int accuracy){

    }
    public void onSensorChanged(SensorEvent event){

        // check sensor type
        if(event.sensor.getType()==Sensor.TYPE_ACCELEROMETER){
            // assign directions
            float x=event.values[0];
            float y=event.values[1];
            float z=event.values[2];

            //displaying for testing purposes
            xCoor.setText("X: "+x);
            yCoor.setText("Y: "+y);
            zCoor.setText("Z: "+z);

            if (connThread != null){ //if Bluetooth connection is alive, we write to the remote party
                connThread.write(x + "," + y + "," + z);
            }
        }
    }
    final BroadcastReceiver blReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(BluetoothDevice.ACTION_FOUND.equals(action)){
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // add the name to the list
                mBTArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                mBTArrayAdapter.notifyDataSetChanged();
            }
        }
    };

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        try {
            final Method m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", UUID.class);
            return (BluetoothSocket) m.invoke(device, BTMODULEUUID);
        } catch (Exception e) {
            Log.e(TAG, "Could not create Insecure RFComm Connection",e);
        }
        return  device.createRfcommSocketToServiceRecord(BTMODULEUUID);
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];  // buffer store for the stream
            int bytes; // bytes returned from read()
            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.available();
                    if(bytes != 0) {
                        buffer = new byte[1024];
                        SystemClock.sleep(100); //pause and wait for rest of data. Adjust this depending on your sending speed.
                        bytes = mmInStream.available(); // how many bytes are ready to be read?
                        bytes = mmInStream.read(buffer, 0, bytes); // record how many bytes we actually read
                        callbackHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer)
                                .sendToTarget(); // Send the obtained bytes to the UI activity
                    }
                } catch (IOException e) {
                    e.printStackTrace();

                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(String input) {
            byte[] bytes = input.getBytes();           //converts entered String into bytes
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) { }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }

    }


}

