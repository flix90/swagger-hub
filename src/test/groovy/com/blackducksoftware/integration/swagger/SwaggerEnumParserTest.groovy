package com.blackducksoftware.integration.swagger;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.blackducksoftware.integration.swagger.parser.SwaggerEnumsParser;

public class SwaggerEnumParserTest {
    @Test
    public void testRemovingDuplicateWords() {
        final SwaggerEnumsParser swaggerEnums = new SwaggerEnumsParser();
        String s = swaggerEnums.removeDuplicateWords("ASimpleSimpleTest");
        assertEquals("ASimpleTest", s);

        s = swaggerEnums.removeDuplicateWords("ASimpleSimpleSimpleTest");
        assertEquals("ASimpleTest", s);

        s = swaggerEnums.removeDuplicateWords("ASimpleSimpleSimpleTestTest");
        assertEquals("ASimpleTest", s);

        s = swaggerEnums.removeDuplicateWords("TrickyTrickyTestTest");
        assertEquals("TrickyTest", s);
    }

}