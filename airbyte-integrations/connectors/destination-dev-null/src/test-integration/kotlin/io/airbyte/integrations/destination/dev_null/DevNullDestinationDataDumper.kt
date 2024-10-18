/*
 * Copyright (c) 2024 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.integrations.destination.dev_null

import io.airbyte.cdk.command.ConfigurationSpecification
import io.airbyte.cdk.load.command.DestinationStream
import io.airbyte.cdk.load.test.util.DestinationDataDumper
import io.airbyte.cdk.load.test.util.OutputRecord
import java.nio.file.Path

object DevNullDestinationDataDumper : DestinationDataDumper {
    override fun dumpRecords(
        configPath: Path,
        stream: DestinationStream
    ): List<OutputRecord> {
        // E2e destination doesn't actually write records, so we shouldn't even
        // have tests that try to read back the records
        throw NotImplementedError()
    }
}
