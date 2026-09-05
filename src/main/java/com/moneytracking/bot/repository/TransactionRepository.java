package com.moneytracking.bot.repository;

import com.moneytracking.bot.entity.Transaction;
import com.moneytracking.bot.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    
    // Untuk pagination riwayat
    @EntityGraph(attributePaths = {"category"})
    Page<Transaction> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);
    
    Optional<Transaction> findByIdAndUser(Long id, User user);
    
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.user = :user AND t.type = 'INCOME'")
    BigDecimal getTotalIncomeByUser(@Param("user") User user);
    
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.user = :user AND t.type = 'EXPENSE'")
    BigDecimal getTotalExpenseByUser(@Param("user") User user);
    
    @EntityGraph(attributePaths = {"category"})
    @Query("SELECT t FROM Transaction t WHERE t.user = :user AND t.transactionDate >= :startDate AND t.transactionDate <= :endDate ORDER BY t.transactionDate DESC")
    List<Transaction> findByUserAndDateRange(
            @Param("user") User user, 
            @Param("startDate") LocalDateTime startDate, 
            @Param("endDate") LocalDateTime endDate);

    // Untuk export PDF seluruh data
    @EntityGraph(attributePaths = {"category"})
    List<Transaction> findByUserOrderByTransactionDateDesc(User user);
    
    // Untuk export PDF urutan lama ke baru
    @EntityGraph(attributePaths = {"category"})
    List<Transaction> findByUserOrderByTransactionDateAsc(User user);
}
