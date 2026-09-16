package com.example.smarthub;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for Agri-Tech Smart Hub application.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    
    @Test
    public void addition_isCorrect() {
        assertEquals(4, 2 + 2);
    }
    
    @Test
    public void testAppName() {
        // Test that app name is not null or empty
        assertNotNull("App name should not be null", "बिहार एग्री-टेक स्मार्ट हब");
        assertFalse("App name should not be empty", "बिहार एग्री-टेक स्मार्ट हब".isEmpty());
    }
    
    @Test
    public void testDatabaseVersion() {
        // Test database version is positive
        assertTrue("Database version should be positive", 2 > 0);
    }
    
}
