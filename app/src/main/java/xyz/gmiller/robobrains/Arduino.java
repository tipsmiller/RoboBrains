package xyz.gmiller.robobrains;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;

public class Arduino {
    private static final String TAG = "RoboBrains::Arduino";
    private static final String ACTION_USB_PERMISSION = "xyz.gmiller.arduinohelloworld.USB_PERMISSION";
    private UsbManager usbManager;
    private UsbDevice device;
    private UsbSerialDevice serialPort;
    private PendingIntent permissionIntent;
    private Context context;
    private ArduinoListener listener;

    public Arduino(Context context, UsbManager usbManager, ArduinoListener listener) {
        this.usbManager = usbManager;
        this.context = context;
        this.listener = listener;

        permissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        this.context.registerReceiver(broadcastReceiver, filter);
    }

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ACTION_USB_PERMISSION)) {
                setupArduino();
            } else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
                Log.i(TAG, "USB device attached\n");
                if (!intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    requestPermission();
                } else {
                    setupArduino();
                }
            } else if (intent.getAction().equals((UsbManager.ACTION_USB_DEVICE_DETACHED))) {
                Log.i(TAG, "USB device detached\n");
                disconnect();
            }
        }
    };

    public final UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback() {
        @Override
        public void onReceivedData(byte[] bytes) {
            String data = null;
            try {
                data = new String(bytes, "UTF-8");
                //tvAppend(textView, data);
                listener.onMessageReceived(data);
            } catch (UnsupportedEncodingException ex) {
                ex.printStackTrace();
            }
        }
    };

    private synchronized void requestPermission() {
        Log.i(TAG, "Looking for devices\n");
        HashMap<String,UsbDevice> usbDevices = usbManager.getDeviceList();
        if (!usbDevices.isEmpty()) {
            if (usbDevices.entrySet().size() == 1) {
                device = usbDevices.values().iterator().next();
                Log.i(TAG, String.format("Device found: VID %s, PID %s\n", device.getVendorId(), device.getProductId()));
                if (usbManager.hasPermission(device)) {
                    setupArduino();
                } else {
                    usbManager.requestPermission(device, permissionIntent);
                }
            }
        }
    }

    private synchronized void setupArduino() {
        UsbDeviceConnection connection = usbManager.openDevice(device);
        serialPort = UsbSerialDevice.createUsbSerialDevice(device, connection);
        if (serialPort != null) {
            if (serialPort.open()) {
                serialPort.setBaudRate(9600);
                serialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
                serialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
                serialPort.setParity(UsbSerialInterface.PARITY_NONE);
                serialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                serialPort.read(mCallback);
                Log.i(TAG, "Success: Serial connection opened!\n");
            } else {
                Log.i(TAG, "Error: Serial connection not opened\n");
            }
        } else {
            Log.i(TAG, "Error: Serial port is null\n");
        }
    }

    private void disconnect() {
        if (serialPort != null) {
            Log.i(TAG, "Serial port disconnected\n");
            serialPort.close();
            serialPort = null;
        }
    }

    public void tryConnect() {
        requestPermission();
    }

    public void sendMessage(String message) {
        serialPort.write(message.getBytes());
    }

    public void destroy() {
        try {
            this.context.unregisterReceiver(broadcastReceiver);
        } catch(IllegalArgumentException ex) {
            //noop
        }
    }

    public interface ArduinoListener {
        public void onMessageReceived(String message);
    }
}
