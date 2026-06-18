import type { Config } from 'tailwindcss'

export default <Config>{
  content: [
    './components/**/*.{vue,ts}',
    './layouts/**/*.vue',
    './pages/**/*.vue',
    './app.vue',
  ],
  theme: {
    extend: {
      colors: {
        primary: {
          50: '#fff1f0',
          100: '#ffddd9',
          200: '#ffc1ba',
          300: '#ff968e',
          400: '#ff5c52',
          500: '#ff2d21',
          600: '#ed1308',
          700: '#c80c06',
          800: '#a50f0a',
          900: '#88130e',
          DEFAULT: '#ff2d21',
        },
        secondary: {
          50: '#f0f7ff',
          100: '#e0efff',
          200: '#b9dfff',
          300: '#7cc5ff',
          400: '#36a9ff',
          500: '#0b8dff',
          600: '#006fd6',
          700: '#0058ad',
          800: '#034b8f',
          900: '#083f76',
          DEFAULT: '#0b8dff',
        },
      },
      fontFamily: {
        sans: ['PingFang SC', 'Microsoft YaHei', 'sans-serif'],
      },
    },
  },
  plugins: [],
}
