package com.example.checker_status;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import android.view.View;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.checker_status.databinding.ActivityMainBinding;

import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

interface ICallback
{
    void callback(String msg);
}

class UDPServer {
    private DatagramSocket udpSocket;
    private int port;
    private Boolean isRunning = false;
    private ICallback callback = null;

    public UDPServer(int port, ICallback callback) {
        this.port = port;
        this.callback = callback;
    }

    public void start()
    {
        try {
            udpSocket = new DatagramSocket(this.port, InetAddress.getByName("0.0.0.0"));
            udpSocket.setSoTimeout(5000);
        } catch (IOException e) {
            // Nothing to do
        }
        isRunning = true;

        Runnable runnable = new Runnable(){
            public void run() {
                String msg = "";

                while (isRunning) {

                    byte[] buf = new byte[256000];
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);

                    // blocks until a packet is received
                    try {
                        udpSocket.receive(packet);
                        callback.callback(new String(packet.getData()));
                    } catch (IOException e) {
                        // Nothing to do
                    }
                }
            }
        };

        Thread thread = new Thread(runnable);
        thread.start();
    }

    public void stop()
    {
        isRunning = false;
    }
}

public class MainActivity extends AppCompatActivity implements ICallback {

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;
    private int PORT = 0;
    private String PSK = "";
    private String SEPARATOR = ";";
    private UDPServer client;
    private String msg = "";

    public void callback(String msg)
    {
        this.msg = msg;
    }

    private void parseConfig(String config)
    {
        if (config.length() > 0) {
            String[] subs = config.split(SEPARATOR);
            if (subs.length == 2) {
                PORT = Integer.parseInt(subs[0].trim());
                PSK = subs[1];
            }
        }
    }

    private void writeToFile(String data, Context context) {
        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(context.openFileOutput("config.txt", Context.MODE_PRIVATE));
            outputStreamWriter.write(data);
            outputStreamWriter.close();
        }
        catch (IOException e)
        {
            // Nothing to do
        }
    }

    private String readFromFile(Context context) {

        String ret = "";

        try {
            InputStream inputStream = context.openFileInput("config.txt");

            if ( inputStream != null ) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String receiveString = "";
                StringBuilder stringBuilder = new StringBuilder();

                while ( (receiveString = bufferedReader.readLine()) != null ) {
                    stringBuilder.append("\n").append(receiveString);
                }

                inputStream.close();
                ret = stringBuilder.toString();
            }
        }
        catch (IOException e)
        {
            // Nothing to do
        }

        return ret;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        binding.fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {

                    Intent intent = new Intent("com.google.zxing.client.android.SCAN");
                    intent.putExtra("SCAN_MODE", "QR_CODE_MODE"); // "PRODUCT_MODE for bar codes

                    startActivityForResult(intent, 0);

                } catch (Exception e) {

                    Uri marketUri = Uri.parse("market://details?id=com.google.zxing.client.android");
                    Intent marketIntent = new Intent(Intent.ACTION_VIEW,marketUri);
                    startActivity(marketIntent);

                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 0) {

            if (resultCode == RESULT_OK) {
                String contents = data.getStringExtra("SCAN_RESULT");
                writeToFile(contents, this);
                parseConfig(contents);
            }
            if(resultCode == RESULT_CANCELED){
                //handle cancel
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }

    @Override
    protected void onStart() {
        super.onStart();
        String contents = readFromFile(this);
        parseConfig(contents);

        client = new UDPServer(PORT, this);
        client.start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        client.stop();
    }
}