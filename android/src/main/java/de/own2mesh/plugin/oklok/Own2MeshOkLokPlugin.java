package de.own2mesh.plugin.oklok;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.getcapacitor.JSObject;
import com.getcapacitor.NativePlugin;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@NativePlugin(
    requestCodes = {
        Own2MeshOkLokPlugin.REQUEST_ENABLE_BT
    },
    permissions = {
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.ACCESS_COARSE_LOCATION
    }
)
public class Own2MeshOkLokPlugin extends Plugin {

    //region Inner-Classes

    public enum ConnectionStatus {
        DISCONNECTED,
        CONNECTED,
        ACQUIRING_TOKEN,
        ACQUIRED_TOKEN
    }

    //endregion



    //region Fields

    private Own2MeshOkLokPlugin plugin;
    private byte[] currentToken = {0x00, 0x00, 0x00, 0x00};

    //region Static-Fields

    private volatile static ConnectionStatus connectionStatus = ConnectionStatus.DISCONNECTED;
    @NativePlugin
    protected static final int REQUEST_ENABLE_BT = 1;
    private static final long GATT_START_DELAY = 500; // msec
    private static final long SCAN_PERIOD = 10000; // msec

    //endregion

    //endregion

    //region Bluetooth-Fields

    private BluetoothAdapter mBluetoothAdapter;
    private Handler mScanHandler;
    private BluetoothLeScanner mScanner;
    private ScanSettings mScanSettings;
    private List<ScanFilter> mScanFilters;
    private BluetoothGatt mGatt;
    private BluetoothGattCharacteristic mWriteCharacteristic;
    private BluetoothGattCharacteristic mReadCharacteristic;

    //endregion

    //endregion



    //region Bluetooth-Callbacks

    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice btDevice = result.getDevice();
            if (btDevice != null) {
                if (btDevice.getName() != null) {
                    Log.i("CONNECT", "" + btDevice.getName());
                }
                connect(btDevice);
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult sr : results) {
                Log.i("ScanResult - Results", sr.toString());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e("Scan Failed", "Error Code: " + errorCode);
        }
    };
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            ((Activity) plugin.getContext()).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.i("onLeScan", device.toString());
                    connect(device);
                }
            });
        }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.i("onConnectionStateChange", "Status: " + status);
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    Log.i("gattCallback", "STATE_CONNECTED");
                    connectionStatus = ConnectionStatus.CONNECTED;
                    gatt.discoverServices();
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    connectionStatus = ConnectionStatus.DISCONNECTED;
                    Log.e("gattCallback", "STATE_DISCONNECTED");
                    break;
                default:
                    Log.e("gattCallback", "STATE_OTHER");
            }

        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            List<BluetoothGattService> services = gatt.getServices();

            for (int i = 0; i < services.size(); i++)
            {
                String servUUID = services.get(i).getUuid().toString().substring(0,8);
                if (servUUID.equals("0000fee7"))
                {
                    Log.i("service", services.get(i).getUuid().toString());
                    for (int j = 0; j < services.get(i).getCharacteristics().size(); j++) {
                        String charUUID = services.get(i).getCharacteristics().get(j).getUuid().toString().substring(0, 8);
                        Log.i("characteristic", services.get(i).getCharacteristics().get(j).toString());

                        Log.i("onServicesDiscovered", charUUID);

                        if (charUUID.equals("000036f5")) {
                            Log.i("onServicesDiscovered", "write ");
                            plugin.mWriteCharacteristic = services.get(i).getCharacteristics().get(j);
                            for (int k = 0; k < services.get(i).getCharacteristics().get(j).getDescriptors().size(); k++) {
                                services.get(i).getCharacteristics().get(j).getDescriptors().get(k).setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);;
                                boolean success = gatt.writeDescriptor(services.get(i).getCharacteristics().get(j).getDescriptors().get(k));
                                Log.i("onDescriptorsDiscovered", String.format("%d", success));
                                Log.i("onDescriptorsDiscovered", services.get(i).getCharacteristics().get(j).getDescriptors().get(k).toString());
                            }
                        } else if (charUUID.equals("000036f6")) {
                            Log.i("onServicesDiscovered", "read ");
                            plugin.mReadCharacteristic = services.get(i).getCharacteristics().get(j);
                            gatt.setCharacteristicNotification(plugin.mReadCharacteristic, true);

                            for (int k = 0; k < services.get(i).getCharacteristics().get(j).getDescriptors().size(); k++) {
                                services.get(i).getCharacteristics().get(j).getDescriptors().get(k).setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);;
                                boolean success = gatt.writeDescriptor(services.get(i).getCharacteristics().get(j).getDescriptors().get(k));
                                Log.i("onDescriptorsDiscovered", String.format("%b", success));
                                Log.i("onDescriptorsDiscovered", services.get(i).getCharacteristics().get(j).getDescriptors().get(k).toString());
                            }
                        }
                    }
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if(status == BluetoothGatt.GATT_SUCCESS){
                Log.i("onCharacteristicWrite", "SUCCESS");
                gatt.readCharacteristic(plugin.mReadCharacteristic);
                byte[] key = ByteArrayUtils.hexStringToByteArray("4c5f0c3c4c2853242036145b53592004");
                Log.i("Writing",ByteArrayUtils.byteArrayToHexString(EncryptionUtils.Decrypt(characteristic.getValue(), key)));
            }else {
                Log.e("Status", ""+ status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.i("onCharacteristicRead", characteristic.toString());
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.i("onCharacteristicChange", characteristic.toString());

            byte[] key = ByteArrayUtils.hexStringToByteArray("4c5f0c3c4c2853242036145b53592004");
            byte[] values = EncryptionUtils.Decrypt(characteristic.getValue(), key);

            if(values.length > 7){
                if(values[0] == 0x06 && values[1] == 0x02){
                    plugin.currentToken = Arrays.copyOfRange(values, 3, 7);
                    connectionStatus = ConnectionStatus.ACQUIRED_TOKEN;

                    Log.i("TOKEN", ByteArrayUtils.byteArrayToHexString(plugin.currentToken));
                }
            }
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.i("onDescriptorRead", descriptor.toString());
        }

    };

    //endregion



    //region Helper

    /*
    Requests bluetooth to be enabled and scans for filtered/wanted locks and also connects to it.
    Returns if the scan has started.
     */
    private void enable(PluginCall call, String address) throws IllegalStateException {
        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) { // Kein Bluetooth Adapter gesetzt und deaktiviert?
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            saveCall(call);
            startActivityForResult(call, enableBtIntent, REQUEST_ENABLE_BT);
            throw new IllegalStateException("Bluetooth not enabled!");
        } else {
            if (Build.VERSION.SDK_INT >= 21) {
                mScanner = mBluetoothAdapter.getBluetoothLeScanner();
                mScanSettings = new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build();
                mScanFilters = new ArrayList<>();
                ScanFilter scanFilter1 = new ScanFilter.Builder()
                        .setDeviceName("OKUSS1M1A")
                        .build();
                mScanFilters.add(scanFilter1);
                ScanFilter scanFilter2 = new ScanFilter.Builder()
                        .setDeviceName("OKGSS101")
                        .build();
                mScanFilters.add(scanFilter2);
                ScanFilter scanFilter3 = new ScanFilter.Builder()
                        .setDeviceName("OKUSS101")
                        .build();
                mScanFilters.add(scanFilter3);
                ScanFilter scanFilter4 = new ScanFilter.Builder()
                        .setDeviceAddress(address)
                        .build();
            }
            scan(true);
        }
    }

    /*
    Stops scaning for devices and disconnects connected device.
     */
    private void disable() {
        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
            scan(false);
        }
    }

    /*
    Scans for available devices and connects.
     */
    private void scan(final boolean enable) {
        if (enable) {
            // Starting BLE scan
            mScanHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (Build.VERSION.SDK_INT < 21) {
                        mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    } else {
                        mScanner.stopScan(mScanCallback);

                    }
                }
            }, SCAN_PERIOD);// Stops scanning after a pre-defined scan period.
            if (Build.VERSION.SDK_INT < 21) {
                mBluetoothAdapter.startLeScan(mLeScanCallback);
            } else {
                mScanner.startScan(mScanFilters, mScanSettings, mScanCallback);
            }
        } else {
            if (Build.VERSION.SDK_INT < 21) {
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
            } else {
                mScanner.stopScan(mScanCallback);
            }
            disconnect();
        }
    }

    /*
    Connects to the specified device. If a device is connected, it will be disconnected.
     */
    private void connect(BluetoothDevice device) {
        if (connectionStatus.equals(ConnectionStatus.DISCONNECTED)) {
            if (mGatt != null) {
                disconnect();
            }
            mGatt = device.connectGatt(this.getContext(), false, gattCallback);
        }
    }

    /*
    If a device is connected, it will be disconnected.
     */
    private void disconnect() {
        if (!connectionStatus.equals(ConnectionStatus.DISCONNECTED)) {
            if (mGatt == null) {
                return;
            }
            mGatt.disconnect();
            mGatt = null;
        }
    }

    private void token(String secret) {
        if (connectionStatus.equals(ConnectionStatus.CONNECTED)) {
            connectionStatus = ConnectionStatus.ACQUIRING_TOKEN;
            byte[] key = ByteArrayUtils.csvStringToByteArray(secret);
            byte[] content = ByteArrayUtils.hexStringToByteArray("060101013762556c68731d6d7e173b4d");

            byte[] values = EncryptionUtils.Encrypt(content, key);

            Log.i("onCharacteristicWrite", this.mWriteCharacteristic.toString());
            this.mWriteCharacteristic.setValue(values);
            Log.i("onCharacteristicWrite", this.mWriteCharacteristic.getValue().toString());
            mGatt.writeCharacteristic(this.mWriteCharacteristic);
        }
    }

    private void open(String secret, String password) {
        if (connectionStatus.equals(ConnectionStatus.ACQUIRED_TOKEN)) {
            byte[] key = ByteArrayUtils.csvStringToByteArray(secret);
            byte[] content = ByteArrayUtils.hexStringToByteArray("050106" + ByteArrayUtils.byteArrayToHexString(ByteArrayUtils.csvStringToByteArray(password)) + ByteArrayUtils.byteArrayToHexString(currentToken) + "303030");

            byte[] values = EncryptionUtils.Encrypt(content, key);

            this.mWriteCharacteristic.setValue(values);
            Log.i("onCharacteristicWrite", this.mWriteCharacteristic.getValue().toString());
            mGatt.writeCharacteristic(this.mWriteCharacteristic);
        }
    }

    //endregion



    //region Plugin-Callbacks

    @Override
    protected void handleOnStart() {
        super.handleOnStart();

        mScanHandler = new Handler();
        plugin = this;

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!this.getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this.getContext(), "BLE Not Supported",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) this.getContext().getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
    }

    @Override
    protected void handleOnPause() {
        super.handleOnPause();

        disable();
    }

    @Override
    protected void handleOnDestroy() {
        if (mGatt == null) {
            super.handleOnDestroy();
            return;
        }
        mGatt.close();
        mGatt = null;
        super.handleOnDestroy();
    }

    @Override
    protected void handleOnActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_CANCELED) {
                //Bluetooth not enabled.
                Toast.makeText(this.getContext(), "BLE Not Enabled",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            PluginCall last = getSavedCall();
            enable(last, last.getString("address"));
        }
        super.handleOnActivityResult(requestCode, resultCode, data);
    }

    //endregion



    //region Plugin-Methods

    @PluginMethod()
    public void echo(PluginCall call) {
        String value = call.getString("value");

        JSObject ret = new JSObject();
        ret.put("value", value);
        call.success(ret);
    }

    @PluginMethod
    public void open(PluginCall call) {
        String address = call.getString("address");
        String secret = call.getString("secret");
        String password = call.getString("pw");
        JSObject ret = new JSObject();

        try {
            enable(call, address);
            token(secret);
            open(secret, password);
            disable();
        } catch (IllegalStateException ex) {
            call.reject(ex.getLocalizedMessage(), ex);
            return;
        }

        ret.put("opened", true);
        call.resolve(ret);
    }

    @PluginMethod
    public void close(PluginCall call) {
        String address = call.getString("address");
        String secret = call.getString("secret");

        enable(call, address);

        JSObject ret = new JSObject();
        ret.put("closed", true);
        call.resolve(ret);
    }

    @PluginMethod
    public void battery_status(PluginCall call) {
        String address = call.getString("address");
        String secret = call.getString("secret");

        JSObject ret = new JSObject();
        ret.put("percentage", 100);
        call.resolve(ret);
    }

    @PluginMethod
    public void lock_status(PluginCall call) {
        String address = call.getString("address");
        String secret = call.getString("secret");

        JSObject ret = new JSObject();
        ret.put("locked", true);
        call.resolve(ret);
    }

    //endregion

}
