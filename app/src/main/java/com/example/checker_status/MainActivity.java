package com.example.checker_status;

import android.app.usage.UsageEvents;
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
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.view.GestureDetectorCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.checker_status.databinding.ActivityMainBinding;

import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.TextView;

import org.w3c.dom.Text;

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
import javax.crypto.spec.IvParameterSpec;
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
        if (!isRunning) {
            isRunning = true;
            this.port = port;

            Runnable runnable = new Runnable() {
                public void run() {
                    try {
                        String msg = "";
                        udpSocket = new DatagramSocket(port, InetAddress.getByName("0.0.0.0"));
                        udpSocket.setSoTimeout(1000);

                        while (isRunning) {
                            try {
                                byte[] buf = new byte[1000000];
                                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                                udpSocket.receive(packet);
                                if (callback != null) {
                                    callback.callback(new String(packet.getData()));
                                }
                            } catch (Exception e) {
                                // Nothing to do
                            }
                        }
                    } catch (Exception e) {
                        // Nothing to do
                    }
                }
            };

            thread = new Thread(runnable);
            thread.start();
        }
    }

    public void stop()
    {
        isRunning = false;
        try {
            thread.join();
        } catch (InterruptedException e) {
            // nothing to do
        }
        udpSocket.close();
    }
}

class DrawView extends View {

    private ArrayList<MainActivity.checker> checkers = null;
    private String lastCheckDate = "Unknown";
    Paint paint = new Paint();
    private Integer Y = 0;
    private Integer step = 50;
    int textSize = 70;

    public void setData(String lastCheckDate, List<MainActivity.checker> checkers)
    {
        this.lastCheckDate = lastCheckDate;
        this.checkers = (ArrayList<MainActivity.checker>) checkers;
        this.invalidate();
    }

    public DrawView(Context context) {
        super(context);
    }

    public void yUp() {
        Y += step;
        invalidate();
    }

    public void yDown() {
        Y -= step;
        invalidate();
    }

    private void drawString(Canvas canvas, String text, int x, int y, Paint paint)
    {
        if (text.contains("\n"))
        {
            String[] texts = text.split("\n");

            for (String txt : texts)
            {
                canvas.drawText(txt, x, y, paint);

                y += paint.getTextSize();
            }
        }
        else
        {
            canvas.drawText(text, x, y, paint);
        }
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int numberOfDots = Math.max(checkers.size(), 1);
        int dotSize = Math.max(Math.max(this.getWidth() / numberOfDots, this.getHeight() / numberOfDots), 500);
        int columns = Math.max(this.getWidth() / dotSize, 2);
        int rows = Math.max(this.getHeight() / dotSize, checkers.size() / columns) + 1;
        int yBorder = (rows * dotSize) - (dotSize * (this.getHeight() / dotSize)) + (dotSize / 2);

        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(textSize);

        if (checkers != null) {
            for (int i = 0; i < checkers.size(); i++) {
                if (Y < -(yBorder))
                {
                    Y = -(yBorder);
                }
                if (Y > 0)
                {
                    Y = 0;
                }

                int x = ((i % columns) * dotSize) + (dotSize / 2);
                int y = (((i / columns) % rows) * dotSize) + Y + dotSize;

                MainActivity.checker checker = checkers.get(i);
                paint.setStyle(Paint.Style.FILL);
                if (checker.isGreen) {
                    paint.setColor(Color.GREEN);
                } else {
                    paint.setColor(Color.RED);
                }
                canvas.drawCircle(x, y, dotSize / 2, paint);
                paint.setColor(Color.BLACK);
                drawString(canvas, checker.label, x, y, paint);
            }
        }

        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(lastCheckDate, getWidth() / 2, textSize * 2, paint);

    }

}

public class MainActivity extends AppCompatActivity implements ICallback, GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener {

    private ActivityMainBinding binding;
    private int PORT = 0;
    private String SEPARATOR = ";";
    private UDPServer client;
    private String msg = "";
    private String lastCheckDate = "";
    private DrawView drawView;
    private String CONFIG_FILE = "config.txt";
    private String DATA_FILE = "data.txt";
    private GestureDetectorCompat mDetector;
    private String PSK = "";
    private int NUMBER_OF_INSTANCES = 0;
    private int INSTANCE = -1;
    private String CONFIG = "";

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
                temp.isGreen = subs[(i * 2) + 2].contains("1");
                checkers.add(temp);
            }
        }
    }

    private String Decrypt(String text, String key) {
        try {
            Cipher cipher = Cipher.getInstance
                    ("AES/CBC/PKCS5Padding"); //this parameters should not be changed
            byte[] keyBytes = new byte[16];
            byte[] b = key.getBytes("UTF-8");
            int len = b.length;
            if (len > keyBytes.length)
                len = keyBytes.length;
            System.arraycopy(b, 0, keyBytes, 0, len);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(keyBytes);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] results = new byte[text.length()];
            try {
                results = cipher.doFinal(Base64.decode(text, Base64.DEFAULT));
            } catch (Exception e) {
                // Nothing to do
            }
            return new String(results, "UTF-8"); // it returns the result as a String
        }
        catch (Exception e)
        {
            // Nothing to do
        }
        return "";
    }

    public void callback(String msg)
    {
        msg = Decrypt(msg, PSK);
        this.msg = msg;
        parseMsg(msg);
        drawView.setData(lastCheckDate, checkers);
        writeToFile(msg, this, DATA_FILE);
    }

    private void parseConfig()
    {
        if (CONFIG.length() > 0) {
            CONFIG = CONFIG.trim();
            String[] lines = CONFIG.split("\n");
            if (lines.length > 0) {
                NUMBER_OF_INSTANCES = lines.length;
                if ((INSTANCE < 0) || (INSTANCE >= lines.length))
                {
                    INSTANCE = 0;
                }
                String[] temp = lines[INSTANCE].split(SEPARATOR);
                if (temp.length == 1) {
                    PORT = Integer.parseInt(temp[0].trim());
                    PSK = "";
                } else if (temp.length == 2) {
                    PORT = Integer.parseInt(temp[0].trim());
                    PSK = temp[1];
                }
            }
        }
    }

    private void writeToFile(String data, Context context, String filename) {
        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(context.openFileOutput(filename, Context.MODE_PRIVATE));
            outputStreamWriter.write(data);
            outputStreamWriter.close();
        }
        catch (IOException e)
        {
            // Nothing to do
        }
    }

    private String readFromFile(Context context, String filename) {

        String ret = "";

        try {
            InputStream inputStream = context.openFileInput(filename);

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
    public boolean onTouchEvent(MotionEvent event){
        if (this.mDetector.onTouchEvent(event)) {
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onDown(MotionEvent event) {
        return true;
    }

    @Override
    public boolean onFling(MotionEvent event1, MotionEvent event2,
                           float velocityX, float velocityY) {
        return true;
    }

    @Override
    public void onLongPress(MotionEvent event) {
        writeToFile("", this, CONFIG_FILE);
        writeToFile("", this, DATA_FILE);
        System.exit(0);
    }

    @Override
    public boolean onScroll(MotionEvent event1, MotionEvent event2, float distanceX,
                            float distanceY) {
        if (distanceY < 0)
        {
            drawView.yUp();
        }
        else
        {
            drawView.yDown();
        }

        return true;
    }

    @Override
    public void onShowPress(MotionEvent event) {
    }

    @Override
    public boolean onSingleTapUp(MotionEvent event) {
        return true;
    }

    @Override
    public boolean onDoubleTap(MotionEvent event) {
        INSTANCE = (INSTANCE + 1) % (NUMBER_OF_INSTANCES + 1);
        parseConfig();
        client.stop();
        client.setCallback(this);
        client.start(PORT);
        drawView.invalidate();
        return true;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent event) {
        return true;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent event) {
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        drawView = new DrawView(this);

        mDetector = new GestureDetectorCompat(this,this);
        mDetector.setOnDoubleTapListener(this);

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
        client = new UDPServer();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 0) {

            if (resultCode == RESULT_OK) {
                String contents = data.getStringExtra("SCAN_RESULT");
                CONFIG += "\r\n" + contents;
                writeToFile(CONFIG, this, CONFIG_FILE);
                parseConfig();
                client.stop();
                client.setCallback(this);
                client.start(PORT);
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        CONFIG = readFromFile(this, CONFIG_FILE);
        parseConfig();

        msg = readFromFile(this, DATA_FILE);
        parseMsg(msg);
        drawView.setData(lastCheckDate, checkers);

        client.setCallback(this);
        client.start(PORT);
    }

    @Override
    protected void onStop() {
        super.onStop();
        client.stop();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if ((newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) ||
            (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT)) {
            client.stop();
            client.setCallback(this);
            client.start(PORT);
        }
    }
}