import { useState } from 'react'
import { Layout } from 'antd'
import './App.css'

// 导入Context Provider
import { AuthProvider } from './context/AuthContext'
import { CartProvider } from './context/CartContext'
import { BooksProvider } from './context/BooksContext'
import { OrdersProvider } from './context/OrdersContext'

// 导入组件
import Header from './components/Header'
import CartModal from './components/CartModal'
import LoginModal from './components/LoginModal'
import RegisterModal from './components/RegisterModal'
import SuccessModal from './components/SuccessModal'
import AIAssistant from './components/AIAssistant'

// 导入页面
import HomePage from './pages/HomePage'
import BookDetailPage from './pages/BookDetailPage'
import OrdersPage from './pages/OrdersPage'
import StatisticsPage from './pages/StatisticsPage'
import DashboardPage from './pages/DashboardPage'
import AboutPage from './pages/AboutPage'

// 导入Context Hooks
import { useBooks } from './context/useBooks';
import { useAuth } from './context/useAuth';

const { Content } = Layout

function AppContent() {
  const [currentPage, setCurrentPage] = useState('home')
  const { showDetail, backToHome } = useBooks()
  const { currentUser } = useAuth()

  // 根据当前页面和状态渲染不同的内容
  const renderContent = () => {
    if (showDetail) {
      return <BookDetailPage />
    }

    switch (currentPage) {
      case 'home':
        return <HomePage />
      case 'orders':
        return <OrdersPage />
      case 'statistics':
        return currentUser?.role === 'ADMIN' ? <StatisticsPage /> : <HomePage />
      case 'dashboard':
        return currentUser?.role === 'ADMIN' ? <DashboardPage /> : <HomePage />
      case 'about':
        return <AboutPage />
      default:
        return <HomePage />
    }
  }

  return (
    <Layout className="app-layout">
      {/* 顶部导航栏 */}
      <Header currentPage={currentPage} setCurrentPage={setCurrentPage} setShowDetail={backToHome} />
      
      {/* 主要内容区域 */}
      <Content className="app-content">
        <div className="content-container">
          {renderContent()}
        </div>
      </Content>
      
      {/* 模态框 */}
      <CartModal />
      <SuccessModal />
      {/* 智能助手 */}
      <AIAssistant />
    </Layout>
  )
}

function App() {
  return (
    <AuthProvider>
      <SessionContent />
    </AuthProvider>
  )
}

function SessionContent() {
  const { currentUser } = useAuth()
  return (
      <CartProvider>
        <BooksProvider>
          <LoginModal />
          <RegisterModal />
          <OrdersProvider key={currentUser?.id ?? 'guest'}>
            <AppContent />
          </OrdersProvider>
        </BooksProvider>
      </CartProvider>
  )
}

export default App
