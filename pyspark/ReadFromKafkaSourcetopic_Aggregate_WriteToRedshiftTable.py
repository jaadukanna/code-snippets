'''
The fields "empId","name","state","apptype","appstatus","transaction_datetime" are in stored as "value" in a Kafka Topic,
"transaction_data".
Read the streaming data from topic "transaction_data", and group the data by
"empId","state","apptype" and "appstatus" fields and find the count for each group and 
write the  streaming data to AWS Redshift Table ,"result", with  field "key" as combination of fields "empId","state","apptype" and 
"appstatus and field "value" as the count computed.
'''
from pyspark.sql import SparkSession
from pyspark.sql.functions import col

# Create a SparkSession
spark = SparkSession.builder.appName("StreamingData").getOrCreate()

# Read the streaming data from Kafka topic
df = spark.readStream.format("kafka") \
    .option("kafka.bootstrap.servers", "localhost:9092") \
    .option("subscribe", "transaction_data") \
    .load()

# Convert the value column from binary to string
df = df.withColumn("value", df["value"].cast("string"))

# Split the value column into separate columns
df = df.withColumn("empId", col("value").getItem(0)) \
    .withColumn("name", col("value").getItem(1)) \
    .withColumn("state", col("value").getItem(2)) \
    .withColumn("apptype", col("value").getItem(3)) \
    .withColumn("appstatus", col("value").getItem(4)) \
    .withColumn("transaction_datetime", col("value").getItem(5))

# Group the data by empId, state, apptype, and appstatus fields and find the count for each group
result = df.groupBy("empId", "state", "apptype", "appstatus").count()

# Write the streaming data to AWS Redshift table
# For appending data instead of overwriting use the .option("truncate", "false") \
result.writeStream \
    .format("jdbc") \
    .option("url", "jdbc:redshift://<redshift_endpoint>:<port>/<database>") \
    .option("dbtable", "result") \
    .option("user", "<username>") \
    .option("password", "<password>") \
    .start()
