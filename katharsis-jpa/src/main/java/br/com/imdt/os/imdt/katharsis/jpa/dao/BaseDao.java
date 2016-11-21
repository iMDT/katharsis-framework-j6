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
package br.com.imdt.os.imdt.katharsis.jpa.dao;

import br.com.imdt.os.imdt.katharsis.jpa.exception.FilterException;
import br.com.imdt.os.imdt.katharsis.jpa.exception.SortingException;
import br.com.imdt.os.imdt.katharsis.jpa.helper.ReflectionHelper;
import br.com.imdt.os.imdt.katharsis.jpa.query.ParsedQuery;
import io.katharsis.queryParams.QueryParams;
import io.katharsis.queryParams.RestrictedPaginationKeys;
import io.katharsis.queryParams.RestrictedSortingValues;
import io.katharsis.resource.annotations.JsonApiResource;
import io.katharsis.utils.ClassUtils;
import io.katharsis.utils.java.Optional;
import io.katharsis.utils.parser.TypeParser;
import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BaseDao<T extends Object, ID extends Serializable> implements IDao<T, ID> {

    private static final Logger log = LoggerFactory.getLogger(BaseDao.class);

    private AtomicLong paramCounter = new AtomicLong(0L);
    private TypeParser typeParser = new TypeParser();
    private Class type;
    private String idProperty;
    private EntityManager em;

    public BaseDao(EntityManager em) {
        try {
            //Detects the class that extends from this DAO
            type = (Class) (((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0]);
        } catch (Exception e) {
        }

        try {
            //Detects the field annotated with @Id annotation
            idProperty = ReflectionHelper.getIdProperty(type);
        } catch (Exception e) {
        }

        setEntityManager(em);
    }

    @Override
    public Iterable<T> list(QueryParams qp, Map<String, Set<String>> additionalFilters) {
        String selectPart = "select _out_obj_ from " + type.getSimpleName()
                + " _out_obj_ where _out_obj_." + idProperty
                + " in ( select distinct _obj_." + idProperty
                + " from " + type.getSimpleName() + " _obj_ ";

        ParsedQuery query = buildQuery(qp, additionalFilters);
        Map<String, Object> dbParameters = (query == null) ? new HashMap<String, Object>() : (Map<String, Object>) query.getParameters();

        String fullQuery = query.generateQuery(selectPart);
        fullQuery += " ) ";

        String orderBy = (String) query.getSort();
        if (orderBy != null && orderBy.length() > 0) {
            fullQuery += " order by " + orderBy;
        }

        log.trace("Query: {}, Params: {}", fullQuery, dbParameters);

        Query q = em.createQuery(fullQuery);

        //Pagination
        Map<RestrictedPaginationKeys, Integer> pagination = qp.getPagination();

        Integer limit = pagination.get(RestrictedPaginationKeys.limit);
        if(limit == null) {
            limit = 1000;
        }
        
        Integer offset = pagination.get(RestrictedPaginationKeys.offset);
        if (offset == null) {
            offset = 0;
        }

        if ((limit <= 0) || (limit > 1000)) {
            limit = 1000;
        }

        q.setFirstResult(offset);
        q.setMaxResults(limit);

        //Set parameters
        for (String paramName : dbParameters.keySet()) {
            q.setParameter(paramName, dbParameters.get(paramName));
        }

        return q.getResultList();
    }

    @Override
    public Long count(QueryParams qp, Map<String, Set<String>> additionalFilters) {
        String selectPart = "select count(distinct _obj_." + idProperty + ") from " + type.getSimpleName() + " _obj_ ";

        ParsedQuery query = buildQuery(qp, additionalFilters);
        Map<String, Object> dbParameters = query.getParameters();
        String fullQuery = query.generateQuery(selectPart);

        log.debug("Query: {}, Params: {}", fullQuery, dbParameters);

        Query q = em.createQuery(fullQuery);
        for (String paramName : dbParameters.keySet()) {
            q.setParameter(paramName, dbParameters.get(paramName));
        }

        return (Long) q.getResultList().get(0);
    }

    @Override
    public T get(ID id) {
        String jpql = "select _obj_ from " + type.getSimpleName() + " _obj_ where _obj_." + idProperty + "  = :id";
        System.out.println("Query = " + jpql);
        Query q = em.createQuery(jpql);
        q.setParameter("id", id);
        List<T> l = q.getResultList();
        if (l.size() > 0) {
            return l.get(0);
        } else {
            return null;
        }
    }

    @Override
    public T getDetached(ID id) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public T save(T obj) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void delete(ID id) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getJsonApiResourceName() {
        Optional<JsonApiResource> result = ClassUtils.getAnnotation((Class) (((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0]), JsonApiResource.class);
        if (!result.isPresent()) {
            return null;
        }
        return result.get().type();
    }

    @Override
    public T detach(T obj) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void setEntityManager(EntityManager em) {
        this.em = em;
    }

    private ParsedQuery buildQuery(QueryParams qp, Map<String, Set<String>> additionalFilters) {
        String jsonApiResourceName = getJsonApiResourceName();

        ParsedQuery query = new ParsedQuery();

        ArrayList<Pair<String, String>> sortFields = new ArrayList<Pair<String, String>>();
        //Read sort parameters
        try {
            Map<String, RestrictedSortingValues> thisEntitySorting = null;
            thisEntitySorting = qp.getSorting().getParams().get(jsonApiResourceName).getParams();
            try {
                HashMap<Integer, Pair<String, String>> hmSort = new HashMap<Integer, Pair<String, String>>();

                for (String arrayKey : thisEntitySorting.keySet()) {
                    String sortField = arrayKey;
                    Integer sortSlot = 0;
                    try {
                        String sortFieldParts[] = sortField.split(Pattern.quote("."));
                        sortSlot = Integer.parseInt(sortFieldParts[0]);
                        sortField = "";
                        for (int i = 1; i < sortFieldParts.length; i++) {
                            if (sortField.length() > 0) {
                                sortField += ".";
                            }
                            sortField += sortFieldParts[i];
                        }
                    } catch (Exception e) {
                    }

                    if (hmSort.containsKey(sortSlot)) {
                        throw new RuntimeException("Sorting slot " + sortSlot + " already in use");
                    }

                    hmSort.put(sortSlot, ImmutablePair.of(sortField, thisEntitySorting.get(arrayKey).toString()));
                }

                ArrayList<Integer> sortSlots = new ArrayList<Integer>(hmSort.keySet());
                Collections.sort(sortSlots);

                for (Integer slot : sortSlots) {
                    sortFields.add(hmSort.get(slot));
                }
            } catch (Exception ex) {
                throw new SortingException("Error generating sort: " + ex.getMessage());
            }
        } catch (Exception e) {
        }

        //Read filter parameters
        Map<String, Set<String>> urlFilters = null;
        try {
            urlFilters = qp.getFilters().getParams().get(jsonApiResourceName).getParams();
        } catch (Exception e) {
            urlFilters = new HashMap<String, Set<String>>();
        }
        log.debug("urlFilters = " + urlFilters);
        log.debug("additionalFilters = " + additionalFilters);

        //Merge received filters (additional filters (provided by repository) and url filters)
        //Level, Filter, Set<Values>
        Map<Integer, Map<String, Set<String>>> allFilters = new HashMap<Integer, Map<String, Set<String>>>();
        Map<Integer, String> filterLevelLogicOperator = new HashMap<Integer, String>();

        Set<Map<String, Set<String>>> sourceMaps = new HashSet<Map<String, Set<String>>>();
        sourceMaps.add(urlFilters);
        sourceMaps.add(additionalFilters);

        for (Map<String, Set<String>> sourceMap : sourceMaps) {
            for (String key : sourceMap.keySet()) {
                String originalKey = key;
                String keyParts[] = key.split(Pattern.quote("."));
                Integer parsedFilterLevel = null;
                Integer filterLevel;
                try {
                    parsedFilterLevel = Integer.parseInt(keyParts[0]);
                } catch (Exception e) {
                }

                filterLevel = (parsedFilterLevel == null) ? 0 : parsedFilterLevel;

                if (keyParts.length == 1) {
                    String firstPartAsString = keyParts[0];
                    Integer firstPartAsInteger = null;
                    try {
                        firstPartAsInteger = Integer.parseInt(firstPartAsString);
                    } catch (Exception e) {
                    }

                    //?filter[entity][0]=AND
                    //If the key is only a number, it is a filter level logic operator indicator
                    if (firstPartAsInteger != null) {
                        if (sourceMap.get(key) != null && sourceMap.get(key).size() > 0) {
                            String value = sourceMap.get(key).iterator().next();

                            if ("o".equals(value.toLowerCase()) || "or".equals(value.toLowerCase())) {
                                value = "or";
                            } else if ("a".equals(value.toLowerCase()) || "and".equals(value.toLowerCase())) {
                                value = "and";
                            } else {
                                log.error("Unrecognized level logic operator {} for level {}", value, filterLevel);
                                continue;
                            }

                            if (filterLevelLogicOperator.get(filterLevel) != null) {
                                throw new FilterException("Multiple logic operators received for level " + filterLevel);
                            }

                            filterLevelLogicOperator.put(filterLevel, value);
                            continue;
                        }
                    }
                }

                if (parsedFilterLevel == null) {
                    key = StringUtils.join(keyParts, ".");
                } else {
                    List<String> keyPartsAsArray = new ArrayList(Arrays.asList(keyParts));
                    //Remove the level from the key name
                    keyPartsAsArray.remove(keyPartsAsArray.get(0));
                    key = StringUtils.join(keyPartsAsArray, ".");
                }

                if (!allFilters.containsKey(filterLevel)) {
                    allFilters.put(filterLevel, new HashMap<String, Set<String>>());
                }

                if (!allFilters.get(filterLevel).containsKey(key)) {
                    allFilters.get(filterLevel).put(key, new HashSet<String>());
                }

                allFilters.get(filterLevel).get(key).addAll(sourceMap.get(originalKey));
            }
        }

        //Json property name to java property name
        HashMap<String, String> json2JavaProp = new HashMap<String, String>();

        //Json property name to java type
        HashMap<String, Class> json2JavaType = new HashMap<String, Class>();

        //Json property name to java generic type (i.e. List<String> => String)
        HashMap<String, Class> json2JavaGenericType = new HashMap<String, Class>();

        //Joins to be generated on iterable objects for "To Many"
        HashMap<String, String> json2Join = new HashMap<String, String>();

        //Strings used in filters and in sorting, used to limit the recursive function scope
        Set<String> joinPropertiesList = new HashSet<String>();

        if (allFilters != null) {
            for (Integer level : allFilters.keySet()) {
                for (String filteredProperty : allFilters.get(level).keySet()) {
                    log.debug("Level " + level + ", key {}, value {} ", filteredProperty, allFilters.get(level).get(filteredProperty));
                    joinPropertiesList.add(filteredProperty.toLowerCase());
                }
            }
        }

        for (Pair<String, String> sortField : sortFields) {
            joinPropertiesList.add(sortField.getLeft().toLowerCase());
        }

        new ReflectionHelper().describeObject(type, json2JavaProp, json2JavaType, json2JavaGenericType, json2Join, joinPropertiesList);

        //log.debug("json2Join elements: " + json2Join.keySet().size());
        //---Filters
        String generatedFilterQuery = "";
        Map<String, Object> dbParameters = new HashMap<String, Object>();
        HashSet<String> hsFrom = new HashSet<String>();

        if (allFilters != null) {

            for (Integer level : allFilters.keySet()) {
                String optionLogicOperator = filterLevelLogicOperator.get(level);
                String thisLevelGeneratedFilterQuery = "";
                if (generatedFilterQuery.length() > 0) {
                    generatedFilterQuery += ") AND (";
                } else {
                    generatedFilterQuery += " (";
                }

                for (String filteredProperty : allFilters.get(level).keySet()) {
                    try {
                        String filteredPropertyLc = filteredProperty.toLowerCase();
                        String filteredPropertyParts[] = filteredPropertyLc.split(Pattern.quote("."));

                        for (String part : filteredPropertyParts) {
                            if (part.startsWith("---")) {
                                log.info("Filters with logic operators starting with --- are deprecated, use entity[level]=OR|AND");
                                String value = part.split(Pattern.quote("---"))[1];
                                if ("o".equals(value.toLowerCase()) || "or".equals(value.toLowerCase())) {
                                    value = "or";
                                } else if ("a".equals(value.toLowerCase()) || "and".equals(value.toLowerCase())) {
                                    value = "and";
                                } else {
                                    throw new Exception("Unrecognized level logic operator " + value + "");
                                }

                                if (optionLogicOperator != null && !value.equals(optionLogicOperator)) {
                                    throw new RuntimeException("Multiple logic operators for same filter level are not supported");
                                }

                                optionLogicOperator = value;
                            }
                        }
                    } catch (Exception ex) {
                        log.error("Error generating filters: " + ex.getMessage(), ex);
                        throw new FilterException("Error generating filters: " + ex.getMessage());
                    }
                }

                if (optionLogicOperator == null) {
                    optionLogicOperator = "and";
                }

                for (String filteredProperty : allFilters.get(level).keySet()) {
                    try {
                        String filteredPropertyLc = filteredProperty.toLowerCase();
                        String filteredPropertyParts[] = filteredPropertyLc.split(Pattern.quote("."));

                        String optionComparator = "=";
                        ArrayList<String> optionTransform = new ArrayList<String>();

                        filteredPropertyLc = "";
                        for (String part : filteredPropertyParts) {
                            if (part.startsWith("---")) {
                                optionLogicOperator = part.split(Pattern.quote("---"))[1];
                            } else if (part.startsWith("--")) {
                                optionComparator = part.split(Pattern.quote("--"))[1];
                            } else if (part.startsWith("-")) {
                                optionTransform.add(part.split(Pattern.quote("-"))[1]);
                            } else {
                                filteredPropertyLc += (filteredPropertyLc.length() > 0) ? "." : "";
                                filteredPropertyLc += part;
                            }
                        }

                        if (!json2JavaProp.containsKey(filteredPropertyLc)) {
                            //System.out.println("Ignoring filter for unknown property " + filteredProperty);
                            throw new RuntimeException("Filter for unknown property " + filteredProperty);
                        }
                        String javaProperty = json2JavaProp.get(filteredPropertyLc);
                        String paramName = generateParamName(javaProperty + "_" + (paramCounter.incrementAndGet()));

                        Set<String> filterValueAsString = allFilters.get(level).get(filteredProperty);
                        Class cls = json2JavaType.get(filteredPropertyLc);
                        if (cls.equals(Number.class) || (cls.getSuperclass() != null && cls.getSuperclass().equals(Number.class))) {
                            if (filterValueAsString.size() == 1) {
                                String[] valueArray = filterValueAsString.iterator().next().split(",");
                                filterValueAsString = new HashSet<String>();
                                for (String value : valueArray) {
                                    filterValueAsString.add(value);
                                }
                            }
                        }

                        String newFrom = json2Join.get(filteredPropertyLc);
                        if (newFrom != null && newFrom.length() > 0) {
                            log.debug("Adding from " + newFrom);
                            hsFrom.add(newFrom);
                        }

                        Class leftSideType = json2JavaType.get(filteredPropertyLc);
                        Class leftSideGenericType = json2JavaGenericType.get(filteredPropertyLc);

                        String leftSide = javaProperty;

                        if (!leftSide.startsWith("join_")) {
                            leftSide = "_obj_." + leftSide;
                        }

                        for (String transformation : optionTransform) {
                            if ("slc".equals(transformation)) {
                                leftSide = "lower(" + leftSide + ")";
                                leftSideType = String.class;
                            } else if ("suc".equals(transformation)) {
                                leftSide = "upper(" + leftSide + ")";
                                leftSideType = String.class;
                            } else if ("sl".equals(transformation)) {
                                leftSide = "length(" + leftSide + ")";
                                leftSideType = Integer.class;
                            }
                        }

                        Boolean haveRightSide = true;
                        String comparator = "=";

                        String rightSidePrependString = "";
                        String rightSideAppendString = "";

                        if ("z".equals(optionComparator)) {
                            comparator = " is null ";
                            haveRightSide = false;
                        } else if ("n".equals(optionComparator)) {
                            comparator = " is not null ";
                            haveRightSide = false;
                        } else if ("eq".equals(optionComparator)) {
                            comparator = "=";
                            rightSidePrependString = "";
                            rightSideAppendString = "";
                        } else if ("ne".equals(optionComparator)) {
                            comparator = "<>";
                        } else if ("gt".equals(optionComparator)) {
                            if (leftSideType.equals(String.class)) {
                                throw new RuntimeException("Numeric comparator used in string field");
                            }

                            comparator = ">";
                        } else if ("ge".equals(optionComparator)) {
                            if (leftSideType.equals(String.class)) {
                                throw new RuntimeException("Numeric comparator used in string field");
                            }

                            comparator = ">=";
                        } else if ("lt".equals(optionComparator)) {
                            if (leftSideType.equals(String.class)) {
                                throw new RuntimeException("Numeric comparator used in string field");
                            }

                            comparator = "<";
                        } else if ("le".equals(optionComparator)) {
                            if (leftSideType.equals(String.class)) {
                                throw new RuntimeException("Numeric comparator used in string field");
                            }

                            comparator = "<=";
                        } else if ("sw".equals(optionComparator)) {
                            if (!leftSideType.equals(String.class)) {
                                throw new RuntimeException("Filter startsWith used with a type that's not string");
                            }
                            comparator = "like";
                            rightSideAppendString = "%";
                        } else if ("nsw".equals(optionComparator)) {
                            if (!leftSideType.equals(String.class)) {
                                throw new RuntimeException("Filter startsWith used with a type that's not string");
                            }
                            comparator = "not like";
                            rightSideAppendString = "%";
                        } else if ("ew".equals(optionComparator)) {
                            if (!leftSideType.equals(String.class)) {
                                throw new RuntimeException("Filter endsWith used with a type that's not string");
                            }
                            comparator = "like";
                            rightSidePrependString = "%";
                        } else if ("new".equals(optionComparator)) {
                            if (!leftSideType.equals(String.class)) {
                                throw new RuntimeException("Filter endsWith used with a type that's not string");
                            }
                            comparator = "not like";
                            rightSidePrependString = "%";
                        } else if ("ct".equals(optionComparator)) {
                            if (!leftSideType.equals(String.class)) {
                                throw new RuntimeException("Filter contains used with a type that's not string");
                            }

                            comparator = "like";
                            rightSidePrependString = "%";
                            rightSideAppendString = "%";
                        } else if ("nct".equals(optionComparator)) {
                            if (!leftSideType.equals(String.class)) {
                                throw new RuntimeException("Filter contains used with a type that's not string");
                            }

                            comparator = "not like";
                            rightSidePrependString = "%";
                            rightSideAppendString = "%";
                        } else if ("=".equals(optionComparator)) {
                            //Nao faz nada :D
                        } else {
                            throw new RuntimeException("Invalid comparator : " + optionComparator);
                        }

                        //Ignore filters that need values, but the values are not supplied, avoiding to generate |... field IN () ...|, or  | ... field = ...|
                        if (haveRightSide && filterValueAsString.size() == 0) {
                            log.debug("Ignoring filter field: {}, values {}, because it's empty.", filteredPropertyLc, filterValueAsString);
                            continue;
                        }

                        String logicOperator = "";
                        if ("o".equals(optionLogicOperator) || "or".equals(optionLogicOperator)) {
                            logicOperator = "OR";
                        } else if ("a".equals(optionLogicOperator) || "and".equals(optionLogicOperator)) {
                            logicOperator = "AND";
                        } else {
                            throw new RuntimeException("Invalid logic operator option: " + optionLogicOperator);
                        }

                        thisLevelGeneratedFilterQuery += (thisLevelGeneratedFilterQuery.length() > 0 ? " " + logicOperator + " " : " ");
                        String sameFieldLogicOperator = "OR";

                        if ("<>".equals(comparator) || "not like".equals(comparator)) {
                            sameFieldLogicOperator = "AND";
                        }

                        if (!haveRightSide) {
                            thisLevelGeneratedFilterQuery += leftSide + comparator;
                        } else {
                            Iterable filterValueAsExpectedType = typeParser.parse(filterValueAsString, (leftSideGenericType != null) ? leftSideGenericType : leftSideType);

                            if (Iterable.class.isAssignableFrom(leftSideType)) {
                                thisLevelGeneratedFilterQuery += ":" + paramName + " in elements(" + leftSide + ") ";
                                dbParameters.put(paramName, filterValueAsExpectedType);
                            } else if (leftSideType.equals(String.class)) {
                                if ("=".equals(comparator)) {
                                    comparator = "like"; //Use like instead of = to workaround CLOB problem in oracle
                                }

                                thisLevelGeneratedFilterQuery += "(";
                                int totalStrings = 0;

                                for (String filteredStringValue : filterValueAsString) {
                                    filteredStringValue = filteredStringValue.replaceAll(Pattern.quote("%"), "");
                                    filteredStringValue = rightSidePrependString + filteredStringValue + rightSideAppendString;
                                    totalStrings++;

                                    thisLevelGeneratedFilterQuery += ((totalStrings > 1) ? " " + sameFieldLogicOperator + " " : "") + leftSide + " " + comparator + " :" + paramName + "_" + totalStrings + " ";
                                    dbParameters.put(paramName + "_" + totalStrings, filteredStringValue);
                                }
                                thisLevelGeneratedFilterQuery += ")";
                            } else if ("=".equals(comparator)) {
                                thisLevelGeneratedFilterQuery += leftSide + " in ( :" + paramName + " ) ";
                                dbParameters.put(paramName, filterValueAsExpectedType);
                            } else {
                                int totalStrings = 0;

                                for (Object value : filterValueAsExpectedType) {

                                    totalStrings++;
                                    thisLevelGeneratedFilterQuery += ((totalStrings > 1) ? " OR " : "") + leftSide + " " + comparator + " :" + paramName + "_" + totalStrings + " ";
                                    dbParameters.put(paramName + "_" + totalStrings, value);
                                }
                            }
                        }

                    } catch (Exception ex) {
                        log.error("Error generating filters: " + ex.getMessage(), ex);
                        throw new FilterException("Error generating filters: " + ex.getMessage());
                    }
                }

                generatedFilterQuery += thisLevelGeneratedFilterQuery;
            }

            if (generatedFilterQuery.length() > 0) {
                generatedFilterQuery += ")";
            }
        }

        //Sorting
        String orderBy = "";
        for (Pair<String, String> sortPair : sortFields) {
            String sortField = sortPair.getLeft();
            String sortMode = sortPair.getRight();

            if (!json2JavaProp.containsKey(sortField)) {
                throw new SortingException("Unrecognized sort field: " + sortField);
            }

            if (orderBy.length() > 0) {
                orderBy += ",";
            }
            orderBy += sortField + " " + sortMode;
        }

        query.setParameters(dbParameters);
        query.setFilter(generatedFilterQuery);
        query.setSort(orderBy);

        String generatedFromQuery = "";
        for (String newFrom : hsFrom) {
            if (generatedFromQuery.length() > 0) {
                generatedFromQuery += " ";
            }
            generatedFromQuery += newFrom;
        }
        query.setFrom(generatedFromQuery);

        return query;
    }

    private String generateParamName(String paramName) {
        return paramName.replaceAll(Pattern.quote("."), "__");
    }
}
