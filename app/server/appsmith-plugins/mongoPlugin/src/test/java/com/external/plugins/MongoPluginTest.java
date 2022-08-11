package com.external.plugins;

import com.appsmith.external.dtos.ExecuteActionDTO;
import com.appsmith.external.exceptions.pluginExceptions.AppsmithPluginError;
import com.appsmith.external.exceptions.pluginExceptions.AppsmithPluginException;
import com.appsmith.external.exceptions.pluginExceptions.StaleConnectionException;
import com.appsmith.external.helpers.PluginUtils;
import com.appsmith.external.models.ActionConfiguration;
import com.appsmith.external.models.ActionExecutionRequest;
import com.appsmith.external.models.ActionExecutionResult;
import com.appsmith.external.models.Connection;
import com.appsmith.external.models.DBAuth;
import com.appsmith.external.models.DatasourceConfiguration;
import com.appsmith.external.models.DatasourceStructure;
import com.appsmith.external.models.DatasourceTestResult;
import com.appsmith.external.models.Endpoint;
import com.appsmith.external.models.Param;
import com.appsmith.external.models.ParsedDataType;
import com.appsmith.external.models.Property;
import com.appsmith.external.models.RequestParamDTO;
import com.appsmith.external.models.SSLDetails;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.DBRef;
import com.mongodb.MongoCommandException;
import com.mongodb.MongoSecurityException;
import com.mongodb.MongoSocketWriteException;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import org.bson.Document;
import org.bson.types.BSONTimestamp;
import org.bson.types.Decimal128;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static com.appsmith.external.constants.ActionConstants.ACTION_CONFIGURATION_BODY;
import static com.appsmith.external.constants.DisplayDataType.JSON;
import static com.appsmith.external.constants.DisplayDataType.RAW;
import static com.appsmith.external.helpers.PluginUtils.OBJECT_TYPE;
import static com.appsmith.external.helpers.PluginUtils.STRING_TYPE;
import static com.appsmith.external.helpers.PluginUtils.getDataValueSafelyFromFormData;
import static com.appsmith.external.helpers.PluginUtils.setDataValueSafelyInFormData;
import static com.external.plugins.constants.FieldName.AGGREGATE_LIMIT;
import static com.external.plugins.constants.FieldName.AGGREGATE_PIPELINES;
import static com.external.plugins.constants.FieldName.BODY;
import static com.external.plugins.constants.FieldName.COLLECTION;
import static com.external.plugins.constants.FieldName.COMMAND;
import static com.external.plugins.constants.FieldName.COUNT_QUERY;
import static com.external.plugins.constants.FieldName.DELETE_LIMIT;
import static com.external.plugins.constants.FieldName.DELETE_QUERY;
import static com.external.plugins.constants.FieldName.DISTINCT_KEY;
import static com.external.plugins.constants.FieldName.DISTINCT_QUERY;
import static com.external.plugins.constants.FieldName.FIND_LIMIT;
import static com.external.plugins.constants.FieldName.FIND_PROJECTION;
import static com.external.plugins.constants.FieldName.FIND_QUERY;
import static com.external.plugins.constants.FieldName.FIND_SORT;
import static com.external.plugins.constants.FieldName.INSERT_DOCUMENT;
import static com.external.plugins.constants.FieldName.SMART_SUBSTITUTION;
import static com.external.plugins.constants.FieldName.UPDATE_LIMIT;
import static com.external.plugins.constants.FieldName.UPDATE_OPERATION;
import static com.external.plugins.constants.FieldName.UPDATE_QUERY;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for MongoPlugin
 */

public class MongoPluginTest {
    MongoPlugin.MongoPluginExecutor pluginExecutor = new MongoPlugin.MongoPluginExecutor();

    private static String address;
    private static Integer port;
    private JsonNode value;
    private static MongoClient mongoClient;

    @SuppressWarnings("rawtypes")
    @ClassRule
    public static GenericContainer mongoContainer = new GenericContainer(CompletableFuture.completedFuture("mongo:4.4"))
            .withExposedPorts(27017);

    @BeforeClass
    public static void setUp() {
        address = mongoContainer.getContainerIpAddress();
        port = mongoContainer.getFirstMappedPort();
        String uri = "mongodb://" + address + ":" + port;
        mongoClient = MongoClients.create(uri);

        Flux.from(mongoClient.getDatabase("test").listCollectionNames()).collectList().
                flatMap(collectionNamesList -> {
                    if (collectionNamesList.size() == 0) {
                        final MongoCollection<Document> usersCollection = mongoClient.getDatabase("test").getCollection(
                                "users");
                        Mono.from(usersCollection.insertMany(List.of(
                                new Document(Map.of(
                                        "name", "Cierra Vega",
                                        "gender", "F",
                                        "age", 20,
                                        "luckyNumber", 987654321L,
                                        "dob", LocalDate.of(2018, 12, 31),
                                        "netWorth", new BigDecimal("123456.789012"),
                                        "updatedByCommand", false
                                )),
                                new Document(Map.of(
                                        "name", "Alden Cantrell",
                                        "gender", "M",
                                        "age", 30,
                                        "dob", new Date(0),
                                        "netWorth", Decimal128.parse("123456.789012"),
                                        "updatedByCommand", false,
                                        "aLong", 9_000_000_000_000_000_000L,
                                        "ts", new BSONTimestamp(1421006159, 4)
                                )),
                                new Document(Map.of("name", "Kierra Gentry", "gender", "F", "age", 40))
                        ))).block();


                        final MongoCollection<Document> addressCollection = mongoClient.getDatabase("test")
                                .getCollection("address");
                        Mono.from(addressCollection.insertMany(List.of(
                                new Document(Map.of(
                                        "user", new DBRef("test", "users", "1"),
                                        "street", "First Street",
                                        "city", "Line One",
                                        "state", "UP"
                                )),
                                new Document(Map.of(
                                        "user", new DBRef("AAA", "BBB", "2000"),
                                        "street", "Second Street",
                                        "city", "Line Two",
                                        "state", "UP"
                                ))
                        ))).block();
                    }
                    return Mono.empty();
                }).block();
    }

    private DatasourceConfiguration createDatasourceConfiguration() {
        Endpoint endpoint = new Endpoint();
        endpoint.setHost(address);
        endpoint.setPort(port.longValue());

        Connection connection = new Connection();
        connection.setMode(Connection.Mode.READ_WRITE);
        connection.setType(Connection.Type.DIRECT);
        connection.setDefaultDatabaseName("test");
        connection.setSsl(new SSLDetails());
        connection.getSsl().setAuthType(SSLDetails.AuthType.DEFAULT);

        DatasourceConfiguration dsConfig = new DatasourceConfiguration();
        dsConfig.setConnection(connection);
        dsConfig.setEndpoints(List.of(endpoint));

        return dsConfig;
    }

    @Test
    public void testConnectToMongo() {
        DatasourceConfiguration dsConfig = createDatasourceConfiguration();

        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(dsConfig);
        StepVerifier.create(dsConnectionMono)
                .assertNext(obj -> {
                    MongoClient client = obj;
                    assertNotNull(client);
                })
                .verifyComplete();
    }

    @Test
    public void testConnectToMongoWithoutUsername() {
        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        dsConfig.setAuthentication(new DBAuth(DBAuth.Type.SCRAM_SHA_1, "", "", "admin"));
        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(dsConfig);
        StepVerifier.create(dsConnectionMono)
                .assertNext(Assert::assertNotNull)
                .verifyComplete();
    }

    /**
     * 1. Test "testDatasource" method in MongoPluginExecutor class.
     */
    @Test
    public void testDatasourceFail() {
        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        dsConfig.getEndpoints().get(0).setHost("badHost");

        StepVerifier.create(pluginExecutor.testDatasource(dsConfig))
                .assertNext(datasourceTestResult -> {
                    assertNotNull(datasourceTestResult);
                    assertFalse(datasourceTestResult.isSuccess());
                })
                .verifyComplete();
    }
    @Test
    public void testDatasourceFailWithInvalidDefaultDatabaseName() {
        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        dsConfig.getConnection().setDefaultDatabaseName("abcd");
        StepVerifier.create(pluginExecutor.testDatasource(dsConfig))
                .assertNext(datasourceTestResult -> {
                    assertNotNull(datasourceTestResult);
                    assertFalse(datasourceTestResult.isSuccess());
                })
                .verifyComplete();
    }

    /*
     * 1. Test that when a query is attempted to run on mongodb but refused because of lack of authorization.
     */
    @Test
    public void testDatasourceWithUnauthorizedException() {
        /*
         * 1. Create mock exception of type: MongoCommandException.
         *      - mock method getErrorCodeName() to return String "Unauthorized".
         */
        MongoCommandException mockMongoCommandException = mock(MongoCommandException.class);
        when(mockMongoCommandException.getErrorCodeName()).thenReturn("Unauthorized");
        when(mockMongoCommandException.getMessage()).thenReturn("Mock Unauthorized Exception");
        when(mockMongoCommandException.getErrorMessage()).thenReturn("Mock error  : Expected 'something' , but got something else.\n" +
                "Doc = [{find mockAction} {filter mockFilter} {limit 10} {$db mockDB} ...]");

        /*
         * 1. Spy MongoPluginExecutor class.
         *      - On calling testDatasource(...) -> call the real method.
         *      - On calling datasourceCreate(...) -> throw the mock exception defined above.
         */
        MongoPlugin.MongoPluginExecutor mongoPluginExecutor = new MongoPlugin.MongoPluginExecutor();
        MongoPlugin.MongoPluginExecutor spyMongoPluginExecutor = spy(mongoPluginExecutor);
        /* Please check this out before modifying this line: https://stackoverflow
         * .com/questions/11620103/mockito-trying-to-spy-on-method-is-calling-the-original-method
         */
        doReturn(Mono.error(mockMongoCommandException)).when(spyMongoPluginExecutor).datasourceCreate(any());

        /*
         * 1. Test that MongoCommandException with error code "Unauthorized" is not successful because of invalid credentials.
         */
        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        StepVerifier
                .create(spyMongoPluginExecutor.testDatasource(dsConfig))
                .assertNext(datasourceTestResult -> {
                    assertFalse(datasourceTestResult.isSuccess());
                })
                .verifyComplete();
    }

    /**
     * Test for DBRef after codec implementation
     */
    @Test
    public void testExecuteQueryDBRef() {
        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(dsConfig);

        ActionConfiguration actionConfiguration = new ActionConfiguration();

        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.TRUE);
        setDataValueSafelyInFormData(configMap, COMMAND, "RAW");
        setDataValueSafelyInFormData(configMap, BODY, "{\n" +
                "      find: \"address\",\n" +
                "      limit: 10,\n" +
                "    }");
        actionConfiguration.setFormData(configMap);

        Mono<Object> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn, new ExecuteActionDTO(), dsConfig, actionConfiguration));

        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = (ActionExecutionResult) obj;
                    assertNotNull(result);
                    assertTrue(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());

                    assertEquals(2, ((ArrayNode) result.getBody()).size());

                    /*
                     * Provided Input : new DBRef("test", "users", "1")
                     * To test if we are getting the expected output after external codec implementation.
                     * Note: when the codec is removed from the MongoDBPlugin, this is found failing
                     */
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        String expectedOutputJsonString = "{\"$db\":\"test\",\"$ref\":\"users\",\"$id\":\"1\"}";
                        JsonNode outputNode = mapper.readTree(expectedOutputJsonString);
                        assertEquals(outputNode, (((ArrayNode) result.getBody()).findValue("user")));
                    } catch (JsonProcessingException e) {
                        assert false;
                    }

                })
                .verifyComplete();
    }


    @Test
    public void testExecuteReadQuery() {
        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(dsConfig);

        ActionConfiguration actionConfiguration = new ActionConfiguration();

        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.TRUE);
        setDataValueSafelyInFormData(configMap, COMMAND, "RAW");
        setDataValueSafelyInFormData(configMap, BODY, "{\n" +
                "      find: \"users\",\n" +
                "      filter: { \"age\": { \"$gte\": 30 } },\n" +
                "      sort: { id: 1 },\n" +
                "      limit: 10,\n" +
                "    }");
        actionConfiguration.setFormData(configMap);

        Mono<Object> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn, new ExecuteActionDTO(), dsConfig, actionConfiguration));

        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = (ActionExecutionResult) obj;
                    assertNotNull(result);
                    assertTrue(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());
                    assertEquals(2, ((ArrayNode) result.getBody()).size());
                    assertEquals(
                            List.of(new ParsedDataType(JSON), new ParsedDataType(RAW)).toString(),
                            result.getDataTypes().toString()
                    );


                    /*
                     * - RequestParamDTO object only have attributes configProperty and value at this point.
                     * - The other two RequestParamDTO attributes - label and type are null at this point.
                     */
                    List<RequestParamDTO> expectedRequestParams = new ArrayList<>();
                    expectedRequestParams.add(new RequestParamDTO(ACTION_CONFIGURATION_BODY,
                            PluginUtils.getDataValueSafelyFromFormData(actionConfiguration.getFormData(), BODY, OBJECT_TYPE), null, null, null));
                    assertEquals(result.getRequest().getRequestParams().toString(), expectedRequestParams.toString());
                })
                .verifyComplete();
    }

    @Test
    public void testExecuteInvalidReadQuery() {
        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(dsConfig);

        ActionConfiguration actionConfiguration = new ActionConfiguration();

        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.TRUE);
        setDataValueSafelyInFormData(configMap, COMMAND, "RAW");
        setDataValueSafelyInFormData(configMap, BODY, "{\n" +
                "      find: \"users\",\n" +
                "      filter: { $is: {} },\n" +
                "      sort: { id: 1 },\n" +
                "      limit: 10,\n" +
                "    }");
        actionConfiguration.setFormData(configMap);

        Mono<Object> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn, new ExecuteActionDTO(), dsConfig, actionConfiguration));

        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = (ActionExecutionResult) obj;
                    assertNotNull(result);
                    assertFalse(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());
                    assertEquals("unknown top level operator: $is", result.getBody());
                    assertEquals(AppsmithPluginError.PLUGIN_EXECUTE_ARGUMENT_ERROR.getTitle(), result.getTitle());

                    /*
                     * - RequestParamDTO object only have attributes configProperty and value at this point.
                     * - The other two RequestParamDTO attributes - label and type are null at this point.
                     */
                    List<RequestParamDTO> expectedRequestParams = new ArrayList<>();
                    expectedRequestParams.add(new RequestParamDTO(ACTION_CONFIGURATION_BODY,
                            PluginUtils.getDataValueSafelyFromFormData(actionConfiguration.getFormData(), BODY, OBJECT_TYPE), null, null, null));
                    assertEquals(result.getRequest().getRequestParams().toString(), expectedRequestParams.toString());
                })
                .verifyComplete();
    }

    @Test
    public void testExecuteWriteQuery() {
        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(dsConfig);

        ActionConfiguration actionConfiguration = new ActionConfiguration();

        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.TRUE);
        setDataValueSafelyInFormData(configMap, COMMAND, "RAW");
        setDataValueSafelyInFormData(configMap, BODY, "{\n" +
                "      insert: \"users\",\n" +
                "      documents: [\n" +
                "        {\n" +
                "          name: \"John Smith\",\n" +
                "          email: [\"john@appsmith.com](mailto:%22john@appsmith.com)\"],\n" +
                "          gender: \"M\",\n" +
                "          age: \"50\",\n" +
                "        },\n" +
                "      ],\n" +
                "    }");
        actionConfiguration.setFormData(configMap);

        Mono<Object> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn, new ExecuteActionDTO(), dsConfig, actionConfiguration));

        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = (ActionExecutionResult) obj;
                    assertNotNull(result);
                    assertTrue(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());
                    assertEquals(
                            List.of(new ParsedDataType(JSON), new ParsedDataType(RAW)).toString(),
                            result.getDataTypes().toString()
                    );
                })
                .verifyComplete();

        // Clean up this newly inserted value
        configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.FALSE);
        setDataValueSafelyInFormData(configMap, COMMAND, "DELETE");
        setDataValueSafelyInFormData(configMap, COLLECTION, "users");
        setDataValueSafelyInFormData(configMap, DELETE_QUERY, "{\"name\": \"John Smith\"}");
        setDataValueSafelyInFormData(configMap, DELETE_LIMIT, "SINGLE");

        actionConfiguration.setFormData(configMap);
        // Run the delete command
        dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn, new ExecuteActionDTO(), dsConfig, actionConfiguration)).block();
    }

    @Test
    public void testFindAndModify() {
        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(dsConfig);

        ActionConfiguration actionConfiguration = new ActionConfiguration();

        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.TRUE);
        setDataValueSafelyInFormData(configMap, COMMAND, "RAW");
        setDataValueSafelyInFormData(configMap, BODY, "{\n" +
                "  findAndModify: \"users\",\n" +
                "  query: " +
                "{ " +
                "name: \"Alden Cantrell\"" +
                " },\n" +
                "  update: { $set: { gender: \"F\" }}\n" +
                "}");
        actionConfiguration.setFormData(configMap);

        Mono<Object> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn, new ExecuteActionDTO(), dsConfig, actionConfiguration));

        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = (ActionExecutionResult) obj;
                    assertNotNull(result);
                    assertTrue(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());
                    value = ((ObjectNode) result.getBody()).get("value");
                    assertNotNull(value);
                    assertEquals("M", value.get("gender").asText());
                    assertEquals("Alden Cantrell", value.get("name").asText());
                    assertEquals(30, value.get("age").asInt());
                    assertEquals(
                            List.of(new ParsedDataType(JSON), new ParsedDataType(RAW)).toString(),
                            result.getDataTypes().toString()
                    );
                })
                .verifyComplete();
    }

    @Test
    public void testCleanUp() {
        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(dsConfig);

        ActionConfiguration actionConfiguration = new ActionConfiguration();

        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.TRUE);
        setDataValueSafelyInFormData(configMap, COMMAND, "RAW");
        setDataValueSafelyInFormData(configMap, BODY, "{\n" +
                "      find: \"users\",\n" +
                "      limit: 1,\n" +
                "    }");
        actionConfiguration.setFormData(configMap);

        Mono<Object> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn, new ExecuteActionDTO(), dsConfig, actionConfiguration));

        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = (ActionExecutionResult) obj;
                    assertNotNull(result);
                    assertTrue(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());
                    final ArrayNode body = (ArrayNode) result.getBody();
                    assertEquals(1, body.size());
                    final JsonNode node = body.get(0);
                    assertTrue(node.get("_id").isTextual());
                    assertTrue(node.get("luckyNumber").isNumber());
                    assertEquals("2018-12-31T00:00:00Z", node.get("dob").asText());
                    assertEquals("123456.789012", node.get("netWorth").toString());
                    assertEquals(
                            List.of(new ParsedDataType(JSON), new ParsedDataType(RAW)).toString(),
                            result.getDataTypes().toString()
                    );
                })
                .verifyComplete();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testStructure() {
        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        Mono<DatasourceStructure> structureMono = pluginExecutor.datasourceCreate(dsConfig)
                .flatMap(connection -> pluginExecutor.getStructure(connection, dsConfig));

        StepVerifier.create(structureMono)
                .assertNext(structure -> {
                    //Sort the Tables since one more table is added and to maintain sequence
                    structure.getTables().sort(
                            (DatasourceStructure.Table t1, DatasourceStructure.Table t2)
                                    ->t2.getName().compareTo(t1.getName())
                    );
                    assertNotNull(structure);
                    assertEquals(2, structure.getTables().size());

                    final DatasourceStructure.Table usersTable = structure.getTables().get(0);
                    assertEquals("users", usersTable.getName());
                    assertEquals(DatasourceStructure.TableType.COLLECTION, usersTable.getType());
                    assertArrayEquals(
                            new DatasourceStructure.Column[]{
                                    new DatasourceStructure.Column("_id", "ObjectId", null, true),
                                    new DatasourceStructure.Column("age", "Integer", null, false),
                                    new DatasourceStructure.Column("dob", "Date", null, false),
                                    new DatasourceStructure.Column("gender", "String", null, false),
                                    new DatasourceStructure.Column("luckyNumber", "Long", null, false),
                                    new DatasourceStructure.Column("name", "String", null, false),
                                    new DatasourceStructure.Column("netWorth", "BigDecimal", null, false),
                                    new DatasourceStructure.Column("updatedByCommand", "Object", null, false),
                            },
                            usersTable.getColumns().toArray()
                    );

                    assertArrayEquals(
                            new DatasourceStructure.Key[]{},
                            usersTable.getKeys().toArray()
                    );
                    List<DatasourceStructure.Template> templates = usersTable.getTemplates();

                    //Assert Find command
                    DatasourceStructure.Template findTemplate = templates.get(0);
                    assertEquals("Find", findTemplate.getTitle());
                    assertEquals("{\n" +
                                    "  \"find\": \"users\",\n" +
                                    "  \"filter\": {\n" +
                                    "    \"gender\": \"F\"\n" +
                                    "  },\n" +
                                    "  \"sort\": {\n" +
                                    "    \"_id\": 1\n" +
                                    "  },\n" +
                                    "  \"limit\": 10\n" +
                                    "}\n",
                            PluginUtils.getDataValueSafelyFromFormData((Map<String, Object>) findTemplate.getConfiguration(), BODY, STRING_TYPE));
                    assertEquals("FIND", PluginUtils.getDataValueSafelyFromFormData((Map<String, Object>) findTemplate.getConfiguration(), COMMAND, STRING_TYPE));

                    assertEquals("{ \"gender\": \"F\"}",
                            PluginUtils.getDataValueSafelyFromFormData((Map<String, Object>) findTemplate.getConfiguration(), FIND_QUERY, STRING_TYPE));
                    assertEquals("{\"_id\": 1}",
                            PluginUtils.getDataValueSafelyFromFormData((Map<String, Object>) findTemplate.getConfiguration(), FIND_SORT, STRING_TYPE));

                    //Assert Find By Id command
                    DatasourceStructure.Template findByIdTemplate = templates.get(1);
                    assertEquals("Find by ID", findByIdTemplate.getTitle());
                    assertEquals("{\n" +
                                    "  \"find\": \"users\",\n" +
                                    "  \"filter\": {\n" +
                                    "    \"_id\": ObjectId(\"id_to_query_with\")\n" +
                                    "  }\n" +
                                    "}\n",
                            PluginUtils.getDataValueSafelyFromFormData((Map<String, Object>) findByIdTemplate.getConfiguration(), BODY, STRING_TYPE));
                    assertEquals("FIND", PluginUtils.getDataValueSafelyFromFormData((Map<String, Object>) findByIdTemplate.getConfiguration(), COMMAND, STRING_TYPE));
                    assertEquals("{\"_id\": ObjectId(\"id_to_query_with\")}",
                            PluginUtils.getDataValueSafelyFromFormData((Map<String, Object>) findByIdTemplate.getConfiguration(), FIND_QUERY, STRING_TYPE));

                    // Assert Insert command
                    DatasourceStructure.Template insertTemplate = templates.get(2);
                    assertEquals("Insert", insertTemplate.getTitle());
                    assertEquals("{\n" +
                                    "  \"insert\": \"users\",\n" +
                                    "  \"documents\": [\n" +
                                    "    {\n" +
                                    "      \"_id\": ObjectId(\"a_valid_object_id_hex\"),\n" +
                                    "      \"age\": 1,\n" +
                                    "      \"dob\": new Date(\"2019-07-01\"),\n" +
                                    "      \"gender\": \"new value\",\n" +
                                    "      \"luckyNumber\": NumberLong(\"1\"),\n" +
                                    "      \"name\": \"new value\",\n" +
                                    "      \"netWorth\": NumberDecimal(\"1\"),\n" +
                                    "      \"updatedByCommand\": {},\n" +
                                    "    }\n" +
                                    "  ]\n" +
                                    "}\n",
                            PluginUtils.getDataValueSafelyFromFormData((Map<String, Object>) insertTemplate.getConfiguration(), BODY, STRING_TYPE));
                    assertEquals("INSERT", PluginUtils.getDataValueSafelyFromFormData((Map<String, Object>) insertTemplate.getConfiguration(), COMMAND, STRING_TYPE));
                    assertEquals("[{      \"_id\": ObjectId(\"a_valid_object_id_hex\"),\n" +
                                    "      \"age\": 1,\n" +
                                    "      \"dob\": new Date(\"2019-07-01\"),\n" +
                                    "      \"gender\": \"new value\",\n" +
                                    "      \"luckyNumber\": NumberLong(\"1\"),\n" +
                                    "      \"name\": \"new value\",\n" +
                                    "      \"netWorth\": NumberDecimal(\"1\"),\n" +
                                    "      \"updatedByCommand\": {},\n" +
                                    "}]",
                            PluginUtils.getDataValueSafelyFromFormData((Map<String, Object>) insertTemplate.getConfiguration(), INSERT_DOCUMENT, STRING_TYPE));

                    // Assert Update command
                    DatasourceStructure.Template updateTemplate = templates.get(3);
                    assertEquals("Update", updateTemplate.getTitle());
                    assertEquals("{\n" +
                            "  \"update\": \"users\",\n" +
                            "  \"updates\": [\n" +
                            "    {\n" +
                            "      \"q\": {\n" +
                            "        \"_id\": ObjectId(\"id_of_document_to_update\")\n" +
                            "      },\n" +
                            "      \"u\": { \"$set\": { \"gender\": \"new value\" } }\n" +
                            "    }\n" +
                            "  ]\n" +
                            "}\n", PluginUtils.getDataValueSafelyFromFormData((Map<String, Object>) updateTemplate.getConfiguration(), BODY, STRING_TYPE));
                    assertEquals("UPDATE", PluginUtils.getDataValueSafelyFromFormData((Map<String, Object>) updateTemplate.getConfiguration(), COMMAND, STRING_TYPE));
                    assertEquals("{ \"_id\": ObjectId(\"id_of_document_to_update\") }",
                            PluginUtils.getDataValueSafelyFromFormData((Map<String, Object>) updateTemplate.getConfiguration(), UPDATE_QUERY, STRING_TYPE));
                    assertEquals("{ \"$set\": { \"gender\": \"new value\" } }",
                            PluginUtils.getDataValueSafelyFromFormData((Map<String, Object>) updateTemplate.getConfiguration(), UPDATE_OPERATION, STRING_TYPE));

                    // Assert Delete Command
                    DatasourceStructure.Template deleteTemplate = templates.get(4);
                    assertEquals("Delete", deleteTemplate.getTitle());
                    assertEquals("{\n" +
                            "  \"delete\": \"users\",\n" +
                            "  \"deletes\": [\n" +
                            "    {\n" +
                            "      \"q\": {\n" +
                            "        \"_id\": \"id_of_document_to_delete\"\n" +
                            "      },\n" +
                            "      \"limit\": 1\n" +
                            "    }\n" +
                            "  ]\n" +
                            "}\n", PluginUtils.getDataValueSafelyFromFormData((Map<String, Object>) deleteTemplate.getConfiguration(), BODY, STRING_TYPE));
                    assertEquals("DELETE", PluginUtils.getDataValueSafelyFromFormData((Map<String, Object>) deleteTemplate.getConfiguration(), COMMAND, STRING_TYPE));
                    assertEquals("{ \"_id\": ObjectId(\"id_of_document_to_delete\") }",
                            PluginUtils.getDataValueSafelyFromFormData((Map<String, Object>) deleteTemplate.getConfiguration(), DELETE_QUERY, STRING_TYPE));
                    assertEquals("SINGLE",
                            PluginUtils.getDataValueSafelyFromFormData((Map<String, Object>) deleteTemplate.getConfiguration(), DELETE_LIMIT, STRING_TYPE));

                    // Assert Count Command
                    DatasourceStructure.Template countTemplate = templates.get(5);
                    assertEquals("Count", countTemplate.getTitle());
                    assertEquals("{\n" +
                            "  \"count\": \"users\",\n" +
                            "  \"query\": " + "{\"_id\": {\"$exists\": true}} \n" +
                            "}\n", PluginUtils.getDataValueSafelyFromFormData((Map<String, Object>) countTemplate.getConfiguration(), BODY, STRING_TYPE));
                    assertEquals("COUNT", PluginUtils.getDataValueSafelyFromFormData((Map<String, Object>) countTemplate.getConfiguration(), COMMAND, STRING_TYPE));
                    assertEquals("{\"_id\": {\"$exists\": true}}",
                            PluginUtils.getDataValueSafelyFromFormData((Map<String, Object>) countTemplate.getConfiguration(), COUNT_QUERY, STRING_TYPE));

                    // Assert Distinct Command
                    DatasourceStructure.Template distinctTemplate = templates.get(6);
                    assertEquals("Distinct", distinctTemplate.getTitle());
                    assertEquals("{\n" +
                            "  \"distinct\": \"users\",\n" +
                            "  \"query\": { \"_id\": ObjectId(\"id_of_document_to_distinct\") }," +
                            "  \"key\": \"_id\"," +
                            "}\n", PluginUtils.getDataValueSafelyFromFormData((Map<String, Object>) distinctTemplate.getConfiguration(), BODY, STRING_TYPE));
                    assertEquals("DISTINCT", PluginUtils.getDataValueSafelyFromFormData((Map<String, Object>) distinctTemplate.getConfiguration(), COMMAND, STRING_TYPE));
                    assertEquals("{ \"_id\": ObjectId(\"id_of_document_to_distinct\") }",
                            PluginUtils.getDataValueSafelyFromFormData((Map<String, Object>) distinctTemplate.getConfiguration(), DISTINCT_QUERY, STRING_TYPE));
                    assertEquals("_id",
                            PluginUtils.getDataValueSafelyFromFormData((Map<String, Object>) distinctTemplate.getConfiguration(), DISTINCT_KEY, STRING_TYPE));

                    // Assert Aggregate Command
                    DatasourceStructure.Template aggregateTemplate = templates.get(7);
                    assertEquals("Aggregate", aggregateTemplate.getTitle());
                    assertEquals("{\n" +
                            "  \"aggregate\": \"users\",\n" +
                            "  \"pipeline\": " + "[ {\"$sort\" : {\"_id\": 1} } ],\n" +
                            "  \"limit\": 10,\n" +
                            "  \"explain\": \"true\"\n" +
                            "}\n", PluginUtils.getDataValueSafelyFromFormData((Map<String, Object>) aggregateTemplate.getConfiguration(), BODY, STRING_TYPE));

                    assertEquals("AGGREGATE", PluginUtils.getDataValueSafelyFromFormData((Map<String, Object>) aggregateTemplate.getConfiguration(), COMMAND, STRING_TYPE));
                    assertEquals("[ {\"$sort\" : {\"_id\": 1} } ]",
                            PluginUtils.getDataValueSafelyFromFormData((Map<String, Object>) aggregateTemplate.getConfiguration(), AGGREGATE_PIPELINES, STRING_TYPE));


                })
                .verifyComplete();
    }

    @Test
    public void testErrorMessageOnSrvUriWithFormInterface() {
        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        dsConfig.getEndpoints().get(0).setHost("mongodb+srv://user:pass@url.net/dbName");
        dsConfig.setProperties(List.of(new Property("Import from URI", "No")));
        Mono<Set<String>> invalidsMono = Mono.just(pluginExecutor.validateDatasource(dsConfig));

        StepVerifier.create(invalidsMono)
                .assertNext(invalids -> {
                    assertTrue(invalids
                            .stream()
                            .anyMatch(error -> error.contains("It seems that you are trying to use a mongo connection" +
                                    " string URI. Please extract relevant fields and fill the form with extracted " +
                                    "values. For details, please check out the Appsmith's documentation for Mongo " +
                                    "database. Alternatively, you may use 'Import from Connection String URI' option " +
                                    "from the dropdown labelled 'Use Mongo Connection String URI' to use the URI " +
                                    "connection string directly.")));
                })
                .verifyComplete();
    }

    @Test
    public void testErrorMessageOnNonSrvUri() {
        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        List<Endpoint> endpoints = new ArrayList<>();
        endpoints.add(new Endpoint("url", 123L));
        endpoints.add(null);
        endpoints.add(new Endpoint(null, 123L));
        endpoints.add(new Endpoint("mongodb://user:pass@url.net:1234,url.net:1234/dbName", 123L));
        dsConfig.setEndpoints(endpoints);
        dsConfig.setProperties(List.of(new Property("Import from URI", "No")));
        Mono<Set<String>> invalidsMono = Mono.just(pluginExecutor.validateDatasource(dsConfig));

        StepVerifier.create(invalidsMono)
                .assertNext(invalids -> {
                    assertTrue(invalids
                            .stream()
                            .anyMatch(error -> error.contains("It seems that you are trying to use a mongo connection" +
                                    " string URI. Please extract relevant fields and fill the form with extracted " +
                                    "values. For details, please check out the Appsmith's documentation for Mongo " +
                                    "database. Alternatively, you may use 'Import from Connection String URI' option " +
                                    "from the dropdown labelled 'Use Mongo Connection String URI' to use the URI " +
                                    "connection string directly.")));
                })
                .verifyComplete();
    }

    @Test
    public void testInvalidsOnMissingUri() {
        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        dsConfig.setProperties(List.of(new Property("Import from URI", "Yes")));
        Mono<Set<String>> invalidsMono = Mono.just(pluginExecutor.validateDatasource(dsConfig));

        StepVerifier.create(invalidsMono)
                .assertNext(invalids -> {
                    assertTrue(invalids
                            .stream()
                            .anyMatch(error -> error.contains("'Mongo Connection String URI' field is empty. Please " +
                                    "edit the 'Mongo Connection URI' field to provide a connection uri to connect with.")));
                })
                .verifyComplete();
    }

    @Test
    public void testInvalidsOnBadSrvUriFormat() {
        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        List<Property> properties = new ArrayList<>();
        properties.add(new Property("Import from URI", "Yes"));
        properties.add(new Property("Srv Url", "mongodb+srv::username:password//url.net"));
        dsConfig.setProperties(properties);
        Mono<Set<String>> invalidsMono = Mono.just(pluginExecutor.validateDatasource(dsConfig));

        StepVerifier.create(invalidsMono)
                .assertNext(invalids -> {
                    assertTrue(invalids
                            .stream()
                            .anyMatch(error -> error.contains("Mongo Connection String URI does not seem to be in the" +
                                    " correct format. Please check the URI once.")));
                })
                .verifyComplete();
    }

    @Test
    public void testInvalidsOnBadNonSrvUriFormat() {
        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        List<Property> properties = new ArrayList<>();
        properties.add(new Property("Import from URI", "Yes"));
        properties.add(new Property("Srv Url", "mongodb::username:password//url.net"));
        dsConfig.setProperties(properties);
        Mono<Set<String>> invalidsMono = Mono.just(pluginExecutor.validateDatasource(dsConfig));

        StepVerifier.create(invalidsMono)
                .assertNext(invalids -> {
                    assertTrue(invalids
                            .stream()
                            .anyMatch(error -> error.contains("Mongo Connection String URI does not seem to be in the" +
                                    " correct format. Please check the URI once.")));
                })
                .verifyComplete();
    }

    @Test
    public void testInvalidsEmptyOnCorrectSrvUriFormat() {
        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        List<Property> properties = new ArrayList<>();
        properties.add(new Property("Import from URI", "Yes"));
        properties.add(new Property("Srv Url", "mongodb+srv://username:password@url.net/dbname"));
        dsConfig.setProperties(properties);
        Mono<Set<String>> invalidsMono = Mono.just(pluginExecutor.validateDatasource(dsConfig));

        StepVerifier.create(invalidsMono)
                .assertNext(invalids -> {
                    assertTrue(invalids.isEmpty());
                })
                .verifyComplete();
    }

    @Test
    public void testInvalidsEmptyOnCorrectNonSrvUriFormat() {
        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        List<Property> properties = new ArrayList<>();
        properties.add(new Property("Import from URI", "Yes"));
        properties.add(new Property("Srv Url", "mongodb://username:password@url-1.net:1234,url-2:1234/dbname"));
        dsConfig.setProperties(properties);
        Mono<Set<String>> invalidsMono = Mono.just(pluginExecutor.validateDatasource(dsConfig));

        StepVerifier.create(invalidsMono)
                .assertNext(invalids -> {
                    assertTrue(invalids.isEmpty());
                })
                .verifyComplete();
    }

    @Test
    public void testTestDatasourceTimeoutError() {
        String badHost = "mongo-bad-url.mongodb.net";
        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        dsConfig.getEndpoints().get(0).setHost(badHost);

        Mono<DatasourceTestResult> datasourceTestResult = pluginExecutor.testDatasource(dsConfig);

        StepVerifier.create(datasourceTestResult)
                .assertNext(result -> {
                    assertFalse(result.isSuccess());
                    assertTrue(result.getInvalids().size() == 1);
                    assertTrue(result
                            .getInvalids()
                            .stream()
                            .anyMatch(error -> error.contains(
                                    "Connection timed out. Please check if the datasource configuration fields have " +
                                            "been filled correctly."
                            )));
                })
                .verifyComplete();
    }

    @Test
    public void testSslToggleMissingError() {
        DatasourceConfiguration datasourceConfiguration = createDatasourceConfiguration();
        datasourceConfiguration.getConnection().getSsl().setAuthType(null);

        Mono<Set<String>> invalidsMono = Mono.just(pluginExecutor)
                .map(executor -> executor.validateDatasource(datasourceConfiguration));


        StepVerifier.create(invalidsMono)
                .assertNext(invalids -> {
                    String expectedError = "Appsmith server has failed to fetch SSL configuration from datasource " +
                            "configuration form. Please reach out to Appsmith customer support to resolve this.";
                    assertTrue(invalids
                            .stream()
                            .anyMatch(error -> expectedError.equals(error))
                    );
                })
                .verifyComplete();
    }

    @Test
    public void testSslDefault() {
        DatasourceConfiguration datasourceConfiguration = createDatasourceConfiguration();
        datasourceConfiguration.getConnection().getSsl().setAuthType(SSLDetails.AuthType.DEFAULT);

        ActionConfiguration actionConfiguration = new ActionConfiguration();

        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.TRUE);
        setDataValueSafelyInFormData(configMap, COMMAND, "RAW");
        setDataValueSafelyInFormData(configMap, BODY, "{\n" +
                "      find: \"users\",\n" +
                "      filter: { age: { $gte: 30 } },\n" +
                "      sort: { id: 1 },\n" +
                "      limit: 10,\n" +
                "    }");
        actionConfiguration.setFormData(configMap);

        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(datasourceConfiguration);
        Mono<Object> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn,
                new ExecuteActionDTO(),
                datasourceConfiguration,
                actionConfiguration));

        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = (ActionExecutionResult) obj;
                    assertNotNull(result);
                    assertTrue(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());
                    assertEquals(2, ((ArrayNode) result.getBody()).size());
                    assertEquals(
                            List.of(new ParsedDataType(JSON), new ParsedDataType(RAW)).toString(),
                            result.getDataTypes().toString()
                    );
                })
                .verifyComplete();
    }

    @Test
    public void testSslDisabled() {
        DatasourceConfiguration datasourceConfiguration = createDatasourceConfiguration();
        datasourceConfiguration.getConnection().getSsl().setAuthType(SSLDetails.AuthType.DISABLED);

        ActionConfiguration actionConfiguration = new ActionConfiguration();

        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.TRUE);
        setDataValueSafelyInFormData(configMap, COMMAND, "RAW");
        setDataValueSafelyInFormData(configMap, BODY, "{\n" +
                "      find: \"users\",\n" +
                "      filter: { age: { $gte: 30 } },\n" +
                "      sort: { id: 1 },\n" +
                "      limit: 10,\n" +
                "    }");
        actionConfiguration.setFormData(configMap);

        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(datasourceConfiguration);
        Mono<Object> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn,
                new ExecuteActionDTO(),
                datasourceConfiguration,
                actionConfiguration));

        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = (ActionExecutionResult) obj;
                    assertNotNull(result);
                    assertTrue(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());
                    assertEquals(2, ((ArrayNode) result.getBody()).size());
                    assertEquals(
                            List.of(new ParsedDataType(JSON), new ParsedDataType(RAW)).toString(),
                            result.getDataTypes().toString()
                    );
                })
                .verifyComplete();
    }

    @Test
    public void testSslEnabled() {
        DatasourceConfiguration datasourceConfiguration = createDatasourceConfiguration();
        datasourceConfiguration.getConnection().getSsl().setAuthType(SSLDetails.AuthType.ENABLED);

        ActionConfiguration actionConfiguration = new ActionConfiguration();

        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.TRUE);
        setDataValueSafelyInFormData(configMap, COMMAND, "RAW");
        setDataValueSafelyInFormData(configMap, BODY, "{\n" +
                "      find: \"users\",\n" +
                "      filter: { age: { $gte: 30 } },\n" +
                "      sort: { id: 1 },\n" +
                "      limit: 10,\n" +
                "    }");
        actionConfiguration.setFormData(configMap);

        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(datasourceConfiguration);
        Mono<ActionExecutionResult> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn,
                new ExecuteActionDTO(),
                datasourceConfiguration,
                actionConfiguration));

        /*
         * - This test case is exactly same as the one's used in DEFAULT and DISABLED tests.
         * - Expect error here because testcontainer does not support SSL connection.
         */
        StepVerifier.create(executeMono)
                .assertNext(result -> {
                    assertFalse(result.getIsExecutionSuccess());
                    assertEquals(AppsmithPluginError.PLUGIN_QUERY_TIMEOUT_ERROR.getTitle(), result.getTitle());
                })
                .verifyComplete();
    }

    @Test
    public void testBsonSmartSubstitution_withBSONValue() {
        DatasourceConfiguration datasourceConfiguration = createDatasourceConfiguration();

        ActionConfiguration actionConfiguration = new ActionConfiguration();

        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.TRUE);
        setDataValueSafelyInFormData(configMap, COMMAND, "RAW");
        setDataValueSafelyInFormData(configMap, BODY, "{\n" +
                "      find: {{Input4.text}},\n" +
                "      filter: \"{{Input1.text}}\",\n" +
                "      sort: { id: {{Input2.text}} },\n" +
                "      limit: {{Input3.text}}\n" +
                "    }");
        actionConfiguration.setFormData(configMap);

        ExecuteActionDTO executeActionDTO = new ExecuteActionDTO();
        List<Param> params = new ArrayList<>();
        Param param1 = new Param();
        param1.setKey("Input1.text");
        param1.setValue("{ age: { \"$gte\": 30 } }");
        params.add(param1);
        Param param3 = new Param();
        param3.setKey("Input2.text");
        param3.setValue("1");
        params.add(param3);
        Param param4 = new Param();
        param4.setKey("Input3.text");
        param4.setValue("10");
        params.add(param4);
        Param param5 = new Param();
        param5.setKey("Input4.text");
        param5.setValue("users");
        params.add(param5);
        executeActionDTO.setParams(params);

        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(datasourceConfiguration);
        Mono<ActionExecutionResult> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn,
                executeActionDTO,
                datasourceConfiguration,
                actionConfiguration));

        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = obj;
                    assertNotNull(result);
                    assertTrue(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());
                    assertEquals(2, ((ArrayNode) result.getBody()).size());

                    // Assert the debug request parameters are getting set.
                    ActionExecutionRequest request = result.getRequest();
                    List<Map.Entry<String, String>> parameters =
                            (List<Map.Entry<String, String>>) request.getProperties().get("smart-substitution-parameters");
                    assertEquals(parameters.size(), 4);

                    Map.Entry<String, String> parameterEntry = parameters.get(0);
                    assertEquals(parameterEntry.getKey(), "users");
                    assertEquals(parameterEntry.getValue(), "STRING");

                    parameterEntry = parameters.get(1);
                    assertEquals(parameterEntry.getKey(), "{ age: { \"$gte\": 30 } }");
                    assertEquals(parameterEntry.getValue(), "BSON");

                    parameterEntry = parameters.get(2);
                    assertEquals(parameterEntry.getKey(), "1");
                    assertEquals(parameterEntry.getValue(), "INTEGER");

                    parameterEntry = parameters.get(3);
                    assertEquals(parameterEntry.getKey(), "10");
                    assertEquals(parameterEntry.getValue(), "INTEGER");

                    assertEquals(
                            List.of(new ParsedDataType(JSON), new ParsedDataType(RAW)).toString(),
                            result.getDataTypes().toString()
                    );

                    String expectedQuery = "{\n" +
                            "      find: \"users\",\n" +
                            "      filter: { age: { \"$gte\": 30 } },\n" +
                            "      sort: { id: 1 },\n" +
                            "      limit: 10\n" +
                            "    }";
                    // check that bindings are not replaced with actual values and not '$i' or '?'
                    assertEquals(expectedQuery,
                            ((RequestParamDTO) (((List) result.getRequest().getRequestParams())).get(0)).getValue());
                })
                .verifyComplete();
    }

    @Test
    public void testBsonSmartSubstitution_withEscapedStringValue() {
        DatasourceConfiguration datasourceConfiguration = createDatasourceConfiguration();

        ActionConfiguration actionConfiguration = new ActionConfiguration();

        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.TRUE);
        setDataValueSafelyInFormData(configMap, COMMAND, "RAW");
        setDataValueSafelyInFormData(configMap, BODY, "{\n" +
                "      find: {{Input4.text}},\n" +
                "      filter: { age: { {{Input1.text}} : 30 } },\n" +
                "      sort: { id: {{Input2.text}} },\n" +
                "      limit: {{Input3.text}}\n" +
                "    }");
        actionConfiguration.setFormData(configMap);

        ExecuteActionDTO executeActionDTO = new ExecuteActionDTO();
        List<Param> params = new ArrayList<>();
        Param param1 = new Param();
        param1.setKey("Input1.text");
        param1.setValue("$gte");
        params.add(param1);
        Param param3 = new Param();
        param3.setKey("Input2.text");
        param3.setValue("1");
        params.add(param3);
        Param param4 = new Param();
        param4.setKey("Input3.text");
        param4.setValue("10");
        params.add(param4);
        Param param5 = new Param();
        param5.setKey("Input4.text");
        param5.setValue("users");
        params.add(param5);
        executeActionDTO.setParams(params);

        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(datasourceConfiguration);
        Mono<ActionExecutionResult> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn,
                executeActionDTO,
                datasourceConfiguration,
                actionConfiguration));

        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = obj;
                    assertNotNull(result);
                    assertTrue(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());
                    assertEquals(2, ((ArrayNode) result.getBody()).size());

                    // Assert the debug request parameters are getting set.
                    ActionExecutionRequest request = result.getRequest();
                    List<Map.Entry<String, String>> parameters =
                            (List<Map.Entry<String, String>>) request.getProperties().get("smart-substitution-parameters");
                    assertEquals(parameters.size(), 4);

                    Map.Entry<String, String> parameterEntry = parameters.get(0);
                    assertEquals(parameterEntry.getKey(), "users");
                    assertEquals(parameterEntry.getValue(), "STRING");

                    parameterEntry = parameters.get(1);
                    assertEquals(parameterEntry.getKey(), "$gte");
                    assertEquals(parameterEntry.getValue(), "STRING");

                    parameterEntry = parameters.get(2);
                    assertEquals(parameterEntry.getKey(), "1");
                    assertEquals(parameterEntry.getValue(), "INTEGER");

                    parameterEntry = parameters.get(3);
                    assertEquals(parameterEntry.getKey(), "10");
                    assertEquals(parameterEntry.getValue(), "INTEGER");

                    assertEquals(
                            List.of(new ParsedDataType(JSON), new ParsedDataType(RAW)).toString(),
                            result.getDataTypes().toString()
                    );

                    String expectedQuery = "{\n" +
                            "      find: \"users\",\n" +
                            "      filter: { age: { \"$gte\" : 30 } },\n" +
                            "      sort: { id: 1 },\n" +
                            "      limit: 10\n" +
                            "    }";
                    // check that bindings are not replaced with actual values and not '$i' or '?'
                    assertEquals(expectedQuery,
                            ((RequestParamDTO) (((List) result.getRequest().getRequestParams())).get(0)).getValue());
                })
                .verifyComplete();
    }

    @Test
    public void testGetStructureReadPermissionError() {
        MongoClient mockConnection = mock(MongoClient.class);
        MongoDatabase mockDatabase = mock(MongoDatabase.class);
        when(mockConnection.getDatabase(any())).thenReturn(mockDatabase);

        MongoCommandException mockMongoCmdException = mock(MongoCommandException.class);
        when(mockDatabase.listCollectionNames()).thenReturn(Mono.error(mockMongoCmdException));
        when(mockMongoCmdException.getErrorCode()).thenReturn(13);

        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        Mono<DatasourceStructure> structureMono = pluginExecutor.datasourceCreate(dsConfig)
                .flatMap(connection -> pluginExecutor.getStructure(mockConnection, dsConfig));

        StepVerifier.create(structureMono)
                .verifyErrorSatisfies(error -> {
                    assertTrue(error instanceof AppsmithPluginException);
                    String expectedMessage = "Appsmith has failed to get database structure. Please provide read permission on" +
                            " the database to fix this.";
                    assertTrue(expectedMessage.equals(error.getMessage()));
                });
    }

    @Test
    public void testFindFormCommand() {
        ActionConfiguration actionConfiguration = new ActionConfiguration();

        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.FALSE);
        setDataValueSafelyInFormData(configMap, COMMAND, "FIND");
        setDataValueSafelyInFormData(configMap, FIND_QUERY, "{ age: { \"$gte\": 30 } }");
        setDataValueSafelyInFormData(configMap, FIND_SORT, "{ id: 1 }");
        setDataValueSafelyInFormData(configMap, COLLECTION, "users");

        actionConfiguration.setFormData(configMap);

        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(dsConfig);
        Mono<Object> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn, new ExecuteActionDTO(), dsConfig, actionConfiguration));
        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = (ActionExecutionResult) obj;
                    assertNotNull(result);
                    assertTrue(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());
                    assertEquals(2, ((ArrayNode) result.getBody()).size());
                    assertEquals(
                            List.of(new ParsedDataType(JSON), new ParsedDataType(RAW)).toString(),
                            result.getDataTypes().toString()
                    );
                })
                .verifyComplete();
    }

    @Test
    public void testInsertFormCommandArrayDocuments() {
        ActionConfiguration actionConfiguration = new ActionConfiguration();

        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.FALSE);
        setDataValueSafelyInFormData(configMap, COMMAND, "INSERT");
        setDataValueSafelyInFormData(configMap, COLLECTION, "users");
        setDataValueSafelyInFormData(configMap, INSERT_DOCUMENT, "[{name : \"ZZZ Insert Form Array Test 1\", gender : \"F\", age : 40, tag : \"test\"}," +
                "{name : \"ZZZ Insert Form Array Test 2\", gender : \"F\", age : 40, tag : \"test\"}" +
                "]");

        actionConfiguration.setFormData(configMap);

        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(dsConfig);
        Mono<Object> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn, new ExecuteActionDTO(), dsConfig, actionConfiguration));
        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = (ActionExecutionResult) obj;
                    assertNotNull(result);
                    assertTrue(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());
                    assertEquals(
                            List.of(new ParsedDataType(JSON), new ParsedDataType(RAW)).toString(),
                            result.getDataTypes().toString()
                    );
                })
                .verifyComplete();

        // Clean up this newly inserted value
        configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.FALSE);
        setDataValueSafelyInFormData(configMap, COMMAND, "DELETE");
        setDataValueSafelyInFormData(configMap, COLLECTION, "users");
        setDataValueSafelyInFormData(configMap, DELETE_QUERY, "{\"tag\" : \"test\"}");
        setDataValueSafelyInFormData(configMap, DELETE_LIMIT, "ALL");

        actionConfiguration.setFormData(configMap);
        // Run the delete command
        dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn, new ExecuteActionDTO(), dsConfig, actionConfiguration)).block();
    }

    @Test
    public void testInsertFormCommandSingleDocument() {
        ActionConfiguration actionConfiguration = new ActionConfiguration();

        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.FALSE);
        setDataValueSafelyInFormData(configMap, COMMAND, "INSERT");
        setDataValueSafelyInFormData(configMap, COLLECTION, "users");
        setDataValueSafelyInFormData(configMap, INSERT_DOCUMENT, "{\"name\" : \"ZZZ Insert Form Single Test\", \"gender\" : \"F\", \"age\" : 40, \"tag\" : \"test\"}");

        actionConfiguration.setFormData(configMap);

        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(dsConfig);
        Mono<Object> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn, new ExecuteActionDTO(), dsConfig, actionConfiguration));
        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = (ActionExecutionResult) obj;
                    assertNotNull(result);
                    assertTrue(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());
                    assertEquals(
                            List.of(new ParsedDataType(JSON), new ParsedDataType(RAW)).toString(),
                            result.getDataTypes().toString()
                    );
                })
                .verifyComplete();

        // Clean up this newly inserted value
        configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.FALSE);
        setDataValueSafelyInFormData(configMap, COMMAND, "DELETE");
        setDataValueSafelyInFormData(configMap, COLLECTION, "users");
        setDataValueSafelyInFormData(configMap, DELETE_QUERY, "{\"tag\" : \"test\"}");
        setDataValueSafelyInFormData(configMap, DELETE_LIMIT, "ALL");

        actionConfiguration.setFormData(configMap);

        // Run the delete command
        dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn, new ExecuteActionDTO(), dsConfig, actionConfiguration)).block();
    }

    @Test
    public void testUpdateOneFormCommand() {
        ActionConfiguration actionConfiguration = new ActionConfiguration();

        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.FALSE);
        setDataValueSafelyInFormData(configMap, COMMAND, "UPDATE");
        setDataValueSafelyInFormData(configMap, COLLECTION, "users");
        setDataValueSafelyInFormData(configMap, UPDATE_QUERY, "{ name: \"Alden Cantrell\" }");
        setDataValueSafelyInFormData(configMap, UPDATE_OPERATION, "{ $set: { age: 31 }}}");
        setDataValueSafelyInFormData(configMap, UPDATE_LIMIT, "SINGLE");

        actionConfiguration.setFormData(configMap);

        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(dsConfig);
        Mono<Object> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn, new ExecuteActionDTO(), dsConfig, actionConfiguration));
        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = (ActionExecutionResult) obj;
                    assertNotNull(result);
                    assertTrue(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());
                    JsonNode value = ((ObjectNode) result.getBody()).get("nModified");
                    assertEquals(value.asText(), "1");
                    assertEquals(
                            List.of(new ParsedDataType(JSON), new ParsedDataType(RAW)).toString(),
                            result.getDataTypes().toString()
                    );
                })
                .verifyComplete();
    }

    @Test
    public void testUpdateManyFormCommand() {
        ActionConfiguration actionConfiguration = new ActionConfiguration();

        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.FALSE);
        setDataValueSafelyInFormData(configMap, COMMAND, "UPDATE");
        setDataValueSafelyInFormData(configMap, COLLECTION, "users");
        // Query for all the documents in the collection
        setDataValueSafelyInFormData(configMap, UPDATE_QUERY, "{}");
        setDataValueSafelyInFormData(configMap, UPDATE_OPERATION, "{ $set: { updatedByCommand: true }}}");
        setDataValueSafelyInFormData(configMap, UPDATE_LIMIT, "ALL");

        actionConfiguration.setFormData(configMap);

        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(dsConfig);
        Mono<Object> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn, new ExecuteActionDTO(), dsConfig, actionConfiguration));
        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = (ActionExecutionResult) obj;
                    assertNotNull(result);
                    assertTrue(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());
                    JsonNode value = ((ObjectNode) result.getBody()).get("nModified");
                    assertEquals("3", value.asText());
                    assertEquals(
                            List.of(new ParsedDataType(JSON), new ParsedDataType(RAW)).toString(),
                            result.getDataTypes().toString()
                    );
                })
                .verifyComplete();
    }

    @Test
    public void testDeleteFormCommandSingleDocument() {
        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(dsConfig);

        ActionConfiguration actionConfiguration = new ActionConfiguration();

        Map<String, Object> configMap = new HashMap<>();
        // Insert multiple documents which would match the delete criterion
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.FALSE);
        setDataValueSafelyInFormData(configMap, COMMAND, "INSERT");
        setDataValueSafelyInFormData(configMap, COLLECTION, "users");
        setDataValueSafelyInFormData(configMap, INSERT_DOCUMENT, "[{\"name\" : \"To Delete1\", \"tag\" : \"delete\"}, {\"name\" : \"To Delete2\", \"tag\" : \"delete\"}]");

        actionConfiguration.setFormData(configMap);

        dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn, new ExecuteActionDTO(), dsConfig, actionConfiguration)).block();

        // Now that the documents have been inserted, lets delete one of them
        configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.FALSE);
        setDataValueSafelyInFormData(configMap, COMMAND, "DELETE");
        setDataValueSafelyInFormData(configMap, COLLECTION, "users");
        setDataValueSafelyInFormData(configMap, DELETE_QUERY, "{tag : \"delete\"}");
        setDataValueSafelyInFormData(configMap, DELETE_LIMIT, "SINGLE");

        actionConfiguration.setFormData(configMap);

        Mono<Object> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn, new ExecuteActionDTO(), dsConfig, actionConfiguration));
        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = (ActionExecutionResult) obj;
                    assertNotNull(result);
                    assertTrue(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());
                    JsonNode value = ((ObjectNode) result.getBody()).get("n");
                    //Assert that only one document out of the two gets deleted
                    assertEquals(value.asInt(), 1);
                })
                .verifyComplete();

        // Run this delete command again to ensure that both the documents added are deleted post this test.
        dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn, new ExecuteActionDTO(), dsConfig, actionConfiguration)).block();

    }

    @Test
    public void testDeleteFormCommandMultipleDocument() {
        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(dsConfig);

        ActionConfiguration actionConfiguration = new ActionConfiguration();

        Map<String, Object> configMap = new HashMap<>();
        // Insert multiple documents which would match the delete criterion
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.FALSE);
        setDataValueSafelyInFormData(configMap, COMMAND, "INSERT");
        setDataValueSafelyInFormData(configMap, COLLECTION, "users");
        setDataValueSafelyInFormData(configMap, INSERT_DOCUMENT, "[{\"name\" : \"To Delete1\", \"tag\" : \"delete\"}, {\"name\" : \"To Delete2\", \"tag\" : \"delete\"}]");

        actionConfiguration.setFormData(configMap);

        dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn, new ExecuteActionDTO(), dsConfig, actionConfiguration)).block();

        // Now that the documents have been inserted, lets delete both of them
        configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.FALSE);
        setDataValueSafelyInFormData(configMap, COMMAND, "DELETE");
        setDataValueSafelyInFormData(configMap, COLLECTION, "users");
        setDataValueSafelyInFormData(configMap, DELETE_QUERY, "{tag : \"delete\"}");
        setDataValueSafelyInFormData(configMap, DELETE_LIMIT, "ALL");

        actionConfiguration.setFormData(configMap);

        Mono<Object> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn, new ExecuteActionDTO(), dsConfig, actionConfiguration));
        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = (ActionExecutionResult) obj;
                    assertNotNull(result);
                    assertTrue(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());
                    JsonNode value = ((ObjectNode) result.getBody()).get("n");
                    assertEquals(value.asInt(), 2);
                })
                .verifyComplete();
    }

    @Test
    public void testCountCommand() {
        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(dsConfig);

        ActionConfiguration actionConfiguration = new ActionConfiguration();

        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.FALSE);
        setDataValueSafelyInFormData(configMap, COMMAND, "COUNT");
        setDataValueSafelyInFormData(configMap, COLLECTION, "users");
        setDataValueSafelyInFormData(configMap, COUNT_QUERY, "{}");

        actionConfiguration.setFormData(configMap);

        Mono<Object> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn, new ExecuteActionDTO(), dsConfig, actionConfiguration));
        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = (ActionExecutionResult) obj;
                    assertNotNull(result);
                    assertTrue(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());
                    JsonNode value = ((ObjectNode) result.getBody()).get("n");
                    assertEquals(value.asInt(), 3);
                })
                .verifyComplete();
    }

    @Test
    public void testDistinctCommand() {
        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(dsConfig);

        ActionConfiguration actionConfiguration = new ActionConfiguration();

        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.FALSE);
        setDataValueSafelyInFormData(configMap, COMMAND, "DISTINCT");
        setDataValueSafelyInFormData(configMap, COLLECTION, "users");
        setDataValueSafelyInFormData(configMap, DISTINCT_QUERY, "{}");
        setDataValueSafelyInFormData(configMap, DISTINCT_KEY, "name");

        actionConfiguration.setFormData(configMap);

        Mono<Object> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn, new ExecuteActionDTO(), dsConfig, actionConfiguration));
        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = (ActionExecutionResult) obj;
                    assertNotNull(result);
                    assertTrue(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());
                    ArrayNode valuesNode = (ArrayNode) ((ObjectNode) result.getBody()).get("values");
                    int valuesSize = valuesNode.size();
                    assertEquals(valuesSize, 3);
                })
                .verifyComplete();
    }

    @Test
    public void testAggregateCommand() {
        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(dsConfig);

        ActionConfiguration actionConfiguration = new ActionConfiguration();

        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.FALSE);
        setDataValueSafelyInFormData(configMap, COMMAND, "AGGREGATE");
        setDataValueSafelyInFormData(configMap, COLLECTION, "users");
        setDataValueSafelyInFormData(configMap, AGGREGATE_PIPELINES, "[ {$sort :{ _id  : 1 }}, { $project: { age : 1}}]");
        setDataValueSafelyInFormData(configMap, AGGREGATE_LIMIT, "2");

        actionConfiguration.setFormData(configMap);

        Mono<Object> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn, new ExecuteActionDTO(), dsConfig, actionConfiguration));
        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = (ActionExecutionResult) obj;
                    assertNotNull(result);
                    assertTrue(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());
                    int numOfOutputResults = ((ArrayNode) result.getBody()).size();
                    assertEquals(numOfOutputResults, 2); // This would be 3 if `LIMIT` was not set to 2.
                })
                .verifyComplete();
    }

    @Test
    public void testAggregateCommandWithInvalidQuery() {
        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(dsConfig);

        ActionConfiguration actionConfiguration = new ActionConfiguration();

        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.FALSE);
        setDataValueSafelyInFormData(configMap, COMMAND, "AGGREGATE");
        setDataValueSafelyInFormData(configMap, COLLECTION, "users");
        // Invalid JSON object (issue: #5326)
        setDataValueSafelyInFormData(configMap, AGGREGATE_PIPELINES, "{$sort :{ _id  : 1 }}abcd");
        setDataValueSafelyInFormData(configMap, AGGREGATE_LIMIT, "2");

        actionConfiguration.setFormData(configMap);

        Mono<Object> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn,
                new ExecuteActionDTO(), dsConfig, actionConfiguration));
        StepVerifier.create(executeMono)
                .expectErrorMatches(throwable -> {
                    boolean sameClass = throwable.getClass().equals(AppsmithPluginException.class);
                    if (sameClass) {
                        var ape = ((AppsmithPluginException) throwable);
                        return ape.getError().equals(AppsmithPluginError.PLUGIN_EXECUTE_ARGUMENT_ERROR)
                                && ape.getArgs()[0].equals("Pipeline stage is not a valid JSON object.");
                    }
                    return false;
                })
                .verify();
    }

    @Test
    public void testFindCommandProjection() {
        ActionConfiguration actionConfiguration = new ActionConfiguration();

        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.FALSE);
        setDataValueSafelyInFormData(configMap, COMMAND, "FIND");
        setDataValueSafelyInFormData(configMap, FIND_QUERY, "{ age: { \"$gte\": 30 } }");
        setDataValueSafelyInFormData(configMap, FIND_SORT, "{ id: 1 }");
        setDataValueSafelyInFormData(configMap, FIND_PROJECTION, "{ name: 1 }");
        setDataValueSafelyInFormData(configMap, COLLECTION, "users");

        actionConfiguration.setFormData(configMap);

        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(dsConfig);
        Mono<Object> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn, new ExecuteActionDTO(), dsConfig, actionConfiguration));
        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = (ActionExecutionResult) obj;
                    assertNotNull(result);
                    assertTrue(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());
                    assertEquals(2, ((ArrayNode) result.getBody()).size());
                    JsonNode value = ((ArrayNode) result.getBody()).get(0).get("name");
                    assertNotNull(value);
                })
                .verifyComplete();
    }

    @Test
    public void testBsonSmartSubstitutionMongoForm() {
        DatasourceConfiguration datasourceConfiguration = createDatasourceConfiguration();

        ActionConfiguration actionConfiguration = new ActionConfiguration();

        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.TRUE);
        setDataValueSafelyInFormData(configMap, COMMAND, "FIND");
        setDataValueSafelyInFormData(configMap, FIND_QUERY, "\"{{Input1.text}}\"");
        setDataValueSafelyInFormData(configMap, FIND_SORT, "{ id: {{Input2.text}} }");
        setDataValueSafelyInFormData(configMap, FIND_LIMIT, "{{Input3.text}}");
        setDataValueSafelyInFormData(configMap, COLLECTION, "{{Input4.text}}");

        actionConfiguration.setFormData(configMap);

        ExecuteActionDTO executeActionDTO = new ExecuteActionDTO();
        List<Param> params = new ArrayList<>();
        Param param1 = new Param();
        param1.setKey("Input1.text");
        param1.setValue("{ age: { \"$gte\": 30 } }");
        params.add(param1);
        Param param3 = new Param();
        param3.setKey("Input2.text");
        param3.setValue("1");
        params.add(param3);
        Param param4 = new Param();
        param4.setKey("Input3.text");
        param4.setValue("10");
        params.add(param4);
        Param param5 = new Param();
        param5.setKey("Input4.text");
        param5.setValue("users");
        params.add(param5);
        executeActionDTO.setParams(params);

        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(datasourceConfiguration);
        Mono<ActionExecutionResult> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn,
                executeActionDTO,
                datasourceConfiguration,
                actionConfiguration));

        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = obj;
                    assertNotNull(result);
                    assertTrue(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());
                    assertEquals(2, ((ArrayNode) result.getBody()).size());

                    assertEquals(
                            List.of(new ParsedDataType(JSON), new ParsedDataType(RAW)).toString(),
                            result.getDataTypes().toString()
                    );

                    String expectedQuery = "{\"find\": \"users\", \"filter\": {\"age\": {\"$gte\": 30}}, \"sort\": {\"id\": 1}, \"limit\": 10, \"batchSize\": 10}";
                    assertEquals(expectedQuery,
                            ((RequestParamDTO)(((List)result.getRequest().getRequestParams())).get(0)).getValue());
                })
                .verifyComplete();
    }

    @Test
    public void testFormSmartInputFind() {
        DatasourceConfiguration datasourceConfiguration = createDatasourceConfiguration();

        ActionConfiguration actionConfiguration = new ActionConfiguration();

        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.TRUE);
        setDataValueSafelyInFormData(configMap, COMMAND, "FIND");
        // Skip adding the query
        setDataValueSafelyInFormData(configMap, FIND_SORT, "{ id: {{Input2.text}} }");
        // Skip adding limit
        setDataValueSafelyInFormData(configMap, COLLECTION, "{{Input4.text}}");

        actionConfiguration.setFormData(configMap);

        ExecuteActionDTO executeActionDTO = new ExecuteActionDTO();
        List<Param> params = new ArrayList<>();
        Param param1 = new Param();
        param1.setKey("Input2.text");
        param1.setValue("1");
        params.add(param1);
        Param param2 = new Param();
        param2.setKey("Input4.text");
        param2.setValue("users");
        params.add(param2);
        executeActionDTO.setParams(params);

        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(datasourceConfiguration);
        Mono<ActionExecutionResult> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn,
                executeActionDTO,
                datasourceConfiguration,
                actionConfiguration));

        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = obj;
                    assertNotNull(result);
                    assertTrue(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());
                    assertEquals(3, ((ArrayNode) result.getBody()).size());

                    assertEquals(
                            List.of(new ParsedDataType(JSON), new ParsedDataType(RAW)).toString(),
                            result.getDataTypes().toString()
                    );

                    String expectedQuery = "{\"find\": \"users\", \"filter\": {}, \"sort\": {\"id\": 1}, \"limit\": 10, \"batchSize\": 10}";
                    assertEquals(expectedQuery,
                            ((RequestParamDTO)(((List)result.getRequest().getRequestParams())).get(0)).getValue());
                })
                .verifyComplete();
    }

    @Test
    public void testFormSmartInputCount() {
        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(dsConfig);

        ActionConfiguration actionConfiguration = new ActionConfiguration();

        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.TRUE);
        setDataValueSafelyInFormData(configMap, COMMAND, "COUNT");
        setDataValueSafelyInFormData(configMap, COLLECTION, "users");
        // Skip adding the query

        actionConfiguration.setFormData(configMap);

        Mono<Object> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn, new ExecuteActionDTO(), dsConfig, actionConfiguration));
        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = (ActionExecutionResult) obj;
                    assertNotNull(result);
                    assertTrue(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());
                    JsonNode value = ((ObjectNode) result.getBody()).get("n");
                    assertEquals(value.asInt(), 3);
                })
                .verifyComplete();
    }

    @Test
    public void testFormSmartInputDistinct() {
        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(dsConfig);

        ActionConfiguration actionConfiguration = new ActionConfiguration();

        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.TRUE);
        setDataValueSafelyInFormData(configMap, COMMAND, "DISTINCT");
        setDataValueSafelyInFormData(configMap, COLLECTION, "users");
        // Skip adding the query
        setDataValueSafelyInFormData(configMap, DISTINCT_KEY, "name");

        actionConfiguration.setFormData(configMap);

        Mono<Object> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn, new ExecuteActionDTO(), dsConfig, actionConfiguration));
        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = (ActionExecutionResult) obj;
                    assertNotNull(result);
                    assertTrue(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());
                    ArrayNode valuesNode = (ArrayNode) ((ObjectNode) result.getBody()).get("values");
                    int valuesSize = valuesNode.size();
                    assertEquals(valuesSize, 3);
                })
                .verifyComplete();
    }

    @Test
    public void testSmartSubstitutionEvaluatedValueContainingQuestionMark() {
        ActionConfiguration actionConfiguration = new ActionConfiguration();

        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.TRUE);
        setDataValueSafelyInFormData(configMap, COMMAND, "INSERT");
        setDataValueSafelyInFormData(configMap, COLLECTION, "users");
        setDataValueSafelyInFormData(configMap, INSERT_DOCUMENT, "{\"name\" : {{Input1.text}}, \"gender\" : {{Input2.text}}, \"age\" : 40, \"tag\" : \"test\"}");

        actionConfiguration.setFormData(configMap);

        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(dsConfig);

        ExecuteActionDTO executeActionDTO = new ExecuteActionDTO();
        List<Param> params = new ArrayList<>();
        Param param1 = new Param();
        param1.setKey("Input1.text");
        param1.setValue("This string contains ? symbol");
        params.add(param1);
        Param param3 = new Param();
        param3.setKey("Input2.text");
        param3.setValue("F");
        params.add(param3);
        executeActionDTO.setParams(params);

        Mono<Object> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn, executeActionDTO, dsConfig, actionConfiguration));
        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = (ActionExecutionResult) obj;
                    assertNotNull(result);
                    assertTrue(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());
                    assertEquals(
                            List.of(new ParsedDataType(JSON), new ParsedDataType(RAW)).toString(),
                            result.getDataTypes().toString()
                    );
                })
                .verifyComplete();

        // Clean up this newly inserted value

        configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.FALSE);
        setDataValueSafelyInFormData(configMap, COMMAND, "DELETE");
        setDataValueSafelyInFormData(configMap, COLLECTION, "users");
        setDataValueSafelyInFormData(configMap, DELETE_QUERY, "{\"tag\" : \"test\"}");
        setDataValueSafelyInFormData(configMap, DELETE_LIMIT, "ALL");

        actionConfiguration.setFormData(configMap);

        // Run the delete command
        dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn, new ExecuteActionDTO(), dsConfig, actionConfiguration)).block();
    }

    @Test
    public void testReadableErrorWithFilterKeyError() {
        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(dsConfig);

        ActionConfiguration actionConfiguration = new ActionConfiguration();

        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.TRUE);
        setDataValueSafelyInFormData(configMap, COMMAND, "RAW");
        // Set bad attribute for limit key
        setDataValueSafelyInFormData(configMap, BODY, "{\n" +
                "      find: \"users\",\n" +
                "      filter: \"filter\",\n" +
                "      limit: 10,\n" +
                "    }");
        actionConfiguration.setFormData(configMap);

        Mono<Object> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn,
                new ExecuteActionDTO(), dsConfig, actionConfiguration));
        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = (ActionExecutionResult) obj;
                    assertNotNull(result);
                    assertFalse(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());

                    // Verify readable error.
                    String expectedReadableError = "'filter' field must be of BSON type object.";
                    assertEquals(expectedReadableError, result.getReadableError());
                })
                .verifyComplete();
    }

    @Test
    public void testReadableErrorWithMongoFailedToParseError() {
        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(dsConfig);

        ActionConfiguration actionConfiguration = new ActionConfiguration();

        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.TRUE);
        setDataValueSafelyInFormData(configMap, COMMAND, "RAW");
        // Set bad attribute for limit key
        setDataValueSafelyInFormData(configMap, BODY, "{\n" +
                "      find: \"users\",\n" +
                "      limit: [10],\n" +
                "    }");
        actionConfiguration.setFormData(configMap);

        Mono<Object> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn,
                new ExecuteActionDTO(), dsConfig, actionConfiguration));
        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = (ActionExecutionResult) obj;
                    assertNotNull(result);
                    assertFalse(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());

                    // Verify readable error.
                    String expectedReadableError = "'limit' field must be numeric.";
                    assertEquals(expectedReadableError, result.getReadableError());
                })
                .verifyComplete();
    }

    @Test
    public void testReadableErrorWithMongoBadKeyError() {
        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(dsConfig);

        ActionConfiguration actionConfiguration = new ActionConfiguration();

        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.TRUE);
        setDataValueSafelyInFormData(configMap, COMMAND, "RAW");
        // Set unrecognized key limitx
        setDataValueSafelyInFormData(configMap, BODY, "{\n" +
                "      find: \"users\",\n" +
                "      limitx: 10,\n" +
                "    }");
        actionConfiguration.setFormData(configMap);

        Mono<Object> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn,
                new ExecuteActionDTO(), dsConfig, actionConfiguration));
        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = (ActionExecutionResult) obj;
                    assertNotNull(result);
                    assertFalse(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());

                    // Verify readable error.
                    String expectedReadableError = "Unrecognized field 'limitx'.";
                    assertEquals(expectedReadableError, result.getReadableError());
                })
                .verifyComplete();
    }

    @Test
    public void testReadableErrorOnTestDatasourceFailWithBadCredentials() {
        // Mock exception on authentication failure.
        MongoSecurityException mockMongoSecurityException = mock(MongoSecurityException.class);
        when(mockMongoSecurityException.getCode()).thenReturn(-4);
        when(mockMongoSecurityException.getMessage()).thenReturn("Exception authenticating " +
                "MongoCredential{mechanism=SCRAM-SHA-1, userName='username', source='admin', password=<hidden>," +
                " mechanismProperties=<hidden>}");

        // Throw mock error on datasource create method call.
        MongoPlugin.MongoPluginExecutor spyMongoPluginExecutor = spy(pluginExecutor);
        doReturn(Mono.error(mockMongoSecurityException)).when(spyMongoPluginExecutor).datasourceCreate(any());

        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        StepVerifier.create(spyMongoPluginExecutor.testDatasource(dsConfig))
                .assertNext(datasourceTestResult -> {
                    assertNotNull(datasourceTestResult);
                    assertFalse(datasourceTestResult.isSuccess());

                    // Verify readable error.
                    String expectedReadableError = "Exception authenticating MongoCredential.";
                    assertEquals(expectedReadableError, datasourceTestResult.getInvalids().toArray()[0]);
                })
                .verifyComplete();
    }

    @Test
    public void testSmartSubstitutionWithObjectIdInDoubleQuotes() {
        final MongoCollection<Document> usersCollection = mongoClient.getDatabase("test").getCollection("users");
        List<String> documentIds = new ArrayList<>();
        Flux.from(usersCollection.find())
                .map(doc -> documentIds.add(doc.get("_id").toString()))
                .collectList()
                .block();

        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(dsConfig);

        String findQuery = "{\n" +
                "   \"find\": \"users\",\n" +
                "   \"filter\": {\n" +
                "           \"_id\": {\n" +
                "               $in: {{Input1.text}}\n" +
                "            }\n" +
                "    }\n" +
                "}";
        ActionConfiguration actionConfiguration = new ActionConfiguration();


        StringBuilder sb = new StringBuilder();
        documentIds.stream()
                .forEach(id -> sb.append(" \"ObjectId(\\\"" + id + "\\\")\","));
        sb.setLength(sb.length() - 1);
        String objectIdsAsArray = "[" + sb + "]";

        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.TRUE);
        setDataValueSafelyInFormData(configMap, COMMAND, "RAW");
        setDataValueSafelyInFormData(configMap, BODY, findQuery);
        actionConfiguration.setFormData(configMap);

        ExecuteActionDTO executeActionDTO = new ExecuteActionDTO();
        List<Param> params = new ArrayList<>();
        Param param1 = new Param();
        param1.setKey("Input1.text");
        param1.setValue(objectIdsAsArray);
        params.add(param1);
        executeActionDTO.setParams(params);

        Mono<Object> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn,
                executeActionDTO, dsConfig, actionConfiguration));
        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = (ActionExecutionResult) obj;
                    assertNotNull(result);
                    assertTrue(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());
                    assertEquals(3, ((ArrayNode) result.getBody()).size());
                })
                .verifyComplete();

    }

    @Test
    public void testSmartSubstitutionWithObjectIdInSingleQuotes() {
        final MongoCollection<Document> usersCollection = mongoClient.getDatabase("test").getCollection("users");
        List<String> documentIds = new ArrayList<>();
        Flux.from(usersCollection.find())
                .map(doc -> documentIds.add(doc.get("_id").toString()))
                .collectList()
                .block();

        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(dsConfig);

        String findQuery = "{\n" +
                "   \"find\": \"users\",\n" +
                "   \"filter\": {\n" +
                "           \"_id\": {\n" +
                "               $in: {{Input1.text}}\n" +
                "            }\n" +
                "    }\n" +
                "}";
        ActionConfiguration actionConfiguration = new ActionConfiguration();

        StringBuilder sb = new StringBuilder();
        documentIds.stream()
                .forEach(id -> sb.append(" \'ObjectId(\\\"" + id + "\\\")\',"));
        sb.setLength(sb.length() - 1);
        String objectIdsAsArray = "[" + sb + "]";

        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.TRUE);
        setDataValueSafelyInFormData(configMap, COMMAND, "RAW");
        setDataValueSafelyInFormData(configMap, BODY, findQuery);
        actionConfiguration.setFormData(configMap);

        ExecuteActionDTO executeActionDTO = new ExecuteActionDTO();
        List<Param> params = new ArrayList<>();
        Param param1 = new Param();
        param1.setKey("Input1.text");
        param1.setValue(objectIdsAsArray);
        params.add(param1);
        executeActionDTO.setParams(params);

        Mono<Object> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn,
                executeActionDTO, dsConfig, actionConfiguration));
        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = (ActionExecutionResult) obj;
                    assertNotNull(result);
                    assertTrue(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());
                    assertEquals(3, ((ArrayNode) result.getBody()).size());
                })
                .verifyComplete();

    }

    @Test
    public void testFormToNativeQueryConversionForFindCommand() {
        ActionConfiguration actionConfiguration = new ActionConfiguration();

        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.FALSE);
        setDataValueSafelyInFormData(configMap, COMMAND, "FIND");
        setDataValueSafelyInFormData(configMap, FIND_QUERY, "{{Input1.text}}");
        setDataValueSafelyInFormData(configMap, FIND_SORT, "{ id: 1 }");
        setDataValueSafelyInFormData(configMap, COLLECTION, "users");
        actionConfiguration.setFormData(configMap);

        ExecuteActionDTO executeActionDTO = new ExecuteActionDTO();
        List<Param> params = new ArrayList<>();
        Param param1 = new Param();
        param1.setKey("Input1.text");
        param1.setValue("{ age: { \"$gte\": 30 } }");
        params.add(param1);
        executeActionDTO.setParams(params);

        pluginExecutor.extractAndSetNativeQueryFromFormData(actionConfiguration);
        setDataValueSafelyInFormData(configMap, COMMAND, "RAW");
        setDataValueSafelyInFormData(configMap, BODY, getDataValueSafelyFromFormData(configMap, "misc.formToNativeQuery", STRING_TYPE));

        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(dsConfig);
        Mono<Object> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn, executeActionDTO,
                dsConfig, actionConfiguration));
        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = (ActionExecutionResult) obj;
                    assertNotNull(result);
                    assertTrue(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());
                    assertEquals(2, ((ArrayNode) result.getBody()).size());
                    assertEquals(
                            List.of(new ParsedDataType(JSON), new ParsedDataType(RAW)).toString(),
                            result.getDataTypes().toString()
                    );
                })
                .verifyComplete();
    }

    @Test
    public void testFormToNativeQueryConversionForInsertCommand() {
        ActionConfiguration actionConfiguration = new ActionConfiguration();

        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.FALSE);
        setDataValueSafelyInFormData(configMap, COMMAND, "INSERT");
        setDataValueSafelyInFormData(configMap, COLLECTION, "{{Input1.text}}");
        setDataValueSafelyInFormData(configMap, INSERT_DOCUMENT, "[{name : \"ZZZ Insert Form Array Test 1\", gender : " +
                "\"F\", age : 40, tag : \"test\"}, {name : \"ZZZ Insert Form Array Test 2\", gender : \"F\", age : " +
                "40, tag : \"test\"}]");

        actionConfiguration.setFormData(configMap);

        ExecuteActionDTO executeActionDTO = new ExecuteActionDTO();
        List<Param> params = new ArrayList<>();
        Param param1 = new Param();
        param1.setKey("Input1.text");
        param1.setValue("users");
        params.add(param1);
        executeActionDTO.setParams(params);

        pluginExecutor.extractAndSetNativeQueryFromFormData(actionConfiguration);
        setDataValueSafelyInFormData(configMap, COMMAND, "RAW");
        setDataValueSafelyInFormData(configMap, BODY, getDataValueSafelyFromFormData(configMap, "misc.formToNativeQuery", STRING_TYPE));

        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(dsConfig);
        Mono<Object> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn,
                executeActionDTO, dsConfig, actionConfiguration));
        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = (ActionExecutionResult) obj;
                    assertNotNull(result);
                    assertTrue(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());
                    assertEquals(
                            List.of(new ParsedDataType(JSON), new ParsedDataType(RAW)).toString(),
                            result.getDataTypes().toString()
                    );
                })
                .verifyComplete();

        // Clean up this newly inserted value
        configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.FALSE);
        setDataValueSafelyInFormData(configMap, COMMAND, "DELETE");
        setDataValueSafelyInFormData(configMap, COLLECTION, "users");
        setDataValueSafelyInFormData(configMap, DELETE_QUERY, "{\"tag\" : \"test\"}");
        setDataValueSafelyInFormData(configMap, DELETE_LIMIT, "ALL");

        actionConfiguration.setFormData(configMap);
        // Run the delete command
        dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn, new ExecuteActionDTO(), dsConfig,
                actionConfiguration)).block();
    }

    @Test
    public void testFormToNativeQueryConversionForUpdateCommand() {
        ActionConfiguration actionConfiguration = new ActionConfiguration();

        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.FALSE);
        setDataValueSafelyInFormData(configMap, COMMAND, "UPDATE");
        setDataValueSafelyInFormData(configMap, COLLECTION, "users");
        // Query for all the documents in the collection
        setDataValueSafelyInFormData(configMap, UPDATE_QUERY, "{}");
        setDataValueSafelyInFormData(configMap, UPDATE_OPERATION, "{{Input1.text}}");
        setDataValueSafelyInFormData(configMap, UPDATE_LIMIT, "ALL");
        actionConfiguration.setFormData(configMap);

        ExecuteActionDTO executeActionDTO = new ExecuteActionDTO();
        List<Param> params = new ArrayList<>();
        Param param1 = new Param();
        param1.setKey("Input1.text");
        param1.setValue("{ $set: { \"updatedByCommand\": false }}");
        params.add(param1);
        executeActionDTO.setParams(params);

        pluginExecutor.extractAndSetNativeQueryFromFormData(actionConfiguration);
        setDataValueSafelyInFormData(configMap, COMMAND, "RAW");
        setDataValueSafelyInFormData(configMap, BODY, getDataValueSafelyFromFormData(configMap, "misc.formToNativeQuery", STRING_TYPE));

        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(dsConfig);
        Mono<Object> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn,
                executeActionDTO, dsConfig, actionConfiguration));
        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = (ActionExecutionResult) obj;
                    assertNotNull(result);
                    assertTrue(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());
                    JsonNode value = ((ObjectNode) result.getBody()).get("nModified");
                    assertEquals("3", value.asText());
                    assertEquals(
                            List.of(new ParsedDataType(JSON), new ParsedDataType(RAW)).toString(),
                            result.getDataTypes().toString()
                    );
                })
                .verifyComplete();
    }

    @Test
    public void testFormToNativeQueryConversionForDeleteCommand() {
        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(dsConfig);

        ActionConfiguration actionConfiguration = new ActionConfiguration();

        Map<String, Object> configMap = new HashMap<>();
        // Insert multiple documents which would match the delete criterion
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.FALSE);
        setDataValueSafelyInFormData(configMap, COMMAND, "INSERT");
        setDataValueSafelyInFormData(configMap, COLLECTION, "users");
        setDataValueSafelyInFormData(configMap, INSERT_DOCUMENT, "[{\"name\" : \"To Delete1\", \"tag\" : \"delete\"}, " +
                "{\"name\" : \"To Delete2\", \"tag\" : \"delete\"}]");

        actionConfiguration.setFormData(configMap);

        dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn, new ExecuteActionDTO(), dsConfig,
                actionConfiguration)).block();

        // Now that the documents have been inserted, lets delete both of them
        configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.FALSE);
        setDataValueSafelyInFormData(configMap, COMMAND, "DELETE");
        setDataValueSafelyInFormData(configMap, COLLECTION, "users");
        setDataValueSafelyInFormData(configMap, DELETE_QUERY, "{{Input1.text}}");
        setDataValueSafelyInFormData(configMap, DELETE_LIMIT, "ALL");
        setDataValueSafelyInFormData(configMap, BODY, "");
        actionConfiguration.setFormData(configMap);

        ExecuteActionDTO executeActionDTO = new ExecuteActionDTO();
        List<Param> params = new ArrayList<>();
        Param param1 = new Param();
        param1.setKey("Input1.text");
        param1.setValue("{tag : \"delete\"}");
        params.add(param1);
        executeActionDTO.setParams(params);

        pluginExecutor.extractAndSetNativeQueryFromFormData(actionConfiguration);
        setDataValueSafelyInFormData(configMap, COMMAND, "RAW");
        setDataValueSafelyInFormData(configMap, BODY, getDataValueSafelyFromFormData(configMap, "misc.formToNativeQuery", STRING_TYPE));

        Mono<Object> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn,
                executeActionDTO, dsConfig, actionConfiguration));
        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = (ActionExecutionResult) obj;
                    assertNotNull(result);
                    assertTrue(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());
                    JsonNode value = ((ObjectNode) result.getBody()).get("n");
                    assertEquals(value.asInt(), 2);
                })
                .verifyComplete();
    }

    @Test
    public void testFormToNativeQueryConversionForCountCommand() {
        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(dsConfig);

        ActionConfiguration actionConfiguration = new ActionConfiguration();

        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.FALSE);
        setDataValueSafelyInFormData(configMap, COMMAND, "COUNT");
        setDataValueSafelyInFormData(configMap, COLLECTION, "{{Input1.text}}");
        setDataValueSafelyInFormData(configMap, COUNT_QUERY, "{}");
        actionConfiguration.setFormData(configMap);

        ExecuteActionDTO executeActionDTO = new ExecuteActionDTO();
        List<Param> params = new ArrayList<>();
        Param param1 = new Param();
        param1.setKey("Input1.text");
        param1.setValue("users");
        params.add(param1);
        executeActionDTO.setParams(params);

        pluginExecutor.extractAndSetNativeQueryFromFormData(actionConfiguration);
        setDataValueSafelyInFormData(configMap, COMMAND, "RAW");
        setDataValueSafelyInFormData(configMap, BODY, getDataValueSafelyFromFormData(configMap, "misc.formToNativeQuery", STRING_TYPE));

        Mono<Object> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn,
                executeActionDTO, dsConfig, actionConfiguration));
        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = (ActionExecutionResult) obj;
                    assertNotNull(result);
                    assertTrue(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());
                    JsonNode value = ((ObjectNode) result.getBody()).get("n");
                    assertEquals(value.asInt(), 3);
                })
                .verifyComplete();
    }

    @Test
    public void testFormToNativeQueryConversionForDistinctCommand() {
        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(dsConfig);

        ActionConfiguration actionConfiguration = new ActionConfiguration();

        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.FALSE);
        setDataValueSafelyInFormData(configMap, COMMAND, "DISTINCT");
        setDataValueSafelyInFormData(configMap, COLLECTION, "users");
        setDataValueSafelyInFormData(configMap, DISTINCT_QUERY, "{}");
        setDataValueSafelyInFormData(configMap, DISTINCT_KEY, "{{Input1.text}}");
        actionConfiguration.setFormData(configMap);

        ExecuteActionDTO executeActionDTO = new ExecuteActionDTO();
        List<Param> params = new ArrayList<>();
        Param param1 = new Param();
        param1.setKey("Input1.text");
        param1.setValue("name");
        params.add(param1);
        executeActionDTO.setParams(params);

        pluginExecutor.extractAndSetNativeQueryFromFormData(actionConfiguration);
        setDataValueSafelyInFormData(configMap, COMMAND, "RAW");
        setDataValueSafelyInFormData(configMap, BODY, getDataValueSafelyFromFormData(configMap, "misc.formToNativeQuery",
                STRING_TYPE));

        Mono<Object> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn,
                executeActionDTO, dsConfig, actionConfiguration));
        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = (ActionExecutionResult) obj;
                    assertNotNull(result);
                    assertTrue(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());
                    ArrayNode valuesNode = (ArrayNode) ((ObjectNode) result.getBody()).get("values");
                    int valuesSize = valuesNode.size();
                    assertEquals(valuesSize, 3);
                })
                .verifyComplete();
    }

    @Test
    public void testFormToNativeQueryConversionForAggregateCommand() {
        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(dsConfig);

        ActionConfiguration actionConfiguration = new ActionConfiguration();

        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.FALSE);
        setDataValueSafelyInFormData(configMap, COMMAND, "AGGREGATE");
        setDataValueSafelyInFormData(configMap, COLLECTION, "users");
        setDataValueSafelyInFormData(configMap, AGGREGATE_PIPELINES, "{{Input1.text}}");
        actionConfiguration.setFormData(configMap);

        pluginExecutor.extractAndSetNativeQueryFromFormData(actionConfiguration);
        setDataValueSafelyInFormData(configMap, COMMAND, "RAW");
        setDataValueSafelyInFormData(configMap, BODY, getDataValueSafelyFromFormData(configMap, "misc.formToNativeQuery", STRING_TYPE));

        ExecuteActionDTO executeActionDTO = new ExecuteActionDTO();
        List<Param> params = new ArrayList<>();
        Param param1 = new Param();
        param1.setKey("Input1.text");
        param1.setValue("[ {$sort :{ _id  : 1 }}, { $project: { age : 1}}, {$count: \"userCount\"} ]");
        params.add(param1);
        executeActionDTO.setParams(params);

        Mono<Object> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn,
                executeActionDTO, dsConfig, actionConfiguration));
        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = (ActionExecutionResult) obj;
                    assertNotNull(result);
                    assertTrue(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());
                    JsonNode value = ((ArrayNode) result.getBody()).get(0).get("userCount");
                    assertEquals(value.asInt(), 3);
                })
                .verifyComplete();
    }

    @Test
    public void testSmartSubstitutionWithMongoTypesWithRawCommand1() {
        final MongoCollection<Document> usersCollection = mongoClient.getDatabase("test").getCollection("users");
        List<String> documentIds = new ArrayList<>();
        Flux.from(usersCollection.find())
                .filter(doc -> doc.containsKey("aLong"))
                .map(doc -> documentIds.add(doc.get("_id").toString()))
                .collectList()
                .block();

        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(dsConfig);

        final String findQuery = "" +
                "{\n" +
                "   \"find\": \"users\",\n" +
                "   \"filter\": {\n" +
                "       \"_id\":{ $in: {{Input0}} },\n" +
                "       \"dob\":{ $in: {{Input1}} },\n" +
                "       \"netWorth\":{ $in: {{Input2}} },\n" +
                "       \"aLong\": {{Input3}},\n" +
                "       \"ts\":{ $in: {{Input4}} },\n" +
                "   },\n" +
                "}";

        ActionConfiguration actionConfiguration = new ActionConfiguration();

        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.TRUE);
        setDataValueSafelyInFormData(configMap, COMMAND, "RAW");
        setDataValueSafelyInFormData(configMap, BODY, findQuery);
        actionConfiguration.setFormData(configMap);

        ExecuteActionDTO executeActionDTO = new ExecuteActionDTO();
        final List<Param> params = new ArrayList<>();
        final Param id = new Param("Input0", "[\"ObjectId('" + documentIds.get(0) + "')\"]");
        params.add(id);
        final Param dob = new Param("Input1", "[\"ISODate('1970-01-01T00:00:00.000Z')\"]");
        params.add(dob);
        final Param netWorth = new Param("Input2", "['NumberDecimal(\"123456.789012\")']");
        params.add(netWorth);
        final Param aLong = new Param("Input3", "\"NumberLong(9000000000000000000)\"");
        params.add(aLong);
        final Param ts = new Param("Input4", "[\"Timestamp(1421006159, 4)\"]");
        params.add(ts);
        executeActionDTO.setParams(params);

        Mono<Object> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn,
                executeActionDTO, dsConfig, actionConfiguration));
        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = (ActionExecutionResult) obj;
                    assertNotNull(result);
                    assertTrue(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());
                    assertEquals(1, ((ArrayNode) result.getBody()).size());
                })
                .verifyComplete();
    }

    @Test
    public void testBsonSmartSubstitutionWithMongoTypesWithFindCommand() {
        DatasourceConfiguration datasourceConfiguration = createDatasourceConfiguration();

        ActionConfiguration actionConfiguration = new ActionConfiguration();

        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.TRUE);
        setDataValueSafelyInFormData(configMap, COMMAND, "FIND");
        setDataValueSafelyInFormData(configMap, FIND_QUERY, "\"{{Input1.text}}\"");
        setDataValueSafelyInFormData(configMap, FIND_SORT, "{ id: {{Input2.text}} }");
        setDataValueSafelyInFormData(configMap, FIND_LIMIT, "{{Input3.text}}");
        setDataValueSafelyInFormData(configMap, COLLECTION, "{{Input4.text}}");

        actionConfiguration.setFormData(configMap);

        ExecuteActionDTO executeActionDTO = new ExecuteActionDTO();
        List<Param> params = new ArrayList<>();
        Param param1 = new Param();
        param1.setKey("Input1.text");
        param1.setValue("{ " +
                "\"dob\": { \"$gte\": \"ISODate('2000-01-01T00:00:00.000Z')\" }, " +
                "\"netWorth\": { \"$in\": [\"NumberDecimal(123456.789012)\"] } " +
                "}");
        params.add(param1);
        Param param3 = new Param();
        param3.setKey("Input2.text");
        param3.setValue("1");
        params.add(param3);
        Param param4 = new Param();
        param4.setKey("Input3.text");
        param4.setValue("10");
        params.add(param4);
        Param param5 = new Param();
        param5.setKey("Input4.text");
        param5.setValue("users");
        params.add(param5);
        executeActionDTO.setParams(params);

        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(datasourceConfiguration);
        Mono<ActionExecutionResult> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn,
                executeActionDTO,
                datasourceConfiguration,
                actionConfiguration));

        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = obj;
                    assertNotNull(result);
                    assertTrue(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());
                    assertEquals(1, ((ArrayNode) result.getBody()).size());
                })
                .verifyComplete();
    }


    @Test
    public void testSmartSubstitutionWithMongoTypes2() {
        final MongoCollection<Document> usersCollection = mongoClient.getDatabase("test").getCollection("users");
        List<String> documentIds = new ArrayList<>();
        Flux.from(usersCollection.find())
                .filter(doc -> doc.containsKey("aLong"))
                .map(doc -> documentIds.add(doc.get("_id").toString()))
                .collectList()
                .block();

        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(dsConfig);

        final String findQuery = "" +
                "{\n" +
                "   \"find\": \"users\",\n" +
                "   \"filter\": {\n" +
                "       \"_id\": {{Input0}},\n" +
                "       \"dob\": {{Input1}},\n" +
                "       \"netWorth\": {{Input2}},\n" +
                "       \"aLong\": {{Input3}},\n" +
                "       \"ts\": {{Input4}},\n" +
                "   },\n" +
                "}";

        ActionConfiguration actionConfiguration = new ActionConfiguration();

        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.TRUE);
        setDataValueSafelyInFormData(configMap, COMMAND, "RAW");
        setDataValueSafelyInFormData(configMap, BODY, findQuery);
        actionConfiguration.setFormData(configMap);

        ExecuteActionDTO executeActionDTO = new ExecuteActionDTO();
        final List<Param> params = new ArrayList<>();
        final Param id = new Param("Input0", "\"ObjectId(\\\"" + documentIds.get(0) + "\\\")\"");
        params.add(id);
        final Param dob = new Param("Input1", "\"ISODate(\\\"1970-01-01T00:00:00.000Z\\\")\"");
        params.add(dob);
        final Param netWorth = new Param("Input2", "\"NumberDecimal(\\\"123456.789012\\\")\"");
        params.add(netWorth);
        final Param aLong = new Param("Input3", "\"NumberLong(9000000000000000000)\"");
        params.add(aLong);
        final Param ts = new Param("Input4", "\"Timestamp(1421006159, 4)\"");
        params.add(ts);
        executeActionDTO.setParams(params);

        Mono<Object> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn,
                executeActionDTO, dsConfig, actionConfiguration));
        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = (ActionExecutionResult) obj;
                    assertNotNull(result);
                    assertTrue(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());
                    assertEquals(1, ((ArrayNode) result.getBody()).size());
                })
                .verifyComplete();
    }

    @Test
    public void testSmartSubstitutionWithMongoTypes3() {
        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(dsConfig);

        final String findQuery = "" +
                "{\n" +
                "   \"find\": \"users\",\n" +
                "   \"filter\": {{Input1}}\n" +
                "}";

        ActionConfiguration actionConfiguration = new ActionConfiguration();

        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.TRUE);
        setDataValueSafelyInFormData(configMap, COMMAND, "RAW");
        setDataValueSafelyInFormData(configMap, BODY, findQuery);
        actionConfiguration.setFormData(configMap);

        ExecuteActionDTO executeActionDTO = new ExecuteActionDTO();
        final List<Param> params = new ArrayList<>();
        final Param dob = new Param("Input1", "{\"dob\": \"ISODate(\\\"1970-01-01T00:00:00.000Z\\\")\"}");
        params.add(dob);
        executeActionDTO.setParams(params);

        Mono<Object> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn,
                executeActionDTO, dsConfig, actionConfiguration));
        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = (ActionExecutionResult) obj;
                    assertNotNull(result);
                    assertTrue(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());
                    assertEquals(1, ((ArrayNode) result.getBody()).size());
                })
                .verifyComplete();
    }

    @Test
    public void testInsertAndFindInvalidDatetime() {
        ActionConfiguration actionConfiguration = new ActionConfiguration();

        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.FALSE);
        setDataValueSafelyInFormData(configMap, COMMAND, "INSERT");
        setDataValueSafelyInFormData(configMap, COLLECTION, "users");
        setDataValueSafelyInFormData(configMap, INSERT_DOCUMENT, "[\n" +
                "      {\n" +
                "        \"name\": {\n" +
                "          \"first\": \"John\",\n" +
                "          \"last\": \"Backus\"\n" +
                "        },\n" +
                "        \"birth\": ISODate(\"0001-01-01T00:00:00.000+00:00\"),\n" +
                "        \"death\": ISODate(\"2007-03-17T04:00:00Z\"),\n" +
                "        \"issue\": 13285\n" +
                "      },\n" +
                "      {\n" +
                "        \"name\": {\n" +
                "          \"first\": \"John\",\n" +
                "          \"last\": \"McCarthy\"\n" +
                "        },\n" +
                "        \"birth\": ISODate(\"1927-09-04T04:00:00Z\"),\n" +
                "        \"death\": ISODate(\"2011-12-24T05:00:00Z\"),\n" +
                "        \"issue\": 13285\n" +
                "      },\n" +
                "      {\n" +
                "        \"name\": {\n" +
                "          \"first\": \"Grace\",\n" +
                "          \"last\": \"Hopper\"\n" +
                "        },\n" +
                "        \"title\": \"Rear Admiral\",\n" +
                "        \"birth\": ISODate(\"1906-12-09T05:00:00Z\"),\n" +
                "        \"death\": ISODate(\"1992-01-01T05:00:00Z\"),\n" +
                "        \"issue\": 13285\n" +
                "      },\n" +
                "      {\n" +
                "        \"name\": {\n" +
                "          \"first\": \"Kristen\",\n" +
                "          \"last\": \"Nygaard\"\n" +
                "        },\n" +
                "        \"birth\": ISODate(\"1926-08-27T04:00:00Z\"),\n" +
                "        \"death\": ISODate(\"2002-08-10T04:00:00Z\"),\n" +
                "        \"issue\": 13285\n" +
                "      }\n" +
                "]");

        actionConfiguration.setFormData(configMap);

        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        Mono<MongoClient> dsConnectionMono = pluginExecutor.datasourceCreate(dsConfig);
        Mono<Object> executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn, new ExecuteActionDTO(), dsConfig, actionConfiguration));
        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = (ActionExecutionResult) obj;
                    assertNotNull(result);
                    assertTrue(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());
                    assertEquals(
                            List.of(new ParsedDataType(JSON), new ParsedDataType(RAW)).toString(),
                            result.getDataTypes().toString()
                    );
                })
                .verifyComplete();

        //Find query
        configMap.clear();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.FALSE);
        setDataValueSafelyInFormData(configMap, COMMAND, "FIND");
        setDataValueSafelyInFormData(configMap, FIND_QUERY, "{ \"issue\": 13285}");
        setDataValueSafelyInFormData(configMap, FIND_SORT, "{ id: 1 }");
        setDataValueSafelyInFormData(configMap, COLLECTION, "users");

        actionConfiguration.setFormData(configMap);

        executeMono = dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn, new ExecuteActionDTO(), dsConfig, actionConfiguration));
        StepVerifier.create(executeMono)
                .assertNext(obj -> {
                    ActionExecutionResult result = (ActionExecutionResult) obj;
                    assertNotNull(result);
                    assertTrue(result.getIsExecutionSuccess());
                    assertNotNull(result.getBody());
                    assertEquals(4, ((ArrayNode) result.getBody()).size());
                    assertEquals(
                            List.of(new ParsedDataType(JSON), new ParsedDataType(RAW)).toString(),
                            result.getDataTypes().toString()
                    );
                })
                .verifyComplete();

        // Clean up this newly inserted values
        configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.FALSE);
        setDataValueSafelyInFormData(configMap, COMMAND, "DELETE");
        setDataValueSafelyInFormData(configMap, COLLECTION, "users");
        setDataValueSafelyInFormData(configMap, DELETE_QUERY, "{ \"issue\": 13285}");
        setDataValueSafelyInFormData(configMap, DELETE_LIMIT, "ALL");

        actionConfiguration.setFormData(configMap);
        // Run the delete command
        dsConnectionMono.flatMap(conn -> pluginExecutor.executeParameterized(conn, new ExecuteActionDTO(), dsConfig, actionConfiguration)).block();
    }

    @Test
    public void testStaleConnectionOnIllegalStateExceptionOnQueryExecution() {
        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        ActionConfiguration actionConfiguration = new ActionConfiguration();
        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.TRUE);
        setDataValueSafelyInFormData(configMap, COMMAND, "RAW");
        setDataValueSafelyInFormData(configMap, BODY, "{\n" +
                "      find: \"address\",\n" +
                "      limit: 10,\n" +
                "    }");
        actionConfiguration.setFormData(configMap);

        MongoClient spyMongoClient = spy(MongoClient.class);
        MongoDatabase spyMongoDatabase = spy(MongoDatabase.class);
        doReturn(spyMongoDatabase).when(spyMongoClient).getDatabase(anyString());
        doReturn(Mono.error(new IllegalStateException())).when(spyMongoDatabase).runCommand(any());

        Mono<ActionExecutionResult> resultMono = pluginExecutor.executeCommon(spyMongoClient, dsConfig,
                actionConfiguration, new ArrayList<>());
        StepVerifier.create(resultMono)
                .expectErrorMatches(throwable -> throwable instanceof StaleConnectionException)
                .verify();
    }

    @Test
    public void testStaleConnectionOnMongoSocketWriteExceptionOnQueryExecution() {
        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        ActionConfiguration actionConfiguration = new ActionConfiguration();
        Map<String, Object> configMap = new HashMap<>();
        setDataValueSafelyInFormData(configMap, SMART_SUBSTITUTION, Boolean.TRUE);
        setDataValueSafelyInFormData(configMap, COMMAND, "RAW");
        setDataValueSafelyInFormData(configMap, BODY, "{\n" +
                "      find: \"address\",\n" +
                "      limit: 10,\n" +
                "    }");
        actionConfiguration.setFormData(configMap);

        MongoClient spyMongoClient = spy(MongoClient.class);
        MongoDatabase spyMongoDatabase = spy(MongoDatabase.class);
        doReturn(spyMongoDatabase).when(spyMongoClient).getDatabase(anyString());
        doReturn(Mono.error(new MongoSocketWriteException("", null, null))).when(spyMongoDatabase).runCommand(any());

        Mono<ActionExecutionResult> resultMono = pluginExecutor.executeCommon(spyMongoClient, dsConfig,
                actionConfiguration, new ArrayList<>());
        StepVerifier.create(resultMono)
                .expectErrorMatches(throwable -> throwable instanceof StaleConnectionException)
                .verify();
    }

    @Test
    public void testStaleConnectionOnIllegalStateExceptionOnGetStructure() {
        MongoClient spyMongoClient = spy(MongoClient.class);
        MongoDatabase spyMongoDatabase = spy(MongoDatabase.class);
        doReturn(spyMongoDatabase).when(spyMongoClient).getDatabase(anyString());
        doReturn(Mono.error(new IllegalStateException())).when(spyMongoDatabase).listCollectionNames();

        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        Mono<DatasourceStructure> structureMono = pluginExecutor.getStructure(spyMongoClient, dsConfig);
        StepVerifier.create(structureMono)
                .expectErrorMatches(throwable -> throwable instanceof StaleConnectionException)
                .verify();
    }

    @Test
    public void testStaleConnectionOnMongoSocketWriteExceptionOnGetStructure() {
        MongoClient spyMongoClient = spy(MongoClient.class);
        MongoDatabase spyMongoDatabase = spy(MongoDatabase.class);
        doReturn(spyMongoDatabase).when(spyMongoClient).getDatabase(anyString());
        doReturn(Mono.error(new MongoSocketWriteException("", null, null))).when(spyMongoDatabase).listCollectionNames();

        DatasourceConfiguration dsConfig = createDatasourceConfiguration();
        Mono<DatasourceStructure> structureMono = pluginExecutor.getStructure(spyMongoClient, dsConfig);
        StepVerifier.create(structureMono)
                .expectErrorMatches(throwable -> throwable instanceof StaleConnectionException)
                .verify();
    }

    @Test
    public void testValidateDatasource_withoutDefaultDBInURIString_returnsInvalid() {
        final DatasourceConfiguration datasourceConfiguration = new DatasourceConfiguration();
        List<Property> properties = new ArrayList<>();
        properties.add(new Property("isUriString", "Yes"));
        properties.add(new Property("uriString", "mongodb://user:pass@url.net/"));
        datasourceConfiguration.setProperties(properties);

        final Set<String> strings = pluginExecutor.validateDatasource(datasourceConfiguration);

        assertEquals(1, strings.size());
        assertTrue(strings.contains("Missing default database name."));
    }
}
