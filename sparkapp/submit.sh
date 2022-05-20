spark-submit \
      --master ${SPARK_MASTER_URL} \
      --class ${MAIN_CLASS} \
      --packages "com.arangodb:arangodb-spark-datasource-3.1_2.12:1.3.0" \
      executable.jar