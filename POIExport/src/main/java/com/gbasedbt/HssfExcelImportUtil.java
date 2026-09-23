package com.gbasedbt;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * .xls 专用导入引擎（HSSF DOM 模式）
 * .xls 上限 65536 行，DOM 模式内存可控（~300MB 峰值）
 */
public class HssfExcelImportUtil {

    private static final Logger log = LoggerFactory.getLogger(HssfExcelImportUtil.class);
    private static final int BATCH_SIZE = 1000;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /**
     * 导入所有 Sheet
     *
     * @param specifiedTable 已校验的指定表名，null 表示使用 Sheet 名称
     */
    public static ExcelImportUtil.ImportResult importAllSheets(
            Connection connection, InputStream inputStream,
            String specifiedTable) throws Exception {

        ExcelImportUtil.ImportResult importResult = new ExcelImportUtil.ImportResult();

        try (Workbook workbook = new HSSFWorkbook(inputStream)) {
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                Sheet sheet = workbook.getSheetAt(s);
                String sheetName = workbook.getSheetName(s);

                // 解析表名
                String tableName = ExcelImportUtil.resolveTableName(
                        connection, specifiedTable, sheetName);

                long count = importSingleSheet(connection, tableName, sheet);
                importResult.add(new ExcelImportUtil.SheetResult(sheetName, tableName, count));
            }
        }

        return importResult;
    }

    // =========================================================================
    // 单 Sheet 导入
    // =========================================================================

    private static long importSingleSheet(Connection connection, String tableName,
                                          Sheet sheet) throws Exception {
        boolean isMysqlMode = connection.getMetaData().getURL().toLowerCase().contains("sqlmode=mysql");

        // 读取表头
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) return 0;

        List<String> headers = new ArrayList<>();
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            String val = (cell != null) ? getCellValueAsString(cell) : null;
            headers.add((val != null && !val.trim().isEmpty()) ? val.trim() : "col_" + i);
        }

        if (headers.isEmpty()) return 0;

        // 构建 SQL
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

        // 批量插入
        long importCount = 0;
        boolean originalAutoCommit = connection.getAutoCommit();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            connection.setAutoCommit(false);

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isEmptyRow(row)) continue;

                for (int i = 0; i < headers.size(); i++) {
                    Cell cell = row.getCell(i);
                    String value = (cell != null) ? getCellValueAsString(cell) : null;
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
    // 工具方法
    // =========================================================================

    private static String getCellValueAsString(Cell cell) {
        if (cell == null) return null;

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();

            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    synchronized (DATE_FORMAT) {
                        return DATE_FORMAT.format(cell.getDateCellValue());
                    }
                }
                double num = cell.getNumericCellValue();
                if (num == Math.floor(num) && !Double.isInfinite(num)) {
                    return String.valueOf((long) num);
                }
                return String.valueOf(num);

            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());

            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    try {
                        return String.valueOf(cell.getNumericCellValue());
                    } catch (Exception e2) {
                        return null;
                    }
                }

            case BLANK:
                return null;

            default:
                return null;
        }
    }

    private static boolean isEmptyRow(Row row) {
        if (row.getFirstCellNum() < 0) return true;
        for (int i = row.getFirstCellNum(); i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }
}