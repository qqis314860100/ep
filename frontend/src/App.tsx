import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App as AntdApp, ConfigProvider, Spin } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import { lazy, Suspense } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AppShell } from './app/AppShell'
import { GlobalStyle } from './styles/GlobalStyle'

const SearchPage = lazy(() => import('./pages/main/search'))
const DetailPage = lazy(() => import('./pages/main/detail'))
const FavoritesPage = lazy(() => import('./pages/main/favorites'))
const MyUploadsPage = lazy(() => import('./pages/main/uploads'))
const DrawingManagementPage = lazy(() => import('./pages/sys/drawing'))
const GovernancePage = lazy(() => import('./features/governance/GovernancePage').then((module) => ({ default: module.GovernancePage })))
const UploadPage = lazy(() =>
  import('./pages/sys/file'),
)
const ModulePlaceholder = lazy(() =>
  import('./features/common/ModulePlaceholder').then((module) => ({ default: module.ModulePlaceholder })),
)

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
})

export default function App() {
  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: {
          colorPrimary: '#2f7567',
          colorInfo: '#2f7567',
          colorBgLayout: '#f3f5f3',
          colorBorder: '#dce3df',
          borderRadius: 6,
          fontFamily: 'Aptos, PingFang SC, Microsoft YaHei, sans-serif',
        },
        components: {
          Layout: { headerBg: '#ffffff', siderBg: '#182421' },
          Menu: {
            darkItemBg: '#182421',
            darkItemSelectedBg: '#2c5f54',
            darkItemHoverBg: '#243b35',
          },
          Table: { headerBg: '#f2f5f3', rowHoverBg: '#f0f6f3' },
        },
      }}
    >
      <QueryClientProvider client={queryClient}>
        <AntdApp>
          <GlobalStyle />
          <BrowserRouter>
            <AppShell>
              <Suspense fallback={<Spin fullscreen tip="正在加载" />}>
                <Routes>
                  <Route path="/" element={<SearchPage />} />
                  <Route path="/assets" element={<SearchPage />} />
                  <Route path="/assets/:id" element={<DetailPage />} />
                  <Route path="/upload" element={<UploadPage />} />
                  <Route path="/governance" element={<GovernancePage />} />
                  <Route path="/sys/drawing" element={<DrawingManagementPage />} />
                  <Route path="/sys/governance" element={<GovernancePage />} />
                  <Route path="/sys/file" element={<UploadPage />} />
                  <Route path="/favorites" element={<FavoritesPage />} />
                  <Route path="/my-uploads" element={<MyUploadsPage />} />
                  <Route path="/dictionaries" element={<ModulePlaceholder title="基础数据管理" />} />
                  <Route path="/sys/dictionaries" element={<ModulePlaceholder title="基础数据管理" />} />
                  <Route path="/settings" element={<ModulePlaceholder title="系统管理" />} />
                  <Route path="/sys/settings" element={<ModulePlaceholder title="系统管理" />} />
                  <Route path="*" element={<Navigate to="/" replace />} />
                </Routes>
              </Suspense>
            </AppShell>
          </BrowserRouter>
        </AntdApp>
      </QueryClientProvider>
    </ConfigProvider>
  )
}
