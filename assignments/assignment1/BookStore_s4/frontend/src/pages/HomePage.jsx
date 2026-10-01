import React from 'react';
import { Button } from 'antd';
import { useBooks } from '../context/BooksContext';
import { useCart } from '../context/CartContext';

const HomePage = () => {
  const { filteredBooks, viewBookDetail } = useBooks();
  const { addToCart } = useCart();

  return (
    <div className="main-content">
      <h2>欢迎来到智能在线书店</h2>
      <p>探索我们的精选书籍，享受阅读的乐趣</p>
      
      {/* 书籍展示区域 */}
      <div className="books-section">
        <h3>热门书籍</h3>
        <div className="books-grid">
          {/* 书籍卡片 */}
          {filteredBooks.map(book => (
            <div 
              key={book.id} 
              className="book-card"
              onClick={() => viewBookDetail(book)}
              style={{ cursor: 'pointer' }}
            >
              <div className="book-cover">
                {book.coverImage ? (
                  book.coverImage.startsWith('http') ? (
                    <img 
                      src={book.coverImage} 
                      alt={book.title} 
                      className="book-cover-image"
                    />
                  ) : (
                    <img 
                      src={`data:image/svg+xml;base64,${book.coverImage}`} 
                      alt={book.title} 
                      className="book-cover-image"
                    />
                  )
                ) : (
                  <span>无封面</span>
                )}
              </div>
              <div className="book-info">
                <h4>{book.title}</h4>
                <p>{book.author}</p>
                <p className="book-price">¥{book.price.toFixed(2)}</p>
                <Button 
                  type="primary" 
                  className="add-to-cart-button"
                  onClick={(e) => {
                    e.stopPropagation(); // 阻止事件冒泡
                    addToCart(book);
                  }}
                >
                  加入购物车
                </Button>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

export default HomePage;