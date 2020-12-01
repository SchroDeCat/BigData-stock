import quandl
import argparse
import os
import tqdm

NASDAQ = r"nasdaqlisted.txt"
QUANDL_EOD_API = r"https://www.quandl.com/api/v3/datasets/EOD/"
stock_codes = []

# add cli parse to accept token of quandl
cli_parser = argparse.ArgumentParser()
cli_parser.add_argument("--token", type=str, nargs=1, help="token for accessing quandl premium data")
cli_args = cli_parser.parse_args()
quandl_token = cli_args.token[0]
print("Accept token ", quandl_token)

# fetch file if not exists
if not os.path.exists(NASDAQ):
    os.system("curl ftp://ftp.nasdaqtrader.com/symboldirectory/{} > {}".format(NASDAQ, NASDAQ))
    if not os.path.exists(NASDAQ):
        raise Exception("Failed to fetch file {}".format(NASDAQ))

# read nasdaq list
with open(NASDAQ, 'r') as file:
    index = 0
    while True:
        index += 1
        stock_line = file.readline()
        try:
            code = stock_line.split("|")[0]
        except:
            print("ERROR: abnormal exit reading list!")
            break

        if index > 1:
            stock_codes.append(code)

        if index > 3:
        # if not stock_line:
            break
        
    print("Finish Reading {} stocks ".format(index))
    # print(stock_codes)

# fetch the csvfiles from quandl
for code in tqdm.tqdm(stock_codes, desc="Loading Stocks from Quandl", unit="file"):
    # 1. fetch to name node
    # quandl.get("EOD/{}".format(code), authtoken=quandl_token)  
    
    # 2. test on local                                       
    # os.system(f"curl -s {QUANDL_EOD_API}{code}.csv?api_key={quandl_token} > {code}.csv")              
    
    # 3. Directly to hdfs /tmp/zhangfx
    # os.system(f"curl -s {QUANDL_EOD_API}{code}.csv?api_key={quandl_token} | hdfs dfs -put /tmp/zhangfx/{code}.csv")  
    
    # 4. Directly to S3 zhangfx-mpcs53014/stocks
    # os.system(f"curl -s {QUANDL_EOD_API}{code}.csv?api_key={quandl_token} | aws s3 cp - s3://zhangfx-mpcs53014/stocks/{code}.csv")  

