# Final Project

By Fengxue Zhang

## 1. Functionality

User type in the stock name on the webpage, the application return the historical ***Sharpe Ratio*** (reward-to-variability ratio), one of the most popular measure for the performance of mutual funds proposed in (Sharpe [1966](http://web.stanford.edu/~wfsharpe/art/sr/SR.htm#Sharpe66)). Recently the stock market has been gainning popularity for individual investors. I hope the application could provide the ***query service for single stock*** first, and develop the extensive service in the future work.

Sharpe ratio is a risk-adjusted return on an investment or portfolio. Usually, any Sharpe ratio greater than 1.0 is considered acceptable to good by investors. A ratio higher than 2.0 is rated as very good. A ratio of 3.0 or higher is considered excellent. A ratio under 1.0 is considered sub-optimal. The formula is:

$$Sharpe\ Ratio = \frac{R_p-R_f}{\sigma_p}$$

where:

- $R_p$ is the exptected return on the asset or portfolio.
- $R_f$ is the risk-free rate of return.
- $\sigma_p$ is the risk (the standard deviation of returns) of the asset or portfolio.

In practice, I use the corresponding index as the risk-free baseline, and the historical return as a substituition of the expected return.

## 2. Data Acquirement

- I download the list of all stocks from NASDAQ official FTP Directory:
  
    ```shell
    ftp://ftp.nasdaqtrader.com/symboldirectory.
    ```

    where two files *nasdaqlisted.txt* and *otherlisted.txt* contain the entire list of tradeable symbols. The list is updated on a daily basis.

- I used the Quandl Student Access to obtain all the needed End Of Day price for each stock. On the name node, I conduct the following commands to establish the necessary environment to acquire the dataset.

    ```shell
    # enter personal folder
    cd /home/hadoop/zhangfx
    # create virtual env
    python -m venv ./
    # activate python vritual environment
    source ./bin/activate
    # install quandl
    pip3 install quandl
    # fetch stock list from nasdaq ftp server to name node
    wget ftp://ftp.nasdaqtrader.com/symboldirectory/nasdaqlisted.txt
    # execute the python script to load latest data from quandle
    python3 data_ingestion.py -s3 --token <quandl token>
    ```

    The total size for all nasdaq EOD historical data is about 1.1GB.

- I used the following hive command to create a hive table to manage all csv files stored in S3 ***zhangfx_final*** and ***zhangfx_final_index***:
  
    ```sql
    DROP TABLE IF EXISTS zhangfx_final;
    CREATE EXTERNAL TABLE zhangfx_final (trade_day DATE,
        open float, high float, low float,
        close float, volume float, dividend float, split float,
        adj_Open float, adj_High float, adj_Low float, adj_Close float, adj_Volume float)
    ROW FORMAT SERDE 'org.apache.hadoop.hive.serde2.OpenCSVSerde'
    LOCATION 's3://zhangfx-mpcs53014/stocks/'
    TBLPROPERTIES ("skip.header.line.count"="1");

    DROP TABLE IF EXISTS zhangfx_final_index;
    CREATE EXTERNAL TABLE zhangfx_final_index (trade_day Date,
        Index_Value float, High float, Low float,
        Total_Market_Value float, Dividend_Market_Value float)
    ROW FORMAT SERDE 'org.apache.hadoop.hive.serde2.OpenCSVSerde'
    LOCATION 's3://zhangfx-mpcs53014/indices/'
    TBLPROPERTIES ("skip.header.line.count"="1");

    ```

## 2. Batch Layer & Serving Layer

First, create a new table in hbase.

```shell
# one table for both index and stocks
create 'zhangfx_final_summary', 'result'
```

Second, use hive to extract all EOD of stocks and index value of index. Use the input_file_name + date as key.

```sql
-- create external table
create external table zhangfx_final_summary (
    stock_name      string,
    num_days        bigint,
    value_avg       float,
    value_std       float,
    start_day_index float,
    end_day_index   float,
    start_day_stock float,
    end_day_stock   float
    ) STORED BY 'org.apache.hadoop.hive.hbase.HBaseStorageHandler'
WITH SERDEPROPERTIES ('hbase.columns.mapping' = ':key,
            result:num_days,
            result:value_avg,
            result:value_std,
            result:start_day_index,
            result:end_day_index,
            result:start_day_stock,
            result:end_day_stock
            ')
TBLPROPERTIES ('hbase.table.name' = 'zhangfx_final_summary');

-- create intermediate
create table zhangfx_final_view (
    stock_name      string,
    trade_day       Date,
    value_of_day    float,
    index_of_day    float
) STORED AS ORC;

-- insert data from stocks
insert overwrite table zhangfx_final_view
    select split(split( zhangfx_final.INPUT__FILE__NAME, '/')[4],'[.]')[0] as stock_name,
        zhangfx_final.trade_day as trade_day,
        zhangfx_final.adj_close as value_of_day,
        zhangfx_final_index.Index_Value as index_of_day
    from zhangfx_final join zhangfx_final_index on zhangfx_final.trade_day = zhangfx_final_index.trade_day
    where zhangfx_final.adj_Close != '' and zhangfx_final_index.Index_Value != '';

-- insert batch view into hbase
insert overwrite table zhangfx_final_summary
    select a.stock_name as stock_name, c.num_days as num_days,
    c.value_avg as value_avg, c.value_std as value_std,
    a.value_of_day as start_day_stock, a.index_of_day as start_day_index,
    b.value_of_day as end_day_stock, b.index_of_day as end_day_index

    from zhangfx_final_view as a, zhangfx_final_view as b, (select stock_name, count(trade_day) as num_days,
                                    min(trade_day) as start_day, max(trade_day) as end_day,
                                    avg(value_of_day) as value_avg, std(value_of_day) as value_std
                                    from zhangfx_final_view group by stock_name) as c
    where (a.trade_day = c.start_day and b.trade_day = c.end_day and a.stock_name = c.stock_name and b.stock_name = c.stock_name);
```

## 3. Webapplication

