package com.moneytracking.bot.service;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.draw.LineSeparator;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);
    private final TransactionRepository transactionRepository;

    // Custom Colors
    private static final BaseColor COLOR_PRIMARY_DARK = new BaseColor(44, 62, 80);    // #2C3E50
    private static final BaseColor COLOR_INCOME = new BaseColor(39, 174, 96);        // #27AE60
    private static final BaseColor COLOR_EXPENSE = new BaseColor(231, 76, 60);       // #E74C3C
    private static final BaseColor COLOR_ROW_ALT = new BaseColor(248, 249, 250);     // Light gray for zebra

    public ExportService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public File generateTransactionPdf(User user) {
        List<Transaction> transactions = transactionRepository.findByUserOrderByTransactionDateAsc(user);
        
        String fileName = "Riwayat_Keuangan_" + user.getUsername() + ".pdf";
        File file = new File(fileName);
        
        try {
            Document document = new Document(PageSize.A4, 36, 36, 54, 36);
            PdfWriter.getInstance(document, new FileOutputStream(file));
            document.open();

            // Font Definitions
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, COLOR_PRIMARY_DARK);
            Font metaFont = FontFactory.getFont(FontFactory.HELVETICA, 10, BaseColor.GRAY);
            Font summaryTitleFont = FontFactory.getFont(FontFactory.HELVETICA, 10, BaseColor.DARK_GRAY);
            Font summaryValueFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, COLOR_PRIMARY_DARK);
            Font summaryBalanceFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, COLOR_PRIMARY_DARK);
            
            // Header Section
            PdfPTable headerTable = new PdfPTable(2);
            headerTable.setWidthPercentage(100);
            
            PdfPCell titleCell = new PdfPCell(new Phrase("Lingz Finance Report", titleFont));
            titleCell.setBorder(Rectangle.NO_BORDER);
            titleCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            headerTable.addCell(titleCell);
            
            String periodStr = "Periode: Belum ada transaksi";
            if (!transactions.isEmpty()) {
                // Because list is ordered ascending, first item is the oldest, last is the newest
                LocalDateTime oldestDate = transactions.get(0).getTransactionDate();
                LocalDateTime newestDate = transactions.get(transactions.size() - 1).getTransactionDate();
                DateTimeFormatter periodFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy");
                
                if (oldestDate.toLocalDate().equals(newestDate.toLocalDate())) {
                    periodStr = "Periode: " + oldestDate.format(periodFormatter);
                } else {
                    periodStr = "Periode: " + oldestDate.format(periodFormatter) + " - " + newestDate.format(periodFormatter);
                }
            }

            DateTimeFormatter metaDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
            String metaInfo = "User: @" + user.getUsername() + "\n" + periodStr + "\nDicetak: " + LocalDateTime.now().format(metaDateFormatter);
            PdfPCell metaCell = new PdfPCell(new Phrase(metaInfo, metaFont));
            metaCell.setBorder(Rectangle.NO_BORDER);
            metaCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            metaCell.setVerticalAlignment(Element.ALIGN_BOTTOM);
            headerTable.addCell(metaCell);
            
            document.add(headerTable);
            
            // Line Separator
            LineSeparator ls = new LineSeparator();
            ls.setLineColor(COLOR_PRIMARY_DARK);
            ls.setLineWidth(2f);
            document.add(new Chunk(ls));
            document.add(new Paragraph("\n"));

            // Calculate Totals First for the Summary Dashboard
            BigDecimal totalIncome = BigDecimal.ZERO;
            BigDecimal totalExpense = BigDecimal.ZERO;
            for (Transaction t : transactions) {
                if (t.getType() == TransactionType.INCOME) {
                    totalIncome = totalIncome.add(t.getAmount());
                } else {
                    totalExpense = totalExpense.add(t.getAmount());
                }
            }
            BigDecimal balance = totalIncome.subtract(totalExpense);
            NumberFormat formatRupiah = NumberFormat.getCurrencyInstance(new Locale("id", "ID"));

            // Summary Dashboard (3 Columns)
            PdfPTable summaryTable = new PdfPTable(3);
            summaryTable.setWidthPercentage(100);
            summaryTable.setSpacingBefore(10f);
            summaryTable.setSpacingAfter(20f);

            summaryTable.addCell(createSummaryCell("Pemasukan", formatRupiah.format(totalIncome).replace("Rp", "Rp "), summaryTitleFont, new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD, COLOR_INCOME)));
            summaryTable.addCell(createSummaryCell("Pengeluaran", formatRupiah.format(totalExpense).replace("Rp", "Rp "), summaryTitleFont, new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD, COLOR_EXPENSE)));
            summaryTable.addCell(createSummaryCell("Saldo Akhir", formatRupiah.format(balance).replace("Rp", "Rp "), summaryTitleFont, summaryBalanceFont));

            document.add(summaryTable);

            // Transactions Table
            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{1.5f, 2f, 3f, 2f, 2.5f});

            // Table Headers (Modern Style)
            addTableHeader(table, "Tanggal", Element.ALIGN_LEFT);
            addTableHeader(table, "Kategori", Element.ALIGN_LEFT);
            addTableHeader(table, "Deskripsi", Element.ALIGN_LEFT);
            addTableHeader(table, "Nominal", Element.ALIGN_RIGHT);
            addTableHeader(table, "Saldo", Element.ALIGN_RIGHT);

            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            Font regularFont = FontFactory.getFont(FontFactory.HELVETICA, 10, BaseColor.BLACK);
            Font balanceFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, COLOR_PRIMARY_DARK);

            // Because transactions are ordered by Date Asc (Oldest first),
            // We calculate running balance starting from zero moving forward.
            BigDecimal currentRunningBalance = BigDecimal.ZERO;

            // Table Rows
            boolean isAltRow = false;
            for (Transaction t : transactions) {
                BaseColor bgColor = isAltRow ? COLOR_ROW_ALT : BaseColor.WHITE;
                
                // Update running balance based on current transaction
                if (t.getType() == TransactionType.INCOME) {
                    currentRunningBalance = currentRunningBalance.add(t.getAmount());
                } else {
                    currentRunningBalance = currentRunningBalance.subtract(t.getAmount());
                }

                // Date
                table.addCell(createDataCell(t.getTransactionDate().format(dateFormatter), regularFont, Element.ALIGN_LEFT, bgColor));
                
                // Category
                table.addCell(createDataCell(t.getCategory().getName(), regularFont, Element.ALIGN_LEFT, bgColor));
                
                // Description
                String desc = (t.getDescription() == null || t.getDescription().trim().isEmpty()) ? "-" : t.getDescription();
                table.addCell(createDataCell(desc, regularFont, Element.ALIGN_LEFT, bgColor));
                
                // Amount with color logic
                String amountStr = formatRupiah.format(t.getAmount()).replace("Rp", "Rp ");
                Font amountFont;
                if (t.getType() == TransactionType.INCOME) {
                    amountStr = "+ " + amountStr;
                    amountFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, COLOR_INCOME);
                } else {
                    amountStr = "- " + amountStr;
                    amountFont = FontFactory.getFont(FontFactory.HELVETICA, 10, COLOR_EXPENSE);
                }
                table.addCell(createDataCell(amountStr, amountFont, Element.ALIGN_RIGHT, bgColor));

                // Running Balance Cell
                String balanceStr = formatRupiah.format(currentRunningBalance).replace("Rp", "Rp ");
                table.addCell(createDataCell(balanceStr, balanceFont, Element.ALIGN_RIGHT, bgColor));

                isAltRow = !isAltRow; // Toggle row color
            }
            
            document.add(table);
            document.close();
            return file;
            
        } catch (Exception e) {
            log.error("Error generating PDF: ", e);
            return null;
        }
    }

    private PdfPCell createSummaryCell(String title, String value, Font titleFont, Font valueFont) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(10f);
        
        Paragraph titlePara = new Paragraph(title, titleFont);
        titlePara.setAlignment(Element.ALIGN_CENTER);
        
        Paragraph valuePara = new Paragraph(value, valueFont);
        valuePara.setAlignment(Element.ALIGN_CENTER);
        
        cell.addElement(titlePara);
        cell.addElement(valuePara);
        return cell;
    }

    private void addTableHeader(PdfPTable table, String headerTitle, int alignment) {
        PdfPCell header = new PdfPCell();
        header.setBackgroundColor(COLOR_PRIMARY_DARK);
        header.setBorder(Rectangle.NO_BORDER); // Remove default borders
        header.setPaddingTop(8f);
        header.setPaddingBottom(8f);
        header.setPaddingLeft(5f);
        header.setPaddingRight(5f);
        
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, BaseColor.WHITE);
        Phrase phrase = new Phrase(headerTitle, headerFont);
        header.setPhrase(phrase);
        header.setHorizontalAlignment(alignment);
        header.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(header);
    }

    private PdfPCell createDataCell(String content, Font font, int alignment, BaseColor bgColor) {
        PdfPCell cell = new PdfPCell(new Phrase(content, font));
        cell.setBackgroundColor(bgColor);
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        
        // Modern styling: Only bottom border, thin and light gray
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColorBottom(new BaseColor(224, 224, 224));
        cell.setBorderWidthBottom(0.5f);
        
        cell.setPaddingTop(8f);
        cell.setPaddingBottom(8f);
        cell.setPaddingLeft(5f);
        cell.setPaddingRight(5f);
        return cell;
    }
}
