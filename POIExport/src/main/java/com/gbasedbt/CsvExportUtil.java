package com.gbasedbt;

import com.opencsv.CSVWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;

/**
 * CSV 导出工具类
 */
public class CsvExportUtil {

    private static final Logger log = LoggerFactory.getLogger(CsvExportUtil.class);

    /**
     * 将 SQL 查询结果导出为 CSV
     *
     * @param connection   数据库连接
     * @param sql          查询 SQL
     * @param outputStream 输出流
     * @return 导出的数据行数
     */
    public static long exportSql(Connection connection, String sql,
                                 OutputStream outputStream) throws Exception {
        long rowCount = 0;

        try (Statement stmt = connection.createStatement(
                ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
             ResultSet rs = stmt.executeQuery(sql)) {

            // 流式读取
            // stmt.setFetchSize(Integer.MIN_VALUE);

            try (CSVWriter writer = new CSVWriter(
                    new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {

                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                // 写入表头
                String[] headers = new String[columnCount];
                for (int i = 1; i <= columnCount; i++) {
                    headers[i - 1] = metaData.getColumnLabel(i);
                }
                writer.writeNext(headers);

                // 写入数据行
                String[] rowData = new String[columnCount];
                while (rs.next()) {
                    for (int i = 1; i <= columnCount; i++) {
                        Object val = rs.getObject(i);
                        rowData[i - 1] = (val != null) ? val.toString() : null;
                    }
                    writer.writeNext(rowData);
                    rowCount++;
                }
            }
        }
        return rowCount;
    }
}