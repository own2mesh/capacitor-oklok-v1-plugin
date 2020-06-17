import Foundation
import Capacitor
import CoreBluetooth
import CommonCrypto

let UUID_LOCK = "FEE7"
let UUID_LOCK_DATA = "36F6" // read (notify) update wenn sich was ändert
let UUID_LOCK_CONFIG = "36F5" // write

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitor.ionicframework.com/docs/plugins/ios
 */
@objc(Own2MeshOkLokPlugin)
public class Own2MeshOkLokPlugin: CAPPlugin, CBPeripheralDelegate, CBCentralManagerDelegate {
    
    // Properties, private variables to store the actual central manager and peripheral
    private var centralManager: CBCentralManager!
    private var peripheral: CBPeripheral!
    private var readCharacteristic: CBCharacteristic!
    private var writeCharacteristic: CBCharacteristic!
    
    private var peripheralName: String = ""
    
    private var address: String = ""
    private var secret: String = ""
    private var pw: String = ""
    
    
    @objc func echo(_ call: CAPPluginCall) {
        let value = call.getString("value") ?? ""
        call.success([
            "value": value
        ])
    }
    
    @objc func open(_ call: CAPPluginCall) {
        guard let addressCheck = call.options["address"] as? String else {
          call.reject("Must provide an address")
          return
        }
        self.address = addressCheck
        
        guard let secretCheck = call.options["secret"] as? String else {
          call.reject("Must provide an secret")
          return
        }
        self.secret = secretCheck
        
        guard let pwCheck = call.options["pw"] as? String else {
          call.reject("Must provide an password (pw)")
          return
        }
        self.pw = pwCheck
        
        
        call.success([
            "opened": true
        ])
        
        
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
        print("Central state update")
        if central.state != .poweredOn {
            print("Central is not powered on")
        } else {
            print("Central scanning for", self.address);
            centralManager.scanForPeripherals(withServices: nil, options: nil)
        }
    }
    
    
    // Handles the result of the scan
    // The centralManager didDiscover event occurs when you receive scan results. We'll use this to start a connection.
    public func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String : Any], rssi RSSI: NSNumber) {
        
        print("\n\n__advertisementData: ")
        print(advertisementData);
        
        self.peripheralName = peripheral.name ?? ""
        print("\n\n__peripheralName: " + self.peripheralName)
        
        if (self.peripheralName).isEmpty {
            if (self.peripheralName == self.address) {
                // We've found it so stop scan
                self.centralManager.stopScan()

                // Copy the peripheral instance
                self.peripheral = peripheral
                self.peripheral.delegate = self

                // Connect!
                self.centralManager.connect(self.peripheral, options: nil)
            } else {
                print("Adress: " + self.address + " does not match with peripheralName: " + self.peripheralName)
            }
        }

    }
    
    // The handler if we do connect succesfully
    // The centralManager didConnect event fires once the device is connected. We'll start the device discovery here.
    // Note: Device discovery is the way we determine what services and characteristics are available.
    // This is a good way to confirm what type of device we're connected to.
    public func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        if peripheral == self.peripheral {
            print("Connected to your Particle Board")
            peripheral.discoverServices(nil)
        }
    }
    
    // Disconnected
    public func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        // TODO:
    }
    
    // Handles discovery event
    // The peripheral didDiscoverServices event first once all the services have been discovered.
    // Notice that we've switched from centralManager to peripheral now that we're connected. We'll start the characteristic discovery here. We'll be using the service UUID as the target.
    public func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
      if let services = peripheral.services {
          for service in services {
            if service.uuid == CBUUID.init(string: UUID_LOCK){
                  print("LOCK service found")
                  //Now kick off discovery of characteristics
                  peripheral.discoverCharacteristics(nil, for: service)
                  return
              }
          }
      }
    }
    
    // Handling discovery of characteristics
    // The peripheral didDiscoverCharacteristicsFor event will provide all the characteristics using the provided service UUID.
    // This is the last step in the chain of doing a full device discovery. It's hairy but it only has to be done once during the connection phase!
    public func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        if let characteristics = service.characteristics {
            for characteristic in characteristics {
                
                if (characteristic.uuid == CBUUID.init(string: UUID_LOCK_DATA)) {
                    print("Lock read characteristic found")
                    self.peripheral .setNotifyValue(true, for: characteristic)
                    
                    self.readCharacteristic = characteristic
                }
                
                if (characteristic.uuid == CBUUID.init(string: UUID_LOCK_CONFIG)) {
                    print("Lock write characteristic found")
                    self.writeCharacteristic = characteristic
                }
                
                if ((self.readCharacteristic != nil) && (self.writeCharacteristic != nil))
                {
                   // TODO: get Token
                }
            }
        }
    }
    
    public func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        if ((error) != nil) {
            print("Error changing notification state: ", error?.localizedDescription ?? "Non detected");
        } else {
            let dataBytes: Data? = characteristic.value
            print("__dataBytes" + (dataBytes?.base64EncodedString())!)
            
            if ((characteristic.uuid == CBUUID.init(string: UUID_LOCK_DATA)) ||
                (characteristic.uuid == CBUUID.init(string: UUID_LOCK_CONFIG))) {
                
                let de_data = dataBytes?.base64EncodedString().aesDecrypt(key: secret, iv: self.pw)
                
                print("__AESDecrypt de_data: " + (de_data)!)
                
//                let length: Int = de_data?.count
//                let index: Int
                
                //TODO
            }
        }
    }
    
    
    
    /*
     AES Stuff
     
     https://stackoverflow.com/questions/27072021/aes-encrypt-and-decrypt
     
     let key = "bbC2H19lkVbQDfakxcrtNMQdd0FloLyw" // length == 32
     let iv = "gqLOHUioQ0QjhuvI" // length == 16
     let s = "string to encrypt"
     let enc = try! s.aesEncrypt(key, iv: iv)
     let dec = try! enc.aesDecrypt(key, iv: iv)
     print(s) // string to encrypt
     print("enc:\(enc)") // 2r0+KirTTegQfF4wI8rws0LuV8h82rHyyYz7xBpXIpM=
     print("dec:\(dec)") // string to encrypt
     print("\(s == dec)") // true
     */
    
     // The IV must be the same of block size, a constant defined in the code, which is 16B. That means your IV must be exactly 16 bytes/characters long.
     // The key must be exactly 128, 192 or 256 bits long (16, 24 or 32 bytes/characters).
//    func aesEncrypt(KEY: String, IV: String) throws -> String {
//        let encrypted = try AES(key: KEY, iv: IV, padding: .pkcs7).encrypt([UInt8](self.data(using: .utf8)!))
//        return Data(encrypted).base64EncodedString()
//    }
//
//    func aesDecrypt(KEY: String, IV: String) throws -> String {
//        guard let data = Data(base64Encoded: self) else { return "" }
//        let decrypted = try AES(key: KEY, iv: IV, padding: .pkcs7).decrypt([UInt8](data))
//        return String(bytes: decrypted, encoding: .utf8) ?? self
//    }
}

/*
AES Stuff
 let encoded = message.aesEncrypt(key: keyString, iv: iv)
 let unencode = encoded?.aesDecrypt(key: keyString, iv: iv)
https://stackoverflow.com/questions/27072021/aes-encrypt-and-decrypt
 */
extension String {

    func aesEncrypt(key:String, iv:String, options:Int = kCCOptionPKCS7Padding) -> String? {
        if let keyData = key.data(using: String.Encoding.utf8),
            let data = self.data(using: String.Encoding.utf8),
            let cryptData    = NSMutableData(length: Int((data.count)) + kCCBlockSizeAES128) {


            let keyLength              = size_t(kCCKeySizeAES128)
            let operation: CCOperation = UInt32(kCCEncrypt)
            let algoritm:  CCAlgorithm = UInt32(kCCAlgorithmAES128)
            let options:   CCOptions   = UInt32(options)



            var numBytesEncrypted :size_t = 0

            let cryptStatus = CCCrypt(operation,
                                      algoritm,
                                      options,
                                      (keyData as NSData).bytes, keyLength,
                                      iv,
                                      (data as NSData).bytes, data.count,
                                      cryptData.mutableBytes, cryptData.length,
                                      &numBytesEncrypted)

            if UInt32(cryptStatus) == UInt32(kCCSuccess) {
                cryptData.length = Int(numBytesEncrypted)
                let base64cryptString = cryptData.base64EncodedString(options: .lineLength64Characters)
                return base64cryptString


            }
            else {
                return nil
            }
        }
        return nil
    }

    func aesDecrypt(key:String, iv:String, options:Int = kCCOptionPKCS7Padding) -> String? {
        if let keyData = key.data(using: String.Encoding.utf8),
            let data = NSData(base64Encoded: self, options: .ignoreUnknownCharacters),
            let cryptData    = NSMutableData(length: Int((data.length)) + kCCBlockSizeAES128) {

            let keyLength              = size_t(kCCKeySizeAES128)
            let operation: CCOperation = UInt32(kCCDecrypt)
            let algoritm:  CCAlgorithm = UInt32(kCCAlgorithmAES128)
            let options:   CCOptions   = UInt32(options)

            var numBytesEncrypted :size_t = 0

            let cryptStatus = CCCrypt(operation,
                                      algoritm,
                                      options,
                                      (keyData as NSData).bytes, keyLength,
                                      iv,
                                      data.bytes, data.length,
                                      cryptData.mutableBytes, cryptData.length,
                                      &numBytesEncrypted)

            if UInt32(cryptStatus) == UInt32(kCCSuccess) {
                cryptData.length = Int(numBytesEncrypted)
                let unencryptedMessage = String(data: cryptData as Data, encoding:String.Encoding.utf8)
                return unencryptedMessage
            }
            else {
                return nil
            }
        }
        return nil
    }


}
