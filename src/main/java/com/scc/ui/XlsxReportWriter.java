package com.scc.ui;

import com.scc.model.OrderStatusReport;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes the report as an Excel (.xlsx) file using only plain Java (no extra library).
 * An .xlsx file is a ZIP file that contains a few small XML files.
 */
public class XlsxReportWriter {
    private static final String MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n";

    public static void write(OrderStatusReport report, File file) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            add(zip, "[Content_Types].xml", HEAD
                    + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                    + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                    + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                    + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
                    + "</Types>");

            add(zip, "_rels/.rels", HEAD
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"" + REL_NS + "/officeDocument\" Target=\"xl/workbook.xml\"/>"
                    + "</Relationships>");

            add(zip, "xl/workbook.xml", HEAD
                    + "<workbook xmlns=\"" + MAIN_NS + "\" xmlns:r=\"" + REL_NS + "\">"
                    + "<sheets><sheet name=\"Order Status Report\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");

            add(zip, "xl/_rels/workbook.xml.rels", HEAD
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"" + REL_NS + "/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                    + "<Relationship Id=\"rId2\" Type=\"" + REL_NS + "/styles\" Target=\"styles.xml\"/>"
                    + "</Relationships>");

            // Two cell styles: 0 = normal, 1 = bold
            add(zip, "xl/styles.xml", HEAD
                    + "<styleSheet xmlns=\"" + MAIN_NS + "\">"
                    + "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                    + "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"
                    + "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill>"
                    + "<fill><patternFill patternType=\"gray125\"/></fill></fills>"
                    + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
                    + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                    + "<cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                    + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/></cellXfs>"
                    + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
                    + "</styleSheet>");

            // The sheet itself: the same rows as the console report
            StringBuilder rows = new StringBuilder();
            int r = 1;
            rows.append("<row r=\"").append(r).append("\">").append(textCell("A", r, OrderStatusReport.REPORT_NAME, true)).append("</row>");
            r += 2; // one blank row
            for (String[] info : report.infoRows()) {
                rows.append("<row r=\"").append(r).append("\">")
                        .append(textCell("A", r, info[0], true)).append(textCell("B", r, info[1], false)).append("</row>");
                r++;
            }
            r++; // one blank row
            rows.append("<row r=\"").append(r).append("\">")
                    .append(textCell("A", r, "Order Status", true)).append(textCell("B", r, "Number of Orders", true)).append("</row>");
            r++;
            for (String[] count : report.countRows()) {
                rows.append("<row r=\"").append(r).append("\">").append(textCell("A", r, count[0], false))
                        .append("<c r=\"B").append(r).append("\"><v>").append(Integer.parseInt(count[1])).append("</v></c></row>"); // a real number
                r++;
            }
            add(zip, "xl/worksheets/sheet1.xml", HEAD
                    + "<worksheet xmlns=\"" + MAIN_NS + "\">"
                    + "<cols><col min=\"1\" max=\"1\" width=\"28\" customWidth=\"1\"/><col min=\"2\" max=\"2\" width=\"30\" customWidth=\"1\"/></cols>"
                    + "<sheetData>" + rows + "</sheetData></worksheet>");
        }
    }

    private static void add(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    // A text cell (inline string). Style 1 = bold.
    private static String textCell(String column, int row, String value, boolean bold) {
        return "<c r=\"" + column + row + "\" t=\"inlineStr\"" + (bold ? " s=\"1\"" : "") + "><is><t>"
                + escape(value) + "</t></is></c>";
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}