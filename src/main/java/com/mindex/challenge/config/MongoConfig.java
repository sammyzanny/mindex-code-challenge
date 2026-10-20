package com.mindex.challenge.config;

import com.mindex.challenge.dao.EmployeeRepository;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.lang.NonNull;

import java.net.InetSocketAddress;

@EnableMongoRepositories(basePackageClasses = EmployeeRepository.class)
@Configuration
public class MongoConfig extends AbstractMongoClientConfiguration {

    @Override
    @NonNull
    protected String getDatabaseName() {
        return "test";
    }

    /**
     * The embedded server is a bean of its own so Spring shuts it down with the context,
     * releasing its port and threads instead of leaving them until the JVM exits.
     */
    @Bean(destroyMethod = "shutdown")
    public MongoServer mongoServer() {
        return new MongoServer(new MemoryBackend());
    }

    @Override
    @NonNull
    public MongoClient mongoClient() {
        InetSocketAddress serverAddress = mongoServer().bind();
        String mongoConnectionString = String.format("mongodb://%s:%d", serverAddress.getHostName(), serverAddress.getPort());
        return MongoClients.create(mongoConnectionString);
    }
}
