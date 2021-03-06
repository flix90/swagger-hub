/*
 * swagger-hub
 *
 * Copyright (c) 2019 Synopsys, Inc.
 *
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
package com.synopsys.integration.swagger

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.synopsys.integration.swagger.creator.ComponentCreator
import com.synopsys.integration.swagger.model.*
import com.synopsys.integration.swagger.parser.SwaggerDefinitionsParser
import com.synopsys.integration.swagger.parser.SwaggerEnumsParser
import com.synopsys.integration.swagger.parser.SwaggerPathsParser
import com.synopsys.integration.swagger.parser.SwaggerPropertiesParser
import freemarker.template.Configuration
import freemarker.template.Template
import org.apache.commons.io.IOUtils

import java.nio.charset.StandardCharsets

public class ModelCreator {
    public static final String BASE_DIRECTORY_ENVIRONMENT_VARIABLE = "SWAGGER_BLACK_DUCK_BASE_DIRECTORY";
    public static final String DEFAULT_BASE_DIRECTORY = "/tmp";

    public static final String DIRECTORY_PREFIX = "/com/synopsys/integration/blackduck/api/generated";
    public static final String DISCOVERY_DIRECTORY = DIRECTORY_PREFIX + "/discovery";
    public static final String ENUM_DIRECTORY = DIRECTORY_PREFIX + "/enumeration";
    public static final String VIEW_DIRECTORY = DIRECTORY_PREFIX + "/view";
    public static final String RESPONSE_DIRECTORY = DIRECTORY_PREFIX + "/response";
    public static final String COMPONENT_DIRECTORY = DIRECTORY_PREFIX + "/component";

    public static final String API_CORE_PACKAGE_PREFIX = "com.synopsys.integration.blackduck.api.core";
    public static final String GENERATED_PACKAGE_PREFIX = "com.synopsys.integration.blackduck.api.generated";
    public static final String MANUAL_PACKAGE_PREFIX = "com.synopsys.integration.blackduck.api.manual";
    public static final String DISCOVERY_PACKAGE = GENERATED_PACKAGE_PREFIX + ".discovery";
    public static final String ENUM_PACKAGE = GENERATED_PACKAGE_PREFIX + ".enumeration";
    public static final String VIEW_PACKAGE_SUFFIX = ".view";
    public static final String RESPONSE_PACKAGE_SUFFIX = ".response";
    public static final String COMPONENT_PACKAGE_SUFFIX = ".component";
    public static final String GENERATED_VIEW_PACKAGE = GENERATED_PACKAGE_PREFIX + VIEW_PACKAGE_SUFFIX;
    public static final String GENERATED_RESPONSE_PACKAGE = GENERATED_PACKAGE_PREFIX + RESPONSE_PACKAGE_SUFFIX;
    public static final String GENERATED_COMPONENT_PACKAGE = GENERATED_PACKAGE_PREFIX + COMPONENT_PACKAGE_SUFFIX;
    public static final String MANUAL_VIEW_PACKAGE = MANUAL_PACKAGE_PREFIX + VIEW_PACKAGE_SUFFIX;
    public static final String MANUAL_RESPONSE_PACKAGE = MANUAL_PACKAGE_PREFIX + RESPONSE_PACKAGE_SUFFIX;
    public static final String MANUAL_COMPONENT_PACKAGE = MANUAL_PACKAGE_PREFIX + COMPONENT_PACKAGE_SUFFIX;

    public static void main(final String[] args) throws Exception {
        final File jsonFile = new File(ModelCreator.class.getClassLoader().getResource("api-docs_2019.2.0_manually_edited.json").toURI());
        final FileInputStream jsonFileInputStream = new FileInputStream(jsonFile);
        final InputStreamReader jsonInputStreamReader = new InputStreamReader(jsonFileInputStream, StandardCharsets.UTF_8);

        final JsonParser jsonParser = new JsonParser();
        final JsonObject swaggerJson = jsonParser.parse(jsonInputStreamReader).getAsJsonObject();

        Set<String> definitionNamesToExtendBlackDuckView = new HashSet<>(getLines('definitions_that_are_black_duck_views.txt'));
        Set<String> definitionNamesToExtendBlackDuckResponse = new HashSet<>(getLines('definitions_that_are_black_duck_responses.txt'));
        Set<String> apiPathsToIgnore = new HashSet<>(getLines('api_path_ignore.txt'));
        Set<String> definitionNamesThatImplementBuildable = new HashSet<>(getLines('definitions_that_are_buildable.txt'));
        Set<String> definitionNamesMaintainedManually = new HashSet<>(getLines('definitions_that_are_maintained_manually.txt'));

        List<DefinitionLinkEntry> linkEntries = getLines('definitions_with_links.txt').collect {
            String[] pieces = it.split(',')
            def linkEntry = new DefinitionLinkEntry()
            linkEntry.definitionName = pieces[0]
            linkEntry.link = pieces[1]
            if (pieces.length == 4) {
                linkEntry.canHaveManyResults = Boolean.valueOf(pieces[2])
                linkEntry.resultClass = pieces[3]
            }

            linkEntry
        }

        Map<String, String> overrideEntries = getLines('api_path_overrides.txt').collectEntries { line ->
            String[] pieces = line.split(',')
            [(pieces[0]): pieces[1]]
        }

        DefinitionsWithManyVersions definitionsWithManyVersions = new DefinitionsWithManyVersions();
        getLines('definitions_that_have_many_versions.txt').each { line ->
            String[] pieces = line.split(',')
            String preferredName = pieces[-1]
            pieces.each { piece -> definitionsWithManyVersions.addDefinitionWithManyVersions(piece, preferredName)
            }
        }

        final SwaggerEnumsParser swaggerEnumsParser = new SwaggerEnumsParser()
        final SwaggerPropertiesParser swaggerPropertiesParser = new SwaggerPropertiesParser(swaggerEnumsParser)
        final SwaggerDefinitionsParser swaggerDefinitionsParser = new SwaggerDefinitionsParser(swaggerPropertiesParser, definitionsWithManyVersions);
        final Map<String, SwaggerDefinition> allObjectDefinitions = swaggerDefinitionsParser.getDefinitionsFromJson(swaggerJson)
        println allObjectDefinitions.keySet().size()
        final DefinitionLinks definitionLinks = new DefinitionLinks(linkEntries, allObjectDefinitions.keySet(), definitionNamesToExtendBlackDuckView, definitionNamesToExtendBlackDuckResponse, swaggerEnumsParser.winningNamesToValues.keySet(), definitionNamesMaintainedManually)

        final SwaggerPathsParser swaggerPathsParser = new SwaggerPathsParser();
        final List<ApiPath> apiPaths = swaggerPathsParser.getPathsToResponses(swaggerJson, apiPathsToIgnore, overrideEntries);

        logPossibleErrors(swaggerDefinitionsParser, swaggerPropertiesParser, allObjectDefinitions);

        Set<String> possibleReferencesForProperties = new HashSet<>();
        possibleReferencesForProperties.addAll(allObjectDefinitions.keySet());
        possibleReferencesForProperties.addAll(SwaggerDefinitionsParser.DEFINITIONS_TO_IGNORE_AND_CREATE_MANUALLY);

        final Configuration configuration = new Configuration(Configuration.VERSION_2_3_23);
        configuration.setClassForTemplateLoading(ModelCreator.class, "/");
        configuration.setDefaultEncoding("UTF-8");
        Template discoveryTemplate = configuration.getTemplate("blackDuckDiscovery.ftl");
        Template enumTemplate = configuration.getTemplate("blackDuckEnum.ftl");
        Template viewTemplate = configuration.getTemplate("blackDuckView.ftl");

        File discoveryBaseDirectory = new File(getBaseDirectory(), ModelCreator.DISCOVERY_DIRECTORY);
        File enumBaseDirectory = new File(getBaseDirectory(), ModelCreator.ENUM_DIRECTORY);
        File viewBaseDirectory = new File(getBaseDirectory(), ModelCreator.VIEW_DIRECTORY);
        File responseBaseDirectory = new File(getBaseDirectory(), ModelCreator.RESPONSE_DIRECTORY);
        File modelBaseDirectory = new File(getBaseDirectory(), ModelCreator.COMPONENT_DIRECTORY);
        discoveryBaseDirectory.mkdirs();
        enumBaseDirectory.mkdirs();
        viewBaseDirectory.mkdirs();
        responseBaseDirectory.mkdirs();
        modelBaseDirectory.mkdirs();
        createDiscoveryFile(discoveryBaseDirectory, discoveryTemplate, apiPaths, definitionLinks)
        createEnumFiles(enumBaseDirectory, enumTemplate, swaggerEnumsParser.winningNamesToValues);

        ComponentCreator componentCreator = new ComponentCreator();
        componentCreator.createViewFiles(getBaseDirectory(), viewTemplate, new ArrayList<>(allObjectDefinitions.values()), possibleReferencesForProperties, definitionNamesToExtendBlackDuckView, definitionNamesToExtendBlackDuckResponse, definitionNamesThatImplementBuildable, definitionNamesMaintainedManually, definitionLinks, swaggerEnumsParser);
    }

    public static List<String> getLines(String resourceName) {
        final InputStream inputStream = ModelCreator.class.getResourceAsStream("/${resourceName}");
        List<String> allLines = IOUtils.readLines(inputStream, StandardCharsets.UTF_8);
        return allLines.collect {
            it?.trim()
        }.findAll {
            it && !it.startsWith('#')
        }
    }

    public static File getBaseDirectory() {
        String baseDirectory = System.getenv(BASE_DIRECTORY_ENVIRONMENT_VARIABLE);
        if (!baseDirectory) {
            baseDirectory = DEFAULT_BASE_DIRECTORY;
        }
        return new File(baseDirectory);
    }

    public static void createDiscoveryFile(File baseDirectory, Template template, List<ApiPath> apiPaths, DefinitionLinks definitionLinks) {
        File discoveryFile = new File(baseDirectory, "ApiDiscovery.java");

        final Map<String, Object> model = new HashMap<>();
        model.put("discoveryPackage", ModelCreator.DISCOVERY_PACKAGE)

        Set imports = new HashSet<>()
        List links = new ArrayList<>()
        model.put("links", links)

        apiPaths.each {
            String importPackage = definitionLinks.getFullyQualifiedClassName(it.resultClass);
            if (null == importPackage) {
                println "couldn't find package for: " + it.resultClass;
            }
            imports.add(importPackage);
            imports.add(ModelCreator.API_CORE_PACKAGE_PREFIX + "." + "LinkResponse");
            imports.add(ModelCreator.API_CORE_PACKAGE_PREFIX + "." + "BlackDuckPath");
            imports.add(ModelCreator.API_CORE_PACKAGE_PREFIX + "." + "BlackDuckPathSingleResponse");
            imports.add(ModelCreator.API_CORE_PACKAGE_PREFIX + "." + "BlackDuckPathMultipleResponses");

            Map<String, Object> linkModel = new HashMap<>();
            linkModel.put("label", it.path);
            linkModel.put("javaConstant", it.javaConstant);
            linkModel.put("resultClass", it.resultClass);
            if (it.hasManyResults) {
                linkModel.put("hasMultipleResults", true);
                linkModel.put("linkType", "BlackDuckPathMultipleResponses<${it.resultClass}>");
            } else {
                linkModel.put("linkType", "BlackDuckPathSingleResponse<${it.resultClass}>");
            }
            links.add(linkModel);
        }

        List sortedImports = new ArrayList<>(imports);
        Collections.sort(sortedImports);
        model.put("imports", sortedImports)

        final FileWriter fileWriter = new FileWriter(discoveryFile);
        template.process(model, fileWriter);
    }

    public static void createEnumFiles(File baseDirectory, Template template, Map<String, List<String>> enumNameToValues) {
        enumNameToValues.each { k, v ->
            File enumFile = new File(baseDirectory, k + ".java");

            final Map<String, Object> model = new HashMap<>();
            model.put("enumPackage", ModelCreator.ENUM_PACKAGE)
            model.put("enumClassName", k);
            model.put("enumValues", v);

            final FileWriter fileWriter = new FileWriter(enumFile);
            template.process(model, fileWriter);
        }
    }

    public static logPossibleErrors(SwaggerDefinitionsParser swaggerDefinitionsParser, SwaggerPropertiesParser swaggerPropertiesParser, Map<String, SwaggerDefinition> allObjectDefinitions) {
        println "Unknown definitions (possible problems) (${swaggerDefinitionsParser.unknownDefinitionNames.size()}):"
        swaggerDefinitionsParser.unknownDefinitionNames.toSorted { a, b -> a <=> b
        }.each { println it }
        println '---------------------------------------'

        println "Unknown property fields (possible problems) (${swaggerPropertiesParser.unknownPropertyFields.size()}):"
        swaggerPropertiesParser.unknownPropertyFields.toSorted { a, b -> a <=> b
        }.each { println it }
        println '---------------------------------------'

        println "Found definitions (${swaggerDefinitionsParser.allProcessedDefintionNames.size()}):"
        swaggerDefinitionsParser.allProcessedDefintionNames.toSorted { a, b -> a <=> b
        }.each { println it }
        println '---------------------------------------'

        println "Found types/formats:"
        swaggerPropertiesParser.propertyTypeToFormats.each { k, v -> println "${k}: formats(${v.join(', ')})"
        }
        println '---------------------------------------'

        allObjectDefinitions.each { k, v -> println v }
    }
}