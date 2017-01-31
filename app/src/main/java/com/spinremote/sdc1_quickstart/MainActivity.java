package com.spinremote.sdc1_quickstart;

import android.Manifest;
import android.annotation.TargetApi;
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
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.ParcelUuid;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

import java.util.List;
import java.util.UUID;

/**
 * This Activity will, if visible on the screen, scan for SPIN remote SDC-1s and connect to the
 * first one it finds. When connected and the Bluetooth Services are discovered, it will turn on
 * notifications on the Action Characteristic and force these notifications. The address, RSSI (only
 * updated when searching) and actions of the SPIN remote SDC-1 will be displayed on the screen.
 *
 * Note that this example is targeted for API 21 and higher, for API 18 - 21, please refer to
 * https://developer.android.com/guide/topics/connectivity/bluetooth-le.html.
 *
 * Make sure to check out the permissions required for this example in the AndroidManifest if you
 * want to use Bluetooth in your own project.
 *
 * Created by Mathijs van Bremen on 31/01/2017.
 * Copyright © 2017 SPIN remote B.V. All rights reserved.
 */
@TargetApi(21)
public class MainActivity extends AppCompatActivity {
    /**
     * BluetoothAdapter which is required for any and all Bluetooth activity
     */
    private BluetoothAdapter bluetoothAdapter;
    /**
     * Locally-defined integer (must be greater than 0) that the system passes back to you in the
     * {@link #onActivityResult(int, int, Intent)} implementation as the requestCode parameter
     */
    private static final int REQUEST_ENABLE_BT = 1;
    /**
     * Locally-defined integer passed back by the system in the
     * {@link #onRequestPermissionsResult(int, String[], int[])} implementation when requesting
     * ACCESS_COARSE_LOCATION permission
     */
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 2;

    /**
     * UUID used to identify a SPIN remote SDC-1, stored as a {@link ParcelUuid} as we get a list
     * with ParcelUuids from a {@link ScanResult} instead of {@link UUID}
     *
     * @see ScanResult
     */
    private static final ParcelUuid DISCOVERY_UUID = ParcelUuid.fromString("9DFACA9D-7801-22A0-9540-F0BB65E824FC");
    /**
     * {@link UUID} of the SPIN service, used to get the Bluetooth Service so we can get the
     * Characteristics (Command and Action Characteristics)
     */
    private static final UUID SPIN_SERVICE_UUID = UUID.fromString("5E5A10D3-6EC7-17AF-D743-3CF1679C1CC7");
    /**
     * {@link UUID} of the Command characteristic so we can force the action notifications
     */
    private static final UUID COMMAND_CHARACTERISTIC_UUID = UUID.fromString("92E92B18-FA20-D486-5E43-099387C61A71");
    /**
     * {@link UUID} of the Action characteristic so we can turn on action notifications
     */
    private static final UUID ACTION_CHARACTERISTIC_UUID = UUID.fromString("182BEC1F-51A4-458E-4B48-C431EA701A3B");
    /**
     * {@link UUID} we need to get the descriptor to turn on the action notifications
     */
    private static final UUID UUID_CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    /**
     * {@link ScanCallback} we'll pass to the
     * {@link BluetoothLeScanner#startScan(ScanCallback)} method to get notified whenever we find a
     * BLE device
     */
    private final ScanCallback scanCallback = new ScanCallback() {
        /**
         * Callback when a BLE advertisement has been found.
         *
         * @param callbackType Determines how this callback was triggered.
         * @param result       A Bluetooth LE scan result.
         */
        @Override
        public void onScanResult(int callbackType, final ScanResult result) {
            super.onScanResult(callbackType, result);

            // Get the ScanRecord and check if it is defined (is nullable)
            final ScanRecord scanRecord = result.getScanRecord();
            if (scanRecord != null) {
                // Check if the Service UUIDs are defined (is nullable) and contain the discovery
                // UUID
                final List<ParcelUuid> serviceUuids = scanRecord.getServiceUuids();
                if (serviceUuids != null && serviceUuids.contains(DISCOVERY_UUID)) {
                    // We have found our device, so update the GUI, stop scanning and start
                    // connecting
                    final BluetoothDevice device = result.getDevice();

                    // We'll make sure the GUI is updated on the UI thread
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // At this point we have the device address and RSSI, so update those
                            deviceAddressTextView.setText(device.getAddress());
                            rssiTextView.setText(getString(R.string.rssi, result.getRssi()));
                        }
                    });

                    stopDiscovery();

                    bluetoothGatt = device.connectGatt(
                            MainActivity.this,
                            // False here, as we want to directly connect to the device
                            false,
                            bluetoothGattCallback
                    );
                }
            }
        }
    };
    /**
     * The {@link BluetoothGattCallback} we will use to get actions from a SPIN remote SDC-1.
     * Steps:
     *      1. Wait for {@link BluetoothProfile#STATE_CONNECTED} callback
     *      2. Discover the Bluetooth Services
     *      3. Wait for the Bluetooth Service discovery to finish
     *      4. Set LED color to red (0xFF0000)
     *      5. Wait for the LED color to be set
     *      6. Enable the action notification
     *      7. Wait for the action notification to be enabled
     *      8. Force the action notification
     */
    private final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
        /**
         * Callback indicating when GATT client has connected/disconnected to/from a remote
         * GATT server.
         *
         * @param gatt     GATT client
         * @param status   Status of the connect or disconnect operation.
         *                 {@link BluetoothGatt#GATT_SUCCESS} if the operation succeeds.
         * @param newState Returns the new connection state. Can be one of
         *                 {@link android.bluetooth.BluetoothProfile#STATE_DISCONNECTED} or
         *                 {@link android.bluetooth.BluetoothProfile#STATE_CONNECTED}
         */
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            // boolean indicating whether or not the next step is successful, default is false
            boolean success = false;

            // Start Service discovery if we're now connected
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    success = gatt.discoverServices();
                } // else: not connected, continue
            } // else: not successful

            onStep(gatt, success);
        }

        /**
         * Callback invoked when the list of remote services, characteristics and descriptors
         * for the remote device have been updated, ie new services have been discovered.
         *
         * @param gatt   GATT client invoked {@link BluetoothGatt#discoverServices}
         * @param status {@link BluetoothGatt#GATT_SUCCESS} if the remote device
         */
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);

            // boolean indicating whether or not the next step is successful, default is false
            boolean success = false;

            // Check if Service discovery was successful and set the LED color to red if it was
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Check if the SPIN Service is found
                final BluetoothGattService spinService = gatt.getService(SPIN_SERVICE_UUID);
                if (spinService != null) {
                    // Check if the Command Characteristic is found, write the new value and store
                    // the result
                    final BluetoothGattCharacteristic commandCharacteristic
                            = spinService.getCharacteristic(COMMAND_CHARACTERISTIC_UUID);
                    if (commandCharacteristic != null) {
                        // Set the value to 0x09FF0000
                        commandCharacteristic.setValue(
                                new byte[]{
                                        (byte) 0x09,    // commandId = set LED color (9)
                                        (byte) 0xFF,    // red      = 0x00 - 0xFF (0 - 255)
                                        (byte) 0x00,    // blue     = 0x00 - 0xFF (0 - 255)
                                        (byte) 0x00     // green    = 0x00 - 0xFF (0 - 255)
                                }
                        );

                        success = gatt.writeCharacteristic(commandCharacteristic);
                    }
                }
            }

            onStep(gatt, success);
        }

        /**
         * Callback indicating the result of a characteristic write operation.
         *
         * <p>If this callback is invoked while a reliable write transaction is
         * in progress, the value of the characteristic represents the value
         * reported by the remote device. An application should compare this
         * value to the desired value to be written. If the values don't match,
         * the application must abort the reliable write transaction.
         *
         * @param gatt GATT client invoked {@link BluetoothGatt#writeCharacteristic}
         * @param characteristic Characteristic that was written to the associated
         *                       remote device.
         * @param status The result of the write operation
         *               {@link BluetoothGatt#GATT_SUCCESS} if the operation succeeds.
         */
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);

            // boolean indicating whether or not the next step is successful, default is false
            boolean success = false;

            // Check if writing was successful and check what it was that we have written so we can
            // determine the next step
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (characteristic.getUuid().equals(COMMAND_CHARACTERISTIC_UUID)) {
                    final byte[] value = characteristic.getValue();

                    // Check if we have written the "Set LED color" command and go to the next step
                    // (enabling action notification, step 6) if it is
                    if ((value != null) && (value.length > 0)) {
                        if (value[0] == (byte) 0x09) {
                            // Check if the SPIN Service is found
                            final BluetoothGattService spinService = gatt.getService(SPIN_SERVICE_UUID);
                            if (spinService != null) {
                                // Check if the Action Characteristic is found
                                final BluetoothGattCharacteristic actionCharacteristic
                                        = spinService.getCharacteristic(ACTION_CHARACTERISTIC_UUID);
                                if (actionCharacteristic != null) {
                                    // Enable notifications for the descriptor and store the result
                                    final BluetoothGattDescriptor descriptor
                                            = actionCharacteristic.getDescriptor(UUID_CLIENT_CHARACTERISTIC_CONFIG);
                                    if (descriptor != null) {
                                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);

                                        success = gatt.setCharacteristicNotification(actionCharacteristic, true)
                                                && gatt.writeDescriptor(descriptor);
                                    }
                                }
                            }
                        } else {
                            // There's no next step, set success to true
                            success = true;
                        }
                    }
                }
            }

            onStep(gatt, success);
        }

        /**
         * Callback triggered as a result of a remote characteristic notification.
         *
         * @param gatt           GATT client the characteristic is associated with
         * @param characteristic Characteristic that has been updated as a result
         */
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);

            // Get the action from the data and update the action TextView
            final int action = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0); // uint8 (byte0), offset 0
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    actionTextView.setText(
                            // We format the String using the current action and the action
                            // description for the current action
                            getString(R.string.action, action, getActionDescriptionForAction(action))
                    );
                }
            });
        }

        /**
         * Callback indicating the result of a descriptor write operation.
         *
         * @param gatt       GATT client invoked {@link BluetoothGatt#writeDescriptor}
         * @param descriptor Descriptor that was writte to the associated
         *                   remote device.
         * @param status     The result of the write operation
         *                   {@link BluetoothGatt#GATT_SUCCESS} if the operation succeeds.
         */
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);

            // boolean indicating whether or not the next step is successful, default is false
            boolean success = false;

            // Check if writing descriptor was successful and force the action notification if it
            // was
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Check if the SPIN Service is found
                final BluetoothGattService spinService = gatt.getService(SPIN_SERVICE_UUID);
                if (spinService != null) {
                    // Check if the Command Characteristic is found, write the new value and store
                    // the result
                    final BluetoothGattCharacteristic commandCharacteristic
                            = spinService.getCharacteristic(COMMAND_CHARACTERISTIC_UUID);
                    if (commandCharacteristic != null) {
                        // Set the value to 0x0801
                        commandCharacteristic.setValue(
                                new byte[]{
                                        (byte) 0x08,    // commandId = force action notification (8)
                                        (byte) 0x01     // enable = false (0) or true (1)
                                }
                        );

                        success = gatt.writeCharacteristic(commandCharacteristic);
                    }
                }
            }

            onStep(gatt, success);
        }

        /**
         * Will handle cleaning up resources and starting the discovery again if the step was not
         * successful
         *
         * @param gatt The {@link BluetoothGatt} to clean up
         * @param success boolean indicating whether or not step was successful
         */
        private void onStep(BluetoothGatt gatt, boolean success) {
            // Close the BluetoothGatt, update GUI and start scanning again if step was not
            // successful
            if (!success) {
                gatt.close();
                bluetoothGatt = null;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        deviceAddressTextView.setText("");
                    }
                });
                startDiscovery();
            }
        }
    };
    /**
     * Current {@link BluetoothGatt} object we are connected/connecting with
     */
    private BluetoothGatt bluetoothGatt;

    /**
     * TextView used to display the device address of the current SPIN remote SDC-1
     */
    private TextView deviceAddressTextView;
    /**
     * TextView used to display the last action from the current SPIN remote SDC-1
     */
    private TextView actionTextView;
    /**
     * TextView used to display the last RSSI from the current SPIN remote SDC-1
     */
    private TextView rssiTextView;

    /**
     * Array containing descriptions of each action (index in array equals action)
     */
    private String[] actionDescriptions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // We set the content View of this Activity
        setContentView(R.layout.activity_main);

        // Get all the TextViews
        deviceAddressTextView = (TextView) findViewById(R.id.device_address);
        actionTextView = (TextView) findViewById(R.id.action);
        rssiTextView = (TextView) findViewById(R.id.rssi);

        // Get the descriptions of the actions
        actionDescriptions = getResources().getStringArray(R.array.action_descriptions);

        // Get the BluetoothManager so we can get the BluetoothAdapter
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Start the scan again if we were expecting this result
        switch (requestCode) {
            case REQUEST_ENABLE_BT: {
                startDiscovery();

                break;
            }

            default:
                super.onActivityResult(requestCode, resultCode, data);

                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        // Start the scan again if we were expecting this result
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                startDiscovery();

                break;
            }

            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);

                break;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Start scanning for SPIN remote SDC-1s
        startDiscovery();
    }

    @Override
    protected void onStop() {
        super.onStop();

        // Stop scanning for SPIN remote SDC-1s
        stopDiscovery();

        // Clean up resources if the BluetoothGatt is defined
        if (bluetoothGatt != null) {
            /* Here we first cancel the LED override of SPIN remote SDC-1, so that it can again
             * manage its own LED color based on the active profile.
             *
             * Note that this code is only meant to demonstrate how to cancel the LED override. The
             * SPIN remote SDC-1 will automatically return to the active profile LED color when the
             * connection is closed.
             */
            // Check if the SPIN Service is found
            final BluetoothGattService spinService = bluetoothGatt.getService(SPIN_SERVICE_UUID);
            if (spinService != null) {
                // Check if the Command Characteristic is found and write the new value
                final BluetoothGattCharacteristic commandCharacteristic
                        = spinService.getCharacteristic(COMMAND_CHARACTERISTIC_UUID);
                if (commandCharacteristic != null) {
                    // Set the value to 0x07
                    commandCharacteristic.setValue(
                            new byte[]{
                                    (byte) 0x07     // commandId = cancel LED override (7)
                            }
                    );

                    bluetoothGatt.writeCharacteristic(commandCharacteristic);
                }
            }

            // Close the BluetoothGatt, closing the connection and cleaning up any resources
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
    }

    private void startDiscovery() {
        // Check if Bluetooth is enabled. If not, display a dialog requesting user permission to
        // enable Bluetooth.
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            final Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);

            return;
        } // else: Bluetooth is enabled

        // On Android Marshmallow (6.0) and higher, we need ACCESS_COARSE_LOCATION or
        // ACCESS_FINE_LOCATION permission to get scan results, so check if we have. If not, ask the
        // user for permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        PERMISSION_REQUEST_COARSE_LOCATION
                );

                return;
            } // else: permission already granted
        } // else: running on older version of Android

        // Start scanning
        bluetoothAdapter.getBluetoothLeScanner().startScan(scanCallback);
    }

    private void stopDiscovery() {
        // Check if Bluetooth is enabled and stop scanning (will crash if disabled)
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            bluetoothAdapter.getBluetoothLeScanner().stopScan(scanCallback);
        } // else: Bluetooth is enabled
    }

    private String getActionDescriptionForAction(int action) {
        // Return the action description for given action if within array range. If not, return an
        // "unknown" string
        if (action >= 0 && action < actionDescriptions.length) {
            return actionDescriptions[action];
        } else {
            return getString(R.string.unknown_action_description);
        }
    }

}
