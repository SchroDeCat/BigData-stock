import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.SparkConf
import org.apache.spark.streaming._
import org.apache.spark.streaming.kafka010.ConsumerStrategies.Subscribe
import org.apache.spark.streaming.kafka010.LocationStrategies.PreferConsistent
import org.apache.spark.streaming.kafka010._
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper}
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.HBaseConfiguration
import org.apache.hadoop.hbase.client.{ConnectionFactory, Get, Increment, Put}
import org.apache.hadoop.hbase.util.Bytes

object StreamStock {
  val mapper = new ObjectMapper()
  mapper.registerModule(DefaultScalaModule)
  val hbaseConf: Configuration = HBaseConfiguration.create()
  hbaseConf.set("hbase.zookeeper.property.clientPort", "2181")
  hbaseConf.set("hbase.zookeeper.quorum", "localhost")
  
  val hbaseConnection = ConnectionFactory.createConnection(hbaseConf)
  val stock_history = hbaseConnection.getTable(TableName.valueOf("zhangfx_final_summary"))
  
  def getOldStock(stock_name: String) = {
      val result = stock_history.get(new Get(Bytes.toBytes(stock_name)))
      if(result.isEmpty())
        None
      else
        Some(KafkaStockHistory(
              stock_name,
              Bytes.toInt(result.getValue(Bytes.toBytes("result"), Bytes.toBytes("num_days"))),
              Bytes.toFloat(result.getValue(Bytes.toBytes("result"), Bytes.toBytes("value_avg"))),
              Bytes.toFloat(result.getValue(Bytes.toBytes("result"), Bytes.toBytes("value_std"))),
              Bytes.toFloat(result.getValue(Bytes.toBytes("result"), Bytes.toBytes("start_day_index"))),
              Bytes.toFloat(result.getValue(Bytes.toBytes("result"), Bytes.toBytes("end_day_index"))),
              Bytes.toFloat(result.getValue(Bytes.toBytes("result"), Bytes.toBytes("start_day_stock"))),
              Bytes.toFloat(result.getValue(Bytes.toBytes("result"), Bytes.toBytes("end_day_stock")))
            )
        )

  }
  
  def UpdateStockByDate(stream : Map[String, String]) : String = {
    val oldStock = getOldStock(stream.key).get

    val num_day = oldStock.num_days
    val avg_old = oldStock.value_avg
    val std_old = oldStock.value_std
    val new_val = Bytes.toFloat(stream.value)
    val std_new = (new_val - avg_old) * (new_val - avg_old) / num_day
    // Update result data
    val put = new Put(Bytes.toBytes(stream.key))
    put.add(Bytes.toBytes("result"),Bytes.toBytes("num_days"), Bytes.toBytes(oldStock.num_days))
    put.add(Bytes.toBytes("result"),Bytes.toBytes("value_avg"), Bytes.toBytes(oldStock.value_avg))
    put.add(Bytes.toBytes("result"),Bytes.toBytes("value_std"), Bytes.toBytes(std_new))
    put.add(Bytes.toBytes("result"),Bytes.toBytes("start_day_index"), Bytes.toBytes(oldStock.start_day_index))
    put.add(Bytes.toBytes("result"),Bytes.toBytes("end_day_index"), Bytes.toBytes(oldStock.end_day_index))
    put.add(Bytes.toBytes("result"),Bytes.toBytes("start_day_stock"), Bytes.toBytes(oldStock.start_day_stock))
    put.add(Bytes.toBytes("result"),Bytes.toBytes("end_day_stock"), Bytes.toBytes(oldStock.end_day_stock))
    // Update to hbase
    stock_history.put(put)
    return "Updated stock " + ksr.stock_name
  }
  
  def main(args: Array[String]) {
    if (args.length < 1) {
      System.err.println(s"""
        |Usage: StreamStock <brokers>
        |  <brokers> is a list of one or more Kafka brokers
        | 
        """.stripMargin)
      System.exit(1)
    }
    
    val Array(brokers) = args

    // Create context with 2 second batch interval
    val sparkConf = new SparkConf().setAppName("StreamStock")
    val ssc = new StreamingContext(sparkConf, Seconds(2))

    // Create direct kafka stream with brokers and topics
    val topicsSet = Set("zhangfx_mpcs53014")

    // Create direct kafka stream with brokers and topics
    val kafkaParams = Map[String, Object](
      "bootstrap.servers" -> brokers,
      "key.deserializer" -> classOf[StringDeserializer],
      "value.deserializer" -> classOf[StringDeserializer],
      "group.id" -> "use_a_separate_group_id_for_each_stream",
      "auto.offset.reset" -> "latest",
      "enable.auto.commit" -> (false: java.lang.Boolean)
    )
    val stream = KafkaUtils.createDirectStream[String, String](
      ssc, PreferConsistent,
      Subscribe[String, String](topicsSet, kafkaParams)
    )

    // Update  
    val updateStock = stream.map(UpdateStockByDate)
    updateStock.print()

    ssc.start()
    ssc.awaitTermination()
  }
}
