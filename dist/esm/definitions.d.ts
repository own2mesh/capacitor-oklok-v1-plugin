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
    open(options: {
        id: string;
        secret: string;
        pw: string;
    }): Promise<{
        opened: boolean;
    }>;
    close(options: {
        id: string;
        secret: string;
    }): Promise<{
        closed: boolean;
    }>;
    battery_status(options: {
        id: string;
        secret: string;
    }): Promise<{
        percentage: number;
    }>;
    lock_status(options: {
        id: string;
        secret: string;
    }): Promise<{
        locked: boolean;
    }>;
}
