declare module '@capacitor/core' {
    interface PluginRegistry {
        Own2MeshOkLokPlugin?: Own2MeshOkLokPlugin;
    }
}
export interface Own2MeshOkLokPlugin {
    echo(options: {
        value: string;
    }): Promise<{
        value: string;
    }>;
    open(options: {
        address: string;
        secret: string;
        pw: string;
    }): Promise<{
        opened: boolean;
    }>;
    close(options: {
        address: string;
        secret: string;
    }): Promise<{
        closed: boolean;
    }>;
    battery_status(options: {
        address: string;
        secret: string;
    }): Promise<{
        percentage: number;
    }>;
    lock_status(options: {
        address: string;
        secret: string;
    }): Promise<{
        locked: boolean;
    }>;
}
