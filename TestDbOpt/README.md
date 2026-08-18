# TestDbOpt  
用于测试数据库操作，可绑定变量  
需要jdk1.8或者以上环境  

## JDBC驱动  
将需要的GBase 8s JDBC驱动放于当前目录，名称类型为gbasedbtjdbc_3.6.5_2L1_1.jar  

## user.properties配置文件  
url, user, pass 用于数据库连接的url, user, pass常规参数，classsname固定为com.gbasedbt.jdbc.Driver  
sqlstr     具体语句，可使使用绑定变量，变量个数需与params中的一致  
printdata  用于查询中是否打印具体返回结果  
params     以空格分隔多个绑定变量参数；每个参数以|分隔，前面为类型（可接受INT|LONG|FLOAT|STRING|DATE|DATETIME等），后面为具体参数值。

## 运行  
执行run.sh 运行  

