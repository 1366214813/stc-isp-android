package cn.wch.stcisp;

import android.app.Activity;
import android.hardware.usb.UsbDevice;
import android.os.Build;
import android.util.Base64;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import cn.wch.uartlib.WCHUARTManager;
import cn.wch.uartlib.exception.ChipException;
import cn.wch.uartlib.exception.NoPermissionException;
import cn.wch.uartlib.exception.UartLibException;

public class SerialBridge {
    private final Activity activity;
    private UsbDevice currentDevice;
    private int currentSerialNumber = 0;
    private boolean serialEnabled = false;

    public SerialBridge(Activity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public boolean isSupported() {
        return activity.getPackageManager().hasSystemFeature("android.hardware.usb.host");
    }

    @JavascriptInterface
    public String enumDevices() {
        try {
            ArrayList<UsbDevice> devices = WCHUARTManager.getInstance().enumDevice();
            JSONArray arr = new JSONArray();
            for (int i = 0; i < devices.size(); i++) {
                UsbDevice dev = devices.get(i);
                JSONObject obj = new JSONObject();
                obj.put("index", i);
                obj.put("name", dev.getDeviceName());
                obj.put("vendorId", dev.getVendorId());
                obj.put("productId", dev.getProductId());
                arr.put(obj);
            }
            return arr.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    @JavascriptInterface
    public boolean openDevice(int index) {
        try {
            ArrayList<UsbDevice> devices = WCHUARTManager.getInstance().enumDevice();
            if (index < 0 || index >= devices.size()) return false;
            UsbDevice device = devices.get(index);

            if (WCHUARTManager.getInstance().isConnected(device)) {
                currentDevice = device;
                return true;
            }

            boolean opened = WCHUARTManager.getInstance().openDevice(device);
            if (opened) {
                currentDevice = device;
                return true;
            }
            return false;
        } catch (NoPermissionException e) {
            try {
                WCHUARTManager.getInstance().requestPermission(activity, currentDevice);
            } catch (Exception ex) {
                // ignore
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @JavascriptInterface
    public void disconnect() {
        if (currentDevice != null) {
            try {
                WCHUARTManager.getInstance().disconnect(currentDevice);
            } catch (Exception e) {
                // ignore
            }
            currentDevice = null;
            serialEnabled = false;
        }
    }

    @JavascriptInterface
    public boolean enableSerial(int serialNumber) {
        if (currentDevice == null) return false;
        try {
            currentSerialNumber = serialNumber;
            boolean result = WCHUARTManager.getInstance().enableSerial(currentDevice, serialNumber, true);
            if (result) serialEnabled = true;
            return result;
        } catch (Exception e) {
            return false;
        }
    }

    @JavascriptInterface
    public void disableSerial(int serialNumber) {
        if (currentDevice == null) return;
        try {
            WCHUARTManager.getInstance().enableSerial(currentDevice, serialNumber, false);
            if (serialNumber == currentSerialNumber) serialEnabled = false;
        } catch (Exception e) {
            // ignore
        }
    }

    @JavascriptInterface
    public boolean setSerialParameter(int baud, int dataBits, int stopBits, int parity, boolean flow) {
        if (currentDevice == null) return false;
        try {
            return WCHUARTManager.getInstance().setSerialParameter(
                    currentDevice, currentSerialNumber,
                    baud, dataBits, stopBits, parity, flow);
        } catch (Exception e) {
            return false;
        }
    }

    @JavascriptInterface
    public int writeData(String base64Data) {
        if (currentDevice == null || !serialEnabled) return -1;
        try {
            byte[] data = Base64.decode(base64Data, Base64.NO_WRAP);
            return WCHUARTManager.getInstance().syncWriteData(
                    currentDevice, currentSerialNumber, data, data.length, 2000);
        } catch (Exception e) {
            return -1;
        }
    }

    @JavascriptInterface
    public String readData(int timeoutMs) {
        if (currentDevice == null || !serialEnabled) return "";
        try {
            byte[] data = WCHUARTManager.getInstance().readData(currentDevice, currentSerialNumber, timeoutMs);
            if (data == null || data.length == 0) return "";
            return Base64.encodeToString(data, Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }

    @JavascriptInterface
    public int getAvailableBytes() {
        if (currentDevice == null || !serialEnabled) return 0;
        try {
            return WCHUARTManager.getInstance().getCurrentReadDataLength(currentDevice, currentSerialNumber);
        } catch (Exception e) {
            return 0;
        }
    }

    @JavascriptInterface
    public boolean setDTR(boolean value) {
        if (currentDevice == null) return false;
        try {
            return WCHUARTManager.getInstance().setDTR(currentDevice, currentSerialNumber, value);
        } catch (Exception e) {
            return false;
        }
    }

    @JavascriptInterface
    public boolean setRTS(boolean value) {
        if (currentDevice == null) return false;
        try {
            return WCHUARTManager.getInstance().setRTS(currentDevice, currentSerialNumber, value);
        } catch (Exception e) {
            return false;
        }
    }

    @JavascriptInterface
    public boolean isConnected() {
        return currentDevice != null && serialEnabled;
    }

    @JavascriptInterface
    public String getDeviceName() {
        if (currentDevice == null) return "";
        return currentDevice.getDeviceName();
    }
}
