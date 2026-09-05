package com.moneytracking.bot.state;

import com.moneytracking.bot.entity.TransactionType;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

public class UserSession {
    private BotState state;
    
    // Data sementara untuk transaksi
    private TransactionType tempType;
    private BigDecimal tempAmount;
    private String tempCategory;
    
    // Data sementara untuk delete
    private Long tempDeleteId;
    
    // Data untuk multi-delete
    private Set<Long> selectedTransactionsToDelete;

    public UserSession() {
        this.state = BotState.IDLE;
        this.selectedTransactionsToDelete = new HashSet<>();
    }

    public BotState getState() { return state; }
    public void setState(BotState state) { this.state = state; }

    public TransactionType getTempType() { return tempType; }
    public void setTempType(TransactionType tempType) { this.tempType = tempType; }

    public BigDecimal getTempAmount() { return tempAmount; }
    public void setTempAmount(BigDecimal tempAmount) { this.tempAmount = tempAmount; }

    public String getTempCategory() { return tempCategory; }
    public void setTempCategory(String tempCategory) { this.tempCategory = tempCategory; }

    public Long getTempDeleteId() { return tempDeleteId; }
    public void setTempDeleteId(Long tempDeleteId) { this.tempDeleteId = tempDeleteId; }
    
    public Set<Long> getSelectedTransactionsToDelete() { return selectedTransactionsToDelete; }
    
    public void clear() {
        this.state = BotState.IDLE;
        this.tempType = null;
        this.tempAmount = null;
        this.tempCategory = null;
        this.tempDeleteId = null;
        this.selectedTransactionsToDelete.clear();
    }
}
