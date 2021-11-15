package com.izis.serialport.connect;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import com.izis.serialport.util.Log;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android_serialport_api.SerialPort;
import android_serialport_api.SerialPortFinder;

public class SerialConnectJNI extends SerialConnect {
    private SerialPort mSerialPort = null;
    private OutputStream mOutputStream = null;
    private InputStream mInputStream = null;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    close();
                    onConnectError(device.getDeviceName());
                }
            }
        }
    };

    public SerialConnectJNI(Context context) {
        super(context);
    }

    @Override
    void openConnect() {
        SerialPortFinder mSerialPortFinder = new SerialPortFinder();
        String[] entryValues = mSerialPortFinder.getAllDevicesPath();
        if (entryValues == null) {
            Log.w("没有找到相关设备");
            onConnectFailNoReConnect();
            return;
        }
        Log.i("查找到设备：" + Arrays.toString(entryValues));

        String device = "";
        for (String item : entryValues) {
            if (item.contains("/dev/ttyUSB0")) {
                device = item;
                break;
            }
        }
        if (device.isEmpty()) {
            Log.w("没有找到相关设备");
            onConnectFailNoReConnect();
            return;
        }

        try {
            // 打开/dev/ttyUSB0路径设备的串口  ttyUSB0
            mSerialPort = new SerialPort(new File(device), 115200, 0);
            mInputStream = mSerialPort.getInputStream();
            mOutputStream = mSerialPort.getOutputStream();

            IntentFilter filter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED);
            if (context != null) context.registerReceiver(usbReceiver, filter);

            onConnectSuccess(device);

            new Thread() {
                @Override
                public void run() {
                    requestData();
                }
            }.start();
        } catch (Exception e) {
            Log.e("打开串口失败");
            onConnectFailNoReConnect();
        }
    }

    @Override
    void disConnect() {
        try {
            if (context != null) {
                context.unregisterReceiver(usbReceiver);
            }
        } catch (Exception e) {
            //防止多次调用close报  Receiver not registered 异常
        }

        if (mSerialPort != null)
            executorService.execute(() -> mSerialPort.close());
    }

    @Override
    public boolean writeBytes(byte[] bytes) {
        try {
            mOutputStream.write(bytes);
            mOutputStream.flush();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void requestData() {
        while (isConnected()) {
            try {
                sleep();

                byte[] buffer = new byte[1024];
                if (mInputStream == null) return;
                int size = mInputStream.read(buffer);
                if (size > 0) {
                    byte[] readBytes = new byte[size];
                    System.arraycopy(buffer, 0, readBytes, 0, size);
                    String curReadData = new String(readBytes);

                    checkData(curReadData);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
