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

package org.finos.legend.depot.store.mongo.entities;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.tuple.Tuples;
import org.eclipse.collections.impl.utility.ListIterate;
import org.finos.legend.depot.domain.entity.StoredEntity;
import org.finos.legend.depot.domain.project.ProjectVersion;
import org.finos.legend.depot.store.mongo.core.BaseMongo;
import org.finos.legend.sdlc.domain.model.entity.Entity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.StringTokenizer;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.mongodb.client.model.Aggregates.group;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.not;
import static com.mongodb.client.model.Filters.or;
import static com.mongodb.client.model.Filters.regex;
import static org.finos.legend.depot.domain.version.VersionValidator.BRANCH_SNAPSHOT;

public abstract class AbstractEntitiesMongo<T extends StoredEntity> extends BaseMongo<T>
{
    public static final String ENTITY = "entity";
    public static final String ENTITY_CLASSIFIER_PATH = "entity.classifierPath";
    public static final String CLASSIFIER_PATH = "classifierPath";
    public static final String ENTITY_PATH = "entity.path";
    public static final String PATH = "path";
    public static final String ENTITY_PACKAGE = "entity.content.package";
    public static final String ENTITY_CONTENT = "entity.content";

    protected AbstractEntitiesMongo(MongoDatabase mongoDatabase, Class documentClass)
    {
        super(mongoDatabase, documentClass);
    }

    protected Bson getEntityPathFilter(String groupId, String artifactId, String versionId, String path)
    {
        return and(getArtifactAndVersionFilter(groupId, artifactId, versionId), eq(ENTITY_PATH, path));
    }

    protected abstract Bson getKeyFilter(T data);

    protected abstract void validateNewData(T data);

    public FindIterable findReleasedEntitiesByClassifier(String classifier, String search, List<ProjectVersion> projectVersions)
    {
        List<Bson> filters = new ArrayList<>();
        filters.add(eq(ENTITY_CLASSIFIER_PATH, classifier));
        if (projectVersions != null && !projectVersions.isEmpty())
        {
            filters.add(or(ListIterate.collect(projectVersions, projectVersion -> getArtifactAndVersionFilter(projectVersion.getGroupId(), projectVersion.getArtifactId(), projectVersion.getVersionId()))));
        }
        if (search != null)
        {
            filters.add(Filters.regex(ENTITY_PATH, Pattern.quote(search), "i"));
        }
        return executeFind(and(filters));
    }

    public FindIterable findLatestEntitiesByClassifier(String classifier, String search)
    {
        List<Bson> filters = new ArrayList<>();
        filters.add(eq(ENTITY_CLASSIFIER_PATH, classifier));
        filters.add(regex(VERSION_ID, BRANCH_SNAPSHOT("")));
        if (search != null)
        {
            filters.add(Filters.regex(ENTITY_PATH, Pattern.quote(search), "i"));
        }
        return executeFind(and(filters));
    }

    public Optional<Entity> getEntity(String groupId, String artifactId, String versionId, String path)
    {
        Bson filterByKey = getEntityPathFilter(groupId, artifactId, versionId, path);
        return findOne(filterByKey).map(T::getEntity);
    }

    public List<T> getStoredEntities(String groupId, String artifactId)
    {
        return find(getArtifactFilter(groupId, artifactId));
    }

    public List<T> getStoredEntities(String groupId, String artifactId, String versionId)
    {
        return find(getArtifactAndVersionFilter(groupId, artifactId, versionId));
    }

    public List<Entity> getAllEntities(String groupId, String artifactId, String versionId)
    {
        return find(getArtifactAndVersionFilter(groupId, artifactId, versionId)).stream().map(T::getEntity).collect(Collectors.toList());
    }

    public List<Entity> getEntitiesByPackage(String groupId, String artifactId, String versionId, String packageName, Set<String> classifierPaths, boolean includeSubPackages)
    {
        Bson filter = getArtifactAndVersionFilter(groupId, artifactId, versionId);
        if (includeSubPackages)
        {
            filter = and(filter, regex(ENTITY_PACKAGE, "^" + packageName + "*"));
        }
        else
        {
            filter = and(filter, eq(ENTITY_PACKAGE, packageName));
        }
        Stream<Entity> entities = find(filter).stream().map(T::getEntity);
        if (classifierPaths != null && !classifierPaths.isEmpty())
        {
            entities = entities.filter(entity -> classifierPaths.contains(entity.getClassifierPath()));
        }
        return entities.collect(Collectors.toList());
    }

    public FindIterable findReleasedEntitiesByClassifier(String classifier)
    {
        return executeFind(and(eq(ENTITY_CLASSIFIER_PATH, classifier), not(regex(VERSION_ID, BRANCH_SNAPSHOT("")))));
    }

    public FindIterable findLatestEntitiesByClassifier(String classifier)
    {
        return executeFind(and(eq(ENTITY_CLASSIFIER_PATH, classifier), regex(VERSION_ID, BRANCH_SNAPSHOT(""))));
    }

    public FindIterable findEntitiesByClassifier(String groupId, String artifactId, String versionId, String classifier)
    {
        return executeFind(and(getArtifactAndVersionFilter(groupId, artifactId, versionId), eq(ENTITY_CLASSIFIER_PATH, classifier)));
    }

    public long delete(String groupId, String artifactId, String versionId)
    {
        return delete(getArtifactAndVersionFilter(groupId, artifactId, versionId));
    }

    public long delete(String groupId, String artifactId)
    {
        return delete(getArtifactFilter(groupId, artifactId));
    }

    public List<Pair<String, String>> getStoredEntitiesCoordinates()
    {
        List<Pair<String, String>> result = new ArrayList<>();
        BasicDBList concat = new BasicDBList();
        concat.add("$groupId");
        concat.add(":");
        concat.add("$artifactId");
        Bson allCoordinates = Aggregates.project(Projections.fields(
                Projections.excludeId(),
                Projections.include(GROUP_ID, ARTIFACT_ID),
                Projections.computed("coordinate", new BasicDBObject("$concat", concat))));

        getCollection().aggregate(Arrays.asList(allCoordinates, group("$coordinate"))).forEach((Consumer<Document>) document ->
                {
                    StringTokenizer tokenizer = new StringTokenizer(document.getString("_id"), ":");
                    result.add(Tuples.pair(tokenizer.nextToken(), tokenizer.nextToken()));
                }
        );
        return result;
    }
}