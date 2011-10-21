package net_alchim31_maven_yuicompressor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.IOUtil;

public class Aggregation {
    public File inputDir;
    public File output;
    public String[] includes;
    public String[] excludes;
    public boolean removeIncluded = false;
    public boolean insertNewLine = false;
    public boolean fixLastSemicolon = false;
    
    public void run() throws Exception {
        defineInputDir();
        List<File> files = getIncludedFiles();
        if (files.size() != 0) {
            output = output.getCanonicalFile();
            output.getParentFile().mkdirs();
            FileOutputStream out = new FileOutputStream(output);
            try {
                for (File file : files) {
                    if (file.getCanonicalPath().equals(output.getCanonicalPath())) {
                        continue;
                    }
                    FileInputStream in = new FileInputStream(file);
                    try {
                        IOUtil.copy(in, out);
                        if (fixLastSemicolon) {
                        	out.write(';');
                        }
                        if (insertNewLine) {
                            out.write('\n');
                        }
                    } finally {
                        IOUtil.close(in);
                        in = null;
                    }
                    if (removeIncluded) {
                        file.delete();
                    }
                }
            } finally {
                IOUtil.close(out);
                out = null;
            }
        }
    }

    private void defineInputDir() throws Exception {
      if (inputDir == null) {
        inputDir = output.getParentFile();
      }
      inputDir = inputDir.getCanonicalFile();
    }
    
    private List<File> getIncludedFiles() throws Exception {
        ArrayList<File> back = new ArrayList<File>();
        if (includes != null) {
            for (String include : includes) {
                addInto(include, back);
            }
        }
        return back;
    }

    private void addInto(String include, List<File> includedFiles) throws Exception {
        if (include.indexOf('*') > -1) {
            DirectoryScanner scanner = newScanner();
            scanner.setIncludes(new String[] { include });
            scanner.scan();
            String[] rpaths = scanner.getIncludedFiles();
            Arrays.sort(rpaths);
            for (String rpath : rpaths) {
                File file = new File(scanner.getBasedir(), rpath);
                if (!includedFiles.contains(file)) {
                    includedFiles.add(file);
                }
            }
        } else {
            File file = new File(include);
            if (!file.isAbsolute()) {
                file = new File(inputDir, include);
            }
            if (!includedFiles.contains(file)) {
                includedFiles.add(file);
            }
        }
    }

    private DirectoryScanner newScanner() throws Exception {
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(inputDir);
        if ((excludes != null) && (excludes.length != 0)) {
            scanner.setExcludes(excludes);
        }
        scanner.addDefaultExcludes();
        return scanner;
    }
}
