/*
 * Copyright (c) 2023 Airbyte, Inc., all rights reserved.
 */
package io.airbyte.integrations.destination.bigquery

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.DeleteObjectsRequest
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.BigQueryOptions
import com.google.cloud.bigquery.Dataset
import com.google.cloud.bigquery.DatasetInfo
import io.airbyte.commons.json.Jsons.deserialize
import io.airbyte.integrations.destination.bigquery.BigQueryDestination.Companion.getServiceAccountCredentials
import io.airbyte.integrations.destination.bigquery.BigQueryUtils.getLoadingMethod
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object BigQueryDestinationTestUtils {
    private val LOGGER: Logger = LoggerFactory.getLogger(BigQueryDestinationTestUtils::class.java)

    /**
     * Parse the config file and replace dataset with rawNamespace and stagingPath randomly
     * generated by the test.
     *
     * @param configFile Path to the config file
     * @param datasetId Dataset id to use in the test. Should be randomized per test case.
     * @param stagingPath Staging GCS path to use in the test, or null if the test is running in
     * standard inserts mode. Should be randomized per test case.
     */
    @Throws(IOException::class)
    fun createConfig(configFile: Path?, datasetId: String?, stagingPath: String?): ObjectNode {
        LOGGER.info("Setting default dataset to {}", datasetId)
        val tmpConfigAsString = Files.readString(configFile)
        val config = deserialize(tmpConfigAsString) as ObjectNode
        config.put(BigQueryConsts.CONFIG_DATASET_ID, datasetId)

        // This is sort of a hack. Ideally tests shouldn't interfere with each other even when using
        // the
        // same staging path.
        // Most likely there's a real bug in the connector - but we should investigate that and
        // write a real
        // test case,
        // rather than relying on tests randomly failing to indicate that bug.
        // See https://github.com/airbytehq/airbyte/issues/28372.
        if (stagingPath != null && getLoadingMethod(config) == UploadingMethod.GCS) {
            val loadingMethodNode = config[BigQueryConsts.LOADING_METHOD] as ObjectNode
            loadingMethodNode.put(BigQueryConsts.GCS_BUCKET_PATH, stagingPath)
        }
        return config
    }

    /**
     * Get a handle for the BigQuery dataset instance used by the test. This dataset instance will
     * be used to verify results of test operations and for cleaning up after the test runs
     *
     * @param config
     * @param bigquery
     * @param datasetId
     * @return
     */
    fun initDataSet(config: JsonNode, bigquery: BigQuery?, datasetId: String?): Dataset? {
        val datasetInfo =
            DatasetInfo.newBuilder(datasetId)
                .setLocation(config[BigQueryConsts.CONFIG_DATASET_LOCATION].asText())
                .build()
        try {
            return bigquery!!.create(datasetInfo)
        } catch (ex: Exception) {
            if (ex.message!!.indexOf("Already Exists") > -1) {
                return bigquery!!.getDataset(datasetId)
            }
        }
        return null
    }

    /**
     * Initialized bigQuery instance that will be used for verifying results of test operations and
     * for cleaning up BigQuery dataset after the test
     *
     * @param config
     * @param projectId
     * @return
     * @throws IOException
     */
    @Throws(IOException::class)
    fun initBigQuery(config: JsonNode, projectId: String?): BigQuery {
        val credentials = getServiceAccountCredentials(config)
        return BigQueryOptions.newBuilder()
            .setProjectId(projectId)
            .setCredentials(credentials)
            .build()
            .service
    }

    /**
     * Deletes bigquery data set created during the test
     *
     * @param bigquery
     * @param dataset
     * @param LOGGER
     */
    fun tearDownBigQuery(bigquery: BigQuery?, dataset: Dataset?, LOGGER: Logger) {
        // allows deletion of a dataset that has contents
        val option = BigQuery.DatasetDeleteOption.deleteContents()
        if (bigquery == null || dataset == null) {
            return
        }
        try {
            val success = bigquery.delete(dataset.datasetId, option)
            if (success) {
                LOGGER.info("BQ Dataset $dataset deleted...")
            } else {
                LOGGER.info("BQ Dataset cleanup for $dataset failed!")
            }
        } catch (ex: Exception) {
            LOGGER.error("Failed to remove BigQuery resources after the test", ex)
        }
    }

    /** Remove all the GCS output from the tests. */
    fun tearDownGcs(s3Client: AmazonS3?, config: JsonNode?, LOGGER: Logger) {
        if (s3Client == null) {
            return
        }
        if (getLoadingMethod(config!!) != UploadingMethod.GCS) {
            return
        }
        val properties = config[BigQueryConsts.LOADING_METHOD]
        val gcsBucketName = properties[BigQueryConsts.GCS_BUCKET_NAME].asText()
        val gcs_bucket_path = properties[BigQueryConsts.GCS_BUCKET_PATH].asText()
        try {
            val keysToDelete: MutableList<DeleteObjectsRequest.KeyVersion> = LinkedList()
            val objects = s3Client.listObjects(gcsBucketName, gcs_bucket_path).objectSummaries
            for (`object` in objects) {
                keysToDelete.add(DeleteObjectsRequest.KeyVersion(`object`.key))
            }

            if (keysToDelete.size > 0) {
                LOGGER.info("Tearing down test bucket path: {}/{}", gcsBucketName, gcs_bucket_path)
                // Google Cloud Storage doesn't accept request to delete multiple objects
                for (keyToDelete in keysToDelete) {
                    s3Client.deleteObject(gcsBucketName, keyToDelete.key)
                }
                LOGGER.info("Deleted {} file(s).", keysToDelete.size)
            }
        } catch (ex: Exception) {
            LOGGER.error("Failed to remove GCS resources after the test", ex)
        }
    }
}
