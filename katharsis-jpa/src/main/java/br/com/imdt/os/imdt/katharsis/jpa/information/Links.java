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
package br.com.imdt.os.imdt.katharsis.jpa.information;

import br.com.imdt.os.imdt.katharsis.jpa.KatharsisJpaContextHolder;
import br.com.imdt.os.imdt.katharsis.jpa.repository.BaseRepository;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.katharsis.queryParams.QueryParams;
import io.katharsis.queryParams.RestrictedPaginationKeys;
import io.katharsis.response.LinksInformation;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Links implements LinksInformation {

    private static final Logger log = LoggerFactory.getLogger(Links.class);

    KatharsisJpaContextHolder context;

    /**
     * Link for the first page
     */
    private String first = "";

    /**
     * Link for the last page
     */
    private String last = "";

    /**
     * Link for the previous page
     */
    private String prev = "";

    /**
     * Link for the next page
     */
    private String next = "";

    public String getFirst() {
        return first;
    }

    public void setFirst(String first) {
        this.first = first;
    }

    public String getLast() {
        return last;
    }

    public void setLast(String last) {
        this.last = last;
    }

    public String getPrev() {
        return prev;
    }

    public void setPrev(String prev) {
        this.prev = prev;
    }

    public String getNext() {
        return next;
    }

    public void setNext(String next) {
        this.next = next;
    }

    /**
     * Create links for repository navigation
     */
    public Links(String repositoryName, KatharsisJpaContextHolder context, QueryParams queryParams, Long totalRecords) {
        this.context = context;
        
        String qs = getQSForPagination();

        String basePath = context.getBaseUrl() + "/" + repositoryName + "/?" + qs;

        Integer intOffset = queryParams.getPagination().get(RestrictedPaginationKeys.offset);
        if (intOffset == null) {
            intOffset = 0;
        }
        Float currentOffset = new Float(intOffset);

        Float fPageSize = new Float(queryParams.getPagination().get(RestrictedPaginationKeys.limit));
        Integer pageSize = fPageSize.intValue();

        Integer lastPage = ((int) Math.ceil(totalRecords / fPageSize));
        if (lastPage < 1) {
            lastPage = 1;
        }

        Integer currentPage = new Double(Math.ceil(currentOffset / (fPageSize) + 1)).intValue();
        if (currentPage < 1) {
            currentPage = 1;
        }

        Integer nextPage = currentPage + 1;
        if (nextPage > lastPage) {
            nextPage = lastPage;
        }

        Integer prevPage = currentPage - 1;
        if (prevPage < 1) {
            prevPage = 1;
        }

        if (!currentPage.equals(1)) {
            first = basePath + "&page[limit]=" + pageSize;
            first = first + "&page[offset]=0";
        } else {
            first = null;
        }

        if (nextPage <= lastPage && !nextPage.equals(currentPage)) {
            next = basePath + "&page[limit]=" + pageSize;
            next = next + "&page[offset]=" + (nextPage - 1) * pageSize;
        } else {
            next = null;
        }

        if (!lastPage.equals(currentPage)) {
            last = basePath + "&page[limit]=" + pageSize;
            last = last + "&page[offset]=" + (lastPage - 1) * pageSize;
        } else {
            last = null;
        }

        if (prevPage >= 1 && !prevPage.equals(currentPage)) {
            prev = basePath + "&page[limit]=" + pageSize;
            prev = prev + "&page[offset]=" + (prevPage - 1) * pageSize;
        } else {
            prev = null;
        }
    }

    /**
     * Reproduces the query string striping out the pagination parameters.
     */
    private String getQSForPagination() {
        try {
            HttpServletRequest request = context.getHttpServletRequest();
            List<NameValuePair> newUrlParams = new ArrayList<NameValuePair>();
            Map<String, String[]> paramMap = request.getParameterMap();
            for (String key : paramMap.keySet()) {
                if (key.toLowerCase().startsWith("page[")) {
                    continue;
                }
                if (key.toLowerCase().startsWith("1")) {
                    continue;
                }

                String[] values = paramMap.get(key);
                if (values.length == 1) {
                    newUrlParams.add(new BasicNameValuePair(key, values[0]));
                } else {
                    for (String value : values) {
                        if (!key.contains("[]")) {
                            newUrlParams.add(new BasicNameValuePair(key + "[]", value));
                        } else {
                            newUrlParams.add(new BasicNameValuePair(key, value));
                        }
                    }
                }
            }
            URIBuilder builder = new URIBuilder();
            builder.setParameters(newUrlParams);
            builder.setPath("/");
            String url = builder.build().toString();
            String urlParts[] = url.split(Pattern.quote("?"));
            if (urlParts.length <= 1) {
                return "";
            } else {
                return urlParts[1].replaceAll(Pattern.quote("%5B"), "[").replaceAll(Pattern.quote("%5D"), "]");
            }
        } catch (URISyntaxException ex) {
            return "";
        }
    }
}
