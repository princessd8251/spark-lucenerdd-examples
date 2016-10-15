#!/usr/bin/env bash

CURRENT_DIR=`pwd`

# Assumes that spark is installed under home directory
HOME_DIR=`echo ~`
export SPARK_LOCAL_IP=localhost
SPARK_HOME=${HOME_DIR}/spark-2.0.1-bin-hadoop2.7

# spark-lucenerdd assembly JAR
MAIN_JAR=${CURRENT_DIR}/target/scala-2.11/spark-lucenerdd-examples-assembly-0.2.1.jar

echo "Running example class $1"

# Run spark shell locally
${SPARK_HOME}/bin/spark-submit   --conf "spark.executor.memory=512m" \
				--conf "spark.driver.memory=512m" \
				--conf "spark.rdd.compress=true" \
				--conf "spark.serializer=org.apache.spark.serializer.KryoSerializer" \
				--conf "spark.kryoserializer.buffer=24mb" \
				--master local[*] \
				--class $1 \
				--jars ${MAIN_JAR} \
				"${MAIN_JAR}"
