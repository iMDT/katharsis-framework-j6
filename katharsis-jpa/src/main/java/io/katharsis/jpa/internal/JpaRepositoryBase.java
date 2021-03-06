package io.katharsis.jpa.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;

import io.katharsis.jpa.JpaModule;
import io.katharsis.jpa.JpaRepositoryFilter;
import io.katharsis.jpa.internal.paging.PagedRepositoryBase;
import io.katharsis.jpa.mapping.IdentityMapper;
import io.katharsis.jpa.mapping.JpaMapper;
import io.katharsis.jpa.mapping.JpaMapping;
import io.katharsis.jpa.query.JpaQuery;
import io.katharsis.jpa.query.JpaQueryExecutor;
import io.katharsis.jpa.query.Tuple;
import io.katharsis.queryspec.QuerySpec;

public abstract class JpaRepositoryBase<T> extends PagedRepositoryBase<T> {

	protected JpaModule module;

	protected Class<T> resourceClass;

	protected JpaMapper<?, T> mapper;

	/**
	 * In case of a mapping the entityType differents from the resourceType
	 */
	protected Class<?> entityClass;

	private boolean readable = true;

	private boolean updateable = true;

	private boolean createable = true;

	private boolean deleteable = true;

	protected <E> JpaRepositoryBase(JpaModule module, Class<T> resourceType) {
		this.module = module;
		this.resourceClass = resourceType;

		JpaMapping<?, T> mapping = module.getMapping(resourceType);
		if (mapping != null) {
			entityClass = mapping.getEntityClass();
			mapper = mapping.getMapper();
		}
		else {
			entityClass = resourceType;
			mapper = IdentityMapper.newInstance();
		}
	}

	
	/**
	 * By default LookupIncludeBehavior.ALWAYS is in place and we let the relationship repositories load the relations. There 
	 * is no need to do join fetches, which can lead to problems with paging (evaluated in memory instead of the db).
	 * 
	 * @param fieldName of the relation to fetch
	 * @return relation will be eagerly fetched if true
	 */
	protected boolean fetchRelations(String fieldName) { // NOSONAR
		return false;
	}

	/**
	 * For read we always use a clean entity manager and return detached entities.
	 * For example, Jackson cannot handle proxies. And Katharsis is modifying the entities
	 * for subgraph loading.
	 */
	protected void resetEntityManager() {
		EntityManager em = module.getEntityManager();
		em.flush();
		em.clear();
	}
	
	protected static <D> D getUniqueOrNull(List<D> list) {
		if (list.isEmpty()) {
			return null;
		}
		else if (list.size() == 1) {
			return list.get(0);
		}
		else {
			throw new IllegalStateException("unique result expected");
		}
	}

	protected static <D, I> Map<I, D> getUniqueOrNull(Map<I, Iterable<D>> map) {
		Map<I, D> oneMap = new HashMap<>();
		for (Map.Entry<I, Iterable<D>> entry : map.entrySet()) {
			I id = entry.getKey();
			Iterable<D> values = entry.getValue();
			
			Iterator<D> iterator = values.iterator();
			if(iterator.hasNext()){
				D value = iterator.next();
				if(iterator.hasNext()){
					throw new IllegalStateException("expected unique result for " + id);
				}
				oneMap.put(id, value);
			}
		}
		return oneMap;
	}

	protected QuerySpec filterQuerySpec(QuerySpec querySpec) {
		QuerySpec filteredQuerySpec = querySpec;
		for (JpaRepositoryFilter filter : module.getFilters()) {
			if (filter.accept(resourceClass)) {
				filteredQuerySpec = filter.filterQuerySpec(this, filteredQuerySpec);
			}
		}
		return filteredQuerySpec;
	}

	protected <E> JpaQuery<E> filterQuery(QuerySpec querySpec, JpaQuery<E> query) {
		JpaQuery<E> filteredQuery = query;
		for (JpaRepositoryFilter filter : module.getFilters()) {
			if (filter.accept(resourceClass)) {
				filteredQuery = filter.filterQuery(this, querySpec, filteredQuery);
			}
		}
		return filteredQuery;
	}

	protected <E> JpaQueryExecutor<E> filterExecutor(QuerySpec querySpec, JpaQueryExecutor<E> executor) {
		JpaQueryExecutor<E> filteredExecutor = executor;
		for (JpaRepositoryFilter filter : module.getFilters()) {
			if (filter.accept(resourceClass)) {
				filteredExecutor = filter.filterExecutor(this, querySpec, filteredExecutor);
			}
		}
		return filteredExecutor;
	}

	protected List<Tuple> filterTuples(QuerySpec querySpec, List<Tuple> tuples) {
		List<Tuple> filteredTuples = tuples;
		for (JpaRepositoryFilter filter : module.getFilters()) {
			if (filter.accept(resourceClass)) {
				filteredTuples = filter.filterTuples(this, querySpec, filteredTuples);
			}
		}
		return filteredTuples;
	}

	protected List<T> filterResults(QuerySpec querySpec, List<T> resources) {
		List<T> filteredResources = resources;
		for (JpaRepositoryFilter filter : module.getFilters()) {
			if (filter.accept(resourceClass)) {
				filteredResources = filter.filterResults(this, querySpec, filteredResources);
			}
		}
		return filteredResources;
	}

	protected List<T> map(List<Tuple> tuples) {
		List<T> resources = new ArrayList<>();
		for (Tuple tuple : tuples) {
			resources.add(mapper.map(tuple));
		}
		return resources;
	}

	public boolean isReadable() {
		return readable;
	}

	/**
	 * @param readable if true no reads will be allowed
	 */
	public void setReadable(boolean readable) {
		this.readable = readable;
	}

	public boolean isUpdateable() {
		return updateable;
	}

	/**
	 * @param updateable if true no updates will be allowed
	 */
	public void setUpdateable(boolean updateable) {
		this.updateable = updateable;
	}

	public boolean isCreateable() {
		return createable;
	}

	/**
	 * @param createable if true no creations will be allowed
	 */
	public void setCreateable(boolean createable) {
		this.createable = createable;
	}

	public boolean isDeleteable() {
		return deleteable;
	}

	/**
	 * @param deleteable if true no deletions will be allowed
	 */
	public void setDeleteable(boolean deleteable) {
		this.deleteable = deleteable;
	}

	protected void checkReadable() {
		if (!readable) {
			throw new UnsupportedOperationException("reads not supported");
		}
	}

	protected void checkDeleteable() {
		if (!deleteable) {
			throw new UnsupportedOperationException("deletions not supported");
		}
	}

	protected void checkCreateable() {
		if (!createable) {
			throw new UnsupportedOperationException("creation not supported");
		}
	}

	protected void checkUpdateable() {
		if (!updateable) {
			throw new UnsupportedOperationException("updates not supported");
		}
	}
}
