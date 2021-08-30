# Own2Mesh - Capacitor plugin OKLOK 
Plugin for capacitor to open locks from OKLOK

You can find a demo app here: [Demo App](https://gitlab.edvsz.hs-osnabrueck.de/own2mesh/demo-ionic-own-2-mesh)
#
## SUPPORT

If you are a student of the HS Osnabrück contact us here: [MSTeams](https://teams.microsoft.com/l/channel/19%3aa3b4c7c7cc62403f8f45331c768abd23%40thread.tacv2/Allgemein?groupId=aef98c48-594c-4f52-a91b-be00b7bae064&tenantId=d38794c2-bc43-472e-a4f7-cbe0edcb3aae)

Otherwise contact us via email: own2mesh@gmail.com

#
## Install
* Install own2mesh-capacitor-plugin-oklok
> `npm i own2mesh-capacitor-plugin-oklok`
>
#
## Info
* You must provide a lock name on IOS *(IOS doesn't support MAC-Address with BLE)*
* You must provide a lock address on Android.
* This plugin has no web implementation. Test only on your phone.

> Import the plugin (Typescript)
>```typescript
> import {Plugins} from '@capacitor/core';
> const {Own2MeshOkLokPlugin} = Plugins;
> ```
> How to use a [methode](#Methods)
> ```typescript
> Own2MeshOkLokPlugin.theMethodeYouLike();
>```

### (Android only) 
> #### Add our installed plugin to the MainActivity
>
> File Path: *myApp/android/app/src/main/java/io/ionic/starter/MainActivity.java*
>
> `import de.own2mesh.plugin.oklok.Own2MeshOkLokPlugin;`
>
> `add(Own2MeshOkLokPlugin.class);`
>
> The MainActivity.java should look like this:
> ```java
> package de.own2mesh.own2mesh_demo;
>
>import android.os.Bundle;
>
> import com.getcapacitor.BridgeActivity;
> import com.getcapacitor.Plugin;
>
> import java.util.ArrayList;
>
> import de.own2mesh.plugin.oklok.Own2MeshOkLokPlugin;
>
> public class MainActivity extends BridgeActivity {
>  @Override
>  public void onCreate(Bundle savedInstanceState) {
>    super.onCreate(savedInstanceState);
>
>    // Initializes the Bridge
>    this.init(savedInstanceState, new ArrayList<Class<? extends Plugin>>() {{
>      // Additional plugins you've installed go here
>      // Ex: add(TotallyAwesomePlugin.class);
>      add(Own2MeshOkLokPlugin.class);
>    }});
>  }
> }
>```
#

#

# Usage

### Import
> Import the plugin
> ```typescript
> import {Plugins} from '@capacitor/core';
> const {Own2MeshOkLokPlugin} = Plugins;
> ```

> How to use a methode
> ```typescript
> Own2MeshOkLokPlugin.theMethodeYouLike();
> ```
#

### Example lock

```typescript
lockName = {
    name: string, // Physical lock name
    address: string, // MAC Address (for android)
    secret: string[16], // lock key as hexadecimal integer literal string array[16 items] (Begins with the 0 digit followed by either an x or X, followed by any combination of the digits 0 through 9 and the letters a through f or A through F.)
    pw: string[6] // password as hexadecimal integer literal string array[6 items] (Begins with the 0 digit followed by either an x or X, followed by any combination of the digits 0 through 9 and the letters a through f or A through F.)
}
```

> __Important__ Hex Strings have to start with a leading 0 if they are single digit

```typescript
lockOKGSS101 = {
    name: 'OKGSS101',
    address: 'F8:45:65:64:CC:B4',
    secret: ['0x4c', '0x5f', '0x0c', '0x3c', '0x4c', '0x28', '0x53', '0x24', '0x23', '0x36', '0x12', '0x5b', '0x33', '0x59', '0x21', '0x04'],
    pw: ['0x33', '0x32', '0x31', '0x39', '0x33', '0x37'] 
}
```
#

# Methods
## echo()
* *Test Methode*

Call this methode to make sure you can communicate with the plugin.
Result by success: {"value":"Hello back from own-2-mesh plugin!"}

> ##### Plugin Methode
> ```
> echo(options: { value: string }): Promise<{ value: string }>;
> ```
> ###### Example
> ```typescript
> echo() {
>    Own2MeshOkLokPlugin.echo({
>        value: 'Hello Own2MeshOkLokPlugin!'
>    }).then(result => {
>        console.log(result.value);
>    });
>}
>```
#

## open()
* *Open lock*

> Call this methode to open a lock.

> ##### Plugin Methode
> ```
> open(options: { name: string, address: string, secret: string[], pw: string[] }): Promise<{ opened: boolean }>;
> ```
> ###### Example
> ```typescript
> openLock() {
>    Own2MeshOkLokPlugin.open({
>       name: lock.name,
>        address: lock.address,
>        secret: lock.secret,
>        pw: lock.pw
>    }).then(result => {
>        console.log(result.opened);
>    });
>}
>```
#

## battery_status()
* *Get battery status*

> Call this methode to get the battery status.

> ##### Plugin Methode
> ```
> battery_status(options: { name: string, address: string, secret: string[] }): Promise<{ percentage: number }>;
> ```
> ###### Example
> ```typescript
> batteryInfo() {
>    Own2MeshOkLokPlugin.battery_status({
>      name: lock.name,
>      secret: lock.secret,
>    }).then(result => {
>      console.log(result.percentage);
>    });
> }
> ```
#

## lock_status()
* *Get lock status*

> Call this methode to get the lock status.

> ##### Plugin Methode
> ```
> lock_status(options: { name: string, address: string, secret: string[] }): Promise<{ locked: boolean }>;
> ```
> ###### Example
> ```typescript
> lockStatus() {
> Own2MeshOkLokPlugin.lock_status({
>      name: lock.name,
>      secret: lock.secret,
>    }).then(result => {
>      console.log(result.locked);
>    });
> }
> ```
#


## close()
* *Get lock status*

> Call this methode to close a lock.

> ##### Plugin Methode
> ```
> close(options: { name: string, address: string, secret: string[] }): Promise<{ closed: boolean }>;
> ```
> ###### Example
> ```typescript
> closeLock() {
>    Own2MeshOkLokPlugin.close({
>        name: lock.name,
>        address: lock.address,
>        secret: lock.secret,
>        pw: lock.pw
>    }).then(result => {
>        this.openLockStatus = result.closed;
>    });
> }
> ```
#

