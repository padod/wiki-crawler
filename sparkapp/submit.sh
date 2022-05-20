spark-submit \
      --master ${SPARK_MASTER_URL} \
      --driver-memory 2g \
      --executor-memory 1g \
      --executor-cores 1  \
      --class ${MAIN_CLASS} \
      --packages "com.arangodb:arangodb-spark-datasource-3.1_2.12:1.3.0" \
      executable.jar