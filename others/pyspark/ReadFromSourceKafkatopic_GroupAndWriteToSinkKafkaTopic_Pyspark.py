'''

This code reads streaming data from the Kafka topic "source_data" using the specified schema. 
It then parses the key and value columns from the Kafka message and applies the schema to the parsed data. 
The data is then grouped by the "city" field and the count is calculated for each group. 
Finally, the grouped data count is written to the Kafka topic "processed_data" with the key as "city" and the value as "count".
The code also includes the necessary configurations for Kafka, such as the bootstrap servers and checkpoint location.
Please note that you need to replace the Kafka bootstrap servers and topic names with the appropriate values for your environment.
Also, make sure to provide a valid checkpoint location for the streaming query.

'''

from pyspark.sql import SparkSession
from pyspark.sql.functions import col
from pyspark.sql.types import StructType, StructField, StringType, IntegerType, DoubleType
from pyspark.sql.streaming import DataStreamReader, DataStreamWriter

# Create a SparkSession
spark = SparkSession.builder.appName("KafkaStreaming").getOrCreate()

# Define the schema for the source_data topic
schema = StructType([
    StructField("name", StringType(), True),
    StructField("city", StringType(), True),
    StructField("age", IntegerType(), True),
    StructField("salary", DoubleType(), True)
])

# Read streaming data from the Kafka topic "source_data"
streaming_df = spark \
    .readStream \
    .format("kafka") \
    .option("kafka.bootstrap.servers", "localhost:9092") \
    .option("subscribe", "source_data") \
    .option("startingOffsets", "latest") \
    .load()

# Parse the key and value columns from the Kafka message
parsed_df = streaming_df \
    .select(col("key").cast("String").alias("name"), col("value").cast("String").alias("data")) \
    .select("name", "data.*")

# Apply the schema to the parsed data
data_df = parsed_df.select(
    col("name"),
    col("city"),
    col("age").cast(IntegerType()),
    col("salary").cast(DoubleType())
)

# Group the data by "city" and calculate the count for each group
grouped_df = data_df.groupBy("city").count()

# Write the grouped data count to the Kafka topic "processed_data"
stream_writer = grouped_df \
    .select(col("city").cast("String").alias("key"), col("count").cast("String").alias("value")) \
    .writeStream \
    .format("kafka") \
    .option("kafka.bootstrap.servers", "localhost:9092") \
    .option("topic", "processed_data") \
    .option("checkpointLocation", "/tmp/checkpoint") \
    .start()

# Wait for the streaming query to finish
stream_writer.awaitTermination()
