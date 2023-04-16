package com.example.offloadingmaster;

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

import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class MainActivity extends AppCompatActivity
{
    // States
    static final int SEARCHING = 1;
    static final int PAIRING = 2;
    static final int PAIRING_SUCCESS = 3;
    static final int PAIRING_FAIL = 4;
    static final int MESSAGE_DETECTED = 5;

    static int firstTime = 1;
    static int firstTime2 = 1;

    // 4x4 matrix multiplication
    public static final int MATRIX_DIM = 4;

    // Minimum requirements to pair slave device
    public static final int MIN_BAT = 20;
    public static final double MAX_DIST = 100;

    // Bluetooth socket required parameters
    private static final String APP_NAME = "OffloadingMaster";
    private static final UUID APP_UUID = UUID.fromString("4c27e22c-82e1-11ea-bc55-0242ac130003");

    // Bluetooth variables
    BluetoothAdapter bluetoothAdap;
    Intent bluetoothIntent;
    int EnableRequestCode;
    BluetoothDevice[] bluetoothDevices;
    HashMap<String, String> listOfDevices = new HashMap<>();
    BluetoothDevice btDevice;

    // Strings
    String matrixA, matrixB, matrixC, matrixD;
    String tempVar, deviceModel;
    String GPSlat, GPSlong;

    // Integers
    int currentPos = 0, pairingStat = 0, batLevel, count = 0;
    int[] matrixOne = new int[4];
    int[] matrixTwo = new int[4];
    int[] matrixThree = new int[4];
    int[] matrixFour = new int[4];

    // Variables to Attributes
    EditText editMat1Row1, editMat2Row1, editMat1Row2, editMat2Row2, editMat1Row3, editMat2Row3, editMat1Row4, editMat2Row4;
    Button bluetoothActivate, displayPaired, transferMatrix, compOnMaster, reqBat;
    TextView status, received, batteryToDisplay, displayConnectionStatus, slaveDistance, masterExecTime, locationDisplay;
    ListView listDevices;

    // Transfer variables
    TransferMsg transMessage;
    TransferMsg transMessageMat;
    TransferMsg reqBatI;
    TransferMsg ask;

    // Text views
    TextView textMat1, textMat2, textMat3, textMat4;
    TextView connectedDevices;
    TextView powerDisplayWODistributed;
    TextView powerDispDist;

    // Performance analysis variables
    private int initPower = 0;
    private int powerWDist = 0;
    private int powerWODist = 0;

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context ctxt, Intent intent)
        {
            batLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
        }
    };

    private void initializeByAttributes()
    {

        // EDIT TEXTS
        // Matrix 1 inputs
        editMat1Row1 = (EditText) findViewById(R.id.mat1);
        editMat1Row2 = (EditText) findViewById(R.id.mat3);
        editMat1Row3 = (EditText) findViewById(R.id.mat5);
        editMat1Row4 = (EditText) findViewById(R.id.mat7);

        // Matrix 2 inputs
        editMat2Row1 = (EditText) findViewById(R.id.mat2);
        editMat2Row2 = (EditText) findViewById(R.id.mat4);
        editMat2Row3 = (EditText) findViewById(R.id.mat6);
        editMat2Row4 = (EditText) findViewById(R.id.mat8);

        // BUTTONS
        bluetoothActivate = (Button) findViewById(R.id.bluetoothOn);
        displayPaired = (Button) findViewById(R.id.listPairedDev);
        transferMatrix = (Button) findViewById(R.id.slaveMatrix);
        reqBat = (Button) findViewById(R.id.getSlaveInfo);

        // TEXT VIEWS
        status = (TextView) findViewById(R.id.status);
        received = (TextView) findViewById(R.id.received);
        batteryToDisplay = (TextView) findViewById(R.id.batteryDisplay);
        locationDisplay = (TextView) findViewById(R.id.locationInfo);
        textMat1 = (TextView) findViewById(R.id.TV_Master);
        textMat2 = (TextView) findViewById(R.id.TV2);
        textMat3 = (TextView) findViewById(R.id.TV3);
        textMat4 = (TextView) findViewById(R.id.TV4);
        masterExecTime = (TextView) findViewById(R.id.execTimeText);
        displayConnectionStatus = (TextView) findViewById(R.id.connectionDisplay);
        slaveDistance = (TextView) findViewById(R.id.distance);
        connectedDevices = (TextView) findViewById(R.id.connectedDevices);

        // LIST VIEW
        listDevices = (ListView) findViewById(R.id.listDevices);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initializeByAttributes(); // connect to UI

        this.registerReceiver(this.broadcastReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        LocationManager locManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        initPower = calcPower();

        LocationListener locationListener = new LocationListener()
        {
            @Override
            public void onLocationChanged(Location location)
            {
                GPSlong = String.valueOf(location.getLongitude());
                GPSlat = String.valueOf(location.getLatitude());
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

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.INTERNET}, 10);
            }
        }

        locManager.requestLocationUpdates("gps", 5000, 0, locationListener);
        bluetoothAdap = BluetoothAdapter.getDefaultAdapter();
        bluetoothIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        EnableRequestCode = 1;

        if (!bluetoothAdap.disable())
        {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, EnableRequestCode);

        }

        bluetoothMethod();
        dispPairedDevices();
        listDevMethod();
        matTransfer();
        masterComp();
        reqInfo();
    }

    private int calcPower()
    {
        int val = 0;

        BatteryManager batManager = (BatteryManager) getApplicationContext().getSystemService(Context.BATTERY_SERVICE);
        if (batManager != null)
        {
            val = batManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
        }

        return val;
    }

    private void bluetoothMethod()
    {
        bluetoothActivate.setOnClickListener(v -> {
            if (bluetoothAdap == null)
            {
                Toast.makeText(getApplicationContext(), "BLUETOOTH UNAVAILABLE", Toast.LENGTH_LONG);
            }
            else if (!bluetoothAdap.isEnabled())
            {
                startActivityForResult(bluetoothIntent, EnableRequestCode);
            }
            else if (bluetoothAdap.isEnabled())
            {
                Toast.makeText(getApplicationContext(), "ALREADY ENABLED", Toast.LENGTH_LONG).show();
            }
        });

    }

    private void dispPairedDevices()
    {

        displayPaired.setOnClickListener(v -> {

            Set<BluetoothDevice> pairedDevices = bluetoothAdap.getBondedDevices();
            setPairedDevices(pairedDevices);
        });
    }

    private void listDevMethod()
    {
        listDevices.setOnItemClickListener((parent, view, pos, id) -> {

        currentPos = pos;
        btDevice = bluetoothDevices[pos];
        InnerClientClass innerClientClass = new InnerClientClass(bluetoothDevices[pos]);
        innerClientClass.start();
        status.setText("Connecting...");
    });
}

    public void matTransfer()
    {

        transferMatrix = (Button) findViewById(R.id.slaveMatrix);
        transferMatrix.setOnClickListener(v -> {

            System.out.println("We have clicked");
            String[] matrix1Row1Values = editMat1Row1.getText().toString().split(" ");
            String[] matrix1Row2Values = editMat1Row2.getText().toString().split(" ");
            String[] matrix1Row3Values = editMat1Row3.getText().toString().split(" ");
            String[] matrix1Row4Values = editMat1Row4.getText().toString().split(" ");

            String[] matrix2Row1Values = editMat2Row1.getText().toString().split(" ");
            String[] matrix2Row2Values = editMat2Row2.getText().toString().split(" ");
            String[] matrix2Row3Values = editMat2Row3.getText().toString().split(" ");
            String[] matrix2Row4Values = editMat2Row4.getText().toString().split(" ");

            switch (count)
            {
                case 0:
                    matrixA = matrix1Row1Values[0] + " " + matrix1Row1Values[1] + " " + matrix1Row2Values[0] + " " + matrix1Row2Values[1];
                    matrixB = matrix1Row1Values[2] + " " + matrix1Row1Values[3] + " " + matrix1Row2Values[2] + " " + matrix1Row2Values[3];
                    matrixC = matrix2Row1Values[0] + " " + matrix2Row1Values[1] + " " + matrix2Row2Values[0] + " " + matrix2Row2Values[1];
                    matrixD = matrix2Row3Values[0] + " " + matrix2Row3Values[1] + " " + matrix2Row4Values[0] + " " + matrix2Row4Values[1];
                    break;

                case 1:
                    matrixA = matrix1Row1Values[0] + " " + matrix1Row1Values[1] + " " + matrix1Row2Values[0] + " " + matrix1Row2Values[1];
                    matrixB = matrix1Row1Values[2] + " " + matrix1Row1Values[3] + " " + matrix1Row2Values[2] + " " + matrix1Row2Values[3];
                    matrixC = matrix2Row1Values[2] + " " + matrix2Row1Values[3] + " " + matrix2Row2Values[2] + " " + matrix2Row2Values[3];
                    matrixD = matrix2Row3Values[2] + " " + matrix2Row3Values[3] + " " + matrix2Row4Values[2] + " " + matrix2Row4Values[3];
                    break;

                case 2:
                    matrixA = matrix1Row3Values[0] + " " + matrix1Row3Values[1] + " " + matrix1Row4Values[0] + " " + matrix1Row4Values[1];
                    matrixB = matrix1Row3Values[2] + " " + matrix1Row3Values[3] + " " + matrix1Row4Values[2] + " " + matrix1Row4Values[3];
                    matrixC = matrix2Row1Values[0] + " " + matrix2Row1Values[1] + " " + matrix2Row2Values[0] + " " + matrix2Row2Values[1];
                    matrixD = matrix2Row3Values[0] + " " + matrix2Row3Values[1] + " " + matrix2Row4Values[0] + " " + matrix2Row4Values[1];
                    break;

                case 3:
                    matrixA = matrix1Row3Values[0] + " " + matrix1Row3Values[1] + " " + matrix1Row4Values[0] + " " + matrix1Row4Values[1];
                    matrixB = matrix1Row3Values[2] + " " + matrix1Row3Values[3] + " " + matrix1Row4Values[2] + " " + matrix1Row4Values[3];
                    matrixC = matrix2Row1Values[2] + " " + matrix2Row1Values[3] + " " + matrix2Row2Values[2] + " " + matrix2Row2Values[3];
                    matrixD = matrix2Row3Values[2] + " " + matrix2Row3Values[3] + " " + matrix2Row4Values[2] + " " + matrix2Row4Values[3];
                    break;

                default:
                    Toast.makeText(getApplicationContext(), "Matrix Computation Success!!", Toast.LENGTH_LONG).show();
                    editMat1Row1.setText("");
                    editMat2Row1.setText("");
                    editMat1Row2.setText("");
                    editMat2Row2.setText("");
                    editMat1Row3.setText("");
                    editMat2Row3.setText("");
                    editMat1Row4.setText("");
                    editMat2Row4.setText("");
                    break;
            }

            if (count < MATRIX_DIM)
            {
                String jsonString ="{ \"matrix_A\" : \""+ matrixA +"\",\"matrix_B\" :\""+ matrixB +"\",\"matrix_C\":\""+ matrixC +"\",\"matrix_D\":\""+ matrixD +"\"}";
                transMessageMat.write(jsonString.getBytes());
            }
            else
            {
                count = 0;
                for (int iterator = 0; iterator < MATRIX_DIM; iterator++)
                {

                    matrixOne[iterator] = 0;
                    matrixTwo[iterator] = 0;
                    matrixThree[iterator] = 0;
                    matrixFour[iterator] = 0;
                }
            }

            powerWDist = calcPower();
            powerDispDist = (TextView) findViewById(R.id.powDist);
            double changePower = (double) Math.abs(powerWDist - initPower) / 1000;
            String dispPower = "Power Consumed with Distributed Computation: " + String.valueOf(changePower) + " mAH";
            powerDispDist.setText(dispPower);
        });
    }

    private void masterComp()
    {

        compOnMaster = (Button) findViewById(R.id.matMaster);
        compOnMaster.setOnClickListener(view -> {
            long startTime = System.nanoTime();

            String[] matrix1Row1Values = editMat1Row1.getText().toString().split(" ");
            String[] matrix1Row2Values = editMat1Row2.getText().toString().split(" ");
            String[] matrix1Row3Values = editMat1Row3.getText().toString().split(" ");
            String[] matrix1Row4Values = editMat1Row4.getText().toString().split(" ");

            String[] matrix2Row1Values = editMat2Row1.getText().toString().split(" ");
            String[] matrix2Row2Values = editMat2Row2.getText().toString().split(" ");
            String[] matrix2Row3Values = editMat2Row3.getText().toString().split(" ");
            String[] matrix2Row4Values = editMat2Row4.getText().toString().split(" ");

            int[] ETVal1 = new int[4];
            int[] ETVal2 = new int[4];
            int[] ETVal3 = new int[4];
            int[] ETVal4 = new int[4];
            int[] ETVal5 = new int[4];
            int[] ETVal6 = new int[4];
            int[] ETVal7 = new int[4];
            int[] ETVal8 = new int[4];

            for (int i = 0; i < MATRIX_DIM; i++)
            {
                ETVal1[i] = Integer.parseInt(matrix1Row1Values[i]);
                ETVal2[i] = Integer.parseInt(matrix2Row1Values[i]);
                ETVal3[i] = Integer.parseInt(matrix1Row2Values[i]);
                ETVal4[i] = Integer.parseInt(matrix2Row2Values[i]);
                ETVal5[i] = Integer.parseInt(matrix1Row3Values[i]);
                ETVal6[i] = Integer.parseInt(matrix2Row3Values[i]);
                ETVal7[i] = Integer.parseInt(matrix1Row4Values[i]);
                ETVal8[i] = Integer.parseInt(matrix2Row4Values[i]);
            }

            int[][] arrOne = new int[MATRIX_DIM][MATRIX_DIM];
            int[][] arrTwo = new int[MATRIX_DIM][MATRIX_DIM];

            int[][] result = new int[MATRIX_DIM][MATRIX_DIM];

            for (int i = 0; i < MATRIX_DIM; i++)
            {
                for (int j = 0; j < MATRIX_DIM; j++)
                {
                    if (i == 0)
                    {
                        arrOne[i][j] = ETVal1[j];
                    }
                    else if (i == 1)
                    {
                        arrOne[i][j] = ETVal3[j];
                    }
                    else if (i == 2)
                    {
                        arrOne[i][j] = ETVal5[j];
                    }
                    else
                    {
                        arrOne[i][j] = ETVal7[j];
                    }
                }
            }

            for (int i = 0; i < MATRIX_DIM; i++)
            {
                for (int j = 0; j < MATRIX_DIM; j++)
                {
                    if (i == 0)
                    {
                        arrTwo[i][j] = ETVal2[j];
                    }
                    else if (i == 1)
                    {
                        arrTwo[i][j] = ETVal4[j];
                    }
                    else if (i == 2)
                    {
                        arrTwo[i][j] = ETVal6[j];
                    }
                    else
                    {
                        arrTwo[i][j] = ETVal8[j];
                    }
                }
            }

            for (int i = 0; i < MATRIX_DIM; i++)
            {
                for (int j = 0; j < MATRIX_DIM; j++)
                {
                    result[i][j] = 0;
                    for (int k = 0; k < MATRIX_DIM; k++)
                    {
                        result[i][j] = result[i][j] + arrOne[i][k] * arrTwo[k][j];
                    }
                }
            }

            for (int i = 0; i < MATRIX_DIM; i++)
            {
                String sentArr = "";
                for (int j = 0; j < MATRIX_DIM; j++)
                {
                    sentArr = sentArr.concat(String.valueOf(result[i][j]) + " ");
                }
                if (i == 0)
                {
                    textMat1.setText(sentArr);
                }
                else if (i == 1)
                {
                    textMat2.setText(sentArr);
                }
                else if (i == 2)
                {
                    textMat3.setText(sentArr);
                }
                else
                {
                    textMat4.setText(sentArr);
                }
            }

            long endTime = System.nanoTime();
            long s = (endTime - startTime)/1000;
            masterExecTime.setText("Computation Time by Master: " + String.valueOf(s) + " micro seconds");


            powerWODist = calcPower();
            powerDisplayWODistributed = (TextView) findViewById(R.id.power_wo);
            double deltaPower = (double)Math.abs(powerWODist - initPower)/1000;
            String displayPower = "Power Consumed without Distributed Computation: " + String.valueOf(deltaPower) + " mAH";
            powerDisplayWODistributed.setText(displayPower);
        }
        );
    }


    public void reqInfo()
    {
        reqBat.setOnClickListener(v -> {
            tempVar = "0";
            String jsonString ="{ \"fromReqBat\" : \""+ tempVar +"\"}";
            reqBatI.write(jsonString.getBytes());
        });
    }

    public String convertToString(int[] arr)
    {
        String tempStr = "";
        for (int value : arr) {
            if (value != 0) {
                tempStr = tempStr.concat(String.valueOf(value) + " ");
            }
        }
        return tempStr;
    }

    Handler handler = new Handler(new Handler.Callback()
    {
        @Override
        public boolean handleMessage(@NonNull Message msg)
        {
            switch (msg.what) {

                case SEARCHING:
                    status.setText("LISTENING");
                    break;

                case PAIRING:
                    status.setText("CONNECTING");
                    break;

                case PAIRING_SUCCESS:
                    status.setText("CONNECTED DEVICES:");

                    Thread t = new Thread()
                    {
                        @Override
                        public void run()
                        {
                            while (!isInterrupted())
                            {
                                try {
                                    String jsonString = "{\"status\":\"0\"}";
                                    transMessage.write(jsonString.getBytes());
                                    Thread.sleep(1000);  //1000ms = 1 sec
                                    runOnUiThread(() -> {
                                        if (pairingStat == 1)
                                        {
                                            String displaySetTxt = deviceModel + " - Slave";
                                            displayConnectionStatus.setText(displaySetTxt);
                                            pairingStat = 0;
                                        } else {
                                            String displaySetTxt = deviceModel + " is NOT able to connect";
                                            displayConnectionStatus.setText(displaySetTxt);
                                            displayConnectionStatus.setTextColor(Color.parseColor("#FF0000"));
                                        }
                                    });
                                } catch (InterruptedException e)
                                {
                                    e.printStackTrace();
                                }
                            }
                        }
                    };

                    t.start();
                    break;

                case PAIRING_FAIL:
                    String statusSetTxt = "The Connection has failed.";
                    status.setText(statusSetTxt);
                    break;

                case MESSAGE_DETECTED:
                    byte[] readBuff = (byte[]) msg.obj;
                    String tempMsg = new String(readBuff, 0, msg.arg1);
                    JsonObject jsonObject = new JsonParser().parse(tempMsg).getAsJsonObject();


                    if (jsonObject.has("viewStat") && jsonObject.has("phoneID"))
                    {

                        pairingStat = 1;
                        deviceModel = jsonObject.get("phoneID").getAsString();
                    }

                    if (jsonObject.has("message"))
                    {

                        String msgs = jsonObject.get("message").getAsString();
                        String receivedPlusMsgs = received.getText() + msgs;
                        received.setText(receivedPlusMsgs);
                    }

                    //gets the slave info
                    if(jsonObject.has("toReqBat"))
                    {
                        deviceModel = jsonObject.get("phoneID").getAsString();
                        String batLev = jsonObject.get("toReqBat").getAsString();
                        String latSlave = jsonObject.get("lat").getAsString();
                        String lonSlave = jsonObject.get("lon").getAsString();
                        batteryToDisplay.setText(batLev+" %");
                        locationDisplay.setText(latSlave + ", " + lonSlave);

                        double coordX1 = Double.parseDouble(GPSlat);
                        double coordY1 = Double.parseDouble(GPSlong);
                        double coordX2 = Double.parseDouble(latSlave);
                        double coordY2 = Double.parseDouble(lonSlave);
                        double slaveDis = 1000 * distance(coordX1, coordY1, coordX2, coordY2);

                        slaveDistance.setText(String.format("%.1f", slaveDis)+" m");

                        if(Integer.parseInt(batLev) > MIN_BAT && slaveDis < MAX_DIST)
                        {
                            if(firstTime == 1)
                            {
                                Toast.makeText(MainActivity.this, "Slave - "+ deviceModel +" satisfies MIN battery level (20)", Toast.LENGTH_LONG).show();
                                firstTime = 0;
                            }

                        }
                        else
                        {
                            Toast.makeText(MainActivity.this, "Slave - "+ deviceModel +" doesn't satisfy MIN battery level (20)", Toast.LENGTH_LONG).show();
                            firstTime = 0;
                        }

                        StringBuilder data = new StringBuilder();
                        String temp = batLev + "; " + slaveDis + "m; " + latSlave + " & " + lonSlave;
                        listOfDevices.put(deviceModel, String.valueOf(temp));

                        for (String name : listOfDevices.keySet())
                        {
                            String val = listOfDevices.get(name);
                            data.append(name).append(" : ").append(val).append("\n");
                        }

                        String fileName = "Slave_info";
                        File folder = new File(getApplicationContext().getExternalFilesDir(null).getAbsolutePath());
                        File file = new File(folder, fileName + ".txt");
                        System.out.println("FILE exists here:" + file.getAbsolutePath());
                        FileOutputStream outStream = null;
                        try
                        {
                            outStream = new FileOutputStream(file);
                        }
                        catch (FileNotFoundException e)
                        {
                            e.printStackTrace();
                        }
                        try
                        {
                            outStream.write(data.toString().getBytes());
                            if(firstTime2 == 1)
                            {
                                Toast.makeText(getApplicationContext(), "Slave Information added to file: " + fileName + ".txt", Toast.LENGTH_SHORT).show();
                                firstTime2 = 0;
                            }
                        }
                        catch (IOException e)
                        {
                            e.printStackTrace();
                        }
                        finally
                        {
                            try
                            {
                                outStream.close();
                            }
                            catch (IOException e)
                            {
                                e.printStackTrace();
                            }
                        }

                    }

                    if (jsonObject.has("result"))
                    {

                        String res = jsonObject.get("result").getAsString();
                        String deviceID = jsonObject.get("phoneID").getAsString();


                        String receiveSetText = "The resultant matrix from " + deviceID +": "+ res;
                        received.setText(receiveSetText);

                        switch (count)
                        {
                            case 0:
                                textMat1.setText("");
                                textMat2.setText("");
                                textMat3.setText("");
                                textMat4.setText("");

                                String[] tk = res.split(" ");

                                matrixOne[0] = Integer.parseInt(tk[0]);
                                matrixOne[1] = Integer.parseInt(tk[1]);
                                matrixTwo[0] = Integer.parseInt(tk[2]);
                                matrixTwo[1] = Integer.parseInt(tk[3]);

                                String arrOneStr = convertToString(matrixOne);
                                String arrTwoStr = convertToString(matrixTwo);

                                textMat1.setText(arrOneStr);
                                textMat2.setText(arrTwoStr);
                                break;

                            case 1:
                                String[] tk1 = res.split(" ");

                                matrixOne[2] = Integer.parseInt(tk1[0]);
                                matrixOne[3] = Integer.parseInt(tk1[1]);
                                matrixTwo[2] = Integer.parseInt(tk1[2]);
                                matrixTwo[3] = Integer.parseInt(tk1[3]);

                                String arrOneStrFull = convertToString(matrixOne);
                                String arrTwoStrFull = convertToString(matrixTwo);

                                textMat1.setText(arrOneStrFull);
                                textMat2.setText(arrTwoStrFull);
                                break;

                            case 2:
                                String[] tk2 = res.split(" ");

                                matrixThree[0] = Integer.parseInt(tk2[0]);
                                matrixThree[1] = Integer.parseInt(tk2[1]);
                                matrixFour[0] = Integer.parseInt(tk2[2]);
                                matrixFour[1] = Integer.parseInt(tk2[3]);

                                String arrThreeStr = convertToString(matrixThree);
                                String arrFourStr = convertToString(matrixFour);

                                textMat3.setText(arrThreeStr);
                                textMat4.setText(arrFourStr);
                                break;

                            case 3:
                                String[] tk3 = res.split(" ");

                                matrixThree[2] = Integer.parseInt(tk3[0]);
                                matrixThree[3] = Integer.parseInt(tk3[1]);
                                matrixFour[2] = Integer.parseInt(tk3[2]);
                                matrixFour[3] = Integer.parseInt(tk3[3]);

                                String arrThreeStrFull = convertToString(matrixThree);
                                String arrFourStrFull = convertToString(matrixFour);

                                textMat3.setText(arrThreeStrFull);
                                textMat4.setText(arrFourStrFull);
                                break;
                        }
                        count++;
                    }
                    break;
            }
            return true;
        }
    });

    private double distance(double lat1, double long1, double lat2, double long2)
    {

        double change = long1 - long2;
        double distance = Math.sin(degToRad(lat1)) * Math.sin(degToRad(lat2)) + Math.cos(degToRad(lat1)) * Math.cos(degToRad(lat2)) * Math.cos(degToRad(change));

        //distance = radToDeg(Math.acos(distance)) * 60 * 1.1515;

        distance = ((Math.acos(distance) * 180.0) / Math.PI) * 60 * 1.1515;

        return (distance);
    }

    private double degToRad(double degree)
    {
        return (degree * Math.PI / 180.0);
    }

    private class InnerClientClass extends Thread
    {

        private BluetoothSocket socket;

        public InnerClientClass(BluetoothDevice dev)
        {
            try
            {
                socket = dev.createInsecureRfcommSocketToServiceRecord(APP_UUID);
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }

        public void run() {
            try {
                socket.connect();
                Message message = Message.obtain();
                message.what = PAIRING_SUCCESS;
                handler.sendMessage(message);

                transMessage = new TransferMsg(socket);
                transMessage.start();

                transMessageMat = new TransferMsg(socket);
                transMessageMat.start();

                reqBatI = new TransferMsg(socket);
                reqBatI.start();
            }
            catch (IOException e)
            {
                e.printStackTrace();
                Message message = Message.obtain();
                message.what = PAIRING_FAIL;
                handler.sendMessage(message);
            }
        }
    }

    class TransferMsg extends Thread
    {

        private final InputStream inStream;
        private final OutputStream outStream;

        public TransferMsg(BluetoothSocket socket)
        {
            InputStream localInStream = null;
            OutputStream localOutStream = null;
            try
            {
                localInStream = socket.getInputStream();
                localOutStream = socket.getOutputStream();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }

            inStream = localInStream;
            outStream = localOutStream;
        }

        public void run() {
            byte[] buff = new byte[1024];
            int inBytes;

            while (true)
            {
                try
                {
                    inBytes = inStream.read(buff);
                    handler.obtainMessage(MESSAGE_DETECTED, inBytes, -1, buff).sendToTarget();
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }

        public void write(byte[] bytes)
        {
            try {
                outStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void setPairedDevices(Set<BluetoothDevice> pairedDevices)
    {

        int index = 0;
        String[] strings = new String[pairedDevices.size()];
        if (pairedDevices.size() > 0)
        {
            bluetoothDevices = new BluetoothDevice[pairedDevices.size()];
            for (BluetoothDevice device : pairedDevices)
            {
                bluetoothDevices[index] = device;
                strings[index] = device.getName();
                index++;
            }

            ArrayAdapter<String> devArray = new ArrayAdapter<>(getApplicationContext(), android.R.layout.simple_list_item_1, strings);
            listDevices.setAdapter(devArray);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == EnableRequestCode)
        {
            if (resultCode == RESULT_OK)
            {
                Toast.makeText(getApplicationContext(), "Bluetooth has been ENABLED", Toast.LENGTH_LONG).show();
            }
        } else if (resultCode == RESULT_CANCELED)
        {
            Toast.makeText(getApplicationContext(), "Bluetooth has been DISABLED", Toast.LENGTH_LONG).show();
        }
    }
}