package com.salesforce.dynamodbv2.mt.mappers.sharedtable;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.*;
import com.salesforce.dynamodbv2.dynamodblocal.AmazonDynamoDbLocal;
import com.salesforce.dynamodbv2.mt.context.MtAmazonDynamoDbContextProvider;
import com.salesforce.dynamodbv2.mt.context.impl.MtAmazonDynamoDbContextProviderThreadLocalImpl;
import com.salesforce.dynamodbv2.mt.util.DynamoDbTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.amazonaws.services.dynamodbv2.model.KeyType.HASH;
import static com.amazonaws.services.dynamodbv2.model.ScalarAttributeType.S;

class SharedTableBuilderTest {

    private static AmazonDynamoDB LOCAL_DYNAMO_DB = AmazonDynamoDbLocal.getAmazonDynamoDbLocal();
    public static final MtAmazonDynamoDbContextProvider MT_CONTEXT =
            new MtAmazonDynamoDbContextProviderThreadLocalImpl();
    private static final String ID_ATTR_NAME = "id";
    private static final String INDEX_ID_ATTR_NAME = "indexId";
    private static final String TABLE_PREFIX_PREFIX = "oktodelete-testBillingMode.";
    private static String tablePrefix;
    private static AtomicInteger counter = new AtomicInteger();
    private static String tableName;

    @BeforeEach
    void beforeEach() {
        tableName = String.valueOf(System.currentTimeMillis());
        tablePrefix = TABLE_PREFIX_PREFIX + counter.incrementAndGet();
    }

    private static List<String> testTables = new ArrayList<>(Arrays.asList("mt_sharedtablestatic_s_s",
            "mt_sharedtablestatic_s_n", "mt_sharedtablestatic_s_b", "mt_sharedtablestatic_s_nolsi",
            "mt_sharedtablestatic_s_s_nolsi", "mt_sharedtablestatic_s_n_nolsi",
            "mt_sharedtablestatic_s_b_nolsi")).stream()
            .collect(Collectors.toList());

    @Test
    void testBillingModeProvisionedThroughputIsSetForCustomCreateTableRequests() {

        CreateTableRequest request = new CreateTableRequest()
                .withTableName(tableName)
                .withKeySchema(new KeySchemaElement(ID_ATTR_NAME, HASH))
                .withAttributeDefinitions(
                        new AttributeDefinition(ID_ATTR_NAME, S),
                        new AttributeDefinition(INDEX_ID_ATTR_NAME, S))
                .withGlobalSecondaryIndexes(new GlobalSecondaryIndex()
                        .withIndexName("index")
                        .withKeySchema(new KeySchemaElement(INDEX_ID_ATTR_NAME, HASH))
                        .withProjection(new Projection().withProjectionType(ProjectionType.ALL))
                        .withProvisionedThroughput(new ProvisionedThroughput(1L,1L))
                ).withProvisionedThroughput(new ProvisionedThroughput(1L,1L));

        SharedTableBuilder.builder()
                .withBillingMode(BillingMode.PROVISIONED)
                .withCreateTableRequests(request)
                .withStreamsEnabled(false)
                .withPrecreateTables(true)
                .withTablePrefix(tablePrefix)
                .withAmazonDynamoDb(LOCAL_DYNAMO_DB)
                .withContext(MT_CONTEXT)
                .build();

        DynamoDbTestUtils.assertProvisionedIsSet(DynamoDbTestUtils.getTableNameWithPrefix(tablePrefix, tableName,
                ""), LOCAL_DYNAMO_DB, 1L);
    }

    @Test
    void testBillingModeProvisionedThroughputIsSetForDefaultCreateTableRequestsWithProvisionedInputBillingMode() {
        SharedTableBuilder.builder()
                .withBillingMode(BillingMode.PROVISIONED)
                .withAmazonDynamoDb(LOCAL_DYNAMO_DB)
                .withTablePrefix(tablePrefix)
                .withPrecreateTables(true)
                .withContext(MT_CONTEXT)
                .build();

        DynamoDbTestUtils.assertProvisionedIsSetForSetOfTables(getPrefixedTables(), LOCAL_DYNAMO_DB, 1L);
    }

    @Test
    void testBillingModeProvisionedThroughputIsSetForDefaultCreateTableRequestsWithNullInputBillingMode() {
        SharedTableBuilder.builder()
                .withAmazonDynamoDb(LOCAL_DYNAMO_DB)
                .withTablePrefix(tablePrefix)
                .withPrecreateTables(true)
                .withContext(MT_CONTEXT)
                .build();

        DynamoDbTestUtils.assertProvisionedIsSetForSetOfTables(getPrefixedTables(), LOCAL_DYNAMO_DB, 1L);
    }

    @Test
    void testBillingModePayPerRequestThroughputIsSetForCustomCreateTableRequest() {
        CreateTableRequest request = new CreateTableRequest()
                .withTableName(tableName)
                .withKeySchema(new KeySchemaElement(ID_ATTR_NAME, HASH))
                .withAttributeDefinitions(
                        new AttributeDefinition(ID_ATTR_NAME, S),
                        new AttributeDefinition(INDEX_ID_ATTR_NAME, S))
                .withGlobalSecondaryIndexes(new GlobalSecondaryIndex()
                        .withIndexName("index")
                        .withKeySchema(new KeySchemaElement(INDEX_ID_ATTR_NAME, HASH))
                        .withProjection(new Projection().withProjectionType(ProjectionType.ALL))
                );

        SharedTableBuilder.builder()
                .withBillingMode(BillingMode.PAY_PER_REQUEST)
                .withCreateTableRequests(request)
                .withStreamsEnabled(false)
                .withPrecreateTables(true)
                .withTablePrefix(tablePrefix)
                .withAmazonDynamoDb(LOCAL_DYNAMO_DB)
                .withContext(MT_CONTEXT)
                .build();

        DynamoDbTestUtils.assertPayPerRequestIsSet(DynamoDbTestUtils.getTableNameWithPrefix(tablePrefix, tableName,
                ""), LOCAL_DYNAMO_DB);
    }

    @Test
    void testBillingModeIsPayPerRequestForDefaultCreateTableRequestsWithPayPerRequestInputBillingMode() {
        SharedTableBuilder.builder()
                .withBillingMode(BillingMode.PAY_PER_REQUEST)
                .withAmazonDynamoDb(LOCAL_DYNAMO_DB)
                .withTablePrefix(tablePrefix)
                .withPrecreateTables(true)
                .withContext(MT_CONTEXT)
                .build();

        for (String table: getPrefixedTables()) {
            DynamoDbTestUtils.assertPayPerRequestIsSet(table, LOCAL_DYNAMO_DB);
        }
    }

    private List<String> getPrefixedTables() {
        return testTables.stream().map(testTable -> tablePrefix + testTable)
                .collect(Collectors.toList());
    }

}