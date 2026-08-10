import { createGlobalStyle } from 'styled-components'

export const GlobalStyle = createGlobalStyle`
  :root {
    font-family: Aptos, "PingFang SC", "Microsoft YaHei", sans-serif;
    color: #202824;
    background: #f4f6f5;
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
    min-width: 320px;
    min-height: 100vh;
    background: #f4f6f5;
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

  * {
    scrollbar-color: #c2ccc7 transparent;
    scrollbar-width: thin;
  }

  *::-webkit-scrollbar {
    width: 8px;
    height: 8px;
  }

  *::-webkit-scrollbar-thumb {
    background: #c2ccc7;
    border: 2px solid transparent;
    border-radius: 8px;
    background-clip: content-box;
  }

  .ant-btn,
  .ant-input,
  .ant-select-selector,
  .ant-segmented,
  .ant-tag {
    letter-spacing: 0;
  }

  .ant-table-wrapper .ant-table-thead > tr > th {
    font-size: 12px;
    font-weight: 650;
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
