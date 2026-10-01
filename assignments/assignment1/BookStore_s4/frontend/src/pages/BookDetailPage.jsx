import React from 'react';
import { Button } from 'antd';
import { useBooks } from '../context/BooksContext';
import { useCart } from '../context/CartContext';

const BookDetailPage = () => {
  const { currentBook, backToHome } = useBooks();
  const { addToCart } = useCart();

  if (!currentBook) return null;

  return (
    <div className="book-detail">
      <Button 
        type="default" 
        onClick={backToHome}
        style={{ marginBottom: '20px' }}
      >
        返回首页
      </Button>
      <div className="book-detail-content">
        <div className="book-detail-cover">
          {currentBook.coverImage ? (
            currentBook.coverImage.startsWith('http') ? (
              <img 
                src={currentBook.coverImage} 
                alt={currentBook.title} 
                className="book-detail-image"
              />
            ) : (
              <img 
                src={`data:image/svg+xml;base64,${currentBook.coverImage}`} 
                alt={currentBook.title} 
                className="book-detail-image"
              />
            )
          ) : (
            <div className="book-detail-cover-placeholder">无封面</div>
          )}
        </div>
        <div className="book-detail-info">
          <h2>{currentBook.title}</h2>
          <p className="book-detail-author">作者：{currentBook.author}</p>
          {currentBook.isbn && (
            <p className="book-detail-isbn">ISBN：{currentBook.isbn}</p>
          )}
          <p className="book-detail-price">¥{currentBook.price.toFixed(2)}</p>
          <p className="book-detail-stock">库存：{currentBook.stock} 本</p>
          <div className="book-detail-description">
            <h3>内容简介</h3>
            <p>{currentBook.description || '暂无简介'}</p>
          </div>
          <Button 
            type="primary" 
            size="large"
            style={{ marginTop: '20px' }}
            onClick={() => addToCart(currentBook)}
          >
            加入购物车
          </Button>
        </div>
      </div>
    </div>
  );
};

export default BookDetailPage;