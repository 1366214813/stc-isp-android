package cn.wch.stcisp;

import android.app.Activity;
import android.hardware.usb.UsbDevice;
import android.os.Build;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import cn.wch.uartlib.WCHUARTManager;
import cn.wch.uartlib.callback.IUsbStateChange;
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

            // 尝试打开设备
            try {
                boolean opened = WCHUARTManager.getInstance().openDevice(device);
                if (opened) {
                    currentDevice = device;
                    return true;
                }
                return false;
            } catch (NoPermissionException e) {
                // 需要请求 USB 权限
                return requestPermissionAndWait(device);
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 请求 USB 权限并等待用户确认
     */
    private boolean requestPermissionAndWait(UsbDevice device) {
        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] result = {false};

        try {
            // 注册权限回调
            WCHUARTManager.getInstance().setUsbStateListener(new IUsbStateChange() {
                @Override
                public void usbDeviceDetach(UsbDevice dev) {}

                @Override
                public void usbDeviceAttach(UsbDevice dev) {}

                @Override
                public void usbDevicePermission(UsbDevice dev, boolean granted) {
                    if (dev != null && dev.equals(device)) {
                        result[0] = granted;
                        latch.countDown();
                    }
                }
            });

            // 在主线程请求权限
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        WCHUARTManager.getInstance().requestPermission(activity, device);
                    } catch (Exception e) {
                        latch.countDown();
                    }
                }
            });

            // 等待用户确认（最多 30 秒）
            boolean waited = latch.await(30, TimeUnit.SECONDS);

            if (result[0]) {
                // 权限已授予，重新尝试打开设备
                boolean opened = WCHUARTManager.getInstance().openDevice(device);
                if (opened) {
                    currentDevice = device;
                    return true;
                }
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
    public int writeData(String hexData) {
        if (currentDevice == null || !serialEnabled) return -1;
        try {
            byte[] data = hexToBytes(hexData);
            return WCHUARTManager.getInstance().syncWriteData(
                    currentDevice, currentSerialNumber, data, data.length, 5000);
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
            return bytesToHex(data);
        } catch (Exception e) {
            return "";
        }
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
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

    /**
     * 硬件复位 MCU: 拉低 DTR 触发复位
     * STC MCU 需要 DTR 信号变化来触发复位
     */
    @JavascriptInterface
    public boolean resetMCU() {
        if (currentDevice == null) return false;
        try {
            // 拉低 DTR (触发复位)
            WCHUARTManager.getInstance().setDTR(currentDevice, currentSerialNumber, true);
            Thread.sleep(100);
            // 释放 DTR (允许 MCU 启动)
            WCHUARTManager.getInstance().setDTR(currentDevice, currentSerialNumber, false);
            Thread.sleep(100);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
