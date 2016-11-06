package org.zouzias.spark.lucenerdd.examples.linkage.shape

import org.apache.spark.SparkConf
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{SaveMode, SparkSession}
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.zouzias.spark.lucenerdd.logging.Logging
import org.zouzias.spark.lucenerdd.models.SparkScoreDoc
import org.zouzias.spark.lucenerdd.spatial.shape._
import org.zouzias.spark.lucenerdd.spatial.shape.rdds.ShapeRDD


case class ElapsedTime(start: Long, end: Long, duration: Long)
/**
  * Spatial world cities self-linkage
  */
object SpatialShapeRDDWorldCitiesSelfLinkage extends Logging {


  def dayString(): String = {
    val date = new DateTime()
    val formatter = DateTimeFormat.forPattern("yyyy-MM-dd")
    formatter.print(date)
  }
  def main(args: Array[String]) {

    // initialise spark context
    val conf = new SparkConf().setAppName(SpatialShapeRDDWorldCitiesSelfLinkage.getClass.getName)

    implicit val spark = SparkSession.builder().config(conf).getOrCreate()
    import spark.implicits._

    val today = dayString()
    val executorMemory = conf.get("spark.executor.memory")
    val executorCores = conf.get("spark.executor.cores")
    val executorInstances = conf.get("spark.executor.instances")
    val fieldName = "City"


    log.info(s"Executor instances: ${executorInstances}")
    log.info(s"Executor cores: ${executorCores}")
    log.info(s"Executor memory: ${executorMemory}")

    val start = System.currentTimeMillis()

    val citiesDF = spark.read.parquet("recordlinkage/world-cities-maxmind.parquet").repartition(60)
    citiesDF.cache
    val total = citiesDF.count
    logInfo(s"Cities: ${total}")

    val cities = citiesDF.select("Latitude", "Longitude", "City", "Country")
      .map(row => ((row.getString(1).toDouble, row.getString(0).toDouble), (row.getString(2), row.getString(3))))


    val shapes = ShapeRDD(cities)

    shapes.cache
    shapes.count
    logInfo("Max mind cities loaded successfully")

    val rdds = cities.rdd.randomSplit(Array(1,1,1,1))

    // Link and fetch top-3
    val linkages: Array[RDD[(((Double, Double), (String, String)), Array[SparkScoreDoc])]] =
    rdds.map { case rdd =>
      shapes.linkByRadius(rdd, { x: ((Double, Double), (String, String)) => x._1 }, 3)
    }

    val finalLinkage = linkages.reduce((a: RDD[(((Double, Double), (String, String)), Array[SparkScoreDoc])],
      b: RDD[(((Double, Double), (String, String)), Array[SparkScoreDoc])]) => a.union(b))

    import spark.implicits._
    val linkedDF = spark.createDataFrame(shapes.postLinker(finalLinkage))

    linkedDF.write.mode(SaveMode.Overwrite)
      .parquet(s"recordlinkage/timing/vshaperdd-max-mind-cities-linkage-result-${today}-${executorMemory}-${executorInstances}-${executorCores}.parquet")

    val end = System.currentTimeMillis()

    spark.createDataFrame(Seq(ElapsedTime(start, end, end - start))).write.mode(SaveMode.Overwrite)
      .parquet(s"recordlinkage/timings/shaperdd-max-mind-cities-linkage-timing-${today}-${executorMemory}-${executorInstances}-${executorCores}.parquet")

    // terminate spark context
    spark.stop()
  }
}

