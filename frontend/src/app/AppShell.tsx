import {
  BookOutlined,
  CloudUploadOutlined,
  DatabaseOutlined,
  FileSearchOutlined,
  FileTextOutlined,
  HeartOutlined,
  HomeOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  QuestionCircleOutlined,
  SettingOutlined,
} from '@ant-design/icons'
import { Avatar, Button, Layout, Tooltip } from 'antd'
import { type ReactNode, useEffect, useMemo, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import styled from 'styled-components'
import { NotificationBell } from './NotificationBell'

const { Header, Sider, Content } = Layout
const expandedWidth = 184
const collapsedWidth = 56

const Shell = styled(Layout)`
  min-height: 100vh;
  background: #f4f6f5;
`

const TopBar = styled(Header)`
  position: sticky;
  top: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  height: 50px;
  padding: 0 12px 0 14px;
  color: #fff;
  background: #102b3d;
  border-bottom: 1px solid #17384f;
  line-height: normal;
`

const CollapseButton = styled(Button)`
  width: 30px;
  height: 30px;
  margin-right: 8px;
  color: #c6d3dc !important;

  &:hover,
  &:focus-visible {
    color: #fff !important;
    background: rgba(255, 255, 255, 0.09) !important;
  }

  @media (max-width: 720px) { display: none; }
`

const Brand = styled.button`
  display: flex;
  align-items: center;
  gap: 9px;
  min-width: 0;
  padding: 0;
  color: #fff;
  background: transparent;
  border: 0;
  cursor: pointer;
  text-align: left;
`

const Mark = styled.span`
  position: relative;
  display: inline-block;
  flex: 0 0 auto;
  width: 27px;
  height: 27px;
  background: #2f7567;
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 5px;

  &::before,
  &::after {
    position: absolute;
    content: '';
    background: #e3b36a;
    border-radius: 1px;
  }

  &::before {
    top: 7px;
    left: 7px;
    width: 13px;
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
  overflow: hidden;
  font-size: 14px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;

  @media (max-width: 560px) { display: none; }
`

const ModuleName = styled.div`
  margin-left: 20px;
  padding-left: 20px;
  color: #c6d3dc;
  border-left: 1px solid rgba(255, 255, 255, 0.18);
  font-size: 12px;

  @media (max-width: 560px) {
    margin-left: 8px;
    padding-left: 8px;
  }
`

const HeaderSpacer = styled.div`
  flex: 1;
`

const HeaderActions = styled.div`
  display: flex;
  align-items: center;
  gap: 3px;

  .ant-btn {
    color: #c6d3dc;
  }

  .ant-btn:hover,
  .ant-btn:focus-visible {
    color: #fff !important;
    background: rgba(255, 255, 255, 0.09) !important;
  }
`

const User = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: 8px;
  padding-left: 12px;
  border-left: 1px solid rgba(255, 255, 255, 0.18);
`

const UserName = styled.div`
  color: #fff;
  font-size: 12px;
  font-weight: 600;
`

const UserRole = styled.div`
  margin-top: 1px;
  color: #9fb1bd;
  font-size: 10px;
`

const UserDetails = styled.div`
  @media (max-width: 560px) { display: none; }
`

const Body = styled(Layout)`
  min-height: calc(100vh - 50px);
  background: #f4f6f5;
`

const Navigation = styled(Sider)`
  position: sticky !important;
  top: 50px;
  align-self: flex-start;
  height: calc(100vh - 50px);
  overflow: hidden auto;
  background: #fff !important;
  border-right: 1px solid #dfe5e2;
`

const NavSection = styled.div`
  padding: 10px 8px 4px;
`

const NavLabel = styled.div<{ $collapsed: boolean }>`
  height: ${({ $collapsed }) => ($collapsed ? '4px' : '24px')};
  padding: ${({ $collapsed }) => ($collapsed ? '0' : '5px 10px 4px')};
  overflow: hidden;
  color: #97a29d;
  font-size: 10px;
  font-weight: 600;
  text-transform: uppercase;
  white-space: nowrap;
`

const NavItem = styled.button<{ $active: boolean; $collapsed: boolean }>`
  position: relative;
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  align-items: center;
  width: 100%;
  min-height: 38px;
  margin-bottom: 2px;
  padding: 0 ${({ $collapsed }) => ($collapsed ? '3px' : '8px')};
  overflow: hidden;
  color: ${({ $active }) => ($active ? '#245f54' : '#475650')};
  text-align: left;
  background: ${({ $active }) => ($active ? '#eaf2ef' : 'transparent')};
  border: 0;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  font-weight: ${({ $active }) => ($active ? 650 : 400)};
  white-space: nowrap;

  &::before {
    position: absolute;
    top: 7px;
    bottom: 7px;
    left: 0;
    width: 3px;
    content: '';
    background: ${({ $active }) => ($active ? '#2f7567' : 'transparent')};
    border-radius: 0 2px 2px 0;
  }

  .anticon {
    justify-self: center;
    color: ${({ $active }) => ($active ? '#2f7567' : '#68756f')};
    font-size: 16px;
  }

  &:hover,
  &:focus-visible {
    color: #245f54;
    background: ${({ $active }) => ($active ? '#eaf2ef' : '#f1f5f3')};
    outline: none;
  }
`

const Main = styled(Content)<{ $fixed?: boolean }>`
  min-width: 0;
  padding: 14px 16px 28px;
  background: #f4f6f5;
  height: ${({ $fixed }) => ($fixed ? 'calc(100vh - 50px)' : 'auto')};
  overflow: ${({ $fixed }) => ($fixed ? 'hidden' : 'visible')};

  @media (max-width: 720px) { padding: 10px 8px 24px; }
`

interface AppShellProps {
  children: ReactNode
}

interface NavigationItem {
  key: string
  label: string
  path: string
  icon: ReactNode
  active: (pathname: string) => boolean
}

const primaryItems: NavigationItem[] = [
  { key: 'home', label: '首页', path: '/home', icon: <HomeOutlined />, active: (path) => path === '/home' },
  { key: 'search', label: '资料检索', path: '/', icon: <FileSearchOutlined />, active: (path) => path === '/' || path.startsWith('/assets') },
  { key: 'documents', label: '文档中心', path: '/documents', icon: <FileTextOutlined />, active: (path) => path.startsWith('/documents') },
  { key: 'upload', label: '上传资料', path: '/upload', icon: <CloudUploadOutlined />, active: (path) => path === '/upload' || path === '/sys/file' },
  { key: 'favorites', label: '我的收藏', path: '/favorites', icon: <HeartOutlined />, active: (path) => path === '/favorites' },
  { key: 'my-uploads', label: '我的上传', path: '/my-uploads', icon: <BookOutlined />, active: (path) => path === '/my-uploads' },
]

const managementItems: NavigationItem[] = [
  { key: 'governance', label: '数据治理', path: '/sys/drawing', icon: <DatabaseOutlined />, active: (path) => path === '/governance' || path.startsWith('/sys/drawing') },
  { key: 'dictionaries', label: '基础数据', path: '/sys/dictionaries', icon: <BookOutlined />, active: (path) => path === '/dictionaries' || path === '/sys/dictionaries' },
  { key: 'settings', label: '系统管理', path: '/sys/settings', icon: <SettingOutlined />, active: (path) => path === '/settings' || path === '/sys/settings' },
]

export function AppShell({ children }: AppShellProps) {
  const location = useLocation()
  const navigate = useNavigate()
  const [collapsed, setCollapsed] = useState(() => window.localStorage.getItem('workspace-nav-collapsed') === 'true')
  const [narrow, setNarrow] = useState(() => window.matchMedia('(max-width: 720px)').matches)
  const allItems = useMemo(() => [...primaryItems, ...managementItems], [])
  const currentModule = allItems.find((item) => item.active(location.pathname))?.label ?? '生产知识资产平台'
  const isUploadRoute = location.pathname === '/upload' || location.pathname === '/sys/file'

  useEffect(() => {
    window.scrollTo(0, 0)
  }, [location.pathname])

  useEffect(() => {
    const media = window.matchMedia('(max-width: 720px)')
    const update = () => setNarrow(media.matches)
    media.addEventListener('change', update)
    return () => media.removeEventListener('change', update)
  }, [])

  const navigationCollapsed = collapsed || narrow

  const toggleCollapsed = () => {
    setCollapsed((current) => {
      window.localStorage.setItem('workspace-nav-collapsed', String(!current))
      return !current
    })
  }

  const renderItem = (item: NavigationItem) => (
    <Tooltip key={item.key} title={navigationCollapsed ? item.label : undefined} placement="right">
      <NavItem
        type="button"
        $active={item.active(location.pathname)}
        $collapsed={navigationCollapsed}
        aria-label={item.label}
        aria-current={item.active(location.pathname) ? 'page' : undefined}
        onClick={() => navigate(item.path)}
      >
        {item.icon}
        {!navigationCollapsed && <span>{item.label}</span>}
      </NavItem>
    </Tooltip>
  )

  return (
    <Shell>
      <TopBar>
        <CollapseButton type="text" icon={navigationCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />} aria-label={navigationCollapsed ? '展开导航' : '收起导航'} onClick={toggleCollapsed} />
        <Brand onClick={() => navigate('/')} aria-label="返回资料检索">
          <Mark aria-hidden="true" />
          <BrandName>数模资产中心</BrandName>
        </Brand>
        <ModuleName>{currentModule}</ModuleName>
        <HeaderSpacer />
        <HeaderActions>
          <Tooltip title="帮助"><Button type="text" icon={<QuestionCircleOutlined />} aria-label="帮助" /></Tooltip>
          <NotificationBell />
          <User>
            <Avatar size={28} style={{ background: '#2f7567' }}>陈</Avatar>
            <UserDetails>
              <UserName>陈工</UserName>
              <UserRole>内容管理员</UserRole>
            </UserDetails>
          </User>
        </HeaderActions>
      </TopBar>
      <Body>
        <Navigation width={expandedWidth} collapsedWidth={collapsedWidth} collapsed={navigationCollapsed} trigger={null}>
          <NavSection>
            <NavLabel $collapsed={navigationCollapsed}>资产工作台</NavLabel>
            {primaryItems.map(renderItem)}
          </NavSection>
          <NavSection>
            <NavLabel $collapsed={navigationCollapsed}>管理与治理</NavLabel>
            {managementItems.map(renderItem)}
          </NavSection>
        </Navigation>
        <Main $fixed={isUploadRoute}>{children}</Main>
      </Body>
    </Shell>
  )
}
