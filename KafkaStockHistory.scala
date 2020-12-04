import scala.reflect.runtime.universe._


case class kafkaStockdata(
       record_date: String,
       GSPC_Prices: Int,
       GSPC_Volumes: Int,
       NASDAQ_PRICE:Int,
       NASDAQ_Volumes: Int
                           )