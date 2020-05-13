declare module "@capacitor/core" {
    interface PluginRegistry {
        Own2MeshOkLokPlugin: Own2MeshOkLokPluginPlugin;
    }
}
export interface Own2MeshOkLokPluginPlugin {
    echo(options: {
        value: string;
    }): Promise<{
        value: string;
    }>;
}
