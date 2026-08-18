package com.gbasedbt;

import java.sql.*;
import java.text.ParseException;
import java.util.*;

public class TestDbOpt {
    private static final String DIRVER_CLASSNAME = "com.gbasedbt.jdbc.Driver";

    private static final String PROP = System.getProperty("prop","user");

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
     * 获取整数配置，找不到或格式错误则返回默认值
     */
    public static int getBundleInt(String key, int defaultValue) {
        try {
            // 注意：getString 获取后需要转换成 int
            return Integer.parseInt(bundle.getString(key));
        } catch (MissingResourceException | NumberFormatException e) {
            // 同时捕获：1. 找不到键的异常  2. 值不是合法数字的异常
            return defaultValue;
        }
    }

    public static void main(String[] args) throws SQLException, ClassNotFoundException, InterruptedException {

        String ini_sqlstr     = getBundleString("sqlstr","SeLeCt 1 from dual");
        String ini_url        = getBundleString("url","jdbc:gbasedbt-sqli://127.0.0.1:9088/testdb:GBASEDBTSERVER=gbase01;DB_LOCALE=zh_CN.utf8;");
        String ini_user       = getBundleString("user","gbasedbt");
        String ini_pass       = getBundleString("pass","GBase123$%");
        String ini_printdata  = getBundleString("printdata","false");
        String ini_params     = getBundleString("params","");
        int    ini_sleepms    = getBundleInt("sleepms",2000);

        // 读取配置文件变量处理
        boolean isQuery = false;
        if (ini_sqlstr.trim().toLowerCase().startsWith("select")){
            isQuery = true;
        }
        // 获取变量数
        int numparams = ini_sqlstr.length() - ini_sqlstr.replace("?","").length();
        List<String> param_list = splitRespectingQuotes(ini_params);
        int numrows = 0;
        Connection connection = null;
        Class.forName(DIRVER_CLASSNAME);
        long connbegin = System.currentTimeMillis();
        connection = DriverManager.getConnection(ini_url,ini_user,ini_pass);
        long connfinish = System.currentTimeMillis();

        System.out.println("数据库的版本号：" + connection.getMetaData().getDatabaseProductName()
                + " Verion " + connection.getMetaData().getDatabaseProductVersion());
        System.out.println("JDBC驱动版本号：" + connection.getMetaData().getDriverVersion());
        System.out.println("JDBC连接字符串：" + connection.getMetaData().getURL());
        long begintime = System.currentTimeMillis();
        System.out.println("成功连接到数据库! 用时：" + (connfinish - connbegin) + " (ms)。");
        System.out.println("===============================================================\n###开始的时间戳：" + begintime);
        System.out.println("待执行的SQL语句：" + ini_sqlstr);
        System.out.println("语句中的变量数（params中的变量大于该值的变量将被忽略）为：" + numparams);

        PreparedStatement preparedStatement = connection.prepareStatement(ini_sqlstr);

        if (isQuery){
            numrows = execQuery(preparedStatement,ini_printdata,numparams,param_list);
        } else {
            numrows = execUpdate(preparedStatement,numparams,param_list);
        }

        long finishtime = System.currentTimeMillis();
        System.out.println("返回或者处理数据行数：" + numrows);
        System.out.println("###完成的时间戳：" + finishtime);
        System.out.println("共用时(ms)：" + (finishtime - begintime));

        Thread.sleep(ini_sleepms);

        printSessEnv(connection);

    }

    /**
     * 仅打印环境变量信息
     * @param connection
     * @throws SQLException
     */
    public static void printSessEnv(Connection connection) throws SQLException {
        System.out.println("\n\n打印当前会话环境变量\n===============================================================");
        String ini_sqlstr = "database sysmaster";
        PreparedStatement preparedStatement = connection.prepareStatement(ini_sqlstr);
        System.out.println("设置当前数据库为：sysmaster");
        preparedStatement.executeUpdate();

        ini_sqlstr = "select * from sysenvses  where envses_sid = DBINFO('SESSIONID')";
        preparedStatement = connection.prepareStatement(ini_sqlstr);

        ResultSet resultSet = preparedStatement.executeQuery();
        while (resultSet.next()){
            System.out.printf("环境变量序号: %2s 变量名称: %-20s 变量值: %-20s\n",
                    resultSet.getObject(2),
                    resultSet.getString(3).trim(),
                    resultSet.getString(4).trim()
            );
        }
    }

    /**
     * 执行查询操作，按需返回数据
     * @param preparedStatement
     * @param ini_printdata
     * @param numparams
     * @param param_list
     * @return
     * @throws SQLException
     */
    public static int execQuery(PreparedStatement preparedStatement,
                                String ini_printdata,
                                int numparams,
                                List<String> param_list) throws SQLException {
        int numrows = 0;
        int numcolumns = 0;
        if (numparams > 0){
            System.out.println("绑定变量（处理时去除开始及结束的引号）：" + param_list.toString());
            setParams(preparedStatement, numparams, param_list);
        }
        ResultSet resultSet = preparedStatement.executeQuery();
        if ("true".equalsIgnoreCase(ini_printdata)){
            ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
            numcolumns = resultSetMetaData.getColumnCount();
        }
        while (resultSet.next()){
            numrows++;
            if ("true".equalsIgnoreCase(ini_printdata)){
                System.out.println("=== 行: " + numrows + " ===========");
                for (int i=1;i<=numcolumns;i++){
                    System.out.println(resultSet.getObject(i));
                }
            }
        }
        return numrows;
    }


    /**
     * 执行更新操作
     * @param preparedStatement
     * @param numparams
     * @param param_list
     * @return
     * @throws SQLException
     */
    public static int execUpdate(PreparedStatement preparedStatement,
                                 int numparams,
                                 List<String> param_list) throws SQLException {
        int numrows = 0;
        if (numparams > 0){
            System.out.println("绑定变量（处理时去除开始及结束的引号）：" + param_list.toString());
            setParams(preparedStatement, numparams, param_list);
        }
        numrows = preparedStatement.executeUpdate();

        return numrows;
    }

    /**
     * 设置变量
     * @param preparedStatement
     * @param param_list
     * @throws SQLException
     * @throws ParseException
     */
    public static void setParams(PreparedStatement preparedStatement,
                                 int numparams,
                                 List<String> param_list) throws SQLException {
        for(int i=0; i<param_list.size() && i<numparams; i++){
            List<String> param = Arrays.asList(param_list.get(i).split("\\|"));
            String tmpstr = param.get(1);
            if ((tmpstr.startsWith("\"")  && tmpstr.endsWith("\"")) ||
                    (tmpstr.startsWith("\'")  && tmpstr.endsWith("\'"))){
                tmpstr = tmpstr.substring(1,tmpstr.length() - 1);
            }
            // 数据类型处理
            switch (param.get(0).toUpperCase()){
                case "INT":
                    preparedStatement.setInt(i+1,Integer.valueOf(tmpstr));
                    break;
                case "BIGINT":
                case "LONG":
                    preparedStatement.setLong(i+1,Long.valueOf(tmpstr));
                    break;
                case "FLOAT":
                    preparedStatement.setFloat(i+1,Float.valueOf(tmpstr));
                    break;
                case "STRING":
                case "DATE":
                    preparedStatement.setString(i+1,tmpstr);
                    break;
                case "DATETIME":
                    preparedStatement.setTimestamp(i+1,Timestamp.valueOf(tmpstr));
                    break;
                case "NULL":
                    preparedStatement.setNull(i+1,Types.CHAR);
                    break;
                default:
                    preparedStatement.setObject(i+1,tmpstr);
            }
            // DEBUG: 打印变量值
            // System.out.println(i + "\t" + param.get(0).toUpperCase() + "\t" + tmpstr);
        }
    }

    /**
     * 按\\s+分隔，但保留引号内\\s+不被分隔。
     * @param input
     * @return
     */
    public static List<String> splitRespectingQuotes(String input) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        boolean inSingle = false; // 是否在单引号内
        boolean inDouble = false; // 是否在双引号内

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            // 1. 处理转义字符：遇到 \ 且后面还有字符，直接原样保留 \' 或 \"
            if (c == '\\' && i + 1 < input.length()) {
                current.append(c);           // 追加 \
                current.append(input.charAt(i + 1)); // 追加被转义的字符
                i++; // 跳过下一个字符，避免被当成普通的引号处理
                continue;
            }

            // 2. 处理双引号
            if (c == '"') {
                inDouble = !inDouble; // 切换双引号状态
                current.append(c);    // 保留双引号字符
            }
            // 3. 处理单引号
            else if (c == '\'') {
                inSingle = !inSingle; // 切换单引号状态
                current.append(c);    // 保留单引号字符
            }
            // 4. 处理空格（分隔符）
            else if (c == ' ' && !inSingle && !inDouble) {
                // 只有在既不在单引号、也不在双引号内时，空格才作为分隔符
                if (current.length() > 0) {
                    result.add(current.toString());
                    current.setLength(0); // 清空 StringBuilder
                }
            }
            // 5. 处理普通字符
            else {
                current.append(c);
            }
        }

        // 6. 添加最后一个 token
        if (current.length() > 0) {
            result.add(current.toString());
        }

        return result;
    }
}

