package com.gbasedbt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static com.gbasedbt.Func.*;

public class POIExport {

    private static final Logger log = LoggerFactory.getLogger(POIExport.class);
    private static final String SQL = System.getProperty("SQL","select * from systables");
    private static final String SQLFILE = System.getProperty("SQLFILE","");
    private static final String OUTDIR = System.getProperty("OUTDIR","/tmp");

    public static void main(String[] args) throws Exception {

        String dir_name       = OUTDIR;
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String os = System.getProperty("os.name").toLowerCase();
        String outfilename = "";
        if (os.contains("win")){
            if ("/tmp".equals(dir_name)){
                dir_name = "D:\\";
            }
            outfilename = (dir_name.endsWith("\\")?dir_name:dir_name+"\\") + "POIExport_win_";
        } else {
            outfilename = (dir_name.endsWith("/")?dir_name:dir_name+"/") + "POIExport_lnx_";
        }
        outfilename = outfilename + now.format(formatter) + ".xlsx";
        log.info("导出文件名: " + outfilename);

        String sql = "";
        if ("".equals(SQLFILE)){
            sql = SQL;
            log.info("当前输入为SQL语句：" + sql);
        } else {
            sql = readFileToString(SQLFILE);
            log.info("当前使用SQL文件，内容为：" + sql);
        }

        try (Connection conn = getConn();
            FileOutputStream fos = new FileOutputStream(outfilename)) {

            PoiExcelExportUtil.export(conn, sql, fos);
            log.info("Excel 导出成功！");
        }
    }
}
