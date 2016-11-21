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
package br.com.imdt.os.imdt.katharsis.jpa;

import br.com.imdt.os.imdt.katharsis.jpa.security.KatharsisJpaAuthorizer;
import java.util.HashMap;
import javax.persistence.EntityManager;
import javax.servlet.http.HttpServletRequest;

public class KatharsisJpaContextHolder {

    EntityManager entityManager;
    HttpServletRequest request;
    String baseUrl;
    KatharsisJpaAuthorizer authorizer;

    public KatharsisJpaContextHolder(HttpServletRequest request, String baseUrl, EntityManager entityManager, KatharsisJpaAuthorizer authorizer) {
        this.request = request;
        this.baseUrl = baseUrl;
        this.entityManager = entityManager;
        this.authorizer = authorizer;
    }

    public HttpServletRequest getHttpServletRequest() {
        return request;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public EntityManager getEntityManager() {
        return entityManager;
    }

    public KatharsisJpaAuthorizer getAuthorizer() {
        return authorizer;
    }

}
