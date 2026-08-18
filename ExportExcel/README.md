# ExportExcel  
用于从数据库中导出结果，保存为excel文档  
需要jre1.8或者以上运行环境  

## JDBC驱动  
将需要的GBase 8s JDBC驱动放于lib目录下，名称类型示例：gbasedbtjdbc_3.6.5_2L1_1.jar  

注意：按实际版本替换lib目录下的`gbasedbtjdbc*.jar`文件  

## user.properties配置文件  
url, user, pass 用于数据库连接的url, user, pass常规参数，classsname固定为com.gbasedbt.jdbc.Driver  

## 运行  
执行run.sh 库名 SQL文件路径|SQL语句  

注意：  
建议使用 $'SQL语句' 的写法用于保留单引号内的特殊字符不处理，但 ' 本身应当使用 \' 替换，或者使用双引号。  

示例：  
1，执行SQL文件  
```text
bash run.sh testdb 1.sql  
```

2，执行SQL  
```text
bash run.sh testdb $'select `tabid`,`tabname` from `systables` where `tabname` like \'sys%\';'
```

结果存放于/tmp目录下，文件名：/tmp/ExportExcel_系统类型_日期时间到秒.xlsx  
