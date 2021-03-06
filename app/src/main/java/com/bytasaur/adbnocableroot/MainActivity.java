package com.bytasaur.adbnocableroot;

import android.annotation.SuppressLint;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;

//TODO: Add quick setting button in notification bar
//TODO: Add widget
//TODO: Add always-on notification
//TODO: Consider adding a timeout on waitFor's

public class MainActivity extends AppCompatActivity {
    private Runtime runtime;
    private TextView status;
    private TextView ip_text;
    private TextView port_text;
    private MenuItem menuPortItem;
    private WifiManager wifiManager;
    static int PORT;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        runtime = Runtime.getRuntime();
        PORT = getPreferences(0).getInt("port", 5555);
        status = findViewById(R.id.state);
        ip_text = findViewById(R.id.ip);
        port_text = findViewById(R.id.port);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh(null);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        menuPortItem = menu.getItem(0);
        menuPortItem.setTitle(PORT+"");
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final EditText editText = new EditText(this);
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        final AlertDialog alertDialog = new AlertDialog.Builder(this).setTitle("Enter port to be used").setView(editText)
                .setPositiveButton("Set", null).setNeutralButton("Default", null).setNegativeButton("Cancel", null).create();
        alertDialog.show();
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String str = editText.getText().toString();
                if(TextUtils.isEmpty(str)) {
                    editText.setError("* Required");
                    return;
                }
                int port = 0;
                try {
                    port = Integer.parseInt(str);
                }
                catch(NumberFormatException ignored) {}
                if(port>0 && port<65536) {
                    if(setProperty(port)) {
                        getPreferences(0).edit().putInt("port", port).apply();
                        PORT = port;
                        menuPortItem.setTitle(PORT + "");
                        refresh(null);
                    }
                    alertDialog.dismiss();
                }
                else {
                    editText.setError("* Invalid port number");
                }
            }
        });
        alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(setProperty(5555)) {
                    getPreferences(0).edit().putInt("port", 5555).apply();
                    PORT = 5555;
                    menuPortItem.setTitle("5555");
                    refresh(null);
                }
                alertDialog.dismiss();
            }
        });
        return super.onOptionsItemSelected(item);
    }

    public void refresh(View v) {
        setStatus();
        setIp();
    }

    @SuppressLint("SetTextI18n")
    public void setStatus() {
        try {
            Process getprop = runtime.exec("getprop service.adb.tcp.port");
            InputStream inputStream = getprop.getInputStream();
            byte buff[] = new byte[8];
            int s = inputStream.read(buff);
            int port = Integer.parseInt(new String(buff, 0, s-1));
            if(port>0) {
                status.setText("Enabled");
            }
            else {
                status.setText("Disabled");
            }
            port_text.setText(" : "+port);
            getprop.waitFor();
        }
        catch(Exception ex) {
            if(ex.getClass() == NumberFormatException.class) {
                status.setText("Unknown");
                port_text.setText(" : NaN");
            }
            ex.printStackTrace();
        }
    }

    public void setIp() {
        int ip = wifiManager.getConnectionInfo().getIpAddress();
        if(ip==0) {
            ip_text.setText("N/A");
        }
        else {
            ip_text.setText(Formatter.formatIpAddress(wifiManager.getConnectionInfo().getIpAddress()));
        }
    }

    public void enable(View v) {
//        if(setProperty(PORT)) {
//            refresh(null);
//        }
        setProperty(PORT);
        refresh(null);
    }

    public void disable(View v) {
//        if(setProperty(-1)) {
//            refresh(null);
//        }
        setProperty(-1);
        refresh(null);
    }

    boolean setProperty(int port) {
        Process su = null;
        boolean r = false;
        DataOutputStream outputStream = null;
        try {
            su = runtime.exec("su");
            outputStream = new DataOutputStream(su.getOutputStream());
            outputStream.writeBytes("setprop service.adb.tcp.port "+port+"\n");
            outputStream.flush();

            outputStream.writeBytes("stop adbd\n");
            outputStream.flush();

            outputStream.writeBytes("start adbd\n");
            outputStream.flush();

            outputStream.writeBytes("exit\n");
            outputStream.flush();

            r = su.waitFor()==0;
            outputStream.close();
        }
        catch(Exception ex) {
//            if(su==null || ex.getClass().equals(IOException.class)) {
//                Toast.makeText(this, "Permission Denied!", Toast.LENGTH_SHORT).show();
//            }
            ex.printStackTrace();
            if(su!=null) {
                su.destroy();
            }
            if(outputStream!=null) {
                try {
                    outputStream.close();
                }
                catch (IOException ignored) {}
            }
        }
        if(!r) {
            Toast.makeText(this, "Permission Denied!", Toast.LENGTH_SHORT).show();
        }
        return r;
    }
}
