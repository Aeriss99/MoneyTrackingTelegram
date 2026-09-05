package com.moneytracking.bot.service;

import com.moneytracking.bot.entity.TransactionType;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LocalParserService {

    private final Random random = new Random();

    public static class ParsedTransaction {
        public TransactionType type;
        public double amount;
        public String category;
        public String description;

        public ParsedTransaction(TransactionType type, double amount, String category, String description) {
            this.type = type;
            this.amount = amount;
            this.category = category;
            this.description = description;
        }
    }

    /**
     * Mengecek apakah pesan berupa pertanyaan sapaan/OOT sederhana
     */
    public String checkSimpleOOTOrGreeting(String text) {
        String lower = text.toLowerCase().trim();
        
        // Sapaan
        if (lower.matches("^(halo|hi|hai|pagi|siang|sore|malam|bot|oy|oi|test|tes).*")) {
            if (lower.length() < 15) {
                String[] greetings = {
                        "Halo bro! Mau nyatet pengeluaran apa hari ini? 👀",
                        "Oi! Dompet aman kan hari ini? 😂",
                        "Hai hai! Jangan bilang mau nyatet pengeluaran lagi 😭",
                        "Halo! Silakan ketik pengeluaran atau pemasukan kamu hari ini 💸"
                };
                return greetings[random.nextInt(greetings.length)];
            }
        }
        
        // OOT sederhana yang langsung ditolak tanpa AI
        if (lower.matches(".*(siapa presiden|cuaca|anime|resep|bikin puisi|coding|game|film|main|lagu).*")) {
            String[] oots = {
                    "😂 Kalau bahas itu aku bisa ngelamun. Aku di sini ngurusin duit kamu, kalau mau bahas yang lain chat owner-ku 👉 @areUlookingFor",
                    "😅 Itu bukan wilayahku bro. Aku lebih jago ngurus saldo daripada ngurusin kehidupan orang 😂 👉 @areUlookingFor",
                    "😂 Aku fokus ngurusin duit kamu dulu. Kalau mau bahas yang lain, owner-ku siap ditanya 👉 @areUlookingFor"
            };
            return oots[random.nextInt(oots.length)];
        }
        
        return null;
    }

    /**
     * Mengecek apakah ini adalah pertanyaan tentang cek saldo sederhana
     */
    public boolean isSimpleBalanceCheck(String text) {
        String lower = text.toLowerCase().trim();
        // Cek pertanyaan tentang saldo/duit
        return lower.matches("^(berapa saldo|sisa saldo|saldo|saldoku|uangku|duitku|duit saya|saldo saya|cek saldo).*");
    }

    /**
     * Mencoba mem-parsing input sederhana seperti "makan 25k" atau "gaji 5 juta".
     * Mengembalikan null jika kalimat terlalu kompleks atau tidak menemukan angka.
     */
    public ParsedTransaction parseSimpleTransaction(String text) {
        String lower = text.toLowerCase();
        
        // Cek jika teks terlalu panjang (berupa cerita/obrolan)
        if (text.split("\\s+").length > 7) {
            return null; // Terlalu panjang, lempar ke AI
        }

        // Regex untuk menangkap nominal (opsional dengan Rp, angka dengan/tanpa titik, opsional k/rb/ribu/jt/juta/m)
        Pattern p = Pattern.compile("\\b(?:rp\\s*)?(\\d{1,3}(?:\\.\\d{3})*|\\d+)\\s*(k|rb|ribu|jt|juta|m|miliar)?\\b");
        Matcher m = p.matcher(lower);

        if (m.find()) {
            String numStr = m.group(1).replace(".", "");
            String multiplier = m.group(2);

            double amount;
            try {
                amount = Double.parseDouble(numStr);
            } catch (NumberFormatException e) {
                return null;
            }

            if (multiplier != null) {
                switch (multiplier) {
                    case "k": case "rb": case "ribu": amount *= 1000; break;
                    case "jt": case "juta": amount *= 1000000; break;
                    case "m": case "miliar": amount *= 1000000000; break;
                }
            }

            // Batasan rasional
            if (amount <= 0) return null;

            // Ekstrak deskripsi (teks sisanya)
            String desc = text.substring(0, m.start()) + text.substring(m.end());
            desc = desc.replaceAll("(?i)\\brp\\b", "").trim().replaceAll("\\s+", " ");

            if (desc.isEmpty()) {
                return null; // Harus ada keterangan, bukan angka saja
            }

            // Tebak Tipe (Pemasukan/Pengeluaran)
            TransactionType type = TransactionType.EXPENSE;
            String[] incomeKeywords = {"gaji", "bonus", "masuk", "dikasih", "refund", "jual", "profit", "hadiah", "thr", "cair", "bayaran", "investasi"};
            for (String kw : incomeKeywords) {
                if (lower.contains(kw)) {
                    type = TransactionType.INCOME;
                    break;
                }
            }

            // Tebak Kategori
            String category = "Lainnya";
            if (type == TransactionType.INCOME) {
                if (lower.contains("gaji")) category = "Gaji";
                else if (lower.contains("bonus") || lower.contains("thr")) category = "Bonus";
            } else {
                if (lower.matches(".*\\b(makan|minum|kopi|gojek|gofood|grabfood|nasi|warteg|kfc|mcd|sate|bakso|indomie|teh|snack|jajan)\\b.*")) category = "Makanan";
                else if (lower.matches(".*\\b(bensin|parkir|tol|gojek|grab|kereta|krl|mrt|bus|angkot|tiket)\\b.*")) category = "Transport";
                else if (lower.matches(".*\\b(listrik|air|wifi|internet|indihome|pulsa|paket|kuota|bpjs|kos|kontrakan|sewa)\\b.*")) category = "Kebutuhan";
                else if (lower.matches(".*\\b(belanja|baju|sepatu|shopee|tokopedia|tiktok|skincare|makeup)\\b.*")) category = "Belanja";
                else if (lower.matches(".*\\b(obat|dokter|rs|rumah sakit|klinik|apotek|vitamin)\\b.*")) category = "Kesehatan";
                else if (lower.matches(".*\\b(nonton|bioskop|tiket|main|game|netflix|spotify|langganan|jalan|liburan)\\b.*")) category = "Hiburan";
            }

            // Capitalize first letter of description
            desc = desc.substring(0, 1).toUpperCase() + desc.substring(1);

            return new ParsedTransaction(type, amount, category, desc);
        }

        return null;
    }

    public String generateFunnyResponse(TransactionType type, double amount, String description) {
        String formattedAmount = TransactionService.formatRupiah(java.math.BigDecimal.valueOf(amount));
        String shortDesc = description.length() > 20 ? description.substring(0, 20) + "..." : description;

        if (type == TransactionType.INCOME) {
            String[] incomeResponses = {
                    "💰 " + formattedAmount + " masuk! Dompet akhirnya dapat suntikan dana 😎",
                    "✅ Pemasukan " + formattedAmount + " (" + shortDesc + ") dicatat. Asik, makin kaya! 🚀",
                    "💵 Wah dapet " + formattedAmount + " nih. Jangan langsung checkout keranjang ya 😂",
                    "🎉 Uang masuk " + formattedAmount + " sudah tercatat. Traktir bot dong sekali-kali 😭",
                    "💸 " + formattedAmount + " mendarat dengan aman. Saldo full senyum 😎"
            };
            return incomeResponses[random.nextInt(incomeResponses.length)];
        } else {
            String[] expenseResponses = {
                    "🍜 Pengeluaran " + formattedAmount + " buat " + shortDesc + " sudah dicatat. Dompet masih aman 😂",
                    "💸 " + formattedAmount + " melayang untuk " + shortDesc + ". Semangat cari gantinya 😭",
                    "📝 " + shortDesc + " " + formattedAmount + " tercatat. Hati-hati saldo menangis 😂",
                    "🚨 Keluar lagi " + formattedAmount + " buat " + shortDesc + ". Kita pantau terus saldonya 👀",
                    "😭 Bye-bye " + formattedAmount + ". Demi " + shortDesc + " kita rela berkorban.",
                    "📉 Dicatat! " + formattedAmount + " untuk " + shortDesc + ". Makin dekat dengan akhir bulan 😂"
            };
            return expenseResponses[random.nextInt(expenseResponses.length)];
        }
    }
}
