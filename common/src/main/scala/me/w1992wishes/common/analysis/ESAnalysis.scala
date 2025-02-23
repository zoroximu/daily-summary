package me.w1992wishes.common.analysis

import org.apache.spark.sql.{DataFrame, SaveMode, SparkSession}
import org.elasticsearch.spark.sql._

trait ESAnalysis extends Analysis {

  /**
   * 通用，获取全量数据
   * @param spark
   * @param resource
   * @return
   */
  def loadFromES(spark : SparkSession,resource : String): DataFrame = {
    val inputDF = spark.esDF(resource)
    inputDF
  }

  /**
   * 通用，按条件获取数据
   *
   * @param spark
   * @param resource
   * @param query
   * """
   * |{
   * |  "query" : {
   * |    "range" : {
   * |        "dt" : {
   * |          "gte": "start_time",
   * |          "lte": "end_time"
   * |        }
   * |    }
   * |  }
   * |}
   * |""".stripMargin.replace("start_time", startTime).replace("end_time", endTime)
   * @return
   */
  def loadFromES(spark : SparkSession,resource : String, query : String): DataFrame = {
    val inputDF = spark.esDF(resource, query)
    inputDF
  }


  /**
   * 通用
   * @param df
   * @param resource index + "/" + type_
   * @param saveMode
   * @param format
   */
  def saveToES(df: DataFrame, resource : String, saveMode: String, format: String): Unit = {
    df.write
      .format(format)
      .mode(saveMode)
      .save(resource)
  }

  def saveToES(df: DataFrame, resource : String): Unit = {
    saveToES(df, resource, "overwrite", "org.elasticsearch.spark.sql")
  }

  def saveToES(df: DataFrame, resource : String, saveMode: String): Unit = {
    saveToES(df, resource, saveMode, "org.elasticsearch.spark.sql")
  }


  /**
   * 通用，传参
   * @param spark
   * @param df
   * @param resource index + "/" + type_
   * @param cfg
   *            es.write.operation
   *            es.mapping.id
   *            ...
   * @param saveMode
   * @param format
   */
  def saveToES(spark : SparkSession, df: DataFrame, resource : String, cfg : Map[String, String], saveMode: SaveMode, format: String): Unit = {
    cfg.foreach(keyValue => {
      spark.conf.set(keyValue._1, keyValue._2)
    })
    df.write
      .format(format)
      .mode(saveMode)
      .save(resource)
  }

  def saveToES(spark : SparkSession, df: DataFrame, resource : String, cfg : Map[String, String]): Unit = {
    saveToES(spark, df, resource, cfg, SaveMode.Overwrite, "org.elasticsearch.spark.sql")
  }

}
