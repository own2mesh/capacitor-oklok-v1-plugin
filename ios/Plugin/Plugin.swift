import Foundation
import Capacitor
import CoreBluetooth
import CommonCrypto

let UUID_LOCK_SERVICE = "FEE7"
let UUID_LOCK_DATA = "36F6" // read (notify) update wenn sich was ändert
let UUID_LOCK_CONFIG = "36F5" // write

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitor.ionicframework.com/docs/plugins/ios
 */
@objc(Own2MeshOkLokPlugin)
public class Own2MeshOkLokPlugin: CAPPlugin, CBPeripheralDelegate, CBCentralManagerDelegate {
    
    private var call: CAPPluginCall!
    
    // Properties, private variables to store the actual central manager and peripheral
    private var centralManager: CBCentralManager!
    private var peripheral: CBPeripheral!
    private var readCharacteristic: CBCharacteristic!
    private var writeCharacteristic: CBCharacteristic!
    
    private var peripheralName: String = ""
    
    var arrayPeripheral: [CBPeripheral] = []
    var arrayPeripheralStringName:[String] = []
    
    private var address: String = "" // Bluetooth name of lock
    private var secretData: Data! // Secret key vor de/encryption
    
    private var pw: [UInt8] = [] // specific password for lock
    
    private var token: [UInt8]  = [0x00, 0x00, 0x00, 0x00]
    
    
    @objc func echo(_ call: CAPPluginCall) {
//        let value = call.getString("value") ?? ""
//        self.consoleLog = self.consoleLog + value
//        call.success([
//            "value": consoleLog
//        ])
    }
    
    @objc func open(_ call: CAPPluginCall) {
        guard let addressCheck = call.options["address"] as? String else {
          call.reject("Must provide an address")
          return
        }
        self.address = addressCheck
        
        guard let secretCheck = call.options["secret"] as? [UInt8] else {
          call.reject("Must provide an secret")
          return
        }
        self.secretData = Data(bytes: secretCheck, count: secretCheck.count)
        
        guard let pwCheck = call.options["pw"] as? [UInt8] else {
          call.reject("Must provide an password (pw)")
          return
        }
        self.pw = pwCheck
        
        self.iniCB()
        
        self.call = call
        
        
    }
    
    
    
    /*
     Bluetooth stuff
     
     Tutorial: https://www.freecodecamp.org/news/ultimate-how-to-bluetooth-swift-with-hardware-in-20-minutes/
     */
    
    private func iniCB() {
        centralManager = CBCentralManager(delegate: self, queue: nil, options: nil)
    }
    
    // If we're powered on, start scanning
    // centralManagerDidUpdateState updates when the Bluetooth Peripheral is switched on or off.
    // It will fire when an app first starts so you know the state of Bluetooth. We also start scanning here.
    public func centralManagerDidUpdateState(_ central: CBCentralManager) {
        if central.state != .poweredOn {
            self.call.reject("Bluetooth is not powered on")
        } else {
            print("Scanning for", self.address);
            centralManager.scanForPeripherals(withServices: nil, options: nil)
        }
    }
    
    
    // Handles the result of the scan
    // The centralManager didDiscover event occurs when you receive scan results. We'll use this to start a connection.
    public func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String : Any], rssi RSSI: NSNumber) {
        
        // LOGS
//        print("\n\n__advertisementData: ")
//        print(advertisementData);
        // LOGS END
        
        // Add deiscovered peripheral name to global variable "peripheralName"
        if (((advertisementData as NSDictionary).value(forKey: "kCBAdvDataLocalName")) != nil){
            self.peripheralName = peripheral.name ?? ""
//            print("\n\n__peripheralName: ", self.peripheralName) // LOGS
        }
                
        // Check if discovered peripheralName matches the given address
        if (!(self.peripheralName).isEmpty) {
            if (self.peripheralName == self.address) {
                // LOGS
//                print("####################################################\nAdress: " + self.address + " match with peripheralName: " + self.peripheralName + "\n####################################################\n")
                // LOGS END
                
                // We've found it so stop scan
                self.centralManager.stopScan()

                // Copy the peripheral instance
                self.peripheral = peripheral
                self.peripheral.delegate = self

                // Connect!
                self.centralManager.connect(self.peripheral, options: nil)
            }
        }
    }
    
    // The handler if we do connect succesfully
    // The centralManager didConnect event fires once the device is connected. We'll start the device discovery here.
    // Note: Device discovery is the way we determine what services and characteristics are available.
    // This is a good way to confirm what type of device we're connected to.
    public func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        if peripheral == self.peripheral {
//            print("Connected to Bluetooth Lock " + self.address) // LOG
            peripheral.discoverServices(nil)
        }
    }
    
    // Handles discovery event
    // The peripheral didDiscoverServices event first once all the services have been discovered.
    // Notice that we've switched from centralManager to peripheral now that we're connected. We'll start the characteristic discovery here. We'll be using the service UUID as the target.
    public func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        if ((error) != nil) {
            self.call.reject("Error discovering services: \(error!.localizedDescription)")
            return
        }
        
        guard let services = peripheral.services else {
            self.call.reject("No service discovered!")
            return
        }
        
        for service in services {
            if service.uuid == CBUUID.init(string: UUID_LOCK_SERVICE){
                // Lock service found
                // Now kick off discovery of characteristics
                peripheral.discoverCharacteristics(nil, for: service)
//                print("Discovered Services: \(services)") // LOG
                return
            }
        }
        
    }
    
    // Handling discovery of characteristics
    // The peripheral didDiscoverCharacteristicsFor event will provide all the characteristics using the provided service UUID.
    // This is the last step in the chain of doing a full device discovery. It's hairy but it only has to be done once during the connection phase!
    public func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        if ((error) != nil) {
            self.call.reject("Error discovering services: \(error!.localizedDescription)")
            return
        }
        
        guard let characteristics = service.characteristics else {
            return
        }
        
        for characteristic in characteristics {
            //looks for the right characteristic

            // Read Characteristic ?
            if (characteristic.uuid == CBUUID.init(string: UUID_LOCK_DATA)) {
                //Once found, subscribe to the this particular characteristic...
                self.peripheral.setNotifyValue(true, for: characteristic)
                self.readCharacteristic = characteristic
                
//                print("Read Characteristic: \(characteristic.uuid)\n") // LOG
            }
            
            // Write Characteristic ?
            if (characteristic.uuid == CBUUID.init(string: UUID_LOCK_CONFIG)) {
                self.writeCharacteristic = characteristic
                
//                print("Write Characteristic: \(characteristic.uuid)\n") // LOG
            }
            
            // Discover descriptors for each characteristic
//            peripheral.discoverDescriptors(for: characteristic) // IS missing and throws error

            // Discovered Read / Write Characteristic?
            if ((self.readCharacteristic != nil) && (self.writeCharacteristic != nil))
            {
                // Send token requst
                let valData: [UInt8] = [0x06, 0x01, 0x01, 0x01, 0x37, 0x62, 0x55, 0x6c, 0x68, 0x73, 0x1d, 0x6d, 0x7e, 0x17, 0x3b, 0x4d] // BLE Communication-v1.pdf, Page 4
                let data: Data = Data(bytes: valData, count: valData.count)

                var encryptedData :Data?
                do {
                    encryptedData = try aesCBCEncrypt(data: data, keyData: self.secretData) // Do encryption
//                    print("cryptData:   \(encryptedData! as NSData)") (( LOG))
                    
                    self.peripheral.writeValue(encryptedData!, for: self.writeCharacteristic, type: .withoutResponse)
                }
                catch (let status) {
                    self.call.reject("Error aesCBCEncrypt: \(status)")
                }
            }
        }
    }

    public func peripheral(_ peripheral: CBPeripheral, didUpdateNotificationStateFor characteristic: CBCharacteristic, error: Error?) {
        if ((error) != nil) {
            self.call.reject("Error update notification state for characteristic: \(error!.localizedDescription)")
            return
        }
    }
    
    public func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        if ((error) != nil) {
            self.call.reject("Error update value for characteristic: \(error!.localizedDescription)")
            return
        } else {
            // LOG
//            print("Update value for characteristic:\n", characteristic)
//            print("--------------------------------------------")
//            print("Characteristic UUID: \(characteristic.uuid)")
//            print("Characteristic isNotifying: \(characteristic.isNotifying)")
//            print("Characteristic properties: \(characteristic.properties)")
//            print("Characteristic descriptors: \(characteristic.descriptors)")
//            print("Characteristic value: \(characteristic.value)")
//            print("--------------------------------------------")
            // LOG END
            
            if ((characteristic.uuid == CBUUID.init(string: UUID_LOCK_DATA)) ||
                (characteristic.uuid == CBUUID.init(string: UUID_LOCK_CONFIG))) {
        
                let data: NSData = characteristic.value! as NSData // Communication frames returned after token request
                let dataBytes: Data = Data(referencing: data) // aesCBCDecrypt needs type Data. But secret need to be stored as NSData, so referencing works just fine..
        
                var decryptedData :Data?
                do {
                    decryptedData = try aesCBCDecrypt(data: dataBytes, keyData: self.secretData) // Do decryption
                    
                    let fileBytes = [UInt8](decryptedData!) // Convert recieved data into UInt8 to get specific bytes
                    
                    // Check for correct fix􏰓ed token identifier (0x06 & 0x02) // BLE Communication-v1.pdf, Page 4
                    if (fileBytes[0] == 0x06)
                    {
                        if (fileBytes[1] == 0x02)
                        {
                            token[0] = fileBytes[3]
                            token[1] = fileBytes[4]
                            token[2] = fileBytes[5]
                            token[3] = fileBytes[6]
                            
                            // Send unlock request
                            let passwordData: [UInt8] = [0x05, 0x01, 0x06, self.pw[0], self.pw[1], self.pw[2], self.pw[3], self.pw[4], self.pw[5], token[0], token[1], token[2], token[3], 0x17, 0x3b, 0x4d] // BLE Communication-v1.pdf, Page 8
                            let unlockData: Data = Data(bytes: passwordData, count: passwordData.count)

                            var encryptedData :Data?
                            do {
                                encryptedData = try aesCBCEncrypt(data: unlockData, keyData: self.secretData)
                                self.peripheral.writeValue(encryptedData!, for: self.writeCharacteristic, type: .withoutResponse)
                                self.call.success([
                                    "opened": true
                                ])
                            }
                            catch (let status) {
                               self.call.reject("Error aesCBCEncrypt: \(status)")
                            }
                        }
                    }
                    else
                    {
                        self.disconnectFromDevice();
                    }
        
                }
                catch (let status) {
                   self.call.reject("Error aesCBCDecrypt: \(status)")
                }
            }
        }
    }
    
    // Disconnect
    public func disconnectFromDevice () {
        if self.peripheral != nil {
            centralManager?.cancelPeripheralConnection(self.peripheral!)
            print("Disconnected")
        }
    }
    
    // Disconnected
    public func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        self.peripheral = nil;
        self.readCharacteristic = nil;
        self.writeCharacteristic = nil;
        
        self.peripheralName = ""
        
        self.arrayPeripheral = []
        self.arrayPeripheralStringName = []
        
        self.address = ""
        self.secretData = nil
           
        self.pw = []
           
        self.token = [0x00, 0x00, 0x00, 0x00]
    }
    
    
    
    
    /*
     AES Stuff
     https://stackoverflow.com/questions/37680361/aes-encryption-in-swift
     */
    
     enum AESError: Error {
         case KeyError((String, Int))
         case IVError((String, Int))
         case CryptorError((String, Int))
     }

     // The iv is prefixed to the encrypted data
    func aesCBCEncrypt(data:Data, keyData:Data) throws -> Data {
        let keyLength = keyData.count
                
         let validKeyLengths = [kCCKeySizeAES128, kCCKeySizeAES192, kCCKeySizeAES256]
         if (validKeyLengths.contains(keyLength) == false) {
            self.call.reject("Invalid key length.\nYour key length \(keyLength).\nSchould be \(kCCKeySizeAES128), \(kCCKeySizeAES192), \(kCCKeySizeAES256)")
         }

        let dataLength = data.count;
        let bufferSize = dataLength + kCCBlockSizeAES128;
        let encryptDataPointer = UnsafeMutableRawPointer.allocate(byteCount: bufferSize, alignment: 1)
        
        // Seems like the easiest way to avoid the `withUnsafeBytes` mess is to use NSData.bytes.
        let dataToDecryptNSData = NSData(data: data)
        let keyToDecryptNSData = NSData(data: keyData)

        var numBytesEncrypted: Int = 0
        
        do {
            let cryptStatus: CCCryptorStatus = CCCrypt(
                                CCOperation(kCCEncrypt),                        // op: CCOperation
                                CCAlgorithm(kCCAlgorithmAES128),                // alg: CCAlgorithm
                                0x0000,                                         // options: CCOptions
                                keyToDecryptNSData.bytes,                       // key: the "password"
                                kCCKeySizeAES128,                               // keyLength: the "password" size
                                [0x0],                                          // iv: Initialization Vector
                                dataToDecryptNSData.bytes,                      // dataIn: Data to encrypt bytes
                                dataLength,                                     // dataInLength: Data to encrypt size
                                encryptDataPointer,                             // dataOut: encrypted Data buffer
                                bufferSize,                                     // dataOutAvailable: encrypted Data buffer size
                                &numBytesEncrypted)                             // dataOutMoved: the number of bytes written
            
            guard cryptStatus == CCCryptorStatus(kCCSuccess) else {
                throw AESError.CryptorError(("Encryption status is \(cryptStatus)", -1))
            }
            
            // LOG
//            print("--------------------------------------------")
//            print("kCCEncrypt: \(kCCEncrypt)")
//            print("kCCAlgorithmAES128: \(kCCAlgorithmAES128)")
//            print("kCCEncrypt: \(0x0000)")
//            print("key bytes: \(keyToDecryptNSData)")
//            print("dataLength: \(dataLength)")
//            let demoD = Data(bytes: encryptDataPointer, count: dataLength)
//            print("buffer: \(demoD as NSData)")
//            print("bufferSize: \(bufferSize)")
//            print("numBytesEncrypted: \(numBytesEncrypted)")
//            print("--------------------------------------------")
            // LOG END
        } catch {
            throw AESError.CryptorError(("Encryptin faild", -1))
        }
        
        let d = Data(bytes: encryptDataPointer, count: dataLength) // Encrypted data
        encryptDataPointer.deallocate() // Free pointer
        
        return d;
     }

     // The iv is prefixed to the encrypted data
     func aesCBCDecrypt(data:Data, keyData:Data) throws -> Data? {
         let keyLength = keyData.count
        
         let validKeyLengths = [kCCKeySizeAES128, kCCKeySizeAES192, kCCKeySizeAES256]
         if (validKeyLengths.contains(keyLength) == false) {
             self.call.reject("Invalid key length.\nYour key length \(keyLength).\nSchould be \(kCCKeySizeAES128), \(kCCKeySizeAES192), \(kCCKeySizeAES256)")
         }

        
        let dataLength = data.count;
        let clearLength = size_t(dataLength + kCCBlockSizeAES128)
        let clearDataPointer = UnsafeMutableRawPointer.allocate(byteCount: clearLength, alignment: 1)
        
        var numberBytesDecrypted: Int = 0
        
        
        
        do {
            try keyData.withUnsafeBytes { (u8Ptr: UnsafePointer<UInt8>) in
            let keyBytes = UnsafeRawPointer(u8Ptr)
                
                try data.withUnsafeBytes { (u8Ptr: UnsafePointer<UInt8>) in
                let dataToDecryptBytes = UnsafeRawPointer(u8Ptr)
                        
                        let cryptStatus: CCCryptorStatus = CCCrypt( // Stateless, one-shot encrypt operation
                            CCOperation(kCCDecrypt),                // op: CCOperation
                            CCAlgorithm(kCCAlgorithmAES128),        // alg: CCAlgorithm
                            0x0000,                                 // options: CCOptions
                            keyBytes,                               // key: the "password"
                            kCCKeySizeAES128,                       // keyLength: the "password" size
                            nil,                                    // iv: Initialization Vector
                            dataToDecryptBytes,                     // dataIn: Data to decrypt bytes
                            dataLength,                             // dataInLength: Data to decrypt size
                            clearDataPointer,                             // dataOut: decrypted Data buffer
                            clearLength,                             // dataOutAvailable: decrypted Data buffer size
                            &numberBytesDecrypted                   // dataOutMoved: the number of bytes written
                        )
                        
                        guard cryptStatus == CCCryptorStatus(kCCSuccess) else {
                            throw AESError.CryptorError(("Decryption status is \(cryptStatus)", -1))
                        }
                    }
                }
        } catch {
            throw AESError.CryptorError(("Decryptin faild", -1))
        }
        
        let d = Data(bytes: clearDataPointer, count: dataLength) // Decrypted data
        clearDataPointer.deallocate() // Free pointer
        
        return d;
     }
    
    
}
