import api from './api';

const bookService = {
  // 获取所有书籍
  getBooks: async () => {
    return await api.get('/books');
  },

  // 获取书籍详情
  getBookById: async (id) => {
    return await api.get(`/books/${id}`);
  },

  // 搜索书籍
  searchBooks: async (keyword) => {
    return await api.get(`/books/search?keyword=${keyword}`);
  },
};

export default bookService;