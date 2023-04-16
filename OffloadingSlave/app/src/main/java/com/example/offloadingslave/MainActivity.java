package com.example.offloadingslave;

import android.Manifest;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;

import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import android.provider.Settings;

import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class MainActivity extends AppCompatActivity
{
    TextView conStat, msgRec, batteryDisp, compExecTime;
    ListView listDevices;
    Button bluetoothActivate, searchBtn;
    SendReceive sendRec, sendRecStat, batInfo, calc, allInfo;

    // States
    static final int SEARCHING = 1;
    static final int PAIRING = 2;
    static final int PAIRING_SUCCESS = 3;
    static final int PAIRING_FAIL = 4;
    static final int MESSAGE_DETECTED = 5;

    // Bluetooth socket required parameters
    private static final UUID MY_UUID = UUID.fromString("4c27e22c-82e1-11ea-bc55-0242ac130003");
    private static final String APP_NAME = "OffloadingSlave";

    // Bluetooth variables
    BluetoothDevice bluetoothDevID;
    BluetoothAdapter bluetoothAdap;
    BluetoothDevice[] bluetoothArr;
    Intent bluetoothIntent;
    String bluetoothName;

    String GPSlong, GPSlat;
    int myPos = 0, requestCodeForEnable, batLevel, clickTracker = 0;

    // Performance analysis attributes
    long execTime = 0;
    double initPower = 0 ;
    double finalPower = 0;
    double totalPower = 0;


    private final BroadcastReceiver batteryInformationReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            int percentage = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            batLevel = percentage;
            batteryDisp.setText(String.valueOf(percentage) + "%");
        }
    };

    private void initializeByAttributes()
    {
        // Buttons
        bluetoothActivate = (Button)findViewById(R.id.bluetoothOn);
        searchBtn = (Button)findViewById(R.id.listen);

        // List
        listDevices = (ListView)findViewById(R.id.listDevices);

        // Text Views
        batteryDisp = (TextView)findViewById(R.id.batteryDisplay);
        compExecTime = (TextView)findViewById(R.id.timer);
        conStat = (TextView)findViewById(R.id.status);
        msgRec = (TextView)findViewById(R.id.received);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initializeByAttributes(); // connect to UI

        this.registerReceiver(this.batteryInformationReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        LocationManager locManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        LocationListener listener = new LocationListener()
        {
            @Override
            public void onLocationChanged(Location location)
            {
                GPSlong = String.valueOf(location.getLongitude());
                GPSlat = String.valueOf(location.getLatitude());

                if (clickTracker == 1)
                {
                    String jsonString = "{\"phoneID\":\"" + bluetoothName + "\",\"toReqBat\":\"" + batLevel + "\",\"lat\":\"" + GPSlat + "\",\"lon\":\"" + GPSlong + "\"}";
                    allInfo.write(jsonString.getBytes());
                }
            }

            @Override
            public void onStatusChanged(String s, int i, Bundle bundle)
            {

            }

            @Override
            public void onProviderEnabled(String s)
            {

            }

            @Override
            public void onProviderDisabled(String s)
            {
                Intent i = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivity(i);
            }
        };

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.INTERNET}, 10);
            }
        }

        locManager.requestLocationUpdates("gps", 5000, 0, listener);

        initPower = calcPower();

        bluetoothAdap = BluetoothAdapter.getDefaultAdapter();
        bluetoothIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        requestCodeForEnable = 1;

        if(!bluetoothAdap.disable())
        {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent,requestCodeForEnable);

        }

        bluetoothActivate.setOnClickListener(v -> {
            if (bluetoothAdap == null)
            {
                Toast.makeText(getApplicationContext(), "BT UNAV", Toast.LENGTH_LONG).show();
            }
            else if (!bluetoothAdap.isEnabled())
            {
                startActivityForResult(bluetoothIntent,requestCodeForEnable);
            }
            else if(bluetoothAdap.isEnabled())
            {
                Toast.makeText(MainActivity.this, "ALREADY ENABLED", Toast.LENGTH_LONG).show();
            }
        });

        searchBtn.setOnClickListener(v -> {
            ServerClass serverclass = new ServerClass();
            serverclass.start();
        });

        System.out.println("Before on list click");
        listDevMethod();
    }


    public int[][] MatrixMultiplication(String numOne, String numTwo)
    {
        String[] partOne = numOne.split(" ");
        int[][] finalResOne = new int[partOne.length/2][partOne.length/2];

        int i = 0;
        for(int x = 0; x < partOne.length/2; x++)
        {
            for(int y = 0; y < partOne.length/2; y++)
            {
                finalResOne[x][y] = Integer.parseInt(partOne[i]);
                i++;
            }
        }

        String[] partTwo = numTwo.split(" ");
        int[][] finalResTwo = new int[partTwo.length/2][partTwo.length/2];

        int j = 0;
        for(int x = 0; x < partTwo.length/2; x++)
        {
            for(int y = 0; y < partTwo.length/2; y++)
            {
                finalResTwo[x][y] = Integer.parseInt(partTwo[j]);
                j++;
            }
        }

        int[][] finalRes = new int[partOne.length/2][partOne.length/2];

        for(int x = 0; x < partOne.length/2; x++)
        {
            for(int y = 0; y < partOne.length/2; y++)
            {
                finalRes[x][y]= 0;
                for(int z = 0; z < partOne.length/2; z++)
                {
                    finalRes[x][y]= finalRes[x][y]+finalResOne[x][z]*finalResTwo[z][y];
                }
            }
        }

        finalPower = calcPower();
        totalPower = (finalPower - initPower) / 1000;

        TextView powNow = (TextView) findViewById(R.id.powerNow);
        powNow.setText("Power Consumption "+ String.valueOf(totalPower) + " mAh");
        return finalRes;
    }

    Handler handler = new Handler(new Handler.Callback()
    {
        @Override
        public boolean handleMessage(@NonNull Message msg)
        {
            switch (msg.what)
            {

                case SEARCHING:
                    conStat.setText("LISTENING");
                    break;
                case PAIRING:
                    conStat.setText("CONNECTING");
                    break;
                case PAIRING_SUCCESS:
                    conStat.setText("CONNECTED");
                    break;
                case PAIRING_FAIL:
                    conStat.setText("CONNECTION FAILED");
                    break;
                case MESSAGE_DETECTED:
                    byte[] readBuff = (byte [])msg.obj;
                    String tempMsg = new String(readBuff,0,msg.arg1);

                    JsonObject jsonObject = new JsonParser().parse(tempMsg).getAsJsonObject();

                    bluetoothName = Settings.Secure.getString(getContentResolver(), "bluetooth_name");

                    if(jsonObject.has("status"))
                    {
                        String jsonString ="{\"phoneID\":\""+ bluetoothName +"\",\"viewStat\":\""+String.valueOf(1)+"\"}";
                        sendRecStat.write(jsonString.getBytes());
                    }


                    if(jsonObject.has("matrix_A")&&jsonObject.has("matrix_B")&&jsonObject.has("matrix_C")&&jsonObject.has("matrix_D"))
                    {
                        msgRec.setText("Row1 of Matrix-1: " +jsonObject.get("matrix_A").getAsString()+"\n"+"Row2 of Matrix-1: " +jsonObject.get("matrix_B").getAsString()+"\n"+"Col1 of Matrix2: " +jsonObject.get("matrix_C").getAsString()+"\n"+"Col2 of Matrix-2: " +jsonObject.get("matrix_D").getAsString());

                        long startTime = System.nanoTime();

                        String numbers =jsonObject.get("matrix_A").getAsString();

                        String numbers1 =jsonObject.get("matrix_C").getAsString();

                        String numbers2 =jsonObject.get("matrix_B").getAsString();

                        String numbers3 =jsonObject.get("matrix_D").getAsString();

                        int[][] resultOutput1 = MatrixMultiplication(numbers,numbers1);

                        int[][] resultOutput2 = MatrixMultiplication(numbers2,numbers3);

                        int[][] finalResult = new int[2][2];

                        for(int i = 0; i < 2;i++)
                        {
                            for(int j = 0; j < 2;j++)
                            {
                                finalResult[i][j] = resultOutput1[i][j] + resultOutput2[i][j];
                            }
                        }

                        String sentArray = "";
                        for(int a = 0; a < 2; a++)
                        {
                            for(int b = 0; b < 2; b ++)
                            {
                                sentArray = sentArray.concat(String.valueOf(finalResult[a][b]));
                                if(a!=1 || b!=1)
                                {
                                    sentArray = sentArray.concat(" ");
                                }
                            }
                        }

                        long endTime = System.nanoTime();
                        execTime = (endTime - startTime) / 1000;
                        compExecTime.setText(compExecTime.getText()+"\n"+"Computation Time: "+ execTime +"micro seconds");

                        String jsonString ="{\"phoneID\":\""+ bluetoothName +"\",\"result\":\""+sentArray+"\"}";
                        sendRec.write(jsonString.getBytes());
                    }


                    if(jsonObject.has("fromReqBat"))
                    {
                        clickTracker = 1;
                        String jsonString ="{\"phoneID\":\""+ bluetoothName +"\",\"toReqBat\":\""+ batLevel +"\",\"lat\":\""+ GPSlat +"\",\"lon\":\""+ GPSlong +"\"}";

                        batInfo.write(jsonString.getBytes());
                    }


                    if(jsonObject.has("message"))
                    {
                        msgRec.setText(msgRec.getText()+ jsonObject.get("message").getAsString());

                    }

                    break;

            }

            return true;
        }
    });


    private class ServerClass extends Thread
    {
        private BluetoothServerSocket servSock;
        public ServerClass()
        {
            try
            {
                servSock = bluetoothAdap.listenUsingInsecureRfcommWithServiceRecord(APP_NAME, MY_UUID);
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }

        public void run()
        {
            BluetoothSocket sock = null;
            while (sock == null)
            {

                try
                {
                    Message msg = Message.obtain();
                    msg.what = PAIRING;
                    handler.sendMessage(msg);
                    sock = servSock.accept();
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                    Message msg = Message.obtain();
                    msg.what= PAIRING_FAIL;
                    handler.sendMessage(msg);
                }

                if(sock != null)
                {
                    Message msg = Message.obtain();
                    msg.what= PAIRING_SUCCESS;
                    handler.sendMessage(msg);

                    allInfo = new SendReceive(sock);
                    allInfo.start();

                    sendRec = new SendReceive(sock);
                    sendRec.start();

                    batInfo = new SendReceive(sock);
                    batInfo.start();

                    calc = new SendReceive(sock);
                    calc.start();

                    sendRecStat =new SendReceive(sock);
                    sendRecStat.start();

                    break;

                }

            }


        }
    }

    private class ClientClass extends Thread
    {
        private  BluetoothSocket sock;

        public ClientClass(BluetoothDevice device1)
        {
            try
            {
                sock = device1.createInsecureRfcommSocketToServiceRecord(MY_UUID);
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }

        }
        public void  run()
        {
            try
            {
                sock.connect();
                Message msg = Message.obtain();
                msg.what = PAIRING_SUCCESS;
                handler.sendMessage(msg);

                allInfo = new SendReceive(sock);
                allInfo.start();

                sendRec = new SendReceive(sock);
                sendRec.start();

                batInfo = new SendReceive(sock);
                batInfo.start();

            }
            catch (IOException e)
            {
                e.printStackTrace();
                Message msg = Message.obtain();
                msg.what = PAIRING_FAIL;
                handler.sendMessage(msg);
            }
        }
    }

    private class  SendReceive extends Thread{

        private  final InputStream inStream;
        private  final OutputStream outStream;

        public  SendReceive(BluetoothSocket socket)
        {
            InputStream in = null;
            OutputStream out = null;
            try
            {
                in = socket.getInputStream();
                out = socket.getOutputStream();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }

            inStream = in;
            outStream = out;
        }

        public void run()
        {
            byte[] buff = new byte[1024];
            int inBytes;

            while (true)
            {
                try
                {
                    inBytes = inStream.read(buff);
                    handler.obtainMessage(MESSAGE_DETECTED,inBytes,-1,buff).sendToTarget();
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }

        public void write(byte[] bytes)
        {
            try
            {
                outStream.write(bytes);
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    private void listDevMethod()
    {

        listDevices.setOnItemClickListener((parent, view, pos, id) -> {
            System.out.println(pos + "position");
            System.out.println("List Devices on list click");

            myPos = pos;
            System.out.println("----c"+ bluetoothArr[pos]);
            bluetoothDevID = bluetoothArr[pos];

            ClientClass clientclass = new ClientClass(bluetoothArr[pos]);
            clientclass.start();
            conStat.setText("Connecting...");
        });

    }

    private double calcPower()
    {

        int val = 0;
        BatteryManager batManager = (BatteryManager) getApplicationContext().getSystemService(Context.BATTERY_SERVICE);
        if (batManager != null)
        {
            val = batManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
        }
        return val;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == requestCodeForEnable)
        {
            if (resultCode == RESULT_OK)
            {
                Toast.makeText(getApplicationContext(), "Bluetooth has been ENABLED", Toast.LENGTH_LONG).show();
            }
        }
        else if (resultCode == RESULT_CANCELED)
        {
            Toast.makeText(getApplicationContext(), "Bluetooth has been DISABLED", Toast.LENGTH_LONG).show();
        }
    }
}