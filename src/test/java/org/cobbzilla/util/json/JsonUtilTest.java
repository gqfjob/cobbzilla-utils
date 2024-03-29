package org.cobbzilla.util.json;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.RandomStringUtils;
import org.cobbzilla.util.io.FileUtil;
import org.cobbzilla.util.io.StreamUtil;
import org.cobbzilla.util.json.data.TestData;
import org.cobbzilla.util.string.StringUtil;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;

public class JsonUtilTest {

    private static final String TEST_JSON = StringUtil.getPackagePath(JsonUtilTest.class)+"/test.json";
    private static final Object[][] TESTS = new Object[][] {
        new Object[] {
                "id", new ReplacementValue() { @Override public String getValue(TestData testData) { return testData.id; } }
        },
        new Object[] {
                "thing.field1[1]", new ReplacementValue() { @Override public String getValue(TestData testData) { return testData.thing.field1[1]; } }
        },
        new Object[] {
                "another_thing.field1", new ReplacementValue() { @Override public String getValue(TestData testData) { return testData.another_thing.field1; } }
        },
    };

    @Test
    public void testReplaceJsonValue () throws Exception {

        final String testJson = StreamUtil.loadResourceAsString(TEST_JSON);
        final String replacement = RandomStringUtils.randomAlphanumeric(10);

        for (Object[] test : TESTS) {
            assertReplacementMade(testJson, replacement, (String) test[0], (ReplacementValue) test[1]);
        }
    }

    public void assertReplacementMade(String testJson, String replacement, String path, ReplacementValue value) throws Exception {
        final ObjectNode doc = JsonUtil.replaceNode(testJson, path, replacement);
        final File temp = File.createTempFile("JsonUtilTest", ".json");
        FileUtil.toFile(temp, JsonUtil.toJson(doc));
        final TestData data = JsonUtil.fromJson(temp, TestData.class);
        assertEquals(replacement, value.getValue(data));
    }

    public static interface ReplacementValue {
        public String getValue(TestData testData);
    }


}
