## 制品:
```
mvn -U clean package -Prelease -Dmaven.test.skip=true
```

```
dolphinscheduler-dist/target/apache-dolphinscheduler-${latest.release.version}-bin.tar.gz: DolphinScheduler 二进制包
dolphinscheduler-dist/target/apache-dolphinscheduler-${latest.release.version}-src.tar.gz: DolphinScheduler 源代码包
```

在win10系统本地环境开发调试: 

https://blog.csdn.net/qq_24760381/article/details/124489630


## 说明

1.增加“磁盘可用容量”监控

2.增加对旧的实例处理（可选）

3.对批量删除重试多次的实例，日志没法全删的情况做了修复

