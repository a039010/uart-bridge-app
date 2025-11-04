package com.example.uartbridge;
public class IUARTBridge {
    static { System.loadLibrary("uart_jni"); }
    public native int uartOpen(String port, int baud);
    public native int uartClose();
    public native int uartWrite(byte[] data);
    public native byte[] uartRead(int len);
    public native byte[] uartReadAvailable(int maxlen, int timeout_ms);
}
