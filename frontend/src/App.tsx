import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App as AntdApp, ConfigProvider, Spin } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import { lazy, Suspense } from 'react'
import { BrowserRouter, Navigate, Route, Routes, useParams } from 'react-router-dom'
import { AppShell } from './app/AppShell'
import { GlobalStyle } from './styles/GlobalStyle'

const SearchPage = lazy(() => import('./pages/main/search'))
const DetailPage = lazy(() => import('./pages/main/detail'))
const FavoritesPage = lazy(() => import('./pages/main/favorites'))
const MyUploadsPage = lazy(() => import('./pages/main/uploads'))
const GovernancePage = lazy(() =>
  import('./pages/sys/drawing'),
)
const GovernanceIssuePoolPage = lazy(() => import('./features/governance/issues/GovernanceIssuePoolPage').then(module => ({ default: module.GovernanceIssuePoolPage })))
const GovernanceTaskDetailPage = lazy(() => import('./features/governance/tasks/GovernanceTaskDetailPage').then(module => ({ default: module.GovernanceTaskDetailPage })))
const GovernanceExecutionPage = lazy(() => import('./features/governance/execution/GovernanceExecutionPage').then(module => ({ default: module.GovernanceExecutionPage })))
const GovernanceConfirmationPage = lazy(() => import('./features/governance/confirmation/GovernanceConfirmationPage').then(module => ({ default: module.GovernanceConfirmationPage })))
const GovernanceAcceptancePage = lazy(() => import('./features/governance/acceptance/GovernanceAcceptancePage').then(module => ({ default: module.GovernanceAcceptancePage })))
const UploadPage = lazy(() =>
  import('./pages/sys/file'),
)
const DictionaryPage = lazy(() => import('./pages/sys/dictionaries'))
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

function TaskRoute({ page: Page }: { page: React.ComponentType<{ taskId: number }> }) {
  const taskId = Number(useParams().taskId)
  return Number.isInteger(taskId) && taskId > 0 ? <Page taskId={taskId} /> : <Navigate to="/sys/drawing" replace />
}

export default function App() {
  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: {
          colorPrimary: '#2f7567',
          colorInfo: '#3578c9',
          colorBgLayout: '#f4f6f5',
          colorBorder: '#dfe5e2',
          colorText: '#26322d',
          controlHeight: 32,
          fontSize: 13,
          borderRadius: 4,
          fontFamily: 'Aptos, PingFang SC, Microsoft YaHei, sans-serif',
        },
        components: {
          Layout: { headerBg: '#102b3d', siderBg: '#ffffff' },
          Menu: {
            darkItemBg: '#102b3d',
            darkItemSelectedBg: '#2c5f54',
            darkItemHoverBg: '#243b35',
          },
          Table: {
            headerBg: '#f2f5f3',
            headerColor: '#46534d',
            rowHoverBg: '#edf4f1',
            cellPaddingBlockSM: 7,
            cellPaddingInlineSM: 10,
          },
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
                  <Route path="/governance" element={<Navigate to="/sys/drawing" replace />} />
                  <Route path="/sys/drawing" element={<GovernancePage />} />
                  <Route path="/sys/drawing/issues" element={<GovernanceIssuePoolPage />} />
                  <Route path="/sys/drawing/tasks/:taskId" element={<TaskRoute page={GovernanceTaskDetailPage} />} />
                  <Route path="/sys/drawing/tasks/:taskId/execute" element={<TaskRoute page={GovernanceExecutionPage} />} />
                  <Route path="/sys/drawing/tasks/:taskId/confirm" element={<TaskRoute page={GovernanceConfirmationPage} />} />
                  <Route path="/sys/drawing/tasks/:taskId/accept" element={<TaskRoute page={GovernanceAcceptancePage} />} />
                  <Route path="/sys/file" element={<UploadPage />} />
                  <Route path="/favorites" element={<FavoritesPage />} />
                  <Route path="/my-uploads" element={<MyUploadsPage />} />
                  <Route path="/dictionaries" element={<DictionaryPage />} />
                  <Route path="/sys/dictionaries" element={<DictionaryPage />} />
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
