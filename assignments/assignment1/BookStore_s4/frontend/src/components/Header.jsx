import React, { useState } from 'react';
import { Layout, Menu, Input, Button } from 'antd';
import { SearchOutlined, ShoppingCartOutlined, MenuOutlined } from '@ant-design/icons';
import { useAuth } from '../context/AuthContext';
import { useCart } from '../context/CartContext';
import { useBooks } from '../context/BooksContext';

const { Header: AntHeader } = Layout;

const Header = ({ currentPage, setCurrentPage, setShowDetail }) => {
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const { currentUser, setIsLoginModalOpen, setIsRegisterModalOpen, handleLogout } = useAuth();
  const { cart, setIsCartModalOpen } = useCart();
  const { searchKeyword, setSearchKeyword } = useBooks();

  // 打开购物车
  const openCart = () => {
    setIsCartModalOpen(true);
  };

  return (
    <AntHeader className="app-header">
      <div className="header-content">
        {/* Logo */}
        <div className="logo">
          <h1>BookStore</h1>
        </div>
        
        {/* 搜索框 - 桌面版 */}
        <div className="search-desktop">
          <Input
            placeholder="搜索书籍"
            prefix={<SearchOutlined />}
            className="search-input"
            value={searchKeyword}
            onChange={(e) => setSearchKeyword(e.target.value)}
          />
        </div>
        
        {/* 导航菜单 - 桌面版 */}
        <div className="nav-desktop">
          <Menu 
            mode="horizontal" 
            className="nav-menu" 
            selectedKeys={[currentPage]}
            items={[
              { key: 'home', label: '首页', onClick: () => { setCurrentPage('home'); setShowDetail(false); } },
              { key: 'orders', label: '订单列表', onClick: () => { setCurrentPage('orders'); setShowDetail(false); } },
              { key: 'statistics', label: '销量统计', onClick: () => { setCurrentPage('statistics'); setShowDetail(false); } },
              ...(currentUser && currentUser.role === 'ADMIN' ? [{ key: 'dashboard', label: '管理员Dashboard', onClick: () => { setCurrentPage('dashboard'); setShowDetail(false); } }] : []),
              { key: 'about', label: '关于我们', onClick: () => { setCurrentPage('about'); setShowDetail(false); } }
            ]}
          />
        </div>
        
        {/* 购物车按钮 */}
        <div className="cart-button">
          <Button 
            icon={<ShoppingCartOutlined />} 
            onClick={openCart}
            className="cart-btn"
          >
            购物车
            {cart.length > 0 && (
              <span className="cart-badge">{cart.length}</span>
            )}
          </Button>
        </div>
        
        {/* 登录/注册按钮 */}
        <div className="auth-buttons">
          {currentUser ? (
            <div className="user-info">
              <span className="username">{currentUser.username}</span>
              <Button className="auth-button" onClick={handleLogout}>退出登录</Button>
            </div>
          ) : (
            <>
              <Button className="auth-button" onClick={() => setIsLoginModalOpen(true)}>登录</Button>
              <Button type="primary" className="auth-button" onClick={() => setIsRegisterModalOpen(true)}>注册</Button>
            </>
          )}
        </div>
        
        {/* 移动端菜单按钮 */}
        <div className="mobile-menu-button">
          <Button 
            icon={<MenuOutlined />} 
            onClick={() => setIsMenuOpen(!isMenuOpen)}
          />
        </div>
      </div>
      
      {/* 移动端搜索框 */}
      {isMenuOpen && (
        <div className="search-mobile">
          <Input
            placeholder="搜索书籍"
            prefix={<SearchOutlined />}
            className="search-input"
            value={searchKeyword}
            onChange={(e) => setSearchKeyword(e.target.value)}
          />
        </div>
      )}
      
      {/* 移动端导航菜单 */}
      {isMenuOpen && (
        <Menu 
          mode="inline" 
          className="mobile-nav-menu"
          items={[
            { key: 'home', label: '首页', onClick: () => { setCurrentPage('home'); setShowDetail(false); setIsMenuOpen(false); } },
            { key: 'orders', label: '订单列表', onClick: () => { setCurrentPage('orders'); setShowDetail(false); setIsMenuOpen(false); } },
            { key: 'statistics', label: '销量统计', onClick: () => { setCurrentPage('statistics'); setShowDetail(false); setIsMenuOpen(false); } },
            ...(currentUser && currentUser.role === 'ADMIN' ? [{ key: 'dashboard', label: '管理员Dashboard', onClick: () => { setCurrentPage('dashboard'); setShowDetail(false); setIsMenuOpen(false); } }] : []),
            { key: 'about', label: '关于我们', onClick: () => { setCurrentPage('about'); setShowDetail(false); setIsMenuOpen(false); } },
            { key: 'cart', label: `购物车 ${cart.length > 0 ? `(${cart.length})` : ''}`, onClick: () => { openCart(); setIsMenuOpen(false); } }
          ]}
        />
      )}
    </AntHeader>
  );
};

export default Header;