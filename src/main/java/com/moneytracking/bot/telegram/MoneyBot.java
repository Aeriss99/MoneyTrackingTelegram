package com.moneytracking.bot.telegram;

import com.moneytracking.bot.entity.TransactionType;
import com.moneytracking.bot.entity.User;
import com.moneytracking.bot.entity.Transaction;
import com.moneytracking.bot.service.TransactionService;
import com.moneytracking.bot.service.UserService;
import com.moneytracking.bot.service.GeminiAiService;
import com.moneytracking.bot.service.LocalParserService;
import com.moneytracking.bot.state.BotState;
import com.moneytracking.bot.state.UserSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
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
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class MoneyBot extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(MoneyBot.class);

    private final String botUsername;
    private final UserService userService;
    private final TransactionService transactionService;
    private final com.moneytracking.bot.service.ExportService exportService;
    private final GeminiAiService geminiAiService;
    private final LocalParserService localParserService;

    // Simpan state per user
    private final Map<Long, UserSession> userSessions = new HashMap<>();

    public MoneyBot(@Value("${telegram.bot.token}") String botToken,
                    @Value("${telegram.bot.username}") String botUsername,
                    UserService userService,
                    TransactionService transactionService,
                    com.moneytracking.bot.service.ExportService exportService,
                    GeminiAiService geminiAiService,
                    LocalParserService localParserService) {
        super(botToken);
        this.botUsername = botUsername;
        this.userService = userService;
        this.transactionService = transactionService;
        this.exportService = exportService;
        this.geminiAiService = geminiAiService;
        this.localParserService = localParserService;
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
        
        log.info("📨 Menerima PESAN dari @{}: \"{}\"", username, text);

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
                    session.getSelectedTransactionsToDelete().clear(); // Reset selections
                    sendDeleteMenu(chatId, user, session, 0);
                    break;
                case "📄 Export PDF":
                    handleExportPdf(chatId, user);
                    break;
                case "⚙️ Pengaturan":
                    sendMessage(chatId, "Fitur pengaturan sedang dalam pengembangan.");
                    break;
                default:
                    handleAiMessage(chatId, user, text);
            }
        } else {
            // Handle State Logic (Sedang input data)
            handleStateInput(chatId, user, session, text);
        }
    }

    private void handleAiMessage(long chatId, User user, String text) {
        // ==========================================
        // 1. LOCAL FAST PARSER (No AI API call needed)
        // ==========================================
        
        // Cek sapaan atau oot
        String ootOrGreeting = localParserService.checkSimpleOOTOrGreeting(text);
        if (ootOrGreeting != null) {
            sendMessage(chatId, ootOrGreeting);
            return;
        }

        // Cek pertanyaan saldo
        if (localParserService.isSimpleBalanceCheck(text)) {
            String currentSaldo = transactionService.getSaldo(user);
            sendMessage(chatId, "💰 Saldo kamu sekarang " + currentSaldo + ".\nMasih aman kan? 😂");
            return;
        }

        // Cek transaksi input kilat
        LocalParserService.ParsedTransaction parsed = localParserService.parseSimpleTransaction(text);
        if (parsed != null) {
            try {
                BigDecimal amount = BigDecimal.valueOf(parsed.amount);
                transactionService.saveTransaction(user, parsed.type, amount, parsed.category, parsed.description);
                
                String reply = localParserService.generateFunnyResponse(parsed.type, parsed.amount, parsed.description);
                sendMessage(chatId, reply);
                return; // Berhenti di sini, tidak perlu panggil Gemini
            } catch (Exception e) {
                log.error("Gagal save dari local parser", e);
                // Jika gagal save, lanjutkan ke AI fallback
            }
        }

        // ==========================================
        // 2. GEMINI AI API (Fallback for complex inputs/chat)
        // ==========================================
        String[] waitingMessages = {
            "🧠 Bentar, otakku lagi ngitung duit kamu 😂",
            "🔍 Lagi nyari siapa yang bikin saldo kamu kabur... 😂",
            "🧮 Sebentar, angka-angkanya lagi dirapatkan 😭",
            "💸 Lagi ngecek ke mana duit kamu pergi 😂",
            "🤔 Bentar, dompetmu lagi aku interogasi 😭",
            "⏳ Sabar ya, lagi bongkar-bongkar catatan kasbon kamu 😂"
        };
        String randomWaitMsg = waitingMessages[new java.util.Random().nextInt(waitingMessages.length)];
        sendMessage(chatId, randomWaitMsg);

        String currentSaldo = transactionService.getSaldo(user);
        GeminiAiService.AiResponse aiResponse = geminiAiService.analyzeText(text, currentSaldo);
        
        if ("record".equals(aiResponse.getIntent())) {
            try {
                TransactionType type = TransactionType.valueOf(aiResponse.getType().toUpperCase());
                BigDecimal amount = BigDecimal.valueOf(aiResponse.getAmount());
                transactionService.saveTransaction(user, type, amount, aiResponse.getCategory(), aiResponse.getDescription());
                
                // Gunakan respon jenaka seperti di local parser untuk konsistensi pengalaman
                String reply = localParserService.generateFunnyResponse(type, aiResponse.getAmount(), aiResponse.getDescription());
                sendMessage(chatId, reply);
            } catch (Exception e) {
                log.error("AI returned invalid data format", e);
                sendMessage(chatId, "😅 AI agak bingung sama angkanya. Coba ketik lebih jelas ya, misalnya: 'Makan siang 25k'.");
            }
        } else if ("out_of_context".equals(aiResponse.getIntent())) {
            sendMessage(chatId, aiResponse.getMessage());
        } else {
            // It's just a regular chat or question
            sendMessage(chatId, aiResponse.getMessage() != null ? aiResponse.getMessage() : "Maaf, AI tidak memberikan respons yang dapat dimengerti.");
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
        String username = callbackQuery.getFrom().getUserName();
        
        log.info("🔘 Menerima KLIK TOMBOL dari @{}: \"{}\"", username, data);
        
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
            editDeleteMenu(chatId, messageId, user, session, page);
        }
        
        // Handle Delete Selection
        else if (data.startsWith("del_select_")) {
            Long trxId = Long.parseLong(data.split("_")[2]);
            int currentPage = Integer.parseInt(data.split("_")[3]);
            
            // Toggle selection
            if (session.getSelectedTransactionsToDelete().contains(trxId)) {
                session.getSelectedTransactionsToDelete().remove(trxId);
            } else {
                session.getSelectedTransactionsToDelete().add(trxId);
            }
            
            // Refresh menu
            editDeleteMenu(chatId, messageId, user, session, currentPage);
        }
        
        // Handle Execute Multi Delete
        else if (data.startsWith("del_exec_")) {
            if (session.getSelectedTransactionsToDelete().isEmpty()) {
                EditMessageText edit = new EditMessageText();
                edit.setChatId(String.valueOf(chatId));
                edit.setMessageId(messageId);
                edit.setText("Batal, tidak ada transaksi yang dipilih.");
                try { execute(edit); } catch (TelegramApiException e) { e.printStackTrace(); }
            } else {
                String result = transactionService.deleteMultipleTransactions(user, session.getSelectedTransactionsToDelete());
                session.clear();
                
                EditMessageText edit = new EditMessageText();
                edit.setChatId(String.valueOf(chatId));
                edit.setMessageId(messageId);
                edit.setText(result);
                try { execute(edit); } catch (TelegramApiException e) { e.printStackTrace(); }
            }
        }
        
        // Handle Cancel Delete
        else if (data.equals("del_cancel")) {
            session.clear();
            EditMessageText edit = new EditMessageText();
            edit.setChatId(String.valueOf(chatId));
            edit.setMessageId(messageId);
            edit.setText("Batal menghapus transaksi.");
            try { execute(edit); } catch (TelegramApiException e) { e.printStackTrace(); }
        }

    }

    // --- UI METHODS ---

    private void handleExportPdf(long chatId, User user) {
        sendMessage(chatId, "⏳ Sedang membuat laporan PDF Anda, mohon tunggu sebentar...");
        
        File pdfFile = exportService.generateTransactionPdf(user);
        
        if (pdfFile != null && pdfFile.exists()) {
            SendDocument sendDocument = new SendDocument();
            sendDocument.setChatId(String.valueOf(chatId));
            sendDocument.setDocument(new InputFile(pdfFile));
            sendDocument.setCaption("📄 Berikut adalah laporan riwayat keuangan Anda.");
            
            try {
                execute(sendDocument);
            } catch (TelegramApiException e) {
                log.error("Gagal mengirim PDF", e);
                sendMessage(chatId, "❌ Gagal mengirim file PDF.");
            } finally {
                // Hapus file setelah dikirim agar tidak memenuhi disk server
                pdfFile.delete();
            }
        } else {
            sendMessage(chatId, "❌ Terjadi kesalahan saat membuat laporan PDF.");
        }
    }

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
        row4.add("📄 Export PDF");
        
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

    private void sendDeleteMenu(long chatId, User user, UserSession session, int page) {
        Page<Transaction> trxPage = transactionService.getRiwayatPage(user, page);
        
        if (trxPage.isEmpty()) {
            sendMessage(chatId, "📭 Belum ada transaksi untuk dihapus.");
            return;
        }

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("🗑 Pilih transaksi yang ingin dihapus (bisa lebih dari 1):");
        
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        for (Transaction t : trxPage.getContent()) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton btn = new InlineKeyboardButton();
            String icon = t.getType() == TransactionType.INCOME ? "🟢" : "🔴";
            String sign = t.getType() == TransactionType.INCOME ? "+" : "-";
            
            // Checkmark logic
            String check = session.getSelectedTransactionsToDelete().contains(t.getId()) ? "✅ " : "⬛ ";
            
            btn.setText(String.format("%s%s %s %s%s", check, icon, t.getCategory().getName(), sign, TransactionService.formatRupiah(t.getAmount())));
            btn.setCallbackData("del_select_" + t.getId() + "_" + page);
            row.add(btn);
            rows.add(row);
        }
        
        // Navigation Row
        List<InlineKeyboardButton> navRow = new ArrayList<>();
        if (page > 0) {
            InlineKeyboardButton pBtn = new InlineKeyboardButton();
            pBtn.setText("◀ Prev"); pBtn.setCallbackData("del_page_" + (page - 1));
            navRow.add(pBtn);
        }
        if (page < trxPage.getTotalPages() - 1) {
            InlineKeyboardButton nBtn = new InlineKeyboardButton();
            nBtn.setText("Next ▶"); nBtn.setCallbackData("del_page_" + (page + 1));
            navRow.add(nBtn);
        }
        if(!navRow.isEmpty()) rows.add(navRow);
        
        // Action Buttons Row
        List<InlineKeyboardButton> actionRow = new ArrayList<>();
        InlineKeyboardButton execBtn = new InlineKeyboardButton();
        int selectedCount = session.getSelectedTransactionsToDelete().size();
        execBtn.setText("🗑 Hapus Terpilih (" + selectedCount + ")");
        execBtn.setCallbackData("del_exec_");
        actionRow.add(execBtn);
        
        InlineKeyboardButton cancelBtn = new InlineKeyboardButton();
        cancelBtn.setText("❌ Batal");
        cancelBtn.setCallbackData("del_cancel");
        actionRow.add(cancelBtn);
        
        rows.add(actionRow);

        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);
        try { execute(message); } catch (TelegramApiException e) { e.printStackTrace(); }
    }
    
    private void editDeleteMenu(long chatId, int messageId, User user, UserSession session, int page) {
        Page<Transaction> trxPage = transactionService.getRiwayatPage(user, page);
        if (trxPage.isEmpty()) return;
        
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        for (Transaction t : trxPage.getContent()) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton btn = new InlineKeyboardButton();
            String icon = t.getType() == TransactionType.INCOME ? "🟢" : "🔴";
            String sign = t.getType() == TransactionType.INCOME ? "+" : "-";
            
            // Checkmark logic
            String check = session.getSelectedTransactionsToDelete().contains(t.getId()) ? "✅ " : "⬛ ";
            
            btn.setText(String.format("%s%s %s %s%s", check, icon, t.getCategory().getName(), sign, TransactionService.formatRupiah(t.getAmount())));
            btn.setCallbackData("del_select_" + t.getId() + "_" + page);
            row.add(btn);
            rows.add(row);
        }
        
        // Navigation Row
        List<InlineKeyboardButton> navRow = new ArrayList<>();
        if (page > 0) {
            InlineKeyboardButton pBtn = new InlineKeyboardButton();
            pBtn.setText("◀ Prev"); pBtn.setCallbackData("del_page_" + (page - 1));
            navRow.add(pBtn);
        }
        if (page < trxPage.getTotalPages() - 1) {
            InlineKeyboardButton nBtn = new InlineKeyboardButton();
            nBtn.setText("Next ▶"); nBtn.setCallbackData("del_page_" + (page + 1));
            navRow.add(nBtn);
        }
        if(!navRow.isEmpty()) rows.add(navRow);
        
        // Action Buttons Row
        List<InlineKeyboardButton> actionRow = new ArrayList<>();
        InlineKeyboardButton execBtn = new InlineKeyboardButton();
        int selectedCount = session.getSelectedTransactionsToDelete().size();
        execBtn.setText("🗑 Hapus Terpilih (" + selectedCount + ")");
        execBtn.setCallbackData("del_exec_");
        actionRow.add(execBtn);
        
        InlineKeyboardButton cancelBtn = new InlineKeyboardButton();
        cancelBtn.setText("❌ Batal");
        cancelBtn.setCallbackData("del_cancel");
        actionRow.add(cancelBtn);
        
        rows.add(actionRow);
        
        markup.setKeyboard(rows);
        
        EditMessageText edit = new EditMessageText();
        edit.setChatId(String.valueOf(chatId));
        edit.setMessageId(messageId);
        edit.setText("🗑 Pilih transaksi yang ingin dihapus (bisa lebih dari 1):");
        edit.setReplyMarkup(markup);
        try { execute(edit); } catch (TelegramApiException e) { e.printStackTrace(); }
    }
}
