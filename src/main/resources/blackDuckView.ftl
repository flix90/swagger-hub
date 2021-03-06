package ${viewPackage};

<#if hasLinksWithResults??>
import java.util.HashMap;
import java.util.Map;

</#if>
<#list imports as import>
import ${import};
</#list>

//this file should not be edited - if changes are necessary, the generator should be updated, then this file should be re-created
public class ${className} extends ${baseClass} <#if buildable??>implements Buildable </#if>{
<#if hasLinksWithResults??>
    public static final Map<String, LinkResponse> links = new HashMap<>();

</#if>
<#if hasLinks??>
    <#list links as link>
    public static final String ${link.javaConstant} = "${link.label}";
    </#list>

    <#list links as link>
        <#if link.resultClass??>
    public static final ${link.linkType} ${link.javaConstant}_RESPONSE = new ${link.linkType}(${link.javaConstant}, ${link.resultClass}.class);
        </#if>
    </#list>

</#if>
<#if hasLinksWithResults??>
    static {
    <#list links as link>
        <#if link.resultClass??>
        links.put(${link.javaConstant}, ${link.javaConstant}_RESPONSE);
        </#if>
    </#list>
    }

</#if>
<#list classFields as field>
    private ${field.type} ${field.name};
</#list>

<#if buildable??>
    public static ${className}Builder newBuilder() {
        return new ${className}Builder();
    }

</#if>
<#list classFields as field>
    public ${field.type} get${field.name?cap_first}() {
        return ${field.name};
    }

    public void set${field.name?cap_first}(${field.type} ${field.name}) {
        this.${field.name} = ${field.name};
    }

</#list>
}
