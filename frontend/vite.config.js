import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The backend allows requests from http://localhost:5173 (iitsm.security.allowed-origins), so keep this port.
export default defineConfig({
  plugins: [react()],
  server: { port: 5173, strictPort: true },
});
