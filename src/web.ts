<<<<<<< HEAD
/*import { WebPlugin } from '@capacitor/core';
import { Own2MeshOkLokPluginPlugin } from './definitions';

export class Own2MeshOkLokPluginWeb extends WebPlugin implements Own2MeshOkLokPluginPlugin {
  constructor() {
    super({
      name: 'Own2MeshOkLokPlugin',
      platforms: ['web']
    });
  }

  async open(options: { id: string, secret: string, pw: string }): Promise<{ opened: boolean }> {
    console.log('Function not available in this context (Web).');
    return false;
  }

  async close(options: { id: string, secret: string }): Promise<{ closed: boolean }> {
    console.log("Function not available in this context (Web).");
    return false;
  }

  async battery_status(options: { id: string, secret: string }): Promise<{ percentage: number }> {
    console.log("Function not available in this context (Web).");
    return 0;
  }

  async lock_status(options: { id: string, secret: string }): Promise<{ locked: boolean }> {
    console.log("Function not available in this context (Web).");
    return false;
  }
}

const Own2MeshOkLokPlugin = new Own2MeshOkLokPluginWeb();

export { Own2MeshOkLokPlugin };

import { registerWebPlugin } from '@capacitor/core';
registerWebPlugin(Own2MeshOkLokPlugin);
*/
=======
>>>>>>> 5d4bf44791202e6e1e9d9932cb810780254ef469
