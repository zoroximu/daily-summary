package me.w1992wishes.flink.details.cep

import java.util

import org.apache.flink.cep.PatternSelectFunction
import org.apache.flink.cep.scala.CEP
import org.apache.flink.cep.scala.pattern.Pattern
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.time.Time

// 输入的登录事件样例类
case class LoginEvent(userId: Long, ip: String, eventType: String, eventTime: Long)

// 输出的异常报警信息样例类
case class Warning(userId: Long, firstFailTime: Long, lastFailTime: Long, warningMsg: String)

/**
  * @author w1992wishes 2020/7/28 16:29
  */
object LoginFailWithCep {
  def main(args: Array[String]): Unit = {

    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
    env.setParallelism(1)

    // 1. 读取事件数据，创建简单事件流
    val loginEventStream = env.readTextFile("E:\\project\\my_project\\daily-summary\\flink\\flink-details\\src\\main\\resources\\LoginLog.csv")
      .map(data => {
        val dataArray = data.split(",")
        LoginEvent(dataArray(0).trim.toLong, dataArray(1).trim, dataArray(2).trim, dataArray(3).trim.toLong)
      })
      .assignTimestampsAndWatermarks(new BoundedOutOfOrdernessTimestampExtractor[LoginEvent](Time.seconds(5)) {
        override def extractTimestamp(element: LoginEvent): Long = element.eventTime * 1000L
      })
      .keyBy(_.userId)

    // 2. 定义匹配模式
    val loginFailPattern = Pattern.begin[LoginEvent]("begin").where(_.eventType == "fail")
      .next("next").where(_.eventType == "fail")
      .within(Time.seconds(3))

    // 3. 在事件流上应用模式，得到一个pattern stream
    val patternStream = CEP.pattern(loginEventStream, loginFailPattern)

    // 4. 从pattern stream上应用select function，检出匹配事件序列
    val LoginFailDataStream = patternStream.select(new LoginFailMatch())

    LoginFailDataStream.print()

    env.execute("login fail with cep job")
  }
}

class LoginFailMatch() extends PatternSelectFunction[LoginEvent, Warning] {
  override def select(map: util.Map[String, util.List[LoginEvent]]): Warning = {
    val firstFail = map.get("begin").iterator().next()
    val lastFail = map.get("next").iterator().next()
    Warning(firstFail.userId, firstFail.eventTime, lastFail.eventTime, "login fail!")
  }
}

