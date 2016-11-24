package io.katharsis.jackson.serializer.include;

import io.katharsis.queryParams.include.Inclusion;
import io.katharsis.queryParams.params.IncludedRelationsParams;
import io.katharsis.queryParams.params.TypedParams;
import io.katharsis.resource.exception.ResourceFieldNotFoundException;
import io.katharsis.resource.field.ResourceField;
import io.katharsis.resource.information.ResourceInformation;
import io.katharsis.resource.registry.RegistryEntry;
import io.katharsis.resource.registry.ResourceRegistry;
import io.katharsis.response.BaseResponseContext;
import io.katharsis.response.Container;
import io.katharsis.response.ContainerType;
import io.katharsis.utils.PropertyUtils;
import io.katharsis.utils.java.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * Extracts inclusions from a resource.
 */
public class IncludedRelationshipExtractor {

    private static final Logger logger = LoggerFactory.getLogger(IncludedRelationshipExtractor.class);
    private final ResourceRegistry resourceRegistry;

    public IncludedRelationshipExtractor(ResourceRegistry resourceRegistry) {
        this.resourceRegistry = resourceRegistry;
    }

    public Map<ResourceDigest, Container> extractIncludedResources(Object resource, BaseResponseContext response) {
        Map<ResourceDigest, Container> includedResources = new HashMap();

        populateIncludedByDefaultResources(resource, response, ContainerType.TOP, includedResources, 1);
        try {
            populateIncludedRelationships(resource, response, includedResources, new HashSet());
        } catch (IllegalAccessException e) {
            logger.trace("Exception while extracting included fields", e);
        } catch (NoSuchMethodException e) {
            logger.trace("Exception while extracting included fields", e);
        } catch (InvocationTargetException e) {
            logger.trace("Exception while extracting included fields", e);
        } catch (NoSuchFieldException e) {
            logger.trace("Exception while extracting included fields", e);
        }
        return includedResources;
    }

    private boolean isFieldIncluded(BaseResponseContext response, String fieldName) {
        if (response.getQueryParams() == null
                || response.getQueryParams().getIncludedRelations() == null
                || response.getQueryParams().getIncludedRelations().getParams() == null) {
            return false;
        }
        IncludedRelationsParams includedRelationsParams = response.getQueryParams().getIncludedRelations().getParams().get(response.getJsonPath().getElementName());
        if (includedRelationsParams == null
                || includedRelationsParams.getParams() == null) {
            return false;
        }

        for (Inclusion inclusion : includedRelationsParams.getParams()) {
            if (inclusion.getPathList().get(0).equals(fieldName)) {
                return true;
            }
        }

        return false;

    }

    private void populateIncludedByDefaultResources(Object resource,
            BaseResponseContext response,
            ContainerType containerType,
            Map<ResourceDigest, Container> includedResources,
            int recurrenceLevel) {
        int recurrenceLevelCounter = recurrenceLevel;
        if (recurrenceLevel >= 42 || resource == null) {
            return;
        }

        Set<ResourceField> relationshipFields = getRelationshipFields(resource);

        //noinspection unchecked
        for (ResourceField resourceField : relationshipFields) {
            Object targetDataObj = PropertyUtils.getProperty(resource, resourceField.getUnderlyingName());
            if (targetDataObj == null) {
                continue;
            }
            if (resourceField.getIncludeByDefault()) {
                recurrenceLevelCounter++;
                if (targetDataObj instanceof Iterable) {
                    for (Object objectItem : (Iterable) targetDataObj) {
                        includedResources.put(getResourceDigest(objectItem), new Container(objectItem, response, containerType));
                        populateIncludedByDefaultResourcesRecursive(objectItem, response, containerType, includedResources, recurrenceLevelCounter);
                    }
                } else {
                    includedResources.put(getResourceDigest(targetDataObj), new Container(targetDataObj, response, containerType));
                    populateIncludedByDefaultResourcesRecursive(targetDataObj, response, containerType, includedResources, recurrenceLevelCounter);
                }
            } // if this is a top level container and its field matches the included parameters traverse further to find defaults
            else if (containerType.equals(ContainerType.TOP) && isFieldIncluded(response, resourceField.getUnderlyingName())) {
                if (targetDataObj instanceof Iterable) {
                    for (Object objectItem : (Iterable) targetDataObj) {
                        populateIncludedByDefaultResourcesRecursive(objectItem,
                                response,
                                containerType,
                                includedResources,
                                recurrenceLevelCounter);
                    }
                } else {
                    populateIncludedByDefaultResourcesRecursive(targetDataObj,
                            response,
                            containerType,
                            includedResources,
                            recurrenceLevelCounter);
                }
            }
        }
    }

    private void populateIncludedByDefaultResourcesRecursive(Object targetDataObj,
            BaseResponseContext response,
            ContainerType containerType,
            Map<ResourceDigest, Container> includedResourceContainers,
            int recurrenceLevelCounter) {
        if (containerType.equals(ContainerType.TOP)) {
            populateIncludedByDefaultResources(targetDataObj, response, ContainerType.INCLUDED_DEFAULT, includedResourceContainers, recurrenceLevelCounter);
        } else if (containerType.equals(ContainerType.INCLUDED_DEFAULT)) {
            populateIncludedByDefaultResources(targetDataObj, response, ContainerType.INCLUDED_DEFAULT_NESTED, includedResourceContainers, recurrenceLevelCounter);
        } else {
            populateIncludedByDefaultResources(targetDataObj, response, ContainerType.INCLUDED_DEFAULT_NESTED, includedResourceContainers, recurrenceLevelCounter);
        }
    }

    private Optional<String> getResourceTypeByObject(Object resource) {
        Optional<Class<?>> optional = resourceRegistry.getResourceClass(resource);
        if (optional.isPresent()) {
            String resourceType = resourceRegistry.getResourceType(optional.get());
            if (resourceType != null) {
                return Optional.of(resourceType);
            }
        }
        return Optional.empty();
    }

    private void populateIncludedRelationships(Object resource, BaseResponseContext response, Map<ResourceDigest, Container> includedResources, HashSet<Object> processedResources)
            throws IllegalAccessException, NoSuchMethodException, InvocationTargetException, NoSuchFieldException {
        if (response.getQueryParams() == null || response.getJsonPath() == null) {
            return;
        }
        TypedParams<IncludedRelationsParams> includedRelations = response.getQueryParams()
                .getIncludedRelations();

        populateIncludedResources(resource, includedRelations, response, includedResources, 1, processedResources);
    }

    private void populateIncludedResources(Object resource, TypedParams<IncludedRelationsParams> includedRelations, BaseResponseContext response, Map<ResourceDigest, Container> includedResources, Integer recurrenceLevel, HashSet<Object> processedResources) {
        int recurrenceLevelCounter = recurrenceLevel;
        if (recurrenceLevel >= 42 || resource == null) {
            return;
        }

        Optional<String> resourceType = getResourceTypeByObject(resource);

        if (!resourceType.isPresent()) {
            return;
        }

        //Include this resource
        if (recurrenceLevel > 1) {
            populateToResourcePropertyToIncludedResources(resource, response, ContainerType.INCLUDED_NESTED, resourceType.get(), includedResources);
        }

        if (processedResources.contains(resource)) {
            return;
        }

        processedResources.add(resource);

        recurrenceLevelCounter++;

        //Check if this resource type have included properties
        if (includedRelations.getParams().keySet().contains(resourceType.get())) {
            Set<Inclusion> thisTypeIncludedRelations = includedRelations.getParams().get(resourceType.get()).getParams();

            Set<ResourceField> relationshipFields = getRelationshipFields(resource);

            logger.trace("relationshipFields: {} ", relationshipFields);
            for (ResourceField resourceField : relationshipFields) {
                boolean included = false;
                for (Inclusion inclusion : thisTypeIncludedRelations) {
                    if (inclusion.getPath().equals(resourceField.getJsonName())) {
                        included = true;
                    }
                }

                if (!included) {
                    logger.trace("Field  " + resourceField.getJsonName() + " of resource type " + resourceType.get() + " not included");
                    continue;
                }

                logger.trace("relationshipFields: [underlyingName]= {} ", resourceField.getUnderlyingName());
                logger.trace("relationshipFields: [getJsonName]= {} ", resourceField.getJsonName());
                Object targetDataObj = PropertyUtils.getProperty(resource, resourceField.getUnderlyingName());
                if (targetDataObj == null) {
                    logger.trace("Returning due to null");
                    continue;
                }

                if (targetDataObj instanceof Iterable) {
                    for (Object objectItem : (Iterable) targetDataObj) {
                        //Recursion
                        populateIncludedResources(objectItem, includedRelations, response, includedResources, recurrenceLevelCounter, processedResources);
                    }
                } else {
                    //Recursion
                    populateIncludedResources(targetDataObj, includedRelations, response, includedResources, recurrenceLevelCounter, processedResources);
                }
            }
        } else {
            logger.trace("Resource type " + resourceType.get() + " does not specify included fields");
        }
    }
    
    private void populateToResourcePropertyToIncludedResources(Object resourceProperty, BaseResponseContext response, ContainerType containerType, String includedFieldName, Map<ResourceDigest, Container> includedResources) {
        if (resourceProperty != null) {
            if (Iterable.class
                    .isAssignableFrom(resourceProperty.getClass())) {
                for (Object resourceToInclude : (Iterable) resourceProperty) {
                    ResourceDigest digest = getResourceDigest(resourceToInclude);
                    includedResources.put(digest, new Container(resourceToInclude, response, containerType, includedFieldName));
                }
            } else {
                ResourceDigest digest = getResourceDigest(resourceProperty);
                includedResources.put(digest, new Container(resourceProperty, response, containerType, includedFieldName));
            }
        }
    }

    private Set<ResourceField> getRelationshipFields(Object resource) {
        Class<?> dataClass = resource.getClass();
        RegistryEntry entry = resourceRegistry.getEntry(dataClass);
        ResourceInformation resourceInformation = entry.getResourceInformation();
        return resourceInformation.getRelationshipFields();
    }

    private ResourceDigest getResourceDigest(Object resource) {
        Class<?> resourceClass = resourceRegistry.getResourceClass(resource).get();
        RegistryEntry registryEntry = resourceRegistry.getEntry(resourceClass);
        String idFieldName = registryEntry.getResourceInformation().getIdField().getUnderlyingName();
        Object idValue = PropertyUtils.getProperty(resource, idFieldName);
        String resourceType = resourceRegistry.getResourceType(resourceClass);
        return new ResourceDigest(idValue, resourceType);
    }
}
