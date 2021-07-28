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
        OPEN,
        CLOSE,
        BATT_STAT,
        LOCK_STAT
    }

    private class LockRequest {

        //region Fields

        //region Attributes

        private String name = new String();
        private String address = new String();
        private byte[] secret = { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
        private byte[] password = { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };

        private PluginCall pluginCall = null;
        private CallType callType = CallType.OPEN;
        private byte[] token = { 0x00, 0x00, 0x00, 0x00 };

        //endregion

        //region Members

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
                    Disable();
                    Connect(btDevice);
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
                                Connect(device);
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
                                mWriteCharacteristic = services.get(i).getCharacteristics().get(j);
                                gatt.setCharacteristicNotification(mWriteCharacteristic, true);
                                Log.i("SERVICE-CHARACTERISTIC", "Write: " + mWriteCharacteristic.toString());

                                for (int k = 0; k < services.get(i).getCharacteristics().get(j).getDescriptors().size(); k++) {
                                    services.get(i).getCharacteristics().get(j).getDescriptors().get(k).setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                    boolean success = gatt.writeDescriptor(services.get(i).getCharacteristics().get(j).getDescriptors().get(k));
                                    Log.i("onDescriptorsDiscovered", String.format("%d", success));
                                    Log.i("onDescriptorsDiscovered", services.get(i).getCharacteristics().get(j).getDescriptors().get(k).toString());
                                }
                            } else if (charUUID.equals("000036f6")) {
                                Log.i("SERVICE-CHARACTERISTIC", "Read ");
                                mReadCharacteristic = services.get(i).getCharacteristics().get(j);
                                gatt.setCharacteristicNotification(mReadCharacteristic, true);
                                Log.i("SERVICE-CHARACTERISTIC", "Read: " + mReadCharacteristic.toString());

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
                    Log.i("CHARACTERISTIC-WRITE", ByteArrayUtils.byteArrayToHexString(EncryptionUtils.Decrypt(characteristic.getValue(), secret)));
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
                byte[] values = EncryptionUtils.Decrypt(characteristic.getValue(), secret);
                Log.i("CHARACTERISTIC-CHANGE", ByteArrayUtils.byteArrayToHexString(values));

                //TOKEN
                if(values[0] == 0x06 && values[1] == 0x02){
                    token = Arrays.copyOfRange(values, 3, 7);
                    Log.i("TOKEN", ByteArrayUtils.byteArrayToHexString(token));

                    switch(callType) {
                        case OPEN:
                            Open();
                            break;
                        case CLOSE:
                            Close();
                            break;
                        case BATT_STAT:
                            BatteryStatus();
                            break;
                        case LOCK_STAT:
                            LockStatus();
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
                    ret.put("percentage", (float) Integer.parseInt(result, 16));
                }
                //LOCK_STAT
                if(values[0] == 0x05 && values[1] == 0x0F && values[2] == 0x01) {
                    String result = String.format("%02x", values[3]);
                    Log.i("LOCK_STAT", result);
                    ret.put("locked", result.equals("00") ? false : true);
                }
                pluginCall.resolve(ret);
                Disconnect();
            }

            @Override
            public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                Log.i("DESCRIPTOR-READ", descriptor.toString());
            }

            @Override
            public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                Log.i("DESCRIPTOR-WRITE", descriptor.toString());

                Token();
            }

        };

        //endregion



        public LockRequest(PluginCall pluginCall, CallType callType, String name, String address, byte[] secret, byte[] password) {
            this.pluginCall = pluginCall;
            this.callType = callType;

            this.name = name;
            this.address = address;
            this.secret = secret;
            this.password = password;
        }



        //region Methods

        public void Send() {
            mScanHandler = new Handler();
            // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
            // BluetoothAdapter through BluetoothManager.
            Log.i("HANDLEONSTART", "BL-Manager");
            final BluetoothManager bluetoothManager =
                    (BluetoothManager) plugin.getContext().getSystemService(Context.BLUETOOTH_SERVICE);
            Log.i("HANDLEONSTART", "BL-Adapter");
            mBluetoothAdapter = bluetoothManager.getAdapter();

            Enable();
        }

        /*
            Requests bluetooth to be enabled and scans for filtered/wanted locks and also connects to it.
            Returns if the scan has started.
             */
        private void Enable() {
            Log.i("ENABLE", "Called");

            if (!plugin.hasRequiredPermissions()) {
                pluginCall.reject("Permissions not granted.");
                plugin.pluginRequestAllPermissions();
                return;
            }

            // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
            // fire an intent to display a dialog asking the user to grant permission to enable it.
            if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) { // Kein Bluetooth Adapter gesetzt und deaktiviert?
                Log.i("ENABLE-BLUETOOTH", "Bluetooth not enabled");
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(pluginCall, enableBtIntent, REQUEST_ENABLE_BT);
            } else {
                if (Build.VERSION.SDK_INT >= 21) {
                    mScanner = mBluetoothAdapter.getBluetoothLeScanner();
                    mScanSettings = new ScanSettings.Builder()
                            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                            .build();
                    mScanFilters = new ArrayList<>();
                    ScanFilter.Builder scanFilterBuilder = new ScanFilter.Builder()
                            .setDeviceAddress(address);
                    if (name != null && !name.equals(new String())) {
                        scanFilterBuilder.setDeviceName(name);
                    }
                    ScanFilter scanFilter = scanFilterBuilder.build();
                    mScanFilters.add(scanFilter);
                    Log.i("ENABLE-SCANFILTER", scanFilter.toString());
                }
                Scan(true);
            }
        }

        /*
        Stops scaning for devices and disconnects connected device.
         */
        private void Disable() {
            Log.i("DISABLE", "Called");
            if (Build.VERSION.SDK_INT < 21 && mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
                Scan(false);
            } else if (mScanner != null) {
                Scan(false);
            }
        }

        /*
        Scans for available devices and connects.
         */
        private void Scan(final boolean enable) {
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
        private void Connect(BluetoothDevice device) {
            Log.i("CONNECT", "Called " + device.toString());
            if (mGatt != null) {
                Disconnect();
            }
            mGatt = device.connectGatt(plugin.getContext(), false, gattCallback);
        }

        /*
        If a device is connected, it will be disconnected.
         */
        private void Disconnect() {
            Log.i("DISCONNECT", "Called");
            if (mGatt == null) {
                return;
            }
            mGatt.disconnect();
        }

        private void Token() {
            Log.i("TOKEN", "Called");
            byte[] content = ByteArrayUtils.hexStringToByteArray("060101013762556c68731d6d7e173b4d");
            byte[] values = EncryptionUtils.Encrypt(content, secret);

            this.mWriteCharacteristic.setValue(values);
            Log.i("CHARACTERISTIC-TOWRITE", ByteArrayUtils.byteArrayToHexString(this.mWriteCharacteristic.getValue()));
            mWriteCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            boolean success = mGatt.writeCharacteristic(this.mWriteCharacteristic);
            Log.i("CHARACTERISTIC-TOWRITE", "Called: " + success);
        }

        private void Open() {
            Log.i("OPEN", "Called");
            byte[] content = ByteArrayUtils.hexStringToByteArray("050106" + ByteArrayUtils.byteArrayToHexString(password) + ByteArrayUtils.byteArrayToHexString(token) + "303030");
            byte[] values = EncryptionUtils.Encrypt(content, secret);

            Log.i("CHARACTERISTIC-TOWRITE", this.mWriteCharacteristic.toString());
            this.mWriteCharacteristic.setValue(values);
            Log.i("CHARACTERISTIC-TOWRITE", this.mWriteCharacteristic.getValue().toString());
            mGatt.writeCharacteristic(this.mWriteCharacteristic);
            Log.i("CHARACTERISTIC-TOWRITE", "Called");
        }

        private void Close() {
            Log.i("CLOSE", "Called");
            byte[] content = ByteArrayUtils.hexStringToByteArray("050C0101" + ByteArrayUtils.byteArrayToHexString(token) + "3030303030303030");
            byte[] values = EncryptionUtils.Encrypt(content, secret);

            Log.i("CHARACTERISTIC-TOWRITE", this.mWriteCharacteristic.toString());
            this.mWriteCharacteristic.setValue(values);
            Log.i("CHARACTERISTIC-TOWRITE", this.mWriteCharacteristic.getValue().toString());
            mGatt.writeCharacteristic(this.mWriteCharacteristic);
        }

        private void BatteryStatus() {
            Log.i("BATT_STAT", "Called");
            byte[] content = ByteArrayUtils.hexStringToByteArray("02010101" + ByteArrayUtils.byteArrayToHexString(token) + "3030303030303030");
            byte[] values = EncryptionUtils.Encrypt(content, secret);

            Log.i("CHARACTERISTIC-TOWRITE", this.mWriteCharacteristic.toString());
            this.mWriteCharacteristic.setValue(values);
            Log.i("CHARACTERISTIC-TOWRITE", this.mWriteCharacteristic.getValue().toString());
            mGatt.writeCharacteristic(this.mWriteCharacteristic);
        }

        private void LockStatus() {
            Log.i("LOCK_STAT", "Called");
            byte[] content = ByteArrayUtils.hexStringToByteArray("050E0101" + ByteArrayUtils.byteArrayToHexString(token) + "3030303030303030");
            byte[] values = EncryptionUtils.Encrypt(content, secret);

            Log.i("CHARACTERISTIC-TOWRITE", this.mWriteCharacteristic.toString());
            this.mWriteCharacteristic.setValue(values);
            Log.i("CHARACTERISTIC-TOWRITE", this.mWriteCharacteristic.getValue().toString());
            mGatt.writeCharacteristic(this.mWriteCharacteristic);
        }

        //endregion

    }

    //endregion



    //region Static-Fields

    @NativePlugin
    protected static final int REQUEST_ENABLE_BT = 1;
    private static final long SCAN_PERIOD = 10000; // msec

    protected static Own2MeshOkLokPlugin plugin;

    //endregion



    //region Plugin-Callbacks

    @Override
    protected void handleOnStart() {
        Log.i("HANDLEONSTART", "Called");
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

        super.handleOnStart();
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

        String name = call.getString("name");
        Log.i("OPEN", "Name: " + name);

        String address = call.getString("address");
        Log.i("OPEN", "Address: " + address);
        if (address == null || address.equals("") || !BluetoothAdapter.checkBluetoothAddress(address)) {
            Log.e("OPEN", "Non/Invalid MAC-Address provided.");
            call.reject("Non/Invalid MAC-Address provided.");
        }

        byte[] secret = null;
        try {
            String secretString = ByteArrayUtils.JSArrayToHexString(call.getArray("secret"));
            Log.i("OPEN", "Secret: " + secret);
            secret = ByteArrayUtils.hexStringToByteArray(secretString);
        } catch (JSONException ex) {
            Log.e("OPEN", ex.getLocalizedMessage(), ex);
            call.reject(ex.getLocalizedMessage(), ex);
            return;
        }

        byte[] password = null;
        try {
            String passwordString = ByteArrayUtils.JSArrayToHexString(call.getArray("pw"));
            Log.i("OPEN", "Password: " + passwordString);
            password = ByteArrayUtils.hexStringToByteArray(passwordString);
        } catch (JSONException ex) {
            Log.e("OPEN", ex.getLocalizedMessage(), ex);
            call.reject(ex.getLocalizedMessage(), ex);
            return;
        }

        LockRequest request = new LockRequest(call, CallType.OPEN, name, address, secret, password);
        request.Send();
    }

    @PluginMethod
    public void close(PluginCall call) {
        Log.i("CLOSE", "Called");
        saveCall(call);

        String name = call.getString("name");
        Log.i("OPEN", "Name: " + name);

        String address = call.getString("address");
        Log.i("CLOSE", "Address: " + address);
        if (address == null || address.equals("") || !BluetoothAdapter.checkBluetoothAddress(address)) {
            Log.e("CLOSE", "Non/Invalid MAC-Address provided.");
            call.reject("Non/Invalid MAC-Address provided.");
        }

        byte[] secret = null;
        try {
            String secretString = ByteArrayUtils.JSArrayToHexString(call.getArray("secret"));
            Log.i("CLOSE", "Secret: " + secretString);
            secret = ByteArrayUtils.hexStringToByteArray(secretString);
        } catch (JSONException ex) {
            Log.e("CLOSE", ex.getLocalizedMessage(), ex);
            call.reject(ex.getLocalizedMessage(), ex);
            return;
        }

        LockRequest request = new LockRequest(call, CallType.CLOSE, name, address, secret, null);
        request.Send();
    }

    @PluginMethod
    public void battery_status(PluginCall call) {
        Log.i("BATT_STAT", "Called");
        saveCall(call);

        String name = call.getString("name");
        Log.i("OPEN", "Name: " + name);

        String address = call.getString("address");
        Log.i("BATT_STAT", "Address: " + address);
        if (address == null || address.equals("") || !BluetoothAdapter.checkBluetoothAddress(address)) {
            Log.e("BATT_STAT", "Non/Invalid MAC-Address provided.");
            call.reject("Non/Invalid MAC-Address provided.");
        }

        byte[] secret = null;
        try {
            String secretString = ByteArrayUtils.JSArrayToHexString(call.getArray("secret"));
            Log.i("BATT_STAT", "Secret: " + secretString);
            secret = ByteArrayUtils.hexStringToByteArray(secretString);
        } catch (JSONException ex) {
            Log.e("BATT_STAT", ex.getLocalizedMessage(), ex);
            call.reject(ex.getLocalizedMessage(), ex);
            return;
        }

        LockRequest request = new LockRequest(call, CallType.BATT_STAT, name, address, secret, null);
        request.Send();
    }

    @PluginMethod
    public void lock_status(PluginCall call) {
        Log.i("LOCK_STAT", "Called");
        saveCall(call);

        String name = call.getString("name");
        Log.i("OPEN", "Name: " + name);

        String address = call.getString("address");
        Log.i("LOCK_STAT", "Address: " + address);
        if (address == null || address.equals("") || !BluetoothAdapter.checkBluetoothAddress(address)) {
            Log.e("LOCK_STAT", "Non/Invalid MAC-Address provided.");
            call.reject("Non/Invalid MAC-Address provided.");
        }

        byte[] secret = null;
        try {
            String secretString = ByteArrayUtils.JSArrayToHexString(call.getArray("secret"));
            Log.i("LOCK_STAT", "Secret: " + secretString);
            secret = ByteArrayUtils.hexStringToByteArray(secretString);
        } catch (JSONException ex) {
            Log.e("LOCK_STAT", ex.getLocalizedMessage(), ex);
            call.reject(ex.getLocalizedMessage(), ex);
            return;
        }

        LockRequest request = new LockRequest(call, CallType.LOCK_STAT, name, address, secret, null);
        request.Send();
    }

    //endregion

}
