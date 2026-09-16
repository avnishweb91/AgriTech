package com.example.smarthub;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Instrumented tests for Agri-Tech Smart Hub application.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
    
    @Test
    public void useAppContext() {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("com.example.smarthub", appContext.getPackageName());
    }
    
    @Test
    public void testAppNameResource() {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String appName = appContext.getString(R.string.app_name);
        assertNotNull("App name resource should not be null", appName);
        assertFalse("App name resource should not be empty", appName.isEmpty());
    }
    
    @Test
    public void testThemeResources() {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertNotNull("Primary color should exist", 
                    appContext.getColor(R.color.primary));
        assertNotNull("Background color should exist", 
                    appContext.getColor(R.color.background));
    }
}