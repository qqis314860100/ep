import {
  ApartmentOutlined,
  DatabaseOutlined,
  FileTextOutlined,
  SafetyCertificateOutlined,
  ToolOutlined,
} from '@ant-design/icons'
import styled from 'styled-components'

interface WelcomePanelProps {
  onAsk: (question: string) => void
}

const categories = [
  {
    icon: DatabaseOutlined,
    title: '资产与数模',
    prompts: [
      '宁德基地 A 拉线有哪些焊接数模？',
      'B 线体装配工位关联了哪些资产文档？',
    ],
  },
  {
    icon: ApartmentOutlined,
    title: '图纸与结构',
    prompts: [
      '输送模块布置数模的版本关系是怎样的？',
      '如何查找指定图号的工程变更记录？',
    ],
  },
  {
    icon: SafetyCertificateOutlined,
    title: '标准与规范',
    prompts: [
      '三维数模的命名规范有哪些要求？',
      '归档交付需要满足什么标准？',
    ],
  },
  {
    icon: ToolOutlined,
    title: '异常排查',
    prompts: [
      '焊接飞溅的常见原因有哪些？',
      '装配尺寸超差一般如何定位原因？',
    ],
  },
]

const Wrapper = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 44px 18px 28px;
  text-align: center;
`

const Logo = styled.div`
  width: 60px;
  height: 60px;
  border-radius: 16px;
  background: #e7f0ec;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
`

const Brand = styled.div`
  color: #2f7567;
  font-size: 26px;
  line-height: 1;
`

const Title = styled.h2`
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  color: #102b3d;
`

const Subtitle = styled.p`
  margin: 8px 0 0;
  max-width: 520px;
  font-size: 13px;
  line-height: 1.8;
  color: #5a6b74;
`

const Grid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  width: 100%;
  max-width: 640px;
  margin-top: 26px;
`

const Card = styled.div`
  border: 1px solid #e6e8ea;
  border-radius: 10px;
  padding: 12px 14px;
  text-align: left;
  background: #fff;
  transition: border-color 0.2s, box-shadow 0.2s;

  &:hover {
    border-color: #cfe0d9;
    box-shadow: 0 2px 8px rgba(47, 117, 103, 0.08);
  }
`

const CardHead = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  color: #26322d;
  font-size: 13px;
  font-weight: 600;
`

const PromptButton = styled.button`
  display: block;
  width: 100%;
  text-align: left;
  padding: 6px 10px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #5a6b74;
  font-size: 13px;
  line-height: 1.5;
  cursor: pointer;

  &:hover {
    background: #e7f0ec;
    color: #2f7567;
  }
`

const Disclaimer = styled.p`
  margin: 22px 0 0;
  font-size: 11px;
  color: #a5b2b8;
`

/** 空会话欢迎面板：品牌块 + 示例问题分类卡片（点击即提问）+ 免责声明。借鉴 ai-rag ChatEmptyWelcome。 */
export default function WelcomePanel({ onAsk }: WelcomePanelProps) {
  return (
    <Wrapper>
      <Logo>
        <Brand>
          <FileTextOutlined />
        </Brand>
      </Logo>
      <Title>AI 助手</Title>
      <Subtitle>
        基于资产库与技术文档，为你提供可追溯的问答。
        <br />
        点选下方问题开始，或直接输入你的疑问。
      </Subtitle>
      <Grid>
        {categories.map(({ icon: Icon, title, prompts }) => (
          <Card key={title}>
            <CardHead>
              <Icon style={{ color: '#2f7567' }} />
              {title}
            </CardHead>
            {prompts.map((prompt) => (
              <PromptButton key={prompt} type="button" onClick={() => onAsk(prompt)}>
                {prompt}
              </PromptButton>
            ))}
          </Card>
        ))}
      </Grid>
      <Disclaimer>AI 生成内容仅供参考，请以资产库原文与正式文档为准</Disclaimer>
    </Wrapper>
  )
}
