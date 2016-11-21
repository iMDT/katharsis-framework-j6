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

import br.com.imdt.os.imdt.katharsis.jpa.dao.IDao;
import io.katharsis.queryParams.QueryParams;
import io.katharsis.repository.RelationshipRepository;
import io.katharsis.resource.exception.ResourceNotFoundException;
import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseRelationshipRepository<ORIG_T extends Object, ORIG_ID extends Serializable, DEST_T extends Object, DEST_ID extends Serializable> extends SecuredRepository implements RelationshipRepository<ORIG_T, ORIG_ID, DEST_T, DEST_ID>, IRepository {

    private static final Logger log = LoggerFactory.getLogger(BaseRelationshipRepository.class);

    private final Class origType;
    //private final Class destType;

    {
        origType = (Class) (((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0]);
        //destType = (Class) (((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[2]);
    }

    IDao<ORIG_T, ORIG_ID> dao;

    @Override
    public void setRelation(ORIG_T t, DEST_ID d_id, String fieldName) {

        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void setRelations(ORIG_T t, Iterable<DEST_ID> itrbl, String fieldName) {

        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void addRelations(ORIG_T t, Iterable<DEST_ID> itrbl, String fieldName) {

        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void removeRelations(ORIG_T t, Iterable<DEST_ID> itrbl, String fieldName) {

        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public DEST_T findOneTarget(ORIG_ID t_id, String fieldName, QueryParams qp) {

        try {
            ORIG_T obj = dao.get(t_id);
            if (obj == null) {
                throw new ResourceNotFoundException(dao.getJsonApiResourceName() + '/' + t_id);
            }

            //System.out.println("origType = " + origType.getName());
            return (DEST_T) new PropertyDescriptor(fieldName, origType).getReadMethod().invoke(obj);
        } catch (InvocationTargetException ex) {
            log.error(ex.getMessage(), ex);
        } catch (IntrospectionException ex) {
            log.error(ex.getMessage(), ex);
        } catch (IllegalAccessException ex) {
            log.error(ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            log.error(ex.getMessage(), ex);
        }

        return null;
    }

    @Override
    public Iterable<DEST_T> findManyTargets(ORIG_ID t_id, String fieldName, QueryParams qp) {

        try {
            ORIG_T obj = dao.get(t_id);
            if (obj == null) {
                throw new ResourceNotFoundException(dao.getJsonApiResourceName() + '/' + t_id);
            }
            return (Iterable<DEST_T>) new PropertyDescriptor(fieldName, origType).getReadMethod().invoke(obj);
        } catch (IllegalAccessException ex) {
            log.error(ex.getMessage(), ex);
        } catch (IntrospectionException ex) {
            log.error(ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            log.error(ex.getMessage(), ex);
        } catch (InvocationTargetException ex) {
            log.error(ex.getMessage(), ex);
        }

        return null;
    }

    public void setDao(IDao<ORIG_T, ORIG_ID> dao) {
        this.dao = dao;
    }

    protected void setupRelationshipRepositorySecurity(SecuredRepository origRepository, SecuredRepository destRepository) {
        Set<String> bothSideReadRoles = new HashSet<String>();
        bothSideReadRoles.addAll(origRepository.getReadRoles());
        bothSideReadRoles.retainAll(destRepository.getReadRoles());
        readRoles.addAll(bothSideReadRoles);

        Set<String> bothSideWriteRoles = new HashSet<String>();
        bothSideWriteRoles.addAll(origRepository.getWriteRoles());
        bothSideWriteRoles.retainAll(destRepository.getWriteRoles());
        writeRoles.addAll(bothSideWriteRoles);
    }

}
