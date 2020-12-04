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

- I used the following hive command to create a hive table to manage all csv files stored in S3 ***zhangfx_final***:
  
    ```sql
    DROP TABLE IF EXISTS zhangfx_final;
    CREATE EXTERNAL TABLE zhangfx_final (trade_day DATE,
        open float, high float, low float, 
        close float, volume float, dividend float, split float,
        adj_Open float, adj_High float, adj_Low float, adj_Close float, adj_Volume float)
    ROW FORMAT SERDE 'org.apache.hadoop.hive.serde2.OpenCSVSerde'
    LOCATION 's3://zhangfx-mpcs53014/stocks/'
    TBLPROPERTIES ("skip.header.line.count"="1");
    ```

# Batch Layer
