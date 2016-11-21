package br.com.imdt.os.imdt.katharsis.jpa.security;

public interface KatharsisJpaAuthorizer {

    boolean isCurrentUserInRole(String role);
}
