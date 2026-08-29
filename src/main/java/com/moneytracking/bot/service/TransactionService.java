package com.moneytracking.bot.service;

import com.moneytracking.bot.entity.Category;
import com.moneytracking.bot.entity.Transaction;
import com.moneytracking.bot.entity.TransactionType;
import com.moneytracking.bot.entity.User;
import com.moneytracking.bot.repository.CategoryRepository;
import com.moneytracking.bot.repository.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;

    public TransactionService(TransactionRepository transactionRepository, CategoryRepository categoryRepository) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public String saveTransaction(User user, TransactionType type, BigDecimal amount, String categoryName, String description) {
        Category category = categoryRepository.findByNameIgnoreCase(categoryName)
                .orElseGet(() -> {
                    Category newCat = new Category();
                    newCat.setName(categoryName);
                    newCat.setType(type);
                    return categoryRepository.save(newCat);
                });

        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setCategory(category);
        transaction.setAmount(amount);
        transaction.setType(type);
        transaction.setDescription(description);
        transaction.setTransactionDate(LocalDateTime.now());

        transactionRepository.save(transaction);
        
        String icon = type == TransactionType.INCOME ? "💰" : "💸";
        return String.format("✅ %s berhasil dicatat.\n\n%s %s\n%s %s\n📝 %s\n\nSaldo sekarang:\n%s",
                type == TransactionType.INCOME ? "Pemasukan" : "Pengeluaran",
                type == TransactionType.INCOME ? "🏷" : "🍜", category.getName(),
                icon, formatRupiah(amount),
                description != null ? description : "-",
                getSaldoValueOnly(user));
    }

    public String getSaldo(User user) {
        BigDecimal income = transactionRepository.getTotalIncomeByUser(user);
        BigDecimal expense = transactionRepository.getTotalExpenseByUser(user);
        BigDecimal saldo = income.subtract(expense);

        return String.format("💰 SALDO KAMU\n\n🟢 Pemasukan\n%s\n\n🔴 Pengeluaran\n%s\n\n━━━━━━━━━━━━━━\n💰 Saldo\n%s",
                formatRupiah(income), formatRupiah(expense), formatRupiah(saldo));
    }

    private String getSaldoValueOnly(User user) {
        BigDecimal income = transactionRepository.getTotalIncomeByUser(user);
        BigDecimal expense = transactionRepository.getTotalExpenseByUser(user);
        return formatRupiah(income.subtract(expense));
    }

    @Transactional(readOnly = true)
    public Page<Transaction> getRiwayatPage(User user, int pageNumber) {
        return transactionRepository.findByUserOrderByCreatedAtDesc(user, PageRequest.of(pageNumber, 10));
    }

    public String formatRiwayat(Page<Transaction> page, User user) {
        if (page.isEmpty()) {
            return "📭 Belum ada transaksi.\n\nSilakan tambahkan pemasukan atau pengeluaran terlebih dahulu.";
        }

        StringBuilder sb = new StringBuilder("📜 RIWAYAT KEUANGAN\n\n");
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd MMMM yyyy, HH:mm", new Locale("id", "ID"));

        for (Transaction t : page.getContent()) {
            String currDate = t.getCreatedAt().format(dtf);
            
            sb.append(currDate).append("\n\n");

            String typeStr = t.getType() == TransactionType.INCOME ? "🟢 Pemasukan" : "🔴 Pengeluaran";
            String sign = t.getType() == TransactionType.INCOME ? "+" : "-";
            String icon = t.getType() == TransactionType.INCOME ? "💼" : "🚗"; 
            
            if (t.getType() == TransactionType.EXPENSE) {
                if (t.getCategory().getName().equalsIgnoreCase("Makanan")) icon = "🍜";
                else if (t.getCategory().getName().equalsIgnoreCase("Transport")) icon = "🚗";
                else if (t.getCategory().getName().equalsIgnoreCase("Belanja")) icon = "🛍";
                else if (t.getCategory().getName().equalsIgnoreCase("Kebutuhan")) icon = "🏠";
                else if (t.getCategory().getName().equalsIgnoreCase("Kesehatan")) icon = "💊";
                else if (t.getCategory().getName().equalsIgnoreCase("Hiburan")) icon = "🎮";
                else icon = "📦";
            }

            String desc = (t.getDescription() != null && !t.getDescription().isEmpty()) ? "📝 " + t.getDescription() : "";
            
            if (!desc.isEmpty()) {
                sb.append(String.format("%s\n%s %s\n%s%s\n%s\n\n",
                        typeStr, icon, t.getCategory().getName(), sign, formatRupiah(t.getAmount()), desc));
            } else {
                sb.append(String.format("%s\n%s %s\n%s%s\n\n",
                        typeStr, icon, t.getCategory().getName(), sign, formatRupiah(t.getAmount())));
            }
        }
        
        sb.append("━━━━━━━━━━━━━━\n\n");
        
        sb.append("💰 Saldo saat ini:\n").append(getSaldoValueOnly(user)).append("\n\n");
        
        sb.append(String.format("Menampilkan %d transaksi.", page.getNumberOfElements()));
        if (page.getTotalPages() > 1) {
            sb.append(String.format(" (Halaman %d dari %d)", page.getNumber() + 1, page.getTotalPages()));
        }
        
        return sb.toString();
    }

    @Transactional
    public String deleteTransaction(User user, Long id) {
        return transactionRepository.findByIdAndUser(id, user)
                .map(t -> {
                    transactionRepository.delete(t);
                    return "✅ Transaksi berhasil dihapus.";
                })
                .orElse("❌ Transaksi tidak ditemukan atau Anda tidak memiliki akses.");
    }
    
    public Optional<Transaction> getTransaction(User user, Long id) {
        return transactionRepository.findByIdAndUser(id, user);
    }

    public String getLaporan(User user, LocalDateTime targetMonth) {
        LocalDateTime startOfMonth = targetMonth.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime endOfMonth = targetMonth.withDayOfMonth(targetMonth.toLocalDate().lengthOfMonth()).withHour(23).withMinute(59).withSecond(59);
        
        List<Transaction> transactions = transactionRepository.findByUserAndDateRange(user, startOfMonth, endOfMonth);

        String monthName = targetMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", new Locale("id", "ID")));
        
        if (transactions.isEmpty()) {
            return "📊 LAPORAN " + monthName.toUpperCase() + "\n\n📭 Tidak ada transaksi bulan ini.";
        }

        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        Map<String, BigDecimal> expenseByCategory = new HashMap<>();

        for (Transaction t : transactions) {
            if (t.getType() == TransactionType.INCOME) {
                income = income.add(t.getAmount());
            } else {
                expense = expense.add(t.getAmount());
                expenseByCategory.merge(t.getCategory().getName(), t.getAmount(), BigDecimal::add);
            }
        }
        
        BigDecimal saldo = income.subtract(expense);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("📊 LAPORAN %s\n\n", monthName.toUpperCase()));
        sb.append(String.format("🟢 Pemasukan\n%s\n\n", formatRupiah(income)));
        sb.append(String.format("🔴 Pengeluaran\n%s\n\n", formatRupiah(expense)));
        sb.append(String.format("💰 Saldo\n%s\n\n━━━━━━━━━━━━━━\n\n", formatRupiah(saldo)));
        
        if (!expenseByCategory.isEmpty()) {
            sb.append("Pengeluaran berdasarkan kategori:\n\n");
            expenseByCategory.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue())) // Sort descending by amount
                .forEach(e -> {
                    sb.append(String.format("▪️ %s\n%s\n\n", e.getKey(), formatRupiah(e.getValue())));
                });
        }

        return sb.toString();
    }

    public static String formatRupiah(BigDecimal amount) {
        NumberFormat format = NumberFormat.getCurrencyInstance(new Locale("id", "ID"));
        return format.format(amount).replace(",00", "");
    }
    
    public BigDecimal parseNominal(String input) {
        String cleanInput = input.replaceAll("[^0-9]", "");
        if (cleanInput.isEmpty()) return null;
        BigDecimal amount = new BigDecimal(cleanInput);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) return null;
        return amount;
    }
}
