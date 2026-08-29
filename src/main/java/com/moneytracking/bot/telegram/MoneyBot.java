package com.moneytracking.bot.telegram;

import com.moneytracking.bot.entity.TransactionType;
import com.moneytracking.bot.entity.User;
import com.moneytracking.bot.entity.Transaction;
import com.moneytracking.bot.service.TransactionService;
import com.moneytracking.bot.service.UserService;
import com.moneytracking.bot.state.BotState;
import com.moneytracking.bot.state.UserSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class MoneyBot extends TelegramLongPollingBot {

    private final String botUsername;
    private final UserService userService;
    private final TransactionService transactionService;

    // Simpan state per user
    private final Map<Long, UserSession> userSessions = new HashMap<>();

    public MoneyBot(@Value("${telegram.bot.token}") String botToken,
                    @Value("${telegram.bot.username}") String botUsername,
                    UserService userService,
                    TransactionService transactionService) {
        super(botToken);
        this.botUsername = botUsername;
        this.userService = userService;
        this.transactionService = transactionService;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    private UserSession getSession(Long userId) {
        return userSessions.computeIfAbsent(userId, k -> new UserSession());
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            handleIncomingMessage(update);
        } else if (update.hasCallbackQuery()) {
            handleCallbackQuery(update.getCallbackQuery());
        }
    }

    private void handleIncomingMessage(Update update) {
        String text = update.getMessage().getText();
        long chatId = update.getMessage().getChatId();
        String username = update.getMessage().getFrom().getUserName();

        User user = userService.getOrCreateUser(chatId, username);
        UserSession session = getSession(chatId);

        // Global commands
        if (text.equals("/start") || text.equals("🏠 Menu Utama") || text.equals("⬅️ Kembali")) {
            session.clear();
            sendMainMenu(chatId, "🤖 MONEY TRACKER\n\nSelamat datang 👋\nApa yang ingin kamu lakukan?");
            return;
        } else if (text.equals("/help")) {
            sendMessage(chatId, "Cukup gunakan menu tombol di bawah untuk mencatat keuangan Anda!");
            return;
        }

        // Handle Main Menu Buttons
        if (session.getState() == BotState.IDLE) {
            switch (text) {
                case "➕ Tambah Pemasukan":
                    session.setState(BotState.WAITING_INCOME_AMOUNT);
                    session.setTempType(TransactionType.INCOME);
                    sendCancelableMessage(chatId, "💰 Masukkan nominal pemasukan.");
                    break;
                case "➖ Tambah Pengeluaran":
                    session.setState(BotState.WAITING_EXPENSE_AMOUNT);
                    session.setTempType(TransactionType.EXPENSE);
                    sendCancelableMessage(chatId, "💸 Masukkan nominal pengeluaran.");
                    break;
                case "💰 Saldo":
                    sendMessage(chatId, transactionService.getSaldo(user));
                    break;
                case "📊 Laporan":
                    sendLaporanMessage(chatId, user, LocalDateTime.now());
                    break;
                case "📜 Riwayat":
                    sendRiwayatMessage(chatId, user, 0);
                    break;
                case "🗑 Hapus Transaksi":
                    sendDeleteMenu(chatId, user, 0);
                    break;
                case "⚙️ Pengaturan":
                    sendMessage(chatId, "Fitur pengaturan sedang dalam pengembangan.");
                    break;
                default:
                    sendMessage(chatId, "Tolong pilih opsi dari menu di bawah 👇");
            }
        } else {
            // Handle State Logic (Sedang input data)
            handleStateInput(chatId, user, session, text);
        }
    }

    private void handleStateInput(long chatId, User user, UserSession session, String text) {
        switch (session.getState()) {
            case WAITING_INCOME_AMOUNT:
                BigDecimal inAmount = transactionService.parseNominal(text);
                if (inAmount == null) {
                    sendMessage(chatId, "❌ Nominal tidak valid.\nContoh: 25000 atau Rp25.000");
                } else {
                    session.setTempAmount(inAmount);
                    session.setState(BotState.WAITING_INCOME_SOURCE);
                    sendMessage(chatId, "🏷 Masukkan sumber pemasukan. (Misal: Gaji, Bonus, dll)");
                }
                break;
                
            case WAITING_INCOME_SOURCE:
                String inCategory = text.trim();
                String inResult = transactionService.saveTransaction(user, TransactionType.INCOME, session.getTempAmount(), inCategory, null);
                session.clear();
                sendMainMenu(chatId, inResult);
                break;
                
            case WAITING_EXPENSE_AMOUNT:
                BigDecimal exAmount = transactionService.parseNominal(text);
                if (exAmount == null) {
                    sendMessage(chatId, "❌ Nominal tidak valid.\nContoh: 25000 atau Rp25.000");
                } else {
                    session.setTempAmount(exAmount);
                    session.setState(BotState.WAITING_EXPENSE_CATEGORY);
                    sendExpenseCategoryMenu(chatId);
                }
                break;
                
            case WAITING_EXPENSE_CATEGORY:
                session.setTempCategory(text.trim());
                session.setState(BotState.WAITING_EXPENSE_DESCRIPTION);
                sendMessage(chatId, "📝 Tambahkan deskripsi (opsional).\nAtau ketik '-' untuk lewati.");
                break;
                
            case WAITING_EXPENSE_DESCRIPTION:
                String desc = text.trim().equals("-") ? null : text.trim();
                String exResult = transactionService.saveTransaction(user, TransactionType.EXPENSE, session.getTempAmount(), session.getTempCategory(), desc);
                session.clear();
                sendMainMenu(chatId, exResult);
                break;
        }
    }

    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        long chatId = callbackQuery.getMessage().getChatId();
        int messageId = callbackQuery.getMessage().getMessageId();
        
        User user = userService.getOrCreateUser(chatId, callbackQuery.getFrom().getUserName());
        UserSession session = getSession(chatId);
        
        // Handle Pagination Riwayat
        if (data.startsWith("riwayat_page_")) {
            int page = Integer.parseInt(data.split("_")[2]);
            editRiwayatMessage(chatId, messageId, user, page);
        }
        
        // Handle Pagination Laporan
        else if (data.startsWith("laporan_month_")) {
            int monthOffset = Integer.parseInt(data.split("_")[2]);
            LocalDateTime targetDate = LocalDateTime.now().plusMonths(monthOffset);
            editLaporanMessage(chatId, messageId, user, targetDate, monthOffset);
        }
        
        // Handle Delete Pagination
        else if (data.startsWith("del_page_")) {
            int page = Integer.parseInt(data.split("_")[2]);
            editDeleteMenu(chatId, messageId, user, page);
        }
        
        // Handle Delete Selection
        else if (data.startsWith("del_select_")) {
            Long trxId = Long.parseLong(data.split("_")[2]);
            Optional<Transaction> trxOpt = transactionService.getTransaction(user, trxId);
            
            if (trxOpt.isPresent()) {
                Transaction trx = trxOpt.get();
                session.setState(BotState.WAITING_DELETE_CONFIRMATION);
                session.setTempDeleteId(trxId);
                
                String text = String.format("Apakah kamu yakin ingin menghapus transaksi ini?\n\n%s\n%s", 
                        trx.getCategory().getName(), TransactionService.formatRupiah(trx.getAmount()));
                        
                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                
                List<InlineKeyboardButton> row = new ArrayList<>();
                InlineKeyboardButton yesBtn = new InlineKeyboardButton();
                yesBtn.setText("✅ Ya, Hapus");
                yesBtn.setCallbackData("del_confirm_yes");
                row.add(yesBtn);
                
                InlineKeyboardButton noBtn = new InlineKeyboardButton();
                noBtn.setText("❌ Batal");
                noBtn.setCallbackData("del_confirm_no");
                row.add(noBtn);
                
                rows.add(row);
                markup.setKeyboard(rows);
                
                EditMessageText edit = new EditMessageText();
                edit.setChatId(String.valueOf(chatId));
                edit.setMessageId(messageId);
                edit.setText(text);
                edit.setReplyMarkup(markup);
                
                try {
                    execute(edit);
                } catch (TelegramApiException e) { e.printStackTrace(); }
            } else {
                sendMainMenu(chatId, "❌ Transaksi tidak ditemukan.");
            }
        }
        
        // Handle Delete Confirmation
        else if (data.equals("del_confirm_yes")) {
            if (session.getState() == BotState.WAITING_DELETE_CONFIRMATION && session.getTempDeleteId() != null) {
                String result = transactionService.deleteTransaction(user, session.getTempDeleteId());
                session.clear();
                
                EditMessageText edit = new EditMessageText();
                edit.setChatId(String.valueOf(chatId));
                edit.setMessageId(messageId);
                edit.setText(result);
                try { execute(edit); } catch (TelegramApiException e) { e.printStackTrace(); }
            }
        } else if (data.equals("del_confirm_no")) {
            session.clear();
            EditMessageText edit = new EditMessageText();
            edit.setChatId(String.valueOf(chatId));
            edit.setMessageId(messageId);
            edit.setText("Batal menghapus transaksi.");
            try { execute(edit); } catch (TelegramApiException e) { e.printStackTrace(); }
        }
    }

    // --- UI METHODS ---

    private void sendMainMenu(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        List<KeyboardRow> keyboard = new ArrayList<>();
        
        KeyboardRow row1 = new KeyboardRow();
        row1.add("➕ Tambah Pemasukan");
        row1.add("➖ Tambah Pengeluaran");
        
        KeyboardRow row2 = new KeyboardRow();
        row2.add("💰 Saldo");
        row2.add("📊 Laporan");
        
        KeyboardRow row3 = new KeyboardRow();
        row3.add("📜 Riwayat");
        row3.add("🗑 Hapus Transaksi");
        
        KeyboardRow row4 = new KeyboardRow();
        row4.add("⚙️ Pengaturan");
        
        keyboard.add(row1);
        keyboard.add(row2);
        keyboard.add(row3);
        keyboard.add(row4);
        
        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);

        try { execute(message); } catch (TelegramApiException e) { e.printStackTrace(); }
    }
    
    private void sendCancelableMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row1 = new KeyboardRow();
        row1.add("⬅️ Kembali");
        keyboard.add(row1);
        
        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);
        try { execute(message); } catch (TelegramApiException e) { e.printStackTrace(); }
    }

    private void sendExpenseCategoryMenu(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Pilih kategori:");
        
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        List<KeyboardRow> keyboard = new ArrayList<>();
        
        KeyboardRow r1 = new KeyboardRow(); r1.add("🍜 Makanan"); r1.add("🚗 Transport");
        KeyboardRow r2 = new KeyboardRow(); r2.add("🛍 Belanja"); r2.add("🏠 Kebutuhan");
        KeyboardRow r3 = new KeyboardRow(); r3.add("💊 Kesehatan"); r3.add("🎮 Hiburan");
        KeyboardRow r4 = new KeyboardRow(); r4.add("📦 Lainnya"); r4.add("⬅️ Kembali");
        
        keyboard.add(r1); keyboard.add(r2); keyboard.add(r3); keyboard.add(r4);
        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);
        
        try { execute(message); } catch (TelegramApiException e) { e.printStackTrace(); }
    }

    private void sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        try { execute(message); } catch (TelegramApiException e) { e.printStackTrace(); }
    }

    private void sendRiwayatMessage(long chatId, User user, int page) {
        try {
            Page<Transaction> trxPage = transactionService.getRiwayatPage(user, page);
            String text = transactionService.formatRiwayat(trxPage, user);
            
            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));
            
            // Telegram memiliki limit untuk format Markdown/HTML, jadi gunakan plain text namun rapi
            // Kita parse text agar aman (opsional jika pakai setParseMode)
            message.setText(text);
            
            InlineKeyboardMarkup paginationMarkup = getRiwayatPagination(page, trxPage.getTotalPages());
            if (paginationMarkup != null) {
                message.setReplyMarkup(paginationMarkup);
            }
            
            execute(message);
        } catch (Exception e) {
            System.err.println("Error saat mengambil/mengirim riwayat: " + e.getMessage());
            e.printStackTrace(); 
            sendMessage(chatId, "❌ Gagal mengambil riwayat transaksi.\nSilakan coba lagi.\nError detail: " + e.getMessage());
        }
    }
    
    private void editRiwayatMessage(long chatId, int messageId, User user, int page) {
        try {
            Page<Transaction> trxPage = transactionService.getRiwayatPage(user, page);
            String text = transactionService.formatRiwayat(trxPage, user);
            
            EditMessageText edit = new EditMessageText();
            edit.setChatId(String.valueOf(chatId));
            edit.setMessageId(messageId);
            edit.setText(text);
            
            InlineKeyboardMarkup paginationMarkup = getRiwayatPagination(page, trxPage.getTotalPages());
            if (paginationMarkup != null) {
                edit.setReplyMarkup(paginationMarkup);
            }
            
            execute(edit);
        } catch (Exception e) {
            e.printStackTrace();
            sendMessage(chatId, "❌ Gagal mengupdate riwayat transaksi.");
        }
    }

    private InlineKeyboardMarkup getRiwayatPagination(int currentPage, int totalPages) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        
        if (currentPage > 0) {
            InlineKeyboardButton prev = new InlineKeyboardButton();
            prev.setText("◀ Sebelumnya");
            prev.setCallbackData("riwayat_page_" + (currentPage - 1));
            row.add(prev);
        }
        
        if (currentPage < totalPages - 1) {
            InlineKeyboardButton next = new InlineKeyboardButton();
            next.setText("Berikutnya ▶");
            next.setCallbackData("riwayat_page_" + (currentPage + 1));
            row.add(next);
        }
        
        if (!row.isEmpty()) {
            rows.add(row);
            markup.setKeyboard(rows);
            return markup;
        }
        return null;
    }
    
    private void sendLaporanMessage(long chatId, User user, LocalDateTime targetMonth) {
        String text = transactionService.getLaporan(user, targetMonth);
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.setReplyMarkup(getLaporanPagination(0)); // 0 = current month
        try { execute(message); } catch (TelegramApiException e) { e.printStackTrace(); }
    }
    
    private void editLaporanMessage(long chatId, int messageId, User user, LocalDateTime targetMonth, int monthOffset) {
        String text = transactionService.getLaporan(user, targetMonth);
        EditMessageText edit = new EditMessageText();
        edit.setChatId(String.valueOf(chatId));
        edit.setMessageId(messageId);
        edit.setText(text);
        edit.setReplyMarkup(getLaporanPagination(monthOffset));
        try { execute(edit); } catch (TelegramApiException e) { e.printStackTrace(); }
    }
    
    private InlineKeyboardMarkup getLaporanPagination(int monthOffset) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        
        InlineKeyboardButton prev = new InlineKeyboardButton();
        prev.setText("◀ Bulan Sebelumnya");
        prev.setCallbackData("laporan_month_" + (monthOffset - 1));
        row.add(prev);
        
        if (monthOffset < 0) { // Hanya bisa next jika lihat ke belakang (bukan bulan depan)
            InlineKeyboardButton next = new InlineKeyboardButton();
            next.setText("Bulan Berikutnya ▶");
            next.setCallbackData("laporan_month_" + (monthOffset + 1));
            row.add(next);
        }
        
        rows.add(row);
        markup.setKeyboard(rows);
        return markup;
    }

    private void sendDeleteMenu(long chatId, User user, int page) {
        Page<Transaction> trxPage = transactionService.getRiwayatPage(user, page);
        
        if (trxPage.isEmpty()) {
            sendMessage(chatId, "📭 Belum ada transaksi untuk dihapus.");
            return;
        }

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("🗑 Pilih transaksi yang ingin dihapus:");
        
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        for (Transaction t : trxPage.getContent()) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton btn = new InlineKeyboardButton();
            String icon = t.getType() == TransactionType.INCOME ? "🟢" : "🔴";
            String sign = t.getType() == TransactionType.INCOME ? "+" : "-";
            btn.setText(String.format("%s %s %s%s", icon, t.getCategory().getName(), sign, TransactionService.formatRupiah(t.getAmount())));
            btn.setCallbackData("del_select_" + t.getId());
            row.add(btn);
            rows.add(row);
        }
        
        List<InlineKeyboardButton> navRow = new ArrayList<>();
        if (page > 0) {
            InlineKeyboardButton pBtn = new InlineKeyboardButton();
            pBtn.setText("◀"); pBtn.setCallbackData("del_page_" + (page - 1));
            navRow.add(pBtn);
        }
        if (page < trxPage.getTotalPages() - 1) {
            InlineKeyboardButton nBtn = new InlineKeyboardButton();
            nBtn.setText("▶"); nBtn.setCallbackData("del_page_" + (page + 1));
            navRow.add(nBtn);
        }
        if(!navRow.isEmpty()) rows.add(navRow);
        
        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);
        try { execute(message); } catch (TelegramApiException e) { e.printStackTrace(); }
    }
    
    private void editDeleteMenu(long chatId, int messageId, User user, int page) {
        Page<Transaction> trxPage = transactionService.getRiwayatPage(user, page);
        if (trxPage.isEmpty()) return;
        
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        for (Transaction t : trxPage.getContent()) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton btn = new InlineKeyboardButton();
            String icon = t.getType() == TransactionType.INCOME ? "🟢" : "🔴";
            String sign = t.getType() == TransactionType.INCOME ? "+" : "-";
            btn.setText(String.format("%s %s %s%s", icon, t.getCategory().getName(), sign, TransactionService.formatRupiah(t.getAmount())));
            btn.setCallbackData("del_select_" + t.getId());
            row.add(btn);
            rows.add(row);
        }
        
        List<InlineKeyboardButton> navRow = new ArrayList<>();
        if (page > 0) {
            InlineKeyboardButton pBtn = new InlineKeyboardButton();
            pBtn.setText("◀"); pBtn.setCallbackData("del_page_" + (page - 1));
            navRow.add(pBtn);
        }
        if (page < trxPage.getTotalPages() - 1) {
            InlineKeyboardButton nBtn = new InlineKeyboardButton();
            nBtn.setText("▶"); nBtn.setCallbackData("del_page_" + (page + 1));
            navRow.add(nBtn);
        }
        if(!navRow.isEmpty()) rows.add(navRow);
        
        markup.setKeyboard(rows);
        
        EditMessageText edit = new EditMessageText();
        edit.setChatId(String.valueOf(chatId));
        edit.setMessageId(messageId);
        edit.setText("🗑 Pilih transaksi yang ingin dihapus:");
        edit.setReplyMarkup(markup);
        try { execute(edit); } catch (TelegramApiException e) { e.printStackTrace(); }
    }
}
