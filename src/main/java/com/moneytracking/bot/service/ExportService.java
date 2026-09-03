package com.moneytracking.bot.service;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.moneytracking.bot.entity.Transaction;
import com.moneytracking.bot.entity.TransactionType;
import com.moneytracking.bot.entity.User;
import com.moneytracking.bot.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);
    private final TransactionRepository transactionRepository;

    public ExportService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public File generateTransactionPdf(User user) {
        List<Transaction> transactions = transactionRepository.findByUserOrderByTransactionDateDesc(user);
        
        String fileName = "Riwayat_Keuangan_" + user.getUsername() + ".pdf";
        File file = new File(fileName);
        
        try {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, new FileOutputStream(file));
            document.open();

            // Title
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BaseColor.BLACK);
            Paragraph title = new Paragraph("Laporan Riwayat Keuangan", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20f);
            document.add(title);

            // Table
            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{2f, 2f, 1.5f, 3f, 2.5f});

            // Table Headers
            addTableHeader(table, "Tanggal");
            addTableHeader(table, "Kategori");
            addTableHeader(table, "Tipe");
            addTableHeader(table, "Deskripsi");
            addTableHeader(table, "Nominal");

            // Formatter
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
            NumberFormat formatRupiah = NumberFormat.getCurrencyInstance(new Locale("id", "ID"));
            
            BigDecimal totalIncome = BigDecimal.ZERO;
            BigDecimal totalExpense = BigDecimal.ZERO;

            // Table Rows
            for (Transaction t : transactions) {
                table.addCell(new PdfPCell(new Phrase(t.getTransactionDate().format(dateFormatter))));
                table.addCell(new PdfPCell(new Phrase(t.getCategory().getName())));
                
                if (t.getType() == TransactionType.INCOME) {
                    PdfPCell cell = new PdfPCell(new Phrase("Masuk"));
                    cell.setBackgroundColor(BaseColor.GREEN);
                    table.addCell(cell);
                    totalIncome = totalIncome.add(t.getAmount());
                } else {
                    PdfPCell cell = new PdfPCell(new Phrase("Keluar"));
                    cell.setBackgroundColor(BaseColor.RED);
                    table.addCell(cell);
                    totalExpense = totalExpense.add(t.getAmount());
                }
                
                table.addCell(new PdfPCell(new Phrase(t.getDescription() == null ? "-" : t.getDescription())));
                table.addCell(new PdfPCell(new Phrase(formatRupiah.format(t.getAmount()).replace("Rp", "Rp "))));
            }
            
            document.add(table);
            
            // Summary
            document.add(new Paragraph("\n"));
            Font summaryFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BaseColor.BLACK);
            document.add(new Paragraph("Total Pemasukan: " + formatRupiah.format(totalIncome).replace("Rp", "Rp "), summaryFont));
            document.add(new Paragraph("Total Pengeluaran: " + formatRupiah.format(totalExpense).replace("Rp", "Rp "), summaryFont));
            
            BigDecimal balance = totalIncome.subtract(totalExpense);
            Paragraph balancePara = new Paragraph("Saldo Akhir: " + formatRupiah.format(balance).replace("Rp", "Rp "), summaryFont);
            balancePara.setSpacingBefore(10f);
            document.add(balancePara);

            document.close();
            return file;
            
        } catch (Exception e) {
            log.error("Error generating PDF: ", e);
            return null;
        }
    }

    private void addTableHeader(PdfPTable table, String headerTitle) {
        PdfPCell header = new PdfPCell();
        header.setBackgroundColor(BaseColor.LIGHT_GRAY);
        header.setBorderWidth(1);
        header.setPhrase(new Phrase(headerTitle, FontFactory.getFont(FontFactory.HELVETICA_BOLD)));
        header.setHorizontalAlignment(Element.ALIGN_CENTER);
        header.setPaddingBottom(5f);
        table.addCell(header);
    }
}
