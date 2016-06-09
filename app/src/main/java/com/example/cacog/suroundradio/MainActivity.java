package com.example.cacog.suroundradio;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.media.AudioManager;
import android.net.Uri;
import android.net.rtp.AudioCodec;
import android.net.rtp.AudioGroup;
import android.net.rtp.AudioStream;
import android.net.rtp.RtpStream;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;

//Socket START/////////////////////////////////////////////////////////////////////////////
//Socket END/////////////////////////////////////////////////////////////////////////////


public class MainActivity extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener, SensorEventListener {

    TextView myLa;
    TextView myLo;
    TextView remoteLa;
    TextView remoteLo;
    TextView myDirection;

    GoogleApiClient mGoogleApiClient;
    GoogleApiClient client;
    Location mLastLocation;



    SensorManager mSensorManager;
    Sensor accelerometer;
    Sensor magnetometer;
    float[] mGravity;
    float[] mGeomagnetic;
    float orientation[] = new float[3];
    Kalman kalman = new Kalman();

    TextView textViewPort;
    TextView textViewIP;
    EditText editTextIP;
    EditText editTextPort;
    private AudioStream audioStream;
    private AudioGroup audioGroup;
    int count=0;

    //Socket START/////////////////////////////////////////////////////////////////////////////
    long socketInterval=0;
    boolean isConnected = false;
    ServerSocket serverSocket=null;
    Socket socket = null;
    Handler updateHandler=null;
    Thread serverThread=null;
    PrintWriter out;
    double remoteLat=0;
    double remoteLog=0;
    boolean remoteChange = false;
    static final int SERVERPORT = 5000;
    static int command = 0;

    //Socket END////////////////////////////////////////!/////////////////////////////////////

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        //Location START////////////////////////////////////////////////////////////////////////////
        myLa=(TextView) findViewById(R.id.myLa);
        myLo=(TextView) findViewById(R.id.myLo);

        buildGoogleApiClient();

        if (mGoogleApiClient != null) {
            mGoogleApiClient.connect();
        } else
            Toast.makeText(this, "Not connected...", Toast.LENGTH_SHORT).show();


        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();


        //Location END////////////////////////////////////////////////////////////////////////////


        //Orientation START////////////////////////////////////////////////////////////////////////////
        myDirection =(TextView) findViewById(R.id.myDirection) ;

        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        for(int i=0;i<3;i++)
        {
            orientation[i]=0;
        }
        kalman.set(0.98);

        //Orientation END////////////////////////////////////////////////////////////////////////////

        //VOID START/////////////////////////////////////////////////////////////////////////////////
        textViewPort = (TextView) findViewById(R.id.textViewPort);
        textViewIP =(TextView) findViewById(R.id.textViewIP);
        editTextIP =(EditText) findViewById(R.id.editTextIP);
        editTextPort = (EditText) findViewById(R.id.editTextPort);
        textViewIP.setText(getLocalServerIp());
        try {
            audioGroup = new AudioGroup();
            audioGroup.setMode(AudioGroup.MODE_NORMAL);
            audioStream = new AudioStream(InetAddress.getByName(getLocalServerIp()));
            audioStream.setCodec(AudioCodec.PCMU);
            audioStream.setMode(RtpStream.MODE_NORMAL);
            audioStream.associate(InetAddress.getByAddress(new byte[]{(byte) 172, (byte) 30, (byte) 1, (byte) 53}),47736);

//            audioStream.join(audioGroup);

            AudioManager Audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            Audio.setMode(AudioManager.MODE_IN_COMMUNICATION);
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        //VOID END/////////////////////////////////////////////////////////////////////////////////

        //Socket START/////////////////////////////////////////////////////////////////////////////
        remoteLa = (TextView) findViewById(R.id.remoteLa);
        remoteLo = (TextView) findViewById(R.id.remoteLo);
        updateHandler = new Handler();

        //Socket END/////////////////////////////////////////////////////////////////////////////




    }


    //Location and Orientation START////////////////////////////////////////////////////////////////////////////

    class Kalman {
        private double p=1;
        public  float calc (float a, float b)
        {
            float result = (float)(p*a + (1-p) * b  +((Math.abs(a-b)>Math.PI)?((a>b)?((1-p)*Math.PI*2):((p-1)*Math.PI*2)):(0)) );
            if(result>Math.PI) return (float)(result-2*Math.PI);
            if(result<-Math.PI) return (float)(result+2*Math.PI);
            else return result;

        }
        public  float calc (float a, float b, boolean half)
        {
            float result = (float)(p*a + (1-p) * b  +((Math.abs(a-b)>Math.PI/2)?((a>b)?((1-p)*Math.PI):((p-1)*Math.PI)):(0)) );
            if(result>Math.PI/2) return (float)(result-Math.PI);
            if(result<-Math.PI/2) return (float)(result+Math.PI);
            else return result;

        }
        public void set( double p)
        {
            if (p>1) return;
            if(p<0) return;
            this.p=p;
        }

    }

    @Override
    public void onConnectionFailed(ConnectionResult arg0) {
        Toast.makeText(this, "Failed to connect...", Toast.LENGTH_SHORT).show();

    }

    @Override
    public void onConnected(Bundle arg0) {
        LocationRequest locationRequest = new LocationRequest().setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY).setInterval(1000);

        LocationCallback locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                super.onLocationResult(result);
            }

            @Override
            public void onLocationAvailability(LocationAvailability locationAvailability) {
                super.onLocationAvailability(locationAvailability);
                mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
                if (mLastLocation != null) {
                    myLa.setText(String.valueOf(mLastLocation.getLatitude()));
                    myLo.setText(String.valueOf(mLastLocation.getLongitude()));
                }
            }
        };


        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient,locationRequest,this);



    }


    @Override
    public void onLocationChanged(Location location) {
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        if (mLastLocation != null) {
            myLa.setText(String.valueOf(mLastLocation.getLatitude()));
            myLo.setText(String.valueOf(mLastLocation.getLongitude()));
            if(isConnected){
                out.println("-1");
                out.println(mLastLocation.getLatitude());
                out.println("-2");
                out.println(mLastLocation.getLongitude());
            }
        }
    }

    @Override
    public void onConnectionSuspended(int arg0) {
        Toast.makeText(this, "Connection suspended...", Toast.LENGTH_SHORT).show();

    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    //Location END////////////////////////////////////////////////////////////////////////////

    //Orientation START////////////////////////////////////////////////////////////////////////////

    public void onStart() {
        super.onStart();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client.connect();
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Main Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-groundsdddfenerated app URL is correct.
                Uri.parse("android-app://com.example.cacog.suroundradio/http/host/path")
        );
        AppIndex.AppIndexApi.start(client, viewAction);
    }

    @Override
    public void onStop() {
        super.onStop();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Main Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app URL is correct.
                Uri.parse("android-app://com.example.cacog.suroundradio/http/host/path")
        );
        AppIndex.AppIndexApi.end(client, viewAction);
        client.disconnect();

        if(serverSocket != null){
            try {
                serverSocket.close();
            } catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
            mGravity = event.values;
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
            mGeomagnetic = event.values;
        if (mGravity != null && mGeomagnetic != null) {
            float R[] = new float[9];
            float I[] = new float[9];
            boolean success = SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic);
            if (success) {
                float nOrientation[] = new float[3];
                SensorManager.getOrientation(R, nOrientation);

                for(int i=0;i<3;i+=2)
                {
                    orientation[i]=kalman.calc(orientation[i],nOrientation[i]);

                }
                orientation[1]=kalman.calc(orientation[1],nOrientation[1],true);

//                float nOrientation =SensorManager.getInclination(I);

                myDirection.setText(String.valueOf(orientation[0]*180.0f/Math.PI));
            }
        }
        if(remoteChange){
            remoteChange=false;
            remoteLa.setText(String.valueOf(remoteLat));
            remoteLo.setText(String.valueOf(remoteLog));

        }
        if(count++>=100){
            count =0;
            
        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
    }

    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }

    //Orientation END////////////////////////////////////////////////////////////////////////////

    //VOID START/////////////////////////////////////////////////////////////////////////////////
    private String getLocalServerIp() //http://theeye.pe.kr/archives/1501
    {
        try
        {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();)
            {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();)
                {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && !inetAddress.isLinkLocalAddress() && inetAddress.isSiteLocalAddress())
                    {
                        return inetAddress.getHostAddress().toString();
                    }
                }
            }
        }
        catch (SocketException ex) {}
        return null;
    }

    void buttonHost(View v)
    {
        audioStream.join(audioGroup);
        textViewPort.setText(Integer.toString(audioStream.getLocalPort()));

        serverThread = new Thread(new ServerThread());
        serverThread.start();

    }

    void buttonGuest(View v)
    {
        InetAddress remoteIP=null;
        Log.d("socket","guest button");
        String remotePort=editTextPort.getText().toString();
        try {
            remoteIP=InetAddress.getByName(editTextIP.getText().toString());
        } catch (Exception e){
            e.printStackTrace();
        }
        if(!remotePort.isEmpty()){
            socketInterval = 1000;
            audioStream.associate(remoteIP, Integer.valueOf(remotePort));
            audioStream.join(audioGroup);
        }

        new Thread(new ClientThread(remoteIP)).start();
        Log.d("socket","guest button end");


    }
    //VOID END/////////////////////////////////////////////////////////////////////////////////

    //Socket START/////////////////////////////////////////////////////////////////////////////
    class ServerThread implements Runnable{
        public void run(){
            try{
                serverSocket = new ServerSocket(SERVERPORT);
            } catch (IOException e) {
                e.printStackTrace();
            }
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    socket = serverSocket.accept();
                    Log.d("socket","check socket connected");
                    while(!socket.isConnected());
                    Log.d("socket","socket is connected");

                    new Thread( new CommunicationThread()).start();
                } catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }
    }

    class ClientThread implements Runnable {
        InetAddress serverAddress;
        public ClientThread(InetAddress serverAddress){
            this.serverAddress =serverAddress;
        }
        public void run() {
            try{
                socket=new Socket(serverAddress,SERVERPORT);
                Log.d("socket","try connect");
                while(!socket.isConnected());
                Log.d("socket","connected");
                new Thread( new CommunicationThread()).start();
            } catch (Exception e){
                e.printStackTrace();
            }

        }
    }

    class CommunicationThread implements Runnable {
        BufferedReader input;
        public CommunicationThread() {
            isConnected=true;
            Log.d("socket","communication start");
            try {
                this.input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream())),
                        true);
             } catch (IOException e) {
                e.printStackTrace();
                Log.d("socket","IOexception");
            }
            out.println("-1");
            out.println("0");
            out.println("-2");
            out.println("0");
            Log.d("socket","data sent");
        }
        public void run() {
            Log.d("socket","communication run start");
            while (!Thread.currentThread().isInterrupted()){
                try {
                    Log.d("socket","communication run");
                    String read = input.readLine();

                    Log.d("socket",read);
                    new inputHandling(read).run();
                    Log.d("socket","handler end");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try{
                    Thread.currentThread().sleep(1000,0);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            Log.d("socket","communication run end");
        }
    }
    class inputHandling implements Runnable {
        String string;
        String value;

        public inputHandling(String string) {
            Log.d("socket","handler start");
            this.string =string;
        }
        public void run(){
            Log.d("socket","handler run");
            if(command!=0)
            {
                Log.d("socket","command is set");

                Log.d("socket","case 2 excute command");
                value=string;
                commandHandling(command,value);

            } else {
                Log.d("socket","command is not set");
                if(Integer.valueOf(string)<0)
                {
                    Log.d("socket","case 3 set command");
                    command =Integer.valueOf(string);
                }
            }
        }
        private void commandHandling(int c, String value)
        {
            Log.d("socket","start command handle");
            switch (c){
                case -1:
                    Log.d("socket","setLat");
                    Log.d("socket",value);

                    remoteLat=Double.valueOf(value);
                    remoteChange=true;
                    command=0;
                    break;
                case -2:
                    Log.d("socket","setLog");
                    Log.d("socket",value);

                    remoteLog=Double.valueOf(value);
                    remoteChange=true;
                    command=0;
                    break;
                default:
                    Log.d("socket","wrong command");
                    command=0;
                    break;
            }
        }
    }
    //Socket END/////////////////////////////////////////////////////////////////////////////

    public void sendData(View v){

        out.println("-1");
        Log.d("socket","data sent");
    }
    public int volumeCalc(double ear, double angle){
        double ang=(ear>angle)?ear-angle:angle-ear;
        if (ang>180)
            ang = 360-ang;
        return (int)((double)100 -ang/2);
    }
}


