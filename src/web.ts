import { WebPlugin } from '@capacitor/core';
import { Own2MeshOkLokPluginPlugin } from './definitions';

export class Own2MeshOkLokPluginWeb extends WebPlugin implements Own2MeshOkLokPluginPlugin {
  constructor() {
    super({
      name: 'Own2MeshOkLokPlugin',
      platforms: ['web']
    });
  }

  async echo(options: { value: string }): Promise<{value: string}> {
    console.log('ECHO', options);
    return options;
  }
}

const Own2MeshOkLokPlugin = new Own2MeshOkLokPluginWeb();

export { Own2MeshOkLokPlugin };

import { registerWebPlugin } from '@capacitor/core';
registerWebPlugin(Own2MeshOkLokPlugin);
