'''
The fields "empId","name","state","apptype","appstatus","transaction_datetime" 
are in stored as "value" in a Kafka Topic,"transaction_data".
Read the streaming data from topic "transaction_data", and group the data by
"empId","state","apptype" and "appstatus" fields and find the count for each group and
write the  data to another topic ,"result", with "key" as combination of fields "empId","state","apptype" and "appstatus and 
"value" as the count computed.
'''

from pyspark.sql import SparkSession
from pyspark.sql.functions import col
from pyspark.sql.functions import concat_ws

# Create a SparkSession
spark = SparkSession.builder.appName("GroupAndCount").getOrCreate()

# Read the streaming data from Kafka topic "transaction_data"
df = spark.readStream.format("kafka") \
    .option("kafka.bootstrap.servers", "localhost:9092") \
    .option("subscribe", "transaction_data") \
    .load()

# Convert the value column from binary to string
df = df.withColumn("value", df["value"].cast("string"))

# Split the value column into individual fields
df = df.withColumn("empId", col("value").getItem("empId"))
df = df.withColumn("name", col("value").getItem("name"))
df = df.withColumn("state", col("value").getItem("state"))
df = df.withColumn("apptype", col("value").getItem("apptype"))
df = df.withColumn("appstatus", col("value").getItem("appstatus"))
df = df.withColumn("transaction_datetime", col("value").getItem("transaction_datetime"))

# Group the data by empId, state, apptype, and appstatus fields and find the count for each group
grouped_df = df.groupBy("empId", "state", "apptype", "appstatus").count()

# Concatenate empId, state, apptype, and appstatus fields to create the key column
grouped_df = grouped_df.withColumn("key", concat_ws("_", "empId", "state", "apptype", "appstatus"))

# Select the key and count columns as the final result
result_df = grouped_df.select("key", "count")

# Write the result to Kafka topic "result"
query = result_df.writeStream.format("kafka") \
    .option("kafka.bootstrap.servers", "localhost:9092") \
    .option("topic", "result") \
    .option("checkpointLocation", "/tmp/checkpoint") \
    .start()

# Wait for the query to finish
query.awaitTermination()
