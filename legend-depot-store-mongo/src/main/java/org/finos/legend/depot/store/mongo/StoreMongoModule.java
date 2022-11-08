//  Copyright 2021 Goldman Sachs
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
//

package org.finos.legend.depot.store.mongo;

import com.google.inject.PrivateModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoDatabase;
import org.finos.legend.depot.store.mongo.core.ConnectionFactory;
import org.finos.legend.depot.store.mongo.core.MongoConfiguration;
import org.finos.legend.depot.store.mongo.core.MongoNonTracingConnectionFactory;
import org.finos.legend.depot.store.mongo.core.MongoTracingConnectionFactory;
import org.finos.legend.depot.tracing.configuration.OpenTracingConfiguration;
import org.finos.legend.depot.tracing.services.TracerFactory;

import javax.inject.Named;

public class StoreMongoModule extends PrivateModule
{
    @Override
    protected void configure()
    {
        expose(ConnectionFactory.class);
        expose(MongoDatabase.class).annotatedWith(Names.named("mongoDatabase"));
        expose(Boolean.class).annotatedWith(Names.named("transactionMode"));
        expose(MongoClient.class);
    }

    @Provides
    @Singleton
    ConnectionFactory getConnectionFactory(@Named("applicationName") String applicationName, MongoConfiguration configuration, OpenTracingConfiguration openTracingConfiguration, TracerFactory tracerFactory)
    {
        if (openTracingConfiguration.isEnabled())
        {
            return new MongoTracingConnectionFactory(applicationName, configuration, tracerFactory.getTracer());
        }
        else
        {
            return new MongoNonTracingConnectionFactory(applicationName, configuration);
        }
    }

    @Provides
    @Named("mongoDatabase")
    public MongoDatabase getMongoDatabase(ConnectionFactory connectionFactory)
    {
        return connectionFactory.getDatabase();
    }

    @Provides
    @Singleton
    public MongoClient getMongoClient(ConnectionFactory connectionFactory)
    {
        return connectionFactory.getClient();
    }

    @Provides
    @Singleton
    @Named("transactionMode")
    Boolean getTransactionsMode()
    {
        return false;
    }
}
