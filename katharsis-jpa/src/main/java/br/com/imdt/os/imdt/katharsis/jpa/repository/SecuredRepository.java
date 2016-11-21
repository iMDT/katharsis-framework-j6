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
import br.com.imdt.os.imdt.katharsis.jpa.exception.AccessDeniedException;
import br.com.imdt.os.imdt.katharsis.jpa.security.KatharsisJpaAuthorizer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class SecuredRepository implements IRepository {

    private static final Logger log = LoggerFactory.getLogger(SecuredRepository.class);

    /**
     * Context holder, used to access the application methods and properties,
     * authorize and katharsis config from within the repository.
     */
    KatharsisJpaContextHolder context;

    /**
     * Set of roles that have read permissions (used by the repositories
     * extending this class).
     */
    protected final Set<String> readRoles = new HashSet<String>();

    /**
     * Set of roles that have write permissions (used by the repositories
     * extending this class).
     */
    protected final Set<String> writeRoles = new HashSet<String>();

    /**
     * Verify if the current user have one of the supplied permissions
     */
    protected void checkPermissions(Set<String> neededPermissions) throws AccessDeniedException {
        for (String permission : neededPermissions) {
            if (context.getAuthorizer().isCurrentUserInRole(permission)) {
                return;
            }
        }
        throw new AccessDeniedException("The user does not have the needed roles to perform this operation. Roles: " + neededPermissions);
    }

    /**
     * Verify if the current user have the supplied permission
     */
    protected boolean checkPermission(String role) {
        Set<String> permissions = new HashSet<String>();
        permissions.add(role);
        try {
            checkPermissions(permissions);
            return true;
        } catch (AccessDeniedException e) {
            return false;
        }
    }

    public Set<String> getReadRoles() {
        return Collections.unmodifiableSet(readRoles);
    }

    public Set<String> getWriteRoles() {
        return Collections.unmodifiableSet(writeRoles);
    }

    public void setContext(KatharsisJpaContextHolder context) {
        this.context = context;
    }

    public KatharsisJpaContextHolder getContext() {
        return context;
    }
}
