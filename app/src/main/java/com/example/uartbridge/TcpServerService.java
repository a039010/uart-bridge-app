package com.example.uartbridge;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
public class TcpServerService extends Service {
    private static final String TAG = "TcpServerService";
    private static final int PORT = 5000;
    private volatile boolean running = true;
    private IUARTBridge uart = new IUARTBridge();
    private final Set<Socket> clients = Collections.synchronizedSet(new HashSet<>());
    @Override public void onCreate() {
        super.onCreate();
        startForegroundIfNeeded();
        uart.uartOpen("/dev/ttys1", 115200);
        new Thread(this::startServer, "TcpServerThread").start();
        new Thread(this::uartReaderThread, "UartReaderThread").start();
    }
    private void startForegroundIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = "uart_bridge_channel";
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel(channelId, "UART Bridge Service", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(channel);
            Notification notif = new Notification.Builder(this, channelId).setContentTitle("UART Bridge").setContentText("TCP server running on port " + PORT).build();
            startForeground(1, notif);
        }
    }
    private void startServer() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            Log.i(TAG, "TCP Server started on port " + PORT);
            while (running) {
                Socket client = serverSocket.accept();
                clients.add(client);
                new Thread(() -> handleClient(client)).start();
            }
        } catch (Exception e) { Log.e(TAG, "Server error", e); }
    }
    private void handleClient(Socket client) {
        try (InputStream in = client.getInputStream(); OutputStream out = client.getOutputStream()) {
            byte[] buffer = new byte[4096];
            int len = in.read(buffer);
            if (len <= 0) return;
            String req = new String(buffer, 0, len).trim();
            Log.i(TAG, "Received: " + req);
            org.json.JSONObject j = new org.json.JSONObject(req);
            String cmd = j.optString("cmd","");
            if ("WR".equalsIgnoreCase(cmd)) {
                String port = j.optString("port","/dev/ttys1");
                int baud = j.optInt("baud",115200);
                if (!port.equals("/dev/ttys1")) { uart.uartClose(); uart.uartOpen(port, baud); }
                byte[] data = j.has("data_hex") ? hexStringToBytes(j.getString("data_hex")) : jsonArrayToBytes(j.getJSONArray("data"));
                int ret = uart.uartWrite(data);
                JSONObject resp = new JSONObject(); resp.put("status","OK"); resp.put("written",ret);
                out.write((resp.toString()+"\n").getBytes());
            } else if ("RR".equalsIgnoreCase(cmd)) {
                String port = j.optString("port","/dev/ttys1");
                int baud = j.optInt("baud",115200);
                if (!port.equals("/dev/ttys1")) { uart.uartClose(); uart.uartOpen(port, baud); }
                int rlen = j.getInt("len"); byte[] result = uart.uartRead(rlen);
                JSONObject resp = new JSONObject(); if (result!=null) { resp.put("status","OK"); resp.put("data_hex", bytesToHex(result)); } else { resp.put("status","ERROR"); }
                out.write((resp.toString()+"\n").getBytes());
            } else {
                JSONObject resp = new JSONObject(); resp.put("status","ERROR"); resp.put("msg","unknown cmd"); out.write((resp.toString()+"\n").getBytes());
            }
        } catch (Exception e) { Log.e(TAG, "Client handling error", e); } finally { try { client.close(); } catch (Exception ignored) {} clients.remove(client); }
    }
    private void uartReaderThread() {
        while (running) {
            try {
                byte[] data = uart.uartReadAvailable(256, 500);
                if (data != null && data.length>0) {
                    String hex = bytesToHex(data);
                    JSONObject evt = new JSONObject();
                    evt.put("event","UART_PUSH");
                    evt.put("port","/dev/ttys1");
                    evt.put("data_hex", hex);
                    String msg = evt.toString() + "\n";
                    synchronized (clients) {
                        for (Socket s: clients) {
                            try { OutputStream out = s.getOutputStream(); out.write(msg.getBytes()); } catch (Exception e) { }
                        }
                    }
                }
            } catch (Exception e) { Log.e(TAG, "UART reader error", e); }
        }
    }
    private byte[] jsonArrayToBytes(org.json.JSONArray arr) throws Exception { byte[] b = new byte[arr.length()]; for (int i=0;i<arr.length();i++) b[i] = (byte)arr.getInt(i); return b; }
    private byte[] hexStringToBytes(String s) { int len = s.length(); byte[] data = new byte[len/2]; for (int i=0;i<len;i+=2) data[i/2] = (byte)((Character.digit(s.charAt(i),16)<<4)+Character.digit(s.charAt(i+1),16)); return data; }
    private String bytesToHex(byte[] bytes) { StringBuilder sb = new StringBuilder(); for (byte b: bytes) sb.append(String.format("%02X", b)); return sb.toString(); }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() { running = false; uart.uartClose(); super.onDestroy(); }
}
