package com.gbasedbt.Mybatis3OutCursor;

import java.util.List;
import java.util.Map;

/**
 * INTERFACE_MAIN 入口存储过程调用接口
 */
public interface InterfaceMainMapper {

    /**
     * 调用入口存储过程 INTERFACE_MAIN（对应 mappers/InterfaceMainMapper.xml）
     *
     * @param params 输入参数 Map，键与 XML 中的 #{...} 属性名一致：
     *               procna  - 存储过程名称，需带包名，如 pkg_demo.query_student（必填）
     *               indata  - 输入数据包，格式 key1=value1|key2=value2
     *               调用结束后，输出参数会被写回同一 Map：
     *               outdata  - 输出数据包
     *               listnm   - 游标结果集 List&lt;Map&lt;String,Object&gt;&gt;
     *               retCode  - 返回码 0:失败 1:成功
     *               retParas - 错误信息or参数
     * @return 游标数据行列表（部分驱动把过程打开的游标直接作为结果集返回，
     *         此时返回值即为游标数据；否则从 params.get("listnm") 获取）
     */
    List<Map<String, Object>> interface_main(Map<String, Object> params);

}
