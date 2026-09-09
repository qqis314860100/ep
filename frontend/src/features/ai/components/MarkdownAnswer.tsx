import ReactMarkdown from 'react-markdown'
import styled from 'styled-components'

/** 完成态回答的 Markdown 渲染（无 raw HTML，避免注入），样式对齐 ep 设计基调。 */
const Prose = styled.div`
  font-size: 14px;
  line-height: 1.7;
  color: #26322d;
  word-break: break-word;

  > *:first-child {
    margin-top: 0;
  }
  > *:last-child {
    margin-bottom: 0;
  }

  p {
    margin: 0.45em 0;
  }
  h1, h2, h3, h4 {
    margin: 0.9em 0 0.4em;
    font-weight: 600;
  }
  h1 { font-size: 1.3em; }
  h2 { font-size: 1.18em; }
  h3 { font-size: 1.08em; }
  h4 { font-size: 1em; }
  ul, ol {
    margin: 0.45em 0;
    padding-left: 1.5em;
  }
  li {
    margin: 0.22em 0;
  }
  a {
    color: #2f7567;
  }
  blockquote {
    margin: 0.55em 0;
    padding: 2px 0 2px 12px;
    border-left: 3px solid #dfe5e2;
    color: #5a6b74;
  }
  code {
    background: #eef2f0;
    border-radius: 4px;
    padding: 1px 5px;
    font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
    font-size: 0.9em;
  }
  pre {
    margin: 0.55em 0;
    padding: 10px 12px;
    background: #f4f6f5;
    border: 1px solid #e6e8ea;
    border-radius: 8px;
    overflow-x: auto;
  }
  pre code {
    background: transparent;
    padding: 0;
  }
  table {
    width: 100%;
    margin: 0.6em 0;
    border-collapse: collapse;
  }
  th, td {
    border: 1px solid #e0e5e2;
    padding: 6px 10px;
    text-align: left;
    font-size: 13px;
  }
  th {
    background: #f2f5f3;
  }
  hr {
    border: 0;
    border-top: 1px solid #e6e8ea;
    margin: 0.9em 0;
  }
`

export default function MarkdownAnswer({ content }: { content: string }) {
  return (
    <Prose>
      <ReactMarkdown>{content}</ReactMarkdown>
    </Prose>
  )
}
