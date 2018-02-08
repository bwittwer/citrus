/*
 * Copyright 2006-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.consol.citrus.generate;

import com.consol.citrus.exceptions.CitrusRuntimeException;
import com.consol.citrus.http.message.HttpMessage;
import com.consol.citrus.model.testcase.http.ObjectFactory;
import io.swagger.models.*;
import io.swagger.models.parameters.*;
import io.swagger.models.properties.*;
import io.swagger.parser.SwaggerParser;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpStatus;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Test generator creates one to many test cases based on operations defined in a XML schema XSD.
 * @author Christoph Deppisch
 * @since 2.7.4
 */
public class SwaggerXmlTestGenerator extends MessagingXmlTestGenerator {

    private String swaggerResource;

    private String contextPath;
    private String operation;

    private String namePrefix;
    private String nameSuffix = "_IT";

    @Override
    public void create() {
        Swagger swagger;
        try {
            swagger = new SwaggerParser().read(new PathMatchingResourcePatternResolver().getResource(swaggerResource).getURI().toURL().toString());
        } catch (IOException e) {
            throw new CitrusRuntimeException("Failed to parse Swagger Open API specification: " + swaggerResource, e);
        }

        if (!StringUtils.hasText(namePrefix)) {
            withNamePrefix(StringUtils.trimAllWhitespace(Optional.ofNullable(swagger.getInfo().getTitle()).orElse("Swagger")) + "_");
        }

        for (Map.Entry<String, Path> path : swagger.getPaths().entrySet()) {
            for (Map.Entry<HttpMethod, Operation> operation : path.getValue().getOperationMap().entrySet()) {

                // Now generate it
                withName(namePrefix + operation.getValue().getOperationId() + nameSuffix);

                HttpMessage requestMessage = new HttpMessage();
                requestMessage.path(Optional.ofNullable(contextPath).orElse("") + (swagger.getBasePath() != null ? swagger.getBasePath() : "") + path.getKey());
                requestMessage.method(org.springframework.http.HttpMethod.valueOf(operation.getKey().name()));

                if (operation.getValue().getParameters() != null) {
                    operation.getValue().getParameters().stream()
                            .filter(p -> p instanceof HeaderParameter)
                            .filter(Parameter::getRequired)
                            .forEach(p -> requestMessage.setHeader(p.getName(), getActor().equals("client") ? createRandomValueExpression(((HeaderParameter) p).getItems(), swagger.getDefinitions(), false) : createValidationExpression(((HeaderParameter) p).getItems(), swagger.getDefinitions(), false)));

                    operation.getValue().getParameters().stream()
                            .filter(param -> param instanceof QueryParameter)
                            .filter(Parameter::getRequired)
                            .forEach(param -> requestMessage.queryParam(param.getName(), getActor().equals("client") ? createRandomValueExpression((QueryParameter) param) : createValidationExpression((QueryParameter) param)));

                    operation.getValue().getParameters().stream()
                            .filter(p -> p instanceof BodyParameter)
                            .filter(Parameter::getRequired)
                            .findFirst()
                            .ifPresent(p -> requestMessage.setPayload(getActor().equals("client") ? createOutboundPayload(((BodyParameter) p).getSchema(), swagger.getDefinitions()) : createInboundPayload(((BodyParameter) p).getSchema(), swagger.getDefinitions())));
                }
                withRequest(requestMessage);

                HttpMessage responseMessage = new HttpMessage();
                if (operation.getValue().getResponses() != null) {
                    Response response = operation.getValue().getResponses().get("200");
                    if (response == null) {
                        response = operation.getValue().getResponses().get("default");
                    }

                    if (response != null) {
                        responseMessage.status(HttpStatus.OK);

                        if (response.getHeaders() != null) {
                            for (Map.Entry<String, Property> header : response.getHeaders().entrySet()) {
                                responseMessage.setHeader(header.getKey(), getActor().equals("client") ? createValidationExpression(header.getValue(), swagger.getDefinitions(), false) : createRandomValueExpression(header.getValue(), swagger.getDefinitions(), false));
                            }
                        }

                        if (response.getSchema() != null) {
                            responseMessage.setPayload(getActor().equals("client") ? createInboundPayload(response.getSchema(), swagger.getDefinitions()): createOutboundPayload(response.getSchema(), swagger.getDefinitions()));
                        }
                    }
                }
                withResponse(responseMessage);

                super.create();

                log.info("Successfully created new test case " + getTargetPackage() + "." + getName());
            }
        }
    }

    @Override
    protected List<String> getMarshallerContextPaths() {
        List<String> contextPaths = super.getMarshallerContextPaths();
        contextPaths.add(ObjectFactory.class.getPackage().getName());
        return contextPaths;
    }

    /**
     * Creates payload from schema for outbound message.
     * @param model
     * @param definitions
     * @return
     */
    private String createOutboundPayload(Model model, Map<String, Model> definitions) {
        StringBuilder payload = new StringBuilder();

        if (model instanceof RefModel) {
            model = definitions.get(((RefModel) model).getSimpleRef());
        }

        if (model instanceof ArrayModel) {
            payload.append(createOutboundPayload(((ArrayModel) model).getItems(), definitions));
        } else {
            payload.append("{");

            if (model.getProperties() != null) {
                for (Map.Entry<String, Property> entry : model.getProperties().entrySet()) {
                    payload.append("\"").append(entry.getKey()).append("\": ").append(createOutboundPayload(entry.getValue(), definitions)).append(",");
                }
            }

            if (payload.toString().endsWith(",")) {
                payload.replace(payload.length() - 1, payload.length(), "");
            }

            payload.append("}");
        }

        return payload.toString();
    }

    /**
     * Creates payload from property for outbound message.
     * @param property
     * @param definitions
     * @return
     */
    private String createOutboundPayload(Property property, Map<String, Model> definitions) {
        StringBuilder payload = new StringBuilder();

        if (property instanceof RefProperty) {
            Model model = definitions.get(((RefProperty) property).getSimpleRef());
            payload.append("{");

            if (model.getProperties() != null) {
                for (Map.Entry<String, Property> entry : model.getProperties().entrySet()) {
                    payload.append("\"").append(entry.getKey()).append("\": ").append(createRandomValueExpression(entry.getValue(), definitions, true)).append(",");
                }
            }

            if (payload.toString().endsWith(",")) {
                payload.replace(payload.length() - 1, payload.length(), "");
            }

            payload.append("}");
        } else if (property instanceof ArrayProperty) {
            payload.append("[");
            payload.append(createRandomValueExpression(((ArrayProperty) property).getItems(), definitions, true));
            payload.append("]");
        } else {
            payload.append(createRandomValueExpression(property, definitions, true));
        }

        return payload.toString();
    }

    /**
     * Create payload from schema with random values.
     * @param property
     * @param definitions
     * @param quotes
     * @return
     */
    private String createRandomValueExpression(Property property, Map<String, Model> definitions, boolean quotes) {
        StringBuilder payload = new StringBuilder();

        if (property instanceof RefProperty) {
            payload.append(createOutboundPayload(property, definitions));
        } else if (property instanceof ArrayProperty) {
            payload.append(createOutboundPayload(property, definitions));
        } else if (property instanceof StringProperty || property instanceof DateProperty || property instanceof DateTimeProperty) {
            if (quotes) {
                payload.append("\"");
            }

            if (property instanceof DateProperty) {
                payload.append("citrus:currentDate()");
            } else if (property instanceof DateTimeProperty) {
                payload.append("citrus:currentDate('yyyy-MM-dd'T'hh:mm:ss')");
            } else if (!CollectionUtils.isEmpty(((StringProperty) property).getEnum())) {
                payload.append("citrus:randomEnumValue(").append(((StringProperty) property).getEnum().stream().map(value -> "'" + value + "'").collect(Collectors.joining(","))).append(")");
            } else {
                payload.append("citrus:randomString(").append(((StringProperty) property).getMaxLength() != null && ((StringProperty) property).getMaxLength() > 0 ? ((StringProperty) property).getMaxLength() : (((StringProperty) property).getMinLength() != null && ((StringProperty) property).getMinLength() > 0 ? ((StringProperty) property).getMinLength() : 10)).append(")");
            }

            if (quotes) {
                payload.append("\"");
            }
        } else if (property instanceof IntegerProperty || property instanceof LongProperty) {
            payload.append("citrus:randomNumber(10)");
        } else if (property instanceof FloatProperty || property instanceof DoubleProperty) {
            payload.append("citrus:randomNumber(10)");
        } else if (property instanceof BooleanProperty) {
            payload.append("citrus:randomEnumValue('true', 'false')");
        } else {
            if (quotes) {
                payload.append("\"\"");
            } else {
                payload.append("");
            }
        }

        return payload.toString();
    }

    /**
     * Creates control payload from property for validation.
     * @param property
     * @param definitions
     * @return
     */
    private String createInboundPayload(Property property, Map<String, Model> definitions) {
        StringBuilder payload = new StringBuilder();

        if (property instanceof RefProperty) {
            Model model = definitions.get(((RefProperty) property).getSimpleRef());
            payload.append("{");

            if (model.getProperties() != null) {
                for (Map.Entry<String, Property> entry : model.getProperties().entrySet()) {
                    payload.append("\"").append(entry.getKey()).append("\": ").append(createValidationExpression(entry.getValue(), definitions, true)).append(",");
                }
            }

            if (payload.toString().endsWith(",")) {
                payload.replace(payload.length() - 1, payload.length(), "");
            }

            payload.append("}");
        } else if (property instanceof ArrayProperty) {
            payload.append("[");
            payload.append(createValidationExpression(((ArrayProperty) property).getItems(), definitions, true));
            payload.append("]");
        } else {
            payload.append(createValidationExpression(property, definitions, false));
        }

        return payload.toString();
    }

    /**
     * Creates control payload from schema for validation.
     * @param model
     * @param definitions
     * @return
     */
    private String createInboundPayload(Model model, Map<String, Model> definitions) {
        StringBuilder payload = new StringBuilder();

        if (model instanceof RefModel) {
            model = definitions.get(((RefModel) model).getSimpleRef());
        }

        if (model instanceof ArrayModel) {
            payload.append("[");
            payload.append(createValidationExpression(((ArrayModel) model).getItems(), definitions, true));
            payload.append("]");
        } else {
            payload.append("{");

            if (model.getProperties() != null) {
                for (Map.Entry<String, Property> entry : model.getProperties().entrySet()) {
                    payload.append("\"").append(entry.getKey()).append("\": ").append(createValidationExpression(entry.getValue(), definitions, true)).append(",");
                }
            }

            if (payload.toString().endsWith(",")) {
                payload.replace(payload.length() - 1, payload.length(), "");
            }

            payload.append("}");
        }

        return payload.toString();
    }

    /**
     * Create validation expression using functions according to parameter type and format.
     * @param property
     * @param definitions
     * @param quotes
     * @return
     */
    private String createValidationExpression(Property property, Map<String, Model> definitions, boolean quotes) {
        StringBuilder payload = new StringBuilder();
        if (property instanceof RefProperty) {
            Model model = definitions.get(((RefProperty) property).getSimpleRef());
            payload.append("{");

            if (model.getProperties() != null) {
                for (Map.Entry<String, Property> entry : model.getProperties().entrySet()) {
                    payload.append("\"").append(entry.getKey()).append("\": ").append(createValidationExpression(entry.getValue(), definitions, quotes)).append(",");
                }
            }

            if (payload.toString().endsWith(",")) {
                payload.replace(payload.length() - 1, payload.length(), "");
            }

            payload.append("}");
        } else if (property instanceof ArrayProperty) {
            if (quotes) {
                payload.append("\"");
            }

            payload.append("@ignore@");

            if (quotes) {
                payload.append("\"");
            }
        } else if (property instanceof StringProperty) {
            if (quotes) {
                payload.append("\"");
            }

            if (StringUtils.hasText(((StringProperty) property).getPattern())) {
                payload.append("@matches(").append(((StringProperty) property).getPattern()).append(")@");
            } else if (!CollectionUtils.isEmpty(((StringProperty) property).getEnum())) {
                payload.append("@matches(").append(((StringProperty) property).getEnum().stream().collect(Collectors.joining("|"))).append(")@");
            } else {
                payload.append("@notEmpty()@");
            }

            if (quotes) {
                payload.append("\"");
            }
        } else if (property instanceof DateProperty) {
            if (quotes) {
                payload.append("\"");
            }

            payload.append("@matchesDatePattern('yyyy-MM-dd')@");

            if (quotes) {
                payload.append("\"");
            }
        } else if (property instanceof DateTimeProperty) {
            if (quotes) {
                payload.append("\"");
            }

            payload.append("@matchesDatePattern('yyyy-MM-dd'T'hh:mm:ss')@");

            if (quotes) {
                payload.append("\"");
            }
        } else if (property instanceof IntegerProperty || property instanceof LongProperty) {
            if (quotes) {
                payload.append("\"");
            }

            payload.append("@isNumber()@");

            if (quotes) {
                payload.append("\"");
            }
        } else if (property instanceof FloatProperty || property instanceof DoubleProperty) {
            if (quotes) {
                payload.append("\"");
            }

            payload.append("@isNumber()@");

            if (quotes) {
                payload.append("\"");
            }
        } else if (property instanceof BooleanProperty) {
            if (quotes) {
                payload.append("\"");
            }

            payload.append("@matches(true|false)@");

            if (quotes) {
                payload.append("\"");
            }
        } else {
            if (quotes) {
                payload.append("\"");
            }

            payload.append("@ignore@");

            if (quotes) {
                payload.append("\"");
            }
        }

        return payload.toString();
    }

    /**
     * Create validation expression using functions according to parameter type and format.
     * @param parameter
     * @return
     */
    private String createValidationExpression(AbstractSerializableParameter parameter) {
        switch (parameter.getType()) {
            case "integer":
                return "@isNumber()@";
            case "string":
                if (parameter.getFormat() != null && parameter.getFormat().equals("date")) {
                    return "\"@matchesDatePattern('yyyy-MM-dd')@\"";
                } else if (parameter.getFormat() != null && parameter.getFormat().equals("date-time")) {
                    return "\"@matchesDatePattern('yyyy-MM-dd'T'hh:mm:ss')@\"";
                } else if (StringUtils.hasText(parameter.getPattern())) {
                    return "\"@matches(" + parameter.getPattern() + ")@\"";
                } else if (!CollectionUtils.isEmpty(parameter.getEnum())) {
                    return "\"@matches(" + (parameter.getEnum().stream().collect(Collectors.joining("|"))) + ")@\"";
                } else {
                    return "@notEmpty()@";
                }
            case "boolean":
                return "@matches(true|false)@";
            default:
                return "@ignore@";
        }
    }

    /**
     * Create random value expression using functions according to parameter type and format.
     * @param parameter
     * @return
     */
    private String createRandomValueExpression(AbstractSerializableParameter parameter) {
        switch (parameter.getType()) {
            case "integer":
                return "citrus:randomNumber(10)";
            case "string":
                if (parameter.getFormat() != null && parameter.getFormat().equals("date")) {
                    return "\"citrus:currentDate('yyyy-MM-dd')\"";
                } else if (parameter.getFormat() != null && parameter.getFormat().equals("date-time")) {
                    return "\"citrus:currentDate('yyyy-MM-dd'T'hh:mm:ss')\"";
                } else if (StringUtils.hasText(parameter.getPattern())) {
                    return "\"citrus:randomValue(" + parameter.getPattern() + ")\"";
                } else if (!CollectionUtils.isEmpty(parameter.getEnum())) {
                    return "\"citrus:randomEnumValue(" + (parameter.getEnum().stream().collect(Collectors.joining("|"))) + ")\"";
                } else {
                    return "citrus:randomString(10)";
                }
            case "boolean":
                return "true";
            default:
                return "";
        }
    }

    /**
     * Set the swagger Open API resource to use.
     * @param swaggerResource
     * @return
     */
    public SwaggerXmlTestGenerator withSpec(String swaggerResource) {
        this.swaggerResource = swaggerResource;
        return this;
    }

    /**
     * Set the server context path to use.
     * @param contextPath
     * @return
     */
    public SwaggerXmlTestGenerator withContextPath(String contextPath) {
        this.nameSuffix = contextPath;
        return this;
    }

    /**
     * Set the test name prefix to use.
     * @param prefix
     * @return
     */
    public SwaggerXmlTestGenerator withNamePrefix(String prefix) {
        this.namePrefix = prefix;
        return this;
    }

    /**
     * Set the test name suffix to use.
     * @param suffix
     * @return
     */
    public SwaggerXmlTestGenerator withNameSuffix(String suffix) {
        this.nameSuffix = suffix;
        return this;
    }

    /**
     * Set the swagger operation to use.
     * @param operation
     * @return
     */
    public SwaggerXmlTestGenerator withOperation(String operation) {
        this.operation = operation;
        return this;
    }

    /**
     * Gets the swaggerResource.
     *
     * @return
     */
    public String getSwaggerResource() {
        return swaggerResource;
    }

    /**
     * Sets the swaggerResource.
     *
     * @param swaggerResource
     */
    public void setSwaggerResource(String swaggerResource) {
        this.swaggerResource = swaggerResource;
    }

    /**
     * Gets the contextPath.
     *
     * @return
     */
    public String getContextPath() {
        return contextPath;
    }

    /**
     * Sets the contextPath.
     *
     * @param contextPath
     */
    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    /**
     * Sets the nameSuffix.
     *
     * @param nameSuffix
     */
    public void setNameSuffix(String nameSuffix) {
        this.nameSuffix = nameSuffix;
    }

    /**
     * Gets the nameSuffix.
     *
     * @return
     */
    public String getNameSuffix() {
        return nameSuffix;
    }

    /**
     * Sets the namePrefix.
     *
     * @param namePrefix
     */
    public void setNamePrefix(String namePrefix) {
        this.namePrefix = namePrefix;
    }

    /**
     * Gets the namePrefix.
     *
     * @return
     */
    public String getNamePrefix() {
        return namePrefix;
    }

    /**
     * Sets the operation.
     *
     * @param operation
     */
    public void setOperation(String operation) {
        this.operation = operation;
    }

    /**
     * Gets the operation.
     *
     * @return
     */
    public String getOperation() {
        return operation;
    }
}
