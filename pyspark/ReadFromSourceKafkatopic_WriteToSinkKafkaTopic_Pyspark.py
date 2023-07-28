'''
This code uses PySpark to read streaming data from a Kafka topic called "source_data" and
write the data to another Kafka topic called "processed_data". 
It starts by creating a SparkSession and defining the schema for the streaming data. 
Then, it reads the streaming data from the "source_data" topic using the Kafka source format and 
the specified Kafka bootstrap servers. 
The value column is converted from binary to string, and the JSON value column is parsed using the defined schema. 
Finally, the parsed data is written to the "processed_data" topic using the Kafka sink format, and
a checkpoint location is specified for fault-tolerance. The streaming query is started and waits for termination.
Please note that you need to replace "localhost:9092" with the actual Kafka bootstrap servers and 
adjust the checkpoint location according to your setup.
'''


from pyspark.sql import SparkSession
from pyspark.sql.functions import from_json
from pyspark.sql.types import StructType, StructField, StringType, IntegerType, DoubleType

# Create a SparkSession
spark = SparkSession.builder.appName("KafkaStreaming").getOrCreate()

# Define the schema for the streaming data
schema = StructType([
    StructField("name", StringType(), True),
    StructField("city", StringType(), True),
    StructField("age", IntegerType(), True),
    StructField("salary", DoubleType(), True)
])

# Read streaming data from Kafka topic "source_data"
streaming_df = spark \
    .readStream \
    .format("kafka") \
    .option("kafka.bootstrap.servers", "localhost:9092") \
    .option("subscribe", "source_data") \
    .load()

# Convert the value column from binary to string
streaming_df = streaming_df.withColumn("value", streaming_df["value"].cast(StringType()))

# Parse the JSON value column using the defined schema
parsed_df = streaming_df \
    .select(from_json(streaming_df["value"], schema).alias("data")) \
    .select("data.*")

# Write the streaming data to Kafka topic "processed_data"
query = parsed_df \
    .writeStream \
    .format("kafka") \
    .option("kafka.bootstrap.servers", "localhost:9092") \
    .option("topic", "processed_data") \
    .option("checkpointLocation", "/tmp/checkpoint") \
    .start()

# Wait for the streaming query to finish
query.awaitTermination()
