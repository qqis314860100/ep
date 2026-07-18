import { createGlobalStyle } from 'styled-components'

export const GlobalStyle = createGlobalStyle`
  :root {
    font-family: Aptos, "PingFang SC", "Microsoft YaHei", sans-serif;
    color: #202824;
    background: #f3f5f3;
    font-synthesis: none;
    text-rendering: optimizeLegibility;
    -webkit-font-smoothing: antialiased;
    letter-spacing: 0;
  }

  * {
    box-sizing: border-box;
  }

  body {
    margin: 0;
    min-width: 1100px;
    min-height: 100vh;
    background: #f3f5f3;
  }

  button,
  input,
  textarea,
  select {
    font: inherit;
  }

  #root {
    min-height: 100vh;
  }

  .sr-only {
    position: absolute;
    width: 1px;
    height: 1px;
    padding: 0;
    margin: -1px;
    overflow: hidden;
    clip: rect(0, 0, 0, 0);
    white-space: nowrap;
    border: 0;
  }
`
