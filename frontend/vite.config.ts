/// <reference types="vitest/config" />

import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    css: true,
  },
  server: {
    proxy: {
      '/api': 'http://127.0.0.1:8080',
    },
  },
  build: {
    // Ant Design is an intentionally cached vendor chunk; business pages remain route-split.
    chunkSizeWarningLimit: 1024,
    rolldownOptions: {
      output: {
        codeSplitting: {
          groups: [
            { name: 'react', test: /node_modules\/(react|react-dom|react-router)/, priority: 30 },
            { name: 'antd', test: /node_modules\/(antd|@ant-design|rc-)/, priority: 20 },
            { name: 'query', test: /node_modules\/@tanstack/, priority: 10 },
          ],
        },
      },
    },
  },
})
