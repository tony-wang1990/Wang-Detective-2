import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig({
  plugins: [vue()],
  base: '/',
  server: {
    proxy: {
      '/api': 'http://127.0.0.1:9527',
      '/actuator': 'http://127.0.0.1:9527'
    }
  },
  build: {
    outDir: '../src/main/resources/dist',
    emptyOutDir: true,
    sourcemap: false,
    chunkSizeWarningLimit: 650,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules/echarts') || id.includes('node_modules/zrender')) {
            return 'charts';
          }
          if (id.includes('node_modules/leaflet')) {
            return 'map';
          }
          if (id.includes('node_modules/vue') || id.includes('node_modules/vue-router')) {
            return 'vue-vendor';
          }
        }
      }
    }
  }
});
