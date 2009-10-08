/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.cxf.jaxrs.provider;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ws.rs.ConsumeMime;
import javax.ws.rs.Encoded;
import javax.ws.rs.ProduceMime;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import org.apache.cxf.jaxrs.ext.MessageContext;
import org.apache.cxf.jaxrs.ext.multipart.MultipartBody;
import org.apache.cxf.jaxrs.impl.MetadataMap;
import org.apache.cxf.jaxrs.utils.AnnotationUtils;
import org.apache.cxf.jaxrs.utils.FormUtils;
import org.apache.cxf.jaxrs.utils.HttpUtils;
import org.apache.cxf.jaxrs.utils.multipart.AttachmentUtils;

@ProduceMime("application/x-www-form-urlencoded")
@ConsumeMime({"application/x-www-form-urlencoded", "multipart/form-data" })
@Provider
public class FormEncodingProvider implements 
    MessageBodyReader<Object>, MessageBodyWriter<MultivaluedMap<String, String>> {
    
    private static final MediaType MULTIPART_FORM_DATA_TYPE = MediaType.valueOf("multipart/form-data");
    
    private FormValidator validator;
    @Context private MessageContext mc;
    private String attachmentDir;
    private String attachmentThreshold;

    public void setAttachmentDirectory(String dir) {
        attachmentDir = dir;
    }
    
    public void setAttachmentThreshold(String threshold) {
        attachmentThreshold = threshold;
    }
    
    public void setValidator(FormValidator formValidator) {
        validator = formValidator;
    }
    
    public boolean isReadable(Class<?> type, Type genericType, 
                              Annotation[] annotations) {
        return MultivaluedMap.class.isAssignableFrom(type)
               || multipartSupported() && MultipartBody.class.isAssignableFrom(type);
    }

    public Object readFrom(
        Class<Object> clazz, Type genericType, Annotation[] annotations, MediaType type, 
        MultivaluedMap<String, String> headers, InputStream is) 
        throws IOException {
        try {
           
            if (MultipartBody.class.isAssignableFrom(clazz)) {
                return AttachmentUtils.getMultipartBody(mc);
            }
            
            MultivaluedMap<String, String> params = createMap(clazz);
            populateMap(params, is, type,
                        AnnotationUtils.getAnnotation(annotations, Encoded.class) == null);
            validateMap(params);
            return params;
        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }

    @SuppressWarnings("unchecked")
    protected MultivaluedMap<String, String> createMap(Class<?> clazz) throws Exception {
        if (clazz == MultivaluedMap.class) {
            return new MetadataMap<String, String>();
        }
        return (MultivaluedMap<String, String>)clazz.newInstance();
    }
    
    /**
     * Retrieve map of parameters from the passed in message
     *
     * @param message
     * @return a Map of parameters.
     */
    protected void populateMap(MultivaluedMap<String, String> params, 
                               InputStream is, MediaType mt, boolean decode) {
        if (mt.isCompatible(MULTIPART_FORM_DATA_TYPE)) {
            MultipartBody body = 
                AttachmentUtils.getMultipartBody(mc, attachmentDir, attachmentThreshold);
            FormUtils.populateMapFromMultipart(params, body, decode);
        } else {
            FormUtils.populateMapFromString(params, FormUtils.readBody(is), decode);
        }
    }
    
    protected void validateMap(MultivaluedMap<String, String> params) {
        if (validator != null) {
            validator.validate(params);
        }
    }

    public long getSize(MultivaluedMap<String, String> t) {
        return -1;
    }

    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations) {
        return MultivaluedMap.class.isAssignableFrom(type);
    }

    public void writeTo(MultivaluedMap<String, String> map, Class<?> c, Type t, Annotation[] anns, 
                        MediaType mt, MultivaluedMap<String, Object> headers, OutputStream os) 
        throws IOException, WebApplicationException {
        boolean encoded = AnnotationUtils.getAnnotation(anns, Encoded.class) != null;
        for (Iterator<Map.Entry<String, List<String>>> it = map.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, List<String>> entry = it.next();
            for (String value : entry.getValue()) {
                os.write(entry.getKey().getBytes("UTF-8"));
                os.write('=');
                String data = encoded ? value : HttpUtils.urlEncode(value); 
                os.write(data.getBytes("UTF-8"));
                if (it.hasNext()) {
                    os.write('&');
                }
            }
        }
    }
    
    private boolean multipartSupported() {

        if (mc == null) {
            return false;
        }
        MediaType mt = mc.getHttpHeaders().getMediaType();        

        return mt != null && mt.isCompatible(MULTIPART_FORM_DATA_TYPE);
    }
}
