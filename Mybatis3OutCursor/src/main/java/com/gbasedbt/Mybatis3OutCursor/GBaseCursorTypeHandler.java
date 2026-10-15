package com.demo.handler;

import com.gbasedbt.jdbc.IfxCallableStatement;
import com.gbasedbt.lang.IfxTypes;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.*;

/**
 * 解决 GBase 8s / Informix 存储过程游标 OUT 参数报错：
 * "不明确的 java.sql.Type，请使用 IfxRegisterOutParameter()"
 *
 * 说明：
 * 1. 标准版 MyBatis 3.5.x 的 TypeHandler/BaseTypeHandler 没有
 *    registerOutParameter 方法，因此下面的 registerOutParameter 不是覆写，
 *    由 GBaseCursorOutParameterInterceptor 在 parameterize 阶段调用。
 *    若使用 GBase 定制版 MyBatis（TypeHandler 自带 registerOutParameter
 *    钩子），此方法签名完全一致，可加回 @Override 并去掉拦截器。
 * 2. GBase 8s 驱动使用 com.gbasedbt.jdbc / com.gbasedbt.lang 包；
 *    若使用 IBM Informix 驱动（ifxjdbc），import 替换为
 *    com.informix.jdbc.IfxCallableStatement / com.informix.lang.IfxTypes 即可。
 */
public class InformixCursorTypeHandler extends BaseTypeHandler<ResultSet> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, ResultSet parameter, JdbcType jdbcType) throws SQLException {
        throw new UnsupportedOperationException("InformixCursorTypeHandler 仅用于处理 OUT 游标参数");
    }

    /**
     * 【核心方法】注册 OUT 参数
     * 其实能走到这里，说明实际上已经是GBase 8s环境了（自定义handle进来的）
     */
    public void registerOutParameter(CallableStatement cs, int parameterIndex, JdbcType jdbcType) throws SQLException {
        IfxCallableStatement ifxCs = null;

        // 【核心】穿透 MyBatis 代理和连接池代理，拿到底层原生对象
        try {
            if (cs.isWrapperFor(IfxCallableStatement.class)) {
                ifxCs = cs.unwrap(IfxCallableStatement.class);
            }
        } catch (SQLException e) {
            // 忽略解包异常，走兜底逻辑
        }
        // 如果驱动是3.6.3 （即3.6.5之前），还不支持isWrapperFor和unwrap，考虑使用元数据的方式获取是否是GBase 8s
        if (ifxCs == null){
            // cs 是你从 MyBatis 获取到的 CallableStatement
            MetaObject metaObject = SystemMetaObject.forObject(cs);

            // MyBatis 的 StatementHandler 代理通常将真实对象存放在 delegate 字段
            // 如果有多层代理，可能需要连续解包
            Object realStmt = cs;
            while (realStmt != null && !(realStmt instanceof IfxCallableStatement)) {
                MetaObject mo = SystemMetaObject.forObject(realStmt);
                if (mo.hasGetter("delegate")) {
                    realStmt = mo.getValue("delegate");
                } else if (mo.hasGetter("stmt")) {
                    realStmt = mo.getValue("stmt");
                } else if (mo.hasGetter("h")) {
                    // JDK 动态代理的 InvocationHandler 中的目标对象
                    Object handler = mo.getValue("h");
                    MetaObject hMo = SystemMetaObject.forObject(handler);
                    // 使用的是statement
                    realStmt = hMo.hasGetter("statement") ? hMo.getValue("statement") : null;
                } else {
                    break; // 无法继续穿透
                }
            }
            if (realStmt instanceof IfxCallableStatement) {
                ifxCs = (IfxCallableStatement) realStmt;
            }
        }

        // 如果解包成功，说明确实是 GBase 8s 环境
        if (ifxCs != null) {
            ifxCs.IfxRegisterOutParameter(parameterIndex, IfxTypes.IFX_TYPE_CURSOR);
        } else {
            // 兼容非 GBase环境
            cs.registerOutParameter(parameterIndex, java.sql.Types.OTHER);
        }
    }

    @Override
    public ResultSet getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return null;
    }

    @Override
    public ResultSet getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return null;
    }

    /**
     * 【核心方法】从 CallableStatement 中获取游标返回的 ResultSet
     */
    @Override
    public ResultSet getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return (ResultSet) cs.getObject(columnIndex);
    }

}
