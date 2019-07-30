/* Copyright (c) 2019, Salesforce.com, Inc.
 * All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause.
 * For full license text, see LICENSE file in the repo root or https://opensource.org/licenses/BSD-3-Clause.
 */
package com.salesforce.dynamodbv2.mt.backups
import com.amazonaws.AmazonServiceException
import com.amazonaws.services.dynamodbv2.model.CreateBackupRequest
import com.amazonaws.services.dynamodbv2.model.ListBackupsRequest
import com.amazonaws.services.dynamodbv2.model.ListBackupsResult
import com.amazonaws.services.dynamodbv2.model.RestoreTableFromBackupRequest
import com.google.common.collect.Maps
import com.salesforce.dynamodbv2.mt.context.MtAmazonDynamoDbContextProvider
import com.salesforce.dynamodbv2.mt.mappers.MtAmazonDynamoDb

/**
 * Interface for grabbing backups of data managed by mt-dynamo.
 *
 * Backups are generated across all managed tables to sliced up, granular tenant-table backups, which may independently
 * be restored. Tenant-table backups must be restored to a different tenant-table target. Additionally, a tenant-table
 * backup may be restored  onto either the same environment, or migrated into different environments, with say,
 * different multitenant strategies (ie: moving a tenant-table from a table per tenant setup
 * onto a shared table setup, or vice versa).
 *
 * One more dimension of value added by these backups are, they should be mt-dynamo version agnostic. So if a backup
 * was generated at v0.10.5 of mt-dynamo, and imagine the physical representation of tenant to table mapping strategy
 * changes at v0.11.0, that backup should be restorable at that later version, offering a path to preserve data when
 * we change table mappings as mt-dynamo evolves.
 *
 * At the moment, these backups are taking full snapshots of an mt-dynamo account, but there are plans to support PITR
 * style continuous backups, offering a time window of available restore points (versus choosing from N snapshots)
 */
interface MtBackupManager {
    /**
     * @return a new multitenant backup job, with status {@link Status.IN_PROGRESS} if none exists with
     * specified backupId.
     */
    fun createBackup(
        createBackupRequest: CreateBackupRequest
    ): MtBackupMetadata

    /**
     * Go through each physical row in {@code physicalTableName} in dynamo, and augment the current in progress backup
     * data on S3 with said data.
     */
    fun backupPhysicalMtTable(
        createBackupRequest: CreateBackupRequest,
        physicalTableName: String
    ): MtBackupMetadata

    /**
     * Internal function to mark an in progress backup to completed state.
     */
    fun markBackupComplete(createBackupRequest: CreateBackupRequest): MtBackupMetadata

    /**
     * Return the utility to snapshot tables.
     */
    fun getMtBackupTableSnapshotter(): MtBackupTableSnapshotter

    /**
     * Get the status of a given multi-tenant backup.
     */
    fun getBackup(backupName: String): MtBackupMetadata?

    /**
     * Delete the given multi-tenant backup, including all metadata and data files related.
     */
    fun deleteBackup(backupName: String): MtBackupMetadata?

    /**
     * Initiate a restore of a given table-tenant backup to a new table-tenant target.
     */
    fun restoreTenantTableBackup(
        restoreMtBackupRequest: RestoreMtBackupRequest,
        mtContext: MtAmazonDynamoDbContextProvider
    ): TenantRestoreMetadata

    /**
     * List all multi-tenant backups known to us on S3.
     */
    fun listBackups(listBackupRequest: ListBackupsRequest): ListBackupsResult
}

/**
 * Metadata of a multitenant backup.
 *
 * @param mtBackupName name of a backup configured by client.
 * @param status {@link Status} of backup
 * @param tenantTables tenant-tables contained within this given backup
 * @param creationTime timestamp this backup began processing in milliseconds since epoch
 */
data class MtBackupMetadata(
    val mtBackupName: String,
    val status: Status,
    val tenantTables: Map<TenantTableBackupMetadata, Long>,
    val creationTime: Long = -1
) {
    /**
     * @return a new MtBackupMetadata object merging this backup metadata with {@code otherBackupMetadata}.
     */
    fun merge(newBackupMetadata: MtBackupMetadata): MtBackupMetadata {
        if (!(newBackupMetadata.mtBackupName.equals(mtBackupName))) {
            throw MtBackupException("Trying to merge a backup with a different backup id, " +
                    "this: $mtBackupName, other: ${newBackupMetadata.mtBackupName}")
        }
        val tenantTableCount: HashMap<TenantTableBackupMetadata, Long> = Maps.newHashMap()
        tenantTableCount.putAll(tenantTables)
        for (tenantTable in newBackupMetadata.tenantTables.keys) {
            tenantTableCount.put(tenantTable, tenantTables.getOrDefault(tenantTable, 0L) +
                    newBackupMetadata.tenantTables.get(tenantTable)!!)
        }
        return MtBackupMetadata(mtBackupName,
                newBackupMetadata.status, // use status of new metadata
                tenantTableCount,
                creationTime) // maintain existing create time for all merges
    }
}

data class TenantTableBackupMetadata(
    val backupName: String,
    val tenantId: String,
    val virtualTableName: String
)

data class TenantRestoreMetadata(val backupName: String, val status: Status, val tenantId: String, val virtualTableName: String)

class RestoreMtBackupRequest(
    val sourceTenantTableBackup: MtAmazonDynamoDb.TenantTable,
    val targetTenantTable: MtAmazonDynamoDb.TenantTable
) : RestoreTableFromBackupRequest() {
    init {
        targetTableName = targetTenantTable.virtualTableName
    }
}
class MtBackupException(message: String, parent: Exception? = null) : AmazonServiceException(message, parent)

enum class Status {
    IN_PROGRESS,
    COMPLETE,
    FAILED
}