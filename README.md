
# bluetoothtest

This project will allow you to scan Bluetooth devices from your Android device. A list of scanned devices will be shown to the user.

When one of the devices is clicked, it will try to connect to it.

Once connected, you can send a sequence of characters to it or disconnect. 

#GATT

GATT is an acronym for the Generic Attribute Profile, and it defines the way that two Bluetooth Low Energy devices transfer data back and forth using concepts called Services and Characteristics. It makes use of a generic data protocol called the Attribute Protocol (ATT), which is used to store Services, Characteristics and related data in a simple lookup table using 16-bit IDs for each entry in the table.

GATT comes into play once a dedicated connection is established between two devices, meaning that you have already gone through the advertising process governed by GAP.

The most important thing to keep in mind with GATT and connections is that connections are exclusive. What is meant by that is that a BLE peripheral can only be connected to one central device (a mobile phone, etc.) at a time! As soon as a peripheral connects to a central device, it will stop advertising itself and other devices will no longer be able to see it or connect to it until the existing connection is broken.

Establishing a connection is also the only way to allow two way communication, where the central device can send meaningful data to the peripheral and vice versa.

# Project content

* Main activity: it is the only UI for this simple example. 
It contains a switch to start/stop scanning for BLE devices and a list of the scanned devices.
When one of the devices in the list is clicked, options to disconnect or send a message to the device will be shown to the user.

* BluetoothLeScannerCompat.getScanner() from nordicsemi scanner library is used to scan

* UARTService: service used to connect and send message to a device. 

* UARTManager: Connects/disconnects to GATT Server hosted by this device and sends the given text to RX characteristic.

* BluetoothDeviceWithStrength is the model representing a Bluetooth Device plus its strength.

* BluetoothDevicesAdapter and BluetoothDeviceView are used to manage and show devices objects.
