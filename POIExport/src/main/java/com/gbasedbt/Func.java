package com.gbasedbt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public class Func {

    private static final Logger log = LoggerFactory.getLogger(Func.class);
    private static final String DIRVER_CLASSNAME = "com.gbasedbt.jdbc.Driver";
    private static final String PROP = System.getProperty("PROP","user");
    private static final String DBNAME = System.getProperty("DBNAME","testdb");
    private static final ResourceBundle bundle = ResourceBundle.getBundle(PROP);

    /**
     * 获取连接
     * @return
     */
    public static Connection getConn(){
        Connection connection = null;
        String ini_url        = getBundleString(bundle,"url","jdbc:gbasedbt-sqli://127.0.0.1:9088/testdb:GBASEDBTSERVER=gbase01;DB_LOCALE=zh_CN.utf8;");
        String ini_user       = getBundleString(bundle,"user","gbasedbt");
        String ini_pass       = getBundleString(bundle,"pass","GBase123$%");
        ini_url               = replaceGBase8sDbName(ini_url, DBNAME);
        log.info("URL地址: " + ini_url);

        try {
            Class.forName(DIRVER_CLASSNAME);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        try {
            if ("local".equalsIgnoreCase(ini_user) || "".equals(ini_user)){
                connection = DriverManager.getConnection(ini_url);
            } else {
                connection = DriverManager.getConnection(ini_url, ini_user, ini_pass);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return connection;
    }

    /**
     * 将date/timestamp/datetime数据类型转换成对应的格式
     * @param dt
     * @return
     */
    public static String getFormat(String dt){
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

    /**
     * 获取字符串配置，找不到则返回默认值
     */
    public static String getBundleString(ResourceBundle bundle, String key, String defaultValue) {
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

    public static boolean supportMysqlMode(Connection connection){
        boolean supportMysql = false;

        return supportMysql;
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
}
