package com.scc.ui;

import com.scc.model.OrderStatusReport;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the report as a one-page PDF file using only plain Java (no extra library).
 * A PDF is a text file made of numbered objects followed by a table of their positions.
 */
public class PdfReportWriter {

    public static void write(OrderStatusReport report, File file) throws IOException {
        // ----- 1. The page content: text and table lines -----
        StringBuilder page = new StringBuilder();
        text(page, "F2", 20, 50, 790, OrderStatusReport.REPORT_NAME);

        int y = 755;
        for (String[] row : report.infoRows()) {
            text(page, "F2", 12, 50, y, row[0] + ":");
            text(page, "F1", 12, 170, y, row[1]);
            y -= 20;
        }

        y -= 15;
        text(page, "F2", 14, 50, y, "Summary of Order Counts");
        y -= 25;

        // Table: header row + one row per status, with borders
        tableRow(page, y, "Order Status", "Number of Orders", true);
        for (String[] row : report.countRows()) {
            y -= 24;
            tableRow(page, y, row[0], row[1], false);
        }

        // ----- 2. The numbered PDF objects -----
        List<String> objects = new ArrayList<>();
        objects.add("<< /Type /Catalog /Pages 2 0 R >>");
        objects.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                + "/Resources << /Font << /F1 4 0 R /F2 5 0 R >> >> /Contents 6 0 R >>");
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>");
        objects.add("<< /Length " + page.length() + " >>\nstream\n" + page + "\nendstream");

        // ----- 3. Put everything together with the cross-reference table -----
        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        int[] offsets = new int[objects.size() + 1];
        for (int i = 0; i < objects.size(); i++) {
            offsets[i + 1] = pdf.length();
            pdf.append(i + 1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");
        }
        int xrefPosition = pdf.length();
        pdf.append("xref\n0 ").append(objects.size() + 1).append("\n");
        pdf.append("0000000000 65535 f \n");
        for (int i = 1; i <= objects.size(); i++) {
            pdf.append(String.format("%010d 00000 n \n", offsets[i]));
        }
        pdf.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\n");
        pdf.append("startxref\n").append(xrefPosition).append("\n%%EOF\n");

        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(pdf.toString().getBytes(StandardCharsets.ISO_8859_1));
        }
    }

    // One text line: BT = begin text, Tf = font and size, Td = position, Tj = show text.
    private static void text(StringBuilder page, String font, int size, int x, int y, String value) {
        page.append("BT /").append(font).append(' ').append(size).append(" Tf ")
                .append(x).append(' ').append(y).append(" Td (").append(clean(value)).append(") Tj ET\n");
    }

    // One table row: two boxes with text inside.
    private static void tableRow(StringBuilder page, int y, String left, String right, boolean header) {
        page.append("0.5 w 50 ").append(y - 6).append(" 250 24 re S\n");
        page.append("300 ").append(y - 6).append(" 150 24 re S\n");
        String font = header ? "F2" : "F1";
        text(page, font, 12, 58, y + 2, left);
        text(page, font, 12, 308, y + 2, right);
    }

    // Keeps only plain characters and escapes the ones that have a special meaning in PDF text.
    private static String clean(String value) {
        StringBuilder safe = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (c < 32 || c > 126) {
                safe.append('?');
            } else if (c == '(' || c == ')' || c == '\\') {
                safe.append('\\').append(c);
            } else {
                safe.append(c);
            }
        }
        return safe.toString();
    }
}