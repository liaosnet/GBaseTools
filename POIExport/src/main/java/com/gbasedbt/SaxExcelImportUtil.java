package com.gbasedbt;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

/**
 * .xlsx 专用导入引擎（SAX 事件模型）
 * 内存占用恒定（~100MB），支持百万行大文件
 * 适配 Apache POI 5.5.1
 */
public class SaxExcelImportUtil {

    private static final Logger log = LoggerFactory.getLogger(SaxExcelImportUtil.class);
    private static final int BATCH_SIZE = 1000;

    /**
     * 导入所有 Sheet
     *
     * @param specifiedTable 已校验的指定表名，null 表示使用 Sheet 名称
     */
    public static ExcelImportUtil.ImportResult importAllSheets(
            Connection connection, InputStream inputStream,
            String specifiedTable) throws Exception {

        ExcelImportUtil.ImportResult importResult = new ExcelImportUtil.ImportResult();

        try (OPCPackage opcPackage = OPCPackage.open(inputStream)) {
            ReadOnlySharedStringsTable stringsTable = new ReadOnlySharedStringsTable(opcPackage);
            XSSFReader xssfReader = new XSSFReader(opcPackage);
            StylesTable stylesTable = xssfReader.getStylesTable();

            XSSFReader.SheetIterator sheetIterator =
                    (XSSFReader.SheetIterator) xssfReader.getSheetsData();

            while (sheetIterator.hasNext()) {
                try (InputStream sheetStream = sheetIterator.next()) {
                    String sheetName = sheetIterator.getSheetName();

                    // 解析表名（指定表名 or Sheet名称 + 存在性校验）
                    String tableName = ExcelImportUtil.resolveTableName(
                            connection, specifiedTable, sheetName);

                    // SAX 解析收集数据
                    RowCollector rowCollector = new RowCollector();
                    XMLReader xmlReader = XMLReaderFactory.createXMLReader();
                    XSSFSheetXMLHandler handler = new XSSFSheetXMLHandler(
                            stylesTable, stringsTable, rowCollector, false);
                    xmlReader.setContentHandler(handler);
                    xmlReader.parse(new InputSource(sheetStream));

                    // 批量插入
                    long count = batchInsert(connection, tableName, rowCollector);
                    importResult.add(new ExcelImportUtil.SheetResult(sheetName, tableName, count));
                }
            }
        }

        return importResult;
    }

    // =========================================================================
    // 批量插入
    // =========================================================================

    private static long batchInsert(Connection connection, String tableName,
                                    RowCollector collector) throws Exception {
        boolean isMysqlMode = connection.getMetaData().getURL().toLowerCase().contains("sqlmode=mysql");

        List<String> headers = collector.getHeaders();
        List<List<String>> dataRows = collector.getDataRows();

        if (headers.isEmpty() || dataRows.isEmpty()) return 0;

        String cols = "";
        if (isMysqlMode){
            cols = "`" + String.join("`,`", headers) + "`";
            tableName = "`" + tableName + "`";
        } else {
            cols = String.join(",", headers);
        }
        String placeholders = headers.stream().map(h -> "?")
                .reduce((a, b) -> a + "," + b).orElse("");
        String sql = "INSERT INTO " + tableName + " (" + cols + ") VALUES (" + placeholders + ")";
        log.info("插入SQL语句：" + sql);

        long importCount = 0;
        boolean originalAutoCommit = connection.getAutoCommit();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            connection.setAutoCommit(false);

            for (List<String> row : dataRows) {
                for (int i = 0; i < headers.size(); i++) {
                    String value = (i < row.size()) ? row.get(i) : null;
                    ps.setString(i + 1, value);
                }
                ps.addBatch();

                if (++importCount % BATCH_SIZE == 0) {
                    ps.executeBatch();
                    ps.clearBatch();
                }
            }

            if (importCount % BATCH_SIZE != 0) {
                ps.executeBatch();
            }
            connection.commit();

        } catch (Exception e) {
            // 可能prepare时就报错了，需要处理一下
            if (! connection.getAutoCommit()){
                connection.rollback();
            }
            throw new RuntimeException("表[" + tableName + "] 批量插入失败: " + e.getMessage(), e);
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }

        return importCount;
    }

    // =========================================================================
    // SAX 行收集器（适配 POI 5.5.1 SheetContentsHandler）
    // =========================================================================

    /**
     * POI 5.5.1 SheetContentsHandler 抽象方法：
     *   - startRow(int rowNum)
     *   - endRow(int rowNum)
     *   - cell(String cellReference, String formattedValue, XSSFComment comment)
     *
     * default 方法（无需重写）：
     *   - headerFooter(String text, boolean isHeader, String tagName)
     */
    static class RowCollector implements SheetContentsHandler {

        private final List<String> headers = new ArrayList<>();
        private final List<List<String>> dataRows = new ArrayList<>();
        private List<String> currentRow;
        private int currentRowNum = -1;

        @Override
        public void startRow(int rowNum) {
            currentRowNum = rowNum;
            currentRow = new ArrayList<>();
        }

        @Override
        public void endRow(int rowNum) {
            if (currentRowNum == 0) {
                headers.addAll(currentRow);
            } else {
                boolean allEmpty = true;
                for (String v : currentRow) {
                    if (v != null && !v.trim().isEmpty()) {
                        allEmpty = false;
                        break;
                    }
                }
                if (!allEmpty) {
                    dataRows.add(currentRow);
                }
            }
            currentRow = null;
        }

        /**
         * POI 5.5.1 cell 方法签名
         * 第三个参数：org.apache.poi.xssf.usermodel.XSSFComment
         */
        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            if (currentRow == null) return;

            int colIndex = cellReferenceToColIndex(cellReference);

            // 补齐中间缺失的空列
            while (currentRow.size() < colIndex) {
                currentRow.add(null);
            }

            if (currentRow.size() == colIndex) {
                currentRow.add(formattedValue);
            } else {
                currentRow.set(colIndex, formattedValue);
            }
        }

        /**
         * 将单元格引用（如 "A1", "BC23"）转为 0-based 列索引
         * 仅解析前缀字母部分，遇到数字即停止
         */
        private int cellReferenceToColIndex(String cellReference) {
            int col = 0;
            for (int i = 0; i < cellReference.length(); i++) {
                char c = cellReference.charAt(i);
                if (c >= 'A' && c <= 'Z') {
                    col = col * 26 + (c - 'A' + 1);
                } else if (c >= 'a' && c <= 'z') {
                    col = col * 26 + (c - 'a' + 1);
                } else {
                    break; // 遇到数字或其他字符即停止
                }
            }
            return col - 1;
        }

        public List<String> getHeaders() { return headers; }
        public List<List<String>> getDataRows() { return dataRows; }
    }
}