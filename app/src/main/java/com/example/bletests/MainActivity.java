package com.example.bletests;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    public int REQUEST_ENABLE_BT = 100;
    public BluetoothAdapter bluetoothAdapter;
    public BluetoothLeService bluetoothLeService;
    public BluetoothManager bluetoothManager;
    public Context context;
    private String address = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = this;
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE Não Suportado", Toast.LENGTH_SHORT).show();
            finish();
        }
        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        registerReceiver(gattUpdateReceiver, intentFilter);
        bluetoothLeService = new BluetoothLeService(this);
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        findViewById(R.id.button).setOnClickListener(c -> {
            scanLeDevice();
        });
    }


    private BluetoothLeScanner bluetoothLeScanner =
            BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner();
    private boolean mScanning;
    private Handler handler = new Handler();

    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 15000;

    private void scanLeDevice() {
        if (!mScanning) {
            // Stops scanning after a pre-defined scan period.
            handler.postDelayed(() -> {
                mScanning = false;
                bluetoothLeScanner.stopScan(leScanCallback);
            }, SCAN_PERIOD);

            mScanning = true;
            Log.d("Scan", "Começou");
            bluetoothLeScanner.startScan(leScanCallback);
        } else {
            mScanning = false;
            Log.d("Scan", "Parou");
            bluetoothLeScanner.stopScan(leScanCallback);

        }
    }

    public class BluetoothLeService extends Service {
        private final String TAG = BluetoothLeService.class.getSimpleName();

        private BluetoothManager bluetoothManager;
        private BluetoothAdapter bluetoothAdapter;
        private String bluetoothDeviceAddress;
        private BluetoothGatt bluetoothGatt;
        private int connectionState = STATE_DISCONNECTED;
        private MainActivity activity;

        private static final int STATE_DISCONNECTED = 0;
        private static final int STATE_CONNECTING = 1;
        private static final int STATE_CONNECTED = 2;

        public final static String ACTION_GATT_CONNECTED =
                "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
        public final static String ACTION_GATT_DISCONNECTED =
                "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
        public final static String ACTION_GATT_SERVICES_DISCOVERED =
                "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
        public final static String ACTION_DATA_AVAILABLE =
                "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
        public final static String EXTRA_DATA =
                "com.example.bluetooth.le.EXTRA_DATA";

        public final UUID UUID_HEART_RATE_MEASUREMENT =
                UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");

        // Various callback methods defined by the BLE API.


        public BluetoothLeService(MainActivity activity) {
            this.activity = activity;
            this.bluetoothAdapter = activity.bluetoothAdapter;
            this.bluetoothManager = activity.bluetoothManager;
        }

        private final BluetoothGattCallback gattCallback =
                new BluetoothGattCallback() {
                    @Override
                    public void onConnectionStateChange(BluetoothGatt gatt, int status,
                                                        int newState) {
                        String intentAction;
                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            intentAction = ACTION_GATT_CONNECTED;
                            connectionState = STATE_CONNECTED;
                            broadcastUpdate(intentAction);
                            Log.i(TAG, "Connected to GATT server.");
                            Log.i(TAG, "Attempting to start service discovery:" +
                                    bluetoothGatt.discoverServices());

                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            intentAction = ACTION_GATT_DISCONNECTED;
                            connectionState = STATE_DISCONNECTED;
                            Log.i(TAG, "Disconnected from GATT server.");
                            broadcastUpdate(intentAction);
                        }
                    }

                    @Override
                    // New services discovered
                    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            bluetoothGatt.readCharacteristic(bluetoothGatt.getServices().get(0).getCharacteristics().get(0));
                            broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
                        } else {
                            Log.w(TAG, "onServicesDiscovered received: " + status);
                        }
                    }

                    @Override
                    // Result of a characteristic read operation
                    public void onCharacteristicRead(BluetoothGatt gatt,
                                                     BluetoothGattCharacteristic characteristic,
                                                     int status) {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            Log.d("Características", "Lido");
                            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
                        }
                    }

                };

        @Nullable
        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        public boolean connect(String address) {
            bluetoothDeviceAddress = address;
            bluetoothGatt = bluetoothAdapter.getRemoteDevice(bluetoothDeviceAddress).connectGatt(this, false, gattCallback);
            return true;
        }


        private void broadcastUpdate(final String action) {
            final Intent intent = new Intent(action);
            activity.sendBroadcast(intent);
        }

        private void broadcastUpdate(final String action,
                                     final BluetoothGattCharacteristic characteristic) {
            final Intent intent = new Intent(action);

            // This is special handling for the Heart Rate Measurement profile. Data
            // parsing is carried out as per profile specifications.
            if ("00002a37-0000-1000-8000-00805f9b34fb".equals(characteristic.getUuid())) {
                int flag = characteristic.getProperties();
                int format = -1;
                if ((flag & 0x01) != 0) {
                    format = BluetoothGattCharacteristic.FORMAT_UINT16;
                    Log.d(TAG, "Heart rate format UINT16.");
                } else {
                    format = BluetoothGattCharacteristic.FORMAT_UINT8;
                    Log.d(TAG, "Heart rate format UINT8.");
                }
                final int heartRate = characteristic.getIntValue(format, 1);
                Log.d(TAG, String.format("Received heart rate: %d", heartRate));
                intent.putExtra(EXTRA_DATA, String.valueOf(heartRate));
            } else {
                // For all other profiles, writes the data formatted in HEX.
                final byte[] data = characteristic.getValue();
                if (data != null && data.length > 0) {
                    final StringBuilder stringBuilder = new StringBuilder(data.length);
                    for (byte byteChar : data)
                        stringBuilder.append(String.format("%02X ", byteChar));
                    intent.putExtra(EXTRA_DATA, new String(data) + "\n" +
                            stringBuilder.toString());
                }
            }
            activity.sendBroadcast(intent);
        }

        public List<BluetoothGattService> getSupportedGattServices() {
            if (bluetoothGatt == null) return null;
            return bluetoothGatt.getServices();
        }

        public void writeToService(String charUuid, String serviceUuid, byte[] value){
            BluetoothGattCharacteristic characteristic = bluetoothGatt.getService(UUID.fromString(serviceUuid)).getCharacteristic(UUID.fromString(charUuid));
            characteristic.setValue(value);
            bluetoothGatt.writeCharacteristic(characteristic);
        }
    }

    private final BroadcastReceiver gattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                Log.d("Conexão GATT", "Conectado");
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                Log.d("Conexão GATT", "DESconectado");
            } else if (BluetoothLeService.
                    ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the
                // user interface.
                Log.d("Conexão GATT", "Serviços Descobertos");
                displayGattServices(bluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                Log.d("Conexão GATT", "Dados de Ações Disponíveis");
                Log.d("Dados", intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };
//0000ffe0-0000-1000-8000-00805f9b34fb
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        for (BluetoothGattService gattService : gattServices) {
            Log.d("Service", gattService.getUuid().toString());
            if(gattService.getUuid().toString().equals("0000ffe0-0000-1000-8000-00805f9b34fb")){
                for(BluetoothGattCharacteristic c: gattService.getCharacteristics()){
                    if(c.getUuid().toString().equals("0000ffe1-0000-1000-8000-00805f9b34fb")){
                        String data = "Blau";
                        bluetoothLeService.writeToService(c.getUuid().toString(), gattService.getUuid().toString(), data.getBytes());
                        Log.d("Service", "Blau");
                    }
                }
            }
        }
    }

    // Device scan callback.
    private ScanCallback leScanCallback =
            new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    if (result.getDevice() != null) {
                        if (result.getDevice().getName() != null && result.getDevice().getAddress() != null) {
                            Log.d("Device Search", result.getDevice().getName());
                            //Coloque o nome do dispositivo que deseja conectar
                            if (address == null && result.getDevice().getName().equals("Slave01")) {
                                address = result.getDevice().getAddress();
                                bluetoothLeService.connect(address);
                                super.onScanResult(callbackType, result);
                            }
                        }
                    }
                }
            };

}