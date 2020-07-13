#import <Foundation/Foundation.h>
#import <Capacitor/Capacitor.h>

// Define the plugin using the CAP_PLUGIN Macro, and
// each method the plugin supports using the CAP_PLUGIN_METHOD macro.
CAP_PLUGIN(Own2MeshOkLokPlugin, "Own2MeshOkLokPlugin",
           CAP_PLUGIN_METHOD(echo, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(open, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(close, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(battery_status, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(lock_status, CAPPluginReturnPromise);
)
