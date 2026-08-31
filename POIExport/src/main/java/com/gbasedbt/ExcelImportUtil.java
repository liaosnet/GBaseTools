package com.gbasedbt;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Excel 导入统一入口
 * <ul>
 *   <li>100% 基于文件头 Magic Number 识别格式，不依赖文件名后缀</li>
 *   <li>表名策略：参数指定 > Sheet名称（自动清洗）</li>
 *   <li>导入前校验目标表是否在数据库中存在</li>
 *   <li>.xlsx → SAX 事件模型（支持百万行）</li>
 *   <li>.xls  → HSSF DOM 模型（上限 65536 行）</li>
 * </ul>
 */
public class ExcelImportUtil {

    private static final byte[] OLE2_MAGIC = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0};
    private static final byte[] ZIP_MAGIC  = {0x50, 0x4B, 0x03, 0x04};
    private static final int MAGIC_LENGTH = 8;

    // =========================================================================
    // 结果对象
    // =========================================================================

    /**
     * 导入总结果
     */
    public static class ImportResult {
        private final List<SheetResult> sheetResults = new ArrayList<>();
        private long totalRows = 0;

        public void add(SheetResult r) {
            sheetResults.add(r);
            totalRows += r.getRowCount();
        }

        public List<SheetResult> getSheetResults() { return sheetResults; }
        public long getTotalRows() { return totalRows; }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("导入完成，共 " + totalRows + " 行:\n");
            for (SheetResult r : sheetResults) {
                sb.append("  [").append(r.getSheetName())
                        .append("] → ").append(r.getTableName())
                        .append(": ").append(r.getRowCount()).append(" 行\n");
            }
            return sb.toString();
        }
    }

    /**
     * 单个 Sheet 导入结果
     */
    public static class SheetResult {
        private final String sheetName;
        private final String tableName;
        private final long rowCount;

        public SheetResult(String sheetName, String tableName, long rowCount) {
            this.sheetName = sheetName;
            this.tableName = tableName;
            this.rowCount = rowCount;
        }

        public String getSheetName() { return sheetName; }
        public String getTableName() { return tableName; }
        public long getRowCount() { return rowCount; }
    }

    /**
     * 自定义异常：目标表不存在
     */
    public static class TableNotFoundException extends RuntimeException {
        public TableNotFoundException(String message) { super(message); }
    }

    // =========================================================================
    // 公开 API
    // =========================================================================

    /**
     * 导入所有 Sheet
     *
     * @param connection     数据库连接
     * @param inputStream    Excel 文件流（任意 InputStream）
     * @param specifiedTable 指定的表名（可为 null）
     *                       - 非null：所有 Sheet 统一写入该表
     *                       - null：每个 Sheet 以自身名称作为表名
     * @return 导入结果（包含每个 Sheet 的行数）
     */
    public static ImportResult importAllSheets(Connection connection,
                                               InputStream inputStream,
                                               String specifiedTable) throws Exception {

        // 如果指定了表名，先校验（多 Sheet 共用时只查一次）
        String resolvedSpecifiedTable = null;
        if (specifiedTable != null && !specifiedTable.trim().isEmpty()) {
            resolvedSpecifiedTable = sanitizeTableName(specifiedTable);
            assertTableExists(connection, resolvedSpecifiedTable);
        }

        // 预读文件头 → 格式检测 → 拼接还原完整流
        byte[] header = readHeader(inputStream);
        InputStream fullStream = reconstructStream(header, inputStream);
        ExcelFormat format = detectByMagic(header);

        switch (format) {
            case XLSX:
                return SaxExcelImportUtil.importAllSheets(connection, fullStream, resolvedSpecifiedTable);
            case XLS:
                return HssfExcelImportUtil.importAllSheets(connection, fullStream, resolvedSpecifiedTable);
            default:
                throw new IllegalArgumentException(
                        "无法识别的 Excel 格式。文件头(hex): " + bytesToHex(header));
        }
    }

    /**
     * 仅导入第一个 Sheet
     */
    public static SheetResult importFirstSheet(Connection connection,
                                               InputStream inputStream,
                                               String specifiedTable) throws Exception {
        ImportResult result = importAllSheets(connection, inputStream, specifiedTable);
        if (result.getSheetResults().isEmpty()) {
            throw new IllegalArgumentException("Excel 文件中没有可导入的 Sheet");
        }
        return result.getSheetResults().get(0);
    }

    /**
     * 兼容旧接口：不指定表名，纯靠 Sheet 名称
     */
    public static ImportResult importAllSheets(Connection connection,
                                               InputStream inputStream) throws Exception {
        return importAllSheets(connection, inputStream, null);
    }

    // =========================================================================
    // 表名解析 & 数据库校验
    // =========================================================================

    /**
     * 解析单个 Sheet 的目标表名
     * 优先级：specifiedTable > sanitize(sheetName)
     */
    public static String resolveTableName(Connection connection,
                                          String specifiedTable,
                                          String sheetName) throws Exception {
        if (specifiedTable != null && !specifiedTable.isEmpty()) {
            return specifiedTable; // 已在入口处校验过
        }
        String tableName = sanitizeTableName(sheetName);
        assertTableExists(connection, tableName);
        return tableName;
    }

    /**
     * 校验数据库中是否存在指定表，不存在则抛出 TableNotFoundException
     */
    public static void assertTableExists(Connection connection, String tableName) throws Exception {
        DatabaseMetaData metaData = connection.getMetaData();
        boolean exists = false;

        // 1. 精确匹配
        try (ResultSet rs = metaData.getTables(null, null, tableName, new String[]{"TABLE"})) {
            if (rs.next()) exists = true;
        }

        // 2. 大小写不敏感兜底（兼容 Oracle 大写 / MySQL 配置差异）
        if (!exists) {
            try (ResultSet rs = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    if (tableName.equalsIgnoreCase(rs.getString("TABLE_NAME"))) {
                        exists = true;
                        break;
                    }
                }
            }
        }

        if (!exists) {
            throw new TableNotFoundException(
                    "目标表 [" + tableName + "] 在数据库中不存在。请先创建该表或检查表名是否正确。");
        }
    }

    /**
     * Sheet 名称 → 安全表名
     * 规则：只保留字母/数字/下划线/中文；空格→下划线；数字开头加 t_ 前缀；截断至64字符
     */
    public static String sanitizeTableName(String sheetName) {
        if (sheetName == null || sheetName.trim().isEmpty()) {
            throw new IllegalArgumentException("Sheet 名称为空，无法生成表名");
        }

        String name = sheetName.trim().replaceAll("\\s+", "_");
        name = name.replaceAll("[^a-zA-Z0-9_\\u4e00-\\u9fa5]", "");

        if (name.isEmpty()) {
            throw new IllegalArgumentException(
                    "Sheet 名称 '" + sheetName + "' 清洗后无合法字符，请指定表名参数");
        }

        if (!Character.isLetter(name.charAt(0)) && name.charAt(0) != '_') {
            name = "t_" + name;
        }

        if (name.length() > 64) {
            name = name.substring(0, 64);
        }

        return name;
    }

    // =========================================================================
    // 内部工具
    // =========================================================================

    enum ExcelFormat { XLS, XLSX, UNKNOWN }

    private static byte[] readHeader(InputStream is) throws IOException {
        byte[] header = new byte[MAGIC_LENGTH];
        int totalRead = 0;
        while (totalRead < header.length) {
            int n = is.read(header, totalRead, header.length - totalRead);
            if (n == -1) break;
            totalRead += n;
        }
        if (totalRead < 4) {
            throw new IllegalArgumentException("文件过小，无法识别 Excel 格式");
        }
        return header;
    }

    private static InputStream reconstructStream(byte[] header, InputStream remaining) {
        return new SequenceInputStream(new ByteArrayInputStream(header), remaining);
    }

    private static ExcelFormat detectByMagic(byte[] header) {
        if (matchesMagic(header, ZIP_MAGIC))  return ExcelFormat.XLSX;
        if (matchesMagic(header, OLE2_MAGIC)) return ExcelFormat.XLS;
        return ExcelFormat.UNKNOWN;
    }

    private static boolean matchesMagic(byte[] data, byte[] magic) {
        if (data.length < magic.length) return false;
        for (int i = 0; i < magic.length; i++) {
            if (data[i] != magic[i]) return false;
        }
        return true;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02X ", b));
        return sb.toString().trim();
    }
}