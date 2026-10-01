import React, { createContext, useState, useContext, useEffect, useCallback } from 'react';
import bookService from '../services/bookService';

const BooksContext = createContext();

export const useBooks = () => {
  const context = useContext(BooksContext);
  if (!context) {
    throw new Error('useBooks must be used within a BooksProvider');
  }
  return context;
};

export const BooksProvider = ({ children }) => {
  const [books, setBooks] = useState([]);
  const [loading, setLoading] = useState(false);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [currentBook, setCurrentBook] = useState(null);
  const [showDetail, setShowDetail] = useState(false);

  // 从后端 API 获取书籍数据
  const fetchBooks = useCallback(async () => {
    try {
      setLoading(true);
      const data = await bookService.getBooks();
      setBooks(data);
    } catch (error) {
      console.error('获取书籍数据失败:', error);
    } finally {
      setLoading(false);
    }
  }, []);

  // 初始化时获取书籍数据
  useEffect(() => {
    fetchBooks();
  }, [fetchBooks]);

  // 查看书籍详情
  const viewBookDetail = useCallback((book) => {
    setCurrentBook(book);
    setShowDetail(true);
  }, []);

  // 返回首页
  const backToHome = useCallback(() => {
    setShowDetail(false);
    setCurrentBook(null);
  }, []);

  // 搜索书籍
  const filteredBooks = books.filter(book => {
    if (!searchKeyword) return true;
    const keyword = searchKeyword.toLowerCase();
    return (
      book.title.toLowerCase().includes(keyword) ||
      book.author.toLowerCase().includes(keyword)
    );
  });

  const value = {
    books,
    filteredBooks,
    loading,
    searchKeyword,
    currentBook,
    showDetail,
    setSearchKeyword,
    viewBookDetail,
    backToHome,
    fetchBooks
  };

  return <BooksContext.Provider value={value}>{children}</BooksContext.Provider>;
};