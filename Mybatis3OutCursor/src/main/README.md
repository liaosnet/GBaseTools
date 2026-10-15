# GBase 8s数据库支持 Mybatis3 OUT/INOUT 游标  
Mybatis3中原生仅支持Oracle的OUT游标类型，此项目使用：  
`GBaseCursorOutParameterInterceptor`拦截`StatementHandler.parameterize`，  
代替标准注册逻辑——游标 OUT 参数交给`GBaseCursorTypeHandler.registerOutParameter`  
（内部调用驱动特有方法`IfxRegisterOutParameter(i, IFX_TYPE_CURSOR)`），  
其余 OUT/INOUT 参数仍按标准逻辑注册；  

## 使用方法  
### 1, 在`mybatis-config.xml`中plugins标签中增加interceptor  

示例：
```xml
    <!--
        游标 OUT 参数注册拦截器：
        代替标准 CallableStatementHandler 的 OUT 参数注册逻辑，
        使游标参数能通过驱动的 IfxRegisterOutParameter(i, IFX_TYPE_CURSOR) 注册
        （标准注册方式 GBase 驱动会报 "不明确的 java.sql.Type"）。
    -->
    <plugins>
        <plugin interceptor="com.gbasedbt.Mybatis3OutCursor.GBaseCursorOutParameterInterceptor"/>
    </plugins>
```
注意`mybatis-config.xml`中的标签顺序：   
```text
properties（属性引入）
settings（全局参数设置）
typeAliases（类型别名）
typeHandlers（类型处理器）
objectFactory（对象工厂）
objectWrapperFactory（对象包装工厂）
reflectorFactory（反射工厂）
plugins（插件）
environments（环境配置）
databaseIdProvider（数据库厂商标识）
mappers（映射器）
```

### 2, 映射文件xml中的OUT游标变量参数  
使用 typeHandler，去掉jdbcType，javaType使用ResultSet  
typeHandler使用自定义的`com.gbasedbt.Mybatis3OutCursor.GBaseCursorTypeHandler`  
示例：
```xml
    <select id="interface_main" resultType="java.util.Map" statementType="CALLABLE">
        { call interface_main(
            #{procna, mode=IN, javaType=java.lang.String, jdbcType=VARCHAR},
            #{indata, mode=IN, javaType=java.lang.String, jdbcType=CLOB},
            #{outdata, mode=OUT, javaType=java.lang.String, jdbcType=VARCHAR},

            <!-- 游标输出：使用 typeHandler，去掉 jdbcType -->
            <!-- typeHandler使用自定义的GBaseCursorTypeHandler -->
            #{listnm, mode=OUT,
              javaType=java.sql.ResultSet,
              resultMap=commListResult,
              typeHandler=com.gbasedbt.Mybatis3OutCursor.GBaseCursorTypeHandler},

            #{retCode, mode=OUT, javaType=java.lang.String, jdbcType=VARCHAR},
            #{retParas, mode=OUT, javaType=java.lang.String, jdbcType=VARCHAR}
        ) }
    </select>
```
