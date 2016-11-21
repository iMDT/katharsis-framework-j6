package br.com.imdt.os.imdt.katharsis.jpa.helper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BasicBeanDescription;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import io.katharsis.utils.ClassUtils;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import javax.persistence.Entity;
import javax.persistence.Id;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReflectionHelper {

    private static final Logger log = LoggerFactory.getLogger(ReflectionHelper.class);

    private AtomicLong joinCounter = new AtomicLong(0L);

    public void describeObject(Class objectType, HashMap<String, String> json2JavaProp, HashMap<String, Class> json2JavaType, HashMap<String, Class> json2JavaGenericType, HashMap<String, String> json2Join, Set<String> filterList) {
        describeObject(objectType, json2JavaProp, json2JavaType, json2JavaGenericType, json2Join, filterList, "", 1);
    }

    /**
     * Percorre a definição da classe e preenche os dois arrays json2JavaProp -
     * Mapa contendo o nome da propriedade json e nome da propriedade em java
     * json2JavaProp - Mapa contendo o nome da propriedade json e o tipo java
     * objectType - Tipo
     */
    private void describeObject(Class objectType, HashMap<String, String> json2JavaProp, HashMap<String, Class> json2JavaType, HashMap<String, Class> json2JavaGenericType, HashMap<String, String> json2Join, Set<String> filterList, String stackPartialName, int level) {
        if (level >= 50) {
            log.info("Max recursion reached for object of type: " + objectType.getSimpleName());
            return;
        }

        Boolean anyFilterUseMe = false;
        for (String filter : filterList) {
            if (filter.startsWith(stackPartialName)) {
                anyFilterUseMe = true;
            }
        }

        if (!anyFilterUseMe) {
            log.trace("Stop condition found. No filter starting with: " + stackPartialName);
            return;
        }

        //We support filters for JSON property names
        ObjectMapper om = new ObjectMapper();
        BasicBeanDescription beanDescription = (BasicBeanDescription) om.getDeserializationConfig().getClassIntrospector().forDeserialization(om.getDeserializationConfig(), om.getTypeFactory().constructType(objectType), om.getDeserializationConfig());
        List<BeanPropertyDefinition> beanProperties = beanDescription.findProperties();
        for (BeanPropertyDefinition property : beanProperties) {
            String jsonPropertyName = property.getName().toLowerCase();
            if (property.getGetter() != null) {
                Class javaType = property.getGetter().getRawReturnType();

                String javaPropertyName = property.getInternalName();

                json2JavaProp.put(jsonPropertyName, javaPropertyName);
                json2JavaType.put(jsonPropertyName, javaType);

                String prefix = StringUtils.repeat("\t", level);
                if (log.isTraceEnabled()) {
                    log.trace(prefix + " " + javaPropertyName);
                }

                ParameterizedType iterableType = null;

                if (Iterable.class.isAssignableFrom(javaType)) {
                    iterableType = (ParameterizedType) property.getGetter().getGenericReturnType();
                }

                //Se é uma referencia "para muitos"
                if (iterableType != null) {
                    javaType = (Class) iterableType.getActualTypeArguments()[0];
                    json2JavaGenericType.put(jsonPropertyName, javaType);
                }

                if (ClassUtils.getAnnotation(javaType, Entity.class).isPresent()) {
                    HashMap<String, String> innerJson2JavaProp = new HashMap<String, String>();
                    HashMap<String, Class> innerJson2JavaType = new HashMap<String, Class>();
                    HashMap<String, Class> innerJson2JavaGenericType = new HashMap<String, Class>();
                    HashMap<String, String> innerJson2Join = new HashMap<String, String>();

                    //Adiciona os filhos neste objeto
                    String joinName = "";
                    if (iterableType != null) {
                        joinName = "join_" + jsonPropertyName + "_" + (joinCounter.incrementAndGet());
                    }

                    log.trace("Buscando filhos do objeto {}", javaType.getSimpleName());
                    //Pega os filhos
                    describeObject(javaType, innerJson2JavaProp, innerJson2JavaType, innerJson2JavaGenericType, innerJson2Join, filterList, stackPartialName + ((stackPartialName.length() > 0) ? "." : "") + jsonPropertyName, level + 1);

                    for (String innerJsonPropertyName : innerJson2JavaProp.keySet()) {
                        String jsonPropertyCompleteName = jsonPropertyName + "." + innerJsonPropertyName;
                        String innerJsonJavaPropertyName = innerJson2JavaProp.get(innerJsonPropertyName);

                        log.trace("Filho encontrado. java {}, json {}", innerJsonJavaPropertyName, jsonPropertyCompleteName);

                        //Das propriedades dos filhos, se eu sou uma propriedade joinada, troco de onde leio
                        if (iterableType != null) {
                            log.trace("Sou uma propriedade joinada. java {}, json {}", javaPropertyName + "." + innerJsonJavaPropertyName, jsonPropertyCompleteName);
                            if (innerJsonJavaPropertyName.startsWith("join_")) {
                                json2JavaProp.put(jsonPropertyCompleteName, innerJsonJavaPropertyName);
                                String innerNameOfJoin = innerJson2Join.get(innerJsonPropertyName).replaceAll(Pattern.quote("_obj_"), joinName);

                                if (log.isTraceEnabled()) {
                                    log.trace("Recebi um join do meu filho: " + innerNameOfJoin);
                                }
                                //System.out.println("Colocando join: " + innerNameOfJoin);
                                json2Join.put(jsonPropertyCompleteName, innerNameOfJoin);
                            } else {
                                json2JavaProp.put(jsonPropertyCompleteName, joinName + "." + innerJsonJavaPropertyName);
                            }
                            String existingJoin = json2Join.get(jsonPropertyCompleteName);
                            if (existingJoin == null) {
                                existingJoin = "";
                            } else {
                                existingJoin = " " + existingJoin;
                            }

                            log.trace("Inserindo join para propriedade {}, sql: {}", jsonPropertyCompleteName, " LEFT JOIN _obj_." + javaPropertyName + "   " + joinName + existingJoin);
                            json2Join.put(jsonPropertyCompleteName, "LEFT JOIN _obj_." + javaPropertyName + "  " + joinName + existingJoin);
                            log.trace("json2Join elements: " + json2Join.keySet().size());
                        } else {
                            log.trace("Não sou uma propriedade joinada. java {}, json {}", javaPropertyName + "." + innerJsonJavaPropertyName, jsonPropertyCompleteName);
                            //Se a coluna HQL a ser lida é um join, não coloca prefixo nela, pois o prefixo já é colocado na definição do join
                            if (innerJsonJavaPropertyName.startsWith("join_")) {
                                json2JavaProp.put(jsonPropertyCompleteName, innerJsonJavaPropertyName);
                            } else {
                                json2JavaProp.put(jsonPropertyCompleteName, javaPropertyName + "." + innerJsonJavaPropertyName);
                            }

                            if (innerJson2Join.containsKey(innerJsonPropertyName)) {
                                log.trace("Sou uma propriedade que possui joins: " + innerJsonPropertyName);

                                String existingJoin = json2Join.get(innerJsonPropertyName);
                                if (existingJoin == null) {
                                    existingJoin = "";
                                } else {
                                    existingJoin = "," + existingJoin;
                                }
                                String joinOfMyChild = innerJson2Join.get(innerJsonPropertyName);
                                joinOfMyChild = joinOfMyChild.replaceAll(Pattern.quote("_obj_"), "_obj_." + jsonPropertyName);
                                json2Join.put(jsonPropertyCompleteName, joinOfMyChild + existingJoin);
                            }
                        }

                        json2JavaType.put(jsonPropertyCompleteName, innerJson2JavaType.get(innerJsonPropertyName));
                        json2JavaGenericType.put(jsonPropertyCompleteName, innerJson2JavaGenericType.get(innerJsonPropertyName));
                    }
                }
            }
        }

        //We also support filters for JAVA property names that start with comp_ (this prefix is needed for security reasons, to avoid that a client can request any getter in the model)
        for (Field field : objectType.getDeclaredFields()) {
            if (field.getName().startsWith("comp_")) {
                json2JavaProp.put(field.getName().toLowerCase(), field.getName());
                json2JavaType.put(field.getName().toLowerCase(), field.getType());
            }
        }
    }

    public static String getIdProperty(Class<?> clazz) throws RuntimeException {
        if (clazz == null) {
            return null;
        }

        for (Field f : clazz.getDeclaredFields()) {
            Annotation[] as = f.getAnnotations();
            for (Annotation a : as) {
                if (a.annotationType() == Id.class) {
                    return f.getName();
                }
            }
        }

        if (clazz.getSuperclass() != Object.class) {
            return getIdProperty(clazz.getSuperclass());
        }

        log.error("Could not detect Id field for class " + clazz.getName());
        return null;
    }

}
