'''
Please note that you need to replace "localhost:9092" with the actual Kafka broker address and port, 
"topic_name" with the name of the Kafka topic you want to consume from, 
"s3a://your_bucket/checkpoint_location" with the S3 bucket and checkpoint location you want to use, and
"s3a://your_bucket/data" with the S3 bucket and path where you want to store the data.
This code sets up a SparkSession and defines the schema for the incoming Kafka messages. 
It then reads the Kafka topic as a streaming DataFrame and converts the value column from Kafka to JSON format. 
The JSON data is parsed using the defined schema, and the resulting DataFrame is written to S3 
with the checkpoint location set as an S3 bucket.
By using the checkpoint location as an S3 bucket, you ensure fault-tolerance and resiliency in case of failures or restarts. The checkpoint data is stored in the specified S3 bucket, allowing the streaming job to resume from where it left off in case of failures.

'''
from pyspark.sql import SparkSession
from pyspark.sql.functions import from_json
from pyspark.sql.types import StructType, StringType, IntegerType

# Create a SparkSession
spark = SparkSession.builder \
    .appName("Kafka to S3") \
    .getOrCreate()

# Define the schema for the incoming Kafka messages
schema = StructType() \
    .add("id", IntegerType()) \
    .add("name", StringType()) \
    .add("age", IntegerType())

# Read the Kafka topic as a streaming DataFrame
df = spark \
    .readStream \
    .format("kafka") \
    .option("kafka.bootstrap.servers", "localhost:9092") \
    .option("subscribe", "topic_name") \
    .load()

# Convert the value column from Kafka to JSON format
df = df.selectExpr("CAST(value AS STRING)")

# Parse the JSON data using the defined schema
df = df.select(from_json(df.value, schema).alias("data")).select("data.*")

# Write the streaming DataFrame to S3 with checkpoint location as an S3 bucket
df.writeStream \
    .format("parquet") \
    .option("checkpointLocation", "s3a://your_bucket/checkpoint_location") \
    .option("path", "s3a://your_bucket/data") \
    .start() \
    .awaitTermination()
