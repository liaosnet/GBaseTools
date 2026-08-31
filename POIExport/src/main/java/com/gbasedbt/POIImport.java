package com.gbasedbt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;

import static com.gbasedbt.Func.*;

public class POIImport {

    private static final Logger log = LoggerFactory.getLogger(POIImport.class);
    private static final String EXCELFILE = System.getProperty("EXCELFILE","");
    private static final String TABNAME = System.getProperty("TABNAME","");

    public static void main(String[] args) throws ClassNotFoundException {


        String ini_excelfile  = "";
        if ("".equals(EXCELFILE)){
            log.error("没有输入要导入的excel文件！");
            System.exit(1);
        } else {
            ini_excelfile = EXCELFILE;
            log.info("要导入的excel文件为：" + ini_excelfile);
        }

        String ini_tabname = null;
        if (! "".equals(TABNAME)){
            ini_tabname = TABNAME;
        }

        try (Connection conn = getConn();
             InputStream fis = new FileInputStream(ini_excelfile)) {
            ExcelImportUtil.SheetResult result = ExcelImportUtil.importFirstSheet(conn, fis, ini_tabname);
            log.info("表：" + result.getTableName() + ", 行数：" + result.getRowCount());
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}
