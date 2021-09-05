package com.example.checker_status;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import android.text.TextPaint;
import android.util.Base64;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

interface ICallback
{
    void callback(String msg);
}

class UDPServer {
    private DatagramSocket udpSocket = null;
    private Boolean isRunning = false;
    private ICallback callback = null;
    private int port = 0;
    Thread thread = null;

    public UDPServer() {
    }

    public void setCallback(ICallback callback) {
        this.callback = callback;
    }

    public void start(int port)
    {
        if (isRunning)
        {
            stop();
        }
        this.port = port;
        isRunning = true;

        Runnable runnable = new Runnable(){
            public void run() {
                try {
                    String msg = "";
                    udpSocket = new DatagramSocket(port, InetAddress.getByName("0.0.0.0"));
                    udpSocket.setSoTimeout(5000);

                    while (isRunning) {
                        try {
                            byte[] buf = new byte[1000000];
                            DatagramPacket packet = new DatagramPacket(buf, buf.length);
                            udpSocket.receive(packet);
                            if (callback != null) {
                                callback.callback(new String(packet.getData()));
                            }
                        }
                        catch (Exception e)
                        {
                            // Nothing to do
                        }
                    }
                }
                catch (Exception e)
                {
                    // Nothing to do
                }
            }
        };

        thread = new Thread(runnable);
        thread.start();
    }

    public void stop()
    {
        isRunning = false;
        try {
            thread.join();
        } catch (InterruptedException e) {
            // nothing to do
        }
    }
}

class DrawView extends View {

    private ArrayList<MainActivity.checker> checkers = null;
    private String lastCheckDate = "Unknown";
    private Paint paint = new Paint();

    public void setData(String lastCheckDate, List<MainActivity.checker> checkers)
    {
        this.lastCheckDate = lastCheckDate;
        this.checkers = (ArrayList<MainActivity.checker>) checkers;
        this.invalidate();
    }

    public DrawView(Context context) {
        super(context);
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        paint.setColor(Color.WHITE);
        paint.setTextSize(100);
        canvas.drawText(lastCheckDate, 10, 200, paint);
    }

}

public class MainActivity extends AppCompatActivity implements ICallback {

    private ActivityMainBinding binding;
    private int PORT = 0;
    private String SEPARATOR = ";";
    private UDPServer client = new UDPServer();
    private String msg = "";
    private String lastCheckDate = "";
    private DrawView drawView;

    public class checker
    {
        public String label;
        public Boolean isGreen;
    }

    private ArrayList<checker> checkers = new ArrayList<checker>();

    void parseMsg(String msg)
    {
        checkers.clear();
        if (msg.length() > 0)
        {
            String[] subs = msg.split(SEPARATOR);
            lastCheckDate = subs[0];
            for(int i = 0; i < (subs.length - 1) / 2; i++)
            {
                checker temp = new checker();
                temp.label = subs[(i * 2) + 1];
                temp.isGreen = (subs[(i * 2) + 2] == "1");
                checkers.add(temp);
            }
        }
    }

    public void callback(String msg)
    {
        this.msg = msg;
        parseMsg(msg);
        drawView.setData(lastCheckDate, checkers);
    }

    private void parseConfig(String config)
    {
        if (config.length() > 0) {
            PORT = Integer.parseInt(config.trim());
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

            if (inputStream != null) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String receiveString = "";
                StringBuilder stringBuilder = new StringBuilder();

                while ((receiveString = bufferedReader.readLine()) != null) {
                    stringBuilder.append("\n").append(receiveString);
                }

                inputStream.close();
                ret = stringBuilder.toString();
            }
        } catch (IOException e) {
            // Nothing to do
        }

        return ret;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        drawView = new DrawView(this);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        binding.getRoot().setBackgroundColor(Color.BLACK);
        binding.getRoot().addView(drawView);
        setContentView(binding.getRoot());

        binding.fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {

                    Intent intent = new Intent("com.google.zxing.client.android.SCAN");
                    intent.putExtra("SCAN_MODE", "QR_CODE_MODE"); // "PRODUCT_MODE for bar codes

                    startActivityForResult(intent, 0);

                } catch (Exception e) {
                    // Nothing to do
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
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        String contents = readFromFile(this);
        parseConfig(contents);

        client.setCallback(this);
        client.start(PORT);
    }
}