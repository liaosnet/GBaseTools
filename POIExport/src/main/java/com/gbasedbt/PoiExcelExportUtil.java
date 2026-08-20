package com.gbasedbt;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class PoiExcelExportUtil {

    private static final Logger log = LoggerFactory.getLogger(POIExport.class);

    /**
     * 极简的 POI 流式动态导出方法（POI 5.x 规范写法）
     */
    public static void export(Connection connection, String sql, OutputStream out) throws Exception {
        Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery(sql);
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        long dataRowCount = 0; // 用于记录实际导出的数据行数

        // 【核心改动】：使用 try-with-resources 自动管理 SXSSFWorkbook 的生命周期
        // 当离开 try 块时，workbook.close() 会被自动调用，它内部已包含清理临时文件的逻辑
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(1000)) {
            // sheet名称使用结果集元数据中的表名
            String sheetname = "".equals(metaData.getTableName(1))?"多表关联":metaData.getTableName(1);
            Sheet sheet = workbook.createSheet(sheetname);
            int rowIndex = 0;

            // 1. 写入动态表头，使用结果集元数据中字段标签
            Row headerRow = sheet.createRow(rowIndex++);
            for (int i = 1; i <= columnCount; i++) {
                headerRow.createCell(i - 1).setCellValue(metaData.getColumnLabel(i));
            }

            // 2. 流式读取并写入数据行
            while (rs.next()) {
                Row row = sheet.createRow(rowIndex++);
                for (int i = 1; i <= columnCount; i++) {
                    Object val = rs.getObject(i);
                    // 数据类型额外处理，这里暂时只是处理了Date
                    log.debug("object type: " + ((val == null)?"":val.getClass()));
                    if (val instanceof Date) {      // date及datetime
                        String dateType = metaData.getColumnTypeName(i);
                        val = new SimpleDateFormat(getFormat(dateType)).format((Date) val);
                    }
                    row.createCell(i - 1).setCellValue(val != null ? val.toString() : "");
                }
            }

            // 3. 输出到流
            // 计算实际数据行数（总行数减去 1 行表头）
            dataRowCount = rowIndex - 1;
            workbook.write(out);
        } finally {
            // 注意：JDBC 资源仍需手动关闭（或也可用 try-with-resources 嵌套）
            rs.close();
            stmt.close();
        }
        log.info("导出行数：" + dataRowCount);
    }

    /**
     * 将date/timestamp/datetime数据类型转换成对应的格式
     * @param dt
     * @return
     */
    private static String getFormat(String dt){
        String fmt = null;
        switch (dt.toLowerCase()) {
            case "date":
            case "datetime year to day":
                fmt = "yyyy-MM-dd";
                break;
            case "datetime year to year":
                fmt = "yyyy";
                break;
            case "datetime year to month":
                fmt = "yyyy-MM";
                break;
            case "datetime year to hour":
                fmt = "yyyy-MM-dd HH";
                break;
            case "datetime year to minute":
                fmt = "yyyy-MM-dd HH:mm";
                break;
            case "datetime year to second":
                fmt = "yyyy-MM-dd HH:mm:ss";
                break;
            case "datetime year to fraction(1)":
                fmt = "yyyy-MM-dd HH:mm:ss.S";
                break;
            case "datetime year to fraction(2)":
                fmt = "yyyy-MM-dd HH:mm:ss.SS";
                break;
            case "datetime year to fraction(3)":
                fmt = "yyyy-MM-dd HH:mm:ss.SSS";
                break;
            case "datetime year to fraction(4)":
                fmt = "yyyy-MM-dd HH:mm:ss.SSSS";
                break;
            case "datetime hour to second":
                fmt = "HH:mm:ss";
                break;
            default:
                fmt = "yyyy-MM-dd HH:mm:ss.SSSSS";
        }
        return fmt;
    }
}