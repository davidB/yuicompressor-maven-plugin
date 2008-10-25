package net.sf.alchim.mojo.yuicompressor;

import java.io.File;

import junit.framework.TestCase;

import org.codehaus.plexus.util.FileUtils;

public class AggregationTestCase extends TestCase {
    private File dir_;

    @Override
    protected void setUp() throws Exception {
        dir_ = File.createTempFile(this.getClass().getName(), "-test");
        dir_.delete();
        dir_.mkdirs();
    }

    @Override
    protected void tearDown() throws Exception {
        FileUtils.deleteDirectory(dir_);
    }

    public void test0to1() throws Exception {
        Aggregation target = new Aggregation();
        target.output = new File(dir_, "output.js");

        assertFalse(target.output.exists());
        target.run();
        assertFalse(target.output.exists());

        target.includes = new String[]{};
        assertFalse(target.output.exists());
        target.run();
        assertFalse(target.output.exists());

        target.includes = new String[]{"**/*.js"};
        assertFalse(target.output.exists());
        target.run();
        assertFalse(target.output.exists());
    }


    public void test1to1() throws Exception {
        File f1 = new File(dir_, "01.js");
        FileUtils.fileWrite(f1.getAbsolutePath(), "1");
        Aggregation target = new Aggregation();
        target.output = new File(dir_, "output.js");
        target.includes = new String[]{f1.getName()};

        assertFalse(target.output.exists());
        target.run();
        assertTrue(target.output.exists());
        assertEquals(FileUtils.fileRead(f1), FileUtils.fileRead(target.output));
    }

    public void test2to1() throws Exception {
        File f1 = new File(dir_, "01.js");
        FileUtils.fileWrite(f1.getAbsolutePath(), "1");

        File f2 = new File(dir_, "02.js");
        FileUtils.fileWrite(f2.getAbsolutePath(), "22\n22");

        Aggregation target = new Aggregation();
        target.output = new File(dir_, "output.js");

        target.includes = new String[]{f1.getName(), f2.getName()};
        assertFalse(target.output.exists());
        target.run();
        assertTrue(target.output.exists());
        assertEquals(FileUtils.fileRead(f1) + FileUtils.fileRead(f2), FileUtils.fileRead(target.output));

        target.output.delete();
        target.includes = new String[]{"*.js"};
        assertFalse(target.output.exists());
        target.run();
        assertTrue(target.output.exists());
        assertEquals(FileUtils.fileRead(f1) + FileUtils.fileRead(f2), FileUtils.fileRead(target.output));
    }

    public void testNoDuplicateAggregation() throws Exception {
        File f1 = new File(dir_, "01.js");
        FileUtils.fileWrite(f1.getAbsolutePath(), "1");

        File f2 = new File(dir_, "02.js");
        FileUtils.fileWrite(f2.getAbsolutePath(), "22\n22");

        Aggregation target = new Aggregation();
        target.output = new File(dir_, "output.js");

        target.includes = new String[]{f1.getName(), f1.getName(), f2.getName()};
        assertFalse(target.output.exists());
        target.run();
        assertTrue(target.output.exists());
        assertEquals(FileUtils.fileRead(f1) + FileUtils.fileRead(f2), FileUtils.fileRead(target.output));

        target.output.delete();
        target.includes = new String[]{f1.getName(), "*.js"};
        assertFalse(target.output.exists());
        target.run();
        assertTrue(target.output.exists());
        assertEquals(FileUtils.fileRead(f1) + FileUtils.fileRead(f2), FileUtils.fileRead(target.output));
    }

    public void test2to1Order() throws Exception {
        File f1 = new File(dir_, "01.js");
        FileUtils.fileWrite(f1.getAbsolutePath(), "1");

        File f2 = new File(dir_, "02.js");
        FileUtils.fileWrite(f2.getAbsolutePath(), "2");

        Aggregation target = new Aggregation();
        target.output = new File(dir_, "output.js");

        target.includes = new String[]{f2.getName(), f1.getName()};
        assertFalse(target.output.exists());
        target.run();
        assertTrue(target.output.exists());
        assertEquals(FileUtils.fileRead(f2) + FileUtils.fileRead(f1), FileUtils.fileRead(target.output));
    }

    public void test2to1WithNewLine() throws Exception {
        File f1 = new File(dir_, "01.js");
        FileUtils.fileWrite(f1.getAbsolutePath(), "1");

        File f2 = new File(dir_, "02.js");
        FileUtils.fileWrite(f2.getAbsolutePath(), "22\n22");

        Aggregation target = new Aggregation();
        target.output = new File(dir_, "output.js");
        target.insertNewLine = true;
        target.includes = new String[]{f1.getName(), f2.getName()};

        assertFalse(target.output.exists());
        target.run();
        assertTrue(target.output.exists());
        assertEquals(FileUtils.fileRead(f1) + "\n" + FileUtils.fileRead(f2) + "\n", FileUtils.fileRead(target.output));
    }

    public void testAbsolutePathFromInside() throws Exception {
        File f1 = new File(dir_, "01.js");
        FileUtils.fileWrite(f1.getAbsolutePath(), "1");

        File f2 = new File(dir_, "02.js");
        FileUtils.fileWrite(f2.getAbsolutePath(), "22\n22");

        Aggregation target = new Aggregation();
        target.output = new File(dir_, "output.js");

        target.includes = new String[]{f1.getAbsolutePath(), f2.getName()};
        assertFalse(target.output.exists());
        target.run();
        assertTrue(target.output.exists());
        assertEquals(FileUtils.fileRead(f1) + FileUtils.fileRead(f2), FileUtils.fileRead(target.output));
    }

    public void testAbsolutePathFromOutside() throws Exception {
        File f1 = File.createTempFile("test-01", ".js");
        try {
            FileUtils.fileWrite(f1.getAbsolutePath(), "1");

            File f2 = new File(dir_, "02.js");
            FileUtils.fileWrite(f2.getAbsolutePath(), "22\n22");

            Aggregation target = new Aggregation();
            target.output = new File(dir_, "output.js");

            target.includes = new String[]{f1.getAbsolutePath(), f2.getName()};
            assertFalse(target.output.exists());
            target.run();
            assertTrue(target.output.exists());
            assertEquals(FileUtils.fileRead(f1) + FileUtils.fileRead(f2), FileUtils.fileRead(target.output));
        } finally {
            f1.delete();
        }
    }
}
