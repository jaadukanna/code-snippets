'''
To write to a Kafka topic using Amazon MSK EMR pyspark, you may need to configure the following properties:

kafka.security.protocol: 
This configuration specifies the security protocol to be used for communication with the Kafka brokers.
It can be set to either PLAINTEXT or SASL_SSL. Use PLAINTEXT if you are not using any security features, and 
SASL_SSL if you want to enable authentication and encryption.

kafka.sasl.mechanism: 
This configuration specifies the SASL (Simple Authentication and Security Layer) mechanism to be used for authentication. 
The supported values are PLAIN, SCRAM-SHA-256, and SCRAM-SHA-512. 
Choose the appropriate mechanism based on your security requirements.

kafka.sasl.jaas.config: 
This configuration specifies the JAAS (Java Authentication and Authorization Service) configuration for SASL authentication. 
It contains the login module and its options. The format of this configuration is specific to the chosen SASL mechanism.

kafka.truststore.location and kafka.truststore.password:
These configurations specify the location and password of the truststore file, which contains the trusted CA certificates. 
This is required when using SSL encryption (SASL_SSL security protocol).

Here is an example of how to set these configurations in your pyspark code:

'''
from pyspark.sql import SparkSession

spark = SparkSession.builder \
    .appName("KafkaWriter") \
    .config("spark.executor.extraJavaOptions", "-Djava.security.auth.login.config=/path/to/jaas.conf") \
    .config("spark.kafka.security.protocol", "SASL_SSL") \
    .config("spark.kafka.sasl.mechanism", "PLAIN") \
    .config("spark.kafka.sasl.jaas.config", "org.apache.kafka.common.security.plain.PlainLoginModule required username='user' password='password';") \
    .config("spark.kafka.truststore.location", "/path/to/truststore.jks") \
    .config("spark.kafka.truststore.password", "truststore_password") \
    .getOrCreate()

# Write data to Kafka topic
df = spark.read.format("csv").load("/path/to/data.csv")
df.write.format("kafka") \
    .option("kafka.bootstrap.servers", "kafka_broker1:9092,kafka_broker2:9092") \
    .option("topic", "my_topic") \
    .save()
# Make sure to replace the placeholders (/path/to/jaas.conf, /path/to/truststore.jks, user, password, kafka_broker1:9092,kafka_broker2:9092, my_topic, truststore_password) with the actual values specific to your environment and use case.

# Note: The above code assumes that you have already set up the necessary security configurations,
# such as generating the JAAS configuration file and truststore file, and providing the 
# correct credentials for authentication.
