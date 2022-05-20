import org.apache.spark.sql.catalyst.ScalaReflection.schemaFor
import org.apache.spark.sql.catalyst.expressions.objects.AssertNotNull
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.StructType
import org.apache.spark.sql.{Column, DataFrame, SaveMode, SparkSession}
import org.slf4j.LoggerFactory


case class Document(
                     id: String,
                     nodeUrl: String,
                     body: String,
                     childNodes: Map[String, String]
                   )


object ArangoTransform {

  private val logger = LoggerFactory.getLogger(ArangoTransform.getClass)

  def parseToCollection(docs: DataFrame): (DataFrame, DataFrame, DataFrame) = {

    val nodesDF = docs.select(
      col("id").as("_key"),
      ltrim(col("nodeUrl"), "https://en.wikipedia.org/wiki/").as("name"),
      col("body"),
      current_timestamp().as("lastModified")
    )

    val explodedDF = docs.select(col("id"),
      explode(col("childNodes")).as(Seq("_key", "name"))
    )

    explodedDF.persist

    val leafNodesDF = explodedDF.select(col("_key"),
      ltrim(col("name"), "/wiki/").as("name"))
      .distinct
      .join(nodesDF, Seq("_key"), "left_anti")
      .withColumn("lastModified", current_timestamp())

    val edgesDF = explodedDF
      .select(new Column(AssertNotNull(concat(lit("wiki_articles/"), col("id")).expr)).as("_from"),
        new Column(AssertNotNull(concat(lit("wiki_articles/"),
          col("_key")).expr)).as("_to")
      )

    (nodesDF, leafNodesDF, edgesDF)
  }

  def saveDF(df: DataFrame, tableName: String, tableType: String = "document"): Unit =
    df.write.format("com.arangodb.spark").mode(SaveMode.Append).options(
      Map(
      "endpoints" -> "host.docker.internal:8529",
      "ssl.enabled" -> "false",
      "ssl.cert.value" -> "",
      "table.shards" -> "1",
      "confirmTruncate" -> "true",
      "overwriteMode" -> "update",
      "mergeObjects"-> "true",
      "table" -> tableName,
      "table.type" -> tableType
    )
    ).save()

  def main(args: Array[String]): Unit = {

    logger.info("Reading json data...")

    val spark: SparkSession = SparkSession.builder
      .appName(ArangoTransform.getClass.getName)
      .master(sys.env.getOrElse("SPARK_MASTER_URL", "spark://spark:7077"))
      .getOrCreate()

    val docsDF = spark.read.schema(schemaFor[Document].dataType.asInstanceOf[StructType]).json(s"/crawler_data/*.json")

    val (nodesDF, leafNodesDF, edgesDF) = parseToCollection(docsDF)

    logger.info("Dataframes created")

    saveDF(nodesDF, "wiki_articles")
    saveDF(leafNodesDF, "wiki_articles")
    saveDF(edgesDF, "wiki_links", "edge")

    logger.info("Saved to ArangoDB")

    spark.stop()
  }
}