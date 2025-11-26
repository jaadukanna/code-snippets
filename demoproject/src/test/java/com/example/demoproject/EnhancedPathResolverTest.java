package com.example.demoproject;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class EnhancedPathResolverTest {
    @Test
void testNestedArrayFilteringWithApplicationStructure() {
    Map<String, Object> data = Map.of(
        "application", Map.of(
            "id", "APP-2025-001",
            "applicant", List.of(
                Map.of(
                    "id", "A1",
                    "demographic", Map.of(
                        "firstName", "John",
                        "lastName", "Doe",
                        "ssn", "123-45-6789",
                        "relationshipType", "APPLICANT"
                    ),
                    "addresses", List.of(
                        Map.of("type", "home", "street", "123 Main St", "city", "NYC", "zip", "10001"),
                        Map.of("type", "billing", "street", "456 Business Ave", "city", "NYC", "zip", "10002"),
                        Map.of("type", "mailing", "street", "123 Main St", "city", "NYC", "zip", "10001")
                    )
                ),
                Map.of(
                    "id", "A2",
                        "demographic", Map.of(
                        "firstName", "Baby",
                        "lastName", "Doe",
                        "ssn", "123-45-6790",
                        "relationshipType", "CHILD"
                    ),
                    "addresses", List.of(
                        Map.of("type", "home", "street", "123 Main St", "city", "NYC", "zip", "10001")
                    )
                )
            )
        )
    );
    
    // Test nested array filtering
    Object primaryBillingStreet = EnhancedPathResolver.read(data, 
        "application.applicant[demographic.relationshipType='APPLICANT'].addresses[type='billing'].street");
    assertEquals("456 Business Ave", primaryBillingStreet);
    
   Object allHomeStreets = EnhancedPathResolver.read(data, 
        "application.applicant.addresses[type='home'].street");
    assertEquals(List.of("123 Main St", "123 Main St"), allHomeStreets);
    
    Object primaryHomeZip = EnhancedPathResolver.read(data, 
        "application.applicant[demographic.relationshipType='CHILD'].addresses[type='home'].zip");
    assertEquals("10001", primaryHomeZip);
    
    // Test filtering by multiple conditions
    Object nycMailingAddresses = EnhancedPathResolver.read(data, 
        "application.applicant.addresses[city='NYC' and zip='10001' and type='mailing'].street");
    assertEquals("123 Main St", nycMailingAddresses);
}

    @Test
    void testNumericAndDecimalPredicates() {
        Map<String, Object> data = Map.of(
            "items", List.of(
                Map.of("id", 30, "name", "thirty"),
                Map.of("id", 40, "name", "forty")
            ),
            "vals", List.of(
                Map.of("val", 3.14, "name", "pi")
            )
        );

        Object name = EnhancedPathResolver.read(data, "items[id=30].name");
        assertEquals("thirty", name);

        // quoted numeric should also match (string vs number)
        Object nameQuoted = EnhancedPathResolver.read(data, "items[id='30'].name");
        assertEquals("thirty", nameQuoted);

        Object pi = EnhancedPathResolver.read(data, "vals[val=3.14].name");
        assertEquals("pi", pi);
    }

    @Test
    void testBooleanPredicate() {
        Map<String, Object> data = Map.of(
            "flags", List.of(
                Map.of("enabled", true, "name", "on"),
                Map.of("enabled", false, "name", "off")
            )
        );

        Object on = EnhancedPathResolver.read(data, "flags[enabled=true].name");
        assertEquals("on", on);

        Object off = EnhancedPathResolver.read(data, "flags[enabled=false].name");
        assertEquals("off", off);
    }

@Test
void testNestedArrayFiltering() {
    Map<String, Object> data = Map.of(
        "applicants", List.of(
            Map.of(
                "firstName", "John",
                "addresses", List.of(
                    Map.of("type", "home", "street", "123 Main St"),
                    Map.of("type", "billing", "street", "456 Business Ave")
                )
            )
        )
    );
    
    // Test nested array filtering
    Object homeStreet = EnhancedPathResolver.read(data, "applicants.addresses[type='home'].street");
    assertEquals("123 Main St", homeStreet);
    
    Object billingStreet = EnhancedPathResolver.read(data, "applicants.addresses[type='billing'].street");
    assertEquals("456 Business Ave", billingStreet);
    
    // Test filtering non-existent type
    Object workStreet = EnhancedPathResolver.read(data, "applicants.addresses[type='work'].street");
    assertNull(workStreet);
}

    @Test
    void testOperatorComparisonsAndEdgeCases() {
        Map<String, Object> data = Map.of(
            "products", List.of(
                Map.of("id", "p1", "price", 50, "name", "expensive"),
                Map.of("id", "p2", "price", 10, "name", "cheap"),
                Map.of("id", "p3", "price", 20, "name", "mid")
            ),
            "users", List.of(
                Map.of("name", "Alice", "role", "admin"),
                Map.of("name", "Bob", "role", "user"),
                Map.of("name", "Carol", "role", "guest")
            )
        );

        Object expensive = EnhancedPathResolver.read(data, "products[price>20].name");
        assertEquals("expensive", expensive);

        Object cheap = EnhancedPathResolver.read(data, "products[price<20].name");
        assertEquals("cheap", cheap);

        Object atLeast20 = EnhancedPathResolver.read(data, "products[price>=20].name");
        assertEquals(List.of("expensive", "mid"), atLeast20);

        Object not20 = EnhancedPathResolver.read(data, "products[price!=20].name");
        assertEquals(List.of("expensive", "cheap"), not20);

        // String not-equals operator
        Object nonAdmins = EnhancedPathResolver.read(data, "users[role!='admin'].name");
        assertEquals(List.of("Bob", "Carol"), nonAdmins);

        // Missing field in predicate should safely return null
        Object missing = EnhancedPathResolver.read(data, "products[discount>0].name");
        assertNull(missing);

        // Invalid numeric comparison value should not throw and should not match
        Object invalidCompare = EnhancedPathResolver.read(data, "products[price>notanumber].name");
        assertNull(invalidCompare);
    }

}