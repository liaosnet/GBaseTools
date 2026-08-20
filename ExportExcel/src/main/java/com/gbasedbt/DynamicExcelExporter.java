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
                    if (value instanceof Timestamp) {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSS");
                        value = sdf.format((Timestamp) value);
                    } else if (value instanceof java.util.Date) {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                        value = sdf.format((java.util.Date) value);
                    }

                    rowData.add(value);
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
}