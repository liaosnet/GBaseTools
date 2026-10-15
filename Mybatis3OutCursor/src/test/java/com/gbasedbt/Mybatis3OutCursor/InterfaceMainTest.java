package com.gbasedbt.Mybatis3OutCursor;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.gbasedbt.Mybatis3OutCursor.InterfaceMainMapper;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

/**
 * INTERFACE_MAIN 存储过程调用测试
 */
public class InterfaceMainTest {

    private static SqlSessionFactory sf;
    private static SqlSession sqlsession;
    private static InterfaceMainMapper interfaceMainMapper;
    static String resource = "mybatis-config.xml";

    public static void main(String[] args) throws Exception {

        InputStream inputStream = Resources.getResourceAsStream(resource);
        sf = new SqlSessionFactoryBuilder().build(inputStream);
        sqlsession = sf.openSession();
        interfaceMainMapper = sqlsession.getMapper(InterfaceMainMapper.class);

        // 调用入口存储过程：procna 为业务过程名（需带包名），indata 为输入数据包
        doInterfaceMain("FCBP", "1");

        sqlsession.close();
    }

    /**
     * 执行入口存储过程 INTERFACE_MAIN
     * @param procna 业务存储过程名称（需带包名，如 pkg_demo.query_student）
     * @param indata 输入数据包，格式 key1=value1|key2=value2
     */
    public static void doInterfaceMain(String procna, String indata) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("procna", procna);
        params.put("indata", indata);

        // 调用后，输出参数被写回到 params 中
        List<Map<String, Object>> resultRows = interfaceMainMapper.interface_main(params);

        System.out.println("==========================================");
        System.out.println("retCode  = " + params.get("retCode"));
        System.out.println("retParas = " + params.get("retParas"));
        System.out.println("outdata  = " + params.get("outdata"));

        // 游标结果集：优先取 OUT 参数 listnm，为空时取方法返回值
        // （不同驱动行为不同：Oracle 风格 refcursor 走 OUT 参数，
        //   Informix/GBase 风格把过程打开的游标直接作为结果集返回）
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> listnm = (List<Map<String, Object>>) params.get("listnm");
        if (listnm == null || listnm.isEmpty()) {
            listnm = resultRows;
        }

        System.out.println("listnm 行数 = " + (listnm == null ? 0 : listnm.size()));
        if (listnm != null) {
            for (Map<String, Object> row : listnm) {
                System.out.println(row);
            }
        }
    }

}
