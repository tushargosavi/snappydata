package io.snappydata.app.twitter

/**
 * Created by ymahajan on 28/10/15.
 */

import org.apache.spark._
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.streaming.{SchemaDStream, StreamingSnappyContext}
import org.apache.spark.sql.{Row, SaveMode, SnappyContext}
import org.apache.spark.streaming._


object KafkaConsumer {

  def main(args: Array[String]) {
    val sparkConf = new org.apache.spark.SparkConf()
      .setAppName("kafkaconsumer")
    //.set("snappydata.store.locators", "rdu-w27:10101")
    //.set("snappydata.embedded", "true")

    val sc = new SparkContext(sparkConf)
    val ssc = new StreamingContext(sc, Milliseconds(30000))
    SnappyContext.getOrCreate(sc)
    val ssnc = StreamingSnappyContext(ssc)

    val urlString = "jdbc:snappydata:;locators=rdu-w27:10101;persist-dd=false;member-timeout=600000;" +
      "jmx-manager-start=false;enable-time-statistics=false;statistic-sampling-enabled=false"

    val props = Map(
      "url" -> urlString,
      "driver" -> "com.pivotal.gemfirexd.jdbc.EmbeddedDriver",
      "poolImpl" -> "tomcat",
      "poolProperties" -> "maxActive=300",
      "user" -> "app",
      "password" -> "app"
    )

    ssnc.sql("create stream table tweetstreamtable (id long, text string, fullName string, " +
      "country string, retweets int, hashtag string) " +
      "using kafka_stream options " +
      "(storagelevel 'MEMORY_AND_DISK_SER_2', streamToRow 'io.snappydata.app.twitter.KafkaMessageToRowConverter' ," +
      " kafkaParams 'metadata.broker.list->rdu-w28:9092,rdu-w29:9092,rdu-w30:9092,rdu-w31:9092,rdu-w32:9092'," +
      " topics 'streamtweet')")


    val tableStream = ssnc.getSchemaDStream("tweetstreamtable")


    ssnc.registerSampleTable("tweetstreamtable_sampled", tableStream.schema, Map(
      "qcs" -> "hashtag",
      "fraction" -> "0.05",
      "strataReservoirSize" -> "300",
      "timeInterval" -> "3m"), Some("tweetstreamtable"))

    ssnc.saveStream(tableStream, Seq("tweetstreamtable_sampled"), {
      (rdd: RDD[Row], _) => rdd
    }, tableStream.schema)


    var numTimes = 0
    tableStream.foreachRDD { rdd =>
      println("Evaluating new batch")
      var start: Long = 0
      var end: Long = 0
      val df = ssnc.createDataFrame(rdd, tableStream.schema)
      df.write.format("column").mode(SaveMode.Append).options(props).saveAsTable("rawStreamColumnTable")

      println("Top 10 hash tags from exact table")
      start = System.nanoTime()
      /*
      val hashtagDF = ssnc.sql("select hashTags from rawStreamColumnTable")
      val top10Tags = hashtagDF.flatMap(_.getString(0).split(",").filter(_.nonEmpty).map(s => (s, 1)))
          .reduceByKey(_ + _).map {
        case (topic, count) => (count, topic)
      }.sortByKey(ascending = false).top(10)
      */
      val top10Tags = ssnc.sql("select count(*) as cnt, hashtag from rawStreamColumnTable " +
        "where length(hashtag) > 0 group by hashtag order by cnt desc limit 10").collect()
      end = System.nanoTime()
      top10Tags.foreach(println)
      println("\n\nTime taken: " + ((end - start) / 1000000L) + "ms")

      numTimes += 1
      if ((numTimes % 18) == 1) {
        ssnc.sql("SELECT count(*) FROM rawStreamColumnTable").show()
      }

      //ssnc.sql("SELECT count(*) as sample_count FROM tweetstreamtable_sampled").show()
      println("Top 10 hash tags from sample table")
      start = System.nanoTime()
      val stop10Tags = ssnc.sql("select count(*) as cnt, hashtag from tweetstreamtable_sampled " +
          "where length(hashtag) > 0 group by hashtag order by cnt desc limit 10").collect()
      end = System.nanoTime()
      stop10Tags.foreach(println)
      println("\n\nTime taken: " + ((end - start) / 1000000L) + "ms")

    }

/*
        val resultSet2: SchemaDStream = ssnc.registerCQ("SELECT * FROM tweetStreamTable window (duration '2' seconds, slide '2' seconds) where text like '%the%'")
        resultSet2.foreachRDD(rdd =>
          println(s"Received Twitter stream results. Count:" +
              s" ${if(!rdd.isEmpty()) {
                val df = ssnc.createDataFrame(rdd, resultSet2.schema)
                //SnappyContext.createTopKRDD()
                //df.show
                //df.write.format("column").mode(SaveMode.Append).options(props).saveAsTable("kafkaExtTable")
                //ssnc.appendToCacheRDD(rdd, streamTable, resultSet.schema)
              }
              }")
        )
*/

    ssnc.sql( """STREAMING CONTEXT START """)


    ssc.awaitTerminationOrTimeout(1800* 1000)
    ssnc.sql( "select count(*) from rawStreamColumnTable").show

    ssnc.sql( """STREAMING CONTEXT STOP """)
  }

}

