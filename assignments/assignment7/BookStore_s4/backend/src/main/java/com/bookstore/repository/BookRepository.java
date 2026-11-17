package com.bookstore.repository;

import com.bookstore.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 书籍数据访问层
 * 提供书籍相关的数据库操作
 */
@Repository
public interface BookRepository extends JpaRepository<Book, Long> {
    Optional<Book> findFirstByIsbnOrderByIdAsc(String isbn);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Book b where b.id = :id")
    Optional<Book> findByIdForUpdate(@Param("id") Long id);
    
    /**
     * 根据标题搜索书籍
     * @param title 标题关键词
     * @return 书籍列表
     */
    List<Book> findByTitleContaining(String title);
    
    /**
     * 根据作者搜索书籍
     * @param author 作者关键词
     * @return 书籍列表
     */
    List<Book> findByAuthorContaining(String author);
    
    /**
     * 根据标题或作者搜索书籍
     * @param title 标题关键词
     * @param author 作者关键词
     * @return 书籍列表
     */
    List<Book> findByTitleContainingOrAuthorContaining(String title, String author);
    
    /**
     * 查找有库存的书籍
     * @return 书籍列表
     */
    List<Book> findByStockGreaterThan(Integer stock);
    
    /**
     * 根据价格范围查找书籍
     * @param minPrice 最低价格
     * @param maxPrice 最高价格
     * @return 书籍列表
     */
    List<Book> findByPriceBetween(java.math.BigDecimal minPrice, java.math.BigDecimal maxPrice);
}
