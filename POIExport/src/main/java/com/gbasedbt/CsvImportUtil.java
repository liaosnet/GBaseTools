package com.gbasedbt;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV 导入工具类
 * 策略：优先使用参数指定的表名，回退到使用文件名（去除后缀）作为表名
 * 导入前校验目标表是否在数据库中存在
 */
public class CsvImportUtil {

    private static final int BATCH_SIZE = 1000;
    private static final Logger log = LoggerFactory.getLogger(CsvImportUtil.class);;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /**
     * 导入 CSV 文件
     *
     * @param connection     数据库连接
     * @param inputStream    CSV 文件流
     * @param specifiedTable 指定的表名（可为 null）
     *                       - 非null：写入该表
     *                       - null：使用 fileName 清洗后作为表名
     * @param fileName       文件名（用于回退生成表名，如 "orders.csv"）
     * @return 成功导入的数据行数
     */
    public static long importCsv(Connection connection, InputStream inputStream,
                                 String specifiedTable, String fileName) throws Exception {

        boolean isMysqlMode = connection.getMetaData().getURL().toLowerCase().contains("sqlmode=mysql");

        // 1. 解析目标表名
        String tableName;
        if (specifiedTable != null && !specifiedTable.trim().isEmpty()) {
            tableName = ExcelImportUtil.sanitizeTableName(specifiedTable);
        } else if (fileName != null && !fileName.trim().isEmpty()) {
            // 去除 .csv 后缀并清洗
            String rawName = fileName.replaceAll("(?i)\\.csv$", "");
            tableName = ExcelImportUtil.sanitizeTableName(rawName);
        } else {
            throw new IllegalArgumentException("未指定表名且未提供文件名，无法生成目标表名");
        }

        // 2. 校验表是否存在（复用 Excel 工具类的校验逻辑）
        ExcelImportUtil.assertTableExists(connection, tableName);

        // 3. 执行batch导入
        return doBatchInsert(connection, tableName, inputStream);
    }

    /**
     * 真正的批量插入逻辑
     */
    private static long doBatchInsert(Connection connection, String tableName, InputStream inputStream) throws Exception {
        boolean isMysqlMode = connection.getMetaData().getURL().toLowerCase().contains("sqlmode=mysql");
        long importCount = 0;
        List<String> headers = new ArrayList<>();
        String sql = null;

        try (CSVReader reader = new CSVReaderBuilder(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8)).build()) {

            String[] line;
            boolean isHeader = true;
            PreparedStatement ps = null;

            try {
                while ((line = reader.readNext()) != null) {
                    // header中生成导入语句
                    if (isHeader) {
                        for (String h : line) headers.add(h.trim());
                        String cols = "";
                        if (isMysqlMode){
                            cols = "`" + String.join("`,`", headers) + "`";
                            tableName = "`" + tableName + "`";
                        } else {
                            cols = String.join(",", headers);
                        }
                        String placeholders = String.join(",", headers.stream()
                                .map(h -> "?").toArray(String[]::new));
                        sql = "INSERT INTO " + tableName + " (" + cols + ") VALUES (" + placeholders + ")";
                        log.info("导入语句为：" + sql);
                        ps = connection.prepareStatement(sql);
                        // connection.setAutoCommit(false);
                        isHeader = false;
                        continue;
                    }

                    // 绑定参数
                    for (int i = 0; i < headers.size(); i++) {
                        String val = (i < line.length) ? line[i] : null;
                        ps.setString(i + 1, val);
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

            } catch (Exception e) {
                throw new RuntimeException("CSV表[" + tableName + "] 导入失败: " + e.getMessage(), e);
            } finally {
                if (ps != null) ps.close();
            }
        }
        return importCount;
    }
}