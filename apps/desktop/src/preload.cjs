const { contextBridge } = require('electron');

contextBridge.exposeInMainWorld('wangDetectiveClient', {
  kind: 'desktop',
  platform: process.platform,
  defaultServerUrl: process.env.WD_DEFAULT_SERVER_URL || ''
});
