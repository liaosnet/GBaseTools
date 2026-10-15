package com.gbasedbt.Mybatis3OutCursor;

import java.sql.CallableStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;

import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;

/**
 * 游标 OUT 参数注册拦截器
 * （标准版 MyBatis 下替代 GBase 定制版的 registerOutParameter 钩子）
 *
 * 标准版 CallableStatementHandler 注册 OUT 参数时：
 *   1. 强制要求参数上有 jdbcType（游标参数不写 jdbcType 会报
 *      "The JDBC Type must be specified for output parameter"）；
 *   2. 只能调用标准的 cs.registerOutParameter(i, jdbcType.TYPE_CODE)，
 *      而 GBase 8s驱动对游标 OUT 参数会报 "不明确的 java.sql.Type，请使用 IfxRegisterOutParameter()"。
 *
 * 本拦截器拦截 StatementHandler.parameterize：
 *   - 非存储过程调用：直接走标准逻辑；
 *   - 存储过程调用：代替标准注册逻辑，游标 OUT 参数交给
 *     GBaseCursorTypeHandler.registerOutParameter（内部使用驱动特有方法 IfxRegisterOutParameter(i, IFX_TYPE_CURSOR)），
 *     其余 OUT/INOUT 参数 仍按标准逻辑注册，随后设置 IN/INOUT 参数值。
 *
 * 若项目中使用的是 GBase 8s 定制版 MyBatis（TypeHandler 自带 registerOutParameter 钩子），
 * 可去掉本拦截器及 mybatis-config.xml 中的plugins 配置。
 */
@Intercepts(@Signature(type = StatementHandler.class, method = "parameterize", args = {Statement.class}))
public class GBaseCursorOutParameterInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Statement statement = (Statement) invocation.getArgs()[0];
        if (!(statement instanceof CallableStatement)) {
            // 非存储过程调用，走标准逻辑
            return invocation.proceed();
        }

        StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
        CallableStatement cs = (CallableStatement) statement;
        BoundSql boundSql = statementHandler.getBoundSql();
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();

        // 注册 OUT/INOUT 参数：与标准 CallableStatementHandler 逻辑一致，
        // 仅游标参数改走 InformixCursorTypeHandler 的驱动特有注册方法
        for (int i = 0; i < parameterMappings.size(); i++) {
            ParameterMapping parameterMapping = parameterMappings.get(i);
            if (parameterMapping.getMode() == ParameterMode.OUT
                    || parameterMapping.getMode() == ParameterMode.INOUT) {
                if (parameterMapping.getTypeHandler() instanceof GBaseCursorTypeHandler) {
                    // 游标 OUT 参数：IfxRegisterOutParameter(i, IFX_TYPE_CURSOR)
                    ((GBaseCursorTypeHandler) parameterMapping.getTypeHandler())
                            .registerOutParameter(cs, i + 1, parameterMapping.getJdbcType());
                } else if (parameterMapping.getJdbcType() != null) {
                    cs.registerOutParameter(i + 1, parameterMapping.getJdbcType().TYPE_CODE);
                } else {
                    // 与标准版 CallableStatementHandler 的报错保持一致
                    throw new ExecutorException("The JDBC Type must be specified for output parameter.  Parameter: "
                            + parameterMapping.getProperty());
                }
            }
        }

        // 设置 IN/INOUT 参数值（等价于标准 parameterize 中的 parameterHandler.setParameters）
        statementHandler.getParameterHandler().setParameters(cs);
        return null;
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // 无额外配置
    }
}
