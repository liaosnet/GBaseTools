package com.gbasedbt;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Date;

public class DynamicExcelExporter {

    private static final Logger log = LoggerFactory.getLogger(DynamicExcelExporter.class);

    public static void export(Connection connection, String sql, OutputStream out) throws Exception {
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery(sql);
        ResultSetMetaData metaData = resultSet.getMetaData();
        int columnCount = metaData.getColumnCount();

        // 1. 动态构建表头
        List<List<String>> head = new ArrayList<>();
        for (int i = 1; i <= columnCount; i++) {
            List<String> columnHead = new ArrayList<>();
            columnHead.add(metaData.getColumnLabel(i));
            head.add(columnHead);
        }

        // 2. 初始化 Writer
        String sheetname = "".equals(metaData.getTableName(1))?"多表关联":metaData.getTableName(1);
        ExcelWriter excelWriter = EasyExcel.write(out).build();
        WriteSheet writeSheet = EasyExcel.writerSheet(sheetname).head(head).build();

        try {
            int batchSize = 1000;
            List<List<Object>> dataList = new ArrayList<>(batchSize);
            int currentRow = 0;

            while (resultSet.next()) {
                List<Object> rowData = new ArrayList<>(columnCount);
                for (int i = 1; i <= columnCount; i++) {
                    Object value = resultSet.getObject(i);

                    // 【核心修复】：拦截日期类型，手动格式化为字符串，
                    // 额外处理一下（实际上 instanceof Date 已经涵盖了 Timestamp）
                    if (value instanceof Date) {
                        String dateType = metaData.getColumnTypeName(i);
                        SimpleDateFormat sdf = new SimpleDateFormat(getFormat(dateType));
                        value = sdf.format((Date) value);
                    }

                    rowData.add((value != null)?value.toString():"");
                }
                dataList.add(rowData);
                currentRow++;

                if (currentRow % batchSize == 0) {
                    excelWriter.write(dataList, writeSheet);
                    dataList.clear();
                }
            }

            if (!dataList.isEmpty()) {
                excelWriter.write(dataList, writeSheet);
            }
            log.info("导出行数：" + currentRow);
        } finally {
            excelWriter.finish();
            resultSet.close();
            statement.close();
        }
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