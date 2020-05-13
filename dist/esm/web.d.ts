import { WebPlugin } from '@capacitor/core';
import { Own2MeshOkLokPluginPlugin } from './definitions';
export declare class Own2MeshOkLokPluginWeb extends WebPlugin implements Own2MeshOkLokPluginPlugin {
    constructor();
    echo(options: {
        value: string;
    }): Promise<{
        value: string;
    }>;
}
declare const Own2MeshOkLokPlugin: Own2MeshOkLokPluginWeb;
export { Own2MeshOkLokPlugin };
