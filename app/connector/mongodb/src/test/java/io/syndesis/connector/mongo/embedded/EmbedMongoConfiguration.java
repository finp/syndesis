/*
 * Copyright (C) 2016 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.syndesis.connector.mongo.embedded;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.util.SocketUtils.findAvailableTcpPort;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ReadPreference;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import de.flapdoodle.embed.mongo.Command;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.DownloadConfigBuilder;
import de.flapdoodle.embed.mongo.config.ExtractedArtifactStoreBuilder;
import de.flapdoodle.embed.mongo.config.IMongodConfig;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.config.RuntimeConfigBuilder;
import de.flapdoodle.embed.mongo.config.Storage;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.config.IRuntimeConfig;
import de.flapdoodle.embed.process.config.io.ProcessOutput;
import de.flapdoodle.embed.process.io.IStreamProcessor;
import de.flapdoodle.embed.process.io.Processors;
import de.flapdoodle.embed.process.io.Slf4jLevel;
import de.flapdoodle.embed.process.io.Slf4jStreamProcessor;
import de.flapdoodle.embed.process.io.directories.FixedPath;
import de.flapdoodle.embed.process.runtime.Network;
import org.bson.Document;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbedMongoConfiguration {

    public static MongoClient getClient(Boolean useCredentials) {
        return getClient(useCredentials, REPLICA_SET);
    }

    public static MongoClient getClientNoReplicaSet(Boolean useCredentials) {
        return getClient(useCredentials, null);
    }

    private static MongoClient getClient(Boolean useCredentials, String replicaSet) {
        MongoClientSettings.Builder settings = MongoClientSettings.builder();

        if (useCredentials) {
            MongoCredential credentials = MongoCredential.createCredential(
                USER, ADMIN_DB, PASSWORD.toCharArray());
            settings.credential(credentials);
        }
        StringBuilder connectionString = new StringBuilder(String.format("mongodb://%s:%d", HOST, PORT));
        if (replicaSet != null) {
            connectionString.append(String.format("/?replicaSet=%s", REPLICA_SET));
        }
        ConnectionString uri = new ConnectionString(connectionString.toString());
        settings.applyConnectionString(uri);

        settings.readPreference(ReadPreference.primaryPreferred());

        return MongoClients.create(settings.build());
    }

    public static MongoClient getClient() {
        return getClient(true);
    }

    public static MongoDatabase getDB() {
        return getDB(true, "test");
    }

    public static MongoDatabase getDB(Boolean useCredentials, String db) {
        return getClient(useCredentials).getDatabase(db);
    }

    @Bean
    public MongoClient mongoBean() {
        return getClient();
    }

    @Bean
    public MongoDatabase database() {
        return getDB();
    }

    public final static int PORT = findAvailableTcpPort();

    public final static String HOST = "localhost";
    public final static String USER = "test-user";
    public final static String PASSWORD = "test-pwd";
    public final static String ADMIN_DB = "admin";

    public final static String REPLICA_SET = "rs0";

    public EmbedMongoConfiguration() {

    }

    static {
        startEmbeddedMongo();
        startReplicationSet();
        createAuthorizationUser();
    }

    private static void startReplicationSet() {
        getClientNoReplicaSet(false).getDatabase(ADMIN_DB).runCommand(new Document("replSetInitiate", new Document()));
    }

    private static void startEmbeddedMongo() {
        final IStreamProcessor logDestination = new Slf4jStreamProcessor(LoggerFactory.getLogger("embeddeddmongo"), Slf4jLevel.INFO);
        final IStreamProcessor daemon = Processors.named("mongod", logDestination);
        final IStreamProcessor error = Processors.named("mongod-error", logDestination);
        final IStreamProcessor command = Processors.named("mongod-command", logDestination);
        final ProcessOutput processOutput = new ProcessOutput(daemon, error, command);
        final IRuntimeConfig runtimeConfig = new RuntimeConfigBuilder()
            .defaults(Command.MongoD)
            .artifactStore(new ExtractedArtifactStoreBuilder()
                .defaults(Command.MongoD)
                .extractDir(new FixedPath(".extracted"))
                .download(new DownloadConfigBuilder()
                    .defaultsForCommand(Command.MongoD)
                    .artifactStorePath(new FixedPath(".cache"))
                    .build())
                .build())
            .processOutput(processOutput)
            .build();

        try {
            final IMongodConfig mongodConfig = createEmbeddedMongoConfiguration();

            final MongodExecutable mongodExecutable = MongodStarter.getInstance(runtimeConfig)
                .prepare(mongodConfig);


            mongodExecutable.start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static IMongodConfig createEmbeddedMongoConfiguration() {
        try {
            final Path storagePath = Files.createTempDirectory("embeddeddmongo");
            storagePath.toFile().deleteOnExit();
            return new MongodConfigBuilder()
                .version(Version.Main.PRODUCTION)
                .net(new Net(PORT, Network.localhostIsIPv6()))
                .replication(new Storage(storagePath.toString(),
                    REPLICA_SET, 5000))
                .stopTimeoutInMillis(10000)
                .build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void createAuthorizationUser() {
        MongoDatabase admin = getDB(false, "admin");
        MongoCollection<Document> usersCollection =
            admin.getCollection("system.users");
        usersCollection.insertOne(Document.parse("{\n"
            + "    \"_id\": \"admin.test-user\",\n"
            + "    \"user\": \"test-user\",\n"
            + "    \"db\": \"admin\",\n"
            + "    \"credentials\": {\n"
            + "        \"SCRAM-SHA-1\": {\n"
            + "            \"iterationCount\": 10000,\n"
            + "            \"salt\": \"gmmPAoNdvFSWCV6PGnNcAw==\",\n"
            + "            \"storedKey\": \"qE9u1Ax7Y40hisNHL2b8/LAvG7s=\",\n"
            + "            \"serverKey\": \"RefeJcxClt9JbOP/VnrQ7YeQh6w=\"\n"
            + "        }\n"
            + "    },\n"
            + "    \"roles\": [\n"
            + "        {\n"
            + "            \"role\": \"readWrite\",\n"
            + "            \"db\": \"test\"\n"
            + "        }\n"
            + "    ]\n"
            + "}"));
    }
}
