import { registerPlugin } from '@capacitor/core';

import type { I_AlwaisOnTracker } from './definitions';

const Example = registerPlugin<I_AlwaisOnTracker>('Example', {
  web: () => import('./web').then((m) => new m.ExampleWeb()),
});

export * from './definitions';
export { Example };
