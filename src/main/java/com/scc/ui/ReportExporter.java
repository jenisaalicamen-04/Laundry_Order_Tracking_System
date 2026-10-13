package com.scc.ui;

import com.scc.model.OrderStatusReport;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Exports the Laundry Order Status Report to the "reports" folder.
 * CSV, PDF, and XLSX are all written with plain Java (no extra library is needed).
 * Each method returns the file ONLY if it was really created.
 */
public class ReportExporter {
    public static final String REPORT_FOLDER = "reports";

    // Makes sure the "reports" folder exists and builds a unique file name with a timestamp.
    private File createTargetFile(String extension) throws IOException {
        File folder = new File(REPORT_FOLDER);
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("Could not create the folder '" + REPORT_FOLDER + "'.");
        }
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String baseName = "Laundry_Order_Status_Report_" + stamp;
        File file = new File(folder, baseName + "." + extension);
        int counter = 2;
        while (file.exists()) { // never overwrite an older export
            file = new File(folder, baseName + "_" + counter + "." + extension);
            counter++;
        }
        return file;
    }

    // Checks that the file really exists and is not empty.
    private File verify(File file) throws IOException {
        if (!file.exists() || file.length() == 0) {
            throw new IOException("The file was not created correctly.");
        }
        return file;
    }

    public File exportCsv(OrderStatusReport report) throws IOException {
        File file = createTargetFile("csv");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(csv(OrderStatusReport.REPORT_NAME));
            writer.newLine();
            for (String[] row : report.infoRows()) {
                writer.write(csv(row[0]) + "," + csv(row[1]));
                writer.newLine();
            }
            writer.newLine();
            writer.write("Order Status,Number of Orders");
            writer.newLine();
            for (String[] row : report.countRows()) {
                writer.write(csv(row[0]) + "," + csv(row[1]));
                writer.newLine();
            }
        }
        return verify(file);
    }

    public File exportPdf(OrderStatusReport report) throws IOException {
        File file = createTargetFile("pdf");
        PdfReportWriter.write(report, file);
        return verify(file);
    }

    public File exportXlsx(OrderStatusReport report) throws IOException {
        File file = createTargetFile("xlsx");
        XlsxReportWriter.write(report, file);
        return verify(file);
    }

    // Puts quotes around a CSV value when it contains a comma, quote, or new line.
    private String csv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}