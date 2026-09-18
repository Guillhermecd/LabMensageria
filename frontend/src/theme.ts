import { theme as antdTheme, type ThemeConfig } from 'antd';

// Gradiente da marca — usar apenas em botões primários, barras de
// progresso, cards hero e fundo da tela de login. Nunca em superfícies
// de leitura (cards de dados, tabelas, textos).
export const BRAND_GRADIENT = 'linear-gradient(135deg, #7C8CFF 0%, #5B6EF5 55%, #3B4FE0 100%)';

// Cores semânticas do domínio de mensageria — nunca a cor primária.
// Espelham os estados usados no engine de simulação (ok/warning/danger)
// mais "info" para eventos neutros (burst, log).
export const semantic = {
  light: {
    ok: { text: '#1B7A5D', bg: '#E4F6F0' },
    warning: { text: '#A15C00', bg: '#FCEFDB' },
    danger: { text: '#C22B3A', bg: '#FCE9EC' },
    info: { text: '#3B4FE0', bg: '#EAEDFC' },
  },
  dark: {
    ok: { text: '#22D3A0', bg: '#12261F' },
    warning: { text: '#F5A524', bg: '#2A2214' },
    danger: { text: '#FF6B4A', bg: '#2A1A17' },
    info: { text: '#7C8CFF', bg: '#1A1D2E' },
  },
} as const;

export const lightTheme: ThemeConfig = {
  algorithm: antdTheme.defaultAlgorithm,
  token: {
    colorPrimary: '#5B6EF5',
    colorLink: '#3B4FE0',
    colorText: '#1D2129',
    colorBgBase: '#FFFFFF',
    colorBgLayout: '#F5F6FB',
    borderRadius: 10,
  },
};

export const darkTheme: ThemeConfig = {
  algorithm: antdTheme.darkAlgorithm,
  token: {
    colorPrimary: '#7C8CFF',
    colorLink: '#A5B0FF',
    colorBgBase: '#14171A',
    colorBgLayout: '#0F1114',
    colorBorder: '#2A2F35',
    borderRadius: 10,
  },
};
