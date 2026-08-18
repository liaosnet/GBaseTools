package com.gbasedbt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public class ExportExcel {

    private static final Logger log = LoggerFactory.getLogger(ExportExcel.class);
    private static final String DIRVER_CLASSNAME = "com.gbasedbt.jdbc.Driver";
    private static final String PROP = System.getProperty("prop","user");
    private static final String SQL = System.getProperty("sql","select 1 from dual");
    private static final String SQLFILE = System.getProperty("sqlfile","");
    private static final String DBNAME = System.getProperty("db","testdb");
    private static final ResourceBundle bundle = ResourceBundle.getBundle(PROP);

    /**
     * 获取字符串配置，找不到则返回默认值
     */
    public static String getBundleString(String key, String defaultValue) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            // 捕获到找不到资源的异常，返回默认值
            return defaultValue;
        }
    }

    /**
     * 动态替换 GBase 8s JDBC URL 中的数据库名
     * @param originalUrl 原始 JDBC URL
     * @param newDbName   新的数据库名
     * @return 替换后的 URL
     */
    public static String replaceGBase8sDbName(String originalUrl, String newDbName) {
        if (originalUrl == null || !originalUrl.startsWith("jdbc:gbasedbt-sqli:")) {
            throw new IllegalArgumentException("非 GBase 8s JDBC URL 格式");
        }
        // 正则: 匹配 jdbc:gbasedbt-sqli: + 可选的主机端口 + / + 数据库名 + 后缀
        String regex = "(jdbc:gbasedbt-sqli:)(//[^/]+)?(/)[^:]+(:.*)";
        return originalUrl.replaceFirst(regex, "$1$2$3" + newDbName + "$4");
    }

    /**
     * 读取sql文件的内容
     * @param fileName
     * @return
     */
    public static String readFileToString(String fileName) {
        String encoding = "UTF-8";
        File file = new File(fileName);
        Long filelength = file.length();
        byte[] filecontent = new byte[filelength.intValue()];
        FileInputStream in=null;
        try {
            in = new FileInputStream(file);
            in.read(filecontent);
            return new String(filecontent, encoding);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                in.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws ClassNotFoundException {
        String ini_url        = getBundleString("url","jdbc:gbasedbt-sqli://127.0.0.1:9088/testdb:GBASEDBTSERVER=gbase01;DB_LOCALE=zh_CN.utf8;");
        String ini_user       = getBundleString("user","gbasedbt");
        String ini_pass       = getBundleString("pass","GBase123$%");

        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

        String os = System.getProperty("os.name").toLowerCase();
        String outfilename = "";
        if (os.contains("win")){
            outfilename = "D:\\ExportExcel_win_";
        } else {
            outfilename = "/tmp/ExportExcel_lnx_";
        }
        outfilename = outfilename + now.format(formatter) + ".xlsx";
        log.info("导出文件名: " + outfilename);

        ini_url = replaceGBase8sDbName(ini_url, DBNAME);
        log.info("URL地址: " + ini_url);

        String sql = "";
        if ("".equals(SQLFILE)){
            sql = SQL;
            log.info("当前输入为SQL语句：" + sql);
        } else {
            sql = readFileToString(SQLFILE);
            log.info("当前使用SQL文件，内容为：" + sql);
        }

        Class.forName(DIRVER_CLASSNAME);
        if ("LOCAL".equalsIgnoreCase(ini_user)){
            try (Connection conn = DriverManager.getConnection(ini_url);
                 FileOutputStream fos = new FileOutputStream(outfilename)) {

                // 直接调用工具类完成导出
                DynamicExcelExporter.export(conn, sql, fos);
                log.info("Excel 导出成功！");

            } catch (Exception e) {
                e.printStackTrace();
            }

        } else {
            try (Connection conn = DriverManager.getConnection(ini_url, ini_user, ini_pass);
                 FileOutputStream fos = new FileOutputStream(outfilename)) {

                // 直接调用工具类完成导出
                DynamicExcelExporter.export(conn, sql, fos);
                log.info("Excel 导出成功！");

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
