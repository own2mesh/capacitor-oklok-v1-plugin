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
import android.widget.Toast;

import com.getcapacitor.JSObject;
import com.getcapacitor.NativePlugin;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@NativePlugin(
        requestCodes = {
                Own2MeshOkLokPlugin.REQUEST_ENABLE_BT
        },
        permissions = {
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
        }
)
// 4c5f0c3c4c2853242036145b53592004
public class Own2MeshOkLokPlugin extends Plugin {

    //region Inner-Classes

    private enum CallType {
        NONE,
        OPEN,
        CLOSE,
        BATT_STAT,
        LOCK_STAT
    }

    //endregion



    //region Fields

    private Own2MeshOkLokPlugin plugin;
    private String currentName = new String();
    private String currentAddress = new String();
    private byte[] currentSecret = { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
    private byte[] currentPassword = { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
    private byte[] currentToken = { 0x00, 0x00, 0x00, 0x00 };

    //region Static-Fields

    @NativePlugin
    protected static final int REQUEST_ENABLE_BT = 1;
    private static final long SCAN_PERIOD = 10000; // msec

    private static CallType callType = CallType.NONE;

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
            Log.i("SCANRESULT - SINGLE", "Called");
            BluetoothDevice btDevice = result.getDevice();
            Log.i("SCANRESULT - SINGLE", btDevice.toString());
            if (btDevice != null) {
                disable();
                connect(btDevice);
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            Log.i("SCANRESULT - BATCH", "Called");
            for (ScanResult sr : results) {
                Log.i("SCANRESULT - SINGLE", sr.toString());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e("SCANFAILED", "Error Code: " + errorCode);
        }
    };

    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    ((Activity) plugin.getContext()).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Log.i("LESCANRESULT", device.toString());
                            connect(device);
                        }
                    });
                }
            };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.i("CONNECTIONCHANGED", "Status: " + status);
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    Log.i("CONNECTIONCHANGED", "STATE_CONNECTED");
                    gatt.discoverServices();
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    Log.e("CONNECTIONCHANGED", "STATE_DISCONNECTED");
                    break;
                default:
                    Log.e("CONNECTIONCHANGED", "STATE_OTHER");
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
                        Log.i("SERVICE-CHARACTERISTICS", services.get(i).getCharacteristics().get(j).toString());

                        Log.i("onServicesDiscovered", charUUID);

                        if (charUUID.equals("000036f5")) {
                            Log.i("SERVICE-CHARACTERISTIC", "Write ");
                            plugin.mWriteCharacteristic = services.get(i).getCharacteristics().get(j);
                            gatt.setCharacteristicNotification(plugin.mWriteCharacteristic, true);
                            Log.i("SERVICE-CHARACTERISTIC", "Write: " + plugin.mWriteCharacteristic.toString());

                            for (int k = 0; k < services.get(i).getCharacteristics().get(j).getDescriptors().size(); k++) {
                                services.get(i).getCharacteristics().get(j).getDescriptors().get(k).setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                boolean success = gatt.writeDescriptor(services.get(i).getCharacteristics().get(j).getDescriptors().get(k));
                                Log.i("onDescriptorsDiscovered", String.format("%d", success));
                                Log.i("onDescriptorsDiscovered", services.get(i).getCharacteristics().get(j).getDescriptors().get(k).toString());
                            }
                        } else if (charUUID.equals("000036f6")) {
                            Log.i("SERVICE-CHARACTERISTIC", "Read ");
                            plugin.mReadCharacteristic = services.get(i).getCharacteristics().get(j);
                            gatt.setCharacteristicNotification(plugin.mReadCharacteristic, true);
                            Log.i("SERVICE-CHARACTERISTIC", "Read: " + plugin.mReadCharacteristic.toString());

                            for (int k = 0; k < services.get(i).getCharacteristics().get(j).getDescriptors().size(); k++) {
                                services.get(i).getCharacteristics().get(j).getDescriptors().get(k).setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);;
                                boolean success = gatt.writeDescriptor(services.get(i).getCharacteristics().get(j).getDescriptors().get(k));
                                Log.i("DESCIRPTOR-DISCOVERED", String.format("%b", success));
                                Log.i("DESCIRPTOR-DISCOVERED", services.get(i).getCharacteristics().get(j).getDescriptors().get(k).toString());
                            }
                        }
                    }
                }
            }

        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if(status == BluetoothGatt.GATT_SUCCESS){
                Log.i("CHARACTERISTIC-WRITE", ByteArrayUtils.byteArrayToHexString(EncryptionUtils.Decrypt(characteristic.getValue(), currentSecret)));
            }else {
                Log.e("CHARACTERISTIC-WRITE", ""+ status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.i("CHARACTERISTIC-READ", characteristic.toString());
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte[] values = EncryptionUtils.Decrypt(characteristic.getValue(), currentSecret);
            Log.i("CHARACTERISTIC-CHANGE", ByteArrayUtils.byteArrayToHexString(values));

            //TOKEN
            if(values[0] == 0x06 && values[1] == 0x02){
                plugin.currentToken = Arrays.copyOfRange(values, 3, 7);
                Log.i("TOKEN", ByteArrayUtils.byteArrayToHexString(plugin.currentToken));

                switch(callType) {
                    case OPEN:
                        open();
                        break;
                    case CLOSE:
                        close();
                        break;
                    case BATT_STAT:
                        battery_status();
                        break;
                    case LOCK_STAT:
                        lock_status();
                        break;
                    default:
                        Log.e("CALLTYPE","Unknown CallType: This should never happen.");
                        break;
                }
                return;
            }
            JSObject ret = new JSObject();
            //UNLOCKING
            if(values[0] == 0x05 && values[1] == 0x02 && values[2] == 0x01) {
                String result = String.format("%02x", values[3]);
                Log.i("OPEN", result);
                ret.put("opened", result.equals("00") ? true : false);
            }
            //LOCKING
            if(values[0] == 0x05 && values[1] == 0x0D && values[2] == 0x01) {
                String result = String.format("%02x", values[3]);
                Log.i("CLOSED", result);
                ret.put("closed", result.equals("00") ? true : false);
            }
            //BATT_STAT
            if(values[0] == 0x02 && values[1] == 0x02 && values[2] == 0x01) {
                String result = String.format("%02x", values[3]);
                Log.i("BATT_STAT", result);
                ret.put("percentage", (((float) Integer.parseInt(result)) / 64) * 100);
            }
            //LOCK_STAT
            if(values[0] == 0x05 && values[1] == 0x0F && values[2] == 0x01) {
                String result = String.format("%02x", values[3]);
                Log.i("CLOSED", result);
                ret.put("locked", result.equals("00") ? false : true);
            }
            plugin.getSavedCall().resolve(ret);
            plugin.disconnect();
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.i("DESCRIPTOR-READ", descriptor.toString());
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.i("DESCRIPTOR-WRITE", descriptor.toString());

            token();
        }

    };

    //endregion



    //region Helper

    /*
    Requests bluetooth to be enabled and scans for filtered/wanted locks and also connects to it.
    Returns if the scan has started.
     */
    private void enable() {
        Log.i("ENABLE", "Called");

        if (!this.hasRequiredPermissions()) {
            this.getSavedCall().reject("Permissions not granted.");
            this.pluginRequestAllPermissions();
            return;
        }

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) { // Kein Bluetooth Adapter gesetzt und deaktiviert?
            Log.i("ENABLE-BLUETOOTH", "Bluetooth not enabled");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(getSavedCall(), enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            if (Build.VERSION.SDK_INT >= 21) {
                mScanner = mBluetoothAdapter.getBluetoothLeScanner();
                mScanSettings = new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build();
                mScanFilters = new ArrayList<>();
                ScanFilter scanFilterAddress = new ScanFilter.Builder()
                        .setDeviceAddress(currentAddress)
                        .build();
                mScanFilters.add(scanFilterAddress);
                Log.i("ENABLE-SCANFILTER", scanFilterAddress.toString());
                if (currentName != null && !currentName.equals(new String())) {
                    ScanFilter scanFilterName = new ScanFilter.Builder()
                            .setDeviceName(currentName)
                            .build();
                    mScanFilters.add(scanFilterName);
                    Log.i("ENABLE-SCANFILTER", scanFilterName.toString());
                }
            }
            scan(true);
        }
    }

    /*
    Stops scaning for devices and disconnects connected device.
     */
    private void disable() {
        Log.i("DISABLE", "Called");
        if (Build.VERSION.SDK_INT < 21 && mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
            scan(false);
        } else if (mScanner != null) {
            scan(false);
        }
    }

    /*
    Scans for available devices and connects.
     */
    private void scan(final boolean enable) {
        Log.i("SCAN", "Called " + enable);
        if (enable) {
            // Starting BLE scan
            mScanHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (Build.VERSION.SDK_INT < 21) {
                        Log.i("SCAN", "Self stopped (low SDK)");
                        mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    } else {
                        Log.i("SCAN", "Self stopped");
                        mScanner.stopScan(mScanCallback);
                    }
                }
            }, SCAN_PERIOD);// Stops scanning after a pre-defined scan period.
            if (Build.VERSION.SDK_INT < 21) {
                Log.i("SCAN", "Started (low SDK)");
                mBluetoothAdapter.startLeScan(mLeScanCallback);
            } else {
                Log.i("SCAN", "Starting");
                mScanner.startScan(mScanFilters, mScanSettings, mScanCallback);
                Log.i("SCAN", "Started");
            }
        } else {
            if (Build.VERSION.SDK_INT < 21) {
                Log.i("SCAN", "Stopped (low S ,DK)");
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
            } else {
                Log.i("SCAN", "Stopped");
                mScanner.stopScan(mScanCallback);
            }
        }
    }

    /*
    Connects to the specified device. If a device is connected, it will be disconnected.
     */
    private void connect(BluetoothDevice device) {
        Log.i("CONNECT", "Called " + device.toString());
        if (mGatt != null) {
            disconnect();
        }
        mGatt = device.connectGatt(this.getContext(), false, gattCallback);
    }

    /*
    If a device is connected, it will be disconnected.
     */
    private void disconnect() {
        Log.i("DISCONNECT", "Called");
        if (mGatt == null) {
            return;
        }
        mGatt.disconnect();
        mGatt = null;
    }

    private void token() {
        Log.i("TOKEN", "Called");
        byte[] content = ByteArrayUtils.hexStringToByteArray("060101013762556c68731d6d7e173b4d");
        byte[] values = EncryptionUtils.Encrypt(content, currentSecret);

        this.mWriteCharacteristic.setValue(values);
        Log.i("CHARACTERISTIC-TOWRITE", ByteArrayUtils.byteArrayToHexString(this.mWriteCharacteristic.getValue()));
        mWriteCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        boolean success = mGatt.writeCharacteristic(this.mWriteCharacteristic);
        Log.i("CHARACTERISTIC-TOWRITE", "Called: " + success);
    }

    private void open() {
        Log.i("OPEN", "Called");
        byte[] content = ByteArrayUtils.hexStringToByteArray("050106" + ByteArrayUtils.byteArrayToHexString(currentPassword) + ByteArrayUtils.byteArrayToHexString(currentToken) + "303030");
        byte[] values = EncryptionUtils.Encrypt(content, currentSecret);

        Log.i("CHARACTERISTIC-TOWRITE", this.mWriteCharacteristic.toString());
        this.mWriteCharacteristic.setValue(values);
        Log.i("CHARACTERISTIC-TOWRITE", this.mWriteCharacteristic.getValue().toString());
        mGatt.writeCharacteristic(this.mWriteCharacteristic);
        Log.i("CHARACTERISTIC-TOWRITE", "Called");
    }

    private void close() {
        Log.i("CLOSE", "Called");
        byte[] content = ByteArrayUtils.hexStringToByteArray("050C0101" + ByteArrayUtils.byteArrayToHexString(currentToken) + "3030303030303030");
        byte[] values = EncryptionUtils.Encrypt(content, currentSecret);

        Log.i("CHARACTERISTIC-TOWRITE", this.mWriteCharacteristic.toString());
        this.mWriteCharacteristic.setValue(values);
        Log.i("CHARACTERISTIC-TOWRITE", this.mWriteCharacteristic.getValue().toString());
        mGatt.writeCharacteristic(this.mWriteCharacteristic);
    }

    private void battery_status() {
        Log.i("BATT_STAT", "Called");
        byte[] content = ByteArrayUtils.hexStringToByteArray("02010101" + ByteArrayUtils.byteArrayToHexString(currentToken) + "3030303030303030");
        byte[] values = EncryptionUtils.Encrypt(content, currentSecret);

        Log.i("CHARACTERISTIC-TOWRITE", this.mWriteCharacteristic.toString());
        this.mWriteCharacteristic.setValue(values);
        Log.i("CHARACTERISTIC-TOWRITE", this.mWriteCharacteristic.getValue().toString());
        mGatt.writeCharacteristic(this.mWriteCharacteristic);
    }

    private void lock_status() {
        Log.i("LOCK_STAT", "Called");
        byte[] content = ByteArrayUtils.hexStringToByteArray("050E0101" + ByteArrayUtils.byteArrayToHexString(currentToken) + "3030303030303030");
        byte[] values = EncryptionUtils.Encrypt(content, currentSecret);

        Log.i("CHARACTERISTIC-TOWRITE", this.mWriteCharacteristic.toString());
        this.mWriteCharacteristic.setValue(values);
        Log.i("CHARACTERISTIC-TOWRITE", this.mWriteCharacteristic.getValue().toString());
        mGatt.writeCharacteristic(this.mWriteCharacteristic);
    }

    //endregion



    //region Plugin-Callbacks

    @Override
    protected void handleOnStart() {
        Log.i("HANDLEONSTART", "Called");
        mScanHandler = new Handler();
        plugin = this;

        if (!this.hasRequiredPermissions()) {
            pluginRequestAllPermissions();
        }

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        Log.i("HANDLEONSTART", "BLE Support");
        if (!this.getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this.getContext(), "BLE not supported",
                    Toast.LENGTH_SHORT).show();
            Log.i("HANDLEONSTART", "BLE not supported");
            return;
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        Log.i("HANDLEONSTART", "BL-Manager");
        final BluetoothManager bluetoothManager =
                (BluetoothManager) this.getContext().getSystemService(Context.BLUETOOTH_SERVICE);
        Log.i("HANDLEONSTART", "BL-Adapter");
        mBluetoothAdapter = bluetoothManager.getAdapter();

        super.handleOnStart();
    }

    @Override
    protected void handleOnDestroy() {
        Log.i("HANDLEONDESTROY", "Called");
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
        Log.i("HANDLEONACTRESULT", "Called " + requestCode);
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_CANCELED) {
                //Bluetooth not enabled.
                Toast.makeText(this.getContext(), "BLE not enabled",
                        Toast.LENGTH_SHORT).show();
                Log.i("HANDLEONACTRESULT", "BLE not enabled");
                this.getSavedCall().reject("BLE not enabled");
                return;
            }
            enable();
        }
        super.handleOnActivityResult(requestCode, resultCode, data);
    }

    //endregion



    //region Plugin-Methods

    @PluginMethod()
    public void echo(PluginCall call) {
        Log.i("ECHO", "Called");
        String value = call.getString("value");
        Log.i("ECHO", value);

        JSObject ret = new JSObject();
        ret.put("value", value);
        call.success(ret);
    }

    @PluginMethod
    public void open(PluginCall call) {
        Log.i("OPEN", "Called");
        saveCall(call);
        currentName = call.getString("name");
        Log.i("OPEN", "Name: " + currentName);
        currentAddress = call.getString("address");
        Log.i("OPEN", "Address: " + currentAddress);
        if (currentAddress == null || currentAddress.equals("")) {
            Log.e("OPEN", "Non/Invalid MAC-Address provided.");
            call.reject("Non/Invalid MAC-Address provided.");
        }
        try {
            String secret = ByteArrayUtils.JSArrayToHexString(call.getArray("secret"));
            Log.i("OPEN", "Secret: " + secret);
            currentSecret = ByteArrayUtils.hexStringToByteArray(secret);
            String pw = ByteArrayUtils.JSArrayToHexString(call.getArray("pw"));
            Log.i("OPEN", "Password: " + pw);
            currentPassword = ByteArrayUtils.hexStringToByteArray(pw);
        } catch (JSONException ex) {
            Log.e("OPEN", ex.getLocalizedMessage(), ex);
            call.reject(ex.getLocalizedMessage(), ex);
            return;
        }

        callType = CallType.OPEN;
        enable();
    }

    @PluginMethod
    public void close(PluginCall call) {
        Log.i("CLOSE", "Called");
        saveCall(call);
        currentAddress = call.getString("address");
        Log.i("CLOSE", "Address: " + currentAddress);
        if (currentAddress == null || currentAddress.equals("")) {
            Log.e("CLOSE", "Non/Invalid MAC-Address provided.");
            call.reject("Non/Invalid MAC-Address provided.");
        }
        try {
            String secret = ByteArrayUtils.JSArrayToHexString(call.getArray("secret"));
            Log.i("CLOSE", "Secret: " + secret);
            currentSecret = ByteArrayUtils.hexStringToByteArray(secret);
        } catch (JSONException ex) {
            Log.e("CLOSE", ex.getLocalizedMessage(), ex);
            call.reject(ex.getLocalizedMessage(), ex);
            return;
        }

        callType = CallType.CLOSE;
        enable();
    }

    @PluginMethod
    public void battery_status(PluginCall call) {
        Log.i("BATT_STAT", "Called");
        saveCall(call);
        currentAddress = call.getString("address");
        Log.i("BATT_STAT", "Address: " + currentAddress);
        if (currentAddress == null || currentAddress.equals("")) {
            Log.e("BATT_STAT", "Non/Invalid MAC-Address provided.");
            call.reject("Non/Invalid MAC-Address provided.");
        }
        try {
            String secret = ByteArrayUtils.JSArrayToHexString(call.getArray("secret"));
            Log.i("BATT_STAT", "Secret: " + secret);
            currentSecret = ByteArrayUtils.hexStringToByteArray(secret);
        } catch (JSONException ex) {
            Log.e("BATT_STAT", ex.getLocalizedMessage(), ex);
            call.reject(ex.getLocalizedMessage(), ex);
            return;
        }

        callType = CallType.BATT_STAT;
        enable();
    }

    @PluginMethod
    public void lock_status(PluginCall call) {
        Log.i("LOCK_STAT", "Called");
        saveCall(call);
        currentAddress = call.getString("address");
        Log.i("LOCK_STAT", "Address: " + currentAddress);
        if (currentAddress == null || currentAddress.equals("")) {
            Log.e("LOCK_STAT", "Non/Invalid MAC-Address provided.");
            call.reject("Non/Invalid MAC-Address provided.");
        }
        try {
            String secret = ByteArrayUtils.JSArrayToHexString(call.getArray("secret"));
            Log.i("LOCK_STAT", "Secret: " + secret);
            currentSecret = ByteArrayUtils.hexStringToByteArray(secret);
        } catch (JSONException ex) {
            Log.e("LOCK_STAT", ex.getLocalizedMessage(), ex);
            call.reject(ex.getLocalizedMessage(), ex);
            return;
        }

        callType = CallType.LOCK_STAT;
        enable();
    }

    //endregion

}
