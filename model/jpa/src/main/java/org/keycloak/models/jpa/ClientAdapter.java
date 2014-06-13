package org.keycloak.models.jpa;

import org.keycloak.models.ClientModel;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleContainerModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.models.jpa.entities.ClientEntity;
import org.keycloak.models.jpa.entities.ClientUserSessionAssociationEntity;
import org.keycloak.models.jpa.entities.ScopeMappingEntity;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public abstract class ClientAdapter implements ClientModel {
    protected ClientEntity entity;
    protected RealmModel realm;
    protected EntityManager em;

    public ClientAdapter(RealmModel realm, ClientEntity entity, EntityManager em) {
        this.realm = realm;
        this.entity = entity;
        this.em = em;
    }

    public ClientEntity getEntity() {
        return entity;
    }

    @Override
    public String getId() {
        return entity.getId();
    }

    @Override
    public RealmModel getRealm() {
        return realm;
    }

    @Override
    public String getClientId() {
        return entity.getName();
    }

    @Override
    public boolean isEnabled() {
        return entity.isEnabled();
    }

    @Override
    public void setEnabled(boolean enabled) {
        entity.setEnabled(enabled);
    }

    @Override
    public long getAllowedClaimsMask() {
        return entity.getAllowedClaimsMask();
    }

    @Override
    public void setAllowedClaimsMask(long mask) {
        entity.setAllowedClaimsMask(mask);
    }

    @Override
    public boolean isPublicClient() {
        return entity.isPublicClient();
    }

    @Override
    public void setPublicClient(boolean flag) {
        entity.setPublicClient(flag);
    }

    @Override
    public Set<String> getWebOrigins() {
        Set<String> result = new HashSet<String>();
        result.addAll(entity.getWebOrigins());
        return result;
    }



    @Override
    public void setWebOrigins(Set<String> webOrigins) {
        entity.setWebOrigins(webOrigins);
    }

    @Override
    public void addWebOrigin(String webOrigin) {
        entity.getWebOrigins().add(webOrigin);
    }

    @Override
    public void removeWebOrigin(String webOrigin) {
        entity.getWebOrigins().remove(webOrigin);
    }

    @Override
    public Set<String> getRedirectUris() {
        Set<String> result = new HashSet<String>();
        result.addAll(entity.getRedirectUris());
        return result;
    }

    @Override
    public void setRedirectUris(Set<String> redirectUris) {
        entity.setRedirectUris(redirectUris);
    }

    @Override
    public void addRedirectUri(String redirectUri) {
        entity.getRedirectUris().add(redirectUri);
    }

    @Override
    public void removeRedirectUri(String redirectUri) {
        entity.getRedirectUris().remove(redirectUri);
    }

    @Override
    public String getSecret() {
        return entity.getSecret();
    }

    @Override
    public void setSecret(String secret) {
        entity.setSecret(secret);
    }

    @Override
    public boolean validateSecret(String secret) {
        return secret.equals(entity.getSecret());
    }

    @Override
    public int getNotBefore() {
        return entity.getNotBefore();
    }

    @Override
    public void setNotBefore(int notBefore) {
        entity.setNotBefore(notBefore);
    }

    @Override
    public int getActiveUserSessions() {
        Query query = em.createNamedQuery("getActiveClientSessions");
        query.setParameter("clientId", getId());
        Object count = query.getSingleResult();
        return ((Number)count).intValue();
    }

    @Override
    public Set<UserSessionModel> getUserSessions() {
        Set<UserSessionModel> list = new HashSet<UserSessionModel>();
        TypedQuery<ClientUserSessionAssociationEntity> query = em.createNamedQuery("getClientUserSessionByClient", ClientUserSessionAssociationEntity.class);
        String id = getId();
        query.setParameter("clientId", id);
        List<ClientUserSessionAssociationEntity> results = query.getResultList();
        for (ClientUserSessionAssociationEntity entity : results) {
            list.add(new UserSessionAdapter(em, realm, entity.getSession()));
        }
        return list;
    }

    public void deleteUserSessionAssociation() {
        em.createNamedQuery("removeClientUserSessionByClient").setParameter("clientId", getId()).executeUpdate();
    }

    @Override
    public Set<RoleModel> getRealmScopeMappings() {
        Set<RoleModel> roleMappings = getScopeMappings();

        Set<RoleModel> appRoles = new HashSet<RoleModel>();
        for (RoleModel role : roleMappings) {
            RoleContainerModel container = role.getContainer();
            if (container instanceof RealmModel) {
                if (((RealmModel) container).getId().equals(realm.getId())) {
                    appRoles.add(role);
                }
            }
        }

        return appRoles;
    }



    @Override
    public Set<RoleModel> getScopeMappings() {
        TypedQuery<ScopeMappingEntity> query = em.createNamedQuery("clientScopeMappings", ScopeMappingEntity.class);
        query.setParameter("client", getEntity());
        List<ScopeMappingEntity> entities = query.getResultList();
        Set<RoleModel> roles = new HashSet<RoleModel>();
        for (ScopeMappingEntity entity : entities) {
            roles.add(new RoleAdapter(realm, em, entity.getRole()));
            em.detach(entity);
        }
        return roles;
    }

    @Override
    public void addScopeMapping(RoleModel role) {
        if (hasScope(role)) return;
        ScopeMappingEntity entity = new ScopeMappingEntity();
        entity.setClient(getEntity());
        entity.setRole(((RoleAdapter) role).getRole());
        em.persist(entity);
        em.flush();
        em.detach(entity);
    }

    @Override
    public void deleteScopeMapping(RoleModel role) {
        TypedQuery<ScopeMappingEntity> query = getRealmScopeMappingQuery((RoleAdapter) role);
        List<ScopeMappingEntity> results = query.getResultList();
        if (results.size() == 0) return;
        for (ScopeMappingEntity entity : results) {
            em.remove(entity);
        }
    }

    protected TypedQuery<ScopeMappingEntity> getRealmScopeMappingQuery(RoleAdapter role) {
        TypedQuery<ScopeMappingEntity> query = em.createNamedQuery("hasScope", ScopeMappingEntity.class);
        query.setParameter("client", getEntity());
        query.setParameter("role", ((RoleAdapter) role).getRole());
        return query;
    }

    @Override
    public boolean hasScope(RoleModel role) {
        Set<RoleModel> roles = getScopeMappings();
        if (roles.contains(role)) return true;

        for (RoleModel mapping : roles) {
            if (mapping.hasRole(role)) return true;
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!this.getClass().equals(o.getClass())) return false;

        ClientAdapter that = (ClientAdapter) o;
        return that.getId().equals(getId());
    }

    @Override
    public int hashCode() {
        return entity.getId().hashCode();
    }
}
