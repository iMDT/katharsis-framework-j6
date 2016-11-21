/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package br.com.imdt.os.imdt.katharsis.jpa.repository;

import br.com.imdt.os.imdt.katharsis.jpa.KatharsisJpaContextHolder;
import br.com.imdt.os.imdt.katharsis.jpa.dao.IDao;
import br.com.imdt.os.imdt.katharsis.jpa.information.Links;
import br.com.imdt.os.imdt.katharsis.jpa.information.MetaData;
import io.katharsis.queryParams.QueryParams;
import io.katharsis.repository.LinksRepository;
import io.katharsis.repository.MetaRepository;
import io.katharsis.repository.ResourceRepository;
import io.katharsis.resource.annotations.JsonApiResource;
import io.katharsis.response.LinksInformation;
import io.katharsis.utils.ClassUtils;
import io.katharsis.utils.java.Optional;
import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseRepository<T extends Object, ID extends Serializable> extends SecuredRepository implements ResourceRepository<T, ID>, MetaRepository<T>, LinksRepository<T> {

    private static final Logger log = LoggerFactory.getLogger(BaseRepository.class);

    /**
     * A repository can enforce a filter, useful for situations like users with
     * limited access.
     */
    protected abstract Map<String, Set<String>> getRepositoryFilters(QueryParams qp);

    

    /**
     * A repository can specify custom metadata using this property.
     */
    final private HashMap<String, Object> customMetadata = new HashMap<String, Object>();

    /**
     * Data access object used to obtain information
     */
    IDao<T, ID> dao;

    /**
     * Find one is used for pre-persist objects and for get operations. If the
     * old version of the object is needed at the save method you need to
     * override this function and get the detached version.
     */
    @Override
    public T findOne(ID id, QueryParams qp) {
        checkPermissions(readRoles);
        return dao.get(id);
    }

    /**
     * Find all is used for get operations. This method supports filter,
     * pagination, sorting. Check the documentation in order to explore all
     * capabilities.
     */
    @Override
    public Iterable<T> findAll(QueryParams qp) {
        checkPermissions(readRoles);
        return dao.list(qp, getRepositoryFilters(qp));
    }

    /**
     * Katharsis supports the usage of findAll specifying a list of ID' instead
     * of filters. You can get the same behavior using the filters, so this
     * method is not implemented.
     */
    @Override
    public Iterable<T> findAll(Iterable<ID> itrbl, QueryParams qp) {
        throw new RuntimeException("Not implemented, use filters for get with multiple ids");
    }

    /**
     * Save is used for PUT/POST operations.
     */
    @Override
    public <S extends T> S save(S s) {
        checkPermissions(writeRoles);
        return (S) dao.save((T) s);
    }

    /**
     * Delete is used for DELETE operations.
     */
    @Override
    public void delete(ID id) {
        checkPermissions(writeRoles);
        dao.delete(id);
    }

    /**
     * Generate count when pagination is happening, and also submit custom
     * metadata for katharsis
     */
    @Override
    public MetaData getMetaInformation(Iterable<T> entities, QueryParams qp) {
        MetaData md = null;
        if (isPaging(qp)) {
            if (md == null) {
                md = new MetaData();
            }
            md.setCount(dao.count(qp, getRepositoryFilters(qp)));
        }

        if (customMetadata.size() > 0) {
            if (md == null) {
                md = new MetaData();
            }
            md.setCustomMetadata(customMetadata);
        }

        return md;
    }

    /**
     * Generate links for pagination
     */
    @Override
    public LinksInformation getLinksInformation(Iterable<T> itrbl, QueryParams qp) {
        if (isPaging(qp)) {
            Long count = dao.count(qp, getRepositoryFilters(qp));
            return new Links(getRepositoryName(), context, qp, count);
        }
        return null;
    }

    /**
     * Detect if the request is a paginated request
     */
    private boolean isPaging(QueryParams queryParams) {
        return queryParams.getPagination() != null && !queryParams.getPagination().isEmpty();
    }

    /**
     * Get the name of repository extending this class
     */
    protected String getRepositoryName() {
        Optional<JsonApiResource> result = ClassUtils.getAnnotation((Class) (((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0]), JsonApiResource.class);
        if (!result.isPresent()) {
            return null;
        }
        return result.get().type();
    }

    public void setDao(IDao<T, ID> dao) {
        this.dao = dao;
    }

    protected EntityManager getEntityManager() {
        return context.getEntityManager();
    }

    protected EntityManager em() {
        return this.getEntityManager();
    }

}
