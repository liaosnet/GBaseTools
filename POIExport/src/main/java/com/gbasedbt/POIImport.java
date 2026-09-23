package com.gbasedbt;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static com.gbasedbt.Func.*;

public class POIImport {

    private static final Logger log = LoggerFactory.getLogger(POIImport.class);
    private static final String EXCELFILE = System.getProperty("EXCELFILE","");
    private static final String TABNAME = System.getProperty("TABNAME","");
    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(
            Arrays.asList("csv", "xlsx", "xls")
    );

    public static void main(String[] args) throws ClassNotFoundException {

        String ini_excelfile  = "";
        if ("".equals(EXCELFILE)){
            log.error("没有输入要导入的文件！");
            System.exit(1);
        } else {
            ini_excelfile = EXCELFILE;
            log.info("要导入的文件为：" + ini_excelfile);
        }
        String fileNamePath = Paths.get(ini_excelfile).getFileName().toString();
        String importFileType = FilenameUtils.getExtension(ini_excelfile);
        if (! ALLOWED_EXTENSIONS.contains(importFileType.toLowerCase())){
            log.error("文件名后缀不匹配，仅限xlsx,xls,csv !");
        }
        String fileName = fileNamePath.replace("." + importFileType, "");
        log.debug("文件名：" + fileName + " ,后缀名：" + importFileType);

        // 从环境变量中读取表名，再次从文件名中读取表名
        String ini_tabname = null;
        if (! "".equals(TABNAME)){
            ini_tabname = TABNAME;
        }
        if (ini_tabname == null){
            ini_tabname = fileName;
        }

        try (Connection conn = getConn();
             InputStream fis = new FileInputStream(ini_excelfile)) {
            if ("csv".equalsIgnoreCase(importFileType)){
                long numImport = CsvImportUtil.importCsv(conn, fis, ini_tabname, fileNamePath);
                log.info("表：" + ini_tabname + ", 行数：" + numImport);
            } else {
                ExcelImportUtil.SheetResult result = ExcelImportUtil.importFirstSheet(conn, fis, ini_tabname);
                log.info("表：" + result.getTableName() + ", 行数：" + result.getRowCount());
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}
