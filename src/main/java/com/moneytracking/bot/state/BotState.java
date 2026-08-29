package com.moneytracking.bot.state;

public enum BotState {
    IDLE,
    
    // Pemasukan
    WAITING_INCOME_AMOUNT,
    WAITING_INCOME_SOURCE,
    
    // Pengeluaran
    WAITING_EXPENSE_AMOUNT,
    WAITING_EXPENSE_CATEGORY,
    WAITING_EXPENSE_DESCRIPTION,

    // Hapus
    WAITING_DELETE_CONFIRMATION
}
