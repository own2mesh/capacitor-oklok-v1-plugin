import Capacitor
import CommonCrypto
import CoreBluetooth
import Foundation

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

	private var isConnecting = false
	private var isScanning = false
	private var timeOut = false
	private static let timeOutTimerTime = 30.0 // sek
	private var timeOutTimer: Timer? // Ends searching for BLE devices

	var arrayPeripheral: [CBPeripheral] = []
	var arrayPeripheralStringName: [String] = []

	private var name: String = "" // Bluetooth name of lock
	private var secretData: Data! // Secret key vor de/encryption
	private var pw: [UInt8] = [] // specific password for lock
	private var token: [UInt8] = [0x00, 0x00, 0x00, 0x00]

	enum lockOptions {
		case echo
		case open
		case battery_status
		case lock_status
		case close
		// TODO: Add more (change lock pw and so on)
	}

	private var whatYouWant: lockOptions = .echo // switch for different stuff (open, battery_status,..) must be difer

	@objc func echo(_ call: CAPPluginCall) {
		call.resolve(["value": "Echo"])
	}

	@objc func open(_ call: CAPPluginCall) {
		guard let nameCheck = call.options["name"] as? String else {
			call.reject("Must provide an name")
			return
		}
		self.name = nameCheck

		guard let secretCheck = call.options["secret"] as? [String] else {
			call.reject("Must provide an secret")
			return
		}

		let uint8Array = secretCheck.map { UInt8($0.dropFirst(2), radix: 16)! }
		self.secretData = Data(bytes: uint8Array, count: secretCheck.count)

		guard let pwCheck = call.options["pw"] as? [String] else {
			call.reject("Must provide an password (pw)")
			return
		}

		let uint8ArrayPW = pwCheck.map { UInt8($0.dropFirst(2), radix: 16)! }
		self.pw = uint8ArrayPW
		self.whatYouWant = lockOptions.open
		self.call = call
		self.iniCB()
	}

	@objc func battery_status(_ call: CAPPluginCall) {
		guard let nameCheck = call.options["name"] as? String else {
			call.reject("Must provide an name")
			return
		}
		self.name = nameCheck

		guard let secretCheck = call.options["secret"] as? [String] else {
			call.reject("Must provide an secret")
			return
		}
		let uint8Array = secretCheck.map { UInt8($0.dropFirst(2), radix: 16)! }
		self.secretData = Data(bytes: uint8Array, count: secretCheck.count)
		self.whatYouWant = lockOptions.battery_status
		self.call = call
		self.iniCB()
	}

	@objc func lock_status(_ call: CAPPluginCall) {
		guard let nameCheck = call.options["name"] as? String else {
			call.reject("Must provide an name")
			return
		}
		self.name = nameCheck

		guard let secretCheck = call.options["secret"] as? [String] else {
			call.reject("Must provide an secret")
			return
		}
		let uint8Array = secretCheck.map { UInt8($0.dropFirst(2), radix: 16)! }
		self.secretData = Data(bytes: uint8Array, count: secretCheck.count)
		self.whatYouWant = lockOptions.lock_status
		self.call = call
		self.iniCB()
	}

	@objc func close(_ call: CAPPluginCall) {
		guard let nameCheck = call.options["name"] as? String else {
			call.reject("Must provide an name")
			return
		}
		self.name = nameCheck

		guard let secretCheck = call.options["secret"] as? [String] else {
			call.reject("Must provide an secret")
			return
		}
		let uint8Array = secretCheck.map { UInt8($0.dropFirst(2), radix: 16)! }
		self.secretData = Data(bytes: uint8Array, count: secretCheck.count)
		self.whatYouWant = lockOptions.close
		self.iniCB()
		self.call = call
	}

	/*
	 Bluetooth stuff

	 Tutorial: https://www.freecodecamp.org/news/ultimate-how-to-bluetooth-swift-with-hardware-in-20-minutes/ <-- 20 min is a lie!
	 */

	/**
	        Initalize the bluetooth manager
	 */
	private func iniCB() {
		self.centralManager = CBCentralManager(delegate: self, queue: nil, options: nil)
	}

	// If we're powered on, start scanning
	// centralManagerDidUpdateState updates when the Bluetooth Peripheral is switched on or off.
	// It will fire when an app first starts so you know the state of Bluetooth. We also start scanning here.
	public func centralManagerDidUpdateState(_ central: CBCentralManager) {
		if central.state != .poweredOn {
			self.call.reject("Bluetooth is not powered on")
		} else {
			if !self.isScanning {
				self.isScanning = true
				print("Scanning for", self.name) // LOG
				self.centralManager.scanForPeripherals(withServices: nil, options: nil)
				self.timeOutTimer = Timer.scheduledTimer(withTimeInterval: Own2MeshOkLokPlugin.timeOutTimerTime, repeats: false) { _ in
					self.timeOut = true
				}
			}
		}
	}

	// Handles the result of the scan
	// The centralManager didDiscover event occurs when you receive scan results. We'll use this to start a connection.
	public func centralManager(_: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String: Any], rssi _: NSNumber) {
		print(".")

		if self.timeOut {
			self.resetAllProperties()
			self.centralManager.stopScan()
			self.call.reject("Time out while scanning for devices.")
		} else

		// Add deiscovered peripheral name to global variable "peripheralName" need to be like that, because ios does not support MAC-Address vrom BLE devices
		if ((advertisementData as NSDictionary).value(forKey: "kCBAdvDataLocalName")) != nil {
			self.peripheralName = peripheral.name ?? ""
		}

		// Check if discovered peripheralName matches the given name
		if !(self.peripheralName).isEmpty {
			if self.peripheralName == self.name {
				// LOGS
				print("--------------------------------------------") // LOG
				print("Found device: " + self.peripheralName)
				print("--------------------------------------------") // LOG
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
	public func centralManager(_: CBCentralManager, didConnect peripheral: CBPeripheral) {
		if !self.isConnecting {
			self.isConnecting = true
			if peripheral == self.peripheral {
				peripheral.discoverServices(nil)
			}
			self.timeOutTimer?.invalidate()
		}
	}

	// Handles discovery event
	// The peripheral didDiscoverServices event first once all the services have been discovered.
	// Notice that we've switched from centralManager to peripheral now that we're connected. We'll start the characteristic discovery here. We'll be using the service UUID as the target.
	public func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
		if error != nil {
			self.call.reject("Error discovering services: \(error!.localizedDescription)")
			self.disconnectFromDevice()
			return
		}

		guard let services = peripheral.services else {
			self.call.reject("No service discovered!")
			self.disconnectFromDevice()
			return
		}

		for service in services {
			if service.uuid == CBUUID(string: UUID_LOCK_SERVICE) {
				// Lock service found
				// Now kick off discovery of characteristics
				peripheral.discoverCharacteristics(nil, for: service)
				return
			}
		}
	}

	// Handling discovery of characteristics
	// The peripheral didDiscoverCharacteristicsFor event will provide all the characteristics using the provided service UUID.
	// This is the last step in the chain of doing a full device discovery. It's hairy but it only has to be done once during the connection phase!
	public func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
		if error != nil {
			self.call.reject("Error discovering services: \(error!.localizedDescription)")
			self.disconnectFromDevice()
			return
		}

		guard let characteristics = service.characteristics else {
			return
		}

		for characteristic in characteristics {
			// looks for the right characteristic

			// Read Characteristic ?
			if characteristic.uuid == CBUUID(string: UUID_LOCK_DATA) {
				// Once found, subscribe to the this particular characteristic...
				self.peripheral.setNotifyValue(true, for: characteristic)
				self.readCharacteristic = characteristic
			}

			// Write Characteristic ?
			if characteristic.uuid == CBUUID(string: UUID_LOCK_CONFIG) {
				self.writeCharacteristic = characteristic
			}

			// Discover descriptors for each characteristic
			peripheral.discoverDescriptors(for: characteristic) // IS missing and throws error

			// Discovered Read / Write Characteristic?
			if self.readCharacteristic != nil, self.writeCharacteristic != nil {
				// Send token requst
				let valData: [UInt8] = [0x06, 0x01, 0x01, 0x01, 0x37, 0x62, 0x55, 0x6C, 0x68, 0x73, 0x1D, 0x6D, 0x7E, 0x17, 0x3B, 0x4D] // BLE Communication-v1.pdf, Page 4
				let data: Data = .init(bytes: valData, count: valData.count)

				var encryptedData: Data?
				do {
					encryptedData = try self.aesECBEncrypt(data: data, keyData: self.secretData) // Do encryption

					self.peripheral.writeValue(encryptedData!, for: self.writeCharacteristic, type: .withoutResponse)
				} catch let status {
					self.call.reject("Error aesECBEncrypt: \(status)")
					self.disconnectFromDevice()
				}
			}
		}
	}

	public func peripheral(_: CBPeripheral, didUpdateNotificationStateFor _: CBCharacteristic, error: Error?) {
		if error != nil {
			self.call.reject("Error update notification state for characteristic: \(error!.localizedDescription)")
			self.disconnectFromDevice()
			return
		}
	}

	public func peripheral(_: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
		if error != nil {
			self.call.reject("Error update value for characteristic: \(error!.localizedDescription)")
			self.disconnectFromDevice()
			return
		} else {
			if (characteristic.uuid == CBUUID(string: UUID_LOCK_DATA)) ||
				(characteristic.uuid == CBUUID(string: UUID_LOCK_CONFIG))
			{
				let data: NSData = characteristic.value! as NSData // Communication frames returned after token request
				let dataBytes: Data = .init(referencing: data) // aesECBDecrypt needs type Data. But secret need to be stored as NSData, so referencing works just fine..

				var decryptedData: Data?
				do {
					decryptedData = try self.aesECBDecrypt(data: dataBytes, keyData: self.secretData) // Do decryption

					let fileBytes = [UInt8](decryptedData!) // Convert recieved data into UInt8 to get specific bytes

					// Check for correct fix􏰓ed token identifier (0x06 & 0x02) // BLE Communication-v1.pdf, Page 4
					if fileBytes[0] == 0x06 {
						if fileBytes[1] == 0x02 {
							self.token[0] = fileBytes[3]
							self.token[1] = fileBytes[4]
							self.token[2] = fileBytes[5]
							self.token[3] = fileBytes[6]

							var unlockData: Data?
							switch self.whatYouWant {
							case .open:
								// Send unlock request
								let passwordData: [UInt8] = [0x05, 0x01, 0x06, self.pw[0], self.pw[1], self.pw[2], self.pw[3], self.pw[4], self.pw[5], self.token[0], self.token[1], self.token[2], self.token[3], 0x0, 0x0, 0x0] // BLE Communication-v1.pdf, Page 8
								unlockData = Data(bytes: passwordData, count: passwordData.count)
							case .battery_status:
								// Send battery_status request
								let passwordData: [UInt8] = [0x02, 0x01, 0x01, 0x01, token[0], token[1], token[2], token[3], 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0] // BLE Communication-v1.pdf, Page 7
								unlockData = Data(bytes: passwordData, count: passwordData.count)
							case .lock_status:
								// Send lock_status request
								let passwordData: [UInt8] = [0x05, 0x0E, 0x01, 0x01, token[0], token[1], token[2], token[3], 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0] // BLE Communication-v1.pdf, Page 10
								unlockData = Data(bytes: passwordData, count: passwordData.count)
							case .close:
								// Send close request
								let passwordData: [UInt8] = [0x05, 0x0C, 0x01, 0x01, token[0], token[1], token[2], token[3], 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0] // BLE Communication-v1.pdf, Page 10
								unlockData = Data(bytes: passwordData, count: passwordData.count)
							default:
								print("Nothing chosen!") // TODO: Checkout: This line should never been called
							}

							var encryptedData: Data?
							do {
								encryptedData = try self.aesECBEncrypt(data: unlockData!, keyData: self.secretData)
								self.peripheral.writeValue(encryptedData!, for: self.writeCharacteristic, type: .withoutResponse)
							} catch let status {
								self.call.reject("Error aesECBEncrypt: \(status)")
								self.disconnectFromDevice()
							}
						}
					}

					if fileBytes[0] == 0x05 { // Open lock
						if fileBytes[1] == 0x02 {
							if fileBytes[2] == 0x01 {
								// Check if successful opend lock
								if fileBytes[3] == 0x0 {
									self.call.resolve([
										"opened": true,
									])
								} else {
									self.call.resolve([
										"opened": false,
									])
								}
								self.disconnectFromDevice()
							}
						}
					}
					if fileBytes[0] == 0x02 { // Battery status
						if fileBytes[1] == 0x02 {
							if fileBytes[2] == 0x01 {
								print("Battery \(fileBytes[3])")
								self.call.resolve([
									"percentage": fileBytes[3],
								])
								self.disconnectFromDevice()
							}
						}
					}
					if fileBytes[0] == 0x05 { // Lock status
						if fileBytes[1] == 0x0F {
							if fileBytes[2] == 0x01 {
								// Check if successful opend lock
								if fileBytes[3] == 0x00 {
									self.call.resolve([
										"locked": false,
									])
								} else if fileBytes[3] == 0x01 {
									self.call.resolve([
										"locked": true,
									])
								} else {
									self.call.reject("Error: Something went wrong")
								}
								self.disconnectFromDevice()
							}
						}
					}
					if fileBytes[0] == 0x05 { // Close lock
						if fileBytes[1] == 0x0D || fileBytes[1] == 0x08 {
							if fileBytes[2] == 0x01 {
								// Check if successful opend lock
								if fileBytes[3] == 0x00 {
									self.call.resolve([
										"closed": true,
									])
								} else if fileBytes[3] == 0x01 {
									self.call.reject("Lock failed to lock")
								} else {
									self.call.reject("Error: Something went wrong")
								}
								self.disconnectFromDevice()
							}
						}
					}
				} catch let status {
					self.call.reject("Error aesECBDecrypt: \(status)")
					self.disconnectFromDevice()
				}
			}
		}
	}

	// Disconnect
	public func disconnectFromDevice() {
		// LOG
		print("--------------------------------------------")
		print("Try to ** DISCONNECT **")
		// LOG END

		if self.peripheral != nil {
			self.centralManager?.cancelPeripheralConnection(self.peripheral!)
		}

		print("--------------------------------------------") // LOG
	}

	// Disconnected
	public func centralManager(_: CBCentralManager, didDisconnectPeripheral _: CBPeripheral, error _: Error?) {
		self.resetAllProperties()
	}

	/**
	 Reset all properties so the class has it's inital state
	 */
	private func resetAllProperties() {
		self.peripheral = nil
		self.readCharacteristic = nil
		self.writeCharacteristic = nil

		self.peripheralName = ""

		self.arrayPeripheral = []
		self.arrayPeripheralStringName = []

		self.name = ""
		self.secretData = nil

		self.pw = []

		self.token = [0x00, 0x00, 0x00, 0x00]

		self.isConnecting = false
		self.isScanning = false
		self.timeOut = false

		// LOG
		print("--------------------------------------------")
		print("** DISCONNECT SUCCESFUL **")
		print("--------------------------------------------") // LOG
		// LOG END
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
	func aesECBEncrypt(data: Data, keyData: Data) throws -> Data {
		let keyLength = keyData.count

		let validKeyLengths = [kCCKeySizeAES128, kCCKeySizeAES192, kCCKeySizeAES256]
		if validKeyLengths.contains(keyLength) == false {
			self.call.reject("Invalid key length.\nYour key length \(keyLength).\nSchould be \(kCCKeySizeAES128), \(kCCKeySizeAES192), \(kCCKeySizeAES256)")
			self.disconnectFromDevice()
		}

		let dataLength = data.count
		let bufferSize = dataLength + kCCBlockSizeAES128
		let encryptDataPointer = UnsafeMutableRawPointer.allocate(byteCount: bufferSize, alignment: 1)

		// Seems like the easiest way to avoid the `withUnsafeBytes` mess is to use NSData.bytes.
		let dataToDecryptNSData = NSData(data: data)
		let keyToDecryptNSData = NSData(data: keyData)

		var numBytesEncrypted = 0

		do {
			let cryptStatus: CCCryptorStatus = CCCrypt(
				CCOperation(kCCEncrypt), // op: CCOperation
				CCAlgorithm(kCCAlgorithmAES128), // alg: CCAlgorithm
				CCOptions(kCCOptionECBMode), // options: CCOptions
				keyToDecryptNSData.bytes, // key: the "password"
				kCCKeySizeAES128, // keyLength: the "password" size
				nil, // iv: Initialization Vector (ignored when in aes ecb mode (kCCOptionECBMode))
				dataToDecryptNSData.bytes, // dataIn: Data to encrypt bytes
				dataLength, // dataInLength: Data to encrypt size
				encryptDataPointer, // dataOut: encrypted Data buffer
				bufferSize, // dataOutAvailable: encrypted Data buffer size
				&numBytesEncrypted
			) // dataOutMoved: the number of bytes written

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

		return d
	}

	// The iv is prefixed to the encrypted data
	func aesECBDecrypt(data: Data, keyData: Data) throws -> Data? {
		let keyLength = keyData.count

		let validKeyLengths = [kCCKeySizeAES128, kCCKeySizeAES192, kCCKeySizeAES256]
		if validKeyLengths.contains(keyLength) == false {
			self.call.reject("Invalid key length.\nYour key length \(keyLength).\nSchould be \(kCCKeySizeAES128), \(kCCKeySizeAES192), \(kCCKeySizeAES256)")
			self.disconnectFromDevice()
		}

		let dataLength = data.count
		let clearLength = size_t(dataLength + kCCBlockSizeAES128)
		let clearDataPointer = UnsafeMutableRawPointer.allocate(byteCount: clearLength, alignment: 1)

		let dataToDecryptNSData = NSData(data: data)
		let keyToDecryptNSData = NSData(data: keyData)
		var numberBytesDecrypted = 0

		do {
			let cryptStatus: CCCryptorStatus = CCCrypt( // Stateless, one-shot encrypt operation
				CCOperation(kCCDecrypt), // op: CCOperation
				CCAlgorithm(kCCAlgorithmAES128), // alg: CCAlgorithm
				CCOptions(kCCOptionECBMode), // options: CCOptions
				keyToDecryptNSData.bytes, // key: the "password"
				kCCKeySizeAES128, // keyLength: the "password" size
				nil, // iv: Initialization Vector (ignored when in mode kCCOptionECBMode)
				dataToDecryptNSData.bytes, // dataIn: Data to decrypt bytes
				dataLength, // dataInLength: Data to decrypt size
				clearDataPointer, // dataOut: decrypted Data buffer
				clearLength, // dataOutAvailable: decrypted Data buffer size
				&numberBytesDecrypted // dataOutMoved: the number of bytes written
			)

			guard cryptStatus == CCCryptorStatus(kCCSuccess) else {
				throw AESError.CryptorError(("Decryption status is \(cryptStatus)", -1))
			}
		} catch {
			throw AESError.CryptorError(("Decryptin faild", -1))
		}

		let d = Data(bytes: clearDataPointer, count: dataLength) // Decrypted data
		clearDataPointer.deallocate() // Free pointer

		return d
	}
}
