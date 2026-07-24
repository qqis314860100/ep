import {
  CloudUploadOutlined,
  DatabaseOutlined,
  FileSearchOutlined,
  HeartOutlined,
  SettingOutlined,
} from '@ant-design/icons'
import { Avatar, Button, Layout, Space, Typography } from 'antd'
import { type ReactNode } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import styled from 'styled-components'

const { Header, Content } = Layout

const Shell = styled(Layout)`
  min-height: 100vh;
  background: #f7f8f6;
`

const TopBar = styled(Header)`
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 42px;
  height: 68px;
  padding: 0 max(28px, calc((100vw - 1320px) / 2));
  background: rgba(255, 255, 255, 0.96);
  border-bottom: 1px solid #e5e8e3;
  backdrop-filter: blur(12px);

  @media (max-width: 1100px) {
    gap: 24px;
    padding: 0 22px;
  }
`

const Brand = styled.button`
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 10px;
  height: auto;
  padding: 0;
  color: #1f302a;
  background: transparent;
  border: 0;
  cursor: pointer;
  line-height: normal;
  text-align: left;
`

const Mark = styled.span`
  position: relative;
  display: inline-block;
  width: 28px;
  height: 28px;
  background: #214f43;
  border-radius: 8px;

  &::before,
  &::after {
    position: absolute;
    content: '';
    background: #e3b36a;
    border-radius: 2px;
  }

  &::before {
    top: 7px;
    left: 7px;
    width: 14px;
    height: 2px;
    box-shadow: 0 6px 0 #e3b36a;
  }

  &::after {
    top: 7px;
    left: 7px;
    width: 2px;
    height: 14px;
  }
`

const BrandName = styled.div`
  font-size: 15px;
  font-weight: 700;
  line-height: 18px;
`

const BrandMeta = styled.div`
  margin-top: 2px;
  color: #849089;
  font-size: 11px;
  font-weight: 400;
`

const Nav = styled.nav`
  display: flex;
  align-self: stretch;
  gap: 26px;

  @media (max-width: 1100px) {
    gap: 16px;
  }
`

const NavItem = styled.button<{ $active: boolean }>`
  position: relative;
  padding: 0;
  color: ${({ $active }) => ($active ? '#214f43' : '#66736d')};
  font-size: 14px;
  font-weight: ${({ $active }) => ($active ? 600 : 400)};
  background: transparent;
  border: 0;
  cursor: pointer;

  &::after {
    position: absolute;
    right: 0;
    bottom: 0;
    left: 0;
    height: 3px;
    content: '';
    background: ${({ $active }) => ($active ? '#d49a4c' : 'transparent')};
    border-radius: 3px 3px 0 0;
  }
`

const HeaderSpacer = styled.div`
  flex: 1;
`

const Main = styled(Content)`
  width: min(1320px, calc(100% - 56px));
  margin: 0 auto;
  padding: 30px 0 56px;

  @media (max-width: 1100px) {
    width: calc(100% - 44px);
  }
`

interface AppShellProps {
  children: ReactNode
}

export function AppShell({ children }: AppShellProps) {
  const location = useLocation()
  const navigate = useNavigate()
  const isSearch = location.pathname === '/' || location.pathname.startsWith('/assets')
  const isSystem = location.pathname.startsWith('/sys') || location.pathname === '/governance' || location.pathname === '/dictionaries'

  return (
    <Shell>
      <TopBar>
        <Brand onClick={() => navigate('/')} aria-label="返回搜索首页">
          <Mark aria-hidden="true" />
          <div>
            <BrandName>图纸资料库</BrandName>
            <BrandMeta>生产知识资产平台</BrandMeta>
          </div>
        </Brand>
        <Nav aria-label="主导航">
          <NavItem $active={isSearch} onClick={() => navigate('/')}>
            <FileSearchOutlined style={{ marginRight: 6 }} />检索资料
          </NavItem>
          <NavItem $active={location.pathname === '/upload'} onClick={() => navigate('/upload')}>
            <CloudUploadOutlined style={{ marginRight: 6 }} />上传资料
          </NavItem>
          <NavItem $active={location.pathname === '/favorites'} onClick={() => navigate('/favorites')}>
            <HeartOutlined style={{ marginRight: 6 }} />我的收藏
          </NavItem>
          <NavItem $active={location.pathname === '/my-uploads'} onClick={() => navigate('/my-uploads')}>
            <CloudUploadOutlined style={{ marginRight: 6 }} />我的上传
          </NavItem>
          <NavItem $active={isSystem} onClick={() => navigate('/sys/drawing')}>
            <DatabaseOutlined style={{ marginRight: 6 }} />系统管理
          </NavItem>
        </Nav>
        <HeaderSpacer />
        <Space size={10}>
          <Avatar size={32} style={{ background: '#2f7567' }}>陈</Avatar>
          <div>
            <Typography.Text strong style={{ fontSize: 13 }}>陈工</Typography.Text>
            <Typography.Text type="secondary" style={{ display: 'block', fontSize: 11 }}>内容管理员</Typography.Text>
          </div>
          <Button type="text" icon={<SettingOutlined />} aria-label="设置" onClick={() => navigate('/sys/settings')} />
        </Space>
      </TopBar>
      <Main>{children}</Main>
    </Shell>
  )
}
