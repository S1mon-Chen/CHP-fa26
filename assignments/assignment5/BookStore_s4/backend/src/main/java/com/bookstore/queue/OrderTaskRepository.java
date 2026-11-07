package com.bookstore.queue;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface OrderTaskRepository extends JpaRepository<OrderTask, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from OrderTask t where t.id = :id")
    Optional<OrderTask> lockById(@Param("id") String id);
}
