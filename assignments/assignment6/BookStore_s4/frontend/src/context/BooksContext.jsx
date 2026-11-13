import { BooksContext } from './useBooks';
import React, { useState, useEffect, useCallback } from 'react';
import bookService from '../services/bookService';
import { useAuth } from './useAuth';

export const BooksProvider = ({ children }) => {
  const { currentUser } = useAuth();
  const [books, setBooks] = useState([]);
  const [loading, setLoading] = useState(false);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [currentBook, setCurrentBook] = useState(null);
  const [showDetail, setShowDetail] = useState(false);

  // 从后端 API 获取书籍数据
  const fetchBooks = useCallback(async (signal) => {
    if (!currentUser) return;
    try {
      setLoading(true);
      const data = await bookService.getBooks({ signal });
      setBooks(data);
    } catch (error) {
      if (error.name !== 'AbortError' && error.status !== 401) console.error('获取书籍数据失败:', error);
    } finally {
      if (!signal?.aborted) setLoading(false);
    }
  }, [currentUser]);

  // 初始化时获取书籍数据
  useEffect(() => {
    const controller = new AbortController();
    fetchBooks(controller.signal);
    return () => controller.abort();
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
