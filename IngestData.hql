--  only s3 test to orc
DROP TABLE IF EXISTS zhangfx_final;
CREATE EXTERNAL TABLE zhangfx_final (trade_day DATE, 
    open float, high float, low float, close float, volume float, dividend float, split float,
    adj_Open float, adj_High float, adj_Low float, adj_Close float, adj_Volume float)
ROW FORMAT SERDE 'org.apache.hadoop.hive.serde2.OpenCSVSerde'
STORED AS orc
LOCATION 's3://zhangfx-mpcs53014/stocks_test/'
TBLPROPERTIES ("skip.header.line.count"="1");

select * from zhangfx_final limit 1;


-- only s3 test to default
DROP TABLE IF EXISTS zhangfx_final;
CREATE EXTERNAL TABLE zhangfx_final (trade_day DATE, 
    open float, high float, low float, close float, volume float, dividend float, split float,
    adj_Open float, adj_High float, adj_Low float, adj_Close float, adj_Volume float)
ROW FORMAT SERDE 'org.apache.hadoop.hive.serde2.OpenCSVSerde'
LOCATION 's3://zhangfx-mpcs53014/stocks_test/'
TBLPROPERTIES ("skip.header.line.count"="1");

select * from zhangfx_final limit 1;