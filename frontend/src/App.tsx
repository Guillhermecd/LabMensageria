import { ConfigProvider } from 'antd';
import { BrowserRouter } from 'react-router-dom';
import { AuthProvider } from './hooks/useAuth';
import { ThemeProvider, useTheme } from './hooks/useTheme';
import { AppRouter } from './router';

function Themed() {
  const { config } = useTheme();
  return (
    <ConfigProvider theme={config}>
      <BrowserRouter>
        <AuthProvider>
          <AppRouter />
        </AuthProvider>
      </BrowserRouter>
    </ConfigProvider>
  );
}

export function App() {
  return (
    <ThemeProvider>
      <Themed />
    </ThemeProvider>
  );
}
